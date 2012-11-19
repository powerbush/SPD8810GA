/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;


import java.util.ArrayList;
import java.util.Locale;
import android.accounts.Account;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AlphabetIndexer;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IIccPhoneBook;
import android.os.ServiceManager;
import com.android.internal.telephony.IccConstants;

//added for dual sim
import com.android.contacts.util.CommonUtil;
import com.android.contacts.util.Config;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.PhoneFactory;
/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
public final class MultiOperateListActivity extends ListActivity implements TextWatcher{
    private static final String TAG = "MultiOperateListActivity";

    static final String NAME_COLUMN = Contacts.DISPLAY_NAME;
    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID, // 0
        Contacts.DISPLAY_NAME, // 1
        Contacts.HAS_PHONE_NUMBER, //2
        Contacts.LOOKUP_KEY, //3
        RawContacts.SIM_INDEX,	//4
        Contacts.PHOTO_ID,	//5

        //added for dual sim
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE,

    };
    static final String[] CONTACTS_PHONE_OR_EMAIL = new String[] {
        "data." + Contacts._ID, // 0
        Contacts.DISPLAY_NAME, // 1
        Contacts.SORT_KEY_PRIMARY, //2   modify by dory.zheng for MMS to contacts bug
        ContactsContract.Data.DATA1, //3 yeezone:jinwei
        RawContacts.SIM_INDEX, //4
        Contacts.PHOTO_ID, //5 modify for bug 11943

        //added for dual sim
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE,
    };

    static final int SUMMARY_ID_COLUMN_INDEX = 0;
    static final int SUMMARY_NAME_COLUMN_INDEX = 1;
    static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 2;
    static final int SUMMARY_LOOKUP_KEY = 3;
    static final int SUMMARY_SIM_COLUMN_INDEX = 4;
    static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 5;

    static final int SUMMARY_SORT_KEY_PRIMARY_INDEX = 2; //modify by dory.zheng for MMS to contacts bug

    protected static final int QUERY_TOKEN = 0;

    private static final int DIALOG_PROGRESS = 4;
    private static final int DIALOG_ON_PROGRESS = 5;

    private ProgressDialog mProgressDialog;
    private ProgressDialog mDialogOnProgress;

    private String mMessage;
    ContactItemListAdapter mAdapter;

    private static final int MODE_PICK = 0;
    private static final int MODE_DELETE = 1;
    private static final int MODE_EXPORT = 2;
    private static final int MODE_IMPORT = 3;
    private static final int ACTION_COLLECT = 4;
    private static final int QUERY_COMPELETE = 5;
    private static final int MODE_SDCARD_EXPORT = 6;
    private static final int MODE_BT_SHARE = 7;
    private static final int MODE_MMS_VCF_CONTACTS = 8;
    private static final int MODE_ADD_CONTACTS_GROUP = 9;
    private static final int MODE_REMOVE_COLLECTION_CONTACTS = 10;
    private static final int MODE_REMOVE_FREQUENT_CONTACTS = 11;

    private static final int PROGRESS_BAR_INCREMENT = 0x10;
    private static final int OPERATE_COMPLETE = 0x11;
    private static final int SHOW_TOAST_MESSAGE = 0x12;

    private static final int GROUP_ALL = 0;
    private static final int GROUP_PHONE = 1;
    private static final int GROUP_SIM = 2;

    //added for dual sim
    private static final int GROUP_SIM1 = 4;
    private static final int GROUP_SIM2 = 5;

    // modify by dory.zheng for NEWMS00120648 at 15-09 begin
    private static final int GROUP_CUSTOM = 3;
    int groupNameId = -1;
    // modify by dory.zheng for MEWMS00120648 at 15-09 end

    int mMode = MODE_PICK;
    int mGroup = GROUP_ALL;

    int mLimit = -1;

    //added for dual sim
    private String mAccountName = null;

    int mContactsGroupNameId = -1;
    String mContactsGroupRingtone = null;

    private QueryHandler mQueryHandler;

    private String mTitle;

    private boolean mSelectAll;
    private CheckBox mSelectAllCheck;
    private Button mOk;
    private Button mCancel;
    private SparseBooleanArray cbs;
    protected Uri mLookupUri;

    private EditText mSearchView;

    private boolean endCollectionContacts;
    private Thread mCollectionContactsThread;
    private ArrayList<ContentValues> mSelectContacts = new ArrayList<ContentValues>();
    public static boolean isOnlyEmail = false; //modify by dory.zheng for export to sim empty record

    private int mBulkAction = -1;
    private final static int MODE_BULK_SMS = 0;
    private final static int MODE_BULK_MAIL = 1;

    private boolean mSearchSuccess = false;

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MODE_DELETE:
            case MODE_EXPORT:

            case MODE_SDCARD_EXPORT:

            case MODE_IMPORT:
                if(mProgressDialog != null && mProgressDialog.isShowing()){
                    dismissDialog(DIALOG_PROGRESS);
                    mProgressDialog = null;
                }
                finish();
                return;
            case ACTION_COLLECT:
                if(mDialogOnProgress != null && mDialogOnProgress.isShowing()){
                    dismissDialog(DIALOG_ON_PROGRESS);
                    mDialogOnProgress = null;
                }
                feedbackCollect();
                break;
            case QUERY_COMPELETE:
                if (mAdapter != null && mAdapter.getCount() > 0) {
                    findViewById(R.id.layoutSelectAll).setVisibility(View.VISIBLE);
                    findViewById(R.id.layoutConfirm).setVisibility(View.VISIBLE);
                    findViewById(R.id.searchTextView).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.layoutSelectAll).setVisibility(View.GONE);
                    findViewById(R.id.layoutConfirm).setVisibility(View.GONE);
                    findViewById(R.id.searchTextView).setVisibility(View.GONE);
                }
                getListView().setEmptyView(findViewById(android.R.id.empty));
                break;
            case PROGRESS_BAR_INCREMENT:
                int progress = msg.arg1;
                if(mProgressDialog != null && mProgressDialog.isShowing()){
                    mProgressDialog.incrementProgressBy(progress);
                } else {
                    showProcessDialog(DIALOG_PROGRESS);
                    if(mProgressDialog != null ){
                        mProgressDialog.incrementProgressBy(progress);
                    }
                }
                break;
            case OPERATE_COMPLETE:
                if(mProgressDialog != null && mProgressDialog.isShowing()){
                    dismissDialog(DIALOG_PROGRESS);
                    mProgressDialog = null;
                }
                finish();
                break;
            case SHOW_TOAST_MESSAGE:
                String text = (String)msg.obj;
                showToast(text);
                break;
            }
        }
    };

    private MultiOperation mOperation = new MultiOperation(this, mHandler);

    private View.OnClickListener selectOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            int cursorCount = mAdapter.getCount();
            switch (v.getId()) {
                case R.id.cbSelctAll:
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(mSelectAllCheck.getWindowToken(), 0);
                    mSelectAll = mSelectAllCheck.isChecked();
                    Log.d(TAG, "select all checkbox has clicked: " + mSelectAll);

                    for (int i = 0; i < cursorCount; i++) {
                        cbs.put(i, mSelectAll);
                    }
                    mAdapter.notifyDataSetChanged();

                    break;
                case R.id.btnOk:
                    int count = 0;
                    for (int i = 0; i < cursorCount; i++) {
                        if (cbs.get(i)) {
                            count++;
                            break;
                        }
                    }
                    if (count > 0) {
                        showProcessDialog(DIALOG_ON_PROGRESS);
                        mCollectionContactsThread = new Thread() {
                            public void run() {
                                collectContacts();
                            }
                        };
                        mCollectionContactsThread.start();
                    } else {
                        if (cursorCount == 0) {
                            showToast(getString(R.string.noContacts));
                        }
                        showToast(getString(R.string.select_contacts));
                    }
                    break;
                case R.id.btnCancel:
                    finish();
                    break;
            }
        }
    };

    protected Uri getSimUri() {
		return  Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "sim_contacts");
	}

    private void collectContacts() {
        mSelectContacts.clear();
        SparseBooleanArray array = cbs;
        StringBuilder uriListBuilder = new StringBuilder();
        int emailNum = 0;
        int anrNum = 0;

        int cursorCount = mAdapter.getCount();
        for (int i = 0; i < cursorCount; i++) {
            if(endCollectionContacts){
                break;
            }
            if (array.get(i)) {
                Cursor cursor = (Cursor) mAdapter.getItem(i);
                if(mMode == MODE_PICK){
                    ContentValues cv = new ContentValues();
                    if(mBulkAction == MODE_BULK_MAIL) {
                        cv.put("number", cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1)));
                    } else {
                        cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_NAME_COLUMN_INDEX], cursor.getString(SUMMARY_NAME_COLUMN_INDEX));
                        cv.put("number", cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1)));
                    }
                    mSelectContacts.add(cv);
                    continue;
                }

                long id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                String name = cursor.getString(SUMMARY_NAME_COLUMN_INDEX);
                String simIndex = cursor.getString(SUMMARY_SIM_COLUMN_INDEX);
                String lookUpKey = cursor.getString(SUMMARY_LOOKUP_KEY);

                //added for dual sim
                String accountName = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));


                if(mMode==MODE_DELETE) {
                    ContentValues cv = new ContentValues();
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX], id);
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_SIM_COLUMN_INDEX], simIndex);
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_LOOKUP_KEY], lookUpKey);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if (mMode == MODE_ADD_CONTACTS_GROUP) {
                    ContentValues cv = new ContentValues();
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX], id);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if(mMode == MODE_REMOVE_FREQUENT_CONTACTS) {
                    ContentValues cv = new ContentValues();
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX], id);
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_LOOKUP_KEY], lookUpKey);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if(mMode == MODE_REMOVE_COLLECTION_CONTACTS) {
                    ContentValues cv = new ContentValues();
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX], id);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if(mMode == MODE_SDCARD_EXPORT) {
                    ContentValues cv = new ContentValues();
                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX], id);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if(mMode == MODE_IMPORT){
                    boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                    ContentValues cv = getAllDataPhoneNum(cursor.getLong(SUMMARY_ID_COLUMN_INDEX), simIndex);
                    cv.put(RawContacts.ACCOUNT_NAME, accountName);
                    mSelectContacts.add(cv);
                } else if(mMode == MODE_EXPORT) {
                    boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                    if (hasPhone || (!TextUtils.isEmpty(name))) {//yeezone:haojie
                        name = getTagNAME(name); //modify by dory.zheng for the name length max 14

                        //added for dual sim
                        IIccPhoneBook iccIpb;
                        if(Config.isMSMS){
                            if( Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
                                iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
                                        PhoneFactory.getServiceName("simphonebook",0)));
                            }
                            else{
                                iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
                                        PhoneFactory.getServiceName("simphonebook",1)));
                            }
                        }
                        else{
                            iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
                        }

                        try {
                            anrNum = iccIpb.getAnrNum();
                            emailNum = iccIpb.getEmailNum();
                        } catch (RemoteException ex) {
                            Log.v(TAG, "excetpion");
                        } catch (SecurityException ex) {
                            Log.v(TAG, "excetpion");
                        }
                        ArrayList<Usimphonestruct> usimContactsMap = getAllExportDataPhoneNum(
                                cursor.getLong(SUMMARY_ID_COLUMN_INDEX),
                                simIndex, anrNum + 1, emailNum);
                        //for bugzilla 13568
