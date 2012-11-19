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

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * Boot completed receiver. used to reset the app install state every time the
 * device boots.
 *
 */
public class StandbyDialogActivity extends Activity {
    private Phone mPhones[];
    private boolean isSim1Checked=false;
    private boolean isSim2Checked=false;
    private boolean isSimChecked=false;
    private boolean isSimStandbyRecord[];
    boolean hasCard1;
    boolean hasCard2;
    Dialog alertDialog;
    private final String TAG="StandbyDialogActivity";
    private PhoneStateListener[] mPhoneStateListener;
    private TelephonyManager[] telephonyManager;
    private BroadcastReceiver mBroadcastReceiver=new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            Log.d(TAG, "onReceive action:"+action);
            if(Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)){
                for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
                    Settings.System.putInt(getContentResolver(), PhoneFactory.getSetting(
                            Settings.System.SIM_STANDBY, i), 0);
                }
                StandbyDialogActivity.this.finish();
            }
        }
    };
    private PhoneStateListener getPhoneStateListener(final int phoneId) {
        PhoneStateListener phoneStateListener = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState state) {
                Log.d(TAG, " onServiceStateChanged Received on SIM_" + phoneId +" state:"+state.getState());
                preparedDialog();
            }
        };
        return phoneStateListener;
    }
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TelephonyManager.getPhoneCount() > 1) {
            mPhones = new Phone[PhoneFactory.getPhoneCount()];
            mPhoneStateListener = new PhoneStateListener[PhoneFactory.getPhoneCount()];
            telephonyManager = new TelephonyManager[PhoneFactory.getPhoneCount()];
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mPhones[i] = (PhoneFactory.getPhones())[i];
                mPhoneStateListener[i] = getPhoneStateListener(i);
                telephonyManager[i] = (TelephonyManager) getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, i));
                // register for phone state notifications.
                telephonyManager[i].listen(mPhoneStateListener[i],
                        PhoneStateListener.LISTEN_SERVICE_STATE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter mIntentFilter=new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        this.registerReceiver(mBroadcastReceiver, mIntentFilter);
        preparedDialog();
    }

    /**
     * set sim card standby state
     * @param phoneId
     * @param isStandby
     */
    private void preparedDialog(){
        List<String> list1 = new LinkedList<String>();
        hasCard1 = telephonyManager[0].hasIccCard();
        hasCard2 = telephonyManager[1].hasIccCard();
        Log.d(TAG, "onResume:hasCard1 " + hasCard1 + " and hasCard2 " + hasCard2);
        if (hasCard1) {
            isSim1Checked = System.getInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 0), 0) == 1;
            isSimChecked = isSim1Checked;
	        list1.add("SIM1");
        }
        if (hasCard2) {
            isSim2Checked = System.getInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 1), 0) == 1;
            isSimChecked = isSim2Checked;
	        list1.add("SIM2");
        }

        if (hasCard1&&hasCard2) {
            isSimStandbyRecord = new boolean[] {isSim1Checked,isSim2Checked};
        }else{
            isSimStandbyRecord = new boolean[]{isSimChecked};
        }

        alertDialog = new AlertDialog.Builder(this).setCancelable(false).setTitle(R.string.standby_select).setIcon(
                android.R.drawable.ic_dialog_info).setMultiChoiceItems(list1.toArray(new String[]{}), 
                               isSimStandbyRecord, new DialogInterface.OnMultiChoiceClickListener() {

            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if (0 == which) {
                    if (hasCard1&&hasCard2) {
                        isSim1Checked = isChecked;
                    }else{
                        isSimChecked = isChecked;
                       }
                } else if (1 == which) {
                    isSim2Checked = isChecked;
                        }
            }
        }).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                if (hasCard1&&hasCard2) {
                  setStandbySim(0, isSim1Checked);
                  setStandbySim(1, isSim2Checked);
                }else{
                  setStandbySim(hasCard1?0:1,isSimChecked);
                  }
                if (mPhoneStateListener!=null&&telephonyManager!=null) {
                    for (int i =0;i<PhoneFactory.getPhoneCount();i++) {
                        telephonyManager[i].listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
                        mPhoneStateListener[i] = null;
                        telephonyManager[i] = null;
                    }
                }
                StandbyDialogActivity.this.finish();
             }
        }).create();
        alertDialog.show();

        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD);
        alertDialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener(){
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                // fix bug 10333 ,forbid search key.
                // fix bug 10513 ,forbid camera Key.
                if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_SEARCH
                        || keyCode == KeyEvent.KEYCODE_CAMERA) {
                    return true;
                } else {
                    return false;
                }
            }
        });
    }
    private void setStandbySim(int phoneId, boolean isStandby) {
        mPhones[phoneId].setRadioPower(isStandby);
        if (isStandby) {
            System.putInt(getContentResolver(), PhoneFactory
                    .getSetting(System.SIM_STANDBY, phoneId), 1);
        } else {
            System.putInt(getContentResolver(), PhoneFactory
                    .getSetting(System.SIM_STANDBY, phoneId), 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(mBroadcastReceiver);
    }

    protected void onDestroy() {
        if (alertDialog!=null)
            alertDialog.dismiss();
        telephonyManager = null;
        mPhoneStateListener = null;
        super.onDestroy();
    }
}