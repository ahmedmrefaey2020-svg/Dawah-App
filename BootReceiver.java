package com.dawa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** بعد إعادة تشغيل الجهاز أو تحديث التطبيق أو تغيير الوقت/المنطقة الزمنية: نعيد ضبط المنبّه. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        AdhanScheduler.armNext(c);
    }
}
