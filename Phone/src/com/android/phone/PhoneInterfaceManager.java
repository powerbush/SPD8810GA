/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.NeighboringCellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * Implementation of the ITelephony interface.
 */
public class PhoneInterfaceManager extends ITelephony.Stub {
    private static final String LOG_TAG = "PhoneInterfaceManager";
    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    // Message codes used with mMainThreadHandler
    private static final int CMD_HANDLE_PIN_MMI = 1;
    private static final int CMD_HANDLE_NEIGHBORING_CELL = 2;
    private static final int EVENT_NEIGHBORING_CELL_DONE = 3;
    private static final int CMD_ANSWER_RINGING_CALL = 4;
    private static final int CMD_END_CALL = 5;  // not used yet
    private static final int CMD_SILENCE_RINGER = 6;

    /** The singleton instance. */
    private static PhoneInterfaceManager sInstance;

    PhoneApp mApp;
    Phone mPhone;
    CallManager mCM;
    MainThreadHandler mMainThreadHandler;

    /**
     * A request object for use with {@link MainThreadHandler}. Requesters should wait() on the
     * request after sending. The main thread will notify the request when it is complete.
     */
    private static final class MainThreadRequest {
        /** The argument to use for the request */
        public Object argument;
        /** The result of the request that is run on the main thread */
        public Object result;

        public MainThreadRequest(Object argument) {
            this.argument = argument;
        }
    }

    /**
     * A handler that processes messages on the main thread in the phone process. Since many
     * of the Phone calls are not thread safe this is needed to shuttle the requests from the
     * inbound binder threads to the main thread in the phone process.  The Binder thread
     * may provide a {@link MainThreadRequest} object in the msg.obj field that they are waiting
     * on, which will be notified when the operation completes and will contain the result of the
     * request.
     *
     * <p>If a MainThreadRequest object is provided in the msg.obj field,
     * note that request.result must be set to something non-null for the calling thread to
     * unblock.
     */
    private final class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MainThreadRequest request;
            Message onCompleted;
            AsyncResult ar;

            switch (msg.what) {
                case CMD_HANDLE_PIN_MMI:
                    request = (MainThreadRequest) msg.obj;
                    request.result = Boolean.valueOf(
                            mPhone.handlePinMmi((String) request.argument));
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_HANDLE_NEIGHBORING_CELL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NEIGHBORING_CELL_DONE,
                            request);
                    mPhone.getNeighboringCids(onCompleted);
                    break;

