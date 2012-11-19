package com.android.contacts;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class TestSim extends Activity{

	private static final String TAG = "TestSim";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Uri simUri = Uri.parse("content://icc/adn");
		String displayName = "a";
		String homeNumber = "1";
		String anr = "1;2;3";
		String email = "a";
		String select = "tag='" + displayName + "' AND number='" + homeNumber + "' AND anr='" +anr + "' AND email='" + email+"'";
		
		for(int i = 0; i < 50; i++){
			Log.v(TAG, "the " + (i + 1));
			getContentResolver().delete(simUri, select, null);
		}
		
	}

}
