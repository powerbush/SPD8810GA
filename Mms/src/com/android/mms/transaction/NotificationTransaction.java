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

import static com.android.mms.transaction.TransactionState.FAILED;
import static com.android.mms.transaction.TransactionState.INITIALIZED;
import static com.android.mms.transaction.TransactionState.SUCCESS;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;
import static com.google.android.mms.pdu.PduHeaders.STATUS_DEFERRED;
import static com.google.android.mms.pdu.PduHeaders.STATUS_RETRIEVED;
import static com.google.android.mms.pdu.PduHeaders.STATUS_UNRECOGNIZED;

import com.android.internal.util.HexDump;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.Recycler;
import com.android.mms.ui.MessageFolderActivity;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.NotifyRespInd;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import android.database.sqlite.SqliteWrapper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.telephony.TelephonyManager;
import android.util.Config;
import android.util.Log;
import android.content.Intent;

import java.io.IOException;

/**
 * The NotificationTransaction is responsible for handling multimedia
 * message notifications (M-Notification.ind).  It:
 *
 * <ul>
 * <li>Composes the notification response (M-NotifyResp.ind).
 * <li>Sends the notification response to the MMSC server.
 * <li>Stores the notification indication.
 * <li>Notifies the TransactionService about succesful completion.
 * </ul>
 *
 * NOTE: This MMS client handles all notifications with a <b>deferred
 * retrieval</b> response.  The transaction service, upon succesful
 * completion of this transaction, will trigger a retrieve transaction
 * in case the client is in immediate retrieve mode.
 */
public class NotificationTransaction extends Transaction implements Runnable {
    private static final String TAG = "NotificationTransaction";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private Uri mUri;
    private NotificationInd mNotificationInd;

