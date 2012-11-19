package com.az.TimingUpGps;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootingBroadCast extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		if(intent.getAction().equals("android.intent.action.BOOT_COMPLETED")){
			Intent service=new Intent(context,SetAlarmTimeService.class);
			context.startService(service);
			//Log.i("life", "开机启动成功");
		}
		else if(intent.getAction().equals("android.intent.action.PACKAGE_RESTARTED")){
			Intent service=new Intent(context,SetAlarmTimeService.class);
			context.startService(service);
			//Log.i("life", "开机启动成功");
		}
		
	}
	
}