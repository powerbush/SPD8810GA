/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.android.music.tests.functional;

import android.app.Activity;
import android.content.*;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.database.Cursor;

import com.android.music.CreatePlaylist;
import com.android.music.MusicUtils;
import com.android.music.PlaylistBrowserActivity;
import com.android.music.TrackBrowserActivity;

import com.android.music.tests.MusicPlayerNames;
import com.android.music.tests.functional.TestSongs;

/**
 * Junit / Instrumentation test case for the PlaylistBrowserActivity
 * This test case need to run in the landscape mode and opened keyboard
 
 */
public class TestPlaylist extends ActivityInstrumentationTestCase <PlaylistBrowserActivity>{
    private static String TAG = "musicplayertests";
  
    public TestPlaylist() {
        super("com.android.music",PlaylistBrowserActivity.class);
    }

    @Override 
    protected void setUp() throws Exception {   
        super.setUp(); 
    }
    
    @Override 
    protected void tearDown() throws Exception {   
        super.tearDown();           
    }
    
    
    private void clearSearchString(int length){
        Instrumentation inst = getInstrumentation();
        for (int j=0; j< length; j++)
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);
    }
    /**
     * Remove playlist
     */
    public void deletePlaylist(String playlistname) throws Exception{
        Instrumentation inst = getInstrumentation();
        inst.sendStringSync(playlistname);
        Thread.sleep(MusicPlayerNames.WAIT_SHORT_TIME);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);       
        inst.invokeContextMenuAction(getActivity(), MusicUtils.Defs.CHILD_MENU_BASE + 1, 0);
        Thread.sleep(MusicPlayerNames.WAIT_SHORT_TIME);
        clearSearchString(playlistname.length());
        
    }

    /**
     *  Start the trackBrowserActivity and add the new playlist
     */
    public void addNewPlaylist(String playListName) throws Exception{
        Instrumentation inst = getInstrumentation();
        Activity trackBrowserActivity;
        ActivityMonitor trackBrowserMon = inst.addMonitor("com.android.music.TrackBrowserActivity", 
                null, false);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        intent.setClassName("com.android.music", "com.android.music.TrackBrowserActivity");
        getActivity().startActivity(intent);     
        Thread.sleep(MusicPlayerNames.WAIT_LONG_TIME);
        trackBrowserActivity = trackBrowserMon.waitForActivityWithTimeout(2000);
        inst.invokeContextMenuAction(trackBrowserActivity, MusicUtils.Defs.NEW_PLAYLIST, 0);
        Thread.sleep(MusicPlayerNames.WAIT_SHORT_TIME);
        //Remove the default playlist name
        clearSearchString(MusicPlayerNames.DEFAULT_PLAYLIST_LENGTH);
        inst.sendStringSync(playListName);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(MusicPlayerNames.WAIT_LONG_TIME);
        trackBrowserActivity.finish();
        clearSearchString(playListName.length());

    }
    
    /**
     * Rename playlist
     */
    public void renamePlaylist(String oldPlaylistName, String newPlaylistName) throws Exception{
        Instrumentation inst = getInstrumentation();
        inst.sendStringSync(oldPlaylistName);
        Thread.sleep(MusicPlayerNames.WAIT_SHORT_TIME);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);       
        inst.invokeContextMenuAction(getActivity(), MusicUtils.Defs.CHILD_MENU_BASE + 3, 0);
        Thread.sleep(MusicPlayerNames.WAIT_SHORT_TIME);
        //Remove the old playlist name
        clearSearchString(oldPlaylistName.length());
        inst.sendStringSync(newPlaylistName);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(MusicPlayerNames.WAIT_LONG_TIME);
        clearSearchString(oldPlaylistName.length());
    }

    public boolean verifyPlaylist(String playlistname) throws Exception{
        Cursor mCursor;
        boolean isEmptyPlaylist = true;
        String[] cols = new String[] {
                MediaStore.Audio.Playlists.NAME
        };
        ContentResolver resolver = getActivity().getContentResolver();
        if (resolver == null) {
            System.out.println("resolver = null");
            assertNull(TAG, resolver);
        } else {
            String whereclause = MediaStore.Audio.Playlists.NAME + " = '" + playlistname +"'";
            mCursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    cols, whereclause, null,
                    MediaStore.Audio.Playlists.NAME);
            isEmptyPlaylist = mCursor.moveToFirst();
        }
        return isEmptyPlaylist;
    }

    /**
     * Test case 1: Add a playlist and delet the playlist just added.
     * Verification: The mediastore playlist should be empty
     */
    @LargeTest
    public void testDeletePlaylist() throws Exception{
        boolean isEmptyPlaylist = true;
        addNewPlaylist(MusicPlayerNames.DELETE_PLAYLIST_NAME);
        deletePlaylist(MusicPlayerNames.DELETE_PLAYLIST_NAME);  
        isEmptyPlaylist = verifyPlaylist(MusicPlayerNames.DELETE_PLAYLIST_NAME);
        assertFalse("testDeletePlaylist", isEmptyPlaylist);
    }
    
    /**
     * Test case 2: Add playlist and rename the playlist just added.
     * Verification: The mediastore playlist should contain the updated name.
     */
    @LargeTest
    public void testRenamePlaylist() throws Exception{
        boolean isEmptyPlaylist = true;
        addNewPlaylist(MusicPlayerNames.ORIGINAL_PLAYLIST_NAME);
        renamePlaylist(MusicPlayerNames.ORIGINAL_PLAYLIST_NAME, MusicPlayerNames.RENAMED_PLAYLIST_NAME);  
        isEmptyPlaylist = verifyPlaylist(MusicPlayerNames.RENAMED_PLAYLIST_NAME);
        deletePlaylist(MusicPlayerNames.RENAMED_PLAYLIST_NAME);
        assertTrue("testDeletePlaylist", isEmptyPlaylist);
    }

}    
