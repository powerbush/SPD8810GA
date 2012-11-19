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

package com.android.music.tests;

/**
 * 
 * This class has the names of the all the activity name and variables 
 * in the instrumentation test.
 *
 */
public class MusicPlayerNames {
  
  //Expected result of the sorted playlistname
    public static final String expectedPlaylistTitle[] = { "**1E?:|}{[]~~.,;'",
        "//><..", "0123456789",
        "0random@112", "MyPlaylist", "UPPERLETTER",
        "combination011", "loooooooog",
        "normal", "~!@#$%^&*()_+"    
    }; 
  
  //Unsorted input playlist name
    public static final String unsortedPlaylistTitle[] = { "//><..","MyPlaylist",
        "0random@112", "UPPERLETTER","normal", 
        "combination011", "0123456789",
        "~!@#$%^&*()_+","**1E?:|}{[]~~.,;'",
        "loooooooog"    
    };
    
    public static final String DELETE_PLAYLIST_NAME = "testDeletPlaylist";
    public static final String ORIGINAL_PLAYLIST_NAME = "original_playlist_name";
    public static final String RENAMED_PLAYLIST_NAME = "rename_playlist_name";
    
    public static int NO_OF_PLAYLIST = 10;
    public static int WAIT_SHORT_TIME = 1000;
    public static int WAIT_LONG_TIME = 2000;
    public static int WAIT_VERY_LONG_TIME = 6000;
    public static int SKIP_WAIT_TIME = 500;
    public static int DEFAULT_PLAYLIST_LENGTH = 15;
    public static int NO_ALBUMS_TOBE_PLAYED = 50;
    public static int NO_SKIPPING_SONGS = 500;
    
    public static final String DELETESONG = "/sdcard/toBeDeleted.amr"; 
    public static final String GOLDENSONG = "/sdcard/media_api/music/AMRNB.amr";
    public static final String TOBEDELETESONGNAME = "toBeDeleted";   
    
    public static int EXPECTED_NO_RINGTONE = 1;
}
