package com.android.contacts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.android.contacts.CallDetailActivity.ViewEntry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class GalleryAdapter extends BaseAdapter {
	private Context context;
	private ArrayList<GalleryContactEntry> galleryContactEntries;
	private LayoutInflater inflater;
	public ImageView phone_contact_imageView;

	public GalleryAdapter(Context context, ArrayList<GalleryContactEntry> ga) {
		this.context = context;
		this.galleryContactEntries = ga;
		inflater = LayoutInflater.from(context);
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
		View v = inflater.inflate(R.layout.gallery_item_contact, null);
		TextView name = (TextView) v.findViewById(R.id.phone_contact_name);
		TextView phoneTextView = (TextView) v
				.findViewById(R.id.phone_contact_numbler);
		phone_contact_imageView = (ImageView) v
				.findViewById(R.id.phone_contact_image);

		final GalleryContactEntry gcEntry = galleryContactEntries.get(i);

		name.setText(gcEntry.getContactName());
		/* by liao 取消文字点击事件 添加按钮
		name.setOnClickListener(new android.view.View.OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				Intent intentnameIntent=new Intent(context,PhoneEditActivity.class);
				
				//需要加入当前页面参数过去。
				intentnameIntent.putExtra("name", gcEntry.getContactName());
				intentnameIntent.putExtra("phone", gcEntry.getContactPhone());
				
				context.startActivity(intentnameIntent);
			}
		});*/
		phoneTextView.setText(gcEntry.getContactPhone());
		phoneTextView.setVisibility(View.GONE);
		/* by liao 取消文字点击事件 添加按钮
		phoneTextView.setOnClickListener(new android.view.View.OnClickListener(){

			@Override
			public void onClick(View view) {
				// TODO Auto-generated method stub
				Intent intentnameIntent=new Intent(context,PhoneEditActivity.class);
				
				//需要加入当前页面参数过去。
				intentnameIntent.putExtra("name", gcEntry.getContactName());
				intentnameIntent.putExtra("phone", gcEntry.getContactPhone());
				
				context.startActivity(intentnameIntent);
			}
			
		});*/
		//by liao 取消文字点击事件 添加按钮
		((Button) v.findViewById(R.id.phone_contact_edit_btn))
				.setOnClickListener(new android.view.View.OnClickListener() {

					@Override
					public void onClick(View view) {
						// TODO Auto-generated method stub
						Intent intentnameIntent = new Intent(context,
								GalleryPhoneEditActivity.class);
				
						//需要加入当前页面参数过去。
						intentnameIntent.putExtra("name",
								gcEntry.getContactName());
						intentnameIntent.putExtra("phone",
								gcEntry.getContactPhone());

						context.startActivity(intentnameIntent);
					}

				});
		((Button) v.findViewById(R.id.phone_contact_add_btn))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Intent intent = new Intent(context,GalleryPhoneEditActivity.class);
						intent.putExtra("mode", true);
						context.startActivity(intent);

					}
				});
		((Button) v.findViewById(R.id.phone_contact_call_btn))
				.setOnClickListener(new android.view.View.OnClickListener() {

					@Override
					public void onClick(View view) {
						// TODO Auto-generated method stub
						Intent intent = new Intent(
								"android.intent.action.CALL", Uri.parse("tel:"
										+ gcEntry.getContactPhone()));

						context.startActivity(intent);
					}

				});
		((Button) v.findViewById(R.id.phone_contact_sms_btn))
				.setOnClickListener(new OnClickListener() {
					
					List<ViewEntry> actions = new ArrayList<ViewEntry>();
                    
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
	                    Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
	                            Uri.fromParts("sms", gcEntry.getContactPhone(), null));
	                    actions.add(new ViewEntry(R.drawable.sym_action_sms,
	                            context.getString(R.string.menu_sendTextMessage), smsIntent));
	                    context.startActivity(smsIntent);
		
					}
				});
		
		phone_contact_imageView
				.setOnLongClickListener(new android.view.View.OnLongClickListener() {

					@Override
					public boolean onLongClick(View view) {

						CharSequence[] items = {
								context.getString(R.string.contact_phone_take_photo),
								context.getString(R.string.contact_phone_select_image),
								context.getString(R.string.contact_phone_delete_image) };
						new AlertDialog.Builder(context)
								.setItems(
										items,
										new android.content.DialogInterface.OnClickListener() {

											@Override
											public void onClick(
													DialogInterface dialoginterface,
													int i) {
												// TODO Auto-generated method
												// stub
												if (i == 0) {
													Intent intent = new Intent(
															MediaStore.ACTION_IMAGE_CAPTURE);
													//by liao 传入照片输出地址 getDataDirectory ()

													intent.putExtra(
															MediaStore.EXTRA_OUTPUT,
															Uri.fromFile(new File(
																	Environment
																			.getExternalStorageDirectory()
																			+ "/temp.jpg")));
													((Activity) context)
															.startActivityForResult(
																	intent,
																	gcEntry.getImageId());
												} else if (i == 1) {
													Intent intent = new Intent(
															Intent.ACTION_GET_CONTENT);
													intent.addCategory(Intent.CATEGORY_OPENABLE);
													intent.setType("image/*");
													((Activity) context)
															.startActivityForResult(
																	Intent.createChooser(
																			intent,
																			context.getString(R.string.contact_phone_select_image)),
																	gcEntry.getImageId());

												} else {
													//via liaobz 删除照片
													// phone_contact_imageView.setImageResource(R.drawable.contact_phone_image_1);
													SQLiteDatabase db = context
															.openOrCreateDatabase(
																	"contactphoto.db",
																	Context.MODE_WORLD_WRITEABLE
																			+ Context.MODE_WORLD_READABLE,
																	null);
													db.execSQL("delete from contacttbl where contact_id = "
															+ gcEntry
																	.getImageId());
													db.close();
													notifyDataSetChanged();
												}

											}
										}).create().show();

						return false;

					}

				});
		SQLiteDatabase db = context.openOrCreateDatabase("contactphoto.db",
				Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE,
				null);
		db.execSQL("create table if not exists contacttbl("
				+ "_id integer primary key autoincrement,"
				+ "contact_id text not null," + "image blob not null" + ")");
		Cursor cursor = db.rawQuery(
				"select * from contacttbl where contact_id="
						+ gcEntry.getImageId(), null);
		if (cursor != null) {
			Log.i("gancuirong", "cursor=" + cursor);
			while (cursor.moveToNext()) { 
				// Log.i("gancuirong",
				// "cursor.moveToNext()==========="+cursor.moveToNext());
				// String
				// pathString=cursor.getString(cursor.getColumnIndex("path_image"));
				// phone_contact_imageView.setImageBitmap(BitmapFactory.decodeFile(pathString));

				// byte[] imageBytes = cursor.getBlob(cursor
				// .getColumnIndexOrThrow("image"));

				byte[] imageBytes = cursor.getBlob(cursor
						.getColumnIndex("image"));
				phone_contact_imageView.setImageBitmap(BitmapFactory
						.decodeByteArray(imageBytes, 0, imageBytes.length));
			}
		}
		cursor.close();
		db.close();
		return v;
	}

	public void upData(int i, String path) {
		ContentValues contentValues = new ContentValues();
		SQLiteDatabase db = context.openOrCreateDatabase("contactphoto.db",
				Context.MODE_WORLD_WRITEABLE + Context.MODE_WORLD_READABLE,
				null);
		db.execSQL("create table if not exists contacttbl("
				+ "_id integer primary key autoincrement,"
				+ "contact_id text not null," + "image blob not null" + ")");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// new BitmapFactory().decodeFile(path).compress(CompressFormat.JPEG,
		// 100, out);
		getBitmap(path, 480, 640).compress(CompressFormat.JPEG, 100, out);
		try {
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		contentValues.put("contact_id", String.valueOf(i));
		contentValues.put("image", out.toByteArray());
		db.execSQL("delete from contacttbl where contact_id = "
				+ String.valueOf(i));
		db.insert("contacttbl", null, contentValues);
		db.close();
	}

	public Bitmap getBitmap(String path, int width, int height) {
		Bitmap bm = null;
		Options opts = new Options();
		opts.inJustDecodeBounds = true;
		bm = BitmapFactory.decodeFile(path, opts);
		int xScale = opts.outWidth / width;// 计算横向的收缩比
		int yScale = opts.outHeight / height;// 计算纵向的收缩比
		int scale = xScale > yScale ? xScale : yScale;// 实际收缩比取二者中的大值
		opts.inJustDecodeBounds = false;
		opts.inSampleSize = scale;
		bm = BitmapFactory.decodeFile(path, opts);
		return bm;
	}
}
