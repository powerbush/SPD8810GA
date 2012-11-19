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
 * Copyright (C) 2007 The Android Open Source Project
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

// mtk80658: add for Music lyrics supporting
import java.util.Scanner;
import java.util.regex.Pattern;
// mtk80658

import com.android.music.R;
import com.android.music.MusicUtils.ServiceToken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.audiofx.AudioEffect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import android.app.StatusBarManager;

import com.mediatek.featureoption.FeatureOption;

import android.drm.*;

public class MediaPlaybackActivity extends Activity implements MusicUtils.Defs,
    View.OnTouchListener, View.OnLongClickListener
{
    private static final String TAG = "MediaPlaybackActivity";
    private static final int USE_AS_RINGTONE = CHILD_MENU_BASE;

    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private Worker mAlbumArtWorker;
    private AlbumArtHandler mAlbumArtHandler;
    private Toast mToast;
    private int mTouchSlop;
    private SubMenu mAddToPlaylistSubmenu;
    private boolean isOFNDown = false;
    /**
     * Some music's durations can only be obtained when playing the media.
     * As a result we must know whether to update the durations.
     */
    private boolean mNeedUpdateDuration = true;
	private ServiceToken mToken;

	// mtk80658: add for Music lyrics supporting
    private ScrollLrcView mScrollLrcView = null;
    private boolean mLrcReady = false;
    private boolean mScrollLrcReady = false;
    private int mLrcMode = ScrollLrcView.LRC_MODE_SINGLE;
    // mtk80658

    public MediaPlaybackActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mAlbumArtWorker = new Worker("album art worker");
        mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // mtk80658: modified for Music lyrics supporting
        // load the layout integrated with Lyrics module when MTK_MUSIC_LRC_SUPPORT enabled
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            setContentView(R.layout.audio_player_with_lrc);
        } else {
            setContentView(R.layout.audio_player); // default layout without lyrics module
        }
        // mtk80658

        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mAlbum = (ImageView) findViewById(R.id.album);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mAlbumName = (TextView) findViewById(R.id.albumname);
        mTrackName = (TextView) findViewById(R.id.trackname);

        View v = (View)mArtistName.getParent(); 
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mAlbumName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mTrackName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        
        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        // mtk80658: add for Music lyrics supporting
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            // initialize lyrics view
            mLrcMode = MusicUtils.getIntPref(this, "lyricMode", ScrollLrcView.LRC_MODE_SINGLE);
            mScrollLrcView = (ScrollLrcView) findViewById(R.id.lyrics_view);

            // disable the touch event (that the scroll view can't be scroll by finger move)
            mScrollLrcView.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            // the imageview that is clickable. for swithing between single-line / multiple-line mode
            ((ImageView) findViewById(R.id.lyrics_shift)).setOnClickListener (
                new View.OnClickListener () {
                	public void onClick(View v) {
            			if (mLrcMode == ScrollLrcView.LRC_MODE_MULTIPLE) {
            			    mLrcMode = ScrollLrcView.LRC_MODE_SINGLE;
            			} else if (mLrcMode == ScrollLrcView.LRC_MODE_SINGLE) {
            				mLrcMode = ScrollLrcView.LRC_MODE_MULTIPLE;
            			}
            			// alps00061113, the preference should be saved immediately here,
            			// otherwise it will return in the "try" block below
            			MusicUtils.setIntPref(MediaPlaybackActivity.this, "lyricMode", mLrcMode);

                        updateLyricsFrame();
                        try {
                            if (!mScrollLrcView.reSetupLyrics(
                                    MediaPlaybackActivity.this, mLrcMode, (int)mService.position())) {
                                return;
                            }
                        } catch (RemoteException ex) {
                            throw new LrcScrollException();
                        }

            			mHandler.sendEmptyMessage(UPDATE_LRC);
                	}
            	}
            );
        } // if (FeatureOption.MTK_MUSIC_LRC_SUPPORT)
        // mtk80658

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);
        
        mQueueButton = (ImageButton) findViewById(R.id.curplaylist);
        mQueueButton.setOnClickListener(mQueueListener);
        mShuffleButton = ((ImageButton) findViewById(R.id.shuffle));
        mShuffleButton.setOnClickListener(mShuffleListener);
        mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
        mRepeatButton.setOnClickListener(mRepeatListener);
        
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }
    
    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;
    
    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.albumname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        TextView tv = textViewForContainer(v);
        if (tv == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            v.setBackgroundColor(0xff606060);
            mInitialX = mLastX = (int) event.getX();
            mDraggingLabel = false;
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            v.setBackgroundColor(0);
            if (mDraggingLabel) {
                Message msg = mLabelScroller.obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(msg, 1000);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDraggingLabel) {
                int scrollx = tv.getScrollX();
                int x = (int) event.getX();
                int delta = mLastX - x;
                if (delta != 0) {
                    mLastX = x;
                    scrollx += delta;
                    if (scrollx > mTextWidth) {
                        // scrolled the text completely off the view to the left
                        scrollx -= mTextWidth;
                        scrollx -= mViewWidth;
                    }
                    if (scrollx < -mViewWidth) {
                        // scrolled the text completely off the view to the right
                        scrollx += mViewWidth;
                        scrollx += mTextWidth;
                    }
                    tv.scrollTo(scrollx, 0);
                }
                return true;
            }
            int delta = mInitialX - (int) event.getX();
            if (Math.abs(delta) > mTouchSlop) {
                // start moving
                mLabelScroller.removeMessages(0, tv);
                
                // Only turn ellipsizing off when it's not already off, because it
                // causes the scroll position to be reset to 0.
                if (tv.getEllipsize() != null) {
                    tv.setEllipsize(null);
                }
                Layout ll = tv.getLayout();
                // layout might be null if the text just changed, or ellipsizing
                // was just turned off
                if (ll == null) {
                    return false;
                }
                // get the non-ellipsized line width, to determine whether scrolling
                // should even be allowed
                mTextWidth = (int) tv.getLayout().getLineWidth(0);
                mViewWidth = tv.getWidth();
                if (mViewWidth > mTextWidth) {
                    tv.setEllipsize(TruncateAt.END);
                    v.cancelLongPress();
                    return false;
                }
                mDraggingLabel = true;
                tv.setHorizontalFadingEdgeEnabled(true);
                v.cancelLongPress();
                return true;
            }
        }
        return false; 
    }

    Handler mLabelScroller = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextView tv = (TextView) msg.obj;
            int x = tv.getScrollX();
            x = x * 3 / 4;
            tv.scrollTo(x, 0);
            if (x == 0) {
                tv.setEllipsize(TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };
    
    public boolean onLongClick(View view) {

        CharSequence title = null;
        String mime = null;
        String query = null;
        String artist;
        String album;
        String song;
        long audioid;
        
        try {
            artist = mService.getArtistName();
            album = mService.getAlbumName();
            song = mService.getTrackName();
            audioid = mService.getAudioId();
        } catch (RemoteException ex) {
            return true;
        } catch (NullPointerException ex) {
            // we might not actually have the service yet
            return true;
        }

        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false;
        }

        if (audioid < 0) {
            return false;
        }

        Cursor c = MusicUtils.query(this,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioid),
                new String[] {MediaStore.Audio.Media.IS_MUSIC}, null, null, null);
        boolean ismusic = true;
        if (c != null) {
            if (c.moveToFirst()) {
                ismusic = c.getInt(0) != 0;
            }
            c.close();
        }
        if (!ismusic) {
            return false;
        }

        boolean knownartist =
            (artist != null) && !MediaStore.UNKNOWN_STRING.equals(artist);

        boolean knownalbum =
            (album != null) && !MediaStore.UNKNOWN_STRING.equals(album);
        
        if (knownartist && view.equals(mArtistName.getParent())) {
            title = artist;
            query = artist;
            mime = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE;
        } else if (knownalbum && view.equals(mAlbumName.getParent())) {
            title = album;
            if (knownartist) {
                query = artist + " " + album;
            } else {
                query = album;
            }
            mime = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE;
        } else if (view.equals(mTrackName.getParent()) || !knownartist || !knownalbum) {
            if ((song == null) || MediaStore.UNKNOWN_STRING.equals(song)) {
                // A popup of the form "Search for null/'' using ..." is pretty
                // unhelpful, plus, we won't find any way to buy it anyway.
                return true;
            }

            title = song;
            if (knownartist) {
                query = artist + " " + song;
            } else {
                query = song;
            }
            mime = "audio/*"; // the specific type doesn't matter, so don't bother retrieving it
        } else {
            throw new RuntimeException("shouldn't be here");
        }
        title = getString(R.string.mediasearch, title);

        Intent i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.putExtra(SearchManager.QUERY, query);
        if(knownartist) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
        }
        if(knownalbum) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, song);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, mime);

        startActivity(Intent.createChooser(i, title));
        return true;
    }

    /**
     * New position to seek to, in miliseconds.
     */
    private long mPositionToSeek;

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            //mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            //long now = SystemClock.elapsedRealtime();
            //if (!mFromTouch && (now - mLastSeekEventTime) > 250) {
            //    mLastSeekEventTime = now;
            //    mPositionToSeek = mDuration * progress / 1000;
         // morris yang 0407 [
         /*
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }
         */
           //  ]
                // trackball event, allow progress updates
            //}
                if (!mFromTouch) {
                mPositionToSeek = mDuration * progress / 1000;
                mPosOverride = mPositionToSeek;
                try {
                    mService.seek(mPositionToSeek);
                } catch (RemoteException ex) {
                }
                refreshNow();
                    mPosOverride = -1;
                }
            }
        public void onStopTrackingTouch(SeekBar bar) {

           // morris yang 0407 [
		   if (mService != null) {
                try {
                    //mService.seek(mPositionToSeek);
                    mPositionToSeek = bar.getProgress() * mDuration / 1000;
                    mPosOverride = mPositionToSeek;
                    mService.seek(mPositionToSeek);
                    refreshNow();
                } catch (RemoteException ex) {
                }
        }
           // ]

            mPosOverride = -1;
            mFromTouch = false;
        }
    };
    
    private View.OnClickListener mQueueListener = new View.OnClickListener() {
        public void onClick(View v) {
            startActivity(
                    new Intent(Intent.ACTION_EDIT)
                    .setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track")
                    .putExtra("playlist", "nowplaying")
            );
        }
    };
    
    private View.OnClickListener mShuffleListener = new View.OnClickListener() {
        public void onClick(View v) {
            toggleShuffle();
        }
    };

    private View.OnClickListener mRepeatListener = new View.OnClickListener() {
        public void onClick(View v) {
            cycleRepeat();
        }
    };

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                if (mService.position() < 2000) {
            		//added by qingfu.su@archermind.com
            		mPosOverride = -1;
            		//added by qingfu.su@archermind.com end
                    mService.prev();
                } else {
                    mService.seek(0);
            		//added by qingfu.su@archermind.com
            		mPosOverride = -1;
            		//added by qingfu.su@archermind.com end
                    mService.play();
                }
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                mService.next();
            	//added by qingfu.su@archermind.com
            	mPosOverride = -1;
            	//added by qingfu.su@archermind.com end
            } catch (RemoteException ex) {
            }
        }
    };

    private RepeatingImageButton.RepeatListener mRewListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };
    
    private RepeatingImageButton.RepeatListener mFfwdListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanForward(repcnt, howlong);
        }
    };
   
    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        unregisterReceiver(mUnmountReceiver);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        paused = false;

        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            mHandler.sendEmptyMessage(QUIT);
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
		f.addAction(MediaPlaybackService.QUIT_PLAYBACK);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mStatusListener, new IntentFilter(f));
        f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addDataScheme("file");
        registerReceiver(mUnmountReceiver, f);
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Intent i = getIntent();
        boolean collapseStatusBar = i.getBooleanExtra("collapse_statusbar", false);
        Log.d(TAG, "onResume: collapseStatusBar=" + collapseStatusBar);
        if (collapseStatusBar) {
        	StatusBarManager statusBar = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
        	statusBar.collapse();
        }
        updateTrackInfo();
        setPauseButtonImage();
		// When back to this activity, ask service for right position
		mPosOverride = -1;
		
		// mtk80658, add for Music lyrics supporting
		if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
		    if (null != mScrollLrcView) {
		        mScrollLrcView.resetHighlight();
		    }
		}
		// mtk80658
    }
    
    @Override
    public void onDestroy()
    {
        mAlbumArtWorker.quit();
        super.onDestroy();
        //System.out.println("***************** playback activity onDestroy\n");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Don't show the menu items if we got launched by path/filedescriptor, or
        // if we're in one shot mode. In most cases, these menu items are not
        // useful in those modes, so for consistency we never show them in these
        // modes, instead of tailoring them to the specific file being played.
        long currentAudioId = MusicUtils.getCurrentAudioId();
        if (currentAudioId >= 0) {
            menu.add(0, GOTO_START, 0, R.string.goto_start).setIcon(R.drawable.ic_menu_music_library);
            menu.add(0, PARTY_SHUFFLE, 0, R.string.party_shuffle); // icon will be set in onPrepareOptionsMenu()
            // get the object for method onPrepareOptionsMenu to keep playlist menu up-to-date
            mAddToPlaylistSubmenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                    R.string.add_to_playlist).setIcon(android.R.drawable.ic_menu_add);
            // these next two are in a separate group, so they can be shown/hidden as needed
            // based on the keyguard state
            
            if (FeatureOption.MTK_DRM_APP) {
	            Cursor c = getContentResolver().query(
	            	ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentAudioId), 
	            	new String[] {MediaStore.Audio.Media.IS_DRM, MediaStore.Audio.Media.DRM_METHOD}, null, null, null);
	            
	            int isDRM = -1;
	            int drmMethod = -1;
	            if (c != null && c.getCount() == 1) {
	            	c.moveToFirst();
	            	isDRM = c.getInt(0);
	            	drmMethod = c.getInt(1);
	            }
	            if (c != null)
	            	c.close();
	            if (isDRM != 1 || (isDRM == 1 && drmMethod == DrmStore.DrmMethod.METHOD_FL)) {
		            menu.add(1, USE_AS_RINGTONE, 0, R.string.ringtone_menu_short)
	  	                  .setIcon(R.drawable.ic_menu_set_as_ringtone);
	  	        }
            } else {
            	 menu.add(1, USE_AS_RINGTONE, 0, R.string.ringtone_menu_short)
            	 	.setIcon(R.drawable.ic_menu_set_as_ringtone);
            }
            menu.add(1, DELETE_ITEM, 0, R.string.delete_item)
                    .setIcon(R.drawable.ic_menu_delete);

            Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            if (getPackageManager().resolveActivity(i, 0) != null) {
                menu.add(0, EFFECTS_PANEL, 0, R.string.effects_list_title).setIcon(R.drawable.ic_menu_eq);
            }
            if (FeatureOption.MTK_FM_TX_SUPPORT) {
                SubMenu fmSettingItem;
                fmSettingItem= menu.addSubMenu (1,Menu.NONE,0,R.string.music_settings).setIcon(com.android.internal.R.drawable.ic_menu_preferences);//setIcon(R.drawable.ic_settings_fm);
                fmSettingItem.add(0, FM_TRANSMITTER, 0, R.string.music_fm_transmiter);
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mService == null) return false;
        MenuItem item = menu.findItem(PARTY_SHUFFLE);
        if (item != null) {
            int shuffle = MusicUtils.getCurrentShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle_off);
            } else {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle);
            }
        }
        // Keep the playlist menu up-to-date
        MusicUtils.makePlaylistMenu(this, mAddToPlaylistSubmenu);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        try {
            switch (item.getItemId()) {
                case GOTO_START:
                    intent = new Intent();
                    intent.setClass(this, MusicBrowserActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;
                case USE_AS_RINGTONE: {
                    // Set the system setting to make this the current ringtone
                    if (mService != null) {
                        MusicUtils.setRingtone(this, mService.getAudioId());
                    }
                    return true;
                }
                case PARTY_SHUFFLE:
                    MusicUtils.togglePartyShuffle();
                    setShuffleButtonImage();
                    break;
                    
                case NEW_PLAYLIST: {
                    intent = new Intent();
                    intent.setClass(this, CreatePlaylist.class);
                    startActivityForResult(intent, NEW_PLAYLIST);
                    return true;
                }

                case PLAYLIST_SELECTED: {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    long playlist = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, list, playlist);
                    return true;
                }
                
                case DELETE_ITEM: {
                    if (mService != null) {
                        long [] list = new long[1];
                        list[0] = MusicUtils.getCurrentAudioId();
                        Bundle b = new Bundle();
                        String f;
                        if (android.os.Environment.isExternalStorageRemovable()) {
                            f = getString(R.string.delete_song_desc, mService.getTrackName());
                        } else {
                            f = getString(R.string.delete_song_desc_nosdcard, mService.getTrackName());
                        }
                        b.putString("description", f);
                        b.putLongArray("items", list);
                        intent = new Intent();
                        intent.setClass(this, DeleteItems.class);
                        intent.putExtras(b);
                        startActivityForResult(intent, -1);
                    }
                    return true;
                }

                case EFFECTS_PANEL: {
                    Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId());
                    startActivityForResult(i, EFFECTS_PANEL);
                    return true;
                }

                case FM_TRANSMITTER:{
                    if (FeatureOption.MTK_FM_TX_SUPPORT) {
                        Intent intentFMTx = new Intent("com.mediatek.FMTransmitter.FMTransmitterActivity");
                        intentFMTx.setClassName("com.mediatek.FMTransmitter", "com.mediatek.FMTransmitter.FMTransmitterActivity");

                        try {
                        startActivity(intentFMTx);
                        } catch (ActivityNotFoundException anfe) {
                        Log.e("MediaPlaybackActivity", "FM Tx activity is not found on this phone!!");
                        }
                    }
                    return true;
                }
            }
        } catch (RemoteException ex) {
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case NEW_PLAYLIST:
                Uri uri = intent.getData();
                if (uri != null) {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    int playlist = Integer.parseInt(uri.getLastPathSegment());
                    MusicUtils.addToPlaylist(this, list, playlist);
                }
                break;
        }
    }
    private final int keyboard[][] = {
        {
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
        },
        {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DEL,
        },
        {
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_ENTER
        }

    };

    private int lastX;
    private int lastY;

    private boolean seekMethod1(int keyCode)
    {
        if (mService == null) return false;
        for(int x=0;x<10;x++) {
            for(int y=0;y<3;y++) {
                if(keyboard[y][x] == keyCode) {
                    int dir = 0;
                    // top row
                    if(x == lastX && y == lastY) dir = 0;
                    else if (y == 0 && lastY == 0 && x > lastX) dir = 1;
                    else if (y == 0 && lastY == 0 && x < lastX) dir = -1;
                    // bottom row
                    else if (y == 2 && lastY == 2 && x > lastX) dir = -1;
                    else if (y == 2 && lastY == 2 && x < lastX) dir = 1;
                    // moving up
                    else if (y < lastY && x <= 4) dir = 1; 
                    else if (y < lastY && x >= 5) dir = -1; 
                    // moving down
                    else if (y > lastY && x <= 4) dir = -1; 
                    else if (y > lastY && x >= 5) dir = 1; 
                    lastX = x;
                    lastY = y;
                    try {
                        mService.seek(mService.position() + dir * 5);
                    } catch (RemoteException ex) {
                    }
                    refreshNow();
                    return true;
                }
            }
        }
        lastX = -1;
        lastY = -1;
        return false;
    }

    private boolean seekMethod2(int keyCode)
    {
        if (mService == null) return false;
        for(int i=0;i<10;i++) {
            if(keyboard[0][i] == keyCode) {
                int seekpercentage = 100*i/10;
                try {
                    mService.seek(mService.duration() * seekpercentage / 100);
                } catch (RemoteException ex) {
                }
                refreshNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            if (mStartSeekPos < 1000) {
                                mService.prev();
                            } else {
                                mService.seek(0);
                            }
                        } else {
                            scanBackward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            mService.next();
                        } else {
                            scanForward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    View curSel = getCurrentFocus();
                    if((curSel !=null && R.id.pause == curSel.getId()) || (curSel == null)){
                        doPauseResume();
            }
                    return true;
            }
        } catch (RemoteException ex) {
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        if (mDeviceHasDpad && (mPrevButton.isFocused() ||
                mNextButton.isFocused() ||
                mPauseButton.isFocused())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        int direction = -1;
        int repcnt = event.getRepeatCount();

        if((seekmethod==0)?seekMethod1(keyCode):seekMethod2(keyCode))
            return true;

        switch(keyCode)
        {
/*
            // image scale
            case KeyEvent.KEYCODE_Q: av.adjustParams(-0.05, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_E: av.adjustParams( 0.05, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // image translate
            case KeyEvent.KEYCODE_W: av.adjustParams(    0.0, 0.0,-1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_X: av.adjustParams(    0.0, 0.0, 1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_A: av.adjustParams(    0.0,-1.0, 0.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_D: av.adjustParams(    0.0, 1.0, 0.0, 0.0, 0.0, 0.0); break;
            // camera rotation
            case KeyEvent.KEYCODE_R: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_U: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // camera translate
            case KeyEvent.KEYCODE_Y: av.adjustParams(    0.0, 0.0, 0.0, 0.0,-1.0, 0.0); break;
            case KeyEvent.KEYCODE_N: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 1.0, 0.0); break;
            case KeyEvent.KEYCODE_G: av.adjustParams(    0.0, 0.0, 0.0,-1.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_J: av.adjustParams(    0.0, 0.0, 0.0, 1.0, 0.0, 0.0); break;

*/

            case KeyEvent.KEYCODE_SLASH:
                seekmethod = 1 - seekmethod;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mPrevButton.hasFocus()) {
                    mPrevButton.requestFocus();
                }
                scanBackward(repcnt, event.getEventTime() - event.getDownTime());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mNextButton.hasFocus()) {
                    mNextButton.requestFocus();
                }
                scanForward(repcnt, event.getEventTime() - event.getDownTime());
                return true;

            case KeyEvent.KEYCODE_S:
                toggleShuffle();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                 return true;
            case KeyEvent.KEYCODE_SPACE:
                doPauseResume();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void scanBackward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doPauseResume() {
        try {
        	Log.d(TAG, "doPauseResume: " + (mService == null ? "mService=null" : ("isPlaying=" + mService.isPlaying())));
            if(mService != null) {
                if (mService.isPlaying()) {
					mPosOverride = mService.position();
                    mService.pause();
                } else {
                    mService.play();
					mPosOverride = -1;
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void toggleShuffle() {
        if (mService == null) {
            return;
        }
        try {
            int shuffle = mService.getShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL ||
                    shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                showToast(R.string.shuffle_off_notif);
            } else {
                Log.e("MediaPlaybackActivity", "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }
    
    private void cycleRepeat() {
        if (mService == null) {
            return;
        }
        try {
            int mode = mService.getRepeatMode();
            if (mode == MediaPlaybackService.REPEAT_NONE) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                showToast(R.string.repeat_all_notif);
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                if (mService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
                showToast(R.string.repeat_current_notif);
            } else {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
                showToast(R.string.repeat_off_notif);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }
        
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }

    private void startPlayback() {

        if(mService == null)
            return;
        Intent intent = getIntent();
        String filename = "";
        Uri uri = intent.getData();
        if (uri != null && uri.toString().length() > 0) {
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            try {
                mService.stop();
                mService.openFile(filename);
                mService.play();
                setIntent(new Intent());
            } catch (Exception ex) {
                Log.d("MediaPlaybackActivity", "couldn't start playback: " + ex);
            }
        }

        updateTrackInfo();

        // mtk80658: add for Music lyrics supporting
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            Log.v(TAG, "reload Lyrics @startPlayback()");
            reloadLyrics();
            updateLyricsFrame();
            updateLyrics();
        }
        // mtk80658

        long next = refreshNow();
        queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                mService = IMediaPlaybackService.Stub.asInterface(obj);
                startPlayback();
                try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                            mService.getPath() != null) {
                        // something is playing now, we're done
                        mRepeatButton.setVisibility(View.VISIBLE);
                        mShuffleButton.setVisibility(View.VISIBLE);
                        mQueueButton.setVisibility(View.GONE);
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                        setPauseButtonImage();
                        return;
                    }
                } catch (RemoteException ex) {
                }
                // Service is dead or not playing anything. If we got here as part
                // of a "play this file" Intent, exit. Otherwise go to the Music
                // app start screen.
                if (getIntent().getData() == null) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setClass(MediaPlaybackActivity.this, MusicBrowserActivity.class);
                    startActivity(intent);
                }
                finish();
            }
            public void onServiceDisconnected(ComponentName classname) {
                mService = null;
            }
    };

    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_once_btn);
                    break;
                default:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_off_btn);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setShuffleButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getShuffleMode()) {
                case MediaPlaybackService.SHUFFLE_NONE:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_shuffle_off_btn);
                    break;
                case MediaPlaybackService.SHUFFLE_AUTO:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_partyshuffle_on_btn);
                    break;
                default:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_shuffle_on_btn);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
               // mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            	mPauseButton.setImageResource(R.drawable.stat_music_stop_1);
                mPosOverride = -1;
            } else {
               // mPauseButton.setImageResource(android.R.drawable.ic_media_play);
            	mPauseButton.setImageResource(R.drawable.stat_music_play_1);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private ImageView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int ALBUM_ART_DECODED = 4;

    // mtk80658: add for Music lyrics supporting
    private static final int UPDATE_LRC = 5;
    // mtk80658

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            if (pos + 100 > mDuration) {
                Log.d(TAG, "refreshNow, do a workaround for position");
                pos = mDuration;
            }
            long remaining = 1000 - (pos % 1000);

            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
                
                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    remaining = 500;
                }

                if (!mFromTouch)
                    mProgress.setProgress((int) (1000 * pos / mDuration));

                // mtk80658: add for Music lyrics supporting
                if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
                    // scroll lyric (multiple line lyrics) or move up one next line (single line) here
                    if (mLrcReady) {
                        if (mScrollLrcReady) { // if the scroll view is already, then just scroll it
                            mScrollLrcView.scrollLyrics(this, (int)pos);
                        }
                        else { // if the scroll view is not ready, then need to update the lyric display
                            Log.v(TAG, "Lyrics is not ready, send message to update it");
                            mHandler.sendEmptyMessage(UPDATE_LRC);
                        }
                    }
                }
                // mtk80658

            } else {
                mCurrentTime.setVisibility(View.VISIBLE);
                mCurrentTime.setText("0:00");
                mTotalTime.setText("--:--");
                if (!mFromTouch)
                    mProgress.setProgress(0);
            }
            // Correct duration for MP3/AMR/AWB/AAC formats
            if (mNeedUpdateDuration && mService.isPlaying()) {
            	long newDuration = mService.duration();

            	if (newDuration > 0L && newDuration != mDuration) {
            		mDuration = newDuration;
            		mNeedUpdateDuration = false;
            		// Update UI
            		mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));

								Log.i(TAG, "new duration updated!!");
            		String id = null;
            		try {
            			id = String.valueOf(mService.getAudioId());
            		} catch (RemoteException e) {
            		}
            	}
            } else if (pos < 0 || pos >= mDuration) {
				mNeedUpdateDuration = false;
	    	}
            
            // return the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            return remaining;
        } catch (RemoteException ex) {
        }
        return 500;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALBUM_ART_DECODED:
                    mAlbum.setImageBitmap((Bitmap)msg.obj);
                    mAlbum.getDrawable().setDither(true);
                    break;

                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                    
                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(MediaPlaybackActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                    break;

                // mtk80658: add for Music lyrics supporting
                case UPDATE_LRC:
                    if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
                    	if (null != mScrollLrcView) {
                            mScrollLrcView.update();
                            mScrollLrcReady = true;
                    	}
                        refreshNow();
                    }
                    break;
                // mtk80658

                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "mStatusListener: " + action);
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();

                // mtk80658: add for Music lyrics supporting
                if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
                    mHandler.removeMessages(REFRESH); // add by Yiqi Kang 20110516 evening
                    Log.v(TAG, "reload Lyrics @META_CHANGED message");
                    reloadLyrics();
                    updateLyricsFrame();
                    updateLyrics();
                }
                // mtk80658

                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.QUIT_PLAYBACK)) {
                mHandler.removeMessages(REFRESH);
                finish();
			} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                // stop refreshing
                Log.d(TAG, "onReceive, stop refreshing ...");
                mHandler.removeMessages(REFRESH);
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                // restore refreshing
                Log.d(TAG, "onReceive, restore refreshing ...");
                long next = refreshNow();
                queueNextRefresh(next);
            }
        }
    };
    
    private BroadcastReceiver mUnmountReceiver = new BroadcastReceiver() {
  	@Override
      public void onReceive(Context context, Intent intent) {
      	String action = intent.getAction();
      	if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
      		Log.i(TAG, "unmount receiver: MEDIA_EJECT, finish self!!");
      		finish();
      	}
      }
    };

    private static class AlbumSongIdWrapper {
        public long albumid;
        public long songid;
        AlbumSongIdWrapper(long aid, long sid) {
            albumid = aid;
            songid = sid;
        }
    }
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }
            
            long songid = mService.getAudioId(); 
            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                // Once we can get album art and meta data from MediaPlayer, we
                // can show that info again when streaming.
                ((View) mArtistName.getParent()).setVisibility(View.INVISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.INVISIBLE);
                mAlbum.setVisibility(View.GONE);
                mTrackName.setText(path);
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(-1, -1)).sendToTarget();
            } else {
                ((View) mArtistName.getParent()).setVisibility(View.VISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.VISIBLE);
                String artistName = mService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = getString(R.string.unknown_artist_name);
                }
                mArtistName.setText(artistName);
                String albumName = mService.getAlbumName();
                long albumid = mService.getAlbumId();
                if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    albumName = getString(R.string.unknown_album_name);
                    albumid = -1;
                }
                mAlbumName.setText(albumName);
                mTrackName.setText(mService.getTrackName());
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
                mAlbum.setVisibility(View.VISIBLE);
            }
            mDuration = mService.duration();
            mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
            // For mp3/aac/amr/awb file, its duration need to be updated when playing
	    	String mimeType = mService.getMIMEType();
	    	if (mimeType != null) {
	    		Log.i(TAG, "mimeType=" + mimeType);
	    	}
	    	if (mimeType != null && (mimeType.equals("audio/mpeg") 
	    		|| mimeType.equals("audio/amr") 
	    		|| mimeType.equals("audio/amr-wb") 
	    		|| mimeType.equals("audio/aac"))) {
				mNeedUpdateDuration = true;
	    	} else {
				mNeedUpdateDuration = false;
	    	}
        } catch (RemoteException ex) {
            finish();
        }
    }

    // mtk80658: add for Music lyrics supporting
    private void reloadLyrics() {
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            if (mService == null) {
                return;
            }
            
            mLrcReady = false;
            mScrollLrcReady = false;
            
            // get sound track file name from service
            String filePathName = null;
            try {
                filePathName = mService.getTrackFilePathName();
                if (filePathName == null) {
                    finish();
                    return;
                }
            } catch (RemoteException ex) {
                finish();
            }

            // now load lyrics here, or should launch another thread if performance matters.
            if (!mLrcReady) {
                mLrcReady = mScrollLrcView.loadLyrics(this, filePathName);
            }
        } // if (FeatureOption.MTK_MUSIC_LRC_SUPPORT)
    }
    // mtk80658

    // mtk80658: add for Music lyrics supporting
    private void updateLyrics() {
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            if (mService == null) {
                return;
            }

            // get sound track file name from service
            String filePathName = null;
            try {
                filePathName = mService.getTrackFilePathName();
                if (filePathName == null) {
                    finish();
                    return;
                }
            } catch (RemoteException ex) {
                finish();
            }

            try {
                if (!mScrollLrcReady) {
                    mScrollLrcView.setupLyrics(this, filePathName, mLrcMode, (int)mService.position());
                }
            } catch (RemoteException ex) {
                throw new LrcScrollException();
            }
        }
    }
    // mtk80658

    // mtk80658: add for Music lyrics supporting
    private void updateLyricsFrame() {
        if (FeatureOption.MTK_MUSIC_LRC_SUPPORT) {
            if (mService == null) {
                return;
            }

            FrameLayout fl = (FrameLayout) findViewById(R.id.lyrics_frame);
            LayoutParams lp = fl.getLayoutParams();
            LinearLayout ll = (LinearLayout) findViewById(R.id.lyrics_layout);
            
            ImageView iv = (ImageView) findViewById(R.id.lyrics_shift);
            
            switch (mLrcMode) {
                case ScrollLrcView.LRC_MODE_SINGLE:
                    lp.height = mScrollLrcView.singleLineModeHeightPixel();
                    ll.setPadding(0, 0, 0, 0);
                    iv.setBackgroundResource(R.drawable.lyrics_switch_single_mode);
                    break;

                case ScrollLrcView.LRC_MODE_MULTIPLE:
                    lp.height = LayoutParams.MATCH_PARENT;
                    ll.setPadding(0, mScrollLrcView.singleLineModePaddingPixel(),
                                  0, mScrollLrcView.singleLineModePaddingPixel());
                    iv.setBackgroundResource(R.drawable.lyrics_switch_multiple_mode);
                    break;

                default:
                    break;
            }
            fl.setLayoutParams(lp);
        }
    }
    // mtk80658

    public class AlbumArtHandler extends Handler {
        private long mAlbumId = -1;
        
        public AlbumArtHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg)
        {
            long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
            long songid = ((AlbumSongIdWrapper) msg.obj).songid;
            if (msg.what == GET_ALBUM_ART && (mAlbumId != albumid || albumid < 0)) {
                // while decoding the new image, show the default album art
                Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
                mHandler.removeMessages(ALBUM_ART_DECODED);
                mHandler.sendMessageDelayed(numsg, 300);
                Bitmap bm = MusicUtils.getArtwork(MediaPlaybackActivity.this, songid, albumid);
                if (bm == null) {
                    bm = MusicUtils.getArtwork(MediaPlaybackActivity.this, songid, -1);
                    albumid = -1;
                }
                if (bm != null) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessage(numsg);
                }
                mAlbumId = albumid;
            }
        }
    }
    
    private static class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        
        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
        public Looper getLooper() {
            return mLooper;
        }
        
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLock.notifyAll();
            }
            Looper.loop();
        }
        
        public void quit() {
            mLooper.quit();
        }
    }
}