    public NotificationTransaction(
            Context context, int serviceId,
            TransactionSettings connectionSettings, String uriString, int phoneId) {
        super(context, serviceId, connectionSettings, phoneId);

        mUri = Uri.parse(uriString);

        try {
            mNotificationInd = (NotificationInd)
                    PduPersister.getPduPersister(context).load(mUri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load NotificationInd from: " + uriString, e);
            throw new IllegalArgumentException();
        }

        mId = new String(mNotificationInd.getTransactionId());
        mContentLocation = new String(mNotificationInd.getContentLocation());
        Log.v(TAG, "X-Mms-Content-Location: " + mContentLocation);
        Log.v(TAG, "X-Mms-Transaction-Id: " + mId);
        // Attach the transaction to the instance of RetryScheduler.
        attach(RetrySchedulerProxy.getInstance(context, phoneId));
    }

    /**
     * This constructor is only used for test purposes.
     */
    public NotificationTransaction(
            Context context, int serviceId,
            TransactionSettings connectionSettings, NotificationInd ind, int phoneId) {
        super(context, serviceId, connectionSettings, phoneId);

        try {
            mUri = PduPersister.getPduPersister(context).persist(
                        ind, Inbox.CONTENT_URI, phoneId);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to save NotificationInd in constructor.", e);
            throw new IllegalArgumentException();
        }

        mNotificationInd = ind;
        mId = new String(ind.getTransactionId());
        mContentLocation = new String(ind.getContentLocation());
        Log.v(TAG, "X-Mms-Content-Location: " + mContentLocation);
        Log.v(TAG, "X-Mms-Transaction-Id: " + mId);
 		 //cienet add yuanman 2011-6-15:
        mPhoneId = phoneId;
        //cienet end yuanman.
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.mms.pdu.Transaction#process()
     */
    @Override
    public void process() {
        new Thread(this).start();
    }

    public void run() {
        DownloadManager downloadManager = DownloadManager.getInstance();
        boolean autoDownload = downloadManager.isAuto(mPhoneId);
        boolean dataSuspended = (MmsApp.getApplication().getTelephonyManager().getDataState() ==
                TelephonyManager.DATA_SUSPENDED);
        // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
        boolean lowMemory = isLowMemory();
        // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====
        try {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Notification transaction launched: " + this);
            }

            // By default, we set status to STATUS_DEFERRED because we
            // should response MMSC with STATUS_DEFERRED when we cannot
            // download a MM immediately.
            int status = STATUS_DEFERRED;

            // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
            //check free memory
            if(lowMemory){
            	downloadManager.markState(mUri, DownloadManager.STATE_LOW_MEMORY,mPhoneId);
            	sendNotifyRespInd(status);
            	return;
            }
            // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====

            // Don't try to download when data is suspended, as it will fail, so defer download
            boolean isBeyondLimit = DownloadManager.getInstance().checkPduTotalSizeLimit(mNotificationInd.getMessageSize());
            if(isBeyondLimit){
		downloadManager.markState(mUri, DownloadManager.STATE_PERMANENT_FAILURE,mPhoneId);
                 sendNotifyRespInd(status);
                 return;
            }
            else if (!autoDownload || dataSuspended) {
                downloadManager.markState(mUri, DownloadManager.STATE_UNSTARTED,mPhoneId);
                sendNotifyRespInd(status);
                return;
            }

            TransactionService.moveMessageType2Retreve(mUri, mContext);
            Log.v(TAG, "move pending message type to retrieve");

            downloadManager.markState(mUri, DownloadManager.STATE_DOWNLOADING, mPhoneId);

            if (LOCAL_LOGV) {
                Log.v(TAG, "Content-Location: " + mContentLocation);
            }

            byte[] retrieveConfData = null;
            // We should catch exceptions here to response MMSC
            // with STATUS_DEFERRED.
            try {
                retrieveConfData = getPdu(mContentLocation);
            } catch (IOException e) {
                mTransactionState.setState(FAILED);
            }

            if(retrieveConfData == null){
                Log.e(TAG, "NotificationTransaction download from URL:"+mContentLocation+", result pdu is null!!!");
            }

            if (retrieveConfData != null) {
                GenericPdu pdu = new PduParser(retrieveConfData).parse(mPhoneId);
                if ((pdu == null) || (pdu.getMessageType() != MESSAGE_TYPE_RETRIEVE_CONF)) {
                    Log.e(TAG, "Invalid M-RETRIEVE.CONF PDU.");
                    mTransactionState.setState(FAILED);
                    status = STATUS_UNRECOGNIZED;
                } else {
                    // Save the received PDU (must be a M-RETRIEVE.CONF).
                    PduPersister p = PduPersister.getPduPersister(mContext);
                    Uri uri = p.persist(pdu, Inbox.CONTENT_URI, mPhoneId);
                    // Use local time instead of PDU time
                    ContentValues values = new ContentValues(1);
                    values.put(Mms.DATE, System.currentTimeMillis() / 1000L);
                    SqliteWrapper.update(mContext, mContext.getContentResolver(),
                            uri, values, null, null);
                    // We have successfully downloaded the new MM. Delete the
                    // M-NotifyResp.ind from Inbox.
                    SqliteWrapper.delete(mContext, mContext.getContentResolver(),
                                         mUri, null, null);
                    // Notify observers with newly received MM.
                    mUri = uri;
                    status = STATUS_RETRIEVED;
                }
            }

            if (LOCAL_LOGV) {
                Log.v(TAG, "status=0x" + Integer.toHexString(status));
            }

            // Check the status and update the result state of this Transaction.
            switch (status) {
                case STATUS_RETRIEVED:
                    Intent receiveMms = new Intent(MessageFolderActivity.mMmsReceiveAction);
                    mContext.sendBroadcast(receiveMms);
                    mTransactionState.setState(SUCCESS);
                    break;
                case STATUS_DEFERRED:
                    // STATUS_DEFERRED, may be a failed immediate retrieval.
                    if (mTransactionState.getState() == INITIALIZED) {
                        mTransactionState.setState(SUCCESS);
                    }
                    break;
            }

            sendNotifyRespInd(status);

            // Make sure this thread isn't over the limits in message count.
            Recycler.getMmsRecycler().deleteOldMessagesInSameThreadAsMessage(mContext, mUri);
        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
        } finally {
            mTransactionState.setContentUri(mUri);
            // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
            if(lowMemory){
            	mTransactionState.setState(SUCCESS);
            	Log.e(TAG, "NotificationTransaction lowMemory.");
            }
            // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====

            if (!autoDownload) {

                mTransactionState.setState(SUCCESS);

            } else if (dataSuspended && mTransactionState.getState() != SUCCESS) {

                mTransactionState.setState(TransactionState.FAILED_DATASUSPENDED);

            } else if (mTransactionState.getState() != SUCCESS) {

                mTransactionState.setState(FAILED);
                Log.e(TAG, "NotificationTransaction failed.");
            }

//            if (!autoDownload || dataSuspended) {
//                // Always mark the transaction successful for deferred
//                // download since any error here doesn't make sense.
//                mTransactionState.setState(SUCCESS);
//            }
//            if (mTransactionState.getState() != SUCCESS && mTransactionState.getState() != TransactionState.FAILED_TIMEOUT) {
//                mTransactionState.setState(FAILED);
//                Log.e(TAG, "NotificationTransaction failed.");
//            }
            notifyObservers();
        }
    }

    private void sendNotifyRespInd(int status) throws MmsException, IOException {
        // Create the M-NotifyResp.ind
        NotifyRespInd notifyRespInd = new NotifyRespInd(
                PduHeaders.CURRENT_MMS_VERSION,
                mNotificationInd.getTransactionId(),
                status,
                mPhoneId);

        // Pack M-NotifyResp.ind and send it
        if(MmsConfig.getNotifyWapMMSC()) {
            sendPdu(new PduComposer(mContext, notifyRespInd).make(), mContentLocation);
        } else {
            sendPdu(new PduComposer(mContext, notifyRespInd).make());
        }
    }

    @Override
    public int getType() {
        return NOTIFICATION_TRANSACTION;
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
