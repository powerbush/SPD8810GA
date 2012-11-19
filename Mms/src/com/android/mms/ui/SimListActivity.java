/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.ui;

import com.android.internal.telephony.ITelephony;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.content.AsyncQueryHandler;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Environment;
import android.os.StatFs;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.EditTextPreference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.SearchRecentSuggestions;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.net.Uri;
import com.android.mms.util.Recycler;
import java.util.*;
import android.telephony.TelephonyManager;

import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.PduPersister;
import android.content.ContentUris;
import com.android.mms.model.SlideshowModel;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Telephony.Mms;
import com.android.internal.telephony.Phone;
import java.io.File;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import com.google.android.mms.MmsException;
import com.android.internal.telephony.PhoneFactory;

import android.content.res.Resources;


/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class SimListActivity extends PreferenceActivity {
    public static interface Mode {
	public static final String KEY="mode";
	public static final int NIL	       =-1;
	public static final int MANAGE_MSG     =0;
	public static final int SMSC	       =1;

    }

    private PreferenceScreen mScreen;
    private TelephonyManager mDefaultTelephonyManager;
    private ArrayList<TelephonyManager> mTelephonyManagers;
    private ArrayList<ITelephony> mTelephonies;
    private int mMode;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
	mScreen=getPreferenceManager().createPreferenceScreen(this);
	setPreferenceScreen(mScreen);

	mDefaultTelephonyManager=MmsApp.getApplication().getTelephonyManager();
	mTelephonyManagers=new ArrayList<TelephonyManager>(2);
	mTelephonies=new ArrayList<ITelephony>(2);

	int phoneCount=mDefaultTelephonyManager.getPhoneCount();
	for (int i=0;i<phoneCount;++i)
	{
	    TelephonyManager tm=MmsApp.getApplication().getTelephonyManager(i);
	    mTelephonyManagers.add(tm);
	    if (tm.hasIccCard() && tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
		ITelephony telephony =ITelephony.Stub.asInterface(
		    ServiceManager.getService(
			PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i)));
		mTelephonies.add(telephony);
	    } else {
		mTelephonies.add(null); // just act as a place holder in the list
	    }
	}

	Intent intent=getIntent();
	mMode=intent.getIntExtra(Mode.KEY,Mode.NIL);
	switch (mMode) {
	    case Mode.MANAGE_MSG:
		handleManageSimMsg();
		break;
	    case Mode.SMSC:
		handleSMSC();
		break;
	    default:
		finish();
	}
    }

    private void handleManageSimMsg() {
	int phoneCount=mTelephonyManagers.size();
	Log.e ("SimListActivity","handleManageSimMsg: phoneCount="+phoneCount);
	for (int i=0;i<phoneCount;++i) {
	    if (mTelephonyManagers.get(i).hasIccCard() && mTelephonyManagers.get(i).getSimState() == TelephonyManager.SIM_STATE_READY) {
	    	Preference preference=new Preference(this);
		    String simName=generateSimName(i);
		    preference.setTitle(simName);
		    preference.setSummary(R.string.pref_title_manage_sim_messages);
		    Intent intent=new Intent(this, ManageSimMessages.class);
		    intent.putExtra(Phone.PHONE_ID,i);
		    preference.setIntent(intent);
		    mScreen.addPreference(preference);
	    }
	}
    }

    private void handleSMSC() {
	int phoneCount=mTelephonyManagers.size();
	for (int i=0;i<phoneCount;++i) {
	    if (mTelephonyManagers.get(i).hasIccCard()&& mTelephonyManagers.get(i).getSimState() == TelephonyManager.SIM_STATE_READY) {
	    	ValidatedEditTextPreference preference=new ValidatedEditTextPreference(this);
		    String simName=generateSimName(i);
		    preference.setTitle(simName);
		    preference.setSummary(R.string.pref_title_manage_sim_smsc);
		    // get orig smsc num
		    // telephony must not be null ,since tm.hasIccCard() == true
		    // `final` the telephony for `lexical closure` usage.
		    final ITelephony telephony=mTelephonies.get(i);

		    String smsc="error";
		    try {
			smsc=mTelephonies.get(i).getSmsc();
		    } catch (Exception e) {
			e.printStackTrace();
		    }

		    preference.setText(smsc);
		    preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			    public boolean onPreferenceChange(Preference preference, Object newValue) {
				String newSmsc=(String)newValue;
				try {
				    telephony.setSmsc(newSmsc);
				} catch (Exception e) {
				    e.printStackTrace();
				}
				return true;
			    }
			});
		    mScreen.addPreference(preference);
	    }
	}
    }

@ Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {		
		// TODO Auto-generated method stub		
		switch (mMode) {
		case Mode.SMSC:
			if(preference instanceof EditTextPreference)
			{
				EditText editable = ((EditTextPreference) preference).getEditText();
			    Editable eText = editable.getText();
			    editable.setSelection(eText.length());
			}
			break;
		default:
			//
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

    String generateSimName(int i) {
    if(MessageUtils.isMSMS){
    	return "SIM "+(i+1)+" "+mTelephonyManagers.get(i).getNetworkOperatorName();
    }else{
    	return "SIM "+mTelephonyManagers.get(i).getNetworkOperatorName();
    }
    }

    public class ValidatedEditTextPreference extends EditTextPreference
    {
        private int mMaxSize = 30;
        public ValidatedEditTextPreference(Context context) {
			super(context);
		}

        private class EditTextWatcher implements TextWatcher
        {
            public void onTextChanged(CharSequence s, int start, int before, int count){}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count){}

            @Override
            public void afterTextChanged(Editable s)
            {
                onEditTextChanged(s);
            }
        }
        private EditTextWatcher m_watcher = new EditTextWatcher();
        protected void onEditTextChanged(Editable s)
        {
            Dialog dlg = getDialog();
            if(dlg instanceof AlertDialog)
            {
                AlertDialog alertDlg = (AlertDialog)dlg;
                Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
                if(s.length() == 0){
                	btn.setEnabled(false);
                }else{
                	btn.setEnabled(true);
                }
            }
            if(s.length() > mMaxSize) {
                String title = SimListActivity.this.getResources().getString(R.string.exceed_text_length_limitation);
                String message = SimListActivity.this.getResources().getString(R.string.exceed_text_length_limitation_info);
                MessageUtils.showErrorDialog(SimListActivity.this, title, message);
                getEditText().setText("");
            }
        }
        @Override
        protected void showDialog(Bundle state)
        {
            super.showDialog(state);
            getEditText().removeTextChangedListener(m_watcher);
            getEditText().addTextChangedListener(m_watcher);
        }
    }
}
