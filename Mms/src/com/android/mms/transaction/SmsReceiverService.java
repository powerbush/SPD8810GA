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

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION;
import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;

import java.util.ArrayList;

import com.android.mms.data.Contact;
import com.android.mms.dom.WapPushParser;
import com.android.mms.ui.ClassZeroActivity;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.MessageItem;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.ui.WapPushMessageShowActivity;
import com.android.mms.util.Recycler;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.MmsException;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.Sms.Outbox;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import android.text.format.Time;

import android.text.TextUtils;

import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.R;
import com.android.mms.LogTag;

/**
 * This service essentially plays the role of a "worker thread", allowing us to store
 * incoming messages to the database, update notifications, etc. without blocking the
 * main thread that SmsReceiver runs on.
 */
public class SmsReceiverService extends Service {
    private static final String TAG = "SmsReceiverService";
    private static final boolean DEBUG = true;

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private boolean mSending;

    public static final String MESSAGE_SENT_ACTION =
        "com.android.mms.transaction.MESSAGE_SENT";

    // Indicates next message can be picked up and sent out.
    public static final String EXTRA_MESSAGE_SENT_SEND_NEXT ="SendNextMsg";

    public static final String ACTION_SEND_MESSAGE =
        "com.android.mms.transaction.SEND_MESSAGE";

    // This must match the column IDs below.
    private static final String[] SEND_PROJECTION = new String[] {
        Sms._ID,        //0
        Sms.THREAD_ID,  //1
        Sms.ADDRESS,    //2
        Sms.BODY,       //3
        Sms.STATUS,     //4
	Sms.PHONE_ID,   //5


    };

    public Handler mToastHandler = new Handler();

    // This must match SEND_PROJECTION.
    private static final int SEND_COLUMN_ID         = 0;
    private static final int SEND_COLUMN_THREAD_ID  = 1;
    private static final int SEND_COLUMN_ADDRESS    = 2;
    private static final int SEND_COLUMN_BODY       = 3;
    private static final int SEND_COLUMN_STATUS     = 4;
    private static final int SEND_COLUMN_PHONE_ID     = 5;

    private int mResultCode;

