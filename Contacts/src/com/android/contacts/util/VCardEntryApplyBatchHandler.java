
package com.android.contacts.util;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.pim.vcard.VCardConfig;
import android.pim.vcard.VCardEntry;
import android.pim.vcard.VCardEntryHandler;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;

/**
 * Batching contacts when import VCard to DB.
 */
public class VCardEntryApplyBatchHandler implements VCardEntryHandler {
    public static String LOG_TAG = "VCardEntryApplyBatchHandler";

    private final ContentResolver mContentResolver;
    private long mTimeToCommit;
    private ArrayList<Uri> mCreatedUris = new ArrayList<Uri>();

    private final static int APPLYBATCH_MAX_VALUE = 20;
    private int applyBatchCount = 0;
    private int totalContactsNum = 0;
    private int rawContactsResultIndex = 0;
    private static final String RAW_CONTACTS_URI = "content://com.android.contacts/raw_contacts";

    private ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

    public VCardEntryApplyBatchHandler(ContentResolver resolver) {
        mContentResolver = resolver;
    }

    public VCardEntryApplyBatchHandler(ContentResolver resolver, int entryCount) {
        this(resolver);
        totalContactsNum = entryCount;
    }

    public void onStart() {
    }

    public void onEnd() {
        if (VCardConfig.showPerformanceLog()) {
            Log.d(LOG_TAG, String.format("time to commit entries: %d ms", mTimeToCommit));
        }
    }

    /**
     * Batching to import contacts, and adding the contacts into DB.
     */
    public void onEntryCreated(final VCardEntry contactStruct) {

        if (contactStruct.isValid()) {
            rawContactsResultIndex = operationList.size();
            contactStruct.pushIntoContentResolverByApplyBatch(mContentResolver, operationList, rawContactsResultIndex);
            applyBatchCount++;
        }

        // Batching to insert contacts to DB per 20 contacts.
        if (applyBatchCount == APPLYBATCH_MAX_VALUE || applyBatchCount == totalContactsNum) {
            try {
                ContentProviderResult[] results = mContentResolver.applyBatch(
                            ContactsContract.AUTHORITY, operationList);
                for(int i = 0; i < results.length; i++) {
                    if(results[i].uri.toString().startsWith(RAW_CONTACTS_URI)) {
                        mCreatedUris.add(results[i].uri);
                    }
                }

                operationList.clear();
                applyBatchCount = 0;
                rawContactsResultIndex = 0;

                // How many contacts have not been imported.
                if (totalContactsNum > APPLYBATCH_MAX_VALUE) {
                    totalContactsNum = totalContactsNum - APPLYBATCH_MAX_VALUE;
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            } catch (OperationApplicationException e) {
                Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            }
        }
    }

    /**
     * Returns the list of created Uris. This list should not be modified by the caller as it is
     * not a clone.
     */
   public ArrayList<Uri> getCreatedUris() {
        return mCreatedUris;
    }

}
