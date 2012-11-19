package com.android.mms.util;

/**
 * 
 * @author jinwei.li
 * enable or disable some feature
 */
public class FeatureSwitch {
	public static final String TAG = "FeatureSwitch";

	/**
	 * if true, Mms enable tip that display on status bar when composing message
	 */
	public static final boolean COMPOSE_MESSAGE_TIP_SUPPORT = true;
	
	/**
	 * if true, Mms enable message be sent if message couldn't be sent correctly
	 */
	public static final boolean MESSAGE_RESEND_SUPPORT = true;
	
	/**
	 * if true, support sent message and quit for rejecting call
	 */
	public static final boolean PHONE_REJECT_AUTO_SENT_SUPPORT = true;
	
	/**
	 * if true, support block message if this number belongs to blacklist and set block message
	 */
	public static final boolean BLOCK_MESAGE = true;
}
