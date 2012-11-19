package com.android.phone;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class IpDailingNumberList extends Activity implements OnClickListener {
	private static final String TAG = "IpDailingNumberList";

	private EditText mOneEditText;

	private EditText mTwoEditText;

	private EditText mThreeEditText;

	private EditText mFourEditText;

	private EditText mFiveEditText;

	private Button mSaveButton;

	private IpDailingUtils mIpDailingUtils;

	private static List<EditText> mEditTextList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ip_dailing_number_list);
		mIpDailingUtils = new IpDailingUtils(this.getApplicationContext());
		mEditTextList = new ArrayList<EditText>();
		mOneEditText = (EditText) findViewById(R.id.ip_dailing_one_edit);
		mTwoEditText = (EditText) findViewById(R.id.ip_dailing_two_edit);
		mThreeEditText = (EditText) findViewById(R.id.ip_dailing_three_edit);
		mFourEditText = (EditText) findViewById(R.id.ip_dailing_four_edit);
		mFiveEditText = (EditText) findViewById(R.id.ip_dailing_five_edit);
		mEditTextList.add(mOneEditText);
		mEditTextList.add(mTwoEditText);
		mEditTextList.add(mThreeEditText);
		mEditTextList.add(mFourEditText);
		mEditTextList.add(mFiveEditText);
		mSaveButton = (Button) findViewById(R.id.save_button);
		mSaveButton.setOnClickListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		for (int i = 0; i < mEditTextList.size(); i++) {
			Log.d(TAG,
					"Get ipnumber.Number" + i + " is : "
							+ mIpDailingUtils.getIpNumber(i));
			mEditTextList.get(i).setText(mIpDailingUtils.getIpNumber(i));
			mEditTextList.get(i).setSelection(mIpDailingUtils.getIpNumber(i).length());
		}
	}

	@Override
	public void onClick(View v) {
		saveIpnumber();
		finish();
	}

	private void saveIpnumber() {
		Log.d(TAG, "saveIpnumber()...");
		List<String> numbers = new ArrayList<String>();
		Editable edit = null;
		String number = null;
		for (int i = 0; i < mEditTextList.size(); i++) {
			edit = mEditTextList.get(i).getText();
			if (edit != null) {
				number = edit.toString();
			} else {
				number = "";
			}
			numbers.add(number);
		}

		for (int j = 0; j < numbers.size(); j++) {
			Log.d(TAG, "Save ipnumber.Number" + j + " is : " + numbers.get(j));
			mIpDailingUtils.setIpNumber(numbers.get(j), j);
		}
	}
}
