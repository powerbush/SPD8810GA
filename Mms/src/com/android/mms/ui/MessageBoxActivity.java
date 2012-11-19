package com.android.mms.ui;

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

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
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.R;
import com.android.mms.data.ContactList;
import com.android.mms.data.CursorMap;
import com.android.mms.transaction.PushReceiver;
import com.android.mms.ui.MessageFolderActivity;

public class MessageBoxActivity extends Activity {
    private static final String TAG = "MessageBoxActivity";
    public static final String action = "sprd.intent.action.boxmsgview";
    private static final Uri uri_inbox_mms = Uri.parse("content://mms/inbox");
    private static final Uri uri_icc = Uri.parse("content://sms/icc");
    private static final int MENU_DELETE_ALL = 0;
    /*private static final int MENU_MSG_LIST = 1;*/
    private static final int MENU_COMPOSE_NEW = 2;
    private static final int MENU_SIM_CAPACITY = 3;
    private static final int MENU_DELETE_ALL_SIM = 4;
    private static final int LONG_PRESS_MENU_VIEW = 1;
    private static final int LONG_PRESS_MENU_VIEW_CONTACT = 2;
    private static final int LONG_PRESS_MENU_ADD_TO_CONTACTS = 3;
    private static final int UPDATE_TITLE = 4;
    private static final int LONG_PRESS_MENU_CALL_BACK = 5;
    private static final int LONG_PRESS_MENU_SEND_SMS = 6;
    private static final int LONG_PRESS_MENU_COPY_TO_PHONE_MEMORY = 7;
    private static final int MENU_DELETE_FROM_SIM = 9;
    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private static final String INBOX = "inbox";
    private static final String OUTBOX = "outbox";
    private static final String SENT = "sent";
    private static final String DRAFTS = "drafts";
    private static final String SIMCARD = "simcard";
    public static String boxType = "";
    private boolean hasMsg = false;
    private int mState;
    private ProgressDialog progressDialog;
    private TextView mTitle;
    private ListView mMsgList;
    private TextView mEmptyMsg;
    private MsgBoxAdapter mAdapter;
    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private MessageSimListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;
    int count_drafts = 0;
    private static final String MMS_LAUNCH_MODE_PATH = "/data/data/com.android.mms/launchmode";
    public static ContactList mBoxMsgRecipients;

    //Add for Dualsim
    private int phoneId = 0;
    private Intent mIntentSmsReceiver = null;
    private Intent mIntentMmsReceiver = null;
    private Intent mIntentSmsSender = null;
    private Intent mIntentMmsSender = null;
    private static final Uri uri_icc1 = Uri.parse("content://sms/icc1");

    // query params for caller id lookup
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
                    + "(SELECT raw_contact_id "
                    + " FROM phone_lookup"
                    + " WHERE normalized_number GLOB('+*'))";
    // Utilizing private API
    private static final Uri PHONES_WITH_PRESENCE_URI = Data.CONTENT_URI;

    private static final String[] CALLER_ID_PROJECTION = new String[] {
            Phone.NUMBER,                   // 0
            Phone.LABEL,                    // 1
            Phone.DISPLAY_NAME,             // 2
            Phone.CONTACT_ID,               // 3
            Phone.CONTACT_PRESENCE,         // 4
            Phone.CONTACT_STATUS,           // 5
    };

    private static final Uri EMAIL_WITH_PRESENCE_URI = Data.CONTENT_URI;
    private static final String EMAIL_SELECTION = "UPPER(" + Email.DATA + ")=UPPER(?) AND "
            + Data.MIMETYPE + "='" + Email.CONTENT_ITEM_TYPE + "'";

