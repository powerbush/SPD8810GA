package com.android.mms.block;

import com.android.mms.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class BlockSettingActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.block_pref);
	}
}
