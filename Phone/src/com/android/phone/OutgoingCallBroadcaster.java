/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.phone;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings;
import android.provider.Settings.System;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.MsmsConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * OutgoingCallBroadcaster receives CALL and CALL_PRIVILEGED Intents, and
 * broadcasts the ACTION_NEW_OUTGOING_CALL intent which allows other
 * applications to monitor, redirect, or prevent the outgoing call.

 * After the other applications have had a chance to see the
 * ACTION_NEW_OUTGOING_CALL intent, it finally reaches the
 * {@link OutgoingCallReceiver}, which passes the (possibly modified)
 * intent on to the {@link InCallScreen}.
 *
 * Emergency calls and calls where no number is present (like for a CDMA
 * "empty flash" or a nonexistent voicemail number) are exempt from being
 * broadcast.
 */
public class OutgoingCallBroadcaster extends Activity {

    private static final String PERMISSION = android.Manifest.permission.PROCESS_OUTGOING_CALLS;
    private static final String TAG = "OutgoingCallBroadcaster";
    private static final boolean DBG = true;
            //(PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);

    public static final String EXTRA_ALREADY_CALLED = "android.phone.extra.ALREADY_CALLED";
    public static final String EXTRA_ORIGINAL_URI = "android.phone.extra.ORIGINAL_URI";
	  public static final String EXTRA_IS_VIDEOCALL = "android.phone.extra.IS_VIDEOCALL";
    public static final String EXTRA_NEW_CALL_INTENT = "android.phone.extra.NEW_CALL_INTENT";
    public static final String EXTRA_SIP_PHONE_URI = "android.phone.extra.SIP_PHONE_URI";
    public static final String FAST_DIAL = "com.android.phone.extra.FAST_DIAL";
    public static final String SIM_SELECTED = "com.android.phone.extra.SIM_SELECTED";
    public static final String EXTRA_SUB1_IS_ACTIVE = "android.phone.extra.SUB1_IS_ACTIVE";
    public static final String EXTRA_SUB2_IS_ACTIVE = "android.phone.extra.SUB2_IS_ACTIVE";



    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Receiving an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an external ITelephony call in the future.
     * TODO: Keep in sync with the string defined in TwelveKeyDialer.java in Contacts app
     * until this is replaced with the ITelephony API.
     */
    public static final String EXTRA_SEND_EMPTY_FLASH = "com.android.phone.extra.SEND_EMPTY_FLASH";

    /**
     * OutgoingCallReceiver finishes NEW_OUTGOING_CALL broadcasts, starting
     * the InCallScreen if the broadcast has not been canceled, possibly with
     * a modified phone number and optional provider info (uri + package name + remote views.)
     */
    public class OutgoingCallReceiver extends BroadcastReceiver {
        private static final String TAG = "OutgoingCallReceiver";

        public void onReceive(Context context, Intent intent) {
            doReceive(context, intent);
            finish();
        }

