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

import com.android.internal.util.HexDump;
import com.android.mms.MmsConfig;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.Recycler;
import com.android.mms.ui.MessageFolderActivity;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.AcknowledgeInd;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.EncodedStringValue;
import android.database.sqlite.SqliteWrapper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.content.Intent;

import java.io.IOException;

/**
 * The RetrieveTransaction is responsible for retrieving multimedia
 * messages (M-Retrieve.conf) from the MMSC server.  It:
 *
 * <ul>
 * <li>Sends a GET request to the MMSC server.
 * <li>Retrieves the binary M-Retrieve.conf data and parses it.
 * <li>Persists the retrieve multimedia message.
 * <li>Determines whether an acknowledgement is required.
 * <li>Creates appropriate M-Acknowledge.ind and sends it to MMSC server.
 * <li>Notifies the TransactionService about succesful completion.
 * </ul>
 */
public class RetrieveTransaction extends Transaction implements Runnable {
    private static final String TAG = "RetrieveTransaction";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private final Uri mUri;
    private boolean mLocked;

    static final String[] PROJECTION = new String[] {
        Mms.CONTENT_LOCATION,
        Mms.LOCKED,
        Mms.MESSAGE_SIZE,
        Mms.TRANSACTION_ID
    };

    // The indexes of the columns which must be consistent with above PROJECTION.
    static final int COLUMN_CONTENT_LOCATION      = 0;
    static final int COLUMN_LOCKED                = 1;
    static final int MESSAGE_SIZE                 = 2;
    static final int TRANSACTION_ID               = 3;

    public RetrieveTransaction(Context context, int serviceId,
            TransactionSettings connectionSettings, String uri, int phoneId)
            throws MmsException {
        super(context, serviceId, connectionSettings, phoneId);

        if (uri.startsWith("content://")) {
            mUri = Uri.parse(uri); // The Uri of the M-Notification.ind
            mContentLocation = getContentLocation(context, mUri);
            if (LOCAL_LOGV) {
                Log.v(TAG, "X-Mms-Content-Location: " + mContentLocation);
                Log.v(TAG, "X-Mms-Transaction-Id: " + mId);
            }
        } else {
            throw new IllegalArgumentException(
                    "Initializing from X-Mms-Content-Location is abandoned!");
        }

        // Attach the transaction to the instance of RetryScheduler.
        attach(RetrySchedulerProxy.getInstance(context, phoneId));
    }

