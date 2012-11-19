package com.android.mms.transaction;

import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.util.Recycler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.Phone;

public class SendMessageIntentReceiver extends BroadcastReceiver {
    private static final String TAG = "Mms/SendMessageIntentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        //Log.d("TAG","onReceive()");
        String number = intent.getStringExtra("number");
        String body = intent.getStringExtra("sms_body");
	int phoneId = intent.getIntExtra(Phone.PHONE_ID,0);

        Conversation conversation;
        if (!TextUtils.isEmpty(number)) {
            conversation = Conversation.get(context,ContactList.getByNumbers(number, false, true), false);
        } else {
            conversation = Conversation.createNew(context);
        }
        long threadId = conversation.ensureThreadId();
        String[] dests = conversation.getRecipients().getNumbers();
        MessageSender sender;
        sender = new SmsMessageSender(context, dests, body, threadId,phoneId);
        try {
            sender.sendMessage(threadId);
            // Make sure this thread isn't over the limits in message count
            Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        } catch (Exception e) {
            Log.e(TAG,"sms send failed.",e);
        }
    }
}
