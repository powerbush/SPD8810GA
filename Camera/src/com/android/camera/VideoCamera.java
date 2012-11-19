/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera;

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.ui.CamcorderHeadUpDisplay;
import com.android.camera.ui.GLRootView;
import com.android.camera.ui.HeadUpDisplay;
import com.android.camera.ui.RotateRecordingTime;
import com.android.camera.ui.ZoomControllerListener;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.MediaStore.Video;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout.LayoutParams;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The Camcorder activity.
 */
public class VideoCamera extends NoSearchActivity
        implements View.OnClickListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        Switcher.OnSwitchListener, PreviewFrameLayout.OnSizeChangedListener {

    private static final String TAG = "VideoCamera";

    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final long NO_STORAGE_ERROR = -1L;
    private static final long CANNOT_STAT_ERROR = -2L;
    private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;

    private static final int STORAGE_STATUS_OK = 0;
    private static final int STORAGE_STATUS_LOW = 1;
    private static final int STORAGE_STATUS_NONE = 2;
    private static final int STORAGE_STATUS_FAIL = 3;
    private static final int STORAGE_STATUS_SPACE_FULL = 4;
    private static final int UPDATE_PREVIEW_STATUS = 10;
    private static final int UPDATE_LAST_IMAGE = 9;
    private static final int UPDATE_THUMBNAIL_BUTTON = 8;
    private static final int START_PREVIEW_SWITCH_CAMERA = 11;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms
    private static final int UPDATE_PARAM_ZOOM = 2;
    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private final static String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    private ComboPreferences mPreferences;

    private PreviewFrameLayout mPreviewFrameLayout;
    private SurfaceView mVideoPreview;
    private SurfaceHolder mSurfaceHolder = null;
    private ImageView mVideoFrame;
    private GLRootView mGLRootView;
    private CamcorderHeadUpDisplay mHeadUpDisplay;
    private static final int ZOOM_STOPPED = 0;
    private static final int ZOOM_START = 1;
    private static final int ZOOM_STOPPING = 2;
    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;
    // mLastPictureButton and mThumbController
    // are non-null only if mIsVideoCaptureIntent is true.
    private ImageView mLastPictureButton;
    private ImageButton mPauseButton;
    private ThumbnailController mThumbController;
    private boolean mStartPreviewFail = false;

    private int mStorageStatus = STORAGE_STATUS_OK;

    private MediaRecorder mMediaRecorder;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;
    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    boolean mPausing = false;
    boolean mPreviewing = false; // True if preview is started.

    private ContentResolver mContentResolver;

    private ShutterButton mShutterButton;
    private RotateRecordingTime mRecordingTimeRect;
    private TextView mRecordingTimeView;
    private Switcher mSwitcher;
    private boolean mRecordingTimeCountsDown = false;

    private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private final Handler mHandler = new MainHandler();
    private Parameters mParameters;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId;

    private MyOrientationEventListener mOrientationListener;
    // The device orientation in degrees. Default is unknown.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails. Degrees are in
    // counter-clockwise
    private int mOrientationCompensation = 0;
    private int mOrientationHint; // the orientation hint for video playback
    private long mResume;

    //MM09: fix the bug9213 in 2012.02.02. begin
    private int mZoomMax; //TODO
    private boolean mSmoothZoomSupported = false;
    private final ZoomListener mZoomListener = new ZoomListener();
    private int mTargetZoomValue;
    private int mZoomState = ZOOM_STOPPED;
    private int mZoomValue;  // The current zoom value.
    //MM09: fix the bug9213 in 2012.02.02. end

    private boolean mPreviewStatus = false;
    private View pauseView;
    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case ENABLE_SHUTTER_BUTTON:
                    mShutterButton.setEnabled(true);
                    break;

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }

                case UPDATE_PREVIEW_STATUS:{
                    mPreviewStatus = true;
                    return;
                }

                case UPDATE_LAST_IMAGE:{
                    Log.d(TAG, "sendLastImageUpdatedMessage received");
                    Bitmap bitmap = (Bitmap) msg.obj;
                    Uri uri = null;
                    String uriStr = msg.getData().getString("uri");

                    if(uriStr != null){
                        uri = Uri.parse(uriStr);
                    }

                    if(mPausing){
                        if(bitmap != null){
                            bitmap.recycle();
                            bitmap = null;
                        }
                        return;
                    }

                    mThumbController.setData(uri, bitmap);
                    mThumbController.updateDisplayIfNeeded();
                    break;
                }

                case UPDATE_THUMBNAIL_BUTTON:{
                    Log.v(TAG, "UPDATE_THUMBNAIL_BUTTON");
                    updateThumbnailButton();
                }

                case START_PREVIEW_SWITCH_CAMERA :
                    mTrySwitchCamera = false;
                    switchCameraId(mCameraId == 0 ? 1 : 0);
                    break;

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                updateAndShowStorageHint(false);
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint(true);
                updateThumbnailButton();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                // handled in ACTION_MEDIA_EJECT
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(VideoCamera.this,
                        getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateAndShowStorageHint(true);
                updateThumbnailButton();
                //fixed bug 15555
                if(mIsVideoCaptureIntent) return;
                if(mThumbController == null) return;
                if(mThumbController.getUri() == null){
                    mHandler.sendEmptyMessageDelayed(UPDATE_THUMBNAIL_BUTTON, 60000);
                }
            }
        }
    }

//*mm09: update in 2011.12.30 *****/
    private boolean mLockStatus = true;
    private void lock() {
        if (mLockStatus) return;
        Log.v(TAG, "lock");
        mCameraDevice.lock();
        mLockStatus = true;
    }

    private void unlock() {
        if (!mLockStatus) return;
        Log.v(TAG, "unlock");
        mCameraDevice.unlock();
        mLockStatus = false;
    }
