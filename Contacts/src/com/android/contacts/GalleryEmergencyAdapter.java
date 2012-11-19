package com.android.contacts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class GalleryEmergencyAdapter extends BaseAdapter{
	private Context context;
	private ArrayList<GalleryContactEntry> galleryContactEntries;
	private LayoutInflater inflater;
	public ImageView phone_contact_imageView;
	public GalleryEmergencyAdapter(Context context,ArrayList<GalleryContactEntry> ga){
		this.context=context;
		this.galleryContactEntries=ga;
		inflater=LayoutInflater.from(context);
	}
	
	
	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return galleryContactEntries.size();
	}

	@Override
	public Object getItem(int i) {
		// TODO Auto-generated method stub
		return galleryContactEntries.get(i);
	}

	@Override
	public long getItemId(int i) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public View getView(int i, View view, ViewGroup viewgroup) {
		// TODO Auto-generated method stub
		View v=inflater.inflate(R.layout.gallery_item_contact, null);
		TextView  name=(TextView) v.findViewById(R.id.phone_contact_name);
		TextView phoneTextView =(TextView) v.findViewById(R.id.phone_contact_numbler);
		phone_contact_imageView=(ImageView) v.findViewById(R.id.phone_contact_image);
		
		final GalleryContactEntry gcEntry=galleryContactEntries.get(i);

		name.setText(gcEntry.getContactName());
		phoneTextView.setText(gcEntry.getContactPhone());
		
		/*
		((Button)v.findViewById(R.id.phone_contact_edit_btn)).setOnClickListener(new android.view.View.OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				Intent intentnameIntent=new Intent(context,PhoneEditActivity.class);
				
				//需要加入当前页面参数过去。
				intentnameIntent.putExtra("name", gcEntry.getContactName());
				intentnameIntent.putExtra("phone", gcEntry.getContactPhone());
				
				context.startActivity(intentnameIntent);
			}

		});
		*/
		((Button)v.findViewById(R.id.phone_contact_edit_btn)).setVisibility(View.GONE);
		((Button)v.findViewById(R.id.phone_contact_call_btn)).setOnClickListener(new android.view.View.OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				Intent intent=new Intent("android.intent.action.CALL",Uri.parse("tel:" + gcEntry.getContactPhone()));

				context.startActivity(intent);
			}

		});
		phone_contact_imageView.setOnLongClickListener(new android.view.View.OnLongClickListener(){

			@Override
			public boolean onLongClick(View view) {
				
				CharSequence[] items = {context.getString(R.string.contact_phone_take_photo),context.getString(R.string.contact_phone_select_image),context.getString(R.string.contact_phone_delete_image)};
				new AlertDialog.Builder(context).setItems(items, new android.content.DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface dialoginterface, int i) {
						// TODO Auto-generated method stub
						if(i== 0 ){
							Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
							//by liao 传入照片输出地址 getDataDirectory ()
							intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Environment.getExternalStorageDirectory() + "/temp.jpg")));
							((Activity) context).startActivityForResult(intent, gcEntry.getImageId());
						}else if(i==1){
							Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
							intent.addCategory(Intent.CATEGORY_OPENABLE);
							intent.setType("image/*");
							((Activity) context).startActivityForResult(Intent.createChooser(intent, context.getString(R.string.contact_phone_select_image)), gcEntry.getImageId());

						}else{
							
							SQLiteDatabase db=context.openOrCreateDatabase("contactphoto.db", Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE, null);
		 					db.execSQL("delete from emergencyinfo where contact_id = " + gcEntry.getImageId());
		 					db.close();
		 					notifyDataSetChanged();
						}
					}}).create().show();
				return false;

			}
			
		});
		SQLiteDatabase db=context.openOrCreateDatabase("contactphoto.db", Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE, null);
		 db.execSQL("create table if not exists emergencyinfo(" +
	        		"_id integer primary key autoincrement," +
	        		"contact_id text not null," +
	        		"image blob not null" +
	        		")");

		Cursor cursor=db.rawQuery("select * from emergencyinfo where contact_id="+gcEntry.getImageId(), null);
		if(cursor!=null){
			while(cursor.moveToNext()){
				//String pathString=cursor.getString(cursor.getColumnIndex("path_image"));
				//phone_contact_imageView.setImageBitmap(BitmapFactory.decodeFile(pathString));
				byte[] imageBytes=cursor.getBlob(cursor.getColumnIndex("image"));
				//Log.i("log_via_liao", "" + imageBytes);
				phone_contact_imageView.setImageBitmap(BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length));
			}
		}
		cursor.close();
		db.close();
		return v;
	}
	
	public void upData(int i,String path){
		ContentValues contentValues = new ContentValues(); 
		SQLiteDatabase db=context.openOrCreateDatabase("contactphoto.db", Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE, null);
		db.execSQL("create table if not exists emergencyinfo(" +
					"_id integer primary key autoincrement," +
					"contact_id text not null," +
					"image blob not null" +
					")");

		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new BitmapFactory().decodeFile(path).compress(CompressFormat.JPEG, 100, out);
		try {
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		contentValues.put("contact_id",String.valueOf(i));
		contentValues.put("image",out.toByteArray());
		db.execSQL("delete from emergencyinfo where contact_id = " + String.valueOf(i));
		db.insert("emergencyinfo", null, contentValues);
		db.close();
	}
}
