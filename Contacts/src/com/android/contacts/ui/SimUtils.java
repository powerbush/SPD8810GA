package com.android.contacts.ui;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.PinYin;
import com.android.contacts.R;
import com.android.contacts.RecentCallsListActivity;
import com.android.contacts.util.CommonUtil;
import com.android.contacts.util.Config;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.PhoneFactory;

public class SimUtils {
    private static final String LOG_TAG = "SimUtils";

    protected static final int NAME_COLUMN = 0;

    protected static final int NUMBER_COLUMN = 1;

    protected static final int EMAILS_COLUMN = 2;

    private static final String ACCOUNT_TYPE_GOOGLE = "com.google";

    private static final String GOOGLE_MY_CONTACTS_GROUP = "System Group: My Contacts";

    private static final ContentValues sEmptyContentValues = new ContentValues();
	private static final int [] SIM_NAME_LENGTH = { 0, 0};
	private static final int[] SIM_LENGTH = { -1, -1 };
	private static final int[] USIM_EMAIL_LENGTH = {0, 0};

    public static final Uri SIM_URI = Uri.parse("content://icc/adn");

    public static final Uri SIM1_URI = Uri.parse("content://icc0/adn");

    public static final Uri SIM2_URI = Uri.parse("content://icc1/adn");

    private SimUtils() {
    };

