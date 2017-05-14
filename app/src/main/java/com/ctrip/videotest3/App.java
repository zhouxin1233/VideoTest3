package com.ctrip.videotest3;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

/**
 * Created by xinzhou on 2017/4/14.
 */

public class App extends Application {
    public static App mApp;
    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        mApp=this;
        LeakCanary.install(this);
    }
}
