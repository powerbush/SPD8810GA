// add by niezhong 0907 for NEWMS00120274 begin
package com.android.contacts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

public class SimInitReceiver extends BroadcastReceiver{
	
	private boolean mFlag = true;
	
	private static final String TAG = "SimInitReceiver";
	
	public static boolean isDelete = false; //modify by dory.zheng for NEWMS00138007
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		Log.d(TAG, "action = " + action);
		try {
			if("com.android.contacts.SIM_OPERATE_START".equals(action)) {
				mFlag = false;
				Settings.System.putString(context.getContentResolver(), 
						"sim_init_state", ""+mFlag);
				context.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_START_PUT"));
			}
			else if("com.android.contacts.SIM_OPERATE_END".equals(action)){
				mFlag = true;
				Settings.System.putString(context.getContentResolver(), 
						"sim_init_state", ""+mFlag);
				context.sendBroadcast(new Intent("com.android.contacts.SIM_OPERATE_END_PUT"));
			}
			else if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
				Settings.System.putString(context.getContentResolver(), 
						"sim_init_state", "true");
			}
			//modify by dory.zheng for NEWMS00138007 begin
			else if("com.android.contacts.DELETE_START".equals(action)){
			    isDelete = true;
			} else if ("com.android.contacts.DELETE_END".equals(action)){
			    isDelete = false;
			}
			//modify by dory.zheng for NEWMS00138007 end
		}
		catch (Exception e) {
			Log.e(TAG, "[mSimOperateStateReceiver] caught " + e);
            e.printStackTrace();
		}
		
	}
}
// add by niezhong 0907 for NEWMS00120274 end