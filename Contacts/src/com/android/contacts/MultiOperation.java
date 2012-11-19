package com.android.contacts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.RemoteException;
import android.pim.vcard.VCardComposer;
import android.pim.vcard.VCardConfig;
import android.telephony.TelephonyManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.Log;
import com.android.contacts.util.CommonUtil;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.AdnRecord;

import com.android.internal.telephony.PhoneFactory;
import com.android.contacts.util.Config;
import com.android.contacts.ui.SimUtils;

public class MultiOperation{

	private static final String TAG = "MultiOperation";

	private static final int MODE_DELETE = 1;
    private static final int MODE_EXPORT = 2;
    private static final int MODE_IMPORT = 3;
    private static final int MODE_ADD_CONTACTS_GROUP = 9;
    private static final int MODE_REMOVE_COLLECTION_CONTACTS = 10;
    private static final int MODE_REMOVE_FREQUENT_CONTACTS = 11;

    private static final int MODE_SDCARD_EXPORT = 6;
    protected Uri mLookupUri;
    static final String NAME_COLUMN = Contacts.DISPLAY_NAME;
    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID, // 0
        Contacts.DISPLAY_NAME, // 1
        Contacts.HAS_PHONE_NUMBER, //2
        Contacts.LOOKUP_KEY, //3
        RawContacts.SIM_INDEX,	//4
        Contacts.PHOTO_ID,	//5
    };
    static final int SUMMARY_ID_COLUMN_INDEX = 0;
    static final int SUMMARY_NAME_COLUMN_INDEX = 1;
    static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 2;
    static final int SUMMARY_LOOKUP_KEY = 3;
    static final int SUMMARY_SIM_COLUMN_INDEX = 4;
    static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 5;
	private ArrayList<ContentValues> mSelectContacts = new ArrayList<ContentValues>();
    public final int[] NUMBERTYPE = {Phone.TYPE_MOBILE,Phone.TYPE_WORK,Phone.TYPE_OTHER};// add by zhengshenglan at 08-24

	private final String PROPERTY_ICC_OPERATOR_NUMERIC = "gsm.sim.operator.numeric";
    private boolean mState = false;
    private int mMax;
    private ContactThread mThread;
    private int mMode;
    private final Context mContext;
    // the id of the contacts group.
    private int mContactsGroupNameId = -1;
    private String mContactsGroupRingtone = null;
    private final Handler mHandler;
    public MultiOperation(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    //added for dual sim
    String mAccountName;
    
    private class ContactThread extends Thread
    {
        @Override
        public void run() {
            try {
                switch (mMode) {
                case MODE_DELETE:
                    deleteContacts();
                    break;
                case MODE_EXPORT:
                    exportContact();
                    break;
                case MODE_IMPORT:
                    importContact();
                    break;
                case MODE_ADD_CONTACTS_GROUP:
                    addContactsToGroup();
                    break;
                case MODE_SDCARD_EXPORT:
                    exportContactToSdcard();
                    break;
                case MODE_REMOVE_FREQUENT_CONTACTS:
                    removeFrequentContacts();
                    break;
                case MODE_REMOVE_COLLECTION_CONTACTS:
                    removeCollectionContacts();
                    break;
                default:
                    break;
                }

            } catch (Exception e) {
                Log.v(TAG, "operation exception");
            }finally{
                mHandler.sendEmptyMessage(0x11);
                setState(false);
                if (null != mSelectContacts) {
                    mSelectContacts.clear();
                    mSelectContacts = null;
                }
            }
        }
    }

    public int getMax() {
        return mMax;
    }

    public boolean getState(){
        return mState;
    }

    public void setState(boolean state){
        mState = state;
    }

    public int getCurrentMode(){
        return mMode;
    }

    protected void start(ArrayList<ContentValues> selectList, int mode, int groupNameId, String accountName, String groupRingtone) { //we may get account_name from this!
        mSelectContacts = selectList;
        selectList = null;
        mMode = mode;
        mMax = mSelectContacts.size();
        // Adding the contacts to the contacts group.
        if(mMode == MODE_ADD_CONTACTS_GROUP) {
            mContactsGroupNameId = groupNameId;
            mContactsGroupRingtone = groupRingtone;
        }
        if (mThread != null && mThread.isAlive()) {
            try {
                mThread.interrupt();
            } catch (Exception e){
                Log.d(TAG, "interupt thread error ", e);
            }
        }
        setState(true);
        mThread=new ContactThread();
        mThread.start();
        mAccountName=accountName;
    }
    
    protected void start(ArrayList<ContentValues> selectList, int mode, int groupNameId, String groupRingtone) { 
        start(selectList, mode, groupNameId,"", groupRingtone);
    }
    
    protected Uri getSimUri() {
        return  Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "sim_contacts");
    }

    /**
     * add by dory.zheng for export contacts to sd card vcf the name is Contacts.vcf
     * Tries to get an appropriate filename. Returns null if it fails.
     */
    private String getAppropriateFileName(final String destDirectory) {
        final Resources resources = mContext.getResources();
        int mFileIndexMaximum = resources.getInteger(R.integer.config_export_file_max_index);
        String mFileNamePrefix = mContext.getString(R.string.config_export_file_prefix);
        String mFileNameSuffix = mContext.getString(R.string.config_export_file_suffix);
        String mFileNameExtension = mContext.getString(R.string.config_export_file_extension);
        int mFileIndexMinimum = resources.getInteger(R.integer.config_export_file_min_index);
        Set<String> mExtensionsToConsider = new HashSet<String>();
        mExtensionsToConsider.add(mFileNameExtension);

        final String additionalExtensions = mContext.getString(
                R.string.config_export_extensions_to_consider);
        if (!TextUtils.isEmpty(additionalExtensions)) {
            for (String extension : additionalExtensions.split(",")) {
                String trimed = extension.trim();
                if (trimed.length() > 0) {
                    mExtensionsToConsider.add(trimed);
                }
            }
        }
        int fileNumberStringLength = 0;
        {
            // Calling Math.Log10() is costly.
            int tmp;
            for (fileNumberStringLength = 0, tmp = mFileIndexMaximum; tmp > 0;
                fileNumberStringLength++, tmp /= 10) {
            }
        }
        String bodyFormat = "%s%0" + fileNumberStringLength + "d%s";

        String possibleBody = String.format(bodyFormat,mFileNamePrefix, 1, mFileNameSuffix);
        if (possibleBody.length() > 8 || mFileNameExtension.length() > 3) {
            Log.e(TAG, "This code does not allow any long file name.");
//            mErrorReason = getString(R.string.fail_reason_too_long_filename,
//                    String.format("%s.%s", possibleBody, mFileNameExtension));
            sendToastMessage(mContext.getString(R.string.exporting_contact_failed_title));
//            showDialog(R.id.dialog_fail_to_export_with_reason);
            // finish() is called via the error dialog. Do not call the method here.
            return null;
        }

        // Note that this logic assumes that the target directory is case insensitive.
        // As of 2009-07-16, it is true since the external storage is only sdcard, and
        // it is formated as FAT/VFAT.
        // TODO: fix this.
        for (int i = mFileIndexMinimum; i <= mFileIndexMaximum; i++) {
            boolean numberIsAvailable = true;
            // SD Association's specification seems to require this feature, though we cannot
            // have the specification since it is proprietary...
            String body = null;
            for (String possibleExtension : mExtensionsToConsider) {
                body = String.format(bodyFormat, mFileNamePrefix, i, mFileNameSuffix);
                File file = new File(String.format("%s/%s.%s",
                        destDirectory, body, possibleExtension));
                if (file.exists()) {
                    numberIsAvailable = false;
                    break;
                }
            }
            if (numberIsAvailable) {
                return String.format("%s/%s.%s", destDirectory, body, mFileNameExtension);
            }
        }
        sendToastMessage(mContext.getString(R.string.fail_reason_too_many_vcard));
        return null;
    }

    private StringBuilder mSelectItems;
    private int exportSize;
	private void exportContactToSdcard() {
		// TODO Auto-generated method stub
		int success_count = 0;
		File SdCardDir = Environment.getExternalStorageDirectory();//
		//modify by dory.zheng for export contacts to sd card vcf the name is Contacts.vcf begin
		String fileName = getAppropriateFileName(SdCardDir.toString());
		Log.d(TAG, "fileName == " + fileName);
		if(TextUtils.isEmpty(fileName)){
		    return;
		}
		if(SdCardDir.exists()){
		    File dir= new File(fileName);
			dir.delete();
		}
		VCardComposer composer = null;
		mSelectItems = new StringBuilder();
		for(int k = 0; k < mSelectContacts.size(); k++){
            mSelectItems.append(mSelectContacts.get(k).getAsString(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX]));
            if (k < mSelectContacts.size() - 1)
                mSelectItems.append(",");

		}
        try {
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(fileName);
            } catch (FileNotFoundException e) {
                return;
            }
            try {
                int vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
                composer = new VCardComposer(mContext, vcardType, true);

                composer.addHandler(composer.new HandlerForOutputStream(
                        outputStream));
                if (!composer.init("_id in (" + mSelectItems + ")", null)) {
                    return;
                }
                exportSize = composer.getCount();
                Log.i(TAG, "count of export to sdcard " + exportSize);
                if (exportSize == 0) {
                    return;
                }

                while (!composer.isAfterLast()) {
                    if (!getState()) {
                        break;
                    }
                    if (!composer.createOneEntry()) {
                        break;
                    }
                    success_count++;
                    sendIncrementMessage(1);
                }
            } catch (Exception ex) {

            }
        } finally {
            if (composer != null) {
                composer.terminate();
            }
        }
		String message = success_count + " " + mContext.getString(R.string.label_success)
		        + ", " + (mSelectContacts.size() - success_count) + " "
		        + mContext.getString(R.string.label_fail);
		sendToastMessage(message);
	}

	//modify by zhengshenglan for NEWMS00118626 at 08-28 begin
	private void exportContact(){
	    int success_count = 0;
	    int selectedCount = mSelectContacts.size();
	    try {
	        mContext.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_START"));
	        for(int i=0; i<selectedCount;i++){
	            if(!getState()){
	                break;
	            }
	            try {
	                if(mContext.getContentResolver()
	                        .insert(getSimUri(), mSelectContacts.get(i)) != null){
	                    success_count++;
	                    sendIncrementMessage(1);
	                }
	                else{
	                    //check the sim contacts count? it may not correct when you do something about exporting!
	                }
	            } catch (Exception e) {
	                Log.w(TAG, "insert exception", e);
	            }
	        }
	    } finally {
	        mContext.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_END"));
	    }
	    //modify by dory.zheng for export to sim empty record begin
	    String message = "";
	    if(MultiOperateListActivity.isOnlyEmail){
	        message = mContext.getString(R.string.exprot_fail_only_email) + ", ";
	        MultiOperateListActivity.isOnlyEmail = false;
	    }
	    message += success_count + " " + mContext.getString(R.string.label_success)
	    + ", " + (selectedCount - success_count) + " "
	    + mContext.getString(R.string.label_fail);

	    if(selectedCount - success_count >0){

	        int remainRecord = 0;
	        if( Account.SIM2_ACCOUNT_NAME.equals(mAccountName)){
	            remainRecord = SimUtils.getSimRemain(mContext, 1);
	        }
	        else{
	            remainRecord = SimUtils.getSimRemain(mContext, 0);
	        }
	        if(remainRecord<=0){
	            message+=","+mContext.getString(R.string.import_sim_too_many);
	        }
	    }
	    Log.i(TAG,"exportContact,message:"+message);
	    sendToastMessage(message);
	}

    private void sendIncrementMessage(int i){
        mHandler.obtainMessage(0x10, i, 0).sendToTarget();
    }

    private void sendToastMessage(String message) {
        mHandler.obtainMessage(0x12, message).sendToTarget();
    }

    private void importContact(){

        // check sim state
        int phoneId = -1;
        if (Account.SIM1_ACCOUNT_NAME.equals(mAccountName)) {
            phoneId = 0;
        } else if (Account.SIM2_ACCOUNT_NAME.equals(mAccountName)) {
            phoneId = 1;
        }

        if (!SimUtils.checkSimState(mContext, true, phoneId)) {
            sendToastMessage(mContext.getString(R.string.sim_no_ready));
            return;
        }

    	int success_count = 0;
    	for(int i=0; i<mSelectContacts.size();i++){
            if(!getState()){
    			break;
    		}
    		if(actuallyImportOneSimContact(mSelectContacts.get(i),
    		        mContext.getContentResolver(),null) == 0){
    		    success_count++;
    		    sendIncrementMessage(1);
    		}
    	}
    	String message = success_count + " " + mContext.getString(R.string.label_success)
                + ", " + (mSelectContacts.size() - success_count) + " "
                + mContext.getString(R.string.label_fail);
    	sendToastMessage(message);
    }
    /**
     * do batch delete phone contacts
     * @param operations
     * @param sucesss
     */
    private int doBatchDeletePhone(ArrayList<ContentProviderOperation> operations){
        int success = 0;
        if (operations.size() > 0) {
            try {
                ContentProviderResult[] result = mContext.getContentResolver()
                        .applyBatch(ContactsContract.AUTHORITY, operations);
                success = result != null ? result.length : 0;
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "e1" + e.toString(), e);
            } catch (OperationApplicationException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "e2" + e.toString(), e);
            }finally{
                if(null != operations){
                    operations.clear();
                }
            }
            sendIncrementMessage(success);
        }
        return success;
    }
	private void deleteContacts() {
		int success_count = 0;

		//split sim operate or phone contacts
		ArrayList<ContentValues> selectSimContacts = new ArrayList<ContentValues>();
		ArrayList<ContentValues> selectPhoneContacts = new ArrayList<ContentValues>();
		ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
		for(int i = 0; i < mSelectContacts.size(); i++){
			String simIndex = mSelectContacts.get(i).getAsString(CONTACTS_SUMMARY_PROJECTION[SUMMARY_SIM_COLUMN_INDEX]);
			if("0".equals(simIndex)){
				selectPhoneContacts.add(mSelectContacts.get(i));
			}else{
				selectSimContacts.add(mSelectContacts.get(i));
			}
		}
        int selectSize = selectPhoneContacts.size();;
        int operNum = selectSize / 10;
        operNum = (operNum == 0 ? 1 : (operNum > 40 ? 40 : operNum));
		for(int i = 0; i < selectSize; i++){
    		if(!getState()){
    			break;
    		}
    		long id = selectPhoneContacts.get(i).getAsLong(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX]);
            Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
            ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(uri);
            operations.add(builder.build());
            if(operNum <= operations.size()){
                success_count += doBatchDeletePhone(operations);
            }
		}
        if (getState() && operations.size() > 0) {
            success_count += doBatchDeletePhone(operations);
        }

		Log.i(TAG, "delete end----------" + System.currentTimeMillis());
 