                case EVENT_NEIGHBORING_CELL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        // create an empty list to notify the waiting thread
                        request.result = new ArrayList<NeighboringCellInfo>();
                    }
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_ANSWER_RINGING_CALL:
                    answerRingingCallInternal();
                    break;

                case CMD_SILENCE_RINGER:
                    silenceRingerInternal();
                    break;

                case CMD_END_CALL:
                    request = (MainThreadRequest) msg.obj;
                    boolean hungUp = false;
                    int phoneType = mPhone.getPhoneType();
                    if (phoneType == Phone.PHONE_TYPE_CDMA) {
                        // CDMA: If the user presses the Power button we treat it as
                        // ending the complete call session
                        hungUp = PhoneUtils.hangupRingingAndActive(mPhone);
                    } else if (phoneType == Phone.PHONE_TYPE_GSM) {
                        // GSM: End the call as per the Phone state
                        hungUp = PhoneUtils.hangup(mCM);
                    } else {
                        throw new IllegalStateException("Unexpected phone type: " + phoneType);
                    }
                    if (DBG) log("CMD_END_CALL: " + (hungUp ? "hung up!" : "no call to hang up"));
                    request.result = hungUp;
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                default:
                    Log.w(LOG_TAG, "MainThreadHandler: unexpected message code: " + msg.what);
                    break;
            }
        }
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see sendRequestAsync
     */
    private Object sendRequest(int command, Object argument) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }

        MainThreadRequest request = new MainThreadRequest(argument);
        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();

        // Wait for the request to complete
        synchronized (request) {
            while (request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    // Do nothing, go back and wait until the request is complete
                }
            }
        }
        return request.result;
    }

    /**
     * Asynchronous ("fire and forget") version of sendRequest():
     * Posts the specified command to be executed on the main thread, and
     * returns immediately.
     * @see sendRequest
     */
    private void sendRequestAsync(int command) {
        mMainThreadHandler.sendEmptyMessage(command);
    }

    /**
     * Initialize the singleton PhoneInterfaceManager instance.
     * This is only done once, at startup, from PhoneApp.onCreate().
     */
    /* package */ static PhoneInterfaceManager init(PhoneApp app, Phone phone) {
        synchronized (PhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManager(app, phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /** Private constructor; @see init() */
    PhoneInterfaceManager(PhoneApp app, Phone phone) {
        mApp = app;
        mPhone = phone;
        mCM = PhoneApp.getInstance().mCM;
        mMainThreadHandler = new MainThreadHandler();
        publish();
    }

    private void publish() {
        String service = "phone";
        int phoneId = mPhone.getPhoneId();
        ServiceManager.addService(PhoneFactory.getServiceName(service, phoneId), this);
    }

    //
    // Implementation of the ITelephony interface.
    //

    public void dial(String number) {
        if (DBG) log("dial: " + number);
        // No permission check needed here: This is just a wrapper around the
        // ACTION_DIAL intent, which is available to any app since it puts up
        // the UI before it does anything.

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        // PENDING: should we just silently fail if phone is offhook or ringing?
        Phone.State state = mPhone.getState();
        if (state != Phone.State.OFFHOOK && state != Phone.State.RINGING) {
            Intent  intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        }
    }

    public void call(String number) {
        if (DBG) log("call: " + number);

        // This is just a wrapper around the ACTION_CALL intent, but we still
        // need to do a permission check since we're calling startActivity()
        // from the context of the phone app.
        enforceCallPermission();

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(mApp, PhoneApp.getCallScreenClassName());
        mApp.startActivity(intent);
    }

    private boolean showCallScreenInternal(boolean specifyInitialDialpadState,
                                           boolean initialDialpadState) {
        if (isIdle()) {
            return false;
        }
        // If the phone isn't idle then go to the in-call screen
        long callingId = Binder.clearCallingIdentity();
        try {
            Intent intent;
            if (specifyInitialDialpadState) {
                intent = PhoneApp.createInCallIntent(initialDialpadState);
            } else {
                intent = PhoneApp.createInCallIntent();
            }
            mApp.startActivity(intent);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return true;
    }

    // Show the in-call screen without specifying the initial dialpad state.
    public boolean showCallScreen() {
        return showCallScreenInternal(false, false);
    }

    // The variation of showCallScreen() that specifies the initial dialpad state.
    // (Ideally this would be called showCallScreen() too, just with a different
    // signature, but AIDL doesn't allow that.)
    public boolean showCallScreenWithDialpad(boolean showDialpad) {
        return showCallScreenInternal(true, showDialpad);
    }

    /**
     * End a call based on call state
     * @return true is a call was ended
     */
    public boolean endCall() {
        enforceCallPermission();
        return (Boolean) sendRequest(CMD_END_CALL, null);
    }

    public void holdCall() {
        PhoneUtils.switchHoldingAndActive((mCM.getFirstActiveBgCall()));
    }

    public void answerRingingCall() {
        if (DBG) log("answerRingingCall...");
        // TODO: there should eventually be a separate "ANSWER_PHONE" permission,
        // but that can probably wait till the big TelephonyManager API overhaul.
        // For now, protect this call with the MODIFY_PHONE_STATE permission.
        enforceModifyPermission();
        sendRequestAsync(CMD_ANSWER_RINGING_CALL);
    }

    /**
     * Make the actual telephony calls to implement answerRingingCall().
     * This should only be called from the main thread of the Phone app.
     * @see answerRingingCall
     *
     * TODO: it would be nice to return true if we answered the call, or
     * false if there wasn't actually a ringing incoming call, or some
     * other error occurred.  (In other words, pass back the return value
     * from PhoneUtils.answerCall() or PhoneUtils.answerAndEndActive().)
     * But that would require calling this method via sendRequest() rather
     * than sendRequestAsync(), and right now we don't actually *need* that
     * return value, so let's just return void for now.
     */
    private void answerRingingCallInternal() {
        final boolean hasRingingCall = !mPhone.getRingingCall().isIdle();
        if (hasRingingCall) {
            final boolean hasActiveCall = !mPhone.getForegroundCall().isIdle();
            final boolean hasHoldingCall = !mPhone.getBackgroundCall().isIdle();
            if (hasActiveCall && hasHoldingCall) {
                // Both lines are in use!
                // TODO: provide a flag to let the caller specify what
                // policy to use if both lines are in use.  (The current
                // behavior is hardwired to "answer incoming, end ongoing",
                // which is how the CALL button is specced to behave.)
                PhoneUtils.answerAndEndActive(mCM, mCM.getFirstActiveRingingCall());
                return;
            } else {
                // answerCall() will automatically hold the current active
                // call, if there is one.
                PhoneUtils.answerCall(mCM.getFirstActiveRingingCall());
                return;
            }
        } else {
            // No call was ringing.
            return;
        }
    }

    public void silenceRinger() {
        if (DBG) log("silenceRinger...");
        // TODO: find a more appropriate permission to check here.
        // (That can probably wait till the big TelephonyManager API overhaul.
        // For now, protect this call with the MODIFY_PHONE_STATE permission.)
        enforceModifyPermission();
        sendRequestAsync(CMD_SILENCE_RINGER);
    }

    /**
     * Internal implemenation of silenceRinger().
     * This should only be called from the main thread of the Phone app.
     * @see silenceRinger
     */
    private void silenceRingerInternal() {
        if ((mPhone.getState() == Phone.State.RINGING)
            && mApp.notifier.isRinging()) {
            // Ringer is actually playing, so silence it.
            if (DBG) log("silenceRingerInternal: silencing...");
            PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
            mApp.notifier.silenceRinger();
        }
    }

    public boolean isOffhook() {
        return (mPhone.getState() == Phone.State.OFFHOOK);
    }

    public boolean isRinging() {
        return (mPhone.getState() == Phone.State.RINGING);
    }

    public boolean isIdle() {
        return (mPhone.getState() == Phone.State.IDLE);
    }

    public boolean isSimPinEnabled() {
        enforceReadPermission();
        return (PhoneApp.getInstance().isSimPinEnabled());
    }

    public boolean supplyPin(String pin) {
        enforceModifyPermission();
        final CheckSimPin checkSimPin = new CheckSimPin(mPhone.getIccCard());
        checkSimPin.start();
        return checkSimPin.checkPin(pin);
    }

    //PUK Input Add Start
    public boolean supplyPuk(String puk,String pin) {
        enforceModifyPermission();
        final CheckSimPuk checkSimPuk = new CheckSimPuk(mPhone.getIccCard());
        checkSimPuk.start();
        return checkSimPuk.checkPuk(puk,pin);
    }

    /**
     * Helper thread to turn async call to {@link SimCard#supplyPin} into
     * a synchronous one.
     */
    private static class CheckSimPuk extends Thread {

        private final IccCard mSimCard;

        private boolean mDone = false;
        private boolean mResult = false;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SUPPLY_PUK_COMPLETE = 100;

        public CheckSimPuk(IccCard simCard) {
            mSimCard = simCard;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (CheckSimPuk.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case SUPPLY_PUK_COMPLETE:
                                Log.d(LOG_TAG, "SUPPLY_PUK_COMPLETE");
                                synchronized (CheckSimPuk.this) {
                                    mResult = (ar.exception == null);
                                    mDone = true;
                                    CheckSimPuk.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                CheckSimPuk.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized boolean checkPuk(String puk,String pin) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SUPPLY_PUK_COMPLETE);

            mSimCard.supplyPuk(puk,pin,callback);

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "done");
            return mResult;
        }
    }
    //PUK Input Add End
	
    /**
     * Helper thread to turn async call to {@link SimCard#supplyPin} into
     * a synchronous one.
     */
    private static class CheckSimPin extends Thread {

        private final IccCard mSimCard;

        private boolean mDone = false;
        private boolean mResult = false;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SUPPLY_PIN_COMPLETE = 100;

        public CheckSimPin(IccCard simCard) {
            mSimCard = simCard;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (CheckSimPin.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case SUPPLY_PIN_COMPLETE:
                                Log.d(LOG_TAG, "SUPPLY_PIN_COMPLETE");
                                synchronized (CheckSimPin.this) {
                                    mResult = (ar.exception == null);
                                    mDone = true;
                                    CheckSimPin.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                CheckSimPin.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized boolean checkPin(String pin) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SUPPLY_PIN_COMPLETE);

            mSimCard.supplyPin(pin, callback);

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "done");
            return mResult;
        }
    }

    public String getSmsc() {
        enforceModifyPermission();
        final GetSetSMSC getSMSC = new GetSetSMSC(mPhone, null);
        getSMSC.start();
        return getSMSC.getSmsc();
    }

    public boolean setSmsc(String smscAddr) {
        enforceModifyPermission();
        final GetSetSMSC getSMSC = new GetSetSMSC(mPhone, smscAddr);
        getSMSC.start();
        return getSMSC.setSmsc();
    }

    private static class GetSetSMSC extends Thread {
        private final Phone mPhone;
        private final String mSmscStr;
        private boolean mDone = false;
        private String mResult;
        private boolean bResult = false;

        private Handler mHandler;

        // For async handler to identify request type
        private static final int QUERY_SMSC_DONE = 100;
        private static final int UPDATE_SMSC_DONE = 101;

        public GetSetSMSC(Phone phone, String SmscStr) {
            mPhone = phone;
            mSmscStr = SmscStr;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (GetSetSMSC.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case QUERY_SMSC_DONE:
                                Log.d(LOG_TAG, "[smsc]QUERY_SMSC_DONE");
                                synchronized (GetSetSMSC.this) {
                                    if (ar.exception == null) {
                                    	mResult = (String)ar.result;
                                    } else {
                                    	mResult = "";
                                    }
                                    mDone = true;
                                    GetSetSMSC.this.notifyAll();
                                }
                                break;
                            case UPDATE_SMSC_DONE:
                                Log.d(LOG_TAG, "[smsc]UPDATE_SMSC_DONE");
                                synchronized (GetSetSMSC.this) {
                                    bResult = (ar.exception == null);
                                    mDone = true;
                                    GetSetSMSC.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                GetSetSMSC.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized String getSmsc() {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            mPhone.getSmscAddress(mHandler.obtainMessage(QUERY_SMSC_DONE));

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "[smsc]wait get for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "[smsc]get done");
            return mResult;
        }

        synchronized boolean setSmsc() {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            mPhone.setSmscAddress(mSmscStr, mHandler.obtainMessage(UPDATE_SMSC_DONE));

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "[smsc]wait set for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "[smsc]set done. result = "+bResult);
            return bResult;
        }
    }

    public void updateServiceLocation() {
        // No permission check needed here: this call is harmless, and it's
        // needed for the ServiceState.requestStateUpdate() call (which is
        // already intentionally exposed to 3rd parties.)
        mPhone.updateServiceLocation();
    }

    public boolean isIccCardOn() {
        return mPhone.getIccCard().isIccCardOn();
    }

    public boolean setIccCard(boolean turnOn) {
        enforceModifyPermission();
        mPhone.setIccCard(turnOn);
        return true;
    }

    public boolean isRadioOn() {
        return mPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF;
    }

    public void toggleRadioOnOff() {
        enforceModifyPermission();
        mPhone.setRadioPower(!isRadioOn());
    }
    public boolean setRadio(boolean turnOn) {
        enforceModifyPermission();
        if ((mPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF) != turnOn) {
            toggleRadioOnOff();
        }
        return true;
    }

    public boolean enableDataConnectivity() {
        enforceModifyPermission();
        return mPhone.enableDataConnectivity();
    }

    public int enableApnType(String type) {
        enforceModifyPermission();
        return mPhone.enableApnType(type);
    }

    public int disableApnType(String type) {
        enforceModifyPermission();
        return mPhone.disableApnType(type);
    }

    public boolean disableDataConnectivity() {
        enforceModifyPermission();
        return mPhone.disableDataConnectivity();
    }

    public boolean isDataConnectivityPossible() {
        return mPhone.isDataConnectivityPossible();
    }

    public boolean handlePinMmi(String dialString) {
        enforceModifyPermission();
        return (Boolean) sendRequest(CMD_HANDLE_PIN_MMI, dialString);
    }

    public void cancelMissedCallsNotification() {
        enforceModifyPermission();
        mApp.notificationMgr.cancelMissedCallNotification();
    }

    public int getCallState() {
        return DefaultPhoneNotifier.convertCallState(mPhone.getState());
    }

    public int getDataState() {
        return DefaultPhoneNotifier.convertDataState(mPhone.getDataConnectionState());
    }

    public int getDataActivity() {
        return DefaultPhoneNotifier.convertDataActivityState(mPhone.getDataActivityState());
    }

    public Bundle getCellLocation() {
        try {
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, null);
        } catch (SecurityException e) {
            // If we have ACCESS_FINE_LOCATION permission, skip the check for ACCESS_COARSE_LOCATION
            // A failure should throw the SecurityException from ACCESS_COARSE_LOCATION since this
            // is the weaker precondition
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION, null);
        }
        Bundle data = new Bundle();
        mPhone.getCellLocation().fillInNotifierBundle(data);
        return data;
    }

    public void enableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        mPhone.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        mPhone.disableLocationUpdates();
    }

    @SuppressWarnings("unchecked")
    public List<NeighboringCellInfo> getNeighboringCellInfo() {
        try {
            mApp.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION, null);
        } catch (SecurityException e) {
            // If we have ACCESS_FINE_LOCATION permission, skip the check
            // for ACCESS_COARSE_LOCATION
            // A failure should throw the SecurityException from
            // ACCESS_COARSE_LOCATION since this is the weaker precondition
            mApp.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, null);
        }

        ArrayList<NeighboringCellInfo> cells = null;

        try {
            cells = (ArrayList<NeighboringCellInfo>) sendRequest(
                    CMD_HANDLE_NEIGHBORING_CELL, null);
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "getNeighboringCellInfo " + e);
        }

        return (List <NeighboringCellInfo>) cells;
    }


    //
    // Internal helper methods.
    //

    /**
     * Make sure the caller has the READ_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceReadPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE, null);
    }

    /**
     * Make sure the caller has the MODIFY_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceModifyPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE, null);
    }

    /**
     * Make sure the caller has the CALL_PHONE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceCallPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE, null);
    }


    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        StringBuilder buf = new StringBuilder("tel:");
        buf.append(number);
        return buf.toString();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    public int getActivePhoneType() {
        return mPhone.getPhoneType();
    }

    /**
     * Returns the CDMA ERI icon index to display
     */
    public int getCdmaEriIconIndex() {
        return mPhone.getCdmaEriIconIndex();
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    public int getCdmaEriIconMode() {
        return mPhone.getCdmaEriIconMode();
    }

    /**
     * Returns the CDMA ERI text,
     */
    public String getCdmaEriText() {
        return mPhone.getCdmaEriText();
    }

    /**
     * Returns true if CDMA provisioning needs to run.
     */
    public boolean getCdmaNeedsProvisioning() {
        if (getActivePhoneType() == Phone.PHONE_TYPE_GSM) {
            return false;
        }

        boolean needsProvisioning = false;
        String cdmaMin = mPhone.getCdmaMin();
        try {
            needsProvisioning = OtaUtils.needsActivation(cdmaMin);
        } catch (IllegalArgumentException e) {
            // shouldn't get here unless hardware is misconfigured
            Log.e(LOG_TAG, "CDMA MIN string " + ((cdmaMin == null) ? "was null" : "was too short"));
        }
        return needsProvisioning;
    }

    /**
     * Returns the unread count of voicemails
     */
    public int getVoiceMessageCount() {
        return mPhone.getVoiceMessageCount();
    }

    /**
     * Returns the network type
     */
    public int getNetworkType() {
        int radiotech = mPhone.getServiceState().getRadioTechnology();
        switch(radiotech) {
            case ServiceState.RADIO_TECHNOLOGY_GPRS:
                return TelephonyManager.NETWORK_TYPE_GPRS;
            case ServiceState.RADIO_TECHNOLOGY_EDGE:
                return TelephonyManager.NETWORK_TYPE_EDGE;
            case ServiceState.RADIO_TECHNOLOGY_UMTS:
                return TelephonyManager.NETWORK_TYPE_UMTS;
            case ServiceState.RADIO_TECHNOLOGY_HSDPA:
                return TelephonyManager.NETWORK_TYPE_HSDPA;
            case ServiceState.RADIO_TECHNOLOGY_HSUPA:
                return TelephonyManager.NETWORK_TYPE_HSUPA;
            case ServiceState.RADIO_TECHNOLOGY_HSPA:
                return TelephonyManager.NETWORK_TYPE_HSPA;
            case ServiceState.RADIO_TECHNOLOGY_IS95A:
            case ServiceState.RADIO_TECHNOLOGY_IS95B:
                return TelephonyManager.NETWORK_TYPE_CDMA;
            case ServiceState.RADIO_TECHNOLOGY_1xRTT:
                return TelephonyManager.NETWORK_TYPE_1xRTT;
            case ServiceState.RADIO_TECHNOLOGY_EVDO_0:
                return TelephonyManager.NETWORK_TYPE_EVDO_0;
            case ServiceState.RADIO_TECHNOLOGY_EVDO_A:
                return TelephonyManager.NETWORK_TYPE_EVDO_A;
            case ServiceState.RADIO_TECHNOLOGY_EVDO_B:
                return TelephonyManager.NETWORK_TYPE_EVDO_B;
            default:
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        return mPhone.getIccCard().hasIccCard();
    }
    /**
     * @return true if a ICC card type is USIM/TD
     */
    public boolean isUsimCard() {
        return mPhone.getIccCard().isUsimCard();
    }
    /**
     * @return true if a IccFdn enabled
     */
    public boolean getIccFdnEnabled() {
        return mPhone.getIccCard().getIccFdnEnabled();
    }
    
    /**
     * Returns the array, String[0] - sres,String[1] - kc
     *
     * @hide
     */
    public String[] Mbbms_Gsm_Authenticate(String nonce) {
    	String authen = mPhone.Mbbms_Gsm_Authenticate(nonce);
    	Log.d(LOG_TAG, "Mbbms_Gsm_Authenticate return:"+authen);
    	if (authen !=  null) {
    		String [] rep = new String[2];
    		String temp;
    		int offset = authen.indexOf(',',0);
    		int str_begin, str_end;
    		str_begin = offset + 1;   		
            offset =  authen.indexOf(',',str_begin);
            if (authen.charAt(str_begin) == '\"') {
            	str_begin++;	
            }
            if ((offset > 1) && (authen.charAt(offset - 1) == '\"')) {
            	str_end = offset - 1;
            }
            else {
                str_end = offset;
            }
            temp = authen.substring(str_begin, str_end);
            if (authen.charAt(offset + 1) == '\"') {
                str_begin = offset + 1;
            }
            else {
            	str_begin = offset;
            }
            
            if (authen.charAt(authen.length() -1) == '\"'){
            	str_end = authen.length() -1;
            }
            else {
            	str_end = authen.length();
            }
            rep[0] = authen.substring(str_begin+1, str_end); 
            Log.d(LOG_TAG, "Mbbms_Gsm_Authenticate resp:"+rep[0]);
            rep[1] = temp;
            Log.d(LOG_TAG, "Mbbms_Gsm_Authenticate resp:"+rep[1]);
    		return rep;
    	}
    	else {
    	    return null;
    	}
    }
    /**
     * Returns the array, String[0], "1" -need GBA recynchronization, "0" - succeed
     * String[1] - res, String[2] -ck, String[3] - ik
     *
     * @hide
     */
    public String[] Mbbms_USim_Authenticate(String nonce, String autn) {
    	String authen = mPhone.Mbbms_USim_Authenticate(nonce, autn);
    	Log.d(LOG_TAG, "Mbbms_USim_Authenticate return:"+authen);
    	if (authen != null) {
    	    String [] rep = new String[4];
    	    int offset = 0;
    		int str_begin = 0, str_end = 0;
                //int testlen = authen1.indexOf(',',3);
                //String authen = authen1.substring(0, testlen);
                int len = authen.length();
                //Log.d(LOG_TAG, "Mbbms_USim_Authenticate return:"+authen);
    		for (int i = 0; i < 2; i++)  {
    			str_begin = offset;
    			offset =  authen.indexOf(',',offset);
    			str_end = offset;
    			if ((offset < 0) && (str_begin >= len)) break;
                        if (offset < 0){
                           offset = len;
                           str_end = len;
                        }
    			if (i == 1) {
    				if (authen.charAt(str_begin) == '\"'){
    					str_begin++;	
    				}
    				
    				if (authen.charAt(str_end -1) == '\"') {
    					str_end--;
    				}
    				rep[i] = authen.substring(str_begin, str_end);	
    			}
    			else {
    			    rep[i] = authen.substring(str_begin, str_end);
    			}
    			offset++;
    		}
    		str_begin = offset;
    		offset =  authen.indexOf(',',offset);
    		if (offset > 0) {

    		    if (authen.charAt(str_begin) == '\"'){
					str_begin++;	
				}
    		    if (authen.charAt(offset - 1) == '\"'){
        		    str_end = offset - 1;
    		    }
    		    else {
        		    str_end = offset;
    		    }
    		    	
    		    rep[2] = authen.substring(str_begin, str_end);
			    if (authen.charAt(offset + 1) == '\"') {
	                str_begin = offset + 1;
	            }
	            else {
	            	str_begin = offset;
	            }
	            
	            if (authen.charAt(authen.length() -1) == '\"'){
	            	str_end = authen.length() -1;
	            }
	            else {
	            	str_end = authen.length();
	            }
                rep[3] = authen.substring(str_begin+1, str_end);
                //rep[3] = temp;
    		}
    		else {
    			rep[2] = " ";
    			rep[3] = " ";
    		}
    	    return rep;
    	}
    	else {
            return null;
    	}
    }
    
    /**
     * Returns the type,0 --SIM,1 -- USIM
     *
     * @hide
     */
    public String getSimType() {
    	String type = mPhone.getSimType();
    	Log.d(LOG_TAG, "getSimType type:"+type); 
        return type;	
    }
    
    public String[] getRegistrationState() {
    	String[] state = mPhone.getRegistrationState();
    	Log.d(LOG_TAG, "getRegistrationState state:"+state.length); 
    	
        return state;	
   }
   
   public boolean isVTCall() {
	   boolean is_vtcall = mPhone.isVTCall();
    	Log.d(LOG_TAG, "isVTCall is_vtcall:"+is_vtcall); 
        return is_vtcall;	
   }

   public int getRemainTimes(int type) {
    	int count = mPhone.getRemainTimes(type);
    	Log.d(LOG_TAG, "getRemainTimes:"+count); 
    	
        return count;	
   }

   public  boolean setApnActivePdpFilter(String apntype,boolean filterenable)
   {
       Log.d(LOG_TAG, " setApnActivePdpFilter:"+apntype+filterenable); 
      return  mPhone.setApnActivePdpFilter(apntype,filterenable);

    }
    public boolean getApnActivePdpFilter(String apntype)
   {
       Log.d(LOG_TAG, " getApnActivePdpFilter:"+apntype); 
	return mPhone.getApnActivePdpFilter(apntype);
		
    }

    public String[] getActiveApnTypes()
    {
        return mPhone.getActiveApnTypes();
    }
    
    public String getActiveApn()
    {
        return mPhone.getActiveApn();
    }
}
