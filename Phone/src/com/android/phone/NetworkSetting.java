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

import java.util.HashMap;
import java.util.List;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.MsmsConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gsm.NetworkInfo;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity
        implements DialogInterface.OnCancelListener {

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;
    private static final int EVENT_AUTO_SELECT_DONE = 300;

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;
    private static final int DIALOG_NETWORK_AUTO_SELECT = 300;

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final String BUTTON_SRCH_NETWRKS_KEY = "button_srch_netwrks_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";

    //map of network controls to the network data.
    private HashMap<Preference, NetworkInfo> mNetworkMap;

    Phone mPhone;
    protected boolean mIsForeground = false;

    /** message for network selection */
    String mNetworkSelectMsg;

    //preference objects
    private PreferenceGroup mNetworkList;
    private Preference mSearchButton;
    private Preference mAutoSelect;
    private  TelephonyManager mTelephonyManager;  //add by liguxiang 11-14-11 for NEWMS00139542
    private ConnectivityManager cm;
    private static final String CHINA_MOBILE = "CHINA MOBILE";
    private static final String CHN_CUGSM = "CHN-CUGSM";
    private static final String CHINA_MOBILE_IC = "46000";
    private static final String CHN_CUGSM_IC = "46001";
    private int mSubId = 0;
    ProgressDialog dialogLoaded;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded ((List<NetworkInfo>) msg.obj, msg.arg1);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) log("hideProgressPanel");
                    removeDialog(DIALOG_NETWORK_SELECTION);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("manual network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("manual network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break;
                case EVENT_AUTO_SELECT_DONE:
                    if (DBG) log("hideProgressPanel");

                    removeDialog(DIALOG_NETWORK_AUTO_SELECT);
                    /*
                    if (mIsForeground) {
                        dismissDialog(DIALOG_NETWORK_AUTO_SELECT);
                    }*/

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break;
            }

            return;
        }
    };

    /**
     * Service connection code for the NetworkQueryService.
     * Handles the work of binding to a local object so that we can make
     * the appropriate service calls.
     */

    /** Local service interface */
    private INetworkQueryService mNetworkQueryService = null;

    /** Service connection */
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) log("connection created, binding local service.");
            mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            // as soon as it is bound, run a query.
            //loadNetworksList();
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) log("connection disconnected, cleaning local binding.");
            mNetworkQueryService = null;
        }
    };

    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<NetworkInfo> networkInfoArray, int status) {
            if (DBG) log("notifying message loop of query completion.");
            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED,
                    status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean handled = false;

        if (preference == mSearchButton) {
           if(cm.getMobileDataEnabledByPhoneId(mSubId)){
                handled=false;
                Toast.makeText(NetworkSetting.this, R.string.toast_message_data_business_on, Toast.LENGTH_LONG).show();
             }else{
            loadNetworksList();
            handled = true;
          }
        } else if (preference == mAutoSelect) {
            getPreferenceScreen().setEnabled(false);
            selectNetworkAutomatic();
            handled = true;
        } else {
            if(cm.getMobileDataEnabledByPhoneId(mSubId)){
                handled=false;
                Toast.makeText(NetworkSetting.this, R.string.toast_message_data_business_on, Toast.LENGTH_LONG).show();
             }else{
                 getPreferenceScreen().setEnabled(false);
                 Preference selectedCarrier = preference;

                 String networkStr = selectedCarrier.getTitle().toString();
                 if (DBG) log("selected network: " + networkStr);

                 Message msg = mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
                 mPhone.selectNetworkManually(mNetworkMap.get(selectedCarrier), msg);

                 displayNetworkSeletionInProgress(networkStr);

            handled = true;
          }
        }

        return handled;
    }

    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        // request that the service stop the query with this callback object.
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        finish();
    }

    private String getDisplayStringFromAct(int act) {
        if (act == NetworkInfo.ACT_UTRAN) {
   	    	return "3G";
        } else {
   	    	return "2G";
    	}
    }

    public String getNormalizedCarrierName(NetworkInfo ni) {
        if (ni != null) {
        	Resources res = getResources();
        	String state = getNetworkState(ni.getState());
        	if (TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
        		String numberIC = ni.getOperatorNumeric().trim();
        		if(CHINA_MOBILE_IC.equals(numberIC)) {
        			return res.getText(R.string.china_mobile_provider) + " " 
        			+ getDisplayStringFromAct(ni.getAct()) + state;
        		}
        		else if (CHN_CUGSM_IC.equals(numberIC)) {
        			return res.getText(R.string.chn_cugsm_provider) + " " 
        			+ getDisplayStringFromAct(ni.getAct()) + state;	
        		}
        		else{
        			return numberIC + " " + getDisplayStringFromAct(ni.getAct()) + state;	
        		}
        	} else {
        		String alphaLong = ni.getOperatorAlphaLong().trim();
        		if(CHINA_MOBILE.equals(alphaLong)) {
        			return res.getText(R.string.china_mobile_provider) + " " 
        			+ getDisplayStringFromAct(ni.getAct()) + state;
        		}
        		else if(CHN_CUGSM.equals(alphaLong)) {
        			return res.getText(R.string.chn_cugsm_provider) + " " 
        			+ getDisplayStringFromAct(ni.getAct()) + state;	
        		}
        		else {
        			return alphaLong + " " + getDisplayStringFromAct(ni.getAct()) + state;
        		}
        	}
        }
        return null;
    }
    
    public String getNetworkState(NetworkInfo.State state) {
    	Resources res = getResources();
    	if(state == null) {
    		return "";
    	}
    	else if(state == NetworkInfo.State.FORBIDDEN) {
    		return ""+" (" + res.getText(R.string.network_inhibit) + ")";
    	}
    	else if(state == NetworkInfo.State.UNKNOWN) {
    		return ""+" (" + res.getText(R.string.network_unknown) + ")";
    	}
    	else {
    		return "";
    	}
    }
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.carrier_select);
        mSubId   = getIntent().getIntExtra(MobileNetworkSettings.SUB_ID, 0);
        if (TelephonyManager.getPhoneCount() > 1) {
            if (mSubId == 0) {
                setTitle(getResources().getString(R.string.sim1)
                        + getResources().getString(R.string.label_available));
            } else if (mSubId == 1) {
                setTitle(getResources().getString(R.string.sim2)
                        + getResources().getString(R.string.label_available));
            }
        }
        mPhone = PhoneApp.getInstance().getPhone(mSubId);

        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
        mNetworkMap = new HashMap<Preference, NetworkInfo>();

        mSearchButton = getPreferenceScreen().findPreference(BUTTON_SRCH_NETWRKS_KEY);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);
        
        // Start the Network Query service, and bind it.
        // The OS knows to start he service only once and keep the instance around (so
        // long as startService is called) until a stopservice request is made.  Since
        // we want this service to just stay in the background until it is killed, we
        // don't bother stopping it from our end.
        
        //add by liguxiang 11-14-11 for NEWMS00139542 begin 
        mTelephonyManager = (TelephonyManager)getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mSubId));
        Log.d("ligx","*************** has Icc Card :" + mTelephonyManager.hasIccCard());
		if (mTelephonyManager.hasIccCard()) {
		    Intent intent = new Intent(this, NetworkQueryService.class);
		    intent.putExtra(MsmsConstants.SUBSCRIPTION_KEY, mSubId);
			startService(intent);
			bindService(new Intent(this, NetworkQueryService.class),
					mNetworkQueryServiceConnection, Context.BIND_AUTO_CREATE);
		} else {
			mSearchButton.setEnabled(false);
			mAutoSelect.setEnabled(false);
		}
		//add by liguxiang 11-14-11 for NEWMS00139542 end
                dialogLoaded = new ProgressDialog(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    /**
     * Override onDestroy() to unbind the query service, avoiding service
     * leak exceptions.
     */
    @Override
    protected void onDestroy() {
        // unbind the service.
    	//add by liguxiang 11-14-11 for NEWMS00139542 begin
        if (mTelephonyManager.hasIccCard()) {
            unbindService(mNetworkQueryServiceConnection);
        }
        if ((dialogLoaded != null) && (dialogLoaded.isShowing())) {
            dialogLoaded.dismiss();
        }
    	//add by liguxiang 11-14-11 for NEWMS00139542 end
        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    // It would be more efficient to reuse this dialog by moving
                    // this setMessage() into onPreparedDialog() and NOT use
                    // removeDialog().  However, this is not possible since the
                    // message is rendered only 2 times in the ProgressDialog -
                    // after show() and before onCreate.
                    dialog.setMessage(mNetworkSelectMsg);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_AUTO_SELECT:
                    dialog.setMessage(getResources().getString(R.string.register_automatically));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                default:
            }
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to dissallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        mNetworkList.setTitle(flag ? R.string.empty_networks_list : R.string.label_available);
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        // TODO: use notification manager?
        mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_SELECTION);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);

        PhoneApp.getInstance().notificationMgr.postTransientNotification(
                        NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME)
        {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }
        PhoneApp.getInstance().notificationMgr.postTransientNotification(
                        NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
        getPreferenceScreen().setEnabled(true);
    }

    private void displayNetworkSelectionSucceeded() {
        String status = getResources().getString(R.string.registration_done);

        PhoneApp.getInstance().notificationMgr.postTransientNotification(
                        NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 1000);
    }
    public void creatDialog() {
        dialogLoaded.setMessage(getResources().getString(R.string.load_networks_progress));
        dialogLoaded.setCancelable(true);
        dialogLoaded.setOnCancelListener(this);
    }

    private void loadNetworksList() {
        if (DBG) log("load networks list...");
        creatDialog();
        if (mIsForeground) {
            dialogLoaded.show();
            getPreferenceScreen().setEnabled(false);
        }

        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback);
        } catch (RemoteException e) {
        }

        displayEmptyNetworkList(false);
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * NetworkInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * NetworkInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<NetworkInfo> result, int status) {
        if (DBG) log("networks list loaded");

        // update the state of the preferences.
        if (DBG) log("hideProgressPanel");
        if (dialogLoaded != null) {
            log("dialog is showing " + dialogLoaded.isShowing());
            dialogLoaded.dismiss();
        }
        getPreferenceScreen().setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) log("error while querying available networks");
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null){
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                for (NetworkInfo ni : result) {
                    Preference carrier = new Preference(this, null);
                    //carrier.setTitle(ni.getOperatorAlphaLong());
                    carrier.setTitle(getNormalizedCarrierName(ni));
                    carrier.setPersistent(false);
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);

                    if (DBG) log("  " + ni);
                }

            } else {
                displayEmptyNetworkList(true);
            }
        }
    }

    private void clearList() {
        for (Preference p : mNetworkMap.keySet()) {
            mNetworkList.removePreference(p);
        }
        mNetworkMap.clear();
    }

    private void selectNetworkAutomatic() {
        if (DBG) log("select network automatically...");
        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_AUTO_SELECT);
        }

        Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
        mPhone.setNetworkSelectionModeAutomatic(msg);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }
}

