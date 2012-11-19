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

import com.android.internal.telephony.PhoneFactory;

import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

/**
 * List of Phone-specific settings screens.
 */
public class MobileNetworkSettings extends PreferenceActivity {

    public static final String SUB_ID = "sub_id";

    private static final String SIM1_KEY = "sim1_key";

    private static final String SIM2_KEY = "sim2_key";

    PreferenceScreen mSim1Setting;

    PreferenceScreen mSim2Setting;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // if is not dsds
        if (TelephonyManager.getPhoneCount() < 2) {
            Intent it = new Intent();
            it.setClass(MobileNetworkSettings.this, Settings.class);
            startActivity(it);
            this.finish();
        } else {
            addPreferencesFromResource(R.xml.mobile_network_setting);
            mSim1Setting = (PreferenceScreen) findPreference(SIM1_KEY);
            mSim2Setting = (PreferenceScreen) findPreference(SIM2_KEY);
            mSim1Setting.getIntent().putExtra(SUB_ID, 0);
            mSim2Setting.getIntent().putExtra(SUB_ID, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSim1Setting.setEnabled(PhoneFactory.isCardReady(0));
        mSim2Setting.setEnabled(PhoneFactory.isCardReady(1));
    }

}
