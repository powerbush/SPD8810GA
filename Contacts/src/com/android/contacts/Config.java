package com.android.contacts;

import android.telephony.TelephonyManager;

public interface Config {

    public static boolean isSupport3G = TelephonyManager.getDefault().getModemType() == TelephonyManager.MODEM_TYPE_TDSCDMA;

    public static final String EXTRA_IS_VIDEOCALL = "android.phone.extra.IS_VIDEOCALL";
}
