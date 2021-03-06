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
package com.android.music.tests.stress;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;
import android.util.Log;
import android.content.Context;


import com.android.music.MusicBrowserActivity;
import com.android.music.MusicUtils;
import com.android.music.TrackBrowserActivity;
import com.android.music.tests.MusicPlayerNames;

public class MusicPlaybackStress extends ActivityInstrumentationTestCase <TrackBrowserActivity>{
    private static String TAG = "mediaplayertests";
  
    public MusicPlaybackStress() {
      super("com.android.music",TrackBrowserActivity.class);
    }
  
    @Override 
    protected void setUp() throws Exception { 
      super.setUp(); 
    }
  
    @Override 
    protected void tearDown() throws Exception {   
      super.tearDown();           
    }

    @LargeTest
    public void testPlayAllSongs() {
      Activity mediaPlaybackActivity;
      try{
        Instrumentation inst = getInstrumentation();
        ActivityMonitor mediaPlaybackMon = inst.addMonitor("com.android.music.MediaPlaybackActivity", 
          null, false);
        inst.invokeMenuActionSync(getActivity(), MusicUtils.Defs.CHILD_MENU_BASE + 3, 0);
        Thread.sleep(MusicPlayerNames.WAIT_LONG_TIME);
        mediaPlaybackActivity = mediaPlaybackMon.waitForActivityWithTimeout(2000);
        for (int i=0;i< MusicPlayerNames.NO_SKIPPING_SONGS;i++){               
          Thread.sleep(MusicPlayerNames.SKIP_WAIT_TIME);
          if (i==0){
            //Set the repeat all
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
     
            //Set focus on the next button
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
          }
          inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);      
        }   
        mediaPlaybackActivity.finish();
      }catch (Exception e){
        Log.e(TAG, e.toString());
      }
      //Verification: check if it is in low memory
      ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
      ((ActivityManager)getActivity().getSystemService("activity")).getMemoryInfo(mi);
      assertFalse(TAG, mi.lowMemory);      
    }
}
