package com.az.Main;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.az.ContactsUpdata.ContactPhoneUp;
import com.az.EmergencyPhoneNum.EmergencyphbMainActivity;
import com.az.PersonInfo.SettingActivity;
import com.az.TimingUpGps.SetAlarmTimeService;

public class MainActivity extends Activity {
	ListView listView ;
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		requestWindowFeature(Window.FEATURE_NO_TITLE); // ע��˳��
		setContentView(R.layout.main); // ע��˳��

		Intent service = new Intent(this, SetAlarmTimeService.class);
		this.startService(service);

		setupView();
	}

	public void setupView() {
		listView = (ListView) findViewById(R.id.listView);
        ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String,Object>>();
        HashMap<String, Object> map1 = new HashMap<String, Object>();
        HashMap<String, Object> map2 = new HashMap<String, Object>();
        HashMap<String, Object> map3 = new HashMap<String, Object>();
        
        //һ��map�����Ӧһ�����
        map1.put("user_name", getString(R.string.apnpersoniforinput));
        map1.put("user_icon", R.drawable.inputsms);
        
        map2.put("user_name", getString(R.string.emergencycontact));
        map2.put("user_icon", R.drawable.contact);
        
        map3.put("user_name", getString(R.string.apnphmanage));
        map3.put("user_icon", R.drawable.sms);
        
        list.add(map1);
        list.add(map2);
        //list.add(map3);
        
        SimpleAdapter listAdapter = new SimpleAdapter(this,list,
        		R.layout.main_item, new String[] {"user_name","user_icon"},
        		new int[] {R.id.user_name,R.id.user_icon});
        		
        //����Adapter setListAdapter()�˷�������ListActivity
        listView.setAdapter(listAdapter);
        listView.setCacheColorHint(0);  //�����϶��б��ʱ���ֹ���ֺ�ɫ����  
        
        listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> l, View v, int position,long id) {
				// TODO Auto-generated method stub
		    	Intent intent;
		    	switch(position){
		    		case 0:
		                //liao
		                intent=new Intent(MainActivity.this,
								SettingActivity.class);
						startActivityForResult(intent, 0x100001);
		    			break;
		    		case 1:
		    			intent = new Intent(MainActivity.this,
								EmergencyphbMainActivity.class);
						startActivity(intent);
		    			break;
		    		case 2:
					intent = new Intent(MainActivity.this,
							ContactPhoneUp.class);
					startActivity(intent);
		    			break;
		    	}
			}
		});
        
		/*
		ImageButton imagebutton_my_information = (ImageButton) findViewById(R.id.imagebutton_my_information);
		imagebutton_my_information.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				Intent intent = new Intent(MainActivity.this,
						SettingActivity.class);
				startActivityForResult(intent, 0x100001);
			}
		});
		ImageButton imagebutton_my_contact = (ImageButton) findViewById(R.id.imagebutton_my_contact);
		imagebutton_my_contact.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				Intent intent = new Intent(MainActivity.this,
						EmergencyphbMainActivity.class);
				startActivity(intent);
			}
		});
		/*
		 * ImageButton imagebutton_my_contact_sms=(ImageButton)
		 * findViewById(R.id.imagebutton_my_contact_sms);
		 * imagebutton_my_contact_sms.setOnClickListener(new OnClickListener() {
		 * 
		 * @Override public void onClick(View view) { Intent intent=new
		 * Intent(MainActivity.this,ContcatListView.class);
		 * startActivity(intent); } });
		 *
		ImageButton imagebutton_contact_phone = (ImageButton) findViewById(R.id.imagebutton_contact_phone);
		imagebutton_contact_phone.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent ContactIntent = new Intent(MainActivity.this,
						ContactPhoneUp.class);
				startActivity(ContactIntent);

			}
		});
		*/

	}
	
	

	// @Override
	protected void onActivityResult(int request, int result, Intent data) {

		if (request == 0x100001) {
			System.exit(0);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add("Setup");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent(this, SettingActivity.class);
		startActivity(intent);
		return super.onOptionsItemSelected(item);
	}

}