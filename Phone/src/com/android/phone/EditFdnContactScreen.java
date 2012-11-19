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
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import android.os.AsyncResult;
import android.os.Message;

import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.GsmAlphabet;
import android.os.ServiceManager;
	
/**
 * Activity to let the user add or edit an FDN contact.
 */
public class EditFdnContactScreen extends Activity {
    private static final String LOG_TAG = PhoneApp.LOG_TAG;
    private static final boolean DBG = true;

    // Menu item codes
    private static final int MENU_IMPORT = 1;
    private static final int MENU_DELETE = 2;

    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";

    private static final int PIN2_REQUEST_CODE = 100;
    private static final int FOOTER_SIZE_BYTES = 14;

    private String mName;
    private String mNumber;
    private String mPin2;
    private int singleRecordLength;
    private boolean mAddContact;
    private QueryHandler mQueryHandler;

    private EditText mNameField;
    private EditText mNumberField;
    private LinearLayout mPinFieldContainer;
    private Button mButton;
    private Phone mPhone;
    private int mErrorTimes = 0;
   

 // size limits for the pin.
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    private static final int EVENT_PIN2_ENTRY_COMPLETE = 100;
    private static final int MAX_INPUT_TIMES =3;
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

    private int mSubId = 0;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        resolveIntent();
//        mPhone = PhoneFactory.getDefaultPhone();
        mPhone = PhoneApp.getInstance().getPhone(mSubId);
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.edit_fdn_contact_screen);
        setupView();
        querySingleRecord();
        setTitle(mAddContact ?
                R.string.add_fdn_contact : R.string.edit_fdn_contact);

        mDataBusy = false;
	  mErrorTimes  =MAX_INPUT_TIMES;
	  Log.i("FDN","EditFdnContactScreen onCreate ");
    }

    /**
     * We now want to bring up the pin request screen AFTER the
     * contact information is displayed, to help with user
     * experience.
     *
     * Also, process the results from the contact picker.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        if (DBG) log("onActivityResult request:" + requestCode + " result:" + resultCode);

        switch (requestCode) {
            case PIN2_REQUEST_CODE:
                Bundle extras = (intent != null) ? intent.getExtras() : null;
                if (extras != null) {
                    mPin2 = extras.getString("pin2");
                    checkPin2(mPin2);
                } else if (resultCode != RESULT_OK) {
                    // if they cancelled, then we just cancel too.
                    if (DBG) log("onActivityResult: cancelled.");
                    finish();
                }
                break;

            // look for the data associated with this number, and update
            // the display with it.
            case CONTACTS_PICKER_CODE:
                if (resultCode != RESULT_OK) {
                    if (DBG) log("onActivityResult: cancelled.");
                    return;
                }
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(intent.getData(), NUM_PROJECTION, null,
                            null, null);
                    if ((cursor == null) || (!cursor.moveToFirst())) {
                        Log.w(LOG_TAG, "onActivityResult: bad contact data, no results found.");
                        return;
                    }
                    mNameField.setText(cursor.getString(0));
                    mNumberField.setText(cursor.getString(1));
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                break;
        }
    }

    /**
     * Overridden to display the import and delete commands.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Resources r = getResources();

        // Added the icons to the context menu
        menu.add(0, MENU_IMPORT, 0, r.getString(R.string.importToFDNfromContacts))
                .setIcon(R.drawable.ic_menu_contact);
        menu.add(0, MENU_DELETE, 0, r.getString(R.string.menu_delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    /**
     * Allow the menu to be opened ONLY if we're not busy.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        return mDataBusy ? false : result;
    }

    /**
     * Overridden to allow for handling of delete and import.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT:
                startActivityForResult(CONTACT_IMPORT_INTENT, CONTACTS_PICKER_CODE);
                return true;

            case MENU_DELETE:
                deleteSelected();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void resolveIntent() {
        Intent intent = getIntent();

        mName =  intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber =  intent.getStringExtra(INTENT_EXTRA_NUMBER);
        //for ds
        mSubId = intent.getIntExtra(CallSettingOptions.SUB_ID, 0);
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
        mNameField = (EditText) findViewById(R.id.fdn_name);
        if (mNameField != null) {
            mNameField.setOnFocusChangeListener(mOnFocusChangeHandler);
            mNameField.setOnClickListener(mClicked);
        }

        mNumberField = (EditText) findViewById(R.id.fdn_number);
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
//        return Uri.parse("content://icc/fdn");
        return getIntent().getData();
    }

    /**
      * @param number is voice mail number
      * @return true if number length is less than 20-digit limit
      */
     private boolean isValidNumber(String number) {
         if (number.startsWith("+")) {
             number = number.substring(1);
         }
         log("isValidNumber " + number);
         return (number.length() <= 20);
     }

	   /**
     * Validate the pin entry.
     *
     * @param pin This is the pin to validate
     * @param isPuk Boolean indicating whether we are to treat
     * the pin input as a puk.
     */
    private boolean validatePin(String pin, boolean isPUK) {

        // for pin, we have 4-8 numbers, or puk, we use only 8.
        int pinMinimum = isPUK ? MAX_PIN_LENGTH : MIN_PIN_LENGTH;

        // check validity
        if (pin == null || pin.length() < pinMinimum || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }
    /**
     * Display a toast for message, like the rest of the settings.
     */
    private final void displayMessage(int strId) {
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT)
            .show();
    }
    /**
     * check whether pin2 is avaliable
     */
    private void checkPin2(String pin2) {
        Log.i("FDN" , "checkPin2");
 
        if (validatePin (pin2, false)) {
            // get the relevant data for the icc call
            boolean isEnabled = mPhone.getIccCard().getIccFdnEnabled();
            Message onComplete = mHandler.obtainMessage(EVENT_PIN2_ENTRY_COMPLETE);
             Log.i("FDN" , "toggleFDNEnable  isEnabled" +isEnabled);
            // make fdn request
            mPhone.getIccCard().setIccFdnEnabled(isEnabled, pin2, onComplete);
        } else {
            // throw up error if the pin is invalid.
            displayMessage(R.string.invalidPin2);
        }

       
    }


    private void addContact() {
        if (DBG) log("addContact");

        if (!isValidNumber(getNumberFromTextField())) {
            handleResult(false, true);
            return;
        }

        Uri uri = getContentURI();

        ContentValues bundle = new ContentValues(3);
        bundle.put("newTag", getNameFromTextField());
        bundle.put("newNumber", getNumberFromTextField());
        bundle.put("pin2", mPin2);


        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startInsert(0, null, uri, bundle);
        FDNInfo.addFdn(getNumberFromTextField(), mSubId);
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
        bundle.put("pin2", mPin2);

        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startUpdate(0, null, uri, bundle, null, null);
        FDNInfo.updateFdn(mNumber,getNumberFromTextField(), mSubId);
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
            intent.setData(getContentURI());
            intent.putExtra(CallSettingOptions.SUB_ID, mSubId);
            startActivity(intent);
        }
        finish();
    }

    private void authenticatePin2() {
     
	  Intent intent = new Intent();
        intent.setClass(this, GetPin2Screen.class);
		
	  intent.putExtra("times",mErrorTimes );
        startActivityForResult(intent, PIN2_REQUEST_CODE);
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
            Toast.makeText(getApplicationContext(), statusMsg, Toast.LENGTH_SHORT)
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
            if(isExceedNameMaxLength(getNameFromTextField())){
                showStatus(getResources().getText(R.string.toast_message_name_too_long));
            } else if (invalidNumber)
                showStatus(getResources().getText(R.string.fdn_invalid_number));
            else{
                showStatus(getResources().getText(R.string.pin2_invalid));
            }
            
        }

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 2000);

    }

    private boolean isExceedNameMaxLength(String name) {
        if (!TextUtils.isEmpty(name)) {
            int maxLength = singleRecordLength - FOOTER_SIZE_BYTES;
            byte[] byteTag;
            String ecode = GsmAlphabet.getStringPreferEncode(name);
            if ("ucs2".equals(ecode)) { // ucs2 coding
                try {
                    byteTag = name.getBytes("utf-16be");
                    if ((byteTag.length + 1) > maxLength) {
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ucs2 encode validate the sim name max length error!", e);
                }
            } else { // gsm coding
                byteTag = GsmAlphabet.stringToGsm8BitPacked(name);
                if (byteTag.length > maxLength) {
                    return true;
                }
            }
        }
        return false;
    }

    private void querySingleRecord() {
        new Thread(){
            public void run() {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService("simphonebook"));
                try {
                    int recordSizes[] = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                    singleRecordLength = recordSizes[0];
                } catch (Exception e) {
                    Log.e(LOG_TAG, "query single record error!", e);
                }
            };
        }.start();
    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPinFieldContainer.getVisibility() != View.VISIBLE) {
                return;
            }

            if (v == mNameField) {
              //  mNumberField.requestFocus(); for bug 11078
            } else if (v == mNumberField) {
                mButton.requestFocus();
            } else if (v == mButton) {
                // Authenticate the pin AFTER the contact information
                // is entered, and if we're not busy.
                int nameLength = mNameField.getText().toString().trim().length();
                int numberLength = mNumberField.getText().toString().trim().length();
                if(nameLength !=0 || numberLength != 0){
                    if (!mDataBusy) {
                        authenticatePin2();
                    }
                }else{
                    showStatus(getResources().getText(R.string.toast_message_name_number_null));
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


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                // when we are enabling FDN, either we are unsuccessful and display
                // a toast, or just update the UI.
                case EVENT_PIN2_ENTRY_COMPLETE: {
			    Log.i("FDN" ," EVENT_PIN2_ENTRY_COMPLETE");
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
              		     Log.i("FDN" ," EVENT_PIN2_ENTRY_COMPLETE mErrorTimes" +mErrorTimes);		
                               mErrorTimes -=1;
                               displayMessage(R.string.pin2_invalid);

					Log.i("FDN" ," EVENT_PIN2_ENTRY_COMPLETE mErrorTimes" +mErrorTimes);		   
				      if(mErrorTimes > 0){
					  	
                                     authenticatePin2();
				       }else{

                                      finish();

					}	
                            
                        }else{
                              if (mAddContact) {
                                     addContact();
                              } else {
                                     updateContact();
                              }
                        }
                    }
                    break;

               
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
