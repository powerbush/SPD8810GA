package com.android.phone;
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

import java.util.ArrayList;
import java.lang.Integer;
import com.android.phone.R;


import android.database.sqlite.SqliteWrapper;
//import com.android.mms.transaction.MessagingNotification;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.RadioButton;
import android.widget.ListView;
import android.widget.TextView;

import android.widget.Button;
import android.widget.EditText;
import android.text.TextUtils;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.DialerKeyListener;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import android.os.AsyncResult;

import android.os.Message;
import com.android.internal.telephony.gsm.SmsCBMessage;
/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsSettingTestActivity extends Activity {
	
	
    private static final String TAG = "CellBroadcastSmsSettingTestActivity";

	

	
    private RadioButton mPdu;
    private RadioButton mText;
    private Button mButton;
    private TextView mConfigText;
	private MyHandler mHandler;
    private int mIsPdu = 0;
    private Phone mPhone;
    private static final int MESSAGE_GET_CB_SMS_CONFIG = 2;
	private String COLUMN_MODE = "mode";
	private long buttonClickStratTime = 0;
	private int mPhoneId = 0 ;
    /**
     * We have multiple layouts, one to indicate that the user needs to
     * open the keyboard to enter information (if the keybord is hidden).
     * So, we need to make sure that the layout here matches that in the
     * layout file.
     */

	@Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(TAG, "onCreate" );
       
        resolveIntent();
       
        setContentView(R.layout.cell_broadcast_test);
        mPdu = (RadioButton) findViewById(R.id.pdu_button);    	
        mText =( RadioButton) findViewById(R.id.text_button);
        mButton = (Button) findViewById(R.id.config_button);    	
        mConfigText =( TextView) findViewById(R.id.cb_config_text);
        
        mPdu.setOnClickListener(mClicked);
        
     
        mText.setOnClickListener(mClicked);
        mButton.setOnClickListener(mClicked);
    	mHandler = new MyHandler();
    	mPhoneId = getIntent().getExtras().getInt("phoneid");
    	mPhone = PhoneApp.getInstance().getPhone(mPhoneId);

		Log.i(TAG, "onCreate " + mPhone.getPhoneName());
	
        if(mIsPdu == 0){
        	
        	mPdu.setChecked(true);
        	mText.setChecked(false);
        }else{
        	mPdu.setChecked(false);
        	mText.setChecked(true);
        	
        }
        
        setTitle(R.string.list_language_title);
    }
	private void resolveIntent() {
		Intent intent = getIntent();

		mIsPdu = intent.getIntExtra(COLUMN_MODE, 0);
		

		Log.i(TAG, "onCreate mMode :" +mIsPdu );
	
	}

    
    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
       
       Log.i(TAG, "OnClickListener");
       
    	   if(v == mPdu){
    		   
    		   Log.i(TAG, "OnClickListener : " +"checked  "+mPdu.isChecked() );  
    		   if(mPdu.isChecked()){
    			   
    			  mIsPdu =  0;
    			  SmsCBMessage.setSmsCBMode(true); 
    		   }
    	   }
    	   
    	   
    	   
           if(v == mText){
    		   
    		   Log.i(TAG, "OnClickListener : " +"checked  "+mText.isChecked() );  
    		   if(mText.isChecked()){
    			   
    			  mIsPdu =  1;
    			  SmsCBMessage.setSmsCBMode(false); 
    			   
    		   }
    	   }
    	    
           Log.i(TAG, "resolveIntent set mLang =: "+ mIsPdu);  
		   getIntent().putExtra(COLUMN_MODE,mIsPdu);
   		   setResult(30,getIntent());
          
            if (v == mButton) {

                if ((System.currentTimeMillis() - buttonClickStratTime) > 5000) {
                    Log.i(TAG, "OnClickListener :  button ");
                    Message msg = Message.obtain(mHandler, MESSAGE_GET_CB_SMS_CONFIG);
                    mPhone.getCellBroadcastSmsConfig(msg);
                    buttonClickStratTime = System.currentTimeMillis();
                }
            }
        }

    };

    private class MyHandler extends Handler {

		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MESSAGE_GET_CB_SMS_CONFIG:
                    AsyncResult ar = null;
                    try {
                        ar = (AsyncResult) msg.obj;
                    } catch (ClassCastException e) {
                        Log.e(TAG, "ClassCastException" );
                    }

                    if (ar == null) {
                        break;
                    }

                    if (ar.exception != null) {
                        Log.e(TAG, "Exception processing incoming SMSCB. Exception:" + ar.exception);
                        break;
                    }

                    String result = (String) ar.result;

                    // String result =
                    // (String) ((AsyncResult) msg.obj).result;

                    CharSequence displayStr = "Cell broadcast config :" + result;

                    mConfigText.setText(displayStr);
                    mConfigText.setVisibility(View.VISIBLE);

                    break;

                default:
                    Log.e(TAG, "Error! Unhandled message in CellBroadcastSms.java. Message: "
                            + msg.what);
                    break;
            }
        }
    }



  
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        
    }
 


 


	
}
