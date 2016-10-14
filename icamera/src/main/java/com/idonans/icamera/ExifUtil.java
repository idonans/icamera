package com.idonans.icamera;

import com.idonans.acommon.lang.CommonLog;
import com.idonans.icamera.exif.ExifInterface;
import com.idonans.icamera.exif.ExifTag;

/**
 * Created by idonans on 2016/10/14.
 */
public class ExifUtil {

    private static final String TAG = "ExifUtil";

    /**
     * 获取图像的旋转角度，返回 0, 90, 180 or 270.
     */
    public static int getRotation(byte[] imageData) {
        if (imageData == null) {
            return 0;
        }

        try {
            ExifInterface exif = new ExifInterface();
            exif.readExif(imageData);
            ExifTag orientationTag = exif.getTag(ExifInterface.TAG_ORIENTATION);
            int orientationTagValue = orientationTag.getValueAsInt(0);
            int rotation = ExifInterface.getRotationForOrientationValue((short) orientationTagValue);
            CommonLog.d(TAG + " orientationTagValue: " + orientationTagValue + ", rotation: " + rotation);
            return rotation;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

}
