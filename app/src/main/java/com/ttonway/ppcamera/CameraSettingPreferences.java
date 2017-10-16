package com.ttonway.ppcamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.support.annotation.NonNull;

/**
 * Created by desmond on 4/10/15.
 */
public class CameraSettingPreferences {

    private static final String FLASH_MODE = "ppcamera__flash_mode";
    private static final String SHOW_GRID = "ppcamera__show_grid";
    private static final String CAMERA_ID = "ppcamera__camera_id";

    private static SharedPreferences getCameraSettingPreferences(@NonNull final Context context) {
        return context.getSharedPreferences("com.ttonway.ppcamera", Context.MODE_PRIVATE);
    }

    protected static void saveCameraFlashMode(@NonNull final Context context, @NonNull final String cameraFlashMode) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(FLASH_MODE, cameraFlashMode);
            editor.apply();
        }
    }

    protected static String getCameraFlashMode(@NonNull final Context context) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            return preferences.getString(FLASH_MODE, Camera.Parameters.FLASH_MODE_AUTO);
        }

        return Camera.Parameters.FLASH_MODE_AUTO;
    }

    protected static void saveShowCameraGrid(@NonNull final Context context, @NonNull final boolean showGrid) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SHOW_GRID, showGrid);
            editor.apply();
        }
    }

    protected static boolean getShowCameraGrid(@NonNull final Context context) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            return preferences.getBoolean(SHOW_GRID, false);
        }

        return false;
    }

    protected static void saveCameraID(@NonNull final Context context, @NonNull final int cameraID) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(CAMERA_ID, cameraID);
            editor.apply();
        }
    }

    protected static int getCameraID(@NonNull final Context context) {
        final SharedPreferences preferences = getCameraSettingPreferences(context);

        if (preferences != null) {
            return preferences.getInt(CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK);
        }

        return Camera.CameraInfo.CAMERA_FACING_BACK;
    }
}