    private String getContentLocation(Context context, Uri uri)
            throws MmsException {
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            uri, PROJECTION, null, null, null);
        mLocked = false;

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    // Get the locked flag from the M-Notification.ind so it can be transferred
                    // to the real message after the download.
                    mLocked = cursor.getInt(COLUMN_LOCKED) == 1;
                    mId = cursor.getString(TRANSACTION_ID);
                    return cursor.getString(COLUMN_CONTENT_LOCATION);
                }
            } finally {
                cursor.close();
            }
        }

        throw new MmsException("Cannot get X-Mms-Content-Location from: " + uri);
    }

    private long getMmsMessageSize(){
        long messagesize = 0;
        Cursor cursor = SqliteWrapper.query(mContext, mContext.getContentResolver(), mUri, PROJECTION, null, null, null);

		if (cursor != null) {
			try {
			    if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
			        // Get the message size.
			    	messagesize = cursor.getLong(MESSAGE_SIZE);
			    }
			} finally {
			    cursor.close();
			}
		}
		return messagesize;
    }

    /*
     * (non-Javadoc)
     * @see com.android.mms.transaction.Transaction#process()
     */
    @Override
    public void process() {
        new Thread(this).start();
    }

    public void run() {
    	// ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
        boolean lowMemory = isLowMemory();
        // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====
        try {
        	// ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
			if (lowMemory) {
				DownloadManager.getInstance().markState(mUri,
									DownloadManager.STATE_LOW_MEMORY,mPhoneId);
				return;
			}
			// ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====

			boolean isBeyondLimit = DownloadManager.getInstance().checkPduTotalSizeLimit(getMmsMessageSize());
			if (isBeyondLimit) {
			    DownloadManager.getInstance().markState(mUri, DownloadManager.STATE_UNSTARTED,mPhoneId);
				return;
			}

            // Change the downloading state of the M-Notification.ind.
            DownloadManager.getInstance().markState(
                    mUri, DownloadManager.STATE_DOWNLOADING, mPhoneId);

            NotificationInd nInd = (NotificationInd) PduPersister.getPduPersister(mContext).load(mUri);
            int phoneId = nInd.getPhoneId();
            // TODO: ensure phoneId == mPhoneId, thus remove unnecessary code

            // Send GET request to MMSC and retrieve the response data.
            byte[] resp = getPdu(mContentLocation);

            if(resp != null){
                String hexstr = HexDump.toHexString(resp);
                Log.d(TAG, "RetrieveTransaction download from URL:"+mContentLocation+", result pdu is :"+hexstr);
            }else{
                Log.e(TAG, "RetrieveTransaction download from URL:"+mContentLocation+", result pdu is null!!!");
            }

            // Parse M-Retrieve.conf
            RetrieveConf retrieveConf = (RetrieveConf) new PduParser(resp).parse(phoneId);
            if (null == retrieveConf) {
                throw new MmsException("Invalid M-Retrieve.conf PDU.");
            }

            Uri msgUri = null;
            if (isDuplicateMessage(mContext, retrieveConf)) {
                // Mark this transaction as failed to prevent duplicate
                // notification to user.
                mTransactionState.setState(TransactionState.FAILED);
                mTransactionState.setContentUri(mUri);
            } else {
                // Store M-Retrieve.conf into Inbox
                PduPersister persister = PduPersister.getPduPersister(mContext);
                msgUri = persister.persist(retrieveConf, Inbox.CONTENT_URI, phoneId);
                // Use local time instead of PDU time
                ContentValues values = new ContentValues(1);
                values.put(Mms.DATE, System.currentTimeMillis() / 1000L);
                SqliteWrapper.update(mContext, mContext.getContentResolver(),
                        msgUri, values, null, null);
                // The M-Retrieve.conf has been successfully downloaded.
                mTransactionState.setState(TransactionState.SUCCESS);
                mTransactionState.setContentUri(msgUri);

                Intent receiveMms = new Intent(MessageFolderActivity.mMmsReceiveAction);
                mContext.sendBroadcast(receiveMms);
                // Remember the location the message was downloaded from.
                // Since it's not critical, it won't fail the transaction.
                // Copy over the locked flag from the M-Notification.ind in case
                // the user locked the message before activating the download.
                updateContentLocation(mContext, msgUri, mContentLocation, mLocked);
            }

            // Delete the corresponding M-Notification.ind.
            SqliteWrapper.delete(mContext, mContext.getContentResolver(),
                                 mUri, null, null);

            if (msgUri != null) {
                // Have to delete messages over limit *after* the delete above. Otherwise,
                // it would be counted as part of the total.
                Recycler.getMmsRecycler().deleteOldMessagesInSameThreadAsMessage(mContext, msgUri);
            }

            // Send ACK to the Proxy-Relay to indicate we have fetched the
            // MM successfully.
            // Don't mark the transaction as failed if we failed to send it.
            sendAcknowledgeInd(retrieveConf);
        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
        } finally {
        	// ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
        	if(lowMemory){
        		mTransactionState.setState(TransactionState.SUCCESS);
                mTransactionState.setContentUri(mUri);
                Log.e(TAG, "Retrieval lowMemory.");
        	}
        	// ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====
            if (mTransactionState.getState() != TransactionState.SUCCESS) {
                mTransactionState.setState(TransactionState.FAILED);
                mTransactionState.setContentUri(mUri);
                Log.e(TAG, "Retrieval failed.");
            }
            notifyObservers();
        }
    }

    private static boolean isDuplicateMessage(Context context, RetrieveConf rc) {
        byte[] rawMessageId = rc.getMessageId();
        if (rawMessageId != null) {
            String messageId = new String(rawMessageId);
            String selection = "(" + Mms.MESSAGE_ID + " = ? AND "
                                   + Mms.MESSAGE_TYPE + " = ?)";
            String[] selectionArgs = new String[] { messageId,
                    String.valueOf(PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF) };
            Cursor cursor = SqliteWrapper.query(
                    context, context.getContentResolver(),
                    Mms.CONTENT_URI, new String[] { Mms._ID },
                    selection, selectionArgs, null);
            if (cursor != null) {
                try {
                    if (cursor.getCount() > 0) {
                        // We already received the same message before.
                        return true;
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return false;
    }

    private void sendAcknowledgeInd(RetrieveConf rc) throws MmsException, IOException {
        // Send M-Acknowledge.ind to MMSC if required.
        // If the Transaction-ID isn't set in the M-Retrieve.conf, it means
        // the MMS proxy-relay doesn't require an ACK.
        byte[] tranId = rc.getTransactionId();
        if (tranId != null) {
            // Create M-Acknowledge.ind
            AcknowledgeInd acknowledgeInd = new AcknowledgeInd(
                    PduHeaders.CURRENT_MMS_VERSION, tranId, mPhoneId);

            // insert the 'from' address per spec
			//lino modify for NEWMS127046 begin
//            String lineNumber = MessageUtils.getLocalNumber();
            PduHeaders pduHeaders = rc.getPduHeaders();
            EncodedStringValue encodedStringValue = null;
            if(pduHeaders != null){
            	EncodedStringValue[] encodedStringValues = pduHeaders.getEncodedStringValues(PduHeaders.TO);
            	if(encodedStringValues != null && encodedStringValues.length >= 1){
            		encodedStringValue = encodedStringValues[0];
            	}
            }
//            acknowledgeInd.setFrom(new EncodedStringValue(lineNumber));
            acknowledgeInd.setFrom(encodedStringValue);
			//lino modify for NEWMS127046 end
            // Pack M-Acknowledge.ind and send it
            if(MmsConfig.getNotifyWapMMSC()) {
                sendPdu(new PduComposer(mContext, acknowledgeInd).make(), mContentLocation);
            } else {
                sendPdu(new PduComposer(mContext, acknowledgeInd).make());
            }
        }
    }

    private static void updateContentLocation(Context context, Uri uri,
                                              String contentLocation,
                                              boolean locked) {
        ContentValues values = new ContentValues(2);
        values.put(Mms.CONTENT_LOCATION, contentLocation);
        values.put(Mms.LOCKED, locked);     // preserve the state of the M-Notification.ind lock.
        SqliteWrapper.update(context, context.getContentResolver(),
                             uri, values, null, null);
    }

    @Override
    public int getType() {
        return RETRIEVE_TRANSACTION;
    }

    @Override
    public void makeFailure() {
        makeFailure(mUri);
    }
    @Override
    public boolean isEquivalent(Transaction transaction) {
        if ((transaction.getType() == NOTIFICATION_TRANSACTION) ||
            (transaction.getType() == RETRIEVE_TRANSACTION)) {
            if(mContentLocation.equals(transaction.mContentLocation)) return true;
            return false;
        }
        return super.isEquivalent(transaction);
    }
}
