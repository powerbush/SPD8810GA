package com.android.phone;
import android.provider.Telephony;
import static android.provider.Telephony.Intents.SECRET_CODE_ACTION;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SimCardLockedReciver extends BroadcastReceiver {

    public SimCardLockedReciver(){

    }
    public void onReceive(Context context, Intent intent) {
        Log.i("SimCardLockedReciver","onReceive");
        if (intent.getAction().equals(SECRET_CODE_ACTION)) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(context, ChooseForLockOrUnlock.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}