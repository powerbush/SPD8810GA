package com.android.mms.ui;

import java.io.FileOutputStream;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.PushReceiver;

public class MessageFolderActivity extends Activity {

    public static final int MENU_MSG_LIST = 0;
    public static final int MENU_COMPOSE_NEW = 1;
    public static final int MENU_PREFERENCES = 2;
    public static final int MENU_SEARCH = 3;
    public static final int MENU_CBSMS = 4;
    private static final Uri uri_inbox_mms = Uri.parse("content://mms/inbox");
    private static final Uri uri_inbox_sms = Uri.parse("content://sms/inbox");
    private static final Uri uri_mms = Uri.parse("content://mms");
    private static final Uri uri_drafts_mms = Uri.parse("content://mms/drafts");
    private static final Uri uri_drafts_sms = Uri.parse("content://sms/draft");
    private static final Uri uri_icc = Uri.parse("content://sms/icc");
    private TextView inbox_num;
    private TextView outbox_num;
    private TextView sent_num;
    private TextView drafts_num;
    private TextView simcard_num;
    int count_inbox = 0;
    int count_outbox = 0;
    int count_sent = 0;
    int count_drafts = 0;
    String count_simcard = "";
    int count_unread = 0;
    private static final String MMS_LAUNCH_MODE_PATH = "/data/data/com.android.mms/launchmode";
    private boolean[] mSmsReady = {false, false};
    public static final String mSmsReceiveAction = "android.provider.Telephony.SMS_RECEIVED";
    public static final String mMmsReceiveAction = "android.provider.Telephony.MMS_RECEIVED";
    public static final String mSmsSendAction = "android.provider.Telephony.SEND_SMS";
    private static final String mSmsInsertAction = "android.provide.NEW_MESSAGE_INSERT";
    private static final String mSimStoreAction = "android.provide.STORE_SIMCARD_SUCESS";
    //Add for Dualsim
    private static final Uri uri_icc1 = Uri.parse("content://sms/icc1");
    private TextView simcard_num1;
    private TextView simcard_num2;
    String count_simcard1 = "";
    String count_simcard2 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.msg_folder_screen);
        initResource();

        // process of clicked inbox
        final RelativeLayout rl_inbox = (RelativeLayout) findViewById(R.id.rl_inbox);
        rl_inbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent it = new Intent(MessageFolderActivity.this,
                        MessageBoxActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("boxType", "inbox");
                bundle.putString("msgCount", Integer.toString(count_inbox));
                it.putExtras(bundle);
                startActivity(it);
            }
        });

        // process of clicked outbox
        final RelativeLayout rl_outbox = (RelativeLayout) findViewById(R.id.rl_outbox);
        rl_outbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent it = new Intent(MessageFolderActivity.this,
                        MessageBoxActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("boxType", "outbox");
                bundle.putString("msgCount", Integer.toString(count_outbox));
                it.putExtras(bundle);
                startActivity(it);
            }
        });

        // process of clicked sent
        final RelativeLayout rl_sent = (RelativeLayout) findViewById(R.id.rl_sent);
        rl_sent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent it = new Intent(MessageFolderActivity.this,
                        MessageBoxActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("boxType", "sent");
                bundle.putString("msgCount", Integer.toString(count_sent));
                it.putExtras(bundle);
                startActivity(it);
            }
        });

        // process of clicked drafts
        final RelativeLayout rl_drafts = (RelativeLayout) findViewById(R.id.rl_drafts);
        rl_drafts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent it = new Intent(MessageFolderActivity.this,
                        MessageBoxActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("boxType", "drafts");
                bundle.putString("msgCount", Integer.toString(count_drafts));
                it.putExtras(bundle);
                startActivity(it);
            }
        });

        // process of clicked simcard
        if(MessageUtils.isMSMS){
        	final RelativeLayout rl_simcard1 = (RelativeLayout) findViewById(R.id.rl_simcard1);
        	rl_simcard1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                	if(simCardReady(0)){
//                		Intent intent=new Intent(MessageFolderActivity.this, ManageSimMessages.class);
//                		intent.putExtra(Phone.PHONE_ID,0);
//                        startActivity(intent);
                		Intent it = new Intent(MessageFolderActivity.this,MessageBoxActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("boxType", "simcard");
                        bundle.putString("msgCount", count_simcard1+"");
                        bundle.putInt("phoneId", 0);
                        it.putExtras(bundle);
                        startActivity(it);
                	}else{
                		Toast.makeText(MessageFolderActivity.this,
                                getString(R.string.sim_no_ready), Toast.LENGTH_LONG)
                                .show();
                	}
                }
            });
        	final RelativeLayout rl_simcard2 = (RelativeLayout) findViewById(R.id.rl_simcard2);
        	rl_simcard2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                	if(simCardReady(1)){
                		
//                		Intent intent=new Intent(MessageFolderActivity.this, ManageSimMessages.class);
//                		intent.putExtra(Phone.PHONE_ID,1);
//                      startActivity(intent);
                		Intent it = new Intent(MessageFolderActivity.this,MessageBoxActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("boxType", "simcard");
                        bundle.putString("msgCount", count_simcard2+"");
                        bundle.putInt("phoneId", 1);
                        it.putExtras(bundle);
                        startActivity(it);
                        
                	}else{
                		Toast.makeText(MessageFolderActivity.this,
                                getString(R.string.sim_no_ready), Toast.LENGTH_LONG)
                                .show();
                	}
                }
            });

        }else{
        	final RelativeLayout rl_simcard = (RelativeLayout) findViewById(R.id.rl_simcard);
            rl_simcard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(MessageFolderActivity.this,
                            MessageBoxActivity.class);
                    Bundle bundle = new Bundle();
                    if (!isAnySmsReady()) {
                        Toast.makeText(MessageFolderActivity.this,
                                getString(R.string.sim_no_ready), Toast.LENGTH_LONG)
                                .show();
                    } else if ("0".equals(count_simcard)) {

                        bundle.putString("boxType", "simcard");
                        bundle.putString("msgCount", "0");
                        it.putExtras(bundle);
                        startActivity(it);
                    } else {
                        bundle.putString("boxType", "simcard");
                        bundle.putString("msgCount", count_simcard);
                        it.putExtras(bundle);
                        startActivity(it);
                    }
                }
            });
        }
        if (MmsApp.initMms) {
            Conversation.init(this, true);
        }
    }

    public boolean isAnySmsReady() {
        if ((TelephonyManager.getPhoneCount() <= 1 && mSmsReady[0]) ||
                (TelephonyManager.getPhoneCount() > 1 && (mSmsReady[0] || mSmsReady[1]))) {
            return true;
        } else {
            return false;
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        // Listen for broadcast intents that indicate the SMS is ready      
        IntentFilter filter = new IntentFilter();
        if(MessageUtils.isMSMS){
        	filter.addAction(PhoneFactory.getAction(TelephonyIntents.ACTION_IS_SIM_SMS_READY, 0));
        	filter.addAction(PhoneFactory.getAction(TelephonyIntents.ACTION_IS_SIM_SMS_READY, 1));
        }else{
        	filter.addAction(TelephonyIntents.ACTION_IS_SIM_SMS_READY);
        }
        registerReceiver(mReceiver, filter);
        // Listen for receive mms
        registerReceiver(mMmsReceiver, new IntentFilter(mMmsReceiveAction));
        // Listen for send sms
        registerReceiver(mSmsSender, new IntentFilter(mSmsSendAction));
        // Listen for send mms
        registerReceiver(mMmsSender, new IntentFilter(PushReceiver.NOTIFY_SHOW_MMS_REPORT_ACTION));
        // Listen for receive sms
        registerReceiver(mSmsReceiver, new IntentFilter(mSmsInsertAction));
        //Listen for sim card store message
        registerReceiver(mSimStoreMsg,new IntentFilter(mSimStoreAction));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setLayout();
        new GetMsgInboxCountTask().execute((Void)null);
        new GetMsgOutboxCountTask().execute((Void)null);
        new GetMsgSentCountTask().execute((Void)null);
        new GetMsgDraftCountTask().execute((Void)null);
        if(MessageUtils.isMSMS){
        	if(simCardReady(0)){
        		new GetMsgSimCountTaskEx(0).execute((Void)null);
        	}
        	if(simCardReady(1)){
        		new GetMsgSimCountTaskEx(1).execute((Void)null);
        	}
        }else{
            if(simCardReady(0)){
                new GetMsgSimCountTask().execute((Void)null);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        if (mMmsReceiver != null) {
            unregisterReceiver(mMmsReceiver);
        }
        if (mSmsSender != null) {
            unregisterReceiver(mSmsSender);
        }
        if (mMmsSender != null) {
            unregisterReceiver(mMmsSender);
        }
        if (mSmsReceiver != null) {
            unregisterReceiver(mSmsReceiver);
        }
        if (mSimStoreMsg != null) {
            unregisterReceiver(mSimStoreMsg);
        }
        Conversation.dismissInitConcatCacheDialog();
    }
    

    private void initResource() {
        inbox_num = (TextView) findViewById(R.id.inbox_num);
        outbox_num = (TextView) findViewById(R.id.outbox_num);
        sent_num = (TextView) findViewById(R.id.sent_num);
        drafts_num = (TextView) findViewById(R.id.drafts_num);
        if(MessageUtils.isMSMS){
        	simcard_num1 = (TextView) findViewById(R.id.simcard_num1);
        	simcard_num2 = (TextView) findViewById(R.id.simcard_num2);
        }else{
        	simcard_num = (TextView) findViewById(R.id.simcard_num);
        }
    }

    private void setLayout() {
        //inbox_num.setText(getString(R.string.load_count));
        //outbox_num.setText(getString(R.string.load_count));
        //sent_num.setText(getString(R.string.load_count));
        //drafts_num.setText(getString(R.string.load_count));
    	
    	if(MessageUtils.isMSMS){
    		simcard_num1.setText(getString(R.string.load_count));
    		simcard_num2.setText(getString(R.string.load_count));
        	if(simCardReady(0)){
        		LinearLayout rl_sim1 = (LinearLayout) findViewById(R.id.rl_simcard1_l);
            	if(rl_sim1 != null){
            		rl_sim1.setVisibility(View.VISIBLE);
            	}        		
        	}else{
        		LinearLayout rl_sim1 = (LinearLayout) findViewById(R.id.rl_simcard1_l);
            	if(rl_sim1 != null){
            		rl_sim1.setVisibility(View.GONE);
            	}
        	}
        	if(simCardReady(1)){
        		LinearLayout rl_sim2 = (LinearLayout) findViewById(R.id.rl_simcard2_l);
            	if(rl_sim2 != null){
            		rl_sim2.setVisibility(View.VISIBLE);
            	}        		
        	}else{
        		LinearLayout rl_sim2 = (LinearLayout) findViewById(R.id.rl_simcard2_l);
            	if(rl_sim2 != null){
            		rl_sim2.setVisibility(View.GONE);
            	}
        	}
        	
        	LinearLayout rl_sim = (LinearLayout) findViewById(R.id.rl_simcard_l);
        	if(rl_sim != null){
        		rl_sim.setVisibility(View.GONE);
        	}
        }else{
        	simcard_num.setText(getString(R.string.load_count));
        	LinearLayout rl_sim = (LinearLayout) findViewById(R.id.rl_simcard_l);
        	if(rl_sim != null){
        		rl_sim.setVisibility(View.VISIBLE);
        	}
        	
        	LinearLayout rl_sim1 = (LinearLayout) findViewById(R.id.rl_simcard1_l);
        	if(rl_sim1 != null){
        		rl_sim1.setVisibility(View.GONE);
        	}
        	
        	LinearLayout rl_sim2 = (LinearLayout) findViewById(R.id.rl_simcard2_l);
        	if(rl_sim2 != null){
        		rl_sim2.setVisibility(View.GONE);
        	}
        }
    }

    /*
     * obtain count of messages
     */
    private int getMsgCount(Uri mmsUri, Uri smsUri) {
        Cursor cur_mms = null;
        Cursor cur_sms = null;
        int count_mms = 0;
        int count_sms = 0;

        if (mmsUri != null) {
        	if(mmsUri.equals(uri_inbox_mms) || mmsUri.equals(uri_mms)){
        		cur_mms = getContentResolver()
                .query(mmsUri, null, "m_type != 134", null, null);
        	}else{
        		cur_mms = getContentResolver()
                .query(mmsUri, null, null, null, null);
        	}
        }
        if (smsUri != null) {
            cur_sms = getContentResolver()
                    .query(smsUri, null, null, null, null);
        }
        if (cur_mms != null) {
            try {
                count_mms = cur_mms.getCount();
            } finally {
                cur_mms.close();
            }
        }
        if (cur_sms != null) {
            try {
                count_sms = cur_sms.getCount();
            } finally {
                cur_sms.close();
            }
        }
        return count_mms + count_sms;
    }

    private int getUnreadCount(Uri mmsUri, Uri smsUri) {
        Cursor cur_mms = null;
        Cursor cur_sms = null;
        int count_mms = 0;
        int count_sms = 0;

        if (mmsUri != null) {
        	if(mmsUri.equals(uri_inbox_mms) || mmsUri.equals(uri_mms)){
        		cur_mms = getContentResolver()
                .query(mmsUri, null, "read = 0 AND m_type != 134", null, null);

        	}else{
        		cur_mms = getContentResolver()
                .query(mmsUri, null, "read = ?", new String[]{"0"}, null);
        	}
        }
        if (smsUri != null) {
            cur_sms = getContentResolver()
                    .query(smsUri, null, "read = ?", new String[]{"0"}, null);
        }
        if (cur_mms != null) {
            try {
                count_mms = cur_mms.getCount();
            } finally {
                cur_mms.close();
            }
        }
        if (cur_sms != null) {
            try {
                count_sms = cur_sms.getCount();
            } finally {
                cur_sms.close();
            }
        }
        return count_mms + count_sms;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_MSG_LIST, 0, R.string.menu_msg_list).setIcon(
                R.drawable.ic_menu_list);
        menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                com.android.internal.R.drawable.ic_menu_compose);
        menu.add(0, MENU_SEARCH, 0, android.R.string.search_go)
                .setIcon(android.R.drawable.ic_menu_search)
                .setAlphabeticShortcut(android.app.SearchManager.MENU_KEY);
        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(
                com.android.internal.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_CBSMS, 0, R.string.cell_broadcast_sms).setIcon(
                R.drawable.menu_cb);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_COMPOSE_NEW:
                Intent itnew = new Intent(this, ComposeMessageActivity.class);
                startActivity(itnew);
                break;
            case MENU_PREFERENCES:
            	if(MessageUtils.isMSMS){
            		Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putString("mode", "folder");
                    intent.putExtras(bundle);
                    startActivityIfNeeded(intent, -1);
            	}else{
                    if (isAnySmsReady()) {
                        Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("mode", "folder");
                        intent.putExtras(bundle);
                        startActivityIfNeeded(intent, -1);
                    } else {
                        Toast.makeText(this, this.getResources().getString(R.string.sim_no_ready),
                                Toast.LENGTH_LONG).show();
                    }
            	}
                break;
            case MENU_MSG_LIST:
                Intent it = new Intent(this, ConversationList.class);
                setLaunchMode(MMS_LAUNCH_MODE_PATH, "conversation");
                Bundle bundle = new Bundle();
                bundle.putBoolean("fromFolder", true);
                it.putExtras(bundle);
                startActivity(it);
                this.finish();
                break;
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_CBSMS:
                final Intent intent = new Intent(MessageFolderActivity.this,
                        CellBroadcastSmsActivity.class);
                startActivity(intent);
                break;
            default:
                return true;
        }
        return false;
    }

    private class GetMsgInboxCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            count_inbox = getMsgCount(uri_inbox_mms, uri_inbox_sms);
            count_unread = getUnreadCount(uri_inbox_mms, uri_inbox_sms);

            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            inbox_num.setText("(" + Integer.toString(count_unread) + "/"
                    + Integer.toString(count_inbox) + ")");
        }
    }
    private class GetMsgOutboxCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Cursor cursor = null;
            String mStr = " * from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select * from sms where (type=4 or type=5 or type=6) group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select * from pdu,addr where (msg_box=4 or msg_box=5 or msg_box=6) and addr.type=137 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) --";
            try{
                cursor = getContentResolver().query(uri_inbox_mms,
                        new String[] { mStr }, null, null, null);
                if (cursor != null) {
                    count_outbox = cursor.getCount();
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            outbox_num.setText("(" + Integer.toString(count_outbox) + ")");
        }
    }
    private class GetMsgSentCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Cursor cursor = null;
            String mStr = " * from (select a.address as _id,threads.recipient_ids as recipient_ids,a.date as date,body as sub, '' as  sub_cs,thread_id as thread_id,a._id as msg_id,a.read as read,'s' as msg_type from (select *  from sms where type=2 group by date) a left join threads on threads._id = a.thread_id union select b.address as _id,threads.recipient_ids as recipient_ids,b.date * 1000 as date,b.sub as sub,b.sub_cs as sub_cs,b.thread_id as thread_id,b._id as msg_id,b.read as read,'m' as msg_type from (select *  from pdu,addr where msg_box=2 and pdu._id = addr.msg_id group by date) b left join threads on threads._id = b.thread_id) --";
            try{
                cursor = getContentResolver().query(uri_inbox_mms,
                        new String[] { mStr }, null, null, null);
                count_sent = cursor.getCount();
            } finally {
                cursor.close();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            sent_num.setText("(" + Integer.toString(count_sent) + ")");
        }
    }
    private class GetMsgDraftCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            count_drafts = getMsgCount(uri_drafts_mms, uri_drafts_sms);
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            drafts_num.setText("(" + Integer.toString(count_drafts) + ")");
        }
    }
    private class GetMsgSimCountTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (simCardReady(0)) {
                String capaStr = "";
                try {
                    SmsManager smsManager = SmsManager.getDefault(0);
                    capaStr = smsManager.getSimCapacity();
                } catch (NullPointerException e) {
                    capaStr = " : ";
                }
                String[] splitStr = capaStr.split(":");
                count_simcard = splitStr[0];
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (simCardReady(0)) {
                if(count_simcard == "") {
                    simcard_num.setText("(0)");
                } else {
                    simcard_num.setText("(" + count_simcard + ")");
                }
            } else {
                simcard_num.setText("(0)");
            }
        }
    }
    
    private class GetMsgSimCountTaskEx extends AsyncTask<Void, Void, Void> {
    	int mPhoneId = 0;
    	GetMsgSimCountTaskEx(int PhoneId){
    		mPhoneId = PhoneId;
    	}
        @Override
        protected Void doInBackground(Void... params) {
            //Modify for Dualsim
            String capaStr = "";
        	if(mPhoneId == 0 && simCardReady(0)){
                try {
                    SmsManager smsManager = SmsManager.getDefault(0);
                    capaStr = smsManager.getSimCapacity();
                } catch (NullPointerException e) {
                    capaStr = " : ";
                }
                String[] splitStr = capaStr.split(":");
                count_simcard1 = splitStr[0];
        	}
        	if(mPhoneId == 1 && simCardReady(1)){
                try {
                    SmsManager smsManager = SmsManager.getDefault(1);
                    capaStr = smsManager.getSimCapacity();
                } catch (NullPointerException e) {
                    capaStr = " : ";
                }
                String[] splitStr = capaStr.split(":");
                count_simcard2 = splitStr[0];
        	}
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if(mPhoneId == 0){
            	if(simCardReady(0)){
                    simcard_num1.setText("(" + count_simcard1 + ")");
            	}else{
            		simcard_num1.setText("(0)");
            	}
        	}
        	if(mPhoneId == 1){
        		if(simCardReady(1)){
                    simcard_num2.setText("(" + count_simcard2 + ")");
        		}else{
        		simcard_num2.setText("(0)");
        		}
        	}
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_IS_SIM_SMS_READY) ||action.equals(PhoneFactory.getAction(TelephonyIntents.ACTION_IS_SIM_SMS_READY,0))) {
                mSmsReady[0] = intent.getBooleanExtra("isReady", false);
            } else if (action.equals(TelephonyIntents.ACTION_IS_SIM_SMS_READY1)) {
                mSmsReady[1] = intent.getBooleanExtra("isReady", false);
            }
        }
    };

    private final BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mSmsInsertAction)) {
                new GetMsgInboxCountTask().execute((Void) null);
            }
        }
    };

    private final BroadcastReceiver mMmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mMmsReceiveAction)) {
                new GetMsgInboxCountTask().execute((Void)null);
            }
        }
    };

    private final BroadcastReceiver mSmsSender = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mSmsSendAction)) {
                new GetMsgOutboxCountTask().execute((Void)null);
                new GetMsgSentCountTask().execute((Void)null);
            }
        }
    };

    private final BroadcastReceiver mMmsSender = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PushReceiver.NOTIFY_SHOW_MMS_REPORT_ACTION)) {
                new GetMsgOutboxCountTask().execute((Void)null);
                new GetMsgSentCountTask().execute((Void)null);
            }
        }
    };

    // for simcard msg count not upate
    private final BroadcastReceiver mSimStoreMsg = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();Log.e("MessageFolderActivity", "isMSMS:"+MessageUtils.isMSMS);
            if (MessageUtils.isMSMS) {
                if (action.equals(mSimStoreAction)) {
                    Log.d("MessageFolderActivity", "simcard store broadcast arrive"
                            + action);
                    if (simCardReady(0)) {
                        new GetMsgSimCountTaskEx2(0).execute((Void) null);
                    }
                    if (simCardReady(1)) {
                        new GetMsgSimCountTaskEx2(1).execute((Void) null);
                    }
                }
            } else {
                if (action.equals(mSimStoreAction)) {
                    new GetMsgSimCountTask().execute((Void) null);
                }
            }
        }
    };

    private class GetMsgSimCountTaskEx2 extends AsyncTask<Void, Void, String> {

        int mPhoneId = 0;

        GetMsgSimCountTaskEx2(int PhoneId) {
            mPhoneId = PhoneId;
        }

        @Override
        protected String doInBackground(Void... params) {
            String capaStr = "";
            String[] splitStr = null;
            if (mPhoneId == 0 && simCardReady(0)) {
                try {
                    SmsManager smsManager = SmsManager.getDefault(0);
                    capaStr = smsManager.getSimCapacity();
                } catch (NullPointerException e) {
                    capaStr = " : ";
                }
                splitStr = capaStr.split(":");

            } else if (mPhoneId == 1 && simCardReady(1)) {
                try {
                    SmsManager smsManager = SmsManager.getDefault(1);
                    capaStr = smsManager.getSimCapacity();
                } catch (NullPointerException e) {
                    capaStr = " : ";
                }
                splitStr = capaStr.split(":");
            }
            Log.d("MessageFolderActivity", "TextView =" + splitStr[0]);
            return splitStr[0];
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (mPhoneId == 0) {
                if (simCardReady(0)) {
                    simcard_num1.setText("(" + result + ")");
                } else {
                    simcard_num1.setText("(0)");
                }
            }
            if (mPhoneId == 1) {
                if (simCardReady(1)) {
                    simcard_num2.setText("(" + result + ")");
                } else {
                    simcard_num2.setText("(0)");
                }
            }
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
    
    private boolean simCardReady(int phoneId){
        TelephonyManager telManager = (TelephonyManager) getSystemService(
                PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, phoneId));
        boolean hasSim = (null != telManager) ? telManager.hasIccCard() : false;
        if (hasSim && telManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
        	return true;
        }else{
        	return false;
        }
    }
}
