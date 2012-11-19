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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.gsm.TDPhone;

import static com.android.internal.telephony.MsmsConstants.SUBSCRIPTION_KEY;

/**
 * NotificationManager-related utility code for the Phone app.
 *
 * This is a singleton object which acts as the interface to the
 * framework's NotificationManager, and is used to display status bar
 * icons and control other status bar-related behavior.
 *
 * @see PhoneApp.notificationMgr
 */
public class MsmsNotificationMgr extends NotificationMgr {
    private static final String LOG_TAG = "MsmsNotificationMgr";
	private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 1)
			&& (SystemProperties.getInt("ro.debuggable", 0) == 1);

    static final int VOICEMAIL_NOTIFICATION_SUB2 = 20;
    static final int CALL_FORWARD_NOTIFICATION_SUB2 = 21;
    static final int VIDEO_CALL_FORWARD_NOTIFICATION_SUB2 = 22;

    /**
     * Private constructor (this is a singleton).
     * @see init()
     */
    private MsmsNotificationMgr(PhoneApp app) {
        super(app);
    }

    /**
     * Initialize the singleton NotificationMgr instance.
     *
     * This is only done once, at startup, from PhoneApp.onCreate().
     * From then on, the NotificationMgr instance is available via the
     * PhoneApp's public "notificationMgr" field, which is why there's no
     * getInstance() method here.
     */
    /* package */ static MsmsNotificationMgr init(PhoneApp app) {
        synchronized (MsmsNotificationMgr.class) {
            if (sInstance == null) {
                sInstance = new MsmsNotificationMgr(app);
                // Update the notifications that need to be touched at startup.
                sInstance.updateNotificationsAtStartup();
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return (MsmsNotificationMgr) sInstance;
        }
    }

    /**
     * Updates the message waiting indicator (voicemail) notification.
     *
     * @param visible true if there are messages waiting
     */
    /* package */
    void updateMwi(boolean visible, Phone phone) {
        if (DBG) log("updateMwi(): " + visible);
		if (phone == null) {
			if (DBG)
				log("updateMwi(): phone is null.");
			return;
		}
        int subscription = phone.getPhoneId();
        if (visible) {
            int resId = android.R.drawable.stat_notify_voicemail;
            int[] iconId = {android.R.drawable.stat_notify_voicemail,
                   android.R.drawable.stat_notify_voicemail};

            // This Notification can get a lot fancier once we have more
            // information about the current voicemail messages.
            // (For example, the current voicemail system can't tell
            // us the caller-id or timestamp of a message, or tell us the
            // message count.)

            // But for now, the UI is ultra-simple: if the MWI indication
            // is supposed to be visible, just show a single generic
            // notification.

            String notificationTitle = mContext.getString(R.string.notification_voicemail_title);
            String vmNumber = phone.getVoiceMailNumber();
            if (DBG) log("- got vm number: '" + vmNumber + "'"
                             + " on Subscription: " + subscription);
                resId = iconId[subscription];

            // Watch out: vmNumber may be null, for two possible reasons:
            //
            //   (1) This phone really has no voicemail number
            //
            //   (2) This phone *does* have a voicemail number, but
            //       the SIM isn't ready yet.
            //
            // Case (2) *does* happen in practice if you have voicemail
            // messages when the device first boots: we get an MWI
            // notification as soon as we register on the network, but the
            // SIM hasn't finished loading yet.
            //
            // So handle case (2) by retrying the lookup after a short
            // delay.

            if ((vmNumber == null) && !phone.getIccRecordsLoaded()) {
                if (DBG) log("- Null vm number: SIM records not loaded (yet)...");

                // TODO: rather than retrying after an arbitrary delay, it
                // would be cleaner to instead just wait for a
                // SIM_RECORDS_LOADED notification.
                // (Unfortunately right now there's no convenient way to
                // get that notification in phone app code.  We'd first
                // want to add a call like registerForSimRecordsLoaded()
                // to Phone.java and GSMPhone.java, and *then* we could
                // listen for that in the CallNotifier class.)

                // Limit the number of retries (in case the SIM is broken
                // or missing and can *never* load successfully.)
                if (mVmNumberRetriesRemaining-- > 0) {
                    if (DBG) log("  - Retrying in " + VM_NUMBER_RETRY_DELAY_MILLIS + " msec...");
                    ((MsmsCallNotifier)PhoneApp.getInstance().notifier).sendMwiChangedDelayed(
                            VM_NUMBER_RETRY_DELAY_MILLIS, phone);
                    return;
                } else {
                    Log.w(LOG_TAG, "NotificationMgr.updateMwi: getVoiceMailNumber() failed after "
                          + MAX_VM_NUMBER_RETRIES + " retries; giving up.");
                    // ...and continue with vmNumber==null, just as if the
                    // phone had no VM number set up in the first place.
                }
            }

            if (TelephonyCapabilities.supportsVoiceMessageCount(phone)) {
                int vmCount = phone.getVoiceMessageCount();
                String titleFormat = mContext.getString(
                        R.string.notification_voicemail_title_count);
                notificationTitle = String.format(titleFormat, vmCount);
            }

            String notificationText;
            if (TextUtils.isEmpty(vmNumber)) {
                notificationText = mContext.getString(
                        R.string.notification_voicemail_no_vm_number);
            } else {
                notificationText = String.format(
                        mContext.getString(R.string.notification_voicemail_text_format),
                        PhoneNumberUtils.formatNumber(vmNumber));
            }

            Intent intent = new Intent(Intent.ACTION_CALL,
                    Uri.fromParts("voicemail", "", null));
            intent.putExtra(SUBSCRIPTION_KEY, subscription);
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

            Notification notification = new Notification(
                    resId,  // icon
                    null, // tickerText
                    System.currentTimeMillis()  // Show the time the MWI notification came in,
                                                // since we don't know the actual time of the
                                                // most recent voicemail message
                    );
            notification.setLatestEventInfo(
                    mContext,  // context
                    notificationTitle,  // contentTitle
                    notificationText,  // contentText
                    pendingIntent  // contentIntent
                    );
            notification.defaults |= Notification.DEFAULT_SOUND;
            notification.flags |= Notification.FLAG_NO_CLEAR;
            configureLedNotification(notification);
            mNotificationManager.notify(VOICEMAIL_NOTIFICATION, notification);
        } else {
            mNotificationManager.cancel(VOICEMAIL_NOTIFICATION);
        }
    }

    /**
     * Updates the message call forwarding indicator notification.
     *
     * @param visible true if there are messages waiting
     */
    /* package */ void updateCfi(boolean visible, int subscription) {
        updateCfi(visible, CommandsInterface.SERVICE_CLASS_VOICE, subscription);
    }

    /**
     * updateCfi by serviceClass
     * @param visible
     * @param serviceClass
     * @param subscription
     */
    void updateCfi(boolean visible, int serviceClass, int subscription) {
        log("updateCfi(): " + visible + ";serviceClass:" + serviceClass + ";sub = " + subscription);
        int notificationId = (subscription == 0) ? CALL_FORWARD_NOTIFICATION :
            CALL_FORWARD_NOTIFICATION_SUB2;
        int[] callfwdIcon = {R.drawable.stat_sys_phone_call_forward1, R.drawable.stat_sys_phone_call_forward2};
        int iconId = callfwdIcon[subscription];
        String intentClassName = "com.android.phone.GsmUmtsCallForwardOptions";
        int simStringId = (subscription == 0) ? R.string.sim1 : R.string.sim2;
        String expandedTitle = mContext.getString(simStringId) + mContext.getString(R.string.labelCF);
        String expandedText = mContext.getString(simStringId) + mContext.getString(R.string.sum_cfu_enabled_indicator);
        //for video call forwarding
        if (TDPhone.SERVICE_CLASS_VIDEO == serviceClass) {
			notificationId = (subscription == 0) ? VIDEO_CALL_FORWARD_NOTIFICATION
					: VIDEO_CALL_FORWARD_NOTIFICATION_SUB2;
			int[] videoCallFwdIcon = {
					R.drawable.stat_sys_phone_video_call_forward1,
					R.drawable.stat_sys_phone_video_call_forward2};
            iconId = videoCallFwdIcon[subscription];
            intentClassName = "com.android.phone.VideoPhoneCallForwardOptions";
            expandedTitle = mContext.getString(simStringId) + mContext.getString(R.string.videophone_callforward_setting_title);
            expandedText = mContext.getString(simStringId) + mContext.getString(R.string.videophone_setting) + mContext.getString(R.string.sum_cfu_enabled_indicator);
        }
        if (visible) {
            // If Unconditional Call Forwarding (forward all calls) for VOICE
            // is enabled, just show a notification.  We'll default to expanded
            // view for now, so the there is less confusion about the icon.  If
            // it is deemed too weird to have CF indications as expanded views,
            // then we'll flip the flag back.

            // TODO: We may want to take a look to see if the notification can
            // display the target to forward calls to.  This will require some
            // effort though, since there are multiple layers of messages that
            // will need to propagate that information.

            Notification notification;
            final boolean showExpandedNotification = true;
            if (showExpandedNotification) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
//                intent.setClassName("com.android.phone", intentClassName); //don't use this
                intent.setComponent(new ComponentName("com.android.phone", intentClassName));
                intent.putExtra(CallSettingOptions.SUB_ID, subscription);

                notification = new Notification(
                        iconId,  // icon
                        null, // tickerText
                        0); // The "timestamp" of this notification is meaningless;
                            // we only care about whether CFI is currently on or not.
                notification.setLatestEventInfo(
                        mContext, // context
                        expandedTitle, // expandedTitle
                        expandedText, // expandedText
                        PendingIntent.getActivity(mContext, subscription, intent, PendingIntent.FLAG_UPDATE_CURRENT)); // contentIntent
            } else {
                notification = new Notification(
                        iconId,  // icon
                        null,  // tickerText
                        System.currentTimeMillis()  // when
                        );
            }

            notification.flags |= Notification.FLAG_ONGOING_EVENT;  // also implies FLAG_NO_CLEAR

            mNotificationManager.notify(
                    notificationId,
                    notification);
        } else {
            mNotificationManager.cancel(notificationId);
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
