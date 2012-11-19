/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
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

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gsm.TDPhone;

import android.content.Context;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Phone app module that listens for phone state changes and various other
 * events from the telephony layer, and triggers any resulting UI behavior
 * (like starting the Ringer and Incoming Call UI, playing in-call tones,
 * updating notifications, writing call log entries, etc.)
 */
public class MsmsCallNotifier extends CallNotifier {
    private static final String LOG_TAG = "MsmsCallNotifier";
    private static final boolean DBG =
            (PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneApp.DBG_LEVEL >= 2);

    //for call forward
    protected boolean needQueryCfuSub1 = true;
    protected boolean needQueryCfuSub2 = true;
    protected boolean needUpdateCfiSub1 = true;
    protected boolean needUpdateCfiSub2 = true;
    protected boolean mVoice1CfuVisible = false;
    protected boolean mVoice2CfuVisible = false;
    protected boolean mVideo1CfuVisible = false;
    protected boolean mVideo2CfuVisible = false;

    /**
     * Initialize the singleton CallNotifier instance.
     * This is only done once, at startup, from PhoneApp.onCreate().
     */
    /* package */ static MsmsCallNotifier init(PhoneApp app, Phone phone, Ringer ringer,
                                           BluetoothHandsfree btMgr, CallLogAsync callLog) {
        synchronized (MsmsCallNotifier.class) {
            if (sInstance == null) {
                sInstance = new MsmsCallNotifier(app, phone, ringer, btMgr, callLog);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return (MsmsCallNotifier) sInstance;
        }
    }

    /** Private constructor; @see init() */
    private MsmsCallNotifier(PhoneApp app, Phone phone, Ringer ringer,
                         BluetoothHandsfree btMgr, CallLogAsync callLog) {
        super(app, phone, ringer, btMgr, callLog);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case PHONE_MWI_CHANGED:
                Phone phone = (Phone)msg.obj;
                onMwiChanged(mApplication.phone.getMessageWaitingIndicator(), phone);
                break;
            default:
                 super.handleMessage(msg);
        }
    }

