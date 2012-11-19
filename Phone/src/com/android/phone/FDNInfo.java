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

package com.android.phone;



import android.util.Log;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import java.util.ArrayList;
import java.util.Iterator;
import android.telephony.PhoneNumberUtils;
/**
 * ADN List activity for the Phone app.
 */
public class FDNInfo  {
    protected static final String TAG = "FDNInfo";
    protected static final boolean DBG = true;
    static final int WILD_CHARACTER = 'D';
 
    private final int REQUEST_RESULT =1;
    static private ArrayList<String> mSim1FdnList = new ArrayList<String>();
    static private ArrayList<String> mSim2FdnList = new ArrayList<String>();


    
    public FDNInfo()
    {
         
	 
    }


	public static boolean isFDNDisableDial(Phone phone, String number) {
		IccCard icc = phone.getIccCard();
		if(icc == null){
			//may be it is a SipPhone
			return false;
		}
		boolean isEnabled = icc.getIccFdnEnabled();
		if (DBG)
			Log.i(TAG, "isFDNDisableDial ");
		if (PhoneNumberUtils.isEmergencyNumber(number)) {
			return false;
		}
		// isEnabled = true;
		if (!isEnabled) {
			if (DBG)
				Log.i(TAG, "isFDNDisableDial isEnabled " + isEnabled);
			return false;
		}
		return !isFoundFdn(number, phone.getPhoneId());
	}


    public static void addFdn(String number , int subId){
          if(DBG) Log.i(TAG, "addFdn  number" +number + " subId " + subId);
          if(subId == 0){
              mSim1FdnList.add(number);
          }else if(subId == 1){
              mSim2FdnList.add(number);
          }
          logFdn(subId);
    }

   
    
    public static void updateFdn(String oldNum, String newNum , int subId){

          if(DBG) Log.i(TAG, "updateFdn  oldNum " +oldNum + "newNum  " +newNum  + " subId " + subId);
          if(subId == 0 ){
              mSim1FdnList.remove(oldNum);
              mSim1FdnList.add(newNum);
          }else if(subId == 1 ){
              mSim2FdnList.remove(oldNum);
              mSim2FdnList.add(newNum);
          }
          logFdn(subId);
    }


     public static void removeFdn(String number , int subId){

         if(DBG) Log.i(TAG, "removeFdn  number" +number + " subId " + subId);
         if(subId == 0 ){
             mSim1FdnList.remove(number);
         }else if(subId == 1 ){
             mSim2FdnList.remove(number);
         }
         logFdn(subId);


    }

    public static void clearFdn(int subId) {
        if (subId == 0 && mSim1FdnList != null) {
            mSim1FdnList.clear();
        }
        if (subId == 1 && mSim2FdnList != null) {
            mSim2FdnList.clear();
        }
    }

    public static  boolean isFoundFdn(String number , int subId){

        int index = -1;
        int count = 1;
	  int i = 0;
	  if(DBG) Log.i(TAG, "isFoundFdn " + " number " + number + " subId " + subId);
	
	  logFdn(subId);
         // cr118635 begin
        if ((number.startsWith("**052")) && number.endsWith("#")) {
            return true;
        }

        if ((number.startsWith("**042")) && number.endsWith("#")) {
            return true;
        }
        // cr118635 end
        if((subId == 0 && mSim1FdnList.size() == 0)
            || (subId == 1 && mSim2FdnList.size() == 0)){
            return false;
        }

        ArrayList<String> tmpFdnList = mSim1FdnList;
        if(subId == 1){
            tmpFdnList = mSim2FdnList;
        }
        for (Iterator<String> it = tmpFdnList.iterator(); it.hasNext(); ) {

	      String fdnNumber = it.next();

	      if(fdnNumber == null ){
                  continue;
	      }	      
		  
	      for(i=0; i< fdnNumber.length(); i++){  

		    if(number.length() >= fdnNumber.length() ){
                       //if(DBG) Log.i(TAG, "isFoundFdn  number.charAt(i)   " + number.charAt(i) + "fdnNumber.charAt(i) " + fdnNumber.charAt(i));
			    if(number.charAt(i) != fdnNumber.charAt(i) && fdnNumber.charAt(i) != WILD_CHARACTER){

                               break;
			    }else{

				    if(i == fdnNumber.length() -1){

				            index = count;
                        
				    }



			    }

		    }
		
	      }

	      if(index > 0){

		      break;

		}	  
          
            count++;
        }
        if(DBG) Log.i(TAG, "isFoundFdn  index " + index);	
        if(index > 0){
              return true;
	  }
		return false;
   }

    private static void logFdn(int subId) {
        if (subId == 0) {
            for (int i = 0; i < mSim1FdnList.size(); i++) {
                if (DBG)
                    Log.i(TAG, "logFdn" + mSim1FdnList.get(i));
            }
        } else if (subId == 1) {
            for (int i = 0; i < mSim2FdnList.size(); i++) {
                if (DBG)
                    Log.i(TAG, "logFdn" + mSim2FdnList.get(i));
            }
        }
    }

}