//*****mm09: update in 2011.12.30 */

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    private void showCameraErrorAndFinish() {
        Resources ress = getResources();
        Util.showFatalErrorAndFinish(VideoCamera.this,
                ress.getString(R.string.camera_error_title),
                ress.getString(R.string.cannot_connect_camera));
    }

    private boolean restartPreview() {
        try {
            startPreview();
        } catch (CameraHardwareException e) {
            mStartPreviewFail = true;
            showCameraErrorAndFinish();
            return false;
        }
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.v(TAG, "onCreate");
        Window win = getWindow();

        // Overright the brightness settings if it is automatic
        int mode = Settings.System.getInt(
                getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
            win.setAttributes(winParams);
        }

        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();

        readVideoPreferences();

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run() {
                try {
                    mStartPreviewFail = false;
                    mTrySwitchCameraState = TRY_SWITCH_CAMERA_CREATE;
                    startPreview();
                } catch (CameraHardwareException e) {
                    // In eng build, we throw the exception so that test tool
                    // can detect it and report it
                    if ("eng".equals(Build.TYPE)) {
                        throw new RuntimeException(e);
                    }
                    mStartPreviewFail = true;
                }
            }
        });
        startPreviewThread.start();

        mContentResolver = getContentResolver();

        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.video_camera);

        mPreviewFrameLayout = (PreviewFrameLayout)
                findViewById(R.id.frame_layout);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        resizeForPreviewAspectRatio();

        mVideoPreview = (SurfaceView) findViewById(R.id.camera_preview);
        mVideoFrame = (ImageView) findViewById(R.id.video_frame);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mVideoPreview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mIsVideoCaptureIntent = isVideoCaptureIntent();
        mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateRecordingTime) findViewById(R.id.recording_time_rect);

        ViewGroup rootView = (ViewGroup) findViewById(R.id.video_camera);
        LayoutInflater inflater = this.getLayoutInflater();
        View controlBar = null;
        if (!mIsVideoCaptureIntent) {
              controlBar = inflater.inflate(
                    R.layout.camera_control, rootView);
            mLastPictureButton =
                    (ImageView) controlBar.findViewById(R.id.review_thumbnail);
            mThumbController = new ThumbnailController(
                    getResources(), mLastPictureButton, mContentResolver);
            mLastPictureButton.setOnClickListener(this);
            mThumbController.loadData(ImageManager.getLastVideoThumbPath());
            mSwitcher = ((Switcher) findViewById(R.id.camera_switch));
            mSwitcher.setOnSwitchListener(this);
            mSwitcher.addTouchView(findViewById(R.id.camera_switch_set));
        } else {
            controlBar = inflater.inflate(
                    R.layout.attach_camera_control, rootView);
            controlBar.findViewById(R.id.btn_cancel).setOnClickListener(this);
            ImageView retake =
                    (ImageView) controlBar.findViewById(R.id.btn_retake);
            retake.setOnClickListener(this);
            retake.setImageResource(R.drawable.btn_ic_review_retake_video);
            controlBar.findViewById(R.id.btn_play).setOnClickListener(this);
            controlBar.findViewById(R.id.btn_done).setOnClickListener(this);
        }

        initPauseView(inflater, rootView);
        hidePauseView();

        mPauseButton = (ImageButton) controlBar.findViewById(R.id.pause_button);
        mPauseButton.setOnClickListener(this);

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setImageResource(R.drawable.btn_ic_video_record);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.requestFocus();

        mOrientationListener = new MyOrientationEventListener(VideoCamera.this);

        // Make sure preview is started.
        try {
            startPreviewThread.join();
            if (mStartPreviewFail) {
                showCameraErrorAndFinish();
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        // add validate
        if (isTvRunning) return;

        initializeZoom();
        // Initialize the HeadUpDiplay after startPreview(). We need mParameters
        // for HeadUpDisplay and it is initialized in that function.
        mHeadUpDisplay = new CamcorderHeadUpDisplay(this);
        mHeadUpDisplay.setListener(new MyHeadUpDisplayListener());
        initializeHeadUpDisplay();
    }

    private void initPauseView(LayoutInflater inflater, ViewGroup rootView) {
        pauseView = inflater.inflate(R.layout.pause_view, null);
        rootView.addView(pauseView);
    }

    private void hidePauseView(){
        pauseView.setVisibility(View.GONE);
    }

    private void showPauseView(){
        pauseView.setVisibility(View.VISIBLE);
    }

    private void updatePauseRecordingIndicator(boolean isPausing){
          if(mPauseButton == null){
                return;
          }

          if(isPausing){
                mPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.btn_ic_video_continue));
                return;
          }

          mPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.pause_button));

    }

    private void changeHeadUpDisplayState() {
        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && !mPausing && mGLRootView == null) {
            attachHeadUpDisplay();
        } else if (mGLRootView != null) {
            detachHeadUpDisplay();
        }
    }

    private void initializeHeadUpDisplay() {
        // fixed bug 16206, 15800 start
        if (mCameraDevice == null) return;
        // fixed bug 16206, 15800 end
        CameraSettings settings = new CameraSettings(this, mParameters,
                CameraHolder.instance().getCameraInfo());

        PreferenceGroup group =
                settings.getPreferenceGroup(R.xml.video_preferences);
        if (mIsVideoCaptureIntent) {
            group = filterPreferenceScreenByIntent(group);
        }

        //MM09: fix the bug9213 in 2012.02.02. begin
        mHeadUpDisplay.initialize(this, group, getZoomRatios(),mOrientationCompensation);

        if (mParameters.isZoomSupported()) {
            mHeadUpDisplay.setZoomListener(new ZoomControllerListener() {
                public void onZoomChanged(
                        int index, float ratio, boolean isMoving) {
                    onZoomValueChanged(index);
                }
            });
        }
      //MM09: fix the bug9213 in 2012.02.02. end
    }

    //MM09: fix the bug9213 in 2012.02.02. begin
    private float[] getZoomRatios() {
        if(!mParameters.isZoomSupported()) return null;
        List<Integer> zoomRatios = mParameters.getZoomRatios();

        float result[] = new float[zoomRatios.size()];
        for (int i = 0, n = result.length; i < n; ++i) {
            result[i] = (float) zoomRatios.get(i) / 100f;
        }
        return result;
    }

    private void initializeZoom() {
        if (mCameraDevice == null) return;
        if (!mParameters.isZoomSupported()) return;
        // Maximum zoom value may change after preview size is set. Get the
        // latest parameters here.
        mParameters = mCameraDevice.getParameters();
        mZoomMax = mParameters.getMaxZoom();

        mSmoothZoomSupported = mParameters.isSmoothZoomSupported();

        mCameraDevice.setZoomChangeListener(mZoomListener);
    }


    private void onZoomValueChanged(int index) {
        Log.v(TAG, "onZoomValueChanged: index = " + index + " mZoomValue = " + mZoomValue);
        if (mSmoothZoomSupported) {
            if (mTargetZoomValue != index && mZoomState != ZOOM_STOPPED) {
                mTargetZoomValue = index;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            } else if (mZoomState == ZOOM_STOPPED && mZoomValue != index) {
                mTargetZoomValue = index;
                mCameraDevice.startSmoothZoom(index);
                mZoomState = ZOOM_START;
            }
        } else {
            // wxz20120203: bug 9213. set the zoom value when recording.
            if (mMediaRecorderRecording) {
                Log.v(TAG, "wxz: set zoom by mMediaRecorder.");
                // fixed bug 16350 start
                mZoomValue = index;
                mMediaRecorder.setRecordingZoonValue(index);
                // fixed bug 16350 end
            } else {
                Log.v(TAG, "wxz: set zoom by mCameraDevice.");
                mZoomValue = index;
                // fixed bug 21403 start
//                updateCameraParametersZoom();
//                mCameraDevice.setParameters(mParameters);
                // we will update zoom the parameters,
                // but device state must open,
                // otherwise we don't need to do anything
                if (mCameraDevice != null) {
                    updateCameraParametersZoom();
                    mCameraDevice.setParameters(mParameters);
                }
                // fixed bug 21403
            }
        }
    }

    private final class ZoomListener implements
            android.hardware.Camera.OnZoomChangeListener {
        public void onZoomChange(int value, boolean stopped,
                android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped=" + stopped);
            mZoomValue = value;
            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);
            // We only care if the zoom is stopped. mZooming is set to true when
            // we start smooth zoom.
            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                     System.out.println("mZoomValue2 = " + mZoomValue);
                } else {
                    mZoomState = ZOOM_STOPPED;
                     System.out.println("mZoomValue3 = " + mZoomValue);
                }
            }
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
    }
    //MM09: fix the bug9213 in 2012.02.02. end

    private void attachHeadUpDisplay() {
        mHeadUpDisplay.setOrientation(mOrientationCompensation);

        FrameLayout frame = (FrameLayout) findViewById(R.id.video_camera);
        mGLRootView = new GLRootView(this);
        FrameLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.RIGHT;
        params.rightMargin = 160;
        params.topMargin = 7;
        params.bottomMargin = 7;
        // fixed bug 16768
        if (mPreviewFrameLayout != null
                && mPreviewFrameLayout.getDensity() == 1.0f) {
            params.rightMargin = (int) (120 * 1.0f / 1.5f);
            if (mParameters != null
                    && mParameters.getPreviewSize().width == 720) {
                mGLRootView.setLayoutContentPane(0, 18);
            }
        }
        // end 16768
        mGLRootView.setLayoutParams(params);
        mGLRootView.setContentPane(mHeadUpDisplay);
        frame.addView(mGLRootView);
    }

    private void detachHeadUpDisplay() {
        mHeadUpDisplay.collapse();
        ((ViewGroup) mGLRootView.getParent()).removeView(mGLRootView);
        mGLRootView = null;
    }

    public static int roundOrientation(int orientation) {
        return ((orientation + 45) / 90 * 90) % 360;
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (mMediaRecorderRecording) return;
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = roundOrientation(orientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation + Util.getDisplayRotation(VideoCamera.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                if (!mIsVideoCaptureIntent) {
                    setOrientationIndicator(mOrientationCompensation);
                }
                mHeadUpDisplay.setOrientation(mOrientationCompensation);
            }
        }
    }

    private void setOrientationIndicator(int degree) {
        ((RotateImageView) findViewById(
                R.id.review_thumbnail)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.camera_switch_icon)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.video_switch_icon)).setDegree(degree);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        if (!mIsVideoCaptureIntent) {
            mSwitcher.setSwitch(SWITCH_VIDEO);
        }
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_retake:
                // fixed bug 20080 start
