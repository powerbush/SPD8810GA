package com.android.contacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.accounts.Account;
import android.app.Service;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.System;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.Toast;
import android.os.ServiceManager;
import android.provider.ContactsContract.Contacts;
//import com.android.settings.AirplaneModeEnabler;
import com.android.contacts.ContactsUtils;
import com.android.contacts.model.ContactsAccount;
import com.android.contacts.ui.SimUtils;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.PhoneFactory;
import android.telephony.ServiceState;

import com.android.contacts.util.CommonUtil;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import com.google.android.collect.Lists;
import android.provider.Telephony.Intents;
import android.app.ActivityManagerNative;
import android.os.SystemProperties;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileReader;

public class SyncSimContactsService extends Service {

    private static final String LOG_TAG = "SyncSimContactsService";
    private static final String TAG = "SyncSimContactsService";

	private static final String[] SIM_COLUMN = new String[] { "name", "number",
			"emails", "sim_index" };

	private static final int INDEX_NAME_COLUMN = 0;
	private static final int INDEX_NUMBER_COLUMN = 1;
	private static final int INDEX_EMAILS_COLUMN = 2;
	private static final int INDEX_SIM_INDEX_COLUMN = 3;

	private static final int MESSAGE_INIT = 11111;
	private static final int MESSAGE_FINISH = 11112;

	public static final String PREFS_SIM_FILE = "sim_sync_file";
	public static final String PREFS_SIM_KEY = "sim_sync_key";
	public static final String PREFS_SIM_KEY0 = "sim_sync_key0";
	public static final String PREFS_SIM_KEY1 = "sim_sync_key1";
	public static final String PREFS_SIM_STATE_IDLE = "sim.state.idle";
	public static final String PREFS_SIM_STATE_BOOT_DELETED = "sim.state.boot.delete";
	public static final String PREFS_SIM_STATE_SHUT_DELETED = "sim.state.shut.delete";
	public static final String PREFS_SIM_STATE_IMPORTING = "sim.state.importing";
	public static final String PREFS_SIM_STATE_IMPORTED = "sim.state.imported";
	private static final int PATCH_INSERT_COUNT = 50;
	private static final int DELETE_MAX_COUNT = 200;
	private static boolean isWorking =  false;
	private int mSimIndex = 0;

	private ServiceHandler mServiceHandler;
	private int mStartId;

    //TelephoneManager t = (TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE);
    private boolean isDualSim = false;

    private Context mContext;

    private boolean mState = false;
    private void setDualSim() {
        if (TelephonyManager.getPhoneCount()>1)
            isDualSim = true;
        Log.w(TAG, "isDualSim = " + isDualSim);
        return;
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        mContext = this;
        mServiceHandler = new ServiceHandler();
        clearAllSyncState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand mState = " + mState + " serviceId = " + startId);
        if (!mState) {
            setDualSim();
            mStartId = startId;
            mState = true;
            Log.d(LOG_TAG, "--------onStartCommand::startId = " + startId);
            doServicehandler(MESSAGE_INIT, startId, intent);
        } else {
            //stopSelf(startId);
        }
        return super.onStartCommand(intent, flags, startId);
    }



	@Override
	public void onDestroy() {
		unregisterSimLoadedReceiver();
		super.onDestroy();
		isWorking = false;
		mState  = false;
		Log.d(TAG, "End Service onDestory mState = " + mState + " mStartId = " + mStartId);
	}

	private void clearSyncState() {
		SharedPreferences.Editor editor = getSharedPreferences(PREFS_SIM_FILE,
				MODE_PRIVATE).edit();
		editor.putString(PREFS_SIM_KEY, PREFS_SIM_STATE_IDLE);
		editor.commit();
	}

	private void setSyncState(String state) {
		Log.d(LOG_TAG, "--------setSyncState key = " + PREFS_SIM_KEY
				+ " , state = " + state);
		SharedPreferences.Editor editor = getSharedPreferences(PREFS_SIM_FILE,
				MODE_PRIVATE).edit();
		editor.putString(PREFS_SIM_KEY, state);
		editor.commit();
	}

	private String getSyncState() {
		SharedPreferences pref = getSharedPreferences(PREFS_SIM_FILE,
				MODE_PRIVATE);
		return pref.getString(PREFS_SIM_KEY, PREFS_SIM_STATE_IDLE);
	}