    @Override
    public void onCreate() {
        // Temporarily removed for this duplicate message track down.
//        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
//            Log.v(TAG, "onCreate");
//        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        Log.i(TAG,"onCreate");
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
	   Log.i(TAG,"onCreate (1)");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Temporarily removed for this duplicate message track down.
//        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
//            Log.v(TAG, "onStart: #" + startId + ": " + intent.getExtras());
//        }

        mResultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Temporarily removed for this duplicate message track down.
//        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
//            Log.v(TAG, "onDestroy");
//        }
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent)msg.obj;
            if (intent != null) {
                String action = intent.getAction();

                int error = intent.getIntExtra("errorCode", 0);

                if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
                    Intent sendMsg = new Intent("android.provider.Telephony.SEND_SMS");
                    sendBroadcast(sendMsg);
                    handleSmsSent(intent, error);
                } else if (SMS_RECEIVED_ACTION.equals(action)) {
                    handleSmsReceived(intent, error);
                } else if (WAP_PUSH_RECEIVED_ACTION.equals(action)) {
                	Log.i(TAG,"WAP_PUSH_RECEIVED_ACTION");
                	handleWapPushReceived(intent, error);
                } else if (ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                    handleServiceStateChanged(intent);
                } else if (ACTION_SEND_MESSAGE.endsWith(action)) {
                    handleSendMessage();
                }
            }
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, serviceId);
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        // If service just returned, start sending out the queued messages
        ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
        int phoneId = intent.getIntExtra(Phone.PHONE_ID, -1);
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            sendFirstQueuedMessage(phoneId);
        }
    }

    private void handleSendMessage() {
        if (!mSending) {
            sendFirstQueuedMessage();
        }
    }

    public synchronized void sendFirstQueuedMessage(int selectionPhoneId) {
        boolean success = true;
        String selection = null;
        if(selectionPhoneId != -1){
            selection = Sms.PHONE_ID + " = " + selectionPhoneId;
        }
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");
        ContentResolver resolver = getContentResolver();
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, selection, null, "date ASC");  // date ASC so we send out in
                                                                        // same order the user tried
                                                                        // to send messages.
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);
            int phoneId = c.getInt(SEND_COLUMN_PHONE_ID);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
            // sunway:last chance to compute a phoneId used to send the

                    SmsMessageSender sender = new SmsSingleRecipientSender(this,
                                       address,
                                       msgText,
                                       threadId,
                                       status == Sms.STATUS_PENDING,
                                       msgUri,
                                       phoneId
            );

                    if (LogTag.VERBOSE || DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);;
                        mSending = true;
                    } catch (MmsException e) {
                        Log.e(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri
                                + ", caught ", e);
                        mSending = false;
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                    }
                }
            } finally {
                c.close();
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            unRegisterForServiceStateChanges();
        }
    }

    public synchronized void sendFirstQueuedMessage() {
        boolean success = true;
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");
        ContentResolver resolver = getContentResolver();
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, null, null, "date ASC");   // date ASC so we send out in
                                                                    // same order the user tried
                                                                    // to send messages.
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);
		    int phoneId = c.getInt(SEND_COLUMN_PHONE_ID);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
		    // sunway:last chance to compute a phoneId used to send the

                    SmsMessageSender sender = new SmsSingleRecipientSender(this,
									   address,
									   msgText,
									   threadId,
									   status == Sms.STATUS_PENDING,
									   msgUri,
									   phoneId
			);

                    if (LogTag.VERBOSE || DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);;
                        mSending = true;
                    } catch (MmsException e) {
                        Log.e(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri
                                + ", caught ", e);
                        mSending = false;
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                    }
                }
            } finally {
                c.close();
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            unRegisterForServiceStateChanges();
        }
    }

    private void handleSmsSent(Intent intent, int error) {
        Uri uri = intent.getData();
        mSending = false;
        boolean sendNextMsg = intent.getBooleanExtra(EXTRA_MESSAGE_SENT_SEND_NEXT, false);

        if (mResultCode == Activity.RESULT_OK) {
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "handleSmsSent sending uri: " + uri);
            }
            if (sendNextMsg) {
                if (!Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_SENT, error)) {
                    Log.e(TAG, "handleSmsSent: failed to move message " + uri + " to sent folder");
                }
                sendFirstQueuedMessage();
            }

            // Update the notification for failed messages since they may be deleted.
            MessagingNotification.updateSendFailedNotification(this);
        } else if ((mResultCode == SmsManager.RESULT_ERROR_RADIO_OFF) ||
                (mResultCode == SmsManager.RESULT_ERROR_NO_SERVICE)) {
            if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "handleSmsSent: no service, queuing message w/ uri: " + uri);
            }
            // We got an error with no service or no radio. Register for state changes so
            // when the status of the connection/radio changes, we can try to send the
            // queued up messages.
            registerForServiceStateChanges();
            // We couldn't send the message, put in the queue to retry later.
            Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_QUEUED, error);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.message_queued),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else if (mResultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.fdn_check_failure),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            messageFailedToSend(uri, error);
            if (sendNextMsg) {
                sendFirstQueuedMessage();
            }
        }
    }

    private void messageFailedToSend(Uri uri, int error) {
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "messageFailedToSend msg failed uri: " + uri);
        }
        Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_FAILED, error);
        MessagingNotification.notifySendFailed(getApplicationContext(), true);
    }

    private void handleSmsReceived(Intent intent, int error) {
        SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
	int id=intent.getIntExtra("phone_id",-1);
        Uri messageUri = insertMessage(this, msgs, error,id);

        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            SmsMessage sms = msgs[0];
            Log.v(TAG, "handleSmsReceived" + (sms.isReplace() ? "(replace)" : "") +
                    " messageUri: " + messageUri +
                    ", address: " + sms.getOriginatingAddress() +
                    ", body: " + sms.getMessageBody());
        }

        if (messageUri != null) {
            // Called off of the UI thread so ok to block.
            MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
        }
    }

    private void handleWapPushReceived(Intent intent, int error) {
        String CONTENT_MIME_TYPE_B_PUSH_SI = "application/vnd.wap.sic";
        String CONTENT_MIME_TYPE_B_PUSH_SL = "application/vnd.wap.slc";
        String pushBody;
        String expired = "";
        String action = "";
        String si_id = "";
        String href = "";

        byte[] pushData = intent.getByteArrayExtra("data");
        WapPushParser push_parser = new WapPushParser(pushData);
        WapPushMsg pushMsg = null;

        if (CONTENT_MIME_TYPE_B_PUSH_SI.equals(intent.getType())) {
            Log.i(TAG,"wap push create SI parser");
            pushMsg = push_parser.parse(WapPushMsg.WAP_PUSH_TYPE_SI);
            if (null == pushMsg) return;
            href = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_HREF);
            String sitext = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_SI_TEXT);
            expired = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_SI_EXPIRED);
            action = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_PRIOR);
            si_id = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_SI_ID);
            Log.i(TAG, "wap push ====> si_id:"+si_id+",action:"+action+",expired:"+expired+",href:"+href+",sitext:"+sitext);
            pushBody = sitext+"\n Url:"+href;
        } else if (CONTENT_MIME_TYPE_B_PUSH_SL.equals(intent.getType())){
            Log.i(TAG,"wap push create SL parser");
            pushMsg = push_parser.parse(WapPushMsg.WAP_PUSH_TYPE_SL);
            if (null == pushMsg) return;
            href = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_HREF);
            action = pushMsg.getAttributeValueString(WapPushMsg.WAP_PUSH_PROJECTION_PRIOR);
            pushBody = "\n Url:"+href;
        } else {
            Log.i(TAG,"wap push non support type:"+intent.getType());
        	return;
        }

        //test
