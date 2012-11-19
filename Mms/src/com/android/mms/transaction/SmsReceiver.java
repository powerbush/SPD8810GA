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

import com.android.mms.ui.NewContactDataReceivedActivity;
import com.android.mms.ui.NewContactReceivedActivity;
import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;
import com.android.mms.util.FeatureSwitch;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;
import android.util.Log;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;

/**
 * Handle incoming SMSes.  Just dispatches the work off to a Service.
 */
public class SmsReceiver extends BroadcastReceiver {
    static final Object mStartingServiceSync = new Object();
	private static final String TAG = "SmsReceiver";
    static PowerManager.WakeLock mStartingService;
    private static SmsReceiver sInstance;
	
	public static final String DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED";

    private static final int BLOCK_PHONE_MESSAGE = 0;
    private static final int BLOCK_PHONE = 1;
    private static final int BLOCK_MESSAGE = 2;
    private static final int PORT_DM_REG = 16998;
    private static final int PORT_VCARD_RECEIVE = 9204;

    public static SmsReceiver getInstance() {
        if (sInstance == null) {
            sInstance = new SmsReceiver();
        }
        return sInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        onReceiveWithPrivilege(context, intent, false);
    }

    protected void onReceiveWithPrivilege(Context context, Intent intent, boolean privileged) {
        // If 'privileged' is false, it means that the intent was delivered to the base
        // no-permissions receiver class.  If we get an SMS_RECEIVED message that way, it
        // means someone has tried to spoof the message by delivering it outside the normal
        // permission-checked route, so we just ignore it.
        if (!privileged && intent.getAction().equals(Intents.SMS_RECEIVED_ACTION)) {
            return;
        }
        
        if(intent.getAction().equals(DATA_SMS_RECEIVED_ACTION)){
        	Log.v(TAG, "android.intent.action.DATA_SMS_RECEIVED");
            Uri uri = intent.getData();
            Log.d(TAG,"DATA_SMS_RECEIVED uri:"+uri);
            if (PORT_DM_REG == uri.getPort()) {
                Log.d(TAG,"Not handle DM message");
                return;
            }else if(PORT_VCARD_RECEIVE == uri.getPort()){
//            	intent.setClass(context, NewContactReceivedActivity.class);
                intent.setClass(context, NewContactDataReceivedActivity.class);
            	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            	context.startActivity(intent);
            }
        	return;
        }

        String action = intent.getAction();
    	Log.i(TAG,"intent action:"+action);
        if (WAP_PUSH_RECEIVED_ACTION.equals(action)) {
            intent.setClass(context, SmsReceiverService.class);
            intent.putExtra("result", getResultCode());
            beginStartingService(context, intent);
            return;
        }
        
        intent.setClass(context, SmsReceiverService.class);
        intent.putExtra("result", getResultCode());
        beginStartingService(context, intent);
    }

    // N.B.: <code>beginStartingService</code> and
    // <code>finishStartingService</code> were copied from
    // <code>com.android.calendar.AlertReceiver</code>.  We should
    // factor them out or, even better, improve the API for starting
    // services under wake locks.

    /**
     * Start the service to process the current event notifications, acquiring
     * the wake lock before returning to ensure that the service will run.
     */
    public static void beginStartingService(Context context, Intent intent) {
        synchronized (mStartingServiceSync) {
            if (mStartingService == null) {
                PowerManager pm =
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "StartingAlertService");
                mStartingService.setReferenceCounted(false);
            }
            mStartingService.acquire();
            context.startService(intent);
        }
    }

    /**
     * Called back by the service when it has finished processing notifications,
     * releasing the wake lock if the service is now stopping.
     */
    public static void finishStartingService(Service service, int startId) {
        synchronized (mStartingServiceSync) {
            if (mStartingService != null) {
                if (service.stopSelfResult(startId)) {
                    mStartingService.release();
                }
            }
        }
    }
    
    public static boolean CheckIsBlockNumber(Context context, String str){
    	ContentResolver cr = context.getContentResolver();
		
		String mumber_value = new String();
		int block_type;
        String[] columns = new String[]{BlockColumns.BlockMumber.MUMBER_VALUE,
        								BlockColumns.BlockMumber.BLOCK_TYPE};

        Cursor cursor = cr.query(BlockColumns.BlockMumber.CONTENT_URI, columns, null, null, null);
        try{
            if (cursor != null && cursor.moveToFirst()) {
            	do{
            		mumber_value = cursor.getString(cursor.getColumnIndex(
            									BlockColumns.BlockMumber.MUMBER_VALUE));
            		block_type = cursor.getInt(cursor.getColumnIndex(
            									BlockColumns.BlockMumber.BLOCK_TYPE));
            		//if(str.trim().equals(mumber_value.trim())){
            		if(str.trim().contains(mumber_value.trim())){
                                       Log.v(TAG, "belong to block mumber");
        				if(block_type == BLOCK_PHONE_MESSAGE || block_type == BLOCK_MESSAGE){
                                               Log.v(TAG, "belong to block type");
        					return true;
        				}	
        			}
            	}while(cursor.moveToNext());
            }
        } catch (Exception e) {
        	// process exception
        } finally {
        	if(cursor != null)
        		cursor.close();
        }
        return false;
    }
    
    public static class BlockColumns {
    	public static final String AUTHORITY  = "com.android.providers.contacts.block";

        public static final class BlockMumber implements BaseColumns {
            public static final Uri CONTENT_URI  = Uri.parse("content://com.android.providers.contacts.block/block_mumbers");

            public static final String MUMBER_VALUE = "mumber_value";
            public static final String BLOCK_TYPE	= "block_type";
            public static final String NOTES		= "notes";
        }

        public static final class BlockRecorder implements BaseColumns {
            public static final Uri CONTENT_URI  = Uri.parse("content://com.android.providers.contacts.block/block_recorded");

            public static final String MUMBER_VALUE = "mumber_value";
            public static final String BLOCK_DATE = "block_date";
        }
    }
}