        public void doReceive(Context context, Intent intent) {
            if (DBG) Log.v(TAG, "doReceive: " + intent);

            boolean alreadyCalled;
            String number;
            String originalUri;
			boolean isStkCall = false;

            alreadyCalled = intent.getBooleanExtra(
                    OutgoingCallBroadcaster.EXTRA_ALREADY_CALLED, false);
            if (alreadyCalled) {
                if (DBG) Log.v(TAG, "CALL already placed -- returning.");
                return;
            }

            number = getResultData();
            final PhoneApp app = PhoneApp.getInstance();

            if (TelephonyCapabilities.supportsOtasp(app.phone)) {
                boolean activateState = (app.cdmaOtaScreenState.otaScreenState
                        == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION);
                boolean dialogState = (app.cdmaOtaScreenState.otaScreenState
                        == OtaUtils.CdmaOtaScreenState.OtaScreenState
                        .OTA_STATUS_SUCCESS_FAILURE_DLG);
                boolean isOtaCallActive = false;

                if ((app.cdmaOtaScreenState.otaScreenState
                        == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_PROGRESS)
                        || (app.cdmaOtaScreenState.otaScreenState
                        == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_LISTENING)) {
                    isOtaCallActive = true;
                }

                if (activateState || dialogState) {
                    if (dialogState) app.dismissOtaDialogs();
                    app.clearOtaState();
                    app.clearInCallScreenMode();
                } else if (isOtaCallActive) {
                    if (DBG) Log.v(TAG, "OTA call is active, a 2nd CALL cancelled -- returning.");
                    return;
                }
            }

            if (number == null) {
                if (DBG) Log.v(TAG, "CALL cancelled (null number), returning...");
                return;
            } else if (TelephonyCapabilities.supportsOtasp(app.phone)
                    && (app.phone.getState() != Phone.State.IDLE)
                    && (app.phone.isOtaSpNumber(number))) {
                if (DBG) Log.v(TAG, "Call is active, a 2nd OTA call cancelled -- returning.");
                return;
            } else if (PhoneNumberUtils.isEmergencyNumber(number)) {
                Log.w(TAG, "Cannot modify outgoing call to emergency number " + number + ".");
                return;
            }

            originalUri = intent.getStringExtra(
                    OutgoingCallBroadcaster.EXTRA_ORIGINAL_URI);
            if (originalUri == null) {
                Log.e(TAG, "Intent is missing EXTRA_ORIGINAL_URI -- returning.");
                return;
            }

            Uri uri = Uri.parse(originalUri);

            // Since the number could be modified/rewritten by the broadcast,
            // we have to strip the unwanted characters here.
            number = PhoneNumberUtils.stripSeparators(
                    PhoneNumberUtils.convertKeypadLettersToDigits(number));

            if (DBG) Log.v(TAG, "CALL to " + number /*"xxxxxxx"*/ + " proceeding.");
            
            boolean isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEOCALL, false);
            int resId = 0;
            if (isVideoCall) {
    			if (PhoneUtils.isVideoCall()) {
					Log.e(TAG, "Cann't make another videocall, during video call");
					resId = R.string.incall_error_dialvt_in_3gcall;
    			} else {
    			    for (Phone phone : CallManager.getInstance().getAllPhones()) {
                        if (phone != null) {
                            if (phone.getState() != Phone.State.IDLE){
            					Log.e(TAG, "Cann't make another videocall, during voice call");
            					resId = R.string.incall_error_dialvt_in_2gcall;
                            }
                        }
                    }
                }
            } else {
    			if (PhoneUtils.isVideoCall()) {
    				Log.e(TAG, "Cann't make another voice, during video call");
                    resId = R.string.incall_error_dial_in_3gcall;
    			}
            }
            if (resId != 0) {
        		Toast txtToast = Toast.makeText(getWindow().getContext(), getString(resId), Toast.LENGTH_LONG);
        		txtToast.show();
                return;
            }
            
