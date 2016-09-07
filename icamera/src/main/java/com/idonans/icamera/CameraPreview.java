package com.idonans.icamera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import com.idonans.acommon.AppContext;
import com.idonans.acommon.lang.CommonLog;
import com.idonans.acommon.lang.Threads;
import com.idonans.acommon.util.IOUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by idonans on 2016/7/26.
 * <p/>
 * 此类不支持 layout 方式，只能使用代码动态添加，注意在添加之前，应当请求照相机权限，当请求成功时，再使用。
 */
public class CameraPreview extends TextureView implements Closeable {

    private static final String TAG = "CameraPreview";

    public static class Params {
        public static final int ASPECT_AUTO = 0;
        public static final int ASPECT_1x1 = 1;
        public static final int ASPECT_4x3 = 2;
        public static final int ASPECT_16x9 = 3;

        private int[] mAspects = new int[]{ASPECT_AUTO};
        private boolean mUseFront = false;
        private boolean mCanSwitch = true;

        /**
         * 设置拍照比例, 可以设置多个, 会选取第一个可用的
         *
         * @param aspects
         */
        public void setAspect(int[] aspects) {
            mAspects = aspects;
        }

        /**
         * 是否默认使用前置摄像头
         *
         * @param useFront
         */
        public void setUseFront(boolean useFront) {
            mUseFront = useFront;
        }

        /**
         * 是否可以切换摄像头
         *
         * @param canSwitch
         */
        public void setCanSwitch(boolean canSwitch) {
            mCanSwitch = canSwitch;
        }

    }

    /**
     * 当前相机的设置参数
     */
    private static class CameraSettings {

        public String flashMode;
        public String focusMode;

        public BestSize mBestSize;

        public CameraInfos cameraInfos;

        public int displayOrientation;

        public void apply(Camera.Parameters parameters, Camera camera) {
            if (!TextUtils.isEmpty(flashMode)) {
                parameters.setFlashMode(flashMode);
            }

            if (!TextUtils.isEmpty(focusMode)) {
                parameters.setFocusMode(focusMode);
            }

            parameters.setPreviewSize(mBestSize.previewSize.width, mBestSize.previewSize.height);
            parameters.setPictureSize(mBestSize.pictureSize.width, mBestSize.pictureSize.height);

            camera.setDisplayOrientation(displayOrientation);
        }

        private int calculateDisplayOrientation() {
            WindowManager windowManager = (WindowManager) AppContext.getContext().getSystemService(Context.WINDOW_SERVICE);
            int rotation = windowManager.getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            int result;
            if (cameraInfos.isFaceFront()) {
                result = (cameraInfos.info.orientation + degrees) % 360;
                result = (360 - result) % 360;
            } else {
                result = (cameraInfos.info.orientation - degrees + 360) % 360;
            }

            return result;
        }
    }

    private final Params mParams;
    private final CameraSettings mCameraSettings;
    private Camera mCamera;

