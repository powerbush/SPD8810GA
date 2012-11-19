/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.android.contacts;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.pim.vcard.VCardEntry;
import android.pim.vcard.VCardEntryHandler;
import android.pim.vcard.VCardConfig;
import android.util.Log;

public class ProgressShower implements VCardEntryHandler {
    public static final String LOG_TAG = "vcard.ProgressShower"; 

    private final Context mContext;
    private final Handler mHandler;
    private final ProgressDialog mProgressDialog;
    private final String mProgressMessage;
    private final boolean mShowEntryParseProgress;
    private final int mEntryCount;
    private int mCreateCount = 0;

    private long mTime;
    
    private class ShowProgressRunnable implements Runnable {
        private VCardEntry mContact;
        
        public ShowProgressRunnable(VCardEntry contact) {
            mContact = contact;
        }
        
        public void run() {
            String message = mProgressMessage;
            if (mShowEntryParseProgress) {
                mProgressDialog.incrementProgressBy(1);
            } else {
                message += "\n" + mContext.getString(
                        R.string.reading_vcard_contacts, mCreateCount,mEntryCount);
            }
            mProgressDialog.setMessage( message + "\n" + 
                    mContact.getDisplayName());
        }
    }
    
    public ProgressShower(ProgressDialog progressDialog,
            String progressMessage,
            Context context,
            Handler handler,
            boolean showEntryParseProgress,
            int entryCount) {
        mContext = context;
        mHandler = handler;
        mProgressDialog = progressDialog;
        mProgressMessage = progressMessage;
        mShowEntryParseProgress = showEntryParseProgress;
        mEntryCount = entryCount;
    }

    public void onStart() {
    }

    public void onEntryCreated(VCardEntry contactStruct) {
        long start = System.currentTimeMillis();
        
        if (!contactStruct.isIgnorable()) {
            if (mProgressDialog != null && mProgressMessage != null) {
                mCreateCount++;
                if (mHandler != null) {
                    mHandler.post(new ShowProgressRunnable(contactStruct));
                } else {
                    String message = mProgressMessage;
                    if (!mShowEntryParseProgress) {
                        message += "\n" + mContext.getString(
                                R.string.reading_vcard_contacts, mCreateCount,mEntryCount);
                    }
                    mProgressDialog.setMessage(mContext.getString(R.string.progress_shower_message,
                            message, contactStruct.getDisplayName()));
                }
            }
        }
        
        mTime += System.currentTimeMillis() - start;
    }

    public void onEnd() {
        if (VCardConfig.showPerformanceLog()) {
            Log.d(LOG_TAG,
                    String.format("Time to progress a dialog: %d ms", mTime));
        }
    }
}
