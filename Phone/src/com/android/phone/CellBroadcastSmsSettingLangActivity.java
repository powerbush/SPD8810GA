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
import android.os.Bundle;
import android.os.Handler;

import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import android.widget.Button;
import android.widget.EditText;
import android.text.TextUtils;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.DialerKeyListener;


/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsSettingLangActivity extends Activity {
	
	private static final int MENU_DELETE = 0;
	private TextView mMessage;
    private static final String TAG = "CellBroadcastSmsSettingLangActivity";
	private static final int ENG_LANG = 0x1;
	private static final int FRENCH_LANG = 0x2;
	private static final int SPANISH_LANG = 0x4;
	private static final int JAPANESE_LANG = 0x8;
	private static final int KOREAN_LANG = 0x10;
	private static final int CHINESE_LANG = 0x20;
	private static final int HERBREW_LANG = 0x40;
	private static final int LANG_MAX = 0x7;
	public static final int REQUEST_LANG = 2;
	private String COLUMN_LANG = "lang";
	
	private static final int [] LangMap ={
		ENG_LANG,
		FRENCH_LANG,
		SPANISH_LANG,
		JAPANESE_LANG,
		KOREAN_LANG,
		CHINESE_LANG,
		HERBREW_LANG
	};
	
	private static final String[] LangTable = {

	"english", "french", "spanish", "japanese", "korean", "chinese", "herbrew"

	};
    private CheckBox[] mCheckbox;
    private CheckBox mEngCheckbox;
    private CheckBox mFrenchCheckbox;
    private CheckBox mSpanishCheckbox;
    private CheckBox mJapaneseCheckbox;
    private CheckBox mKoreanCheckbox;
    private CheckBox mChineseCheckbox;
    private CheckBox mHebrewCheckbox;
    private int mLang = 0 ;
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
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        mPhoneId = getIntent().getExtras().getInt("phoneid");
        setContentView(R.layout.cell_broadcast_lang);
        setupCheckbox();
        setTitle(R.string.list_language_title);
    }
    
    private void resolveIntent() {
        Intent intent = getIntent();

        mLang =  intent.getIntExtra(COLUMN_LANG,0);
        Log.i(TAG, "resolveIntent mLang =: "+ mLang);  
    }
    
    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
       
       Log.i(TAG, "OnClickListener");
       for(int i=0;i<LANG_MAX;i++){
    	   
    	   if(v == mCheckbox[i]){
    		   
    		   Log.i(TAG, "OnClickListener i=: "+i +"checked"+mCheckbox[i].isChecked() );  
    		   if(mCheckbox[i].isChecked()){
    			   
    			   //mCheckbox[i].setChecked(true);
   				
   				   mLang |= LangMap[i];
    			   
    		   }else{
    			   
    			
    				// mCheckbox[i].setChecked(false);
      			   mLang &= ~LangMap[i];
    			   
    		   }
    		   Log.i(TAG, "resolveIntent set mLang =: "+ mLang);  
    			getIntent().putExtra(COLUMN_LANG,mLang);
        		setResult(20,getIntent());
    		   
    	   }
    	   
       } 	
        	
       
    }

    };

    /**
     * We have multiple layouts, one to indicate that the user needs to
     * open the keyboard to enter information (if the keybord is hidden).
     * So, we need to make sure that the layout here matches that in the
     * layout file.
     */
    private void setupCheckbox() {
    
    	mEngCheckbox = (CheckBox) findViewById(R.id.eng_checkbox);    	
    	mFrenchCheckbox =( CheckBox) findViewById(R.id.french_checkbox);
    		
    	mSpanishCheckbox =( CheckBox) findViewById(R.id.spanish_checkbox);
    	mJapaneseCheckbox =( CheckBox) findViewById(R.id.japanese_checkbox);
    	mKoreanCheckbox =( CheckBox) findViewById(R.id.korean_checkbox);
    	mChineseCheckbox =( CheckBox) findViewById(R.id.chinese_checkbox);
    	mHebrewCheckbox =( CheckBox) findViewById(R.id.hebrew_checkbox);
        mCheckbox = new CheckBox[LANG_MAX];
        mCheckbox[0] = mEngCheckbox;        
        mCheckbox[1] = mFrenchCheckbox;
        mCheckbox[2] = mSpanishCheckbox;
        mCheckbox[3] = mJapaneseCheckbox;
        mCheckbox[4] = mKoreanCheckbox;
        mCheckbox[5] = mChineseCheckbox;
        mCheckbox[6]=  mHebrewCheckbox;
        
        
    	for(int i=0;i<LANG_MAX ;i++){
    	    
    		if((mLang & LangMap[i]) !=0 ){
    			
    			 mCheckbox[i].setChecked(true);
    			
    		}
    		mCheckbox[i].setOnClickListener(mClicked);
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