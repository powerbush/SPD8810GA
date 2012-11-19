/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.CameraProfile;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.ui.CameraHeadUpDisplay;
import com.android.camera.ui.GLRootView;
import com.android.camera.ui.HeadUpDisplay;
import com.android.camera.ui.ZoomControllerListener;
import com.android.camera.FocusRectangle;
import com.android.camera.FocusRectangle.Pointer;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/** The Camera activity which can preview and take pictures. */
public class Camera extends NoSearchActivity implements View.OnClickListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
        Switcher.OnSwitchListener ,View.OnTouchListener{

    private static final String TAG = "camera";

    private static final int CROP_MSG = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int RESTART_PREVIEW = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 5;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;
    private static final int UPDATE_PREVIEW_STATUS = 10;
    private static final int START_PREVIEW_SWITCH_CAMERA = 11;
    private static final int UPDATE_LAST_IMAGE = 9;
    protected static final int UPDATE_THUMBNAIL_BUTTON = 8;
    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int FOCUS_BEEP_VOLUME = 100;

    private static final int ZOOM_STOPPED = 0;
    private static final int ZOOM_START = 1;
    private static final int ZOOM_STOPPING = 2;

    private int mZoomState = ZOOM_STOPPED;
    private boolean mSmoothZoomSupported = false;
    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private int mTargetZoomValue;

    private Parameters mParameters;
    private Parameters mInitialParams;

    private MyOrientationEventListener mOrientationListener;
    // The device orientation in degrees. Default is unknown.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails.
    private int mOrientationCompensation = 0;
    private ComboPreferences mPreferences;

    private static final int IDLE = 1;
    private static final int SNAPSHOT_IN_PROGRESS = 2;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private int mStatus = IDLE;
    private static final String sTempCropFilename = "crop-temp";

    private android.hardware.Camera mCameraDevice;
    private ContentProviderClient mMediaProviderClient;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder = null;
    private ShutterButton mShutterButton;
    private FocusRectangle mFocusRectangle;
    private ToneGenerator mFocusToneGenerator;
    //private GestureDetector mGestureDetector;
    private Switcher mSwitcher;
    private boolean mStartPreviewFail = false;
    private boolean mValidCamKeyDown = false;
    private GLRootView mGLRootView;

    // mPostCaptureAlert, mLastPictureButton, mThumbController
    // are non-null only if isImageCaptureIntent() is true.
    private ImageView mLastPictureButton;
    private ThumbnailController mThumbController;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private ImageCapture mImageCapture = null;

    private boolean mPreviewing;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;
    private boolean mRecordLocation;

    private static final int FOCUS_NOT_STARTED = 0;
    private static final int FOCUSING = 1;
    private static final int FOCUSING_SNAP_ON_FINISH = 2;
    private static final int FOCUS_SUCCESS = 3;
    private static final int FOCUS_FAIL = 4;
    private int mFocusState = FOCUS_NOT_STARTED;

    private ContentResolver mContentResolver;
    private boolean mDidRegister = false;

    private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private LocationManager mLocationManager = null;

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final ZoomListener mZoomListener = new ZoomListener();
    // Use the ErrorCallback to capture the crash count
    // on the mediaserver
    private final ErrorCallback mErrorCallback = new ErrorCallback();

    private long mFocusStartTime;
    private long mFocusCallbackTime;
    private long mCaptureStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private int mPicturesRemaining;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;

    // Add for test
    public static boolean mMediaServerDied = false;


    // Focus mode. Options are pref_camera_focusmode_entryvalues.
    private String mFocusMode = "auto";
    private String mSceneMode;

    private final Handler mHandler = new MainHandler();
    private CameraHeadUpDisplay mHeadUpDisplay;

    // multiple cameras support
    private int mNumberOfCameras;

    private int mCameraId;
    private long mResume;
    private boolean mPreviewStatus = false;
    private View pauseView;
    private PreviewFrameLayout mPreviewFrameLayout;
    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESTART_PREVIEW: {
                    // fixed bug 20995 start
//                    restartPreview();
                    // we get restart preview result
                    // if restart preview success, then we can click tools bar
                    // otherwise we can't click
                    boolean result = restartPreview();
                    if (mHeadUpDisplay != null) {
                        mHeadUpDisplay.setEnabled(result);
                    }
                    // fixed bug 20995 end
                    if (mJpegPictureCallbackTime != 0) {
                        long now = System.currentTimeMillis();
                        mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                        Log.v(TAG, "mJpegCallbackFinishTime = "
                                + mJpegCallbackFinishTime + "ms");
                        mJpegPictureCallbackTime = 0;
                    }
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
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

                case UPDATE_THUMBNAIL_BUTTON: {
                    Log.v(TAG, "UPDATE_THUMBNAIL_BUTTON");
                    updateThumbnailButton();
                    break;
                }
                case UPDATE_PREVIEW_STATUS: {
                    Log.v(TAG, " resume finished");
                    mPreviewStatus  = true;
                    return;
                }

                case START_PREVIEW_SWITCH_CAMERA :
                    mTrySwitchCamera = false;
                    switchCameraId(mCameraId == 0 ? 1 : 0);
                    break;
            }
        }
    }

    private void resetExposureCompensation() {
        String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_EXPOSURE, "0");
            editor.apply();
            if (mHeadUpDisplay != null) {
                mHeadUpDisplay.reloadPreferences();
            }
        }
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = getContentResolver()
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized) {
            return;
        }

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        mOrientationListener = new MyOrientationEventListener(Camera.this);
        mOrientationListener.enable();

        // Initialize location sevice.
        mLocationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        mRecordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        if (mRecordLocation) startReceivingLocationUpdates();

        keepMediaProviderInstance();
        checkStorage();

        // Initialize last picture button.
        mContentResolver = getContentResolver();
        if (!mIsImageCaptureIntent)  {
            findViewById(R.id.camera_switch).setOnClickListener(this);
            mLastPictureButton =
                    (ImageView) findViewById(R.id.review_thumbnail);
            mLastPictureButton.setOnClickListener(this);
            mThumbController = new ThumbnailController(
                    getResources(), mLastPictureButton, mContentResolver);
            mThumbController.loadData(ImageManager.getLastImageThumbPath());
            // Update last image thumbnail.
            updateThumbnailButton();
        }

        // Initialize shutter button.
        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.setVisibility(View.VISIBLE);

        updateFocusIndicator();

        initializeScreenBrightness();
        installIntentFilter();
        initializeFocusTone();
        initializeZoom();
        mHeadUpDisplay = new CameraHeadUpDisplay(this);
        mHeadUpDisplay.setListener(new MyHeadUpDisplayListener());
        initializeHeadUpDisplay();
        mFirstTimeInitialized = true;
        changeHeadUpDisplayState();
        addIdleHandler();
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            public boolean queueIdle() {
                ImageManager.ensureOSXCompatibleFolder();
                return false;
            }
        });
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        if (!mThumbController.isUriValid() && mPicturesRemaining >= 0) {
//            updateLastImage();
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
                       ImageManager.INCLUDE_IMAGES,
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

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        mOrientationListener.enable();

        // Start location update if needed.
        mRecordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        if (mRecordLocation) startReceivingLocationUpdates();

        installIntentFilter();
        initializeFocusTone();
        initializeZoom();
        changeHeadUpDisplayState();

        keepMediaProviderInstance();
        checkStorage();

        if (!mIsImageCaptureIntent) {
            updateThumbnailButton();
        }
    }

    private void initializeZoom() {
        if (mCameraDevice == null) return;
        if (!mParameters.isZoomSupported()) return;

        // Maximum zoom value may change after preview size is set. Get the
        // latest parameters here.
        mParameters = mCameraDevice.getParameters();
        mZoomMax = mParameters.getMaxZoom();

        mSmoothZoomSupported = mParameters.isSmoothZoomSupported();
        //mGestureDetector = new GestureDetector(this, new ZoomGestureListener());

        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    private void onZoomValueChanged(int index) {
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
            mZoomValue = index;
            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);
        }
    }

    private float[] getZoomRatios() {
        if(!mParameters.isZoomSupported()) return null;
        List<Integer> zoomRatios = mParameters.getZoomRatios();

        float result[] = new float[zoomRatios.size()];
        for (int i = 0, n = result.length; i < n; ++i) {
            result[i] = (float) zoomRatios.get(i) / 100f;
        }
        return result;
    }

/*    private class ZoomGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Perform zoom only when preview is started and snapshot is not in
            // progress.
            if (mPausing || !isCameraIdle() || !mPreviewing
                    || mZoomState != ZOOM_STOPPED) {
                return false;
            }

            if (mZoomValue < mZoomMax) {
                // Zoom in to the maximum.
                mZoomValue = mZoomMax;
            } else {
                mZoomValue = 0;
            }

            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);

            mHeadUpDisplay.setZoomIndex(mZoomValue);
            return true;
        }
    }*/