    //for dual sim begin
    public static void clearSyncState(Context context, int phoneId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_SIM_FILE,
                                          MODE_PRIVATE).edit();
        editor.putString(PREFS_SIM_KEY + phoneId, PREFS_SIM_STATE_IDLE);
        editor.commit();
    }

    public static void setSyncState(Context context, String state, int phoneId) {
        Log.d(TAG, "setSyncState key = " + (PREFS_SIM_KEY + phoneId)
              + " , state = " + state);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_SIM_FILE,
                                          MODE_PRIVATE).edit();
        editor.putString(PREFS_SIM_KEY + phoneId, state);
        editor.commit();
    }

    public static String getSyncState(Context context, int phoneId) {
        SharedPreferences pref = context.getSharedPreferences(PREFS_SIM_FILE,
                                 MODE_PRIVATE);
        return pref.getString(PREFS_SIM_KEY + phoneId, PREFS_SIM_STATE_IDLE);
    }
    //for dual sim end

    // clear SharedPreferences
    public void clearAllSyncState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_SIM_FILE, MODE_PRIVATE).edit();
        editor.clear();
        editor.commit();
    }

    private Uri getSimDeleteUri() {
        return RawContacts.CONTENT_URI
               .buildUpon()
               .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
                                     "true").build();
    }

    private void waitForBootCompleted(final int startId, int phoneId) {
        Message msg = Message.obtain(mWaitHandler);
        msg.what = MESSAGE_WAIT;
        msg.arg1 = startId;
        //add for dual sim begin
        if (isDualSim)
            msg.arg2 = phoneId;
        //add for dual sim end
        mWaitHandler.sendMessageDelayed(msg, WAIT_DELAY_TIME);
    }

	private static final int MESSAGE_WAIT = 11001;
	private WaitHandler mWaitHandler = new WaitHandler();
	private static final int WAIT_MAX_COUNT = 60;
	private int mCurrentCount = 0;
	private static final int WAIT_DELAY_TIME = 5 * 1000; // 5s

    private class WaitHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_WAIT:
                Log.d(LOG_TAG, "-------------waitForBootCompleted");
                int serviceStartId = msg.arg1;
                int phoneId = -1;
                if (isDualSim) {
                    phoneId = msg.arg2;
                }
                if (isBootCompleted()) {
                    checkSimPrefsAndDo(serviceStartId, phoneId);
                } else if (mCurrentCount <= WAIT_MAX_COUNT) {
                    mCurrentCount++;
                    waitForBootAndImport(serviceStartId, phoneId);
                }
                break;
            default:
                break;
            }
        }
    }

    
    //added for dual sim begin

    
    
    private void clearSimAction(final int serviceStartId, final boolean boot, int phoneId) {
        Log.d(TAG, "clearSimAction::begin boot is " + boot + " begin clear sim contacts thread!");
        SimClearThread simClear = new SimClearThread(serviceStartId, boot, phoneId);
        simClear.start();
            
    }
    

    private class SimClearThread extends Thread {
    	private int mServiceStartId;
        private ContactsAccount mAccount;
        private final int mPhoneId;
		private int mSimIndex = 0;
        private ContentResolver mResolver;
        private boolean mBoot = false;
        
        SimClearThread(int id, boolean boot, int phoneId) {
            mServiceStartId = id;
            mPhoneId = phoneId;
            mBoot = boot;
        }

        @Override
        public void run() {
        	Log.d(TAG, "serviceId = " + mServiceStartId + " mPhoneId = " + mPhoneId + " mBoot = " +mBoot);
        	StringBuffer where = new StringBuffer();
            where.append(RawContacts.ACCOUNT_NAME).append("=\'").append(
                    mPhoneId == 0 ? Account.SIM1_ACCOUNT_NAME : Account.SIM2_ACCOUNT_NAME).append("\'")
                    .append(" AND " + RawContacts.SIM_INDEX + "<>0");
            getContentResolver().delete(getSimDeleteUri(), where.toString(), null);
            if (mBoot) {
                Log.v(TAG, "BOOT COMPLEMENTED DELETE");
                setSyncState(mContext, PREFS_SIM_STATE_BOOT_DELETED, mPhoneId);
            } else {
                Log.v(TAG, "SHUTDOWN DELETE");
                setBootCompleteState(false);
                setSyncState(mContext, PREFS_SIM_STATE_SHUT_DELETED, mPhoneId);
            }
            return;
        }
    }
    
    
    
    //added for dual sim end

   private void deleteSimAction(final int startId, final boolean boot, final int phoneId) {
        Log.d(LOG_TAG, "-------deleteSimAction::begin boot is " + boot);
        isWorking = true;
	if(phoneId == 0 ){
        	getContentResolver().delete(getSimDeleteUri(),
                                    RawContacts.ACCOUNT_NAME + " = 'SIM1'", null);
	}else if(phoneId == 1){
        	getContentResolver().delete(getSimDeleteUri(),
                                    RawContacts.ACCOUNT_NAME + " = 'SIM2'", null);
	}else {
		Log.e(TAG, "this is the wrong phoneID");
		return;
	}
        if (boot) {
            Log.v(LOG_TAG, "boot complement");
            setSyncState(PREFS_SIM_STATE_BOOT_DELETED);
            setBootCompleteState(false);
            //setBootCompleteState(true);
        } else {
            Log.v(LOG_TAG, "shutdown");
            setSyncState(PREFS_SIM_STATE_SHUT_DELETED);
        }
        return;
    }



    private void deleteSimAction(final int startId, final boolean boot) {
        Log.d(LOG_TAG, "-------deleteSimAction::begin boot is " + boot);
        isWorking = true;
        getContentResolver().delete(getSimDeleteUri(),
                                    RawContacts.SIM_INDEX + "<>0", null);
        if (boot) {
            Log.v(LOG_TAG, "boot complement");
            setSyncState(PREFS_SIM_STATE_BOOT_DELETED);
            setBootCompleteState(false);
        } else {
            Log.v(LOG_TAG, "shutdown");
            setSyncState(PREFS_SIM_STATE_SHUT_DELETED);
        }
        return;
    }

	private void querySimAction(final int startId) {
		Log.d(LOG_TAG, "-------querySimAction::begin");
		new Thread(new Runnable() {
			public void run() {
				// TODO Auto-generated method stub
//				Cursor c = getContentResolver().query(
//						Uri.parse("content://icc/adn"), SIM_COLUMN, null, null,
//						null);
				Log.d(LOG_TAG, "-------querySimAction::end");
				doServicehandler(MESSAGE_FINISH, startId, null);
			}
		}).start();
	}


    //added for dual sim begin
    private void importDualSimAction(int serviceId, int phoneId) {
        

	String accountName = Account.SIM1_ACCOUNT_NAME;
        Uri uri = SimUtils.SIM1_URI;
        if (phoneId == 1) {
            accountName = Account.SIM2_ACCOUNT_NAME;
            uri = SimUtils.SIM2_URI;
        }
        DualSimcardImportThread simImport = new DualSimcardImportThread(serviceId,
                new ContactsAccount(accountName, Account.SIM_ACCOUNT_TYPE, uri), phoneId);
        simImport.start();
    }

    private static Object[] PREFS_SIM_STATE_LOCK = new Object[] {new Object(), new Object()};
    private class DualSimcardImportThread extends Thread {
        private int mServiceStartId;
        private ContactsAccount mAccount;
        private final int mPhoneId;
		private int mSimIndex = 0;
        private ContentResolver mResolver;
        DualSimcardImportThread(int id, ContactsAccount account, int phoneId) {
            mServiceStartId = id;
            mAccount = account;
            mPhoneId = phoneId;
            mResolver = getContentResolver();
        }

        @Override
        public void run() {
            Log.d(TAG, "Begin import thread, first delete sim contacts in databases");
            deleteSimAction(mServiceStartId, true, mPhoneId);
	    synchronized (PREFS_SIM_STATE_LOCK[mPhoneId]) {
                if (PREFS_SIM_STATE_IMPORTING.equals(getSyncState(mContext, mPhoneId))
                        || PREFS_SIM_STATE_IMPORTED.equals(getSyncState(mContext, mPhoneId))) {
                    return;
                }
                setSyncState(mContext, PREFS_SIM_STATE_IMPORTING, mPhoneId);

                String whichSIM = mAccount.getContactsAccountName();
                Log.v(TAG, "query " + whichSIM + " begin  mAccount.getContactsAccountUri() = " + mAccount.getContactsAccountUri());
                Cursor simCursor = null;
                try {
                    simCursor = mResolver.query(mAccount.getContactsAccountUri(),
                                                SIM_COLUMN, null, null, null);
                } catch (Exception e) {
                    Log.d("TAG","query " + whichSIM + " crash ");
                }
		Log.d(TAG, "query " + whichSIM + " return cursor = " + simCursor);
                if (simCursor == null || simCursor.getCount() == 0) {
                    Log.d(TAG, "Count of contacts on " + whichSIM + " is 0");
                    clearSimAction(mServiceStartId, true, mPhoneId);
                    doServicehandler(MESSAGE_FINISH, mServiceStartId, null);
                    return;
                }
                Log.d(TAG, "Count of contacts on " + whichSIM + " is " + simCursor.getCount());
                Log.d(TAG, "query " + whichSIM + " end ");
		int phoneId = 0;
                if (Account.SIM2_ACCOUNT_NAME.equals(mAccount
                                                     .getContactsAccountName())) {
                    phoneId = 1;
                }

                int anrNum = 0;
                int emailNum = 0;
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                                       .asInterface(ServiceManager.getService(PhoneFactory
                                                                              .getServiceName("simphonebook", phoneId)));
                try {
                    anrNum = iccIpb.getAnrNum();
                    emailNum = iccIpb.getEmailNum();
                    Log.d(TAG, whichSIM + " anrNum = " + anrNum + "emailNum = " + emailNum);
                } catch (RemoteException ex) {
                    Log.v(TAG, whichSIM + "excetpion", ex);
                } catch (SecurityException ex) {
                    Log.v(TAG, whichSIM + "excetpion", ex);
                }
                String oneSimCursorValue;

                boolean hasExistOldSimRecord;

            	mSimIndex = getEffectiveSimIndex();

		ArrayList<ContentProviderOperation> simList = new ArrayList<ContentProviderOperation>();
                Log.i(TAG, "Build " + whichSIM + " operation list begin");
                simCursor.moveToPosition(-1);
                while (simCursor.moveToNext()) {
                    String name_sim = simCursor.getString(simCursor
                                                          .getColumnIndex("name"));
                    String phoneNumber = simCursor.getString(simCursor
                                         .getColumnIndex("number"));
                    String anr = simCursor.getString(simCursor
                                                     .getColumnIndex("anr"));
                    String mail = simCursor.getString(simCursor
                                                      .getColumnIndex("email"));
                    if (name_sim == null) {
                        name_sim = "";
                    }
                    if (phoneNumber == null) {
                        phoneNumber = "";
                    }
                    if (anr == null) {
                        anr = "";
                        for (int i = 1; i < anrNum; i++) {
                            anr += ":";
                        }
                    }
                    if (mail == null) {
                        mail = "";
                    }
                    oneSimCursorValue = "/" + name_sim + "/" + phoneNumber + "/"
                                        + anr + "/" + mail + "/";
                    Log.d(TAG, "new " + whichSIM + " contacts data : " + oneSimCursorValue);
                         Log.d(TAG, whichSIM + " insertSimContactsBatch::begin ");
                        insertSimContactsBatch(simCursor, mAccount, simList);
                        Log.d(TAG, whichSIM + " insertSimContactsBatch::end ");
                  
                }
                Log.i(TAG, "Batch insert " + whichSIM + " contacts into databases begin");
		try {
		    if(checkSimEnabled(phoneId)){
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, simList);
		    }
                } catch (RemoteException e) {
                    Log.w(TAG, "Batch insert " + whichSIM + " contacts into databases fail", e);
                } catch (OperationApplicationException e) {
                    Log.w(TAG, "Batch insert " + whichSIM + " contacts into databases fail", e);
                }
                Log.i(TAG, "Batch insert " + whichSIM + " contacts into databases end");
		/*
                Log.d(TAG, "Clear old " + whichSIM + " contacts begin");
                String where = "";
                for (int i = 0; i < sims.size(); i++) {
                    String id = sims.get(i);
                    where += "(" + (RawContacts._ID + "=" + Long.parseLong(id) + ") or ");
                }
                if (where.length() > 0) {
                    where = where.trim().substring(0,
                                                   where.length() - " or ".length());
                    Log.i(TAG, "Clear old " + whichSIM + " where is : " + where);
                    getContentResolver().delete(getSimDeleteUri(), where, null);
                }
                Log.d(TAG, "Clear old " + whichSIM + " contacts end");

                where = "";
                Log.i(TAG, "Delete some no data contacts on " + whichSIM + " begin");
                // Delete empty sim record
                for (int i = 0; i < old_sims.size(); i++) {
                    String id = old_sims.get(i);
                    if (!sims.contains(id)) {
                        where += "(" + RawContacts._ID + "=" + Long.parseLong(id) +") or ";
                    }
                }
                if (where.length() > 0) {
                    where = where.trim().substring(0,
                                                   where.length() - " or ".length());
                    Log.i(TAG, "Delete some no data contacts on " + whichSIM + " where is : " + where);
                    getContentResolver().delete(getSimDeleteUri(), where, null);
                }
               */
                Log.i(TAG, "Delete some no data contacts on " + whichSIM + " end");
		 setSyncState(mContext, PREFS_SIM_STATE_IMPORTED, mPhoneId);
                doServicehandler(MESSAGE_FINISH, mServiceStartId, null);
                mResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null);
		
            }
	    Log.d(TAG, "End import thread");
        }


		 private int getEffectiveSimIndex() {
            Cursor cursor = getContentResolver().query(RawContacts.CONTENT_URI,
                            new String[] {RawContacts.SIM_INDEX},
                            RawContacts.SIM_INDEX + "<>0",
                            null,
                            RawContacts.SIM_INDEX + " desc");
            Log.v(LOG_TAG, "RawContacts.CONTENT_URI sim count is " + cursor.getCount());
            int max_sim_index = 1;
            try {
                if (cursor.moveToFirst()) {
                    max_sim_index = cursor.getInt(0);
                    return max_sim_index + 1;
                } else {
                    return 1;
                }
            } catch (Exception e) {
                Log.v(LOG_TAG, "Fail query raw contact");
                return 1;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }




        //added for dual sim begin
        /**
        * apply batch operation object add
        *
        * @param cursor
        */
        private void insertSimContactsBatch(Cursor cursor,
                                            ContactsAccount account, ArrayList<ContentProviderOperation> simList) {
            int sim_index = Integer.valueOf(cursor
                                            .getString(cursor.getColumnIndex("sim_index")));

			String name = cursor.getString(cursor.getColumnIndex("name"));
            String phoneNumber = cursor.getString(cursor
                                                  .getColumnIndex("number"));
            String anr = cursor.getString(cursor.getColumnIndex("anr"));
            String email = cursor.getString(cursor.getColumnIndex("email"));

            String[] otherPhoneNumber = SimUtils.splitString(anr);

            String[] otherMail = email == null ? null : email.split(",");

            sim_index = mSimIndex++;
			Log.w(TAG, "sim_index = " + sim_index);
			ContentValues SimValues = new ContentValues();

            SimValues.put(RawContacts.SIM_INDEX, sim_index);
            SimValues.put(RawContacts.ACCOUNT_NAME,
                          account.getContactsAccountName());
            SimValues.put(RawContacts.ACCOUNT_TYPE,
                          account.getContactsAccountType());
            SimValues.put(RawContacts.AGGREGATION_MODE,
                          RawContacts.AGGREGATION_MODE_DISABLED);
            int firstIndex = simList.size();

            ContentProviderOperation simOperation = ContentProviderOperation
                                                    .newInsert(RawContacts.CONTENT_URI).withValues(SimValues)
                                                    .withYieldAllowed(true).build();
            simList.add(simOperation);
            if (!TextUtils.isEmpty(name)) {
                ContentValues NameValues = new ContentValues();
                NameValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
                NameValues.put(StructuredName.GIVEN_NAME, name);
                ContentProviderOperation nameOperation = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, firstIndex)
                        .withValues(NameValues).withYieldAllowed(true).build();
                simList.add(nameOperation);
            }

            if (!TextUtils.isEmpty(phoneNumber)) {
                ContentValues NumberValues = new ContentValues();
                NumberValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                NumberValues.put(Phone.TYPE, Phone.TYPE_HOME);
                NumberValues.put("data15", 0);
                NumberValues.put(Phone.NUMBER, phoneNumber);
                NumberValues.put(Data.IS_PRIMARY, 1);
                NumberValues.put(Data.IS_SUPER_PRIMARY, 0);

                ContentProviderOperation phoneOpertion = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, firstIndex)
                        .withValues(NumberValues).withYieldAllowed(true)
                        .build();
                simList.add(phoneOpertion);
            }

            // add othernumber
            if (otherPhoneNumber != null) {
                for (int i = 0; i < otherPhoneNumber.length; i++) {
                    if (!TextUtils.isEmpty(otherPhoneNumber[i])) {
                        ContentValues values = new ContentValues();
                        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                        values.put("data15", i + 1);
                        values.put(Phone.TYPE, NUMBERTYPE[i]);
                        values.put(Phone.NUMBER, otherPhoneNumber[i]);
                	values.put(Data.IS_PRIMARY, 1);
                        values.put(Data.IS_SUPER_PRIMARY, 0);

                        Log.d(LOG_TAG, NUMBERTYPE[i] + "===== " + otherPhoneNumber[i]);
                        ContentProviderOperation otherOperation = ContentProviderOperation
                                .newInsert(Data.CONTENT_URI)
                                .withValueBackReference(Data.RAW_CONTACT_ID,
                                                        firstIndex).withValues(values)
                                .withYieldAllowed(true).build();
                        simList.add(otherOperation);

                    }
                }
            }

            if (otherMail != null) {
                for (int i = 0; i < otherMail.length; i++) {
                    String mail = otherMail[i];
                    if (!TextUtils.isEmpty(mail)) {
                        ContentValues mailValue = new ContentValues();
                        mailValue.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                        mailValue.put("data15", i);
                        mailValue.put(Email.DATA, email);
                        mailValue.put(Data.IS_SUPER_PRIMARY, 0);
                        ContentProviderOperation emailOperation = ContentProviderOperation
                                .newInsert(Data.CONTENT_URI)
                                .withValueBackReference(Data.RAW_CONTACT_ID,
                                                        firstIndex).withValues(mailValue)
                                .withYieldAllowed(true).build();
                        simList.add(emailOperation);
                    }
                }
            }

        }

    }


    private void waitForBootAndImport(int serviceStartId, int phoneId) {
        Message msg = Message.obtain(mWaitHandler);
        msg.what = MESSAGE_WAIT;
        msg.arg1 = serviceStartId;
        msg.arg2 = phoneId;
        mWaitHandler.sendMessageDelayed(msg, WAIT_DELAY_TIME);
    }

    //added for dual sim end




    private void importSimAction(int id) {
        //		SyncSimContactsService.this.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_START"));
        isWorking = true;
        SimcardImportThread simImport = new SimcardImportThread(id);
        simImport.start();
    }
	private class SimcardImportThread extends Thread {
		private int serviceId;
//		private Cursor mSimCursor;
		int a = 0;
		//Lino modify for NEWMS00131859 begin 2011-10-27
		ContentValues[] cvs;//haojie add
		int DifferentSimCount = 0;
		//Lino modify for NEWMS00131859 end 2011-10-27
		// ArrayList<ContentProviderOperation>
		ArrayList<ContentProviderOperation> simList = null;

		SimcardImportThread(int id) {
			serviceId = id;
		}

        private ArrayList<String> getOldSimIndex() {
            ArrayList<String> old_sims = new ArrayList<String>();
            Cursor total_cursor = getContentResolver().query(
                    RawContacts.CONTENT_URI,
                    new String[] { RawContacts.SIM_INDEX },
                    RawContacts.SIM_INDEX + "<>0", null, RawContacts.SIM_INDEX);
            Log.d(LOG_TAG, "total_cursor.length = " + total_cursor.getCount());
            try {
                if (total_cursor != null && total_cursor.moveToFirst()) {
                    do {
                        old_sims.add(total_cursor.getString(0));
                    } while (total_cursor.moveToNext());

                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                if (total_cursor != null) {
                    total_cursor.close();
                    total_cursor = null;
                }
            }

            return old_sims;
        }

        private ArrayList<String>[] getOldSimInfo() {
            String name = "", mobileNumber = "", homeNumber = "", workNumber = "", otherNumber = "", email = "";
            ArrayList<String>[] oldSimInfo = new ArrayList[2];
            oldSimInfo[0] = new ArrayList<String>();
            oldSimInfo[1] = new ArrayList<String>();
            String sim_index = "0";
            int row = 0;
            Cursor c = getContentResolver().query(
                    Uri.parse("content://" + "com.android.contacts"
                            + "/phone_or_email"),
                    new String[] { "sim_index", "data.data1", "data2",
                            Data.MIMETYPE }, RawContacts.SIM_INDEX + "<>0",
                    null, RawContacts.SIM_INDEX);
            // prevSimCursor = new String[c.getCount()];
            try {
                if (c.moveToFirst()) {
                    sim_index = c.getString(0);
                    Log.v(LOG_TAG, "the first sim_index is " + sim_index);
                    do {
                        if (!c.getString(0).equals(sim_index)) {
                            oldSimInfo[0].add("/" + name + "/" + homeNumber
                                    + mobileNumber + "/" + workNumber + "/"
                                    + otherNumber + "/" + email + "/");
                            oldSimInfo[1].add(sim_index);
                            Log.d(LOG_TAG, "sim_index = " + sim_index);
                            Log.d(LOG_TAG,
                                    "get a row check:" + oldSimInfo[0].get(row));
                            row++;
                            sim_index = c.getString(0);

                            homeNumber = "";
                            mobileNumber = "";
                            workNumber = "";
                            otherNumber = "";
                            name = "";
                            email = "";
                        }

                        if (Phone.CONTENT_ITEM_TYPE.equals(c.getString(3))) {
                            if ((Phone.TYPE_HOME + "").equals(c.getString(2))) {
                                homeNumber = c.getString(1);
                                if (homeNumber == null) {
                                    homeNumber = "";
                                }
                            } else if ((Phone.TYPE_MOBILE + "").equals(c
                                    .getString(2))) {
                                mobileNumber = c.getString(1);
                                if (mobileNumber == null) {
                                    mobileNumber = "";
                                }
                            } else if ((Phone.TYPE_WORK + "").equals(c
                                    .getString(2))) {
                                workNumber = c.getString(1);
                                if (workNumber == null) {
                                    workNumber = "";
                                }
                            } else if ((Phone.TYPE_OTHER + "").equals(c
                                    .getString(2))) {
                                otherNumber = c.getString(1);
                                if (otherNumber == null) {
                                    otherNumber = "";
                                }
                            }

                        } else if (StructuredName.CONTENT_ITEM_TYPE.equals(c
                                .getString(3))) {
                            name = c.getString(1);
                            if (name == null) {
                                name = "";
                            }
                        } else if (Email.CONTENT_ITEM_TYPE.equals(c
                                .getString(3))) {
                            email = c.getString(1);
                            if (email == null) {
                                email = "";
                            }
                        }

                    } while (c.moveToNext());
                    // add the last record
                    oldSimInfo[0].add("/" + name + "/" + homeNumber
                            + mobileNumber + "/" + workNumber + "/"
                            + otherNumber + "/" + email + "/");
                    Log.d(LOG_TAG, "get a row check:" + oldSimInfo[0].get(row));
                    oldSimInfo[1].add(sim_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (c != null) {
                    c.close();
                    c = null;
                }
            }
            return oldSimInfo;
        }

        private SparseBooleanArray buildSimContactsList(Cursor cursor,
                ArrayList<String>[] oldSimInfo) {
            SparseBooleanArray sim_flag = new SparseBooleanArray();
            String oneSimCursorValue;
            boolean hasExistOldSimRecord;
            ContentResolver contresolver = SyncSimContactsService.this
                    .getContentResolver();// haojie add
            if (cursor.moveToFirst()) {
                do {
                    String name_sim = cursor.getString(cursor
                            .getColumnIndex("name"));
                    String phoneNumber = cursor.getString(cursor
                            .getColumnIndex("number"));
                    // TODO get otherNumber and email
                    String anr = cursor.getString(cursor.getColumnIndex("anr"));
                    String mail = cursor.getString(cursor
                            .getColumnIndex("email"));
                    String mobile_number = "", work_number = "", other_number = "";
                    if (name_sim == null) {
                        name_sim = "";
                    }

                    if (phoneNumber == null) {
                        phoneNumber = "";
                    }

                    if (mail == null) {
                        mail = "";
                    }
                    String[] otherPhoneNumber = splitString(anr);
                    if (otherPhoneNumber != null) {
                        for (int i = 0; i < otherPhoneNumber.length; i++) {
                            switch (i) {
                            case 0:
                                mobile_number = otherPhoneNumber[i];
                                break;
                            case 1:
                                work_number = otherPhoneNumber[i];
                                break;
                            case 2:
                                other_number = otherPhoneNumber[i];
                                break;
                            }
                        }
                    }
                    oneSimCursorValue = "/" + name_sim + "/" + phoneNumber
                            + mobile_number + "/" + work_number + "/"
                            + other_number + "/" + mail + "/";
                    Log.d(LOG_TAG, "oneSimCursorValue = " + oneSimCursorValue);
                    hasExistOldSimRecord = false;
                    for (int i = 0; i < oldSimInfo[0].size(); i++) {
                        if (sim_flag.get(
                                Integer.parseInt(oldSimInfo[1].get(i)), false)) {
                            continue;
                        }
                        if (oneSimCursorValue.equals(oldSimInfo[0].get(i))) {
                            hasExistOldSimRecord = true;
                            sim_flag.put(
                                    Integer.parseInt(oldSimInfo[1].get(i)),
                                    true);
                            break;
                        }
                    }

                    if (!hasExistOldSimRecord) {
                        Log.d(LOG_TAG,
                                "-------importOneSimContactsToPhoneTest::begin");
                        // importOneSimContactsToPhoneTest(mSimCursor);
                        insertSimContactsBatch(cursor);
                    }

                } while (cursor.moveToNext());
                try {
                    contresolver
                            .applyBatch(ContactsContract.AUTHORITY, simList);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (OperationApplicationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                return null;
            }
            return sim_flag;
        }

        private void clearOldSimContactsList(SparseBooleanArray sim_flag,
                ArrayList<String> old_sims, ArrayList<String> sims) {
            if (sim_flag == null) {
                getContentResolver().delete(getSimDeleteUri(),
                        RawContacts.SIM_INDEX + "<>0", null);
                return;
            }
            String args[] = new String[sims.size()];
            int s = 0;
            for (int i = 0; i < sims.size(); i++) {
                if (!sim_flag.get(Integer.parseInt(sims.get(i)), false)) {
                    args[s++] = sims.get(i);
                    /*
                     * getContentResolver().delete(getSimDeleteUri(),
                     * RawContacts.SIM_INDEX + "='" + sims.get(i) + "'", null);
                     */
                }

            }
            String where = "";
            String[] args2 = new String[s];
            for (int u = 0; u < s; u++) {
                args2[u] = "'" + args[u] + "'";
                where += (RawContacts.SIM_INDEX + "=" + args2[u] + " or ");
            }
            if (where.length() > 0) {
                where = where.trim().substring(0,
                        where.length() - " or ".length());
                getContentResolver().delete(getSimDeleteUri(), where, null);
            }

            where = "";
            // Delete empty sim record
            for (int i = 0; i < old_sims.size(); i++) {
                if (!sims.contains(old_sims.get(i))) {
                    s++;
                    where += (RawContacts.SIM_INDEX + "=" + old_sims.get(i) + " or ");
                }

                if (i % DELETE_MAX_COUNT == 0) {
                    if (where.length() > 0) {
                        where = where.trim().substring(0,
                                where.length() - " or ".length());
                        getContentResolver().delete(getSimDeleteUri(), where,
                                null);
                    }
                    Log.v(LOG_TAG, "where = " + where);
                    where = "";
                }
            }

            Log.v(LOG_TAG, "delete sim records is " + s);
            if (where.length() > 0) {
                where = where.trim().substring(0,
                        where.length() - " or ".length());
                getContentResolver().delete(getSimDeleteUri(), where, null);
            }
        }
		@Override
		public void run() {

            deleteSimAction(serviceId, true);
			setSyncState(PREFS_SIM_STATE_IMPORTING);
			Log.v(LOG_TAG, "query IccProvider start");
			// add by chengyake for preventing crash Monday, October 29 2011 begin
			Cursor simCursor = null;
			try {
                    simCursor = getContentResolver().query(Uri.parse("content://icc/adn"), SIM_COLUMN,
                        null, null, null);
					if(simCursor == null){
		                Log.d(LOG_TAG, "simCursor == null");
		                deleteSimAction(mStartId, true);
		                setSyncState(PREFS_SIM_STATE_IMPORTED);
		                isWorking = false;
		                doServicehandler(MESSAGE_FINISH, serviceId, null);
		            }else{
		                simList = new ArrayList<ContentProviderOperation>();
		                ArrayList<String> old_sims = getOldSimIndex();
		                ArrayList<String>[] oldSimInfo = getOldSimInfo();
		                SparseBooleanArray sim_flag = buildSimContactsList(simCursor,oldSimInfo);
		                clearOldSimContactsList(sim_flag,old_sims,oldSimInfo[1]);
		            }
				} catch (Exception e) {
					Log.d("LOG_TAG", "query IccProvider crash");
				} finally{
//					SyncSimContactsService.this.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_END"));
				    if(simCursor != null){
				        simCursor.close();
				    }
				}
            Log.v(LOG_TAG, "sim operate complete");
			setSyncState(PREFS_SIM_STATE_IMPORTED);
			isWorking = false;
			doServicehandler(MESSAGE_FINISH, serviceId, null);
			getContentResolver().notifyChange(ContactsContract.AUTHORITY_URI, null);
		}

		private int getEffectiveSimIndex(){
	    	Cursor cursor = getContentResolver().query(RawContacts.CONTENT_URI,
	    			new String[] {RawContacts.SIM_INDEX},
	    			RawContacts.SIM_INDEX + "<>0",
	    			null,
	    			RawContacts.SIM_INDEX + " desc");
	    	Log.v(LOG_TAG, "RawContacts.CONTENT_URI sim count is " + cursor.getCount());
	    	int max_sim_index = 1;
	    	try {
	    		if(cursor.moveToFirst()){
	    			max_sim_index = cursor.getInt(0);
	    			return max_sim_index + 1;
	    		}else{
	    			return 1;
	    		}
			} catch (Exception e) {
				Log.v(LOG_TAG, "Fail query raw contact");
				return 1;
			}finally{
				if(cursor != null){
					cursor.close();
				}
			}
		}
		//Lino modify for NEWMS00131859 end 2011-10-27
		/**
		 * apply batch operation object add
		 * @param cursor
		 */
		private void insertSimContactsBatch(final Cursor cursor){
			Log.v(LOG_TAG, "importOneSimContactsToPhoneTest");
			String name = cursor.getString(cursor.getColumnIndex("name"));
			String phoneNumber = cursor.getString(cursor
					.getColumnIndex("number"));
			int sim_index = Integer.valueOf(cursor.getString(cursor
					.getColumnIndex("sim_index")));

			String anr = cursor.getString(cursor.getColumnIndex("anr"));
			String email = cursor.getString(cursor.getColumnIndex("email"));

			// String[] otherPhoneNumber = null;
			// if (anr != null) {
			String[] otherPhoneNumber = splitString(anr);
			// }

			sim_index = ++mSimIndex;
			Log.i(LOG_TAG,"sim_index" + sim_index);
			ContentValues Values = new ContentValues();
			ContentValues SimValues = new ContentValues();
			ContentValues NameValues = new ContentValues();
			ContentValues NumberValues = new ContentValues();
			ContentValues OtherValues[] = new ContentValues[otherPhoneNumber.length];
			ContentValues EmailValues = new ContentValues();

			// add sim index
			SimValues.put(RawContacts.SIM_INDEX, sim_index);
			// Log.v(LOG_TAG, "sim_index=" + sim_index);
			SimValues.put("sim_index", sim_index);
			SimValues.put(RawContacts.ACCOUNT_NAME, Constants.SIM_ACCOUNT_NAME);
			SimValues.put(RawContacts.ACCOUNT_TYPE, Constants.SIM_ACCOUNT_TYPE);

			// Uri rawContactUri =
			// SyncSimContactsService.this.getContentResolver()
			// .insert(RawContacts.CONTENT_URI, SimValues);
			// long rawContactId = ContentUris.parseId(rawContactUri);
			int firstIndex = simList.size();

			ContentProviderOperation simOperation = ContentProviderOperation
					.newInsert(RawContacts.CONTENT_URI).withValues(SimValues)
					.withYieldAllowed(true).build();
			simList.add(simOperation);
			// Log.d(LOG_TAG, "rawContactId=" + rawContactId);
			if (!TextUtils.isEmpty(name)) { // && name.length() > 0){
				NameValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
				NameValues.put(StructuredName.GIVEN_NAME, name);
				ContentProviderOperation nameOperation = ContentProviderOperation
						.newInsert(Data.CONTENT_URI)
						.withValueBackReference(Data.RAW_CONTACT_ID, firstIndex)
						.withValues(NameValues).build();
				simList.add(nameOperation);
			}

			if (!TextUtils.isEmpty(phoneNumber)) { // && phoneNumber.length() >
													// 0){
				NumberValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
				NumberValues.put(Phone.TYPE, Phone.TYPE_HOME);
				NumberValues.put(Phone.NUMBER, phoneNumber);
				NumberValues.put(Data.IS_PRIMARY, 1);
				NumberValues.put(Data.IS_SUPER_PRIMARY, 0);

				ContentProviderOperation phoneOpertion = ContentProviderOperation
						.newInsert(Data.CONTENT_URI)
						.withValueBackReference(Data.RAW_CONTACT_ID, firstIndex)
						.withValues(NumberValues)
						.build();
				simList.add(phoneOpertion);
			}

			// add othernumber
			if (otherPhoneNumber != null) {
				for (int i = 0; i < otherPhoneNumber.length; i++) {
					if (!TextUtils.isEmpty(otherPhoneNumber[i])) { // &&
																	// otherPhoneNumber[i].length()
																	// > 0) {
						ContentValues values = new ContentValues();
						OtherValues[i] = values;
						OtherValues[i].put(Data.MIMETYPE,
								Phone.CONTENT_ITEM_TYPE);
						OtherValues[i].put(Phone.TYPE, NUMBERTYPE[i]);
						OtherValues[i].put(Phone.NUMBER, otherPhoneNumber[i]);
						OtherValues[i].put(Data.IS_PRIMARY, 1);
						OtherValues[i].put(Data.IS_SUPER_PRIMARY, 0);

						ContentProviderOperation otherOperation = ContentProviderOperation
								.newInsert(Data.CONTENT_URI)
								.withValueBackReference(Data.RAW_CONTACT_ID,
										firstIndex).withValues(OtherValues[i])
								.build();
						simList.add(otherOperation);

					}
				}
			}
			if (!TextUtils.isEmpty(email)) { // && email.length() > 0){
				EmailValues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
				EmailValues.put(Email.TYPE, Email.TYPE_HOME);
				EmailValues.put(Email.DATA, email);
				EmailValues.put(Data.IS_PRIMARY, 1);
				EmailValues.put(Data.IS_SUPER_PRIMARY, 0);

				ContentProviderOperation emailOperation = ContentProviderOperation
						.newInsert(Data.CONTENT_URI)
						.withValueBackReference(Data.RAW_CONTACT_ID, firstIndex)
						.withValues(EmailValues).build();
				simList.add(emailOperation);
			}

		}
	}
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

    private void doServicehandler(int what, int arg1, Object obj) {
        // added for dual sim begin
        Log.w(TAG, "start send message to handler intent = " + obj);
        Intent intent = (Intent) obj;
        if (isDualSim) {
            int sendtimes = 1;
            if (MESSAGE_INIT == what) {
                if (isSendTwice(intent)) {
                    sendtimes = 2;
                }
            }
            Log.e(TAG, "before send message sendtime = " + sendtimes);
            if (sendtimes == 1) {
                int phoneId = 0;
                if (null != intent) {
                    phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, 0);
                }

                Message msgDualSim = mServiceHandler.obtainMessage(what);
                msgDualSim.arg1 = arg1;
                msgDualSim.arg2 = phoneId;
                msgDualSim.obj = obj;
                mServiceHandler.sendMessageDelayed(msgDualSim, 500L);
                return;
            }
            for (int i = 0; i < sendtimes; i++) {
                Message msgDualSim = mServiceHandler.obtainMessage(what);
                msgDualSim.arg1 = arg1;
                msgDualSim.arg2 = i;
                msgDualSim.obj = obj;
                mServiceHandler.sendMessageDelayed(msgDualSim, 500L);
            }
        }
        // added for dual sim end
        else {
            Message msg = mServiceHandler.obtainMessage(what);
            msg.arg1 = arg1;
            msg.obj = obj;
            mServiceHandler.sendMessage(msg);
        }
    }

	private boolean isSendTwice(Intent intent){
	    boolean isSendTwoTimes = false;
	    String action = intent.getAction();
	    //if(!action.equals("android.intent.action.SelectSimCard")&& !action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){
	    if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){
               //isSendTwoTimes = false;
               isSendTwoTimes = true;
           }else if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED0) ||
			action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED1)){
	       isSendTwoTimes = false;
	   }
	   else if(!action.equals("android.intent.action.SelectSimCard") &&
			 !action.equals("android.intent.action.FDN_STATE_CHANGED0") && 
				!action.equals("android.intent.action.FDN_STATE_CHANGED1") ){
		isSendTwoTimes = true;
	    }else if(action.equals("android.intent.action.SelectSimCard")) {
		boolean sim1Changed = intent.getBooleanExtra("SIM1", false);
		boolean sim2Changed = intent.getBooleanExtra("SIM2", false);
		if(sim1Changed && sim2Changed){
			isSendTwoTimes = true;
		}
	    }
	    Log.d(TAG, "isSendTwoTimes = "+ isSendTwoTimes);
	    return isSendTwoTimes;
	}


	private final String PROPERTY_ICC_OPERATOR_NUMERIC = "gsm.sim.operator.numeric";
	private BroadcastReceiver mProcessSimLoadedReceiver = null;
	private void registerSimLoadedReceiver(){
		Log.d(LOG_TAG, "registerSimLoadedReceiver");
		if(mProcessSimLoadedReceiver == null){
			mProcessSimLoadedReceiver = new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){
						String stateExtra = intent
						.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
						Log.d(LOG_TAG, "stateExtra = " + stateExtra);
						if (stateExtra != null && stateExtra.equals(IccCard.INTENT_VALUE_ICC_LOADED)) {
							importSimAction(mStartId);
						}

					}
				}
			};
		}
		IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
		registerReceiver(mProcessSimLoadedReceiver, filter);
	}

    //added for dual sim begin

    private void registerSimLoadedReceiver(final int serviceStartId) {
        if (mProcessSimLoadedReceiver == null) {
            Log.d(TAG, "registerSimLoadedReceiver, serviceStartId = " + serviceStartId);
            mProcessSimLoadedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                        String stateExtra = intent
                                            .getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
                        Log.d(TAG, "[mProcessSimLoadedReceiver]phoneId = " +
                              intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, -1) + ", stateExtra = " + stateExtra);
                        if (IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
                            doServicehandler(MESSAGE_INIT, serviceStartId, intent);
                        } else if ("android.intent.action.FDN_STATE_CHANGED0".equals(action)
                        || "android.intent.action.FDN_STATE_CHANGED1".equals(action)) {
                            doServicehandler(MESSAGE_INIT, serviceStartId, intent);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            registerReceiver(mProcessSimLoadedReceiver, filter);
            filter = new IntentFilter("android.intent.action.FDN_STATE_CHANGED0");
            registerReceiver(mProcessSimLoadedReceiver, filter);
            filter = new IntentFilter("android.intent.action.FDN_STATE_CHANGED1");
            registerReceiver(mProcessSimLoadedReceiver, filter);
	    filter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED0);
            registerReceiver(mProcessSimLoadedReceiver, filter);
	    filter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED1);
            registerReceiver(mProcessSimLoadedReceiver, filter);

        }
    }


    //added for dual sim end


    private void unregisterSimLoadedReceiver() {
        Log.d(LOG_TAG, "unregisterSimLoadedReceiver");
        if (mProcessSimLoadedReceiver != null) {
            unregisterReceiver(mProcessSimLoadedReceiver);
            mProcessSimLoadedReceiver = null;
        }
    }

	private class ServiceHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            switch (msg.what) {
            case MESSAGE_INIT:
                int startId = msg.arg1;
                Intent intent = (Intent) msg.obj;
				Log.w(TAG, "intent = " + intent);
                if (intent != null) {
                    String action = intent.getAction();
                    Log.d(LOG_TAG, "-------------action = " + action);
                    //added for dual sim begin
                    if (isDualSim) {
                        int phoneId = msg.arg2;
                        String whichSIM = "SIM" + (phoneId + 1);
                        TelephonyManager t = (TelephonyManager) getSystemService(PhoneFactory
                                             .getServiceName(Context.TELEPHONY_SERVICE, phoneId));

                        Log.d(TAG, "[ServiceHandler] phoneId = " + phoneId + ", ACTION = " + action);
                        if (action.equals(Intent.ACTION_BOOT_COMPLETED) ) {
                            
                            //deleteSimAction(startId, true);
                            
			                if (!isBootCompleted()) {
                                 setBootCompleteState(true);
                            }
                            synchronized (PREFS_SIM_STATE_LOCK[phoneId]) {
                                clearSyncState(mContext, phoneId);
                                if (TelephonyManager.SIM_STATE_ABSENT == t.getSimState() || !checkSimEnabled(phoneId)) {
                                    Log.d(TAG, whichSIM + " IS ABSENT or sim "+ phoneId + " is disabled");
                                    clearSimAction(startId, true, phoneId);
                                    if (isAllSimAbsent()) {
                                        doServicehandler(MESSAGE_FINISH, startId, null);
                                        break;
                                    }
                                }
                            }
                            String sim_oper_num = t.getSimOperator();
                            Log.d(TAG, whichSIM + " PROPERTY_ICC_OPERATOR_NUMERIC value is " + sim_oper_num);
                            if ((sim_oper_num != null && sim_oper_num.length() != 0)) {
				importDualSimAction(startId, phoneId);
                            } else {
                                clearSimAction(startId, true, phoneId);
                                registerSimLoadedReceiver(startId);
                            }
                        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED) ||
					action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED0) ||
					action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED1)) {
                            if(TelephonyManager.SIM_STATE_ABSENT == t.getSimState() || !checkSimEnabled(phoneId)){
                                  Log.d(TAG, whichSIM + " IS ABSENT or sim "+ phoneId + " is disabled in SIM_STATE_CHANGED");
				  synchronized (PREFS_SIM_STATE_LOCK[phoneId]) {
                                  	clearSyncState(mContext, phoneId);
				  	clearSimAction(startId, true, phoneId);
				  }
                            }else {  
			 
				    String stateExtra = intent
							.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
				    //phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, -1);
				    Log.d(TAG, "[ServiceHandler: in SIM_STATE_CHANGED] ACTION_SIM_STATE_CHANGED phoneId = " + phoneId + " INTENT_KEY_ICC_STATE = " + stateExtra);
				    
				    if (TelephonyManager.SIM_STATE_READY == t.getSimState() && IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra) && checkSimEnabled(phoneId)) {
					if (!isBootCompleted()) {
					    clearSyncState(mContext, phoneId);
					    //setBootCompleteState(true);
					 }
					    checkSimPrefsAndDo(startId, phoneId);

				    } else {
					clearSyncState(mContext, phoneId);
				  	clearSimAction(startId, true, phoneId);
                                	registerSimLoadedReceiver(startId);
					//finishServiceSelf(startId);
				    }
			  }
                        } else if (TelephonyIntents.SIM_ADNCACHE_LOADED.equals(action)) {
                            Log.v(TAG, "IccCard.INTENT_KEY_PHONE_ID = " + phoneId);
                            if (t.getAdnCachestate() == Constants.ADNCACHE_STATE_NOT_READY) {
                                   Log.w(TAG, "importSimAction return when adn cache state not ready.");
                                   return;
                            }else{
			        String sim_sync_state = getSyncState(mContext, phoneId);
				Log.d(TAG, "[andCache loaded] sim_sync_state = " + sim_sync_state + " , phoneId = " + phoneId);
				if (PREFS_SIM_STATE_BOOT_DELETED.equals(sim_sync_state)
					|| PREFS_SIM_STATE_IDLE.equals(sim_sync_state)) {
				    importDualSimAction(startId, phoneId);
				} else if (PREFS_SIM_STATE_IMPORTED.equals(sim_sync_state)) {
				    doServicehandler(MESSAGE_FINISH, startId, null);
				} 
				
			    }
                        } else if ("android.intent.action.FDN_STATE_CHANGED0".equals(action)
                                   || "android.intent.action.FDN_STATE_CHANGED1".equals(action)) {
                    
                        	phoneId = intent.getIntExtra("phone_id", -1);
                        	boolean isEnableFDN = intent.getBooleanExtra("fdn_status", false);
                        	Log.d(TAG, "in FDN changed phoneId = " + phoneId + " fdn enable is " + isEnableFDN);
                        	if(phoneId < 0){
                        		Log.e(TAG, "receive fdn phoneId < 0");
                        		doServicehandler(MESSAGE_FINISH, startId, null);
					return;
                        	}
                        	if(isEnableFDN){
                        		//if fdn is activited, we need to delete sim phoneId contacts in database
                        		synchronized (PREFS_SIM_STATE_LOCK[phoneId]) {
                                    clearSyncState(mContext, phoneId);
                                    //no need to find sim card is disabled
                                  if (TelephonyManager.SIM_STATE_ABSENT == t.getSimState() || !checkSimEnabled(phoneId)) {
                                    	Log.d(TAG, whichSIM + " IS ABSENT or sim "+ phoneId + " is disabled or fdn is enable");
                                        clearSimAction(startId, true, phoneId);
                                        doServicehandler(MESSAGE_FINISH, startId, null);
                                    }
                        		}
                        	}else{
                        		//if fdn is disable 
					Log.d(TAG, "begin to import from sim card in fdn ");
					airModeImport(startId, phoneId);
                        		 //checkSimPrefsAndDo(startId, phoneId);
                        	}
                            
                           
                        }else if(action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)){
				Log.d(TAG, "enter to airplan mode = " + intent.getBooleanExtra("state", false));	
	                	boolean airMode = intent.getBooleanExtra("state", false);
                            	//deleteSimAction(startId, true);
				if (airMode) {
					Log.d(TAG, "airplan mode is turn on");
                            	//	deleteSimAction(startId, true);
					clearSimAction(startId, true, phoneId);
                                    	doServicehandler(MESSAGE_FINISH, startId, null);
				}else{
					Log.d(TAG, "airplan mode is turn off");
                                   setBootCompleteState(true);
				   synchronized (PREFS_SIM_STATE_LOCK[phoneId]) {
					clearSyncState(mContext, phoneId);
					if (TelephonyManager.SIM_STATE_ABSENT == t.getSimState() || !checkSimEnabled(phoneId)) {
                                    	    Log.d(TAG, whichSIM + " IS ABSENT or sim "+ phoneId + " is disabled");
					    clearSimAction(startId, true, phoneId);
					    doServicehandler(MESSAGE_FINISH, startId, null);
					}
				    }
				    String sim_oper_num = t.getSimOperator();
				    Log.d(TAG, whichSIM + " PROPERTY_ICC_OPERATOR_NUMERIC value is " + sim_oper_num);
				    if ((sim_oper_num != null && sim_oper_num.length() != 0)) {
					importDualSimAction(startId, phoneId);
				    } else {
                                        clearSimAction(startId, true, phoneId);
					registerSimLoadedReceiver(startId);
				    }
				}
			}else if(action.equals("android.intent.action.SelectSimCard")){
				Log.d(TAG, "Enable or disable sim card");
				//once receive this message, you should know which sim card state is changed or all changed
				phoneId = -1; 
				boolean sim1Changed = intent.getBooleanExtra("SIM1", false);
				boolean sim2Changed = intent.getBooleanExtra("SIM2", false);	
				boolean isSim1Enabled = true;
				boolean isSim2Enabled = true;
				Log.d(TAG, "SIM1 changed : "+ sim1Changed + "  SIM2 changed : "+ sim2Changed);	
				if(sim1Changed){
					//if sim1 changed,get sim1 status
					phoneId = 0;
					isSim1Enabled = Settings.System.getInt(mContext.getContentResolver(),
                 				PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneId), 1) == 1;
					Log.d(TAG, "isSim1Enable is ["+isSim1Enabled+"]");
					if(isSim1Enabled){
						//import from sim card
						Log.d(TAG, "begin to import from sim1 in selectSimCard");
						airModeImport(startId, phoneId);	
					}else{
						//delete sim card contacts
						Log.d(TAG, "begin to delete sim1 card contacts in selectSimCard");
						//deleteSimAction(startId, true, phoneId);
						clearSimAction(startId, true, phoneId);
                                		//finishServiceSelf(startId);
					}
				} 
				
				if(sim2Changed){
					//if sim1 changed,get sim1 status
					phoneId = 1;
					isSim2Enabled = Settings.System.getInt(mContext.getContentResolver(),
                 				PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneId), 1) == 1;
					Log.d(TAG, "isSim2Enable is ["+isSim2Enabled+"]");
					if(isSim2Enabled){
						//import from sim card
						Log.d(TAG, "begin to import from sim2 in selectSimCard");
						airModeImport(startId, phoneId);	
					}else{
						//delete sim card contacts
						Log.d(TAG, "begin to delete sim2 card contacts in selectSimCard");
						//deleteSimAction(startId, true, phoneId);
						//deleteSimAction(startId, false, phoneId);
						clearSimAction(startId, false, phoneId);
                                		//finishServiceSelf(startId);
					}
			
				}
				if(!isSim1Enabled || !isSim2Enabled){
					Log.d(TAG, "sim1 or sim2 is disabled so finish this service");
					finishServiceSelf(startId);
				}
				
			}


                    }// added for dual sim end
                    else {
                        if (action.equals(Intent.ACTION_BOOT_COMPLETED) || action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED) ||
                                action.equals(TelephonyIntents.ACTION_STK_REFRESH_SIM_CONTACTS)) {
                            clearSyncState();
                            Log.v(LOG_TAG, "action is " + action);
                            //deleteSimAction(startId, true);
                            isWorking = false;
                            String sim_oper_num = android.os.SystemProperties.get(PROPERTY_ICC_OPERATOR_NUMERIC);
                            if (sim_oper_num == null) {
                                Log.d(LOG_TAG, "PROPERTY_ICC_OPERATOR_NUMERIC value's null");
                            } else if (sim_oper_num.length() == 0) {
                                Log.d(LOG_TAG, "PROPERTY_ICC_OPERATOR_NUMERIC value's length is 0");
                            } else {
                                Log.d(LOG_TAG,"sim_oper_num = " + sim_oper_num);
                            }

                            boolean isAirplaneEnable = false;
                            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                                isAirplaneEnable = intent.getBooleanExtra("state", false);
                            }
                            //if sim card is absent, finish this service
                            if (TelephonyManager.SIM_STATE_ABSENT == ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getSimState()
                                    || isAirplaneEnable) {
                                Log.d(LOG_TAG, "sim is not absent");
                                deleteSimAction(startId, true);
                                doServicehandler(MESSAGE_FINISH, startId, null);
                            }
                            if ((sim_oper_num != null && sim_oper_num.length() != 0)) {
                                Log.d(LOG_TAG, "PROPERTY_ICC_OPERATOR_NUMERIC is not null and start import sim action");
                                boolean isFdnEnable = CommonUtil.isFdnEnable(mContext);
                                if(!isFdnEnable && !isAirplaneEnable) {
                                    importSimAction(startId);
                                } else {
                                    finishServiceSelf(startId);
                                }
                            } else {
                                registerSimLoadedReceiver();
                            }
                        } else if (action.equals(Intent.ACTION_SHUTDOWN)) {
                            Log.v(LOG_TAG, "action is " + action);
                            //deleteSimAction(startId, false);
                        } else if (action
                                   .equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                            String stateExtra = intent
                                                .getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
                            Log.d(LOG_TAG, "-------------stateExtra = "
                                  + stateExtra);
                            if (stateExtra.equals(IccCard.INTENT_VALUE_ICC_LOADED)) {
                                if (!isBootCompleted()) {
                                    setBootCompleteState(true);
                                }
                                String sim_sync_state = getSyncState();
                                Log.d(LOG_TAG, "-------------sim_sync_state = " + sim_sync_state);
                                if (sim_sync_state.equals(PREFS_SIM_STATE_BOOT_DELETED)
                                        || sim_sync_state.equals(PREFS_SIM_STATE_IDLE)) {
                                    importSimAction(msg.arg1);
                                } else if (sim_sync_state.equals(PREFS_SIM_STATE_IMPORTED)) {
                                    querySimAction(msg.arg1);
                                }
                            } else {
                                finishServiceSelf(msg.arg1);
                            }
                        } else if ("android.intent.action.FDN_STATE_CHANGED0".equals(action)
                                || "android.intent.action.FDN_STATE_CHANGED1".equals(action)) {
                             clearSyncState();
                             boolean fdnStatus = intent.getBooleanExtra("fdn_status", false);
                             Log.d(LOG_TAG, "action ACTION_FDN_STATE_CHANGED with fdn_status " + fdnStatus);
                             if (fdnStatus) {
                                 deleteSimAction(startId, true);
                                 doServicehandler(MESSAGE_FINISH, startId, null);
                             } else {
                                 importSimAction(startId);
                             }
                        }
                    }
                }
                break;
            case MESSAGE_FINISH: {
                int serviceId = msg.arg1;
                finishServiceSelf(serviceId);
                break;
            }
            default:
                break;
            }
        }
    }

   private boolean isAllSimAbsent(){
	boolean isNoSimCard = false;
	final TelephonyManager telManger1= (TelephonyManager) getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, 0));
	final TelephonyManager telManger2= (TelephonyManager) getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, 1));
	if(TelephonyManager.SIM_STATE_ABSENT == telManger1.getSimState() && TelephonyManager.SIM_STATE_ABSENT == telManger2.getSimState()){
		Log.d(TAG, "there is no sim card");
		isNoSimCard = true;
	}
	Log.d(TAG, "isAllSimAbsent = " + isNoSimCard);
	return isNoSimCard;

   }

    private boolean checkSimEnabled(int phoneId){
    //if sim card is disabled
	boolean isSimEnabled = Settings.System.getInt(mContext.getContentResolver(),
                 				PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneId), 1) == 1;
	//if fdn is disabled
	final TelephonyManager telManger= (TelephonyManager) getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,phoneId));
	boolean isFdnEnabled = telManger.getIccFdnEnabled();
	
	//if airplan mode is enabled	
	boolean isAirModeOn = false;
	int airMode = Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0);
	if(airMode == 1)
		isAirModeOn = true;	
	Log.d(TAG, "[CheckSimEnabled] phoneId=" +phoneId + " isSimEnabled = " + isSimEnabled + " isFdnEnabled = " + isFdnEnabled + " isAirModeOn = " + isAirModeOn + " return result = " +  (isSimEnabled && !isFdnEnabled && !isAirModeOn));
	return isSimEnabled && !isFdnEnabled && !isAirModeOn;
   
    }    


    private void airModeImport(int startId, int phoneId){
	   TelephonyManager t = (TelephonyManager) getSystemService(PhoneFactory
                                             .getServiceName(Context.TELEPHONY_SERVICE, phoneId));
	   if(!isBootCompleted()){
	   	setBootCompleteState(true);
	   }
	   synchronized (PREFS_SIM_STATE_LOCK[phoneId]) {
		clearSyncState(mContext, phoneId);
		if (TelephonyManager.SIM_STATE_ABSENT == t.getSimState()) {
		    Log.d(TAG, "SIM" + phoneId+ " IS ABSENT");
		    clearSimAction(startId, true, phoneId);
		    doServicehandler(MESSAGE_FINISH, startId, null);
		}
	    }
	    String sim_oper_num = t.getSimOperator();
	    Log.d(TAG, "SIM" +phoneId+ " PROPERTY_ICC_OPERATOR_NUMERIC value is " + sim_oper_num);
	    if ((sim_oper_num != null && sim_oper_num.length() != 0)) {
		importDualSimAction(startId, phoneId);
	    } else {
                clearSimAction(startId, true, phoneId);
		registerSimLoadedReceiver(startId);
	    }
	return;

    }

    private static final int BOOT_IDLE = 0;
    private static final int BOOT_COMPLETED = 1;
    private static final String FILE_BOOT_COMPLETE = "/data/data/com.android.contacts/boot_flag";
    private static final String  BOOT_COMPLETE_SYSTEM_FLAG = "dev.bootcomplete";
    private static Object BOOT_COMPLETED_FILE_LOCK = new Object();


    public static boolean isBootCompleted() {
        synchronized (BOOT_COMPLETED_FILE_LOCK) {
            try {
                FileReader file = new FileReader(FILE_BOOT_COMPLETE);
                char[] buffer = new char[1024];
                int len = file.read(buffer, 0, 1024);
                int value = Integer.valueOf((new String(buffer, 0, len)).trim());
                boolean result = (value == BOOT_COMPLETED);
                Log.d(LOG_TAG, "-------------isBootCompleted = " + result);
                return result;
            } catch (Exception e) {
                Log.e(LOG_TAG, "-------------isBootCompleted io fail");
                return false;
            }
        }
    }


    /**
     * @deprecated set flag for boot complete
     */
    private void setBootCompleteState(boolean boot) {

        synchronized (BOOT_COMPLETED_FILE_LOCK) {

            File f = new File(FILE_BOOT_COMPLETE);
            if (f.exists()) {
                f.delete();
            }
            if (boot) {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    Log.d(LOG_TAG, "-------------createNewFile_failed - " + FILE_BOOT_COMPLETE);
                }

                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                    String stateTmp = "" + BOOT_COMPLETED;
                    bw.write(stateTmp);
                    bw.write("\r\n");
                    bw.close();
                } catch (IOException e) {
                    Log.d(LOG_TAG, "-------------write_file_failed - " + FILE_BOOT_COMPLETE);
                }
                Log.d(LOG_TAG, "-------------setBootCompleteState");
            }
        }
    }

    public static final String BROADCAST_ACTION_REFRESH = "com.android.contacts.refresh.afterSync";

    private void finishServiceSelf(int serviceId) {
        if (isDualSim) {
            String sim1_sync_state = getSyncState(mContext,0);
            String sim2_sync_state = getSyncState(mContext,1);
            boolean sim1_sync_over = PREFS_SIM_STATE_IMPORTED.equals(sim1_sync_state)
                                     || PREFS_SIM_STATE_BOOT_DELETED.equals(sim1_sync_state);
            boolean sim2_sync_over = PREFS_SIM_STATE_IMPORTED.equals(sim2_sync_state)
                                     || PREFS_SIM_STATE_BOOT_DELETED.equals(sim2_sync_state);
            if (sim1_sync_over && sim2_sync_over) {
                Log.d(TAG, "service call stopSelf, serviceStartId = " + serviceId);
                stopSelf();
            }
	   //SyncSimContactsReceiver.finishStartingService(
           //     SyncSimContactsService.this, serviceId);
	   stopSelf(); 
	   Log.d(TAG, "finish service 22222222222222222222");

        } else {
            String sim_sync_state = getSyncState();
            if (sim_sync_state.equals(PREFS_SIM_STATE_IMPORTED)
                    || sim_sync_state.equals(PREFS_SIM_STATE_BOOT_DELETED)) {
                Intent refresh = new Intent(BROADCAST_ACTION_REFRESH);
                sendBroadcast(refresh);
                Log.d(LOG_TAG, "-------sendBroadcast::refresh");
            }
            SyncSimContactsReceiver.finishStartingService(
                SyncSimContactsService.this, serviceId);
        }
    }

	public static boolean isWorking(){
		return isWorking;
	}
	public final int[] NUMBERTYPE = {Phone.TYPE_MOBILE,Phone.TYPE_WORK,Phone.TYPE_OTHER};
