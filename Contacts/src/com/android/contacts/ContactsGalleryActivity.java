package com.android.contacts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ContactsGalleryActivity extends Activity {
	private Button mListGalleryBtnButton;
	private Button mListBtnButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.btn_gallery);
		mListGalleryBtnButton=(Button) findViewById(R.id.contact_list_gallery_btn);
		mListBtnButton=(Button) findViewById(R.id.contact_list_btn);
		
		mListGalleryBtnButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Intent intentGallery=new Intent	(ContactsGalleryActivity.this,GalleryContactPhoneActivity.class);
            	startActivity(intentGallery);
			}
		});
		
		mListBtnButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Intent intentList=new Intent(ContactsGalleryActivity.this, ContactsListActivity.class);
				startActivity(intentList);
			}
		});
	}
	
}
