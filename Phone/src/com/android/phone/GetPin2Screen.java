/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pin2 entry screen.
 */
public class GetPin2Screen extends Activity {
    private static final String LOG_TAG = PhoneApp.LOG_TAG;

    private EditText mPin2Field;
    private Button mButton;
    private TextView mTextView;
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.get_pin2_screen);
        setupView();
    }

    /**
     * Reflect the changes in the layout that force the user to open
     * the keyboard. 
     */
    private void setupView() {
        Intent intent = getIntent();
	
	  int times = intent.getIntExtra("times", 0);
	  
	  if(times !=0){

             mTextView = (TextView)findViewById(R.id.prompt);

             CharSequence str =  mTextView.getText();

		CharSequence nstr =  getString(R.string.promptWordOne) + times +  getString(R.string.promptWordTwo);

	      mTextView.setText(nstr);
             		
	  }
	  
        mPin2Field = (EditText) findViewById(R.id.pin);
        if (mPin2Field != null) {
            mPin2Field.setKeyListener(DigitsKeyListener.getInstance());
            mPin2Field.setOnClickListener(mClicked);
        }

        mButton = (Button) findViewById(R.id.button);
        if (mButton != null) {
            mButton.setOnClickListener(mClicked);
        }

    }

    private String getPin2() {
        return mPin2Field.getText().toString();
    }

    private void returnResult() {
        Bundle map = new Bundle();
        map.putString("pin2", getPin2());

        Intent intent = getIntent();
        Uri uri = intent.getData();

        Intent action = new Intent();
        if (uri != null) action.setAction(uri.toString());
        setResult(RESULT_OK, action.putExtras(map));
        finish();
    }

	private boolean validatePin(String pin, boolean isPUK) {

		// for pin, we have 4-8 numbers, or puk, we use only 8.
		int pinMinimum = isPUK ? MAX_PIN_LENGTH : MIN_PIN_LENGTH;

		// check validity
		if (pin == null || pin.length() < pinMinimum
				|| pin.length() > MAX_PIN_LENGTH) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Display a toast for message, like the rest of the settings.
	 */
	private final void displayMessage(int strId) {
		Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
	}

	/**
	 * check whether pin2 is avaliable
	 */
	private void checkPin2(String pin2) {
		Log.i("FDN", "checkPin2");
		if (validatePin(pin2, false)) {
			returnResult();
		} else {
			// throw up error if the pin is invalid.
			displayMessage(R.string.invalidPin2);
		}

	}

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (TextUtils.isEmpty(mPin2Field.getText())) {
                return;
            }
			if (v == mButton || v == mPin2Field) {
				// returnResult();
				checkPin2(mPin2Field.getText().toString());
			}
		}
	};

    private void log(String msg) {
        Log.d(LOG_TAG, "[GetPin2] " + msg);
    }
}
