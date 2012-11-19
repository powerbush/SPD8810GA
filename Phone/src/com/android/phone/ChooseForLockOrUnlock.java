/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

/**
 * List of Phone-specific settings screens.
 */
public class ChooseForLockOrUnlock extends PreferenceActivity 
implements EditPinPreference.OnPinEnteredListener , DialogInterface.OnCancelListener{

    public static final String KEY = "on_or_off"; 

    private static final String UNLOCK_KEY = "unlock_key";

    private static final String LOCK_KEY = "lock_key";
    private static final String LogTag = "ChooseForLockOrUnlock";
    private static final int EVENT_SIM1_LOCK_COMPLETE = 100;
    private static final int EVENT_SIM2_LOCK_COMPLETE = 200;
    EditPinPreference simunLock;
    EditPinPreference simLock;
    Phone phone = null;
    Phone phone1 = null;
    private boolean hasException = false;
    private int phoneCount = 0;
    private int pendingMessage = 0;
    private Handler mSimLockHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SIM1_LOCK_COMPLETE: 
                	pendingMessage--;
                	Log.e(LogTag, "handleMessage EVENT_SIM1_LOCK_COMPLETE");
                    AsyncResult ar = (AsyncResult) msg.obj;
                	if(ar.exception != null){
                		hasException = true;
                	}else {
                		if(!hasException){
                    		hasException = false;
                		}
                	}
                	if(pendingMessage == 0){
                		if(hasException){
                			displayMessage(R.string.sim_lock_process_failed);
                		} else {
                			displayMessage(R.string.sim_lock_process_success);
                		}
                		hasException = false;
                	}
                    break;
                case EVENT_SIM2_LOCK_COMPLETE: 
                	pendingMessage--;
                	Log.e(LogTag, "handleMessage EVENT_SIM1_LOCK_COMPLETE");
                    AsyncResult acr = (AsyncResult) msg.obj;
                	if(acr.exception != null){
                		hasException = true;
                	}else{
                		if(!hasException){
                    		hasException = false;
                		}
                	}
                	if(pendingMessage == 0){
                		if(hasException){
                			displayMessage(R.string.sim_lock_process_failed);
                		} else {
                			displayMessage(R.string.sim_lock_process_success);
                		}
                		hasException = false;
                	}
                    break;
            }
        }
    };
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // if is not dsds
        addPreferencesFromResource(R.xml.sim_lock_choose);
        simunLock = (EditPinPreference) findPreference(UNLOCK_KEY);
        simLock = (EditPinPreference) findPreference(LOCK_KEY);
        simunLock.setOnPinEnteredListener(this);
        simLock.setOnPinEnteredListener(this);
    }

    protected void onResume() {
        super.onResume();
        if (TelephonyManager.getPhoneCount() < 2) {
        	simunLock.setEnabled(PhoneFactory.isCardExist(0));
        	simLock.setEnabled(PhoneFactory.isCardExist(0));
        	phone = (PhoneFactory.getPhones())[0];
        	phoneCount = 1;
        } else {
        	phoneCount = 2;
            if((!PhoneFactory.isCardExist(0))&&(!PhoneFactory.isCardExist(1))){
            	simunLock.setEnabled(false);
            	simLock.setEnabled(false);
            }else {
            	if(PhoneFactory.isCardExist(0)){
            		phone = (PhoneFactory.getPhones())[0];
            	}
            	if(PhoneFactory.isCardExist(1)){
            		phone1 = (PhoneFactory.getPhones())[1];
            	}
            	simunLock.setEnabled(true);
            	simLock.setEnabled(true);
            }
        }
    }

	public void onPinEntered(EditPinPreference preference,
			boolean positiveResult) {
	    Log.e(LogTag, "onPinEntered");
        if (preference == simunLock) {
            unlockSimCardLock(positiveResult , false , preference);
        } else if (preference == simLock){
            unlockSimCardLock(positiveResult , true , preference);
        }
		
	}
    private void unlockSimCardLock(boolean positiveResult , boolean lockOrNot , EditPinPreference preference) {
        if(!positiveResult){
            return;
        }
        Log.e(LogTag, "unlockSimCardLock");
        String password = preference.getText();
        if (checkSimLock(lockOrNot , phoneCount)) {
            // make sim lock request
            if((phone != null) && (phone1 != null)){
            	pendingMessage++;
            	pendingMessage++;
                Message onComplete = mSimLockHandler.obtainMessage(EVENT_SIM1_LOCK_COMPLETE);
                phone.getIccCard().setSimCardLockEnabled(lockOrNot, password, onComplete);
                Message onComplete1 = mSimLockHandler.obtainMessage(EVENT_SIM2_LOCK_COMPLETE);
                phone1.getIccCard().setSimCardLockEnabled(lockOrNot, password, onComplete1);
            } else if (phone != null) {
            	if(phone.getIccCard().getIccSimEnabled() != lockOrNot){
                	pendingMessage++;
                    Message onComplete = mSimLockHandler.obtainMessage(EVENT_SIM1_LOCK_COMPLETE);
                    phone.getIccCard().setSimCardLockEnabled(lockOrNot, password, onComplete);
            	}
            } else if (phone1 != null){
            	if(phone1.getIccCard().getIccSimEnabled() != lockOrNot){
                	pendingMessage++;
                    Message onComplete = mSimLockHandler.obtainMessage(EVENT_SIM2_LOCK_COMPLETE);
                    phone1.getIccCard().setSimCardLockEnabled(lockOrNot, password, onComplete);
            	}
            }
        } else {
            Toast.makeText(this, getString(R.string.sim_lock_state_is_new), Toast.LENGTH_SHORT)
            .show();
        }

        preference.setText("");
    }
    private boolean checkSimLock( boolean onOrOff , int phoneCounts ){
    	if(phoneCounts == 1){
    		if(phone.getIccCard().getIccSimEnabled() != onOrOff){
    			return true;
    		}
    	}else if(phoneCounts == 2){
    		if(((phone!=null) && (phone.getIccCard().getIccSimEnabled()== onOrOff)) 
    				&& ((phone1!=null) && (phone1.getIccCard().getIccSimEnabled()== onOrOff))){
    			return false;
    		}else if ((phone == null) && ((phone1!=null) && (phone1.getIccCard().getIccSimEnabled()== onOrOff))){
    			return false;
    		}else if ((phone1 == null) && ((phone!=null) && (phone.getIccCard().getIccSimEnabled()== onOrOff))){
    			return false;
    		}else {
    			return true;
    		}
    	}
    	return false;
    }
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        Log.e(LogTag, "onSaveInstanceState");
    }
    private final void displayMessage(int strId) {
    	Log.e(LogTag, "displayMessage:" + getString(strId));
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT)
            .show();
    }

	public void onCancel(DialogInterface dialog) {
	}
}
