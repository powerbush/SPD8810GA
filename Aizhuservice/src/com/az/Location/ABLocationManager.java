package com.az.Location;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

public class ABLocationManager {
	Handler handler = null;
	public static Double latitude = 0.0;
	public static Double longitude = 0.0;
	public static String address = "";
	// 定位类
	private LocationClient mLocationClient = null;
	private LocationClientOption option = null;
	private final String TAG = "ABLocationManager";

	public ABLocationManager(Context context, Handler handler) {
		this.handler = handler;
		mLocationClient = new LocationClient(context);
		mLocationClient.registerLocationListener(new MyReceiveListenner());
	}

	// 接受定位得到的消息
	private class MyReceiveListenner implements BDLocationListener {

		@Override
		public void onReceiveLocation(BDLocation location) {
			// TODO Auto-generated method stub
			if (location == null)
				return;

			latitude = location.getLatitude();
			longitude = location.getLongitude();
			address = location.getAddrStr();

			// Looper.prepare();
			//
			Message message = new Message();
			Bundle bundle = new Bundle();
			bundle.putBoolean("LOCATION_FINISH", true);
			message.setData(bundle);
			handler.sendMessage(message);

			// stop loction
			mLocationClient.stop();
		}
	}

	/**
	 * start Location
	 */
	public void startLocation() {
		try {
			option = new LocationClientOption();
			// 设置返回的坐标类型
			option.setCoorType("gcj02");
			// 设置时间
			// option.setScanSpan(myLocationTime);
			// 返回地址类型
			option.setAddrType("detail");

			mLocationClient.setLocOption(option);
			// start loction
			mLocationClient.start();

		} catch (Exception e) {
			Log.i(TAG, "打开定位异常" + e.toString());
		}
	}
}
