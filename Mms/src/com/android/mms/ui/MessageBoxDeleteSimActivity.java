package com.android.mms.ui;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
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

import com.android.mms.R;
import com.android.mms.data.CursorMap;

public class MessageBoxDeleteSimActivity extends Activity implements View.OnClickListener,OnItemClickListener{

    private static final Uri ICC_URI = Uri.parse("content://sms/icc");
    static final int COLUMN_MSG_TYPE            = 0;
    static final int COLUMN_SMS_ADDRESS         = 1;
    static final int COLUMN_SMS_BODY            = 3;
    static final int COLUMN_SMS_DATE            = 4;
    private ContentResolver mContentResolver;
    private ProgressDialog mDelMessProDialog;
    private TextView mTitle;
    private ListView mMsgList;
    private CheckBox mSelectAllCheckbox;
    private Button mDeleteBtn;
    private Button mCancelBtn;
    private static final int MENU_DELETE_ALL = 0;
    private static final int MENU_MSG_LIST = 1;
    private Cursor mCursor;
    private SimAdapter simAdapter;
    ItemView itemView;
    private Context mContext;
    private int mDelCount = 0;
    Map<Integer, Map<Integer, String>> curMap = null;

    //Add for Dualsim
    private static final Uri ICC_URI1 = Uri.parse("content://sms/icc1");
    private int PhoneId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mContext = this.getApplicationContext();
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mContentResolver = getContentResolver();
        setContentView(R.layout.msg_box_delete_list);
        setTitle();
        initResource();
        Intent curIt = getIntent();
        Bundle curBundle = curIt.getExtras();
        CursorMap c = curBundle.getParcelable("curMap");
        curMap = c.map;
        simAdapter = new SimAdapter(getBaseContext(),mCursor);
        mMsgList.setAdapter(simAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( mCursor != null ) {
            mCursor.close();
        }
    }

