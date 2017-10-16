package com.ttonway.ppcamera.widget;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;

/**
 * Created by ttonway on 16/4/21.
 */
public class OrientationImageButton extends ImageButton {

    float mOrientation;
    ValueAnimator mAnimator;

    public OrientationImageButton(Context context) {
        super(context);
    }

    public OrientationImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OrientationImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public OrientationImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOrientation(int orientation, boolean animated) {
        if (this.mOrientation != orientation) {

            if (animated) {
                if (mAnimator != null) {
                    mAnimator.cancel();
                }
                ValueAnimator animator = ValueAnimator.ofFloat(this.mOrientation, orientation);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mOrientation = (Float) animation.getAnimatedValue();
                        invalidate();
                    }
                });
                animator.setDuration(300).start();
                mAnimator = animator;
            } else {
                this.mOrientation = orientation;
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.rotate(-mOrientation, getWidth() / 2, getHeight() / 2);
        super.onDraw(canvas);
    }
}