    private static final String[] EMAIL_PROJECTION = new String[] {
            Email.DISPLAY_NAME,           // 0
            Email.CONTACT_PRESENCE,       // 1
            Email.CONTACT_ID,             // 2
            Phone.DISPLAY_NAME,           //
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.msg_box_list);
        setTitle();
        initResource();
        startQuery();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (INBOX.equals(boxType)) {
            // Listen for receive sms
            mIntentSmsReceiver = registerReceiver(mSmsReceiver, new IntentFilter(MessageFolderActivity.mSmsReceiveAction));
            // Listen for receive mms
            mIntentMmsReceiver = registerReceiver(mMmsReceiver, new IntentFilter(MessageFolderActivity.mMmsReceiveAction));
        }
        if (OUTBOX.equals(boxType) || SENT.equals(boxType)) {
            // Listen for send sms
            mIntentSmsSender = registerReceiver(mSmsSender, new IntentFilter(MessageFolderActivity.mSmsSendAction));
            // Listen for send mms
            mIntentMmsSender = registerReceiver(mMmsSender, new IntentFilter(PushReceiver.NOTIFY_SHOW_MMS_REPORT_ACTION));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (INBOX.equals(boxType)) {
            if(mIntentSmsReceiver != null) {
                try {
                    unregisterReceiver(mSmsReceiver);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                mIntentSmsReceiver = null;
            }
            if(mIntentMmsReceiver != null) {
                try {
                    unregisterReceiver(mMmsReceiver);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                mIntentMmsReceiver = null;
            }
        }
        if (OUTBOX.equals(boxType) || SENT.equals(boxType)) {
            if(mIntentSmsSender != null) {
                try {
                    unregisterReceiver(mSmsSender);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                mIntentSmsSender = null;
            }
            if(mIntentMmsSender != null) {
                try {
                    unregisterReceiver(mMmsSender);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                mIntentMmsSender = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null) {
            progressDialog.cancel();
        }
        if ( mAdapter != null ) {
            mAdapter.changeCursor(null);
        }
        if ( mListAdapter != null ) {
            mListAdapter.changeCursor(null);
        }
        if ( mCursor != null ) {
            mCursor.close();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DRAFTS.equals(boxType)) {
            new GetMsgDraftCountTask().execute((Void)null);
        } else if (SENT.equals(boxType)) {
            new GetMsgSentCountTask().execute((Void)null);
        }
        startQuery();
    }

    private void setTitle() {
        mTitle = (TextView) findViewById(R.id.box_title);
        Intent it = getIntent();
        Bundle bundle = it.getExtras();
        if (bundle != null) {
            boxType = bundle.getString("boxType");
            phoneId = bundle.getInt("phoneId");
            Log.i(TAG,"phoneId is"+phoneId);
        }
        if (INBOX.equals(boxType)) {
            mTitle.setText(getString(R.string.folder_inbox));
        } else if (OUTBOX.equals(boxType)) {
            mTitle.setText(getString(R.string.folder_outbox));
        } else if (SENT.equals(boxType)) {
            mTitle.setText(getString(R.string.folder_sent));
        } else if (DRAFTS.equals(boxType)) {
            mTitle.setText(getString(R.string.folder_drafts));
        } else if (SIMCARD.equals(boxType)) {
        	if(MessageUtils.isMSMS){
                if (phoneId == 0) {
                    mTitle.setText(getString(R.string.folder_simcard1));
                } else if (phoneId == 1) {
                    mTitle.setText(getString(R.string.folder_simcard2));
                } else {
                    // do nothing
                }
            }else{
                mTitle.setText(getString(R.string.folder_simcard));
            }
        }
    }

    private void initResource() {
        mMsgList = (ListView) findViewById(R.id.box_messages);
        mEmptyMsg = (TextView) findViewById(R.id.empty_message);
        progressDialog = new ProgressDialog(this);
        CharSequence title = getString(R.string.pref_title_manage_sim_messages);
        progressDialog.setTitle(title);
        progressDialog.setMessage(getText(R.string.wait_message));
        progressDialog.setCancelable(false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (SIMCARD.equals(boxType)) {
            if (mState == SHOW_LIST) {
                menu.add(0, MENU_DELETE_ALL_SIM, 0, R.string.menu_delete_box_msg)
                        .setIcon(android.R.drawable.ic_menu_delete);
            }
            //Modify for Dualsim
            SmsManager smsManager = null;
            if(MessageUtils.isMSMS){
            	smsManager = SmsManager.getDefault(phoneId);
            }else{
            	smsManager = SmsManager.getDefault();
            }
            if (smsManager.getSimCapacity() != null) {
                menu.add(0, MENU_SIM_CAPACITY, 0, R.string.menu_sim_capacity).setIcon(
                        android.R.drawable.ic_menu_save);
            }
        } else {
            if (hasMsg) {
                menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_box_msg)
                        .setIcon(android.R.drawable.ic_menu_delete);
            }
            /*menu.add(0, MENU_MSG_LIST, 0, R.string.menu_msg_list).setIcon(
                    R.drawable.ic_menu_list);*/
        }
        if (INBOX.equals(boxType) || DRAFTS.equals(boxType)) {
            menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                    com.android.internal.R.drawable.ic_menu_compose);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE_ALL:
            Intent itdel = new Intent(this, MessageBoxDeleteActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("boxType", boxType);
            itdel.putExtras(bundle);
            startActivity(itdel);
            this.finish();
            break;
        /*case MENU_MSG_LIST:
            Intent it = new Intent(this, ConversationList.class);
            setLaunchMode(MMS_LAUNCH_MODE_PATH, "conversation");
            Bundle bd = new Bundle();
            bd.putBoolean("fromFolder", true);
            it.putExtras(bd);
            startActivity(it);
            this.finish();
            break;*/
        case MENU_COMPOSE_NEW:
            Intent itnew = new Intent(this, ComposeMessageActivity.class);
            startActivity(itnew);
            break;
        case MENU_SIM_CAPACITY:
            viewCapacityDialog(getCapacityDetails(this,phoneId));
            break;
        case MENU_DELETE_ALL_SIM:
            Map<Integer, Map<Integer, String>> curMap = new HashMap<Integer, Map<Integer, String>>();
            mCursor.moveToFirst();
            for (int i=0; i< mCursor.getCount(); i++) {
                Map<Integer,String> mMap = new HashMap<Integer, String>();
                mMap.put(1, mCursor.getString(1));//address
                mMap.put(2, mCursor.getString(3));//body
                mMap.put(3, mCursor.getString(4));//date
                mMap.put(4, mCursor.getString(6));//index_on_icc
                curMap.put(i, mMap);
                mCursor.moveToNext();
            }
            CursorMap c = new CursorMap();
            c.map = curMap;
            Intent simDeleteIntent = new Intent(this, MessageBoxDeleteSimActivity.class);
            Bundle b = new Bundle();
            b.putString("boxType", boxType);
            b.putInt("phoneId", phoneId);
            Log.i(TAG,"MessageBoxDeleteActivity phoneId is"+phoneId);
            b.putParcelable("curMap", c);
            simDeleteIntent.putExtras(b);;
            startActivity(simDeleteIntent);
            this.finish();
            break;
        default:
            return true;
        }
        return false;
    }

    private class QueryHandler extends AsyncQueryHandler {
        private final MessageBoxActivity mParent;

        public QueryHandler(ContentResolver contentResolver,
                MessageBoxActivity parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            sendHander();
            hander.sendEmptyMessage(UPDATE_TITLE);
            if (cursor != null) {
                if (!cursor.moveToFirst()) {
                    updateState(SHOW_EMPTY);
                } else {
                    mListAdapter = new MessageSimListAdapter(mParent, cursor);
                    mMsgList.setAdapter(mListAdapter);
                    mMsgList.setOnCreateContextMenuListener(mSimMsgListOnCreateContextMenuListener);
                    updateState(SHOW_LIST);
                }
            } else {
                // Let user know the msg is empty
                updateState(SHOW_EMPTY);
            }
            if ( mCursor != null && !mCursor.isClosed() ) {
                mCursor.close();
            }
            mCursor = cursor;
        }
        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            refreshMessageList();
        }
    }

    private void startQuery() {
        try {
            if (INBOX.equals(boxType)) {
                String mStr = " _id,date,sub,sub_cs,thread_id,msg_id,read,recipient_ids,msg_type from (select canonical_addresses.address as _id,sms.date as date,sms.body as sub,'' as sub_cs,sms.thread_id as thread_id,sms._id as msg_id,sms.read as read,threads.recipient_ids as recipient_ids,'s' as msg_type from sms,canonical_addresses,threads where sms.type = 1 and sms.thread_id = threads._id and threads.recipient_ids = canonical_addresses._id union all select canonical_addresses.address as _id,pdu.date * 1000 as date,pdu.sub as sub,pdu.sub_cs as sub_cs,pdu.thread_id as thread_id,pdu._id as msg_id,pdu.read as read,threads.recipient_ids as recepient_ids,'m' as msg_type from canonical_addresses,addr,pdu,threads where addr.type = 137 and addr.msg_id = pdu._id and pdu.msg_box = 1 and pdu.thread_id = threads._id and threads.recipient_ids = canonical_addresses._id) order by date desc --";
                setListView(mStr);
            } else if (OUTBOX.equals(boxType)) {
                String mStr = " * from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select * from sms where (type=4 or type=5 or type=6) group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select * from pdu,addr where (msg_box=4 or msg_box=5 or msg_box=6) and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
                setListView(mStr);
            } else if(SIMCARD.equals(boxType)){
            	showProgressDialog();
            	if(phoneId == 0){
                	mQueryHandler.startQuery(9, null, uri_icc, null, null, null,null);
                }else if(phoneId == 1){
                	mQueryHandler.startQuery(9, null, uri_icc1, null, null, null,null);
                }
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void updateState(int state) {
        if (mState == state) {
            return;
        }
        mState = state;
        switch (state) {
        case SHOW_LIST:
            mMsgList.setVisibility(View.VISIBLE);
            mEmptyMsg.setVisibility(View.GONE);
            setProgressBarIndeterminateVisibility(false);
            break;
        case SHOW_EMPTY:
            mMsgList.setVisibility(View.GONE);
            mEmptyMsg.setVisibility(View.VISIBLE);
            setProgressBarIndeterminateVisibility(false);
            break;
        case SHOW_BUSY:
            mMsgList.setVisibility(View.GONE);
            mEmptyMsg.setVisibility(View.GONE);
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);
            break;
        default:
            Log.e(TAG, "Invalid State");
        }
    }

    private Handler hander = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                if (null != progressDialog) {
                    progressDialog.hide();
                }
            }
        }
    };

    private void showProgressDialog() {
        if (null != progressDialog && progressDialog.isShowing()) {
            progressDialog.hide();
        }
        if (null != progressDialog && !isFinishing()) {
            progressDialog.show();
        }
    }

    private void sendHander() {
        Message message = new Message();
        message.what = 1;
        hander.sendMessage(message);
    }

    private void setListView(String str) {
        Cursor cursor = null;
        cursor = getContentResolver().query(uri_inbox_mms,
                new String[] { str }, null, null, null);
        if ( mAdapter != null ) {
            mAdapter.changeCursor(null);
        }
        mAdapter = new MsgBoxAdapter(this, R.layout.box_messages, cursor,
                new String[] {}, new int[] {});

        mMsgList.setAdapter(mAdapter);
        mMsgList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (view != null) {
                    Cursor cursor = (Cursor) mAdapter.getItem(position);
                    if (null != cursor && 0 < cursor.getCount()) {
                        try {
                            Intent it = new Intent(MessageBoxActivity.this,
                                    ComposeMessageActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putString("boxmsgFlg", "true");
                            bundle.putString("boxmsgAddress", cursor.getString(cursor
                                    .getColumnIndexOrThrow("_id")));
                            bundle.putString("boxmsgDate", cursor.getString(cursor
                                    .getColumnIndexOrThrow("date")));
                            bundle.putString("boxmsgSubject", cursor.getString(cursor
                                    .getColumnIndexOrThrow("sub")));
                            bundle.putString("boxmsgThreadId", cursor.getString(cursor
                                    .getColumnIndexOrThrow("thread_id")));
                            bundle.putString("boxmsgMsgId", cursor.getString(cursor
                                    .getColumnIndexOrThrow("msg_id")));
                            bundle.putString("boxmsgType", cursor.getString(cursor
                                    .getColumnIndexOrThrow("msg_type")));
                            bundle.putString("boxType", boxType);
                            String recipitent_ids = cursor.getString(cursor.getColumnIndex("recipient_ids"));
                            mBoxMsgRecipients = ContactList.getByIds(recipitent_ids, true);
                            it.putExtras(bundle);
                            startActivity(it);
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                }
            }
        });
        mMsgList.setOnCreateContextMenuListener(mMsgListOnCreateContextMenuListener);
        if ((cursor == null) || (cursor.getCount() == 0)) {
            updateState(SHOW_EMPTY);
        } else {
            updateState(SHOW_LIST);
        }
        if (cursor != null && cursor.getCount() > 0) {
            hasMsg = true;
        } else {
            hasMsg = false;
        }
    }

    static class MsgBoxAdapter extends SimpleCursorAdapter {
        int addressIdx;
        int dateIdx;
        int subjectIdx;
        int sub_csIdx;
        int readIdx;
        int recipitent_idsIdx;
        int msg_typeIdx;
        // String boxType;
        public MsgBoxAdapter(Context context, int layout, Cursor c,
                String[] from, int[] to) {
            super(context, layout, c, from, to);
            addressIdx = c.getColumnIndexOrThrow("_id");
            dateIdx = c.getColumnIndexOrThrow("date");
            subjectIdx = c.getColumnIndexOrThrow("sub");
            sub_csIdx = c.getColumnIndexOrThrow("sub_cs");
            readIdx = c.getColumnIndexOrThrow("read");
            recipitent_idsIdx = c.getColumnIndexOrThrow("recipient_ids");
            msg_typeIdx = c.getColumnIndexOrThrow("msg_type");
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            TextView tv_address = (TextView) view
                    .findViewById(R.id.box_msg_address);
            String recipitent_ids = cursor.getString(recipitent_idsIdx);

            if (recipitent_ids != null) {
                ContactList recipients = ContactList.getByIds(recipitent_ids, true);
                tv_address.setText(recipients.formatNames(", "));
            }

            TextView tv_date = (TextView) view.findViewById(R.id.box_msg_date);
            Long datel = cursor.getLong(dateIdx);
            String date = MessageUtils.formatTimeStampString(context, datel);
            tv_date.setText(date);

            TextView tv_subject = (TextView) view
                    .findViewById(R.id.box_msg_subject);
            String sub_cs = cursor.getString(sub_csIdx);
            String subject = "";
            if (!"".equals(sub_cs)) {
                subject = MessageUtils.extractEncStrFromCursor(cursor, subjectIdx, sub_csIdx);
            } else {
                subject = cursor.getString(subjectIdx);
            }
            tv_subject.setText(subject);

            ImageView mImageView = (ImageView) view
                    .findViewById(R.id.msg_type_image);
            Long read = cursor.getLong(readIdx);
            String msg_type = cursor.getString(msg_typeIdx);
            if (INBOX.equals(boxType)) {
                if (read == 0) {
                    if ("s".equals(msg_type)) {
                        mImageView.setBackgroundResource(R.drawable.msg_unread);
                    } else {
                        mImageView.setBackgroundResource(R.drawable.mms_unread);
                    }
                } else {
                    if ("s".equals(msg_type)) {
                        mImageView.setBackgroundResource(R.drawable.msg_readed);
                    } else {
                        mImageView.setBackgroundResource(R.drawable.mms_readed);
                    }
                }
            } else if (OUTBOX.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    mImageView.setBackgroundResource(R.drawable.ic_outbox);
                } else {
                    mImageView.setBackgroundResource(R.drawable.ic_outbox_mms);
                }
            } else if (SENT.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    mImageView.setBackgroundResource(R.drawable.ic_sent);
                } else {
                    mImageView.setBackgroundResource(R.drawable.ic_sent_mms);
                }
            } else if (DRAFTS.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    mImageView.setBackgroundResource(R.drawable.msg_drafts_sms);
                } else {
                    mImageView.setBackgroundResource(R.drawable.msg_drafts_mms);
                }
            }
        }
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
        //fix bug 10490 start
        String capaStr = "";
        try {
        	SmsManager smsManager = null;
        	if(MessageUtils.isMSMS){
        		smsManager = SmsManager.getDefault(phoneId);
        	}else{
        		smsManager = SmsManager.getDefault();
        	}
            capaStr = smsManager.getSimCapacity();
        } catch(NullPointerException e) {
            capaStr = " : ";
        }
        //fix bug 10490 end
        String[] splitStr = capaStr.split(":");
        details.append(res.getString(R.string.menu_sim_capacity_used));
        details.append(splitStr[0]);
        details.append('\n');
        details.append(res.getString(R.string.menu_sim_capacity_total));
        details.append(splitStr[1]);
        return details.toString();
    }

