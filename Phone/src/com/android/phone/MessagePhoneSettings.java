/**
 * 
 */
package com.android.phone;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * @author jinwei.li
 *
 */
public class MessagePhoneSettings extends Activity implements OnClickListener {
	public static final String DEFAULT_MESSAGE_REPLY = "message_content";
	EditText mEditMessageContent;
	Button mOk;
	Button mCancel;
    private int mSubId = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mSubId  = getIntent().getIntExtra(CallSettingOptions.SUB_ID, 0);
		setContentView(R.layout.set_reply_message_reject);
		this.setTitle(this.getResources().getString(R.string.title_message_reply_for_hangup));
		
		mEditMessageContent = (EditText) this.findViewById(R.id.edit_content);
		mOk = (Button) this.findViewById(R.id.ok_for_save_message_content);
		mCancel = (Button) this.findViewById(R.id.cancel_for_exit_activity);
		
		SharedPreferences settings = getSharedPreferences(CallFeaturesSetting.SETTING_INFOS, 1);
		String message_content = settings.getString(DEFAULT_MESSAGE_REPLY + mSubId,
									this.getResources().getString(R.string.reply_message_default));
		mEditMessageContent.setText(message_content);
		
		mOk.setOnClickListener(this);
		mCancel.setOnClickListener(this);
		
	}

	public void onClick(View v) {
		switch(v.getId()){
		case R.id.ok_for_save_message_content:
			String save_message = mEditMessageContent.getText().toString();
			if(save_message == null || save_message.length() == 0){
				Toast.makeText(this, R.string.reply_message_cannot_be_empty, Toast.LENGTH_LONG)
					.show();
			}else {
				SharedPreferences settings = getSharedPreferences(CallFeaturesSetting.SETTING_INFOS, 1);
				settings.edit()
					.putString(DEFAULT_MESSAGE_REPLY + mSubId, save_message)
					.commit();
				finish();
			}
			break;
		case R.id.cancel_for_exit_activity:
			finish();
			break;
		}
		
	}

}
