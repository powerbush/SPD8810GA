
package com.android.phone;

import java.util.Hashtable;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class SelectSimCard extends PreferenceActivity implements View.OnClickListener{
    private static final String STANDBY_CHECKED = "standbyChecked";
    ImageButton mSim1Checked;
    ImageButton mSim2Checked;
    CheckBoxPreference mStandbyChecked;
    
    private boolean standbyCheckedStatus = false;
    private boolean standbyCheckedStatusOld = false;

    private boolean isStandbySim1Card=true;
    
    private boolean isStandbySim2Card=true;
    
    private boolean hasCard1 = false;
    private boolean hasCard2 = false;

    private boolean isAirplaneModeOn;

    private boolean isStandby[];
    
    private int phoneCount;

    private Phone mPhones[];
    private IntentFilter mIntentFilter;
    private TextView mOkButton, mCancelButton;
    private static final int EVENT_SET_SUBSCRIPTION_DONE = 100;
    private static final int EVENT_SET_SUBSCRIPTION_TIMEOUT = 200;
    private static final int DIALOG_WAIT_MAX_TIME= 60000;
    private static final String LOG_TAG = "SelectSimCard";

    Hashtable<Integer,Boolean> waitTable = new Hashtable<Integer,Boolean>();
    Hashtable<Integer,Boolean> busyTable = new Hashtable<Integer,Boolean>();
    private PhoneStateListener[] mPhoneStateListener;
    private TelephonyManager[] mTelephonyManagers;
    private boolean mIsForeground;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_DONE:
                  Log.d(LOG_TAG,"EVENT_SET_SUBSCRIPTION_DONE");
                  finishSettingsWait();
                  break;
                case EVENT_SET_SUBSCRIPTION_TIMEOUT:
                    if (mIsForeground)
                        Toast.makeText(SelectSimCard.this, R.string.sim_state_set_timeout, Toast.LENGTH_LONG).show();
                    Log.d(LOG_TAG,"EVENT_SET_SUBSCRIPTION_TIMEOUT");
                    finishSettingsWait();
                    break;
            }

            return;
        }
    };

    private void finishSettingsWait() {
        Log.d(LOG_TAG, "Finish dual settings wait.");
        PhoneFactory.autoSetDefaultPhoneId(true);
        removeDialog(0);
        closeTimer();
        mSubDialog = null;
        waitTable.clear();
        busyTable.clear();
        finish();
    }

    private BroadcastReceiver mBroadcastReceiver=new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG,"onReceive "+action);
            if(Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)){
                if (mIsForeground) {
                    isAirplaneModeOn = Settings.System.getInt(getContentResolver(),
                            Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                    if (hasCard1&& !isAirplaneModeOn) {
                        mSim1Checked.setOnClickListener(SelectSimCard.this);
                     }
                    if (hasCard2&& !isAirplaneModeOn) {
                        mSim2Checked.setOnClickListener(SelectSimCard.this);
                     }
                    mStandbyChecked.setEnabled(!isAirplaneModeOn);
                    mOkButton.setEnabled(!isAirplaneModeOn);
                    updateStatus();
                }else{
                    finish();
                }
            }
        }
    };

    private ProgressDialog mSubDialog;

    @Override
    protected Dialog onCreateDialog(int id) {
        if (mSubDialog==null) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.updating_dualsim_title));
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(getText(R.string.updating_settings));
            mSubDialog = dialog;
        }
        return mSubDialog;
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"activity onDestroy()");
        super.onDestroy();
        if (mPhoneStateListener!=null&&mTelephonyManagers!=null) {
            for (int i =0;i<mTelephonyManagers.length;i++) {
                mTelephonyManagers[i].listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
                mPhoneStateListener[i] = null;
                mTelephonyManagers[i] = null;
            }
        }
        mTelephonyManagers = null;
        mPhoneStateListener = null;
    }
    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(mBroadcastReceiver);
        mIsForeground = false;
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
    }

