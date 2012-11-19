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

import com.android.mms.util.DownloadManager;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.pdu.PduHeaders;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.StatFs;
import android.provider.Settings;


import com.android.mms.util.SendingProgressTokenManager;

/**
 * Transaction is an abstract class for notification transaction, send transaction
 * and other transactions described in MMS spec.
 * It provides the interfaces of them and some common methods for them.
 */
public abstract class Transaction extends Observable {
    private static final String TAG = "Mms/Transaction";
    private final int mServiceId;

    protected Context mContext;
    protected String mId;
    protected String mContentLocation;
    protected TransactionState mTransactionState;
    protected TransactionSettings mTransactionSettings;
    protected int mPhoneId;

    /**
     * Identifies push requests.
     */
    public static final int NOTIFICATION_TRANSACTION = 0;
    /**
     * Identifies deferred retrieve requests.
     */
    public static final int RETRIEVE_TRANSACTION     = 1;
    /**
     * Identifies send multimedia message requests.
     */
    public static final int SEND_TRANSACTION         = 2;
    /**
     * Identifies send read report requests.
     */
    public static final int READREC_TRANSACTION      = 3;

    public Transaction(Context context, int serviceId,
            TransactionSettings settings, int phoneId) {
        mContext = context;
        mTransactionState = new TransactionState();
        mServiceId = serviceId;
        mTransactionSettings = settings;
        mPhoneId = phoneId;
    }

    /**
     * Returns the transaction state of this transaction.
     *
     * @return Current state of the Transaction.
     */
    @Override
    public TransactionState getState() {
        return mTransactionState;
    }

    /**
     * An instance of Transaction encapsulates the actions required
     * during a MMS Client transaction.
     */
    public abstract void process();

    /**
     * Used to determine whether a transaction is equivalent to this instance.
     *
     * @param transaction the transaction which is compared to this instance.
     * @return true if transaction is equivalent to this instance, false otherwise.
     */
    public boolean isEquivalent(Transaction transaction) {
        return getClass().equals(transaction.getClass())
                && mId.equals(transaction.mId);
    }

    /**
     * Get the service-id of this transaction which was assigned by the framework.
     * @return the service-id of the transaction
     */
    public int getServiceId() {
        return mServiceId;
    }

    public TransactionSettings getConnectionSettings() {
        return mTransactionSettings;
    }
    public void setConnectionSettings(TransactionSettings settings) {
        mTransactionSettings = settings;
    }

    /**
     * A common method to send a PDU to MMSC.
     *
     * @param pdu A byte array which contains the data of the PDU.
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(>=400) returned from the server.
     */
    protected byte[] sendPdu(byte[] pdu) throws IOException {
        return sendPdu(SendingProgressTokenManager.NO_TOKEN, pdu,
                mTransactionSettings.getMmscUrl());
    }

    /**
     * A common method to send a PDU to MMSC.
     *
     * @param pdu A byte array which contains the data of the PDU.
     * @param mmscUrl Url of the recipient MMSC.
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(>=400) returned from the server.
     */
    protected byte[] sendPdu(byte[] pdu, String mmscUrl) throws IOException {
        return sendPdu(SendingProgressTokenManager.NO_TOKEN, pdu, mmscUrl);
    }

    /**
     * A common method to send a PDU to MMSC.
     *
     * @param token The token to identify the sending progress.
     * @param pdu A byte array which contains the data of the PDU.
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(>=400) returned from the server.
     */
    protected byte[] sendPdu(long token, byte[] pdu) throws IOException {
        return sendPdu(token, pdu, mTransactionSettings.getMmscUrl());
    }

    /**
     * A common method to send a PDU to MMSC.
     *
     * @param token The token to identify the sending progress.
     * @param pdu A byte array which contains the data of the PDU.
     * @param mmscUrl Url of the recipient MMSC.
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(>=400) returned from the server.
     */
    protected byte[] sendPdu(long token, byte[] pdu, String mmscUrl) throws IOException {
        ensureRouteToHost(mmscUrl, mTransactionSettings);
        return HttpUtils.httpConnection(
                mContext, token,
                mmscUrl,
                pdu, HttpUtils.HTTP_POST_METHOD,
                mTransactionSettings.isProxySet(),
                mTransactionSettings.getProxyAddress(),
                mTransactionSettings.getProxyPort(),
                mPhoneId);
    }

    /**
     * A common method to retrieve a PDU from MMSC.
     *
     * @param url The URL of the message which we are going to retrieve.
     * @return A byte array which contains the data of the PDU.
     *         If the status code is not correct, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(>=400) returned from the server.
     */
    protected byte[] getPdu(String url) throws IOException {
    	Log.i(TAG,"[Transaction]mTransactionSettings.getProxyAddress().:"+mTransactionSettings.getProxyAddress());
    	Log.i(TAG,"[Transaction]mTransactionSettings.getProxyPort().:"+mTransactionSettings.getProxyPort());
    	Log.i(TAG,"[Transaction]mTransactionSettings.isProxySet().:"+mTransactionSettings.isProxySet());
    	Log.i(TAG,"[Transaction]mPhoneId:"+mPhoneId);
        ensureRouteToHost(url, mTransactionSettings);
        return HttpUtils.httpConnection(
                mContext, SendingProgressTokenManager.NO_TOKEN,
                url, null, HttpUtils.HTTP_GET_METHOD,
                mTransactionSettings.isProxySet(),
                mTransactionSettings.getProxyAddress(),
                mTransactionSettings.getProxyPort(),
                mPhoneId);
    }

