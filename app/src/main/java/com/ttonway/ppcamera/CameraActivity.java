package com.ttonway.ppcamera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.ttonway.ppcamera.gallery.IImage;
import com.ttonway.ppcamera.utils.BitmapUtil;
import com.ttonway.ppcamera.widget.CameraGrid;
import com.ttonway.ppcamera.widget.FocusRectangle;
import com.ttonway.ppcamera.widget.OrientationImageButton;
import com.ttonway.ppcamera.widget.SquareCameraPreview;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class CameraActivity extends Activity
        implements SurfaceHolder.Callback, Camera.PictureCallback, RadioGroup.OnCheckedChangeListener {

    public static final String TAG = CameraActivity.class.getSimpleName();
    public static final String CAMERA_ID_KEY = "camera_id";
    public static final String CAMERA_FLASH_KEY = "flash_mode";
    public static final String IMAGE_INFO = "image_info";

    private static final int PICTURE_SIZE_MAX_WIDTH = 1280;
    private static final int PREVIEW_SIZE_MAX_WIDTH = 640;

    private static final int RESTART_PREVIEW = 3;

    private int mCameraID;
    private String mFlashMode;
    private Camera mCamera;
    private SquareCameraPreview mPreviewView;
    private SurfaceHolder mSurfaceHolder;

    private boolean mIsSafeToTakePhoto = false;

    private ImageParameters mImageParameters;

    private CameraOrientationListener mOrientationListener;

    OrientationImageButton mFlashModeBtn;
    OrientationImageButton mSpaceBtn;
    OrientationImageButton mToggleGridBtn;
    OrientationImageButton mSwapCameraBtn;
    CameraGrid mGridView;
    RadioGroup mFlashModeGroup;
    FocusRectangle mFocusRectangle;
    View mOverlayView;
    TextView mTitleTextView;
    TextView mProjectTextView;
    TextView mPartTextView;
    TextView mDateTextView;
    OrientationImageButton mLastPictureButton;

    DateFormat mDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private long mCaptureStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    private long mJpegPictureCallbackTime;

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback = new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback = new RawPictureCallback();
    private final PreviewFrameCallback mPreviewFrameCallback = new PreviewFrameCallback();

    private ImageCapture mImageCapture = null;
    private ThumbnailController mThumbController;

    private final Handler mHandler = new MainHandler();

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESTART_PREVIEW: {
                    restartPreview();
                    break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mOrientationListener = new CameraOrientationListener(this);
        mImageCapture = new ImageCapture();

        if (savedInstanceState == null) {
            mCameraID = CameraSettingPreferences.getCameraID(this);
            mFlashMode = CameraSettingPreferences.getCameraFlashMode(this);
            mImageParameters = new ImageParameters();
        } else {
            mCameraID = savedInstanceState.getInt(CAMERA_ID_KEY);
            mFlashMode = savedInstanceState.getString(CAMERA_FLASH_KEY);
            mImageParameters = savedInstanceState.getParcelable(IMAGE_INFO);
        }

        mPreviewView = (SquareCameraPreview) findViewById(R.id.camera_preview);
        mFocusRectangle = (FocusRectangle) findViewById(R.id.focus_rectangle);
        mOverlayView = findViewById(R.id.overlay_view);
        mTitleTextView = (TextView) mOverlayView.findViewById(R.id.text_title);
        mProjectTextView = (TextView) mOverlayView.findViewById(R.id.text_project);
        mPartTextView = (TextView) mOverlayView.findViewById(R.id.text_part);
        mDateTextView = (TextView) mOverlayView.findViewById(R.id.text_date);
        mOverlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
            }
        });
        mPreviewView.getHolder().addCallback(CameraActivity.this);
        mPreviewView.setFocusView(mFocusRectangle);

        mSwapCameraBtn = (OrientationImageButton) findViewById(R.id.btn_camera_face);
        mSwapCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCameraID = getBackCameraID();
                } else {
                    mCameraID = getFrontCameraID();
                }
                restartPreview();
            }
        });

        mGridView = (CameraGrid) findViewById(R.id.grid_view);
        mGridView.setShowGrid(CameraSettingPreferences.getShowCameraGrid(this));
        mToggleGridBtn = (OrientationImageButton) findViewById(R.id.btn_grid);
        mToggleGridBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGridView.setShowGrid(!mGridView.isShowGrid());
            }
        });

        mFlashModeGroup = (RadioGroup) findViewById(R.id.flash_mode_group);
        mFlashModeGroup.check(Camera.Parameters.FLASH_MODE_AUTO.equalsIgnoreCase(mFlashMode) ? R.id.mode_auto :
                (Camera.Parameters.FLASH_MODE_ON.equalsIgnoreCase(mFlashMode) ? R.id.mode_on : R.id.mode_off));
        mFlashModeGroup.setOnCheckedChangeListener(this);
        mSpaceBtn = (OrientationImageButton) findViewById(R.id.btn_space);
        mFlashModeBtn = (OrientationImageButton) findViewById(R.id.btn_flash);
        setupFlashMode();
        mFlashModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                showFlashModeMenu(!mFlashModeBtn.isSelected());
            }
        });

        final Button takePhotoBtn = (Button) findViewById(R.id.btn_capture_image);
        takePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDateTextView.setText(mDateFormat.format(new Date()));
                takePicture();
            }
        });

        mLastPictureButton = (OrientationImageButton) findViewById(R.id.btn_gallery);
        mLastPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGallery();
            }
        });
        mThumbController = new ThumbnailController(
                getResources(), mLastPictureButton, getContentResolver());
        //mThumbController.loadData(ImageManager.getLastImageThumbPath());
        String lastPhotoThumbPath = getLastPhotoThumbPath();
        if (lastPhotoThumbPath != null) {
            mThumbController.loadData(lastPhotoThumbPath);
        }
        // Update last image thumbnail.
        updateThumbnailButton();
    }

    private String getLastPhotoThumbPath() {
        return ImageManager.getLastImageThumbPath();
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        if (!mThumbController.isUriValid()) {
            updateLastImage();
        }
        mThumbController.updateDisplayIfNeeded(500);
    }

    private void updateLastImage() {
        final String lastPhotoThumbPath = getLastPhotoThumbPath();
        if (lastPhotoThumbPath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(lastPhotoThumbPath);
            mThumbController.setData(Uri.fromFile(new File(lastPhotoThumbPath)), bitmap);
        } else {
            mThumbController.setData(null, null);
        }

       /* IImageList list = ImageManager.makeImageList(
            mContentResolver,
            dataLocation(),
            ImageManager.INCLUDE_IMAGES,
            ImageManager.SORT_ASCENDING,
            ImageManager.CAMERA_IMAGE_BUCKET_ID);
        int count = list.getCount();
        if (count > 0) {
            IImage image = list.getImageAt(count - 1);
            Uri uri = image.fullSizeImageUri();
            mThumbController.setData(uri, image.miniThumbBitmap());
        } else {
            mThumbController.setData(null, null);
        }
        list.close();*/
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        String newMode = Camera.Parameters.FLASH_MODE_AUTO;
        switch (checkedId) {
            case R.id.mode_auto:
                newMode = Camera.Parameters.FLASH_MODE_AUTO;
                break;
            case R.id.mode_on:
                newMode = Camera.Parameters.FLASH_MODE_ON;
                break;
            case R.id.mode_off:
                newMode = Camera.Parameters.FLASH_MODE_OFF;
                break;
        }
        mFlashMode = newMode;
        setupFlashMode();
        setupCamera();
    }

    private void setupFlashMode() {
        if (Camera.Parameters.FLASH_MODE_AUTO.equalsIgnoreCase(mFlashMode)) {
            mFlashModeBtn.setImageResource(R.drawable.ic_flash_auto);
        } else if (Camera.Parameters.FLASH_MODE_ON.equalsIgnoreCase(mFlashMode)) {
            mFlashModeBtn.setImageResource(R.drawable.ic_flash_on);
        } else if (Camera.Parameters.FLASH_MODE_OFF.equalsIgnoreCase(mFlashMode)) {
            mFlashModeBtn.setImageResource(R.drawable.ic_flash_off);
        }
    }

    void showFlashModeMenu(boolean show) {
        // save show status in mFlashModeBtn.selected
        if (show == mFlashModeBtn.isSelected()) {
            return;
        }
        mFlashModeBtn.setSelected(show);

        ObjectAnimator animator;
        if (show) {

            animator = ObjectAnimator.ofFloat(mFlashModeGroup, "alpha", 0f, 1f);
            animator.addListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationStart(Animator animation) {
                    mSpaceBtn.setVisibility(View.GONE);
                    mToggleGridBtn.setVisibility(View.GONE);
                    mSwapCameraBtn.setVisibility(View.GONE);
                    mFlashModeGroup.setVisibility(View.VISIBLE);
                }
            });
        } else {

            animator = ObjectAnimator.ofFloat(mFlashModeGroup, "alpha", 1f, 0f);
            animator.addListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {
                    mSpaceBtn.setVisibility(View.VISIBLE);
                    mToggleGridBtn.setVisibility(View.VISIBLE);
                    mSwapCameraBtn.setVisibility(View.VISIBLE);
                    mFlashModeGroup.setVisibility(View.GONE);
                }
            });
        }
        animator.setDuration(300);
        animator.start();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