//    private void handleResult(AsyncResult ar) {
//        if (ar.exception == null) {
//            Log.v(LOG_TAG, "handleResult: success!");
//            // TODO: show success feedback
//            if (null != mPhones[0]) {
//                //cienet add liqiangwu 2011-6-13:
////                mPhones[0].setPreferredRadioPower(mSim1Checked.isChecked());
//            }
//            if (null != mPhones[1]) {
////                mPhones[1].setPreferredRadioPower(mSim2Checked.isChecked());
//            }
//            //cienet end liqiangwu.
//            Intent i = new Intent(Intent.ACTION_REBOOT);
//            i.putExtra("nowait", 1);
//            i.putExtra("interval", 1);
//            i.putExtra("window", 0);
//            sendBroadcast(i);
//
//        } else if (ar.exception instanceof CommandException) {
//            Log.v(LOG_TAG, "handleResult: failed!");
//
//            CommandException ce = (CommandException) ar.exception;
//            if (ce.getCommandError() == CommandException.Error.GENERIC_FAILURE) {
//                Log.v(LOG_TAG, "handleResult: generic failure!");
//                // TODO: show generic failure feedback
//                Toast.makeText(this, R.string.sim_state_changed_failed, Toast.LENGTH_LONG).show();
//            }
//        }
//    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.selectsimcard);
        setContentView(R.layout.set_subscription_pref_layout);

        hasCard1 = PhoneFactory.isCardExist(0);
        hasCard2 = PhoneFactory.isCardExist(1);

        isAirplaneModeOn = Settings.System.getInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;

        mOkButton = (TextView) findViewById(R.id.ok);
        mOkButton.setOnClickListener(this);
        mCancelButton = (TextView) findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(this);
        mSim1Checked = (ImageButton) findViewById(R.id.sub_0);
        if (hasCard1&& !isAirplaneModeOn) {
            mSim1Checked.setOnClickListener(this);
         }
        mSim2Checked = (ImageButton) findViewById(R.id.sub_1);
        if (hasCard2&& !isAirplaneModeOn) {
            mSim2Checked.setOnClickListener(this);
         }
        mStandbyChecked=(CheckBoxPreference)findPreference(STANDBY_CHECKED);

        phoneCount = PhoneFactory.getPhoneCount();
        isStandby = new boolean[phoneCount];
        mPhoneStateListener = new PhoneStateListener[phoneCount];
        mTelephonyManagers = new TelephonyManager[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            isStandby[i] = System.getInt(getContentResolver(),
                    PhoneFactory.getSetting(System.SIM_STANDBY, i), 1) == 1;
            mPhoneStateListener[i] = getPhoneStateListener(i);
            // register for phone state notifications.
            mTelephonyManagers[i] = (TelephonyManager) this.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, i));
            mTelephonyManagers[i].listen(mPhoneStateListener[i],
                    PhoneStateListener.LISTEN_SERVICE_STATE);
        }
            isStandbySim1Card=isStandby[0];
            isStandbySim2Card=isStandby[1];

        standbyCheckedStatusOld = System.getInt(getContentResolver(),
                System.POWER_ON_STANDBY_SELECT, 0) == 1;
        standbyCheckedStatus = standbyCheckedStatusOld;
        //receiver intent
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    private PhoneStateListener getPhoneStateListener(final int phoneId) {
        PhoneStateListener phoneStateListener = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState state) {
                Log.d(LOG_TAG, "SelectSimCard onServiceStateChanged Received on SIM_" + phoneId +" state:"+state.getState());
                checkServiceState(phoneId);
            }
        };
        return phoneStateListener;
    }

    private int getIconId(int phoneId,boolean isCheck) {
        mStandbyChecked.setEnabled(!isAirplaneModeOn);
        if ((!hasCard2 && !hasCard1) || isAirplaneModeOn) {
            mOkButton.setEnabled(false);
        }else{
            mOkButton.setEnabled(true);
        }
        if (phoneId==1) {
            if (!hasCard2||isAirplaneModeOn) {
                mSim2Checked.setEnabled(false);
                return R.drawable.dual_sim2_invalid;
            }
            mSim2Checked.setEnabled(true);
            if (isCheck) {
                return R.drawable.dual_sim2_checked;
           } else {
                return R.drawable.dual_sim2;
            }
        }else{
            if (!hasCard1||isAirplaneModeOn) {
                mSim1Checked.setEnabled(false);
                return R.drawable.dual_sim1_invalid;
            }
            mSim1Checked.setEnabled(true);
            if (isCheck) {
                return R.drawable.dual_sim1_checked;
	       } else {
	            return R.drawable.dual_sim1;
	        }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG,"activity onResume()");
        mStandbyChecked.setChecked(standbyCheckedStatus);
        if (TelephonyManager.getPhoneCount() > 1) {
            mPhones = new Phone[PhoneFactory.getPhoneCount()];
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mPhones[i] = (PhoneFactory.getPhones())[i];
             }
            updateStatus();
        }
        this.registerReceiver(mBroadcastReceiver, mIntentFilter);
        mIsForeground = true;
    }

    private void updateStatus() {
        Log.d(LOG_TAG,"updateStatus : isStandbySim1Card="+isStandbySim1Card+" isStandbySim2Card="+isStandbySim2Card);
        mSim1Checked.setImageResource(getIconId(0,isStandbySim1Card));
        mSim2Checked.setImageResource(getIconId(1,isStandbySim2Card));
    }

    public void onClick(View v) {
//         if(mSubDialog != null){
//             return;
//         }

        if (v == mOkButton) {
            standbyCheckedStatus = mStandbyChecked.isChecked();
            if (standbyCheckedStatusOld== standbyCheckedStatus&&isStandby[0] == isStandbySim1Card&&isStandby[1] == isStandbySim2Card){
                Toast.makeText(getApplicationContext(), R.string.sim_state_not_changes, Toast.LENGTH_LONG).show();
                finish();
                return;
             }
            System.putInt(getContentResolver(), System.Standby_Select_Card_Show, 1);//set Standby_Select_Card_Show to true ,so can`t pop this time
            setSubscription();

        } else if (v == mCancelButton) {
            finish();
        } else if (v == mSim1Checked) {
            isStandbySim1Card = !isStandbySim1Card;
            mSim1Checked.setImageResource(getIconId(0,isStandbySim1Card));
        } else if (v == mSim2Checked) {
            isStandbySim2Card = !isStandbySim2Card;
            mSim2Checked.setImageResource(getIconId(1,isStandbySim2Card));
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if(preference == mStandbyChecked){//save standbyCheckedStatus when other application does works;
            standbyCheckedStatus = mStandbyChecked.isChecked();
        }
       return true;
    }

    private Timer timer;
    private TimerTask timerTask;

    private void startTimer() {
        closeTimer();
        timer = new Timer(true);
        timerTask = new TimerTask() {
            public void run() {
                //force execute busy act
                execBusyAct(true);
                mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_TIMEOUT);
            }
        };
        Log.d(LOG_TAG, "startTimer,timer start");
        timer.schedule(timerTask, DIALOG_WAIT_MAX_TIME);
    }

    private void closeTimer() {
        Log.d(LOG_TAG, "closeTimer,timer end");
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private void setSubscription() {
        try{
            if (isStandby[0] != isStandbySim1Card) {
                  if (isStandbySim1Card) {
                      System.putInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 0), 1);
                  } else {
                      System.putInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 0), 0);
                  }
              }
            if (isStandby[1] != isStandbySim2Card) {
                  if (isStandbySim2Card) {
                      System.putInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 1), 1);
                  } else {
                      System.putInt(getContentResolver(), PhoneFactory.getSetting(System.SIM_STANDBY, 1), 0);
                    }
              }

           if (standbyCheckedStatusOld!= standbyCheckedStatus) {
               if (standbyCheckedStatus) {
                    System.putInt(getContentResolver(), System.POWER_ON_STANDBY_SELECT, 1);
                } else {
                    System.putInt(getContentResolver(), System.POWER_ON_STANDBY_SELECT, 0);
                  }
             }
           if (Config.LOGD) {
                   Log.d(LOG_TAG, "isStandby[0]= " + isStandby[0] + " isStandby[1] ="
                           + isStandby[1] + " isStandbySim1Card =" + isStandbySim1Card
                           + " isStandbySim2Card =" + isStandbySim2Card);
               }

            if (isStandby[0] != isStandbySim1Card||isStandby[1] != isStandbySim2Card) {
                Intent intent=new Intent("android.intent.action.SelectSimCard");
                if (isStandby[0] != isStandbySim1Card) {
                    waitTable.put(0, isStandbySim1Card);
                    busyTable.put(0, isStandbySim1Card);
                    intent.putExtra("SIM1", true);//true ,StandbySim1Card has changed
                  }
                if (isStandby[1] != isStandbySim2Card) {
                    waitTable.put(1,isStandbySim2Card);
                    busyTable.put(1,isStandbySim2Card);
                    intent.putExtra("SIM2", true);//true ,StandbySim2Card has changed
                  }
                setDefaultSim();
                startChangeSimStandby();
                //for ds-contacts,
                Log.d(LOG_TAG,"setSubscription:sent StandBySimCard action,sim1="+intent.getBooleanExtra("SIM1", false)+" ,sim2="+intent.getBooleanExtra("SIM2", false));
                this.sendBroadcast(intent);
            }else{
                Log.d(LOG_TAG,"only change POWER_ON_STANDBY_SELECT");
                mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_DONE);
            }
        }catch(Exception e){
            mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_DONE);
            Log.d(LOG_TAG,"run finish exception:"+e);
            e.printStackTrace();
         }
    }

    private void setDefaultSim() {
        if (Config.LOGD)
            Log.d(LOG_TAG, "setDefaultSim:hasCard1 " + hasCard1 + ",hasCard2 " + hasCard2
                    + ",isStandbySim1Card " + isStandbySim1Card + ",isStandbySim2Card "
                    + isStandbySim2Card);
        if (hasCard1 || hasCard2) {
            if (isStandbySim1Card && !isStandbySim2Card) {
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_MMS, 0);
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_TEL, 0);
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VTEL, 0);
            } else if (!isStandbySim1Card && isStandbySim2Card) {
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_MMS, 1);
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_TEL, 1);
                TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VTEL, 1);
            }
        }
    }
    private void startChangeSimStandby() {
        mOkButton.setEnabled(false);
        Log.d(LOG_TAG,"startChangeSimStandby");
        try{
            showDialog(0);
        } catch (RuntimeException e){
            Log.d(LOG_TAG,"An exception occurs");
        }
        execBusyAct(false);
        startTimer();
    }

    private void execBusyAct(boolean isForce) {
        boolean isStart = false;
        if (!isForce&&busyTable.size()>1) {
            Set<Integer> keySet = busyTable.keySet();
            for(Integer key:keySet) {
                boolean onOrOff = busyTable.get(key);
                if (!onOrOff) {
                    mPhones[key].setRadioPower(onOrOff);
                    busyTable.remove(key);
                    isStart = true;
                    Log.d(LOG_TAG,"start change Sim "+key+" power to "+(onOrOff?"On":"Off"));
                    break;
                }
            }
            if (!isStart) {
                for(Integer key:keySet) {
                    boolean onOrOff = busyTable.get(key);
                    mPhones[key].setRadioPower(onOrOff);
                    busyTable.remove(key);
                    Log.d(LOG_TAG,"start change Sim "+key+" power to "+(onOrOff?"On":"Off"));
                    break;
                }
            }
        }else{
            Set<Integer> keySet = busyTable.keySet();
            for(Integer key:keySet) {
                boolean onOrOff = busyTable.get(key);
                mPhones[key].setRadioPower(onOrOff);
                busyTable.remove(key);
                Log.d(LOG_TAG,"start change Sim "+key+" power to "+(onOrOff?"On":"Off"));
            }
        }
    }

//    public void onDismiss(DialogInterface dialog) {
//
//    }
//
//    public void onClick(DialogInterface dialog, int which) {
//
//    }

    private void checkServiceState(int phoneId) {
        if (phoneId>=0&&waitTable.size()>0) {
            Boolean simCardOpen = waitTable.get(phoneId);
            if (simCardOpen!=null) {
                if (hasService(mPhones[phoneId].getServiceState())==simCardOpen) {
                    waitTable.remove(phoneId);
                    Log.d(LOG_TAG,"change Sim "+phoneId+" power finish success!");
                    if (busyTable.size()>0) {
                        execBusyAct(false);
                    }
                }
            }
            if (waitTable.size()==0) {
                mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_DONE);
                Log.d(LOG_TAG,"change Sim power all finish success!");
            }
        }
    }

    private boolean hasService(ServiceState ss) {
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }
}