//                Log.v(TAG, "btn_retake");
//                deleteCurrentVideo();
//                hideAlert();
//               try {
//                    startPreview();
//               } catch (CameraHardwareException e) {
//                    return;
//               }
                boolean result = retake();
                Log.d(TAG, "btn_retake success? " + result);
                // fixed bug 20080 end
                break;
            case R.id.btn_play:
                startPlayVideoActivity();
                break;
            case R.id.btn_done:
                doReturnToCaller(true);
                break;
            case R.id.btn_cancel:
                stopVideoRecordingAndReturn(false);
                break;
            case R.id.review_thumbnail:
                if (!mMediaRecorderRecording) viewLastVideo();
                break;
            case R.id.pause_button:
                if(mMediaRecorder == null){
                     Log.v(TAG, "mMediaRecorder is null");
                     return;
                }
                if(isRecordingPaused){
                    resumeVideoRecord();
                    return;
                  }

                pauseVideoRecording();
                break;
        }
    }

    // fixed bug 20080 start
    private boolean retake() {
        Log.d(TAG, "retake()");
        boolean result = true;
        deleteCurrentVideo();
        hideAlert();
        result = restartPreview();
        return result;
    }
    // fixed bug 20080 end

    private long skiptime = 0;
    private boolean isRecordingPaused = false;
    private long pauseRecordingTimeSpot = 0;
    private void pauseVideoRecording(){
          Log.v(TAG, "pauseVideoRecording");
          updatePauseRecordingIndicator(true);
          isRecordingPaused = true;
          mMediaRecorder.pauseRecording();
          pauseRecordingTimeSpot = SystemClock.uptimeMillis();
    }

    private void resumeVideoRecord(){
          Log.v(TAG, "resumeVideoRecord");
          updatePauseRecordingIndicator(false);
          isRecordingPaused = false;
          mMediaRecorder.resumeRecording();
          long now = SystemClock.uptimeMillis();
          skiptime += (now - pauseRecordingTimeSpot);
          updateRecordingTime();
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        // Do nothing (everything happens in onShutterButtonClick).
    }

    private void onStopVideoRecording(boolean valid) {
        Log.v(TAG, "onStopVideoRecording");
        if (mIsVideoCaptureIntent) {
            if (mQuickCapture) {
                stopVideoRecordingAndReturn(valid);
            } else {
                stopVideoRecordingAndShowAlert();
            }
        } else {
            stopVideoRecordingAndGetThumbnail();
            try {
                startPreview();
            } catch (CameraHardwareException e) {
                Log.e(TAG, "can't be connected to Camera");
                return;
            }
//            initializeRecorder();
        }
    }

    public void onShutterButtonClick(ShutterButton button) {
        // fixed bug 20728 start
        Log.d(TAG, "onShutterButtonClick");
        if (mGLRootView != null && mHeadUpDisplay != null) {
            mHeadUpDisplay.collapse();
        }
        // fixed bug 20728 end
        switch (button.getId()) {
            case R.id.shutter_button:
                if (mHeadUpDisplay.collapse()) return;

                if (mMediaRecorderRecording) {
                    onStopVideoRecording(true);
                }else{
                    startVideoRecording();
                }

                mShutterButton.setEnabled(false);
                mHandler.sendEmptyMessageDelayed(ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
                break;
        }
    }

    private OnScreenHint mStorageHint;

    private void updateAndShowStorageHint(boolean mayHaveSd) {
        mStorageStatus = getStorageStatus(mayHaveSd);
        showStorageHint();
    }

    private void showStorageHint() {
        String errorMessage = null;
        switch (mStorageStatus) {
            case STORAGE_STATUS_NONE:
                errorMessage = getString(R.string.no_storage);
                break;
            case STORAGE_STATUS_LOW:
                errorMessage = getString(R.string.spaceIsLow_content);
                break;
            case STORAGE_STATUS_FAIL:
                // fixed not storage hint start
//                errorMessage = getString(R.string.access_sd_fail);
                errorMessage = getString(R.string.access_storage_fail);
                // fixed not storage hint end
                break;
            case STORAGE_STATUS_SPACE_FULL:
                errorMessage = getString(R.string.memory_full);
                break;
        }

        if (errorMessage != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, errorMessage);
            } else {
                mStorageHint.setText(errorMessage);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private int getStorageStatus(boolean mayHaveSd) {
        // fixed not storage hint start
        if (MemoryCheck.checkMemory()) {
            return STORAGE_STATUS_SPACE_FULL;
        }

        int result = STORAGE_STATUS_OK;
        long remain = getAvailableStorage();
        // validate "storage" state is "MEDIA_CHECKING"
        if (NO_STORAGE_ERROR == remain) {
            String storage_state = Environment.getExternalStorageState();
            if (!ImageManager.getIsInternalBucket() &&
                    Environment.MEDIA_CHECKING.equals(storage_state)) {
                result = STORAGE_STATUS_NONE;
            }
        }
        // validate "storage" state is "CANNOT_STAT_ERROR"
        else if (CANNOT_STAT_ERROR == remain) {
            result = STORAGE_STATUS_FAIL;
        }
        // validate "storage" space < 512KB
        else if (LOW_STORAGE_THRESHOLD > remain) {
            result = STORAGE_STATUS_LOW;
        }
        return result;
//        if (MemoryCheck.checkMemory())
//            return STORAGE_STATUS_SPACE_FULL;
//
//        long remaining = mayHaveSd ? getAvailableStorage() : NO_STORAGE_ERROR;
//        if (remaining == NO_STORAGE_ERROR) {
//            return STORAGE_STATUS_NONE;
//        } else if (remaining == CANNOT_STAT_ERROR) {
//            return STORAGE_STATUS_FAIL;
//        }
//        return remaining < LOW_STORAGE_THRESHOLD
//                ? STORAGE_STATUS_LOW
//                : STORAGE_STATUS_OK;
        // fixed not storage hint end
    }

    private void readVideoPreferences() {
        String quality = mPreferences.getString(
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);
        Log.v(TAG, "readVideoPreferences: quality =" + quality);
        int videoQualityLevel = CamcorderProfile.QUALITY_HIGH;
        //mm09: fix the bug9239 in 2011.02.01.begin
//        boolean videoQualityHigh = CameraSettings.getVideoQuality(quality);
        videoQualityLevel = CameraSettings.getVideoQualityLevel(quality);
        // Set video quality.
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
//            videoQualityHigh = (extraVideoQuality > 0);
            videoQualityLevel = (extraVideoQuality > 0) ? CamcorderProfile.QUALITY_HIGH : CamcorderProfile.QUALITY_LOW;
        }

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
                    intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        } else {
            mMaxVideoDurationInMs =
                    CameraSettings.getVidoeDurationInMillis(quality);
        }
//        mProfile = CamcorderProfile.get(mCameraId,
//                videoQualityHigh
//                ? CamcorderProfile.QUALITY_HIGH
//                : CamcorderProfile.QUALITY_LOW);
        mProfile = CamcorderProfile.get(mCameraId, videoQualityLevel);
        //mm09: fix the bug9239 in 2011.02.01.end
    }

    private void resizeForPreviewAspectRatio() {
        // Set the preview frame aspect ratio according to the picture size.
        String previewSize = mPreferences.getString(CameraSettings.KEY_PREVIEW_SIZE, CameraSettings.DEFAULT_CAMERA_PREVIEW_SIZE_VALUE);
        if(previewSize.equals(CameraSettings.CAMERA_PREVIEW_SIZE_FULLSCREEN)){
            mPreviewFrameLayout.setFullScreenMode(true);
        }else{
            mPreviewFrameLayout.setFullScreenMode(false);
        }
        mPreviewFrameLayout.setAspectRatio((double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        mWindowHasFocus = hasWindowFocus();
        hidePauseView();
        mPausing = false;
        mWindowHasFocusPause = !mPausing;
        mResume = 0;
        mTrySwitchCameraState = TRY_SWITCH_CAMERA_RESUME;
        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        // add validate
        if (!isTvRunning) {
            // fixed bug 16206, 15800 start
//            mOrientationListener.enable();
            // fixed bug 162006, 15800 end
            mVideoPreview.setVisibility(View.VISIBLE);
            readVideoPreferences();
            resizeForPreviewAspectRatio();
        }

        if (!mPreviewing && !mStartPreviewFail) {
            if (!restartPreview()) return;
        }

        // add validate // fixed bug 16206, 15800 start
        if (isTvRunning || mStartPreviewFail) return;
        // fixed bug 15206, 15800 end

        // fixed bug 16206, 15800 start
        if (!isTvRunning) {
            mOrientationListener.enable();
        }
        // fixed bug 16206, 15800 end
        keepScreenOnAwhile();

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
        mStorageStatus = getStorageStatus(true);
        //fixed bug for 14956
        if (mIsVideoCaptureIntent
                && (mStorageStatus == STORAGE_STATUS_NONE || mStorageStatus == STORAGE_STATUS_FAIL)) {
            this.hideAlert();
        }
        mHandler.postDelayed(new Runnable() {
            public void run() {
                showStorageHint();
            }
        }, 500);

        changeHeadUpDisplayState();
        //fixed bug for 14960
        if (mIsVideoCaptureIntent && mVideoFrame != null
                && mVideoFrame.getVisibility() == View.VISIBLE
                && mGLRootView != null) {
            detachHeadUpDisplay();
        }
        updateThumbnailButton();

        if(!mPreviewStatus){
            mHandler.sendEmptyMessageDelayed(UPDATE_PREVIEW_STATUS, 500);
        }

        mResume = System.currentTimeMillis();
        // fixed bug 20196 start format code
        TelephonyManager telephonyManager =
            (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(
            mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        // fixed bug 20196 end
    }

    private boolean mWindowHasFocus = true;
    private boolean mWindowHasFocusPause = mWindowHasFocus;
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mWindowHasFocus = hasFocus;
        mWindowHasFocusPause = !mWindowHasFocus;
        if (!mPausing && !mMediaRecorderRecording) setCameraParameters();
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private static final int TRY_SWITCH_CAMERA_CREATE = 0;
    private static final int TRY_SWITCH_CAMERA_RESUME = 1;
    private boolean mTrySwitchCamera = true;
    private int mTrySwitchCameraState = TRY_SWITCH_CAMERA_CREATE;
    private void startPreview() throws CameraHardwareException {
        Log.v(TAG, "startPreview");

        // add validate
//        if (doCheck()) return;

        try {
            // if onCreate call startPreview() so try one time,
            // because current activity isn't activated, we can't
            // show dialog notice user;
            // if onResume call startPreview() so try ten times
            int tried =
                (mTrySwitchCameraState == TRY_SWITCH_CAMERA_CREATE ? 1 : 10);
            while (tried-- > 0) {
                try {
                    // test camera hardware broken start
//                  if (true && mCameraId == 1) throw new Exception();
                    // test camera hardware broken end
                    Log.d(TAG, "tried begin");
                    if (mCameraDevice == null) {
                        // If the activity is paused and resumed, camera device has been
                        // released and we need to open the camera.
                        mCameraDevice = CameraHolder.instance().open(mCameraId);
                    }
                    if (mPreviewing) {
                        mCameraDevice.stopPreview();
                        mPreviewing = false;
                    }
                    setPreviewDisplay(mSurfaceHolder);
                    Util.setCameraDisplayOrientation(this, mCameraId, mCameraDevice);
                    setCameraParameters();
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(150L); }
                    catch (InterruptedException ex) { }
                    Log.d(TAG, "tried count = " + tried + " --- cameraId = " + mCameraId + " --- E: " + e);
                    if (tried <= 0) {
                        Log.v(TAG, "tried finish throw Throwable .");
                        throw new Throwable(e);
                    }
                }
            }

            Log.v(TAG, "start camera device preview");
            mCameraDevice.startPreview();
            mPreviewing = true;
        } catch (Throwable ex) {
            Log.v(TAG, "start camera device preview failed");
            closeCamera();
            if (!mTrySwitchCamera) {
                if (doCheck()) return;
                throw new CameraHardwareException(ex);
            }
            if (mTrySwitchCamera && mTrySwitchCameraState == TRY_SWITCH_CAMERA_RESUME) {
                // fixed bug 15206, 15800 start
                mTrySwitchCamera = false;
                switchCameraId(mCameraId == 0 ? 1 : 0);
//                mHandler.sendEmptyMessage(START_PREVIEW_SWITCH_CAMERA);
                // fixed bug 15206, 15800 end
            }
            return;
        }
        mTrySwitchCamera = true;
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        // If we don't lock the camera, release() will fail.
//        mCameraDevice.lock();
      //*mm09: update in 2011.12.30 *****/
        lock();
      //*****mm09: update in 2011.12.30 */
        CameraHolder.instance().release();
        mCameraDevice = null;
        mPreviewing = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        //fixed bug 16734
        if(mResetDialog !=null){
            mResetDialog.cancel();
            mResetDialog = null;
        }
        mPausing = true;
        mWindowHasFocusPause = mPausing;
        if(!isSwitchCamera){
            showPauseView();
        }else{
            isSwitchCamera = false;
        }

        changeHeadUpDisplayState();

        // Hide the preview now. Otherwise, the preview may be rotated during
        // onPause and it is annoying to users.
        mVideoPreview.setVisibility(View.INVISIBLE);

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
            if (mIsVideoCaptureIntent) {
                stopVideoRecording();
                showAlert();
            } else {
                stopVideoRecordingAndGetThumbnail();
            }
        } else {
            stopVideoRecording();
        }
        closeCamera();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

        if (!mIsVideoCaptureIntent) {
            mThumbController.storeData(ImageManager.getLastVideoThumbPath());
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        mOrientationListener.disable();
        mHandler.removeMessages(UPDATE_LAST_IMAGE);
        // fixed bug 20196 start format code
        TelephonyManager telephonyManager =
            (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(
            mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        // fixed bug 20196 end
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!mMediaRecorderRecording) keepScreenOnAwhile();
    }

    @Override
    public void onBackPressed() {
        if (mPausing) return;
        if (mMediaRecorderRecording) {
            onStopVideoRecording(false);
        } else if (mHeadUpDisplay == null || !mHeadUpDisplay.collapse()) {
            // fixed bug 19050 start
            if (mGLRootView != null) mGLRootView.onPause();
            // fixed bug 19050 end
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        Log.v(TAG, "onKeyDown: keyCode = " + keyCode + " ,action = " + event.getAction());
        if (mPausing) {
            return true;
        }

        switch (keyCode) {
               case KeyEvent.KEYCODE_VOLUME_UP:
                  return true;
              case KeyEvent.KEYCODE_VOLUME_DOWN:
                  return true;
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    // fixed bug 20080 start
//                    if(mShutterButton != null){
//                        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pressed);
//                    }
//                    mShutterButton.performClick();
                    if (mShutterButton != null) {
                        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pressed);
                        if (mIsVideoCaptureIntent && isAlertVisible()) {
                            boolean result = retake();
                            Log.d(TAG, "onKeyDown() retake success? " + result);
                        } else {
                            mShutterButton.performClick();
                        }
                    }
                    return true;
                    // fixed bug 20080 end
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    mShutterButton.performClick();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                if (mMediaRecorderRecording) {
                    //fixed bug for 14961
                    //onStopVideoRecording(true);
                    return true;
                }

                if (false == mPreviewStatus){
                    Log.v(TAG, "it is not ready for menu opening");
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                if(mShutterButton != null){
                    mShutterButton.setBackgroundResource(R.drawable.btn_shutter);
                }
                mShutterButton.setPressed(false);
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Log.v(TAG, "surfaceChanged");
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        mSurfaceHolder = holder;

        if (mPausing) {
            // We're pausing, the screen is off and we already stopped
            // video recording. We don't want to start the camera again
            // in this case in order to conserve power.
            // The fact that surfaceChanged is called _after_ an onPause appears
            // to be legitimate since in that case the lockscreen always returns
            // to portrait orientation possibly triggering the notification.
            return;
        }

        // The mCameraDevice will be null if it is fail to connect to the
        // camera hardware. In this case we will show a dialog and then
        // finish the activity, so it's OK to ignore it.
        if (mCameraDevice == null) return;

        // Set preview display if the surface is being created. Preview was
        // already started.
        if (holder.isCreating()) {
            setPreviewDisplay(holder);
        } else {
            stopVideoRecording();
            restartPreview();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mSurfaceHolder = null;
    }

    private void gotoGallery() {
        MenuHelper.gotoCameraVideoGallery(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsVideoCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
        }
        return true;
    }

    private boolean isVideoCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
        } else {
            resultCode = RESULT_CANCELED;
        }
        setResult(resultCode, resultIntent);
        finish();
    }

    /**
     * Returns
     *
     * @return number of bytes available, or an ERROR code.
     */
    private static long getAvailableStorage() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                // fixed bug 16191, 16230 start
                String dir = ImageManager.getBucketRootPath();
                StatFs stat = new StatFs(dir);
                int blocks = stat.getAvailableBlocks();
                int block_size = stat.getBlockSize();
                long remain = (long) (((float) blocks) * ((float) block_size));
                Log.d(TAG,
                    String.format("available storage blocks = %d --- size = % d, remain = %d",
                        new Object[] { blocks, block_size, remain }));
                return remain;
                // fixed bug 16191, 16230 end
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // free bytes exist. It might be zero but just leave it
            // blank since we really don't know.
            Log.e(TAG, "Fail to access sdcard", ex);
            return CANNOT_STAT_ERROR;
        }
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    private android.hardware.Camera mCameraDevice;

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        if (mSurfaceHolder == null) {
            Log.v(TAG, "Surface holder is null. Wait for surface changed.");
            return;
        }

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();

        // Unlock the camera object before passing it to media recorder.
//        mCameraDevice.unlock();
      //*mm09: update in 2011.12.30 *****/
        unlock();
      //******mm09: update in 2011.12.30*/
        //fixed bug 17205
        if("3gp".equals(getVideoFormat())){
            mProfile.fileFormat = 1;
        }else{
            mProfile.fileFormat = 2;
        }
        mMediaRecorder.setCamera(mCameraDevice);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

        // Set output file.
        if (mStorageStatus != STORAGE_STATUS_OK) {
            mMediaRecorder.setOutputFile("/dev/null");
        } else {
            // Try Uri in the intent first. If it doesn't exist, use our own
            // instead.
            if (mVideoFileDescriptor != null) {
                mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
                try {
                    mVideoFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Fail to close fd", e);
                }
            } else {
                createVideoPath();
                mMediaRecorder.setOutputFile(mVideoFilename);
            }
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.
        // remaining >= LOW_STORAGE_THRESHOLD at this point, reserve a quarter
        // of that to make it more likely that recording can complete
        // successfully.
        long maxFileSize = getAvailableStorage() - LOW_STORAGE_THRESHOLD / 4;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
        mMediaRecorder.setOrientationHint(rotation);
        mOrientationHint = rotation;

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        // Take back the camera object control from media recorder. Camera
        // device may be null if the activity is paused.
        if (mCameraDevice != null){
            //*mm09: update in 2011.12.30 *****/
//          mCameraDevice.lock();
            lock();
          //******mm09: update in 2011.12.30 */
        }
    }

    private void createVideoPath() {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String format = getVideoFormat();
        //fixed 17205
        Log.d(TAG, "createVideoPath format="+format);
        String filename = title + "."+format; // Used when emailing.
        // fixed bug 16191, 16230 start
        String dir = ImageManager.getCameraImageBucketPath();
        String path = dir.concat("/").concat(filename);
        // fixed bug 16191, 16230 end
        ContentValues values = new ContentValues(7);
        values.put(Video.Media.TITLE, title);
        values.put(Video.Media.DISPLAY_NAME, filename);
        values.put(Video.Media.DATE_TAKEN, dateTaken);
        values.put(Video.Media.MIME_TYPE, "video/"+format);
        values.put(Video.Media.DATA, path);
        mVideoFilename = path;
        Log.v(TAG, "Current camera video filename: " + mVideoFilename);
        mCurrentVideoValues = values;
    }
    //fixed 17205
    private String getVideoFormat(){
    	return mPreferences.getString(CameraSettings.Key_VIDEO_FORMAT, CameraSettings.DEFAULT_VIDEO_FORMAT_VALUE);
    }

    private void registerVideo() {
        if (mVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mCurrentVideoValues.put(Video.Media.SIZE,
                    new File(mCurrentVideoFilename).length());
            try {
                mCurrentVideoUri = mContentResolver.insert(videoTable,
                        mCurrentVideoValues);
            } catch (Exception e) {
                // We failed to insert into the database. This can happen if
                // the SD card is unmounted.
                mCurrentVideoUri = null;
                mCurrentVideoFilename = null;
            } finally {
                Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
            }
        }
        mCurrentVideoValues = null;
    }

    private void deleteCurrentVideo() {
        if (mCurrentVideoFilename != null) {
            deleteVideoFile(mCurrentVideoFilename);
            mCurrentVideoFilename = null;
        }
        if (mCurrentVideoUri != null) {
            mContentResolver.delete(mCurrentVideoUri, null, null);
            mCurrentVideoUri = null;
        }
        updateAndShowStorageHint(true);
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, false, new Runnable() {
            public void run() {
                switchToCameraMode();
            }
        });
        MenuItem gallery = menu.add(Menu.NONE, Menu.NONE,
                MenuHelper.POSITION_GOTO_GALLERY,
                R.string.camera_gallery_photos_text)
                .setOnMenuItemClickListener(
                    new OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            gotoGallery();
                            return true;
                        }
                    });
        gallery.setIcon(android.R.drawable.ic_menu_gallery);
        mGalleryItems.add(gallery);

        if (mNumberOfCameras > 1) {
            menu.add(Menu.NONE, Menu.NONE,
                    MenuHelper.POSITION_SWITCH_CAMERA_ID,
                    R.string.switch_camera_id)
                    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
//                    switchCameraId((mCameraId + 1) % mNumberOfCameras);
                    switchCameraId(mCameraId!=0?0:1);
                    return true;
                }
            }).setIcon(android.R.drawable.ic_menu_camera);
        }
    }

    private void switchCameraId(int cameraId) {
        if (mPausing) return;
        mCameraId = cameraId;
        CameraSettings.writePreferredCameraId(mPreferences, cameraId);

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
            if (mIsVideoCaptureIntent) {
                stopVideoRecording();
                showAlert();
            } else {
                stopVideoRecordingAndGetThumbnail();
            }
        } else {
            stopVideoRecording();
        }
        closeCamera();

        // Reload the preferences.
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        // Read media profile again because camera id is changed.
        readVideoPreferences();
        resizeForPreviewAspectRatio();
        restartPreview();

        // Reload the UI.
        initializeHeadUpDisplay();
    }

    private PreferenceGroup filterPreferenceScreenByIntent(
            PreferenceGroup screen) {
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }

        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }
        return screen;
    }

    // from MediaRecorder.OnErrorListener
    public void onError(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            updateAndShowStorageHint(true);
        }
    }

    // from MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra) {
        // fixed not storage hint start
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED :
                if (mMediaRecorderRecording) onStopVideoRecording(true);
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED :
                if (mMediaRecorderRecording) onStopVideoRecording(true);
                updateAndShowStorageHint(true);
                break;
        }
