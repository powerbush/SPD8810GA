package com.android.phone;

import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;

public class GsmUmtsAdditionalCallOptions extends
        TimeConsumingPreferenceActivity {
    private static final String LOG_TAG = "GsmUmtsAdditionalCallOptions";
    private final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    private static final String BUTTON_CLIR_KEY  = "button_clir_key";
    private static final String BUTTON_CW_KEY    = "button_cw_key";

    private CLIRListPreference mCLIRButton;
    private CallWaitingCheckBoxPreference mCWButton;

    private ArrayList<Preference> mPreferences = new ArrayList<Preference> ();
    private int mInitIndex= 0;
    int mSubId = 0;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mSubId   = getIntent().getIntExtra(CallSettingOptions.SUB_ID, 0);
        addPreferencesFromResource(R.xml.gsm_umts_additional_options);
        if (PhoneFactory.getPhoneCount() > 1) {
            if(mSubId == 0) {
                setTitle(getResources().getString(R.string.sim1) + getResources().getString(R.string.additional_gsm_call_settings));
            }else if(mSubId == 1) {
                setTitle(getResources().getString(R.string.sim2) + getResources().getString(R.string.additional_gsm_call_settings));
            }
        }

        PreferenceScreen prefSet = getPreferenceScreen();
        mCLIRButton = (CLIRListPreference) prefSet.findPreference(BUTTON_CLIR_KEY);
        mCWButton = (CallWaitingCheckBoxPreference) prefSet.findPreference(BUTTON_CW_KEY);

        mPreferences.add(mCLIRButton);
        mPreferences.add(mCWButton);

        if (icicle == null) {
            if (DBG) Log.d(LOG_TAG, "start to init ");
            mCLIRButton.init(this, false, mSubId);
            mCWButton.init(this, true, mSubId);
        } else {
            if (DBG) Log.d(LOG_TAG, "restore stored states");
            mInitIndex = mPreferences.size();
            mCLIRButton.init(this, true, mSubId);
            mCWButton.init(this, true, mSubId);
            int[] clirArray = icicle.getIntArray(mCLIRButton.getKey());
            if (clirArray != null) {
                if (DBG) Log.d(LOG_TAG, "onCreate:  clirArray[0]="
                        + clirArray[0] + ", clirArray[1]=" + clirArray[1]);
                mCLIRButton.handleGetCLIRResult(clirArray);
            } else {
                mCLIRButton.init(this, false, mSubId);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mCLIRButton.clirArray != null) {
            outState.putIntArray(mCLIRButton.getKey(), mCLIRButton.clirArray);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            mInitIndex++;
            Preference pref = mPreferences.get(mInitIndex);
            if (pref instanceof CallWaitingCheckBoxPreference) {
                ((CallWaitingCheckBoxPreference) pref).init(this, false, mSubId);
            }
        }
        super.onFinished(preference, reading);
    }

}