    private void setTitle() {
        mTitle = (TextView) findViewById(R.id.box_title);
        mTitle.setText(getString(R.string.folder_simcard));
        Intent it = getIntent();
        Bundle bundle = it.getExtras();
        if (bundle != null) {
        	PhoneId = bundle.getInt("phoneId");
        }
    }
    private void initResource() {
        mMsgList = (ListView) findViewById(R.id.box_messages_delete);
        mMsgList.setOnItemClickListener(this);
        mSelectAllCheckbox = (CheckBox) findViewById(R.id.checkbox_selected_all);
        mSelectAllCheckbox.setOnClickListener(this);
        mDeleteBtn = (Button) findViewById(R.id.DeleteButton);
        mDeleteBtn.setOnClickListener(this);
        mDeleteBtn.setEnabled(false);
        mCancelBtn = (Button) findViewById(R.id.CancelButton);
        mCancelBtn.setOnClickListener(this);
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
            for ( int i = 0; i < simAdapter.selectedMap.size(); i++ ) {
                if ( simAdapter.selectedMap.get(i) ) {
                    String mid = MessageBoxDeleteSimActivity.this.curMap.get(i).get(4);
                    Uri simUri = null;
                    if(MessageUtils.isMSMS){
                    	if(PhoneId == 0){
                    		simUri = ICC_URI.buildUpon().appendPath(mid).build();
                    	}else if(PhoneId ==1){
                    		simUri = ICC_URI1.buildUpon().appendPath(mid).build();
                    	}else{
                    		//
                    	}
                    }else{
                    	simUri = ICC_URI.buildUpon().appendPath(mid).build();
                    }

                    SqliteWrapper.delete(mContext, mContentResolver, simUri, null, null);
                }
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            if (mDelCount == 0) {
                Intent it = new Intent(MessageBoxDeleteSimActivity.this, MessageBoxActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("boxType", "simcard");
                if(MessageUtils.isMSMS){
                	bundle.putInt("phoneId", PhoneId);
                }
                it.putExtras(bundle);
                startActivity(it);
                MessageBoxDeleteSimActivity.this.finish();
                mDelMessProDialog.dismiss();
            }
        }
    }

    class SimAdapter extends BaseAdapter{
        Map<Integer,Boolean> selectedMap;
        Cursor cur;
        Context context;

        public SimAdapter(Context context, Cursor c) {
            super();
            this.cur = c;
            this.context = context;
            selectedMap = new HashMap<Integer,Boolean>();
            for(int i = 0 ;i<curMap.size();i++){
                selectedMap.put(i, false);
            }
        }

        @Override
        public int getCount() {
            return curMap.size();
        }

        @Override
        public Object getItem(int position) {
            return curMap.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null){
                convertView = LayoutInflater.from(context).inflate(R.layout.box_messages_delete,null);
                itemView = new ItemView();
                itemView.tv_address = (TextView)convertView.findViewById(R.id.box_msg_address);
                itemView.tv_date = (TextView)convertView.findViewById(R.id.box_msg_date);
                itemView.tv_subject=(TextView)convertView.findViewById(R.id.box_msg_subject);
                itemView.checkbox = (CheckBox)convertView.findViewById(R.id.checkbox_select);
                itemView.mImageView = (ImageView)convertView.findViewById(R.id.msg_type_image);
                convertView.setTag(itemView);
            }
            ItemView itemView =(ItemView)convertView.getTag();

            String address = curMap.get(position).get(1);
            itemView.tv_address.setText(address);

            long datel = Long.parseLong(curMap.get(position).get(3));
            String date = MessageUtils.formatTimeStampString(context, datel);
            itemView.tv_date.setText(String.valueOf(date));

            String subject = curMap.get(position).get(2);
            itemView.tv_subject.setText(subject);
            itemView.checkbox.setChecked(selectedMap.get(position));
            ItemMessage im = new ItemMessage();
            im.id =curMap.get(position).get(4);

            itemView.checkbox.setChecked(selectedMap.get(position));
            return convertView;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,long id) {
        ItemView views = (ItemView) view.getTag();
        views.checkbox.toggle();
        simAdapter.selectedMap.put(position, views.checkbox.isChecked());
        if (simAdapter.selectedMap.containsValue(false)) {
                mSelectAllCheckbox.setChecked(false);
        } else {
            mSelectAllCheckbox.setChecked(true);
        }

        if (simAdapter.selectedMap.containsValue(true)) {
                mDeleteBtn.setEnabled(true);
        } else {
            mDeleteBtn.setEnabled(false);
        }
        simAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        if(v == mSelectAllCheckbox){
            if(mSelectAllCheckbox.isChecked()){
                for(int i = 0;i<curMap.size();i++){
                    simAdapter.selectedMap.put(i, true);
                    ItemMessage im = new ItemMessage();
                    im.id = curMap.get(i).get(4);
                }
                mDeleteBtn.setEnabled(true);
            }else{
                for(int i=0;i<curMap.size();i++){
                    simAdapter.selectedMap.put(i, false);
                }
                mDeleteBtn.setEnabled(false);
            }
            simAdapter.notifyDataSetChanged();
        } else if (v == mDeleteBtn) {
            new AlertDialog.Builder(this).setTitle(R.string.clearConfirmation_title)
            .setMessage(R.string.clearConfirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog,int which){
                    mDelMessProDialog = new ProgressDialog(MessageBoxDeleteSimActivity.this);
                    mDelMessProDialog.setTitle(getString(R.string.delete_message));
                    mDelMessProDialog.setMessage(getText(R.string.wait_message));
                    final DeleteMessageAsyncTask task = new DeleteMessageAsyncTask();
                    mDelMessProDialog.show();
                    task.execute(simAdapter.selectedMap);
                }
            }).setCancelable(true).create().show();
        } else if (v == mCancelBtn) {
            Intent it = new Intent(MessageBoxDeleteSimActivity.this, MessageBoxActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("boxType", "simcard");
            if(MessageUtils.isMSMS){
            	bundle.putInt("phoneId", PhoneId);
            }
            it.putExtras(bundle);
            startActivity(it);
            this.finish();
        }
    }

    class ItemMessage {
        String id;
        public boolean equals(Object o) {
            boolean result = false;
            ItemMessage im = (ItemMessage) o;
            if (id.equals(im.id)) {
                result = true;
            } else {
                result = false;
            }
            return result;
        }
    }
}