    public CameraPreview(Context context, @NonNull Params params) {
        super(context);
        mParams = params;
        mCameraSettings = preSetting(params);
        if (mCameraSettings == null) {
            showMessage("相机初始化失败");
            return;
        }

        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                CommonLog.d(TAG + " onSurfaceTextureAvailable width:" + width + ", height:" + height);
                changeToSize(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                CommonLog.d(TAG + " onSurfaceTextureSizeChanged width:" + width + ", height:" + height);
                changeToSize(surface, width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                CommonLog.d(TAG + " onSurfaceTextureDestroyed");
                IOUtil.closeQuietly(CameraPreview.this);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // ignore
            }
        });
    }

    private static void showMessage(final String message) {
        Threads.runOnUi(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AppContext.getContext(), String.valueOf(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 预设置相机参数
     *
     * @param params
     * @return
     */
    private CameraSettings preSetting(@NonNull Params params) {
        CameraInfos cameraInfos = getCameraInfos(params.mUseFront);
        if (cameraInfos == null) {
            CommonLog.e(TAG + " CameraInfos is null");
            return null;
        }

        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.cameraInfos = cameraInfos;

        Camera camera = null;
        try {
            camera = Camera.open(cameraInfos.id);
            Camera.Parameters parameters = camera.getParameters();

            // 闪光灯
            List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            if (supportedFlashModes != null) {
                // 支持自动闪光灯
                if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                    cameraSettings.flashMode = Camera.Parameters.FLASH_MODE_AUTO;
                }
            }

            // 对焦方式
            List<String> supportedFocusModes = parameters.getSupportedFocusModes();
            if (supportedFocusModes != null) {
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    // 支持自动对焦
                    cameraSettings.focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
                }
            }

            // 计算拍照尺寸和预览尺寸
            List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
            List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();

            for (int aspect : params.mAspects) {
                BestSize bestSize = findBestSize(previewSizes, pictureSizes, aspect);
                if (bestSize != null) {
                    cameraSettings.mBestSize = bestSize;
                    break;
                }
            }

            if (cameraSettings.mBestSize == null) {
                CommonLog.e(TAG + " best size not found");
                return null;
            }

            cameraSettings.displayOrientation = cameraSettings.calculateDisplayOrientation();
            return cameraSettings;
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            releaseCamera(camera);
        }

        return null;
    }

    public static class BestSize implements Comparable<BestSize> {
        @NonNull
        final Camera.Size previewSize;
        @NonNull
        final Camera.Size pictureSize;

        private BestSize(@NonNull Camera.Size previewSize, @NonNull Camera.Size pictureSize) {
            this.previewSize = previewSize;
            this.pictureSize = pictureSize;
        }

        @Override
        public int compareTo(BestSize another) {
            return this.pictureSize.width - another.pictureSize.width;
        }
    }

    /**
     * 根据需要的比例找到一个最佳的拍照尺寸和预览尺寸, 如果没有找到匹配的尺寸, 返回 null.
     *
     * @param previewSizes
     * @param pictureSizes
     * @param aspect
     * @return
     */
    @CheckResult
    private static BestSize findBestSize(List<Camera.Size> previewSizes, List<Camera.Size> pictureSizes, int aspect) {
        if (previewSizes == null || pictureSizes == null) {
            return null;
        }

        switch (aspect) {
            case Params.ASPECT_AUTO: {
                List<BestSize> bestSizes = new ArrayList<>();
                for (Camera.Size s1 : previewSizes) {
                    for (Camera.Size s2 : pictureSizes) {
                        if (isSameAspect(s1, s2)) {
                            bestSizes.add(new BestSize(s1, s2));
                        }
                    }
                }
                return findBestSize(bestSizes);
            }
            case Params.ASPECT_1x1: {
                List<BestSize> bestSizes = new ArrayList<>();
                for (Camera.Size s1 : previewSizes) {
                    if (isSameAspect(s1, 1, 1)) {
                        for (Camera.Size s2 : pictureSizes) {
                            if (isSameAspect(s1, s2)) {
                                bestSizes.add(new BestSize(s1, s2));
                            }
                        }
                    }
                }
                return findBestSize(bestSizes);
            }
            case Params.ASPECT_4x3: {
                List<BestSize> bestSizes = new ArrayList<>();
                for (Camera.Size s1 : previewSizes) {
                    if (isSameAspect(s1, 4, 3)) {
                        for (Camera.Size s2 : pictureSizes) {
                            if (isSameAspect(s1, s2)) {
                                bestSizes.add(new BestSize(s1, s2));
                            }
                        }
                    }
                }
                return findBestSize(bestSizes);
            }
            case Params.ASPECT_16x9: {
                List<BestSize> bestSizes = new ArrayList<>();
                for (Camera.Size s1 : previewSizes) {
                    if (isSameAspect(s1, 16, 9)) {
                        for (Camera.Size s2 : pictureSizes) {
                            if (isSameAspect(s1, s2)) {
                                bestSizes.add(new BestSize(s1, s2));
                            }
                        }
                    }
                }
                return findBestSize(bestSizes);
            }
            default: {
                return null;
            }
        }
    }

    /**
     * 在其中找到一个中间尺寸
     *
     * @param bestSizes
     * @return
     */
    @CheckResult
    private static BestSize findBestSize(List<BestSize> bestSizes) {
        if (bestSizes == null || bestSizes.isEmpty()) {
            return null;
        }

        Collections.sort(bestSizes);
        int size = bestSizes.size();
        if (size <= 2) {
            return bestSizes.get(size - 1);
        }

        return bestSizes.get((size + 1) / 2);
    }

    /**
     * 判断这两个尺寸是否具有相同的比例
     *
     * @param s1
     * @param s2
     * @return
     */
    private static boolean isSameAspect(Camera.Size s1, Camera.Size s2) {
        if (s1 == null || s2 == null) {
            return false;
        }

        return isSameAspect(s1, s2.width, s2.height);
    }

    private static boolean isSameAspect(Camera.Size s, int width, int height) {
        if (s == null) {
            return false;
        }

        return s.width * height == s.height * width;
    }

    private static void releaseCamera(Camera camera) {
        if (camera == null) {
            return;
        }

        try {
            camera.stopPreview();
        } catch (Throwable e) {
            // ignore
        }

        try {
            camera.setPreviewTexture(null);
        } catch (Throwable e) {
            // ignore
        }

        try {
            camera.release();
        } catch (Throwable e) {
            // ignore
        }
    }

    @CheckResult
    private static Camera previewCamera(CameraSettings cameraSettings, SurfaceTexture texture) {
        if (cameraSettings == null) {
            return null;
        }

        Camera camera = null;
        try {
            camera = Camera.open(cameraSettings.cameraInfos.id);
            Camera.Parameters parameters = camera.getParameters();
            cameraSettings.apply(parameters, camera);
            camera.setParameters(parameters);
            camera.setPreviewTexture(texture);
            camera.startPreview();
            return camera;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        releaseCamera(camera);
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            if (mCameraSettings == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }

            int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            CommonLog.d(TAG + " onMeasure pre width:" + width + ", height:" + height);

            if (width <= 0 || height <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }

            // 此处仅处理手机竖屏情况
            int aspectWidth = mCameraSettings.mBestSize.previewSize.height;
            int aspectHeight = mCameraSettings.mBestSize.previewSize.width;

            if (width * aspectHeight < height * aspectWidth) {
                // 压缩高度
                int measureWidth = width;
                int measureHeight = (int) (1f * width * aspectHeight / aspectWidth);
                setMeasuredDimension(measureWidth, measureHeight);
            } else {
                // 压缩宽度
                int measureHeight = height;
                int measureWidth = (int) (1f * height * aspectWidth / aspectHeight);
                setMeasuredDimension(measureWidth, measureHeight);
            }
        } finally {
            CommonLog.d(TAG + " onMeasure measured size " + getMeasuredWidth() + ", " + getMeasuredHeight());
        }
    }

    private void changeToSize(SurfaceTexture surface, int width, int height) {
        CommonLog.d(TAG + " changeToSize width:" + width + ", height:" + height);

        releaseCamera(mCamera);

        if (surface == null) {
            CommonLog.e(TAG + " changeToSize SurfaceTexture is null");
            return;
        }
        if (width <= 0 || height <= 0) {
            CommonLog.e(TAG + " changeToSize width or height invalid " + width + "," + height);
            return;
        }

        mCamera = previewCamera(mCameraSettings, surface);
    }


    @Override
    public void close() throws IOException {
        CommonLog.d(TAG + " close");
        releaseCamera(mCamera);
        mCamera = null;
    }

    private boolean mPictureTaking;

    /**
     * 拍照，如果当前正在拍照，则会忽略本次请求
     */
    public boolean takePicture(final Camera.PictureCallback callback) {
        if (mCamera == null) {
            return false;
        }

        if (mPictureTaking) {
            CommonLog.d(TAG + " takePicture ignored, already in picture taking status");
            return false;
        }

        try {
            mPictureTaking = true;
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    mCamera.takePicture(null, null, new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            resumeCameraPreviewAfterTakePicture();
                            callback.onPictureTaken(data, camera);
                        }
                    });
                }
            });
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            resumeCameraPreviewAfterTakePicture();
            return false;
        }
    }

    private void resumeCameraPreviewAfterTakePicture() {
        try {
            mCamera.startPreview();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        mPictureTaking = false;
    }

    @CheckResult
    private static CameraInfos getCameraInfos(boolean faceFront) {
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (!faceFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    CommonLog.d(TAG + " find facing back camera with id " + i);
                    return new CameraInfos(cameraInfo, i, numberOfCameras);
                } else if (faceFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    CommonLog.d(TAG + " find facing front camera with id " + i);
                    return new CameraInfos(cameraInfo, i, numberOfCameras);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class CameraInfos {

        @NonNull
        public final Camera.CameraInfo info;

        /**
         * 摄像头 id [0, numberOfCameras)
         */
        public final int id;

        /**
         * 摄像头数量
         */
        public final int numberOfCameras;

        public CameraInfos(@NonNull Camera.CameraInfo info, int id, int numberOfCameras) {
            this.info = info;
            this.id = id;
            this.numberOfCameras = numberOfCameras;
        }

        /**
         * 是否是前置摄像头, 如果是返回 true, 否则是后置摄像头, 返回 false.
         *
         * @return
         */
        public boolean isFaceFront() {
            return info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

    }

}