//	public final int[] NUMBERTYPE = {Phone.TYPE_HOME,Phone.TYPE_WORK,Phone.TYPE_OTHER};
	public static final String[] EMAILPHONE = {"phonehome","phonemobile","homework","","","","phoneother","email"};
	private static String[] splitString(String source){
		String[] result = {"","",""};
		if(source == null || source.equals("")){
			return result;
		}
		Integer pos;
		String  strSource = source;
		Integer times=0;
		while ( (pos = strSource.indexOf(AdnRecord.ANR_SPLIT_FLG)) >=0 ) {
			result[times] = strSource.substring(0, pos);
			strSource = strSource.substring(pos+1);
			times++;
		}
		if (strSource.length() > 0) {
			result[times]  = strSource;
		}

        return result;
    }

    private void checkSimPrefsAndDo(int serviceStartId,int phoneId) {
        String sim_sync_state = getSyncState(mContext, phoneId);
        Log.d(TAG, "[checkSimPrefsAndDo] sim_sync_state = " + sim_sync_state + " , phoneId = " + phoneId);
        if (PREFS_SIM_STATE_BOOT_DELETED.equals(sim_sync_state)
                || PREFS_SIM_STATE_IDLE.equals(sim_sync_state)) {
            importDualSimAction(serviceStartId, phoneId);
        } else if (PREFS_SIM_STATE_IMPORTED.equals(sim_sync_state)) {
            doServicehandler(MESSAGE_FINISH, serviceStartId, null);
        } else if (PREFS_SIM_STATE_IMPORTING.equals(sim_sync_state)){
	   //do nothing
	}
	else {
            waitForBootAndImport(serviceStartId, phoneId);
        }
    }

    private  String[] getSimContactRecordFromLocalCursor(Cursor c) {
        Map<Integer,String> anrMap = new HashMap<Integer,String>();
        int anrNum = 0;
        String[] record = {"","","",""};
        if (c != null) {
            try {
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    if (Phone.CONTENT_ITEM_TYPE.equals(c.getString(2))) {
                        int numberIndex = c.getInt(1);
                        if (numberIndex == 0) {
                            record[1] = c.getString(0);
                        } else {
                            anrMap.put(numberIndex, c.getString(0));
                            anrNum = anrNum > numberIndex ? anrNum : numberIndex;
                        }
                    } else if (StructuredName.CONTENT_ITEM_TYPE.equals(c
                               .getString(2))) {
                        record[0] = c.getString(0);
                        if (record[0] == null) {
                            record[0] = "";
                        }
                    } else if (Email.CONTENT_ITEM_TYPE.equals(c
                               .getString(2))) {
                        record[3] = c.getString(0);
                        if (record[3] == null) {
                            record[3] = "";
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "get information of sim contact fail", e);
            } finally {
                c.close();
                c = null;
            }
        }
        String anr = "";
        for (int i = 1; i <= anrNum; i++) {
            String anrNumber = anrMap.get(i);
            anr += ":";
            if (!TextUtils.isEmpty(anrNumber)) {
                anr += anrNumber;
            }
        }
        anr = anr.length() > 0 ? anr.substring(1) : "";
        record[2] = anr;
        return record;
    }

}
