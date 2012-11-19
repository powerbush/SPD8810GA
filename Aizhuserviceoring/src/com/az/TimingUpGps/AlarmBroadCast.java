package com.az.TimingUpGps;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmBroadCast extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
	
		//AlarmService as=new AlarmService(context, intent);
		//String lai=intent.getAction();
		//if(intent.getAction().equals("android.intent.action.BOOT_COMPLETED")){
			Intent service=new Intent(context,AlarmService.class);
			context.startService(service);
			//Log.i("intent.getAction()", intent.getAction());
		//}
	}

}
