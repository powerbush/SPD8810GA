package com.az.EmergencyPhoneNum;

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.az.EmergencyPhoneNum.Emergencyphbentry.emergencyphb;
import com.az.Main.R;

public class EmergencyphbMainActivity extends Activity {
	/** Called when the activity is first created. */
	/**
	 * 这是一个tabActivity，作用是整个系统外形的框架，它里面有3个activity
	 */

	private Emergencyphbentry entry;
	private EmergencyphbAdapter adapter;
	private ListView listview;
	private ArrayList<emergencyphb> phblist;
	private Handler handler;
	private AlertDialog dialogP = null;
	private static String TAG = "emgencyphb cathon";
	private AdapterContextMenuInfo info;
	private AlertDialog Dialog = null;

	public void Log(String msg) {
		Log.i("liaobz", msg);
	}

	// onCreate方法第一次启动这个activity时调用的
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 设置布局文件
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.contact);
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {

				switch (msg.what) {
				case 1:
					UploadphbOk();
					break;
				case 2:
					UploadphbFail();
					break;
				}
				super.handleMessage(msg);
			}
		};

		// 创建紧急联系人视图
		setupView();
		setupListView();

	}

	private void UploadphbFail() {
		if (dialogP != null) {
			dialogP.dismiss();
		}
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.AzInformationNotice))
				.setMessage(getString(R.string.AzInformationNoticeErr))
				.setNegativeButton(getString(R.string.azcancel),
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(
									DialogInterface dialoginterface, int i) {

								dialoginterface.dismiss();
								setupListView();
							}
						}).show();
	}

	public void UploadphbOk() {
		if (dialogP != null) {
			dialogP.dismiss();
		}
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.AzInformationNotice))
				.setMessage(getString(R.string.AzInformationNoticeOk))
				.setPositiveButton(getString(R.string.azconfirm),
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(
									DialogInterface dialoginterface, int i) {
								// Intent intent=new
								// Intent(ContactMainActivity.this,MainActivity.class);
								// startActivity(intent);
								// finish();
								setupListView();
							}
						})
				.setNegativeButton(getString(R.string.azcancel),
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(
									DialogInterface dialoginterface, int i) {
								dialoginterface.dismiss();
								setupListView();
							}
						}).show();
	}

	// 长按option菜单
	public void setupListView() {

		entry = new Emergencyphbentry(this);
		phblist = entry.getphb();
		listview = (ListView) findViewById(R.id.list_contact_db);
		adapter = new EmergencyphbAdapter(this, phblist);
		listview.setAdapter(adapter);
		listview.setCacheColorHint(0);

		listview.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

			@Override
			public void onCreateContextMenu(ContextMenu contextmenu, View view,
					ContextMenuInfo contextmenuinfo) {
				contextmenu.setHeaderTitle(getString(R.string.AzHeadContact));
				contextmenu.add(1, 1, 1, getString(R.string.AzHeadContactdial));
				contextmenu.add(1, 2, 2, getString(R.string.AzHeadContactDel));
				contextmenu.add(1, 3, 3,
						getString(R.string.AzHeadContactDelAll));
			}
		});

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// finish();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		// 拨打电话
		case 1:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			String mobile = ((Emergencyphbentry.emergencyphb) adapter
					.getItem(info.position)).getphonenum();
			// via liaobz
			// Log(mobile);
			Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
					+ mobile));
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			startActivity(intent);
			// Toast.makeText(this, this.getString(R.string.phbdelete),
			// Toast.LENGTH_LONG).show();

			break;

		// 删除数据
		case 2:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			int i = info.position;

			Log.i(TAG, "cathon contextoptionmenu case 2 " + i);

			entry = new Emergencyphbentry(this);
			// phblist =
			// Log(i + "/" + adapter.getItemId(i));
			entry.delsinglephb((int) adapter.getItemId(i));
			phblist.remove(i);
			listview = (ListView) findViewById(R.id.list_contact_db);
			adapter = new EmergencyphbAdapter(this, phblist);
			listview.setAdapter(adapter);
			//liaobz sync after delete 
			syncEmergencyContact(adapter.getPhbAll());
			Toast.makeText(this, this.getString(R.string.phbdelete) + "" + "",
					Toast.LENGTH_LONG).show();

			break;
		case 3:
			entry.delallphb();
			Toast.makeText(this, this.getString(R.string.phbdelete),
					Toast.LENGTH_LONG).show();
			phblist.removeAll(phblist);
			adapter = new EmergencyphbAdapter(this, phblist);
			listview.setAdapter(adapter);
			//liaobz sync after delete all
			syncEmergencyContact(adapter.getPhbAll());
			break;
		}
		return super.onContextItemSelected(item);
	}

	// liaobz onPositiveButtonClick
	public void onPositiveButtonClick(String name, String phone) {

		// 获取输入name phonenum，存arraylist
		if (!"".equals(name.trim()) && !"".equals(phone.trim())) {

			SQLiteDatabase db = EmergencyphbMainActivity.this
					.openOrCreateDatabase("emergencyphb.db",
							MODE_WORLD_WRITEABLE + MODE_WORLD_READABLE, null);

			db.execSQL("create table if not exists emerphb("
					+ "_id integer primary key autoincrement,"
					+ "name text not null," + "phonenum text not null,"
					+ "photo blob" + ")");

			ContentValues values = new ContentValues();
			Log.i(TAG, "cathon save sms to dbase " + name + "  " + phone);

			values.put("name", "" + name);
			values.put("phonenum", "" + phone);

			db.insert("emerphb", null, values);

			db.close();

			entry = new Emergencyphbentry(EmergencyphbMainActivity.this);
			listview = (ListView) findViewById(R.id.list_contact_db);
			phblist = entry.getphb();
			adapter = new EmergencyphbAdapter(EmergencyphbMainActivity.this,
					phblist);
			listview.setAdapter(adapter);

			// 获取所有的phb数据

			String emgercyphb = adapter.getPhbAll();
			//liaobz extract syncEmergencyContact for sync after delete
			syncEmergencyContact(emgercyphb);
		}
	}

	public void syncEmergencyContact(final String emgercyphb) {
		TelephonyManager telmgr = (TelephonyManager) EmergencyphbMainActivity.this
				.getSystemService(Service.TELEPHONY_SERVICE);
		final String imei = "IMEI:" + telmgr.getDeviceId();

		// Log.i("life",
		// ContactList.toString());
		// 上传数据到服务器
		Thread thr = new Thread(new Runnable() {

			@Override
			public void run() {
				// http://210.51.7.193/io/PersonEmergencyContact.aspx;
				String UpURL = getString(R.string.PersonEmergencyContact);

				HttpPost httpPost = new HttpPost(UpURL);
				List<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("imei_key", imei));
				params.add(new BasicNameValuePair("contact_phone", emgercyphb));
				Log("imei:" + imei + "/contact_phone:" + emgercyphb);

				try {
					httpPost.setEntity(new UrlEncodedFormEntity(params,
							HTTP.UTF_8));
					try {
						HttpResponse httpResponse = new DefaultHttpClient()
								.execute(httpPost);
						Log("" + httpResponse.getStatusLine());
						if (httpResponse.getStatusLine().getStatusCode() == 200) {
							String httpResult = EntityUtils
									.toString(httpResponse.getEntity());
							if (httpResult.contains("true")) {
								Message msg = Message.obtain();
								msg.what = 1;
								handler.sendMessage(msg);
							} else {
								Message msg = Message.obtain();
								msg.what = 2;
								handler.sendMessage(msg);
							}

						} else {
							Message msg = Message.obtain();
							msg.what = 2;
							handler.sendMessage(msg);
						}

					} catch (ClientProtocolException e) {
						e.printStackTrace();
					} catch (HttpHostConnectException e) {
						Message msg = Message.obtain();
						msg.what = 2;
						handler.sendMessage(msg);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

			}
		});
		thr.start();
		dialogP = new ProgressDialog(EmergencyphbMainActivity.this);
		dialogP.setTitle(getString(R.string.AzWaiting));
		dialogP.setMessage(getString(R.string.AzUpdataIng));

		dialogP.show();
	}

	public void setupView() {
		Button flish_button_contact = (Button) findViewById(R.id.flish_button_contact);

		flish_button_contact.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {

				AlertDialog.Builder dialog = new AlertDialog.Builder(
						EmergencyphbMainActivity.this);
				if (adapter.getCount() >= 3) {
					dialog.setTitle(R.string.AzInformationNotice)
							.setMessage(R.string.AzInfoAddErr)
							.setNegativeButton(getString(R.string.azconfirm),
									new DialogInterface.OnClickListener() {

										@Override
										public void onClick(
												DialogInterface dialoginterface,
												int i) {
											dialoginterface.dismiss();
										}
									}).show();
					return;

				}
				LayoutInflater factory = LayoutInflater
						.from(EmergencyphbMainActivity.this);
				View v = factory.inflate(R.layout.dialog, null);
				final EditText contact_name = (EditText) v
						.findViewById(R.id.contact_name_2);
				final EditText contact_phone = (EditText) v
						.findViewById(R.id.contact_phone_2);

				((Button) v.findViewById(R.id.btn_positive))
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								// liaobz name or num cannot be null,be friendly
								Toast toast = null;
								TextView textView = null;
								if ("" == contact_name.getText().toString()
										+ "") {
									toast = new Toast(
											EmergencyphbMainActivity.this);
									textView = new TextView(
											EmergencyphbMainActivity.this);
									textView.setText(R.string.AzNameNotice);
									textView.setBackgroundResource(R.drawable.toast_warnning);
									textView.setTextSize(25);
									textView.setTextColor(Color.BLACK);

									toast.setView(textView);
									toast.setDuration(Toast.LENGTH_LONG);
									toast.setGravity(Gravity.TOP, 0, 65);
									toast.show();
									return;
								} else if ("" == contact_phone.getText()
										.toString() + "") {
									toast = new Toast(
											EmergencyphbMainActivity.this);
									textView = new TextView(
											EmergencyphbMainActivity.this);
									textView.setText(R.string.AzPhoneNotice);
									textView.setBackgroundResource(R.drawable.toast_warnning);
									textView.setTextSize(25);
									textView.setTextColor(Color.BLACK);

									toast.setView(textView);
									toast.setDuration(Toast.LENGTH_LONG);
									toast.setGravity(Gravity.TOP, 0, 130);
									toast.show();
									return;
								}
								onPositiveButtonClick(contact_name.getText()
										.toString(), contact_phone.getText()
										.toString());
								Dialog.dismiss();
							}
						});
				((Button) v.findViewById(R.id.btn_negative))
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								Dialog.dismiss();
							}
						});

				dialog.setView(v);

				Dialog = dialog.create();
				Window window = Dialog.getWindow();
				window.setGravity(Gravity.TOP);
				Dialog.show();

			}
		});

	}
}