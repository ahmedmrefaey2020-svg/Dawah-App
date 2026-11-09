package com.dawa.app; // <-- غيّره لـ appId بتاعك

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** جسر JS <-> أندرويد: تحميل ملف المؤذن، جدولة الأذان، وطلب أذونات الخلفية. */
@CapacitorPlugin(name = "DawaAdhan")
public class DawaAdhanPlugin extends Plugin {

    static File adhanDir(Context c) {
        File d = new File(c.getFilesDir(), "adhan"); // المسار: /data/data/<appId>/files/adhan/
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String safeName(String id) {
        return id.replaceAll("[^A-Za-z0-9_.-]", "_") + ".mp3";
    }

    /** download({url,id}) -> {name,size}  بيحفظ في files/adhan/<id>.mp3 */
    @PluginMethod
    public void download(final PluginCall call) {
        final String url = call.getString("url");
        final String id = call.getString("id");
        if (url == null || id == null || !url.startsWith("https://")) {
            call.reject("bad args");
            return;
        }
        final Context ctx = getContext();
        new Thread(() -> {
            File part = null;
            try {
                String cur = url;
                HttpURLConnection c = null;
                boolean ok = false;
                for (int i = 0; i < 6 && !ok; i++) {
                    c = (HttpURLConnection) new URL(cur).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "DawaApp");
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = c.getHeaderField("Location");
                        c.disconnect();
                        if (loc == null) throw new Exception("redirect");
                        cur = new URL(new URL(cur), loc).toString();
                        if (!cur.startsWith("https://")) throw new Exception("insecure redirect");
                    } else if (code == 200) {
                        ok = true;
                    } else {
                        throw new Exception("HTTP " + code);
                    }
                }
                if (!ok) throw new Exception("too many redirects");
                String name = safeName(id);
                File dir = adhanDir(ctx);
                part = new File(dir, name + ".part");
                long total = 0;
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 25L * 1024 * 1024) throw new Exception("too big");
                        out.write(buf, 0, n);
                    }
                }
                c.disconnect();
                if (total < 5000) throw new Exception("empty file");
                File dst = new File(dir, name);
                if (dst.exists()) dst.delete();
                if (!part.renameTo(dst)) throw new Exception("rename failed");
                JSObject r = new JSObject();
                r.put("name", name);
                r.put("size", total);
                call.resolve(r);
            } catch (Exception e) {
                if (part != null) part.delete();
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    @PluginMethod
    public void remove(PluginCall call) {
        String name = call.getString("name", "");
        if (!name.isEmpty() && name.indexOf('/') < 0 && name.indexOf("..") < 0) {
            new File(adhanDir(getContext()), name).delete();
        }
        call.resolve();
    }

    /** schedule({items:[{id,at,title,body}], file, volume}) */
    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray items = call.getArray("items");
        if (items == null) {
            call.reject("no items");
            return;
        }
        Double vol = call.getDouble("volume", 0.9);
        AdhanScheduler.save(getContext(), (JSONArray) items, call.getString("file", ""), vol == null ? 0.9 : vol);
        AdhanScheduler.armNext(getContext());
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        AdhanScheduler.clear(getContext());
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        AdhanService.stop(getContext());
        call.resolve();
    }

    /** test({file,volume,seconds,title,body}) — بيشتغل حتى لو قفلت التطبيق */
    @PluginMethod
    public void test(PluginCall call) {
        Double vol = call.getDouble("volume", 0.9);
        Integer sec = call.getInt("seconds", 10);
        AdhanScheduler.armTest(getContext(), (sec == null ? 10 : sec) * 1000L, call.getString("file", ""),
                vol == null ? 0.9f : vol.floatValue(), call.getString("title", "الأذان"), call.getString("body", ""));
        call.resolve();
    }

    private boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    private boolean ignoringBattery(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** بيفتح إعدادات النظام لو لسه ناقص: المنبّه الدقيق أولًا، ثم استثناء البطارية. بيتنادى تاني لحد ما الاتنين يتفعّلوا. */
    @PluginMethod
    public void requestBackground(PluginCall call) {
        Context c = getContext();
        boolean exact = canExact(c), batt = ignoringBattery(c);
        Activity a = getActivity();
        try {
            Uri pkg = Uri.parse("package:" + c.getPackageName());
            if (!exact && Build.VERSION.SDK_INT >= 31) {
                a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg));
            } else if (!batt && Build.VERSION.SDK_INT >= 23) {
                a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg));
            }
        } catch (Exception ignored) {
        }
        JSObject r = new JSObject();
        r.put("exact", exact);
        r.put("battery", batt);
        r.put("ok", exact && batt);
        call.resolve(r);
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("exact", canExact(c));
        r.put("battery", ignoringBattery(c));
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        JSArray files = new JSArray();
        File[] fs = adhanDir(c).listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".mp3")) files.put(f.getName());
        r.put("files", files);
        call.resolve(r);
    }
}
