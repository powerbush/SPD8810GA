package com.az.ContactsUpdata;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;


import com.az.Main.MainActivity;
import com.az.Main.R;
import com.az.Main.R.id;
import com.az.Main.R.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class ContactPhoneUp extends Activity{

	public AlertDialog dialogP;
	public Handler handler;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.contact_phone);
		super.onCreate(savedInstanceState);
		handler=new Handler(){
			@Override
			public void handleMessage(Message msg) {
				switch(msg.what){
				case 1:
					UpOk();
					break;
				case 2:
					UpFail();
					break;
				}
				
				super.handleMessage(msg);
			}
		};
		setupView();
	}
	
	public void setupView(){
		Button contact_phone_but=(Button) findViewById(R.id.contact_phone_but_up);
		
		contact_phone_but.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				TelephonyManager telmgr = (TelephonyManager)ContactPhoneUp.this.getSystemService(Service.TELEPHONY_SERVICE);
				final String imei = "IMEI:" + telmgr.getDeviceId();

				
				if(imei.equals("")){
					Toast.makeText(ContactPhoneUp.this, getString(R.string.AzImeiNum), Toast.LENGTH_SHORT);
					//Log.i("life", "电话：" +imei);
				}else{
					dialogP = new ProgressDialog(ContactPhoneUp.this);
					dialogP.setTitle(getString(R.string.AzWaiting));
					dialogP.setMessage(getString(R.string.AzUpdataIng));
					
					dialogP.show();
					Thread th=new Thread(new Runnable() {
						
						@Override
						public void run() {
							// TODO Auto-generated method stub
							 String LoginURIString = getString(R.string.PersonTongXunLu);//"http://61.143.124.173:8080/io/PersonTongXunLu.aspx ";

					         /*建立HTTP Post联机*/
					         HttpPost httpRequest = new HttpPost(LoginURIString); 
					         //Post运作传送变量必须用NameValuePair[]数组储存
					         List <NameValuePair> params = new ArrayList <NameValuePair>(); 
					         
					         params.add(new BasicNameValuePair("imei_key",imei ));
					         params.add(new BasicNameValuePair("contact_phone", getContactPhone().toString()));
					         /*发出HTTP request*/
				        	 try {
				        		 
								httpRequest.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
								 try {
									HttpResponse httpResponse = new DefaultHttpClient().execute(httpRequest);
									if(httpResponse.getStatusLine().getStatusCode()==200 ){
										String s=EntityUtils.toString(httpResponse.getEntity());
										Log.i("life", s);
										if(s.contains("true")){
											//Log.i("life", "通讯录上传成功");
											Message msg=Message.obtain();
											msg.what=1;
											handler.sendMessage(msg);
											//Toast.makeText(ContactPhoneUp.this, "通讯录上次成功", Toast.LENGTH_SHORT).show();
										}else{
											//Log.i("life", "通讯录上传失败");
											Message msg=Message.obtain();
											msg.what=2;
											handler.sendMessage(msg);
											//Toast.makeText(ContactPhoneUp.this, "通讯录上次失败", Toast.LENGTH_SHORT).show();
										}
									}
									
								} catch (ClientProtocolException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									
								}catch (HttpHostConnectException e){
									Message msg=Message.obtain();
									msg.what=2;
									handler.sendMessage(msg);
								} 
								 catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} 
				        	 } catch (UnsupportedEncodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} 
						}
					});
					th.start();
				}
				
				
			}
		});
	}
	public ArrayList<String> getContactPhone(){
		 ContentResolver cont=getContentResolver();
		 
		  Uri uri = Contacts.CONTENT_URI;
	        String[] projection = {
	            	Contacts._ID,
	            	Contacts.DISPLAY_NAME	
	            };
		Cursor cursor=cont.query(uri, projection, null, null, Contacts.DISPLAY_NAME + " desc");
		
		ArrayList<String> phones =new ArrayList<String>();
		while(cursor.moveToNext()){
			
			int id =cursor.getInt( cursor.getColumnIndex(Contacts._ID));
			String s=cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
			//查询电话号码按ID
			Cursor cur = getContentResolver().query(Phone.CONTENT_URI, new String[]{Phone.NUMBER}, Phone.CONTACT_ID+"=?", new String[]{id+""}, null);
			if(cur!=null){
				while(cur.moveToNext()){
					//Log.i("life", "电话：" +s+ cur.getString(cur.getColumnIndex(Phone.NUMBER)));
					phones.add(s+"@@"+cur.getString(cur.getColumnIndex(Phone.NUMBER)));
				}
				cur.close();
			}
		
		
		}
		cursor.close();
		return phones;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//finish();
	}
	
	public void UpOk(){
		if(dialogP!=null){
			dialogP.dismiss();
		}
		
		new AlertDialog.Builder(this).setTitle(getString(R.string.AzInformationNotice)).setMessage(getString(R.string.AzInfoUpOK)).setPositiveButton(getString(R.string.azconfirm), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialoginterface, int i) {
				//Intent intent=new Intent(ContactPhoneUp.this,MainActivity.class);
				//startActivity(intent);
				//finish();
				
			}
		}).setNegativeButton(getString(R.string.azcancel), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialoginterface, int i) {
				dialoginterface.dismiss();
				
			}
		} ).show();
		
	}
	private void UpFail(){
		if(dialogP!=null){
			dialogP.dismiss();
		}
		new AlertDialog.Builder(this).setTitle(getString(R.string.AzInformationNotice)).setMessage(getString(R.string.AzContactUpErr)).setNegativeButton(getString(R.string.azcancel), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialoginterface, int i) {
				// TODO Auto-generated method stub
				dialoginterface.dismiss();
			}
		}).show();
	}
}
