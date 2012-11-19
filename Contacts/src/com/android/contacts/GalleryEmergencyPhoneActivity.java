package com.android.contacts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class GalleryEmergencyPhoneActivity extends Activity {
    /** Called when the activity is first created. */
    private GalleryEmergencyEntry entry;
    private GalleryEmergencyAdapter adapter;
    private Gallery list;
    private ArrayList<GalleryContactEntry> gaentry;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setRequestedOrientation(GalleryContactPhoneActivity.SCREEN_ORIENTATION_LANDSCAPE);//处理相片旋转90°，报错！
        setContentView(R.layout.main_gallery);
        setupView();
    }
    
    public void setupView(){
    	entry=new GalleryEmergencyEntry(this);
    	gaentry=entry.getContactPhones();
    	adapter=new GalleryEmergencyAdapter(this, gaentry);
    	list=(Gallery) findViewById(R.id.gallery_contact_phone);
    	list.setAdapter(adapter);
    	
    	
    }
    @Override
    protected void onResume() {
    	// TODO Auto-generated method stub
    	setupView();
    	super.onResume();
    }

    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	// TODO Auto-generated method stub
    	super.onActivityResult(requestCode, resultCode, data);
    	

    	if(resultCode==RESULT_OK){
            try{
                Uri uri=data.getData();//获得图片的uri 
                ContentResolver cResolver=getContentResolver();
                String [] proj={MediaStore.Images.Media.DATA};
                Cursor sorCursor=managedQuery(uri, proj, null, null, null);
                
                int column_index = sorCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);//获得用户选择的图片的索引值
                sorCursor.moveToFirst();
                String pathString=sorCursor.getString(column_index);//根据索引值获取图片路径
                Bitmap bmp=BitmapFactory.decodeFile(pathString);
                Log.i("life", ""+bmp+pathString);
                adapter.upData(requestCode,pathString);
            } catch(Exception e) {
            	/*
                adapter.upData(requestCode,"/sdcard/test.jpg");   //报空
                Toast.makeText(this, ""+requestCode, Toast.LENGTH_SHORT).show();
                */

                adapter.upData(requestCode, Environment.getExternalStorageDirectory() + "/temp.jpg");
                try{
                    File file=new File(Environment.getExternalStorageDirectory() + "/temp.jpg");
                    file.delete();
                } catch(Exception ex) {

                }

            }
    	
    	}else{
    		Toast.makeText(this, getString(R.string.contact_phone_please_select_image), Toast.LENGTH_SHORT).show();
    	}
    }
    

    
 }