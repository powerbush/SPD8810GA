package com.android.contacts;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class GalleryPhoneEditActivity extends Activity {

	private Button btnComfirm;
	private Button btnNew;
	private Button btnCancel;
	private String TAG = "GalleryPhoneEditActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.edit_name_phone_numbler);
		setupView();
	}

	public void setupView() {
		Intent intent = getIntent();

		final Boolean isAddMode = intent.getBooleanExtra ("mode", false);
		final String contactName = intent.getStringExtra("name");
		final String contactPhone = intent.getStringExtra("phone");
		final EditText nameTextView = (EditText) findViewById(R.id.phone_name_contact_edit);
		nameTextView.setText(contactName);
		final EditText phoneTextView = (EditText) findViewById(R.id.phone_number_contact_edit);
		phoneTextView.setText(contactPhone);

		btnCancel = (Button) findViewById(R.id.phone_contact_edit_btn_canel);
		btnNew = (Button) findViewById(R.id.phone_contact_edit_btn_new);
		btnComfirm = (Button) findViewById(R.id.phone_contact_edit_btn_ok);

		if(isAddMode){
			btnNew.setVisibility(View.VISIBLE);
			btnComfirm.setVisibility(View.GONE);
		}else{
			btnNew.setVisibility(View.GONE);
			btnComfirm.setVisibility(View.VISIBLE);
		}

		btnCancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				finish();
			}
		});

		btnNew.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				String name = nameTextView.getText().toString().trim();
				String phone = phoneTextView.getText().toString().trim();
				Toast toast = null;
				TextView textView = null;
				if("" == "" + name){
					toast = new Toast(GalleryPhoneEditActivity.this);
					textView = new TextView(GalleryPhoneEditActivity.this);
					textView.setText(R.string.alert_name);
					textView.setBackgroundResource(R.drawable.toast_warnning);
					textView.setTextSize(25);
					textView.setTextColor(Color.BLACK);

					toast.setView(textView);
					toast.setDuration(Toast.LENGTH_LONG);
					toast.setGravity(Gravity.TOP, -60, 105);
					toast.show();
					return;
				} else if ("" == "" + phone) {
					toast = new Toast(GalleryPhoneEditActivity.this);
					textView = new TextView(GalleryPhoneEditActivity.this);
					textView.setText(R.string.alert_num);
					textView.setBackgroundResource(R.drawable.toast_warnning);
					textView.setTextSize(25);
					textView.setTextColor(Color.BLACK);

					toast.setView(textView);
					toast.setDuration(Toast.LENGTH_LONG);
					toast.setGravity(Gravity.TOP, -60, 200);
					toast.show();
					return;
				}

				ContentValues values = new ContentValues();
				//首先向RawContacts.CONTENT_URI执行一个空值插入，目的是获取系统返回的rawContactId
				Uri rawContactUri = getContentResolver().insert(RawContacts.CONTENT_URI, values);
				long rawContactId = ContentUris.parseId(rawContactUri);
				
				//往data表入姓名数据
				values.clear();
				values.put(Data.RAW_CONTACT_ID, rawContactId);
				values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
				values.put(StructuredName.GIVEN_NAME, name);
				getContentResolver().insert(android.provider.ContactsContract.Data.CONTENT_URI, values);
				
				//往data表入电话数据
				values.clear();
				values.put(android.provider.ContactsContract.Contacts.Data.RAW_CONTACT_ID, rawContactId);
				values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
				values.put(Phone.NUMBER, phone);
				values.put(Phone.TYPE, Phone.TYPE_MOBILE);
				getContentResolver().insert(android.provider.ContactsContract.Data.CONTENT_URI, values);
				Intent i=new Intent(GalleryPhoneEditActivity.this, GalleryContactPhoneActivity.class);
				startActivity(i);
				finish();
			}
		});

		btnComfirm.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				String name = nameTextView.getText().toString().trim();
				String phone = phoneTextView.getText().toString().trim();
				Toast toast = null;
				TextView textView = null;
				if("" == "" + name){
					toast = new Toast(GalleryPhoneEditActivity.this);
					textView = new TextView(GalleryPhoneEditActivity.this);
					textView.setText(R.string.alert_name);
					textView.setBackgroundResource(R.drawable.toast_warnning);
					textView.setTextSize(25);
					textView.setTextColor(Color.BLACK);

					toast.setView(textView);
					toast.setDuration(Toast.LENGTH_LONG);
					toast.setGravity(Gravity.TOP, -60, 105);
					toast.show();
					return;
				} else if ("" == "" + phone) {
					toast = new Toast(GalleryPhoneEditActivity.this);
					textView = new TextView(GalleryPhoneEditActivity.this);
					textView.setText(R.string.alert_num);
					textView.setBackgroundResource(R.drawable.toast_warnning);
					textView.setTextSize(25);
					textView.setTextColor(Color.BLACK);

					toast.setView(textView);
					toast.setDuration(Toast.LENGTH_LONG);
					toast.setGravity(Gravity.TOP, -60, 200);
					toast.show();
					return;
				}

				ContentResolver contentResolver = getContentResolver();
				Cursor cursorId = null;

				/*
				 * 1.读取联系人　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　
				 * 1.1.手机读取　　　　　　　　　　　　　　　　　　　　　　　
				 * 1.2.操作SIM卡中的联系人　　　　　　　　　　　　　　　　　　　　　　　
				 * 2.保存联系人　　　　　　　　　　　　　　　　　　　　　　　
				 */

				String _id = "";

				// 1.1.手机读取_id
				Uri uriRawContacts = Uri
						.parse("content://com.android.contacts/raw_contacts");
				String[] projection = new String[] { "_id" };
				String selection = " display_name = ? ";
				String[] selectionArgs = new String[] { contactName };

				/*
				 * uri:要查询的内容提供者(content provider)的URI 　　　　　
				 * projection:要返回的columns列表 　　　　　　　　　　　　　　　
				 * selection:SQL语句的where子句
				 * selectionArgs:selection的参数，如果包含?，?号将会被参数所替换
				 * sortOrder:SQL的ORDER BY排序子句
				 */
				cursorId = contentResolver.query(uriRawContacts, projection,
						selection, selectionArgs, null);

				while (cursorId.moveToNext()) {
					// 按名字查询查询ID ，id可能是多个
					String raw_contact_id = cursorId.getString(cursorId
							.getColumnIndex("_id"));
					Uri uriData = Uri
							.parse("content://com.android.contacts/data");
					projection = new String[] { "data1" };
					selection = " raw_contact_id = ? and mimetype = ? ";
					selectionArgs = new String[] { raw_contact_id,
							"vnd.android.cursor.item/phone_v2" };

					Cursor cursorData1 = contentResolver.query(uriData,
							projection, selection, selectionArgs, null);

					while (cursorData1.moveToNext()) {
						String data1 = cursorData1.getString(cursorData1
								.getColumnIndex("data1"));
						if (data1.equals(contactPhone)) {
							_id = raw_contact_id;
							break;
						}
					}
					cursorData1.close();
				}

				//1.2.操作SIM卡中的联系人（如果手机上不包括此联系人，考虑做一个判断）
				int rows = -1;
				// if (_id == null || _id == "") {  //条件不满足执行不到
				Uri uriSim = Uri.parse("content://icc/adn");
				ContentValues values = new ContentValues();
				//添加联系人到SIM卡
				values.put("tag", contactName);
				values.put("number", contactPhone);
				values.put("newTag", nameTextView.getText().toString());
				values.put("newNumber", phoneTextView.getText().toString());
				
				//更新SIM卡联系人
				rows = getContentResolver().update(uriSim, values, null, null);
				// }
				Log.i(TAG, "update sim row count:" + rows);

				// 2.保存联系人
				if (!(_id == null || _id == "")) {

					Uri uriData = Uri
							.parse("content://com.android.contacts/data");
					// 更新名字
					values = new ContentValues();
					// 为了防止姓氏、中间名、名字编辑出错，先清空
					values.put("data3", "");// 姓氏 Family Name
					values.put("data5", "");// 中间名 Middle Name
					values.put("data2", "");// 名字 First Name
					values.put("data1", name);
					// values.put("mimetype", "vnd.android.cursor.item/name");
					String where = " raw_contact_id = ? and mimetype = ? ";
					selectionArgs = new String[] { _id,
							"vnd.android.cursor.item/name" };
					rows = getContentResolver().update(uriData, values, where,
							selectionArgs);
					Log.i(TAG, "update name row count:" + rows);
					// 更新号码
					values = new ContentValues();
					values.put("data1", phone);
					// values.put("mimetype",
					// "vnd.android.cursor.item/phone_v2");
					where = " raw_contact_id = ? and mimetype = ? ";
					selectionArgs = new String[] { _id,
							"vnd.android.cursor.item/phone_v2" };
					rows = getContentResolver().update(uriData, values, where,
							selectionArgs);
					Log.i(TAG, "update phone row count:" + rows);
				}

				cursorId.close();
				finish();
			}
		});

	}
}
