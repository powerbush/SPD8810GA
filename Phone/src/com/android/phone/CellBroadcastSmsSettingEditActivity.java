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
import java.util.regex.Pattern;
import java.lang.Integer;

import org.apache.harmony.kernel.vm.StringUtils;

import com.android.phone.R;

import android.database.sqlite.SqliteWrapper;
//import com.android.mms.transaction.MessagingNotification;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.AsyncTask;
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
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.Button;
import android.widget.EditText;
import android.text.TextUtils;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.DialerKeyListener;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;

import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import android.content.ContentValues;

/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsSettingEditActivity extends Activity {

	private static final int MENU_DELETE = 0;
	private TextView mMessage;
	private static final String TAG = "CellBroadcastSmsSettingEditActivity";
	private int mPos;
	private EditText mNameField;
	private EditText mNumberField;

	private Button mButton;
	private boolean mAdd;
	private String COLUMN_CHANNEL_ID = "channel_id";
	private String COLUMN_CHANNEL_NAME = "channel_name";
	private String COLUMN_CHANNEL_PHONEID = "phone_id";
	private String ACTION = "add";
	private String COLUMN_CHANNEL_ENABLE = "enable";
	private String COLUMN_LANG = "lang";
	private String mName;
	private String mNumber;
	private CheckBox mCheckbox;
	private int mEnable = 1;
	private int mLang = 0;
	private ContentResolver mContentResolver;
	private int[] mConfigDataComplete;
	private int[] mAllEnableChannel;
	private int[] mLangSet;
	private String COLUMN_ID = "_id";
	private int mSave = 0;
	private int mPhoneId = 0;

	private static final int MESSAGE_SET_CB_SMS_CONFIG = 4;
	private final String[] PROJECTION = {

	COLUMN_ID, COLUMN_CHANNEL_ID, COLUMN_CHANNEL_NAME, COLUMN_CHANNEL_ENABLE,COLUMN_CHANNEL_PHONEID

	};
	private final String[] SET_PROJECTION = {

	COLUMN_ID, COLUMN_LANG, COLUMN_CHANNEL_ENABLE

	};

	private static final int ENG_LANG = 0x1;
	private static final int FRENCH_LANG = 0x2;
	private static final int SPANISH_LANG = 0x4;
	private static final int JAPANESE_LANG = 0x8;
	private static final int KOREAN_LANG = 0x10;
	private static final int CHINESE_LANG = 0x20;
	private static final int HERBREW_LANG = 0x40;
	private static final int PADDING = 0XFFFF;

	private static final int[] LangMap = { ENG_LANG, FRENCH_LANG, SPANISH_LANG,
			JAPANESE_LANG, KOREAN_LANG, CHINESE_LANG, HERBREW_LANG };
	private static int MAX_LANG = 7;
	private static final int COMMA = 0x2c;
	private static final int QUOTES = 0x22;
	private static final int CR = 0xd;
	private Phone mPhone;
	private MyHandler mHandler;
	private static final Uri CBSMS_URI_SET = Uri
			.parse("content://sms/cbsmssetting");
	private Cursor mSetCursor = null;
	private int mSubscription;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		mPhone = PhoneFactory.getDefaultPhone();
		mHandler = new MyHandler();
		mContentResolver = getContentResolver();
		Log.i(TAG, "onCreate 2" + mPhone.getPhoneName());
		resolveIntent();
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.cell_broadcast_setting_edit);
		setupView();
		setTitle(R.string.edit);
	}

	private void resolveIntent() {
		Intent intent = getIntent();

		mName = intent.getStringExtra(COLUMN_CHANNEL_NAME);
		mNumber = intent.getStringExtra(COLUMN_CHANNEL_ID);

		mLang = intent.getIntExtra(COLUMN_LANG, 0);
		mPos = intent.getIntExtra("pos", 0);

		mPhoneId = intent.getExtras().getInt("phoneid");
        mPhone = PhoneApp.getInstance().getPhone(mPhoneId);

		Log.i(TAG, "onCreate" + "mLang :" + mLang + "mPos :" + mPos);
		if (TextUtils.isEmpty(mName) && TextUtils.isEmpty(mNumber)) {
			mAdd = true;
		}
		if (!mAdd) {

			mEnable = intent.getIntExtra(COLUMN_CHANNEL_ENABLE, 0);

		}
	}

	private View.OnClickListener mClicked = new View.OnClickListener() {
		public void onClick(View v) {

			if (v == mNameField) {
				mNameField.requestFocus();
			} else if (v == mNumberField) {
				mNumberField.requestFocus();
			} else if (v == mButton) {
			    String regexNum = "^[0-9]*$";
			    String mNumber = mNumberField.getText().toString().trim();
			    if ("".equals(mNumber)) {
                    Toast.makeText(getApplicationContext(), getString(R.string.info_input_channel_number),
                            Toast.LENGTH_SHORT).show();
			    } else if (!mNumber.matches(regexNum)) {
                    Toast.makeText(getApplicationContext(), getString(R.string.info_input_channel_number_num),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // test
                    if (onAction()) {
                        setResult(22, getIntent());
                        finish();
                    }
                }
			} else if (v == mCheckbox) {

				int enable = (mCheckbox.isChecked() == false) ? 1 : 0;

				if (enable != mEnable) {

					mSave = 1;
					mEnable = enable;
				} else {
					mSave = 0;
				}
				Log.i(TAG, "mCheckbox" + mCheckbox.isChecked() + "mEnable :"
						+ mEnable);
			}
		}
	};

	View.OnFocusChangeListener mOnFocusChangeHandler = new View.OnFocusChangeListener() {
		public void onFocusChange(View v, boolean hasFocus) {
			if (hasFocus) {
				TextView textView = (TextView) v;
				Selection.selectAll((Spannable) textView.getText());
			}
		}
	};

	/**
	 * We have multiple layouts, one to indicate that the user needs to open the
	 * keyboard to enter information (if the keybord is hidden). So, we need to
	 * make sure that the layout here matches that in the layout file.
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

		if (!mAdd) {
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
		mCheckbox = (CheckBox) findViewById(R.id.enable_checkbox);
		mCheckbox.setOnClickListener(mClicked);

		boolean enable;
		if (mAdd) {
			enable = false;
		} else {
			enable = (mEnable == 0) ? true : false;
		}
		mCheckbox.setChecked(enable);

	}

	private String getNameFromTextField() {
		return mNameField.getText().toString();
	}

	private String getNumberFromTextField() {
		return mNumberField.getText().toString().trim();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();

	}

	OnClickListener positiveListener = new OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			Log.i(TAG, "mPos" + mPos);
			// getIntent().putExtra("pos",mPos);
			setResult(20, getIntent());
			CellBroadcastSmsSettingEditActivity.this.finish();
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		super.onCreateOptionsMenu(menu);

		Log.i(TAG, "onCreateOptionsMenu");
		menu.add(0, MENU_DELETE, Menu.NONE, R.string.menu_delete_sigle_channel)
				.setIcon(android.R.drawable.ic_menu_delete);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		Log.i(TAG, "onOptionsItemSelectedSIM MenuItem");

		switch (item.getItemId()) {

		case MENU_DELETE: {

			Log.i(TAG, "onOptionsItemSelectedSIM MENU_DELETE");
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.menu_delete_sigle_channel);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setCancelable(true);
			builder.setMessage(R.string.confirm_delete_message);
			builder.setPositiveButton(R.string.menu_delete_sigle_channel,
					positiveListener);
			builder.setNegativeButton(R.string.no, null);
			builder.show();

			return true;
		}

		default:
			break;

		}
		return false;

	}

	private void setxOneChannelATCmd(int channel_id, boolean isEnable) {

		int[] channel = new int[1];
		channel[0] = channel_id;
		int[] lang = getLang();
		int[] data;
		data = setChannelATCmd(channel, mLangSet, isEnable);
		if (data != null) {
			mPhone.setCellBroadcastSmsConfig(mConfigDataComplete,

			Message.obtain(mHandler, MESSAGE_SET_CB_SMS_CONFIG));
		}
	}

	private boolean onAction() {
		{
			Log.i(TAG, "onAction: mEnable" + mEnable);

			String id = getNumberFromTextField();

			if (id == null && getNameFromTextField() == null) {

				return false;

			}
			int newId = Integer.parseInt(getNumberFromTextField());

			int channel_id = Integer.parseInt(getNumberFromTextField());

			matchInfo ret = matchName(id);
			String name = getNameFromTextField();

			if (ret.isMatch) {

				AlertDialog.Builder builder = new AlertDialog.Builder(this);

				builder.setIcon(android.R.drawable.ic_dialog_alert);
				
				

				CharSequence info = getString(R.string.same_setting) + " < "
						+ ret.name + " >";
				
				builder.setMessage(info);

				builder.show();
				return false;
			}
			try {
				ContentValues values = new ContentValues();
				values.put(COLUMN_CHANNEL_ID, getNumberFromTextField());
				values.put(COLUMN_CHANNEL_NAME, getNameFromTextField());
				values.put(COLUMN_CHANNEL_ENABLE, mEnable);
				values.put(COLUMN_CHANNEL_PHONEID, mPhoneId);

				if (mAdd) {

					Uri insertedUri = SqliteWrapper.insert(
							CellBroadcastSmsSettingEditActivity.this,
							mContentResolver, CBSMS_URI_SET, values);

					if (mEnable == 0) {

						int enableChannel[] = new int[1];

						enableChannel[0] = channel_id;

						mAllEnableChannel = enableChannel;

						Log.i(TAG, "onAction + mAllEnableChannel "
								+ mAllEnableChannel.toString());

						setOneChannelATCmd(channel_id, true);

					}
				} else {
					Uri updateUri = ContentUris.withAppendedId(CBSMS_URI_SET,
							mPos);

					SqliteWrapper.update(
							CellBroadcastSmsSettingEditActivity.this,
							mContentResolver, updateUri, values, null, null);

					if (Integer.parseInt(getNumberFromTextField()) != Integer
							.parseInt(mNumber) || mSave == 1) {

						int enableChannel[] = new int[1];

						enableChannel[0] = channel_id;

						mAllEnableChannel = enableChannel;

						Log.i(TAG, "onAction + mAllEnableChannel "
								+ mAllEnableChannel.toString());
						boolean isEnable = (mEnable == 0) ? true : false;

						setxOneChannelATCmd(channel_id, isEnable);

					}

				}

			} catch (SQLiteException e) {
				SqliteWrapper.checkSQLiteException(this, e);
			}

		}

		return true;
	}

	private int[] setChannelATCmd(int[] channel, int[] lang, boolean isEnable) {

		int[] data;
		int raw_length = 0;
		int length = 0;
		int j = 0;
		int i = 0;

		if (channel != null && lang != null) {

			raw_length = (channel.length + lang.length) * 2 + 10;

		} else {

			return null;

		}

		if (raw_length % 4 != 0) {

			length = raw_length + (4 - raw_length % 4);
		} else {

			length = raw_length;
		}

		Log.i(TAG, "setChannelATCmd length :" + length + " rawlength "
				+ raw_length + "lang.length" + lang.length);

		for (i = 0; i < lang.length; i++) {

			Log.i(TAG, "setChannelATCmd lang :" + lang[i]);
		}

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
			Log.i(TAG, "setChannelATCmd data :" + mConfigDataComplete[i]);
		}

		return data;

	}

	private void setOneChannelATCmd(int channel_id, boolean isEnable) {

		int[] channel = new int[1];
		channel[0] = channel_id;
		int[] lang = getLang();
		int[] data;
		data = setChannelATCmd(channel, mLangSet, isEnable);
		if (data != null) {
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

	private class MyHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_SET_CB_SMS_CONFIG:
				// Only a log message here, because the received response is
				// always null
				// Log.i(TAG, "Set Cell Broadcast SMS values.");
				// / mPhone.getCellBroadcastSmsConfig(Message.obtain(mHandler,
				// MESSAGE_SET_CB_SMS_CONFIG));
				break;

			default:
				Log.e(TAG,
						"Error! Unhandled message in CellBroadcastSms.java. Message: "
								+ msg.what);
				break;
			}
		}
	}

	private class matchInfo{
		
		public boolean isMatch;
		public String  name;
		public String  id;
		public int mPhoneId;
	}
	
	private matchInfo matchName(String id) {
		
		matchInfo ret = new matchInfo();
		
		ret.isMatch = false;

//		Cursor cursor = SqliteWrapper.query(this, mContentResolver,
//				CBSMS_URI_SET, PROJECTION, null, null, null);
		
		Cursor cursor = SqliteWrapper
                .query(this, mContentResolver, CBSMS_URI_SET, PROJECTION,
                        "phone_id = " + mPhoneId, null, null);

		int count = cursor.getCount();

		if (cursor != null) {

			try {
				if (cursor.moveToFirst()) {

					for (int i = 0; i < count; ++i) {

						int channel_id = cursor.getInt(cursor
								.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));

						int ids = Integer.parseInt(id);

						Log.i(TAG, "channel_id:" + channel_id + " ids:" + ids);
						if (channel_id == ids)

						{
							String channel_name = cursor
									.getString(cursor
											.getColumnIndexOrThrow(COLUMN_CHANNEL_NAME));
							Log.i(TAG, "channel_id:" + channel_id
									+ " channel_name:" + channel_name);

							ret.name = channel_name;
							ret.id = id;
							ret.isMatch = true;
							break;
						}

						cursor.moveToNext();

					}
				}
			} finally {
				cursor.close();
			}
		}

		return ret;

	}
}