//        Log.d(TAG, "onSaveInstanceState");
        outState.putInt(CAMERA_ID_KEY, mCameraID);
        outState.putString(CAMERA_FLASH_KEY, mFlashMode);
        outState.putParcelable(IMAGE_INFO, mImageParameters);
        super.onSaveInstanceState(outState);
    }

    private void getCamera(int cameraID) {
        try {
            mCamera = Camera.open(cameraID);
            mPreviewView.setCamera(mCamera);
        } catch (Exception e) {
            Log.d(TAG, "Can't open camera with id " + cameraID, e);
            e.printStackTrace();
        }
    }

    /**
     * Restart the camera preview
     */
    private void restartPreview() {
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
        }

        getCamera(mCameraID);
        startCameraPreview();
    }

    /**
     * Start the camera preview
     */
    private void startCameraPreview() {
        determineDisplayOrientation();
        setupCamera();

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();

            setSafeToTakePhoto(true);
            setCameraFocusReady(true);
        } catch (IOException e) {
            Log.d(TAG, "Can't start camera preview due to IOException " + e);
            e.printStackTrace();
        }
    }

    /**
     * Stop the camera preview
     */
    private void stopCameraPreview() {
        setSafeToTakePhoto(false);
        setCameraFocusReady(false);

        // Nulls out callbacks, stops face detection
        mCamera.stopPreview();
        mPreviewView.setCamera(null);
    }

    private void setSafeToTakePhoto(final boolean isSafeToTakePhoto) {
        mIsSafeToTakePhoto = isSafeToTakePhoto;
    }

    private void setCameraFocusReady(final boolean isFocusReady) {
        if (this.mPreviewView != null) {
            mPreviewView.setIsFocusReady(isFocusReady);
        }
    }

    /**
     * Determine the current display orientation and rotate the camera preview
     * accordingly
     */
    private void determineDisplayOrientation() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, cameraInfo);

        // Clockwise rotation needed to align the window display to the natural position
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: {
                degrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                degrees = 90;
                break;
            }
            case Surface.ROTATION_180: {
                degrees = 180;
                break;
            }
            case Surface.ROTATION_270: {
                degrees = 270;
                break;
            }
        }

        int displayOrientation;

        // CameraInfo.Orientation is the angle relative to the natural position of the device
        // in clockwise rotation (angle that is rotated clockwise from the natural position)
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            // Orientation is angle of rotation when facing the camera for
            // the camera image to match the natural orientation of the device
            displayOrientation = (cameraInfo.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        }

        mImageParameters.mDisplayOrientation = displayOrientation;
        mImageParameters.mLayoutOrientation = degrees;

        mCamera.setDisplayOrientation(mImageParameters.mDisplayOrientation);
        mPreviewView.setMirror(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        mPreviewView.setDisplayOrientation(mImageParameters.mDisplayOrientation);
    }

    /**
     * Setup the camera parameters
     */
    private void setupCamera() {
        // Never keep a global parameters
        Camera.Parameters parameters = mCamera.getParameters();

        Camera.Size bestPreviewSize = determineBestPreviewSize(parameters);
        Camera.Size bestPictureSize = determineBestPictureSize(parameters);

        parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
        parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);


        // Set continuous picture focus, if it's supported
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        List<String> flashModes = parameters.getSupportedFlashModes();
        showFlashModeMenu(false);
        if (flashModes != null && flashModes.contains(mFlashMode)) {
            parameters.setFlashMode(mFlashMode);
            mFlashModeBtn.setVisibility(View.VISIBLE);
        } else {
            mFlashModeBtn.setVisibility(View.INVISIBLE);
        }

        // Lock in the changes
        mCamera.setParameters(parameters);
    }

    private Camera.Size determineBestPreviewSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPreviewSizes(), PREVIEW_SIZE_MAX_WIDTH);
    }

    private Camera.Size determineBestPictureSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPictureSizes(), PICTURE_SIZE_MAX_WIDTH);
    }

    private Camera.Size determineBestSize(List<Camera.Size> sizes, int widthThreshold) {
        Camera.Size bestSize = null;
        Camera.Size size;
        int numOfSizes = sizes.size();
        for (int i = 0; i < numOfSizes; i++) {
            size = sizes.get(i);
            boolean isDesireRatio = (size.width / 4) == (size.height / 3);
            boolean isBetterSize = (bestSize == null) || size.width > bestSize.width;

            if (isDesireRatio && isBetterSize) {
                bestSize = size;
            }
        }

        if (bestSize == null) {
            Log.d(TAG, "cannot find the best camera size");
            return sizes.get(sizes.size() - 1);
        }

        return bestSize;
    }

    private int getFrontCameraID() {
        PackageManager pm = getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

        return getBackCameraID();
    }

    private int getBackCameraID() {
        return Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    private boolean isFrontFacing(int cameraID) {
        PackageManager pm = getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return cameraID == Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        return false;
    }

    private void startGallery() {
        if (mThumbController.isUriValid()) {
            // Open in the gallery
            Intent intent = new Intent(BitmapUtil.REVIEW_ACTION, mThumbController.getUri());
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                try {
                    intent = new Intent(Intent.ACTION_VIEW, mThumbController.getUri());
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "review image fail", e);
                }
            }
        } else {
            Log.e(TAG, "Can't view last image.");
        }
    }

    /**
     * Take a picture
     */
    private void takePicture() {

        if (mIsSafeToTakePhoto) {
            setSafeToTakePhoto(false);

            mImageCapture.onSnap();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mImageParameters.mIsPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mOrientationListener.enable();

        if (mCamera == null) {
            restartPreview();
        }


        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mTitleTextView.setText(sp.getString("overlay.title", ""));
        mProjectTextView.setText(sp.getString("overlay.project", ""));
        mPartTextView.setText(sp.getString("overlay.partment", ""));
        mDateTextView.setText(mDateFormat.format(new Date()));
    }

    @Override
    public void onStop() {
        mOrientationListener.disable();

        // stop the preview
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
        }

        CameraSettingPreferences.saveCameraFlashMode(this, mFlashMode);
        CameraSettingPreferences.saveShowCameraGrid(this, mGridView.isShowGrid());
        CameraSettingPreferences.saveCameraID(this, mCameraID);

        super.onStop();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;

        getCamera(mCameraID);
        startCameraPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // The surface is destroyed with the visibility of the SurfaceView is set to View.Invisible
    }

    /**
     * A picture has been taken
     *
     * @param data
     * @param camera
     */
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        int rotation = getPhotoRotation();
//        Log.d(TAG, "normal orientation: " + orientation);
//        Log.d(TAG, "Rotate Picture by: " + rotation);

        mJpegPictureCallbackTime = System.currentTimeMillis();

        // If postview callback has arrived, the captured image is displayed
        // in postview callback. If not, the captured image is displayed in
        // raw picture callback.
        if (mPostViewPictureCallbackTime != 0) {
            mShutterToPictureDisplayedTime =
                    mPostViewPictureCallbackTime - mShutterCallbackTime;
            mPictureDisplayedToJpegCallbackTime =
                    mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
        } else {
            mShutterToPictureDisplayedTime =
                    mRawPictureCallbackTime - mShutterCallbackTime;
            mPictureDisplayedToJpegCallbackTime =
                    mJpegPictureCallbackTime - mRawPictureCallbackTime;
        }
        Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                + mPictureDisplayedToJpegCallbackTime + "ms");

        // We want to show the taken picture for a while, so we wait
        // for at least 1.2 second before restarting the preview.
        long delay = (isFrontFacing(mCameraID) ? 1200 : 400) - mPictureDisplayedToJpegCallbackTime;
        if (delay < 0) {
            restartPreview();
        } else {
            mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, delay);
        }

        Bitmap bitmap = BitmapUtil.makeBitmap(data, IImage.UNCONSTRAINED);
        if (rotation != 0) {
            bitmap = BitmapUtil.rotate(bitmap, rotation);
        }
        mOverlayView.setDrawingCacheEnabled(true);
        Bitmap overlay = mOverlayView.getDrawingCache();
        Bitmap newBitmap = bitmap;
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        canvas.drawBitmap(overlay, 0, bitmap.getHeight() - overlay.getHeight(), paint);
        canvas.save(Canvas.ALL_SAVE_FLAG);
        canvas.restore();
        mOverlayView.setDrawingCacheEnabled(false);
        overlay.recycle();
        overlay = null;
        mImageCapture.storeImage(newBitmap, camera);
        bitmap.recycle();
        bitmap = null;

        if (mAnimationDone) {
            Log.d(TAG, "BUG: updating after capture");
            mThumbController.updateDisplayIfNeeded(0);
        }

        setSafeToTakePhoto(true);
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        return "IMG_" + sp.getString("overlay.project", "") + "_" + dateFormat.format(date);
    }

    private int getPhotoRotation() {
        int rotation;
        int orientation = mOrientationListener.getRememberedNormalOrientation();
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, info);

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {
            rotation = (info.orientation + orientation) % 360;
        }

        return rotation;
    }

    public boolean isPreviewAnimationDisable() {
        return false;
    }


    private boolean mDoAnimation = false;
    private boolean mAnimationDone = false;

    private void animatePreviewToThumb(byte[] data) {
        if (!mDoAnimation || mLastPictureButton == null || data == null) {
            return;
        }

        mAnimationDone = false;

        //mPreviewFrameData = new byte[data.length];
        //System.arraycopy(data, 0, mPreviewFrameData, 0, data.length);
        //Log.d(TAG, "data="+mPreviewFrameData);
        Camera.Parameters parameters = mCamera.getParameters();
        int width = parameters.getPreviewSize().width;
        int height = parameters.getPreviewSize().height;
        int[] pixels = new int[width * height];
        BitmapUtil.decodeYUV(pixels, data, width, height);
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, info);
        final int cameraRotation = info.orientation;
        int screenRotation;
        int orientation = mOrientationListener.getRememberedNormalOrientation();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            screenRotation = (-orientation + 360) % 360;
        } else {
            screenRotation = orientation % 360;
        }
        if (cameraRotation != 0) {
            bitmap = BitmapUtil.rotate(bitmap, cameraRotation);
        }
        if (isFrontFacing(mCameraID)) {
            bitmap = BitmapUtil.flipHorizontally(bitmap);
            screenRotation = (-screenRotation + 360) % 360;
        }
        final int rotation = screenRotation;

        final ImageView previewImage = (ImageView) findViewById(R.id.imageViewPreview);

        //if (!CameraHolder.instance().isFrontFacing(mCameraId)) {
        //Disable animation for front facing camera for now,
        //as we have an orientation issue. Should be fixed when using preview frame for bitmap
        previewImage.setImageBitmap(bitmap);
        //}

        int[] origin = new int[2];
        previewImage.getLocationInWindow(origin);
        int[] destination = new int[2];
        mLastPictureButton.getLocationInWindow(destination);

        ScaleAnimation scaleAnim = new ScaleAnimation(1f,
                (float) (mLastPictureButton.getWidth() + 9) / previewImage.getWidth(),
                1f,
                (float) (mLastPictureButton.getHeight() - 9) / previewImage.getHeight(),
                ScaleAnimation.ABSOLUTE,
                destination[0] + 12,
                ScaleAnimation.ABSOLUTE,
                destination[1] + 12);
        scaleAnim.setDuration(500);
        scaleAnim.setStartOffset(0);
        scaleAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Drawable drawable = previewImage.getDrawable();
                if (drawable != null) {
                    Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    mThumbController.updateThumb(bitmap, rotation, false);
                }
            }
        });
        scaleAnim.setInterpolator(new DecelerateInterpolator(2.0f));

        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
        alphaAnimation.setDuration(1100);
        alphaAnimation.setStartOffset(500);

        AnimationSet animation = new AnimationSet(true);
        animation.addAnimation(scaleAnim);
        animation.addAnimation(alphaAnimation);

        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mAnimationDone = true;
                previewImage.setVisibility(View.INVISIBLE);
                Drawable drawable = previewImage.getDrawable();
                if (drawable != null) {
                    Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    previewImage.setImageBitmap(null);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }
        });

        previewImage.setVisibility(View.VISIBLE);
        previewImage.startAnimation(animation);

        mDoAnimation = false;
    }

    /**
     * When orientation changes, onOrientationChanged(int) of the listener will be called
     */
    private class CameraOrientationListener extends OrientationEventListener {

        private int mCurrentNormalizedOrientation;
        private int mRememberedNormalOrientation;

        public CameraOrientationListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation != ORIENTATION_UNKNOWN) {
                mCurrentNormalizedOrientation = normalize(orientation);

                mFlashModeBtn.setOrientation(mCurrentNormalizedOrientation, true);
                mSpaceBtn.setOrientation(mCurrentNormalizedOrientation, true);
                mToggleGridBtn.setOrientation(mCurrentNormalizedOrientation, true);
                mSwapCameraBtn.setOrientation(mCurrentNormalizedOrientation, true);
                mLastPictureButton.setOrientation(mCurrentNormalizedOrientation, true);
            }
        }

        /**
         * @param degrees Amount of clockwise rotation from the device's natural position
         * @return Normalized degrees to just 0, 90, 180, 270
         */
        private int normalize(int degrees) {
            if (degrees > 315 || degrees <= 45) {
                return 0;
            }

            if (degrees > 45 && degrees <= 135) {
                return 90;
            }

            if (degrees > 135 && degrees <= 225) {
                return 180;
            }

            if (degrees > 225 && degrees <= 315) {
                return 270;
            }

            throw new RuntimeException("The physics as we know them are no more. Watch out for anomalies.");
        }

        public void rememberOrientation() {
            mRememberedNormalOrientation = mCurrentNormalizedOrientation;
        }

        public int getRememberedNormalOrientation() {
            rememberOrientation();
            return mRememberedNormalOrientation;
        }
    }

    private final class PreviewFrameCallback implements Camera.PreviewCallback {
        public PreviewFrameCallback() {
        }

        @Override
        public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
            Log.d(TAG, "onPreviewFrame");

            mImageCapture.capture();
            if (!isPreviewAnimationDisable()) {
                animatePreviewToThumb(data);
            }
        }

    }

    private final class ShutterCallback
            implements android.hardware.Camera.ShutterCallback {
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
        }
    }

    private final class PostViewPictureCallback implements Camera.PictureCallback {
        public void onPictureTaken(
                byte[] data, android.hardware.Camera camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements Camera.PictureCallback {
        public void onPictureTaken(
                byte[] rawData, android.hardware.Camera camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private class ImageCapture {

        private Uri mLastContentUri;


        // Returns the rotation degree in the jpeg header.
        private int storeImage(Bitmap bitmap) {
            try {
                long dateTaken = System.currentTimeMillis();
                String title = createName(dateTaken);
                String filename = title + ".jpg";
                int[] degree = new int[1];
                mLastContentUri = ImageManager.addImage(
                        getContentResolver(),
                        title,
                        dateTaken,
                        null, // location from gps/network
                        ImageManager.CAMERA_IMAGE_BUCKET_NAME, filename,
                        bitmap, null,
                        degree);
                return degree[0];
            } catch (Exception ex) {
                Log.e(TAG, "Exception while compressing image.", ex);
                return 0;
            }
        }

        public int storeImage(final Bitmap bitmap,
                              android.hardware.Camera camera) {
            storeImage(bitmap);
            sendBroadcast(new Intent(
                    "com.android.camera.NEW_PICTURE", mLastContentUri));
            if (isPreviewAnimationDisable()) {
                mThumbController.setData(mImageCapture.getLastCaptureUri(), bitmap);
                mThumbController.updateDisplayIfNeeded(500);
            } else {
                mThumbController.setUri(mImageCapture.getLastCaptureUri());
            }
            return 0;
        }

        /**
         * Initiate the capture of an image.
         */
        public void initiate() {
            if (mCamera == null) {
                return;
            }

            mDoAnimation = true;
            mCamera.setOneShotPreviewCallback(mPreviewFrameCallback);
        }

        private void capture() {
            mOrientationListener.rememberOrientation();

            // jpeg callback occurs when the compressed image is available
            mCamera.takePicture(mShutterCallback, mRawPictureCallback, mPostViewPictureCallback, CameraActivity.this);
        }

        public void onSnap() {
            mCaptureStartTime = System.currentTimeMillis();
            mPostViewPictureCallbackTime = 0;

            mImageCapture.initiate();
        }

        public Uri getLastCaptureUri() {
            return mLastContentUri;
        }
    }
}
