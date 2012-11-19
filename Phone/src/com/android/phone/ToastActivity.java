package com.android.phone;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class ToastActivity extends Activity {

    private static final int MSG_DELAY_CLOSE = 1;
    private static final long CLOSE_DELAY = 15000;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		

        LayoutInflater inflate = (LayoutInflater)
                getWindow().getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(com.android.internal.R.layout.transient_notification, null);
        TextView tv = (TextView)v.findViewById(com.android.internal.R.id.message);
        tv.setText(R.string.prompt_wait_voicecall);
        setContentView(v);
        
        Handler hdl = new Handler(Looper.myLooper()){        	
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == MSG_DELAY_CLOSE){
					finish();
				}
			}        	
        };
        
        hdl.sendEmptyMessageDelayed(MSG_DELAY_CLOSE, CLOSE_DELAY);
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		finish();
	}


}
