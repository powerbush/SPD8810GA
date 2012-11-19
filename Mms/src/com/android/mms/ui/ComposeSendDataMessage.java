package com.android.mms.ui;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.mms.R;

public class ComposeSendDataMessage extends Activity implements OnClickListener{
	private static final String TAG = "ComposeSendDataMessage";
	public static final String DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED";
	
	private Button mSendButton;
	private EditText mRecipient;
	private EditText mBody;
	private String mVcardString;
	
	//Contact
	private static final Pattern patternVCard = Pattern.compile(
    			"(?ms)^BEGIN:VCARD$.+?^END:VCARD$");
	
	//full name
	private static final Pattern patternFullName = Pattern.compile(
    			"(?m)^FN:([^;\\r\\n]+)*.*$");
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.send_data_message);
		initResourceRefs();
		if(getIntent() == null || getIntent().getExtras() == null){
			finish();
		}else{
			Uri vCardPath = (Uri)getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
			Log.v(TAG, "vCardPath = " + vCardPath);
			if(vCardPath == null){
				finish();
			}
			byte[] buffer = null;
			try {
				AssetFileDescriptor fileDescriptor = this.getContentResolver().openAssetFileDescriptor(vCardPath, "r");
        		FileInputStream inputStream = fileDescriptor.createInputStream();
        		buffer = new byte[(int) fileDescriptor.getDeclaredLength()];
        		inputStream.read(buffer);
        		inputStream.close();
        		fileDescriptor.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			mVcardString = new String(buffer);
			
			if(mVcardString == null){
				finish();
			}else {
				Log.v(TAG, "pass vCard is " + mVcardString + "\n");
				Matcher matcher ;
				Matcher matcherVCard = patternVCard.matcher(mVcardString);
				if(matcherVCard.find()){
					matcher = patternFullName.matcher(mVcardString);
					if(matcher.find()){
						String fullname = matcher.group(1);
						if(fullname != null){
							mBody.setText(getString(R.string.prefix_send) + " " + fullname + getString(R.string.suffix_vcf));
							mBody.setEnabled(false);
						}
					}
				}
			}
    		
			mSendButton.setOnClickListener(this);
		}
		
	}
	
    /**
     * Initialize all UI elements from resources.
     */
    private void initResourceRefs() {
    	mRecipient = (EditText) findViewById(R.id.recipients_editor2);
    	mBody = (EditText) findViewById(R.id.embedded_text_editor2);
    	mSendButton = (Button) findViewById(R.id.send_button2);
    }

	public void onClick(View v) {
		if ((v == mSendButton)) {
			String recipient = null;
			Log.v(TAG, "mRecipient.getText" + mRecipient.getText());
			if(mRecipient.getText() != null){
				recipient = mRecipient.getText().toString();
			}else{
				Toast.makeText(this, "Have no recipient", Toast.LENGTH_SHORT).show();
				return;
			}
			
			if(recipient == null || recipient.length() != 11){
				Toast.makeText(this, "Invalid recipient", Toast.LENGTH_SHORT).show();
				return;
			}
			SmsManager smsManager = SmsManager.getDefault();//"13548629593",18621616286 ,13564061251
    		
			try {
				smsManager.sendDataMessage(recipient, null, (short) 0x23F4, mVcardString.getBytes(), null, null);
				finish();
			} catch (Exception e) {
//				Toast.makeText(this, "Please check your ricipient or net service", Toast.LENGTH_SHORT).show();
				/*fixed CR<NEWMS00139577> by luning at 2011.11.12*/
				Toast.makeText(this, "Please check your recipient or net service", Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
        }
	}
}