            if (PhoneApp.getInstance().getInVideoCallScreen() != null) {
				Log.e(TAG, "Cann't make another call, because getInVideoCallScreen() != null");
				return;
            }
            startSipCallOptionsHandler(context, intent, uri, number);
        }
    }


    private void startSipCallOptionsHandler(Context context, Intent intent,
            Uri uri, String number) {
        Intent newIntent = new Intent(Intent.ACTION_CALL, uri);
        newIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);

        PhoneUtils.checkAndCopyPhoneProviderExtras(intent, newIntent);

		boolean isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEOCALL, false);

		if (isVideoCall) {
			newIntent.setClass(context, InVideoCallScreen.class);
		} else {
			newIntent.setClass(context, InCallScreen.class);
		}
		newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		boolean isStkCall = intent.getBooleanExtra("StkCallFlag", false);
		Log.v(TAG, "isStkCall = " + isStkCall);
		newIntent.putExtra("StkCallFlag", isStkCall);
        boolean isNeedToAirplaneModeOff = intent.getBooleanExtra("isNeedToAirplaneModeOff", false);
        Log.v(TAG, "isNeedToAirplaneModeOff = " + isNeedToAirplaneModeOff);
        newIntent.putExtra("isNeedToAirplaneModeOff", isNeedToAirplaneModeOff);
		//to do 
		int phoneId = intent.getIntExtra(MsmsConstants.SUBSCRIPTION_KEY, 0);
		newIntent.putExtra(MsmsConstants.SUBSCRIPTION_KEY,phoneId);
		
        Intent selectPhoneIntent = new Intent(EXTRA_NEW_CALL_INTENT, uri);
        selectPhoneIntent.setClass(context, SipCallOptionHandler.class);
        selectPhoneIntent.putExtra(EXTRA_NEW_CALL_INTENT, newIntent);
        selectPhoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (DBG) Log.v(TAG, "startSipCallOptionsHandler(): " + "calling startActivity: " + selectPhoneIntent);
        context.startActivity(selectPhoneIntent);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        final Configuration configuration = getResources().getConfiguration();

        if (DBG) Log.v(TAG, "onCreate: this = " + this + ", icicle = " + icicle);
        if (DBG) Log.v(TAG, " - getIntent() = " + intent);
        if (DBG) Log.v(TAG, " - configuration = " + configuration);

        if (icicle != null) {
            // A non-null icicle means that this activity is being
            // re-initialized after previously being shut down.
            //
            // In practice this happens very rarely (because the lifetime
            // of this activity is so short!), but it *can* happen if the
            // framework detects a configuration change at exactly the
            // right moment; see bug 2202413.
            //
            // In this case, do nothing.  Our onCreate() method has already
            // run once (with icicle==null the first time), which means
            // that the NEW_OUTGOING_CALL broadcast for this new call has
            // already been sent.
            Log.i(TAG, "onCreate: non-null icicle!  "
                  + "Bailing out, not sending NEW_OUTGOING_CALL broadcast...");

            // No need to finish() here, since the OutgoingCallReceiver from
            // our original instance will do that.  (It'll actually call
            // finish() on our original instance, which apparently works fine
            // even though the ActivityManager has already shut that instance
            // down.  And note that if we *do* call finish() here, that just
            // results in an "ActivityManager: Duplicate finish request"
            // warning when the OutgoingCallReceiver runs.)

            return;
        }

        String number = PhoneNumberUtils.getNumberFromIntent(intent, this);

        //now to check which phone we will used
        boolean fromBlueooth = isIntentFromBluetooth(intent);
        boolean sipCall = isSIPCall(number, intent);
        boolean sendEmptyFlash = intent.getBooleanExtra(EXTRA_SEND_EMPTY_FLASH, false);
        boolean isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEOCALL, false);
        boolean isStkCall = intent.getBooleanExtra("StkCallFlag", false);
        boolean isFastDial = intent.getBooleanExtra(FAST_DIAL, false);
        //get default sim id. -1 will be always ask
        int defaultPhoneId = TelephonyManager.getDefaultSim(this,TelephonyManager.MODE_TEL);
        int phoneId = PhoneApp.getInstance().getVoiceSubscription();

        if (isVideoCall) {
            defaultPhoneId = TelephonyManager.getDefaultSim(this,TelephonyManager.MODE_VTEL);
            phoneId = PhoneApp.getInstance().getVideoSubscription();
        }
        Log.d(TAG, "defaultPhoneId is:" + defaultPhoneId);
        //show dialog when defaultPhoneId == -1 or has no active card
        boolean ask = (defaultPhoneId == -1 || PhoneApp.getInstance().getActiveSubCount() == 0)
                && !fromBlueooth && !sipCall && !isStkCall;
        boolean simSelected = intent.getBooleanExtra(OutgoingCallBroadcaster.SIM_SELECTED, false);
        if (ask && !simSelected) {
        	 if (DBG) Log.d(TAG, "Start multisimdialer activity and get the sub selected by user");
             Intent intentMSim = new Intent(this, MsmsDialerActivity.class);
             intentMSim.setData(intent.getData());
             intentMSim.setAction(intent.getAction());
             intentMSim.putExtra(EXTRA_SEND_EMPTY_FLASH, sendEmptyFlash);
             intentMSim.putExtra(EXTRA_IS_VIDEOCALL, isVideoCall);
             intentMSim.putExtra("StkCallFlag", isStkCall);
             intentMSim.putExtra(FAST_DIAL, isFastDial);
             intentMSim.putExtra(EXTRA_SUB1_IS_ACTIVE, PhoneFactory.isCardReady(0));
             intentMSim.putExtra(EXTRA_SUB2_IS_ACTIVE, PhoneFactory.isCardReady(1));
             int requestCode = 1;
//             startActivityForResult(intentMSim, requestCode);
             startActivity(intentMSim);
             finish();
         }else {
             if (simSelected) {
                 phoneId = intent.getIntExtra(Phone.PHONE_ID,PhoneFactory.RAW_DEFAULT_PHONE_ID);
             }
             Log.d(TAG, "subscription when there is:" + phoneId);

	        if (isFastDial && !simSelected) {
	            SharedPreferences fastDialSp = getApplicationContext().getSharedPreferences("fast_dial_numbers" + phoneId,
	                    Context.MODE_WORLD_READABLE);
	            number = fastDialSp.getString("fast_dial_" + number, "");
	            intent.setData(Uri.fromParts("tel", number, null));
	            Log.d(TAG, "single phone fast dial number:" + number);
	        }
			processIntent(intent,phoneId);
         }
    }
     
    private void processIntent(Intent intent,int phoneId) {
    	String action = intent.getAction();
        intent.putExtra(Phone.PHONE_ID, phoneId);
        if (DBG)Log.d(TAG, "outGoingcallBroadCaster action is :"+ action);
        String number = PhoneNumberUtils.getNumberFromIntent(intent, this);
        if (DBG)Log.d(TAG, " number from Intent : "+ number);
        // Check the number, don't convert for sip uri
        // TODO put uriNumber under PhoneNumberUtils
        if (number != null) {
            if (!PhoneNumberUtils.isUriNumber(number)) {
                number = PhoneNumberUtils.convertKeypadLettersToDigits(number);
                number = PhoneNumberUtils.stripSeparators(number);
            }
        }

        final boolean emergencyNumber = (number != null) && PhoneNumberUtils.isEmergencyNumber(number);

        //call emergency call use the sim card that has emergencyNubmer.
        //get phoneId for ds emergency call
        if (emergencyNumber && PhoneFactory.getPhoneCount() > 1) {
            int existSubCount = PhoneApp.getInstance().getExistSubCount();
            int activeSubCount = PhoneApp.getInstance().getActiveSubCount();
            if (existSubCount != 0 && (isCardStandby(phoneId) || activeSubCount == 0)
                    && !PhoneNumberUtils.isSimEmergencyNumber(number, phoneId)) {
                for (int i = 0; i < existSubCount; i++) {
                    if (PhoneNumberUtils.isSimEmergencyNumber(number, i)) {
                        phoneId = i;
                        break;
                    }
                }
            }
            if (DBG) Log.i(TAG, "onCreate: emergencyNumber phoneId= " + phoneId);
            intent.putExtra(Phone.PHONE_ID, phoneId);
        }

        boolean callNow;

        if (getClass().getName().equals(intent.getComponent().getClassName())) {
            // If we were launched directly from the OutgoingCallBroadcaster,
            // not one of its more privileged aliases, then make sure that
            // only the non-privileged actions are allowed.
            if (!Intent.ACTION_CALL.equals(intent.getAction())) {
                Log.w(TAG, "Attempt to deliver non-CALL action; forcing to CALL");
                intent.setAction(Intent.ACTION_CALL);
            }
        }

        /* Change CALL_PRIVILEGED into CALL or CALL_EMERGENCY as needed. */
        // TODO: This code is redundant with some code in InCallScreen: refactor.
        if (Intent.ACTION_CALL_PRIVILEGED.equals(action)) {
            action = emergencyNumber
                    ? Intent.ACTION_CALL_EMERGENCY
                    : Intent.ACTION_CALL;
            if (DBG) Log.v(TAG, "- updating action from CALL_PRIVILEGED to " + action);
            intent.setAction(action);
        }

        if (Intent.ACTION_CALL.equals(action)) {
            if (emergencyNumber) {
                Log.w(TAG, "Cannot call emergency number " + number
                        + " with CALL Intent " + intent + ".");

                Intent invokeFrameworkDialer = new Intent();

                // TwelveKeyDialer is in a tab so we really want
                // DialtactsActivity.  Build the intent 'manually' to
                // use the java resolver to find the dialer class (as
                // opposed to a Context which look up known android
                // packages only)
                invokeFrameworkDialer.setClassName("com.android.contacts",
                                                   "com.android.contacts.DialtactsActivity");
                invokeFrameworkDialer.setAction(Intent.ACTION_DIAL);
                invokeFrameworkDialer.setData(intent.getData());

                if (DBG) Log.v(TAG, "onCreate(): calling startActivity for Dialer: "
                               + invokeFrameworkDialer);
                startActivity(invokeFrameworkDialer);
                finish();
                return;
            }
            callNow = false;
        } else if (Intent.ACTION_CALL_EMERGENCY.equals(action)) {
            // ACTION_CALL_EMERGENCY case: this is either a CALL_PRIVILEGED
            // intent that we just turned into a CALL_EMERGENCY intent (see
            // above), or else it really is an CALL_EMERGENCY intent that
            // came directly from some other app (e.g. the EmergencyDialer
            // activity built in to the Phone app.)
            if (!emergencyNumber) {
                Log.w(TAG, "Cannot call non-emergency number " + number
                        + " with EMERGENCY_CALL Intent " + intent + ".");
                finish();
                return;
            }
            intent.setAction(action);
            callNow = true;
        } else {
            Log.e(TAG, "Unhandled Intent " + intent + ".");
            finish();
            return;
        }

        // Make sure the screen is turned on.  This is probably the right
        // thing to do, and more importantly it works around an issue in the
        // activity manager where we will not launch activities consistently
        // when the screen is off (since it is trying to keep them paused
        // and has...  issues).
        //
        // Also, this ensures the device stays awake while doing the following
        // broadcast; technically we should be holding a wake lock here
        // as well.
        PhoneApp.getInstance().wakeUpScreen();

        /* If number is null, we're probably trying to call a non-existent voicemail number,
         * send an empty flash or something else is fishy.  Whatever the problem, there's no
         * number, so there's no point in allowing apps to modify the number. */
        if (number == null || TextUtils.isEmpty(number)) {
            if (intent.getBooleanExtra(FAST_DIAL, false)) {
                Toast.makeText(getApplicationContext(), R.string.no_fast_dial, Toast.LENGTH_LONG).show();
                intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
            }
            if (intent.getBooleanExtra(EXTRA_SEND_EMPTY_FLASH, false)) {
                Log.i(TAG, "onCreate: SEND_EMPTY_FLASH...");
                PhoneUtils.sendEmptyFlash(PhoneApp.getPhone());
                finish();
                return;
            } else {
                Log.i(TAG, "onCreate: null or empty number, setting callNow=true...");
                callNow = true;
            }
        }

        if (callNow) {
            intent.setClass(this, InCallScreen.class);
            if (DBG) Log.v(TAG, "onCreate(): callNow case, calling startActivity: " + intent);
            startActivity(intent);
            finish();
            return;
        }

        // For now, SIP calls will be processed directly without a
        // NEW_OUTGOING_CALL broadcast.
        //
        // TODO: In the future, though, 3rd party apps *should* be allowed to
        // intercept outgoing calls to SIP addresses as well.  To do this, we should
        // (1) update the NEW_OUTGOING_CALL intent documentation to explain this
        // case, and (2) pass the outgoing SIP address by *not* overloading the
        // EXTRA_PHONE_NUMBER extra, but instead using a new separate extra to hold
        // the outgoing SIP address.  (Be sure to document whether it's a URI or just
        // a plain address, whether it could be a tel: URI, etc.)
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        if ("sip".equals(scheme) || PhoneNumberUtils.isUriNumber(number)) {
            startSipCallOptionsHandler(this, intent, uri, number);
            finish();
            return;
        }

        Intent broadcastIntent = new Intent(Intent.ACTION_NEW_OUTGOING_CALL);
        if (number != null) {
            broadcastIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);
        }
        PhoneUtils.checkAndCopyPhoneProviderExtras(intent, broadcastIntent);
        broadcastIntent.putExtra(EXTRA_ALREADY_CALLED, callNow);
        broadcastIntent.putExtra(EXTRA_ORIGINAL_URI, uri.toString());

		boolean isVideoCall;
		isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEOCALL, false);
		broadcastIntent.putExtra(EXTRA_IS_VIDEOCALL, isVideoCall);
		boolean isStkCall = intent.getBooleanExtra("StkCallFlag", false);
        broadcastIntent.putExtra("StkCallFlag", isStkCall);
        broadcastIntent.putExtra(MsmsConstants.SUBSCRIPTION_KEY,phoneId);
        boolean isNeedToAirplaneModeOff = intent.getBooleanExtra("isNeedToAirplaneModeOff", false);
        broadcastIntent.putExtra("isNeedToAirplaneModeOff", isNeedToAirplaneModeOff);
        if (DBG) Log.v(TAG, "Broadcasting intent " + broadcastIntent + ".");
        sendOrderedBroadcast(broadcastIntent, PERMISSION,
                new OutgoingCallReceiver(), null, Activity.RESULT_OK, number, null);
        // The receiver will finish our activity when it finally runs.
    }

    // Implement onConfigurationChanged() purely for debugging purposes,
    // to make sure that the android:configChanges element in our manifest
    // is working properly.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DBG) Log.v(TAG, "onConfigurationChanged: newConfig = " + newConfig);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Collect subscription data from the intent and use it
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "activity cancelled or backkey pressed ");
            finish();
        } else if (resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            int phoneId = extras.getInt(Phone.PHONE_ID);
            Log.d(TAG, "subscription selected from multiSimDialer : " + phoneId);
            processIntent(data,phoneId);
        }
    }

	public static final String BLUETOOTH = "Bluetooth";

	private boolean isIntentFromBluetooth(Intent intent) {
		boolean btIntent = false;
		Bundle extras = intent.getExtras();
		if (extras != null) {
			if ((extras.getString(BLUETOOTH) != null) && (extras.getString(BLUETOOTH).equals("true"))) {
				btIntent = true;
				if (DBG)Log.d(TAG, "isIntentFromBluetooth " + btIntent + "intent :" + extras.getString(BLUETOOTH));
			}
		}
		return btIntent;
	}

	private boolean isSIPCall(String number, Intent intent) {
		boolean sipCall = false;
		String scheme = "";
		if (intent.getData() != null) {
			scheme = intent.getData().getScheme();
			if ((scheme != null) && ("sip".equals(scheme) || PhoneNumberUtils.isUriNumber(number))) {
				sipCall = true;
			}
		}
		if (DBG)Log.d(TAG, "isSIPCall : " + sipCall);
		return sipCall;
	}

    private boolean isCardStandby(int phoneId) {
        return System.getInt(getContentResolver(),
                PhoneFactory.getSetting(System.SIM_STANDBY, phoneId), 1) == 1;
    }

}
