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

//Modify by huibin
//import com.android.common.NetworkConnectivityListener;
import com.android.mms.R;
import com.android.mms.LogTag;
import com.android.mms.util.RateController;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.android.internal.telephony.Phone;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.common.NetworkConnectivityListener;
import com.android.internal.telephony.DataCallState;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.util.RateController;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.util.SqliteWrapper;

import android.content.IntentFilter;

/**
 * The TransactionService of the MMS Client is responsible for handling requests
 * to initiate client-transactions sent from:
 * <ul>
 * <li>The Proxy-Relay (Through Push messages)</li>
 * <li>The composer/viewer activities of the MMS Client (Through intents)</li>
 * </ul>
 * The TransactionService runs locally in the same process as the application.
 * It contains a HandlerThread to which messages are posted from the
 * intent-receivers of this application.
 * <p/>
 * <b>IMPORTANT</b>: This is currently the only instance in the system in
 * which simultaneous connectivity to both the mobile data network and
 * a Wi-Fi network is allowed. This makes the code for handling network
 * connectivity somewhat different than it is in other applications. In
 * particular, we want to be able to send or receive MMS messages when
 * a Wi-Fi connection is active (which implies that there is no connection
 * to the mobile data network). This has two main consequences:
 * <ul>
 * <li>Testing for current network connectivity ({@link android.net.NetworkInfo#isConnected()} is
 * not sufficient. Instead, the correct test is for network availability
 * ({@link android.net.NetworkInfo#isAvailable()}).</li>
 * <li>If the mobile data network is not in the connected state, but it is available,
 * we must initiate setup of the mobile data connection, and defer handling
 * the MMS transaction until the connection is established.</li>
 * </ul>
 */
public class TransactionService extends Service implements Observer {
    private String TAG = "TransactionService";
    private static final boolean DEBUG = true;

    /**
     * Used to identify notification intents broadcasted by the
     * TransactionService when a Transaction is completed.
     */
    public static final String TRANSACTION_COMPLETED_ACTION =
            "android.intent.action.TRANSACTION_COMPLETED_ACTION";

    /**
     * Action for the Intent which is sent by Alarm service to launch
     * TransactionService.
     */
    public static final String ACTION_ONALARM = "android.intent.action.ACTION_ONALARM";

    /**
     * Used as extra key in notification intents broadcasted by the TransactionService
     * when a Transaction is completed (TRANSACTION_COMPLETED_ACTION intents).
     * Allowed values for this key are: TransactionState.INITIALIZED,
     * TransactionState.SUCCESS, TransactionState.FAILED.
     */
    public static final String STATE = "state";

    /**
     * Used as extra key in notification intents broadcasted by the TransactionService
     * when a Transaction is completed (TRANSACTION_COMPLETED_ACTION intents).
     * Allowed values for this key are any valid content uri.
     */
    public static final String STATE_URI = "uri";

    private static final int EVENT_TRANSACTION_REQUEST = 1;
    private static final int EVENT_DATA_STATE_CHANGED = 2;
    private static final int EVENT_CONTINUE_MMS_CONNECTIVITY = 3;
    private static final int EVENT_HANDLE_NEXT_PENDING_TRANSACTION = 4;
    private static final int EVENT_QUIT = 100;

    private static final int TOAST_MSG_QUEUED = 1;
    private static final int TOAST_DOWNLOAD_LATER = 2;
//    public static final int TOAST_SENDING_FAILED = 3;/*delete for CR<NEWMS00132656> by luning at 2011.11.15*/
    private static final int TOAST_APN_CONFIG_ERROR = 4;
    private static final int TOAST_NONE = -1;

    // How often to extend the use of the MMS APN while a transaction
    // is still being processed.
    private static final int APN_EXTENSION_WAIT = 30 * 1000;
    private static final int APN_TIMEOUT_WAIT = 5 * 60 * 1000;

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private final ArrayList<Transaction> mProcessing  = new ArrayList<Transaction>();
    private final ArrayList<Transaction> mPending  = new ArrayList<Transaction>();
    private ConnectivityManager mConnMgr;

    //Modify by huibin
    //private NetworkConnectivityListener mConnectivityListener;
	private ConnectivityBroadcastReceiver mReceiver;
    private PowerManager.WakeLock mWakeLock;
    // corresponding to class name
    private int mPhoneId = 0;//TODO:wether the mPhoneId's value is 0 nor -1

