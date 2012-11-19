package com.az.Main;


import com.az.Main.R;
import com.az.ContactsUpdata.ContactPhoneUp;
import com.az.EmergencyPhoneNum.EmergencyphbMainActivity;
import com.az.PersonInfo.SettingActivity;
import com.az.TimingUpGps.SetAlarmTimeService;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // 注意顺序   
        setContentView(R.layout.main); // 注意顺序   

		Intent service=new Intent(this,SetAlarmTimeService.class);
		this.startService(service);

        setupView();
    }
public void setupView(){
	ImageButton imagebutton_my_information=(ImageButton) findViewById(R.id.imagebutton_my_information);
	imagebutton_my_information.setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View view) {
			Intent intent = new Intent(MainActivity.this, SettingActivity.class);
			startActivity(intent);
		}
	});
	ImageButton imagebutton_my_contact=(ImageButton) findViewById(R.id.imagebutton_my_contact);
	imagebutton_my_contact.setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View view) {
			// TODO Auto-generated method stub
			Intent intent=new Intent(MainActivity.this,EmergencyphbMainActivity.class);
			startActivity(intent);
		}
	});
	/*
	ImageButton imagebutton_my_contact_sms=(ImageButton) findViewById(R.id.imagebutton_my_contact_sms);
	imagebutton_my_contact_sms.setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View view) {
			Intent intent=new Intent(MainActivity.this,ContcatListView.class);
			startActivity(intent);
		}
	});
	ImageButton imagebutton_contact_phone=(ImageButton) findViewById(R.id.imagebutton_contact_phone);
	imagebutton_contact_phone.setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Intent ContactIntent=new Intent(MainActivity.this,ContactPhoneUp.class);
			startActivity(ContactIntent);
			
		}
	});*/
	
}
/*
    //@Override
	protected void onActivityResult(int request, int result, Intent data) {

		if (request == 0x100001) {
			System.exit(0);
		}
	}
*/
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add("Setup");
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode==KeyEvent.KEYCODE_MENU){
			
			
			return true;
		}
		
		
		return super.onKeyDown(keyCode, event);
	}
	
	protected void onDestroy() {
		super.onDestroy();
		//android.os.Process.killProcess(android.os.Process.myPid());//kill掉自已，这方法不是很好
		// finish();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent(this, SettingActivity.class);
		startActivity(intent);
		return super.onOptionsItemSelected(item);
	}
	
}