//added for the dual sim
package com.android.contacts.util;

import android.telephony.TelephonyManager;

public interface Config {
    
    public static boolean isMSMS = TelephonyManager.getPhoneCount() > 1;
    
}
