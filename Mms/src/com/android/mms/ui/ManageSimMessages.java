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

import com.android.mms.R;
import android.database.sqlite.SqliteWrapper;
import com.android.mms.transaction.MessagingNotification;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class ManageSimMessages extends Activity
        implements View.OnCreateContextMenuListener {
    private Uri mIccUri;
    private static final String TAG = "ManageSimMessages";
    private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
    private static final int MENU_DELETE_FROM_SIM = 1;
    private static final int MENU_VIEW = 2;
    private static final int OPTION_MENU_DELETE_ALL = 0;
    private static final int OPTION_MENU_SIM_CAPACITY = 1;

    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;

    private ProgressDialog progressDialog;

    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private ListView mSimList;
    private TextView mMessage;
    private MessageListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;
    private Context mContext;
    public static final int SIM_FULL_NOTIFICATION_ID = 234;

    private int mPhoneId;
    private SimContactsReceiver mReceiver ;

    private ArrayList<String> selectState;
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mContext=this;
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.sim_list);
        mSimList = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);

        progressDialog = new ProgressDialog(this);
        CharSequence title = getString(R.string.pref_title_manage_sim_messages);
        progressDialog.setTitle(title);
        progressDialog.setMessage(getText(R.string.wait_message));
        progressDialog.setCancelable(false);
        Log.d(TAG, "[dlg]creat");
        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        init();
    }

    private void init() {
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);
	Intent intent=getIntent();
	mPhoneId=intent.getIntExtra(Phone.PHONE_ID,-1);
	if (mPhoneId==-1) {
	    finish();
	    return;
	}
	if (mPhoneId==0) {
	    mIccUri=Uri.parse("content://sms/icc");	
	} else {
	    mIccUri=Uri.parse("content://sms/icc"+mPhoneId);	    
	}
        updateState(SHOW_BUSY);
        Log.d(TAG, "init startQuery()");
        startQuery();
    }

    private void showProgressDialog() {
        if (null != progressDialog && progressDialog.isShowing()) {
            Log.d(TAG, "[dlg]showProgressDialog hide");
            progressDialog.hide();
        }
        if (!isFinishing()) {
            progressDialog.show();
            Log.d(TAG, "[dlg]showProgressDialog show");
        }
    }

    private Handler hander = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                if (null != progressDialog) {
                    Log.d(TAG, "[dlg]handleMessage hide");
                    progressDialog.hide();
                }
            }
        }
    };

    private class QueryHandler extends AsyncQueryHandler {
        private final ManageSimMessages mParent;

        public QueryHandler(
                ContentResolver contentResolver, ManageSimMessages parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(
                int token, Object cookie, Cursor cursor) {
            sendHander();
            if (cursor != null) {
                if (!cursor.moveToFirst()) {
                    // Let user know the SIM is empty
                    updateState(SHOW_EMPTY);
                } else if (mListAdapter == null) {
                    // Note that the MessageListAdapter doesn't support auto-requeries. If we
                    // want to respond to changes we'd need to add a line like:
                    //   mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
                    // See ComposeMessageActivity for an example.
                    mListAdapter = new MessageListAdapter(
                            mParent, cursor, mSimList, false, null);
                    mSimList.setAdapter(mListAdapter);
                    mSimList.setOnCreateContextMenuListener(mParent);
                    updateState(SHOW_LIST);
                } else {
                    mListAdapter.changeCursor(cursor);
                    updateState(SHOW_LIST);
                }
                //startManagingCursor(mCursor);
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
            if ( mCursor != null && !mCursor.isClosed() ) {
                mCursor.close();
            }
            mCursor = cursor;
            Log.d(TAG, "onQueryComplete() mCursor = " + mCursor);
        }

		/*fixed by luning for CR<NEWMS00139156> at 2011.11.11*/
		protected void onDeleteComplete(int token, Object cookie, int result) {
			super.onDeleteComplete(token, cookie, result);
            if (selectState != null && selectState.size() > 0) {

                Log.d(TAG, "onChange DelAllPending");
                String cursorIndxe = selectState.get(0);
                deleteFromSim(cursorIndxe);
                selectState.remove(cursorIndxe);

            } else {
                Log.d(TAG, "onChange refreshMessageList");
                refreshMessageList();
            }
		}
    }

    private void startQuery() {
        try {
            mQueryHandler.startQuery(0, null, mIccUri, null, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        if (mCursor != null) {
            //stopManagingCursor(mCursor);
            mCursor.close();
        }
        Log.d(TAG, "refreshMessageList startQuery()");
        startQuery();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0, MENU_COPY_TO_PHONE_MEMORY, 0,
                 R.string.sim_copy_to_phone_memory);
        menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);

        // TODO: Enable this once viewMessage is written.
        // menu.add(0, MENU_VIEW, 0, R.string.sim_view);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException exception) {
            Log.e(TAG, "Bad menuInfo.", exception);
            return false;
        }

        final Cursor cursor = (Cursor) mListAdapter.getItem(info.position);

        switch (item.getItemId()) {
            case MENU_COPY_TO_PHONE_MEMORY:
                copyToPhoneMemory(cursor);
                return true;
            case MENU_DELETE_FROM_SIM:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        if(cursor.isClosed()){
                            Toast.makeText(mContext,mContext.getResources().getString(R.string.sms_init),/*fixed CR<NEWMS00133563> by luning*/
                            Toast.LENGTH_SHORT).show();
                        } else {
                            Log.d(TAG, "delete one message");
                            updateState(SHOW_BUSY);
                            deleteFromSim(getMessageIndexString(cursor));
                        }

                    }
                }, R.string.confirm_delete_SIM_message);
                sendHander();
                return true;
            case MENU_VIEW:
                viewMessage(cursor);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        //for bugzilla 13356
        final TelephonyManager telManager = (TelephonyManager) getSystemService(
                PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mPhoneId));
        if(TelephonyManager.SIM_STATE_READY != telManager.getSimState()){
            finish();
        }
        mReceiver = new SimContactsReceiver();
        IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
    }

    private class SimContactsReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }            
            String state = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
            int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, -1);
            final TelephonyManager telManager = (TelephonyManager) getSystemService(
                    PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mPhoneId));
            Log.i(TAG, "phoneId=" + phoneId + ", state="+state);
            if(phoneId==mPhoneId && TelephonyManager.SIM_STATE_READY != telManager.getSimState()){
                ManageSimMessages.this.finish();
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        this.unregisterReceiver(mReceiver);
    }

    private void copyToPhoneMemory(Cursor cursor) {
        String address = cursor.getString(
                cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        Long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

        try {
            if (isIncomingMessage(cursor)) {
                Sms.Inbox.addMessage(mContentResolver, address, body, null, date, true /* read */);
            } else {
                Sms.Sent.addMessage(mContentResolver, address, body, null, date);
            }
            Toast.makeText(this, R.string.move_message_to_phone_memory, Toast.LENGTH_SHORT).show();
        } catch (SQLiteException e) {
            Toast.makeText(this, R.string.can_not_copy_the_message, Toast.LENGTH_SHORT).show();
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private boolean isIncomingMessage(Cursor cursor) {
        int messageStatus = cursor.getInt(
                cursor.getColumnIndexOrThrow("status"));

        return (messageStatus == SmsManager.STATUS_ON_ICC_READ) ||
               (messageStatus == SmsManager.STATUS_ON_ICC_UNREAD);
    }

    private void deleteFromSim(String messageIndexString) {
        Uri simUri = mIccUri.buildUpon().appendPath(messageIndexString).build();
        mQueryHandler.startDelete(0, null, simUri, null, null);/*add by luning for CR<NEWMS00139156> at 2011.11.11*/
    }

    private void deleteAllFromSim() {
        Cursor delCursor = (Cursor) mListAdapter.getCursor();

        selectState = new ArrayList<String>();

        if (delCursor != null) {
            if (delCursor.moveToFirst()) {

                String messageIndexString = getMessageIndexString(delCursor);
                selectState.add(messageIndexString);

                while (delCursor.moveToNext()) {
                    messageIndexString = getMessageIndexString(delCursor);
                    selectState.add(messageIndexString);
                }

                deleteFromSim(messageIndexString);
                selectState.remove(messageIndexString);

            }
        }
    }

    private String getMessageIndexString (Cursor delCursor) {
        String messageIndexString = delCursor.getString(delCursor
                .getColumnIndexOrThrow("index_on_icc"));
        return messageIndexString;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if ((null != mCursor) && (mCursor.getCount() > 0) && mState == SHOW_LIST) {
            menu.add(0, OPTION_MENU_DELETE_ALL, 0, R.string.menu_delete_messages).setIcon(
                    android.R.drawable.ic_menu_delete);
        }
        menu.add(0, OPTION_MENU_SIM_CAPACITY, 0, R.string.menu_sim_capacity).setIcon(
                android.R.drawable.ic_menu_save);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_DELETE_ALL:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "delete all messages");
                        updateState(SHOW_BUSY);
                        deleteAllFromSim();
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_all_SIM_messages);
                sendHander();
                break;
            case OPTION_MENU_SIM_CAPACITY:
                String capacityDetails = getCapacityDetails(this,this.mPhoneId);
                //getCapacityDetails
                viewCapacityDialog(capacityDetails);
                break;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(messageId);

        builder.show();
    }

    private void viewCapacityDialog(String detailstring) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_sim_capacity);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, null);
        builder.setMessage(detailstring);
        builder.show();
    }    
    private static String getCapacityDetails(Context context,int phoneId) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();
        //SmsManager smsManager = SmsManager.getDefault(phoneId);
        SmsManager smsManager = SmsManager.getDefault(phoneId);
        String capaStr = smsManager.getSimCapacity();
        Log.d(TAG, "[sms]getSimCapacity =" + capaStr);
        String[] splitStr = capaStr.split(":");
        details.append(res.getString(R.string.menu_sim_capacity_used));
        Log.d(TAG, "[sms]getSimCapacity simUsed:" + splitStr[0]);
        Log.d(TAG, "[sms]getSimCapacity simTotal:" + splitStr[1]);
        details.append(splitStr[0]);
        details.append('\n');
        details.append(res.getString(R.string.menu_sim_capacity_total));
        details.append(splitStr[1]);

        return details.toString();
    }

    private void updateState(int state) {
        showProgressDialog();
        if (mState == state) {
            return;
        }

        mState = state;
        switch (state) {
            case SHOW_LIST:
                mSimList.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.GONE);
                if(MessageUtils.isMSMS){
                	if(mPhoneId == 0){
                		setTitle(getString(R.string.sim_manage_messages_title1));
                	}else if(mPhoneId == 1){
                		setTitle(getString(R.string.sim_manage_messages_title2));
                	}else{
                		//do nothing
                	}
                }else{
                	setTitle(getString(R.string.sim_manage_messages_title));
                }
                
                setProgressBarIndeterminateVisibility(false);
                break;
            case SHOW_EMPTY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                if(MessageUtils.isMSMS){
                	if(mPhoneId == 0){
                		setTitle(getString(R.string.sim_manage_messages_title1));
                	}else if(mPhoneId == 1){
                		setTitle(getString(R.string.sim_manage_messages_title2));
                	}else{
                		//do nothing
                	}
                }else{
                	setTitle(getString(R.string.sim_manage_messages_title));
                }
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
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
        }
        Log.d(TAG, "[dlg]onDestroy");
        if (null != progressDialog && progressDialog.isShowing()) {
            Log.d(TAG, "[dlg]dismiss");
            progressDialog.dismiss();
        }
        if (mListAdapter != null) {
            mListAdapter.changeCursor(null);
        }
    }

    private void viewMessage(Cursor cursor) {
        // TODO: Add this.
    }

    private void sendHander(){
        Message message = new Message();
        message.what = 1;
        hander.sendMessage(message);
    }
}