/*    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (!super.dispatchTouchEvent(m) && mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(m);
        }
        return true;
    }*/

    LocationListener [] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_CHECKING)) {
                checkStorage();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                Log.d(TAG, "scaned finished");
                checkStorage();
                if (!mIsImageCaptureIntent)  {
                    updateThumbnailButton();
                    if(mThumbController.getUri() == null){
                        mHandler.sendEmptyMessageDelayed(UPDATE_THUMBNAIL_BUTTON, 60000);
                    }
                }
            }
        }
    };

    private class LocationListener
            implements android.location.LocationListener {
        Location mLastLocation;
        boolean mValid = false;
        String mProvider;

        public LocationListener(String provider) {
            mProvider = provider;
            mLastLocation = new Location(mProvider);
        }

        public void onLocationChanged(Location newLocation) {
            Log.v(TAG, "onLocationChanged");
            if (newLocation.getLatitude() == 0.0
                    && newLocation.getLongitude() == 0.0) {
                // Hack to filter out 0.0,0.0 locations
                return;
            }
            // If GPS is available before start camera, we won't get status
            // update so update GPS indicator when we receive data.
            if (mRecordLocation
                    && LocationManager.GPS_PROVIDER.equals(mProvider)) {
                if (mHeadUpDisplay != null) {
                    mHeadUpDisplay.setGpsHasSignal(true);
                }
            }
            mLastLocation.set(newLocation);
            mValid = true;
        }

        public void onProviderEnabled(String provider) {
            Log.v(TAG, "onProviderEnabled: provider = " + provider);
        }

        public void onProviderDisabled(String provider) {
            Log.v(TAG, "onProviderDisabled: provider = " + provider);
            mValid = false;
        }

        public void onStatusChanged(
                String provider, int status, Bundle extras) {
            Log.v(TAG, "onStatusChanged: status = " + status);
            switch(status) {
                case LocationProvider.OUT_OF_SERVICE:
                case LocationProvider.TEMPORARILY_UNAVAILABLE: {
                    mValid = false;
                    if (mRecordLocation &&
                            LocationManager.GPS_PROVIDER.equals(provider)) {
                        if (mHeadUpDisplay != null) {
                            mHeadUpDisplay.setGpsHasSignal(false);
                        }
                    }
                    break;
                }
            }
        }

        public Location current() {
            return mValid ? mLastLocation : null;
        }
    }

    private final class ShutterCallback
            implements android.hardware.Camera.ShutterCallback {
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
            clearFocusState();
            mFocusRectangle.setVisibility(View.GONE);
            mFocusRectangle.reset();
            mShutterButton.setBackgroundResource(R.drawable.btn_shutter);
        }
    }

    private final class PostViewPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] data, android.hardware.Camera camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] rawData, android.hardware.Camera camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;
        // fixed bug 20611 start
        // this property abandoned, because the audio in the "Framework" in a start
//        private final int mCaptureCount = getCaptureCount();
//        private final MediaPlayer mMediaPlayer = createMediaPlayer();
        // fixed bug 20611 end

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        // fixed bug 20611 start
        // this method abandoned, because the audio in the "Framework" in a start
//        private int getCaptureCount() {
//            int result = 1;
//            String str_def_count =
//                Camera.this.getString(
//                    R.string.pref_camera_continuous_capture_default);
//            String str_para_count = null;
//
//            if (str_def_count != null)
//                result = Integer.parseInt(str_def_count);
//            if (mParameters != null) {
//                str_para_count =
//                    mParameters.get(CameraSettings.KEY_CAPTURE_MODE);
//                if (str_para_count != null)
//                    result = Integer.parseInt(str_para_count);
//            }
//            Log.d(TAG, "getCaptureCount() result = " + result + " --- str_def_count == " + str_def_count + " --- str_para_count == " + str_para_count);
//            return result;
//        }
        // fixed bug 20611 end

        // fixed bug 20611 start
        // this method abandoned, because the audio in the "Framework" in a start
//        private MediaPlayer createMediaPlayer() {
//            MediaPlayer result = null;
//            if (mMediaPlayer == null) {
//                /*result = MediaPlayer.create(
//                    Camera.this, CameraSettings.AUDIO_CAMERA_CLICK_URI);*/
//                //fixed bug 15097
//                try {
//                    result = new MediaPlayer();
//                    result.setDataSource(Camera.this,
//                            CameraSettings.AUDIO_CAMERA_CLICK_URI);
//                    result.setAudioStreamType(AudioManager.STREAM_SYSTEM_ENFORCED);
//                    result.prepare();
//                    result.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//                        @Override
//                        public void onCompletion(MediaPlayer mp) {
//                            if (mp != null && mContinuousCaptureCount <= 0) {
//                                mp.release();
//                            }
//                        }
//                    });
//                } catch (Exception e) {
//                    Log.d(TAG, "createMediaPlayer() :" + e.toString());
//                }
//            } else {
//                result = mMediaPlayer;
//            }
//            return result;
//        }
        // fixed bug 20611 end

        public void onPictureTaken(
                final byte [] jpegData, final android.hardware.Camera camera) {
            // fixed bug 20611 start
            // this logic abandoned, because the audio in the "Framework" in a start
//            boolean b_start_audio = (mCaptureCount != mContinuousCaptureCount--);
//            Log.d(TAG, "mContinuousCaptureCount == " + mContinuousCaptureCount + " --- mPausing == " + mPausing + " --- b_start_audio == " + b_start_audio);
//            if (mPausing || mContinuousCaptureCount < 0) return;
            Log.d(TAG, "mContinuousCaptureCount == " + mContinuousCaptureCount + " --- mPausing == " + mPausing);
            if (--mContinuousCaptureCount < 0 || mPausing) return;
            // fixed bug 20611 end

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
            // fixed bug 20995 start
            // move code logic to mHandler "RESTART_PREVIEW" code block,
            // because we must start preview success, then we can click 
            // tools bar
            // fixed bug 17159
//            if(mContinuousCaptureCount == 0 && mHeadUpDisplay!= null){
//                mHeadUpDisplay.setEnabled(true);
//            }
            // fixed bug 20995 end

            // fixed bug 20611 start
            // this logic abandoned, because the audio in the "Framework" in a start
            // start audio
//            if (b_start_audio && mMediaPlayer != null){
//                mMediaPlayer.start();
//            }
            // fixed bug 20611 end

//            if (!mIsImageCaptureIntent) {
//                // We want to show the taken picture for a while, so we wait
//                // for at least 1.2 second before restarting the preview.
//                long delay = 1200 - mPictureDisplayedToJpegCallbackTime;
//                if (delay < 0) {
//                    restartPreview();
//                } else {
//                    mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, delay);
//                }
//            }
            if (!mIsImageCaptureIntent) {
                // We want to show the taken picture for a while, so we wait
                // for at least 1.2 second before restarting the preview.
                long delay = 1200 - mPictureDisplayedToJpegCallbackTime;
                if (delay < 0) delay = 0;
                if (mContinuousCaptureCount <= 0) {
                    mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, delay);
                }
            }

