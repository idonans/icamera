package com.idonans.icamera.demo;

import android.util.Log;

import com.idonans.acommon.App;

/**
 * Created by idonans on 2016/7/27.
 */
public class BuildConfigAdapterImpl implements App.BuildConfigAdapter {

    @Override
    public int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    @Override
    public String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public String getLogTag() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public int getLogLevel() {
        return Log.DEBUG;
    }

    @Override
    public boolean isDebug() {
        return BuildConfig.DEBUG;
    }

}
