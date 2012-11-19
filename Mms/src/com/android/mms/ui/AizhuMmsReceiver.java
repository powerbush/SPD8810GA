package com.android.mms.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

//import android.os.Bundle;//by lai
//import android.telephony.SmsMessage;//by lai
//import android.content.SharedPreferences;//by lai
//import android.preference.PreferenceManager;//by lai

//import android.util.Log;

public class AizhuMmsReceiver extends BroadcastReceiver{	
        //private SmsMessage smsMessage;//by lai
        //private String phoneAddress;//by lai
        //private String smsBody;//by lai
        //private Bundle bundle;//by lai
        //private Object[] objs;//by lai
        //private BlockListData blockListData;//by lai
        private Intent intent;
	private Context context;
	
	@Override
	public void onReceive(Context context, Intent intent) {
	this.context = context;
	this.intent = intent;
	/*
	Object[] messages = (Object[]) intent.getSerializableExtra("pdus");   
        byte[][] pduObjs = new byte[messages.length][];   
        for (int i = 0; i < messages.length; i++) {   
             pduObjs[i] = (byte[]) messages[i];   
        }   
        byte[][] pdus = new byte[pduObjs.length][];   
        int pduCount = pdus.length;   
        SmsMessage[] msgs = new SmsMessage[pduCount];   
        for (int i = 0; i < pduCount; i++) {   
             pdus[i] = pduObjs[i];   
             msgs[i] = SmsMessage.createFromPdu(pdus[i]);   
             if(i==0){
                 phoneAddress=msgs[i].getDisplayOriginatingAddress();
                 smsBody=msgs[i].getDisplayMessageBody();
             }else{
                 smsBody=smsBody + msgs[i].getDisplayMessageBody();
             }
        }   
		SharedPreferences prs= PreferenceManager.getDefaultSharedPreferences(context);
		String textphone=prs.getString("text_input_phone_pr", "");
		if(phoneAddress.equals(textphone)||phoneAddress.equals("+86"+textphone)){
			//abortBroadcast(); //拦截短信不让Broadcast往下发
			blockListData=new BlockListData(context);
			blockListData.MoveSMSInfo(phoneAddress,smsBody);
			return;
    	}
	*/	
		if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")){
			Bundle bundle=intent.getExtras();
			Object[] objs=(Object[]) bundle.get("pdus");
			for(Object ob:objs){
				byte[] pdu=(byte[])ob;
				SmsMessage msg=SmsMessage.createFromPdu(pdu);
				String phoneAddress=msg.getDisplayOriginatingAddress();
				intent.setClass(context,AizhuSmsFrameActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra("phoneAddress", phoneAddress);
				context.startActivity(intent);
				break;
			}
		}
	}
}


		
	