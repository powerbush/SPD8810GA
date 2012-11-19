package com.android.mms.transaction;

import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneFactory;
import com.android.mms.MmsApp;
import com.android.mms.ui.MessageUtils;

/**
 * 11-10-21
 * @author luning
 *
 */
public class FDNUtils {

	public final static String FDN_DISABLE = "FDN_DISABLE";
	private final Uri fdnUri = Uri.parse("content://icc/fdn");
	private final Uri fdnUri1 = Uri.parse("content://icc1/fdn");
	private static ArrayList<String> fdnNumbers = new ArrayList<String>();
	//private static FDNUtils fdnInfo;
	private int mPhoneId;

	private static FDNUtils[] fdnInfo;
	static {
		fdnInfo = new FDNUtils[PhoneFactory.getPhoneCount()];
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
        	fdnInfo[i] = new FDNUtils(i);
        }
    }
	private FDNUtils(int phoneID){
		mPhoneId = phoneID;
	}

	public static FDNUtils getInstance(){
		return getInstance(PhoneFactory.getDefaultPhoneId());
	}

	public static FDNUtils getInstance(int phoneId){
		return fdnInfo[phoneId];
	}


	public void queryFDN(Context context) {
		Cursor cur = null;
		fdnNumbers.clear();
		try {
			do {
				Uri tempUri = fdnUri;
				if(mPhoneId > 0){
					tempUri = fdnUri1;
				}
				cur = context.getContentResolver().query(tempUri, new String[] { "name",
						"number" }, null, null, null);
				if (null != cur && 0 == cur.getCount()) {
					cur.close();
					break;
				}
				if (null != cur && 0 < cur.getCount()) {

					if (cur.moveToFirst()) {
						do {
							String number = cur.getString(1);
							fdnNumbers.add(number);
						} while (cur.moveToNext());
						cur.close();
						break;
					}
				}
			} while (true);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (null != cur && !cur.isClosed()) {
				cur.close();
			}
		}
	}

	public  boolean isFDNDisable(String number) {
		if (PhoneNumberUtils.isEmergencyNumber(number)) {
			return false;
		}
		return !isFoundFdn(number);
	}

	private  boolean isFoundFdn(String number) {
		int index = -1;
		int count = 1;
		// cr118635 begin
		if ((number.startsWith("**052")) && number.endsWith("#")) {
			return true;
		}
		if ((number.startsWith("**042")) && number.endsWith("#")) {
			return true;
		}
		// cr118635 end
		if (fdnNumbers.size() == 0) {
			return false;
		}
		for (Iterator<String> it = fdnNumbers.iterator(); it.hasNext();) {
			if (number.equals(it.next())) {
				index = count;
				break;
			}
			count++;
		}
		if (index > 0) {
			return true;
		}
		return false;
	}

	public boolean isSmscDisable() {
		if (MmsApp.getApplication().getTelephonyManager(mPhoneId).hasIccCard()) {
			ITelephony iTelephony = null;
			if(MessageUtils.isMSMS){
				iTelephony =ITelephony.Stub.asInterface(
					    ServiceManager.getService(
						PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,mPhoneId)));
			}else{
				iTelephony = ITelephony.Stub.asInterface(ServiceManager
						.getService(Context.TELEPHONY_SERVICE));
			}
			String smsc = null;
			if (null != iTelephony) {
				try {
					smsc = iTelephony.getSmsc();
				} catch (RemoteException e) {
					smsc = null;
					e.printStackTrace();
				}
			}
			if (null != smsc) {
				return !isFoundFdn(smsc);
			}
		}
		return true;
	}
}
