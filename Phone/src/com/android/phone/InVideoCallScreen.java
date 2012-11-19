package com.android.phone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaPhone;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.pim.ContactsAsyncHelper;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.IWindowManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.util.EventLog;
import android.util.Log;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.gsm.TDPhone;

import java.io.IOException;

import static com.android.internal.telephony.MsmsConstants.SUBSCRIPTION_KEY;

public class InVideoCallScreen extends Activity 
	implements View.OnClickListener, CallTime.OnTickListener, CallerInfoAsyncQuery.OnQueryCompleteListener,
				MediaPhone.OnErrorListener,
                MediaPhone.OnMediaInfoListener,
                MediaPhone.OnCallEventListener,
                MediaPhone.OnVideoSizeChangedListener,
                AdapterView.OnItemClickListener,
                AdjustMenuView.OnSeekBarChangeListener,
                SurfaceHolder.Callback{
    private static final String LOG_TAG = "InVideoCallScreen";

    private static final boolean DBG = true;
            //(PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = true;//(PhoneApp.DBG_LEVEL >= 2);
			
	static final String SHOW_DIALPAD_EXTRA = "com.android.phone.ShowDialpad";

	private static final boolean ENABLE_MEDIAPHONE = true;
	private static final boolean ENABLE_DIALPANEL_HANDLER = true;


    // High-level "modes" of the in-call UI.
    private enum InCallScreenMode {
        /**
         * Normal in-call UI elements visible.
         */
        NORMAL,
        /**
         * Non-interactive UI state.  Call card is visible,
         * displaying information about the call that just ended.
         */
        CALL_ENDED,

		FALL_BACK,
        /**
         * Default state when not on call
         */
        UNDEFINED
    }
    private InCallScreenMode mInCallScreenMode = InCallScreenMode.UNDEFINED;

    // Possible error conditions that can happen on startup.
    // These are returned as status codes from the various helper
    // functions we call from onCreate() and/or onResume().
    // See syncWithPhoneState() and checkIfOkToInitiateOutgoingCall() for details.
    private enum InCallInitStatus {
        SUCCESS,
	SURFACE_NOT_READY,
        VOICEMAIL_NUMBER_MISSING,
        POWER_OFF,
        EMERGENCY_ONLY,
        OUT_OF_SERVICE,
        PHONE_NOT_IN_USE,
        NO_PHONE_NUMBER_SUPPLIED,
        NO_IN_3G,
        CALL_FAILED,
        ALREADY_IN_3GCALL,
        ALREADY_IN_2GCALL,
        CALL_FAILED_FDN_ONLY,
        AIRPLANE_MODE_ON,
    }
    private InCallInitStatus mInCallInitialStatus;  // see onResume()
	
    private boolean mRegisteredForPhoneStates;
    private boolean mNeedShowCallLostDialog;

    private Phone mPhone;
    // Phone app instance
    private PhoneApp mApplication;    
    private CallManager mCM;
	
    private Call mForegroundCall;
    private Call mBackgroundCall;
    private Call mRingingCall;

    private BluetoothHandsfree mBluetoothHandsfree;
    private BluetoothHeadset mBluetoothHeadset;
    private boolean mBluetoothConnectionPending;
    private long mBluetoothConnectionRequestTime;

    // Text colors, used for various labels / titles
    private int mTextColorDefaultPrimary;
    private int mTextColorDefaultSecondary;
    private int mTextColorConnected;
    private int mTextColorConnectedBluetooth;
    private int mTextColorEnded;
    private int mTextColorOnHold;

		// Surface view for video phone
	private SurfaceView mRemoteVideoPhoneView;
	private SurfaceView mLocalVideoPhoneView;

	private SurfaceView mLargeVideoPhoneView;
	private SurfaceView mSmallVideoPhoneView;	
    
    private int mSurfaceReadyCount = 0;
	
	PowerManager.WakeLock mWakeLock;
	Call.State mLastFGCallState = Call.State.IDLE;

	// Parmeter for videocall
	private enum ViewType {
		LIVE,
		PICTURE,
		VIDEO,
		NONE
	}
	private ViewType mLocalViewType = ViewType.LIVE;
	private ViewType mRemoteViewType = ViewType.LIVE;
	private String mLocalPicUri = null;
	private String mRemotePicUri = null;
	private String mLocalVideoUri = null;
	private String mRemoteVideoUri = null;

	// Caller Info
	private ViewGroup mCallInfo;
    private VideoCallTime mCallTime;
    private TextView mName;
    private TextView mPhoneNumber;

	private View mInCallPanel;
	private View mInComingCallPanel;
	private Button mDialerBtn;
	private Button mHangupBtn;
	private Button mAnswerBtn;
	private Button mDeclineBtn;

	// option menu items
	private Menu mOptionMenu;
	private MenuItem mCameraSwitch_item;
	private MenuItem mViewSwitch_item;
	private MenuItem mCamera_item;
	private MenuItem mSpeaker_item;
	private MenuItem mBluetooth_item;
	private MenuItem mMute_item;
	private MenuItem mCamera_brightness_item;
	private MenuItem mCamera_contrast_item;
	private MenuItem mStartrecord_item;
	private MenuItem mStoprecord_item;
	private boolean mbExternalStorageAvail = false;
	
    // Title and elapsed-time widgets
    private TextView mUpperTitle;
    private TextView mElapsedTime;
    private TextView mLabel;
    private TextView mBeginTime;
    // Track the state for the photo.
    private ContactsAsyncHelper.ImageTracker mPhotoTracker;

	// AdjustMenu
	AdjustMenuView mBrightnessAdjustMenu;
	AdjustMenuView mCameraBrightnessAdjustMenu;
	AdjustMenuView mCameraContrastAdjustMenu;
	
    private static final int MINIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_DIM + 10;
    private static final int MAXIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_ON;
    private static final int MINIMUM_CAMERA_BACKLIGHT = 0;
    private static final int MAXIMUM_CAMERA_BACKLIGHT = 6;
    private static final int MINIMUM_CAMERA_CONTRAST = 0;
    private static final int MAXIMUM_CAMERA_CONTRAST = 6;
    private int mMyBrightness;
	private int mMyCameraBrightness;
	private int mMyCameraContrast;
	private int mSysBrightness;
	private int mSysCameraBrightness;
	private int mSysCameraContrast;
	
    // Cached DisplayMetrics density.
    private float mDensity;
	
    private static final String DEFAULT_COMM = "videophone:///dev/ts0710mux12";
	private static final String DEFAULT_FALLBACK = "DEFAULT_FALLBACK";
	MediaPhone mp;	
	HandlerThread mMediaPhoneThread;
	Thread mStartPreviewThread;

	private enum ScreenLayoutConfig {
		LOCAL_SMALL_REMOTE_LARGE,
		LOCAL_LARGE_REMOTE_SMALL
	}
	private ScreenLayoutConfig mScreenLayoutConfig = ScreenLayoutConfig.LOCAL_SMALL_REMOTE_LARGE;
	
	private enum FallBackConfig {
		FALLBACK_ASK,
		FALLBACK_ALWAYS,
		FALLBACK_NEVER
	}
	private FallBackConfig mFallBackConfig = FallBackConfig.FALLBACK_ASK;

	private enum CameraType {
		BOTTOM_CAMERA,
		FRONT_CAMERA,
		FAKE_CAMERA,
	}
	private CameraType mCameraType = CameraType.FRONT_CAMERA;
	private CameraType mRealCameraType = CameraType.FRONT_CAMERA;
	private boolean mbEnableCamera = false;
	private boolean mbShowSubstitutePic = true;
	private boolean mbEnableRecord = false;
	private boolean mbInitialCamera = false;
	private boolean mAllowVolumeButtion = false;
	private String mLocalSubstitutePic = null;
	private String mRemoteSubstitutePic = null;
    
    private BroadcastReceiver mStorageReceiver;
	
	private static final String DEFAULT_STORE_SUBDIR = "/videocall";
	private static final String DEFAULT_RECORD_SUFFIX = ".3gp";
	private static final long MINIMUM_START_FREE_SIZE = 2 * 1024 * 1024;
	private static final long MINIMUM_FREE_SIZE = 1024 * 1024;

	private enum RemoteCameraClose_ActionConfig {
		REMOTECAMERACLOSE_DONOTHING,
		REMOTECAMERACLOSE_SUBTITUTE,
	}
	private RemoteCameraClose_ActionConfig mRemoteCameraClose_ActionConfig = RemoteCameraClose_ActionConfig.REMOTECAMERACLOSE_SUBTITUTE;

	private class CallData {
		String number;
		Uri contactUri;

		public CallData(String num, Uri uri){
			number = num;
			contactUri = uri;
		}
	}

	private CallData mCallData;
	
	// Dialog
//    private AlertDialog mIncomingDialog;

    // "Touch lock overlay" feature
    private boolean mUseTouchLockOverlay;  // True if we use this feature on the current device
    private View mTouchLockOverlay;  // The overlay over the whole screen
    private View mTouchLockIcon;  // The "lock" icon in the middle of the screen
    private long mTouchLockLastTouchTime;  // in SystemClock.uptimeMillis() time base

    // DTMF Dialer controller and its view:
    private VideoPhoneTwelveKeyDialer mDialer;
    private VideoPhoneTwelveKeyDialerView mDialerView;

    // Various dialogs we bring up (see dismissAllDialogs()).
    // TODO: convert these all to use the "managed dialogs" framework.
    //
    // The MMI started dialog can actually be one of 2 items:
    //   1. An alert dialog if the MMI code is a normal MMI
    //   2. A progress dialog if the user requested a USSD
    private AlertDialog mGenericErrorDialog;
    private AlertDialog mWaitPromptDialog;
    private AlertDialog mWildPromptDialog;
    private AlertDialog mCallLostDialog;
    private AlertDialog mPausePromptDialog;
    private Dialog mFallBackDialog;
    // NOTE: if you add a new dialog here, be sure to add it to dismissAllDialogs() also.

    // TODO: If the Activity class ever provides an easy way to get the
    // current "activity lifecycle" state, we can remove these flags.
    private boolean mIsDestroyed = false;
    private boolean mIsForegroundActivity = false;
    private boolean mHasFocus = false;

    // Amount of time (in msec) that we display the "Call ended" state.
    // The "short" value is for calls ended by the local user, and the
    // "long" value is for calls ended by the remote caller.
    private static final int CALL_ENDED_SHORT_DELAY =  1000;  // msec
    private static final int CALL_ENDED_LONG_DELAY = 3000;  // msec

    // Message codes; see mHandler below.
    // Note message codes < 100 are reserved for the PhoneApp.
    private static final int PHONE_STATE_CHANGED = 101;
    private static final int PHONE_DISCONNECT = 102;
    private static final int EVENT_HEADSET_PLUG_STATE_CHANGED = 103;
    private static final int POST_ON_DIAL_CHARS = 104;
    private static final int WILD_PROMPT_CHAR_ENTERED = 105;
    private static final int ADD_VOICEMAIL_NUMBER = 106;
    private static final int DONT_ADD_VOICEMAIL_NUMBER = 107;
    private static final int DELAYED_CLEANUP_AFTER_DISCONNECT = 108;
    private static final int SUPP_SERVICE_FAILED = 110;
    private static final int DISMISS_MENU = 111;
    private static final int ALLOW_SCREEN_ON = 112;
    private static final int TOUCH_LOCK_TIMER = 113;
    private static final int REQUEST_UPDATE_BLUETOOTH_INDICATION = 114;
    private static final int PHONE_CDMA_CALL_WAITING = 115;
    private static final int THREEWAY_CALLERINFO_DISPLAY_DONE = 116;
    private static final int EVENT_OTA_PROVISION_CHANGE = 117;
    private static final int REQUEST_CLOSE_SPC_ERROR_NOTICE = 118;
    private static final int REQUEST_CLOSE_OTA_FAILURE_NOTICE = 119;
    private static final int EVENT_PAUSE_DIALOG_COMPLETE = 120;
    private static final int EVENT_HIDE_PROVIDER_OVERLAY = 121;  // Time to remove the overlay.
    private static final int REQUEST_UPDATE_TOUCH_UI = 122;
    private static final int DELAYED_SET_SURFACE = 123;
	private static final int DELAYED_END_WAITCC = 124;
	private static final int DELAYED_SWITCH_SPEAKER = 125;
	private static final int MEDIAPHONE_CAMERA_OPEN = 126;
	private static final int MEDIAPHONE_CAMERA_CLOSE = 127;
	private static final int MEDIAPHONE_STRING = 128;
	private static final int MEDIAPHONE_CODEC_START = 129;
	private static final int MEDIAPHONE_CODEC_CLOSE = 130;
	private static final int MEDIAPHONE_CAMERA_FAIL = 131;
	private static final int MEDIAPHONE_MEDIA_START = 132;
	private static final int DELAY_UPDATEVIEWCONFIG = 133;
    private static final int REQUEST_UPDATE_MUTE_INDICATION = 134;
	private static final int DELAYED_CREATE_CAMERA = 135;
	private static final int INITIALED_CAMERA = 136;
	private static final int CHECK_FREESPACE = 137;

	// arg1 for DELAYED_SET_SURFACE
	private static final int ARG_PLACE_CALL = 1;
	private static final int ARG_RESUME = 2;

	// view size
	private static final int SMALL_VIEW_SIZE = 80;
	private static final int LARGE_VIEW_SIZE = 160;

    private android.hardware.Camera mCameraDevice;
    private boolean mStartPreviewFail = false;
    boolean mPreviewing = false; // True if preview is started.
    private Parameters mParameters;
	private ListView mFallBackList;
	private Dialog	mActiveDlg = null;
	private String mRecordFile = null;

	private Camera mCamera = null;
	private Object mCameraLock = new Object();
	private SurfaceHolder m_sHolder = null;

	private boolean mWaitCC = false;
	private boolean mWaitCD = false;
	private long mCCTime = 0;
	private long mMMRingDuring = 0;
	private Call.State mOldCallState = Call.State.IDLE;
	private static final int WAIT_CC_DELAY = 4000;
	
	private enum LastAction {
		ACTION_NONE,
		ACTION_VIDEOCALL,
		ACTION_VOICECALL
	}
	private LastAction mLastAction = LastAction.ACTION_NONE;

	private IWindowManager mWindowManager;
	private Button mButtonIncrease;
	private Button mButtonDecline;

	// whether in dialing state. If not, don't display disconnect cause
	// it only be true in incoming call state
	private boolean mbIncoming = true;
	private boolean mbAnswered = false;
    private boolean mbEverConnected = false;
    private boolean mbHangupByUser = false;
	private boolean mbMediaStarted = false;
	
    // save initial intent to make call
    private Intent mCallIntent = null;

    // Flag indicating whether or not we should bring up the Call Log when
    // exiting the in-call UI due to the Phone becoming idle.  (This is
    // true if the most recently disconnected Call was initiated by the
    // user, or false if it was an incoming call.)
    // This flag is used by delayedCleanupAfterDisconnect(), and is set by
    // onDisconnect() (which is the only place that either posts a
    // DELAYED_CLEANUP_AFTER_DISCONNECT event *or* calls
    // delayedCleanupAfterDisconnect() directly.)
    private boolean mShowCallLogAfterDisconnect;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mIsDestroyed) {
                if (DBG) log("Handler: ignoring message " + msg + "; we're destroyed!");
                return;
            }
            if (!mIsForegroundActivity) {
                if (DBG) log("Handler: handling message " + msg + " while not in foreground");
                // Continue anyway; some of the messages below *want* to
                // be handled even if we're not the foreground activity
                // (like DELAYED_CLEANUP_AFTER_DISCONNECT), and they all
                // should at least be safe to handle if we're not in the
                // foreground...
            }

            PhoneApp app = PhoneApp.getInstance();
            switch (msg.what) {

                case PHONE_STATE_CHANGED:
                    onPhoneStateChanged((AsyncResult) msg.obj);
                    break;

                case PHONE_DISCONNECT:
                    onDisconnect((AsyncResult) msg.obj);
                    break;

		case EVENT_HEADSET_PLUG_STATE_CHANGED:
		case REQUEST_UPDATE_MUTE_INDICATION:
		   updateAudioMenu();
                   break;
				   
                case DELAYED_CLEANUP_AFTER_DISCONNECT:
                    delayedCleanupAfterDisconnect();
                    break;

                case ALLOW_SCREEN_ON:
                    if (VDBG) log("ALLOW_SCREEN_ON message...");
                    // Undo our previous call to preventScreenOn(true).
                    // (Note this will cause the screen to turn on
                    // immediately, if it's currently off because of a
                    // prior preventScreenOn(true) call.)
                    app.preventScreenOn(false);
                    break;

                case REQUEST_UPDATE_BLUETOOTH_INDICATION:
                    if (VDBG) log("REQUEST_UPDATE_BLUETOOTH_INDICATION...");
                    // The bluetooth headset state changed, so some UI
                    // elements may need to update.  (There's no need to
                    // look up the current state here, since any UI
                    // elements that care about the bluetooth state get it
                    // directly from PhoneApp.showBluetoothIndication().)
                    updateScreen();
		    		break;

				case DELAYED_END_WAITCC:
				    Log.w(LOG_TAG, "handleMessage(), DELAYED_END_WAITCC, mWaitCC: " + mWaitCC);
					if (mWaitCC){
						mWaitCC = false;
						updateMainCallStatus();
					}
					break;
		case DELAYED_SWITCH_SPEAKER:
			Log.w(LOG_TAG, "handleMessage(), DELAYED_SWITCH_SPEAKER" );
			if (Call.State.ACTIVE == mForegroundCall.getState()) {
				if (isAudioInCall()) {
					switchToSpeaker();
				} else {
					this.sendEmptyMessageDelayed(DELAYED_SWITCH_SPEAKER, 100);
				}
			}
			break;
		case MEDIAPHONE_CAMERA_OPEN:{
				Toast txtToast = Toast.makeText(getWindow().getContext(), getString(R.string.prompt_remotecamera_open), Toast.LENGTH_LONG);
				txtToast.show();
				if (ViewType.LIVE != mRemoteViewType){
					mRemoteViewType = ViewType.LIVE;
					//updateViewConfig();
					
					 removeMessages(DELAY_UPDATEVIEWCONFIG);
					 sendEmptyMessageDelayed(DELAY_UPDATEVIEWCONFIG,	 1000);
				}
			}
			break;				
		case MEDIAPHONE_CAMERA_CLOSE:{
				Toast txtToast = Toast.makeText(getWindow().getContext(), getString(R.string.prompt_remotecamera_close), Toast.LENGTH_LONG);
				txtToast.show();
				if (mbShowSubstitutePic && (ViewType.PICTURE != mRemoteViewType)){
					mRemoteViewType = ViewType.PICTURE;
					updateViewConfig();
				}
			}
			break;
		case MEDIAPHONE_STRING:{				
				String strRemote = (String)msg.obj;
				log("MEDIAPHONE_STRING, strRemote: " + strRemote + ", mWaitCC: " + mWaitCC + ", mWaitCD: " + mWaitCD);
				if (strRemote.equals("CC")){
					if (mWaitCC){
						mWaitCC = false;
						mWaitCD = true;
             					mHandler.removeMessages(DELAYED_END_WAITCC);
						//mCCTime = SystemClock.elapsedRealtime();
						
						SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
						mBeginTime.setText(sdf.format(new Date()));
						log("MEDIA_CALLEVENT_STRING, mBeginTime: " + mBeginTime.getText());
					} else {
						log("receive 'CC' unexpected");
					}
				} else if (strRemote.equals("CD")){
					if (mWaitCD){
						mWaitCC = false;
						mWaitCD = false;
						mMMRingDuring = SystemClock.elapsedRealtime() - mCCTime;
						updateMainCallStatus();
					} else {
						log("receive 'CD' unexpected");
					}
				}
			}
			break;
		case MEDIAPHONE_CODEC_START:
			updateViewConfig();
			updateOptionMenu();
			break;
		case MEDIAPHONE_CODEC_CLOSE:
			updateViewConfig();
			updateOptionMenu();
			if (mbEnableRecord && (mRecordFile != null)) {
				Toast.makeText(getWindow().getContext(), getString(R.string.prompt_record_finish) + mRecordFile, Toast.LENGTH_LONG).show();
				mbEnableRecord = false;
			}
			break;
		case MEDIAPHONE_CAMERA_FAIL:
			Toast.makeText(getWindow().getContext(), getString(R.string.prompt_camera_fail), Toast.LENGTH_LONG).show();
			break;
        case MEDIAPHONE_MEDIA_START:
            mElapsedTime.setVisibility(View.VISIBLE);
            long duration = VideoCallTime.getCallDuration(null);  // msec
            updateElapsedTimeWidget(duration / 1000);
            break;
		case DELAY_UPDATEVIEWCONFIG:
			if (isForegroundActivity()) {
				updateViewConfig();
			}
			break;
		case DELAYED_CREATE_CAMERA:
			startCameraPreview();
			break;
		case INITIALED_CAMERA:
			//updateInCallPanel();
			updateOptionMenu();
			break;
        case CHECK_FREESPACE:
            if (!isSpaceEnough()) {
			    Toast.makeText(getWindow().getContext(), getString(R.string.prompt_no_freespace), Toast.LENGTH_LONG).show();
                enableRecord(false, 0);
            } else {
                this.sendEmptyMessageDelayed(CHECK_FREESPACE, 10 * 1000);
            }
            break;
        }
        }
    };
	
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    // Listen for ACTION_HEADSET_PLUG broadcasts so that we
                    // can update the onscreen UI when the headset state changes.
                    // if (DBG) log("mReceiver: ACTION_HEADSET_PLUG");
                    // if (DBG) log("==> intent: " + intent);
                    // if (DBG) log("    state: " + intent.getIntExtra("state", 0));
                    // if (DBG) log("    name: " + intent.getStringExtra("name"));
                    // send the event and add the state as an argument.
                    Message message = Message.obtain(mHandler, EVENT_HEADSET_PLUG_STATE_CHANGED,
                            intent.getIntExtra("state", 0), 0);
                    mHandler.sendMessage(message);
                }
            }
        };

	private void initVideoPoneView(SurfaceView view){
		view.getHolder().addCallback(this);
		view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		view.setFocusable(true);
		view.setFocusableInTouchMode(true);
		view.requestFocus();
	}

	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        Log.d(LOG_TAG, "surfaceChanged");
	}

	public void surfaceCreated(SurfaceHolder arg0) {
		if (mSurfaceReadyCount < 2) mSurfaceReadyCount++;
		Log.d(LOG_TAG, "surfaceCreated, mSurfaceReadyCount: " + mSurfaceReadyCount);

		if (mSurfaceReadyCount == 2) {
			mp.setLocalDisplay(mLocalVideoPhoneView.getHolder().getSurface());
			mp.setRemoteDisplay(mRemoteVideoPhoneView.getHolder().getSurface());
			if (mp.getCodecState() == MediaPhone.CodecState.CODEC_START) {
				mp.startDownLink();
			} else {
				if (mCamera != null) {
					synchronized(mCameraLock) {
                        if (mCamera != null) {
    						mCamera.lock();
    						if (!mCamera.previewEnabled()) {
    							try{
    							    Log.d(LOG_TAG, "surfaceCreated startPreview. ");
    								mCamera.setPreviewDisplay(mLocalVideoPhoneView.getHolder());
    								mCamera.startPreview();
    							} catch (Exception e) {
    								Log.e(LOG_TAG, "setPreviewDisplay failed, " + e);
    							}
    						} else {
    							try{
    							    Log.d(LOG_TAG, "surfaceCreated setPreviewDisplay. ");
    								mCamera.setPreviewDisplay(mLocalVideoPhoneView.getHolder());
    							} catch (Exception e) {
    								Log.e(LOG_TAG, "setPreviewDisplay failed, " + e);
    							}
                            }
    						mCamera.unlock();
                        }
					}
				}
			}
		}
	    Log.d(LOG_TAG, "surfaceCreated end. ");
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
	 	if (mSurfaceReadyCount > 0) mSurfaceReadyCount--;
        	Log.d(LOG_TAG, "surfaceDestroyed, mSurfaceReadyCount: " + mSurfaceReadyCount);

		// sometimes, this function will be called after onDestroy(), so we need check (mp != null) here
		if ((mSurfaceReadyCount == 0) && (mp != null)){
			if (mp.getCodecState() == MediaPhone.CodecState.CODEC_START) {
				mp.stopDownLink();
			} else {
				if (mCamera != null) {
					synchronized(mCameraLock) {
                        if (mCamera != null) {
    						mCamera.lock();
    						try{
                                Log.d(LOG_TAG, "surfaceDestroyed stopPreview. "+mCamera.previewEnabled());
    							if (mCamera.previewEnabled()) {
    								mCamera.stopPreview();
    							}
    							mCamera.setPreviewDisplay(null);
    							mCamera.startPreview();
    						} catch (Exception e) {
    							Log.e(LOG_TAG, "setPreviewDisplay failed, " + e);
    						}
    						mCamera.unlock();
                        }
					}
				}
			}
			mp.setLocalDisplay(null);
			mp.setRemoteDisplay(null);
		}
		Log.d(LOG_TAG, "surfaceDestroyed end. ");
	}
	

	private void initMediaPhone(Phone phone) {
		mp.setOnVideoSizeChangedListener(this);
		mp.setOnMediaInfoListener(this);
		mp.setOnCallEventListener(this);
		mp.setOnErrorListener(this);
	}
	
    private void showCameraBusyAndFinish() {
        Log.e(LOG_TAG, "camera busy and finish");
        //Resources ress = getResources();
//        Util.showFatalErrorAndFinish(InVideoCallScreen.this,
//                "camera busy and finish",
//                "camera busy and finish");
    }

    private static Phone getPhoneBase(Phone phone) {
        if (phone instanceof PhoneProxy) {
            return phone.getForegroundCall().getPhone();
        }
        return phone;
    }

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	if (DBG) log("onCreate()...  this = " + this);
		
    setCameraFlag(true);
    
	if(SystemProperties.getInt("ro.sprd.volume_control_icon", 0) == 1){
		mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
	}
		super.onCreate(savedInstanceState);

        final PhoneApp app = PhoneApp.getInstance();
        mApplication = PhoneApp.getInstance();
        app.setInVideoCallScreenInstance(this);

        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (app.getPhoneState() == Phone.State.OFFHOOK) {
            // While we are in call, the in-call screen should dismiss the keyguard.
            // This allows the user to press Home to go directly home without going through
            // an insecure lock screen.
            // But we do not want to do this if there is no active call so we do not
            // bypass the keyguard if the call is not answered or declined.
            flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        }
        getWindow().addFlags(flags);
		
        int phoneId = getIntent().getIntExtra(SUBSCRIPTION_KEY,
                    app.getDefaultSubscription());
        log("onCreate phoneId: " + phoneId);

        setPhone(app.getPhone(phoneId));  // Sets mPhone and mForegroundCall/mBackgroundCall/mRingingCall

        mCM =  PhoneApp.getInstance().mCM;

		mBluetoothHandsfree = app.getBluetoothHandsfree();
        if (VDBG) log("- mBluetoothHandsfree: " + mBluetoothHandsfree);

        if (mBluetoothHandsfree != null) {
            // The PhoneApp only creates a BluetoothHandsfree instance in the
            // first place if BluetoothAdapter.getDefaultAdapter()
            // succeeds.  So at this point we know the device is BT-capable.
            mBluetoothHeadset = new BluetoothHeadset(this, null);
            if (VDBG) log("- Got BluetoothHeadset: " + mBluetoothHeadset);
        }
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.invideocall_screen);

		initInCallScreen();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "InVideoCallScreen Tag"); 

		// Create the dtmf dialer.  The dialer view we use depends on the
        // current platform:
        //
        // - On non-prox-sensor devices, it's the dialpad contained inside
        //   a SlidingDrawer widget (see dtmf_twelve_key_dialer.xml).
        //
        // - On "full touch UI" devices, it's the compact non-sliding
        //   dialpad that appears on the upper half of the screen,
        //   above the main cluster of InCallTouchUi buttons
        //   (see non_drawer_dialpad.xml).
        //
        // TODO: These should both be ViewStubs, and right here we should
        // inflate one or the other.  (Also, while doing that, let's also
        // move this block of code over to initInCallScreen().)
        //
        SlidingDrawer dialerDrawer;
        if (isTouchUiEnabled()) {
            // This is a "full touch" device.
            mDialerView = (VideoPhoneTwelveKeyDialerView) findViewById(R.id.non_drawer_dtmf_dialer);
            if (DBG) log("- Full touch device!  Found dialerView: " + mDialerView);
            dialerDrawer = null;  // No SlidingDrawer used on this device.
        } else {
            // Use the old-style dialpad contained within the SlidingDrawer.
            mDialerView = (VideoPhoneTwelveKeyDialerView) findViewById(R.id.dtmf_dialer);
            if (DBG) log("- Using SlidingDrawer-based dialpad.  Found dialerView: " + mDialerView);
            dialerDrawer = (SlidingDrawer) findViewById(R.id.dialer_container);
            if (DBG) log("  ...and the SlidingDrawer: " + dialerDrawer);
        }
        // Sanity-check that (regardless of the device) at least the
        // dialer view is present:
        if (mDialerView == null) {
            Log.e(LOG_TAG, "onCreate: couldn't find dialerView", new IllegalStateException());
        }
        // Finally, create the DTMFTwelveKeyDialer instance.
        mDialer = new VideoPhoneTwelveKeyDialer(this, mDialerView, null);
		
		registerForPhoneStates();

		getVideoPhoneConfig();
		updateViewConfig();
		mMyBrightness = getBrightness();

        // if in incoming call, don't checkout network anymore and always start camera preview.
        String action = getIntent().getAction();
        if ((action != null) && (action.equals(Intent.ACTION_CALL))) {
            if (checkIfOkToInitiateOutgoingCall() == InCallInitStatus.SUCCESS) {
        		startCameraPreview();
            }
        } else {
            startCameraPreview();
        }
		
		if (ENABLE_MEDIAPHONE) {
			if (mp == null) {
				final Object syncObj = new Object();
				final CommandsInterface ril = ((TDPhone)getPhoneBase(mPhone)).mCM;//PhoneFactory.getDefaultCM();
			        mMediaPhoneThread = new HandlerThread("MediaPhone"){					
					protected void onLooperPrepared() {
						Log.e(LOG_TAG, "before create mediaphone");
						synchronized(syncObj) {
							mp = MediaPhone.create(ril, DEFAULT_COMM);
							syncObj.notifyAll();
						}
						Log.e(LOG_TAG, "after create mediaphone");
					}
			        };
			        mMediaPhoneThread.start();
				Log.e(LOG_TAG, "before wait mediaphone");
				synchronized(syncObj) {
					try{
						syncObj.wait(1500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				Log.e(LOG_TAG, "after wait mediaphone");
			}
		}
	
		// No need to change wake state here; that happens in onResume() when we
        // are actually displayed.

        // Handle the Intent we were launched with, but only if this is the
        // the very first time we're being launched (ie. NOT if we're being
        // re-initialized after previously being shut down.)
        // Once we're up and running, any future Intents we need
        // to handle will come in via the onNewIntent() method.
        if (savedInstanceState == null) {
            if (DBG) log("onCreate(): this is our very first launch, checking intent...");

            // Stash the result code from internalResolveIntent() in the
            // mInCallInitialStatus field.  If it's an error code, we'll
            // handle it in onResume().
            mInCallInitialStatus = internalResolveIntent(getIntent());
            if (DBG) log("onCreate(): mInCallInitialStatus = " + mInCallInitialStatus);
            if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
                Log.w(LOG_TAG, "onCreate: status " + mInCallInitialStatus
                      + " from internalResolveIntent()");
                // See onResume() for the actual error handling.
            }
        } else {
            mInCallInitialStatus = InCallInitStatus.SUCCESS;
        }
		
        // The "touch lock overlay" feature is used only on devices that
        // *don't* use a proximity sensor to turn the screen off while in-call.
        mUseTouchLockOverlay = !app.proximitySensorModeEnabled();

        mStorageReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                onReceiveMediaBroadcast(intent);
            }
        };
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addDataScheme("file");
        registerReceiver(mStorageReceiver, intentFilter);

		if (DBG) log("onCreate(): exit");
	}

	private void onReceiveMediaBroadcast(Intent intent) {
		String action = intent.getAction();
		if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            log("ACTION_MEDIA_MOUNTED");
			mbExternalStorageAvail = true;
		} else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
            log("ACTION_MEDIA_UNMOUNTED");
			mbExternalStorageAvail = false;
            enableRecord(false, 0);
		}
	}

    /**
     * Sets the Phone object used internally by the InCallScreen.
     *
     * In normal operation this is called from onCreate(), and the
     * passed-in Phone object comes from the PhoneApp.
     * For testing, test classes can use this method to
     * inject a test Phone instance.
     */
    /* package */ void setPhone(Phone phone) {
        mPhone = phone;
        // Hang onto the three Call objects too; they're singletons that
        // are constant (and never null) for the life of the Phone.
        mForegroundCall = mPhone.getForegroundCall();
        mBackgroundCall = mPhone.getBackgroundCall();
        mRingingCall = mPhone.getRingingCall();
    }

    public Phone getPhone() {
        return mPhone;
    }
	@Override
	 protected void onStart() {
		if (DBG) log("onStart()...");
		
		super.onStart();

		// get config from system
		getSysConfig();
	}

	 @Override
	 protected void onResume() {
		 if (DBG) log("onResume()...");
		 super.onResume();
		 mWakeLock.acquire(); 
		 getSysConfig();
		 setMyConfig();
		 updateViewConfig();
		 
		 mIsForegroundActivity = true;
		 
		 final PhoneApp app = PhoneApp.getInstance();
 
		 //app.disableStatusBar();
 
		 // Touch events are never considered "user activity" while the
		 // InCallScreen is active, so that unintentional touches won't
		 // prevent the device from going to sleep.
		 app.setIgnoreTouchUserActivity(true);
 
		 // Disable the status bar "window shade" the entire time we're on
		 // the in-call screen.
//		 NotificationMgr.getDefault().getStatusBarMgr().enableExpandedView(false);
 
		 // Listen for broadcast intents that might affect the onscreen UI.
		 registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
		 
		 // Keep a "dialer session" active when we're in the foreground.
		 // (This is needed to play DTMF tones.)
		 mDialer.startDialerSession();

		 // Check for any failures that happened during onCreate() or onNewIntent().
		if (DBG) log("- onResume: initial status = " + mInCallInitialStatus);
		if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
		    if (DBG) log("- onResume: failure during startup: " + mInCallInitialStatus);

		    // Don't bring up the regular Phone UI!  Instead bring up
		    // something more specific to let the user deal with the
		    // problem.
		    if (handleStartupError(mInCallInitialStatus)) {

		    // But it *is* OK to continue with the rest of onResume(),
		    // since any further setup steps (like updateScreen() and the
		    // CallCard setup) will fall back to a "blank" state if the
		    // phone isn't in use.
		    mInCallInitialStatus = InCallInitStatus.SUCCESS;
		    }
		}

        // Set the volume control handler while we are in the foreground.
        final boolean bluetoothConnected = isBluetoothAudioConnected();

        if (bluetoothConnected) {
            setVolumeControlStream(AudioManager.STREAM_BLUETOOTH_SCO);
        } else {
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }

		takeKeyEvents(true);

		// Before checking the state of the phone, clean up any
        // connections in the DISCONNECTED state.
        // (The DISCONNECTED state is used only to drive the "call ended"
        // UI; it's totally useless when *entering* the InCallScreen.)
        mPhone.clearDisconnected();

        // InCallScreen is now active.
        EventLog.writeEvent(EventLogTags.PHONE_UI_ENTER);

		// Update the poke lock and wake lock when we move to
        // the foreground.
        //
        // But we need to do something special if we're coming
        // to the foreground while an incoming call is ringing:
        if (mPhone.getState() == Phone.State.RINGING) {
            // If the phone is ringing, we *should* already be holding a
            // full wake lock (which we would have acquired before
            // firing off the intent that brought us here; see
            // PhoneUtils.showIncomingCallUi().)
            //
            // We also called preventScreenOn(true) at that point, to
            // avoid cosmetic glitches while we were being launched.
            // So now we need to post an ALLOW_SCREEN_ON message to
            // (eventually) undo the prior preventScreenOn(true) call.
            //
            // (In principle we shouldn't do this until after our first
            // layout/draw pass.  But in practice, the delay caused by
            // simply waiting for the end of the message queue is long
            // enough to avoid any flickering of the lock screen before
            // the InCallScreen comes up.)
            if (VDBG) log("- posting ALLOW_SCREEN_ON message...");
            mHandler.removeMessages(ALLOW_SCREEN_ON);
            mHandler.sendEmptyMessage(ALLOW_SCREEN_ON);

            // TODO: There ought to be a more elegant way of doing this,
            // probably by having the PowerManager and ActivityManager
            // work together to let apps request that the screen on/off
            // state be synchronized with the Activity lifecycle.
            // (See bug 1648751.)
        } else {
            // The phone isn't ringing; this is either an outgoing call, or
            // we're returning to a call in progress.  There *shouldn't* be
            // any prior preventScreenOn(true) call that we need to undo,
            // but let's do this just to be safe:
            app.preventScreenOn(false);
        }
        app.updateWakeState();

		// Restore the mute state if the last mute state change was NOT
        // done by the user.
        if (app.getRestoreMuteOnInCallResume()) {
        	//TS for compile
            PhoneUtils.restoreMuteState();
            app.setRestoreMuteOnInCallResume(false);
        }
		//getVideoPhoneConfig();

		if (DBG) log("onResume: mSurfaceReadyCount = " + mSurfaceReadyCount);


		updateScreen();
		
        Profiler.profileViewCreate(getWindow(), InCallScreen.class.getName());
        if (VDBG) log("onResume() done.");
	 }

	 // onPause is guaranteed to be called when the InCallScreen goes
	 // in the background.
	 @Override
	 protected void onPause() {
		 if (DBG) log("onPause()...");
		 super.onPause();
		 mWakeLock.release();

		//restore system config
		 setSysConfig();

		 mIsForegroundActivity = false;
	 
		 final PhoneApp app = PhoneApp.getInstance();
	 
		 // A safety measure to disable proximity sensor in case call failed
		 // and the telephony state did not change.
		 app.setBeginningCall(false);
	 
		// as a catch-all, make sure that any dtmf tones are stopped
		// when the UI is no longer in the foreground.
		mDialer.onDialerKeyUp(null);

		// Release any "dialer session" resources, now that we're no
		// longer in the foreground.
		mDialer.stopDialerSession();
		
		 // If the device is put to sleep as the phone call is ending,
		 // we may see cases where the DELAYED_CLEANUP_AFTER_DISCONNECT
		 // event gets handled AFTER the device goes to sleep and wakes
		 // up again.
	 
		 // This is because it is possible for a sleep command
		 // (executed with the End Call key) to come during the 2
		 // seconds that the "Call Ended" screen is up.  Sleep then
		 // pauses the device (including the cleanup event) and
		 // resumes the event when it wakes up.
	 
		 // To fix this, we introduce a bit of code that pushes the UI
		 // to the background if we pause and see a request to
		 // DELAYED_CLEANUP_AFTER_DISCONNECT.
	 
		 // Note: We can try to finish directly, by:
		 //  1. Removing the DELAYED_CLEANUP_AFTER_DISCONNECT messages
		 //  2. Calling delayedCleanupAfterDisconnect directly
	 
		 // However, doing so can cause problems between the phone
		 // app and the keyguard - the keyguard is trying to sleep at
		 // the same time that the phone state is changing.  This can
		 // end up causing the sleep request to be ignored.
		 if (mHandler.hasMessages(DELAYED_CLEANUP_AFTER_DISCONNECT)
				 && mPhone.getState() != Phone.State.RINGING) {
			 if (DBG) log("DELAYED_CLEANUP_AFTER_DISCONNECT detected, moving UI to background.");
		 }
	 
		 EventLog.writeEvent(EventLogTags.PHONE_UI_EXIT);
	 
		 // Re-enable the status bar (which we disabled in onResume().)
		 //NotificationMgr.getDefault().getStatusBarMgr().enableExpandedView(true);
	 
		 // Unregister for broadcast intents.  (These affect the visible UI
		 // of the InCallScreen, so we only care about them while we're in the
		 // foreground.)
		 unregisterReceiver(mReceiver);
	 
		 // Re-enable "user activity" for touch events.
		 // We actually do this slightly *after* onPause(), to work around a
		 // race condition where a touch can come in after we've paused
		 // but before the device actually goes to sleep.
		 // TODO: The PowerManager itself should prevent this from happening.
		 mHandler.postDelayed(new Runnable() {
				 public void run() {
					 app.setIgnoreTouchUserActivity(false);
				 }
			 }, 500);
	 
        // Dismiss any dialogs we may have brought up, just to be 100%
        // sure they won't still be around when we get back here.
        dismissAllDialogs();
		
		 //app.reenableStatusBar();
	 
		 // Make sure we revert the poke lock and wake lock when we move to
		 // the background.
		 app.updateWakeState();
	 
		 // clear the dismiss keyguard flag so we are back to the default state
		 // when we next resume
		 updateKeyguardPolicy(false);
		  log("onPause()...mInCallScreenMode: " + mInCallScreenMode);
		 if (InCallScreenMode.FALL_BACK == mInCallScreenMode) {
		 	finish();
		 }
	 }
	 
	 @Override
	 protected void onStop() {
		 if (DBG) log("onStop()...");
		 
		 super.onStop();
		 
		 
		 Phone.State state = mPhone.getState();
		 if (DBG) log("onStop: state = " + state);
	 }
	 
	 @Override
	 protected void onDestroy() {
		 if (DBG) log("onDestroy()...");
		 super.onDestroy(); 
		 
		 if (mStorageReceiver != null) {
			 unregisterReceiver(mStorageReceiver);
			 mStorageReceiver = null;
		 }
        
		 if (mStartPreviewThread != null) {
			 try {
				 mStartPreviewThread.join();		 
			 } catch (InterruptedException ex) {
		            log("mStartPreviewThread.quit() exception " + ex);
		        }
		 }
	 
      	 setCameraFlag(false);
      	 closeCamera(1000);
		 mCallTime.cancelTimer();
		 
		 if (mp != null){
			 mp.release();
			 mp = null;
			 if (DBG) log("mMediaPhoneThread.quit(): " + mMediaPhoneThread.quit());
		 }
		 // Set the magic flag that tells us NOT to handle any handler
		 // messages that come in asynchronously after we get destroyed.
		 mIsDestroyed = true;
	 
		 final PhoneApp app = PhoneApp.getInstance();
		 app.setInVideoCallScreenInstance(null);
	 
        mDialer.clearInCallScreenReference();
        mDialer = null;
		
		 unregisterForPhoneStates();
		 // No need to change wake state here; that happens in onPause() when we
		 // are moving out of the foreground.
	 
		 if (mBluetoothHeadset != null) {
			 mBluetoothHeadset.close();
			 mBluetoothHeadset = null;
		 }

         if (mCallIntent != null) {
    		 if (LastAction.ACTION_VIDEOCALL == mLastAction) {
    		 	startActivity(mCallIntent);
    		 } else if (LastAction.ACTION_VOICECALL == mLastAction) {
    			Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);
    			try {
    			intent.setData(Uri.fromParts("tel", getInitialNumber(mCallIntent), null));
    			} catch (PhoneUtils.VoiceMailNumberMissingException ex) {
    				log(" exception when getInitialNumber: " + ex);
    			}
            		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            		startActivity(intent);
    		 }
         }
		 mLastAction = LastAction.ACTION_NONE;
		 
        // Dismiss all dialogs, to be absolutely sure we won't leak any of
        // them while changing orientation.
        dismissAllDialogs();
	 }

	 /**
	  * Dismisses the in-call screen.
	  *
	  * We never *really* finish() the InCallScreen, since we don't want to
	  * get destroyed and then have to be re-created from scratch for the
	  * next call.	Instead, we just move ourselves to the back of the
	  * activity stack.
	  *
	  * This also means that we'll no longer be reachable via the BACK
	  * button (since moveTaskToBack() puts us behind the Home app, but the
	  * home app doesn't allow the BACK key to move you any farther down in
	  * the history stack.)
	  *
	  * (Since the Phone app itself is never killed, this basically means
	  * that we'll keep a single InCallScreen instance around for the
	  * entire uptime of the device.  This noticeably improves the UI
	  * responsiveness for incoming calls.)
	  */
	 /*@Override
	 public void finish() {
		 if (DBG) log("finish()...");
		 moveTaskToBack(true);
	 }*/
	 
	 /**
	  * End the current in call screen session.
	  *
	  * This must be called when an InCallScreen session has
	  * complete so that the next invocation via an onResume will
	  * not be in an old state.
	  */
	 public void endInCallScreenSession() {
		 if (DBG) log("endInCallScreenSession()...");
		 //moveTaskToBack(true);
		 setInCallScreenMode(InCallScreenMode.UNDEFINED);
		 this.finish();
	 }
	 
	 /* package */ boolean isForegroundActivity() {
		 return mIsForegroundActivity;
	 }
	 
	 /* package */ void updateKeyguardPolicy(boolean dismissKeyguard) {
		 if (dismissKeyguard) {
			 getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		 } else {
			 getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		 }
	 }
	 
	 private void registerForPhoneStates() {
		 if (!mRegisteredForPhoneStates) {
			 mPhone.registerForPreciseVideoCallStateChanged(mHandler, PHONE_STATE_CHANGED, null);
			 mPhone.registerForVideoCallDisconnect(mHandler, PHONE_DISCONNECT, null);
/*			 int phoneType = mPhone.getPhoneType();
			 if (phoneType == Phone.PHONE_TYPE_GSM) {
				 mPhone.registerForMmiInitiate(mHandler, PhoneApp.MMI_INITIATE, null);
	 
				 // register for the MMI complete message.	Upon completion,
				 // PhoneUtils will bring up a system dialog instead of the
				 // message display class in PhoneUtils.displayMMIComplete().
				 // We'll listen for that message too, so that we can finish
				 // the activity at the same time.
				 mPhone.registerForMmiComplete(mHandler, PhoneApp.MMI_COMPLETE, null);
			 } else if (phoneType == Phone.PHONE_TYPE_CDMA) {
				 if (DBG) log("Registering for Call Waiting.");
				 mPhone.registerForCallWaiting(mHandler, PHONE_CDMA_CALL_WAITING, null);
			 } else {
				 throw new IllegalStateException("Unexpected phone type: " + phoneType);
			 }
	 
			 mPhone.setOnPostDialCharacter(mHandler, POST_ON_DIAL_CHARS, null);
			 mPhone.registerForSuppServiceFailed(mHandler, SUPP_SERVICE_FAILED, null);
			 if (phoneType == Phone.PHONE_TYPE_CDMA) {
				 mPhone.registerForCdmaOtaStatusChange(mHandler, EVENT_OTA_PROVISION_CHANGE, null);
			 }*/
			 mRegisteredForPhoneStates = true;
		 }
	 }
	 
	 private void unregisterForPhoneStates() {
		 mPhone.unregisterForPreciseVideoCallStateChanged(mHandler);
		 mPhone.unregisterForVideoCallDisconnect(mHandler);
		 mPhone.unregisterForMmiInitiate(mHandler);
		 mPhone.setOnPostDialCharacter(null, POST_ON_DIAL_CHARS, null);
		 mRegisteredForPhoneStates = false;
	 }
	 
	 /* package */ void updateAfterRadioTechnologyChange() {
		 if (DBG) Log.d(LOG_TAG, "updateAfterRadioTechnologyChange()...");
		 // Unregister for all events from the old obsolete phone
		 unregisterForPhoneStates();
	 
		 // (Re)register for all events relevant to the new active phone
		 registerForPhoneStates();
	 }
	 
	 @Override
	 protected void onNewIntent(Intent intent) {
		 if (DBG) log("onNewIntent: intent=" + intent);
	 
		 // We're being re-launched with a new Intent.	Since we keep
		 // around a single InCallScreen instance for the life of the phone
		 // process (see finish()), this sequence will happen EVERY time
		 // there's a new incoming or outgoing call except for the very
		 // first time the InCallScreen gets created.  This sequence will
		 // also happen if the InCallScreen is already in the foreground
		 // (e.g. getting a new ACTION_CALL intent while we were already
		 // using the other line.)
	 
		 // Stash away the new intent so that we can get it in the future
		 // by calling getIntent().  (Otherwise getIntent() will return the
		 // original Intent from when we first got created!)
		 setIntent(intent);
	 
		 // Activities are always paused before receiving a new intent, so
		 // we can count on our onResume() method being called next.
	 
		 // Just like in onCreate(), handle this intent, and stash the
		 // result code from internalResolveIntent() in the
		 // mInCallInitialStatus field.  If it's an error code, we'll
		 // handle it in onResume().
		 mInCallInitialStatus = internalResolveIntent(intent);
		 if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
			 Log.w(LOG_TAG, "onNewIntent: status " + mInCallInitialStatus
				   + " from internalResolveIntent()");
			 // See onResume() for the actual error handling.
		 }
	 }
 
	 /* package */ InCallInitStatus internalResolveIntent(Intent intent) {
		 if (intent == null || intent.getAction() == null) {
			 return InCallInitStatus.SUCCESS;
		 }
	 
		 String action = intent.getAction();
		 if (DBG) log("internalResolveIntent: action=" + action);
	 
		 // The calls to setRestoreMuteOnInCallResume() inform the phone
		 // that we're dealing with new connections (either a placing an
		 // outgoing call or answering an incoming one, and NOT handling
		 // an aborted "Add Call" request), so we should let the mute state
		 // be handled by the PhoneUtils phone state change handler.
		 final PhoneApp app = PhoneApp.getInstance();
		 // If OTA Activation is configured for Power up scenario, then
		 // InCallScreen UI started with Intent of ACTION_SHOW_ACTIVATION
		 // to show OTA Activation screen at power up.
		 if (action.equals(Intent.ACTION_ANSWER)) {
			 internalAnswerCall();
			 app.setRestoreMuteOnInCallResume(false);
			 return InCallInitStatus.SUCCESS;
		 } else if (action.equals(Intent.ACTION_CALL)){	
		 	mbIncoming = false;
            mCallIntent = intent;
		 	if (mPhone.getState() != Phone.State.IDLE) {
				if (PhoneUtils.isVideoCall()) {
					Log.e(LOG_TAG, "Cann't make another videocall, during video call");
					return InCallInitStatus.ALREADY_IN_3GCALL;
				} else {
					Log.e(LOG_TAG, "Cann't make another videocall, during voice call");
					return InCallInitStatus.ALREADY_IN_2GCALL;
				}
			}
			
			 app.setRestoreMuteOnInCallResume(false);	 
			 InCallInitStatus status = placeCall(intent);
			 Log.w(LOG_TAG, "internalResolveIntent: placecall status: " + status);
			 return status;
		 } else if (action.equals(intent.ACTION_MAIN)) {
		 	 //mbIncoming = intent.getBooleanExtra(PhoneApp.EXTRA_IS_INCOMINGCALL, false);
			 log("internalResolveIntent: mbIncoming=" + mbIncoming);
			 if (mbIncoming){
				if (ENABLE_MEDIAPHONE) {
					initMediaPhone(mPhone);
					mp.onCodecRequest(MediaPhone.CODEC_OPEN, 0);
				}
	            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
	                boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
	                if (VDBG) log("- internalResolveIntent: SHOW_DIALPAD_EXTRA: " + showDialpad);
	                if (showDialpad) {
	                    mDialer.openDialer(false);  // no "opening" animation
	                } else {
	                    mDialer.closeDialer(false);  // no "closing" animation
	                }
	            }
				 return InCallInitStatus.SUCCESS;
			 } else {
			 	// re-enter video call screen
			 	mbIncoming = false;
			 }
			 return InCallInitStatus.SUCCESS;
		 } else {
			 Log.w(LOG_TAG, "internalResolveIntent: unexpected intent action: " + action);
			 // But continue the best we can (basically treating this case
			 // like ACTION_MAIN...)
			 return InCallInitStatus.SUCCESS;
		 }
	 }
	 
	 private void initInCallScreen() {
		 if (VDBG) log("initInCallScreen()...");
	 
		 // Have the WindowManager filter out touch events that are "too fat".
		 getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
	 
		 // Run in a 32-bit window, which improves the appearance of some
		 // semitransparent artwork in the in-call UI (like the CallCard
		 // photo borders).
		 getWindow().setFormat(PixelFormat.RGBX_8888);

		 // Helper class to keep track of enabledness/state of UI controls
//		 mInCallControlState = new InCallControlState(this, mPhone);
		
         mDensity = getResources().getDisplayMetrics().density;
         if (DBG) log("- Density: " + mDensity);

        mAllowVolumeButtion = getResources().getBoolean(R.bool.config_show_vt_onscreen_volume_button);
	mButtonIncrease = (Button)findViewById(R.id.increase_volume);
	mButtonDecline = (Button)findViewById(R.id.decline_volume);
	if (mAllowVolumeButtion) {
		mButtonIncrease.setOnClickListener(this);
		mButtonDecline.setOnClickListener(this);
		mButtonIncrease.setText("+");
		mButtonDecline.setText("-");
	} else {
		mButtonIncrease.setVisibility(View.GONE);
		mButtonDecline.setVisibility(View.GONE);
	}

        // Text colors
         mTextColorDefaultPrimary =  // corresponds to textAppearanceLarge
                getResources().getColor(android.R.color.primary_text_dark);
         mTextColorDefaultSecondary =  // corresponds to textAppearanceSmall
                getResources().getColor(android.R.color.secondary_text_dark);
         mTextColorConnected = getResources().getColor(R.color.incall_textConnected);
         mTextColorConnectedBluetooth =
                getResources().getColor(R.color.incall_textConnectedBluetooth);
         mTextColorEnded = getResources().getColor(R.color.incall_textEnded);
         mTextColorOnHold = getResources().getColor(R.color.incall_textOnHold);
		
		 
		 mLargeVideoPhoneView =(SurfaceView)findViewById(R.id.LargeVideoPhoneView);
		 mSmallVideoPhoneView = (SurfaceView)findViewById(R.id.SmallVideoPhoneView);
		 initVideoPoneView(mLargeVideoPhoneView);
		 initVideoPoneView(mSmallVideoPhoneView);
		 mBrightnessAdjustMenu = (AdjustMenuView)findViewById(R.id.brightness_adjustmenu);
		 mCameraBrightnessAdjustMenu = (AdjustMenuView)findViewById(R.id.camera_brightness_adjustmenu);
		 mCameraContrastAdjustMenu = (AdjustMenuView)findViewById(R.id.camera_contrast_adjustmenu);
        if((mBrightnessAdjustMenu != null)
			&& (mCameraBrightnessAdjustMenu != null)
			&& (mCameraContrastAdjustMenu != null)){
			mBrightnessAdjustMenu.setMax(MAXIMUM_BACKLIGHT);
			mBrightnessAdjustMenu.setMin(MINIMUM_BACKLIGHT);
        	mBrightnessAdjustMenu.setOnSeekBarChangeListener(this);
			mCameraBrightnessAdjustMenu.setMax(MAXIMUM_CAMERA_BACKLIGHT);
			mCameraBrightnessAdjustMenu.setMin(MINIMUM_CAMERA_BACKLIGHT);
        	mCameraBrightnessAdjustMenu.setOnSeekBarChangeListener(this);
			mCameraContrastAdjustMenu.setMax(MAXIMUM_CAMERA_CONTRAST);
			mCameraContrastAdjustMenu.setMin(MINIMUM_CAMERA_CONTRAST);
        	mCameraContrastAdjustMenu.setOnSeekBarChangeListener(this);
        }

     //SurfaceHolder largeHolder = mLargeVideoPhoneView.getHolder();
     //largeHolder.addCallback(mLargeVideoPhoneView);

     //SurfaceHolder smallHolder = mSmallVideoPhoneView.getHolder();
     //smallHolder.addCallback(mSmallVideoPhoneView);

		 mInCallPanel = findViewById(R.id.InCallPanel);
	 	 mInComingCallPanel = findViewById(R.id.InComingCallPanel);
		 mDialerBtn = (Button)findViewById(R.id.dialer);
		 mHangupBtn = (Button)findViewById(R.id.hangup);
		 mAnswerBtn = (Button)findViewById(R.id.answer);
		 mDeclineBtn = (Button)findViewById(R.id.decline);
		 mDialerBtn.setOnClickListener(this);
		 mHangupBtn.setOnClickListener(this);
		 mAnswerBtn.setOnClickListener(this);
		 mDeclineBtn.setOnClickListener(this);
		 
		 // "Upper" and "lower" title widgets
		 mName = (TextView) findViewById(R.id.name);
		 mPhoneNumber = (TextView) findViewById(R.id.phoneNumber);
		 mUpperTitle = (TextView) findViewById(R.id.upperTitle);
		 mElapsedTime = (TextView) findViewById(R.id.elapsedTime);
         //mLabel = (TextView) findViewById(R.id.label);
         mBeginTime = (TextView) findViewById(R.id.beginTime);
		 
		 mCallInfo = (ViewGroup)findViewById(R.id.CallInfo);
		 mCallTime = new VideoCallTime(this);
		 // create a new object to track the state for the photo.
		 mPhotoTracker = new ContactsAsyncHelper.ImageTracker();
	 }
	 
	 /**
	  * Returns true if the phone is "in use", meaning that at least one line
	  * is active (ie. off hook or ringing or dialing).  Conversely, a return
	  * value of false means there's currently no phone activity at all.
	  */
	 private boolean phoneIsInUse() {
		 return mPhone.getState() != Phone.State.IDLE;
	 }
	 
	 @Override
	 public boolean onKeyUp(int keyCode, KeyEvent event) {
		 // if (DBG) log("onKeyUp(keycode " + keyCode + ")...");
	 
		 // push input to the dialer.
		 if ((mDialer != null) && (mDialer.onDialerKeyUp(event))){
			 return true;
		 } else if (keyCode == KeyEvent.KEYCODE_CALL) {
			 // Always consume CALL to be sure the PhoneWindow won't do anything with it
			 return true;
		 }
		 return super.onKeyUp(keyCode, event);
	 }

	 @Override
     public boolean onKeyDown(int keyCode, KeyEvent event) {
     	if (DBG) log("onKeyDown(keycode " + keyCode + ")...");

        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
				if (mPhone.getState() == Phone.State.RINGING)
					internalAnswerCall();
				break;
			case KeyEvent.KEYCODE_ENDCALL:
				if (mPhone.getState() == Phone.State.RINGING)
					internalHangupRingingCall();
				else if (mPhone.getState() == Phone.State.OFFHOOK)
					internalHangup();
				break;
			case KeyEvent.KEYCODE_MENU:
				//event.startTracking();
				if (event.getRepeatCount() != 0) {
					if ((event.getFlags()&KeyEvent.FLAG_LONG_PRESS) != 0)
						return true;
				}
		}
		
        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }

		return super.onKeyDown(keyCode, event);
	}

	 /**
	  * Something has changed in the phone's state.  Update the UI.
	  */
	 private void onPhoneStateChanged(AsyncResult r) {
		 if (DBG) log("onPhoneStateChanged()...");
	 
		 // There's nothing to do here if we're not the foreground activity.
		 // (When we *do* eventually come to the foreground, we'll do a
		 // full update then.)
		 if (!mIsForegroundActivity) {
			 if (DBG) log("onPhoneStateChanged: Activity not in foreground! Bailing out...");
			 return;
		 }

		 if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
			 if (DBG) log("onPhoneStateChanged: failure during startup! Bailing out...");
			 return;
		 }
	 
		 updateScreen();

		Call.State fgCallState = mForegroundCall.getState();
		
		 if (DBG) log("mLastFGCallState: " + mLastFGCallState + ", fgCallState: " + fgCallState);
		if ( (mLastFGCallState != Call.State.ACTIVE) && (fgCallState == Call.State.ACTIVE)){
			mHandler.sendEmptyMessageDelayed(DELAYED_SWITCH_SPEAKER, 1000);
		}
		
		mLastFGCallState = mForegroundCall.getState();
			
		 // Make sure we update the poke lock and wake lock when certain
		 // phone state changes occur.
		 PhoneApp.getInstance().updateWakeState();
	 }
	 
	 /**
	  * Updates the UI after a phone connection is disconnected, as follows:
	  *
	  * - If this was a missed or rejected incoming call, and no other
	  *   calls are active, dismiss the in-call UI immediately.  (The
	  *   CallNotifier will still create a "missed call" notification if
	  *   necessary.)
	  *
	  * - With any other disconnect cause, if the phone is now totally
	  *   idle, display the "Call ended" state for a couple of seconds.
	  *
	  * - Or, if the phone is still in use, stay on the in-call screen
	  *   (and update the UI to reflect the current state of the Phone.)
	  *
	  * @param r r.result contains the connection that just ended
	  */
	  private void onDisconnect(AsyncResult r) {
  		if (mInCallScreenMode == InCallScreenMode.FALL_BACK){
	        if (DBG) log("onDisconnect: during fallback, just return");
			return;
		}
		displayStartTime();
		
		 Connection c = (Connection) r.result;
		 Connection.DisconnectCause cause = c.getDisconnectCause();
		 if (DBG) log("onDisconnect: " + c + ", cause=" + cause);
 
		 boolean currentlyIdle = !phoneIsInUse();
		 
		 // Any time a call disconnects, clear out the "history" of DTMF
		 // digits you typed (to make sure it doesn't persist from one call
		 // to the next.)
		 mDialer.clearDigits();
 
		 // Under certain call disconnected states, we want to alert the user
		 // with a dialog instead of going through the normal disconnect
		 // routine.
		 if (cause == Connection.DisconnectCause.CALL_BARRED) {
			 showGenericErrorDialog(R.string.callFailed_cb_enabled, false);
			 return;
		 } else if (cause == Connection.DisconnectCause.FDN_BLOCKED) {
			 showGenericErrorDialog(R.string.callFailed_fdn_only, false);
			 return;
		 } else if (cause == Connection.DisconnectCause.CS_RESTRICTED) {
			 showGenericErrorDialog(R.string.callFailed_dsac_restricted, false);
			 return;
		 } else if (cause == Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY) {
			 showGenericErrorDialog(R.string.callFailed_dsac_restricted_emergency, false);
			 return;
		 } else if (cause == Connection.DisconnectCause.CS_RESTRICTED_NORMAL) {
			 showGenericErrorDialog(R.string.callFailed_dsac_restricted_normal, false);
			 return;
		 }
 
		 // Note: see CallNotifier.onDisconnect() for some other behavior
		 // that might be triggered by a disconnect event, like playing the
		 // busy/congestion tone.
 
		 // Keep track of whether this call was user-initiated or not.
		 // (This affects where we take the user next; see delayedCleanupAfterDisconnect().)
		 mShowCallLogAfterDisconnect = !c.isIncoming();
 
		 {
			 if (VDBG) log("- onDisconnect: delayed bailout...");
			 // Stay on the in-call screen for now.  (Either the phone is
			 // still in use, or the phone is idle but we want to display
			 // the "call ended" state for a couple of seconds.)
 
			 // Force a UI update in case we need to display anything
			 // special given this connection's DisconnectCause (see
			 // CallCard.getCallFailedString()).
			 updateScreen();
 
			 // Display the special "Call ended" state when the phone is idle
			 // but there's still a call in the DISCONNECTED state:
			 if (currentlyIdle
				 && ((mForegroundCall.getState() == Call.State.DISCONNECTED)
					 || (mBackgroundCall.getState() == Call.State.DISCONNECTED))) {
				 if (VDBG) log("- onDisconnect: switching to 'Call ended' state...");
				 setInCallScreenMode(InCallScreenMode.CALL_ENDED);
			 }
             setUpperTitle(mPhone.getContext().getString(R.string.card_title_call_ended), mTextColorEnded, Call.State.DISCONNECTED);
 
			 // Finally, arrange for delayedCleanupAfterDisconnect() to get
			 // called after a short interval (during which we display the
			 // "call ended" state.)  At that point, if the
			 // Phone is idle, we'll finish out of this activity.
			 int callEndedDisplayDelay =
					 (cause == Connection.DisconnectCause.LOCAL)
					 ? CALL_ENDED_SHORT_DELAY : CALL_ENDED_LONG_DELAY;
			 mHandler.removeMessages(DELAYED_CLEANUP_AFTER_DISCONNECT);
			 mHandler.sendEmptyMessageDelayed(DELAYED_CLEANUP_AFTER_DISCONNECT,
											  callEndedDisplayDelay);
		 }
	  }
	  

	 
	  private void onDisconnect() {
        if (DBG) log("onDisconnect: ");

        boolean currentlyIdle = !phoneIsInUse();
        {
            if (VDBG) log("- onDisconnect: delayed bailout...");
            // Stay on the in-call screen for now.  (Either the phone is
            // still in use, or the phone is idle but we want to display
            // the "call ended" state for a couple of seconds.)

            // Force a UI update in case we need to display anything
            // special given this connection's DisconnectCause (see
            // CallCard.getCallFailedString()).
            updateScreen();

            // Display the special "Call ended" state when the phone is idle
            // but there's still a call in the DISCONNECTED state:
            if (currentlyIdle
                && ((mForegroundCall.getState() == Call.State.DISCONNECTED)
                    || (mBackgroundCall.getState() == Call.State.DISCONNECTED))) {
                if (VDBG) log("- onDisconnect: switching to 'Call ended' state...");
                setInCallScreenMode(InCallScreenMode.CALL_ENDED);
            }
            setUpperTitle(mPhone.getContext().getString(R.string.card_title_call_ended), mTextColorEnded, Call.State.DISCONNECTED);


            mHandler.removeMessages(DELAYED_CLEANUP_AFTER_DISCONNECT);
            mHandler.sendEmptyMessageDelayed(DELAYED_CLEANUP_AFTER_DISCONNECT,
                                             CALL_ENDED_SHORT_DELAY);
        }
    }

	private void updateMainCallStatus() {
        //if (DBG) log("updateMainCallStatus()...");
		Phone.State state = mPhone.getState();
		
		if (state == Phone.State.RINGING){
			mRingingCall = mPhone.getRingingCall();
			displayMainCallStatus(mPhone,mRingingCall);
		} else if (state == Phone.State.OFFHOOK){
			mForegroundCall = mPhone.getForegroundCall();
			displayMainCallStatus(mPhone,mForegroundCall);
		} else {
			displayMainCallStatus(mPhone,null);
		}
	}

	 /**
	  * Updates the state of the in-call UI based on the current state of
	  * the Phone.
	  */
	 private void updateScreen() {
        if (DBG) log("updateScreen()...");
        
        // Don't update anything if we're not in the foreground (there's
        // no point updating our UI widgets since we're not visible!)
        // Also note this check also ensures we won't update while we're
        // in the middle of pausing, which could cause a visible glitch in
        // the "activity ending" transition.
        if (!mIsForegroundActivity) {
            if (DBG) log("- updateScreen: not the foreground Activity! Bailing out...");
            return;
        }

        final PhoneApp app = PhoneApp.getInstance();

        if (mInCallScreenMode == InCallScreenMode.CALL_ENDED) {
            if (DBG) log("- updateScreen: call ended state (NOT updating in-call UI)...");
            // Actually we do need to update one thing: the background.
            updateInCallBackground();
            return;
        }

        if (DBG) log("- updateScreen: updating the in-call UI...");
        updateDialpadVisibility();
        updateInCallBackground();
		updateInCallPanel();
		updateMainCallStatus();

    }

	 /**
	  * Given the Intent we were initially launched with,
	  * figure out the actual phone number we should dial.
	  *
	  * @return the phone number corresponding to the
	  *   specified Intent, or null if the Intent is not
	  *   an ACTION_CALL intent or if the intent's data is
	  *   malformed or missing.
	  *
	  * @throws VoiceMailNumberMissingException if the intent
	  *   contains a "voicemail" URI, but there's no voicemail
	  *   number configured on the device.
	  */
	 private String getInitialNumber(Intent intent)
			 throws PhoneUtils.VoiceMailNumberMissingException {
		 String action = intent.getAction();
		 if (action == null) {
			 return null;
		 }
	 
		 if (action != null && action.equals(Intent.ACTION_CALL) &&
				 intent.hasExtra(Intent.EXTRA_PHONE_NUMBER)) {
			 return intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
		 }
	 
		 //TS for compile
		 return PhoneUtils.getNumberFromIntent(this, intent);
	 }

	 /**
     * Make a call to whomever the intent tells us to.
     *
     * @param intent the Intent we were launched with
     * @return InCallInitStatus.SUCCESS if we successfully initiated an
     *    outgoing call.  If there was some kind of failure, return one of
     *    the other InCallInitStatus codes indicating what went wrong.
     */
    private InCallInitStatus placeCall(Intent intent) {
        if (VDBG) log("placeCall()...  intent = " + intent);
        String number;

        // Check the current ServiceState to make sure it's OK
        // to even try making a call.
        InCallInitStatus okToCallStatus = checkIfOkToInitiateOutgoingCall();

        try {
            number = getInitialNumber(intent);
        } catch (PhoneUtils.VoiceMailNumberMissingException ex) {
            // If the call status is NOT in an acceptable state, it
            // may effect the way the voicemail number is being
            // retrieved.  Mask the VoiceMailNumberMissingException
            // with the underlying issue of the phone state.
            if (okToCallStatus != InCallInitStatus.SUCCESS) {
                if (DBG) log("Voicemail number not reachable in current SIM card state.");
                return okToCallStatus;
            }
            if (DBG) log("VoiceMailNumberMissingException from getInitialNumber()");
            return InCallInitStatus.VOICEMAIL_NUMBER_MISSING;
        }

        if (number == null) {
            Log.w(LOG_TAG, "placeCall: couldn't get a phone number from Intent " + intent);
            return InCallInitStatus.NO_PHONE_NUMBER_SUPPLIED;
        }
		
		if (okToCallStatus != InCallInitStatus.SUCCESS) {					
			return okToCallStatus;
		}

        final PhoneApp app = PhoneApp.getInstance();

        mNeedShowCallLostDialog = false;

        // We have a valid number, so try to actually place a call:
        // make sure we pass along the intent's URI which is a
        // reference to the contact. We may have a provider gateway
        // phone number to use for the outgoing call.
        int callStatus;
        Uri contactUri = intent.getData();
	mCallData = new CallData(number, contactUri);

	return internalPlaceCall();
	
    }

	private InCallInitStatus internalPlaceCall() {
        int callStatus;
		InCallInitStatus ret = InCallInitStatus.SUCCESS;

		String number = mCallData.number;
		Uri contactUri = mCallData.contactUri;
		
		if (InCallInitStatus.SUCCESS != ret)
			return ret;
		
		Log.w(LOG_TAG, "internalPlaceCall");

		if (ENABLE_MEDIAPHONE) {
			initMediaPhone(mPhone);
		}

		callStatus = PhoneUtils.placeVideoCall(mPhone, number, contactUri);

		switch (callStatus) {
			case PhoneUtils.CALL_STATUS_DIALED:
				if (VDBG) log("placeCall: PhoneUtils.placeVideoCall() succeeded for regular call '"
				             + number + "'.");
				
                // Any time we initiate a call, force the DTMF dialpad to
                // close.  (We want to make sure the user can see the regular
                // in-call UI while the new call is dialing, and when it
                // first gets connected.)
                mDialer.closeDialer(false);  // no "closing" animation

                // Also, in case a previous call was already active (i.e. if
                // we just did "Add call"), clear out the "history" of DTMF
                // digits you typed, to make sure it doesn't persist from the
                // previous call to the new call.
                // TODO: it would be more precise to do this when the actual
                // phone state change happens (i.e. when a new foreground
                // call appears and the previous call moves to the
                // background), but the InCallScreen doesn't keep enough
                // state right now to notice that specific transition in
                // onPhoneStateChanged().
                mDialer.clearDigits();
				
				return InCallInitStatus.SUCCESS;
			case PhoneUtils.CALL_STATUS_FAILED:
				Log.w(LOG_TAG, "placeCall: PhoneUtils.placeVideoCall() FAILED for number '"
				      + number + "'.");
				// We couldn't successfully place the call; there was some
				// failure in the telephony layer.
				return InCallInitStatus.CALL_FAILED;
		      case PhoneUtils.CALL_STATUS_FAILED_ONLY_FDN:
                      Log.w(LOG_TAG, "placeCall: PhoneUtils.placeCall() FAILED for number '"
                      + number + "'.");
                      // We couldn't successfully place the call; there was some
                      // failure in the telephony layer.
                       return InCallInitStatus.CALL_FAILED_FDN_ONLY;
			default:
				Log.w(LOG_TAG, "placeCall: unknown callStatus " + callStatus
				      + " from PhoneUtils.placeVideoCall() for number '" + number + "'.");
				return InCallInitStatus.SUCCESS;  // Try to continue anyway...
		}
	}
	 /**
	  * Checks the current ServiceState to make sure it's OK
	  * to try making an outgoing call to the specified number.
	  *
	  * @return InCallInitStatus.SUCCESS if it's OK to try calling the specified
	  *    number.	If not, like if the radio is powered off or we have no
	  *    signal, return one of the other InCallInitStatus codes indicating what
	  *    the problem is.
	  */
	 private InCallInitStatus checkIfOkToInitiateOutgoingCall() {
		if (Settings.System.getInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) > 0) {
			return InCallInitStatus.AIRPLANE_MODE_ON;
		}
		
		 if (!isIn3GNetwork())
		 	return InCallInitStatus.NO_IN_3G;
		 	//isIn3GNetwork();
		 
		 // Watch out: do NOT use PhoneStateIntentReceiver.getServiceState() here;
		 // that's not guaranteed to be fresh.	To synchronously get the
		 // CURRENT service state, ask the Phone object directly:
		 int state = mPhone.getServiceState().getState();
		 if (VDBG) log("checkIfOkToInitiateOutgoingCall: ServiceState = " + state);
	 
		 switch (state) {
			 case ServiceState.STATE_IN_SERVICE:
				 // Normal operation.  It's OK to make outgoing calls.
				 return InCallInitStatus.SUCCESS;
	 
			 case ServiceState.STATE_POWER_OFF:
				 // Radio is explictly powered off.
				 return InCallInitStatus.POWER_OFF;
	 
			 case ServiceState.STATE_EMERGENCY_ONLY:
				 // The phone is registered, but locked. Only emergency
				 // numbers are allowed.
				 // Note that as of Android 2.0 at least, the telephony layer
				 // does not actually use ServiceState.STATE_EMERGENCY_ONLY,
				 // mainly since there's no guarantee that the radio/RIL can
				 // make this distinction.	So in practice the
				 // InCallInitStatus.EMERGENCY_ONLY state and the string
				 // "incall_error_emergency_only" are totally unused.
				 return InCallInitStatus.EMERGENCY_ONLY;
	 
			 case ServiceState.STATE_OUT_OF_SERVICE:
				 // No network connection.
				 return InCallInitStatus.OUT_OF_SERVICE;
	 
			 default:
				 throw new IllegalStateException("Unexpected ServiceState: " + state);
		 }

	 }

	 //
	 // Helper functions for answering incoming calls.
	 //
	 
	 /**
	  * Answer a ringing call.	This method does nothing if there's no
	  * ringing or waiting call.
	  */
	 /* package */ void internalAnswerCall() {
        if (DBG) log("internalAnswerCall()...");
        // if (DBG) PhoneUtils.dumpCallState(mPhone);

        final boolean hasRingingCall = !mRingingCall.isIdle();

        if (hasRingingCall) {		
//		mLocalVideoPhoneView.setVisibility(View.VISIBLE);
//		mRemoteVideoPhoneView.setVisibility(View.VISIBLE);
//		Message.obtain(mHandler, DELAYED_PLACE_CALL);
		    PhoneUtils.answerCall(mRingingCall);
        }
    }
	 
	 /**
	  * Answer the ringing call *and* hang up the ongoing call.
	  */
	 /* package */ void internalAnswerAndEnd() {
		 if (DBG) log("internalAnswerAndEnd()...");
		 // if (DBG) PhoneUtils.dumpCallState(mPhone);
		 PhoneUtils.answerAndEndActive(mCM, mRingingCall);
	 }
	 
	 /**
	  * Hang up the ringing call (aka "Don't answer").
	  */
	 /* package */ void internalHangupRingingCall() {
		if (DBG) log("internalHangupRingingCall()...");
	        if (VDBG) PhoneUtils.dumpCallManager();
	        // In the rare case when multiple calls are ringing, the UI policy
	        // it to always act on the first ringing call.v
        	PhoneUtils.hangupRingingCall(mCM.getFirstActiveRingingCall());
	 }
	 
	 /**
	  * Hang up the current active call.
	  */
	 /* package */ void internalHangup() {
		 if (DBG) log("internalHangup()...");
         mbHangupByUser = true;
		 PhoneUtils.hangup(mCM);
	 }

	 /**
	  * Fallback the current ringing call.
	  */
	 /* package */ void internalFallBack() {
		 if (DBG) log("internalFallBack()...");
		 PhoneUtils.fallBack(mPhone);
		 
      	 Intent intent = new Intent();
      	 intent.setClass(getWindow().getContext(), ToastActivity.class);
      	 startActivity(intent);
	 }

	 /**
	  * Sets the current high-level "mode" of the in-call UI.
	  *
	  * NOTE: if newMode is CALL_ENDED, the caller is responsible for
	  * posting a delayed DELAYED_CLEANUP_AFTER_DISCONNECT message, to make
	  * sure the "call ended" state goes away after a couple of seconds.
	  */
	 private void setInCallScreenMode(InCallScreenMode newMode) {
        if (DBG) log("setInCallScreenMode: " + newMode);
        mInCallScreenMode = newMode;
        switch (mInCallScreenMode) {
            case CALL_ENDED:
                break;

            case NORMAL:
                break;

            case UNDEFINED:
                break;
        }
		
        // Update the visibility of the DTMF dialer tab on any state
        // change.
        updateDialpadVisibility();
    }

	 public void resetInCallScreenMode() {
		 if (DBG) log("resetInCallScreenMode - InCallScreenMode set to UNDEFINED");
		 setInCallScreenMode(InCallScreenMode.UNDEFINED);
	 }

	 private void log(String msg) {
		 Log.d(LOG_TAG, msg);
	 }

	/* package */ void requestUpdateTouchUi() {
		if (DBG) log("requestUpdateTouchUi()...");
	}

    /**
     * Do some delayed cleanup after a Phone call gets disconnected.
     *
     * This method gets called a couple of seconds after any DISCONNECT
     * event from the Phone; it's triggered by the
     * DELAYED_CLEANUP_AFTER_DISCONNECT message we send in onDisconnect().
     *
     * If the Phone is totally idle right now, that means we've already
     * shown the "call ended" state for a couple of seconds, and it's now
     * time to endInCallScreenSession this activity.
     *
     * If the Phone is *not* idle right now, that probably means that one
     * call ended but the other line is still in use.  In that case, we
     * *don't* exit the in-call screen, but we at least turn off the
     * backlight (which we turned on in onDisconnect().)
     */
    private void delayedCleanupAfterDisconnect() {
        if (VDBG) log("delayedCleanupAfterDisconnect()...  Phone state = " + mPhone.getState());

        // Clean up any connections in the DISCONNECTED state.
        //
        // [Background: Even after a connection gets disconnected, its
        // Connection object still stays around, in the special
        // DISCONNECTED state.  This is necessary because we we need the
        // caller-id information from that Connection to properly draw the
        // "Call ended" state of the CallCard.
        //   But at this point we truly don't need that connection any
        // more, so tell the Phone that it's now OK to to clean up any
        // connections still in that state.]
        mPhone.clearDisconnected();

        {            // And (finally!) exit from the in-call screen
            // (but not if we're already in the process of pausing...)
            if (mIsForegroundActivity) {
                if (DBG) log("- delayedCleanupAfterDisconnect: finishing InCallScreen...");

                // If this is a call that was initiated by the user, and
                // we're *not* in emergency mode, finish the call by
                // taking the user to the Call Log.
                // Otherwise we simply call endInCallScreenSession, which will take us
                // back to wherever we came from.
                if (mShowCallLogAfterDisconnect) {
                    if (VDBG) log("- Show Call Log after disconnect...");
                    final Intent intent = PhoneApp.createCallLogIntent();
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    // Even in this case we still call endInCallScreenSession (below),
                    // to make sure we don't stay in the activity history.
                }
            }
            endInCallScreenSession();
        } 
    }
	
	public void onClick(View view) {
		int id = view.getId();
		switch (id) {
			case R.id.answer:
				if (mPhone.getState() == Phone.State.RINGING) {
					if (mbInitialCamera && (!mbAnswered)) {
						internalAnswerCall();
						mInComingCallPanel.setVisibility(View.INVISIBLE);
						mbAnswered = true;
					}
				}
				return;
			case R.id.hangup:	
				if (mDialer.isOpened()){
					if (VDBG) log("onClick: mDialer is opened, don't hangup...");
					return;
				}
				if (mPhone.getState() == Phone.State.OFFHOOK)
					internalHangup();
				return;
			case R.id.decline:
				if (mPhone.getState() == Phone.State.RINGING)
					internalHangupRingingCall();
				return;
			case R.id.dialer:
				if (mDialer.isOpened()){
					if (VDBG) log("onClick: mDialer is opened, don't response dialer button...");
					return;
				}
				onShowHideDialpad();
				return;			
			case R.id.increase_volume:
				if (VDBG) log("onClick: increase_volume...");
				handleIncallVolumeButton(id);
				break;			
			case R.id.decline_volume:
				if (VDBG) log("onClick: decline_volume...");
				handleIncallVolumeButton(id);
				break;
		}
	}
	
	private void handleIncallVolumeButton(int Id) {
	if (VDBG) log("handleIncallVolumeButton()...Id ="+Id);
	if(Id == R.id.increase_volume){
		if (VDBG) log("KeyEvent.KEYCODE_VOLUME_UP");
	                         new Thread(new Runnable() {
	                               public void run() {
	                                    /* Simulate a KeyStroke to the menu-button. */
	                                    simulateKeystroke(KeyEvent.KEYCODE_VOLUME_UP);
	                               }
	                          }).start(); /* And start the Thread. */

	}
	else if(Id == R.id.decline_volume){
		if (VDBG) log("KeyEvent.KEYCODE_VOLUME_DOWN");
	                         new Thread(new Runnable() {
	                               public void run() {
	                                    /* Simulate a KeyStroke to the menu-button. */
	                                    simulateKeystroke(KeyEvent.KEYCODE_VOLUME_DOWN);
	                               }
	                          }).start(); /* And start the Thread. */
	}

	}
	private void simulateKeystroke(int KeyCode) {
		if (mHasFocus && mIsForegroundActivity) {
	      		doInjectKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyCode));
		      	doInjectKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyCode));
		}
	 }
	
	/** This function actually handles the KeyStroke-Injection. */
	private void doInjectKeyEvent(KeyEvent kEvent) {
		 try {
			  /* Inject the KeyEvent to the Window-Manager. */
	   mWindowManager.injectKeyEvent(kEvent, true);
		 } catch (RemoteException e) {
			  e.printStackTrace();
		 } catch (SecurityException e) {
	               e.printStackTrace();
	          }
	}

	    /**
     * Updates the background of the InCallScreen to indicate the state of
     * the current call(s).
     */
	private void updateInCallBackground() {
	}

	private void updateInCallPanel() {
		Phone.State state = mPhone.getState();
		boolean bShowVolumeButton = false;
		
		if (state == Phone.State.OFFHOOK){
			mInCallPanel.setVisibility(View.VISIBLE);
			mInComingCallPanel.setVisibility(View.GONE);
			Call call = mPhone.getForegroundCall();
			if (Call.State.ACTIVE == call.getState()) {
				bShowVolumeButton = true;
			} else {
				bShowVolumeButton = false;
			}
		} else if (state == Phone.State.RINGING) {
			mInCallPanel.setVisibility(View.GONE);
			mInComingCallPanel.setVisibility(View.VISIBLE);
			mAnswerBtn.setEnabled(true);
			bShowVolumeButton = false;
		} else {
			mInCallPanel.setVisibility(View.GONE);
			mInComingCallPanel.setVisibility(View.GONE);
			bShowVolumeButton = false;
		}

		if (mAllowVolumeButtion) {
			if (bShowVolumeButton) {
				mButtonIncrease.setVisibility(View.VISIBLE);
				mButtonDecline.setVisibility(View.VISIBLE);
			} else {
				mButtonIncrease.setVisibility(View.GONE);
				mButtonDecline.setVisibility(View.GONE);
			}
		}
	}

	private String getSubstitutePic(boolean bLocal){
		String picFn = null;
		if (bLocal) {
			picFn = "/data/data/com.android.phone/files/" + VideoPhoneSetting.FAKE_LOCAL_IMAGE;
		}
		else {
			picFn = "/data/data/com.android.phone/files/" + VideoPhoneSetting.FAKE_REMOTE_IMAGE;
		}
		
		File picFile = new File(picFn);

		if (!picFile.exists()){       
            Log.e(LOG_TAG, "getSubstitutePic, file: " + picFn + " does not exist");     
    		if (bLocal) {
                VideoPhoneSetting.createThumb(getWindow().getContext(), null, true, VideoPhoneSetting.FAKE_LOCAL_IMAGE);
    		} else {
                VideoPhoneSetting.createThumb(getWindow().getContext(), null, true, VideoPhoneSetting.FAKE_REMOTE_IMAGE);
            }
		}
		Log.d(LOG_TAG, "getSubstitutePic, bLocal: " + bLocal + ", picFn: " + picFn);
		
		return picFn;
	}

	private boolean isSurffixJPG(String str){
		int len = str.length();
		if (len<=4)
			return false;
		return (str.regionMatches(true, len - 4, ".jpg", 0, 4));
	}
	
	/**
	 * Updates the view references according to the "view size configure" and "view type configure"
	*/		
	private void updateViewConfig() {
		Log.d(LOG_TAG, "updateViewConfig mScreenLayoutConfig=" + mScreenLayoutConfig + " mLocalViewType=" + mLocalViewType + " mRemoteViewType=" + mRemoteViewType);

		mLocalVideoPhoneView = null;
		mRemoteVideoPhoneView = null;
		
		mLargeVideoPhoneView.setBackgroundDrawable(null);
		mSmallVideoPhoneView.setBackgroundDrawable(null);
		if (mScreenLayoutConfig == ScreenLayoutConfig.LOCAL_LARGE_REMOTE_SMALL) {
			mLocalVideoPhoneView = mLargeVideoPhoneView;
			mRemoteVideoPhoneView = mSmallVideoPhoneView;
		} else if (mScreenLayoutConfig == ScreenLayoutConfig.LOCAL_SMALL_REMOTE_LARGE) {
			mLocalVideoPhoneView = mSmallVideoPhoneView;
			mRemoteVideoPhoneView = mLargeVideoPhoneView;
		} else {
			Log.e(LOG_TAG, "ViewConfig mScreenLayoutConfig is illegal.");
			return;
		}
		
		boolean bShowRemoteSurface = ((mp != null) && ((mp.getCodecState() == MediaPhone.CodecState.CODEC_START)
			|| (mp.getCodecState() == MediaPhone.CodecState.CODEC_CLOSE)));

		if (bShowRemoteSurface) {
			if (mRemoteViewType == ViewType.LIVE) {
			} else if (mRemoteViewType == ViewType.PICTURE) {
				if (mbShowSubstitutePic){
					Drawable pic = Drawable.createFromPath(getSubstitutePic(false));
					if (pic != null){
						mRemoteVideoPhoneView.setBackgroundDrawable(pic);
					}
				}
			} else {
				Log.e(LOG_TAG, "ViewConfig is illegal.");
			}
		} else {
			Drawable pic = Drawable.createFromPath(VideoPhoneSetting.DEF_SUBSTITUTE_IMAGE_PATH);
			if (pic != null){
				mRemoteVideoPhoneView.setBackgroundDrawable(pic);
			}
		}
		
	 }

   /**
     * Updates the main block of caller info on the CallCard
     * (ie. the stuff in the primaryCallInfo block) based on the specified Call.
     */
    private void displayMainCallStatus(Phone phone, Call call) {
        if (DBG) log("displayMainCallStatus(phone " + phone
                     + ", call " + call + ")...");

        if (call == null) {
            // There's no call to display, presumably because the phone is idle.
            //mCallInfo.setVisibility(View.GONE);
            return;
        }
        mCallInfo.setVisibility(View.VISIBLE);

        Call.State state = call.getState();
        if (DBG) log("displayMainCallStatus  - state: " + state + ", oldstate: " + mOldCallState);
		if (DBG) log("displayMainCallStatus  - mWaitCC:" + mWaitCC + ", mWaitCD:" + mWaitCD);
		if (((mOldCallState == Call.State.DIALING)||(mOldCallState == Call.State.ALERTING))
			&& (state == Call.State.ACTIVE)){
			if (!mWaitCC){
       				 if (DBG) log("displayMainCallStatus  - mWaitCC: " + mWaitCC + ", send DELAYED_END_WAITCC");
			 	 mWaitCC = true;
	             mHandler.removeMessages(DELAYED_END_WAITCC);
	             mHandler.sendEmptyMessageDelayed(DELAYED_END_WAITCC,
	                                             WAIT_CC_DELAY);
			}
		}
		mOldCallState = state;

		/*if ((mWaitCC | mWaitCD) && (state == Call.State.ACTIVE)){
        	//if (DBG) log("displayMainCallStatus don't action, mWaitCC:" + mWaitCC + ", mWaitCD:" + mWaitCD);
			return;
		}*/

        switch (state) {
            case ACTIVE:
		mbIncoming = false;
                mbEverConnected = true;
                mCallTime.setActiveCallMode(call);/*
                mCallTime.reset();
                mCallTime.periodicUpdateTimer();
		mCCTime = SystemClock.elapsedRealtime();
		if (mBeginTime.getText().length() <= 0) {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			mBeginTime.setText(getString(R.string.video_label_begintime) + sdf.format(new Date()));
			log("ACTIVE, mBeginTime: " + mBeginTime.getText());
		}*/
		break;
				
            case DISCONNECTING:
                mCallTime.cancelTimer();
                break;
				
            case DISCONNECTED:
                // Stop getting timer ticks from this call
                mCallTime.cancelTimer();
                break;

            default:
                Log.w(LOG_TAG, "displayMainCallStatus: unexpected call state: " + state);
                break;
        }

        updateCardTitleWidgets(phone, call);

        if (PhoneUtils.isConferenceCall(call)) {
            // Update onscreen info for a conference call.
//            updateDisplayForConference();
        } else {
            // Update onscreen info for a regular call (which presumably
            // has only one connection.)
            Connection conn = null;
            int phoneType = phone.getPhoneType();
            conn = call.getEarliestConnection();

            if (conn == null) {
               // if (DBG) log("displayMainCallStatus: connection is null, using default values.");
                // if the connection is null, we run through the behaviour
                // we had in the past, which breaks down into trivial steps
                // with the current implementation of getCallerInfo and
                // updateDisplayForPerson.
                CallerInfo info = PhoneUtils.getCallerInfo(mPhone.getContext(), null /* conn */);
                updateDisplayForPerson(info, Connection.PRESENTATION_ALLOWED, false, call);
            } else {
                //if (DBG) log("  - CONN: " + conn + ", state = " + conn.getState());
                int presentation = conn.getNumberPresentation();

                // make sure that we only make a new query when the current
                // callerinfo differs from what we've been requested to display.
                boolean runQuery = true;
                Object o = conn.getUserData();
                if (o instanceof PhoneUtils.CallerInfoToken) {
                    runQuery = mPhotoTracker.isDifferentImageRequest(
                            ((PhoneUtils.CallerInfoToken) o).currentInfo);
                } else {
                    runQuery = mPhotoTracker.isDifferentImageRequest(conn);
                }

                if (runQuery) {
                    if (DBG) log("- displayMainCallStatus: starting CallerInfo query...");
                    PhoneUtils.CallerInfoToken info =
                            PhoneUtils.startGetCallerInfo(mPhone.getContext(), conn, this, call);
                    updateDisplayForPerson(info.currentInfo, presentation, !info.isFinal, call);
                } else {
                    // No need to fire off a new query.  We do still need
                    // to update the display, though (since we might have
                    // previously been in the "conference call" state.)
                    if (DBG) log("- displayMainCallStatus: using data we already have...");
                    if (o instanceof CallerInfo) {
                        CallerInfo ci = (CallerInfo) o;
                        // Update CNAP information if Phone state change occurred
                        ci.cnapName = conn.getCnapName();
                        ci.numberPresentation = conn.getNumberPresentation();
                        ci.namePresentation = conn.getCnapNamePresentation();
                        if (DBG) log("- displayMainCallStatus: CNAP data from Connection: "
                                + "CNAP name=" + ci.cnapName
                                + ", Number/Name Presentation=" + ci.numberPresentation);
                        if (DBG) log("   ==> Got CallerInfo; updating display: ci = " + ci);
                        updateDisplayForPerson(ci, presentation, false, call);
                    } else if (o instanceof PhoneUtils.CallerInfoToken){
                        CallerInfo ci = ((PhoneUtils.CallerInfoToken) o).currentInfo;
                        if (DBG) log("- displayMainCallStatus: CNAP data from Connection: "
                                + "CNAP name=" + ci.cnapName
                                + ", Number/Name Presentation=" + ci.numberPresentation);
                        if (DBG) log("   ==> Got CallerInfoToken; updating display: ci = " + ci);
                        updateDisplayForPerson(ci, presentation, true, call);
                    } else {
                        Log.w(LOG_TAG, "displayMainCallStatus: runQuery was false, "
                              + "but we didn't have a cached CallerInfo object!  o = " + o);
                        // TODO: any easy way to recover here (given that
                        // the CallCard is probably displaying stale info
                        // right now?)  Maybe force the CallCard into the
                        // "Unknown" state?
                    }
                }
            }
        }

        // If we don't have a hint to display, just don't touch
        // mPhoneNumber and mLabel. (Their text / color / visibility have
        // already been set correctly, by either updateDisplayForPerson()
        // or updateDisplayForConference().)
    }

    /**
     * Updates the "card title" (and also elapsed time widget) based on
     * the current state of the call.
     */
    // TODO: it's confusing for updateCardTitleWidgets() and
    // getTitleForCallCard() to be separate methods, since they both
    // just list out the exact same "phone state" cases.
    // Let's merge the getTitleForCallCard() logic into here.
    private void updateCardTitleWidgets(Phone phone, Call call) {
       // if (DBG) log("updateCardTitleWidgets(call " + call + ")...");
        Call.State state = call.getState();

        // TODO: Still need clearer spec on exactly how title *and* status get
        // set in all states.  (Then, given that info, refactor the code
        // here to be more clear about exactly which widgets on the card
        // need to be set.)

        String cardTitle;
        int phoneType = mApplication.phone.getPhoneType();
        cardTitle = getTitleForCallCard(call);
		
       // if (DBG) log("updateCardTitleWidgets: " + cardTitle);

        // Update the title and elapsed time widgets based on the current call state.
        switch (state) {
            case ACTIVE:
            case DISCONNECTING:
                final boolean bluetoothActive = mApplication.showBluetoothIndication();
                int ongoingCallIcon = bluetoothActive ? R.drawable.ic_incall_ongoing_bluetooth
                        : R.drawable.ic_incall_ongoing;
                int connectedTextColor = bluetoothActive
                        ? mTextColorConnectedBluetooth : mTextColorConnected;

                    // While in the DISCONNECTING state we display a
                    // "Hanging up" message in order to make the UI feel more
                    // responsive.  (In GSM it's normal to see a delay of a
                    // couple of seconds while negotiating the disconnect with
                    // the network, so the "Hanging up" state at least lets
                    // the user know that we're doing something.)
                    // TODO: consider displaying the "Hanging up" state for
                    // CDMA also if the latency there ever gets high enough.
                    if (state == Call.State.DISCONNECTING) {
                        // Display the brief "Hanging up" indication.
                        setUpperTitle(cardTitle, mTextColorDefaultPrimary, state);
                    } else {  // state == Call.State.ACTIVE
                        // Normal "ongoing call" state; don't use any "title" at all.
                        //clearUpperTitle();
                		setUpperTitle(cardTitle, mTextColorConnected, state);
                    }

                // Use the elapsed time widget to show the current call duration.
                mElapsedTime.setTextColor(connectedTextColor);
                // Also see onTickForCallTimeElapsed(), which updates this
                // widget once per second while the call is active.
                break;

            case DISCONNECTED:
                // Display "Call ended" (or possibly some error indication;
                // see getCallFailedString()) in the upper title, in red.

                // TODO: display a "call ended" icon somewhere, like the old
                // R.drawable.ic_incall_end?

                setUpperTitle(cardTitle, mTextColorEnded, state);

                // In the "Call ended" state, leave the mElapsedTime widget
                // visible, but don't touch it (so  we continue to see the elapsed time of
                // the call that just ended.)
                mElapsedTime.setVisibility(View.VISIBLE);
                mElapsedTime.setTextColor(mTextColorEnded);
                break;

            case HOLDING:
                // For a single call on hold, display the title "On hold" in
                // orange.
                // (But since the upper title overlaps the label of the
                // Hold/Unhold button, we actually use the elapsedTime widget
                // to display the title in this case.)

                // TODO: display an "On hold" icon somewhere, like the old
                // R.drawable.ic_incall_onhold?

                clearUpperTitle();
                mElapsedTime.setText(cardTitle);

                // While on hold, the elapsed time widget displays an
                // "on hold" indication rather than an amount of time.
                mElapsedTime.setVisibility(View.VISIBLE);
                mElapsedTime.setTextColor(mTextColorOnHold);
                break;

            default:
                // All other states (DIALING, INCOMING, etc.) use the "upper title":
                setUpperTitle(cardTitle, mTextColorDefaultPrimary, state);

                // ...and we don't show the elapsed time.
                mElapsedTime.setVisibility(View.INVISIBLE);
                break;
        }
    }

    /**
     * Updates mElapsedTime based on the specified number of seconds.
     * A timeElapsed value of zero means to not show an elapsed time at all.
     */
    private void updateElapsedTimeWidget(long timeElapsed) {
        // if (DBG) log("updateElapsedTimeWidget: " + timeElapsed);
        if (DBG) log("updateElapsedTimeWidget: timeElapsed: " + timeElapsed + ", mMMRingDuring: " + mMMRingDuring);
        if (mWaitCD || (timeElapsed == 0)) {
            mElapsedTime.setText("");
        } else {
            mElapsedTime.setText(getString(R.string.video_label_time) + DateUtils.formatElapsedTime(timeElapsed - mMMRingDuring/1000));
        }
    }

    /**
     * Returns the "card title" displayed at the top of a foreground
     * ("active") CallCard to indicate the current state of this call, like
     * "Dialing" or "In call" or "On hold".  A null return value means that
     * there's no title string for this state.
     */
    private String getTitleForCallCard(Call call) {
        String retVal = null;
        Call.State state = call.getState();
        Context context = mPhone.getContext();
        int resId;

        if (DBG) log("- getTitleForCallCard(Call " + call + ")...");

        switch (state) {
            case IDLE:
                break;

            case ACTIVE:
                // Title is "Call in progress".  (Note this appears in the
                // "lower title" area of the CallCard.)
                int phoneType = mApplication.phone.getPhoneType();
                retVal = context.getString(R.string.card_title_in_progress);
                break;

            case HOLDING:
                retVal = context.getString(R.string.card_title_on_hold);
                // TODO: if this is a conference call on hold,
                // maybe have a special title here too?
                break;

            case DIALING:
                retVal = context.getString(R.string.card_title_dialing_video);
                break;

            case ALERTING:
                retVal = context.getString(R.string.card_title_alerting_video);
                break;

            case INCOMING:
            case WAITING:
                retVal = context.getString(R.string.card_title_incoming_video_call);
                break;

            case DISCONNECTING:
                retVal = context.getString(R.string.card_title_hanging_up);
                break;

            case DISCONNECTED:
                retVal = getCallFailedString(call);
                break;
        }

        if (DBG) log("  ==> result: " + retVal);
        return retVal;
    }

    /**
     * Updates the name / photo / number / label fields on the CallCard
     * based on the specified CallerInfo.
     *
     * If the current call is a conference call, use
     * updateDisplayForConference() instead.
     */
    private void updateDisplayForPerson(CallerInfo info,
                                        int presentation,
                                        boolean isTemporary,
                                        Call call) {
        //if (DBG) log("updateDisplayForPerson(" + info + ")\npresentation:" +
                     //presentation + " isTemporary:" + isTemporary);

        // inform the state machine that we are displaying a photo.
        mPhotoTracker.setPhotoRequest(info);
        mPhotoTracker.setPhotoState(ContactsAsyncHelper.ImageTracker.DISPLAY_IMAGE);

        String name;
        String displayNumber = null;
        String label = null;
        Uri personUri = null;
        String socialStatusText = null;
        Drawable socialStatusBadge = null;

        if (info != null) {
            // It appears that there is a small change in behaviour with the
            // PhoneUtils' startGetCallerInfo whereby if we query with an
            // empty number, we will get a valid CallerInfo object, but with
            // fields that are all null, and the isTemporary boolean input
            // parameter as true.

            // In the past, we would see a NULL callerinfo object, but this
            // ends up causing null pointer exceptions elsewhere down the
            // line in other cases, so we need to make this fix instead. It
            // appears that this was the ONLY call to PhoneUtils
            // .getCallerInfo() that relied on a NULL CallerInfo to indicate
            // an unknown contact.

            if (TextUtils.isEmpty(info.name)) {
                if (TextUtils.isEmpty(info.phoneNumber)) {
                    name =  getPresentationString(presentation);
                } else if (presentation != Connection.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a phone #
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    name = getPresentationString(presentation);
                } else if (!TextUtils.isEmpty(info.cnapName)) {
                    name = info.cnapName;
                    info.name = info.cnapName;
                    displayNumber = info.phoneNumber;
                } else {
                    name = info.phoneNumber;
                }
            } else {
                if (presentation != Connection.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a name
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    name = getPresentationString(presentation);
                } else {
                    name = info.name;
                    displayNumber = info.phoneNumber;
                    label = info.phoneLabel;
                }
            }
            personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, info.person_id);
        } else {
            name =  getPresentationString(presentation);
        }

        if (call.isGeneric()) {
            mName.setText(R.string.card_title_in_call);
        } else {
            mName.setText(name);
        }
        mName.setVisibility(View.VISIBLE);
