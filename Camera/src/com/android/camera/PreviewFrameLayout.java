/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A layout which handles the preview aspect ratio and the position of
 * the gripper.
 */
public class PreviewFrameLayout extends ViewGroup {
    private static final int MIN_HORIZONTAL_MARGIN = 10; // 10dp
    private static final int CONTROL_BAR_WIDTH = 114; // 
    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged();
    }

    private double mAspectRatio = 4.0 / 3.0;
    private FrameLayout mFrame;
    private OnSizeChangedListener mSizeListener;
    private final DisplayMetrics mMetrics = new DisplayMetrics();

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ((Activity) context).getWindowManager()
                .getDefaultDisplay().getMetrics(mMetrics);
    }

    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mSizeListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        mFrame = (FrameLayout) findViewById(R.id.frame);
        if (mFrame == null) {
            throw new IllegalStateException(
                    "must provide child with id as \"frame\"");
        }
    }

    private boolean mFullScreenMode;
    public void setFullScreenMode(boolean isFullScrennMode){
        mFullScreenMode = isFullScrennMode;
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
        }
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int frameWidth = getWidth();
        int frameHeight = getHeight();

        FrameLayout f = mFrame;
        int horizontalPadding = f.getPaddingLeft() + f.getPaddingRight();
        int verticalPadding = f.getPaddingBottom() + f.getPaddingTop();
        int previewHeight = frameHeight - verticalPadding;
        int previewWidth = frameWidth - horizontalPadding;

        // resize frame and preview for aspect ratio
        if (previewWidth > previewHeight * mAspectRatio) {
            previewWidth = (int) (previewHeight * mAspectRatio + .5);
        } else {
            previewHeight = (int) (previewWidth / mAspectRatio + .5);
        }

        double w = r - l;
        double h = b - t;
        if(mFullScreenMode){
             double screenRatio = w / h;
             if(screenRatio < mAspectRatio){
                 w = h * mAspectRatio;
             }else{
                 h = w / mAspectRatio;
             }
             mFrame.measure(
                     MeasureSpec.makeMeasureSpec((int) w, MeasureSpec.EXACTLY),
                     MeasureSpec.makeMeasureSpec((int) h, MeasureSpec.EXACTLY));
             mFrame.layout(l, t, (int) (l + w), (int) (b + h));
             if (mSizeListener != null) {
                 mSizeListener.onSizeChanged();
             }
        }else{
            //fixed bug 16708
            int control_bar_width = CONTROL_BAR_WIDTH;
            if (mMetrics.density == 1.0) {
                float densityWidth = 627 * mMetrics.density / 1.5f;
                if (previewWidth > densityWidth) {
                    previewHeight = (int) (densityWidth / previewWidth
                            * previewHeight + .5);
                    previewWidth = (int) (densityWidth + .5);
                }
                control_bar_width = (int)(CONTROL_BAR_WIDTH * mMetrics.density / 1.5f);
            }
            frameWidth = previewWidth + horizontalPadding;
            frameHeight = previewHeight + verticalPadding;
            int hSpace = (int) ((w - frameWidth -control_bar_width ) / 2) ;
            int vSpace = (int) ((h - frameHeight) / 2);
            int left = l + hSpace;
            int top  = t + vSpace;
            int right = left + frameWidth;
            int bottom = top + frameHeight;
            mFrame.measure(
                    MeasureSpec.makeMeasureSpec(frameWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
            mFrame.layout(left, top, right, bottom);
            if (mSizeListener != null) {
                mSizeListener.onSizeChanged();
            }
        }

    }

    public float getDensity(){
    	return mMetrics.density;
    }
}

