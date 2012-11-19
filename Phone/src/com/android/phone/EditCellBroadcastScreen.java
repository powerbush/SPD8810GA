/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.PhonesColumns;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity to let the user add or edit an FDN contact.
 */
public class EditCellBroadcastScreen extends Activity {
    private static final String LOG_TAG = PhoneApp.LOG_TAG;
    private static final boolean DBG = false;


    private EditText mNameField;
    private EditText mNumberField;
    private LinearLayout mPinFieldContainer;
    private Button mButton;
    private boolean mAddContact;
    private Handler mHandler = new Handler();
    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";
    private String mName;
    private String mNumber;

    private QueryHandler mQueryHandler;
    /**
     * Constants used in importing from contacts
     */
    /** request code when invoking subactivity */
    private static final int CONTACTS_PICKER_CODE = 200;
    /** projection for phone number query */
    private static final String NUM_PROJECTION[] = {PeopleColumns.DISPLAY_NAME,
        PhonesColumns.NUMBER};
    /** static intent to invoke phone number picker */
    private static final Intent CONTACT_IMPORT_INTENT;
    static {
        CONTACT_IMPORT_INTENT = new Intent(Intent.ACTION_GET_CONTENT);
        CONTACT_IMPORT_INTENT.setType(android.provider.Contacts.Phones.CONTENT_ITEM_TYPE);
    }
    /** flag to track saving state */
    private boolean mDataBusy;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        resolveIntent();
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.cellbroadcast_edit);
        setupView();
        setTitle(mAddContact ?
                R.string.add_fdn_contact : R.string.edit_fdn_contact);

        mDataBusy = false;
    }

    private void resolveIntent() {
        Intent intent = getIntent();

        mName =  intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber =  intent.getStringExtra(INTENT_EXTRA_NUMBER);

        if (TextUtils.isEmpty(mName) && TextUtils.isEmpty(mNumber)) {
            mAddContact = true;
        }
    }


    /**
     * We have multiple layouts, one to indicate that the user needs to
     * open the keyboard to enter information (if the keybord is hidden).
     * So, we need to make sure that the layout here matches that in the
     * layout file.
     */
    private void setupView() {
        mNameField = (EditText) findViewById(R.id.channel_name_edit);
        if (mNameField != null) {
            mNameField.setOnFocusChangeListener(mOnFocusChangeHandler);
            mNameField.setOnClickListener(mClicked);
        }

        mNumberField = (EditText) findViewById(R.id.channel_number_edit);
        if (mNumberField != null) {
            mNumberField.setKeyListener(DialerKeyListener.getInstance());
            mNumberField.setOnFocusChangeListener(mOnFocusChangeHandler);
            mNumberField.setOnClickListener(mClicked);
        }

        if (!mAddContact) {
            if (mNameField != null) {
                mNameField.setText(mName);
            }
            if (mNumberField != null) {
                mNumberField.setText(mNumber);
            }
        }

        mButton = (Button) findViewById(R.id.button);
        if (mButton != null) {
            mButton.setOnClickListener(mClicked);
        }

        mPinFieldContainer = (LinearLayout) findViewById(R.id.pinc);

    }

    private String getNameFromTextField() {
        return mNameField.getText().toString();
    }

    private String getNumberFromTextField() {
        return mNumberField.getText().toString();
    }

    private Uri getContentURI() {
        return Uri.parse("content://icc/fdn");
    }

    /**
      * @param number is voice mail number
      * @return true if number length is less than 20-digit limit
      */
     private boolean isValidNumber(String number) {
         return (number.length() <= 20);
     }


    private void addContact() {
        if (DBG) log("addContact");

        if (!isValidNumber(getNumberFromTextField())) {
            handleResult(false, true);
            return;
        }

        Uri uri = getContentURI();

        ContentValues bundle = new ContentValues(3);
        bundle.put("tag", getNameFromTextField());
        bundle.put("number", getNumberFromTextField());
    


        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startInsert(0, null, uri, bundle);
        displayProgress(true);
        showStatus(getResources().getText(R.string.adding_fdn_contact));
    }

    private void updateContact() {
        if (DBG) log("updateContact");

        if (!isValidNumber(getNumberFromTextField())) {
            handleResult(false, true);
            return;
        }
        Uri uri = getContentURI();

        ContentValues bundle = new ContentValues();
        bundle.put("tag", mName);
        bundle.put("number", mNumber);
        bundle.put("newTag", getNameFromTextField());
        bundle.put("newNumber", getNumberFromTextField());
    

        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startUpdate(0, null, uri, bundle, null, null);
        displayProgress(true);
        showStatus(getResources().getText(R.string.updating_fdn_contact));
    }

    /**
     * Handle the delete command, based upon the state of the Activity.
     */
    private void deleteSelected() {
        // delete ONLY if this is NOT a new contact.
        if (!mAddContact) {
            Intent intent = new Intent();
            intent.setClass(this, DeleteFdnContactScreen.class);
            intent.putExtra(INTENT_EXTRA_NAME, mName);
            intent.putExtra(INTENT_EXTRA_NUMBER, mNumber);
            startActivity(intent);
        }
        finish();
    }



    private void displayProgress(boolean flag) {
        // indicate we are busy.
        mDataBusy = flag;
        getWindow().setFeatureInt(
                Window.FEATURE_INDETERMINATE_PROGRESS,
                mDataBusy ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
        // make sure we don't allow calls to save when we're
        // not ready for them.
        mButton.setClickable(!mDataBusy);
    }

    /**
     * Removed the status field, with preference to displaying a toast
     * to match the rest of settings UI.
     */
    private void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT)
            .show();
        }
    }

    private void handleResult(boolean success, boolean invalidNumber) {
        if (success) {
            if (DBG) log("handleResult: success!");
            showStatus(getResources().getText(mAddContact ?
                    R.string.fdn_contact_added : R.string.fdn_contact_updated));
        } else {
            if (DBG) log("handleResult: failed!");
            if (invalidNumber)
                showStatus(getResources().getText(R.string.fdn_invalid_number));
            else
                showStatus(getResources().getText(R.string.pin2_invalid));
        }

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 2000);

    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPinFieldContainer.getVisibility() != View.VISIBLE) {
                return;
            }

            if (v == mNameField) {
                mNumberField.requestFocus();
            } else if (v == mNumberField) {
                mButton.requestFocus();
            } else if (v == mButton) {
                // Authenticate the pin AFTER the contact information
                // is entered, and if we're not busy.
                if (!mDataBusy) {
                    
                }
            }
        }
    };

    View.OnFocusChangeListener mOnFocusChangeHandler =
            new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                TextView textView = (TextView) v;
                Selection.selectAll((Spannable) textView.getText());
            }
        }
    };

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
        }

        @Override
        protected void onInsertComplete(int token, Object cookie,
                                        Uri uri) {
            if (DBG) log("onInsertComplete");
            displayProgress(false);
            handleResult(uri != null, false);
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            if (DBG) log("onUpdateComplete");
            displayProgress(false);
            handleResult(result > 0, false);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[EditFdnContact] " + msg);
    }
}
