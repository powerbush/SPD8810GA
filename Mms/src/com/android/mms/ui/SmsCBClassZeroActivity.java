/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import android.text.TextUtils;
import android.util.Log;
import android.util.Config;
import android.view.Window;

import com.android.mms.R;

import com.android.mms.transaction.MessagingNotification;

import android.database.sqlite.SqliteWrapper;
import com.android.internal.telephony.gsm.SmsCBMessage.SmsCBPage;
import android.content.Context;
import com.android.mms.ui.CellBroadcastSmsActivity;

/**
 * Display a class-zero SMS message to the user. Wait for the user to dismiss
 * it.
 */
public class SmsCBClassZeroActivity extends Activity {
    private static final String BUFFER = " ";
    private static final String TAG = "SmsCBClassZeroActivity";
    private static final int BUFFER_OFFSET = BUFFER.length() * 2;

    private static final int ON_AUTO_SAVE = 1;
    private static final Uri CBSMS_URI_T = Uri.parse("content://sms/cbsms");
    private String COLUMN_ID = "_id";
    private String COLUMN_ADDRESS = "address";
    private String COLUMN_BODY = "body";
    private String COLUMN_DATE = "date";
    private String COLUMN_READ = "read";
    private String COLUMN_SEEN = "seen";
	private String COLUMN_ICONID = "iconId";
	private String COLUMN_LANGID = "langId";
	private int DEFAULT_ICON = R.drawable.unread_cbsms;
	private int READ_ICON = R.drawable.read_cbsms;
    


    /** Default timer to dismiss the dialog. */
    private static final long DEFAULT_TIMER = 5 * 60 * 1000;

    /** To remember the exact time when the timer should fire. */
    private static final String TIMER_FIRE = "timer_fire";

    private SmsCBPage[] mMessage;

    /** Is the message read. */
    private boolean mRead = false;

