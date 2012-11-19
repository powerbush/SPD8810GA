package com.android.phone;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

public class IpDialCallDialog extends Activity {

	private static final String TAG = "IpDialCallDialog";

	public static final String DIAL_TYPE = "dial_type";

	private Dialog mIpDialingDialog = null;  
	private CharSequence mIpSelectedNumber = "";

	private String mNumber;
	private int mType;
	private Intent mIntent;

	private List<CharSequence> mSelector = new ArrayList<CharSequence>();

	private static final String EXCLUDE_PREFIX[] = new String[] { "+86" };

	private static final int PLACE_CALL = 101;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case PLACE_CALL:
				placeCall();
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate: this = " + this);
		IpDailingUtils ipUtils = new IpDailingUtils(
				this.getApplicationContext());

        mIntent = (Intent) getIntent().getParcelableExtra(
                OutgoingCallBroadcaster.EXTRA_NEW_CALL_INTENT);
        if (mIntent == null) {
            finish();
            return;
        }
		Intent intent = getIntent();

		mType = intent.getIntExtra(DIAL_TYPE, 0);
        mNumber = mIntent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

		// is ipdialing
		if (mType == 1) {
			String ipNumbers = ipUtils.getAllIpNumberString();
			if (!TextUtils.isEmpty(ipNumbers)) {

				String ipNumber[] = ipNumbers.split("\\|");

				List<CharSequence> items = new ArrayList<CharSequence>();

				mSelector.clear();
				items.add(getResources().getString(R.string.directly_call));
				mSelector.add("");
				for (String num : ipNumber) {
					if (!TextUtils.isEmpty(num) && TextUtils.isDigitsOnly(num)) {
						mSelector.add(num);
						items.add(getResources().getString(R.string.ip_call)
								+ " " + num);
					}
				}

				mIpDialingDialog = new AlertDialog.Builder(this)
						.setOnKeyListener(new DialogInterface.OnKeyListener() {
							public boolean onKey(DialogInterface dialog,
									int keyCode, KeyEvent event) {
								switch (keyCode) {
								case KeyEvent.KEYCODE_CALL:
								case KeyEvent.KEYCODE_SEARCH:
									return true;
								}
								return false;
							}
						})
						.setTitle(R.string.dialing_selector)
						.setSingleChoiceItems(
								items.toArray(new CharSequence[0]), 0,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										if (which > 0
												&& which < mSelector.size()) {
											mIpSelectedNumber = mSelector
													.get(which);
											for (String prefix : EXCLUDE_PREFIX) {
												if (mNumber.startsWith(prefix)) {
													mNumber = mNumber
															.substring(prefix
																	.length());
													break;
												}
											}

											if (mNumber
													.startsWith(mIpSelectedNumber
															.toString())) {
												mNumber = mNumber
														.substring(mIpSelectedNumber
																.length());
											}
											mNumber = mIpSelectedNumber
													+ mNumber;
										}
										dialog.dismiss();
										mHandler.sendEmptyMessage(PLACE_CALL);
									}
								})
						.setOnCancelListener(
								new DialogInterface.OnCancelListener() {
									public void onCancel(DialogInterface dialog) {
										finish();
									}
								}).create();
				mIpDialingDialog.show();

			}
		}

		Log.v(TAG, "onCreate: done");
	}

	@Override
	protected void onDestroy() {
		if (mIpDialingDialog != null) {
			mIpDialingDialog.dismiss();
			mIpDialingDialog = null;
		}
		super.onDestroy();
	}

    void placeCall() {
        if (mType == 1) {
            Log.v(TAG, "CALL to " + mNumber + " proceeding.");
            mIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, mNumber);
            Log.v(TAG, "doReceive(): calling startActivity: " + mIntent);
            startActivity(mIntent);
        }
        finish();
    }

}