    protected void listenPhoneStateListerner() {
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            TelephonyManager telephonyManager = (TelephonyManager) mApplication.mContext
                    .getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, i));
            telephonyManager.listen(getPhoneStateListener(i),
                    PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR
                            | PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR
                            | PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    private void onMwiChanged(boolean visible, Phone phone) {
        if (VDBG) log("onMwiChanged(): " + visible);

        // "Voicemail" is meaningless on non-voice-capable devices,
        // so ignore MWI events.
//        if (!PhoneApp.sVoiceCapable) {
            // ...but still log a warning, since we shouldn't have gotten this
            // event in the first place!
            // (PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR events
            // *should* be blocked at the telephony layer on non-voice-capable
            // capable devices.)
//            Log.w(LOG_TAG, "Got onMwiChanged() on non-voice-capable device! Ignoring...");
//            return;
//        }

        ((MsmsNotificationMgr)mApplication.notificationMgr).updateMwi(visible, phone);
    }

    /**
     * Posts a delayed PHONE_MWI_CHANGED event, to schedule a "retry" for a
     * failed NotificationMgr.updateMwi() call.
     */
    /* package */
    void sendMwiChangedDelayed(long delayMillis, Phone phone) {
        Message message = Message.obtain(this, PHONE_MWI_CHANGED, phone);
        sendMessageDelayed(message, delayMillis);
    }

    protected void onCfiChanged(boolean visible, int subscription) {
        if (VDBG) log("onCfiChanged(): " + visible);
        ((MsmsNotificationMgr)mApplication.notificationMgr).updateCfi(visible, subscription);
    }

    protected void onCfiChanged(boolean visible, int serviceClass, int subscription) {
        if (VDBG) log("onCfiChanged(): " + visible + "serviceClass:" + serviceClass);
        if (0 == subscription) {
            if (CommandsInterface.SERVICE_CLASS_VOICE == serviceClass) {
                mVoice1CfuVisible = visible;
            }
            if (TDPhone.SERVICE_CLASS_VIDEO == serviceClass) {
                mVideo1CfuVisible = visible;
            }
        }
        if (1 == subscription) {
            if (CommandsInterface.SERVICE_CLASS_VOICE == serviceClass) {
                mVoice2CfuVisible = visible;
            }
            if (TDPhone.SERVICE_CLASS_VIDEO == serviceClass) {
                mVideo2CfuVisible = visible;
            }
        }
        ((MsmsNotificationMgr) mApplication.notificationMgr).updateCfi(visible, serviceClass, subscription);
    }

    private PhoneStateListener getPhoneStateListener(final int sub) {
        Log.d(LOG_TAG, "getPhoneStateListener: SUBSCRIPTION == " + sub);

        PhoneStateListener phoneStateListener = new PhoneStateListener() {
            @Override
            public void onMessageWaitingIndicatorChanged(boolean mwi) {
                // mSubscription is a data member of PhoneStateListener class.
                // Each subscription is associated with one PhoneStateListener.
                onMwiChanged(mwi, MsmsPhoneApp.getInstance().getPhone(sub));
            }

            @Override
            public void onCallForwardingIndicatorChangedByServiceClass(boolean cfi, int serviceClass) {
                onCfiChanged(cfi, serviceClass, sub);
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (!PhoneFactory.isCardExist(sub)) {
                    return;
                }
                switch (serviceState.getState()) {
                    case ServiceState.STATE_OUT_OF_SERVICE:
                    case ServiceState.STATE_POWER_OFF:
                        ((MsmsNotificationMgr) mApplication.notificationMgr).updateCfi(false,
                                CommandsInterface.SERVICE_CLASS_VOICE, sub);
                        if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
                            ((MsmsNotificationMgr) mApplication.notificationMgr).updateCfi(false,
                                    TDPhone.SERVICE_CLASS_VIDEO, sub);
                        }
                        needUpdateCfiSub1 = ((0 == sub) ? true :needQueryCfuSub1);
                        needUpdateCfiSub2 = ((1 == sub) ? true :needQueryCfuSub2);
                        break;
                    case ServiceState.STATE_IN_SERVICE:
                        if (SystemProperties.getInt("persist.sys.callforwarding", 1) == 1) {
                            if (0 == sub) {
                                if (needQueryCfuSub1) {
                                    log("(sub0) query call forward only this once");
                                    mApplication.getPhone(sub).getCallForwardingOption(
                                            CommandsInterface.CF_REASON_UNCONDITIONAL,
                                            CommandsInterface.SERVICE_CLASS_VOICE, null);
                                    // only sub0 need query video cfu.
                                    if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
                                        mApplication.getPhone(sub).getCallForwardingOption(
                                                CommandsInterface.CF_REASON_UNCONDITIONAL,
                                                TDPhone.SERVICE_CLASS_VIDEO, null);
                                    }
                                    needQueryCfuSub1 = false;
                                } else if (needUpdateCfiSub1) {
                                    ((MsmsNotificationMgr) mApplication.notificationMgr).updateCfi(
                                            mVoice1CfuVisible,
                                            CommandsInterface.SERVICE_CLASS_VOICE, sub);
                                    if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
                                        ((MsmsNotificationMgr) mApplication.notificationMgr)
                                                .updateCfi(mVideo1CfuVisible,
                                                        TDPhone.SERVICE_CLASS_VIDEO, sub);
                                    }
                                    needUpdateCfiSub1 = false;
                                }
                            }
                            if (1 == sub) {
                                if (needQueryCfuSub2) {
                                    log("(sub1) query call forward only this once");
                                    mApplication.getPhone(sub).getCallForwardingOption(
                                            CommandsInterface.CF_REASON_UNCONDITIONAL,
                                            CommandsInterface.SERVICE_CLASS_VOICE, null);
                                    if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
                                        mApplication.getPhone(sub).getCallForwardingOption(
                                                CommandsInterface.CF_REASON_UNCONDITIONAL,
                                                TDPhone.SERVICE_CLASS_VIDEO, null);
                                    }
                                    needQueryCfuSub2 = false;
                                } else if (needUpdateCfiSub2) {
                                    ((MsmsNotificationMgr) mApplication.notificationMgr).updateCfi(
                                            mVoice2CfuVisible,
                                            CommandsInterface.SERVICE_CLASS_VOICE, sub);
                                    if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
                                        ((MsmsNotificationMgr) mApplication.notificationMgr)
                                                .updateCfi(mVideo2CfuVisible,
                                                        TDPhone.SERVICE_CLASS_VIDEO, sub);
                                    }
                                    needUpdateCfiSub2 = false;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        return phoneStateListener;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