//        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
//            if (mMediaRecorderRecording) onStopVideoRecording(true);
//        } else if (what
//                == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
//            if (mMediaRecorderRecording) onStopVideoRecording(true);
//
//            // Show the toast.
//            Toast.makeText(VideoCamera.this, R.string.video_reach_size_limit,
//                           Toast.LENGTH_LONG).show();
//        }
        // fixed not storage hint end
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void pauseAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");

        sendBroadcast(i);
    }

    // fix the bug 15103 start
    private void closeFMRadio() {
        Intent i = new Intent("com.android.camera.action.RECORD_VIDEO");
        sendBroadcast(i);
    }
    // fix the bug 15103 end

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        if (mStorageStatus != STORAGE_STATUS_OK) {
            Log.v(TAG, "Storage issue, ignore the start request");
            return;
        }

        initializeRecorder();
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder");
            return;
        }

        pauseAudioPlayback();
        closeFMRadio(); // fix the bug 15103

        try {
            mMediaRecorder.start(); // Recording is now started
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start media recorder. ", e);
            releaseMediaRecorder();
            return;
        }
        mHeadUpDisplay.setEnabled(false);

        mMediaRecorderRecording = true;
        mRecordingStartTime = SystemClock.uptimeMillis();
        updateRecordingIndicator(false);
        // Rotate the recording time.
        mRecordingTimeRect.setOrientation(mOrientationCompensation);
        mRecordingTimeView.setText("");
        mRecordingTimeView.setVisibility(View.VISIBLE);
        updateRecordingTime();
        keepScreenOn();
    }

    private void updateRecordingIndicator(boolean showRecording) {
        int drawableId =
                showRecording ? R.drawable.btn_ic_video_record
                        : R.drawable.btn_ic_video_record_stop;
        Drawable drawable = getResources().getDrawable(drawableId);
        mShutterButton.setImageDrawable(drawable);

        if(showRecording){
             fadeOut(mPauseButton);
             if(mIsVideoCaptureIntent){
                  fadeIn((View) findViewById(R.id.btn_cancel).getParent());
             }else{
                  fadeIn(mLastPictureButton);
             }
        }else{
             if(mIsVideoCaptureIntent){
                fadeOut((View) findViewById(R.id.btn_cancel).getParent());
             }else{
                  fadeOut(mLastPictureButton);
             }
             mPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.pause_button));
             fadeIn(mPauseButton);
        }
        isRecordingPaused = false;
        skiptime = 0;
    }

    private void stopVideoRecordingAndGetThumbnail() {
        stopVideoRecording();
        acquireVideoThumb();
    }

    private void stopVideoRecordingAndReturn(boolean valid) {
        stopVideoRecording();
        doReturnToCaller(valid);
    }

    private void stopVideoRecordingAndShowAlert() {
        stopVideoRecording();
        showAlert();
    }

    private void showAlert() {
        fadeOut(findViewById(R.id.shutter_button));
        if (mCurrentVideoFilename != null) {
            Bitmap src = ThumbnailUtils.createVideoThumbnail(
                    mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it back (and mirror if it is front-facing camera).
            CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            if (info[mCameraId].facing == CameraInfo.CAMERA_FACING_BACK) {
                src = Util.rotateAndMirror(src, -mOrientationHint, false);
            } else {
                // fixed bug 19412 start
                //fixed bug for 14958
//                src = Util.rotateAndMirror(src, mOrientationHint, true);
                src = Util.rotateAndMirror(src, mOrientationHint, false);
                // fixed bug 19412 end
            }
            mVideoFrame.setImageBitmap(src);
            mVideoFrame.setVisibility(View.VISIBLE);
            //fixed bug for 14960
            if(mIsVideoCaptureIntent && mGLRootView != null){
                detachHeadUpDisplay();
            }
        }
        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            View button = findViewById(id);
            fadeIn(((View) button.getParent()));
        }
    }

    private void hideAlert() {
        mVideoFrame.setVisibility(View.INVISIBLE);
        fadeIn(findViewById(R.id.shutter_button));
        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            View button = findViewById(id);
            fadeOut(((View) button.getParent()));
        }
        //fixed bug for 14960
        if(mIsVideoCaptureIntent && mGLRootView == null){
            attachHeadUpDisplay();
        }
    }

    private static void fadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(0F, 1F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private static void fadeOut(View view) {
        view.setVisibility(View.INVISIBLE);
        Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private boolean isAlertVisible() {
        // fixed bug 20080 start
//        return this.mVideoFrame.getVisibility() == View.VISIBLE;
        boolean result = (mVideoFrame != null);
        if (result) {
            int visible = mVideoFrame.getVisibility();
            Log.d(TAG, "mVideoFrame.getVisibility() == " + visible);
            result = (View.VISIBLE == visible);
        }
        Log.d(TAG, "isAlertVisible() == " + result);
        return result;
        // fixed bug 20080 end
    }

    private void viewLastVideo() {
        Intent intent = null;
        if (mThumbController.isUriValid()) {
            intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                try {
                    intent = new Intent(Intent.ACTION_VIEW, mThumbController.getUri());
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "review video fail", e);
                }
            }
        } else {
            Log.e(TAG, "Can't view last video.");
        }
    }

    private void stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        if (mMediaRecorderRecording) {
            boolean needToRegisterRecording = false;
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            try {
                mMediaRecorder.stop();
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "Setting current video filename: " + mCurrentVideoFilename);
                needToRegisterRecording = true;
                // fixed bug 16191, 16230 start
                ImageManager.ensureFilePermission(mCurrentVideoFilename);
                // fixed bug 16191, 16230 end
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail: ", e);
                deleteVideoFile(mVideoFilename);
            }
            mMediaRecorderRecording = false;
            mHeadUpDisplay.setEnabled(true);
            updateRecordingIndicator(true);
            mRecordingTimeView.setVisibility(View.GONE);
            keepScreenOnAwhile();
            if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK) {
                registerVideo();
            }
            mVideoFilename = null;
            mVideoFileDescriptor = null;
        }

        releaseMediaRecorder();  // always release media recorder
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void acquireVideoThumb() {
        Bitmap videoFrame = ThumbnailUtils.createVideoThumbnail(
                mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
        mThumbController.setData(mCurrentVideoUri, videoFrame);
        mThumbController.updateDisplayIfNeeded();
    }

    private static ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    private void updateThumbnailButton() {
        // Update the last video thumbnail.
        Log.v(TAG, "updateThumbnailButton: mIsVideoCaptureIntent = " + mIsVideoCaptureIntent);
        if (!mIsVideoCaptureIntent) {
            if (!mThumbController.isUriValid()) {
//                updateLastVideo();
                Thread thread = new Thread(new Runnable() {
                    public void run() {
                      sendLastImageUpdatedMessage();
                    }
                  });

                  thread.start();
                try {
                    thread.join(500);
                } catch (InterruptedException e) {
                    Log.d(TAG, "updateThumbnailButton: thread interrupted");
                }
            }
            mThumbController.updateDisplayIfNeeded();
        }
    }

    private void sendLastImageUpdatedMessage(){
        Log.d(TAG, "sendLastImageUpdatedMessage");
        Bitmap bitmap = null;
        Uri uri = null;

        IImage image = updateLastImageData();
        if(image != null){
            uri = image.fullSizeImageUri();
            bitmap = image.miniThumbBitmap();
        }

        Log.d(TAG, "sendLastImageUpdatedMessage: bitmap = " + bitmap + " uri = " + uri);
        if(mPausing && bitmap !=null){
            bitmap.recycle();
            bitmap = null;
            return;
        }

        final Message msg = new Message();
        final Bundle b = new Bundle();
        if(uri != null){
            b.putString("uri", uri.toString());
        }
        msg.what = UPDATE_LAST_IMAGE;
        msg.obj = bitmap;
        msg.setData(b);

        mHandler.sendMessage(msg);
   }

    private IImage updateLastImageData(){
        IImageList list = null;
        IImage image = null;
        try {
            list = ImageManager.makeImageList(
                       mContentResolver,
                       dataLocation(),
                       ImageManager.INCLUDE_VIDEOS,
                       ImageManager.SORT_ASCENDING,
                       // fixed bug 16191, 16230 start
                       ImageManager.getCameraImageBucketId());
                       // fixed bug 16191, 16230 end
             int count = list.getCount();
             if (count > 0) {
                 image = list.getImageAt(count - 1);
             }
        } finally{
           if(list != null){
               list.close();
           }
        }

        return image;
    }

    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }

        if(isRecordingPaused){
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime - skiptime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long next_update_delay = 1000 - (delta % 1000);
        long seconds;
        if (countdownRemainingTime) {
            delta = Math.max(0, mMaxVideoDurationInMs - delta);
            seconds = (delta + 999) / 1000;
        } else {
            seconds = delta / 1000; // round to nearest
        }

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2) {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2) {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0) {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2) {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
        }

        mRecordingTimeView.setText(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mRecordingTimeView.setTextColor(color);
        }

        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, next_update_delay);
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void setCameraParameters() {
        if (mCameraDevice == null) return;
        mParameters = mCameraDevice.getParameters();
        mParameters.set("sensororientation", 0);
	mParameters.set("videodatatype", "1");

        mParameters.setPreviewSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

        // Set flash mode.
        String flashMode = mPreferences.getString(
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                getString(R.string.pref_camera_video_flashmode_default));
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
            if (!mWindowHasFocus || mWindowHasFocusPause) flashMode = "off";
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
                flashMode = getString(
                        R.string.pref_camera_flashmode_no_flash);
            }
        }

        // Set white balance parameter.
        String whiteBalance = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }
        //fixed bug 16350
        mParameters.setZoom(mZoomValue);
        mCameraDevice.setParameters(mParameters);
        // Keep preview size up to date.
        mParameters = mCameraDevice.getParameters();
    }

    boolean isSwitchCamera = false;
    private boolean switchToCameraMode() {
        //fix bug4837
        long current = System.currentTimeMillis();
        long elapsed_time = current - mResume;
        Log.d(TAG, "Elapsed time : "+elapsed_time);
        if (elapsed_time < 1000 || mResume == 0) return false;
        //end fix bug4837
        if (isFinishing() || mMediaRecorderRecording) return false;
        isSwitchCamera = true;
        MenuHelper.gotoCameraMode(this);
        finish();
        return true;
    }

    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        if (onOff == SWITCH_CAMERA) {
            return switchToCameraMode();
        } else {
            return true;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        changeHeadUpDisplayState();
    }

    private void resetCameraParameters() {
        // We need to restart the preview if preview size is changed.
        Size size = mParameters.getPreviewSize();
        if (size.width != mProfile.videoFrameWidth
                || size.height != mProfile.videoFrameHeight) {
            // It is assumed media recorder is released before
            // onSharedPreferenceChanged, so we can close the camera here.
            closeCamera();
            resizeForPreviewAspectRatio();
            restartPreview(); // Parameters will be set in startPreview().
            //fixed bug 16768
            setLayoutContentPane();
        } else {
            resizeForPreviewAspectRatio();
            setCameraParameters();
        }
    }
    //fixed bug 16768
    private void setLayoutContentPane() {
        if (mPreviewFrameLayout != null
                && mPreviewFrameLayout.getDensity() == 1.0) {
            if (mProfile.videoFrameWidth == 720) {
                mGLRootView.setLayoutContentPane(0, 18);
            } else {
                mGLRootView.setLayoutContentPane(0, 0);
            }
        }
    }

    public void onSizeChanged() {
        // TODO: update the content on GLRootView
    }

    private class MyHeadUpDisplayListener implements HeadUpDisplay.Listener {
        public void onSharedPreferencesChanged() {
            mHandler.post(new Runnable() {
                public void run() {
                    VideoCamera.this.onSharedPreferencesChanged();
                }
            });
        }

        public void onRestorePreferencesClicked() {
            mHandler.post(new Runnable() {
                public void run() {
                    VideoCamera.this.onRestorePreferencesClicked();
                }
            });
        }

        public void onPopupWindowVisibilityChanged(final int visibility) {
        }
    }

    private void onRestorePreferencesClicked() {
        Runnable runnable = new Runnable() {
            public void run() {
//                int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
//                int frontCameraId = 1;
//                if(cameraId == frontCameraId){
//                    int backCameraId = 0;
//                    switchCameraId(backCameraId);
//                }

                if(mZoomValue != 0) {
                    mZoomValue = 0;
                    mHeadUpDisplay.setZoomIndex(mZoomValue);
                    updateCameraParametersZoom();
                    mCameraDevice.setParameters(mParameters);
                }

                mHeadUpDisplay.restorePreferences(mParameters);
            }
        };
        mResetDialog = MenuHelper.confirmAction(this,
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                runnable);
    }
    private AlertDialog mResetDialog = null;

    private void onSharedPreferencesChanged() {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPausing) return;
        synchronized (mPreferences) {
            readVideoPreferences();
            // If mCameraDevice is not ready then we can set the parameter in
            // startPreview().
            if (mCameraDevice == null) return;

            // Check if camera id is changed.
            int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
            if (mCameraId != cameraId) {
                switchCameraId(cameraId);
                return;
            }

            resetCameraParameters();

        }
    }

    // fixed bug 17312, 20196(20120618) start
    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch(state) {
                case TelephonyManager.CALL_STATE_RINGING :
                    Log.d(TAG, "TelephonyManager.CALL_STATE_RINGING, mQuickCapture = " + mQuickCapture);
                    if (!checkPhoneBlackList(incomingNumber)) {
                        onStopVideoRecording(mQuickCapture);
                    }
                    break;
            }
        }

        // uri
        private final Uri URI_PHONE_BLACK_LIST =
            Uri.parse("content://com.yeezonetech.providers.block/black_mumbers");
        // columns
        private final String TYPE = "block_type";
        private final String NUMBER = "mumber_value";

        private boolean checkPhoneBlackList(String number) {
            Log.d(TAG, "checkPhoneBlackList()");
            boolean result = false;
            Cursor cr = null;
            try {
                cr = getContentResolver().query(
                    URI_PHONE_BLACK_LIST,
                    new String[] { "COUNT(1) cnt" },
                    // WHERE block_type <> 1 AND mumber_value = 'incoming number'
                    String.format("%s<>1 AND %s='%s'", new Object[] { TYPE, NUMBER, number }),
                    null, null);
                if (cr != null) {
                    for (cr.moveToFirst(); !cr.isAfterLast(); cr.moveToNext()) {
                        int col_cnt = cr.getInt(0);
                        result = (col_cnt > 0);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "checkPhoneBlackList() query failed", e);
                result = false;
            } finally {
                if (cr != null)
                    cr.close();
                Log.d(TAG,
                    String.format("checkPhoneBlackList() finished, the '%s' number in black list? [%b]",
                        new Object[] { number, result }));
            }
            return result;
        }
    };
    // fixed bug 20196 end
}
