/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.app.StatusBarManager;

public class DeleteItems extends Activity
{
    private TextView mPrompt;
    private Button mButton;
    private long [] mItemList;

    private static final int PROGRESS_DIALOG_KEY = 0;

    // Status of deleting
    private final static int START_DELETING = 0;
    private final static int FINISH = 1;

    private Handler mHandler = new Handler() {
    @Override
		public void handleMessage(Message msg) {
	    	if (msg.what == START_DELETING) {
				// Do the time-consuming job in its own thread to avoid blocking anyone
				new Thread(new Runnable() {
		    		public void run() {
						doDeleteItems();
		    		}
				}).start();
	    	} else if (msg.what == FINISH) {
				dismissDialog(PROGRESS_DIALOG_KEY);

				// Notify user the deletion is done
				String message = getResources().getQuantityString(
					R.plurals.NNNtracksdeleted, mItemList.length, Integer.valueOf(mItemList.length));
				Toast.makeText(DeleteItems.this, message, Toast.LENGTH_SHORT).show();

				finish();
            }
        }
    };


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        setTitle(R.string.delete_item);
        setContentView(R.layout.confirm_delete);
        window.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                R.drawable.ic_dialog_alert);

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
    }
    
    private View.OnClickListener mButtonClicked = new View.OnClickListener() {
        public void onClick(View v) {
	    	showDialog(PROGRESS_DIALOG_KEY);
	    	Message msg = mHandler.obtainMessage(START_DELETING);
	    	mHandler.sendMessage(msg);
		}
    };
    

    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id) {
    	case PROGRESS_DIALOG_KEY:
	    	ProgressDialog dialog = new ProgressDialog(this);
	    	dialog.setTitle(R.string.delete_progress_title);
	    	dialog.setMessage(getResources().getString(R.string.delete_progress_message)); 
	    	dialog.setIndeterminate(true);
	    	dialog.setCancelable(false);
	    	return dialog;
    	default:
	    	return null;
    	}
    }

    private void doDeleteItems() {
		// Do the deletion
            MusicUtils.deleteTracks(DeleteItems.this, mItemList);

		// Tell them we are done
		Message msg = mHandler.obtainMessage(FINISH);
		mHandler.sendMessage(msg);
    }

	@Override
	public void onPause() {
        try {
            StatusBarManager statusBar = (StatusBarManager) 
                    getSystemService(Context.STATUS_BAR_SERVICE);
            statusBar.disable(StatusBarManager.DISABLE_NONE);
        } catch (Exception e) {
            // Just in case
        }
		super.onPause();
	}

	@Override
	public void onResume() {
        try {
            StatusBarManager statusBar = (StatusBarManager) 
                    getSystemService(Context.STATUS_BAR_SERVICE);
            statusBar.disable(StatusBarManager.DISABLE_EXPAND);
        } catch (Exception e) {
            // Just in case
        }
		super.onResume();
        }
}
