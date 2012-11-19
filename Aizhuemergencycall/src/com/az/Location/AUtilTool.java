package com.az.Location;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.widget.Toast;

public class AUtilTool {
	public static String getaddfromgoogle=null;
	
	public static boolean isGpsEnabled(LocationManager locationManager) {
		boolean isOpenGPS = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
		boolean isOpenNetwork = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
		if (isOpenGPS || isOpenNetwork) {
			return true;
		}
		return false;
	} 
	
    /**
     * @param cellIds
     * @return
     * @throws Exception 
     */
    public static Location callGear(Context ctx, ArrayList<ACellInfo> cellIds) throws Exception {
    	String result="";
    	JSONObject data=null;
    	JSONObject datafroaddss=null;
    	Log.i("cathon gpslog", "agps cellid " + cellIds + " " + cellIds.size());
    	
    	if (cellIds == null||cellIds.size()==0) {
    		//UtilTool.alert(ctx, "cell request param null");
    		Log.i("cathon gpslog", "cell request param null" );
    		return null;
    	};
    	
		try {
			result = AUtilTool.getResponseResult(ctx, "http://www.google.com/loc/json", cellIds);
			Log.i("cathon gpslog", "get result string " + result);
			if(result.length() <= 1)
				return null;
			data = new JSONObject(result);
			datafroaddss = new JSONObject(result);
			data = (JSONObject) data.get("location");
			
			try{
				getaddfromgoogle = datafroaddss.getJSONObject("location").getJSONObject("address").getString("region")+
						datafroaddss.getJSONObject("location").getJSONObject("address").getString("city")+
						datafroaddss.getJSONObject("location").getJSONObject("address").getString("street")+
						datafroaddss.getJSONObject("location").getJSONObject("address").getString("street_number");

			}catch(Exception ex){
				Log.i("cathon ", "get goolge error: " + ex.getMessage() + "jasonstr error: " + result);
			}

			Location loc = new Location(LocationManager.NETWORK_PROVIDER);
			loc.setLatitude((Double) data.get("latitude"));
			loc.setLongitude((Double) data.get("longitude"));
			loc.setAccuracy(Float.parseFloat(data.get("accuracy").toString()));
			loc.setTime(AUtilTool.getUTCTime());


			return loc;
		} catch (JSONException e) {
			return null;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}


	public static String getResponseResult(Context ctx,String path, ArrayList<ACellInfo> cellInfos)
			throws UnsupportedEncodingException, MalformedURLException,
			IOException, ProtocolException, Exception {
		String result="";
		Log.i(ctx.getApplicationContext().getClass().getSimpleName(), 
				"in param: "+getRequestParams(cellInfos));
		InputStream inStream=AUtilTool.sendPostRequest(path, 
				getRequestParams(cellInfos), "UTF-8");
		if(inStream!=null){			
			
			byte[] datas=AUtilTool.readInputStream(inStream);
			if(datas!=null&&datas.length>0){
				result=new String(datas, "UTF-8");
				Log.i(ctx.getApplicationContext().getClass().getSimpleName(), 
				    "google cell receive data result:"+result);
			}else{
				Log.i(ctx.getApplicationContext().getClass().getSimpleName(), 
						"google cell receive data null");
			}
		}else{
			Log.i(ctx.getApplicationContext().getClass().getSimpleName(), 
			    "google cell receive inStream null");
		}
		return result;
	}

	public static String getRequestParams(List<ACellInfo> cellInfos){
		StringBuffer sb=new StringBuffer("");
		sb.append("{");
		if(cellInfos!=null&&cellInfos.size()>0){
			sb.append("'version': '1.1.0',"); //google api
			sb.append("'host': 'maps.google.com',"); //
			sb.append("'home_mobile_country_code': "+cellInfos.get(0).getMobileCountryCode()+","); //
			sb.append("'home_mobile_network_code': "+cellInfos.get(0).getMobileNetworkCode()+","); //
			sb.append("'radio_type': '"+cellInfos.get(0).getRadioType()+"',"); //
			sb.append("'request_address': true,");
			sb.append("'address_language': 'zh_CN',"); 
			sb.append("'cell_towers':["); 
			for(ACellInfo cellInfo:cellInfos){
				sb.append("{");
				sb.append("'cell_id': '"+cellInfo.getCellId()+"',"); 
				sb.append("'location_area_code': "+cellInfo.getLocationAreaCode()+","); 
				sb.append("'mobile_country_code': "+cellInfo.getMobileCountryCode()+","); 
				sb.append("'mobile_network_code': "+cellInfo.getMobileNetworkCode()+",");
				sb.append("'age': 0"); 
				sb.append("},");
			}
			sb.deleteCharAt(sb.length()-1);
			sb.append("]");
		}
		sb.append("}");
		return sb.toString();
	}

	public static long getUTCTime() {
        Calendar cal = Calendar.getInstance(Locale.CHINA);
        int zoneOffset = cal.get(java.util.Calendar.ZONE_OFFSET); 
        int dstOffset = cal.get(java.util.Calendar.DST_OFFSET); 
        cal.add(java.util.Calendar.MILLISECOND, -(zoneOffset + dstOffset)); 
        return cal.getTimeInMillis();
    }
	
	public static ArrayList<ACellInfo> init(Context ctx) {
		ArrayList<ACellInfo> cellInfos = new ArrayList<ACellInfo>();
		
		TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);

		int type = tm.getNetworkType();

		String imsi = tm.getSubscriberId(); 
		//alert(ctx, "imsi: "+imsi);
		Log.i("cathon gpslog", "imsi" +imsi );
		if(imsi!=null&&!"".equals(imsi)){ 
			//alert(ctx, "imsi");
			if (imsi.startsWith("46000") || imsi.startsWith("46002")) {

				mobile(cellInfos, tm);
			} else if (imsi.startsWith("46001")) {

				union(cellInfos, tm);
			} else if (imsi.startsWith("46003")) {

				cdma(cellInfos, tm);
			}   
		}else{

			if (type == TelephonyManager.NETWORK_TYPE_EVDO_A 
					|| type == TelephonyManager.NETWORK_TYPE_EVDO_0
					|| type == TelephonyManager.NETWORK_TYPE_CDMA 
					|| type ==TelephonyManager.NETWORK_TYPE_1xRTT){
				cdma(cellInfos, tm);
			}
			else if(type == TelephonyManager.NETWORK_TYPE_EDGE
					|| type == TelephonyManager.NETWORK_TYPE_GPRS ){
				mobile(cellInfos, tm);
			}
			else if(type == TelephonyManager.NETWORK_TYPE_GPRS
					||type == TelephonyManager.NETWORK_TYPE_EDGE
					||type == TelephonyManager.NETWORK_TYPE_UMTS
					||type == TelephonyManager.NETWORK_TYPE_HSDPA){
				union(cellInfos, tm);
			}
		}
		
		Log.i("cathon", "getmobile : " + cellInfos.toString());
		return cellInfos;
	}


