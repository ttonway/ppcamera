package com.ttonway.ppcamera.utils;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtils {
    private static final String TAG = "IOUtils";

    public static void closeSlient(OutputStream os) {
        if (os != null) {
            try {
                os.close();
            } catch (IOException ignored) {
                Log.w(TAG, "close IOUtils fail.", ignored);
            }
        }
    }

    public static void closeSlient(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {
                Log.w(TAG, "close IOUtils fail.", ignored);
            }
        }
    }

    public static long getFileSize(File file)  {
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                size = fis.available();
            } catch (IOException ignored) {
            } finally {
                closeSlient(fis);
            }
        }
        return size;
    }

    public static boolean copyFile(File from, File to) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(from);
            os = new FileOutputStream(to);

            int b;
            while ((b = is.read()) != -1) {
                os.write(b);
            }
            os.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "copyFile fail.", e);
            return false;
        } finally {
            IOUtils.closeSlient(os);
            IOUtils.closeSlient(is);
        }
    }

    public static boolean saveBitmap(Bitmap bm, File file) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 0, out);
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "saveBitmap fail.", e);
            return false;
        } finally {
            closeSlient(out);
        }
    }
}
