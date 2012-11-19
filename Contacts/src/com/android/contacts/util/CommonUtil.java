package com.android.contacts.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import android.database.Cursor;
import com.android.internal.telephony.PhoneFactory;
import android.provider.Telephony;
import android.provider.Settings;
import android.provider.Settings.System;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccConstants;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.net.Uri;

import com.android.contacts.ui.SimUtils;
import com.android.contacts.R;
import android.widget.Toast;
import java.util.List;
import java.util.regex.Matcher;
/**
 * Common util method class.
 * @author phone_07
 *
 */
public class CommonUtil {
	
     private static String TAG = "CommonUtil";
    /**
     * Check intent action is or not exist. if exist return true else return
     * false.
     *
     * @param context
     * @param intentAction
     * @return if intentAction exist return true else return false.
     */
    public static boolean intentActionExist(Context context, String intentAction) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(intentAction);
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static boolean isEmailAddress(String emailAddress) {
        if (TextUtils.isEmpty(emailAddress)) {
            return false;
        }
        return Telephony.Mms.isEmailAddress(emailAddress);
    }

    public static boolean isPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }
        return phoneNumber.matches(Constants.PHONE_NUMBER_CHECK_EXPR);
    }

    public static boolean isFdnEnable(Context context) {
        final Context ctx = context;
        context = null;

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);

        if (null != tm) {
            return tm.getIccFdnEnabled();
        }
        return false;
    }
    
    public static boolean isAirplaneMode(Context context){
	  int airMode = Settings.System.getInt(context.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0);
          if(airMode == 1){
                  //it is in air plan mdoe
                  Log.d(TAG, "aire plan mode is on");
                  airPlanModeEableToast(context);
                  return true;
          }
	  return false;
    }

    public static boolean isSimCardReady(int phoneId, boolean reminder, Context context){
	final TelephonyManager telManager = (TelephonyManager)context.getSystemService(
	            PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, phoneId));
	int adnCacheState = telManager.getAdnCachestate();
	//if air plan mode is open
	if(isAirplaneMode(context)){
              Log.d(TAG, "sim not ready: aieplan mode is on");
	      return false;
	}	

	boolean isFdnEnabled = telManager.getIccFdnEnabled();
	if(isFdnEnabled){
		Log.d(TAG, "Fdn is enabled in phone " + phoneId);
		if(reminder)
		    simCardFdnEableToast(phoneId, context);
		return false;
	}		
	
	if(TelephonyManager.SIM_STATE_READY == telManager.getSimState()){
		Log.v(TAG, "sim ready:"+phoneId);
		if (adnCacheState == Constants.ADNCACHE_STATE_NOT_READY) {
              		Log.v(TAG, "adn cache not ready");
			if(reminder)
                 	    simCardReadyToast(phoneId, context);
                 	return false;
             	}
	     return true;
	 }else{
	     return false;
	 }
    }
//added for dual sim
	private static  void airPlanModeEableToast(Context context){
		Toast.makeText(context, context.getString(R.string.airplan_mode_enable),Toast.LENGTH_SHORT).show();
	}


	private static  void simCardFdnEableToast(int phoneId, Context context){
		Toast.makeText(context, phoneId==0?context.getString(R.string.sim1_fdn_enable):context.getString(R.string.sim2_fdn_enable),
                Toast.LENGTH_SHORT).show();
	} 
   private static  boolean isFdnEnableDualSim(int phoneId, Context context){
		 final TelephonyManager telManager = (TelephonyManager) context.getSystemService(
		            PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, phoneId));
		 if(telManager.getIccFdnEnabled()){
				Log.d(TAG, "phone" +phoneId + " fdn is enabled");
				simCardFdnEableToast(phoneId, context);
				return true;
			}
		 return false;
	}
    private static void simCardReadyToast(int phoneId, Context context ){
	        // Toast.makeText(this, phoneId==0?getString(R.string.sim1_no_ready):getString(R.string.sim2_no_ready),
	          //       Toast.LENGTH_SHORT).show();
	         Toast.makeText(context, phoneId==0? context.getString(R.string.sim1_no_ready):context.getString(R.string.sim2_no_ready),
	                 Toast.LENGTH_SHORT).show();
	 }


    public static int getFreeCapacity(Context context, int phoneId) {
        int total = getSimCardLength(phoneId);
        if (total == 0 || total == -1) 
            return -1;
        int ContactorNum = getSimContactorNum(context, phoneId);
        int remain = total - ContactorNum;
        Log.d(TAG, "SIM"+ phoneId +"'s Free Capacity is "+remain);
        return remain < 0 ? -1 : remain;
    }
    /**
     * Get capacity of SIM card
     *
     * @param phoneId
     * @return
     */
    private static int getSimCardLength(int phoneId) {

        int size = -1;
        try {
	    IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                                       .asInterface(ServiceManager.getService(PhoneFactory
                                                                              .getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                if(sizes != null){
                    if (sizes.length == 3){
                        size = sizes[2];
                    } else if(sizes.length == 2){
                        size = sizes[1] / sizes[0];
                    }
                }
            }
        } catch (RemoteException ex) {
            Log.d(TAG, "RemoteException: " + ex.toString());
        } catch (SecurityException ex) {
            Log.d(TAG, "SecurityException: " + ex.toString());
        }
        return size;
    }
    /**
     * Get number of contactor in Sim card
     *
     * @param context
     * @param phoneId
     * @return
     */
    private static int getSimContactorNum(Context context, int phoneId) {
        Cursor cur = null;
	//String simUri = "content://icc/adn";
	Uri simUri = SimUtils.SIM1_URI;
        //added for dual sim
        String selection = null;
        if(Config.isMSMS){
	    if(phoneId == 1)
		//simUri = "content://icc1/adn";
		simUri = SimUtils.SIM2_URI;
	    
        }
        try {
           if(Config.isMSMS){ 
	        cur = context.getContentResolver().query(simUri, null, null, null, null);
	    }
	    Log.d(TAG, "cur.getCount  = " + cur.getCount());
            return null == cur ? -1 : cur.getCount();
        } finally {
            if(cur != null){
                cur.close();
                cur = null;
            }
        }
    }



    /**
     * Hide softInputFromWindow whit an Activity
     * @param act Activity is showing current
     * @return if true hide else don't hide
     */
    public static boolean hideSoftKeyboard(Activity act) {
        final Activity activity = act;
        act = null;
        final InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (null != imm && null != activity.getCurrentFocus() && null != activity.getCurrentFocus().getWindowToken()) {
            return imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
        } else {
            Log.w(TAG, "can't hide soft keyboard, because some object is null");
            return false;
        }
    }
}
