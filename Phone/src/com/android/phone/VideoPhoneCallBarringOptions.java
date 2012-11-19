package com.android.phone;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import com.android.internal.telephony.CallBarringInfo;
import com.android.internal.telephony.CommandsInterface;

import java.util.ArrayList;

public class VideoPhoneCallBarringOptions extends TimeConsumingPreferenceActivity 
	implements CallBarringEditPreferencePreferenceListener{
    private static final String LOG_TAG = "VideoPhoneCallBarringOptions";
    private final boolean DBG = true;//(PhoneApp.DBG_LEVEL >= 2);

    private static final String NUM_PROJECTION[] = {Phone.NUMBER};

    private static final String BUTTON_AO_KEY   = "button_ao_key";
    private static final String BUTTON_OI_KEY   = "button_oi_key";
    private static final String BUTTON_OX_KEY = "button_ox_key";
    private static final String BUTTON_AI_KEY = "button_ai_key";
    private static final String BUTTON_IR_KEY = "button_ir_key";
    private static final String BUTTON_AB_KEY = "button_ab_key";
	
    private static final String BUTTON_CHGPWD_KEY = "button_chgpwd_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PASSWORD = "password";

    private CallBarringEditPreference mButtonAO;
    private CallBarringEditPreference mButtonOI;
    private CallBarringEditPreference mButtonOX;
    private CallBarringEditPreference mButtonAI;
    private CallBarringEditPreference mButtonIR;
    private CallBarringEditPreference mButtonAB;
    private CallBarringChgPwdPreference mButtonChgPwd;

    private final ArrayList<CallBarringEditPreference> mPreferences =
            new ArrayList<CallBarringEditPreference> ();
    private int mInitIndex= 0;

    private boolean mFirstResume;
    private Bundle mIcicle;
    private int mSubId = 0;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mSubId    = getIntent().getIntExtra(CallSettingOptions.SUB_ID, 0);
        addPreferencesFromResource(R.xml.videophone_callbarring_options);

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonAO   = (CallBarringEditPreference) prefSet.findPreference(BUTTON_AO_KEY);
        mButtonOI   = (CallBarringEditPreference) prefSet.findPreference(BUTTON_OI_KEY);
        mButtonOX = (CallBarringEditPreference) prefSet.findPreference(BUTTON_OX_KEY);
        mButtonAI = (CallBarringEditPreference) prefSet.findPreference(BUTTON_AI_KEY);
        mButtonIR = (CallBarringEditPreference) prefSet.findPreference(BUTTON_IR_KEY);
        mButtonAB = (CallBarringEditPreference) prefSet.findPreference(BUTTON_AB_KEY);
        mButtonChgPwd = (CallBarringChgPwdPreference) prefSet.findPreference(BUTTON_CHGPWD_KEY);

        mButtonAO.setParentActivity(this, mButtonAO.mReason);
        mButtonOI.setParentActivity(this, mButtonOI.mReason);
        mButtonOX.setParentActivity(this, mButtonOX.mReason);
        mButtonAI.setParentActivity(this, mButtonAI.mReason);
        mButtonIR.setParentActivity(this, mButtonIR.mReason);
        mButtonAB.setParentActivity(this, mButtonAB.mReason);
		mButtonAB.setNeedEcho(false);
		mButtonAB.setToggled(true);
        mButtonChgPwd.setParentActivity(this, 0);

        mPreferences.add(mButtonAO);
        mPreferences.add(mButtonOI);
        mPreferences.add(mButtonOX);
        mPreferences.add(mButtonAI);
        mPreferences.add(mButtonIR);
        mPreferences.add(mButtonAB);

        // we wait to do the initialization until onResume so that the
        // TimeConsumingPreferenceActivity dialog can display as it
        // relies on onResume / onPause to maintain its foreground state.

        mFirstResume = true;
        mIcicle = icicle;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mFirstResume) {
            if (mIcicle == null) {
                if (DBG) Log.d(LOG_TAG, "start to init ");
                mPreferences.get(mInitIndex).init(this, false, mSubId);
		mPreferences.get(mInitIndex).setListener(this);
                mButtonChgPwd.init(this, false, mSubId);
            } else {
                mInitIndex = mPreferences.size();

                for (CallBarringEditPreference pref : mPreferences) {
                    Bundle bundle = mIcicle.getParcelable(pref.getKey());
                    pref.setToggled(bundle.getBoolean(KEY_TOGGLE));
                    CallBarringInfo cb = new CallBarringInfo();
                    cb.password = bundle.getString(KEY_PASSWORD);
                    cb.status = bundle.getInt(KEY_STATUS);
                    pref.handleCallBarringResult(cb);
                    pref.init(this, true, mSubId);
                }				
            }
            mFirstResume = false;
            mIcicle=null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        for (CallBarringEditPreference pref : mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, pref.isToggled());
            if (pref.mCallBarringInfo != null) {
                bundle.putString(KEY_PASSWORD, pref.mCallBarringInfo.password);
                bundle.putInt(KEY_STATUS, pref.mCallBarringInfo.status);
            }
            outState.putParcelable(pref.getKey(), bundle);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
			mInitIndex++;
			mPreferences.get(mInitIndex).init(this, !mPreferences.get(mInitIndex).getNeedEcho(), mSubId);
			mPreferences.get(mInitIndex).setListener(this);
        } 
		
        super.onFinished(preference, reading);
	
    }
	
	 public void onChange(Preference preference, int reason){	 	
	    	if (DBG) Log.d(LOG_TAG, "onChange, reason:  " + reason);
		mInitIndex = 0;
		mPreferences.get(mInitIndex).init(this, !mPreferences.get(mInitIndex).getNeedEcho(), mSubId);
	 }

}


