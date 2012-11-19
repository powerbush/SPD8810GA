package com.android.mms.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.mms.R;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class NewContactReceivedActivity extends ListActivity{
	private static final String TAG = "NewContactReceivedActivity";
	
	//Contact
	private static final Pattern patternVCard = Pattern.compile(
    			"(?ms)^BEGIN:VCARD$.+?^END:VCARD$");
	
	//full name
	private static final Pattern patternFullName = Pattern.compile(
				"(?m)^FN:([^;\\r\\n]+)*.*$");
	
	//name
	private static final Pattern patternName = Pattern.compile(
			"(?m)^N:([^;\\r\\n]*)(?:;([^;\\r\\n]+))*.*$");
	
	//home phone
	private static final Pattern patternHomePhone = Pattern.compile(
			"(?m)^TEL;(TYPE=)?HOME.*:([\\(\\)\\d-_\\+]{1,20})$");
	
	//cell
	private static final Pattern patternCell = Pattern.compile(
			"(?m)^TEL;(TYPE=)?(CELL|PREF).*:([\\(\\)\\d-_\\+]{1,20})$");
	
	//office phone
	private static final Pattern patternOfficePhone = Pattern.compile(
			"(?m)^TEL;(TYPE=)?WORK.*:([\\(\\)\\d-_\\+]{1,20})$");
	
	//other phone
	private static final Pattern patternOtherPhone = Pattern.compile(
	"(?m)^TEL;(TYPE=)?VOICE.*:([\\(\\)\\d-_\\+]{1,20})$");
	
	//fax
	private static final Pattern patternFax = Pattern.compile(
			"(?m)^TEL;(TYPE=)?FAX.*:([\\(\\)\\d-_\\+]{1,20})$");
	
	//email
	private static final Pattern patternEmail = Pattern.compile(
			"(?m)^EMAIL;(HOME)?:(.{1,100})$");
	
	//birthday
	private static final Pattern patternBirthday = Pattern.compile(
			"(?m)^BDAY:(\\d{4})-?(\\d{2})-?(\\d{2})$");
	
	//address
	private static final Pattern patternAddress = Pattern.compile(
			"(?m)^ADR([^:]+):;*(.+)$");	//backup: "(?m)^ADR([^:]+):(.+)$"
	
	//Organization
	private static final Pattern patternOrganization = Pattern.compile(
				"(?m)^ORG:([^;\\r\\n]+)*.*$");
	
	//Job title
	private static final Pattern patternJobTitle = Pattern.compile(
				"(?m)^TITLE:([^;\\r\\n]+)*.*$");
	
	
	Matcher matcher;
	boolean bl;

	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.v(TAG,"oncreate()");
		setContentView(R.layout.received_contact_list);
		Intent intent = this.getIntent();
		String content;
		/*if(intent.getExtras().getString("mode").equalsIgnoreCase("test")){
			content =  intent.getExtras().getString("contents");
		}else {
			content = receivedMessage(intent);
		}*/

		content = receivedMessage(intent);
		Log.v(TAG, "content is " + content);
		//addView();
		setFields(content);
		Button saveButton = (Button) this.findViewById(R.id.save_a_new_contact);
		Button cancelButton = (Button) this.findViewById(R.id.cancel_new_contact);
		saveButton.setOnClickListener(listener);
		cancelButton.setOnClickListener(listener);
	}
	OnClickListener listener = new OnClickListener() {
		
		public void onClick(View v) {
			Button button = (Button)v;
			switch (button.getId()) {
			case R.id.save_a_new_contact:
				Log.v(TAG,"save");
				
				ContentValues values = new ContentValues();
		        //首先向RawContacts.CONTENT_URI执行一个空值插入，目的是获取系统返回的rawContactId
		        Uri rawContactUri = NewContactReceivedActivity.this.getContentResolver().insert(RawContacts.CONTENT_URI, values);
		        long rawContactId = ContentUris.parseId(rawContactUri);
		        
		        //往data表入姓名数据
		        if(NewContactReceivedActivity.this.fullname != null){
			        values.clear();
			        values.put(Data.RAW_CONTACT_ID, rawContactId);
			        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
			        values.put(StructuredName.DISPLAY_NAME, NewContactReceivedActivity.this.fullname);
			        NewContactReceivedActivity.this.getContentResolver().insert(
			                android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		        
		        //插入姓 和 名
		        if(NewContactReceivedActivity.this.firstname != null && NewContactReceivedActivity.this.lastname != null){
			        values.clear();
			        values.put(Data.RAW_CONTACT_ID, rawContactId);
			        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
			        values.put(StructuredName.DISPLAY_NAME, NewContactReceivedActivity.this.firstname + " " + NewContactReceivedActivity.this.lastname );
			        NewContactReceivedActivity.this.getContentResolver().insert(
			                android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		        
		        
		        //往data表入 cell
		        if(NewContactReceivedActivity.this.cell != null){
		        	values.clear();
		        	values.put(Data.RAW_CONTACT_ID, rawContactId);
		        	values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
		        	values.put(Phone.NUMBER, NewContactReceivedActivity.this.cell);
		        	values.put(Phone.TYPE, Phone.TYPE_MOBILE);
		        	NewContactReceivedActivity.this.getContentResolver().insert(
		        			android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		      
		      //往data表入 home phone
		        if(NewContactReceivedActivity.this.homephone != null){
		        	values.clear();
		        	values.put(Data.RAW_CONTACT_ID, rawContactId);
		        	values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
		        	values.put(Phone.NUMBER, NewContactReceivedActivity.this.homephone);
		        	values.put(Phone.TYPE, Phone.TYPE_HOME);
		        	NewContactReceivedActivity.this.getContentResolver().insert(
		        			android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		        
		      //往data表入 fax
		        if(NewContactReceivedActivity.this.fax != null){
		        	values.clear();
		        	values.put(Data.RAW_CONTACT_ID, rawContactId);
		        	values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
		        	values.put(Phone.NUMBER, NewContactReceivedActivity.this.fax);
		        	values.put(Phone.TYPE, Phone.TYPE_FAX_WORK);
		        	NewContactReceivedActivity.this.getContentResolver().insert(
		        			android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }

		      //往data表入 office phone
		        if(NewContactReceivedActivity.this.officephone != null){
		        	values.clear();
		        	values.put(Data.RAW_CONTACT_ID, rawContactId);
		        	values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
		        	values.put(Phone.NUMBER, NewContactReceivedActivity.this.officephone);
		        	values.put(Phone.TYPE, Phone.TYPE_WORK_MOBILE);
		        	NewContactReceivedActivity.this.getContentResolver().insert(
		        			android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		        
		      //往data表入Email数据
		        if(NewContactReceivedActivity.this.email != null){
		        	values.clear();
		        	values.put(android.provider.ContactsContract.Contacts.Data.RAW_CONTACT_ID, rawContactId);
		        	values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
		        	values.put(Email.DATA, NewContactReceivedActivity.this.email);
		        	values.put(Email.TYPE, Email.TYPE_WORK);
		        	NewContactReceivedActivity.this.getContentResolver().insert(
		        			android.provider.ContactsContract.Data.CONTENT_URI, values);
		        }
		        NewContactReceivedActivity.this.finish();
				break;
				
			case R.id.cancel_new_contact:
				NewContactReceivedActivity.this.finish();
				break;
			default:
				break;
			}
		}
	};
	
	private String fullname = null, firstname= null, lastname= null, homephone = null, 
			address = null, cell = null, email = null, fax = null, officephone = null, otherphonenumber = null;
	
	private void setFields(String content){
		ListView listView = getListView();
		Matcher matcherVCard = patternVCard.matcher(content);
		Matcher matcher ;
		List<HashMap<String, String>> list = new ArrayList<HashMap<String,String>>();
		HashMap<String, String> map ;
		while(matcherVCard.find()){
			String strOneContact = matcherVCard.group(0);
			System.out.println("strOneContact:"+strOneContact);
			
			//pattern full name
			matcher = patternFullName.matcher(strOneContact);
			if(matcher.find()){
				fullname = matcher.group(1);
				map = new HashMap<String, String>();
				map.put("field", "Fullname:");
				map.put("content", fullname);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern FullName");
			}
			
			//pattern name
			matcher = patternName.matcher(strOneContact);
			if(matcher.find()){
				firstname = matcher.group(1);
				lastname = matcher.group(2);
				if (lastname == null) {
					lastname = "";
				}
				map = new HashMap<String, String>();
				map.put("field", "Firstname:");
				map.put("content", firstname);
				list.add(map);
				map = new HashMap<String, String>();
				map.put("field", "Lastname:");
				map.put("content", lastname);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Name");
			}
			
			//cell phone number
			matcher = patternCell.matcher(strOneContact);
			if(matcher.find()){
				Log.v(TAG, "patternCell's result is " + matcher.group());
				cell = matcher.group(3);
				map = new HashMap<String, String>();
				map.put("field", "Cell:");
				map.put("content", cell);
				list.add(map);
			}else{
				Log.v(TAG, "can't PhoneCell number");
			}
			
			//home phone number
			matcher = patternHomePhone.matcher(strOneContact);
			if(matcher.find()){
				homephone = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Home phone:");
				map.put("content", homephone);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern HomePhone number");
			}
			
			//office phone number
			matcher = patternOfficePhone.matcher(strOneContact);
			if(matcher.find()){
				officephone = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Office phone:");
				map.put("content", officephone);
				list.add(map);
			}else {
				Log.v(TAG, "can't pattern HomePhone number");
			}
			
			//other number
			matcher = patternOtherPhone.matcher(strOneContact);
			if(matcher.find()){
				otherphonenumber = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Other phone number:");
				map.put("content", otherphonenumber);
				list.add(map);
			}else {
				Log.v(TAG, "can't pattern Other Phone number");
			}
			
			//email
			matcher = patternEmail.matcher(strOneContact);
			if(matcher.find()){
				email = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Email:");
				map.put("content", email);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Email");
			}
			
			//fax
			matcher = patternFax.matcher(strOneContact);
			if(matcher.find()){
				fax = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Fax:");
				map.put("content", fax);
				list.add(map);
			}else {
				Log.v(TAG, "can't pattern Fax");
			}
			
			//address
			matcher = patternAddress.matcher(strOneContact);
			if(matcher.find()){
				address = matcher.group(2);
				map = new HashMap<String, String>();
				map.put("field", "Address:");
				map.put("content", address);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Address");
			}
			
			//birthday
			matcher = patternBirthday.matcher(strOneContact);
			if(matcher.find()){
				String year = matcher.group(1);
				String month = matcher.group(2);
				String day = matcher.group(3);
				map = new HashMap<String, String>();
				map.put("field", "Birthday:");
				map.put("content", year + "-" + month + "-" + day);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Birthday");
			}
			
			//organization
			matcher = patternOrganization.matcher(strOneContact);
			if(matcher.find()){
				String organization = matcher.group(1);
				map = new HashMap<String, String>();
				map.put("field", "Organization:");
				map.put("content", organization);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Organization");
			}
			
			//title
			matcher = patternJobTitle.matcher(strOneContact);
			if(matcher.find()){
				String job_title = matcher.group(1);
				map = new HashMap<String, String>();
				map.put("field", "Job title:");
				map.put("content", job_title);
				list.add(map);
			}else{
				Log.v(TAG, "can't pattern Job title");
			}
		}
		SimpleAdapter adapter = new SimpleAdapter(this, list, R.layout.received_contact_item, new String[]{"field","content"}, new int[]{R.id.field,R.id.content});
		listView.setAdapter(adapter);
	}
	
	private String receivedMessage(Intent intent){
		Bundle bundle = intent.getExtras();
		Log.v(TAG,"bundle:"+bundle.toString());
        Object[] pdus = (Object[])bundle.get("pdus");
        SmsMessage[] messages = new SmsMessage[pdus.length];
        for(int i=0;i<pdus.length;i++){
            messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
        }
        StringBuilder sb = new StringBuilder();
        String content = null;
        for(SmsMessage tmp : messages){
            /*String content = tmp.getDisplayMessageBody();
            Log.v(TAG,"1:"+content);
            Log.v(TAG,"2:"+tmp.getMessageBody());
            Log.v(TAG,"3:"+tmp.getMessageClass());*/
            Log.v(TAG,"4:"+tmp.getOriginatingAddress());
            /*Log.v(TAG,"5:"+tmp.getPseudoSubject());
            Log.v(TAG,"6:"+tmp.getStatus());
            Log.v(TAG,"7:"+tmp.ENCODING_8BIT);*/
            content = new String(tmp.getUserData());
            sb.append(content);
        }
        return sb.toString();
        //Log.v(TAG,"content:"+sb.toString());
	}
	
}
