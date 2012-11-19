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

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import android.os.AsyncResult;
import android.os.Message;
import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

/**
 * Activity to let the user delete an FDN contact.
 */
public class DeleteFdnContactScreen extends Activity {
    private static final String LOG_TAG = PhoneApp.LOG_TAG;
    private static final boolean DBG = false;

    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";
    // size limits for the pin.
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    private static final int EVENT_PIN2_ENTRY_COMPLETE = 100;
    private static final int MAX_INPUT_TIMES =3;

    private static final int PIN2_REQUEST_CODE = 100;

    private String mName;
    private String mNumber;
    private String mPin2;
    private Phone mPhone;
    private int mErrorTimes = 0;
    protected QueryHandler mQueryHandler;
    private int mSubId = 0;


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        resolveIntent();
        mErrorTimes = MAX_INPUT_TIMES;
        authenticatePin2();
//        mPhone = PhoneFactory.getDefaultPhone();
        mPhone = PhoneApp.getInstance().getPhone(mSubId);
	
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.delete_fdn_contact_screen);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        if (DBG) log("onActivityResult");

        switch (requestCode) {
            case PIN2_REQUEST_CODE:
                Bundle extras = (intent != null) ? intent.getExtras() : null;
                if (extras != null) {
                    mPin2 = extras.getString("pin2");
			 checkPin2(mPin2);
                  
                   
                } else {
                    // if they cancelled, then we just cancel too.
                    if (DBG) log("onActivityResult: CANCELLED");
                    displayProgress(false);
                    finish();
                }
                break;
        }
    }
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
    private void resolveIntent() {
        Intent intent = getIntent();

        mName =  intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber =  intent.getStringExtra(INTENT_EXTRA_NUMBER);
        //for ds
        mSubId  = intent.getIntExtra(CallSettingOptions.SUB_ID, 0);
        //NEWMS00186476
        //if (TextUtils.isEmpty(mNumber)) {
        //    finish();
        //}
    }

    private void deleteContact() {
        //StringBuilder buf = new StringBuilder();
        //buf.append("tag='");
        //buf.append(mName);
        //buf.append("' AND number='");
        //buf.append(mNumber);
        //buf.append("' AND pin2='");
        //buf.append(mPin2);
        //buf.append("'");
	  String[] whereArgs = new String[5];
	  whereArgs[0] = mName;
	  whereArgs[1] = mNumber;
  	  whereArgs[2] = "";
	  whereArgs[3] = "";
        whereArgs[4] = mPin2;
//        Uri uri = Uri.parse("content://icc/fdn");
        Uri uri = getIntent().getData();

        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startDelete(0, null, uri, null, whereArgs);
        FDNInfo.removeFdn(mNumber, mSubId);
        displayProgress(true);
    }

    private void authenticatePin2() {
        Intent intent = new Intent();
	  intent.putExtra("times",mErrorTimes );
        intent.setClass(this, GetPin2Screen.class);
        startActivityForResult(intent, PIN2_REQUEST_CODE);
    }

    private void displayProgress(boolean flag) {
        getWindow().setFeatureInt(
                Window.FEATURE_INDETERMINATE_PROGRESS,
                flag ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
    }

    // Replace the status field with a toast to make things appear similar
    // to the rest of the settings.  Removed the useless status field.
    private void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT)
            .show();
        }
    }
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
                             showStatus(getResources().getText(
                                R.string.deleting_fdn_contact));
                             deleteContact();
                        }
                    }
                    break;

               
            }
        }
    };
    private void handleResult(boolean success) {
        if (success) {
            if (DBG) log("handleResult: success!");
            showStatus(getResources().getText(R.string.fdn_contact_deleted));
        } else {
            if (DBG) log("handleResult: failed!");
            showStatus(getResources().getText(R.string.pin2_invalid));
        }

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 2000);

    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
        }

        protected void onInsertComplete(int token, Object cookie,
                                        Uri uri) {
        }

        protected void onUpdateComplete(int token, Object cookie, int result) {
        }

        protected void onDeleteComplete(int token, Object cookie, int result) {
            if (DBG) log("onDeleteComplete");
            displayProgress(false);
            handleResult(result > 0);
        }

    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[DeleteFdnContact] " + msg);
    }
}
