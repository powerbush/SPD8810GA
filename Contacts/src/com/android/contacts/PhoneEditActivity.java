package com.android.contacts;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PhoneEditActivity extends Activity{
	private Button canelButton;
	private Button okButton;
	private String TAG="gancuirong";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.edit_name_phone_numbler);
		setupView();
	}
	public void setupView(){
		Intent intent =getIntent();
		final String name=intent.getStringExtra("name");
		final String phoneString=intent.getStringExtra("phone");
		final EditText nameTextView=(EditText) findViewById(R.id.phone_name_contact_edit);
		nameTextView.setText(name);
		final EditText phoneTextView=(EditText) findViewById(R.id.phone_number_contact_edit);
		phoneTextView.setText(phoneString);
		
		canelButton =(Button) findViewById(R.id.phone_contact_edit_btn_canel);
		
		canelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				finish();
			}
		});
		
		okButton=(Button) findViewById(R.id.phone_contact_edit_btn_ok);
		
		okButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				
				ContentResolver contentResolver=getContentResolver();
				Cursor cursor=null;
			/*	String [] pro=new String[]{
					Contacts.People._ID,Contacts.People.NAME,Contacts.People.NUMBER	
				};
				cursor=contentResolver.query(Contacts.People.CONTENT_URI, pro, Contacts.People.NAME+"=?", new String[]{name}, Contacts.People.NAME+" ASC");
			cursor.moveToFirst();
			Uri uri=Uri.withAppendedPath(Contacts.People.CONTENT_URI, cursor.getString(cursor.getColumnIndex(Contacts.People._ID)));
			ContentValues values=new ContentValues();
			values.put(Contacts.People.NAME, nameTextView.getText().toString());
			
			
			contentResolver.update(uri, values, null, null);*/
				String id="";
				String [] proStrings=new String[]{
					"_id"	
				};
				Log.i(TAG, "change_after=="+nameTextView.getText().toString());
				Uri uri1=Uri.parse("content://com.android.contacts/raw_contacts");
				Log.i(TAG, "uri1=="+uri1);
				/*
				uri:要查询的内容提供者(content provider)的URI
				projection:要返回的columns列表
				selection:SQL语句的where子句
				selectionArgs:selection的参数，如果包含?，?号将会被参数所替换
				sortOrder:SQL的ORDER BY排序子句
				*/
				cursor=contentResolver.query(uri1, proStrings, "display_name =?", new String[]{name}, null);//第三个参数
				Log.i(TAG, "cursor_PhoneEditActivity=="+cursor);
				while(cursor.moveToNext()){
					Log.i(TAG, "cursor.moveToNext==ture");
					//按名字查询查询ID ，id可能是多个
					String _id=cursor.getString(cursor.getColumnIndex("_id"));
					Log.i(TAG, "联系人ID raw_contact_id="+_id);
					Uri uri3=Uri.parse("content://com.android.contacts/data");
					Log.i(TAG, "uri3=="+uri3);
					//��ID��ȡ�绰����
					//via liaobz 2012-10-10
					//�����߼�����,������:
					//�༭��ʱ���ò���_id,��ԭ����data2(data1-data15�ڲ�ͬmimetype�´��ֵ��ͬ)
					//����mimetype=[vnd.android.cursor.item/phone_v2]ʱ,data2����������
					//��סլ���ֻ�λ����λ���档������data2=2���ֻ�,������Ȼ����!!
					Cursor cursor2=contentResolver.query(uri3, new String[]{"data1"}, "raw_contact_id =?"+" and data2=2", new String[]{_id}, null);
					Log.i(TAG, "PhoneEditAvtivity----cursor2="+cursor2);
					while(cursor2.moveToNext()){
						String phoneStringData=cursor2.getString(cursor2.getColumnIndex("data1"));
						if(phoneStringData.equals(phoneString)){
							id=_id;
							Log.i(TAG, "id==_id===="+id);
							
						}
					}
					cursor2.close();
				}
				
				//读取SIM卡中的联系人
				Uri uriSim=Uri.parse("content://icc/adn");
				Cursor mCursor=getContentResolver().query(uriSim, null, null, null, null);
				if(mCursor!=null){
					while(mCursor.moveToNext()){
						String name=mCursor.getString(mCursor.getColumnIndex("name"));
						String number=mCursor.getString(mCursor.getColumnIndex("number"));
						String idSim=mCursor.getString(mCursor.getColumnIndex("_id"));
						/*
						//获取联系人名字
						int nameFieldColumnIndex=mCursor.getColumnIndex("name");
						//获取电话号码
						int numberFieldColumnIndex=mCursor.getColumnIndex("number");
						//获取id
						int idFieldColumnIndex=mCursor.getColumnIndex("_id");
						*/
						if(number.equals(phoneString)){
							id=idSim;
						}
					}
				}
				mCursor.close();
				
			
			//联系人电话更新
			/*
			Uri uri2 = Uri.parse("content://com.android.contacts/data");
			ContentValues values2=new ContentValues();
			values2.put("data1", phoneTextView.getText().toString());
			contentResolver.update(uri2, values2, "raw_contact_id =?"+" and data2=2",new String[]{id});*/
			if(id!=""){
				/*//删除该条记录
				ContentValues values3=new ContentValues();
				values3.put("deleted", 1);
				contentResolver.update(uri1, values3, "_id =?", new String[]{id});*/
				ContentValues values3=new ContentValues();
				values3.put("deleted", 1);
				contentResolver.update(uri1, values3, "_id =?", new String[]{id});
				ContentResolver resolver = getContentResolver();     
				Uri uri4 = Uri.parse("content://com.android.contacts/data");
				resolver.delete(uri4, "raw_contact_id =?", new String[]{id});
				//插入数据

				uri4 = Uri.parse("content://com.android.contacts/raw_contacts"); 
				ContentValues values4 = new ContentValues();   
				// long ids = ContentUris.parseId(resolver.insert(uri4, values4));  

				//删除联系人姓名
				resolver.delete(uri4, "_id =?", new String[]{id});
				//删除联系人电话

				//添加联系人姓名      
				long ids = ContentUris.parseId(resolver.insert(uri4, values4));  
				uri4 = Uri.parse("content://com.android.contacts/data");      
				values4.put("raw_contact_id", ids);      
				values4.put("data2", nameTextView.getText().toString());      
				values4.put("mimetype", "vnd.android.cursor.item/name");      
				resolver.insert(uri4, values4);     
				//添加联系人电话      
				values4.clear(); // 清空上次的数据    
				values4.put("raw_contact_id", ids);      
				values4.put("data1", phoneTextView.getText().toString());      
				values4.put("data2", "2");      
				values4.put("mimetype", "vnd.android.cursor.item/phone_v2");      
				resolver.insert(uri4, values4);  
			}
			else{
				Toast.makeText(PhoneEditActivity.this, R.string.cannot_edit_contact, Toast.LENGTH_LONG).show();
			}
			//插入数据		
			cursor.close();
			finish();
			}
		});
	}
	
}