//		String sim_oper_num = android.os.SystemProperties.get(PROPERTY_ICC_OPERATOR_NUMERIC);
//		if(sim_oper_num == null || sim_oper_num.length() == 0){
//			Log.d(TAG, "Sim not loaded");
//			sendToastMessage(mContext.getString(R.string.sim_no_ready));
//		}else{
	    try{
	        mContext.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_START"));
	        mContext.sendBroadcast(new Intent("com.android.contacts.DELETE_START")); //modify by dory.zheng for NEWMS00138007
			for(int i = 0; i < selectSimContacts.size(); i++){
	    		if(!getState()){
	    			break;
	    		}
	    		String simIndex = selectSimContacts.get(i).getAsString(CONTACTS_SUMMARY_PROJECTION[SUMMARY_SIM_COLUMN_INDEX]);
            String selection = "sim_index=? and account_name=?";
			String accountName = selectSimContacts.get(i).getAsString(RawContacts.ACCOUNT_NAME);
        	String[] selectionArgs = { simIndex, accountName };
			if(Config.isMSMS){
				Log.d(TAG, "mutil delete accountName = "+accountName);
                                 int phoneId = -1;
                                 if(accountName.equals(Account.SIM1_ACCOUNT_NAME)){
                                         phoneId = 0;
                                 }else if(accountName.equals(Account.SIM2_ACCOUNT_NAME)){
                                         phoneId = 1;
                                 }
 
                                 if(phoneId <0){
                                         Log.e(TAG, "phoneID error in menu delete ");
                                         return;
                                 }

				 final TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(
						PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, phoneId));
				    int adnCacheState = telManager.getAdnCachestate();

				    if(TelephonyManager.SIM_STATE_READY == telManager.getSimState()){
					Log.v(TAG, "sim ready:"+phoneId);
					if (adnCacheState == Constants.ADNCACHE_STATE_NOT_READY) {
						Log.v(TAG, "adn cache not ready");
						sendToastMessage(phoneId==0?mContext.getString(R.string.sim1_no_ready):mContext.getString(R.string.sim2_no_ready));
						return;
					}
				     }

                } else {
                    TelephonyManager tm = (TelephonyManager) mContext
                            .getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm.getAdnCachestate() == Constants.ADNCACHE_STATE_NOT_READY) {
                        Log.v(TAG, "adn cache not ready");
                        sendToastMessage(mContext.getString(R.string.initializing_sim_contacts));
                        break;
                    }
                }
	        	if(mContext.getContentResolver()
	        	        .delete(getSimUri(), selection, selectionArgs) > 0){
	        		success_count++;
	        	}
	            sendIncrementMessage(1);
			}
	    } finally {
	        mContext.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_END"));
	        mContext.sendBroadcast(new Intent("com.android.contacts.DELETE_END")); //modify by dory.zheng for NEWMS00138007

            // release some memory
            if (null != selectSimContacts) {
                selectSimContacts.clear();
                selectSimContacts = null;
            }
            if (null != selectPhoneContacts) {
                selectPhoneContacts.clear();
                selectPhoneContacts = null;
            }
            if (null != operations) {
                operations.clear();
                operations = null;
            }
	    }

    	String mes = success_count + " " + mContext.getString(R.string.label_success)
    	        + ", " + (mSelectContacts.size() - success_count) + " "
    	        + mContext.getString(R.string.label_fail);
    	sendToastMessage(mes);
	}

	/**
	 * add contacts to the group via the mContactsGroupNameId.
	 */
    private void addContactsToGroup() {

        int success_count = 0;
        int result = 0;

        for(int i = 0; i < mSelectContacts.size(); i++){
            if(!getState()){
                break;
            }

            long id = mSelectContacts.get(i).getAsLong(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX]);
            ContentValues contentValues = new ContentValues();
            ContentResolver resolver = mContext.getContentResolver();
            contentValues.put("divided_group_name_id", mContactsGroupNameId);
            contentValues.put("custom_group_ringtone", mContactsGroupRingtone);

            if(id != -1 && mContactsGroupNameId != -1) {
                result = resolver.update(Uri.parse("content://"+ContactsContract.AUTHORITY+"/raw_contacts"),
                        contentValues, "contact_id = ?", new String[]{String.valueOf(id)});
                Log.i(TAG,"contact_id ="+id);
                if(result > 0) {
                    success_count++;
                    sendIncrementMessage(1);
                }
            }
        }

        String mes = success_count + " " + mContext.getString(R.string.label_success)
                + ", " + (mSelectContacts.size() - success_count) + " "
                + mContext.getString(R.string.label_fail);
        sendToastMessage(mes);
    }

    /**
     * delete frequent contacts.
     */
    private void removeFrequentContacts() {

        int success_count = 0;
        int result = 0;

        for(int i = 0; i < mSelectContacts.size(); i++){
            if(!getState()){
                break;
            }

            long id = mSelectContacts.get(i).getAsLong(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX]);
            String lookupKey = mSelectContacts.get(i).getAsString(CONTACTS_SUMMARY_PROJECTION[SUMMARY_LOOKUP_KEY]);
            ContentValues values = new ContentValues(1);
            values.put(Contacts.TIMES_CONTACTED, 0);
            final Uri selectedUri = Contacts.getLookupUri(id, lookupKey);

            if(id != -1) {
                result = mContext.getContentResolver().update(selectedUri, values, null, null);
                if(result > 0) {
                    success_count++;
                    sendIncrementMessage(1);
                }
            }
        }

        String mess = success_count + " " + mContext.getString(R.string.label_success)
                + ", " + (mSelectContacts.size() - success_count)
                + " " + mContext.getString(R.string.label_fail);
        sendToastMessage(mess);
    }

    /**
     * remove the collection contacts.
     */
    private void removeCollectionContacts() {

        int success_count = 0;
        int result = 0;

        for(int i = 0; i < mSelectContacts.size(); i++){
            if(!getState()){
                break;
            }

            long id = mSelectContacts.get(i).getAsLong(CONTACTS_SUMMARY_PROJECTION[SUMMARY_ID_COLUMN_INDEX]);
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            final Uri selectedUri=ContentUris.withAppendedId(Contacts.CONTENT_URI, id);

            if(id != -1) {
                result = mContext.getContentResolver().update(selectedUri, values, null, null);
                if(result > 0) {
                    success_count++;
                    sendIncrementMessage(1);
                }
            }
        }

        String mess = success_count + " " + mContext.getString(R.string.label_success)
                + ", " + (mSelectContacts.size() - success_count)
                + " " + mContext.getString(R.string.label_fail);
        sendToastMessage(mess);
    }

	//modify by zhengshenglan at 0824 begin
	private int actuallyImportOneSimContact(
            ContentValues cv, final ContentResolver resolver, Account account) {
        String name = cv.getAsString("name");
		String phoneHome = cv.getAsString("phoneHome");
		String phoneMobile = cv.getAsString("phoneMobile");
		String phoneWork = cv.getAsString("phoneWork");
		String phoneOther = cv.getAsString("phoneOther");
		String email = cv.getAsString("email");

		Log.d(TAG, "name:"+name+";phoneHome:"+phoneHome+";phoneMobile:"+phoneMobile
				+";phoneWork:"+phoneWork+";phoneOther:"+phoneOther+";email:"+email);

		String[] otherPhoneNumber = {phoneMobile, phoneWork , phoneOther};
//		String[] otherPhoneNumber = {phoneHome, phoneWork , phoneOther};

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValues(new ContentValues());
        operationList.add(builder.build());

        //add name
        if(name != null && !"".equals(name)){
	        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
	        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
	        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
	        builder.withValue(StructuredName.GIVEN_NAME, name);
	        operationList.add(builder.build());
        }
        //add phoneMobile
        if(phoneHome != null && !"".equals(phoneHome.trim())){
	        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
	        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
	        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
	        builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
	        builder.withValue(Phone.NUMBER, phoneHome);
	//        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
	//        builder.withValue(Phone.NUMBER, phoneMobile);
	        builder.withValue(Data.IS_PRIMARY, 1);
	        operationList.add(builder.build());
        }
    	//add otherPhoneNumber
		if (otherPhoneNumber != null) {
			for(int i = 0;i<otherPhoneNumber.length;i++){
				if (otherPhoneNumber[i] != null && !"".equals(otherPhoneNumber[i].trim())) {
					builder = ContentProviderOperation
							.newInsert(Data.CONTENT_URI);
					builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
					builder.withValue(Data.MIMETYPE,
							Phone.CONTENT_ITEM_TYPE);
					builder.withValue(Phone.TYPE, NUMBERTYPE[i]);
					builder.withValue(Phone.NUMBER, otherPhoneNumber[i]);
					builder.withValue(Data.IS_PRIMARY, 1);
					operationList.add(builder.build());
					Log.d(TAG, NUMBERTYPE[i] + " = " + otherPhoneNumber[i]);
				}
			}
		}

		//add email
		if(email != null && !"".equals(email)){
			builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
			builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
			builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
			builder.withValue(Email.TYPE, Email.TYPE_HOME);
			builder.withValue(Email.DATA, email);
			builder.withValue(Data.IS_PRIMARY, 1);
			operationList.add(builder.build());
		}

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return -1;
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return -1;
        } catch (SQLiteDiskIOException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return -1;
        } finally {
            if (null != operationList) {
                operationList.clear();
            }
        }
        return 0;
    }
}
