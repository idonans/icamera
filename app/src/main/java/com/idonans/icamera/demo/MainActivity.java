package com.idonans.icamera.demo;

import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.idonans.acommon.AppContext;
import com.idonans.acommon.lang.CommonLog;
import com.idonans.acommon.lang.Threads;
import com.idonans.acommon.util.AvailableUtil;
import com.idonans.acommon.util.FileUtil;
import com.idonans.acommon.util.IOUtil;
import com.idonans.acommon.util.ViewUtil;
import com.idonans.icamera.CameraPreview;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ViewGroup mCameraPreviewPanel;
    private CameraPreview mCameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraPreviewPanel = ViewUtil.findViewByID(this, R.id.camera_preview_panel);
        attachCameraPreview();

        findViewById(R.id.take_picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    private void attachCameraPreview() {
        if (mCameraPreview != null) {
            IOUtil.closeQuietly(mCameraPreview);
            mCameraPreviewPanel.removeView(mCameraPreview);
            mCameraPreview = null;
        }

        CameraPreview.Params params = new CameraPreview.Params();
        params.setAspect(new int[]{CameraPreview.Params.ASPECT_4x3});
        params.setUseFront(true);

        mCameraPreview = new CameraPreview(this, params);
        mCameraPreviewPanel.addView(mCameraPreview, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void takePicture() {
        if (mCameraPreview != null) {
            mCameraPreview.takePicture(new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Threads.mustUi();

                    if (data == null || data.length < 1) {
                        showMessage("picture data empty");
                        return;
                    }

                    File dir = FileUtil.getPublicDCIMDir();
                    if (dir == null) {
                        showMessage("public dcim dir not found, please check sdcard.");
                        return;
                    }

                    File jpegFile = FileUtil.createNewTmpFileQuietly("icamera", ".jpg", dir);
                    if (jpegFile == null) {
                        showMessage("jpeg file create fail, please check sdcard.");
                        return;
                    }

                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(jpegFile);
                        IOUtil.copy(data, fos, AvailableUtil.always(), null);
                        showMessage("picture saved on " + jpegFile.getAbsolutePath());
                    } catch (Throwable e) {
                        e.printStackTrace();
                        CommonLog.d(TAG + " fail to write data to jpeg file, delete tmp file " + jpegFile.getAbsolutePath());
                        FileUtil.deleteFileQuietly(jpegFile);
                    } finally {
                        IOUtil.closeQuietly(fos);
                    }
                }
            });
        }
    }

    private static void showMessage(String message) {
        Toast.makeText(AppContext.getContext(), message, Toast.LENGTH_SHORT).show();
    }

}
