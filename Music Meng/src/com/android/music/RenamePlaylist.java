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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class RenamePlaylist extends Activity
{
    private EditText mPlaylist;
    private TextView mPrompt;
    private Button mSaveButton;
    private long mRenameId;
    private long mExistingId;
    private String mOriginalName;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.create_playlist);
        //getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
        //                            WindowManager.LayoutParams.WRAP_CONTENT);

        mPrompt = (TextView)findViewById(R.id.prompt);
        mPlaylist = (EditText)findViewById(R.id.playlist);
        mSaveButton = (Button) findViewById(R.id.create);
        mSaveButton.setOnClickListener(mOpenClicked);

        ((Button)findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        mRenameId = icicle != null ? icicle.getLong("rename")
                : getIntent().getLongExtra("rename", -1);
        mExistingId = icicle != null ? icicle.getLong("existing", -1)
                : getIntent().getLongExtra("existing", -1);
        mOriginalName = nameForId(mRenameId);
        String defaultname = icicle != null ? icicle.getString("defaultname") : mOriginalName;
        
        if (mRenameId < 0 || mOriginalName == null || defaultname == null) {
            Log.i("@@@@", "Rename failed: " + mRenameId + "/" + defaultname);
            finish();
            return;
        }
        
        String promptformat;
        if (mOriginalName.equals(defaultname)) {
            promptformat = getString(R.string.rename_playlist_same_prompt);
        } else {
            promptformat = getString(R.string.rename_playlist_diff_prompt);
        }
                
        String prompt = String.format(promptformat, mOriginalName, defaultname);
        mPrompt.setText(prompt);
        mPlaylist.setText(defaultname);
        mPlaylist.setSelection(defaultname.length());
        mPlaylist.addTextChangedListener(mTextWatcher);
        setSaveButton();
    }
    
    TextWatcher mTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // don't care about this one
        }
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // check if playlist with current name exists already, and warn the user if so.
            setSaveButton();
        };
        public void afterTextChanged(Editable s) {
            // don't care about this one
        }
    };
    
    private void setSaveButton() {
        String typedname = mPlaylist.getText().toString();
        if (typedname.trim().length() == 0) {
            mSaveButton.setEnabled(false);
        } else {
            mSaveButton.setEnabled(true);
        		final long id = idForplaylist(typedname);
        		if (id >= 0 && !mOriginalName.equals(typedname)) {
                mSaveButton.setText(R.string.create_playlist_overwrite_text);
		            mExistingId = id;
            } else {
                mSaveButton.setText(R.string.create_playlist_create_text);
            		mExistingId = -1;
            }
        }
    }
    
    private int idForplaylist(String name) {
        Cursor c = MusicUtils.query(this, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Playlists._ID },
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[] { name },
                MediaStore.Audio.Playlists.NAME);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                id = c.getInt(0);
            }
        }
        c.close();
        return id;
    }
    
    private String nameForId(long id) {
        Cursor c = MusicUtils.query(this, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Playlists.NAME },
                MediaStore.Audio.Playlists._ID + "=?",
                new String[] { Long.valueOf(id).toString() },
                MediaStore.Audio.Playlists.NAME);
        String name = null;
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                name = c.getString(0);
            }
        }
        c.close();
        return name;
    }
    
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
        outcicle.putLong("rename", mRenameId);
        outcicle.putLong("existing", mExistingId);
    }
    
    @Override
    public void onResume() {
        super.onResume();
    }

    private View.OnClickListener mOpenClicked = new View.OnClickListener() {
        public void onClick(View v) {
            String name = mPlaylist.getText().toString();
            if (name != null && name.length() > 0) {
                ContentResolver resolver = getContentResolver();
                if (mExistingId >= 0) {
                    // There is another playlist which has the same name with renamed one
                    // we should overwrite existing one, i.e. delete it from database
                    resolver.delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            MediaStore.Audio.Playlists._ID + "=?",
                            new String[] {Long.valueOf(mExistingId).toString()});
                    Log.d("RenamePlaylist", "to overwrite, delete the existing one");
                }
                ContentValues values = new ContentValues(1);
                values.put(MediaStore.Audio.Playlists.NAME, name);
                resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Playlists._ID + "=?",
                        new String[] { Long.valueOf(mRenameId).toString()});
                
                setResult(RESULT_OK);
                Toast.makeText(RenamePlaylist.this, R.string.playlist_renamed_message, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}
