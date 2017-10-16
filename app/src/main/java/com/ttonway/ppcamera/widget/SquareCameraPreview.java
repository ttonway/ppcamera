package com.ttonway.ppcamera.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.widget.RelativeLayout;


import com.ttonway.ppcamera.utils.BitmapUtil;
import com.ttonway.ppcamera.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@TargetApi(14)
public class SquareCameraPreview extends SurfaceView {

    public static final String TAG = SquareCameraPreview.class.getSimpleName();
    private static final int INVALID_POINTER_ID = -1;

    private static final int ZOOM_OUT = 0;
    private static final int ZOOM_IN = 1;
    private static final int ZOOM_DELTA = 1;

    private static final int FOCUS_SQR_SIZE = 100;
    private static final int FOCUS_MAX_BOUND = 1000;
    private static final int FOCUS_MIN_BOUND = -FOCUS_MAX_BOUND;

    //    private static final double ASPECT_RATIO = 3.0 / 4.0;
    private Camera mCamera;

    private float mLastTouchX;
    private float mLastTouchY;

    // For scaling
    private int mMaxZoom;
    private boolean mIsZoomSupported;
    private int mActivePointerId = INVALID_POINTER_ID;
    private int mScaleFactor = 1;
    private ScaleGestureDetector mScaleDetector;

    // For focus
    private boolean mIsFocus;
    private boolean mIsFocusReady;
    private Camera.Area mFocusArea;
    private ArrayList<Camera.Area> mFocusAreas;
    private FocusRectangle mFocusRectangle;

    private Paint mPaint;

    private Rect mTouchRect;
    private boolean mMirror; // true if the camera is front-facing.
    private int mDisplayOrientation;
    private Matrix mMatrix;

    public SquareCameraPreview(Context context) {
        super(context);
        init(context);
    }

    public SquareCameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SquareCameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        if (Utils.hasIceCreamSandwich()) {
            mFocusArea = new Camera.Area(new Rect(), 1000);
            mFocusAreas = new ArrayList<Camera.Area>();
            mFocusAreas.add(mFocusArea);
        }

        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);
        mPaint.setStrokeWidth(10.0f);

        mMatrix = new Matrix();
        mTouchRect = new Rect();
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        setMatrix();
    }

    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        setMatrix();
    }

    public void setFocusView(FocusRectangle view) {
        this.mFocusRectangle = view;
    }

    /**
     * Measure the view and its content to determine the measured width and the
     * measured height
     */
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        int height = MeasureSpec.getSize(heightMeasureSpec);
//        int width = MeasureSpec.getSize(widthMeasureSpec);
//
//        final boolean isPortrait =
//                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
//
//        if (isPortrait) {
//            if (width > height * ASPECT_RATIO) {
//                width = (int) (height * ASPECT_RATIO + 0.5);
//            } else {
//                height = (int) (width / ASPECT_RATIO + 0.5);
//            }
//        } else {
//            if (height > width * ASPECT_RATIO) {
//                height = (int) (width * ASPECT_RATIO + 0.5);
//            } else {
//                width = (int) (height / ASPECT_RATIO + 0.5);
//            }
//        }
//
//        setMeasuredDimension(width, height);
//    }
    public int getViewWidth() {
        return getWidth();
    }

    public int getViewHeight() {
        return getHeight();
    }

    public void setCamera(Camera camera) {
        mCamera = camera;

        if (camera != null) {
            Camera.Parameters params = camera.getParameters();
            mIsZoomSupported = params.isZoomSupported();
            if (mIsZoomSupported) {
                mMaxZoom = params.getMaxZoom();
            }
        }

        setMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);

        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mIsFocus = true;

                mLastTouchX = event.getX();
                mLastTouchY = event.getY();

                mActivePointerId = event.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mIsFocus && mIsFocusReady) {
                    handleFocus(mCamera.getParameters());
                }
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                mCamera.cancelAutoFocus();
                mIsFocus = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }
        }

        return true;
    }

    private void handleZoom(Camera.Parameters params) {
        int zoom = params.getZoom();
        if (mScaleFactor == ZOOM_IN) {
            if (zoom < mMaxZoom) zoom += ZOOM_DELTA;
        } else if (mScaleFactor == ZOOM_OUT) {
            if (zoom > 0) zoom -= ZOOM_DELTA;
        }
        params.setZoom(zoom);
        mCamera.setParameters(params);
    }

    private void handleFocus(Camera.Parameters params) {
        float x = mLastTouchX;
        float y = mLastTouchY;

        if (!Utils.hasIceCreamSandwich()) return;
        if (!setFocusBound((int) x, (int) y)) return;

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null
                && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            Log.d(TAG, "focusAreas: " + mFocusAreas);
            params.setFocusAreas(mFocusAreas);
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            startFocusAnimation();
            mCamera.setParameters(params);
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    // Callback when the auto focus completes

                    if (mFocusRectangle != null) {
                        mFocusRectangle.clearAnimation();
                        if (success) {
                            mFocusRectangle.showSuccess();
                        } else {
                            mFocusRectangle.showFail();
                        }
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mFocusRectangle.setVisibility(View.GONE);
                            }
                        }, 800);
                    }
                }
            });
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setMatrix();
    }

    public void setIsFocusReady(final boolean isFocusReady) {
        mIsFocusReady = isFocusReady;
    }

    private boolean setFocusBound(int x, int y) {
        int areaWidth = FOCUS_SQR_SIZE;
        int areaHeight = FOCUS_SQR_SIZE;
        int left = BitmapUtil.clamp(x - areaWidth / 2, 0, getWidth() - areaWidth);
        int top = BitmapUtil.clamp(y - areaHeight / 2, 0, getHeight() - areaHeight);

        RectF rectF = new RectF(left, top, left + areaWidth, top + areaHeight);
        BitmapUtil.rectFToRect(rectF, mTouchRect);
        mMatrix.mapRect(rectF);
        BitmapUtil.rectFToRect(rectF, mFocusArea.rect);

        return true;
    }

    private void setMatrix() {
        int previewWidth = getWidth();
        int previewHeight = getHeight();
        if (previewWidth != 0 && previewHeight != 0) {
            Matrix matrix = new Matrix();
            prepareMatrix(matrix, mMirror, mDisplayOrientation,
                    previewWidth, previewHeight);
            // In face detection, the matrix converts the driver coordinates to UI
            // coordinates. In tap focus, the inverted matrix converts the UI
            // coordinates to driver coordinates.
            matrix.invert(mMatrix);
        }
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    private void startFocusAnimation() {
        if (mFocusRectangle == null) return;
        Rect rect = mTouchRect;
        mFocusRectangle.showStart();
        RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(mFocusRectangle.getLayoutParams());
        layout.width = rect.width();
        layout.height = rect.height();
        layout.setMargins(rect.left, rect.top, 0, 0);
        mFocusRectangle.setLayoutParams(layout);
        mFocusRectangle.setVisibility(View.VISIBLE);
        ScaleAnimation sa = new ScaleAnimation(3f, 1f, 3f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        sa.setDuration(800);
        mFocusRectangle.startAnimation(sa);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor = (int) detector.getScaleFactor();
            handleZoom(mCamera.getParameters());
            return true;
        }
    }
}
