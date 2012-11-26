package com.az.TimingUpGps;


import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.az.Location.ABLocationManager;
import com.az.Location.ACellInfo;
import com.az.Location.AGps;
import com.az.Location.AUtilTool;
import com.az.Location.CellInfoManager;
import com.az.Location.CellLocationManager;
import com.az.Location.WifiInfoManager;
import com.az.Location.WifiPowerManager;
import com.az.SmsGetLocation.NetInterface;
import com.az.SmsGetLocation.SMSService;
import com.az.Main.R;
import com.globalLock.location.Client;
import com.globalLock.location.Device;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Handler.Callback;
import android.telephony.TelephonyManager;
import android.util.Log;

public class AlarmService extends Service{
	
	static boolean doLocationFlish = true;
	//WifiPowerManager wifiPower = null;
	//cathon xiong add
	private Location location = null;
	private LocationManager locationManager = null;
	//private Context context = null;
	ArrayList<ACellInfo> cellIds = null;
	private AGps gps=null;
	private final static String TAG= "cathon gpslog";
	private Double longti;
	private Double langti;
	private boolean threadDisable=false; 
	private String GpsAddress;
	private String phoneAddress;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public void onDestroy() {
		//stopSelf();
		super.onDestroy();
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		
		Log.i("life", "------------------AlarmService onStart ---------------");
		
		//onLocation();
		//GetgpsbymulcellIds();
		
		Handler handler = handMessage();

		ABLocationManager locationManager = new ABLocationManager(this, handler);
		locationManager.startLocation();
		
		super.onStart(intent, startId);
	}

	/**
	 * @return
	 */
	private Handler handMessage() {
		Handler handler = new Handler(new Callback() {

			@Override
			public boolean handleMessage(Message msg) {
				if (msg.getData().getBoolean("LOCATION_FINISH")) {

					longti = ABLocationManager.longitude;
					langti = ABLocationManager.latitude;
					GpsAddress= ABLocationManager.address;

					String Longitude = longti.toString();
					String Latitude =  langti.toString();
					// GpsAddress = AUtilTool.getaddfromgoogle;
					String LoginURIString = getString(R.string.PersonLocation);
					TelephonyManager telmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
					String imei = "IMEI:" + telmgr.getDeviceId();

					// Post运作传送变量必须用NameValuePair[]数组储存
					List<NameValuePair> InfoParamss = new ArrayList<NameValuePair>(); 
					InfoParamss.add(new BasicNameValuePair("longitude", Longitude));
					InfoParamss.add(new BasicNameValuePair("latitude", Latitude));
					InfoParamss.add(new BasicNameValuePair("imei_key", imei));
					InfoParamss.add(new BasicNameValuePair("Address", GpsAddress));
					InfoParamss.add(new BasicNameValuePair("IsLocationByBaidu", "1"));

					new NetInterface().SendInfoToNet(LoginURIString, InfoParamss);

				}
				return true;
			}
		});
		return handler;
	}

	private void onLocation() {
			//if(doLocationFlish){
				//doLocationFlish= false; 	
				
				//wifiPower = new WifiPowerManager(this);
				//wifiPower.acquire(); //打开wifi电源
				
				CellInfoManager cellManager = new CellInfoManager(this);
				WifiInfoManager wifiManager = new WifiInfoManager(this);
				CellLocationManager locationManager = new CellLocationManager(this, cellManager, wifiManager) {
				@Override
				public void onLocationChanged() {
					  
					  String Longitude=String.valueOf(this.longitude());//经度
					  String Latitude=String.valueOf(this.latitude());//纬度
					  String addr=this.address();//纬度
					  this.stop();
					  
					  //wifiPower.release();//关掉wifi电源
					  
					  String LoginURIString = getString(R.string.PersonLocation);
					  TelephonyManager telmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
					  String imei = "IMEI:" + telmgr.getDeviceId();	              
					         
					  List <NameValuePair> InfoParamss = new ArrayList <NameValuePair>(); //Post运作传送变量必须用NameValuePair[]数组储存
					  InfoParamss.add(new BasicNameValuePair("longitude", Longitude)); 
					  InfoParamss.add(new BasicNameValuePair("latitude", Latitude));
					  InfoParamss.add(new BasicNameValuePair("imei_key", imei));
					  InfoParamss.add(new BasicNameValuePair("Address", addr));
					  
					  new NetInterface().SendInfoToNet(LoginURIString,InfoParamss);
					  
					  //stopSelf();
					  
					  //doLocationFlish= true; 
				}
			};
			locationManager.start();
		//}
	}
	
	private void GetgpsbymulcellIds()
	{
		
		//locationManager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		gps=new AGps(AlarmService.this);
		cellIds=AUtilTool.init(AlarmService.this);
		
		Log.i(TAG, "GetgpsbymulcellIds begin");
		
		new Thread(new Runnable(){
			@Override
			public void run() {

				Location location=gps.getLocation();
				if(location==null){
			//2.根据基站信息获取经纬度
			try {
				location = AUtilTool.callGear(AlarmService.this, cellIds);

				if(location==null)
				{
					Log.i(TAG, "gpsalarm address null ");
				}
				else
				{
					longti = location.getLongitude();	//location.getLatitude().toString();
					langti = location.getLatitude();
					
					String Longitude = longti.toString();
					String Latitude =  langti.toString();
					
					GpsAddress =AUtilTool.getaddfromgoogle ;

				  String LoginURIString = getString(R.string.PersonLocation);
				  TelephonyManager telmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				  String imei = "IMEI:" + telmgr.getDeviceId();

					  List <NameValuePair> InfoParamss = new ArrayList <NameValuePair>(); //Post运作传送变量必须用NameValuePair[]数组储存
					  InfoParamss.add(new BasicNameValuePair("longitude", Longitude));
					  InfoParamss.add(new BasicNameValuePair("latitude", Latitude));
					  InfoParamss.add(new BasicNameValuePair("imei_key", imei));
					  InfoParamss.add(new BasicNameValuePair("Address", GpsAddress));
					  
					  new NetInterface().SendInfoToNet(LoginURIString,InfoParamss);					  			
				}
	
				//Log.i(TAG, "agps location "+ Longitude + " "+ Latitude + " " +GpsAddress);
			} catch (Exception e) {
				location=null;
				e.printStackTrace();
			}
			if(location==null){
				Log.i(TAG, "cell location null"); 
			}
		}		
				
	}
	}).start();

		
	}
	
	
}