/*
        // Update mPhoto
        // if the temporary flag is set, we know we'll be getting another call after
        // the CallerInfo has been correctly updated.  So, we can skip the image
        // loading until then.

        // If the photoResource is filled in for the CallerInfo, (like with the
        // Emergency Number case), then we can just set the photo image without
        // requesting for an image load. Please refer to CallerInfoAsyncQuery.java
        // for cases where CallerInfo.photoResource may be set.  We can also avoid
        // the image load step if the image data is cached.
        if (isTemporary && (info == null || !info.isCachedPhotoCurrent)) {
            mPhoto.setVisibility(View.INVISIBLE);
        } else if (info != null && info.photoResource != 0){
            showImage(mPhoto, info.photoResource);
        } else if (!showCachedImage(mPhoto, info)) {
            // Load the image with a callback to update the image state.
            // Use the default unknown picture while the query is running.
            ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(
                info, 0, this, call, getContext(), mPhoto, personUri, R.drawable.picture_unknown);
        }
        // And no matter what, on all devices, we never see the "manage
        // conference" button in this state.
        mManageConferencePhotoButton.setVisibility(View.INVISIBLE);*/

        if (displayNumber != null && !call.isGeneric()) {
            mPhoneNumber.setText(displayNumber);
            mPhoneNumber.setTextColor(mTextColorDefaultSecondary);
            mPhoneNumber.setVisibility(View.VISIBLE);
        } else {
            mPhoneNumber.setVisibility(View.GONE);
        }

        if (label != null && !call.isGeneric()) {
            //mLabel.setText(label);
            //mLabel.setVisibility(View.VISIBLE);
        } else {
            //mLabel.setVisibility(View.GONE);
        }

        // "Social status": currently unused.
        // Note socialStatus is *only* visible while an incoming
        // call is ringing, never in any other call state.
        /*if ((socialStatusText != null) && call.isRinging() && !call.isGeneric()) {
            mSocialStatus.setVisibility(View.VISIBLE);
            mSocialStatus.setText(socialStatusText);
            mSocialStatus.setCompoundDrawablesWithIntrinsicBounds(
                    socialStatusBadge, null, null, null);
            mSocialStatus.setCompoundDrawablePadding((int) (mDensity * 6));
        } else {
            mSocialStatus.setVisibility(View.GONE);
        }*/
    }

    /**
     * Implemented for CallerInfoAsyncQuery.OnQueryCompleteListener interface.
     * refreshes the CallCard data when it called.
     */
    public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
        if (DBG) log("onQueryComplete: token " + token + ", cookie " + cookie + ", ci " + ci);

        if (cookie instanceof Call) {
            // grab the call object and update the display for an individual call,
            // as well as the successive call to update image via call state.
            // If the object is a textview instead, we update it as we need to.
            if (DBG) log("callerinfo query complete, updating ui from displayMainCallStatus()");
            Call call = (Call) cookie;
            Connection conn = null;
            conn = call.getEarliestConnection();
            PhoneUtils.CallerInfoToken cit =
                   PhoneUtils.startGetCallerInfo(mPhone.getContext(), conn, this, null);

            int presentation = Connection.PRESENTATION_ALLOWED;
            if (conn != null) presentation = conn.getNumberPresentation();
            if (DBG) log("- onQueryComplete: presentation=" + presentation
                    + ", contactExists=" + ci.contactExists);

            // Depending on whether there was a contact match or not, we want to pass in different
            // CallerInfo (for CNAP). Therefore if ci.contactExists then use the ci passed in.
            // Otherwise, regenerate the CIT from the Connection and use the CallerInfo from there.
            if (ci.contactExists) {
                updateDisplayForPerson(ci, Connection.PRESENTATION_ALLOWED, false, call);
            } else {
                updateDisplayForPerson(cit.currentInfo, presentation, false, call);
            }

        } else if (cookie instanceof TextView){
            if (DBG) log("callerinfo query complete, updating ui from ongoing or onhold");
            ((TextView) cookie).setText(PhoneUtils.getCompactNameFromCallerInfo(ci, mPhone.getContext()));
        }
    }
	
    public void onTickForCallTimeElapsed(long timeElapsed) {
        // While a call is in progress, update the elapsed time shown
        // onscreen.
        log("onTickForCallTimeElapsed(), timeElapsed: " + timeElapsed);
        updateElapsedTimeWidget(timeElapsed);
    }

    /**
     * Sets the CallCard "upper title".  Also, depending on the passed-in
     * Call state, possibly display an icon along with the title.
     */
    private void setUpperTitle(String title, int color, Call.State state) {
        mUpperTitle.setText(title);
        mUpperTitle.setTextColor(color);

        int bluetoothIconId = 0;
        if (!TextUtils.isEmpty(title)
                && ((state == Call.State.INCOMING) || (state == Call.State.WAITING))
                && mApplication.showBluetoothIndication()) {
            // Display the special bluetooth icon also, if this is an incoming
            // call and the audio will be routed to bluetooth.
            bluetoothIconId = R.drawable.ic_incoming_call_bluetooth;
        }

        mUpperTitle.setCompoundDrawablesWithIntrinsicBounds(bluetoothIconId, 0, 0, 0);
        if (bluetoothIconId != 0) mUpperTitle.setCompoundDrawablePadding((int) (mDensity * 5));
    }

    /**
     * Clears the CallCard "upper title", for states (like a normal
     * ongoing call) where we don't use any "title" at all.
     */
    private void clearUpperTitle() {
        setUpperTitle("", 0, Call.State.IDLE);  // Use dummy values for "color" and "state"
    }

    private String getCallFailedString(Call call) {
        Connection c = call.getEarliestConnection();
        int resID;

        if (c == null) {
            if (DBG) log("getCallFailedString: connection is null, using default values.");
            // if this connection is null, just assume that the
            // default case occurs.
            resID = R.string.card_title_call_ended;
        } else {

            Connection.DisconnectCause cause = c.getDisconnectCause();

            // TODO: The card *title* should probably be "Call ended" in all
            // cases, but if the DisconnectCause was an error condition we should
            // probably also display the specific failure reason somewhere...

            switch (cause) {
                case BUSY:
                    resID = R.string.callFailed_userBusy;
                    break;

                case CONGESTION:
                    resID = R.string.callFailed_congestion;
                    break;

                case LOST_SIGNAL:
                case CDMA_DROP:
                    resID = R.string.callFailed_noSignal;
                    break;

                case LIMIT_EXCEEDED:
                    resID = R.string.callFailed_limitExceeded;
                    break;

                case POWER_OFF:
                    resID = R.string.callFailed_powerOff;
                    break;

                case ICC_ERROR:
                    resID = R.string.callFailed_simError;
                    break;

                case OUT_OF_SERVICE:
                    resID = R.string.callFailed_outOfService;
                    break;

                default:
                    resID = R.string.card_title_call_ended;
                    break;
            }
        }
        return mPhone.getContext().getString(resID);
    }

    private String getPresentationString(int presentation) {
        String name = mPhone.getContext().getString(R.string.unknown);
        if (presentation == Connection.PRESENTATION_RESTRICTED) {
            name = mPhone.getContext().getString(R.string.private_num);
        } else if (presentation == Connection.PRESENTATION_PAYPHONE) {
            name = mPhone.getContext().getString(R.string.payphone);
        }
        return name;
    }


	/**
	 * Brings up UI to handle the various error conditions that
	 * can occur when first initializing the in-call UI.
	 * This is called from onResume() if we encountered
	 * an error while processing our initial Intent.
	 *
	 * @param status one of the InCallInitStatus error codes.
	 * @return true if need reset the InCallInitStatus, false if needn't
	 */
	private boolean handleStartupError(InCallInitStatus status) {
        if (DBG) log("handleStartupError(): status = " + status);

        // NOTE that the regular Phone UI is in an uninitialized state at
        // this point, so we don't ever want the user to see it.
        // That means:
        // - Any cases here that need to go to some other activity should
        //   call startActivity() AND immediately call endInCallScreenSession
        //   on this one.
        // - Any cases here that bring up a Dialog must ensure that the
        //   Dialog handles both OK *and* cancel by calling endInCallScreenSession.
        //   Activity.  (See showGenericErrorDialog() for an example.)

        switch (status) {

            case POWER_OFF:
                // Radio is explictly powered off.

                // TODO: This UI is ultra-simple for 1.0.  It would be nicer
                // to bring up a Dialog instead with the option "turn on radio
                // now".  If selected, we'd turn the radio on, wait for
                // network registration to complete, and then make the call.

                showGenericErrorDialog(R.string.incall_error_power_off, true);
                break;

            case EMERGENCY_ONLY:
                // Only emergency numbers are allowed, but we tried to dial
                // a non-emergency number.
                // (This state is currently unused; see comments above.)
                showGenericErrorDialog(R.string.incall_error_emergency_only, true);
                break;

            case OUT_OF_SERVICE:
                // No network connection.
                showGenericErrorDialog(R.string.incall_error_out_of_service, true);
                break;

            case PHONE_NOT_IN_USE:
                // This error is handled directly in onResume() (by bailing
                // out of the activity.)  We should never see it here.
                Log.w(LOG_TAG,
                      "handleStartupError: unexpected PHONE_NOT_IN_USE status");
                break;

            case NO_PHONE_NUMBER_SUPPLIED:
                // The supplied Intent didn't contain a valid phone number.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                showGenericErrorDialog(R.string.incall_error_no_phone_number_supplied, true);
                break;

            case CALL_FAILED:
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                showGenericErrorDialog(R.string.incall_error_call_failed, true);
                break;

            case NO_IN_3G:
                handleFallBack(DEFAULT_FALLBACK,-1);
                break;
				
            case ALREADY_IN_3GCALL:
		  Toast txtToast = Toast.makeText(this, R.string.incall_error_dialvt_in_3gcall, Toast.LENGTH_LONG);
		  txtToast.show();
                break;
				
            case ALREADY_IN_2GCALL:
                showGenericErrorDialog(R.string.incall_error_dialvt_in_2gcall, true);
		  return false;
                //break;
             case CALL_FAILED_FDN_ONLY:
		      showGenericErrorDialog(R.string.callFailed_fdn_only, true);
		       break;
             case AIRPLANE_MODE_ON:
		      showGenericErrorDialog(R.string.incall_error_aireplane_mode_on, true);
		       break;
			   
            default:
                Log.w(LOG_TAG, "handleStartupError: unexpected status code " + status);
                showGenericErrorDialog(R.string.incall_error_call_failed, true);
                break;
        }
	 return true;
    }

	/**
	 * Utility function to bring up a generic "error" dialog, and then bail
	 * out of the in-call UI when the user hits OK (or the BACK button.)
	 */
	private void showGenericErrorDialog(int resid, boolean isStartupError) {
        CharSequence msg = getResources().getText(resid);
        if (DBG) log("showGenericErrorDialog('" + msg + "')...");
		

        // create the clicklistener and cancel listener as needed.
        DialogInterface.OnClickListener clickListener;
        OnCancelListener cancelListener;
        if (isStartupError) {
            clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    bailOutAfterErrorDialog();
                }};
            cancelListener = new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    bailOutAfterErrorDialog();
                }};
        } else {
            clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    delayedCleanupAfterDisconnect();
                }};
            cancelListener = new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    delayedCleanupAfterDisconnect();
                }};
        }

        // TODO: Consider adding a setTitle() call here (with some generic
        // "failure" title?)
        mGenericErrorDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, clickListener)
                .setOnCancelListener(cancelListener)
                .create();

        // When the dialog is up, completely hide the in-call UI
        // underneath (which is in a partially-constructed state).
        mGenericErrorDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mGenericErrorDialog.show();
    }

    private void bailOutAfterErrorDialog() {
        if (mGenericErrorDialog != null) {
            if (DBG) log("bailOutAfterErrorDialog: DISMISSING mGenericErrorDialog.");
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        if (DBG) log("bailOutAfterErrorDialog(): end InCallScreen session...");
        endInCallScreenSession();
    }

    /**
     * Dismisses (and nulls out) all persistent Dialogs managed
     * by the InCallScreen.  Useful if (a) we're about to bring up
     * a dialog and want to pre-empt any currently visible dialogs,
     * or (b) as a cleanup step when the Activity is going away.
     */
    private void dismissAllDialogs() {
        if (DBG) log("dismissAllDialogs()...");

        // Note it's safe to dismiss() a dialog that's already dismissed.
        // (Even if the AlertDialog object(s) below are still around, it's
        // possible that the actual dialog(s) may have already been
        // dismissed by the user.)
        if (mGenericErrorDialog != null) {
            if (VDBG) log("- DISMISSING mGenericErrorDialog.");
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        if (mWaitPromptDialog != null) {
            if (VDBG) log("- DISMISSING mWaitPromptDialog.");
            mWaitPromptDialog.dismiss();
            mWaitPromptDialog = null;
        }
        if (mWildPromptDialog != null) {
            if (VDBG) log("- DISMISSING mWildPromptDialog.");
            mWildPromptDialog.dismiss();
            mWildPromptDialog = null;
        }
        if (mCallLostDialog != null) {
            if (VDBG) log("- DISMISSING mCallLostDialog.");
            mCallLostDialog.dismiss();
            mCallLostDialog = null;
        }
        if (mPausePromptDialog != null) {
            if (DBG) log("- DISMISSING mPausePromptDialog.");
            mPausePromptDialog.dismiss();
            mPausePromptDialog = null;
        }
        if (mFallBackDialog != null) {
            if (DBG) log("- DISMISSING mFallBackDialog.");
            mFallBackDialog.dismiss();
            mFallBackDialog = null;
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.invideocallscreen_option, menu);
		mOptionMenu = menu;
		
		mCameraSwitch_item = menu.findItem(R.id.video_camera_switch);
		mViewSwitch_item = menu.findItem(R.id.video_view_switch);
		mCamera_item = menu.findItem(R.id.video_camera);
		mSpeaker_item = menu.findItem(R.id.video_speaker);
		mBluetooth_item = menu.findItem(R.id.video_bluetooth);
		mMute_item = menu.findItem(R.id.video_mute);
		mCamera_brightness_item = menu.findItem(R.id.camera_brightness);
		mCamera_contrast_item = menu.findItem(R.id.camera_contrast);
		mStartrecord_item = menu.findItem(R.id.video_record);
		mStoprecord_item = menu.findItem(R.id.video_stop_record);
		
        if (VDBG) log("onPrepareOptionsMenu: MEDIA_MOUNTED: " + (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)));
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
			mbExternalStorageAvail = true;
		}
		updateOptionMenu();
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {		
		updateAudioMenu();
		if (mPhone.getState() == Phone.State.RINGING) {
			if (mOptionMenu != null) {
				mOptionMenu.setGroupVisible(R.id.grp_incall, false);			
			}
		} else if (mPhone.getState() == Phone.State.OFFHOOK) {
			if (mOptionMenu != null) {
				mOptionMenu.setGroupVisible(R.id.grp_incoming, false);
				mOptionMenu.setGroupVisible(R.id.grp_incall, true);	
			}
			boolean isCodecStart = (mp.getCodecState() == MediaPhone.CodecState.CODEC_START);
			if (mStartrecord_item != null) {
				mStartrecord_item.setEnabled(isCodecStart && mbExternalStorageAvail && !mbEnableRecord);
				mStartrecord_item.setVisible(!mbEnableRecord);
			}
			if (mStoprecord_item != null) {
				mStoprecord_item.setEnabled(isCodecStart && mbEnableRecord);				
				mStoprecord_item.setVisible(mbEnableRecord);
			}
		} else {
			if (mOptionMenu != null) {
				mOptionMenu.setGroupVisible(R.id.grp_incoming, false);
			}
		}

		return true;
	}
	private void updateOptionMenu() {

		log("updateOptionMenu");
		if (mPhone.getState() == Phone.State.OFFHOOK) {
			if (mOptionMenu != null) {
				mOptionMenu.setGroupVisible(R.id.grp_incoming, false);
				mOptionMenu.setGroupVisible(R.id.grp_incall, true);	
			}
			boolean isCodecStart = (mp.getCodecState() == MediaPhone.CodecState.CODEC_START);
			if (mCamera_item != null) {
				mCamera_item.setTitle(mbEnableCamera?R.string.video_camera_close:R.string.video_camera_open);
				mCamera_item.setEnabled(isCodecStart);
			}
			if (mViewSwitch_item != null) {
				mViewSwitch_item.setEnabled(isCodecStart);
			}
			if (mCameraSwitch_item != null) {
				mCameraSwitch_item.setEnabled(mbEnableCamera&&isCodecStart);
			}
			if (mCamera_brightness_item != null) {
				mCamera_brightness_item.setEnabled(mbEnableCamera&&isCodecStart);
			}
			if (mCamera_contrast_item != null) {
				mCamera_contrast_item.setEnabled(mbEnableCamera&&isCodecStart);
			}
			if (mStartrecord_item != null) {
				mStartrecord_item.setEnabled(isCodecStart && mbExternalStorageAvail && !mbEnableRecord);
				mStartrecord_item.setVisible(!mbEnableRecord);
			}
			if (mStoprecord_item != null) {
				mStoprecord_item.setEnabled(isCodecStart && mbEnableRecord);				
				mStoprecord_item.setVisible(mbEnableRecord);
			}
			updateAudioMenu();
		} 
	}
	
	private void updateAudioMenu() {
		boolean isCodecStart = (mp.getCodecState() == MediaPhone.CodecState.CODEC_START);
		if (mSpeaker_item != null) {
			mSpeaker_item.setTitle(PhoneUtils.isSpeakerOn(this)?R.string.video_speaker_close:R.string.video_speaker_open);
			mSpeaker_item.setEnabled(isCodecStart);
		}
		if (mMute_item != null) {
			mMute_item.setTitle(PhoneUtils.getMute()?R.string.video_unmute:R.string.video_mute);
			mMute_item.setEnabled(isCodecStart);
		}
		if (mBluetooth_item != null) {
			boolean bBtAvail = isBluetoothAvailable();
			boolean bBtConnected = bBtAvail&&isBluetoothAudioConnected();
			mBluetooth_item.setTitle(bBtConnected?R.string.video_bluetooth_close:R.string.video_bluetooth_open);
			mBluetooth_item.setEnabled(bBtAvail&&isCodecStart);
		}
	}
	/**
	 * Returns true whenever any one of the options from the menu is selected.
	 * Code changes to support dialpad options
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	boolean dismissMenuImmediate = true;
		switch (item.getItemId()) {
			case R.id.video_falldown:
				internalFallBack();
				break;
			case R.id.video_camera:
				onCameraCloseClick();
				break;
			case R.id.video_camera_switch:
				onCameraSwitchClick();
                break;
			case R.id.video_view_switch:
				onViewSwitchClick();
				break;
			case R.id.video_speaker:
                if (VDBG) log("onClick: Speaker...");
                onSpeakerClick();
                dismissMenuImmediate = false;
                break;
			case R.id.video_bluetooth:
                if (VDBG) log("onClick: Bluetooth...");
                onBluetoothClick();
                dismissMenuImmediate = false;
                break;
			case R.id.video_mute:
                if (VDBG) log("onClick: Mute...");
                onMuteClick();
                dismissMenuImmediate = false;
				break;
			case R.id.brightness:
				updateAdjustMenu(R.id.brightness_adjustmenu);
				break;
			case R.id.camera_brightness:
				updateAdjustMenu(R.id.camera_brightness_adjustmenu);
				break;
			case R.id.camera_contrast:
				updateAdjustMenu(R.id.camera_contrast_adjustmenu);
				break;
			case R.id.video_start_record_both:
				enableRecord(true, 0);
				break;
			case R.id.video_start_record_audio:
				enableRecord(true, 1);
				break;
			case R.id.video_stop_record:
				enableRecord(false, 0);
				break;
		}
		return true;
	}


    /* package */ void requestUpdateMuteIndication() {
        if (VDBG) log("requestUpdateMuteIndication()...");
        // No need to look at the current state here; any UI elements that
        // care about the bluetooth state (i.e. the CallCard) get
        // the necessary state directly from PhoneApp.showBluetoothIndication().
        mHandler.removeMessages(REQUEST_UPDATE_MUTE_INDICATION);
        mHandler.sendEmptyMessage(REQUEST_UPDATE_MUTE_INDICATION);
    }

	private void getSysConfig(){
		mSysBrightness = getBrightness();
	}

	private void setSysConfig(){
		setBrightness(mSysBrightness);
	}
	
	private void setMyConfig(){
		setBrightness(mMyBrightness);
	}

	private int getBrightness(){
		int brightness = 0;
        try {
            brightness = Settings.System.getInt(mPhone.getContext().getContentResolver(), 
                Settings.System.SCREEN_BRIGHTNESS);
			if (VDBG) log("getBrightness(), brightness: " + brightness);
        } catch (SettingNotFoundException snfe) {
            brightness = MAXIMUM_BACKLIGHT;
        }		
		return brightness;
	}
	
    private void setBrightness(int brightness) {
	    try {
	        IPowerManager power = IPowerManager.Stub.asInterface(
	                ServiceManager.getService("power"));
	        if (power != null) {
				if (VDBG) log("setBrightness(), brightness" + brightness);
	            power.setBacklightBrightness(brightness);
	        }
	    } catch (RemoteException doe) {
	        
	    }        
    }

	private int getCameraBrightness(){
        /*mParameters = mCamera.getParameters();
        int brightness = Integer.parseInt(mParameters.get("brightness"));*/
	int brightness = mp.getCameraParam("brightness");
		
		if (VDBG) log("getCameraBrightness(), brightness: " + brightness);
		return brightness;
	}
	
    private void setCameraBrightness(int brightness) {
		mp.setCameraParam("brightness", brightness);
    }
	
	private int getCameraContrast(){
        /*mParameters = mCamera.getParameters();
        int contrast = Integer.parseInt(mParameters.get("contrast"));*/
	int contrast = mp.getCameraParam("contrast");
		
		if (VDBG) log("getCameraContrast(), contrast: " + contrast);
		return contrast;
	}
	
    private void setCameraContrast(int contrast) {
		mp.setCameraParam("contrast", contrast);
    }
	
	private void updateAdjustMenu(int id){
		mBrightnessAdjustMenu.setVisibility(View.GONE);
		mCameraBrightnessAdjustMenu.setVisibility(View.GONE);
		mCameraContrastAdjustMenu.setVisibility(View.GONE);

		if (id == R.id.brightness_adjustmenu){
			//mMyBrightness = getBrightness();
	        mBrightnessAdjustMenu.setProgress(mMyBrightness);
			mBrightnessAdjustMenu.setVisibility(View.VISIBLE);
		} else if(id == R.id.camera_brightness_adjustmenu) {
			mMyCameraBrightness = getCameraBrightness();
			mCameraBrightnessAdjustMenu.setProgress(mMyCameraBrightness);
			mCameraBrightnessAdjustMenu.setVisibility(View.VISIBLE);
		} else if(id == R.id.camera_contrast_adjustmenu) {
			mMyCameraContrast = getCameraContrast();
			mCameraContrastAdjustMenu.setProgress(mMyCameraContrast);
			mCameraContrastAdjustMenu.setVisibility(View.VISIBLE);
		}
	}
	
    public void onProgressChanged(AdjustMenuView adjustMenu, float progress, int rank, boolean fromUser)
    {
		if (VDBG) log("onProgressChanged(), progress: " + progress + ", rank: " + rank);
    	if (adjustMenu == mBrightnessAdjustMenu) {
			if (VDBG) log("onProgressChanged(), Brightness");
    		adjustMenu.setIndication(Integer.toString(rank));
			setBrightness((int)progress);
			mMyBrightness = (int)progress;
    	} else if (adjustMenu == mCameraBrightnessAdjustMenu) {
			if (VDBG) log("onProgressChanged(), Camera Brightness");
    		adjustMenu.setIndication(Integer.toString(rank));
			setCameraBrightness((int)progress);
			mMyCameraBrightness = (int)progress;
    	} else if (adjustMenu == mCameraContrastAdjustMenu) {
			if (VDBG) log("onProgressChanged(), Contrast");
    		adjustMenu.setIndication(Integer.toString(rank));
			setCameraContrast((int)progress);
			mMyCameraContrast = (int)progress;
    	}
    }
	
	private void onMuteClick() {
		if (VDBG) log("onMuteClick()...");
		//TS for compile
		boolean newMuteState = !PhoneUtils.getMute();
		PhoneUtils.setMute(newMuteState);
		mp.controlLocalAudio(!newMuteState, null);
		updateAudioMenu();
	}

    private void onSpeakerClick() {
        if (VDBG) log("onSpeakerClick()...");

        // TODO: Turning on the speaker seems to enable the mic
        //   whether or not the "mute" feature is active!
        // Not sure if this is an feature of the telephony API
        //   that I need to handle specially, or just a bug.
        boolean newSpeakerState = !PhoneUtils.isSpeakerOn(this);
        if (newSpeakerState && isBluetoothAvailable() && isBluetoothAudioConnected()) {
            disconnectBluetoothAudio();
        }
        PhoneUtils.turnOnSpeaker(this, newSpeakerState, true);
	 updateAudioMenu();
    }
	
    private void onBluetoothClick() {
        if (VDBG) log("onBluetoothClick()...");

        if (isBluetoothAvailable()) {
            // Toggle the bluetooth audio connection state:
            if (isBluetoothAudioConnected()) {
                disconnectBluetoothAudio();
            } else {
                // Manually turn the speaker phone off, instead of allowing the
                // Bluetooth audio routing handle it.  This ensures that the rest
                // of the speakerphone code is executed, and reciprocates the
                // menuSpeaker code above in onClick().  The onClick() code
                // disconnects the active bluetooth headsets when the
                // speakerphone is turned on.
                if (PhoneUtils.isSpeakerOn(this)) {
                    PhoneUtils.turnOnSpeaker(this, false, true);
                }

                connectBluetoothAudio();
            }
	 	updateAudioMenu();
        } else {
            // Bluetooth isn't available; the "Audio" button shouldn't have
            // been enabled in the first place!
            Log.w(LOG_TAG, "Got onBluetoothClick, but bluetooth is unavailable");
        }
    }
	
	private void onViewSwitchClick() {
		if (VDBG) log("onViewSwitchClick()...");
		if (ENABLE_MEDIAPHONE){
			if (!mp.lockCamera()) {
				return;
			}
		}

		if (mScreenLayoutConfig == ScreenLayoutConfig.LOCAL_LARGE_REMOTE_SMALL){
			mScreenLayoutConfig = ScreenLayoutConfig.LOCAL_SMALL_REMOTE_LARGE;
		} else{
			mScreenLayoutConfig = ScreenLayoutConfig.LOCAL_LARGE_REMOTE_SMALL;
		}
		
		updateViewConfig();

		if (ENABLE_MEDIAPHONE){
			mp.stopDownLink();
		}
		
		if (ENABLE_MEDIAPHONE){
			mp.setLocalDisplay(mLocalVideoPhoneView.getHolder().getSurface());
			mp.setRemoteDisplay(mRemoteVideoPhoneView.getHolder().getSurface());
			mp.startDownLink();
			mp.unlockCamera();
		}
		if (VDBG) log("onViewSwitchClick() end");
	}

	private void onCameraCloseClick() {
		if (VDBG) log("onCameraCloseClick()...");
        if (!checkCameraFlag()){
            if(!setCameraFlag(true)) {
                Log.e(LOG_TAG, "onCameraCloseClick(), setCameraFlag failed");
                return;
            }
        }
		
		if (ENABLE_MEDIAPHONE){
			if ((mCamera == null)||(!mp.lockCamera())) {
				return;
			}
			mp.stopUpLink();
		}

        // forcely reinitialize the camera info, in order to enalbe fake camera
		Camera.CameraInfo info = new Camera.CameraInfo();
        mCamera.getCameraInfo(2, info);

        closeCamera();
		if (mbEnableCamera) {
			mRealCameraType = mCameraType;
			mCameraType = CameraType.FAKE_CAMERA;
			mp.setSubtitutePic(getSubstitutePic(true));			
		} else {
			mCameraType = mRealCameraType;
		}
		
		createCamera();
		
		mbEnableCamera = !mbEnableCamera;
		
		if (ENABLE_MEDIAPHONE){
			mp.setCamera(mCamera);
			mp.startUpLink();
			mp.controlLocalVideo(mbEnableCamera, true, null);
			mp.unlockCamera();
		}
		updateOptionMenu();
	}
	
	private void onCameraSwitchClick() {
		if (VDBG) log("onCameraSwitchClick()...");
		if (ENABLE_MEDIAPHONE){
			if ( (mCamera == null)||(!mp.lockCamera())) {
				return;
			}
		}
		
		if (mCameraType == CameraType.BOTTOM_CAMERA){
			mCameraType = CameraType.FRONT_CAMERA;
		}
		else if (mCameraType == CameraType.FRONT_CAMERA){
			mCameraType = CameraType.BOTTOM_CAMERA;
		}
		else {
			log("onCameraSwitchClick(), mCameraType is illegal: " + mCameraType);
			return;
		}

		if (ENABLE_MEDIAPHONE){
			mp.stopUpLink();
			closeCamera();
		} 
		
		createCamera();
		
		if (ENABLE_MEDIAPHONE){
			mp.setCamera(mCamera);
			mp.startUpLink();
			mp.unlockCamera();
		} 
	}	

	
    private String getRecordFileName(){
		File base = null;
		String root = Environment.getExternalStorageDirectory().getPath();
		base = new File(root + DEFAULT_STORE_SUBDIR);
        if (!base.isDirectory() && !base.mkdir()) {
            Log.e(LOG_TAG, "Recording File aborted - can't create base directory " + base.getPath());
            return null;
        }
        
    	SimpleDateFormat sdf = new SimpleDateFormat("'videocall'-yyyyMMddHHmmss");
    	String fn = sdf.format(new Date());
    	fn = base.getPath() + File.separator + fn + DEFAULT_RECORD_SUFFIX;
        
        StatFs stat = null;
        stat = new StatFs(base.getPath());
        long available_size = stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4);
        if (available_size < MINIMUM_START_FREE_SIZE){
            Log.e(LOG_TAG, "Recording File aborted - not enough free space");
			Toast txtToast = Toast.makeText(getWindow().getContext(), getString(R.string.storage_is_full), Toast.LENGTH_LONG);
			txtToast.show();
            return null;        	
        }

		File outFile = new File(fn);
		try{
			if (outFile.exists()){
				outFile.delete();
			}
			boolean bRet = outFile.createNewFile();
			if (!bRet) {
				Log.e(LOG_TAG, "getRecordFileName, fn: " + fn + ", failed");
				return null;
			}
		} catch (SecurityException e){
			Log.e(LOG_TAG, "getRecordFileName, fn: " + fn + ", " + e);
			return null;
		} catch (IOException e){
			Log.e(LOG_TAG, "getRecordFileName, fn: " + fn + ", " + e);
			return null;
		}
        
        return outFile.getAbsolutePath();        
    }

    private boolean isSpaceEnough(){
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());        
        long available_size = stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4);
        Log.e(LOG_TAG, "free space: " + available_size);
        return (available_size > MINIMUM_FREE_SIZE);
    }

	private void enableRecord(boolean bEnable, int type){
		if (bEnable == mbEnableRecord){
			Log.e(LOG_TAG, "Error, enableRecord(" + bEnable + ", " + type + ") called when mbEnableRecord: " + mbEnableRecord);
			return;
		}
		
		log("enableRecord() bEnable : " + bEnable + ", type: " + type);
		
		if (bEnable) {
			mRecordFile = getRecordFileName();
			if (mRecordFile == null){
				return;
			} else{
				log("enableRecord(), fn: " + mRecordFile);
			}
		}
		
		mbEnableRecord = !mbEnableRecord;
		if (ENABLE_MEDIAPHONE){
			try{
				mp.enableRecord(mbEnableRecord, type, mRecordFile);
			} catch (Exception e) {
				Log.e(LOG_TAG, "enableRecord failed, " + e);
                return;
			}
		}
		
		if (!bEnable) {
			Toast.makeText(this, getString(R.string.prompt_record_finish) + mRecordFile, Toast.LENGTH_LONG).show();
			mRecordFile = null;
		}
		if (mStartrecord_item != null) {
			mStartrecord_item.setVisible(!mbEnableRecord);
		}
		if (mStoprecord_item != null) {
			mStoprecord_item.setVisible(mbEnableRecord);
		}
        
        mHandler.removeMessages(CHECK_FREESPACE);
        if (bEnable) {            
            mHandler.sendEmptyMessageDelayed(CHECK_FREESPACE, 10 * 1000);
        }
	}
	
	private void getVideoPhoneConfig() {
		if (VDBG) log("getVideoPhoneConfig()...");

		ScreenLayoutConfig tempScreenLayout;
		FallBackConfig tempFallBack;
		
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String layout = mPrefs.getString(VideoPhoneSetting.KEY_SCREEN_LAYOUT, getString(R.string.videophone_setting_layout_defaultvalue));
        String fallback = mPrefs.getString(VideoPhoneSetting.KEY_FALLBACK, getString(R.string.videophone_setting_fallback_defaultvalue));
		mbShowSubstitutePic = mPrefs.getBoolean(VideoPhoneSetting.KEY_STATIC_IMAGE, true);
		mLocalSubstitutePic = mPrefs.getString(VideoPhoneSetting.KEY_LOCAL_STATIC_IMAGE_PATH, VideoPhoneSetting.DEF_SUBSTITUTE_IMAGE_PATH);
		mRemoteSubstitutePic = mPrefs.getString(VideoPhoneSetting.KEY_REMOTE_STATIC_IMAGE_PATH, VideoPhoneSetting.DEF_SUBSTITUTE_IMAGE_PATH);
		/*if (VDBG)*/ log("getVideoPhoneConfig() layout : " + layout);
		/*if (VDBG)*/ log("getVideoPhoneConfig() fallback : " + fallback);
		/*if (VDBG)*/ log("getVideoPhoneConfig() subtitute : " + mbShowSubstitutePic + ", local: " + mLocalSubstitutePic + ", remote: " + mRemoteSubstitutePic);

		if (layout.equals("1")){
			tempScreenLayout = ScreenLayoutConfig.LOCAL_SMALL_REMOTE_LARGE;
		} else{
			tempScreenLayout = ScreenLayoutConfig.LOCAL_LARGE_REMOTE_SMALL;
		}
		
		if (fallback.equals("1")){
			tempFallBack = FallBackConfig.FALLBACK_ASK;
		} else if (fallback.equals("2")){
			tempFallBack = FallBackConfig.FALLBACK_ALWAYS;
		} else {
			tempFallBack = FallBackConfig.FALLBACK_NEVER;
		}
		mFallBackConfig = tempFallBack;

		if (tempScreenLayout != mScreenLayoutConfig){	
			if (VDBG) log("getVideoPhoneConfig() layout change: " + layout);
			mScreenLayoutConfig = tempScreenLayout;
			updateViewConfig();			
		}
	}

	private void createCamera() {		
        if (mCamera == null) {
            // If the activity is paused and resumed, camera device has been
            // released and we need to open the camera.
            mCamera = Camera.open(getCamerID());
            {	   	
                Camera.Parameters params = mCamera.getParameters();
                params.setSensorRotation(getSensorRotation());
                params.set("sensororientation", 1);
	            params.set("videodatatype", "1");
                mCamera.setParameters(params);
            }
            Log.d(LOG_TAG, "createCamera(), mCamera: " + mCamera);
            mCamera.unlock();
        }
	}

    private boolean lockCamera(long msec) {
        try {
	        mCamera.lock();
        } catch (Exception e) {
            Log.e(LOG_TAG, "lockCamera() failed");
            if (msec > 0) {
                try {
                    Log.e(LOG_TAG, "lockCamera() wait: " + msec);
                    wait(msec);
                } catch (Exception f) {
                    Log.e(LOG_TAG, "lockCamera() wait failed");
                }
                return lockCamera(0);
            }
            return false;
        }
        return true;
    }
    
    private void closeCamera() {
        Log.v(LOG_TAG, "closeCamera");
	    mHandler.removeMessages(DELAYED_CREATE_CAMERA);
        if (mCamera == null) {
            Log.d(LOG_TAG, "already stopped.");
            return;
        }
        mCamera.lock();
        mCamera.release();
	    mCamera = null;
        mPreviewing = false;
    }

    private void closeCamera(long msec) {
        Log.v(LOG_TAG, "closeCamera, msec: " + msec);
	    mHandler.removeMessages(DELAYED_CREATE_CAMERA);
        if (mCamera == null) {
            Log.d(LOG_TAG, "already stopped.");
            return;
        }
        if (lockCamera(msec)) {
            mCamera.release();
    	    mCamera = null;
            mPreviewing = false;
        }
    }

	private void startCameraPreview() {
        if (mStartPreviewThread != null){
            try {
                mStartPreviewThread.join();		 
            } catch (InterruptedException ex) {
                log("mStartPreviewThread.quit() exception " + ex);
            }
        }
			
		mStartPreviewThread = new Thread(new Runnable() {
			public void run() {
			    Log.d(LOG_TAG, "mStartPreviewThread start. ");
				try {
					mStartPreviewFail = false;
					mCamera = Camera.open(getCamerID());
					synchronized(mCameraLock) {
					   	Camera.Parameters params = mCamera.getParameters();
					   	params.setSensorRotation(getSensorRotation());
						params.set("sensororientation", 1);
	                    params.set("videodatatype", "1");
				        params.setPreviewSize(176, 144);
				        params.setPreviewFrameRate(10);
						mCamera.setParameters(params);
                        Log.d(LOG_TAG, "createCamera(), mCamera: " + mCamera);
						mp.setCamera(mCamera);
						if (mSurfaceReadyCount == 2) {
							mCamera.setPreviewDisplay(mLocalVideoPhoneView.getHolder());
							mCamera.startPreview();
						}
					        mCamera.unlock();
					}
					mbEnableCamera = true;
					mbInitialCamera = true;
					mHandler.removeMessages(INITIALED_CAMERA);
					mHandler.sendEmptyMessage(INITIALED_CAMERA);
				}catch (Exception e) {
					mStartPreviewFail = true;
					Log.e(LOG_TAG, "mStartPreviewFail, " + e);
                    synchronized(mCameraLock) {
					    closeCamera();
                    }
					mHandler.removeMessages(DELAYED_CREATE_CAMERA);
					mHandler.sendEmptyMessageDelayed(DELAYED_CREATE_CAMERA, 500);
				}
				Log.d(LOG_TAG, "mStartPreviewThread end. ");
			}
		});
		mStartPreviewThread.start();
	}

	public void onVideoSizeChanged(MediaPhone mp, int width, int height)
	{
	}

	public boolean onError(MediaPhone mp, int what, Object extra)
	{
		return true;
	}

	public boolean onMediaInfo(MediaPhone mp, int what, Object extra)
	{
		return true;
	}

	public boolean onCallEvent(MediaPhone mp, int what, Object extra)
	{
		log("onCallEvent(), what: " + what);
		switch(what){
			case MediaPhone.MEDIA_CALLEVENT_CAMERAOPEN:
				mHandler.removeMessages(MEDIAPHONE_CAMERA_OPEN);
				mHandler.sendEmptyMessage(MEDIAPHONE_CAMERA_OPEN);
				break;				
			case MediaPhone.MEDIA_CALLEVENT_CAMERACLOSE:
				mHandler.removeMessages(MEDIAPHONE_CAMERA_CLOSE);
				mHandler.sendEmptyMessage(MEDIAPHONE_CAMERA_CLOSE);
				break;
			case MediaPhone.MEDIA_CALLEVENT_STRING:
				mHandler.removeMessages(MEDIAPHONE_STRING);
				Message.obtain(mHandler, MEDIAPHONE_STRING, extra).sendToTarget();
				break;
			case MediaPhone.MEDIA_CALLEVENT_CODEC_START:	
				mHandler.removeMessages(MEDIAPHONE_CODEC_START);
				mHandler.sendEmptyMessage(MEDIAPHONE_CODEC_START);
				break;
			case MediaPhone.MEDIA_CALLEVENT_CODEC_CLOSE:				
				mHandler.removeMessages(MEDIAPHONE_CODEC_CLOSE);
				mHandler.sendEmptyMessage(MEDIAPHONE_CODEC_CLOSE);
				break;
			case MediaPhone.MEDIA_CALLEVENT_CODEC_OPEN:				
				 if (InCallScreenMode.FALL_BACK == mInCallScreenMode) {
				 	setInCallScreenMode(InCallScreenMode.UNDEFINED);
				 }
				 break;
			case MediaPhone.MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER:
				if (mCamera != null) {
                    synchronized(mCameraLock) {
                        if (mCamera != null) {
                            try {
        					mCamera.lock();
        					if (mCamera.previewEnabled()) {
        						log("onCallEvent(), MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER: true");
        						mCamera.unlock();
        						return true;
        					}
        					mCamera.unlock();
                            }catch (Exception e) {
                                Log.e(LOG_TAG, "onCallEvent, MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER, camera exception " + e);
        						return true;
                            }
                        }
                    }
				}
				log("onCallEvent(), MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER: false");
				mHandler.removeMessages(MEDIAPHONE_CAMERA_FAIL);
				mHandler.sendEmptyMessage(MEDIAPHONE_CAMERA_FAIL);
				return false;
            case MediaPhone.MEDIA_CALLEVENT_MEDIA_START:
                if (mbMediaStarted) {
                    break;
                }
                mbMediaStarted = true;
                mCallTime.reset();
                mCallTime.periodicUpdateTimer();
        		mCCTime = SystemClock.elapsedRealtime();
        		if (mBeginTime.getText().length() <= 0) {
        			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        			mBeginTime.setText(getString(R.string.video_label_begintime) + sdf.format(new Date()));
        			log("ACTIVE, mBeginTime: " + mBeginTime.getText());
        		}
    			mHandler.removeMessages(MEDIAPHONE_MEDIA_START);
    			mHandler.sendEmptyMessage(MEDIAPHONE_MEDIA_START);
                break;
/*			case MediaPhone.MEDIA_CALLEVENT_CODEC_OPEN:
                // update timer field
                //if (DBG) log("displayMainCallStatus: start periodicUpdateTimer");
                mCallTime.reset();
                mCallTime.periodicUpdateTimer();

				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    			mBeginTime.setText(sdf.format(new Date()));
				break;
			case MediaPhone.MEDIA_CALLEVENT_CODEC_CLOSE:
                // Stop getting timer ticks from this call
                mCallTime.cancelTimer();

				TextView lable_beginTime = (TextView) findViewById(R.id.label_begintime);
				lable_beginTime.setVisibility(View.VISIBLE);
				mBeginTime.setVisibility(View.VISIBLE);
				break;*/
		}
		return true;
	}
	
	void handleFallBack(String number, int cause){
		log("handleFallBack(), number: " + number + ", cause: " + cause + ", mbIncoming: " + mbIncoming);
		if (mbIncoming || (number == null))
			return;
		if( (!number.equals(getCurrentNumber())) && (!number.equals(DEFAULT_FALLBACK)))
			return;
		
		if (mFallBackConfig == FallBackConfig.FALLBACK_ALWAYS){
			if (cause == -1) {
				showToast(getString(R.string.videophone_notin3g_always_fallback));
			} else {
				showToast(getString(R.string.videophone_always_fallback));
			} 
			intenalFallBack();
		} else if (mFallBackConfig == FallBackConfig.FALLBACK_NEVER){
			if (cause == -1) {
				showToast(getString(R.string.videophone_notin3g_never_fallback));
			} else {
				showToast(getString(R.string.videophone_never_fallback));
			}
			finish();
		} else {
			if (!mIsForegroundActivity) {
				finish();
				return;
			}
			setInCallScreenMode(InCallScreenMode.FALL_BACK);
			showFallBackMenu(cause);
		}
	}
	
	void handleVideoCallFail(String number, int cause){		
		log("handleVideoCallFail(), number: " + number + ", cause: " + cause + ", mbIncoming: " + mbIncoming 
            + ", mbHangupByUser: " + mbHangupByUser);
		if (mbIncoming || (number == null))
			return;
		if ((getCurrentNumber() != null) && (!number.equals(getCurrentNumber())))
			return;
        if (!mbHangupByUser) {
    		// 16&31&255 is normally ended
    		if ((cause != 16) && (cause != 31) && (cause != 255)){
    			Toast txtToast = Toast.makeText(getWindow().getContext(), causeToString(cause), Toast.LENGTH_LONG);
    			txtToast.show();
    		} else {
    		    log("handleVideoCallFail(), mbEverConnected: " + mbEverConnected);
    		    // if hasn't connected,  also notify user
    		    if (!mbEverConnected) {
        			Toast txtToast = Toast.makeText(getWindow().getContext(), causeToString(3), Toast.LENGTH_LONG);
        			txtToast.show();
                }
            }
        }
        // if h324 hasn't completely released last time(it also means connection is active now), need hangup call and notify user
        if (cause == 1000) {
		    PhoneUtils.hangup(mCM);
        } else {
            onDisconnect();
        }
	}

	String causeToString(int cause){
		log("causeToString: " + cause);
		switch(cause){
			case 1000:
				return getString(R.string.videophone_failcause_1000);
			case 1:
			case 22:
			case 28:
				return getString(R.string.videophone_failcause_1);
			case 3:
			case 6:
			case 18:
			case 21:
			case 29:
			case 38:
			case 41:
			case 43:
			case 49:
			case 81:
				return getString(R.string.videophone_failcause_3);
			case 8:
			case 55:
				return getString(R.string.videophone_failcause_8);
			case 17:
				return getString(R.string.videophone_failcause_17);
			case 19:
				return getString(R.string.videophone_failcause_19);
			case 27:
				return getString(R.string.videophone_failcause_27);
			case 34:
			case 42:
			case 44:
				return getString(R.string.videophone_failcause_34);
			case 63:
				return getString(R.string.videophone_failcause_63);
			case 79:
				return getString(R.string.videophone_failcause_79);
			// fall back cause:
			case 47:
				return getString(R.string.videophone_failcause_47);
			case 50:
			case 57:
				return getString(R.string.videophone_failcause_57);
			case 58:
				return getString(R.string.videophone_failcause_58);
			case 88:
				return getString(R.string.videophone_failcause_88);
			case -1:
				return getString(R.string.videophone_failcause_minus_1);
			default:
				return getString(R.string.videophone_failcause_default);
			
		}
	}
	void showFallBackMenu(int cause){
		final CharSequence[] items = getResources().getTextArray(R.array.videophone_fallback_menu);
		mFallBackDialog = new Dialog(this);
		
		mFallBackDialog.setTitle(getString(R.string.videophone_fallback_title));
		mFallBackDialog.setContentView(R.xml.videophone_fallback);

		TextView causeView = (TextView)mFallBackDialog.findViewById(R.id.FallBackCause);
		causeView.setText(causeToString(cause));
		
		mFallBackList = (ListView)mFallBackDialog.findViewById(R.id.FallBackList);
		mFallBackList.setAdapter(new ArrayAdapter<CharSequence>(this,
                android.R.layout.simple_list_item_1, items));       
		mFallBackList.setItemsCanFocus(false);
		mFallBackList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mFallBackList.setOnItemClickListener(this);
		
		mFallBackDialog.setOnKeyListener(new OnKeyListener(){
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            finish();
                            return true;
                        case KeyEvent.KEYCODE_SEARCH:
                            log("KEYCODE_SEARCH");
                            return true;
                    }
                }
                return false;
            }
        });
		mFallBackDialog.show();
		mActiveDlg = mFallBackDialog;
	}

	private void internalRetryVideoCall(){
		this.finish();
		mLastAction = LastAction.ACTION_VIDEOCALL;
	}
	
	private void intenalFallBack(){
		this.finish();
		mLastAction = LastAction.ACTION_VOICECALL;
	}
	
    public void onItemClick(AdapterView parent, View v, int position, long id)
    {
    	if (parent == (AdapterView)mFallBackList){
			final CharSequence[] items = getResources().getTextArray(R.array.videophone_fallback_menu);
			showToast(items[(int) id].toString());
			if (mActiveDlg != null){
				mActiveDlg.dismiss();
				mActiveDlg = null;
			}
			// choose "video call"
			if (0 == position){
				internalRetryVideoCall();
				/*if (okToRetryVideoCall()){
					internalRetryVideoCall();
				} else {
					this.finish();
				}*/
			} else if (1 == position){ // choose "voice call"
				intenalFallBack();
			} else if (2 == position){				
                            	finish();
			}
			setInCallScreenMode(InCallScreenMode.UNDEFINED);
    	}
    }
	
	private void showToast(String str){
		Toast txtToast = Toast.makeText(getWindow().getContext(), str, Toast.LENGTH_SHORT);
		txtToast.show();
	}
	
    public boolean isTouchUiEnabled() {
        return false;
    }
	
	private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        if (VDBG) log("handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.  We do so
        // only if the okToDialDTMFTones() conditions pass.
        if (okToDialDTMFTones()) {
            return mDialer.onDialerKeyDown(event);

            // TODO: If the dialpad isn't currently visible, maybe
            // consider automatically bringing it up right now?
            // (Just to make sure the user sees the digits widget...)
            // But this probably isn't too critical since it's awkward to
            // use the hard keyboard while in-call in the first place,
            // especially now that the in-call UI is portrait-only...
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        if (DBG) log("onBackPressed()...");

        // To consume this BACK press, the code here should just do
        // something and return.  Otherwise, call super.onBackPressed() to
        // get the default implementation (which simply finishes the
        // current activity.)

        if (!mRingingCall.isIdle()) {
            // While an incoming call is ringing, BACK behaves just like
            // ENDCALL: it stops the ringing and rejects the current call.
            // (This is only enabled on some platforms, though.)
            if (getResources().getBoolean(R.bool.allow_back_key_to_reject_incoming_call)) {
                if (DBG) log("BACK key while ringing: reject the call");
                internalHangupRingingCall();

                // Don't consume the key; instead let the BACK event *also*
                // get handled normally by the framework (which presumably
                // will cause us to exit out of this activity.)
                super.onBackPressed();
                return;
            } else {
                // The BACK key is disabled; don't reject the call, but
                // *do* consume the keypress (otherwise we'll exit out of
                // this activity.)
                if (DBG) log("BACK key while ringing: ignored");
                return;
            }
        }

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:

        if (mDialer.isOpened()) {

            mDialer.closeDialer(true);  // do the "closing" animation
            return;
        }

        // Nothing special to do.  Fall back to the default behavior.
        //super.onBackPressed();
        moveTaskToBack(true);
    }
	

	/**
     * Determines when we can dial DTMF tones.
     */
    private boolean okToDialDTMFTones() {
        final boolean hasRingingCall = !mRingingCall.isIdle();
        final Call.State fgCallState = mForegroundCall.getState();

        // We're allowed to send DTMF tones when there's an ACTIVE
        // foreground call, and not when an incoming call is ringing
        // (since DTMF tones are useless in that state), or if the
        // Manage Conference UI is visible (since the tab interferes
        // with the "Back to call" button.)

        // We can also dial while in ALERTING state because there are
        // some connections that never update to an ACTIVE state (no
        // indication from the network).
        boolean canDial =
            (fgCallState == Call.State.ACTIVE || fgCallState == Call.State.ALERTING)
            && !hasRingingCall;

        if (VDBG) log ("[okToDialDTMFTones] foreground state: " + fgCallState +
                ", ringing state: " + hasRingingCall +
                ", result: " + canDial);

        return canDial;
    }

    /**
     * @return true if the in-call DTMF dialpad should be available to the
     *      user, given the current state of the phone and the in-call UI.
     *      (This is used to control the visibility of the dialer's
     *      onscreen handle, if applicable, and the enabledness of the "Show
     *      dialpad" onscreen button or menu item.)
     */
    /* package */ boolean okToShowDialpad() {
        // The dialpad is available only when it's OK to dial DTMF
        // tones given the current state of the current call.
        return okToDialDTMFTones();
    }
	
    /**
     * Overriden to track relevant focus changes.
     *
     * If a key is down and some time later the focus changes, we may
     * NOT recieve the keyup event; logically the keyup event has not
     * occured in this window.  This issue is fixed by treating a focus
     * changed event as an interruption to the keydown, making sure
     * that any code that needs to be run in onKeyUp is ALSO run here.
     *
     * Note, this focus change event happens AFTER the in-call menu is
     * displayed, so mIsMenuDisplayed should always be correct by the
     * time this method is called in the framework, please see:
     * {@link onCreatePanelView}, {@link onOptionsMenuClosed}
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // the dtmf tones should no longer be played
        if (VDBG) log("onWindowFocusChanged(" + hasFocus + ")...");
	mHasFocus = hasFocus;
        if (!hasFocus && mDialer != null) {
            if (VDBG) log("- onWindowFocusChanged: faking onDialerKeyUp()...");
            mDialer.onDialerKeyUp(null);
        }
    }
	
    private void onShowHideDialpad() {
        if (VDBG) log("onShowHideDialpad()...");
        if (mDialer.isOpened()) {
            mDialer.closeDialer(true);  // do the "closing" animation
        } else {
            mDialer.openDialer(true);  // do the "opening" animation
        }
		if (ENABLE_DIALPANEL_HANDLER){
        	mDialer.setHandleVisible(true);
		} else {
			mDialer.setHandleVisible(false);
		}
    }

	/**
     * Updates the visibility of the DTMF dialpad (and its onscreen
     * "handle", if applicable), based on the current state of the phone
     * and/or the current InCallScreenMode.
     */
    private void updateDialpadVisibility() {
    	if (VDBG) log("updateDialpadVisibility()...");
        //
        // (1) The dialpad itself:
        //
        // If an incoming call is ringing, make sure the dialpad is
        // closed.  (We do this to make sure we're not covering up the
        // "incoming call" UI, and especially to make sure that the "touch
        // lock" overlay won't appear.)
        if (mPhone.getState() == Phone.State.RINGING) {
            mDialer.closeDialer(false);  // don't do the "closing" animation

            // Also, clear out the "history" of DTMF digits you may have typed
            // into the previous call (so you don't see the previous call's
            // digits if you answer this call and then bring up the dialpad.)
            //
            // TODO: it would be more precise to do this when you *answer* the
            // incoming call, rather than as soon as it starts ringing, but
            // the InCallScreen doesn't keep enough state right now to notice
            // that specific transition in onPhoneStateChanged().
            mDialer.clearDigits();
        }

        //
        // (2) The onscreen "handle":
        //
        // The handle is visible only if it's OK to actually open the
        // dialpad.  (Note this is meaningful only on platforms that use a
        // SlidingDrawer as a container for the dialpad.)
        if (ENABLE_DIALPANEL_HANDLER){
	        mDialer.setHandleVisible(okToShowDialpad());
        } else {
	        mDialer.setHandleVisible(false);
        }

        //
        // (3) The main in-call panel (containing the CallCard):
        //
        // On some platforms(*) we need to hide the CallCard (which is a
        // child of mInCallPanel) while the dialpad is visible.
        //
        // (*) We need to do this when using the dialpad from the
        //     InCallTouchUi widget, but not when using the
        //     SlidingDrawer-based dialpad, because the SlidingDrawer itself
        //     is opaque.)
        if (!mDialer.usingSlidingDrawer()) {				
			if (VDBG) log("isDialerOpened(): " + isDialerOpened() + ", mInCallScreenMode: " + mInCallScreenMode);

/*            if (isDialerOpened()) {
                mInCallPanel.setVisibility(View.GONE);
            } else {
                // Dialpad is dismissed; bring back the CallCard if
                // it's supposed to be visible.
                mInCallPanel.setVisibility(View.VISIBLE);
            }*/
        }
    }

    /**
     * @return true if the DTMF dialpad is currently visible.
     */
    /* package */ boolean isDialerOpened() {
        return (mDialer != null && mDialer.isOpened());
    }    


    //
    // Bluetooth helper methods.
    //
    // - BluetoothAdapter is the Bluetooth system service.  If
    //   getDefaultAdapter() returns null
    //   then the device is not BT capable.  Use BluetoothDevice.isEnabled()
    //   to see if BT is enabled on the device.
    //
    // - BluetoothHeadset is the API for the control connection to a
    //   Bluetooth Headset.  This lets you completely connect/disconnect a
    //   headset (which we don't do from the Phone UI!) but also lets you
    //   get the address of the currently active headset and see whether
    //   it's currently connected.
    //
    // - BluetoothHandsfree is the API to control the audio connection to
    //   a bluetooth headset. We use this API to switch the headset on and
    //   off when the user presses the "Bluetooth" button.
    //   Our BluetoothHandsfree instance (mBluetoothHandsfree) is created
    //   by the PhoneApp and will be null if the device is not BT capable.
    //

    /**
     * @return true if the Bluetooth on/off switch in the UI should be
     *         available to the user (i.e. if the device is BT-capable
     *         and a headset is connected.)
     */
    /* package */ boolean isBluetoothAvailable() {
        if (VDBG) log("isBluetoothAvailable()...");
        if (mBluetoothHandsfree == null) {
            // Device is not BT capable.
            if (VDBG) log("  ==> FALSE (not BT capable)");
            return false;
        }

        // There's no need to ask the Bluetooth system service if BT is enabled:
        //
        //    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        //    if ((adapter == null) || !adapter.isEnabled()) {
        //        if (DBG) log("  ==> FALSE (BT not enabled)");
        //        return false;
        //    }
        //    if (DBG) log("  - BT enabled!  device name " + adapter.getName()
        //                 + ", address " + adapter.getAddress());
        //
        // ...since we already have a BluetoothHeadset instance.  We can just
        // call isConnected() on that, and assume it'll be false if BT isn't
        // enabled at all.

        // Check if there's a connected headset, using the BluetoothHeadset API.
        boolean isConnected = false;
        if (mBluetoothHeadset != null) {
            if (VDBG) log("  - headset state = " + mBluetoothHeadset.getState(null));
            BluetoothDevice headset = mBluetoothHeadset.getCurrentHeadset();
            if (VDBG) log("  - headset address: " + headset);
            if (headset != null) {
                isConnected = mBluetoothHeadset.isConnected(headset);
                if (VDBG) log("  - isConnected: " + isConnected);
            }
        }

        if (VDBG) log("  ==> " + isConnected);
        return isConnected;
    }

    /**
     * @return true if a BT device is available, and its audio is currently connected.
     */
    /* package */ boolean isBluetoothAudioConnected() {
        if (mBluetoothHandsfree == null) {
            if (VDBG) log("isBluetoothAudioConnected: ==> FALSE (null mBluetoothHandsfree)");
            return false;
        }
        boolean isAudioOn = mBluetoothHandsfree.isAudioOn();
        if (VDBG) log("isBluetoothAudioConnected: ==> isAudioOn = " + isAudioOn);
        return isAudioOn;
    }

    /**
     * Helper method used to control the state of the green LED in the
     * "Bluetooth" menu item.
     *
     * @return true if a BT device is available and its audio is currently connected,
     *              <b>or</b> if we issued a BluetoothHandsfree.userWantsAudioOn()
     *              call within the last 5 seconds (which presumably means
     *              that the BT audio connection is currently being set
     *              up, and will be connected soon.)
     */
    /* package */ boolean isBluetoothAudioConnectedOrPending() {
        if (isBluetoothAudioConnected()) {
            if (VDBG) log("isBluetoothAudioConnectedOrPending: ==> TRUE (really connected)");
            return true;
        }

        // If we issued a userWantsAudioOn() call "recently enough", even
        // if BT isn't actually connected yet, let's still pretend BT is
        // on.  This is how we make the green LED in the menu item turn on
        // right away.
        if (mBluetoothConnectionPending) {
            long timeSinceRequest =
                    SystemClock.elapsedRealtime() - mBluetoothConnectionRequestTime;
            if (timeSinceRequest < 5000 /* 5 seconds */) {
                if (VDBG) log("isBluetoothAudioConnectedOrPending: ==> TRUE (requested "
                             + timeSinceRequest + " msec ago)");
                return true;
            } else {
                if (VDBG) log("isBluetoothAudioConnectedOrPending: ==> FALSE (request too old: "
                             + timeSinceRequest + " msec ago)");
                mBluetoothConnectionPending = false;
                return false;
            }
        }

        if (VDBG) log("isBluetoothAudioConnectedOrPending: ==> FALSE");
        return false;
    }

    /**
     * Posts a message to our handler saying to update the onscreen UI
     * based on a bluetooth headset state change.
     */
    /* package */ void requestUpdateBluetoothIndication() {
        if (VDBG) log("requestUpdateBluetoothIndication()...");
        // No need to look at the current state here; any UI elements that
        // care about the bluetooth state (i.e. the CallCard) get
        // the necessary state directly from PhoneApp.showBluetoothIndication().
        mHandler.removeMessages(REQUEST_UPDATE_BLUETOOTH_INDICATION);
        mHandler.sendEmptyMessage(REQUEST_UPDATE_BLUETOOTH_INDICATION);
    }

    private void dumpBluetoothState() {
        log("============== dumpBluetoothState() =============");
        log("= isBluetoothAvailable: " + isBluetoothAvailable());
        log("= isBluetoothAudioConnected: " + isBluetoothAudioConnected());
        log("= isBluetoothAudioConnectedOrPending: " + isBluetoothAudioConnectedOrPending());
        log("= PhoneApp.showBluetoothIndication: "
            + PhoneApp.getInstance().showBluetoothIndication());
        log("=");
        if (mBluetoothHandsfree != null) {
            log("= BluetoothHandsfree.isAudioOn: " + mBluetoothHandsfree.isAudioOn());
            if (mBluetoothHeadset != null) {
                BluetoothDevice headset = mBluetoothHeadset.getCurrentHeadset();
                log("= BluetoothHeadset.getCurrentHeadset: " + headset);
                if (headset != null) {
                    log("= BluetoothHeadset.isConnected: "
                        + mBluetoothHeadset.isConnected(headset));
                }
            } else {
                log("= mBluetoothHeadset is null");
            }
        } else {
            log("= mBluetoothHandsfree is null; device is not BT capable");
        }
    }

    /* package */ void connectBluetoothAudio() {
        if (VDBG) log("connectBluetoothAudio()...");
        if (mBluetoothHandsfree != null) {
            mBluetoothHandsfree.userWantsAudioOn();
        }

        // Watch out: The bluetooth connection doesn't happen instantly;
        // the userWantsAudioOn() call returns instantly but does its real
        // work in another thread.  Also, in practice the BT connection
        // takes longer than MENU_DISMISS_DELAY to complete(!) so we need
        // a little trickery here to make the menu item's green LED update
        // instantly.
        // (See isBluetoothAudioConnectedOrPending() above.)
        mBluetoothConnectionPending = true;
        mBluetoothConnectionRequestTime = SystemClock.elapsedRealtime();
    }

    /* package */ void disconnectBluetoothAudio() {
        if (VDBG) log("disconnectBluetoothAudio()...");
        if (mBluetoothHandsfree != null) {
            mBluetoothHandsfree.userWantsAudioOff();
        }
        mBluetoothConnectionPending = false;
    }

	
	/**
	 * Called any time the DTMF dialpad is opened.
	 * @see DTMFTwelveKeyDialer.onDialerOpen()
	 */
	/* package */ void onDialerOpen() {
		if (DBG) log("onDialerOpen()...");

		// Update any other onscreen UI elements that depend on the dialpad.
		updateDialpadVisibility();

		// This counts as explicit "user activity".
		PhoneApp.getInstance().pokeUserActivity();
	}

	/**
	 * Called any time the DTMF dialpad is closed.
	 * @see DTMFTwelveKeyDialer.onDialerClose()
	 */
	/* package */ void onDialerClose() {
		if (DBG) log("onDialerClose()...");

		final PhoneApp app = PhoneApp.getInstance();

		// Update the visibility of the dialpad itself (and any other
		// onscreen UI elements that depend on it.)
		updateDialpadVisibility();

		// This counts as explicit "user activity".
		app.getInstance().pokeUserActivity();
	}

	private boolean isIn3GNetwork() {
		int radiotech = mPhone.getServiceState().getRadioTechnology();
		if (DBG) log("isIn3GNetwork()..., radiotech: " + radiotech);

		return ((radiotech == ServiceState.RADIO_TECHNOLOGY_UMTS)
			|| (radiotech == ServiceState.RADIO_TECHNOLOGY_HSDPA)
			|| (radiotech == ServiceState.RADIO_TECHNOLOGY_HSUPA)
			|| (radiotech == ServiceState.RADIO_TECHNOLOGY_HSPA));
	}

	private boolean okToRetryVideoCall() {
		return isIn3GNetwork();
	}

	MediaPhone getMediaPhone(){
		if (DBG) log("getMediaPhone()..., mp: " + mp);
		return mp;
	}

    // enable/disable fake camera
    private boolean setCameraFlag(boolean bSet) {
        boolean bRet = false;
		String oldValue = SystemProperties.get("gsm.camera.vt");
		SystemProperties.set("gsm.camera.vt", bSet?"1":"0");
		String newValue = SystemProperties.get("gsm.camera.vt");
        log("setCameraFlag(), bSet: " + bSet + ", oldValue: " + oldValue + ", newValue: " + newValue);
        if (bSet) {
            bRet = newValue.equals("1");
        } else {
            bRet = newValue.equals("0");
        }
        log("setCameraFlag(), bRet: " + bRet);
        return bRet;
    }

    private boolean checkCameraFlag() {
        boolean bRet = false;
        String newValue = SystemProperties.get("gsm.camera.vt");
        bRet = newValue.equals("1");
        log("checkCameraFlag(), bRet: " + bRet);
        return bRet;
    }

	private int getCamerID(){
		int iRet = 0;
		
		if (mCameraType == CameraType.BOTTOM_CAMERA){
			iRet = 0;
		} else if (mCameraType == CameraType.FRONT_CAMERA) {
			iRet = 1;
		} else if (mCameraType == CameraType.FAKE_CAMERA) {
			iRet = 2;            
		} else {
			iRet = 0;
		}
		if (DBG) log("getCamerID(), iRet: " + iRet);
		return iRet;
	}
    
	private int getSensorRotation(){		
		if (mCameraType == CameraType.FRONT_CAMERA){
			return 270;
		}
        return 90;
	}

	private void switchToSpeaker(){
        	final PhoneApp app = PhoneApp.getInstance();
		// open speaker in default
		if (!PhoneUtils.isSpeakerOn(this)){
			if (!app.isHeadsetPlugged()){
				if (!isBluetoothAudioConnected()) {
					PhoneUtils.turnOnSpeaker(this, true, true);					
	 				updateAudioMenu();
				}
			}
		}
	}

	private boolean isAudioInCall(){
		String line = null;
		try{
			FileReader fr = new FileReader("/sys/class/modem/status"); 
			//FileReader fr = new FileReader("/data/status"); 
			BufferedReader br = new BufferedReader(fr);  
			
			log("<isAudioInCall>");  
			line = br.readLine();
			log(line);  
		}catch (Exception e) {
			Log.e(LOG_TAG, "isAudioInCall failed, " + e);
		}

		if (null == line)
			return false;
		
		return line.startsWith("incall:1", 0); 
	}

	private void displayStartTime() {
		log("displayStartTime() mBeginTime: " + mBeginTime.getText());
		if (!mWaitCD && (mBeginTime.getText().length() > 0)){		
			//TextView lable_beginTime = (TextView) findViewById(R.id.label_begintime);
			//lable_beginTime.setVisibility(View.VISIBLE);
			mBeginTime.setVisibility(View.VISIBLE);
		}
	}

	private String getCurrentNumber() {
		Phone.State state = mPhone.getState();
		Call call;
		
		if (state == Phone.State.RINGING){
			call = mPhone.getRingingCall();
		} else if (state == Phone.State.OFFHOOK){
			call = mPhone.getForegroundCall();
		} else {
			return null;
		}

		Connection conn = call.getEarliestConnection();
		String number = conn.getAddress();
		log("getCurrentNumber(), number: " + number);
		return number;
	}
}
