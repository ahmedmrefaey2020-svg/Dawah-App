package com.dawa.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** بيحفظ مواعيد الصلاة ويضبط منبّه دقيق واحد للأذان القادم فقط، وكل ما يشتغل بيضبط اللي بعده. */
final class AdhanScheduler {
    static final String PREFS = "dawa_adhan";
    private static final int REQ = 7001, REQ_TEST = 7002;
    static final String ACTION = "com.dawa.app.ADHAN";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, JSONArray items, String file, double volume) {
        p(c).edit().putString("items", items.toString()).putString("file", file == null ? "" : file)
                .putFloat("vol", (float) volume).apply();
    }

    static String file(Context c) { return p(c).getString("file", ""); }
    static float volume(Context c) { return p(c).getFloat("vol", 0.9f); }

    private static PendingIntent pi(Context c, int req, Intent in) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, req, in, fl);
    }

    private static void setExact(Context c, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); // احتياطي: غير دقيق
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void clear(Context c) {
        p(c).edit().remove("items").apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, REQ, new Intent(c, AdhanReceiver.class).setAction(ACTION)));
    }

    /** يضبط أقرب أذان في المستقبل. */
    static void armNext(Context c) {
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
            JSONObject pick = null;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long at = o.optLong("at");
                if (at > now + 1000 && at < best) { best = at; pick = o; }
            }
            Intent in = new Intent(c, AdhanReceiver.class).setAction(ACTION);
            if (pick == null) {
                ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c, REQ, in));
                return;
            }
            in.putExtra("title", pick.optString("title", "الأذان"));
            in.putExtra("body", pick.optString("body", ""));
            setExact(c, best, pi(c, REQ, in));
        } catch (Exception ignored) {
        }
    }

    static void armTest(Context c, long delayMs, String file, float vol, String title, String body) {
        Intent in = new Intent(c, AdhanReceiver.class).setAction(ACTION + ".TEST");
        in.putExtra("test", true).putExtra("file", file).putExtra("vol", vol)
                .putExtra("title", title).putExtra("body", body);
        setExact(c, System.currentTimeMillis() + delayMs, pi(c, REQ_TEST, in));
    }
}
