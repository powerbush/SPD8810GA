package com.android.contacts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class GalleryEmergencyEntry {
	private Context context;
	public GalleryEmergencyEntry(Context context) {
		this.context = context;
	}

	public ArrayList<GalleryContactEntry> getContactPhones() {
		
		//String databaseFilename = "/data/data/com.az.Main/databases/paddy_database";
		String databaseFilename = "/data/data/com.az.Main/databases/emergencyphb.db";
		
		/*
	    File dir = new File("/data/data/com.az.Main/databases");
	    // 判断文件夹是否存在，不存在就新建一个
	    if (!dir.exists())    
	         dir.mkdir();
	    //判断数据库文件是否存在，若不存在则执行导入，否则直接打开数据库
	    if (!(new File(databaseFilename)).exists()) {    
	    	
	    	Log.i("gancuirong", "will new file======================");
	    	
	    	// 获得封装paddy_database.db文件的InputStream对象
		     InputStream is = context.getResources().openRawResource(R.raw.paddy_database);    
		     Log.i("gancuirong", "InputStream is="+is);
		     
		     // 得到数据库文件的写入流 
		     FileOutputStream fos = new FileOutputStream(databaseFilename);    
		     Log.i("gancuirong", "FileOutputStream fos"+fos);
		    
		     byte[] buffer = new byte[8192];
		     int count = 0;
		     // 开始复制paddy_database.db文件
	         while ((count = is.read(buffer)) > 0) {
	              fos.write(buffer, 0, count);
	         }
	         fos.close();
	         is.close();
	    }
	    */
		
	    ArrayList<GalleryContactEntry> phones = new ArrayList<GalleryContactEntry>();
	    try{
		
			SQLiteDatabase db = context.openOrCreateDatabase(databaseFilename,Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE,null);
			Log.i("gancuirong", "db======databaseFilename"+db);
			
			//Cursor cursor = db.query("database01",new String[]{"nameId","nameFlag","name"},"nameId='phoneId'",null,null,null,null);
			Cursor cursor=db.query("emerphb", new String[]{"_id","name","phonenum","photo"}, null, null, null, null, null);
			Log.i("gancuirong", cursor+"cursor=============");
			if(cursor!=null){
				while(cursor.moveToNext()){
					GalleryContactEntry phoneContactEntry = new GalleryContactEntry();
					// phoneContactEntry.setContactPhone(cursor.getString(cursor.getColumnIndex("nameId")));//联系人的电话号码
					// phoneContactEntry.setImageId(Integer.valueOf(phoneContactEntry.getContactPhone().substring(2)));//唯一标识 
					// phoneContactEntry.setContactName(cursor.getString(cursor.getColumnIndex("nameFlag")));//联系人的名字
					
					phoneContactEntry.setImageId(cursor.getInt(cursor.getColumnIndex("_id")));
					phoneContactEntry.setContactPhone(cursor.getString(cursor.getColumnIndex("phonenum")));
					phoneContactEntry.setContactName(cursor.getString(cursor.getColumnIndex("name")));
					
					phones.add(phoneContactEntry);
				}
				cursor.close();
			}		
			db.close();
		}
		catch(Exception e){}
		return phones;
	}


}
