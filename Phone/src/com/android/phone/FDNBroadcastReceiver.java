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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.internal.telephony.IccCard;

public class FDNBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("FDNBroadcastReceiver","onReceive");
        if ((intent.getAction().equals(
                "android.intent.action.SIM_STATE_CHANGED") && (IccCard.INTENT_VALUE_ICC_LOADED
                .equals(intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE))|| IccCard.INTENT_VALUE_ICC_REFRESH
                .equals(intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE))))) {
            Log.i("FDNBroadcastReceiver"," startService ");
            context.startService(new Intent("read_fdn"));
        }
    }

}
