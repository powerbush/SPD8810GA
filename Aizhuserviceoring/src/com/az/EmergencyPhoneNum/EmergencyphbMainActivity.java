package com.az.EmergencyPhoneNum;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
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

import com.az.ContactsUpdata.ContactPhoneUp;
import com.az.EmergencyPhoneNum.Emergencyphbentry.emergencyphb;

import com.az.Main.MainActivity;
import com.az.PersonInfo.SettingActivity;
import com.az.TimingUpGps.SetAlarmTimeService;

import android.R.plurals;
import android.view.Gravity;
import com.az.Main.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.app.TabActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

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
				// TODO Auto-generated method stub
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
								// TODO Auto-generated method stub
								dialoginterface.dismiss();
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
								
								// finish();
							}
						})
				.setNegativeButton(getString(R.string.azcancel),
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(
									DialogInterface dialoginterface, int i) {
								dialoginterface.dismiss();

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

		listview.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

			@Override
			public void onCreateContextMenu(ContextMenu contextmenu, View view,
					ContextMenuInfo contextmenuinfo) {
				contextmenu.setHeaderTitle(getString(R.string.AzHeadContact));
				contextmenu.add(1, 1, 1, getString(R.string.AzHeadContactdial));
				contextmenu.add(1, 2, 2, getString(R.string.AzHeadContactDel));
				contextmenu.add(1, 3, 3, getString(R.string.AzHeadContactDelAll));
			}
		});

	}

	@Override
	protected void onDestroy() {
		//android.os.Process.killProcess(android.os.Process.myPid());//kill掉自已，这方法不是很好
		 //Intent intent=new Intent(EmergencyphbMainActivity.this,MainActivity.class);
		 //startActivity(intent);
		 super.onDestroy();
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
			Log.i("log_via_liao", "" + mobile);
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
			Log.i("log_via_liao", ""+ adapter.getItemId(i));
			entry.delsinglephb((int) adapter.getItemId(i));
			phblist.remove(i);
			listview = (ListView) findViewById(R.id.list_contact_db);
			adapter = new EmergencyphbAdapter(this, phblist);
			listview.setAdapter(adapter);

			Toast.makeText(this, this.getString(R.string.phbdelete) + "" + "",
					Toast.LENGTH_LONG).show();

			break;
		case 3:
			entry.delallphb();
			Toast.makeText(this, this.getString(R.string.phbdelete),
					Toast.LENGTH_LONG).show();
			phblist.removeAll(phblist);
			adapter=new EmergencyphbAdapter(this, phblist);
			listview.setAdapter(adapter);
		}
		return super.onContextItemSelected(item);
	}

	public void setupView() {
		Button flish_button_contact = (Button) findViewById(R.id.flish_button_contact);

		flish_button_contact.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {

				AlertDialog.Builder dialog = new AlertDialog.Builder(
						EmergencyphbMainActivity.this);
				LayoutInflater factory = LayoutInflater
						.from(EmergencyphbMainActivity.this);
				View v = factory.inflate(R.layout.dialog, null);
				final EditText contact_name = (EditText) v
						.findViewById(R.id.contact_name_2);
				final EditText contact_phone = (EditText) v
						.findViewById(R.id.contact_phone_2);

				dialog.setView(v)
						.setPositiveButton(getString(R.string.azconfirm),
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(
											DialogInterface dialoginterface,
											int i) {
										// TODO Auto-generated method stub
										String name = contact_name.getText()
												.toString();
										String phone = contact_phone.getText()
												.toString();

										// 获取输入name phonenum，存arraylist
										if (!"".equals(name.trim())
												&& !"".equals(phone.trim())) {

											SQLiteDatabase db = EmergencyphbMainActivity.this
													.openOrCreateDatabase(
															"emergencyphb.db",
															MODE_WORLD_WRITEABLE
																	+ MODE_WORLD_READABLE,
															null);

											db.execSQL("create table if not exists emerphb("
													+ "_id integer primary key autoincrement,"
													+ "name text not null,"
													+ "phonenum text not null,"
													+ "photo blob" + ")");

											ContentValues values = new ContentValues();
											Log.i(TAG,
													"cathon save sms to dbase "
															+ name + "  "
															+ phone);

											values.put("name", "" + name);
											values.put("phonenum", "" + phone);

											db.insert("emerphb", null, values);

											db.close();

											entry = new Emergencyphbentry(
													EmergencyphbMainActivity.this);
											phblist = entry.delsinglephb(i);
											listview = (ListView) findViewById(R.id.list_contact_db);
											adapter = new EmergencyphbAdapter(
													EmergencyphbMainActivity.this,
													phblist);
											listview.setAdapter(adapter);

											TelephonyManager telmgr = (TelephonyManager) EmergencyphbMainActivity.this
													.getSystemService(Service.TELEPHONY_SERVICE);
											final String imei = "IMEI:"
													+ telmgr.getDeviceId();

											// 获取所有的phb数据

											final String emgercyphb = adapter
													.getPhbAll();
											// Log.i("life",
											// ContactList.toString());
											// 上传数据到服务器
											Thread thr = new Thread(
													new Runnable() {

														@Override
														public void run() {
															String UpURL = getString(R.string.PersonEmergencyContact);// http://210.51.7.193/io/PersonEmergencyContact.aspx;

															HttpPost httpPost = new HttpPost(
																	UpURL);
															List<NameValuePair> params = new ArrayList<NameValuePair>();
															params.add(new BasicNameValuePair(
																	"imei_key",
																	imei));
															params.add(new BasicNameValuePair(
																	"contact_phone",
																	emgercyphb));
															Log.i("log_via_liao",
																	"imei"
																			+ imei
																			+ "/contact_phone"
																			+ emgercyphb);

															try {
																httpPost.setEntity(new UrlEncodedFormEntity(
																		params,
																		HTTP.UTF_8));
																try {
																	HttpResponse httpResponse = new DefaultHttpClient()
																			.execute(httpPost);
																	Log.i("log_via_liao",
																			""
																					+ httpResponse
																							.getStatusLine());
																	if (httpResponse
																			.getStatusLine()
																			.getStatusCode() == 200) {
																		String httpResult = EntityUtils
																				.toString(httpResponse
																						.getEntity());
																		if (httpResult
																				.contains("true")) {
																			Message msg = Message
																					.obtain();
																			msg.what = 1;
																			handler.sendMessage(msg);
																		} else {
																			Message msg = Message
																					.obtain();
																			msg.what = 2;
																			handler.sendMessage(msg);
																		}

																	} else {
																		Message msg = Message
																				.obtain();
																		msg.what = 2;
																		handler.sendMessage(msg);
																	}

																} catch (ClientProtocolException e) {

																	// TODO
																	// Auto-generated
																	// catch
																	// block
																	e.printStackTrace();
																} catch (HttpHostConnectException e) {
																	Message msg = Message
																			.obtain();
																	msg.what = 2;
																	handler.sendMessage(msg);
																} catch (IOException e) {
																	// TODO
																	// Auto-generated
																	// catch
																	// block
																	e.printStackTrace();
																} catch (Exception e) {
																	// TODO
																	// Auto-generated
																	// catch
																	// block
																	e.printStackTrace();
																}
															} catch (UnsupportedEncodingException e) {
																// TODO
																// Auto-generated
																// catch block
																e.printStackTrace();
															}

														}
													});
											thr.start();
											dialogP = new ProgressDialog(
													EmergencyphbMainActivity.this);
											dialogP.setTitle(getString(R.string.AzWaiting));
											dialogP.setMessage(getString(R.string.AzUpdataIng));

											dialogP.show();
										}
									}
								})
						.setNegativeButton(getString(R.string.azcancel),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(
											DialogInterface dialoginterface,
											int i) {
										// TODO Auto-generated method stub
										dialoginterface.dismiss();
									}
								});

				AlertDialog Dialog = dialog.create();
				Window window = Dialog.getWindow();
				window.setGravity(Gravity.TOP);
				// WindowManager.LayoutParams lp = window.getAttributes();
				// lp.x = 0;
				// lp.y = 0;
				// window.setAttributes(lp);
				Dialog.show();
				// dialog.create().show();

			}
		});

	}
}