//                        int contactsCount = mSelectContacts.size();
//                        contactsCount += usimContactsMap == null || usimContactsMap.isEmpty() ?
//                                1 : usimContactsMap.size();
//                        if(contactsCount > remainRecord){
//                            String message = getString(R.string.import_sim_too_many);
//                            mHandler.obtainMessage(SHOW_TOAST_MESSAGE, message).sendToTarget();
//                            dismissDialog(DIALOG_ON_PROGRESS);
//                            return;
//                        }
                        String phoneName = "";
                        String phoneHome = "";
                        String phoneMobile = "";
                        String phoneWork = "";
                        String phoneOther = "";
                        String email = "";
                        for (int j = 0; j < usimContactsMap.size(); j++) {
                            phoneHome = usimContactsMap.get(j).phonenumber;
                            phoneMobile = usimContactsMap.get(j).anr[0];
                            phoneWork = usimContactsMap.get(j).anr[1];
                            phoneOther = usimContactsMap.get(j).anr[2];
                            email = usimContactsMap.get(j).email;
                            phoneName = usimContactsMap.get(j).name;
                            Log.d(TAG, "export to sim >>> phoneHome==="+phoneHome +" phoneMobile==="+phoneMobile
                                    + " phoneWork==="+phoneWork + " phoneOther==="+phoneOther + " email==="+email);

                            String anr = phoneMobile + AdnRecord.ANR_SPLIT_FLG + phoneWork + AdnRecord.ANR_SPLIT_FLG
                            + phoneOther;
                            String mSimNewAnr = "";
                            String newAnr = "";
                            if (anrNum > 0) { // 1
                                mSimNewAnr = phoneMobile;
                                newAnr = phoneMobile + AdnRecord.ANR_SPLIT_FLG + AdnRecord.ANR_SPLIT_FLG;
                                if (anrNum > 1) { // 2
                                    mSimNewAnr += AdnRecord.ANR_SPLIT_FLG + phoneWork;
                                    newAnr = phoneMobile + AdnRecord.ANR_SPLIT_FLG + phoneWork + AdnRecord.ANR_SPLIT_FLG;
                                    if (anrNum > 2) { //
                                        mSimNewAnr = anr;
                                        newAnr = anr;
                                    }
                                }
                            }
                            if (emailNum == 0) {
                                email = "";
                            }

                            //modify by dory.zheng for export to sim empty record begin
                            if(anrNum == 0 && emailNum == 0 && phoneName.equals("") && phoneHome.equals("")){
                                isOnlyEmail = true;
                                continue;
                            }
                            //modify by dory.zheng for exoprt to sim empty record end
                            ContentValues map = new ContentValues();
                            map.put("newTag", phoneName);
                            map.put("newNumber", phoneHome);
                            map.put("newAnr", mSimNewAnr);
//                            map.put("newSimAnr", newAnr);
                            map.put("newEmail", email);

                            //added for dual sim
                            if(Config.isMSMS){
                                map.put("account_name", mAccountName);
                                map.put("account_type", Account.SIM_ACCOUNT_TYPE);
                            }
                            Log.i(TAG,"map:"+map);
                            Log.i(TAG,"account_name"+map.getAsString("account_name"));
                            mSelectContacts.add(map);
                        }
                        //yeezone:jinwei 2011-9-3 process all number is null or ""
                        if(usimContactsMap == null || usimContactsMap.size() == 0){
                            ContentValues map = new ContentValues();
                            map.put("newTag", name);
                            map.put("newNumber", "");
                            map.put("newAnr", "");
//                            map.put("newSimAnr", "");
                            map.put("newEmail", "");

                            //added for dual sim
                            if(Config.isMSMS){
                                map.put("account_name", mAccountName);
                                map.put("account_type", Account.SIM_ACCOUNT_TYPE);
                            }
                            Log.i(TAG,"map:"+map);
                            Log.i(TAG,"account_name"+map.getAsString("account_name"));
                            mSelectContacts.add(map);
                        }
                    }
                    //modify by zhengshenglan for NEWMS00118626 at 08-28 end
                } else if (mMode == MODE_BT_SHARE || mMode == MODE_MMS_VCF_CONTACTS) {
                    if (cursor.getCount() <= 0) {
                        Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        uriListBuilder.append(cursor.getString(3));
                        uriListBuilder.append(':');
                    }
                } else {
                    boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                    if (hasPhone) {
                        Cursor phonesCursor = null;
                        phonesCursor = queryPhoneNumbers(cursor.getLong(SUMMARY_ID_COLUMN_INDEX), simIndex);
                        try {
                            if (phonesCursor != null && phonesCursor.getCount() != 0) {
                                phonesCursor.moveToPosition(-1);
                                while (phonesCursor.moveToNext()) {
                                    String number = phonesCursor.getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                                    ContentValues cv = new ContentValues();
                                    cv.put(CONTACTS_SUMMARY_PROJECTION[SUMMARY_NAME_COLUMN_INDEX], name);
                                    cv.put("number", number);
                                    mSelectContacts.add(cv);
                                }
                            }
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }finally{	//yeezone:jinwei 2011-8-17
                            if(phonesCursor != null){
                                phonesCursor.close();
                                phonesCursor = null;
                            }
                        }
                    }
                }
            }
        }
        if (mMode == MODE_BT_SHARE) {
            try {
                Log.d(TAG, "Through Bluetooth share contacts, uriListBuilder is : " + uriListBuilder.toString());
                Uri uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Trough Bluetooth share concats error!", e);
            }
        }
        if(mMode == MODE_MMS_VCF_CONTACTS) {
            try {
                Log.d(TAG, "Through Bluetooth share contacts, uriListBuilder is : " + uriListBuilder.toString());
                Uri uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));
                Intent intent = new Intent();
                intent.setType("text/x-vcard");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                MultiOperateListActivity.this.setResult(RESULT_OK, intent);
                finish();
            } catch (Exception e) {
                Log.d(TAG, "Exception: " + e.toString());
            }
            return;
        }
        mHandler.sendEmptyMessage(ACTION_COLLECT);

        // release some memory
        cbs.clear();
    }
    // add by niezhong 08-30-11 for NEWMS00118868  begin
    private String getTagNAME(String tagName) {

        //added for dual sim
        IIccPhoneBook iccIpb;
        if(Config.isMSMS){
            if( Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
                 iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
                            PhoneFactory.getServiceName("simphonebook",0)));
            }
            else{
                iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
                        PhoneFactory.getServiceName("simphonebook",1)));
            }
        }
        else{
            iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
        }

        int[] adnRecords = new int[]{};
        try {
            adnRecords = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
        } catch (RemoteException ex) {
            Log.v(TAG, "RemoteException");
        } catch (SecurityException ex) {
            Log.v(TAG, "SecurityException");
        }
        final int nameSize = adnRecords[0] - 14;
    	int sCount = 0;
		try {
			sCount = GsmAlphabet.countGsmSeptets(tagName, true,true);
		} catch (EncodeException e) {
			// TODO Auto-generated catch block
			sCount = tagName.length() * 2;
			int size = (nameSize - 1 ) / 2;
			if (sCount > size * 2) {
				tagName = (tagName.subSequence(0, size)).toString();
			}
			return tagName;
		}
		if (sCount > nameSize) {
		  //modify by dory.zheng for add Contacts, the name length max 14 begin
		    StringBuffer tempName = new StringBuffer();
            int tempSize = nameSize;
            char[] tagNameArray = tagName.toCharArray();
            for (char c : tagNameArray){
                if(GsmAlphabet.charToGsmExtended(c) != 32){
                    tempSize -= 2;
                } else {
                    tempSize -= 1;
                }
                if(tempSize < 0) break;
                tempName.append(c);
            }
            tagName = tempName.toString();
          //modify by dory.zheng for add Contacts, the name length max 14 end
		}
		return tagName;
    }
    // add by niezhong 08-30-11 for NEWMS00118868  end
  //modify by zhengshenglan for NEWMS00118626 at 08-28 begin

	private ArrayList<Usimphonestruct> getAllExportDataPhoneNum(long contactId,
			String sim_index, int aiPhoneNumCount, int aiEmailNumCount) {
		ArrayList<String> usimPhone = new ArrayList<String>();
		ArrayList<String> usimEmail = new ArrayList<String>();
		Cursor phonesCursor = null;
		String name = queryPhoneName(contactId, sim_index);
		name = getTagNAME((name == null ? "":name));
		phonesCursor = queryPhoneNumbers(contactId, sim_index);
		try {
			if (!(phonesCursor == null || phonesCursor.getCount() == 0)) {
				phonesCursor.moveToPosition(-1);
				while (phonesCursor.moveToNext()) {
					String type = phonesCursor.getString(phonesCursor
							.getColumnIndex(Data.MIMETYPE));
					String data = phonesCursor.getString(phonesCursor
							.getColumnIndex(Phone.NUMBER));
					if (Phone.CONTENT_ITEM_TYPE.equals(type) && !TextUtils.isEmpty(data)) {
						data = data.replace("-", "");
						usimPhone.add(data.length() > 20 ? data.substring(0, 20) : data);
					} else if (aiEmailNumCount > 0 && Email.CONTENT_ITEM_TYPE.equals(type) && !TextUtils.isEmpty(data)) {
						usimEmail.add(data.length() > 40 ? data.substring(0, 40) : data);
					}
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (phonesCursor != null) {
				phonesCursor.close();
				phonesCursor = null;
			}
		}
		int phonenumberpackage = (usimPhone.size() % aiPhoneNumCount == 0 ? usimPhone.size()
				/ aiPhoneNumCount : usimPhone.size() / aiPhoneNumCount + 1);
		phonenumberpackage = phonenumberpackage > usimEmail.size() ? phonenumberpackage
				: usimEmail.size();
		ArrayList<Usimphonestruct> result = new ArrayList<Usimphonestruct>();

		for (int i = 0; i < phonenumberpackage; i++) {
			Usimphonestruct usimstruct = new Usimphonestruct();
			if (i * aiPhoneNumCount >= usimPhone.size()) {
				usimstruct.phonenumber = "";
			} else {
				usimstruct.phonenumber = usimPhone.get(i * aiPhoneNumCount); // phonenumber
			}
			for (int j = 0 ; j < aiPhoneNumCount - 1 ; j++) {
			    int index = i * aiPhoneNumCount + 1 + j;
			    if (index < usimPhone.size()) {
			        usimstruct.anr[j] = usimPhone.get(index);
			    }
			}
			if (i >= usimEmail.size()) {
				usimstruct.email = "";
			} else {
				usimstruct.email = usimEmail.get(i); // email
			}
			usimstruct.name = name;
			result.add(usimstruct);
		}
		return result;
	}

    //modify by zhengshenglan at 08-24 begin
    private ContentValues getAllDataPhoneNum(long contactId , String simIndex) {
    	String phoneHome = "";
    	String phoneMobile = "";
    	String phoneWork = "";
    	String phoneOther = "";
    	String type ;
    	int phoneType ;
    	String email = "";
		Cursor phonesCursor = null;
		String name = queryPhoneName(contactId, simIndex);
        phonesCursor = queryPhoneNumbers(contactId, simIndex);
        try {
        	if(phonesCursor != null){
			    phonesCursor.moveToPosition(-1);
			    while (phonesCursor.moveToNext()) {
			        	type = phonesCursor.getString(phonesCursor.getColumnIndex(Data.MIMETYPE));
			        	if(Email.CONTENT_ITEM_TYPE.equals(type)){
			        		email = phonesCursor.getString(phonesCursor.getColumnIndex(Email.DATA));
			        		continue;
			        	}
			        	if(Phone.CONTENT_ITEM_TYPE.equals(type)){
			        		phoneType = phonesCursor.getInt(phonesCursor.getColumnIndex("data2"));
			        		if(phoneType == Phone.TYPE_HOME){
			        			phoneHome = phonesCursor.
			        			getString(phonesCursor.getColumnIndex(Phone.NUMBER));
			        			continue;
			        		}
			        		if(phoneType == Phone.TYPE_MOBILE){
			        			phoneMobile = phonesCursor.
			                    getString(phonesCursor.getColumnIndex(Phone.NUMBER));
			        			continue;
			        		}
			        		if(phoneType == Phone.TYPE_WORK){
			        			phoneWork = phonesCursor.
			                    getString(phonesCursor.getColumnIndex(Phone.NUMBER));
			        			continue;
			        		}
			        		if(phoneType == Phone.TYPE_OTHER){
			        			phoneOther = phonesCursor.
			                    getString(phonesCursor.getColumnIndex(Phone.NUMBER));
			        			continue;
			        		}
//			        	}
			        }
			    }
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			if(phonesCursor != null){
				phonesCursor.close();
				phonesCursor = null;
			}
	}
        Log.v(TAG, "ContactsListActivity---getAllDataPhoneNum---phoneHome="+phoneHome
				+";phoneMobile="+phoneMobile+";phoneWork="+phoneWork+";phoneOther="+phoneOther+";email="+email);
		ContentValues cv = new ContentValues();
		cv.put("phoneHome", phoneHome);
		cv.put("phoneMobile", phoneMobile);
		cv.put("phoneWork", phoneWork);
		cv.put("phoneOther", phoneOther);
		cv.put("email", email);
		cv.put("name", name);
        return cv;
	}
	private String queryPhoneName(long contactId, String sim_index) {
		String name = null;
		Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
				contactId);
		Uri dataUri = Uri.withAppendedPath(baseUri,
				Contacts.Data.CONTENT_DIRECTORY);

		Cursor c = getContentResolver().query(dataUri,
				new String[] { Phone.NUMBER }, Data.MIMETYPE + "=?",
				new String[] { StructuredName.CONTENT_ITEM_TYPE }, null);
		if (c != null && c.moveToNext()) {
			name = c.getString(c.getColumnIndex(Phone.NUMBER));
		}
		if(c != null){
			c.close();
		}
		return name == null ? "" : name;
	}

    private Cursor queryPhoneNumbers(long contactId, String simIndex) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);
        String simIndexSelection="sim_index="+simIndex+" AND ";

        Cursor c = getContentResolver().query(dataUri,
                new String[] {Phone._ID, Phone.NUMBER,Phone.DATA2,Phone.MIMETYPE,Phone.IS_SUPER_PRIMARY},
                simIndexSelection+Data.MIMETYPE + "=? or "+Data.MIMETYPE + "=?", new String[] {Phone.CONTENT_ITEM_TYPE,Email.CONTENT_ITEM_TYPE}, null);
        if (c != null){	// && c.moveToFirst()) {	//yeezone:jinwei
            return c;
        }

        return null;
    }

    private String convertValuesForMms() {

        final String CR = "\r";
        final String CRLF = "\r\n";

        StringBuffer sb = new StringBuffer();

        for (ContentValues value : mSelectContacts) {
            String name = value.getAsString(Contacts.DISPLAY_NAME);
            String number = value.getAsString("number");

            sb.append(name).append(CR).append(number).append(CRLF);
        }
        return sb.toString();
    }

    private boolean checkMaxLimit(int size) {
        if (mLimit > 0 && mLimit <= size) {
            Toast.makeText(MultiOperateListActivity.this,
                    getString(R.string.tip_limit_contacts_tomms, mLimit), Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return true;
    }

    private void feedbackCollect() {
        if (mMode == MODE_PICK) {
            if (mBulkAction == MODE_BULK_SMS) {
                final int size = mSelectContacts.size();
                if (!checkMaxLimit(size)) {
                    return;
                }
                Intent result = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        Constants.SCHEME_SMSTO, "", null));
                // result.putParcelableArrayListExtra("ContactsForMms", mSelectContacts);
                result.putExtra("ContactsForMms", convertValuesForMms());
                result.putExtra("count", size);
                result.putExtra("mAction", MODE_BULK_SMS);
                startActivity(result);
            } else if (mBulkAction == MODE_BULK_MAIL) {
                StringBuffer emailStr = new StringBuffer();
                for (ContentValues contacts : mSelectContacts) {
                    String email = contacts.getAsString("number");
                    if (CommonUtil.isEmailAddress(email)) {
                        emailStr.append(email + ",");
                    }
                }
                if (emailStr.length() > 0) {
                    String email = emailStr.substring(0, emailStr.length() - 1);
                    emailStr = null;
                    final Uri mailUri = Uri.fromParts(Constants.SCHEME_MAILTO, email, null);
                    Intent bulk_mail_intent = new Intent(android.content.Intent.ACTION_SENDTO,
                            mailUri);
                    startActivity(bulk_mail_intent);
                } else {
                    Toast.makeText(MultiOperateListActivity.this, R.string.error_email_fromat,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                final int size = mSelectContacts.size();
                if (!checkMaxLimit(size)) {
                    return;
                }
                Intent result = new Intent();
                // result.putParcelableArrayListExtra("ContactsForMms", mSelectContacts);
                result.putExtra("ContactsForMms", convertValuesForMms());
                result.putExtra("count", size);
                MultiOperateListActivity.this.setResult(RESULT_OK, result);
            }
            if (null != mSelectContacts) {
                mSelectContacts.clear();
                mSelectContacts = null;
            }
            finish();
			return;
		}

		//for bugzilla 13568
//		if(mMode==MODE_EXPORT){

		    //added for dual sim
//		    IIccPhoneBook iccIpb;
//		    String selection=null;
//		    if(Config.isMSMS){
//		        if( Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
//		            iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
//		                    PhoneFactory.getServiceName("simphonebook",0)));
//		            selection = "sim_index<>0 AND account_name='"+Account.SIM1_ACCOUNT_NAME+"' AND "+getContactSelection();
//		        }
//		        else{
//		            iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(
//		                    PhoneFactory.getServiceName("simphonebook",1)));
//		            selection = "sim_index<>0 AND account_name='"+Account.SIM1_ACCOUNT_NAME+"' AND "+getContactSelection();
//		        }
//		    }
//		    else{
//		        iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
//		        selection = "sim_index<>0 AND "+getContactSelection();
//		    }
//
//		    int[] array = {};
//		    try{
//		        array= iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
//		    }catch(RemoteException e){
//		        //ignore it.
//		    }
//		    Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI, CONTACTS_SUMMARY_PROJECTION, selection, null, getSortOrder());
//		    int remainRecord = array[2]- cursor.getCount();
//		    cursor.close();
//		    Log.d(TAG, "array[2]=" + array[2]);
//		    Log.d(TAG, "remainRecord=" + remainRecord);
//		    Log.d(TAG, "insert count" + mSelectContacts.size());
//		    if(mSelectContacts.size() > remainRecord){
//		        Toast.makeText(MultiOperateListActivity.this, R.string.import_sim_too_many, Toast.LENGTH_LONG).show();
//		        return;
//		    }
//		}
		if(mMode==MODE_SDCARD_EXPORT){

		}
        if(mMode != MODE_ADD_CONTACTS_GROUP) {
           mContactsGroupNameId = -1;
           mContactsGroupRingtone = null;
        }
        if(Config.isMSMS){
            mOperation.start(mSelectContacts, mMode, mContactsGroupNameId, mAccountName, mContactsGroupRingtone);
        }
        else{
            mOperation.start(mSelectContacts, mMode, mContactsGroupNameId, mContactsGroupRingtone);
        }
        mOperation.setState(!endCollectionContacts);
        showProcessDialog(DIALOG_PROGRESS);
    }

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Resolve the intent
        setContentView(R.layout.multi_operate_list_content);
        mSearchView = (EditText) findViewById(R.id.searchtext);
        mSearchView.addTextChangedListener(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mSelectAllCheck = (CheckBox)findViewById(R.id.cbSelctAll);
        mSelectAllCheck.setOnClickListener(selectOnClickListener);
        mOk = (Button)findViewById(R.id.btnOk);
        mOk.setOnClickListener(selectOnClickListener);
        mCancel = (Button)findViewById(R.id.btnCancel);
        mCancel.setOnClickListener(selectOnClickListener);
        // Setup the UI
        final ListView list = getListView();
        list.setFocusable(true);
        list.setItemsCanFocus(false);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final Intent intent = getIntent();
        mMode=intent.getIntExtra("mode",MODE_PICK);
        mLimit = intent.getIntExtra("limit", -1);
        mAccountName=intent.getStringExtra("account_name");
        mBulkAction = intent.getIntExtra("bulkaction", -1);
        Log.v(TAG, "mMode = " + mMode);

        if(mMode == MODE_SDCARD_EXPORT) {
            mLookupUri = Contacts.CONTENT_VCARD_URI;
        }

        mAdapter = new ContactItemListAdapter(this);
        setListAdapter(mAdapter);
        mQueryHandler = new QueryHandler(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(mOperation.getState() && mMode != MODE_PICK){
            Log.d(TAG, "MultiOperateService.getState() && mMode != MODE_PICK");
            mMode = mOperation.getCurrentMode();
        }
        mGroup=getIntent().getIntExtra("group", GROUP_ALL);
        Log.d(TAG, "mGroup:"+mGroup);
        switch (mMode) {
            case MODE_PICK:
                Log.v(TAG, "MODE_PICK");
                mTitle=getString(R.string.select_contacts);
                if(mBulkAction == MODE_BULK_SMS) {
                    mTitle = getString(R.string.bulk_sms);
                } else if(mBulkAction == MODE_BULK_MAIL) {
                    mTitle = getString(R.string.bulk_email);
                }
                //modify by dory.zheng for NEWMS00120648 at 15-09 begin
                int group_type = getIntent().getIntExtra("type", -1);
                switch (group_type) {
                    case GROUP_ALL:
                        mGroup = GROUP_ALL;
                        break;
                    case GROUP_PHONE:
                        mGroup = GROUP_PHONE;
                        break;
                    case GROUP_SIM:
                        mGroup = GROUP_SIM;
                        break;
                    case GROUP_SIM1:
                        mGroup = GROUP_SIM1;
                        break;
                    case GROUP_SIM2:
                        mGroup = GROUP_SIM2;
                        break;
                    default:
                        mGroup = GROUP_CUSTOM;
                        groupNameId = getIntent().getIntExtra("groupNameId", -1);
                        break;
                }
                break;
                //modify by dory.zheng for NEWMS00120648 at 15-09 end
            case MODE_DELETE:
                mTitle=getString(R.string.delete_contacts);
                mMessage=getString(R.string.deleting_contacts);
                break;
            case MODE_ADD_CONTACTS_GROUP:
                mContactsGroupNameId = getIntent().getIntExtra("mContactsGroupNameId", -1);
                mContactsGroupRingtone = getIntent().getStringExtra("mContactsGroupRingtone");
                mTitle = getString(R.string.add_contacts_to_group);
                mMessage = getString(R.string.adding_contacts_to_group);
                break;
            case MODE_REMOVE_FREQUENT_CONTACTS:
                mTitle = getString(R.string.remove_frequent_contacts);
                mMessage = getString(R.string.removing_frequent_contacts);
                break;
            case MODE_REMOVE_COLLECTION_CONTACTS:
                mTitle = getString(R.string.remove_collection_contacts);
                mMessage = getString(R.string.removing_collection_contacts);
                break;
            case MODE_EXPORT:
                mTitle=getString(R.string.export_contacts);
                mMessage=getString(R.string.exporting_contacts);
                mGroup=GROUP_PHONE;

                // listener phone call coming
                final int phoneCount = TelephonyManager.getPhoneCount();
                for (int i = 0; i < phoneCount; i++) {
                    TelephonyManager t = (TelephonyManager) getSystemService(PhoneFactory
                            .getServiceName(Context.TELEPHONY_SERVICE, i));
                    if (null != t) {
                        t.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                    }
                }

                break;
            case MODE_IMPORT:
                mTitle=getString(R.string.import_contacts);
                mMessage=getString(R.string.importing_contacts);
                //mGroup=GROUP_SIM;
                break;

            case MODE_SDCARD_EXPORT:
                mTitle=getString(R.string.export_contacts_to_sdcard);
                mMessage=getString(R.string.exporting_contacts);
                mGroup=GROUP_ALL;
                break;
        }
        setTitle(mTitle);
        if(!mOperation.getState() || mMode==MODE_PICK){
            if(!this.isFinishing()){
                startQuery(true);
            }
        }
    }

	@Override
	protected void onStop() {
		Log.d(TAG, "onStop");
		super.onStop();
		if (!mOperation.getState()) {
	        endCollectionContacts = true;
	        SaflyExitThread(mCollectionContactsThread);
			finish();
		} else {
		    mOperation.setState(false);
		}
	}

	@Override
	protected void onDestroy() {
	    Log.d(TAG, "onDestory");
        endCollectionContacts = true;
        SaflyExitThread(mCollectionContactsThread);
    	if(mAdapter!=null){
    	    mAdapter.changeCursor(null);
    	}
		try {
			if(mMode!=MODE_PICK){
				if(mDialogOnProgress != null && mDialogOnProgress.isShowing()){
					dismissDialog(DIALOG_ON_PROGRESS);
					mDialogOnProgress = null;
				}

				if(mProgressDialog != null && mProgressDialog.isShowing()){
					Log.d(TAG, "dismiss DIALOG_PROGRESS");
					dismissDialog(DIALOG_PROGRESS);
					mProgressDialog = null;
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}

        if (mMode == MODE_EXPORT) {
            // remove listener phone
            final int phoneCount = TelephonyManager.getPhoneCount();
            for (int i = 0; i < phoneCount; i++) {
                TelephonyManager t = (TelephonyManager) getSystemService(PhoneFactory
                        .getServiceName(Context.TELEPHONY_SERVICE, i));
                if (null != t) {
                    t.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                }
            }
        }

        if (null != cbs) {
            cbs.clear();
            cbs = null;
        }
		super.onDestroy();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
    	switch (id) {
    	case DIALOG_PROGRESS:
    		//modify by dory.zheng for NEWMS138007 begin
    		mOk.setEnabled(false);
    		mCancel.setEnabled(false);
    		//modify by dory.zheng for NEWMS138007 end
    		ProgressDialog horizontalProgressDialog = new ProgressDialog(this){
    			public boolean dispatchKeyEvent(KeyEvent event) {
    				if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&mMode!=MODE_PICK){
    					if(!mOperation.getState()){
                            endCollectionContacts = true;
                            SaflyExitThread(mCollectionContactsThread);
                            MultiOperateListActivity.this.finish();
                            return true;
    					}else{
    					    mOperation.setState(false);
    						return true;
    					}
    				}
    				return super.dispatchKeyEvent(event);
    			}
    		};

    		horizontalProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    		horizontalProgressDialog.setTitle(mTitle);
    		horizontalProgressDialog.setMessage(mMessage);
			return horizontalProgressDialog;
    	case DIALOG_ON_PROGRESS:
    		//modify by dory.zheng for NEWMS138007 begin
    		mOk.setEnabled(true);
    		mCancel.setEnabled(true);
    		//modify by dory.zheng for NEWMS138007 end
    		 ProgressDialog spinnerProgressDialog = new ProgressDialog(this){
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if(event.getKeyCode() == KeyEvent.KEYCODE_BACK){
                        endCollectionContacts = true;
                        MultiOperateListActivity.this.finish();
                        return true;
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                        return super.dispatchKeyEvent(event);
                    }
                    return true;
                }
            };
    		return spinnerProgressDialog;
    	}
    	return super.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		// TODO Auto-generated method stub
		switch (id) {
    	case DIALOG_PROGRESS:
    		if(dialog!=null){
    			mProgressDialog=(ProgressDialog)dialog;
    			mProgressDialog.setProgress(0);
    			int max = mOperation.getMax();
                Log.d(TAG, "count of selected contacts : " + max);
    			mProgressDialog.setMax(max);

    		}
    		break;
    	case DIALOG_ON_PROGRESS:
    		if(dialog!=null){
    			mDialogOnProgress = (ProgressDialog)dialog;	//yeezone:jinwei for mark this dialog is exist
    			((ProgressDialog)dialog).setMessage(getString(R.string.on_progress));
    		}
    		break;
    	}
		super.onPrepareDialog(id, dialog);
	}
	private void showToast(String toast){
    	Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mList.getWindowToken(), 0);

        ContactListItemCache cache = (ContactListItemCache) v.getTag();
        boolean status = cache.checked.isChecked();
        cache.checked.setChecked(!status);
        if (!status) {
            mSelectAllCheck.setChecked(status);
        }
        cbs.put(position, cache.checked.isChecked());

        // judging whether all are selected.
        int cursorCount = mAdapter.getCount();
        int count = 0;
        for (int i = 0; i < cursorCount; i++) {
            if (cbs.get(i)) {
                count++;
            }
        }

        Log.d(TAG, "-------- the number of being seledted " + count);
        Log.d(TAG, "-------- the number of all items" + cursorCount);
        if (count == cursorCount) {
            Log.d(TAG, "all are selected!!!");
            mSelectAllCheck.setChecked(true);
        }

    }

    private void showProcessDialog(int id) {
        try {
            if (null != this && !this.isFinishing()) {
                showDialog(id);
                return;
            }
            Log.d(TAG, "this activity has finished.");
        } catch (Exception e) {
            Log.e(TAG, "showProcessDialog throw an exception : " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static String getSortOrder() {
        //modify by dory.zheng for MMS to contacts bug begin
        return Contacts.SORT_KEY_PRIMARY;
//    	return NAME_COLUMN + " COLLATE LOCALIZED ASC";
        //modify by dory.zheng for MMS to contacts bug end
    }

    /**
     * filter empty number contacts when SD export
     * @return
     */
    private String getContactSelection() {
        String selection=Contacts.IN_VISIBLE_GROUP + "=1";
        if (mMode!=MODE_DELETE
                && mMode != MODE_EXPORT
                && mMode != MODE_SDCARD_EXPORT
                && mMode != MODE_ADD_CONTACTS_GROUP
                && mMode != MODE_REMOVE_COLLECTION_CONTACTS
                && mMode != MODE_IMPORT) {
            selection+=" AND " +Contacts.HAS_PHONE_NUMBER + "=1";
        }
        return selection;
    }

    void startQuery(boolean showDialog) {
        if(showDialog){
            showProcessDialog(DIALOG_ON_PROGRESS);
        }
        String andWhere = "";
        if(Config.isMSMS){
	     if(!CommonUtil.isSimCardReady(0, false, this)){ 
		if (!CommonUtil.isSimCardReady(1, false, this)){
		    //if sim1 and sim2 all not ready
	            andWhere = " and sim_index = 0";
	        }
	     }
	}else {
	     if(TelephonyManager.SIM_STATE_READY != ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getSimState()){
                  andWhere = " and sim_index = 0";
              }
	}
        Log.d(TAG, "start Query contacts, mGroup is " + mGroup);

        Uri phoneOrEmailUri = Uri.parse("content://"+"com.android.contacts"+"/phone_or_email");
        String where = Data.MIMETYPE + " in ('" + Phone.CONTENT_ITEM_TYPE + "','" + Email.CONTENT_ITEM_TYPE + "') and " + RawContacts.DELETED + " <> 1 ";
        switch (mGroup) {
            case GROUP_ALL:
                if(mMode == MODE_PICK){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and data1 <> ''" + andWhere,//Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
                            null,
                            getSortOrder());
                } else if(mMode == MODE_REMOVE_FREQUENT_CONTACTS) {
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,
                            getContactSelection() + " AND " + Contacts.TIMES_CONTACTED + " > 0", null,
                            getSortOrder());
                } else if(mMode == MODE_REMOVE_COLLECTION_CONTACTS) {
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,
                            getContactSelection() + " AND starred=1",
                            null,
                            getSortOrder());
                } else{
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION, getContactSelection(), null,
                            getSortOrder());
                }
                break;
            case GROUP_PHONE:
                if(mMode == MODE_PICK){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index = 0 and data1 <> ''",//Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
                            null,
                            getSortOrder());
                } else if(mMode == MODE_ADD_CONTACTS_GROUP){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index=0 AND "+getContactSelection() + " and (divided_group_name_id <>" + mContactsGroupNameId + " or divided_group_name_id is NULL)",
                            null,
                            getSortOrder());
                } else{
                    mQueryHandler.startQuery(QUERY_TOKEN, null,Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,"sim_index=0 AND "+getContactSelection(), null,
                            getSortOrder());
                }
                break;
            case GROUP_SIM:
                if(mMode == MODE_PICK){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index <> 0 and data1 <> ''",//Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
                            null,
                            getSortOrder());
                }else{
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,"sim_index<>0 AND "+getContactSelection(), null,
                            getSortOrder());
                }
                break;
            case GROUP_SIM1:
                if(mMode == MODE_PICK){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index <> 0 and data1 <> '' and account_name='"+Account.SIM1_ACCOUNT_NAME+"'",
                            null,
                            getSortOrder());
                }else{
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,"sim_index<>0 and account_name='"+Account.SIM1_ACCOUNT_NAME
                            +"' AND "+getContactSelection(), null, getSortOrder());
                }
                break;
            case GROUP_SIM2:
                if(mMode == MODE_PICK){
                    mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index <> 0 and data1 <> '' and account_name='"+Account.SIM2_ACCOUNT_NAME+"'",
                            null,
                            getSortOrder());
                }else{
                    mQueryHandler.startQuery(QUERY_TOKEN, null, Contacts.CONTENT_URI,
                            CONTACTS_SUMMARY_PROJECTION,"sim_index<>0 and account_name='"+Account.SIM2_ACCOUNT_NAME
                            +"' AND "+getContactSelection(), null, getSortOrder());
                }
                break;
            case GROUP_CUSTOM:
                if(mMode == MODE_PICK){
                    if(groupNameId != -1){
                        if(mBulkAction == MODE_BULK_MAIL) {
                            where = Data.MIMETYPE + " in ('" + Email.CONTENT_ITEM_TYPE + "') and " + RawContacts.DELETED + " <> 1 ";
                            mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                                    CONTACTS_PHONE_OR_EMAIL,
                                    where + "and data1 <> '' and divided_group_name_id =" + groupNameId + andWhere,//Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
                                    null,
                                    getSortOrder());
                        } else {
                            mQueryHandler.startQuery(QUERY_TOKEN, null, phoneOrEmailUri,
                                    CONTACTS_PHONE_OR_EMAIL,
                                    where + "and data1 <> '' and divided_group_name_id =" + groupNameId + andWhere,//Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
                                    null,
                                    getSortOrder());
                        }
                    }
                }
                break;
        }
    }
    //modify by dory.zheng for NEWMS00120648 at 15-09 end

    private final class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (null == cbs) {
                if (null != cursor) {
                    cbs = new SparseBooleanArray(cursor.getCount());
                } else {
                    cbs = new SparseBooleanArray();
                }
            } else {
                cbs.clear();
            }
            mAdapter.changeCursor(cursor);
            mAdapter.setLoading(false);
            if (mDialogOnProgress != null && mDialogOnProgress.isShowing()) {
                dismissDialog(DIALOG_ON_PROGRESS);
                mDialogOnProgress = null;
            }
            mHandler.sendEmptyMessage(QUERY_COMPELETE);
        }
    }

    final static class ContactListItemCache {
        public CheckBox checked;
        public TextView nameView;
        public TextView desNumberView;
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public ImageView mPhoto;
    }

    private final class ContactItemListAdapter extends ResourceCursorAdapter
            implements SectionIndexer {
        private SectionIndexer mIndexer;
        private String mAlphabet;
        private boolean mLoading = true;
        private CharSequence mUnknownNameText;
        private boolean checkedItemStatus;

        public ContactItemListAdapter(Context context) {
            super(context, R.layout.multi_operate_list_item, null, false);

            mAlphabet = context.getString(com.android.internal.R.string.fast_scroll_alphabet);

            mUnknownNameText = context.getText(android.R.string.unknownName);
        }

        public void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            View v;
            if (convertView == null) {
                v = newView(mContext, mCursor, parent);
            } else {
                v = convertView;
            }
            if(cbs != null){
            	checkedItemStatus = cbs.get(position);
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = super.newView(context, cursor, parent);

            final ContactListItemCache cache = new ContactListItemCache();
            cache.checked = (CheckBox)view.findViewById(R.id.cbselcet);
            cache.checked.setOnCheckedChangeListener(new OnCheckedChangeListener(){

				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					if(!isChecked){
						mSelectAllCheck.setChecked(false);
						Log.d(TAG, "mSelectAllCheck.setChecked is false");
					}
				}
        	});
            cache.nameView = (TextView) view.findViewById(R.id.name);
            cache.desNumberView = (TextView) view.findViewById(R.id.des_number);
            cache.mPhoto = (ImageView) view.findViewById(R.id.photo);
            view.setTag(cache);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();
            cache.checked.setChecked(checkedItemStatus);

            // Set the name
            cursor.copyStringToBuffer(SUMMARY_NAME_COLUMN_INDEX, cache.nameBuffer);
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                cache.nameView.setText(cache.nameBuffer.data, 0, size);
            } else {
                cache.nameView.setText(mUnknownNameText);
            }

            if (mMode == MODE_PICK) {
                cache.desNumberView.setText(cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1)));
                cache.desNumberView.setVisibility(View.VISIBLE);
            } else {
                cache.nameView.setPadding(0, 16, 0, 0);
            }

            if(cursor.getInt(SUMMARY_SIM_COLUMN_INDEX)!=0){
                //added for dual sim
                if(Config.isMSMS){
                    String accountName = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));

                    if(Account.SIM1_ACCOUNT_NAME.equals(accountName)){
                        cache.mPhoto.setImageResource(R.drawable.ic_contact_picture_sim1);
                    }
                    else{
                        cache.mPhoto.setImageResource(R.drawable.ic_contact_picture_sim2);
                    }
                }
                else{
                    cache.mPhoto.setImageResource(R.drawable.ic_sim_contact_list_picture);
                }

        	}else {
        		long photoId = 0;
                if (!cursor.isNull(SUMMARY_PHOTO_ID_COLUMN_INDEX)) {
                    photoId = cursor.getLong(SUMMARY_PHOTO_ID_COLUMN_INDEX);
                }
                if (photoId == 0) {
                	cache.mPhoto.setImageResource(R.drawable.ic_contact_list_picture);
                }else {
                	 Bitmap photo = null;
                     try {
                         photo = ContactsUtils.loadContactPhoto(mContext, photoId, null);
                     } catch (OutOfMemoryError e) {
                         e.printStackTrace();
                     }
                     if (photo != null) {
                         cache.mPhoto.setImageBitmap(photo);
                     }
				}
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            Cursor cursor = doFilter(constraint.toString());
            if (null == cbs) {
                if (null != cursor) {
                    cbs = new SparseBooleanArray(cursor.getCount());
                } else {
                    cbs = new SparseBooleanArray();
                }
            } else {
                cbs.clear();
            }
            return cursor;
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            // Update the indexer for the fast scroll widget
            if (mSearchSuccess) {
                if (mAdapter != null && mAdapter.getCount() > 0) {
                    findViewById(R.id.layoutSelectAll).setVisibility(View.VISIBLE);
                    findViewById(R.id.layoutConfirm).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.layoutSelectAll).setVisibility(View.GONE);
                    findViewById(R.id.layoutConfirm).setVisibility(View.GONE);
                    getListView().setEmptyView(findViewById(android.R.id.empty));
                }

                mSearchSuccess = false;
            }
            updateIndexer(cursor);
        }

        private SectionIndexer getNewIndexer(Cursor cursor) {
            //modify by dory.zheng for MMS to contacts bug begin
//            return new AlphabetIndexer(cursor, SUMMARY_NAME_COLUMN_INDEX, mAlphabet);
            return new AlphabetIndexer(cursor, SUMMARY_SORT_KEY_PRIMARY_INDEX, mAlphabet);
            //modify by dory.zheng for MMS to contacts bug end
        }
        private void updateIndexer(Cursor cursor) {
            if (mIndexer == null) {
                mIndexer = getNewIndexer(cursor);
            } else {
                if (Locale.getDefault().equals(Locale.JAPAN)) {
                        mIndexer = getNewIndexer(cursor);
                } else {
                	Log.v(TAG, "!Locale.getDefault().equals(Locale.JAPAN)");
                    if (mIndexer instanceof AlphabetIndexer) {
                        ((AlphabetIndexer)mIndexer).setCursor(cursor);
                    } else {
                    	Log.v(TAG, "!mIndexer instanceof AlphabetIndexer");
                        mIndexer = getNewIndexer(cursor);
                    }
                }
            }
        }
        public Object [] getSections() {
        	return mIndexer.getSections();
        }

        public int getPositionForSection(int sectionIndex) {
            if (mIndexer == null) {
                Cursor cursor = mAdapter.getCursor();
                if (cursor == null) {
                    // No cursor, the section doesn't exist so just return 0
                    return 0;
                }
                mIndexer = getNewIndexer(cursor);
            }
            return mIndexer.getPositionForSection(sectionIndex);
        }

		public int getSectionForPosition(int position) {
			// TODO Auto-generated method stub
			return 0;
		}
    }

	class Usimphonestruct {
		String name;
		String phonenumber;
		String[] anr = new String[]{"", "", ""};
		String email;
	}
   /**
     *safe exit thread
     * @param thread
     */
    private void SaflyExitThread(Thread thread) {
        if (thread != null) {
            int attempts = 0;
            while (thread.isAlive() && attempts < 10) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    // Keep on going until max attempts is reached.
                }
                attempts++;
            }
            if (thread.isAlive()) {
                // Find out why the thread did not exit in a timely
                // fashion. Last resort: increase the sleep duration
                // and/or the number of attempts.
                Log.e(TAG, "Thread is still alive after max attempts.");
            }
            thread = null;
        }
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                Log.d(TAG, "incoming call when export contacts to SIM, and stop export.");
                mOperation.setState(false);
            }
        };
    };

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        onSearchTextChanged();
    }

    protected void onSearchTextChanged() {
        Filter filter = mAdapter.getFilter();
        filter.filter(getTextFilter());
    }

    private String getTextFilter() {
        if (mSearchView != null) {
            return mSearchView.getText().toString();
        }
        return null;
    }

    Cursor doFilter(String filter) {

        String andWhere = "";
        if(Config.isMSMS){
            if(!CommonUtil.isSimCardReady(0, false, this)){
                if (!CommonUtil.isSimCardReady(1, false, this)){
                    andWhere = " and sim_index = 0";
                }
            }
        } else {
            if(TelephonyManager.SIM_STATE_READY != ((TelephonyManager)
                    getSystemService(Context.TELEPHONY_SERVICE)).getSimState()){
                andWhere = " and sim_index = 0";
            }
        }
        Log.d(TAG, "start Query contacts, mGroup is " + mGroup);

        final ContentResolver resolver = getContentResolver();
        Uri phoneOrEmailUri = Uri.parse("content://" + "com.android.contacts" + "/phone_or_email");
        String where = Data.MIMETYPE + " in ('" + Phone.CONTENT_ITEM_TYPE + "','" + Email.CONTENT_ITEM_TYPE + "') and " + RawContacts.DELETED + " <> 1 ";
        mSearchSuccess = true;
        switch(mGroup) {
            case GROUP_ALL:
                if(mMode == MODE_PICK){
                    return resolver.query(
                            phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + andWhere + " and data.data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')",
                            null,
                            getSortOrder());
                } else if(mMode == MODE_REMOVE_FREQUENT_CONTACTS) {
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            getContactSelection() + " AND " + Contacts.TIMES_CONTACTED + " > 0",
                            null,
                            getSortOrder());
                } else if(mMode == MODE_REMOVE_COLLECTION_CONTACTS) {
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            getContactSelection() + " AND starred=1",
                            null,
                            getSortOrder());
                } else{
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            getContactSelection(),
                            null,
                            getSortOrder());
                }
            case GROUP_PHONE:
                if(mMode == MODE_PICK){
                    return resolver.query(
                            phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + " and sim_index = 0 and data.data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')",
                            null,
                            getSortOrder());
                } else if(mMode == MODE_ADD_CONTACTS_GROUP){
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index=0 AND " + getContactSelection() + " and (divided_group_name_id <>" + mContactsGroupNameId + " or divided_group_name_id is NULL)",
                            null,
                            getSortOrder());
                } else{
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index=0 AND " + getContactSelection(),
                            null,
                            getSortOrder());
                }
            case GROUP_SIM:
                if(mMode == MODE_PICK){
                    return resolver.query(
                            phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + andWhere + " and sim_index <> 0 and data.data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')",
                            null,
                            getSortOrder());
                }else{
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index<>0 AND " + getContactSelection(),
                            null,
                            getSortOrder());
                }
            case GROUP_SIM1:
                if(mMode == MODE_PICK){
                    return resolver.query(
                            phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index <> 0 and data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')" + " and account_name='" + Account.SIM1_ACCOUNT_NAME+"'",
                            null,
                            getSortOrder());
                }else{
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index<>0 and account_name='" + Account.SIM1_ACCOUNT_NAME + "' AND " + getContactSelection(),
                            null,
                            getSortOrder());
                }
            case GROUP_SIM2:
                if(mMode == MODE_PICK){
                    return resolver.query(
                            phoneOrEmailUri,
                            CONTACTS_PHONE_OR_EMAIL,
                            where + "and sim_index <> 0 and data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')" + " and account_name='" + Account.SIM2_ACCOUNT_NAME+"'",
                            null,
                            getSortOrder());
                }else{
                    return resolver.query(
                            getContactAndPhoneFilterUri(filter),
                            CONTACTS_SUMMARY_PROJECTION,
                            "sim_index<>0 and account_name='" + Account.SIM2_ACCOUNT_NAME + "' AND " + getContactSelection(),
                            null,
                            getSortOrder());
                }
            case GROUP_CUSTOM:
                if(mMode == MODE_PICK){
                    if(groupNameId != -1){
                        if(mBulkAction == MODE_BULK_MAIL) {
                            where = Data.MIMETYPE + " in ('" + Email.CONTENT_ITEM_TYPE + "') and " + RawContacts.DELETED + " <> 1 ";
                            return resolver.query(
                                    phoneOrEmailUri,
                                    CONTACTS_PHONE_OR_EMAIL,
                                    where + "and data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')" + " and divided_group_name_id =" + groupNameId + andWhere,
                                    null,
                                    getSortOrder());
                        } else {
                            return resolver.query(
                                    phoneOrEmailUri,
                                    CONTACTS_PHONE_OR_EMAIL,
                                    where + "and data1 <> '' and (data.data1 LIKE '" + filter + "%' or " + Contacts.DISPLAY_NAME + " LIKE '" + filter + "%')" + " and divided_group_name_id =" + groupNameId + andWhere,
                                    null,
                                    getSortOrder());
                        }
                    }
                }
                break;
        }
        return null;
    }

    private Uri getContactAndPhoneFilterUri(String filter) {
        Uri baseUri = null;
        if (!TextUtils.isEmpty(filter)) {
            if (isPhoneNumberOnly(filter)) {
                baseUri = Uri.withAppendedPath(Contacts.CONTENT_URI, "search/" + Uri.encode(filter));
            } else {
                baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI,Uri.encode(filter));
            }
        } else {
            baseUri = Contacts.CONTENT_URI;
        }
        return baseUri;
    }

    private boolean isPhoneNumberOnly(String str) {
        if (!TextUtils.isEmpty(str)) {
            final int len = str.length();
            for (int i = 0; i < len; i++) {
                char ch = str.charAt(i);
                if (ch < '0' || ch > '9') {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
