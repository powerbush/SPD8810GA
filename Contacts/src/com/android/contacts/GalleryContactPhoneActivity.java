package com.android.contacts;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Gallery;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class GalleryContactPhoneActivity extends Activity {
    /** Called when the activity is first created. */
    private GalleryEntry entry;
    private GalleryAdapter adapter;
    private Gallery list;
    private ArrayList<GalleryContactEntry> gaentry;
    private Button btnJump;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main_gallery);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        setupView();
    }
    
    public void setupView(){
    	entry=new GalleryEntry(this);
    	gaentry=entry.getContactPhone();
    	adapter=new GalleryAdapter(this, gaentry);
    	list=(Gallery) findViewById(R.id.gallery_contact_phone);
    	list.setAdapter(adapter);
    	
        btnJump = (Button)findViewById(R.id.jump_button);
        btnJump.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(GalleryContactPhoneActivity.this, ContactsListActivity.class);
                startActivity(intent);
            }
        });
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
                //via liaobz 取完照片返回默认由图库传回.如果异常 改为取相机方式(需后续优化)
                Uri uri=data.getData();//获得图片的uri 
                ContentResolver cResolver=getContentResolver();
                String [] proj={MediaStore.Images.Media.DATA};
                Cursor sorCursor=managedQuery(uri, proj, null, null, null);
                
                int column_index = sorCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                sorCursor.moveToFirst();
                String pathString=sorCursor.getString(column_index);//根据索引值获取图片路径
                Bitmap bmp=BitmapFactory.decodeFile(pathString);
                Log.i("life", ""+bmp+pathString);
                adapter.upData(requestCode,pathString);
            } catch(Exception e) {
                adapter.upData(requestCode, Environment.getExternalStorageDirectory() + "/temp.jpg");
                try{
                    File file=new File(Environment.getExternalStorageDirectory() + "/temp.jpg");
                    file.delete();
                } catch(Exception ex) {

                }
            }
    		
			/*try {
				Bitmap bmp = BitmapFactory.decodeStream(cResolver.openInputStream(uri));
				if(bmp!=null){
	    			bmp.recycle();
	    			try {
						bmp=BitmapFactory.decodeStream(cResolver.openInputStream(uri));
						
						Log.i("life", ""+bmp);
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    		}
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}*/
    		
    	}else{
    		Toast.makeText(this, getString(R.string.contact_phone_please_select_image), Toast.LENGTH_SHORT).show();
    	}
    }
}