	enum SwitchState{
		NA,
		CONNECTING,
		CONNECTED,
		PENDING_SWITCH,
	}
	//cienet end yuanman.

	private SwitchState mPendingSwitch = SwitchState.NA;
    public Handler mToastHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String str = null;

            if (msg.what == TOAST_MSG_QUEUED) {
                str = getString(R.string.message_queued);
            } else if (msg.what == TOAST_DOWNLOAD_LATER) {
                str = getString(R.string.download_later);
            }
            /*delete for CR<NEWMS00132656> by luning at 2011.11.15*/
//            else if (msg.what == TOAST_SENDING_FAILED) {
//                str = getString(R.string.sending_failed);
//            }

            if (str != null) {
            Toast.makeText(TransactionService.this, str,
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "onCreate() Creating TransactionService");
        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread("TransactionService");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        //Modify by huibin
        //mConnectivityListener = new NetworkConnectivityListener();
		//mConnectivityListener.registerHandler(mServiceHandler, EVENT_DATA_STATE_CHANGED);
		//mConnectivityListener.startListening(this);

		mReceiver = new ConnectivityBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }
        mConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean noNetwork = !isNetworkAvailable();

        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "onStart: #" + startId + ": " + intent.getExtras() + " intent=" + intent);
            Log.v(TAG, "    networkAvailable=" + !noNetwork);
        }

        if (ACTION_ONALARM.equals(intent.getAction()) || (intent.getExtras() == null)) {
            // Scan database to find all pending operations.
            Cursor cursor = PduPersister.getPduPersister(this).getPendingMessages(
                    System.currentTimeMillis(), mPhoneId);
            if (cursor != null) {
                try {
                    int count = cursor.getCount();

                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "onStart: cursor.count=" + count);
                    }

                    if (count == 0) {
                        Log.i(TAG, "onStart: no pending messages. Stopping service.");
                        RetryScheduler.setRetryAlarm(this, mPhoneId);
                        stopSelfIfIdle(startId);
                        return Service.START_NOT_STICKY;
                    }

                    int columnIndexOfMsgId = cursor.getColumnIndexOrThrow(PendingMessages.MSG_ID);
                    int columnIndexOfMsgType = cursor.getColumnIndexOrThrow(
                            PendingMessages.MSG_TYPE);
                    int columnIndexOfPhoneId = cursor.getColumnIndexOrThrow(PendingMessages.PHONE_ID);

                    if (noNetwork) {
                        // Make sure we register for connection state changes.
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "onStart: registerForConnectionStateChanges");
                        }
                        MmsSystemEventReceiver.registerForConnectionStateChanges(
                                getApplicationContext(),mPhoneId);
                    }

                    while (cursor.moveToNext()) {
                        int msgType = cursor.getInt(columnIndexOfMsgType);
                        int transactionType = getTransactionType(msgType);

                        // zhanglj add begin
                        int phoneId = cursor.getInt(columnIndexOfPhoneId);
                        long msgId = cursor.getLong(columnIndexOfMsgId);
                        if (transactionType == Transaction.NOTIFICATION_TRANSACTION ) {
                            Log.d(TAG,"onStart: the msgId:"+msgId+" type=NOTIFICATION_TRANSACTION process next transaction..");
                            continue;
                        }
                        Log.v(TAG, "onStart: pending meassage. id:" + msgId);
                        if (noNetwork) {
                            onNetworkUnavailable(startId, transactionType);
                            return Service.START_NOT_STICKY;
                        }
                        switch (transactionType) {
                            case -1:
                                break;
                            case Transaction.RETRIEVE_TRANSACTION:
                                // If it's a transiently failed transaction,
                                // we should retry it in spite of current
                                // downloading mode.
                                int failureType = cursor.getInt(
                                        cursor.getColumnIndexOrThrow(
                                                PendingMessages.ERROR_TYPE));
                                if (!isTransientFailure(failureType)) {
                                    break;
                                }
                                // fall-through
                            default:
                                Uri uri = ContentUris.withAppendedId(
                                        Mms.CONTENT_URI,
                                        cursor.getLong(columnIndexOfMsgId));
                                TransactionBundle args = new TransactionBundle(
                                        transactionType, uri.toString(), phoneId);
                                int transType = args.getTransactionType();
                                if ( transType == Transaction.RETRIEVE_TRANSACTION ) {
                                    Log.d(TAG,"transaction type is RETRIEVE_TRANSACTION, move pending message type to retrieve");
                                    TransactionService.moveMessageType2Retreve(Uri.parse(args.getUri()), this);
                                }
                                // FIXME: We use the same startId for all MMs.
                                launchTransaction(startId, args, false);
                                break;
                        }
                    }
                } finally {
                    cursor.close();
                }
            } else {
                Log.i(TAG, "onStart: no pending messages. Stopping service.");
                RetryScheduler.setRetryAlarm(this, mPhoneId);
                stopSelfIfIdle(startId);
            }
        } else {
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "onStart: launch transaction...");
            }
            // For launching NotificationTransaction and test purpose.
            TransactionBundle args = new TransactionBundle(intent.getExtras());
            launchTransaction(startId, args, noNetwork);
        }
        return Service.START_NOT_STICKY;
    }
    static public void moveMessageType2Retreve(Uri uri, Context context) {
        long msgId = ContentUris.parseId(uri);
        Uri.Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        ContentValues values = new ContentValues();
        values.put(PendingMessages.MSG_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);

        SqliteWrapper.update(context, context.getContentResolver(),
                uriBuilder.build(), values, PendingMessages.MSG_ID+"="+msgId, null);
    }
    //cienet add yuanman 2011-6-17:
	private int getMMSConnected() {
		int phoneId = -1 ;

		for ( int i=0; i< TelephonyManager.getPhoneCount() ; i++){

			if ( isMMSConnected(i)) {
				phoneId = i;
				break;
			}

		}

		Log.i(TAG, "MMS Connected :" + phoneId);
		return phoneId;
	}
	//cienet end yuanman.

    private void stopSelfIfIdle(int startId) {
        synchronized (mProcessing) {
            if (mProcessing.isEmpty() && mPending.isEmpty()) {
                if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "stopSelfIfIdle: STOP! unRegisterForConnectionStateChanges.");
                }
                // Make sure we're no longer listening for connection state changes.
                MmsSystemEventReceiver.unRegisterForConnectionStateChanges(getApplicationContext(),mPhoneId);
                stopSelf(startId);
            }
        }
    }

    private static boolean isTransientFailure(int type) {
        return (type < MmsSms.ERR_TYPE_GENERIC_PERMANENT) && (type > MmsSms.NO_ERROR);
    }

    private boolean isNetworkAvailable() {
	NetworkInfo info=mConnMgr.getNetworkInfo(ConnectivityManager.getMmsTypeByPhoneId(mPhoneId));
	if (info==null) {
	    return false;
	}
        return info.isAvailable();
    }

    //cienet add yuanman 2011-6-17:
    /**
     * Get the Connection state of the given phone subscription's MMS
     * @param phoneId
     * @return whether it is connected or connecting
     */
    private boolean isMMSConnected(int phoneId) {
        return mConnMgr.getNetworkInfo(ConnectivityManager.getMmsTypeByPhoneId(mPhoneId)).isConnectedOrConnecting();
    }
    //cienet end yuanman.

    private int getTransactionType(int msgType) {
        switch (msgType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                return Transaction.NOTIFICATION_TRANSACTION;
            case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                return Transaction.RETRIEVE_TRANSACTION;
            case PduHeaders.MESSAGE_TYPE_READ_REC_IND:
                return Transaction.READREC_TRANSACTION;
            case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                return Transaction.SEND_TRANSACTION;
            default:
                Log.w(TAG, "Unrecognized MESSAGE_TYPE: " + msgType);
                return -1;
        }
    }

    private void launchTransaction(int serviceId, TransactionBundle txnBundle, boolean noNetwork) {
        if (noNetwork) {
            Log.w(TAG, "launchTransaction: no network error!");
            onNetworkUnavailable(serviceId, txnBundle.getTransactionType());

            //fix bug 9187 by phone_08 20120201
            MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
            return;
        }
        Message msg = mServiceHandler.obtainMessage(EVENT_TRANSACTION_REQUEST);
        msg.arg1 = serviceId;
        msg.obj = txnBundle;

        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "launchTransaction: sending message " + msg);
        }
        mServiceHandler.sendMessage(msg);
    }

    private void onNetworkUnavailable(int serviceId, int transactionType) {
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "onNetworkUnavailable: sid=" + serviceId + ", type=" + transactionType);
        }

        int toastType = TOAST_NONE;
        if (transactionType == Transaction.RETRIEVE_TRANSACTION) {
            toastType = TOAST_DOWNLOAD_LATER;
        } else if (transactionType == Transaction.SEND_TRANSACTION) {
            toastType = TOAST_MSG_QUEUED;
        }
        if (toastType != TOAST_NONE) {
            mToastHandler.sendEmptyMessage(toastType);
        }
        stopSelf(serviceId);
    }

    @Override
    public void onDestroy() {
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "Destroying TransactionService");
        }
        if (!mPending.isEmpty()) {
            Log.w(TAG, "TransactionService exiting with transaction still pending");
        }

        releaseWakeLock();

        //Modify by huibin
        //mConnectivityListener.unregisterHandler(mServiceHandler);
        //mConnectivityListener.stopListening();
        //mConnectivityListener = null;

		unregisterReceiver(mReceiver);

        mServiceHandler.sendEmptyMessage(EVENT_QUIT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Handle status change of Transaction (The Observable).
     */
    public void update(Observable observable) {
        Transaction transaction = (Transaction) observable;
        int serviceId = transaction.getServiceId();

        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "update transaction " + serviceId);
        }

        try {
            synchronized (mProcessing) {
                mProcessing.remove(transaction);
                if (mPending.size() > 0) {
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "update: handle next pending transaction...");
                    }
                    Message msg = mServiceHandler.obtainMessage(
                            EVENT_HANDLE_NEXT_PENDING_TRANSACTION,
                            transaction.getConnectionSettings());
                    mServiceHandler.sendMessage(msg);
                }
                else {
                    int numProcessTransaction = mProcessing.size();
                    if (numProcessTransaction == 0) {
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "update: endMmsConnectivity");
                        }
                        endMmsConnectivity();
                    }
                }
            }

            Intent intent = new Intent(TRANSACTION_COMPLETED_ACTION);
            TransactionState state = transaction.getState();
            int result = state.getState();
            intent.putExtra(STATE, result);

            switch (result) {
                case TransactionState.SUCCESS:
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction complete: " + serviceId);
                    }

                    intent.putExtra(STATE_URI, state.getContentUri());

                    // Notify user in the system-wide notification area.
                    switch (transaction.getType()) {
                        case Transaction.NOTIFICATION_TRANSACTION:
                        case Transaction.RETRIEVE_TRANSACTION:
                            // We're already in a non-UI thread called from
                            // NotificationTransacation.run(), so ok to block here.
                            MessagingNotification.blockingUpdateNewMessageIndicator(this, true,
                                    false);
                            MessagingNotification.updateDownloadFailedNotification(this);
                            break;
                        case Transaction.SEND_TRANSACTION:
                            RateController.getInstance().update();
                            break;
                    }
                    break;
                case TransactionState.FAILED:
                    //Lino modify for NEWMS00135971 begin 2011-11-03
                    if(transaction.getType() == Transaction.RETRIEVE_TRANSACTION){
                        mToastHandler.sendEmptyMessage(TOAST_DOWNLOAD_LATER);
                    }
                    /*delete for CR<NEWMS00132656> by luning at 2011.11.15*/