//            mImageCapture.storeImage(jpegData, camera, mLocation);
            if ((jpegData != null) && (jpegData.length != 0)) {
                mImageCapture.storeImage(jpegData, camera, mLocation);
            }

            // Calculate this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card in
            // the mean time and fill it, but that could have happened between the
            // shutter press and saving the JPEG too.
            calculatePicturesRemaining();

            if (mPicturesRemaining < 1) {
                updateStorageHint(mPicturesRemaining);
            }

            if (mContinuousCaptureCount <= 0
                    && !mHandler.hasMessages(RESTART_PREVIEW)) {
                long now = System.currentTimeMillis();
                mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                Log.v(TAG, "mJpegCallbackFinishTime = "
                        + mJpegCallbackFinishTime + "ms");
                mJpegPictureCallbackTime = 0;
                if (!mIsImageCaptureIntent)
                    mHandler.sendEmptyMessage(RESTART_PREVIEW);
            }
        }
    }

    private final class AutoFocusCallback
            implements android.hardware.Camera.AutoFocusCallback {
        public void onAutoFocus(
                boolean focused, android.hardware.Camera camera) {
            mFocusCallbackTime = System.currentTimeMillis();
            mAutoFocusTime = mFocusCallbackTime - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            if (mFocusState == FOCUSING_SNAP_ON_FINISH) {
                // Take the picture no matter focus succeeds or fails. No need
                // to play the AF sound if we're about to play the shutter
                // sound.
                if (focused) {
                    mFocusState = FOCUS_SUCCESS;
                } else {
                    mFocusState = FOCUS_FAIL;
                }
                mImageCapture.onSnap();
            } else if (mFocusState == FOCUSING) {
                // User is half-pressing the focus key. Play the focus tone.
                // Do not take the picture now.
                ToneGenerator tg = mFocusToneGenerator;
                if (focused) {
                    if (tg != null) {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2);
                    }
                    mFocusState = FOCUS_SUCCESS;
                } else {
                    if (tg != null) {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                    }
                    mFocusState = FOCUS_FAIL;
                }
            } else if (mFocusState == FOCUS_NOT_STARTED) {
                // User has released the focus key before focus completes.
                // Do nothing.
            }
            updateFocusIndicator();
        }
    }

    // fixed bug 18989, change class to "private final class"
    private final class ErrorCallback
        implements android.hardware.Camera.ErrorCallback {
        // fixed bug 18989 start
        private static final String TAG = "CameraErrorCallback";
        // "0x80000000" see framework/base/include/utils/Errors.h
        private static final int ERR_CODE_UNKNOW = 0x80000000;
        private static final int ERR_CODE_SERVER_DIED =
            android.hardware.Camera.CAMERA_ERROR_SERVER_DIED;
        // fixed bug 18989 end
        public void onError(int err_code, android.hardware.Camera camera) {
            // fixed bug 18989 start
            Log.d(TAG, "call back err_code = " + err_code);
            switch (err_code) {
                case ERR_CODE_SERVER_DIED :
                    Log.v(TAG, "media server died");
                    mMediaServerDied = true;
                    break;
                case ERR_CODE_UNKNOW :
                    Log.d(TAG, "native unknow error, current mStatus = " + mStatus);
                    // if "mStatus" is "SNAPSHOT_IN_PROGRESS" state, then native
                    // capture error, so we must restart preview
                    if (mStatus == SNAPSHOT_IN_PROGRESS) {
                        Log.d(TAG, "native capture error");
                        if (mHandler != null &&
                                !mHandler.hasMessages(RESTART_PREVIEW)) {
                            Toast.makeText(
                                Camera.this,
                                R.string.notice_capture_call_back_error,
                                Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "send restart preview in onError() method");
                            // we must set "mStatus" is IDLE, because "RESTART_PREVIEW"
                            // has delay, if we don't set IDLE, so focus has error;
                            mStatus = IDLE;
                            cancelAutoFocus();
                            mHandler.sendEmptyMessage(RESTART_PREVIEW);
                        }
                    }
                    break;
            }
            // fixed bug 18989 end
        }
    }

    private final class ZoomListener
            implements android.hardware.Camera.OnZoomChangeListener {
        public void onZoomChange(
                int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped="+ stopped);
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
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }

    private void setFocusP(){
        // Parameters of the transmission of contacts coordinate, in order to
        // minimize framework layer code changes, use Size instead of points.
        // Size of the List is the number of contacts.

        //*************
        int width = mFocusRectangle.getWidth();
        int height = mFocusRectangle.getHeight();
        Log.v(TAG, "capture: mFocusRectangle width = " + width + " , height: " + height);
        Size viewSize  = mCameraDevice.new Size(width, height);
        Log.d(TAG, "mParameters.setAutoFocusViewSize : " + viewSize.height + " " + viewSize.width);
        mParameters.setAutoFocusViewSize(viewSize);
        Log.d(TAG, "mParameters.setAutoFocusViewSize : end");
        ArrayList<android.hardware.Camera.Size> zones = new ArrayList<android.hardware.Camera.Size>();
        ArrayList<Pointer> pointers = mFocusRectangle.getPointers();

        if(!mFocusMode.equals(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO_MULTI)){
            Pointer pointer = pointers.get(0);
            Size size = mCameraDevice.new Size((int) pointer.x, (int) pointer.y);
            Log.d(TAG, "focus zones : x = " + pointer.x + " : y = " + pointer.y);
            zones.add(size);
        }else{
            for(int i = 0; i < pointers.size(); ++i){
                Pointer pointer = pointers.get(i);
                Size size = mCameraDevice.new Size((int) pointer.x, (int) pointer.y);
                Log.d(TAG, "focus zones : x = " + pointer.x + " : y = " + pointer.y);
                zones.add(size);
            }
        }
        Log.d(TAG, "mParameters.setAutoFocusZones : " + zones.size());
        mParameters.setAutoFocusZones(zones);
        Log.d(TAG, "mParameters.setAutoFocusZones : end");
        //*************
    }

    private void setFocusParameters(){
        setFocusP();
        mCameraDevice.setParameters(mParameters);
    }

    private class ImageCapture {

        private Uri mLastContentUri;

        byte[] mCaptureOnlyData;

        // Returns the rotation degree in the jpeg header.
        private int storeImage(byte[] data, Location loc) {
            try {
                long dateTaken = System.currentTimeMillis();
                String title = createName(dateTaken);
                String filename = title + ".jpg";
                int[] degree = new int[1];
                mLastContentUri = ImageManager.addImage(
                        mContentResolver,
                        title,
                        dateTaken,
                        loc, // location from gps/network
                        // fixed bug 16191, 16230 start
                        ImageManager.getCameraImageBucketPath(), filename,
                        // fixed bug 16191, 16230 end
                        null, data,
                        degree);
                return degree[0];
            } catch (Exception ex) {
                Log.e(TAG, "Exception while compressing image.", ex);
                return 0;
            }
        }

        public void storeImage(final byte[] data,
                android.hardware.Camera camera, Location loc) {
            if (!mIsImageCaptureIntent) {
                int degree = storeImage(data, loc);
                sendBroadcast(new Intent(
                        "com.android.camera.NEW_PICTURE", mLastContentUri));
                setLastPictureThumb(data, degree,
                        mImageCapture.getLastCaptureUri());
                mThumbController.updateDisplayIfNeeded();
            } else {
                mCaptureOnlyData = data;
                showPostCaptureAlert();
            }
        }

        /**
         * Initiate the capture of an image.
         */
        public void initiate() {
            if (mCameraDevice == null) {
                return;
            }

            capture();
        }

        public Uri getLastCaptureUri() {
            return mLastContentUri;
        }

        public byte[] getLastCaptureData() {
            return mCaptureOnlyData;
        }

        private void capture() {
            mCaptureOnlyData = null;
            mParameters.set("sensororientation", 0);
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
            mParameters.setRotation(rotation);

            // Clear previous GPS location from the parameters.
            mParameters.removeGpsData();

            // We always encode GpsTimeStamp
            mParameters.setGpsTimestamp(System.currentTimeMillis() / 1000);

            // Set GPS location.
            Location loc = mRecordLocation ? getCurrentLocation() : null;
            if (loc != null) {
                double lat = loc.getLatitude();
                double lon = loc.getLongitude();
                boolean hasLatLon = (lat != 0.0d) || (lon != 0.0d);

                if (hasLatLon) {
                    mParameters.setGpsLatitude(lat);
                    mParameters.setGpsLongitude(lon);
                    mParameters.setGpsProcessingMethod(loc.getProvider().toUpperCase());
                    if (loc.hasAltitude()) {
                        mParameters.setGpsAltitude(loc.getAltitude());
                    } else {
                        // for NETWORK_PROVIDER location provider, we may have
                        // no altitude information, but the driver needs it, so
                        // we fake one.
                        mParameters.setGpsAltitude(0);
                    }
                    if (loc.getTime() != 0) {
                        // Location.getTime() is UTC in milliseconds.
                        // gps-timestamp is UTC in seconds.
                        long utcTimeSeconds = loc.getTime() / 1000;
                        mParameters.setGpsTimestamp(utcTimeSeconds);
                    }
                } else {
                    loc = null;
                }
            }

            mCameraDevice.setParameters(mParameters);

            mPreviewing = false;
            try{
                mCameraDevice.takePicture(mShutterCallback, mRawPictureCallback,
                        mPostViewPictureCallback, new JpegPictureCallback(loc));
            }catch(RuntimeException e){
                if("takePicture failed".equals(e.getMessage())) {
                    Log.d(TAG, "baofeng is running,take photo error!!!!!!!");
                    Toast.makeText(Camera.this, Camera.this.getString(R.string.camera_memory_low), Toast.LENGTH_SHORT).show();
                    clearFocusState();
                    mHeadUpDisplay.setEnabled(true);
                    mFocusRectangle.reset();
                    mShutterButton.setBackgroundResource(R.drawable.btn_shutter);
                    mHandler.sendEmptyMessage(RESTART_PREVIEW);
                } else {
                    throw e;
                }
            }
        }

        public void onSnap() {
            // If we are already in the middle of taking a snapshot then ignore.
            if (mPausing || mStatus == SNAPSHOT_IN_PROGRESS) {
                return;
            }
            mCaptureStartTime = System.currentTimeMillis();
            mPostViewPictureCallbackTime = 0;
            mHeadUpDisplay.setEnabled(false);
            mStatus = SNAPSHOT_IN_PROGRESS;

            mImageCapture.initiate();
        }

        private void clearLastData() {
            mCaptureOnlyData = null;
        }
    }

    private boolean saveDataToFile(String filePath, byte[] data) {
        FileOutputStream f = null;
        try {
            f = new FileOutputStream(filePath);
            f.write(data);
        } catch (IOException e) {
            return false;
        } finally {
            MenuHelper.closeSilently(f);
        }
        return true;
    }

    private void setLastPictureThumb(byte[] data, int degree, Uri uri) {
        //fixed bug 14101
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inSampleSize = 16;
        Bitmap lastPictureThumb = null;
        try {
            lastPictureThumb = BitmapFactory.decodeByteArray(data, 0,
                    data.length, options);
        } catch (OutOfMemoryError ex) {
            Log.d(TAG, "setLastPictureThumb:OutOfMemoryError");
            options.inSampleSize = 32;
            lastPictureThumb = BitmapFactory.decodeByteArray(data, 0,
                    data.length, options);
        }
        lastPictureThumb = Util.rotate(lastPictureThumb, degree);
        mThumbController.setData(uri, lastPictureThumb);
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.image_file_name_format));

        return dateFormat.format(date);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.camera);
        Log.v(TAG, "onCreate");

        mFocusRectangle = (FocusRectangle) findViewById(R.id.focus_rectangle);
        mFocusRectangle.setCamera(this);
        mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);
        //fixed bug for 15012
        mSurfaceView.setOnTouchListener(this);
        mFocusRectangle.setOnTouchListener(this);
    //wxz20110302: test for multi cameras.
    /*mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
    mCameraId = 1;
    CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
    CameraHolder.instance().setCameraId(mCameraId);
    */

        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();

        // we need to reset exposure for the preview
        //fixed 17496
        //resetExposureCompensation();
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

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceChanged / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mIsImageCaptureIntent = isImageCaptureIntent();
        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        }

        LayoutInflater inflater = getLayoutInflater();

        ViewGroup rootView = (ViewGroup) findViewById(R.id.camera);
        if (mIsImageCaptureIntent) {
            View controlBar = inflater.inflate(
                    R.layout.attach_camera_control, rootView);
            controlBar.findViewById(R.id.btn_cancel).setOnClickListener(this);
            controlBar.findViewById(R.id.btn_retake).setOnClickListener(this);
            controlBar.findViewById(R.id.btn_done).setOnClickListener(this);
        } else {
            inflater.inflate(R.layout.camera_control, rootView);
            mSwitcher = ((Switcher) findViewById(R.id.camera_switch));
            mSwitcher.setOnSwitchListener(this);
            mSwitcher.addTouchView(findViewById(R.id.camera_switch_set));
        }

        initPauseView(inflater, rootView);
        hidePauseView();
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
    }

    private void initPauseView(LayoutInflater inflater, ViewGroup rootView) {
        pauseView = inflater.inflate(R.layout.pause_view, null);
        rootView.addView(pauseView);
    }

    private void hidePauseView(){
        pauseView.setVisibility(View.INVISIBLE);
    }

    private void showPauseView(){
        pauseView.setVisibility(View.VISIBLE);
    }

    private void changeHeadUpDisplayState() {
        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && !mPausing && mFirstTimeInitialized) {
            if (mGLRootView == null) attachHeadUpDisplay();
        } else if (mGLRootView != null) {
            detachHeadUpDisplay();
        }
    }

    private void overrideHudSettings(final String flashMode,
            final String whiteBalance, final String focusMode) {
        mHeadUpDisplay.overrideSettings(
                CameraSettings.KEY_FLASH_MODE, flashMode,
                CameraSettings.KEY_WHITE_BALANCE, whiteBalance
                /*CameraSettings.KEY_FOCUS_MODE, focusMode*/);
    }

    private void updateSceneModeInHud() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            //fixed bug for 14455
            mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            mParameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
            overrideHudSettings(mParameters.getFlashMode(),
                    mParameters.getWhiteBalance(), mParameters.getFocusMode());
        } else {
            overrideHudSettings(null, null, null);
        }
    }

    private void initializeHeadUpDisplay() {
        CameraSettings settings = new CameraSettings(this, mInitialParams,
                CameraHolder.instance().getCameraInfo());
        mHeadUpDisplay.initialize(this,
                settings.getPreferenceGroup(R.xml.camera_preferences),
                getZoomRatios(), mOrientationCompensation, mIsImageCaptureIntent);
        if (mParameters.isZoomSupported()) {
            mHeadUpDisplay.setZoomListener(new ZoomControllerListener() {
                public void onZoomChanged(
                        int index, float ratio, boolean isMoving) {
                    onZoomValueChanged(index);
                }
            });
        }
        updateSceneModeInHud();
    }

    private void attachHeadUpDisplay() {
        mHeadUpDisplay.setOrientation(mOrientationCompensation);
        if (mParameters.isZoomSupported()) {
            mHeadUpDisplay.setZoomIndex(mZoomValue);
        }
        FrameLayout frame = (FrameLayout) findViewById(R.id.camera);
        mGLRootView = new GLRootView(this);
        FrameLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.RIGHT;
        params.rightMargin = 140;
        // fixed bug 16768
        if (mPreviewFrameLayout != null && mPreviewFrameLayout.getDensity() == 1.0f) {
            params.rightMargin = (int) (110 * 1.0f / 1.5f);
        }
        // end 16768
        params.topMargin = 7;
        params.bottomMargin = 7;
        mGLRootView.setLayoutParams(params);
        mGLRootView.setContentPane(mHeadUpDisplay);
        frame.addView(mGLRootView);
    }

    private void detachHeadUpDisplay() {
        mHeadUpDisplay.setGpsHasSignal(false);
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
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = roundOrientation(orientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(Camera.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                if (!mIsImageCaptureIntent) {
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
    public void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        if (!mIsImageCaptureIntent) {
            mSwitcher.setSwitch(SWITCH_CAMERA);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    private void checkStorage() {
        calculatePicturesRemaining();
        updateStorageHint(mPicturesRemaining);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_retake:
                // fixed bug 21208 start
                // we're must waiting start preview success, we can hide alert
                // and set tools bar enabled; here set tools bar enable, because
                // of bug 20995, move code logic to mHandler "RESTART_PREVIEW";
//                hidePostCaptureAlert();
//                restartPreview();
                boolean result = restartPreview();
                if (result) {
                    hidePostCaptureAlert();
                    if (mHeadUpDisplay != null)
                        mHeadUpDisplay.setEnabled(result);
                }
                // fixed bug 21208 end
                break;
            case R.id.review_thumbnail:
                if (isCameraIdle()) {
                    viewLastImage();
                }
                break;
            case R.id.btn_done:
                doAttach();
                break;
            case R.id.btn_cancel:
                doCancel();
        }
    }

    private Bitmap createCaptureBitmap(byte[] data) {
        // This is really stupid...we just want to read the orientation in
        // the jpeg header.
        String filepath = ImageManager.getTempJpegPath();
        int degree = 0;
        if (saveDataToFile(filepath, data)) {
            degree = ImageManager.getExifOrientation(filepath);
            new File(filepath).delete();
        }

        // Limit to 50k pixels so we can return it in the intent.
        Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
        bitmap = Util.rotate(bitmap, degree);
        return bitmap;
    }

    private void doAttach() {
        // fixed bug 20053 start
        Log.d(TAG, "doAttach()");
        if (mPausing) return;
        // get last capture data
        byte[] data = mImageCapture.getLastCaptureData();
        // declare ret_result, default is RESULT_OK;
        int ret_result = RESULT_OK;

        // First handle the no crop case -- just return the value.  If the
        // caller specifies a "save uri" then write the data to it's
        // stream. Otherwise, pass back a scaled down version of the bitmap
        // directly in the extras.
        if (mCropValue == null) {
            Log.d(TAG,
                String.format("current request uri is '%s'", mSaveUri));
            if (mSaveUri != null) {
                Log.d(TAG, "save to out put stream by mSaveUri");
                OutputStream out = null;
                try {
                    out = mContentResolver.openOutputStream(mSaveUri);
                    out.write(data);
                } catch (NullPointerException e) {
                    ret_result = RESULT_CANCELED;
                    Log.d(TAG, "save to out put stream by mSaveUri is failed, because has a NullPointerException", e);
                } catch (IOException e) {
                    ret_result = RESULT_CANCELED;
                    Log.d(TAG, "save to out put stream by mSaveUri is failed, because has a IOException", e);
                } catch (Exception e) {
                    ret_result = RESULT_CANCELED;
                    Log.d(TAG, "save to out put stream by mSaveUri is failed, because has a Exception", e);
                } finally {
                    Util.closeSilently(out);
                    setResult(ret_result);
                    Log.d(TAG,
                        String.format("save to out put stream by mSaveUri finished, so return result is %d", ret_result));
                }
            } else {
                Log.d(TAG, "save to Bitmap object by data");
                Bitmap bitmap = createCaptureBitmap(data);
                Intent intent_data = new Intent("inline-data");
                intent_data.putExtra("data", bitmap);
                setResult(ret_result, intent_data);
                Log.d(TAG,
                    String.format("save to Bitmap object by data finished, bitmap = %s, data = %s",
                        new Object[] { bitmap, data }));
            }
            // if current within (mCropValue == null) block,
            // so anyway we must close activity.
            finish();
        } else {
            // Save the image to a temp file and invoke the cropper
            Log.d(TAG, "save to temp file");
            Uri uri = null;
            FileOutputStream out = null;
            try {
                // validate temp file exists ?
                // if exists, then delete temp file
                File file = getFileStreamPath(sTempCropFilename);
                if (file.exists()) {
                    file.delete();
                }
                // open file out put stream
                out = openFileOutput(sTempCropFilename, 0);
                out.write(data);
                // get uri by file
                uri = Uri.fromFile(file);
                Log.d(TAG,
                    String.format("save to temp file success, file = %s, uri = %s",
                        new Object[] { file, uri }));
            } catch (FileNotFoundException e) {
                ret_result = RESULT_CANCELED;
                Log.d(TAG, "save to temp file failed, because has a FileNotFoundException", e);
            } catch (IOException e) {
                ret_result = RESULT_CANCELED;
                Log.d(TAG, "save to temp file failed, because has a IOException", e);
            } catch (Exception e) {
                ret_result = RESULT_CANCELED;
                Log.d(TAG, "save to temp file failed, because has a Exception", e);
            } finally {
                Util.closeSilently(out);
                Log.d(TAG,
                    String.format("save to temp file finished, so return result is %d", ret_result));
                if (RESULT_CANCELED == ret_result) {
                    finish();
                    return;
                }
            }

            // save information to bundle and start activity for result
            Bundle extras = new Bundle();
            // put circle value
            if ("circle".equals(mCropValue)) {
                extras.putString("circleCrop", "true");
            }
            // put info by mSaveUri
            if (mSaveUri != null) {
                extras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                extras.putBoolean("return-data", true);
            }

            // start activity
            Intent intent_data = new Intent("com.android.camera.action.CROP");
            intent_data.setData(uri);
            intent_data.putExtras(extras);
            Log.d(TAG,
                String.format("start activity for result, intent_data = %s, extras = %s",
                    new Object[] { intent_data, extras }));
            startActivityForResult(intent_data, CROP_MSG);
        }
        // fixed bug 20053 end
    }

    private void doCancel() {
        setResult(RESULT_CANCELED, new Intent());
        finish();
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
//        if (mPausing) {
//            return;
//        }
//
//        if(isFocusing){
//            return;
//        }
        if (mPausing || isFocusing) return;

//        switch (button.getId()) {
//            case R.id.shutter_button:
//                if(canTakePicture()){
//                    if(pressed){
//                        if(isCanflash())
//                            mCameraDevice.startFlash();
//                        if(isNeedFocus())
//                            doFocus(true);
//                    }else{
//                        //doFocus(pressed);
//                        //doSnapForCameraButton();
//                    }
//                }
//                else
//                    mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
//                break;
//        }
        // fixed bug 20728 start
        if (mHeadUpDisplay != null && mGLRootView != null){
            mHeadUpDisplay.collapse();
        }
        // fixed bug 20728 end
        if (button.getId() == R.id.shutter_button) {
            if (canTakePicture() && pressed) {
                if (isCanflash()) mCameraDevice.startFlash();
                if (isNeedFocus()) doFocus(true);
            } else {
                mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
            }
        }
    }

    public void onShutterButtonClick(ShutterButton button) {
        Log.v(TAG, "onShutterButtonClick");
//        if (mPausing) {
//            return;
//        }
//
//        if(isFocusing){
//            return;
//        }
        if (mPausing || isFocusing) return;

//        switch (button.getId()) {
//            case R.id.shutter_button:
//                doSnap();
//                break;
//        }
        if (button.getId() == R.id.shutter_button) {
            setContinuousCaptureCount();
            doSnap();
            // fixed bug 20995 start
            // remove code, because the logic in the "onSnap()" was exists
            //fixed 17159
//            if(mHeadUpDisplay !=null){
//                mHeadUpDisplay.setEnabled(false);
//            }
            // fixed bug 20995 end
        }
    }

    private int mContinuousCaptureCount = 0;
    private void setContinuousCaptureCount() {
        if (!canTakePicture()) return;
        int result = 0;
        boolean need_reset = false;
        final int default_capture_count =
            Integer.valueOf(getString(R.string.pref_camera_continuous_capture_default));

        try {
            Log.d(TAG, "start set capture-mode parameter");
            // ensure capture-mode parameter in mPreferences and mParameters same
            int pref_capture_count = 0, para_capture_count = 0;
            String str_pref_count = null, str_para_count = null;

            // validate mPreferences
            if (mPreferences != null
                    && (str_pref_count = mPreferences.getString(
                            CameraSettings.KEY_CONTINUOUS_CAPTURE,
                                String.valueOf(default_capture_count))) != null) {
                pref_capture_count = Integer.parseInt(str_pref_count);
            }
            Log.d(TAG, "mPreferences.KEY_CONTINUOUS_CAPTURE == " + pref_capture_count);

            // validate mParameters
            if (mParameters != null
                    && (str_para_count = mParameters.get(
                            CameraSettings.KEY_CAPTURE_MODE)) != null) {
                para_capture_count = Integer.parseInt(str_para_count);
            } else if (str_para_count == null) {
                para_capture_count = default_capture_count;
            }
            Log.d(TAG, "mParameters.KEY_CAPTURE_MODE == " + para_capture_count);

            // update capture-mode parameter
            need_reset = (pref_capture_count != para_capture_count);
            result = pref_capture_count;
            Log.d(TAG, "end set capture-mode parameter");
        } catch (Exception e) {
            need_reset = false;
            Log.e(TAG, "set capture-mode faild;", e);
        } finally {
            mContinuousCaptureCount = (mIsImageCaptureIntent ? 1 : result);
            if (need_reset) {
                mParameters.set(
                    CameraSettings.KEY_CAPTURE_MODE,
                    String.valueOf(mContinuousCaptureCount));
                updateCameraParameterPictureSize(need_reset);
            }
            Log.d(TAG, "print need_reset == " + need_reset + " --- mContinuousCaptureCount == " + mContinuousCaptureCount);
        }
    }

    // fixed bug 16455 start
    private String getPictureSizeByCaptureMode() {
        Log.d(TAG, "----- getPictureSizeByCaptureMode() -----");
        String result = null;
        boolean has_error = false;
        // get default result
        final String saved_picture_size =
            mPreferences.getString(CameraSettings.KEY_PICTURE_SIZE, null);
        try {
            // get support picture size list
            List<Size> sizeArray = mParameters.getSupportedPictureSizes();
            // sort list --> Z --> A
            Collections.sort(sizeArray, new java.util.Comparator<Size>() {
                @Override
                public int compare(Size item1, Size item2) {
                    int result = 0;
                    long item_1_res = (item1.width * item1.height);
                    long item_2_res = (item2.width * item2.height);
                    if (item_1_res < item_2_res) result = 1;
                    else if (item_1_res > item_2_res) result = -1;
                    return result;
                }
            });

            // set default picture size
            final int default_size =
                (mContinuousCaptureCount > 1 ? 2000000 : 5000000);
            final int array_size = sizeArray.size();
            // found picture size by default
            for (int i = 0; i < array_size; i++) {
                Size item = sizeArray.get(i);
                // found first "item" less-than "default_size" item
                if ((item.width * item.height) < default_size) {
                    i = (--i <= 0 ? 0 : ++i);
                    item = sizeArray.get(i);
                    result = String.format("%dx%d", new Object[] { item.width, item.height });
                    break;
                }
            }

        } catch (Exception e) {
            has_error = true;
            Log.d(TAG, "getPictureSizeByCaptureMode() error.", e);
        } finally {
            Log.d(TAG,
                String.format("return result = %s, default size = %s",
                    new Object[] { result, saved_picture_size }));
            // if exists error then return default picture size
            if (has_error) result = saved_picture_size;
        }
        return result;
    }
    // fixed bug 16455 end

    private void updateCameraParameterPictureSize(boolean changed) {
        boolean validate = (mPreferences != null && mParameters != null);
        Log.d(TAG, "updateCameraParameterPictureSize validate == " + validate);
        if (validate) {
            String str_picture_size =
                mPreferences.getString(CameraSettings.KEY_PICTURE_SIZE, null);
            // initial picture-size parameters
            if (str_picture_size == null) {
                CameraSettings.initialCameraPictureSize(this, mParameters);
                Log.d(TAG, "mPreferences picture-size initiation");
            } else {
                Log.d(TAG, "mPreferences picture-size, befor size == " + str_picture_size);
                // update picture-size parameter by changed
                if (changed) {
                    // fixed bug 16455 start
//                  String str_tmp_picture_size = getString(
//                      (mContinuousCaptureCount > 1 ?
//                          R.string.pref_camera_picturesize_entry_values_1600x1200 :
//                              R.string.pref_camera_picturesize_entry_values_2592x1944));
                  String str_tmp_picture_size = getPictureSizeByCaptureMode();
				  str_tmp_picture_size = str_picture_size;  //lvyanbing add
                  // fixed bug 16455 end
                    str_picture_size =
                        (str_tmp_picture_size.equals(str_picture_size) ?
                            null : str_tmp_picture_size);
                }
                Log.d(TAG, "changed == " + changed + ", change after size == " + str_picture_size);

                // update
                if (str_picture_size != null) {
                    List<Size> sizeArray = mParameters.getSupportedPictureSizes();
                    if (CameraSettings.setCameraPictureSize(
                            str_picture_size, sizeArray, mParameters, mPreferences)) {
                        Log.d(TAG, "reset picture-size to mParameters and reloadPreferences");
                        // start reload preferences in mPreferences
                        Runnable run_reload = new Runnable() {
                            @Override
                            public void run() {
                                if (mGLRootView != null
                                        && mHeadUpDisplay != null) {
                                    mHeadUpDisplay.reloadPreferences();
                                }
                            }
                        };
                        mHandler.post(run_reload);
                    }
                }
            }
        }
    }

    private OnScreenHint mStorageHint;

    private void updateStorageHint(int remaining) {
        // fixed not storage hint start
        int res = -1;

        // validate "storage" state is "MEDIA_CHECKING"
        if (MenuHelper.NO_STORAGE_ERROR == remaining) {
            String storage_state = Environment.getExternalStorageState();
            if (!ImageManager.getIsInternalBucket() &&
                    Environment.MEDIA_CHECKING.equals(storage_state)) {
                res = R.string.preparing_sd;
            }
        }
        // validate "storage" state is "CANNOT_STAT_ERROR"
        else if (MenuHelper.CANNOT_STAT_ERROR == remaining) {
            res = R.string.access_storage_fail;
        }
        // validate "storage" state is not space
        else if (remaining < 1) {
            res = R.string.memory_full;
        }

        // we must check memory has storage
        if (MemoryCheck.checkMemory()) {
            res = R.string.memory_full;
        }

        if (res != -1) {
            String str_error = getString(res);
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, str_error);
            } else {
                mStorageHint.setText(str_error);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
//        String noStorageText = null;
//
//        if (remaining == MenuHelper.NO_STORAGE_ERROR) {
//            String state = Environment.getExternalStorageState();
//            if (state == Environment.MEDIA_CHECKING) {
//                noStorageText = getString(R.string.preparing_sd);
//            } else {
//                noStorageText = getString(R.string.no_storage);
//            }
//        } else if (remaining == MenuHelper.CANNOT_STAT_ERROR) {
//            noStorageText = getString(R.string.access_sd_fail);
//        } else if (remaining < 1) {
//            noStorageText = getString(R.string.not_enough_space);
//        }
//
//        if (MemoryCheck.checkMemory()) {
//            noStorageText = getString(R.string.memory_full);
//        }
//
//        if (noStorageText != null) {
//            if (mStorageHint == null) {
//                mStorageHint = OnScreenHint.makeText(this, noStorageText);
//            } else {
//                mStorageHint.setText(noStorageText);
//            }
//            mStorageHint.show();
//        } else if (mStorageHint != null) {
//            mStorageHint.cancel();
//            mStorageHint = null;
//        }
        // fixed not storage hint end
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;
    }

    private void initializeFocusTone() {
        // Initialize focus tone generator.
        try {
            //fixed bug 15310
            mFocusToneGenerator = new ToneGenerator(
                    AudioManager.STREAM_SYSTEM_ENFORCED, FOCUS_BEEP_VOLUME);
        } catch (Throwable ex) {
            Log.w(TAG, "Exception caught while creating tone generator: ", ex);
            mFocusToneGenerator = null;
        }
    }

    private void initializeScreenBrightness() {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        hidePauseView();

        mFocusRectangle.reset();
        cancelAutoFocus();
        clearFocusState();

        mResume = 0;
        mPausing = false;
        mJpegPictureCallbackTime = 0;
//        mZoomValue = 0;
        mImageCapture = new ImageCapture();
        mTrySwitchCameraState = TRY_SWITCH_CAMERA_RESUME;

        // Start the preview if it is not started.
        if (!mPreviewing && !mStartPreviewFail) {
            //fixed 17496
            //resetExposureCompensation();
            if (!restartPreview()) return;
        }

        // add validate // fixed bug 16206, 15800 start
        if (isTvRunning || mStartPreviewFail) return;
        // fixed bug 15206, 15800 end

        if (mSurfaceHolder != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            } else {
                initializeSecondTime();
            }
        }
        keepScreenOnAwhile();

        if(!mPreviewStatus){
            mHandler.sendEmptyMessageDelayed(UPDATE_PREVIEW_STATUS, 500);
        }

        // fixed bug 16095 start
        if (mHeadUpDisplay != null) mHeadUpDisplay.setEnabled(true);
        // fixed bug 16095 end
        mResume = System.currentTimeMillis();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        changeHeadUpDisplayState();
    }

    private static ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    @Override
    protected void onPause() {
        // fixed bug 20569 start
        // add log and format 16734 code
        Log.d(TAG, "onPause()");
        // fixed bug 16734
        if(mResetDialog != null) {
            mResetDialog.cancel();
            mResetDialog = null;
        }
        if (!isSwitchToVideo) {
            showPauseView();
        } else {
            isSwitchToVideo = false;
        }
        // we must waiting GLRootView finished
        if (mGLRootView != null) mGLRootView.onPause();
        // fixed bug 20569 end

        mPausing = true;
        stopPreview();
        // Close the camera now because other activities may need to use it.
        closeCamera();
        resetScreenOn();
        changeHeadUpDisplayState();

        if (mFirstTimeInitialized) {
            mOrientationListener.disable();
            if (!mIsImageCaptureIntent) {
                mThumbController.storeData(
                        ImageManager.getLastImageThumbPath());
            }
            hidePostCaptureAlert();
        }

        if (mDidRegister) {
            unregisterReceiver(mReceiver);
            mDidRegister = false;
        }
        stopReceivingLocationUpdates();

        if (mFocusToneGenerator != null) {
            mFocusToneGenerator.release();
            mFocusToneGenerator = null;
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mImageCapture.clearLastData();
        mImageCapture = null;

        // Remove the messages in the event queue.
        mHandler.removeMessages(RESTART_PREVIEW);
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(UPDATE_LAST_IMAGE);
        Log.d(TAG, "onPause() finished");
        super.onPause();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CROP_MSG: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                setResult(resultCode, intent);
                finish();

                File path = getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    private boolean canTakePicture() {
        boolean isIdle = (mStatus == IDLE && (mFocusState == FOCUS_NOT_STARTED || mFocusState == FOCUS_SUCCESS || mFocusState == FOCUS_FAIL || mFocusState == FOCUSING));
        return isIdle && mPreviewing && (mPicturesRemaining > 0) && !MemoryCheck.checkMemory();
    }

    private boolean canAutoFocus() {
        return isCameraIdle() && mPreviewing && !MemoryCheck.checkMemory() ;
    }

    private void autoFocus() {
        // Initiate autofocus only when preview is started and snapshot is not
        // in progress.
        if (canAutoFocus()) {
            mHeadUpDisplay.setEnabled(false);
            Log.v(TAG, "Start autofocus.");
            if(mFocusRectangle.isMultiFocusModeSupported()){
                setFocusParameters();
            }

            mFocusStartTime = System.currentTimeMillis();
            mFocusState = FOCUSING;
            updateFocusIndicator();
            mCameraDevice.autoFocus(mAutoFocusCallback);
        }
    }

    public void cancelAutoFocus() {
        // User releases half-pressed focus key.
        if (mStatus != SNAPSHOT_IN_PROGRESS && (mFocusState == FOCUSING
                || mFocusState == FOCUS_SUCCESS || mFocusState == FOCUS_FAIL)) {
            Log.v(TAG, "Cancel autofocus.");
            mHeadUpDisplay.setEnabled(true);
            mCameraDevice.cancelAutoFocus();
        }
        if (mFocusState != FOCUSING_SNAP_ON_FINISH) {
            clearFocusState();
        }
    }

    public void clearFocusState() {
        mFocusState = FOCUS_NOT_STARTED;
        //mHeadUpDisplay.setEnabled(true);
        isFocusing = false;
        updateFocusIndicator();
    }

    public void enableMenu() {
        if(null != mHeadUpDisplay)
            mHeadUpDisplay.setEnabled(true);
        isFocusing = false;
        updateFocusIndicator();
    }

    private void updateFocusIndicator() {
        if (mFocusRectangle == null) return;

        if (mFocusState == FOCUSING || mFocusState == FOCUSING_SNAP_ON_FINISH) {
            mFocusRectangle.showStart();
        } else if (mFocusState == FOCUS_SUCCESS) {
            mFocusRectangle.showSuccess();
        } else if (mFocusState == FOCUS_FAIL) {
            mFocusRectangle.showFail();
        } else {
        if(!mFocusRectangle.isMultiFocusModeSupported()){
            mFocusRectangle.clear();
            return;
        }
            mFocusRectangle.showStart();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isCameraIdle()) {
            // ignore backs while we're taking a picture
            return;
        } else if (mHeadUpDisplay == null || !mHeadUpDisplay.collapse()) {
            super.onBackPressed();
        }
    }

    private boolean isFocusing;
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                   return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                   return true;
            case KeyEvent.KEYCODE_MENU:
                if (false == mPreviewStatus) {
                    Log.v(TAG, "it is not ready for menu opening");
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_FOCUS:
                Log.v(TAG, "keycode = KEYCODE_FOCUS");
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    if(canTakePicture()){
                        if(isCanflash())
                            mCameraDevice.startFlash();
                        if(isNeedFocus())
                            doFocus(true);
                    }
                    else
                        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
                }
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                Log.v(TAG, "keycode = KEYCODE_CAMERA");
                if (mFirstTimeInitialized && event.getRepeatCount() == 0  && mStatus == IDLE) {
                    mValidCamKeyDown = true;
                    if(mShutterButton != null){
                        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pressed);
                    }
                    if (mHeadUpDisplay != null && mGLRootView != null){
                        mHeadUpDisplay.collapse();
                    }
                    if(canTakePicture()){
                        if(isCanflash())
                            mCameraDevice.startFlash();
                        if(isNeedFocus())
                            doFocus(true);
                    }
                    isFocusing = true;
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, doFocus() will be
                    // called again but it is fine.
                    if (mGLRootView != null && mHeadUpDisplay.collapse()) return true;
                    if(canTakePicture()){
                        //if(isCanflash())
                        //    mCameraDevice.startFlash();
                        if(isNeedFocus())
                            doFocus(true);
                    }
                    else
                        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
                    if (mShutterButton.isInTouchMode()) {
                        mShutterButton.requestFocusFromTouch();
                    } else {
                        mShutterButton.requestFocus();
                    }
                    mShutterButton.setPressed(true);
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                   return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                   return true;

            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    doFocus(false);
                }
                Log.i(TAG, "onKeyUp: keycode = KEYCODE_FOCUS");
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                Log.i(TAG, "onKeyUp: keycode = KEYCODE_CAMERA");
                if (!mValidCamKeyDown) {
                    return true;
                }

                isFocusing = false;
                setContinuousCaptureCount();
                doSnapForCameraButton();
                if(mShutterButton != null){
                    mShutterButton.setBackgroundResource(R.drawable.btn_shutter);
                }
                mValidCamKeyDown = false;
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void doSnapForCameraButton() {
        if(!canTakePicture()){
            mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
            return;
        }
        if(!( mPreviewing && (mPicturesRemaining > 0))){
        Log.v(TAG, "doSnapForCameraButton: mPreviewing:%d" + mPreviewing + ", mPicturesRemaing: %d" + mPicturesRemaining);
        return;
    }
        if (mGLRootView != null && mHeadUpDisplay.collapse()) return;

        Log.v(TAG, "doSnapForCameraButton: mFocusState=" + mFocusState);
        // If the user has half-pressed the shutter and focus is completed, we
        // can take the photo right away. If the focus mode is infinity, we can
        // also take the photo.
        if (mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                || (mFocusState == FOCUS_SUCCESS
        || mFocusState == FOCUS_NOT_STARTED
                || mFocusState == FOCUS_FAIL)) {
            mImageCapture.onSnap();
        } else if (mFocusState == FOCUSING) {
            // Half pressing the shutter (i.e. the focus button event) will
            // already have requested AF for us, so just request capture on
            // focus here.
            mFocusState = FOCUSING_SNAP_ON_FINISH;
        }
    }

    private void doSnap() {
        if(!canTakePicture()){
            mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
            return;
        }
        if (mHeadUpDisplay.collapse()) return;

        Log.v(TAG, "doSnap: mFocusState=" + mFocusState);
        // If the user has half-pressed the shutter and focus is completed, we
        // can take the photo right away. If the focus mode is infinity, we can
        // also take the photo.
        if (mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                || mFocusMode.equals(Parameters.FOCUS_MODE_FIXED)
                || mFocusMode.equals(Parameters.FOCUS_MODE_EDOF)
                || (mFocusState == FOCUS_SUCCESS
                || mFocusState == FOCUS_FAIL)) {
            mImageCapture.onSnap();
        } else if (mFocusState == FOCUSING) {
            // Half pressing the shutter (i.e. the focus button event) will
            // already have requested AF for us, so just request capture on
            // focus here.
            mFocusState = FOCUSING_SNAP_ON_FINISH;
        } else if (mFocusState == FOCUS_NOT_STARTED) {
            // Focus key down event is dropped for some reasons. Just ignore.
        }
    }

    public void doFocus(boolean pressed) {
        // Do the focus if the mode is not infinity.
        if(mHeadUpDisplay == null) return;
        if (mHeadUpDisplay.collapse()) return;
        if (!(mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                  || mFocusMode.equals(Parameters.FOCUS_MODE_FIXED)
                  || mFocusMode.equals(Parameters.FOCUS_MODE_EDOF))) {
            if (pressed) {  // Focus key down.
                autoFocus();
            } else {  // Focus key up.
                cancelAutoFocus();
            }
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        //keyup maybe Intercepted ,cancle focusetangle by this function.
        if(!hasFocus) {
            if (!mValidCamKeyDown) return;
            if ((mFocusState != FOCUS_NOT_STARTED) && mFirstTimeInitialized) {
                doFocus(false);
            }
            mValidCamKeyDown = false;
            //mShutterButton.setBackgroundResource(R.drawable.btn_shutter_normal);
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        // We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // The mCameraDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCameraDevice == null) return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing || isFinishing()) return;

        if (mPreviewing && holder.isCreating()) {
            // Set preview display if the surface is being created and preview
            // was already started. That means preview display was set to null
            // and we need to set it now.
            setPreviewDisplay(holder);
        } else {
            // 1. Restart the preview if the size of surface was changed. The
            // framework may not support changing preview display on the fly.
            // 2. Start the preview now if surface was destroyed and preview
            // stopped.
            restartPreview();
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
         Log.v(TAG, "surfaceCreated");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        stopPreview();
        mSurfaceHolder = null;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice = null;
            mPreviewing = false;
        }
    }

    private void ensureCameraDevice() throws CameraHardwareException {
        if (mCameraDevice == null) {
            mCameraDevice = CameraHolder.instance().open(mCameraId);
            mInitialParams = mCameraDevice.getParameters();
            List <String> focusMode = mInitialParams.getSupportedFocusModes();
            if(focusMode == null || focusMode.size() <= 1){
                mFocusRectangle.setMultiFocusModeSupported(false);
            }else{
                Log.v(TAG, "focusMode's size = " + focusMode.size());
                mFocusRectangle.setMultiFocusModeSupported(true);
            }
        }
    }

    private void showCameraErrorAndFinish() {
        Resources ress = getResources();
        Util.showFatalErrorAndFinish(Camera.this,
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
        if (/*doCheck() ||*/ mPausing || isFinishing()) return;

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
//                    if (true /*&& mCameraId == 0*/) throw new Exception();
                    // test camera hardware broken end
                    Log.d(TAG, "tried begin");
                    // If we're previewing already,
                    // stop the preview first (this will blank the screen).
                    if (mPreviewing) stopPreview();
                    ensureCameraDevice();
                    setPreviewDisplay(mSurfaceHolder);
                    Util.setCameraDisplayOrientation(this, mCameraId, mCameraDevice);
                    setCameraParameters(UPDATE_PARAM_ALL);
                    mCameraDevice.setErrorCallback(mErrorCallback);
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
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (mGLRootView == null && mHeadUpDisplay != null)
                    attachHeadUpDisplay();
            }
            mCameraDevice.startPreview();
            mFocusRectangle.setVisibility(View.GONE); // fix bug 10043
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

        mPreviewing = true;
        mTrySwitchCamera = true;
        mZoomState = ZOOM_STOPPED;
        mStatus = IDLE;
        mFocusRectangle.invalidate();
    }

    private void stopPreview() {
        if (mCameraDevice != null && mPreviewing) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        mPreviewing = false;
        // If auto focus was in progress, it would have been canceled.
        clearFocusState();
    }

    private Size getOptimalPreviewSize(List<Size> sizes, double targetRatio) {
        final double ASPECT_TOLERANCE = 0.05;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        Display display = getWindowManager().getDefaultDisplay();
        int targetHeight = Math.min(display.getHeight(), display.getWidth());

        if (targetHeight <= 0) {
            // We don't know the size of SurefaceView, use screen height
            WindowManager windowManager = (WindowManager)
                    getSystemService(Context.WINDOW_SERVICE);
            targetHeight = windowManager.getDefaultDisplay().getHeight();
        }

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            Log.v(TAG, "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
    }

    private void updateCameraParametersPreference() {
        boolean isNeedFocusReset = false;
        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(this, mParameters);
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }

        setContinuousCaptureCount();
        // Set the preview frame aspect ratio according to the picture size.
        PreviewFrameLayout frameLayout =
                (PreviewFrameLayout) findViewById(R.id.frame_layout);
        mPreviewFrameLayout = frameLayout;
        String previewSize = mPreferences.getString(CameraSettings.KEY_PREVIEW_SIZE, CameraSettings.DEFAULT_CAMERA_PREVIEW_SIZE_VALUE);
        Size size = mParameters.getPictureSize();
        if(previewSize.equals(CameraSettings.CAMERA_PREVIEW_SIZE_FULLSCREEN)){
            frameLayout.setFullScreenMode(true);
            Display display = getWindowManager().getDefaultDisplay();
            double width = display.getWidth();
            double height = display.getHeight();
            double aspectRatio = (double) size.width / size.height;
            if(width / height < aspectRatio){
                width = height * aspectRatio;
            }else{
                height = width / aspectRatio;
            }
            frameLayout.setAspectRatio(aspectRatio);
        }else{
            frameLayout.setFullScreenMode(false);
            frameLayout.setAspectRatio((double) size.width / size.height);
        }

//        Size size = mParameters.getPictureSize();
        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = getOptimalPreviewSize(
                sizes, (double) size.width / size.height);
        if (optimalSize != null) {
            Size original = mParameters.getPreviewSize();
            if (!original.equals(optimalSize)) {
                mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

                // Zoom related settings will be changed for different preview
                // sizes, so set and read the parameters to get lastest values
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        }
        // Since change scene mode may change supported values,
        // Set scene mode first,
        mSceneMode = mPreferences.getString(
                CameraSettings.KEY_SCENE_MODE,
                getString(R.string.pref_camera_scenemode_default));
        if (isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);
                mCameraDevice.setParameters(mParameters);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mParameters = mCameraDevice.getParameters();
                isNeedFocusReset = true;
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        // Set brightness
        String brightness = mPreferences.getString(
                CameraSettings.KEY_BRIGHTNESS,
                getString(R.string.pref_camera_brightness_default));
        if (isSupported(brightness, mParameters.getSupportedBrightness())) {
            if (!mParameters.getBrightness().equals(brightness)) {
                mParameters.setBrightness(brightness);
            }
        }
        // Set contrast
        String contrast = mPreferences.getString(CameraSettings.KEY_CONTRAST,
                getString(R.string.pref_camera_contrast_default));
        if (isSupported(contrast, mParameters.getSupportedContrast())) {
            if (!mParameters.getContrast().equals(contrast)) {
                mParameters.setContrast(contrast);
            }
        }

        // Set JPEG quality.
        String jpegQuality = mPreferences.getString(
                CameraSettings.KEY_JPEG_QUALITY,
                getString(R.string.pref_camera_jpegquality_default));
        mParameters.setJpegQuality(JpegEncodingQualityMappings.getQualityNumber(jpegQuality));

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // Set exposure compensation
        String exposure = mPreferences.getString(
                CameraSettings.KEY_EXPOSURE,
                getString(R.string.pref_exposure_default));
        try {
            int value = Integer.parseInt(exposure);
            int max = mParameters.getMaxExposureCompensation();
            int min = mParameters.getMinExposureCompensation();
            if (value >= min && value <= max) {
                mParameters.setExposureCompensation(value);
            } else {
                Log.w(TAG, "invalid exposure range: " + exposure);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "invalid exposure: " + exposure);
        }

        if (mHeadUpDisplay != null) updateSceneModeInHud();

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (isSupported(flashMode, supportedFlash)) {
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
        }/* else {
            mFocusMode = mParameters.getFocusMode();
            if (mFocusMode == null) {
                 mFocusMode = Parameters.FOCUS_MODE_AUTO;
            }
        }*/

        // Set focus mode.
        mFocusMode = mPreferences.getString(
                CameraSettings.KEY_FOCUS_MODE,
                getString(R.string.pref_camera_focusmode_default));
        if (isSupported(mFocusMode, mParameters.getSupportedFocusModes())) {
            Log.v(TAG, "updateCameraParametersPreference: mFocusMode = " + mFocusMode);
            if(! mFocusMode.equals(mFocusRectangle.getMode())){
                mFocusRectangle.setMode(mFocusMode);
                mParameters.setFocusMode(mFocusMode);
                isNeedFocusReset = true;
            }
        } else {
            mFocusMode = mParameters.getFocusMode();
            if (mFocusMode == null) {
                mFocusMode = Parameters.FOCUS_MODE_AUTO;
            }
        }

        String antibanding = mPreferences.getString(
                CameraSettings.KEY_ANTIBANDING,
                getString(R.string.pref_camera_antibanding_default));
        if (isSupported(antibanding, mParameters.getSupportedAntibanding())) {
            mParameters.setAntibanding(antibanding);
        }

        if(isNeedFocusReset){
            clearFocusState();
            mFocusRectangle.reset();
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        mParameters = mCameraDevice.getParameters();
     mParameters.set("sensororientation", 0);

        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

        mCameraDevice.setParameters(mParameters);
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    private void gotoGallery() {
        MenuHelper.gotoCameraImageGallery(this);
    }

    private void viewLastImage() {
        if (mThumbController.isUriValid()) {
            Intent intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
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

    private void startReceivingLocationUpdates() {
        if (mLocationManager != null) {
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        0F,
                        mLocationListeners[1]);
            } catch (java.lang.SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "provider does not exist " + ex.getMessage());
            }
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000,
                        0F,
                        mLocationListeners[0]);
            } catch (java.lang.SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "provider does not exist " + ex.getMessage());
            }
        }
    }

    private void stopReceivingLocationUpdates() {
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
    }

    private Location getCurrentLocation() {
        // go in best to worst order
        for (int i = 0; i < mLocationListeners.length; i++) {
            Location l = mLocationListeners[i].current();
            if (l != null) return l;
        }
        return null;
    }

    private boolean isCameraIdle() {
        return mStatus == IDLE && (mFocusState == FOCUS_NOT_STARTED || mFocusState == FOCUS_SUCCESS || mFocusState == FOCUS_FAIL);
    }

    private boolean isImageCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void showPostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            if (mGLRootView != null) {
                detachHeadUpDisplay();
            }
            mFocusRectangle.setVisibility(View.GONE);//fix bug 10043
            findViewById(R.id.shutter_button).setVisibility(View.INVISIBLE);
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                View button = findViewById(id);
                ((View) button.getParent()).setVisibility(View.VISIBLE);
            }
        }
    }

    private void hidePostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            findViewById(R.id.shutter_button).setVisibility(View.VISIBLE);
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                View button = findViewById(id);
                ((View) button.getParent()).setVisibility(View.GONE);
            }
        }
    }

    private int calculatePicturesRemaining() {
        mPicturesRemaining = MenuHelper.calculatePicturesRemaining();
        return mPicturesRemaining;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Only show the menu when camera is idle.
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(isCameraIdle());
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsImageCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
        }
        return true;
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, true, new Runnable() {
            public void run() {
                switchToVideoMode();
            }
        });
        MenuItem gallery = menu.add(Menu.NONE, Menu.NONE,
                MenuHelper.POSITION_GOTO_GALLERY,
                R.string.camera_gallery_photos_text)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
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
        if (mPausing || !isCameraIdle()) return;
        mCameraId = cameraId;
        CameraSettings.writePreferredCameraId(mPreferences, cameraId);

        stopPreview();
        closeCamera();

        // Remove the messages in the event queue.
        mHandler.removeMessages(RESTART_PREVIEW);

        // Reset variables
        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        // Reload the preferences.
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        // Restart the preview.
        //fixed 17496
        //resetExposureCompensation();
        if (!restartPreview()) return;

        initializeZoom();
        clearFocusState();
        mFocusRectangle.reset();

        // Reload the UI.
        if (mFirstTimeInitialized) {
            initializeHeadUpDisplay();
        }
    }

    private boolean isSwitchToVideo = false;
    private boolean switchToVideoMode() {
        long current = System.currentTimeMillis();
        long elapsed_time = current - mResume;
        //fix the bug4837
        Log.d(TAG, "Elapsed time : "+elapsed_time);
        if (elapsed_time < 1000 || mResume == 0) return false;
        //end fix bug4837
        if (isFinishing() || !isCameraIdle() ) return false;
        // fixed bug 19900 start
        isSwitchToVideo = true;
        // might starting VideoCamera has delay,
        // so we must enable mShutterButton click
        if (mShutterButton != null) {
            mShutterButton.setOnShutterButtonListener(null);
        }
        // fixed bug 19900 end
        MenuHelper.gotoVideoMode(this);
        mHandler.removeMessages(FIRST_TIME_INIT);
        finish();
        return true;
    }

    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        if (onOff == SWITCH_VIDEO) {
            return switchToVideoMode();
        } else {
            return true;
        }
    }

    private void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPausing) return;

        boolean recordLocation;

        recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());

        if (mRecordLocation != recordLocation) {
            mRecordLocation = recordLocation;
            if (mRecordLocation) {
                startReceivingLocationUpdates();
            } else {
                stopReceivingLocationUpdates();
            }
        }
        int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
        if (mCameraId != cameraId) {
            switchCameraId(cameraId);
        } else {
            setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnAwhile();
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

    private class MyHeadUpDisplayListener implements HeadUpDisplay.Listener {

        public void onSharedPreferencesChanged() {
            Camera.this.onSharedPreferenceChanged();
        }

        public void onRestorePreferencesClicked() {
            Camera.this.onRestorePreferencesClicked();
        }

        public void onPopupWindowVisibilityChanged(int visibility) {
        }
    }

    protected void onRestorePreferencesClicked() {
        if (mPausing) return;
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
                if(null != mFocusRectangle)
                    mFocusRectangle.reset();
                cancelAutoFocus();
                clearFocusState();
            }
        };
        mResetDialog = MenuHelper.confirmAction(this,
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                runnable);
    }
    private AlertDialog mResetDialog = null;

    public boolean isFocusing(){
        return mFocusState == FOCUSING_SNAP_ON_FINISH || FOCUSING == mFocusState || mStatus == SNAPSHOT_IN_PROGRESS || mValidCamKeyDown;
    }


    private boolean isNeedFocus(){
        return mFocusState != FOCUS_SUCCESS && FOCUS_FAIL != mFocusState && FOCUSING != mFocusState;
    }

    private boolean isCanflash(){
        return "on".equals(mParameters.getFlashMode());
    }
    //fixed bug for 15012
    private int touchCount;
    private long firTouth;
    private long secTouth;
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(v.getId() != R.id.camera_preview && v.getId() != R.id.focus_rectangle){
            return false;
        }
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            touchCount++;
            if (touchCount == 1) {
                firTouth = System.currentTimeMillis();
            } else if (touchCount == 2) {
                secTouth = System.currentTimeMillis();
                if (secTouth - firTouth < 300) {
                    //do double event;
                    //fixed bug 15903
                    if (mPausing || !isCameraIdle() || !mPreviewing
                            || mZoomState != ZOOM_STOPPED || mHeadUpDisplay == null) {
                        return false;
                    }

                    if (mZoomValue < mZoomMax) {
                        // Zoom in to the maximum.
                        mZoomValue = mZoomMax;
                    } else {
                        mZoomValue = 0;
                    }
                    setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);
                    mHeadUpDisplay.setZoomIndex(mZoomValue);
                }
            }
            break;
        case MotionEvent.ACTION_UP:
            if (touchCount >= 2) {
                touchCount = 0;
                firTouth = 0;
                secTouth = 0;
            }
            break;
        }
        return false;
    }
}

