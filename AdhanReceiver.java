package com.dawa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        boolean test = i.getBooleanExtra("test", false);
        String title = i.getStringExtra("title"), body = i.getStringExtra("body");
        String file = test ? i.getStringExtra("file") : AdhanScheduler.file(c);
        float vol = test ? i.getFloatExtra("vol", 0.9f) : AdhanScheduler.volume(c);
        try {
            AdhanService.play(c, title, body, file, vol);
        } catch (Throwable t) {
            AdhanService.notifyFallback(c, title, body); // لو أندرويد منع تشغيل الخدمة
        }
        if (!test) AdhanScheduler.armNext(c); // جهّز الأذان اللي بعده
    }
}
