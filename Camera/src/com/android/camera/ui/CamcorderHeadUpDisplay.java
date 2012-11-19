/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;

import com.android.camera.CameraSettings;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

public class CamcorderHeadUpDisplay extends HeadUpDisplay {

    private static final String TAG = "CamcorderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private int mInitialOrientation;
    //MM09: fix the bug9213 in 2012.02.02. begin
    private ZoomIndicator mZoomIndicator;
    private Context  mContext;;
    private float[] mInitialZoomRatios;
    //MM09: fix the bug9213 in 2012.02.02.end

    public CamcorderHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    //MM09: fix the bug9213 in 2012.02.02.begin
//    public void initialize(Context context, PreferenceGroup group,
//            int initialOrientation) {
//        mInitialOrientation = initialOrientation;
//        super.initialize(context, group);
//    }

    public void initialize(Context context, PreferenceGroup group,
    		float[] initialZoomRatios, int initialOrientation) {
		mInitialZoomRatios = initialZoomRatios;
		mInitialOrientation = initialOrientation;
		super.initialize(context, group);
    }
    //MM09: fix the bug9213 in 2012.02.02. end

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference[] prefs = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_PREVIEW_SIZE,
                CameraSettings.Key_VIDEO_FORMAT);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
        addIndicator(context, group, CameraSettings.KEY_VIDEO_QUALITY);

      //MM09: fix the bug9213 in 2012.02.02. begin
        if (mInitialZoomRatios != null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mZoomIndicator.setZoomRatios(mInitialZoomRatios);
            mIndicatorBar.addComponent(mZoomIndicator);
        } else {
            mZoomIndicator = null;
        }
     //MM09: fix the bug9213 in 2012.02.02. end

        addIndicator(context, group, CameraSettings.KEY_CAMERA_ID);
        mIndicatorBar.setOrientation(mInitialOrientation);
    }

    //MM09: fix the bug9213 in 2012.02.02. begin
    public void setZoomListener(ZoomControllerListener listener) {
        // The rendering thread won't access listener variable, so we don't
        // need to do concurrency protection here
        mZoomIndicator.setZoomListener(listener);
    }

    public void setZoomIndex(int index) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                mZoomIndicator.setZoomIndex(index);
            }
        } else {
            mZoomIndicator.setZoomIndex(index);
        }
    }

    /**
     * Sets the zoom rations the camera driver provides. This methods must be
     * called before <code>setZoomListener()</code> and
     * <code>setZoomIndex()</code>
     */
    public void setZoomRatios(float[] zoomRatios) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized(root) {
                setZoomRatiosLocked(zoomRatios);
            }
        } else {
            setZoomRatiosLocked(zoomRatios);
        }
    }

    private void setZoomRatiosLocked(float[] zoomRatios) {
        mZoomIndicator.setZoomRatios(zoomRatios);
    }
    //MM09: fix the bug9213 in 2012.02.02. end
}