    private final OnCreateContextMenuListener mMsgListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v,
                    ContextMenuInfo menuInfo) {
                    Cursor cursor = (Cursor) mAdapter.getCursor();
                    String address = cursor.getString(mAdapter.addressIdx);
                    if (address == null || "insert-address-token".equals(address)) {
                        address = getDraftMsgAddress(address, cursor);
                    }
                    String recipitent_ids = cursor.getString(mAdapter.recipitent_idsIdx);
                    ContactList recipients = ContactList.getByIds(recipitent_ids, true);
                    int size = recipients.size();
                    String mAddress = recipients.formatNames(", ");
                    String callBackString = getString(R.string.menu_call_back).replace("%s", mAddress);
                    String sendSmsString = getString(R.string.menu_send_sms).replace("%s", mAddress);
                    menu.setHeaderTitle(mAddress);
                    menu.add(0, LONG_PRESS_MENU_VIEW, 0, R.string.menu_view_box_message);
                    if(!recipients.isEmpty() && size == 1){
                    	if (recipients.get(0).existsInDatabase()) {
                            menu.add(0, LONG_PRESS_MENU_VIEW_CONTACT, 0, R.string.menu_view_contact);
                        } else {
                            menu.add(0, LONG_PRESS_MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts);
                        }
                    }

                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
                            + address));
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    if (size == 1) {
                        menu.add(0, LONG_PRESS_MENU_CALL_BACK, 0, callBackString)
                        .setIntent(intent);
                    }
                    intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"
                            + address));
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    if (size == 1) {
                        menu.add(0, LONG_PRESS_MENU_SEND_SMS, 0, sendSmsString)
                        .setIntent(intent);
                    }
            }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (SIMCARD.equals(boxType)) {
            AdapterView.AdapterContextMenuInfo info;
            try {
                 info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            } catch (ClassCastException exception) {
                Log.e(TAG, "Bad menuInfo.", exception);
                return false;
            }
            final Cursor simCursor = (Cursor) mListAdapter.getItem(info.position);
            switch (item.getItemId()) {
                case LONG_PRESS_MENU_COPY_TO_PHONE_MEMORY:
                    copyToPhoneMemory(simCursor);
                    return true;
                case MENU_DELETE_FROM_SIM:
                    confirmDeleteDialog(new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                            if(simCursor.isClosed()){
                                Toast.makeText(MessageBoxActivity.this,MessageBoxActivity.this.getResources().getString(R.string.sms_init),/*fixed CR<NEWMS00133563> by luning*/
                                Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d(TAG, "delete one message");
                                deleteFromSim(simCursor);
                            }
                        }
                    }, R.string.confirm_delete_SIM_message);
                    return true;
                default:
                    break;
            }
        } else {
            Cursor cursor = (Cursor) mAdapter.getCursor();
            String address = cursor.getString(mAdapter.addressIdx);
            if (address == null || "insert-address-token".equals(address)) {
                Log.i(TAG,"address == null || .equals(address)");
                address = getDraftMsgAddress(address, cursor);
                Log.i(TAG,"address is"+address);
            }
            Long personId = null;
            String selection = null;
            if(Mms.isEmailAddress(address)){
            	selection = EMAIL_SELECTION;
                Log.i(TAG,"selection is"+selection);
                Cursor c = getContentResolver().query(
                		EMAIL_WITH_PRESENCE_URI,
                		EMAIL_PROJECTION,
                        selection,
                        new String[] { address },
                        null);
                if ( c != null ) {
                    try {
                    	int size = c.getCount();
                        if (c.moveToFirst()) {
                            personId = c.getLong(2);
                            Log.i(TAG,"c.moveToFirst() personId is"+personId);
                        }
                    } finally {
                        c.close();
                    }
                }
            }else{
            	selection = CALLER_ID_SELECTION.replace("+",
                        PhoneNumberUtils.toCallerIDMinMatch(address));
                Log.i(TAG,"selection is"+selection);
                Cursor c = getContentResolver().query(
                        PHONES_WITH_PRESENCE_URI,
                        CALLER_ID_PROJECTION,
                        selection,
                        new String[] { address },
                        null);
                if ( c != null ) {
                    try {
                    	int size = c.getCount();
                        if (c.moveToFirst()) {
                            personId = c.getLong(3);
                            Log.i(TAG,"c.moveToFirst() personId is"+personId);
                        }
                    } finally {
                        c.close();
                    }
                }
            }

            switch (item.getItemId()) {
                case LONG_PRESS_MENU_VIEW: {
                    Intent it = new Intent(MessageBoxActivity.this,
                            ComposeMessageActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putString("boxmsgFlg", "true");
                    bundle.putString("boxmsgAddress",
                            cursor.getString(cursor.getColumnIndexOrThrow("_id")));
                    bundle.putString("boxmsgDate",
                            cursor.getString(cursor.getColumnIndexOrThrow("date")));
                    bundle.putString("boxmsgSubject",
                            cursor.getString(cursor.getColumnIndexOrThrow("sub")));
                    bundle.putString("boxmsgThreadId",
                            cursor.getString(cursor.getColumnIndexOrThrow("thread_id")));
                    bundle.putString("boxmsgMsgId",
                            cursor.getString(cursor.getColumnIndexOrThrow("msg_id")));
                    bundle.putString("boxmsgType",
                            cursor.getString(cursor.getColumnIndexOrThrow("msg_type")));
                    bundle.putString("boxType", boxType);
                    it.putExtras(bundle);
                    startActivity(it);
                    break;
                }
                case LONG_PRESS_MENU_VIEW_CONTACT: {
                	if(personId != null){
                		Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("content://com.android.contacts/contacts/"
                                        + personId));
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                            startActivity(intent);
                	}
                    break;
                }
                case LONG_PRESS_MENU_ADD_TO_CONTACTS: {
                	String recipitent_ids = cursor.getString(mAdapter.recipitent_idsIdx);
                	ContactList recipients = ContactList.getByIds(recipitent_ids, true);
                    String mAddress = recipients.formatNames(", ");
                    Log.i(TAG, "LONG_PRESS_MENU_ADD_TO_CONTACTS mAddress is"+mAddress);
                    startActivity(createAddContactIntent(mAddress));
                    break;
                }
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    private final OnCreateContextMenuListener mSimMsgListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v,
                    ContextMenuInfo menuInfo) {
                menu.add(0, LONG_PRESS_MENU_COPY_TO_PHONE_MEMORY, 0,
                        R.string.sim_copy_to_phone_memory);
                menu.add(0, MENU_DELETE_FROM_SIM, 0,
                        R.string.sim_delete);
            }
    };

    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient

        Log.i(TAG, "address is"+address);
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        return intent;
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
    private void deleteFromSim(Cursor cursor) {
        if(null != cursor && !cursor.isClosed()/*add by luning for CR<NEWMS00139156> at 2011.11.11*/){
            String messageIndexString =
                    cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
            Uri simUri = null;
            if(MessageUtils.isMSMS){
            	if(phoneId == 0){
            		simUri = uri_icc.buildUpon().appendPath(messageIndexString).build();
            	}else if(phoneId == 1){
            		simUri = uri_icc1.buildUpon().appendPath(messageIndexString).build();
            	}else{
            		//
            	}
            }else{
            	simUri = uri_icc.buildUpon().appendPath(messageIndexString).build();
            }
            mQueryHandler.startDelete(0, null, simUri, null, null);/*add by luning for CR<NEWMS00139156> at 2011.11.11*/
        }
    }
    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        startQuery();
    }

    private class GetMsgDraftCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mTitle.setText(getString(R.string.folder_drafts));
            String mStr = " * from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select *  from sms where type=3 group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select *  from pdu,addr where msg_box=3 and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
            setListView(mStr);
        }
    }

    private class GetMsgSentCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mTitle.setText(getString(R.string.folder_sent));
            String mStr = " * from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select *  from sms where type=2 group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select *  from pdu,addr where msg_box=2 and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
            setListView(mStr);
        }
    }

    private final BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            new ReceiveMsgTask().execute((Void)null);
        }
    };

    private final BroadcastReceiver mMmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            new ReceiveMsgTask().execute((Void)null);
        }
    };

    private final BroadcastReceiver mSmsSender = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OUTBOX.equals(boxType)) {
                new ReceiveMsgTask().execute((Void)null);
            } else {
                new GetMsgSentCountTask().execute((Void)null);
            }
        }
    };

    private final BroadcastReceiver mMmsSender = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OUTBOX.equals(boxType)) {
                new ReceiveMsgTask().execute((Void)null);
            } else {
                new GetMsgSentCountTask().execute((Void)null);
            }
        }
    };

    private class ReceiveMsgTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            startQuery();
        }
    }

    private void setLaunchMode(String filename, String mode) {
        try {
            FileOutputStream is = new FileOutputStream(filename);
            byte buffer[] = mode.getBytes();
            is.write(buffer);
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDraftMsgAddress(String address,Cursor cursor) {
        address = cursor.getString(cursor.getColumnIndex("recipient_ids"));
        Cursor addrCur = null;
        String str = " address from canonical_addresses where _id = '" + address + "' --";
        addrCur = getContentResolver().query(uri_inbox_mms,
                new String[] { str }, null, null, null);
        if (addrCur != null) {
            try {
                if (addrCur.moveToFirst()) {
                    address = addrCur.getString(addrCur
                            .getColumnIndexOrThrow("address"));
                }
            } finally {
                addrCur.close();
            }
        }
        return address;
    }
}