    /**
     * show toast.
     * 
     * @param context
     * @param message
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    /*
     * @param context
     * @param uri
     * @param ArrayList<ContactEntity>
     */
    public static void querySimContact(Context context, Uri uri,
            ArrayList<HashMap<String, String>> simContactsList) {
        Cursor cur = null;
        String[] projection = new String[] {
                "name", "number", "anr", "email"
        };
        try {
            TelephonyManager t = (TelephonyManager) context.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, 0));
            TelephonyManager t2 = (TelephonyManager) context.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, 1));
            final boolean hasSim1 = (null != t) ? t.hasIccCard() : false;
            final boolean hasSim2 = (null != t2) ? t2.hasIccCard() : false;
            // add sim card state judge by phone_03 start
            boolean isStandby1 = Settings.System.getInt(context.getContentResolver(),
                    PhoneFactory.getSetting(Settings.System.SIM_STANDBY, 0), 1) == 1;
            boolean isStandby2 = Settings.System.getInt(context.getContentResolver(),
                    PhoneFactory.getSetting(Settings.System.SIM_STANDBY, 1), 1) == 1;
            // add sim card state judge by phone_03 end
            if (hasSim1 && isStandby1 && uri.equals(SimUtils.SIM1_URI)) {
                Log.i(LOG_TAG, "querySimContact : ...... query one start");
                cur = context.getContentResolver().query(uri, projection, null, null, null); // modify
                // by
                // dory.zheng
                // for
                // usim
                Log.i(LOG_TAG, "querySimContact : ...... query one end");
                if (null != cur && 0 == cur.getCount()) {
                    cur.close();
                    cur = null;
                    return;
                }
                if (null != cur && 0 < cur.getCount()) {
                    if (cur.moveToFirst()) {
                        do {
                            HashMap<String, String> oneRecord = new HashMap<String, String>();
                            String number = cur.getString(1);
                            if (null == number)
                                continue;
                            String name = (null != cur.getString(0)) ? cur.getString(0) : number;
                            // modify by dory.zheng for usim begin
                            String anr = cur.getString(cur.getColumnIndex("anr"));
                            String email = cur.getString(cur.getColumnIndex("email"));
                            // modify by dory.zheng for usim end
                            oneRecord.put("name", name);
                            oneRecord.put("number", number);
                            // modify by dory.zheng for usim begin
                            oneRecord.put("anr", anr);
                            oneRecord.put("email", email);
                            // modify by dory.zheng for usim end
                            simContactsList.add(oneRecord);
                        } while (cur.moveToNext());
                        cur.close();
                        cur = null;
                        return;
                    }
                }
            }
            if (hasSim2 && isStandby2 && uri.equals(SimUtils.SIM2_URI)) {
                Log.i("querySimContact :", " ...... query two start");
                cur = context.getContentResolver().query(uri, projection, null, null, null); // modify
                // by
                // dory.zheng
                // for
                // usim
                Log.i("querySimContact :", " ...... query two start");
                if (null != cur && 0 == cur.getCount()) {
                    cur.close();
                    cur = null;
                    return;
                }
                if (null != cur && 0 < cur.getCount()) {

                    if (cur.moveToFirst()) {
                        do {
                            HashMap<String, String> oneRecord = new HashMap<String, String>();
                            String number = cur.getString(1);
                            if (null == number)
                                continue;
                            String name = (null != cur.getString(0)) ? cur.getString(0) : number;
                            // modify by dory.zheng for usim begin
                            String anr = cur.getString(cur.getColumnIndex("anr"));
                            String email = cur.getString(cur.getColumnIndex("email"));
                            // modify by dory.zheng for usim end
                            oneRecord.put("name", name);
                            oneRecord.put("number", number);
                            // modify by dory.zheng for usim begin
                            oneRecord.put("anr", anr);
                            oneRecord.put("email", email);
                            // modify by dory.zheng for usim end
                            simContactsList.add(oneRecord);
                        } while (cur.moveToNext());
                        cur.close();
                        cur = null;
                        return;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != cur && !cur.isClosed()) {
                cur.close();
                cur = null;
            }
        }
    }

    public static void newPhoneContact(Account account, final ContentResolver resolver,
            String name, String number) {
        final NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(name);
        final String realname = namePhoneTypePair.name;
        final int phoneType = namePhoneTypePair.phoneType;
        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder = ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI);
        String myGroupsId = null;
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);

            // TODO: temporal fix for "My Groups" issue. Need to be refactored.
            if (ACCOUNT_TYPE_GOOGLE.equals(account.type)) {
                final Cursor tmpCursor = resolver.query(Groups.CONTENT_URI, new String[] {
                    Groups.SOURCE_ID
                }, Groups.TITLE + "=?", new String[] {
                    GOOGLE_MY_CONTACTS_GROUP
                }, null);
                try {
                    if (tmpCursor != null && tmpCursor.moveToFirst()) {
                        myGroupsId = tmpCursor.getString(0);
                    }
                } finally {
                    if (tmpCursor != null) {
                        tmpCursor.close();
                    }
                }
            }
        } else {
            builder.withValues(sEmptyContentValues);
        }
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, realname);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, phoneType);
        builder.withValue(Phone.NUMBER, number);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (myGroupsId != null) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(GroupMembership.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
            builder.withValue(GroupMembership.GROUP_SOURCE_ID, myGroupsId);
            operationList.add(builder.build());
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private static class NamePhoneTypePair {
        final String name;

        final int phoneType;

        public NamePhoneTypePair(String nameWithPhoneType) {
            // Look for /W /H /M or /O at the end of the name signifying the
            // type
            int nameLen = nameWithPhoneType.length();
            if (nameLen - 2 >= 0 && nameWithPhoneType.charAt(nameLen - 2) == '/') {
                char c = Character.toUpperCase(nameWithPhoneType.charAt(nameLen - 1));
                if (c == 'W') {
                    phoneType = Phone.TYPE_WORK;
                } else if (c == 'M' || c == 'O') {
                    phoneType = Phone.TYPE_MOBILE;
                } else if (c == 'H') {
                    phoneType = Phone.TYPE_HOME;
                } else {
                    phoneType = Phone.TYPE_OTHER;
                }
                name = nameWithPhoneType.substring(0, nameLen - 2);
            } else {
                phoneType = Phone.TYPE_OTHER;
                name = nameWithPhoneType;
            }
        }
    }

	public static String changeName(String name, int phoneId) {
	    int NmLen = getSimContactorNameLength(phoneId);
        while(true){
            byte[] bytes = getSimRecordBytes(name);
            if (bytes != null && bytes.length > NmLen) {
                int end = name.length() > 1 ? name.length() - 1 : 0;
                name = name.substring(0, end);
            } else {
                break;
            }
        }
        return name;
    }

    public static String changeEmail(String email, int phoneId){
        int emailRecordSize = getSimContactEmailLength(phoneId);
        byte[] bytes = getSimRecordBytes(email);
        while(bytes != null && bytes.length > emailRecordSize){
            int end = email.length() > 1 ? email.length() - 1 : 0;
            email = email.substring(0, end);
            bytes = getSimRecordBytes(email);
        }
        return email;
    }

    /**
     * Get capacity of SIM card
     * 
     * @param phoneId
     * @return
     */
    public static int getSimCardLength(int phoneId) {
        if (SIM_LENGTH[phoneId] < 1) {
            try {
                IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
                if (iccIpb != null) {

					int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
					int size = -1;
					if(sizes != null){
					    if (sizes.length == 3){
					        size = sizes[2];
					    } else if(sizes.length == 2){
					        size = sizes[1] / sizes[0];
					    }
					}
					SIM_LENGTH[phoneId] = size;
				}
			} catch (RemoteException ex) {
				// ignore it
			} catch (SecurityException ex) {
				// ignore it
			}
		}
		return SIM_LENGTH[phoneId];
	}

	/**
	 * Get number of contactor in Sim card
	 * 
	 * @param context
	 * @param phoneId
	 * @return
	 */
	public static int getSimContactorNum(Context context, int phoneId) {
		Uri uri = Config.isMSMS ? SimUtils.SIM1_URI : SimUtils.SIM_URI;
		if (phoneId == 1) {
			uri = SimUtils.SIM2_URI;
		}
		Cursor cur = null;
		try {
			cur = context.getContentResolver().query(uri,
					new String[] { "name", "number", "anr", "email"}, null, null, null); //modify by dory.zheng for usim 
			return null == cur ? -1 : cur.getCount();
		} finally {
			if(cur != null){
				cur.close();
				cur = null;
			}
		}
	}

	/**
	 * Get remain size in sim card
	 * 
	 * @param context
	 * @param phoneId
	 * @return
	 */
	public static int getSimRemain(Context context, int phoneId) {
		int total = getSimCardLength(phoneId);
		int ContactorNum = getSimContactorNum(context, phoneId);
		int remain = total - ContactorNum;
		return remain < 0 ? -1 : remain;
	}
	
	public static int getSimContactorNameLength(int phoneId){
	    if (SIM_NAME_LENGTH[phoneId] < 1) {
            try {
                IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
                if (iccIpb != null) {
                    int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                    int size = -1;
                    if(sizes != null){
                        size = sizes[0] - 14;
                        if(size < 0){
                        	//get length of sim contactor's Name fail
                            return 12;
                        }
                    } else {
                        return 12;
                    }
                    SIM_NAME_LENGTH[phoneId] = size - 1;
                } else {
                    return 12;
                }
            } catch (RemoteException ex) {
                return 12;
            } catch (SecurityException ex) {
                return 12;
            }
        }
        return SIM_NAME_LENGTH[phoneId];
    }

    private static IIccPhoneBook getIccPhoneBook(int phoneId){
        return IIccPhoneBook.Stub.asInterface(ServiceManager
                .getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
    }

    public static int getSimContactEmailLength(int phoneId){
        if(USIM_EMAIL_LENGTH[phoneId] <= 0){
            int[] sizes = null;
            IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
            if (iccIpb != null) {
                try {
                    sizes = iccIpb.getEmailRecordsSize();
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "get email record size fial", e);
                }
            }
            int size = 0;
            if(sizes != null){
                size = sizes[0] - 2;
            }
            USIM_EMAIL_LENGTH[phoneId] = size > 0 ? size : 0;
        }
        return USIM_EMAIL_LENGTH[phoneId];
    }

    public static byte[] getSimRecordBytes(String record){
        byte[] bytes = null;
        if (record == null) {
            record = "";
        }
        try {
            bytes = GsmAlphabet.isAsciiStringToGsm8BitUnpackedField(record);
        } catch (EncodeException e) {
            // TODO Auto-generated catch block
            try {
                bytes = record.getBytes("utf-16be");
            } catch (UnsupportedEncodingException e1) {
                // TODO Auto-generated catch block
                Log.w(LOG_TAG, "record convert byte exception : " + record,e1);
            }
        }
        return bytes;
	}
    
    public static String getDisplayNameFromPhoneNumber(String number){
        if (number != null){
            return number.replace(";", "W").replace(",", "P");
        } else {
            return "";
        }
    }

    /**
     * add by dory.zheng for splitString
     * 
     * @param source
     * @return
     */
    public static String[] splitString(String source) {
        String[] result = new String[3];
        if (source == null || source.equals("")) {
            return result;
        }
        Integer pos;
        String strSource = source;
        Integer times = 0;
        //while ((pos = strSource.indexOf(AdnRecord.ANR_SPLIT_FLG)) >= 0) {
        while ((pos = strSource.indexOf(":")) >= 0) {
            result[times] = strSource.substring(0, pos);
            strSource = strSource.substring(pos + 1);
            times++;
        }
        if (strSource.length() > 0) {
            result[times] = strSource;
        }
    
        return result;
    }

    public static  String getFistAnrNumber(String anr) {
        if (anr != null) {
            //String[] anrStr = anr.split(AdnRecord.ANR_SPLIT_FLG);
            String[] anrStr = anr.split(":");
            for (String anrNumber : anrStr) {
                if (!TextUtils.isEmpty(anrNumber)) {
                    return anrNumber;
                }
            }
        }
        return null;
    }

    /**
     * check sim card state and fdn state and and state
     * @param context
     * @param justCheckSimStateReady flag for just check sim state is ready or not
     * @param phoneId not -1 if phone is dual sim
     * @return true if sim state is ready and fdn is not edable and adn has ready, else false
     */
    public static boolean checkSimState(Context context, boolean justCheckSimStateReady, int phoneId) {
        final Context ctx = context;
        TelephonyManager tm = null;
        context = null;

        if (-1 != phoneId) {
            tm = (TelephonyManager) ctx.getSystemService(PhoneFactory.getServiceName(
                    Context.TELEPHONY_SERVICE, phoneId));
        } else {
            tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        }

        int simState = tm.getSimState();
        int adnCacheState = tm.getAdnCachestate();
        if (TelephonyManager.SIM_STATE_READY == simState) {
            Log.v(LOG_TAG, "sim ready");

            if (justCheckSimStateReady) {
                return true;
            }

            if (CommonUtil.isFdnEnable(ctx)) {
                Log.d(LOG_TAG, "FDN has enable");
                return false;
            }

            if (adnCacheState == Constants.ADNCACHE_STATE_NOT_READY) {
                Log.v(LOG_TAG, "adn cache not ready");
                return false;
            }
            return true;
        } else {
            Log.v(LOG_TAG, "sim state not ready");
            return false;
        }
    }
}
