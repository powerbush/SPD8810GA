
package com.android.phone;

import com.android.internal.telephony.PhoneFactory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;

public class CallSettingOptions extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String SIM1_KEY = "sim1_key";

    private static final String SIM2_KEY = "sim2_key";

    public static final String SUB_ID = "sub_id";

    private static final String SET_IP_DAILING_ON = "set_ip_dailing_on";
    private static final String IP_DAILING_LIST = "set_ip_dailing_preference";

    private static final String GSM_BUTTON_VIBRATE_KEY = "gsm_button_call_vibrate_key";

    PreferenceScreen mSim1Setting;

    PreferenceScreen mSim2Setting;
    private CheckBoxPreference mIpDailingOn;
    private PreferenceScreen mIpDailingPreference;
    private IpDailingUtils mIpDailingUtils;

    private CheckBoxPreference mGsmButtonCallVibrate;

    private SharedPreferences defaultSharedpref;
    private Editor defaultPredEditor;
    private static final String KEY_CALL_VIBRATE = "isCallVibrate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        mIpDailingUtils = new IpDailingUtils(this.getApplicationContext());
        if (TelephonyManager.getPhoneCount() > 1) {
            addPreferencesFromResource(R.xml.callsetting_options);
            mSim1Setting = (PreferenceScreen) findPreference(SIM1_KEY);
            mSim2Setting = (PreferenceScreen) findPreference(SIM2_KEY);
            mSim1Setting.getIntent().putExtra(SUB_ID, 0);
            mSim2Setting.getIntent().putExtra(SUB_ID, 1);
            mIpDailingOn = (CheckBoxPreference) findPreference(SET_IP_DAILING_ON);
            mIpDailingOn.setOnPreferenceChangeListener(this);
            mIpDailingPreference = (PreferenceScreen) findPreference(IP_DAILING_LIST);
            mGsmButtonCallVibrate = (CheckBoxPreference) findPreference(GSM_BUTTON_VIBRATE_KEY);
            defaultSharedpref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            defaultPredEditor = defaultSharedpref.edit();
        } else {
            Intent intent = getIntent();
            intent.setClass(CallSettingOptions.this, CallFeaturesSetting.class);
            startActivity(intent);
            this.finish();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSim1Setting.setEnabled(PhoneFactory.isCardReady(0));
        mSim2Setting.setEnabled(PhoneFactory.isCardReady(1));
        mIpDailingOn.setChecked(mIpDailingUtils.getIsIpDial());
        mIpDailingPreference.setEnabled(mIpDailingOn.isChecked());
        mGsmButtonCallVibrate.setChecked((defaultSharedpref == null ? true : defaultSharedpref.getBoolean(KEY_CALL_VIBRATE, true)));
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if ((preference == mSim1Setting || preference == mSim2Setting)
                && preference.getIntent() != null) {
            this.startActivity(preference.getIntent());
            return true;
        } else if (preference == mGsmButtonCallVibrate) {
            defaultPredEditor.putBoolean(KEY_CALL_VIBRATE, mGsmButtonCallVibrate.isChecked());
            defaultPredEditor.commit();
            return true;
    }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == mIpDailingOn) {
			mIpDailingPreference.setEnabled(!mIpDailingOn.isChecked());
			mIpDailingUtils.setIsIpDialer(!mIpDailingOn.isChecked());
		}
		return true;
	}

}
