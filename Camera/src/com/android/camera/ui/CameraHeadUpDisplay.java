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

import java.util.LinkedList;

import android.content.Context;

import com.android.camera.CameraSettings;
import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

public class CameraHeadUpDisplay extends HeadUpDisplay {

    private static final String TAG = "CamcoderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private GpsIndicator mGpsIndicator;
    private ZoomIndicator mZoomIndicator;
    private Context mContext;
    private float[] mInitialZoomRatios;
    private int mInitialOrientation;
    private boolean mIsImageCaptureIntent;

    public CameraHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    public void initialize(Context context, PreferenceGroup group,
            float[] initialZoomRatios, int initialOrientation) {
        mInitialZoomRatios = initialZoomRatios;
        mInitialOrientation = initialOrientation;
        super.initialize(context, group);
    }

    public void initialize(Context context, PreferenceGroup group,
            float[] initialZoomRatios, int initialOrientation,
            boolean isImageCaptureIntent) {
        mIsImageCaptureIntent = isImageCaptureIntent;
        initialize(context, group, initialZoomRatios, initialOrientation);
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        // fixed bug 15741 start
        LinkedList<String> keyList = new LinkedList<String>();
        keyList.add(CameraSettings.KEY_FOCUS_MODE);
        keyList.add(CameraSettings.KEY_EXPOSURE);
        keyList.add(CameraSettings.KEY_SCENE_MODE);
        keyList.add(CameraSettings.KEY_PREVIEW_SIZE);
        keyList.add(CameraSettings.Key_VIDEO_FORMAT);
        keyList.add(CameraSettings.KEY_PICTURE_SIZE);
        keyList.add(CameraSettings.KEY_JPEG_QUALITY);
        keyList.add(CameraSettings.KEY_BRIGHTNESS);
        keyList.add(CameraSettings.KEY_CONTRAST);
        keyList.add(CameraSettings.KEY_COLOR_EFFECT);
        keyList.add(CameraSettings.KEY_ANTIBANDING);
        if (!mIsImageCaptureIntent)
            keyList.add(CameraSettings.KEY_CONTINUOUS_CAPTURE);
        String[] keyArray = keyList.toArray(new String[] { });

        ListPreference prefs[] = getListPreferences(group, keyArray);
        // fixed bug 15741 end

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        boolean enableGps = true;
        enableGps =
            android.os.SystemProperties.getBoolean("ro.device.support.gps", true);
        if (enableGps) {
            mGpsIndicator = new GpsIndicator(
                    context, (IconListPreference)
                    group.findPreference(CameraSettings.KEY_RECORD_LOCATION));
            mIndicatorBar.addComponent(mGpsIndicator);
        }

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_FLASH_MODE);

        if (mInitialZoomRatios != null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mZoomIndicator.setZoomRatios(mInitialZoomRatios);
            mIndicatorBar.addComponent(mZoomIndicator);
        } else {
            mZoomIndicator = null;
        }

        addIndicator(context, group, CameraSettings.KEY_CAMERA_ID);

        mIndicatorBar.setOrientation(mInitialOrientation);
    }

    public void setZoomListener(ZoomControllerListener listener) {
        // The rendering thread won't access listener variable, so we don't
        // need to do concurrency protection here
        mZoomIndicator.setZoomListener(listener);
    }

    public void setZoomIndex(int index) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                // fixed bug 19943 start
//                mZoomIndicator.setZoomIndex(index);
                if (mZoomIndicator != null)
                    mZoomIndicator.setZoomIndex(index);
                // fixed bug 19943 end
            }
        } else {
            mZoomIndicator.setZoomIndex(index);
        }
    }

    public void setGpsHasSignal(final boolean hasSignal) {
        if (mGpsIndicator == null) return;
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                mGpsIndicator.setHasSignal(hasSignal);
            }
        } else {
            mGpsIndicator.setHasSignal(hasSignal);
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
}
