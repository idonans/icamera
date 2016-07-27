package com.idonans.icamera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.annotation.CheckResult;
import android.view.TextureView;
import android.widget.Toast;

import com.idonans.acommon.AppContext;
import com.idonans.acommon.lang.CommonLog;
import com.idonans.acommon.util.DimenUtil;
import com.idonans.acommon.util.IOUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Created by idonans on 2016/7/26.
 * <p/>
 * 此类不支持 layout 方式，只能使用代码动态添加，注意在添加之前，应当请求照相机权限，当请求成功时，再使用。
 */
public class CameraPreview extends TextureView implements Closeable {

    private static final String TAG = "CameraPreview";

    public static final int PICTURE_ASPECT_1X1 = 1;
    public static final int PICTURE_ASPECT_4X3 = 2;
    public static final int PICTURE_ASPECT_16X9 = 3;

    private int mPictureAspect = PICTURE_ASPECT_4X3;

    private boolean mUseFaceFront = true;

    private int mTextureSizeWidth = -1;
    private int mTextureSizeHeight = -1;

    private Camera mCamera;

    public CameraPreview(Context context) {
        super(context);
        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                changeToSize(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                changeToSize(surface, width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                IOUtil.closeQuietly(CameraPreview.this);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            CommonLog.d(TAG + " onMeasure pre width:" + width + ", height:" + height);

            if (width <= 0 || height <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }

            switch (mPictureAspect) {
                case PICTURE_ASPECT_1X1: {
                    int minSize = Math.min(width, height);
                    setMeasuredDimension(minSize, minSize);
                    break;
                }
                case PICTURE_ASPECT_4X3: {
                    if (width * 3 < height * 4) {
                        // 压缩高度
                        int measureWidth = width;
                        int measureHeight = (int) (1f * width * 3 / 4);
                        setMeasuredDimension(measureWidth, measureHeight);
                    } else {
                        // 压缩宽度
                        int measureHeight = height;
                        int measureWidth = (int) (1f * height * 4 / 3);
                        setMeasuredDimension(measureWidth, measureHeight);
                    }
                    break;
                }
                case PICTURE_ASPECT_16X9: {
                    if (width * 9 < height * 16) {
                        // 压缩高度
                        int measureWidth = width;
                        int measureHeight = (int) (1f * width * 9 / 16);
                        setMeasuredDimension(measureWidth, measureHeight);
                    } else {
                        // 压缩宽度
                        int measureHeight = height;
                        int measureWidth = (int) (1f * height * 16 / 9);
                        setMeasuredDimension(measureWidth, measureHeight);
                    }
                    break;
                }
                default: {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    break;
                }
            }
        } finally {
            CommonLog.d(TAG + " onMeasure measured size " + getMeasuredWidth() + ", " + getMeasuredHeight());
        }
    }

    public void setPictureAspect(int pictureAspect) {
        if (mPictureAspect != pictureAspect) {
            mPictureAspect = pictureAspect;
            requestLayout();
        }
    }

    public void setUseFaceFront(boolean useFaceFront) {
        if (mUseFaceFront != useFaceFront) {
            mUseFaceFront = useFaceFront;
            requestLayout();
        }
    }

    private void changeToSize(SurfaceTexture surface, int width, int height) {
        CommonLog.d(TAG + " changeToSize width:" + width + ", height:" + height);
        if (surface == null) {
            CommonLog.e(TAG + " changeToSize SurfaceTexture is null");
            return;
        }
        if (width <= 0 || height <= 0) {
            CommonLog.e(TAG + " changeToSize width or height invalid " + width + "," + height);
            return;
        }

        if (mTextureSizeWidth == width && mTextureSizeHeight == height) {
            CommonLog.d(TAG + " changeToSize width and height not change (equals with current) " + width + "," + height);
            return;
        }

        mTextureSizeWidth = width;
        mTextureSizeHeight = height;

        if (mCamera == null) {
            mCamera = openCamera(mUseFaceFront);
        } else {
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                // ignore
            }
        }

        if (mCamera == null) {
            CommonLog.d(TAG + " camera not found");
            return;
        }

        if (setupCameraParams(mCamera)) {
            try {
                mCamera.setPreviewTexture(surface);
                mCamera.startPreview();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                IOUtil.closeQuietly(this);
            }
        } else {
            IOUtil.closeQuietly(this);
        }
    }

    private static void showToast(String message) {
        Toast.makeText(AppContext.getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean setupCameraParams(Camera camera) {
        try {
            int[] bestSizes = findBestPreviewAndPictureSize(camera, mPictureAspect);
            if (bestSizes != null) {
                showToast("相机不支持当前画面比例");
                return false;
            }

            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(bestSizes[0], bestSizes[1]);
            parameters.setPictureSize(bestSizes[2], bestSizes[3]);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    private static int[] findBestPreviewAndPictureSize(Camera camera, int pictureAspect) throws Exception {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();

        int x;
        int y;
        switch (pictureAspect) {
            case PICTURE_ASPECT_1X1:
                x = 1;
                y = 1;
                break;
            case PICTURE_ASPECT_4X3:
                x = 4;
                y = 3;
                break;
            case PICTURE_ASPECT_16X9:
                x = 16;
                y = 9;
                break;
            default:
                throw new IllegalArgumentException("invalid picture aspect " + pictureAspect);
        }

        int expectPreviewSize = DimenUtil.dp2px(100);
        int expectPictureSize = DimenUtil.dp2px(150);

        Camera.Size bestPreviewSize = null;
        Camera.Size bestPictureSize = null;
        for (Camera.Size size : previewSizes) {
            if (size.width * y == size.height * x) {
                bestPreviewSize = bestSize(expectPreviewSize, bestPreviewSize, size);
            }
        }
        for (Camera.Size size : pictureSizes) {
            if (size.width * y == size.height * x) {
                bestPictureSize = bestSize(expectPictureSize, bestPictureSize, size);
            }
        }

        if (bestPreviewSize != null && bestPictureSize != null) {
            return new int[]{bestPreviewSize.width, bestPreviewSize.height, bestPictureSize.width, bestPictureSize.height};
        }
        return null;
    }

    /**
     * 尽可能取到一个非空值，取相对较小的一个，但是排除太小的，如果都很小，从中取一个相对最大的。
     */
    private static Camera.Size bestSize(int expectSize, Camera.Size size1, Camera.Size size2) {
        if (size1 == null) {
            return size2;
        }
        if (size2 == null) {
            return size1;
        }

        Camera.Size min;
        Camera.Size max;

        if (size1.width > size2.width) {
            min = size2;
            max = size1;
        } else {
            min = size1;
            max = size2;
        }
        if (min.width >= expectSize) {
            return min;
        }
        return max;
    }

    private void releaseCamera(Camera camera) {
        if (camera != null) {
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
    }

    @Override
    public void close() throws IOException {
        mTextureSizeWidth = -1;
        mTextureSizeHeight = -1;
        releaseCamera(mCamera);
        mCamera = null;
    }

    @CheckResult
    private Camera openCamera(boolean faceFront) {
        CommonLog.d(TAG + " openCamera faceFront: " + faceFront);
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (!faceFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    CommonLog.d(TAG + " find facing back camera, try open " + i);
                    return Camera.open(i);
                } else if (faceFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    CommonLog.d(TAG + " find facing front camera, try open " + i);
                    return Camera.open(i);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

}
