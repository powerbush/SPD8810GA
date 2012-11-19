/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.ui;


import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_ABORT;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_COMPLETE;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_START;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_STATUS_ACTION;
import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MMS_LOCKED;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;
import static com.android.mms.ui.MessageListAdapter.PROJECTION;

import java.util.ArrayList;
import java.lang.Integer;
import com.android.mms.R;
import android.database.sqlite.SqliteWrapper;
import com.android.mms.transaction.MessagingNotification;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;





/**
 * Displays a list of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsItemActivity extends Activity {
	
	private static final int MENU_DELETE = 0;
	private TextView mMessage;
    private static final String TAG = "CellBroadcastSmsItemActivity";
    private String mPos =""; 
	@Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.cell_broadcast_message);
        TextView textView = (TextView)findViewById(R.id.cb_message_text);
        Bundle bundle = getIntent().getExtras();
        String s ="";
      
        s+=getString(R.string.from_label)+": "+bundle.getString("address")+"\n"+"\n";
        s+=getString(R.string.broadcast_message)+"(" + getLangName(bundle.getInt("langId",0))+" "+getString(R.string.language_type) +")"+":\n\n "+bundle.getString("body");
        //s+="date"+": "+bundle.getString("date")+"\n"+"\n";
        mPos += bundle.getString("pos");
        Log.i(TAG,"mPos"+ mPos);
        textView.setText(s);
    }
    

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        
    }
    OnClickListener positiveListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
        	Log.i(TAG,"mPos"+ mPos);
        	getIntent().putExtra("pos",mPos);
    		setResult(20,getIntent());
    		CellBroadcastSmsItemActivity.this.finish();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        super.onCreateOptionsMenu(menu);
        
        Log.i(TAG,"onCreateOptionsMenu");
        menu.add(0,MENU_DELETE,Menu.NONE,getString(R.string.menu_delete)).setIcon(android.R.drawable.ic_menu_delete);

       
        return true;
    }

 
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
    	
    	Log.i(TAG,"onOptionsItemSelectedSIM MenuItem");	
    	
    	
        switch (item.getItemId()) {

      
     
        case MENU_DELETE: {
        
        	Log.i(TAG,"onOptionsItemSelectedSIM MENU_DELETE");    
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    		builder.setTitle(R.string.confirm_dialog_title);
    		builder.setIcon(android.R.drawable.ic_dialog_alert);
    		builder.setCancelable(true);
    		builder.setMessage(R.string.confirm_delete_message);
    		builder.setPositiveButton(R.string.delete, positiveListener);
    		builder.setNegativeButton(R.string.no, null);
    		builder.show();
        	
            
            return true;
        }
    
        
        
        default:
        	break;
    
  
    }
    return false;
    	
    }

    //refrence iso639
    private  String getLangName(int langId){
    	
    	String langname ;
    	
    	switch(langId){
    	    case 0x7a68:
			   langname = "Chinese";
			break;
    		case 0x6465:
    			langname = "German";
    			break;
    		case 0x656e:
    			langname = "English";
    			break;
    		case 0x6974:
    			langname = "Italia";
    			break;
    		case 0x6672:
    			langname = "French";
    			break;
    		case 0x6573:
    			langname = "Spanish";
    			break;
    		case 0x6e6c:
    			langname = "Dutch";
    			break;
    		case 0x7376:
    			langname = "Swedish";
    			break;
    		case 0x6461:
    			langname = "Danish";
    			break;
    		case 0x7074:
    			langname = "Portuguese";
    			break;
    		case 0x6669:
    			langname = "Finnish";
    			break;
    		case 0x656c:
    			langname = "Greek";
    			break;
    		case 0x6e6f:
    			langname = "Norwegian";
    			break;
    		case 0x746b:
    			langname = "Turkish";
    			break;
    		case 0x6875:
    			langname = "Hungarian";
    			break;
    		case 0x706c:
    			langname = "Polish";
    			break;
    		case 0x6373:
    			langname = "Czech";
    			break;
    		case 0x6865:
    			langname = "Herbrew";
    			break;
    		case 0x6172:
    			langname = "Arabic";
    			break;
    		case 0x7275:
    			langname = "Russian";
    			break;
    		case 0x6973:
    			langname = "Icelanic";
    			break;
    		default:
    			
    			StringBuilder name = new StringBuilder(langId);
    			langname = name.toString();
    			break;
    		
    	}
    	
    	Log.i(TAG,"getLangName : " + langname);
    	return langname;
    	
    	
    	
    	
    	
    	
    }
}
