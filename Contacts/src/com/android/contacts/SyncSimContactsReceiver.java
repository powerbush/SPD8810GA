package com.android.contacts;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import android.telephony.ServiceState;
import android.provider.Telephony.Intents;

public class SyncSimContactsReceiver extends BroadcastReceiver {
	private static final String TAG = "SyncSimContactsReceiver";
	private static final boolean VERBOSE = true;
	private static final Object mStartingServiceSync = new Object();

	private static PowerManager.WakeLock mStartingService;
	private static SyncSimContactsReceiver sInstance;
	private boolean isDualSim = false;



	public static SyncSimContactsReceiver getInstance() {
		if (sInstance == null) {
			sInstance = new SyncSimContactsReceiver();
		}
		return sInstance;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		if (intent == null) {
			return;
		}
		setDualSimFlag();
		String action = intent.getAction();
		Log.d(TAG, "action = " + action);
		if (action != null && !action.equals("")) {
			boolean isStartService = false;
			
			if(!isDualSim){
				if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
					String stateExtra = intent
							.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
					if (VERBOSE) {
						Log.v(TAG, "IccCard.INTENT_KEY_ICC_STATE = " + stateExtra);
					}
					//add by niezhong for NEWMS00118673 09-17-11 begin
					if (IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
						isStartService = true;
					}
					//add by niezhong for NEWMS00118673 09-17-11 end				
				} else if (action.equals(Intent.ACTION_BOOT_COMPLETED)
						|| action.equals(Intent.ACTION_SHUTDOWN)
								|| action.equals("com.android.phone.reboot.query.sim")
								) {
					isStartService = true;
				}else if(action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)){
                    isStartService = true;
				} else if(action.equals(TelephonyIntents.ACTION_STK_REFRESH_SIM_CONTACTS)) {
					Log.d(TAG,"[stk]receive ACTION_STK_REFRESH_SIM_CONTACTS");
					isStartService = true;
				} else if ("android.intent.action.FDN_STATE_CHANGED0".equals(action)
                        || "android.intent.action.FDN_STATE_CHANGED1".equals(action)) {
				    isStartService = true;
	            }
			}else{
				if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED) ||
                                              action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED0) ||
						action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED1)) {
					String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
					int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, -1);
					Log.v(TAG, "IccCard.INTENT_KEY_PHONE_ID = " + phoneId);
					Log.v(TAG, "IccCard.INTENT_KEY_ICC_STATE = " + stateExtra);
					if (IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
						isStartService = true;
						//SIM state do not send to service
					}
				} else if (action.equals(Intent.ACTION_BOOT_COMPLETED)
						|| action.equals(Intent.ACTION_SHUTDOWN)
						|| action.equals("com.android.phone.reboot.query.sim")) {
					isStartService = true;
	            		} else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
	                	//	if (!intent.getBooleanExtra("state", false)) {
					Log.d(TAG, "receiver ACTION_AIRPLANE_MODE_CHANGED intent");
	                    		isStartService = true;
	                	//	}
				} else if (TelephonyIntents.SIM_ADNCACHE_LOADED.equals(action)) {
					int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, -1);
					Log.v(TAG, "IccCard.INTENT_KEY_PHONE_ID = " + phoneId);
					//isStartService = true;
				} else if ("android.intent.action.FDN_STATE_CHANGED0".equals(action)
					|| "android.intent.action.FDN_STATE_CHANGED1".equals(action)) {
					isStartService = true;
				}else if(action.equals("android.intent.action.SelectSimCard")){
					//for enable or disable sim card
					Log.d(TAG, "Enter SelectSimCard");
					isStartService = true;
				}
			}
			Log.d(TAG, "before startService is " + isStartService);
			if (isStartService) {
				intent.setClass(context, SyncSimContactsService.class);
				// beginStartingService(context, intent);
				context.startService(intent);
			}
		}
	}


	private void setDualSimFlag(){
		if(TelephonyManager.getPhoneCount()>1)
			isDualSim = true;
		Log.w(TAG, "isDualSim = " + isDualSim);
		return;	
	}
	
	
	

	/**
	 * Start the service to process the current event notifications, acquiring
	 * the wake lock before returning to ensure that the service will run.
	 */
	public static void beginStartingService(Context context, Intent intent) {
		Log.d(TAG, "beginStartingService");
		synchronized (mStartingServiceSync) {
			if (mStartingService == null) {
				PowerManager pm = (PowerManager) context
						.getSystemService(Context.POWER_SERVICE);
				mStartingService = pm.newWakeLock(
						PowerManager.PARTIAL_WAKE_LOCK,
						"StartingSimContactsService");
				mStartingService.setReferenceCounted(false);
			}
			mStartingService.acquire();
			context.startService(intent);
		}
	}

	/**
	 * Called back by the service when it has finished processing notifications,
	 * releasing the wake lock if the service is now stopping.
	 */
	public static void finishStartingService(Service service, int startId) {
		Log.d(TAG, "finishStartingService::id = " + startId);
		/*
		 * synchronized (mStartingServiceSync) { if (mStartingService != null) {
		 * if (service.stopSelfResult(startId)) { mStartingService.release(); }
		 * } }
		 */
		service.stopSelfResult(startId);
	}

}
