package com.android.emergencycall;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;

public class EmergencycallActivity extends Activity {
	private String mobile = null;
	private String databaseFilename = "/data/data/com.az.Main/databases/emergencyphb.db";

	/** Called when the activity is first created. */

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		 setContentView(R.layout.main);	//4006231121
		
		Context azContext=null;
		try {
			azContext = createPackageContext("com.az.Main",
					Context.CONTEXT_IGNORE_SECURITY);
			SharedPreferences prs = azContext.getSharedPreferences(
					"com.az.PersonInfo_preferences",
					Context.MODE_WORLD_READABLE);
			String setinfo_flag = prs.getString("check_insurance_key", "");
			// 02:emergency call
			if(!setinfo_flag.contains("02")){
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.alert_title));
				builder.setMessage(R.string.alert_msg);
				builder.setPositiveButton(R.string.confirm, new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
					}
				});
				AlertDialog dialog = builder.create();
				dialog.show();
				return;
			}
		} catch (NameNotFoundException ex) {
			// not found com.az.Main
			ex.printStackTrace();
		}
		mobile = "10086";
		Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
				+ mobile));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		startActivity(intent);

		// 发送三条短信
		int count = 0;
		SmsManager smsManager = SmsManager.getDefault();
		ArrayList<String> phones = new ArrayList<String>();
		try {
			SQLiteDatabase db = openOrCreateDatabase(databaseFilename,
					Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE,
					null);

			Cursor cursor = db.query("emerphb", new String[] { "phonenum" },
					null, null, null, null, null);
			if (cursor != null) {
				while (cursor.moveToNext() && count++ < 3) {
					smsManager
							.sendTextMessage(cursor.getString(cursor
									.getColumnIndex("phonenum")), null, this.getString(R.string.strindctabg)
									+ count + this.getString(R.string.strindctass), null, null);
				}
				cursor.close();
			}
			db.close();
		} catch (Exception e) {
		}

		// 启动紧急定位服务,自动上传服务器
		startService(new Intent(this, AlarmService.class));
	}


	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		finish();

	}
}