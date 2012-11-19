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

public class PhoneEditActivity extends Activity{
	private Button canelButton;
	private Button okButton;
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
				Log.i("life", nameTextView.getText().toString());
				Uri uri1=Uri.parse("content://com.android.contacts/raw_contacts");
				cursor=contentResolver.query(uri1, proStrings, "display_name =?", new String[]{name}, null);
				while(cursor.moveToNext()){
					//�����ֲ�ѯ��ѯID ��id�����Ƕ��
					String _id=cursor.getString(cursor.getColumnIndex("_id"));
					Log.i("life", "��ϵ��ID raw_contact_id="+_id);
					Uri uri3=Uri.parse("content://com.android.contacts/data");
					//��ID��ȡ�绰����
					Cursor cursor2=contentResolver.query(uri3, new String[]{"data1"}, "raw_contact_id =?"+" and data2=2", new String[]{_id}, null);
					while(cursor2.moveToNext()){
						String phoneStringData=cursor2.getString(cursor2.getColumnIndex("data1"));
						if(phoneStringData.equals(phoneString)){
							id=_id;
							
							
						}
					}
					cursor2.close();
				}
				
			
			//��ϵ�˵绰����
			/*
			Uri uri2 = Uri.parse("content://com.android.contacts/data");
			ContentValues values2=new ContentValues();
			values2.put("data1", phoneTextView.getText().toString());
			contentResolver.update(uri2, values2, "raw_contact_id =?"+" and data2=2",new String[]{id});*/
			if(id!=""){
				/*//ɾ�������¼
				ContentValues values3=new ContentValues();
				values3.put("deleted", 1);
				contentResolver.update(uri1, values3, "_id =?", new String[]{id});*/
				ContentValues values3=new ContentValues();
				values3.put("deleted", 1);
				contentResolver.update(uri1, values3, "_id =?", new String[]{id});
				 ContentResolver resolver = getContentResolver();     
				 Uri uri4 = Uri.parse("content://com.android.contacts/data");
				 resolver.delete(uri4, "raw_contact_id =?", new String[]{id});
				//�������
				
				 uri4 = Uri.parse("content://com.android.contacts/raw_contacts"); 
				 ContentValues values4 = new ContentValues();   
				// long ids = ContentUris.parseId(resolver.insert(uri4, values4));  
				
				 //ɾ����ϵ������
				 resolver.delete(uri4, "_id =?", new String[]{id});
				 //ɾ����ϵ�˵绰
				 
				 
				 //�����ϵ������      
				 long ids = ContentUris.parseId(resolver.insert(uri4, values4));  
				        uri4 = Uri.parse("content://com.android.contacts/data");      

				         values4.put("raw_contact_id", ids);      

				        values4.put("data2", nameTextView.getText().toString());      
				       values4.put("mimetype", "vnd.android.cursor.item/name");      

				       resolver.insert(uri4, values4);     
				     //�����ϵ�˵绰      

				             values4.clear(); // ����ϴε����      

				            values4.put("raw_contact_id", ids);      

				            values4.put("data1", phoneTextView.getText().toString());      

				                values4.put("data2", "2");      

				              values4.put("mimetype", "vnd.android.cursor.item/phone_v2");      

				              resolver.insert(uri4, values4);  

				 
				 
			}
		//�������		
		cursor.close();
		finish();
			}
		});
	}
	
}
