package com.az.SmsGetLocation;

import java.util.ArrayList;

import com.az.TimingUpGps.AlarmService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.telephony.SmsMessage;


public class SMSBroadcastRec extends BroadcastReceiver{

	private SmsMessage msg;
	private String phoneAddress;
	private Context context;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		this.context=context;
		if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")){
			Bundle bundle=intent.getExtras();
			Object[] objs=(Object[]) bundle.get("pdus");
			for(Object ob:objs){
				byte[] pdu=(byte[])ob;
				msg=SmsMessage.createFromPdu(pdu);
				phoneAddress=msg.getDisplayOriginatingAddress();
				if(msg.getDisplayMessageBody().contains("gps")){
					if(CheckPhoneNumvAlidity()==true){
						abortBroadcast(); //拦截短信不让Broadcast往下发
						Intent SmsIntent = new Intent(this.context,SMSService.class);
						SmsIntent.putExtra("phoneAddress", phoneAddress);
						this.context.startService(SmsIntent);
					}
				}
			}
		}
	}
	
	private boolean CheckPhoneNumvAlidity()//注意此处不能太耗时
	{
		SQLiteDatabase sqldb=this.context.openOrCreateDatabase("emergencyphb.db",1, null);
		Cursor cur = sqldb.query("emerphb",new String[]{"name","phonenum"},null,null,null,null,null);	 
		ArrayList<String> ar=new ArrayList<String>();
		//是否是紧急联系人数据库保存的电话
		 boolean availability = false;
		 //数据库中的
		while(cur.moveToNext()){
			ar.add(cur.getString(cur.getColumnIndex("name")));
			//Log.i("life",cur.getString(cur.getColumnIndex("name")));
			String s=cur.getString(cur.getColumnIndex("phonenum"));
			if(s.contains(phoneAddress)||("+86"+s).contains(phoneAddress)){
				availability=true;
			}
		}
		cur.close();
		sqldb.close();
		return availability;
	}
	
}	
	