	/**
	 * 
	 * @param cellInfos
	 * @param tm
	 */
	private static void cdma(ArrayList<ACellInfo> cellInfos, TelephonyManager tm) {
		CdmaCellLocation location = (CdmaCellLocation) tm.getCellLocation();
		ACellInfo info = new ACellInfo();
		info.setCellId(location.getBaseStationId());
		info.setLocationAreaCode(location.getNetworkId());
		info.setMobileNetworkCode(String.valueOf(location.getSystemId()));
		info.setMobileCountryCode(tm.getNetworkOperator().substring(0, 3));
		info.setRadioType("cdma");
		cellInfos.add(info);
		
		List<NeighboringCellInfo> list = tm.getNeighboringCellInfo();
		int size = list.size();
		for (int i = 0; i < size; i++) {
			ACellInfo cell = new ACellInfo();
			cell.setCellId(list.get(i).getCid());
			cell.setLocationAreaCode(location.getNetworkId());
			cell.setMobileNetworkCode(String.valueOf(location.getSystemId()));
			cell.setMobileCountryCode(tm.getNetworkOperator().substring(0, 3));
			cell.setRadioType("cdma");
			cellInfos.add(cell);
		}
	}


	/**
	 * 
	 * @param cellInfos
	 * @param tm
	 */
	private static void mobile(ArrayList<ACellInfo> cellInfos,
			TelephonyManager tm) {
		GsmCellLocation location = (GsmCellLocation)tm.getCellLocation();  
		ACellInfo info = new ACellInfo();
		info.setCellId(location.getCid());
		//info.setRssi(location.getRssi());
		info.setLocationAreaCode(location.getLac());
		info.setMobileNetworkCode(tm.getNetworkOperator().substring(3, 5));
		info.setMobileCountryCode(tm.getNetworkOperator().substring(0, 3));
		info.setRadioType("gsm");
		cellInfos.add(info);
		
		List<NeighboringCellInfo> list = tm.getNeighboringCellInfo();
		int size = list.size();
		for (int i = 0; i < size; i++) {
			ACellInfo cell = new ACellInfo();
			cell.setCellId(list.get(i).getCid());
			cell.setRssi(list.get(i).getRssi());
			cell.setLocationAreaCode(location.getLac());
			cell.setMobileNetworkCode(tm.getNetworkOperator().substring(3, 5));
			cell.setMobileCountryCode(tm.getNetworkOperator().substring(0, 3));
			cell.setRadioType("gsm");
			cellInfos.add(cell);
		}
		
		Log.i("cathon", "cellinfor: " + cellInfos.toString());
	}


