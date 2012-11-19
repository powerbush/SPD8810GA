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

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.ui.MessageUtils;
import com.google.android.mms.util.PduCache;

/**
 * MmsSystemEventReceiver receives the
 * {@link android.content.intent.ACTION_BOOT_COMPLETED},
 * {@link com.android.internal.telephony.TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED}
 * and performs a series of operations which may include:
 * <ul>
 * <li>Show/hide the icon in notification area which is used to indicate
 * whether there is new incoming message.</li>
 * <li>Resend the MM's in the outbox.</li>
 * </ul>
 */
public class MmsSystemEventReceiver extends BroadcastReceiver {
    private static final String TAG = "MmsSystemEventReceiver";
    private static MmsSystemEventReceiver sMmsSystemEventReceiver;

    private Handler mHandler = new Handler();

    private static boolean isRegisterForConnectionStateChanges[] = {false,false};

    private static void wakeUpService(Context context, int phoneId) {
            Log.v(TAG, "wakeUpService: start transaction service ...");
        // TODO: Class name is depend on phoneId
        //cienet edit yuanman 2011-6-15:
        if (TelephonyManager.getPhoneCount() > 1 || MessageUtils.isMSMS){
            context.startService(new Intent(context, TransactionServiceHelper
                    .getTransactionServiceClass(phoneId)));
        } else {
            context.startService(new Intent(context, TransactionService.class));
        }
        //cienet end yuanman.
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "Intent received: " + intent);
        }

        String action = intent.getAction();
        if (action.equals(Mms.Intents.CONTENT_CHANGED_ACTION)) {
            Uri changed = (Uri) intent.getParcelableExtra(Mms.Intents.DELETED_CONTENTS);
            PduCache.getInstance().purge(changed);
        } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
            String state = intent.getStringExtra(Phone.STATE_KEY);
            int phoneId = intent.getIntExtra(Phone.PHONE_ID, 0);
            Log.v(TAG, "ANY_DATA_STATE event received: " + state+" , phone_id = "+phoneId);

            if (state.equals("CONNECTED")) {
                wakeUpService(context, phoneId);
            }
        } else if (action.equals("com.android.mms.MOBILE_DATA_ENABLED")) {
            int phoneId = intent.getIntExtra(Phone.PHONE_ID, 0);
            Log.v(TAG, "com.android.mms.MOBILE_DATA_ENABLED event received: phone_id = "+phoneId );
            wakeUpService(context, phoneId);
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            // We should check whether there are unread incoming
            // messages in the Inbox and then update the notification icon.
            // Called on the UI thread so don't block.
            MessagingNotification.nonBlockingUpdateNewMessageIndicator(context, false, false);
            updatePendingMms(context);
                if (TelephonyManager.getPhoneCount() > 1 || MessageUtils.isMSMS) {
                    context.startService(new Intent(context,
                            TransactionServiceHelper
                                    .getTransactionServiceClass(0)));
                    context.startService(new Intent(context,
                            TransactionServiceHelper
                                    .getTransactionServiceClass(1)));
                } else {
                    context.startService(new Intent(context,
                            TransactionService.class));
                }
        }

        //===== fixed CR<NEWMS00127040> by luning at 11-10-12 begin =====
        else if (intent.getAction().equals(PushReceiver.NOTIFY_SHOW_MMS_REPORT_ACTION)) {
            final String report = intent.getStringExtra("report");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, report, Toast.LENGTH_LONG).show();
                }
            });
	}
    }
    //add by beijing spreadst---start
    private boolean updatePendingMms(Context context){
        Uri.Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        String[] projection = new String[]{PendingMessages.ERROR_TYPE};
        String selection =PendingMessages.ERROR_TYPE+"= ?";
        String[] selectionArgs = new String[]{String.valueOf(MmsSms.NO_ERROR)};
        boolean shouldRetry = false;
        Cursor cursor = SqliteWrapper.query(context,context.getContentResolver(), uriBuilder.build(), projection, selection, selectionArgs, null);
        if(cursor != null){
	    if(cursor.getCount() > 0){
		try{
		    ContentValues values = new ContentValues();
		    values.put(PendingMessages.ERROR_TYPE, MmsSms.ERR_TYPE_GENERIC);
		    String where = PendingMessages.ERROR_TYPE +" = "+String.valueOf(MmsSms.NO_ERROR);
		    SqliteWrapper.update(context, context.getContentResolver(), uriBuilder.build(), values, where, null);
		    shouldRetry = true;
		}catch(SQLiteException e){
		    shouldRetry = false;
		}
		finally{
		    cursor.close();
		}
	    }
	}
	return shouldRetry;
    }

    public static void registerForConnectionStateChanges(Context context,int phoneId) {
        Log.v(TAG, "registerForConnectionStateChanges phoneId = "+phoneId);
        synchronized (isRegisterForConnectionStateChanges) {
            for (int i = 0; i < isRegisterForConnectionStateChanges.length; i++) {
                if(isRegisterForConnectionStateChanges[i] == true){
                    isRegisterForConnectionStateChanges[phoneId] = true;
                    return;
                }
            }
//        unRegisterForConnectionStateChanges(context,phoneId);
            isRegisterForConnectionStateChanges[phoneId] = true;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        IntentFilter intentFilter_data = new IntentFilter();
        intentFilter_data.addAction("com.android.mms.MOBILE_DATA_ENABLED");
        Log.v(TAG, "registerForConnectionStateChanges");
        if (sMmsSystemEventReceiver == null) {
            sMmsSystemEventReceiver = new MmsSystemEventReceiver();
        }

        context.registerReceiver(sMmsSystemEventReceiver, intentFilter);
        context.registerReceiver(sMmsSystemEventReceiver, intentFilter_data);
    }

    public static void unRegisterForConnectionStateChanges(Context context,int phoneId) {
        Log.v(TAG, "unRegisterForConnectionStateChanges phoneid = " + phoneId );
        synchronized (isRegisterForConnectionStateChanges) {
            isRegisterForConnectionStateChanges[phoneId] = false;
            for (int i = 0; i < isRegisterForConnectionStateChanges.length; i++) {
                if (isRegisterForConnectionStateChanges[i] == true)
                    return;
            }
        }
        if (sMmsSystemEventReceiver != null) {
            try {
                context.unregisterReceiver(sMmsSystemEventReceiver);
            } catch (IllegalArgumentException e) {
                // Allow un-matched register-unregister calls
            }
        }
    }
}