    /** The timer to dismiss the dialog automatically. */
    private long mTimerSet = 0;
    private AlertDialog mDialog = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Do not handle an invalid message.
            if (msg.what == ON_AUTO_SAVE) {
                mRead = false;
                mDialog.dismiss();
                saveMessage();
                finish();
            }
        }
    };

	private void saveMessage() {
		Uri messageUri = null;
		// if (mMessage.isReplace()) {
		// messageUri = replaceMessage(mMessage);
		// } else
		{
			messageUri = storeMessage(this, mMessage, 0);
		}

		if (messageUri != null) {

			notifyListChanged();
			if (!mRead) {
				MessagingNotification.nonBlockingUpdateNewMessageIndicator(
						this, true, false);

			}
		}
	}

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(
                R.drawable.class_zero_background);

        int length = getIntent().getIntExtra("length",0);
        
        Log.i(TAG,"onCreate length :"+ length);
        mMessage = new SmsCBPage[length];
        
		for(int i=0; i<length; i++){
			
			
			mMessage[i] = new SmsCBPage();
			String gsS = "gs"+i;
			mMessage[i].gs = getIntent().getIntExtra(gsS,0);
			String messageCodeS = "messageCode"+i;
			mMessage[i].messageCode= getIntent().getIntExtra(messageCodeS,0);
			String updateNumS = "updateNum"+i;
			mMessage[i].updateNum =getIntent().getIntExtra(updateNumS,0);
			String msgIdS = "msgId"+i;
			mMessage[i].msgId=getIntent().getIntExtra(msgIdS,0);
			Log.i(TAG, "storeMessage : msgId " +mMessage[i].msgId);
			String dcsS = "dcs"+i;
			mMessage[i].dcs= getIntent().getIntExtra(dcsS,0);
			String sequenceNumS = "sequenceNum"+i;
			mMessage[i].sequenceNum = getIntent().getIntExtra(sequenceNumS,0);
			String totalNumS = "totalNum"+i;
			mMessage[i].totalNum =getIntent().getIntExtra(totalNumS,0);
			String langIdS = "langId"+i;
			mMessage[i].langId = getIntent().getIntExtra(langIdS,0);
			 Log.i(TAG,"onCreate content :"+ mMessage[i].langId );
			String contentS = "content"+i;
			mMessage[i].content = getIntent().getStringExtra(contentS);
			 Log.i(TAG,"onCreate content :"+ mMessage[i].content );
			
		}
		
         int pageCount = length;
         StringBuilder body = new StringBuilder();	         
			
         for (int i = 0; i < pageCount; i++) {
     
                body.append(mMessage[i].content);
            }
            
        Log.i(TAG,"onCreate content "+body.toString());
       

	    CharSequence messageChars ="";
	    
	    String message = body.toString();
        
        if (TextUtils.isEmpty(message)) {
            finish();
            return;
        }
        // TODO: The following line adds an emptry string before and after a message.
        // This is not the correct way to layout a message. This is more of a hack
        // to work-around a bug in AlertDialog. This needs to be fixed later when
        // Android fixes the bug in AlertDialog.
        Log.i(TAG,"onCreate message "+message);
        messageChars = messageChars +"("+ getLangName(mMessage[0].langId)+" "+getString(R.string.language_type)  +")"+"\n\n"+message;
        if (message.length() < BUFFER_OFFSET) messageChars = BUFFER + message + BUFFER;
        long now = SystemClock.uptimeMillis();
        mDialog = new AlertDialog.Builder(this).setMessage(messageChars)
        .setPositiveButton(R.string.save, mSaveListener)
        .setNegativeButton(android.R.string.cancel, mCancelListener)
        .setTitle(getString(R.string.cell_broadcast_sms))
        .setCancelable(false).show();
        
        mTimerSet = now + DEFAULT_TIMER;
        if (icicle != null) {
            mTimerSet = icicle.getLong(TIMER_FIRE, mTimerSet);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        long now = SystemClock.uptimeMillis();
        if (mTimerSet <= now) {
            // Save the message if the timer already expired.
            mHandler.sendEmptyMessage(ON_AUTO_SAVE);
        } else {
            mHandler.sendEmptyMessageAtTime(ON_AUTO_SAVE, mTimerSet);
            if (Config.DEBUG) {
                Log.d(TAG, "onRestart time = " + Long.toString(mTimerSet) + " "
                        + this.toString());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(TIMER_FIRE, mTimerSet);
        if (Config.DEBUG) {
            Log.d(TAG, "onSaveInstanceState time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeMessages(ON_AUTO_SAVE);
        if (Config.DEBUG) {
            Log.d(TAG, "onStop time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    private final OnClickListener mCancelListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            finish();
        }
    };

    private final OnClickListener mSaveListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            mRead = true;
            saveMessage();
            finish();
        }
    };



   

    private Uri storeMessage(Context context, SmsCBPage[] msgs, int error) {
        SmsCBPage msg = new SmsCBPage();
       
        if(msgs.length == 0){
        	return null;
        }
        msg = msgs[0];
        int pageCount = msgs.length;
        Log.i(TAG, "storeMessage :length "+pageCount);
        // Store the message in the content provider.
        ContentValues values = extractContentValues(msg);

         if (pageCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(COLUMN_BODY,msg.content);
            Log.i(TAG, "storeMessage :content "+msg.content);
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pageCount; i++) {
     
                body.append(msgs[i].content);
            }
            values.put(COLUMN_BODY, body.toString());
            Log.i(TAG, "storeMessage :content "+body.toString());
        }
        Log.i(TAG, "storeMessage : msgId " +msg.msgId);
    
	   values.put(COLUMN_ADDRESS, msg.msgId);
	   values.put(COLUMN_ICONID, READ_ICON);
       values.put(COLUMN_LANGID, msg.langId);

	//values.put(COLUMN_DATE, enable);
    
    
        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver,CBSMS_URI_T , values);

        // Now make sure we're not over the limit in stored messages
        //Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(), threadId);

        return insertedUri;
    }

    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsCBPage msg) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();
 

        values.put(COLUMN_READ, 0);
        values.put(COLUMN_SEEN, 0);

        return values;
    }
    
    
    //refrence iso639
    private  String getLangName(int langId){
    	
    	String langname ;
    	
    	switch(langId){
    	    case 0x7a68:
			   langname = "Chinese";
			break;
		case 0x6465:
			langname = "German";
			break;
		case 0x656e:
			langname = "English";
			break;
		case 0x6974:
			langname = "Italia";
			break;
		case 0x6672:
			langname = "French";
			break;
		case 0x6573:
			langname = "Spanish";
			break;
		case 0x6e6c:
			langname = "Dutch";
			break;
		case 0x7376:
			langname = "Swedish";
			break;
		case 0x6461:
			langname = "Danish";
			break;
		case 0x7074:
			langname = "Portuguese";
			break;
		case 0x6669:
			langname = "Finnish";
			break;
		case 0x656c:
			langname = "Greek";
			break;
		case 0x6e6f:
			langname = "Norwegian";
			break;
		case 0x746b:
			langname = "Turkish";
			break;
		case 0x6875:
			langname = "Hungarian";
			break;
		case 0x706c:
			langname = "Polish";
			break;
		case 0x6373:
			langname = "Czech";
			break;
		case 0x6865:
			langname = "Herbrew";
			break;
		case 0x6172:
			langname = "Arabic";
			break;
		case 0x7275:
			langname = "Russian";
			break;
		case 0x6973:
			langname = "Icelanic";
			break;
		default:

			StringBuilder name = new StringBuilder(langId);
			langname = name.toString();
			break;

		}

		return langname;

	}

	private void notifyListChanged() {
		
		Log.i(TAG, "notifyListChanged :  " );

		Context context = getApplicationContext();

		Intent intent = new Intent(context, CellBroadcastSmsActivity.class);

		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);

	}
}