	/**
	 *  
	 * @param cellInfos
	 * @param tm
	 */
	private static void union(ArrayList<ACellInfo> cellInfos, TelephonyManager tm) {
		GsmCellLocation location = (GsmCellLocation)tm.getCellLocation();  
		ACellInfo info = new ACellInfo();
		//info.setMobileNetworkCode(tm.getNetworkOperator().substring(3, 5));  
		//info.setMobileCountryCode(tm.getNetworkOperator().substring(0, 3));
		info.setCellId(location.getCid());
		info.setLocationAreaCode(location.getLac());
		info.setMobileNetworkCode("");
		info.setMobileCountryCode("");
		info.setRadioType("gsm");
		cellInfos.add(info);
		
		List<NeighboringCellInfo> list = tm.getNeighboringCellInfo();
		int size = list.size();
		for (int i = 0; i < size; i++) {
			ACellInfo cell = new ACellInfo();
			cell.setCellId(list.get(i).getCid());
			cell.setLocationAreaCode(location.getLac());
			cell.setMobileNetworkCode("");
			cell.setMobileCountryCode("");
			cell.setRadioType("gsm");
			cellInfos.add(cell);
		}
	}
	/**
	 * 
	 * @param ctx
	 * @param msg
	 */
	public static void alert(Context ctx,String msg){
		Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
	}
	

	public static InputStream sendPostRequest(String path, String params, String encoding)
	throws UnsupportedEncodingException, MalformedURLException,
	IOException, ProtocolException {
		byte[] data = params.getBytes(encoding);
		URL url = new URL(path);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/x-javascript; charset="+ encoding);
		conn.setRequestProperty("Content-Length", String.valueOf(data.length));
		conn.setConnectTimeout(5 * 1000);
		OutputStream outStream = conn.getOutputStream();
		outStream.write(data);
		outStream.flush();
		outStream.close();
		if(conn.getResponseCode()==200)
			return conn.getInputStream();
		return null;
	}
	

	public static String sendGetRequest(String path) throws Exception {
		URL url = new URL(path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5 * 1000);
		conn.setRequestMethod("GET");
		InputStream inStream = conn.getInputStream();
		byte[] data = readInputStream(inStream);
		String result = new String(data, "UTF-8");
		return result;
	}
	

	public static byte[] readInputStream(InputStream inStream) throws Exception{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while( (len = inStream.read(buffer)) !=-1 ){
			outStream.write(buffer, 0, len);
		}
		byte[] data = outStream.toByteArray();
		outStream.close();
		inStream.close();
		return data;
	}

	
}
