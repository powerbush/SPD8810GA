package com.android.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class IpDailingUtils {
	private static final String TAG = "IpDailingUtils";
	private static final String IP_DIALING_SHARED_PREFERENCES_NAME = "ipdailinginfo";
	private static final String KEY_IP_NUMBER = "ipnumber";
	private static final String KEY_IS_IP_DIAL = "isipdial";
	private static final int NUMBER_COUNT = 5;
	private Context mContext;
	private SharedPreferences mPreference;
	private Editor mEditor;

	@SuppressWarnings("static-access")
	public IpDailingUtils(Context context) {
		mContext = context;
		mPreference = context.getSharedPreferences(
				IP_DIALING_SHARED_PREFERENCES_NAME,
				mContext.MODE_WORLD_READABLE);
		mEditor = mPreference.edit();
	}

	public boolean getIsIpDial() {
		return mPreference.getBoolean(KEY_IS_IP_DIAL, false);
	}

	public void setIsIpDialer(boolean isIpDial) {
		mEditor.putBoolean(KEY_IS_IP_DIAL, isIpDial);
		mEditor.commit();
	}

	public void setIpNumber(String ipNumber, int editTextNumber) {
		mEditor.putString(KEY_IP_NUMBER + editTextNumber, ipNumber);
		mEditor.commit();
	}

	public String getIpNumber(int editTextNumber) {
		return mPreference.getString(KEY_IP_NUMBER + editTextNumber, "");
	}

	public String getAllIpNumberString() {
		StringBuilder prefixList = new StringBuilder();
		for (int i = 0; i < NUMBER_COUNT; i++) {
			prefixList.append(getIpNumber(i));
			prefixList.append("|");
		}
		return prefixList.toString();
	}
}
