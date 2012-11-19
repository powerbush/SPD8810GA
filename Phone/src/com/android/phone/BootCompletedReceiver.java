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

import com.android.internal.telephony.IccCard.State;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Config;
import android.util.Log;

/**
 * Boot completed receiver. used to reset the app install state every time the
 * device boots.
 *
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive:action=" + action);
        mContext = context;
        isStandby = System.getInt(mContext.getContentResolver(), System.POWER_ON_STANDBY_SELECT, 0);
        airplaneMode = System.getInt(mContext.getContentResolver(), System.AIRPLANE_MODE_ON, 0);
        isNeedCheck = (airplaneMode == 1) ? true : (isStandby == 1 ? false : true);
        mIsStandbySelectShow = System.getInt(mContext.getContentResolver(),
                System.Standby_Select_Card_Show, 0) == 1;
        if (Config.LOGD) {
            Log.d(TAG,
                    "onReceive:PhoneFactory.getSimState(0)=" + PhoneFactory.getIccCardState(0)
                            + ", PhoneFactory.getSimState(1)=" + PhoneFactory.getIccCardState(1)
                            + " ,airplaneMode=" + airplaneMode + ", isStandby=" + isStandby
                            + " mIsStandbySelectDisplay = " + mIsStandbySelectShow
                            + ", isNeedCheck=" + isNeedCheck);
        }
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)
                || Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            synchronized (checkLock) {

                if (!isNeedCheck) {// needed display StandbyDialogActivity
                                   // Activity
                    if (!mIsStandbySelectShow) {// StandbyDialogActivity
                                                            // has ever
                                                            // displayed or not?
                                                            // false,no display
                                                            // true,has already
                                                            // display
                          boolean isSend = false;
                          for(int i =0;i<TelephonyManager.getPhoneCount();i++) {
                              if (PhoneFactory.getIccCardState(i) != IccCard.State.ABSENT
                                      && PhoneFactory.getIccCardState(i) != IccCard.State.UNKNOWN) {
                                  isSend = true;
                                  break;
                              }
                          }
                          if (isSend) {
                              Log.d(TAG,
                                      " send start StandbyDialogActivity broadcast");
                              mHandler.obtainMessage().sendToTarget();
                          }
                    }
                }
            }
        }
    }

    private static boolean isNeedCheck = true;
    private static String checkLock = "";
    private Context mContext;
    private int isStandby = 0;//not need display StandbyDialogActivity
    static String TAG = "BootCompletedReceiver";
    private int airplaneMode = 0;//close airplane mode
    private boolean mIsStandbySelectShow = false;//whether StandbyDialogActivity has showed,ever
                                                 //true ,has already showed
//    Thread setSimThread = new Thread(){
//        public void run(){
//            while(true) {
//                try {
//                    if (isNeedCheck) {
//                        if (PhoneFactory.getSimState(0)==null||PhoneFactory.getSimState(1)==null) {
//                            Thread.sleep(4000);
//                        }else{
//                            mHandler.obtainMessage().sendToTarget();
//                            break;
//                           }
//                    }else{
//                        break;
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG,e.getMessage());
//                }
//            }
//        }
//    };

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {

            //pop StandbyDialogActivity if next three conditidons are satisfy:
            // isStandby is open
            // both SIM1 and SIM2 aren`t absent at least
            // airPlane is not open
            if ( PhoneFactory.getIccCardState(0) != State.ABSENT || PhoneFactory.getIccCardState(1) != State.ABSENT) {
                System.putInt(mContext.getContentResolver(),
                        System.Standby_Select_Card_Show, 1);
                Intent it = new Intent(mContext, StandbyDialogActivity.class);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(it);
            }
        }
    };
}