//                	else{
//                		mToastHandler.sendEmptyMessage(TOAST_SENDING_FAILED);
//                	}
                    //Lino modify for NEWMS00135971 end 2011-11-03
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction failed: " + serviceId);
                    }
                    break;

                case TransactionState.FAILED_DATASUSPENDED:

                        Log.d(TAG, "TransactionState.FAILED_DATASUSPENDED: add the transaction to mPending"
                                +" ;mPending size :"+mPending.size()+";mProcessing size:"+mProcessing.size());
                        Log.d(TAG, "FAILED_DATASUSPENDED retry");
                        mPending.add(transaction);
                        Message msg = mServiceHandler.obtainMessage(
                                EVENT_HANDLE_NEXT_PENDING_TRANSACTION,
                                transaction.getConnectionSettings());
                        mServiceHandler.sendMessage(msg);
                    break;
                default:
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction state unknown: " +
                                serviceId + " " + result);
                    }
                    break;
            }

            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "update: broadcast transaction result " + result);
            }
            // Broadcast the result of the transaction.
            sendBroadcast(intent);
        } finally {
            if (transaction.getState().getState() != TransactionState.FAILED_DATASUSPENDED) {
                transaction.detach(this);
            }
            stopSelfIfIdle(serviceId);
        }
    }

    private synchronized void createWakeLock() {
        // Create a new wake lock if we haven't made one yet.
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connectivity");
            mWakeLock.setReferenceCounted(false);
        }
    }

    private void acquireWakeLock() {
        // It's okay to double-acquire this because we are not using it
        // in reference-counted mode.
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        // Don't release the wake lock if it hasn't been created and acquired.
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    protected int beginMmsConnectivity() throws IOException {
        // Take a wake lock so we don't fall asleep before the message is downloaded.
        Log.i(TAG,"beginMmsConnectivity..");
        if(TransactionDataconnectionState.getDataconnectionState(mPhoneId)
                == TransactionDataconnectionState.TRANSACTION_STATE_PENDING){
            return TransactionDataconnectionState.TRANSACTION_STATE_PENDING;
        }
        createWakeLock();

        int result = mConnMgr.startUsingNetworkFeature(
                ConnectivityManager.TYPE_MOBILE, PhoneFactory.getFeature(Phone.FEATURE_ENABLE_MMS, mPhoneId));

        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "beginMmsConnectivity: result=" + result);
        }

        switch (result) {
            case Phone.APN_ALREADY_ACTIVE:
            	//cienet add yuanman 2011-6-17:
            	if ( mPendingSwitch == SwitchState.CONNECTING){
            		mPendingSwitch = SwitchState.CONNECTED;
            	}
            	//cienet end yuanman.
            case Phone.APN_REQUEST_STARTED:
                acquireWakeLock();
                return result;
        }

        throw new IOException("Cannot establish MMS connectivity");
    }

    protected void endMmsConnectivity() {
        try {
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "endMmsConnectivity");
            }

            // cancel timer for renewal of lease
            mServiceHandler.removeMessages(EVENT_CONTINUE_MMS_CONNECTIVITY);
            if (mConnMgr != null) {
                mConnMgr.stopUsingNetworkFeature(
                        ConnectivityManager.TYPE_MOBILE,
                        PhoneFactory.getFeature(Phone.FEATURE_ENABLE_MMS, mPhoneId));
            }
        } finally {
            TransactionDataconnectionState.setEndDataconnectionState(mPhoneId);
            releaseWakeLock();
        }
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        private String decodeMessage(Message msg) {
            if (msg.what == EVENT_QUIT) {
                return "EVENT_QUIT";
            } else if (msg.what == EVENT_CONTINUE_MMS_CONNECTIVITY) {
                return "EVENT_CONTINUE_MMS_CONNECTIVITY";
            } else if (msg.what == EVENT_TRANSACTION_REQUEST) {
                return "EVENT_TRANSACTION_REQUEST";
            } else if (msg.what == EVENT_HANDLE_NEXT_PENDING_TRANSACTION) {
                return "EVENT_HANDLE_NEXT_PENDING_TRANSACTION";
            }
            return "unknown message.what";
        }

        private String decodeTransactionType(int transactionType) {
            if (transactionType == Transaction.NOTIFICATION_TRANSACTION) {
                return "NOTIFICATION_TRANSACTION";
            } else if (transactionType == Transaction.RETRIEVE_TRANSACTION) {
                return "RETRIEVE_TRANSACTION";
            } else if (transactionType == Transaction.SEND_TRANSACTION) {
                return "SEND_TRANSACTION";
            } else if (transactionType == Transaction.READREC_TRANSACTION) {
                return "READREC_TRANSACTION";
            }
            return "invalid transaction type";
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the
         * MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "Handling incoming message: " + msg + " = " + decodeMessage(msg));
            }

            Transaction transaction = null;

            switch (msg.what) {
                case EVENT_QUIT:
                    getLooper().quit();
                    return;

                case EVENT_CONTINUE_MMS_CONNECTIVITY:
                    synchronized (mProcessing) {
                        if (mProcessing.isEmpty() && mPending.isEmpty()) {
                            Log.i(TAG, "EVENT_CONTINUE_MMS_CONNECTIVITY mProcessing & mPending is empty. ->setEndDataconnectionState");
                            TransactionDataconnectionState.setEndDataconnectionState(mPhoneId);
                            return;
                        }
                    }

                    Log.i(TAG, "handle EVENT_CONTINUE_MMS_CONNECTIVITY event...");

                    try {
                        int result = beginMmsConnectivity();
                        if (result != Phone.APN_ALREADY_ACTIVE && result != Phone.APN_REQUEST_STARTED
                                && result != TransactionDataconnectionState.TRANSACTION_STATE_PENDING) {
                            Log.i(TAG, "Extending MMS connectivity returned " + result +
                                    " instead of APN_ALREADY_ACTIVE");
                            // Just wait for connectivity startup without
                            // any new request of APN switch.
                            TransactionDataconnectionState.setEndDataconnectionState(mPhoneId);
                            return;
                        }
                        if(result == Phone.APN_REQUEST_STARTED && TransactionDataconnectionState.isDataconnectionStartingTimeout(mPhoneId)){
                            makeAllTransactionFailure();

                            endMmsConnectivity();
                            // sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                            // APN_TIMEOUT_WAIT);
                            return;
                        }
                    } catch (IOException e) {
                        TransactionDataconnectionState.setEndDataconnectionState(mPhoneId);
                        Log.w(TAG, "Attempt to extend use of MMS connectivity failed");
                        return;
                    }

                    // Restart timer
                    sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                                       APN_EXTENSION_WAIT);
                    return;

                /*
                case EVENT_DATA_STATE_CHANGED:

                    if (mConnectivityListener == null) {
                        TransactionDataconnectionState.setEndDataconnectionState(mPhoneId);
                        return;
                    }

                    NetworkInfo info = mConnectivityListener.getNetworkInfo();
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Handle DATA_STATE_CHANGED event: " + info);
                    }

                    // Check availability of the mobile network.
//                    if ((info == null) || (info.getType() !=
//                            ConnectivityManager.TYPE_MOBILE_MMS)) {
                    if ((info == null) || (info.getType() != ConnectivityManager.getMmsTypeByPhoneId(mPhoneId))) {
                            Log.i(TAG, "  type is not TYPE_MOBILE_MMS" + mPhoneId + ", bail");
                        return;
                    }

                    if (!info.isConnected()) {
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "  TYPE_MOBILE_MMS not connected, bail");
                        }
                        return;
                    }

                    //
                    TransactionSettings settings = new TransactionSettings(
                            TransactionService.this, info.getExtraInfo(), mPhoneId);

                    // If this APN doesn't have an MMSC, wait for one that does.
                    if (TextUtils.isEmpty(settings.getMmscUrl())) {
                        Log.i(TAG, "  empty MMSC url, bail");
                        try {
                            int result = beginMmsConnectivity();
                            if (result == Phone.APN_ALREADY_ACTIVE || result == Phone.APN_REQUEST_STARTED) {
                                Log.i(TAG, "  empty MMSC url,but connectivity is started or active. need stop connectivity");
                                makeAllTransactionFailure();
                                mToastHandler.sendEmptyMessage(TOAST_APN_CONFIG_ERROR);
                                endMmsConnectivity();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                        return;
                    }

                    // Set a timer to keep renewing our "lease" on the MMS connection
                    sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                                       APN_EXTENSION_WAIT);
                    processPendingTransaction(transaction, settings);
                    return;
				*/
                case EVENT_TRANSACTION_REQUEST:
                    int serviceId = msg.arg1;
                    try {
                        TransactionBundle args = (TransactionBundle) msg.obj;
                        TransactionSettings transactionSettings;

                        // Set the connection settings for this transaction.
                        // If these have not been set in args, load the default settings.
                        String mmsc = args.getMmscUrl();
                        Log.i(TAG,"handle EVENT_TRANSACTION_REQUEST:String mmsc:"+mmsc);
                        if (mmsc != null) {
                            transactionSettings = new TransactionSettings(
                                    mmsc, args.getProxyAddress(), args.getProxyPort(), mPhoneId);
                        } else {
                            transactionSettings = new TransactionSettings(
                                                    TransactionService.this, null, mPhoneId);
                        }

                        int transactionType = args.getTransactionType();

                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "handle EVENT_TRANSACTION_REQUEST: transactionType=" +
                                    transactionType);
                        }

                        // Create appropriate transaction
                        switch (transactionType) {
                            case Transaction.NOTIFICATION_TRANSACTION:
                                String uri = args.getUri();
                                if (uri != null) {
                                    transaction = new NotificationTransaction(
                                            TransactionService.this, serviceId,
                                            transactionSettings, uri, mPhoneId);
                                } else {
                                    // Now it's only used for test purpose.
                                    byte[] pushData = args.getPushData();
                                    PduParser parser = new PduParser(pushData);
                                    GenericPdu ind = parser.parse(mPhoneId);

                                    int type = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
                                    if ((ind != null) && (ind.getMessageType() == type)) {
                                        transaction = new NotificationTransaction(
                                                TransactionService.this, serviceId,
                                                transactionSettings, (NotificationInd) ind, PhoneFactory.getDefaultPhoneId()); //make it as default phone
                                    } else {
                                        Log.e(TAG, "Invalid PUSH data.");
                                        transaction = null;
                                        return;
                                    }
                                }
                                break;
                            case Transaction.RETRIEVE_TRANSACTION:
                                transaction = new RetrieveTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri(), mPhoneId);
                                break;
                            case Transaction.SEND_TRANSACTION:
                                transaction = new SendTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri(), mPhoneId);
                                break;
                            case Transaction.READREC_TRANSACTION:
                                transaction = new ReadRecTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri(), mPhoneId);
                                break;
                            default:
                                Log.w(TAG, "Invalid transaction type: " + serviceId);
                                transaction = null;
                                return;
                        }

                        if (!processTransaction(transaction)) {
                            transaction = null;
                            return;
                        }

                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Started processing of incoming message: " + msg);
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Exception occurred while handling message: " + msg, ex);

                        if (transaction != null) {
                            try {
                                transaction.detach(TransactionService.this);
                                if (mProcessing.contains(transaction)) {
                                    synchronized (mProcessing) {
                                        mProcessing.remove(transaction);
                                    }
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "Unexpected Throwable.", t);
                            } finally {
                                // Set transaction to null to allow stopping the
                                // transaction service.
                                transaction = null;
                            }
                        }
                    } finally {
                        if (transaction == null) {
                            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                Log.v(TAG, "Transaction was null. Stopping self: " + serviceId);
                            }
                            endMmsConnectivity();
                            stopSelf(serviceId);
                        }
                    }
                    return;
                case EVENT_HANDLE_NEXT_PENDING_TRANSACTION:
                    processPendingTransaction(transaction, (TransactionSettings) msg.obj);
                    return;
                default:
                    Log.w(TAG, "what=" + msg.what);
                    return;
            }
        }

        private void makeAllTransactionFailure() {
            synchronized (mProcessing) {
                while (!mPending.isEmpty()) {
                    Transaction tran = mPending.remove(0);
                    tran.makeFailure();
                    int serviceId = tran.getServiceId();
                    Log.d(TAG, "transaction make failure. transaction serviceId = "+serviceId);
                    tran.detach(TransactionService.this);
                    MmsSystemEventReceiver.unRegisterForConnectionStateChanges(getApplicationContext(),mPhoneId);
                    stopSelf(serviceId);
                }
            }
        }

        private void processPendingTransaction(Transaction transaction,
                                               TransactionSettings settings) {

            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "processPendingTxn: transaction=" + transaction);
            }

            int numProcessTransaction = 0;
            synchronized (mProcessing) {
                if (mPending.size() != 0) {
                    transaction = mPending.remove(0);
                }
                numProcessTransaction = mProcessing.size();
            }

            if (transaction != null) {
                if (settings != null) {
                    transaction.setConnectionSettings(settings);
                }

                /*
                 * Process deferred transaction
                 */
                try {
                    int serviceId = transaction.getServiceId();

                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processPendingTxn: process " + serviceId);
                    }

                    if (processTransaction(transaction)) {
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Started deferred processing of transaction  "
                                    + transaction);
                        }
                    } else {
                        transaction = null;
                        stopSelf(serviceId);
                    }
                } catch (IOException e) {
                    Log.w(TAG, e.getMessage(), e);
                }
            } else {
                if (numProcessTransaction == 0) {
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processPendingTxn: no more transaction, endMmsConnectivity");
                    }
                    endMmsConnectivity();
                }
            }
        }

        /**
         * Internal method to begin processing a transaction.
         * @param transaction the transaction. Must not be {@code null}.
         * @return {@code true} if process has begun or will begin. {@code false}
         * if the transaction should be discarded.
         * @throws IOException if connectivity for MMS traffic could not be
         * established.
         */
        private boolean processTransaction(Transaction transaction) throws IOException {
            // Check if transaction already processing
            synchronized (mProcessing) {
                for (Transaction t : mPending) {
                    if (t.isEquivalent(transaction)) {
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Transaction already pending: " +
                                    transaction.getServiceId());
                        }
                        return true;
                    }
                }
                for (Transaction t : mProcessing) {
                    if (t.isEquivalent(transaction)) {
                        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Duplicated transaction: " + transaction.getServiceId());
                        }
                        return true;
                    }
                }

                /*
                * Make sure that the network connectivity necessary
                * for MMS traffic is enabled. If it is not, we need
                * to defer processing the transaction until
                * connectivity is established.
                */
                if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "processTransaction: call beginMmsConnectivity...");
                    Log.i(TAG, "  mProcessing.size = "+mProcessing.size());
                    Log.i(TAG, "  mPending.size = "+mPending.size());
                }
                int connectivityResult = beginMmsConnectivity();
                if (connectivityResult == Phone.APN_REQUEST_STARTED
                    || connectivityResult == TransactionDataconnectionState.TRANSACTION_STATE_PENDING) {
                    //|| !mProcessing.isEmpty()) {
                    mPending.add(transaction);
                    sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                            APN_EXTENSION_WAIT);
                    if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processTransaction: connResult=APN_REQUEST_STARTED, " +
                                "defer transaction pending MMS connectivity");
                    }
                    return true;
                }

                if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "Adding transaction to 'mProcessing' list: " + transaction);
                }
                mProcessing.add(transaction);
            }

            // Set a timer to keep renewing our "lease" on the MMS connection
            sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                               APN_EXTENSION_WAIT);

            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "processTransaction: starting transaction " + transaction);
            }

            // Attach to transaction and process it
            transaction.attach(TransactionService.this);
            transaction.process();
            return true;
        }
    }

    private void renewMmsConnectivity() {
        // Set a timer to keep renewing our "lease" on the MMS connection
        mServiceHandler.sendMessageDelayed(
                mServiceHandler.obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                           APN_EXTENSION_WAIT);
    }

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.w(TAG, "ConnectivityBroadcastReceiver.onReceive() action: " + action);
            }

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            	Log.w(TAG, "!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)");
                return;
            }

            boolean noConnectivity =
                intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            NetworkInfo networkInfo = (NetworkInfo)
                intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

            /*
             * If we are being informed that connectivity has been established
             * to allow MMS traffic, then proceed with processing the pending
             * transaction, if any.
             */

            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "Handle ConnectivityBroadcastReceiver.onReceive(): " + networkInfo);
            }

            // Check availability of the mobile network.
            if ((networkInfo == null) || (networkInfo.getType() != ConnectivityManager.getMmsTypeByPhoneId(mPhoneId))) {
                	Log.i(TAG, "  type is not TYPE_MOBILE_MMS" + mPhoneId + ", bail");
                return;
            }

            if (!networkInfo.isConnected()) {
            	if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "   TYPE_MOBILE_MMS not connected, bail");
                }
                return;
            }

            TransactionSettings settings = new TransactionSettings(TransactionService.this,
            		networkInfo.getExtraInfo(), mPhoneId);

            if (TextUtils.isEmpty(settings.getMmscUrl())) {
                Log.i(TAG, "  empty MMSC url, bail");
                //merge from 4.0
                /*try {
                    int result = beginMmsConnectivity();
                    if (result == Phone.APN_ALREADY_ACTIVE || result == Phone.APN_REQUEST_STARTED) {
                        Log.i(TAG, "  empty MMSC url,but connectivity is started or active. need stop connectivity");
                        TransactionService.this.mServiceHandler.makeAllTransactionFailure();
                        mToastHandler.sendEmptyMessage(TOAST_APN_CONFIG_ERROR);
                        endMmsConnectivity();
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }*/
                return;
            }

            renewMmsConnectivity();
            TransactionService.this.mServiceHandler.processPendingTransaction(null, settings);
        }
    };

    public TransactionService(){
        mPhoneId = 0;
    }

    public TransactionService(int phoneId){
        mPhoneId = phoneId;
        TAG = "TransactionService"+mPhoneId;
    }

    public Class<TransactionService> getClass(String className){
    	Class<TransactionService> myClass = null;
		try {
			myClass = (Class<TransactionService>) Class.forName(className);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
    	return myClass;
    }

}
