/*
 * Copyright (C) 2008 Esmertec AG.
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

package com.android.mms.util;

import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduPersister;
import android.database.sqlite.SqliteWrapper;

import com.android.internal.telephony.IccCardApplication;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.Phone;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Telephony.Mms;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Config;
import android.util.Log;
import android.widget.Toast;

import android.os.SystemProperties;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private static final int DEFERRED_MASK           = 0x04;

    public static final int STATE_UNSTARTED         = 0x80;
    public static final int STATE_DOWNLOADING       = 0x81;
    public static final int STATE_TRANSIENT_FAILURE = 0x82;
    public static final int STATE_PERMANENT_FAILURE = 0x87;
    //luning
    public static final int STATE_LOW_MEMORY = -1;
    
    

    private final Context mContext;
    private final Handler mHandler;
    private final SharedPreferences mPreferences;
    private boolean[] mAutoDownload;

    private final OnSharedPreferenceChangeListener mPreferencesChangeListener =
        new OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (MessagingPreferenceActivity.AUTO_RETRIEVAL.equals(key)
                    || MessagingPreferenceActivity.RETRIEVAL_DURING_ROAMING.equals(key)) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Preferences updated.");
                }

                synchronized (sInstance) {
                	for(int i =0; i < mAutoDownload.length; i++){
	                    mAutoDownload[i] = getAutoDownloadState(prefs, i);
	                    if (LOCAL_LOGV) {
	                        Log.v(TAG, "mAutoDownload[" + i + "]------> " + mAutoDownload[i]);
	                    }
                	}
                }
            }
        }
    };

    private final BroadcastReceiver mRoamingStateListener =
        new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Service state changed: " + intent.getExtras());
                }
                int phoneId = intent.getIntExtra(Phone.PHONE_ID, 0);

                ServiceState state = ServiceState.newFromBundle(intent.getExtras());
                boolean isRoaming = state.getRoaming();
                if (LOCAL_LOGV) {
                    Log.v(TAG, "roaming ------> " + isRoaming);
                }
                synchronized (sInstance) {
                    mAutoDownload[phoneId] = getAutoDownloadState(mPreferences, isRoaming);
                    if (LOCAL_LOGV) {
                    	Log.v(TAG, "mAutoDownload[" + phoneId + "]------> " + mAutoDownload[phoneId]);
                    }
                }
            }
        }
    };

    private static DownloadManager sInstance;

    private DownloadManager(Context context) {
        mContext = context;
        mHandler = new Handler();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferencesChangeListener);

        context.registerReceiver(
                mRoamingStateListener,
                new IntentFilter(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED));

        int phoneCount = PhoneFactory.getPhoneCount();
        mAutoDownload = new boolean[phoneCount];
        for(int i = 0; i < phoneCount; i++){
	        mAutoDownload[i] = getAutoDownloadState(mPreferences,i);
	        if (LOCAL_LOGV) {
	            Log.v(TAG, "mAutoDownload[" + i + "]------> " + mAutoDownload[i]);
	        }
        }
    }

    public boolean isAuto(int phoneId) {
        return mAutoDownload[phoneId];
    }

    public static void init(Context context) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "DownloadManager.init()");
        }

        if (sInstance != null) {
            Log.w(TAG, "Already initialized.");
        }
        sInstance = new DownloadManager(context);
    }

    public static DownloadManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("Uninitialized.");
        }
        return sInstance;
    }

    static boolean getAutoDownloadState(SharedPreferences prefs, int phoneId) {
        return getAutoDownloadState(prefs, isRoaming(phoneId));
    }

    static boolean getAutoDownloadState(SharedPreferences prefs, boolean roaming) {
        boolean autoDownload = prefs.getBoolean(
                MessagingPreferenceActivity.AUTO_RETRIEVAL, true);

        if (LOCAL_LOGV) {
            Log.v(TAG, "auto download without roaming -> " + autoDownload);
        }

        if (autoDownload) {
            boolean alwaysAuto = prefs.getBoolean(
                    MessagingPreferenceActivity.RETRIEVAL_DURING_ROAMING, false);

            if (LOCAL_LOGV) {
                Log.v(TAG, "auto download during roaming -> " + alwaysAuto);
            }

            if (!roaming || alwaysAuto) {
                return true;
            }
        }
        return false;
    }

    static boolean isRoaming(int phoneId) {
        // TODO: fix and put in Telephony layer
        String roaming = SystemProperties.get(
                PhoneFactory.getProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING, phoneId), null);
        if (LOCAL_LOGV) {
            Log.v(TAG, "roaming ------> " + roaming);
        }
        return "true".equals(roaming);
    }

    public void markState(final Uri uri, int state, int phoneId) {
        if(state == STATE_LOW_MEMORY){
            //change state
            state = STATE_UNSTARTED;
            //show toast
            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, R.string.low_storage_toast,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
        // Notify user if the message has expired.
        try {
            NotificationInd nInd = (NotificationInd) PduPersister.getPduPersister(mContext)
                    .load(uri);
            if ((nInd.getExpiry() < System.currentTimeMillis()/1000L)
                && (state == STATE_DOWNLOADING)) {
                mHandler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(mContext, R.string.dl_expired_notification,
                                Toast.LENGTH_LONG).show();
                    }
                });
                SqliteWrapper.delete(mContext, mContext.getContentResolver(), uri, null, null);
                return;
            }
        } catch(MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            return;
        }

        // Notify user if downloading permanently failed.
        if (state == STATE_PERMANENT_FAILURE) {
            mHandler.post(new Runnable() {
                public void run() {
                    try {
                        Toast.makeText(mContext, getMessage(uri),
                                Toast.LENGTH_LONG).show();
                    } catch (MmsException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            });
        } else if (!mAutoDownload[phoneId]) {
            state |= DEFERRED_MASK;
        }

        // Use the STATUS field to store the state of downloading process
        // because it's useless for M-Notification.ind.
        ContentValues values = new ContentValues(1);
        values.put(Mms.STATUS, state);
        SqliteWrapper.update(mContext, mContext.getContentResolver(),
                    uri, values, null, null);
    }

    public void showErrorCodeToast(int errorStr) {
        final int errStr = errorStr;
        mHandler.post(new Runnable() {
            public void run() {
                try {
                    Toast.makeText(mContext, errStr, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG,"Caught an exception in showErrorCodeToast");
                }
            }
        });
    }

    private String getMessage(Uri uri) throws MmsException {
        NotificationInd ind = (NotificationInd) PduPersister
                .getPduPersister(mContext).load(uri);

        EncodedStringValue v = ind.getSubject();
        String subject = (v != null) ? v.getString()
                : mContext.getString(R.string.no_subject);

        v = ind.getFrom();
        String from = (v != null)
                ? Contact.get(v.getString(), false).getName()
                : mContext.getString(R.string.unknown_sender);

        return mContext.getString(R.string.dl_failure_notification, subject, from);
    }

    public int getState(Uri uri) {
        Cursor cursor = SqliteWrapper.query(mContext, mContext.getContentResolver(),
                            uri, new String[] {Mms.STATUS}, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0) &~ DEFERRED_MASK;
                }
            } finally {
                cursor.close();
            }
        }
        return STATE_UNSTARTED;
    }

    public String getNetworkType(){
        String networktype_str = "g";
        int networktype = TelephonyManager.getDefault().getNetworkType();
        switch(networktype) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                networktype_str = "e";
                break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
                networktype_str = "e";
                break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPA:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
                networktype_str = "1x";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                networktype_str = "3g";
                break;
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                networktype_str = "1x";
                break;
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            default:
                networktype_str = "g";
                break;
        }
        return networktype_str;
    }

    public boolean checkMmsSizeLimit(long size){
        //0 --SIM，1 -- USIM, 20120131
//        String simttype = TelephonyManager.getDefault().getSimType();
        boolean isBeyondLimit = false;
        if(size > MmsConfig.getMaxMessageSize()) {
            isBeyondLimit = true;
        }
        return isBeyondLimit;
    }
    public boolean checkPduTotalSizeLimit(long size){
        //0 --SIM，1 -- USIM, 20120131
//        String simttype = TelephonyManager.getDefault().getSimType();
        boolean isBeyondLimit = false;
        if(size > MmsConfig.getPduMaxTotalSize()) {
            isBeyondLimit = true;
        }
        return isBeyondLimit;
    }
}