    /**
     * Make sure that a network route exists to allow us to reach the host in the
     * supplied URL, and to the MMS proxy host as well, if a proxy is used.
     * @param url The URL of the MMSC to which we need a route
     * @param settings Specifies the address of the proxy host, if any
     * @throws IOException if the host doesn't exist, or adding the route fails.
     */
    private void ensureRouteToHost(String url, TransactionSettings settings) throws IOException {
        ConnectivityManager connMgr =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        int inetAddr;
        if (settings.isProxySet()) {
            String proxyAddr = settings.getProxyAddress();
            inetAddr = lookupHost(proxyAddr);
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.getMmsTypeByPhoneId(settings.getPhoneId()), inetAddr)) {
                    throw new IOException("Cannot establish route to proxy " + inetAddr);
                }
            }
        } else {
            Uri uri = Uri.parse(url);
            inetAddr = lookupHost(uri.getHost());
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.getMmsTypeByPhoneId(settings.getPhoneId()), inetAddr)) {
                    throw new IOException("Cannot establish route to " + inetAddr + " for " + url);
                }
            }
        }
    }

    /**
     * Look up a host name and return the result as an int. Works if the argument
     * is an IP address in dot notation. Obviously, this can only be used for IPv4
     * addresses.
     * @param hostname the name of the host (or the IP address)
     * @return the IP address as an {@code int} in network byte order
     */
    // TODO: move this to android-common
    public static int lookupHost(String hostname) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }
        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                |  (addrBytes[0] & 0xff);
        return addr;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": serviceId=" + mServiceId + " id=" + mId;
    }

    /**
     * Get the type of the transaction.
     *
     * @return Transaction type in integer.
     */
    abstract public int getType();
    
    
    // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 begin=====
    private final static int MEMORY_BUFFER_SIZE = 500 * 1024;
    private static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;
    private static final String DATA_PATH = "/data";
    protected boolean isLowMemory(){
		StatFs statfs = new StatFs(DATA_PATH);
		int value = Settings.Secure.getInt(mContext.getContentResolver(),
				Settings.Secure.SYS_STORAGE_THRESHOLD_PERCENTAGE,
				DEFAULT_THRESHOLD_PERCENTAGE);
		long totalMemory = ((long) statfs.getBlockCount() * statfs
				.getBlockSize()) / 100L;
		long thresholdMemory = totalMemory * value + MEMORY_BUFFER_SIZE;
		statfs.restat(DATA_PATH);
		long availMemory = (long) statfs.getAvailableBlocks()
				* statfs.getBlockSize();
		return availMemory < thresholdMemory;
    }
    // ===== fixed CR<NEWMS00118858> by luning at 11-10-17 end=====
    abstract public void makeFailure();

    public void makeFailure(Uri uri) {
        Log.d("Transaction", "make transaction failure .");
        mTransactionState.setState(TransactionState.FAILED);
        mTransactionState.setContentUri(uri);
        Uri.Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        uriBuilder.appendQueryParameter("message", String.valueOf(ContentUris.parseId(uri)));
        ContentValues values = new ContentValues(4);
        values.put(PendingMessages.ERROR_TYPE, MmsSms.ERR_TYPE_GENERIC_PERMANENT);
        DefaultRetryScheme scheme = new DefaultRetryScheme(mContext, 0);
        values.put(PendingMessages.RETRY_INDEX, scheme.getRetryLimit());
        values.put(PendingMessages.LAST_TRY, System.currentTimeMillis());
        Cursor cursor = SqliteWrapper.query(mContext, mContext.getContentResolver(),
                uriBuilder.build(), null, null, null, null);
        try {
            if (cursor != null) {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    int msgType = cursor.getInt(cursor
                            .getColumnIndexOrThrow(PendingMessages.MSG_TYPE));
                    boolean isRetryDownloading = (msgType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
                    if (isRetryDownloading) {
                        Cursor c = SqliteWrapper.query(mContext, mContext.getContentResolver(),
                                uri, new String[] {
                                    Mms.THREAD_ID
                                }, null, null, null);

                        long threadId = -1;
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    threadId = c.getLong(0);
                                }
                            } finally {
                                c.close();
                            }
                        }

                        if (threadId != -1) {
                            // Downloading process is permanently failed.
                            MessagingNotification.notifyDownloadFailed(mContext, threadId, true);
                        }

                        DownloadManager.getInstance().markState(uri,
                                DownloadManager.STATE_PERMANENT_FAILURE, mPhoneId);
                    } else {
                        // Mark the failed message as unread.
                        ContentValues readValues = new ContentValues(1);
                        readValues.put(Mms.READ, 0);
                        readValues.put(Mms.RESPONSE_STATUS,
                                PduHeaders.RESPONSE_STATUS_ERROR_NETWORK_PROBLEM);
                        SqliteWrapper.update(mContext, mContext.getContentResolver(), uri,
                                readValues, null, null);
                        MessagingNotification.notifySendFailed(mContext, true);
                    }
                    int columnIndex = cursor.getColumnIndexOrThrow(PendingMessages._ID);
                    long id = cursor.getLong(columnIndex);
                    SqliteWrapper.update(mContext, mContext.getContentResolver(),
                            PendingMessages.CONTENT_URI, values, PendingMessages._ID + "=" + id,
                            null);
                }
            }

        } finally {
            cursor.close();
        }
    }
}
