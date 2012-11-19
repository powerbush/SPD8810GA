package com.android.contacts;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

public class GalleryEntry {
	private Context context;
	public GalleryEntry(Context context){
		this.context=context;
	}
	

	public ArrayList<GalleryContactEntry>  getContactPhone(){
		 ContentResolver cont=context.getContentResolver();
		 
		  Uri uri = Contacts.CONTENT_URI;
	        String[] projection = {
	            	Contacts._ID,
	            	Contacts.DISPLAY_NAME	
	            };
		Cursor cursor=cont.query(uri, projection, null, null, Contacts.DISPLAY_NAME + " desc");
		
		ArrayList<GalleryContactEntry> phones =new ArrayList<GalleryContactEntry>();
		while(cursor.moveToNext()){
			
			int id =cursor.getInt( cursor.getColumnIndex(Contacts._ID));
			String s=cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
			//查询电话号码按ID
			Cursor cur = context.getContentResolver().query(Phone.CONTENT_URI, new String[]{Phone.NUMBER}, Phone.CONTACT_ID+"=?", new String[]{id+""}, null);
			if(cur!=null){
				while(cur.moveToNext()){
					//Log.i("life", "电话：" +s+ cur.getString(cur.getColumnIndex(Phone.NUMBER)));
					GalleryContactEntry phoneContactEntry =new GalleryContactEntry();
					phoneContactEntry.setContactName(s);
					phoneContactEntry.setContactPhone(cur.getString(cur.getColumnIndex(Phone.NUMBER)));
					phoneContactEntry.setImageId(id);
					phones.add(phoneContactEntry);
					//phones.add(s+"@@"+cur.getString(cur.getColumnIndex(Phone.NUMBER)));
				}
				cur.close();
			}
		
		
		}
		cursor.close();
		return phones;
	}
	
	
	
}
