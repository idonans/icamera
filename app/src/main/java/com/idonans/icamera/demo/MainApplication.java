package com.idonans.icamera.demo;

import android.app.Application;

import com.idonans.acommon.App;

/**
 * Created by idonans on 2016/7/27.
 */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new App.Config.Builder()
                .setContext(this)
                .setBuildConfigAdapter(new BuildConfigAdapterImpl())
                .build()
                .init();
    }

}
