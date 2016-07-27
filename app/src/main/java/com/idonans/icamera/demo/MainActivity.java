package com.idonans.icamera.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;

import com.idonans.acommon.util.IOUtil;
import com.idonans.acommon.util.ViewUtil;
import com.idonans.icamera.CameraPreview;

public class MainActivity extends AppCompatActivity {

    private ViewGroup mCameraPreviewPanel;
    private CameraPreview mCameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraPreviewPanel = ViewUtil.findViewByID(this, R.id.camera_preview_panel);
        attachCameraPreview();
    }

    private void attachCameraPreview() {
        if (mCameraPreview != null) {
            IOUtil.closeQuietly(mCameraPreview);
            mCameraPreviewPanel.removeView(mCameraPreview);
            mCameraPreview = null;
        }

        mCameraPreview = new CameraPreview(this);
        mCameraPreviewPanel.addView(mCameraPreview, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

}
