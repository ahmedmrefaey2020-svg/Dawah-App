package com.dawa.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DawaAdhanPlugin.class); // لازم قبل super.onCreate
        super.onCreate(savedInstanceState);
    }
}
