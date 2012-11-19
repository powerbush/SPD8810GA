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

package com.android.phone;

import java.util.ArrayList;
import java.lang.Integer;


import com.android.phone.R;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;


import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;

import android.content.Intent;

import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import android.telephony.TelephonyManager;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;

import android.widget.SimpleCursorAdapter;
import android.content.ContentValues;

import android.widget.CheckBox;

import android.content.ContentUris;
import android.content.DialogInterface.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import android.os.AsyncResult;

import android.os.Message;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.preference.Preference;
import android.content.pm.PackageManager.NameNotFoundException; 
import com.android.internal.telephony.gsm.SmsCBMessage;
/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsSettingActivity extends Activity {

	private static final Uri CBSMS_URI_SET = Uri
			.parse("content://sms/cbsmssetting");
	private static final Uri CBSMS_URI_SETALL = Uri
			.parse("content://sms/cbsmssettingall");
	private static final String TAG = "CellBroadcastSmsSettingActivity";
	public static final int REQUEST_VIEW_CBSMS = 1;
	public static final int REQUEST_LANG = 2;
	public static final int REQUEST_TEST = 3;
	// String keys for preference lookup


	private static final int MENU_ADD = 0;
	private static final int MENU_DELETE = 1;
	private static final int MENU_TEST = 2;

	// Handler keys
	private static final int MESSAGE_ACTIVATE_CB_SMS = 1;
	private static final int MESSAGE_GET_CB_SMS_CONFIG = 2;
	private static final int MESSAGE_SET_CB_SMS_CONFIG = 4;
	// Member variables
	private Phone mPhone;
	private MyHandler mHandler;
	private static final int SHOW_LIST = 0;
	private static final int SHOW_EMPTY = 1;
	private static final int SHOW_BUSY = 2;
	private int mState;
    private int mListCount = 0;
	private static final int THREAD_LIST_QUERY_TOKEN = 1701;

	
	private static final int NO_OF_INTS_STRUCT_1 = 5;

	private ContentResolver mContentResolver;
	private Cursor mCursor = null;
	private ListView mSimList;
	private TextView mMessage;
	private MyListAdapter mListAdapter = null;
	private AsyncQueryHandler mQueryHandler = null;
	private int mPos = 0;
	private boolean mTestFlag = false;

	private CheckBox mCheckbox;
	private Button mButton;

	private String COLUMN_ID = "_id";
	private String COLUMN_CHANNEL_ID = "channel_id";
	private String COLUMN_CHANNEL_NAME = "channel_name";
	private String COLUMN_CHANNEL_ENABLE = "enable";
	private String ACTION = "add";
	private String COLUMN_LANG = "lang";
	private String COLUMN_MODE = "mode";
	private int mLang;
	private int mEnable = 0;
	private boolean mSetall = false;
	private int[] mConfigDataComplete;
	private int[] mAllEnableChannel;
	private int[] mLangSet;
	private int mMode = 0;
	private int mTest =0;

	private final String[] PROJECTION = {

	COLUMN_ID, COLUMN_CHANNEL_ID, COLUMN_CHANNEL_NAME, COLUMN_CHANNEL_ENABLE

	};
	private final String[] SET_PROJECTION = {

	COLUMN_ID, COLUMN_LANG, COLUMN_CHANNEL_ENABLE, COLUMN_MODE

	};

	private static final int ENG_LANG = 0x1;
	private static final int FRENCH_LANG = 0x2;
	private static final int SPANISH_LANG = 0x4;
	private static final int JAPANESE_LANG = 0x8;
	private static final int KOREAN_LANG = 0x10;
	private static final int CHINESE_LANG = 0x20;
	private static final int HERBREW_LANG = 0x40;

	private static final int[] LangMap = { ENG_LANG, FRENCH_LANG, SPANISH_LANG,
			JAPANESE_LANG, KOREAN_LANG, CHINESE_LANG, HERBREW_LANG };
	private static int MAX_LANG = 7;
	private static final int COMMA = 0x2c;
	private static final int QUOTES = 0x22;
	private static final int CR = 0xd;
	//Add for Dualsim
	private static final int PADDING = 0XFFFF;

	public static final int SIM_FULL_NOTIFICATION_ID = 234;
	
	private int mPhoneId = 0;

	private final ContentObserver simChangeObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfUpdate) {
			Log.i(TAG, "onChange");
			refreshMessageList();
		}
	};

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Log.i(TAG, "onCreate");
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		mContentResolver = getContentResolver();

		mQueryHandler = new QueryHandler(mContentResolver, this);

		setContentView(R.layout.cell_broadcast_set);

		mSimList = (ListView) findViewById(R.id.cb_set_list);

		mMessage = (TextView) findViewById(R.id.cb_set_text);

		mCheckbox = (CheckBox) findViewById(R.id.cb_set_checkbox);

		mPhoneId = getIntent().getExtras().getInt("phoneid");
		mPhone = PhoneApp.getInstance().getPhone(mPhoneId);
		//mPhone = PhoneFactory.getDefaultPhone();

		Log.i(TAG, "onCreate " + mPhone.getPhoneName());
		mHandler = new MyHandler();
		
		mCheckbox.setChecked(false);

		mCheckbox.setOnClickListener(mClicked);
		mButton = (Button) findViewById(R.id.lang_button);
		mButton.setHorizontalFadingEdgeEnabled(true);
		
		mButton.setOnClickListener(mClicked);

		//addCellBroadcastSmsSetting();
		init();
		boolean enable = (mEnable == 0) ? false : true;
		mCheckbox.setChecked(enable);
		// registerForContextMenu(mButton);
		mSimList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (view != null) {
					Cursor cursor = (Cursor) mListAdapter.getItem(position);

					String channel_id = cursor.getString(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));
					String channel_name = cursor.getString(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_NAME));
					mPos = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_ID));
					int enable = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ENABLE));
					// @temp
					Intent intent = new Intent(
							CellBroadcastSmsSettingActivity.this,
							CellBroadcastSmsSettingEditActivity.class);
					intent.putExtra(COLUMN_CHANNEL_ID, channel_id);
					intent.putExtra(COLUMN_CHANNEL_NAME, channel_name);
					intent.putExtra(COLUMN_CHANNEL_ENABLE, enable);
					intent.putExtra(COLUMN_LANG, mLang);
					// String posStr = "";
					// posStr += position;
					// Log.i("onItemClick", "pos" + position);
					intent.putExtra("pos", mPos);
					//1 mPos = position;
					//Add for DualSIM
					intent.putExtra("phoneid", mPhoneId);
					startActivityForResult(intent, REQUEST_VIEW_CBSMS);
				}
			}
		});
	}

	// TODO: Enable this once viewMessage is written.
	//
	private View.OnClickListener mClicked = new View.OnClickListener() {
		public void onClick(View v) {

			if (v == mButton) {
				// Authenticate the pin AFTER the contact information
				// is entered, and if we're not busy.
				Log.i(TAG, "OnClickListener");
				Intent intent = new Intent(
						CellBroadcastSmsSettingActivity.this,
						CellBroadcastSmsSettingLangActivity.class);
				intent.putExtra(COLUMN_LANG, mLang);
				//Add for Dualsim
				Bundle mBundle = new Bundle();
                mBundle.putInt("phoneid", mPhoneId);
                intent.putExtras(mBundle);
				startActivityForResult(intent, REQUEST_LANG);

			} else if (v == mCheckbox) {
				mEnable = (mCheckbox.isChecked() == false) ? 0 : 1;
				updeteSettingAll();
			}
		}
	};

	private boolean querySetting() {

//		Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
//				CBSMS_URI_SETALL, SET_PROJECTION, null, null, null);
		Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
				CBSMS_URI_SETALL, SET_PROJECTION, COLUMN_ID+"="+mPhoneId, null, null);

		if (cursor != null) {
			Log.i(TAG, "cursor != null");
			try {
				while (cursor.moveToNext()) {
					int lang = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_LANG));
					mEnable = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ENABLE));
					mLang = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_LANG));
					mMode = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_MODE));
					Log.i(TAG, "COLUMN_LANG :" + lang + "mEnable:" + mEnable
							+ "mLang: " + mLang + "mMode :"+ mMode);
					mSetall = true;
				}
			} finally {
				cursor.close();
			}
		} else {

			mSetall = false;

		}
		return false;

	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);

		init();
	}

	private void init() {
		// MessagingNotification.cancelNotification(getApplicationContext(),
		// SIM_FULL_NOTIFICATION_ID);

		updateState(SHOW_BUSY);
		startQuery();
		querySetting();
	}

	private void setLangATCmd() {

		int[] data;
		int[] lang = getLang();

		boolean isEnable = ((mEnable == 0) ? false : true);
		data = setChannelATCmd(mAllEnableChannel, mLangSet, isEnable);
        if(data!= null){
            mPhone.setCellBroadcastSmsConfig(mConfigDataComplete,
                    Message.obtain(mHandler, MESSAGE_SET_CB_SMS_CONFIG));
        }else{
        	enableCellBroadcast(isEnable);
        }
	}
	
	private int setData(int[] fromData, int startId){
		int data = PADDING;
		if(startId < fromData.length){
			data = fromData[startId];
		}
		return data;
  }


	private int[] setChannelATCmd(int[] channel, int[] lang, boolean isEnable) {

		int[] data;
		
		int raw_length = 0;
		int length = 0;
		int j = 0;
		int i = 0;
		if(lang != null && channel != null){
			
			raw_length = (channel.length + lang.length) * 2 + 10;
		}else{
			
			return null;
			
		}
		if (raw_length % 4 != 0) {
			length = raw_length + (4 - raw_length % 4);
		} else {
			length = raw_length;
		}
		/*
		Log.i(TAG, "setChannelATCmd length :" + length + " rawlength "
				+ raw_length + "lang.length" + lang.length);

		for (i = 0; i < lang.length; i++) {
			Log.i(TAG, "setChannelATCmd lang["+i+"]:" + lang[i]);
			Log.i(TAG,"-----------------------------------------");
		}
        for (int k = 0; k < channel.length; k++) {
            Log.i(TAG, "setChannelATCmd channel["+k+"]:" + channel[k]);
        }*/
		data = new int[length];

		if (isEnable) {

			data[0] = 0;
		} else {

			data[0] = 1;

		}
		data[1] = COMMA;
		data[2] = QUOTES;
		j = 3;
		for (i = 0; i < channel.length; i++) {

			data[j] = channel[i];

			if (i == channel.length - 1) {
				j++;
				break;
			}
			data[j + 1] = COMMA;
			j += 2;
		}

		data[j] = QUOTES;
		if (lang.length > 0) {
			j++;
			data[j] = COMMA;
			j++;
			data[j] = QUOTES;
			j++;

			for (i = 0; i < lang.length; i++) {

				data[j] = lang[i];

				if (i == lang.length - 1) {
					j++;
					break;
				}
				data[j + 1] = COMMA;
				j += 2;
			}
			data[j] = QUOTES;
		//not select the language,so pass two quotes
		} else {
            j++;
            data[j] = COMMA;
            j++;
            data[j] = QUOTES;
            j++;
            data[j] = QUOTES;
		}
		j++;
		Log.i(TAG, "setChannelATCmd raw length:" + j);

		for (i = j; i < length; i++) {

			data[i] = CR;
			Log.i(TAG, "setChannelATCmd  FILL  CR :" + data[i]);
		}

		Log.i(TAG, "setChannelATCmd data length:" + data.length);
		mConfigDataComplete = new int[data.length];

		for (i = 0; i < data.length; i++) {

			mConfigDataComplete[i] = data[i];
			Log.i(TAG, "setChannelATCmd data["+i+"]:" + mConfigDataComplete[i]);
		}

		return data;

	}

	private void setOneChannelATCmd(int channel_id, boolean isEnable) {

		int[] channel = new int[1];
		channel[0] = channel_id;
		int[] lang = getLang();
		int[] data;
		data = setChannelATCmd(channel, mLangSet, isEnable);
		
		if(data != null){

		mPhone.setCellBroadcastSmsConfig(mConfigDataComplete,
		   Message.obtain(mHandler, MESSAGE_SET_CB_SMS_CONFIG));
		}
	}

	

	
	private int getLangId(int langSet) {

		int langId = 0XFFFF;

		switch (langSet) {

		case ENG_LANG:
			langId = 1;
			break;
		case FRENCH_LANG:
			langId = 3;
			break;
		case SPANISH_LANG:
			langId = 4;
			break;

		case JAPANESE_LANG:// @need to do
			langId = 0;
			break;
		case KOREAN_LANG:// @need to do
			langId = 0;
			break;
		case CHINESE_LANG:
			langId = 0x48;
			break;
		case HERBREW_LANG:
			langId = 0x21;
			break;

		default:
			break;

		}

		return langId;

	}

	private int[] getLang() {

		int[] data = new int[MAX_LANG];

		int lang = 0xFFFF;
		int j = 0;
		Log.i(TAG, "getLang mLang " + mLang);
		for (int i = 0; i < MAX_LANG; i++) {

			if ((mLang & LangMap[i]) != 0) {

				lang = getLangId(LangMap[i]);
				Log.i(TAG, "getLang lang " + lang);
				if (0xFFFF != lang) {

					data[j] = lang;
					j++;
				}

			}

		}

		mLangSet = new int[j];
		Log.i(TAG, "getLang count " + j);
		for (int i = 0; i < j; i++) {

			mLangSet[i] = data[i];
			Log.i(TAG, "getLang mLangSet " + mLangSet[i]);

		}

		return mLangSet;

	}

	private class QueryHandler extends AsyncQueryHandler {
		private final CellBroadcastSmsSettingActivity mParent;

		public QueryHandler(ContentResolver contentResolver,
				CellBroadcastSmsSettingActivity parent) {
			super(contentResolver);
			mParent = parent;
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

			int count = cursor.getCount();
			int j = 0;
			int[] data;

			if (count == 0) {
				Log.i(TAG, "onQueryComplete ,count == 0");

			} else {
				Log.i(TAG, "onQueryComplete ,count " + count);
			}
			mListCount = count;
			
			mCursor = cursor;
			if (mCursor != null) {
				if (!mCursor.moveToFirst()) {
					// Let user know the SIM is empty
					Log.i(TAG, "onQueryComplete is empty");
					updateState(SHOW_EMPTY);
				} else if (mListAdapter == null) {
					// Note that the MessageListAdapter doesn't support
					// auto-requeries. If we
					// want to respond to changes we'd need to add a line like:
					// mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
					// See ComposeMessageActivity for an example.
					Log.i(TAG, "onQueryComplete mListAdapter is empty");
					if (cursor != null) {
						if (cursor.moveToFirst()) {

							count = cursor.getCount();
							int total_count = count;
							
							//boolean to_save = false;

							data = new int[total_count];
							j = 0;
							for (int i = 0; i < count; ++i) {
								//to_save = false;
								int channel_id = cursor
										.getInt(cursor
												.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));
								String channel_name = cursor
										.getString(cursor
												.getColumnIndexOrThrow(COLUMN_CHANNEL_NAME));
								int enable = cursor
										.getInt(cursor
												.getColumnIndexOrThrow(COLUMN_CHANNEL_ENABLE));
								Log.i(TAG, "channel_id:" + channel_id
										+ " channel_name:" + channel_name
										+ " enable:" + enable);

								if (enable == 0) {

									//to_save = true;

									//if (to_save) {

										data[j] = channel_id;

										Log.i(TAG,
												"onQueryComplete: channel_id ="
														+ data[j]);
										j++;

									//}
								}

								cursor.moveToNext();

							}
							if (j > 0) {
								mAllEnableChannel = new int[j];

								for (int i = 0; i < j; i++) {
									mAllEnableChannel[i] = data[i];
								}
								Log.i(TAG, "onQueryComplete  last  count " + j);

								//SetCellBroadcastSmsConfig(mAllEnableChannel,
									//	true);
							}

						}

					}
					mListAdapter = new MyListAdapter(mParent,mCursor);
					//mListAdapter = new SimpleCursorAdapter(mParent,
				    //			R.layout.cell_broadcast_setting_item, mCursor,
					//		new String[] { COLUMN_CHANNEL_NAME,
					//				COLUMN_CHANNEL_ID }, new int[] {
					//				R.id.channel_name, R.id.channel_id });
					Log.i(TAG, "onQueryComplete mListAdapter is empty (1)");
					mSimList.setAdapter(mListAdapter);
					Log.i(TAG, "onQueryComplete mListAdapter is empty (2)");
					// mSimList.setOnCreateContextMenuListener(mParent);
					updateState(SHOW_LIST);
				} else {
					Log.i(TAG, "onQueryComplete mListAdapter is not empty");
					mListAdapter.changeCursor(mCursor);
					updateState(SHOW_LIST);
				}
				registerSimChangeObserver();
			} else {
				// Let user know the SIM is empty
				updateState(SHOW_EMPTY);
			}
		}
	}

	private void startQuery() {
		try {
			mQueryHandler.startQuery(THREAD_LIST_QUERY_TOKEN, null, CBSMS_URI_SET, PROJECTION,
	                    "phone_id = "+mPhoneId, null, null);
//			mQueryHandler.startQuery(THREAD_LIST_QUERY_TOKEN, null,
//					CBSMS_URI_SET, PROJECTION, null, null, null);
		} catch (SQLiteException e) {
			SqliteWrapper.checkSQLiteException(this, e);
		}
	}

	private void refreshMessageList() {
		updateState(SHOW_BUSY);
		if (mCursor != null) {
			stopManagingCursor(mCursor);
			mCursor.close();
		}
		startQuery();
	}

	@Override
	public void onResume() {
		super.onResume();
		registerSimChangeObserver();
	}

	@Override
	public void onPause() {
		super.onPause();
		mContentResolver.unregisterContentObserver(simChangeObserver);
		// mPhone.setCellBroadcastSmsConfig(getEnableCellBroadcast(),
		// Message.obtain(mHandler, MESSAGE_SET_CB_SMS_CONFIG));
	}

	private void registerSimChangeObserver() {
		mContentResolver.registerContentObserver(CBSMS_URI_SET, true,
				simChangeObserver);
	}

    @Override
    protected void onDestroy() {
		if (mListAdapter != null) {
			mListAdapter.changeCursor(null);
		}
		if(mCursor != null && !mCursor.isClosed()){
			mCursor.close();
		}
		super.onDestroy();
    }

    public boolean onPrepareOptionsMenu(Menu menu) {

        Log.i(TAG, "onCreateOptionsMenu");
        menu.clear();
        menu.add(0, MENU_ADD, Menu.NONE, getString(R.string.menu_add_channel)).setIcon(
                android.R.drawable.ic_menu_add);
        if (mListCount > 0) {
            menu.add(0, MENU_DELETE, Menu.NONE, getString(R.string.menu_delete_channel)).setIcon(
                    android.R.drawable.ic_menu_delete);
        }
        if (mTestFlag) {
            menu.add(0, MENU_TEST, Menu.NONE, "Test switch");
        }
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		Log.i(TAG, "onOptionsItemSelectedSIM MenuItem");

		switch (item.getItemId()) {
		
		case MENU_ADD: {
			Log.i(TAG, "onOptionsItemSelectedSIM menu MENU_ADD");
			Intent intent = new Intent(CellBroadcastSmsSettingActivity.this,
					CellBroadcastSmsSettingEditActivity.class);
			intent.putExtra(COLUMN_LANG, mLang);
			Bundle mBundle = new Bundle();
            mBundle.putInt("phoneid", mPhoneId);
            intent.putExtras(mBundle);
			startActivityForResult(intent, REQUEST_VIEW_CBSMS);
			return true;
		}
		case MENU_DELETE: {
			Log.i(TAG, "onOptionsItemSelectedSIM MENU_DELETE");
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.menu_delete_channel);
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setCancelable(true);
                builder.setMessage(R.string.confirm_delete_message);
                builder.setPositiveButton(R.string.menu_delete_channel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SqliteWrapper.delete(CellBroadcastSmsSettingActivity.this,
                                        mContentResolver, CBSMS_URI_SET, null, null);
                                refreshMessageList();
                            }
                        });
                builder.setNegativeButton(R.string.no, null);
                builder.show();
                return true;
		}
		case MENU_TEST:{
			Log.i(TAG, "onOptionsItemSelectedSIM menu MENU_TEST");
			Intent intent = new Intent(CellBroadcastSmsSettingActivity.this,
					CellBroadcastSmsSettingTestActivity.class);
			intent.putExtra(COLUMN_MODE, mMode);
			Bundle mBundle = new Bundle();
            mBundle.putInt("phoneid", mPhoneId);
            intent.putExtras(mBundle);
			startActivityForResult(intent, REQUEST_TEST);
			return true;
		}
		default:
			break;
		}
		return false;

	}



	private void addCellBroadcastSmsSetting() {
		
		ArrayList<String> data = getdata();

		Log.i(TAG, "addCellBroadcastSms ");

		for (int i = 0; i < 5; i++) {

			try {
				ContentValues values = new ContentValues();
				values.put("channel_id", i);
				values.put("channel_name", data.get(i));
				values.put("enable", 0);
				Uri insertedUri = SqliteWrapper.insert(
						CellBroadcastSmsSettingActivity.this, mContentResolver,
						CBSMS_URI_SET, values);

			} catch (SQLiteException e) {
				SqliteWrapper.checkSQLiteException(this, e);
			}
		}

		

	}

	private class MyHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_ACTIVATE_CB_SMS:
				// Only a log message here, because the received response is
				// always null
				Log.i(TAG, "Cell Broadcast SMS enabled/disabled.");
				

				break;
			case MESSAGE_GET_CB_SMS_CONFIG:
				int result[] = (int[]) ((AsyncResult) msg.obj).result;

				// check if the actual service categoties table size on the NV
				// is '0'
				if (result[0] == 0) {
				
					mPhone.getCellBroadcastSmsConfig(Message.obtain(mHandler,
							MESSAGE_GET_CB_SMS_CONFIG));
				
				}

				

				break;
			case MESSAGE_SET_CB_SMS_CONFIG:
				
				break;
			default:
				Log.e(TAG,
						"Error! Unhandled message in CellBroadcastSms.java. Message: "
								+ msg.what);
				break;
			}
		}
	}

	private void updateState(int state) {
		if (mState == state) {
			return;
		}

		mState = state;
		switch (state) {
		case SHOW_LIST:
			mSimList.setVisibility(View.VISIBLE);
			//mSimList.setDivider(android.drawable.divider_horizontal_bright);
			//mSimList.setDividerHeight(2);
			mMessage.setVisibility(View.GONE);
			setTitle(getString(R.string.cb_sms_settings));
			setProgressBarIndeterminateVisibility(false);
			break;
		case SHOW_EMPTY:
			mSimList.setVisibility(View.GONE);
			mMessage.setVisibility(View.VISIBLE);
			setTitle(getString(R.string.cb_sms_settings));
			setProgressBarIndeterminateVisibility(false);
			break;
		case SHOW_BUSY:
			mSimList.setVisibility(View.GONE);
			mMessage.setVisibility(View.GONE);
			setTitle(getString(R.string.refreshing));
			setProgressBarIndeterminateVisibility(true);
			break;
		default:
			Log.e(TAG, "Invalid State");
		}
	}



	private void enableCellBroadcast(boolean enable) {
		Log.i(TAG, "enableCellBroadcast");
		if (enable) {
			mPhone.activateCellBroadcastSms(
					RILConstants.CDMA_CELL_BROADCAST_SMS_ENABLED,
					Message.obtain(mHandler, MESSAGE_ACTIVATE_CB_SMS));
		} else {
			mPhone.activateCellBroadcastSms(
					RILConstants.CDMA_CELL_BROADCAST_SMS_DISABLED,
					Message.obtain(mHandler, MESSAGE_ACTIVATE_CB_SMS));
		}
	}

	private void updeteSettingAll() {

		if (!mSetall) {
			try {
				ContentValues values = new ContentValues();
				
				//Add for Dualsim
				values.put(COLUMN_ID, mPhoneId); 
				values.put(COLUMN_LANG, mLang);
				values.put(COLUMN_CHANNEL_ENABLE, mEnable);
				values.put(COLUMN_MODE, mMode);
				SqliteWrapper.insert(
						this, mContentResolver,
						CBSMS_URI_SETALL, values);

			} catch (SQLiteException e) {
				SqliteWrapper.checkSQLiteException(this, e);
			}

		} else {

			try {
				ContentValues values = new ContentValues();

				values.put(COLUMN_LANG, mLang);

				values.put(COLUMN_CHANNEL_ENABLE, mEnable);
				values.put(COLUMN_MODE, mMode);

				SqliteWrapper.update(CellBroadcastSmsSettingActivity.this, mContentResolver, CBSMS_URI_SETALL, values, COLUMN_ID+"="+mPhoneId, null);
				//SqliteWrapper.update(this, mContentResolver, CBSMS_URI_SETALL,values, null, null);
			} catch (SQLiteException e) {
				SqliteWrapper.checkSQLiteException(this, e);
			}
		}
		setLangATCmd();
	}

	private int[] getEnableCellBroadcast() {

		Cursor cursor = mCursor;

		if (cursor != null) {
			if (cursor.moveToFirst()) {

				int count = mCursor.getCount();
				int total_count = count * NO_OF_INTS_STRUCT_1 + 1;
				int j = 1;
				mConfigDataComplete = new int[total_count];
				mConfigDataComplete[0] = count;

				Log.i(TAG, "getEnableCellBroadcast: total_count ="
						+ total_count + " count :" + count);

				for (j = 1; j < total_count; j += NO_OF_INTS_STRUCT_1) {

					mConfigDataComplete[j] = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));
					mConfigDataComplete[j + 4] = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ENABLE));

					Log.i(TAG, "getEnableCellBroadcast: channel_id ="
							+ mConfigDataComplete[j] + "enable :"
							+ mConfigDataComplete[j + 4]);
					cursor.moveToNext();

				}
			}

		}
		return mConfigDataComplete;

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Log.i("onActivityResult: requestCode=" + requestCode, "resultCode"
				+ resultCode + "+data=" + data);
		switch (requestCode) {

		case REQUEST_VIEW_CBSMS:
			if (resultCode == 20) {			
				
				
				Log.i(TAG,"onActivityResult: mPos"+mPos );			
               
		    	Uri updateUri = ContentUris.withAppendedId(
		    			CBSMS_URI_SET, mPos);

				SqliteWrapper.delete(CellBroadcastSmsSettingActivity.this, mContentResolver, updateUri,
						 null, null);
		       
		        refreshMessageList();
				
			
			} else if (resultCode == 21) {
				String pos = data.getStringExtra("pos");
				Log.i(TAG, "onActivityResult: pos" + pos);
				int channel_id = Integer.parseInt(data
						.getStringExtra(COLUMN_CHANNEL_ID));
				String channel_name = data.getStringExtra(COLUMN_CHANNEL_NAME);
				boolean add = data.getBooleanExtra(ACTION, false);
				int enable = data.getIntExtra(COLUMN_CHANNEL_ENABLE, 0);
				
				Log.i(TAG,"onActivityResult: enable =" + enable);
				try {
					ContentValues values = new ContentValues();
					values.put(COLUMN_CHANNEL_ID, channel_id);
					values.put(COLUMN_CHANNEL_NAME, channel_name);
					values.put(COLUMN_CHANNEL_ENABLE, enable);
					if (add) {
						Uri insertedUri = SqliteWrapper.insert(
								CellBroadcastSmsSettingActivity.this,
								mContentResolver, CBSMS_URI_SET, values);
					} else {

						Uri updateUri = ContentUris.withAppendedId(
								CBSMS_URI_SET, mPos);

						SqliteWrapper
								.update(CellBroadcastSmsSettingActivity.this,
										mContentResolver, updateUri, values,
										null, null);

					}

					if (add && (enable == 0)) {
						Log.i(TAG,
								"onActivityResult + mAllEnableChannel.length "
										+ mAllEnableChannel.length);
						int i;
						int enableChannel[] = new int[mAllEnableChannel.length + 1];

						for (i = 0; i < mAllEnableChannel.length; i++) {

							enableChannel[i] = mAllEnableChannel[i];
						}

						enableChannel[i] = channel_id;

						mAllEnableChannel = enableChannel;

						Log.i(TAG, "onActivityResult + mAllEnableChannel "
								+ mAllEnableChannel.toString());

						setOneChannelATCmd(channel_id, true);

					}

					if (!add && (enable == 1)) {

						Log.i(TAG,
								"onActivityResult + mAllEnableChannel.length "
										+ mAllEnableChannel.length);
						int i;
						int j = 0;
						int enableChannel[] = new int[mAllEnableChannel.length - 1];

						for (i = 0; i < mAllEnableChannel.length; i++) {

							if (mAllEnableChannel[i] != channel_id) {

								enableChannel[j] = mAllEnableChannel[i];
								j++;

							}
						}

						mAllEnableChannel = enableChannel;

						Log.i(TAG, "onActivityResult + mAllEnableChannel "
								+ mAllEnableChannel.toString());

						setOneChannelATCmd(channel_id, false);

					}

					refreshMessageList();

				} catch (SQLiteException e) {
					SqliteWrapper.checkSQLiteException(this, e);
				}

			}else if(resultCode == 22){
				
				refreshMessageList();
				
			}
			break;
		case REQUEST_LANG:
			if (resultCode == 20) {

				mLang = data.getIntExtra(COLUMN_LANG, 0);
				Log.i(TAG, "onActivityResult: lang" + mLang);

				updeteSettingAll();

			}
			break;
		case REQUEST_TEST:
			if (resultCode == 30) {

				int test = data.getIntExtra(COLUMN_MODE, 0);
				mMode = test;
				Log.i(TAG, "onActivityResult: test " + test);
				mPhone.activateCellBroadcastSms(test,
	                        Message.obtain(mHandler, MESSAGE_ACTIVATE_CB_SMS));
				updeteSettingAll();
			}
			break;
		default:
			break;
		}

	}



	ArrayList<String> getdata() {

		ArrayList<String> data = new ArrayList<String>();

		data.add("test1");
		data.add("test2");
		data.add("test3");
		data.add("test4");
		data.add("test5");
		data.add("test6");
		return data;

	}

	ArrayList<String> getDest() {

		ArrayList<String> data = new ArrayList<String>();

		data.add("getDest1");
		data.add("getDest2");
		data.add("getDest3");
		data.add("getDest4");
		data.add("getDest5");
		data.add("getDest6");
		return data;

	}
	
	public class MyListAdapter extends CursorAdapter {
	    private static final String TAG = "MyListAdapter";
	   

	    private final LayoutInflater mFactory;
	  

	    public MyListAdapter(Context context, Cursor cursor) {
	        super(context, cursor, false /* auto-requery */);
	        mFactory = LayoutInflater.from(context);
	    }

	    @Override
	    public void bindView(View view, Context context, Cursor cursor) {
	      
	    	TextView nametextView= (TextView) view.findViewById(R.id.channel_name);
	    	TextView idtextView= (TextView) view.findViewById(R.id.channel_id);
			ImageView imageView = (ImageView) view.findViewById(R.id.icon);
			
			int channel_id = cursor
			.getInt(cursor
					.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));
	        String channel_name = cursor
			.getString(cursor
					.getColumnIndexOrThrow(COLUMN_CHANNEL_NAME));
			Log.i(TAG, "MyListAdapter address: "
					+ channel_name + "channel_id:" + channel_id);
			String  id =  String.valueOf(channel_id);
		
			imageView.setImageResource(R.drawable.cb_item);
			nametextView.setText(channel_name);
			nametextView.setTextColor(android.graphics.Color.WHITE);
			idtextView.setText(id);
			
			idtextView.setTextColor(android.graphics.Color.WHITE);
			
		
	      
	    }

	  
	    @Override
	    public View newView(Context context, Cursor cursor, ViewGroup parent) {
	        Log.i(TAG, "inflating new view");
	        return mFactory.inflate(R.layout.cell_broadcast_setting_item, parent, false);
	    }

	}

}
