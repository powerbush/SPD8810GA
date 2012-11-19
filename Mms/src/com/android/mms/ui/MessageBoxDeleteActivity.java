package com.android.mms.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;

import com.android.mms.R;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;


public class MessageBoxDeleteActivity extends Activity implements View.OnClickListener,OnItemClickListener {
    private static final String TAG = "MessageBoxDeleteActivity";
    private static final Uri uri_inbox_mms = Uri.parse("content://mms/inbox");
    private static final Uri uri_icc = Uri.parse("content://sms/icc");
    private static final int MENU_DELETE_ALL = 0;
    private static final int MENU_MSG_FOLDER = 1;
    public static String boxType = "";
    public  CheckBox mSelectAllCheckbox;
    private ProgressDialog progressDialog;
    private ProgressDialog mDelMessProDialog;
    private TextView mTitle;
    private ListView mMsgList;
    private Button cancelBtn;
    private Button mDeleteBtn;
    public  MessageListAdapter mMsgListAdapter;
    private ContentResolver mContentResolver;
    private Context mContext;
    private static Cursor currentCursor = null;
    private MsgBoxAdapter mAdapter;
    private CheckBox mLockCheckBox;
    private ItemView itemView;
    private static final String INBOX = "inbox";
    private static final String OUTBOX = "outbox";
    private static final String SENT = "sent";
    private static final String DRAFTS = "drafts";
    private static final String SIMCARD = "simcard";
    private static final short PROJECTION_ID = 0;
    private static final short PROJECTION_DATE = 1;
    private static final short PROJECTION_SUB = 2;
    private static final short PROJECTION_SUB_CS = 3;
    private static final short PROJECTION_THREAD_ID = 4;
    private static final short PROJECTION_MSG_ID = 5;
    private static final short PROJECTION_READ = 6;
    private static final short PROJECTION_RECIPIENT_IDS = 7;
    private static final short PROJECTION_MSG_TYPE = 8;
    private AsyncQueryHandler mQueryHandler = null;
    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;
    private MessageSimListAdapter mListAdapter = null;
    private TextView mEmptyMsg;
    // private CheckBox checkbox;
    //Add for Dualsim


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mContentResolver = getContentResolver();
        mContext = this;
        setContentView(R.layout.msg_box_delete_list);
        mQueryHandler = new QueryHandler(mContentResolver, this);
        progressDialog = new ProgressDialog(this);
        CharSequence title = getString(R.string.pref_title_manage_sim_messages);
        progressDialog.setTitle(title);
        progressDialog.setMessage(getText(R.string.wait_message));
        progressDialog.setCancelable(false);
        setTitle();
        initResource();
        startQuery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( mMsgListAdapter != null ) {
            mMsgListAdapter.changeCursor(null);
        }
        if ( mListAdapter != null ) {
            mListAdapter.changeCursor(null);
        }
        if ( currentCursor != null ) {
            currentCursor.close();
        }
    }

    private void setTitle() {
        mTitle = (TextView) findViewById(R.id.box_title);
        Intent it = getIntent();
        Bundle bundle = it.getExtras();
        if (bundle != null) {
            boxType = bundle.getString("boxType");
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
            mTitle.setText(getString(R.string.folder_simcard));
        }
    }
    private void initResource() {
        mMsgList = (ListView) findViewById(R.id.box_messages_delete);
        mSelectAllCheckbox = (CheckBox) findViewById(R.id.checkbox_selected_all);
        mSelectAllCheckbox.setOnClickListener(this);
        mDeleteBtn = (Button) findViewById(R.id.DeleteButton);
        mDeleteBtn.setOnClickListener(this);
        mDeleteBtn.setEnabled(false);
        cancelBtn = (Button) findViewById(R.id.CancelButton);
        cancelBtn.setOnClickListener(this);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_MSG_FOLDER, 0, R.string.menu_msg_folder).setIcon(
                R.drawable.ic_menu_folder);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE_ALL:
            // createNewMessage();
            Intent intentDelete = new Intent(this,
                    MessageBoxDeleteActivity.class);
            startActivity(intentDelete);
            break;

        case MENU_MSG_FOLDER:
            Intent it = new Intent(this, MessageFolderActivity.class);
            startActivity(it);
            break;
        default:
            return true;
        }
        return false;
    }

    private void startQuery() {
        try {
            if (INBOX.equals(boxType)) {//1
                String mStr = "_id,date,sub,sub_cs,thread_id,msg_id,read,recipient_ids,msg_type from (select canonical_addresses.address as _id,sms.date as date,sms.body as sub,'' as sub_cs,sms.thread_id as thread_id,sms._id as msg_id,sms.read as read,threads.recipient_ids as recipient_ids,'s' as msg_type from sms,canonical_addresses,threads where sms.type = 1 and sms.thread_id = threads._id and threads.recipient_ids = canonical_addresses._id union all select canonical_addresses.address as _id,pdu.date * 1000 as date,pdu.sub as sub,pdu.sub_cs as sub_cs,pdu.thread_id as thread_id,pdu._id as msg_id,pdu.read as read,threads.recipient_ids as recepient_ids,'m' as msg_type from canonical_addresses,addr,pdu,threads where addr.type = 137 and addr.msg_id = pdu._id and pdu.msg_box = 1 and pdu.thread_id = threads._id and threads.recipient_ids = canonical_addresses._id) order by date desc --";
                setListView(mStr);
            } else if (OUTBOX.equals(boxType)) {//4
                String mStr = "_id,date,sub,sub_cs,thread_id,msg_id,read,recipient_ids,msg_type from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select * from sms where (type=4 or type=5 or type=6) group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select * from pdu,addr where (msg_box=4 or msg_box=5 or msg_box=6) and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
                setListView(mStr);
            } else if (SENT.equals(boxType)) {//2
                String mStr = "_id,date,sub,sub_cs,thread_id,msg_id,read,recipient_ids,msg_type from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select *  from sms where type=2 group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select *  from pdu,addr where msg_box=2 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
                setListView(mStr);
            } else if (DRAFTS.equals(boxType)) {//3
                String mStr = "_id,date,sub,sub_cs,thread_id,msg_id,read,recipient_ids,msg_type from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select *  from sms where type=3 group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select *  from pdu,addr where msg_box=3 and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) order by date desc --";
                setListView(mStr);
            }else if (SIMCARD.equalsIgnoreCase(boxType)){
                mQueryHandler.startQuery(0, null, uri_icc, null, null, null,null);
            }
        } catch (SQLiteException e) {
            //SqliteWrapper.checkSQLiteException(this, e);
        }
    }
    private void showProgressDialog() {
        if (null != progressDialog && progressDialog.isShowing()) {
            progressDialog.hide();
        }
        if (null != progressDialog && !isFinishing()) {
            progressDialog.show();
        }
    }

    private void setListView(String str) {
        if (currentCursor != null) {
            currentCursor.close();
        }
        currentCursor = getContentResolver().query(uri_inbox_mms,new String[] { str }, null, null, null);
        mAdapter = new MsgBoxAdapter(this,currentCursor);
        mMsgList.setAdapter(mAdapter);
        mMsgList.setOnItemClickListener(this);
    }

    class MsgBoxAdapter extends BaseAdapter {
        Map<Integer,Boolean> selectedMap;
        Map<Integer,ItemMessage> selectedIm;
        Cursor cur;
        Context context;

        // String boxType;
        public MsgBoxAdapter(Context context, Cursor c) {
            super();
            cur = c;
            selectedMap = new HashMap<Integer,Boolean>();
            for(int i = 0 ;i<c.getCount();i++){
                selectedMap.put(i, false);
            }
            selectedIm = new HashMap<Integer,ItemMessage>();
        }
        @Override
        public int getCount() {
            return cur.getCount();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
             if (cur.moveToPosition(position)) {
                 return cur;
              } else {
                 return null;
              }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null){
                convertView = LayoutInflater.from(MessageBoxDeleteActivity.this).inflate(
                        R.layout.box_messages_delete,null);
                itemView = new ItemView();
                itemView.tv_address = (TextView)convertView.findViewById(R.id.box_msg_address);
                itemView.tv_date = (TextView)convertView.findViewById(R.id.box_msg_date);
                itemView.tv_subject=(TextView)convertView.findViewById(R.id.box_msg_subject);
                itemView.checkbox = (CheckBox)convertView.findViewById(R.id.checkbox_select);
                itemView.mImageView = (ImageView)convertView.findViewById(R.id.msg_type_image);
                convertView.setTag(itemView);
            }
            cur.moveToPosition(position);
            ItemView itemView =(ItemView)convertView.getTag();

            String recipitent_ids = cur.getString(PROJECTION_RECIPIENT_IDS);
            if (recipitent_ids != null) {
                ContactList recipients = ContactList.getByIds(recipitent_ids, true);
                itemView.tv_address.setText(recipients.formatNames(", "));
            }

            long datel = cur.getLong(PROJECTION_DATE);
            String date = MessageUtils.formatTimeStampString(MessageBoxDeleteActivity.this, datel);
            itemView.tv_date.setText(date);

            String subject = cur.getString(PROJECTION_SUB);
            String subject_cs = cur.getString(PROJECTION_SUB_CS);
            String mSub = "";
            if (!"".equals(subject_cs)) {
                mSub = MessageUtils.extractEncStrFromCursor(cur, PROJECTION_SUB, PROJECTION_SUB_CS);
            } else {
                mSub = subject;
            }
            itemView.tv_subject.setText(mSub);

            itemView.checkbox.setChecked(selectedMap.get(position));
            ItemMessage im = new ItemMessage();
            im.id = cur.getString(PROJECTION_ID);
            im.msg_type = cur.getString(PROJECTION_MSG_TYPE);
            im.msg_id = cur.getString(PROJECTION_MSG_ID);
            im.thread_id= cur.getString(PROJECTION_THREAD_ID);
            im.date = datel;
            selectedIm.put(position, im);
            Long read = cur.getLong(PROJECTION_READ);
            String msg_type = cur.getString(PROJECTION_MSG_TYPE);
            if (INBOX.equals(boxType)) {
                if (read == 0) {
                    if ("s".equals(msg_type)) {
                        itemView.mImageView.setBackgroundResource(R.drawable.msg_unread);
                    } else {
                        itemView.mImageView.setBackgroundResource(R.drawable.mms_unread);
                    }
                } else {
                    if ("s".equals(msg_type)) {
                        itemView.mImageView.setBackgroundResource(R.drawable.msg_readed);
                    } else {
                        itemView.mImageView.setBackgroundResource(R.drawable.mms_readed);
                    }
                }
            } else if (OUTBOX.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    itemView.mImageView.setBackgroundResource(R.drawable.ic_outbox);
                } else {
                    itemView.mImageView.setBackgroundResource(R.drawable.ic_outbox_mms);
                }
            } else if (SENT.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    itemView.mImageView.setBackgroundResource(R.drawable.ic_sent);
                } else {
                    itemView.mImageView.setBackgroundResource(R.drawable.ic_sent_mms);
                }
            } else if (DRAFTS.equals(boxType)) {
                if ("s".equals(msg_type)) {
                    itemView.mImageView.setBackgroundResource(R.drawable.msg_drafts_sms);
                } else {
                    itemView.mImageView.setBackgroundResource(R.drawable.msg_drafts_mms);
                }
            }
            return convertView;
        }
    }
    @Override
    public void onClick(View v) {
        if(v == mSelectAllCheckbox){
            if(mSelectAllCheckbox.isChecked()){
                for(int i = 0;i<mAdapter.getCount();i++){
                    mAdapter.selectedMap.put(i, true);
                    mAdapter.cur.moveToPosition(i);
                     ItemMessage im = new ItemMessage();
                     im.id = mAdapter.cur.getString(PROJECTION_ID);
                     im.msg_type = mAdapter.cur.getString(PROJECTION_MSG_TYPE);
                     im.msg_id = mAdapter.cur.getString(PROJECTION_MSG_ID);
                     im.thread_id= mAdapter.cur.getString(PROJECTION_THREAD_ID);
                     im.date = mAdapter.cur.getLong(PROJECTION_DATE);
                     mAdapter.selectedIm.put(i, im);
                }
                mDeleteBtn.setEnabled(true);
            }else{
                for(int i=0;i<mAdapter.getCount();i++){
                    mAdapter.selectedMap.put(i, false);
                }
                mDeleteBtn.setEnabled(false);
            }
            mAdapter.notifyDataSetChanged();
        }
        else if(v == mDeleteBtn){
            if (DRAFTS.equals(boxType)) {
                new AlertDialog.Builder(MessageBoxDeleteActivity.this).setTitle(R.string.clearConfirmation_title)
                .setMessage(R.string.clearConfirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog,int which){
                        mDelMessProDialog = new ProgressDialog(MessageBoxDeleteActivity.this);
                        mDelMessProDialog.setTitle(getString(R.string.delete_message));
                        mDelMessProDialog.setMessage(getText(R.string.wait_message));
                        mDelMessProDialog.setCancelable(false);
                        final DeleteMessageAsyncTask task = new DeleteMessageAsyncTask();
                        mDelMessProDialog.show();
                        task.execute(mAdapter.selectedMap);
                    }
                }).setCancelable(true).create().show();
            } else {
                View contents = View.inflate(MessageBoxDeleteActivity.this, R.layout.delete_box_message_dialog_view, null);
                mLockCheckBox = (CheckBox)contents.findViewById(R.id.delete_locked);
                new AlertDialog.Builder(MessageBoxDeleteActivity.this).setTitle(R.string.clearConfirmation_title)
                .setView(contents)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog,int which){
                        mDelMessProDialog = new ProgressDialog(MessageBoxDeleteActivity.this);
                        mDelMessProDialog.setTitle(getString(R.string.delete_message));
                        mDelMessProDialog.setMessage(getText(R.string.wait_message));
                        mDelMessProDialog.setCancelable(false);
                        final DeleteMessageAsyncTask task = new DeleteMessageAsyncTask();
                        mDelMessProDialog.show();
                        task.execute(mAdapter.selectedMap,mLockCheckBox.isChecked());
                    }
                }).setCancelable(true).create().show();
            }
        }else if(v == cancelBtn){
            this.finish();
        }
    }

    public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        ItemView views = (ItemView) view.getTag();
        views.checkbox.toggle();
        mAdapter.selectedMap.put(position, views.checkbox.isChecked());
        if (mAdapter.selectedMap.containsValue(false)) {
                mSelectAllCheckbox.setChecked(false);
        } else {
            mSelectAllCheckbox.setChecked(true);
        }

        if (mAdapter.selectedMap.containsValue(true)) {
                mDeleteBtn.setEnabled(true);
        } else {
            mDeleteBtn.setEnabled(false);
        }
        mAdapter.notifyDataSetChanged();
    }

    private final class ItemView{
        TextView tv_address;
        TextView tv_date;
        TextView tv_subject;
        ImageView mImageView;
        CheckBox checkbox;
    }

    class DeleteMessageAsyncTask extends AsyncTask<Object,Void,Void>{

        protected Void doInBackground(Object... object) {
            String mWhere = "";
            if (DRAFTS.equals(boxType)) {
                mWhere = " locked = 0 ";
            } else {
                if (mLockCheckBox.isChecked()) {
                    mWhere = " (locked = 0 or locked = 1) ";
                } else {
                    mWhere = " locked = 0 ";
                }
            }
            String smsWhere = "";
            String mmsWhere = "";
            if (INBOX.equals(boxType)) {
                smsWhere = mWhere + " and type = 1 ";
                mmsWhere = mWhere + " and msg_box = 1 ";
            } else if (OUTBOX.equals(boxType)) {
                smsWhere = mWhere + " and (type = 4 or type = 5 or type = 6) ";
                mmsWhere = mWhere + " and (msg_box = 4 or msg_box = 5 or msg_box = 6) ";
            } else if (SENT.equals(boxType)) {
                smsWhere = mWhere + " and type = 2 ";
                mmsWhere = mWhere + " and msg_box = 2 ";
            } else if (DRAFTS.equals(boxType)) {
                smsWhere = " type = 3 ";
                mmsWhere = " msg_box = 3 ";
            }

            if (mSelectAllCheckbox.isChecked()) {
                if (INBOX.equals(boxType)) {
                    Conversation.markAllConversationsAsSeen(getApplicationContext());
                }
                getContentResolver().delete(Sms.CONTENT_URI, smsWhere, null);
                getContentResolver().delete(Mms.CONTENT_URI, mmsWhere, null);
            } else {
                for ( int i = 0; i < mAdapter.selectedMap.size(); i++ ) {
                    if ( mAdapter.selectedMap.get(i) ) {
                        ItemMessage im = (ItemMessage)mAdapter.selectedIm.get(i);
                        Uri mDeleteUri = null;
                        if("s".equals(im.msg_type)){
                            mDeleteUri = ContentUris.withAppendedId(Sms.CONTENT_URI, Long.parseLong(im.msg_id));
                            String selection = "";
                            if (DRAFTS.equals(boxType)) {
                                selection = " thread_id = " +im.thread_id + " and " + " date = " + im.date;
                            } else {
                                selection = " thread_id = " +im.thread_id + " and " + " date = " + im.date + " and " + mWhere;
                            }
                            Cursor delCursor = mContentResolver.query(Sms.CONTENT_URI, new String[] { "_id"},selection, null, null);
                            if ( delCursor != null ) {
                                try {
                                    while (delCursor.moveToNext()){
                                        getContentResolver().delete(ContentUris.withAppendedId(Sms.CONTENT_URI,delCursor.getLong(0)),null, null);
                                    }
                                } finally {
                                    delCursor.close();
                                }
                            }
                        } else if ("m".equals(im.msg_type)) {
                            mDeleteUri = ContentUris.withAppendedId(Mms.CONTENT_URI, Long.parseLong(im.msg_id));
                            mContentResolver.delete(mDeleteUri, mWhere, null);
                        }
                    }
                }
                // Always update notifications regardless of the read state.
                MessagingNotification.blockingUpdateAllNotifications(mContext);
            }
            return null;
        }
        protected void onPostExecute(Void result) {
            Intent it = new Intent(MessageBoxDeleteActivity.this, MessageBoxActivity.class);
            startActivity(it);
            MessageBoxDeleteActivity.this.finish();
            mDelMessProDialog.dismiss();
        }
    }
    class ItemMessage{

        String id ;
        String msg_type;
        String msg_id;
        String thread_id;
        long date;

        public boolean equals(Object o) {
            boolean result = false;
            ItemMessage im = (ItemMessage)o;
            if(id.equals(im.id)&&msg_type.equals(im.msg_type)&&msg_id.equals(im.msg_id)&&thread_id.equals(im.thread_id)){
                result = true;
            }else{
                result = false;
            }
            return result;
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
    private class QueryHandler extends AsyncQueryHandler {
        private final Context mParent;

        public QueryHandler(
                ContentResolver contentResolver, Context parent) {
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
                    mListAdapter = new MessageSimListAdapter(mParent, cursor);
                    mMsgList.setAdapter(mListAdapter);
                    updateState(SHOW_LIST);
                } else {
                    //mListAdapter.changeCursor(currentCursor);
                    updateState(SHOW_LIST);
                }
                //startManagingCursor(cursor);
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
            if ( currentCursor != null ) {
                currentCursor.close();
            }
            currentCursor = cursor;
            Log.d(TAG, "onQueryComplete() mCursor = " + currentCursor);
        }
    }
    private void sendHander() {
        Message message = new Message();
        message.what = 1;
        hander.sendMessage(message);
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
}