//        Intent sendintent = new Intent("android.intent.action.ShowWapPush");
//        sendintent.setClass(this, WapPushMessageShowActivity.class);
//        if(tempstr.contains("1")){
//            sendintent.putExtra("abc", 1);
//        }else if(tempstr.contains("2")){
//            sendintent.putExtra("abc", 2);
//        }
//
//        sendintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
//        this.startActivity(sendintent);
        //test

        // do something depend on action
        boolean  isNeedNotify = false;
        boolean isNeedStore = false;
        int actiontype = WapPushParser.getPushAttrValue(action);
//        actiontype = 11;//for test
//        si_id = "202.108.22.5";
        if( actiontype == WapPushMsg.WAP_PUSH_PRIO_NONE.intValue()){
            isNeedNotify = false;
            isNeedStore = false;
        } else if(actiontype == WapPushMsg.WAP_PUSH_PRIO_LOW.intValue()||actiontype == WapPushMsg.WAP_PUSH_SL_PRIO_CACHE.intValue()){
            isNeedNotify = false;
            isNeedStore = true;
        } else if(actiontype == WapPushMsg.WAP_PUSH_PRIO_MEDIUM.intValue()){
            isNeedNotify = true;
            isNeedStore = true;
        } else if(actiontype == WapPushMsg.WAP_PUSH_PRIO_HIGH.intValue()
                ||actiontype == WapPushMsg.WAP_PUSH_SL_PRIO_LOW.intValue()
                ||actiontype == WapPushMsg.WAP_PUSH_SL_PRIO_HIGH.intValue()){
            isNeedNotify = true;
            isNeedStore = true;
            //need show wap push message on dialog
            Intent sendintent = new Intent("android.intent.action.ShowWapPush");
            sendintent.setClass(this, WapPushMessageShowActivity.class);
            sendintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            sendintent.putExtra("href", href);
            sendintent.putExtra("pushBody", pushBody);
            this.startActivity(sendintent);
        } else if(actiontype == WapPushMsg.WAP_PUSH_PRIO_DELETE.intValue()){
            isNeedNotify = false;
            isNeedStore = false;
            deleteWapPushMessageBySIID(si_id);
        }

        Log.i(TAG,"wap push body = "+pushBody);
        Uri messageUri = null;
        if(isNeedStore){
            SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
            messageUri = storePushMessage(this, msgs, pushBody, error, expired, si_id);
        }

        Log.d(TAG,"wap push messageUri = "+messageUri+",isNeedNotify="+isNeedNotify);
        if (messageUri != null && isNeedNotify) {
            // Called off of the UI thread so ok to block.
            Log.i(TAG,"wap push messageUri = "+messageUri);
            MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
        }
    }

    private void deleteWapPushMessageBySIID(String si_id){
        Log.d(TAG, "delete wap push message by si_id ='"+si_id+"'");
        if(si_id == null || si_id.trim().length() <= 0){
            return;
        }
        ContentResolver cr = this.getContentResolver();
        int deleteCount = 0;
        try {
            deleteCount = cr.delete(Sms.CONTENT_URI, " si_id = '"+si_id+"'", null);
        } catch (Exception e) {
            Log.e(TAG, "process deleteWapPushMessageBySIID happened exception!",e);
        }finally{
        }
    }

    private Uri storePushMessage(Context context, SmsMessage[] msgs, String pushBody, int error, String expired, String si_id) {
        SmsMessage sms = msgs[0];

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
//        int pduCount = msgs.length;

//        if (pduCount == 1) {
//            // There is only one part, so grab the body directly.
//            values.put(Inbox.BODY, sms.getDisplayMessageBody());
//        } else {
//            // Build up the body from the parts.
//            StringBuilder body = new StringBuilder();
//            for (int i = 0; i < pduCount; i++) {
//                sms = msgs[i];
//                body.append(sms.getDisplayMessageBody());
//            }
//            values.put(Inbox.BODY, body.toString());
//        }
        values.put(Inbox.BODY, pushBody);
        values.put("wap_push", 1);
        values.put("expired", expired);
        values.put("si_id", si_id);


        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);
        String name = "";
        Contact cacheContact = Contact.get(address,true);
        if (cacheContact != null) {
            address = cacheContact.getNumber();
            name = cacheContact.getName();
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Threads.getOrCreateThreadId(context, address, name);
            values.put(Sms.THREAD_ID, threadId);
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(), threadId);

        return insertedUri;
    }

    private void handleBootCompleted() {
        moveOutboxMessagesToQueuedBox();
        sendFirstQueuedMessage();

        // Called off of the UI thread so ok to block.
        MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
    }

    private void moveOutboxMessagesToQueuedBox() {
        ContentValues values = new ContentValues(1);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);

        SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
    }

    public static final String CLASS_ZERO_BODY_KEY = "CLASS_ZERO_BODY";

    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    /**
     * If the message is a class-zero message, display it immediately
     * and return null.  Otherwise, store it using the
     * <code>ContentResolver</code> and return the
     * <code>Uri</code> of the thread containing this message
     * so that we can use it for notification.
     */
    private Uri insertMessage(Context context, SmsMessage[] msgs, int error,int phoneId) {
        // Build the helper classes to parse the messages.
        SmsMessage sms = msgs[0];

        if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            displayClassZeroMessage(context, sms);
            return null;
        } else if (sms.isReplace()) {
            Log.d(TAG, "[cmgw]enter replaceMessage");
            return replaceMessage(context, msgs, error,phoneId);
        } else {
            Log.d(TAG, "[cmgw]enter storeMessage");
            return storeMessage(context, msgs, error,phoneId);
        }
    }

    /**
     * This method is used if this is a "replace short message" SMS.
     * We find any existing message that matches the incoming
     * message's originating address and protocol identifier.  If
     * there is one, we replace its fields with those of the new
     * message.  Otherwise, we store the new message as usual.
     *
     * See TS 23.040 9.2.3.9.
     */
    private Uri replaceMessage(Context context, SmsMessage[] msgs, int error,int phoneId) {
        SmsMessage sms = msgs[0];
        ContentValues values = extractContentValues(sms);

        values.put(Inbox.BODY, sms.getMessageBody());
        values.put(Sms.ERROR_CODE, error);
	values.put(Sms.PHONE_ID, phoneId);

        ContentResolver resolver = context.getContentResolver();
        String originatingAddress = sms.getOriginatingAddress();
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection =
                Sms.ADDRESS + " = ? AND " +
                Sms.PROTOCOL + " = ?";
        String[] selectionArgs = new String[] {
            originatingAddress, Integer.toString(protocolIdentifier)
        };

        Cursor cursor = SqliteWrapper.query(context, resolver, Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);

                    SqliteWrapper.update(context, resolver, messageUri,
                                        values, null, null);
                    return messageUri;
                }
            } finally {
                cursor.close();
            }
        }
        return storeMessage(context, msgs, error,phoneId);
    }

    private Uri storeMessage(Context context, SmsMessage[] msgs, int error,int phoneId) {
        SmsMessage sms = msgs[0];
        boolean isClass2 = (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_2) ? true : false;

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
	values.put(Sms.PHONE_ID, phoneId);

        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, sms.getDisplayMessageBody());
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                body.append(sms.getDisplayMessageBody());
            }
            values.put(Inbox.BODY, body.toString());
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);
        String name = "";
        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address,true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
                name = cacheContact.getName();
            }
        } else {
            address = getString(R.string.unknown_sender);
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Threads.getOrCreateThreadId(context, address, name);
            values.put(Sms.THREAD_ID, threadId);
        }

        // Check needs to be saved to SIM card
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean saveSimcard = prefs.getBoolean(
                MessagingPreferenceActivity.SMS_SAVE_TO_SIMCARD, false);
        Log.d(TAG, "[cmgw]need to save sim card =" + saveSimcard + " isClass2 = " + isClass2);
        if (saveSimcard && !isClass2) {
            if (!MessageUtils.isSimMemFull(phoneId)) {
                Log.d(TAG, "[cmgw]save to sim memory");
                saveMessageToSim(address, values.getAsString(Sms.BODY), values.getAsLong(Sms.DATE),phoneId);
            } else {
                Log.d(TAG, "[cmgw]sim mem full");
                Intent intent = new Intent(Intents.SIM_FULL_ACTION);
                intent.putExtra(Phone.PHONE_ID, phoneId);
                context.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
            }
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(), threadId);

        // added for MessageFolderActivity to refresh
        Intent intent = new Intent("android.permission.SAVE_SMS_TO_DATABASE");
        intent.putExtra(Phone.PHONE_ID, phoneId);
        context.sendBroadcast(intent);

        return insertedUri;
    }

    private void saveMessageToSim(String address, String mbody, long date,int phoneId) {
        boolean result = false;
        String timeString = null;
        String bcdtimeString  = null;

        SmsManager smsManager = SmsManager.getDefault(phoneId);
        ArrayList<String> messages = null;
        messages = smsManager.divideMessage(mbody);

        Time t = new Time();
        t.set(date);
        timeString = t.format("%g%m%d%H%M%S");
        Log.d(TAG, "[cmgw]timeString =" + timeString);
//        byte[] timebcd = PhoneNumberUtils.numberToCalledPartyBCD(timeString);
//        Log.d(TAG, "[cmgw]bcdlen ="+timebcd.length+" timelen =" + timeString.length());
//        if (timebcd.length > timeString.length()/2) {
//            int dataIndex = 1;
//            byte[] data = new byte[timebcd.length - dataIndex];
//            System.arraycopy(timebcd, dataIndex, data, 0, data.length);
//            bcdtimeString = IccUtils.bytesToHexString(data) + "00";
//        } else {
//            bcdtimeString = IccUtils.bytesToHexString(timebcd) + "00";
//        }
        byte[] timebcd = MessageUtils.GetSctsTime(t);
        bcdtimeString = IccUtils.bytesToHexString(timebcd);
        Log.d(TAG, "[cmgw]bcd timeString = " + bcdtimeString);

        result = smsManager.saveMultipartTextMessage(address, messages, false, bcdtimeString);
        Log.d(TAG, "[cmgw]save result =" + result);
        /*****************begin*************/
        //if result = true represent successful store to the simcard,then send a broadcast to update UI
        if (result) {
            final String mSimStoreAction = "android.provide.STORE_SIMCARD_SUCESS";
            Intent mIntent = new Intent(mSimStoreAction);
            this.sendBroadcast(mIntent);
        }
        /******************** end **********/
    }

    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Inbox.ADDRESS, sms.getDisplayOriginatingAddress());

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    /**
     * Displays a class-zero message immediately in a pop-up window
     * with the number from where it received the Notification with
     * the body of the message
     *
     */
    private void displayClassZeroMessage(Context context, SmsMessage sms) {
        // Using NEW_TASK here is necessary because we're calling
        // startActivity from outside an activity.
        Intent smsDialogIntent = new Intent(context, ClassZeroActivity.class)
                .putExtra("pdu", sms.getPdu())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(smsDialogIntent);
        MessagingNotification.classZeroNotification(context);
    }

    private void registerForServiceStateChanges() {
        Context context = getApplicationContext();
        unRegisterForServiceStateChanges();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "registerForServiceStateChanges");
        }

        context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
    }

    private void unRegisterForServiceStateChanges() {
        if (DEBUG || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "unRegisterForServiceStateChanges");
        }
        try {
            Context context = getApplicationContext();
            context.unregisterReceiver(SmsReceiver.getInstance());
        } catch (IllegalArgumentException e) {
            // Allow un-matched register-unregister calls
        }
    }

}


