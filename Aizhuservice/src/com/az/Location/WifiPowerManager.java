package com.az.Location;

import android.os.PowerManager;
import android.net.wifi.WifiManager;
import android.content.Context;


public class WifiPowerManager {
	private Context mContext;
	private PowerManager.WakeLock mWakeLock = null;
	private WifiManager.WifiLock mWifiLock = null;
	
	private final static String WAKELOCK_KEY = "PowerManagerService";
	private final static String WIFILOCK_KEY = "WifiManagerService";
	
	public WifiPowerManager(Context context){
		mContext = context;
		//Create a wake lock
		PowerManager powerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);		
		mWakeLock.setReferenceCounted(true);
		
		//Create a wifi lock
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		mWifiLock = wifiManager.createWifiLock(WIFILOCK_KEY);
		mWifiLock.setReferenceCounted(true);
	}
	
	public void acquire(){
		// Acquire wake lock
		mWakeLock.acquire();
		// Acquire wifi lock
		mWifiLock.acquire();
	}
	
	public void release(){
		//Release wifi lock
		mWifiLock.release();
		//Release wake lock
		mWakeLock.release();
	}
}