//class FocusRectangle extends View {
//
//    @SuppressWarnings("unused")
//    private static final String TAG = "FocusRectangle";
//
//    public FocusRectangle(Context context, AttributeSet attrs) {
//        super(context, attrs);
//    }
//
//    private void setDrawable(int resid) {
//        setBackgroundDrawable(getResources().getDrawable(resid));
//    }
//
//    public void showStart() {
//        setDrawable(R.drawable.focus_focusing);
//    }
//
//    public void showSuccess() {
//        setDrawable(R.drawable.focus_focused);
//    }
//
//    public void showFail() {
//        setDrawable(R.drawable.focus_focus_failed);
//    }
//
//    public void clear() {
//        setBackgroundDrawable(null);
//    }
//}

/*
 * Provide a mapping for Jpeg encoding quality levels
 * from String representation to numeric representation.
 */
class JpegEncodingQualityMappings {
    private static final String TAG = "JpegEncodingQualityMappings";
    private static final int DEFAULT_QUALITY = 85;
    private static HashMap<String, Integer> mHashMap =
            new HashMap<String, Integer>();

    static {
        mHashMap.put("normal",    CameraProfile.QUALITY_LOW);
        mHashMap.put("fine",      CameraProfile.QUALITY_MEDIUM);
        mHashMap.put("superfine", CameraProfile.QUALITY_HIGH);
    }

    // Retrieve and return the Jpeg encoding quality number
    // for the given quality level.
    public static int getQualityNumber(String jpegQuality) {
        Integer quality = mHashMap.get(jpegQuality);
        if (quality == null) {
            Log.w(TAG, "Unknown Jpeg quality: " + jpegQuality);
            return DEFAULT_QUALITY;
        }
        return CameraProfile.getJpegEncodingQualityParameter(quality.intValue());
    }
}
