/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import android.database.sqlite.SqliteWrapper;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;

public class MessageStatusReceiver extends BroadcastReceiver {
    public static final String MESSAGE_STATUS_RECEIVED_ACTION =
            "com.android.mms.transaction.MessageStatusReceiver.MESSAGE_STATUS_RECEIVED";
    private static final String[] ID_PROJECTION = new String[] { Sms._ID };
    private static final String[] SMS_STATUS_PROJECTION = new String[] {
        Sms.THREAD_ID, Sms.DATE, Sms.ADDRESS, Sms.SUBJECT, Sms.BODY };
    private static final String LOG_TAG = "MessageStatusReceiver";
    private static final Uri STATUS_URI =
            Uri.parse("content://sms/status");
    private Context mContext;
    private static Handler mToastHandler = new Handler();

    private static final int COLUMN_SMS_ADDRESS = 2;
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        log(intent.toString());
        if (MESSAGE_STATUS_RECEIVED_ACTION.equals(intent.getAction())) {

            Uri messageUri = intent.getData();
            byte[] pdu = (byte[]) intent.getExtra("pdu");

            SmsMessage message = updateMessageStatus(context, messageUri, pdu);

            // Called on the UI thread so don't block.
            if (message.getStatus() < Sms.STATUS_PENDING)
                //MessagingNotification.nonBlockingUpdateNewMessageIndicator(context,
                //        true, message.isStatusReportMessage());
                popupToastMessage(context, messageUri, message.isStatusReportMessage());
       }
    }

    private SmsMessage updateMessageStatus(Context context, Uri messageUri, byte[] pdu) {
        // Create a "status/#" URL and use it to update the
        // message's status in the database.
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            messageUri, ID_PROJECTION, null, null, null);
        SmsMessage message = SmsMessage.createFromPdu(pdu);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                int messageId = cursor.getInt(0);

                Uri updateUri = ContentUris.withAppendedId(STATUS_URI, messageId);
                int status = message.getStatus();
                boolean isStatusReport = message.isStatusReportMessage();
                ContentValues contentValues = new ContentValues(1);

                log("updateMessageStatus: msgUrl=" + messageUri + ", status=" + status +
                    ", isStatusReport=" + isStatusReport);

                contentValues.put(Sms.STATUS, status);
                SqliteWrapper.update(context, context.getContentResolver(),
                                    updateUri, contentValues, null, null);
            } else {
                error("Can't find message for status update: " + messageUri);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return message;
    }

    public void popupToastMessage(final Context context, final Uri messageUri, boolean isStatusMessage){
        // And deals with delivery reports (which use Toasts). It's safe to call in a worker
        // thread because the toast will eventually get posted to a handler.
        new Thread(new Runnable() {
            public void run() {
                ContentResolver resolver = context.getContentResolver();
                Cursor cursor = SqliteWrapper.query(context, resolver, messageUri,
                        SMS_STATUS_PROJECTION, null, null, null);
                log("popupToastMessage---after cusor");
                if (cursor == null)
                    return;

                try {
                    log("popupToastMessage--- cusor != null");
                    //TODO !cursor.moveToFirst() or !cursor.moveToLast() need to confirm
                    if (!cursor.moveToFirst()) {
                        log("popupToastMessage--- cursor.moveToFirst() == false");
                        return;
                    }
                    String address = cursor.getString(COLUMN_SMS_ADDRESS);
                    Contact contact = Contact.get(address, false);
                    String from = contact.getName();
                    final String message = String.format(context.getString(R.string.delivery_toast_body), from);

                    mToastHandler.post(new Runnable() {
                        public void run() {
                            log("popupToastMessage---Toask to show");
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }
                    });

                }catch(Exception ex){
                    Log.e(LOG_TAG, "[MessageStatusReceiver] " + ex.getMessage(), ex);
                }
                finally {
                    if(cursor != null)
                        cursor.close();
                }
            }
        }).start();
    }

    private void error(String message) {
        Log.e(LOG_TAG, "[MessageStatusReceiver] " + message);
    }

    private void log(String message) {
        Log.d(LOG_TAG, "[MessageStatusReceiver] " + message);
    }
}
