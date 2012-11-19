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

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class GsmUmtsCallOptions extends PreferenceActivity {
    private static final String LOG_TAG = "GsmUmtsCallOptions";
    private static final String BUTTON_CF_EXPAND_KEY = "button_cf_expand_key";
    private static final String BUTTON_CB_EXPAND_KEY = "button_cb_expand_key";
    private static final String BUTTON_MORE_EXPAND_KEY = "button_more_expand_key";

    private final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);
    private int mSubId = 0;
    private PreferenceScreen mButtonCfExpand;
    private PreferenceScreen mButtonCbExpand;
    private PreferenceScreen mButtonMoreExpand;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mSubId  = getIntent().getIntExtra(CallSettingOptions.SUB_ID, 0);

        addPreferencesFromResource(R.xml.gsm_umts_call_options);

        if (PhoneApp.getInstance().getPhone(mSubId).getPhoneType() != Phone.PHONE_TYPE_GSM) {
            //disable the entire screen
            getPreferenceScreen().setEnabled(false);
        } else {
            mButtonCfExpand = (PreferenceScreen) findPreference(BUTTON_CF_EXPAND_KEY);
            mButtonCfExpand.getIntent().putExtra(CallSettingOptions.SUB_ID, mSubId);
            mButtonCbExpand = (PreferenceScreen) findPreference(BUTTON_CB_EXPAND_KEY);
            mButtonCbExpand.getIntent().putExtra(CallSettingOptions.SUB_ID, mSubId);
            mButtonMoreExpand = (PreferenceScreen) findPreference(BUTTON_MORE_EXPAND_KEY);
            mButtonMoreExpand.getIntent().putExtra(CallSettingOptions.SUB_ID, mSubId);
        }
    }
}
