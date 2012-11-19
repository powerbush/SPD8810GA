/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.transaction;

import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;

import com.android.mms.ui.ConfirmOTAActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Config;
import android.util.Log;

/**
 * Receives Intent.WAP_PUSH_RECEIVED_ACTION intents
 */
public class OtaReceiver extends BroadcastReceiver {
    private static final String TAG = "OtaReceiver";

    private static final boolean DEBUG = true;

    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(WAP_PUSH_RECEIVED_ACTION)) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Received PUSH Intent: " + intent);
            }

            // Hold a wake lock for 5 seconds, enough to give any
            // services we start time to take their own wake locks.
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MMS PushReceiver");
            wl.acquire(5000);
            intent.setClass(context, ConfirmOTAActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

}
