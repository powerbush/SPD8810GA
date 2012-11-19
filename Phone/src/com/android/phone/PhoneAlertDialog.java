/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.AlertDialog;
import android.util.Log;
import android.content.DialogInterface;

public class PhoneAlertDialog extends AlertDialog {

    private Context mContext;
    private static final String LOG_TAG = "PhoneAlertDialog";
    public static final String CALL_SCREEN_ON_TOP = "call_screen_on_top";  
    public static final String CALL_SCREEN_ON_BOTTOM = "call_screen_on_bottom";

    private boolean mNeedShowAgain;

    public PhoneAlertDialog(Context context) {
        super(context); 
        mContext = context;
        mNeedShowAgain = true;
        this.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    PhoneAlertDialog.this.mNeedShowAgain = false;
                }});
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context,Intent intent) {
            String action = intent.getAction();
            if (CALL_SCREEN_ON_TOP.equals(action)) {
                PhoneAlertDialog.this.hide();
            } else if (CALL_SCREEN_ON_BOTTOM.equals(action)) {
                if(mNeedShowAgain) {
                    PhoneAlertDialog.this.show();
                }
            }
        }
    };
    
    public void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter(CALL_SCREEN_ON_TOP);
        intentFilter.addAction(CALL_SCREEN_ON_BOTTOM);
        mContext.registerReceiver(mReceiver,intentFilter);
    }

    public void onStop() { 
        super.onStop();
        mContext.unregisterReceiver(mReceiver);
    }

    public void setNeedShowAgain(boolean needShow) {
        mNeedShowAgain = needShow;
    }

}
