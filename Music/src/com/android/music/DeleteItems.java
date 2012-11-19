/*
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

package com.android.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class DeleteItems extends Activity
{
    private TextView mPrompt;
    private Button mButton;
    private long [] mItemList;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.confirm_delete);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.WRAP_CONTENT);

        mPrompt = (TextView)findViewById(R.id.prompt);
        mButton = (Button) findViewById(R.id.delete);
        mButton.setOnClickListener(mButtonClicked);

        ((Button)findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Bundle b = getIntent().getExtras();
        String desc = b.getString("description");
        mItemList = b.getLongArray("items");
        
        mPrompt.setText(desc);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SHARED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);
    }
    
    private View.OnClickListener mButtonClicked = new View.OnClickListener() {
        public void onClick(View v) {
            // delete the selected item(s)
            MusicUtils.deleteTracks(DeleteItems.this, mItemList);
            finish();
        }
    };
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("DeleteItems", "action="+intent.getAction());
            if(intent.getAction().equals(Intent.ACTION_MEDIA_SHARED)){
                finish();
            }
        }
    };
    @Override
    public void onDestroy() {
        Log.d("DeleteItems", "onDestroy");
        unregisterReceiver(mScanListener);
        super.onDestroy();
    }
}
