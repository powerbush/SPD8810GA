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

package com.android.mms.ui;

import java.util.ArrayList;

import com.android.mms.R;
import android.database.sqlite.SqliteWrapper;
import com.android.mms.transaction.MessagingNotification;

import android.app.Activity;
import android.app.NotificationManager;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;

import android.content.Intent;

import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony.Sms;

import android.util.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.ui.CellBroadcastSmsItemActivity;

import android.content.ContentUris;
//import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;

import android.view.LayoutInflater;

import android.view.ViewGroup;

import android.widget.CursorAdapter;
import android.content.Context;

//import com.android.mms.ui.CellBroadcastSmsSettingActivity;
/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsActivity extends Activity implements
		View.OnCreateContextMenuListener {

	private static final Uri CBSMS_URI_T = Uri.parse("content://sms/cbsms");
	private static final String TAG = "CellBroadcastSmsActivity";
	public static final int REQUEST_VIEW_CBSMS = 1;

	// private static final int MENU_VIEW= 0;
	// private static final int MENU_ADD = 1;
	private static final int MENU_DELETE = 0;
	private static final int MENU_SETTING = 1;

	private static final int SHOW_LIST = 0;
	private static final int SHOW_EMPTY = 1;
	private static final int SHOW_BUSY = 2;
	private static int mState;

	private static final int THREAD_LIST_QUERY_TOKEN = 1701;

	private static ContentResolver mContentResolver;
	private static Cursor mCursor = null;
	private static ListView mSimList;
	private static TextView mMessage;
	private static MyListAdapter mListAdapter = null;
	private static AsyncQueryHandler mQueryHandler = null;
	private static int mPos = 0;
	private static int mListCount = 0;
	private static int mIconId = 0;
	private static ImageView mPresenceView;

	private static String COLUMN_ADDRESS = "address";
	private static String COLUMN_BODY = "body";
	private static String COLUMN_ID = "_id";

	private static  String COLUMN_ICONID = "iconId";
	private static  int DEFAULT_ICON = R.drawable.unread_cbsms;
	private static int READ_ICON = R.drawable.read_cbsms;
	public static final int SIM_FULL_NOTIFICATION_ID = 234;
	private static   int mUnreadCount = 0;

	private static final Uri CBSMS_URI_SET = Uri
			.parse("content://sms/cbsmssetting");
	private String COLUMN_CHANNEL_ID = "channel_id";
	private String COLUMN_CHANNEL_NAME = "channel_name";
	private String COLUMN_CHANNEL_ENABLE = "enable";
	private String ACTION = "add";
	private String COLUMN_LANG = "lang";
	private static int mLang;
	private static int mEnable = 0;
	private static Cursor mSetCursor = null;
	private static boolean mIsOpen = false;
	private static Intent mIntent;

	private final String[] PROJECTION = {

	COLUMN_ID, COLUMN_CHANNEL_ID, COLUMN_CHANNEL_NAME, COLUMN_CHANNEL_ENABLE

	};

	private final ContentObserver simChangeObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfUpdate) {
			Log.i("CellBroadcastSmsActivity", "onChange");
			refreshMessageList();
		}
	};

	private void setPresenceIcon(int iconId) {
		if (iconId == 0) {
			mPresenceView.setVisibility(View.GONE);
		} else {
			mPresenceView.setImageResource(iconId);
			mPresenceView.setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Log.i("CellBroadcastSmsActivity", "onCreate");
		mIsOpen  = true;
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		mContentResolver = getContentResolver();
		mQueryHandler = new QueryHandler(mContentResolver, this);
		setContentView(R.layout.cell_broadcast_list);
		mSimList = (ListView) findViewById(R.id.messages);
		mMessage = (TextView) findViewById(R.id.empty_message);
		// mPresenceView = (ImageView) findViewById(R.id.presence);

		// addCellBroadcastSms();
		init();

		mSimList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (view != null) {
					Cursor cursor = (Cursor) mListAdapter.getItem(position);

					String address = cursor.getString(cursor
							.getColumnIndexOrThrow(COLUMN_ADDRESS));
					String body = cursor.getString(cursor
							.getColumnIndexOrThrow(COLUMN_BODY));
					Long date = cursor.getLong(cursor
							.getColumnIndexOrThrow("date"));
					int langId = cursor.getInt(cursor
							.getColumnIndexOrThrow("langId"));
					mPos = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_ID));
					String dateStr = "";
					dateStr += date;
					Intent intent = new Intent(CellBroadcastSmsActivity.this,
							CellBroadcastSmsItemActivity.class);
					String setName = matchName(address);
				
					if(setName != null){
						
						intent.putExtra("address", setName);	
					}else{
					    intent.putExtra("address", address);
					}
					intent.putExtra("body", body);
					intent.putExtra("date", dateStr);
					intent.putExtra("langId", langId);
				
					Log.i(TAG,"onItemClick pos" + mPos);

					int iconId = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_ICONID));
					mIconId = iconId;

					if (mIconId == DEFAULT_ICON) {

						mUnreadCount--;

						if (mUnreadCount == 0) {

							NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

							nm.cancel(R.drawable.stat_notify_sms);

						}

					}

					startActivityForResult(intent, REQUEST_VIEW_CBSMS);
				}
			}
		});

	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);

		//init();
		//startQuery();
		refreshMessageList();
	}

	private void init() {
		MessagingNotification.cancelNotification(getApplicationContext(),
				SIM_FULL_NOTIFICATION_ID);

		updateState(SHOW_BUSY);
		startQuery();
	}

	private class QueryHandler extends AsyncQueryHandler {
		private final CellBroadcastSmsActivity mParent;

		public QueryHandler(ContentResolver contentResolver,
				CellBroadcastSmsActivity parent) {
			super(contentResolver);
			mParent = parent;
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

			int count = cursor.getCount();
			int unreadCount = 0;
			if (count == 0) {
				Log.i(TAG, "onQueryComplete ,count == 0");

			} else {
				Log.i(TAG, "onQueryComplete ,count " + count);
			}
			mListCount = count;

			if (cursor != null) {
				if (!cursor.moveToFirst()) {
					// Let user know the SIM is empty
					Log.i(TAG, "onQueryComplete is empty");
					updateState(SHOW_EMPTY);
				} else if (mListAdapter == null) {

					if (cursor != null) {
						if (cursor.moveToFirst()) {

							count = cursor.getCount();

							for (int i = 0; i < count; ++i) {
								String address = cursor.getString(cursor
										.getColumnIndexOrThrow("address"));
								int iconid = cursor.getInt(cursor
										.getColumnIndexOrThrow(COLUMN_ICONID));
								Log.i(TAG, "onQueryComplete address: "
										+ address + "iconid:" + iconid);
								if (iconid == DEFAULT_ICON) {

									unreadCount++;
									Log.i(TAG, "onQueryComplete unreadCount "
											+ unreadCount);
								}
								cursor.moveToNext();
							}

							mUnreadCount = unreadCount;
						}
					}
				
					// mListAdapter = new SimpleCursorAdapter(mParent,
					// R.layout.cell_broadcast_list_item, mCursor,
					// new String[] { COLUMN_ICONID, COLUMN_ADDRESS },
					// new int[] { R.id.icon, R.id.from });
					mListAdapter = new MyListAdapter(mParent, cursor);
					Log.i(TAG, "onQueryComplete mListAdapter is empty (1)");
					// setPresenceIcon(R.drawable.ic_sms_mms_delivered);
					mSimList.setAdapter(mListAdapter);
					mSimList.setOnCreateContextMenuListener(mParent);
					updateState(SHOW_LIST);

					if (unreadCount == 0) {

						NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

						nm.cancel(R.drawable.stat_notify_sms);
					}

				} else {
					Log.i(TAG, "onQueryComplete mListAdapter is not empty");
					mListAdapter.changeCursor(cursor);
					mSimList.setAdapter(mListAdapter);
					mSimList.setOnCreateContextMenuListener(mParent);
					
					updateState(SHOW_LIST);
					if (unreadCount == 0) {

						NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

						nm.cancel(R.drawable.stat_notify_sms);
					}
				}
				//startManagingCursor(cursor);
				registerSimChangeObserver();

                if ( mCursor != null && !mCursor.isClosed() ) {
                    mCursor.close();
                }
                mCursor = cursor;
			} else {
				// Let user know the SIM is empty
				updateState(SHOW_EMPTY);
			}
		}
	}

	private  void startQuery() {
	    
		setTitle(getString(R.string.refreshing));
        setProgressBarIndeterminateVisibility(true);
        mQueryHandler.cancelOperation(THREAD_LIST_QUERY_TOKEN);
		try {
			mQueryHandler.startQuery(THREAD_LIST_QUERY_TOKEN, null,
					CBSMS_URI_T, null, null, null, null);
		} catch (SQLiteException e) {
			SqliteWrapper.checkSQLiteException(CellBroadcastSmsActivity.this, e);
		}
	}

	private  void refreshMessageList() {
		updateState(SHOW_BUSY);
		if (mCursor != null) {
			//stopManagingCursor(mCursor);
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
		
	}
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	Log.i(TAG, "onDestroy mIsOpen " +mIsOpen);
    	mIsOpen = false;
        if (mListAdapter != null) {
            mListAdapter.changeCursor(null);
        }
        if ( mCursor != null ) {
            mCursor.close();
        }
    }
    
	private void registerSimChangeObserver() {
		mContentResolver.registerContentObserver(CBSMS_URI_T, true,
				simChangeObserver);
	}

	public boolean onCreateOptionsMenu(Menu menu) {

		super.onCreateOptionsMenu(menu);

		Log.i(TAG, "onCreateOptionsMenu");
		if (mListCount > 0) {
			menu.add(0, MENU_DELETE, Menu.NONE,
					getString(R.string.delete_message)).setIcon(
					android.R.drawable.ic_menu_delete);
		}
		menu.add(0, MENU_SETTING, Menu.NONE,
				getString(R.string.menu_preferences)).setIcon(
				android.R.drawable.ic_menu_preferences);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		Log.i(TAG, "onOptionsItemSelectedSIM MenuItem");

		switch (item.getItemId()) {

		case MENU_DELETE: {
			Log.i(TAG, "onOptionsItemSelectedSIM MENU_DELETE");
			SqliteWrapper.delete(this, mContentResolver, CBSMS_URI_T, null,
					null);
			refreshMessageList();
			return true;
		}
		case MENU_SETTING: {
			Log.i(TAG, "onOptionsItemSelectedSIM MENU_SETTING");
			// deleteSelectedCellBroadcast();
			// Intent intent = new Intent(CellBroadcastSmsActivity.this,
			// CellBroadcastSmsSettingActivity.class);

			// startActivity(intent);

			Intent intent = new Intent(Intent.ACTION_VIEW);
			//intent.setClassName("com.android.phone","com.android.phone.CellBroadcastSmsSettingActivity");
			
			intent.setClassName("com.android.phone",
					"com.android.phone.CellBroadcastSmsSettingTabActivity");
			

			startActivity(intent);
			return true;

		}

		default:
			break;

		}
		return false;

	}

	private void addCellBroadcastSms() {
		long mTimestamp;
		ArrayList<String> data = getdata();
		ArrayList<String> mDests = getDest();

		Log.i(TAG, "addCellBroadcastSms ");

		for (int i = 0; i < 5; i++) {

			try {
				mTimestamp = System.currentTimeMillis();
				// Sms.addMessageToUri(mContentResolver,
				// Uri.parse("content://sms"), mDests.get(i),
				// data.get(i), null, mTimestamp,
				// true /* read */,
				// false
				// );
				Sms.CellBroadcastSms
						.addMessage(mContentResolver, mDests.get(i),
								data.get(i), null, mTimestamp, true /* read */);
			} catch (SQLiteException e) {
				SqliteWrapper.checkSQLiteException(this, e);
			}

		}

	}

	private  void updateState(int state) {
		if (mState == state) {
			return;
		}

		mState = state;
		switch (state) {
		case SHOW_LIST:
			mSimList.setVisibility(View.VISIBLE);
			mMessage.setVisibility(View.GONE);
			setTitle(getString(R.string.cell_broadcast_sms));
			setProgressBarIndeterminateVisibility(false);
			break;
		case SHOW_EMPTY:
			mSimList.setVisibility(View.GONE);
			mMessage.setVisibility(View.VISIBLE);
			setTitle(getString(R.string.cell_broadcast_sms));
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Log.i("onActivityResult: requestCode=" + requestCode, "resultCode"
				+ resultCode + "+data=" + data);
		switch (requestCode) {

		case REQUEST_VIEW_CBSMS:
			if (resultCode == 20) {

				Log.i(TAG, "onActivityResult: mPos" + mPos);

				Uri updateUri = ContentUris.withAppendedId(CBSMS_URI_T, mPos);

				SqliteWrapper.delete(CellBroadcastSmsActivity.this,
						mContentResolver, updateUri, null, null);
				// SqliteWrapper.delete(this, mContentResolver, simUri, null,
				// null);

			} else {

				if (mIconId == DEFAULT_ICON) {

					ContentValues values = new ContentValues();

					values.put(COLUMN_ICONID, READ_ICON);

					Uri updateUri = ContentUris.withAppendedId(CBSMS_URI_T,
							mPos);

					SqliteWrapper.update(CellBroadcastSmsActivity.this,
							mContentResolver, updateUri, values, null, null);

				}
			}
			refreshMessageList();

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

			TextView textView = (TextView) view.findViewById(R.id.from);

			ImageView imageView = (ImageView) view.findViewById(R.id.icon);

			String address = cursor.getString(cursor
					.getColumnIndexOrThrow(COLUMN_ADDRESS));
			int iconid = cursor.getInt(cursor
					.getColumnIndexOrThrow(COLUMN_ICONID));
			Log.i(TAG, "MyListAdapter address: " + address + "iconid:" + iconid);
			
			String channelName = matchName(address);
			if(null != channelName){
				textView.setText(channelName);
			}else{
			    textView.setText(address);
			}
			imageView.setImageResource(iconid);
			Log.i(TAG, "MyListAdapter address: " + address + "iconid:" + iconid);

		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			Log.i(TAG, "inflating new view");
			return mFactory.inflate(R.layout.cell_broadcast_list_item, parent,
					false);
		}

	}




	private String matchName(String id) {

		Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
				CBSMS_URI_SET, PROJECTION, null, null, null);
		
		int count = cursor.getCount();
        try{
		if (cursor != null) {
			if (cursor.moveToFirst()) {

				for (int i = 0; i < count; ++i) {
                    
					int channel_id = cursor.getInt(cursor
							.getColumnIndexOrThrow(COLUMN_CHANNEL_ID));
					
					int ids = Integer.parseInt(id);

					Log.i(TAG, "channel_id:" + channel_id + " ids:" + ids);
					
					if (channel_id == ids )

					{
						String channel_name = cursor.getString(cursor
								.getColumnIndexOrThrow(COLUMN_CHANNEL_NAME));
						Log.i(TAG, "channel_id:" + channel_id
								+ " channel_name:" + channel_name);

						return channel_name;
					}

					cursor.moveToNext();

				}
			}
		}
        }finally {
            cursor.close();
        }
		return null;

	}
	
	public static  boolean IschangeList(){
		Log.i(TAG, "changeList:  mIsOpen " + mIsOpen);
		
		return mIsOpen;
	}

}
