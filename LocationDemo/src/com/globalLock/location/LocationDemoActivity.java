package com.globalLock.location;

import java.nio.charset.CharacterCodingException;
import java.util.Properties;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class LocationDemoActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Properties prop = new Properties();
		// temp
		// prop.setProperty("server", "58.251.70.201");
		prop.setProperty("server", "119.145.9.123");
		prop.setProperty("port", "2277");
		Client.setProperty(prop);
		device = new Device("340198005365045", 10, "18666210001", "18666212742");
		((Button) findViewById(R.id.button1))
				.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						String s = ((TextView) findViewById(R.id.editText1))
								.getText().toString();
						if (s.split(",").length == 2) {
							try {
								String desc = device.QueryLocation(
										Double.parseDouble(s.split(",")[0]),
										Double.parseDouble(s.split(",")[1]));
								((TextView) findViewById(R.id.editText3))
										.setText(desc);
							} catch (NumberFormatException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (CharacterCodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}
					}
				});
		((Button) findViewById(R.id.button2))
				.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						String s = ((TextView) findViewById(R.id.editText2))
								.getText().toString();
						if (s.split(",").length == 4) {
							try {
								String desc = device.QueryLocation(
										Long.parseLong(s.split(",")[0]),
										Long.parseLong(s.split(",")[1]),
										Short.parseShort(s.split(",")[2]),
										Short.parseShort(s.split(",")[3]));
								((TextView) findViewById(R.id.editText3))
										.setText(desc);
							} catch (NumberFormatException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (CharacterCodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}
					}
				});

	}

	Device device = null;

}