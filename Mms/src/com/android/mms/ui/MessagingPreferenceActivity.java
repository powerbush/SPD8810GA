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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
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
import android.widget.EditText;
import android.net.Uri;
import com.android.mms.util.Recycler;
import android.widget.Toast;

import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.PduPersister;
import android.content.ContentUris;
import com.android.mms.model.SlideshowModel;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Telephony.Mms;
import java.io.File;

import android.telephony.SmsManager;
import android.text.format.Formatter;
import com.google.android.mms.MmsException;
import com.android.internal.telephony.SMSDispatcher;


import android.content.res.Resources;


/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MessagingPreferenceActivity extends PreferenceActivity implements
								    Preference.OnPreferenceChangeListener{
    // Symbolic names for the keys used for preference lookup
    public static final String MMS_DELIVERY_REPORT_MODE = "pref_key_mms_delivery_reports";
    public static final String EXPIRY_TIME              = "pref_key_mms_expiry";
    public static final String PRIORITY                 = "pref_key_mms_priority";
    public static final String READ_REPORT_MODE         = "pref_key_mms_read_reports";
    public static final String SMS_DELIVERY_REPORT_MODE = "pref_key_sms_delivery_reports";
    public static final String SMS_SAVE_TO_SIMCARD      = "pref_key_sms_save_to_sim_card";
    public static final String SMS_RETRY_TIMES          = "pref_key_sms_retry_times";
    public static final String NOTIFICATION_ENABLED     = "pref_key_enable_notifications";
    public static final String NOTIFICATION_VIBRATE     = "pref_key_vibrate";
    public static final String NOTIFICATION_VIBRATE_WHEN= "pref_key_vibrateWhen";
    public static final String NOTIFICATION_RINGTONE    = "pref_key_ringtone";
    public static final String AUTO_RETRIEVAL           = "pref_key_mms_auto_retrieval";
    public static final String RETRIEVAL_DURING_ROAMING = "pref_key_mms_retrieval_during_roaming";
    public static final String AUTO_DELETE              = "pref_key_auto_delete";
    public static final String FORWARDING_NUMBER        = "pref_key_forwarding_number";
    public static final String SET_SMS_TEXT_SIZE        = "pref_key_set_sms_text_size";

    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS    = 1;

    private Preference mSmsLimitPref;
    private Preference mSmsDeliveryReportPref;
    private Preference mMmsLimitPref;
    private Preference mMmsDeliveryReportPref;
    private Preference mMmsReadReportPref;
    private Preference mManageSimPref;
    private Preference mSmsRetryTimes;
    private Preference mSmscPref;

    private Preference mAutoDeletePred;

    private Preference mSaveToSimPref;

    private Preference mClearHistoryPref;
    private ListPreference mVibrateWhenPref;
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27 begin ======
    private ListPreference mValidityPref;
    public static final String SMS_VALIDITY= "pref_key_sms_validity";
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27  end  ======
    private Recycler mSmsRecycler;
    private Recycler mMmsRecycler;
    private static final int CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG = 3;

    private static final String LOG_TAG = "MessagingPreferenceActivity";
    private EditText smscEdit;
    private String smscStr;
    private Context mContext;
    private static String mode = "";
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.preferences);

        mContext=this;


        setMessagePreferences();
    }

    private void setMessagePreferences() {

        mManageSimPref = findPreference("pref_key_manage_sim_messages");
        mSmsLimitPref = findPreference("pref_key_sms_delete_limit");
        mSmsDeliveryReportPref = findPreference("pref_key_sms_delivery_reports");
        mSmsRetryTimes = findPreference("pref_key_sms_retry_times");
        mMmsDeliveryReportPref = findPreference("pref_key_mms_delivery_reports");
        mMmsReadReportPref = findPreference("pref_key_mms_read_reports");
        mMmsLimitPref = findPreference("pref_key_mms_delete_limit");
        mClearHistoryPref = findPreference("pref_key_mms_clear_history");
        mVibrateWhenPref = (ListPreference) findPreference(NOTIFICATION_VIBRATE_WHEN);
        mSmscPref = findPreference("pref_key_sim_smsc");

        mAutoDeletePred = findPreference("pref_key_auto_delete");

        mSaveToSimPref = findPreference("pref_key_sms_save_to_sim_card");

        //====== fixed CR<NEWMSOO112910> by luning at 11-08-27 begin ======
        mValidityPref = (ListPreference) findPreference(SMS_VALIDITY);
        mValidityPref.setOnPreferenceChangeListener(this);
        //====== fixed CR<NEWMSOO112910> by luning at 11-08-27  end  ======

        if (!MmsApp.getApplication().hasAnyIccCard()) {
            PreferenceCategory smsCategory =
                (PreferenceCategory)findPreference("pref_key_sms_settings");
            smsCategory.removePreference(mManageSimPref);
            smsCategory.removePreference(mSmscPref);
	    smsCategory.removePreference(mSaveToSimPref);
        }

        Intent it = getIntent();
        Bundle bundle = it.getExtras();
        if (bundle != null) {
            mode = bundle.getString("mode");
        }
        PreferenceCategory smsStoreCategory =
            (PreferenceCategory)findPreference("pref_key_storage_settings");
        if ("folder".equals(mode)) {
            smsStoreCategory.removePreference(mAutoDeletePred);
            smsStoreCategory.removePreference(mSmsLimitPref);
            smsStoreCategory.removePreference(mMmsLimitPref);
            smsStoreCategory.setTitle(getString(R.string.display_store_title));
        } else {
            smsStoreCategory.setTitle(getString(R.string.pref_sms_storage_title));
        }
        boolean SMSDeliveryReport = Resources.getSystem()
	    .getBoolean(com.android.internal.R.bool.config_sms_delivery_reports_support);
        if (!SMSDeliveryReport) {
            PreferenceCategory smsCategory =
                (PreferenceCategory)findPreference("pref_key_sms_settings");
            smsCategory.removePreference(mSmsDeliveryReportPref);
            if (!MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
                getPreferenceScreen().removePreference(smsCategory);
            }
        }

        if (!MmsConfig.getMmsEnabled()) {
            // No Mms, remove all the mms-related preferences
            PreferenceCategory mmsOptions =
                (PreferenceCategory)findPreference("pref_key_mms_settings");
            getPreferenceScreen().removePreference(mmsOptions);

            PreferenceCategory storageOptions =
                (PreferenceCategory)findPreference("pref_key_storage_settings");
            storageOptions.removePreference(findPreference("pref_key_mms_delete_limit"));
        } else {
            boolean MMSDeliveryReport = Resources.getSystem()
		.getBoolean(com.android.internal.R.bool.config_mms_delivery_reports_support);
            boolean MMSReadReport = Resources.getSystem()
		.getBoolean(com.android.internal.R.bool.config_mms_read_reports_support);
            if (!MMSDeliveryReport) {
                PreferenceCategory mmsOptions =
                    (PreferenceCategory)findPreference("pref_key_mms_settings");
                mmsOptions.removePreference(mMmsDeliveryReportPref);
            }
            if (!MMSReadReport) {
                PreferenceCategory mmsOptions =
                    (PreferenceCategory)findPreference("pref_key_mms_settings");
                mmsOptions.removePreference(mMmsReadReportPref);
            }
        }

        // If needed, migrate vibration setting from a previous version
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sharedPreferences.contains(NOTIFICATION_VIBRATE_WHEN) &&
	    sharedPreferences.contains(NOTIFICATION_VIBRATE)) {
            int stringId = sharedPreferences.getBoolean(NOTIFICATION_VIBRATE, false) ?
		R.string.prefDefault_vibrate_true :
		R.string.prefDefault_vibrate_false;
            mVibrateWhenPref.setValue(getString(stringId));
        }

        if(sharedPreferences.contains(SMS_RETRY_TIMES)){
            boolean smsRetryTimes = sharedPreferences.getBoolean(SMS_RETRY_TIMES, true);
            ((CheckBoxPreference)mSmsRetryTimes).setChecked(smsRetryTimes);
        }
        mSmsRecycler = Recycler.getSmsRecycler();
        mMmsRecycler = Recycler.getMmsRecycler();

        // Fix up the recycler's summary with the correct values
        setSmsDisplayLimit();
        setMmsDisplayLimit();
        setMmsMemoryUsage();
    }
       
    
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27 begin ======
    protected void onResume() {		
	super.onResume();
	int validity = Settings.System.getInt(getContentResolver(), Settings.System.SMS_VALIDITY , 255);
	Log.d(LOG_TAG, "MessageingPreference --> onResume,validity:"+validity);
	mValidityPref.setValue(String.valueOf(validity));
    }
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27  end  ======

    private void setMmsMemoryUsage(){
	Uri mMessageUri;
	ContentResolver cr = getContentResolver();
	AsyncQueryHandler mQueryHandler;
	String projection[]={"_id"};
	Uri uri = Uri.parse("content://mms-sms/complete-conversations");
	mQueryHandler = new AsyncQueryHandler(cr) {
		protected void onQueryComplete(int token, Object cookie, Cursor c) {
		    showMmsMemoryUsage(c);
		}
	    };
        mQueryHandler.startQuery(0, null, uri, projection, null, null, null);
    }
    private void showMmsMemoryUsage(Cursor cursor) {
        CheckMmsMemoryUsageTask showMemoryUsageTask = new CheckMmsMemoryUsageTask(this,cursor);
        showMemoryUsageTask.execute();
    }

    private class CheckMmsMemoryUsageTask extends AsyncTask {
        Context mContext;
        Cursor c;

        public CheckMmsMemoryUsageTask(Context context, Cursor cursor) {
            mContext = context;
            c = cursor;
        }

        @Override
        protected void onPreExecute() {
            findPreference("memory_internal_usage").setSummary(
		getString(R.string.mms_memory_usage_progressDialog_title));
        }

        @Override
        protected Object doInBackground(Object... params) {
            if (c == null) {
                return null;
            }
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            long totalBlocks = stat.getBlockCount();
            SlideshowModel mSlideshow;
            int mMessageSize = 0;
            if (c.getCount()>0 && c.moveToFirst()) {
                do {
                    Long mMsgId = c.getLong(c.getColumnIndex("_id"));
                    try {
                        PduPersister p = PduPersister.getPduPersister(mContext);
                        Uri mMessageUri = ContentUris.withAppendedId(Mms.CONTENT_URI, mMsgId);
                        if (!(p.load(mMessageUri) instanceof com.google.android.mms.pdu.DeliveryInd)) {
                            MultimediaMessagePdu msg = (MultimediaMessagePdu) p.load(mMessageUri);
                            mSlideshow = SlideshowModel.createFromPduBody(mContext, msg.getBody(), false);
                            mMessageSize += mSlideshow.getTotalMessageSize();
                        }
                    } catch (Exception e) {
                    }
                } while (c.moveToNext());
            }
            long availableSize = availableBlocks * blockSize - 500 * 1024;
            long finalTotalSize = totalBlocks * blockSize;
            if (availableSize < 0) {
                availableSize = 0;
            }
            if (finalTotalSize < 0) {
                finalTotalSize = 0;
            }
            return formatSize(mMessageSize) + " / " + formatSize(availableSize) + " / " + formatSize(finalTotalSize);
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result != null) {
                findPreference("memory_internal_usage").setSummary(result.toString());
            }
            if ( c != null ) {
                c.close();
            }
        }
    }

    private String formatSize(long size) {
        return Formatter.formatFileSize(this, size);
    }

    // modify by zhengshenglan for NEWMS00113028 begin  at 16/08/2011   
    private void setSmsDisplayLimit() {
        mSmsLimitPref.setSummary(
	    //getString(R.string.pref_summary_delete_limit,
	    getString(R.string.pref_summary_delete_limit_sms,
		      mSmsRecycler.getMessageLimit(this)));
    }

    private void setMmsDisplayLimit() {
        mMmsLimitPref.setSummary(
//                getString(R.string.pref_summary_delete_limit,
	    getString(R.string.pref_summary_delete_limit_mms,
		      mMmsRecycler.getMessageLimit(this)));
    }
    // modify by zhengshenglan for NEWMS00113028 end  at 16/08/2011   
    
    
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string.restore_default);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESTORE_DEFAULTS:
                restoreDefaultPreferences();
                return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
					 Preference preference) {
        if (preference == mSmsLimitPref) {
            new NumberPickerDialog(this,
				   mSmsLimitListener,
				   mSmsRecycler.getMessageLimit(this),
				   mSmsRecycler.getMessageMinLimit(),
				   mSmsRecycler.getMessageMaxLimit(),
				   R.string.pref_title_sms_delete,
				   false).show();
        } else if (preference == mMmsLimitPref) {
            new NumberPickerDialog(this,
				   mMmsLimitListener,
				   mMmsRecycler.getMessageLimit(this),
				   mMmsRecycler.getMessageMinLimit(),
				   mMmsRecycler.getMessageMaxLimit(),
				   R.string.pref_title_mms_delete,
				   true).show();
        } else if (preference == mManageSimPref) {
	    Intent intent=new Intent(this, SimListActivity.class);
	    intent.putExtra(SimListActivity.Mode.KEY,SimListActivity.Mode.MANAGE_MSG);
            startActivity(intent);
        } else if (preference == mSmscPref) {
	    Intent intent=new Intent(this, SimListActivity.class);
	    intent.putExtra(SimListActivity.Mode.KEY,SimListActivity.Mode.SMSC);
            startActivity(intent);
        } else if (preference == mClearHistoryPref) {
            showDialog(CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG);
            return true;
        } else if(preference == mSmsRetryTimes){
            CheckBoxPreference checkPre = (CheckBoxPreference)preference;
            SharedPreferences.Editor editPrefs =PreferenceManager.getDefaultSharedPreferences(this).edit();
            if(checkPre.isChecked()){
                //Toast.makeText(this, "on", Toast.LENGTH_LONG).show();
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.setMaxSendRetries(3);
            }else{
                //Toast.makeText(this, "off", Toast.LENGTH_LONG).show();
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.setMaxSendRetries(1);
            }
            editPrefs.putBoolean(SMS_RETRY_TIMES, checkPre.isChecked());
            editPrefs.commit();
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }


    private void restoreDefaultPreferences() {
        PreferenceManager.getDefaultSharedPreferences(this)
	    .edit().clear().apply();
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.preferences);
        setMessagePreferences();
        Settings.System.putInt(getContentResolver(), Settings.System.SMS_VALIDITY , 255);
        int validity = Settings.System.getInt(getContentResolver(), Settings.System.SMS_VALIDITY , 255);
        mValidityPref.setValue(String.valueOf(validity));
    }

    NumberPickerDialog.OnNumberSetListener mSmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mSmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setSmsDisplayLimit();
            }
	};

    NumberPickerDialog.OnNumberSetListener mMmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mMmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setMmsDisplayLimit();
            }
	};

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG:
                return new AlertDialog.Builder(MessagingPreferenceActivity.this)
                    .setTitle(R.string.confirm_clear_search_title)
                    .setMessage(R.string.confirm_clear_search_text)
                    .setPositiveButton(android.R.string.ok, new AlertDialog.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
				SearchRecentSuggestions recent =
				    ((MmsApp)getApplication()).getRecentSuggestions();
				if (recent != null) {
				    recent.clearHistory();
				}
				dialog.dismiss();
			    }
			})
                    .setNegativeButton(android.R.string.cancel, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .create();
        }
        return super.onCreateDialog(id);
    }
    
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27 begin ======
    public boolean onPreferenceChange(Preference preference, Object newValue) {
	String value = newValue.toString();
	mValidityPref.setValue(value);
	int validity = Integer.parseInt(value);
	Log.d(LOG_TAG, "MessageingPreference --> onPreferenceChange,newValue:"+newValue);
	Settings.System.putInt(getContentResolver(), Settings.System.SMS_VALIDITY, validity);
	return false;
    }
    //====== fixed CR<NEWMSOO112910> by luning at 11-08-27 end ======
}
