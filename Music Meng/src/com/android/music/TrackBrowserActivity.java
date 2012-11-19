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

import com.android.music.MusicUtils.ServiceToken;

import android.app.Activity;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.AbstractCursor;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.Environment;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Playlists;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.text.Collator;
import java.util.Arrays;

import android.os.FileUtils;
import android.drm.*;
import com.mediatek.featureoption.FeatureOption;

public class TrackBrowserActivity extends ListActivity
        implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection, DialogInterface.OnClickListener
{
    private static final int Q_SELECTED = CHILD_MENU_BASE;
    private static final int Q_ALL = CHILD_MENU_BASE + 1;
    private static final int SAVE_AS_PLAYLIST = CHILD_MENU_BASE + 2;
    private static final int PLAY_ALL = CHILD_MENU_BASE + 3;
    private static final int CLEAR_PLAYLIST = CHILD_MENU_BASE + 4;
    private static final int REMOVE = CHILD_MENU_BASE + 5;
    private static final int SEARCH = CHILD_MENU_BASE + 6;


    private static final String LOGTAG = "TrackBrowser";

    private String[] mCursorCols;
    private String[] mPlaylistMemberCols;
    private boolean mDeletedOneRow = false;
    private boolean mEditMode = false;
    private String mCurrentTrackName;
    private String mCurrentAlbumName;
    private String mCurrentArtistNameForAlbum;
    private ListView mTrackList;
    private Cursor mTrackCursor;
    private TrackListAdapter mAdapter;
    private boolean mAdapterSent = false;
    private String mAlbumId;
    private String mArtistId;
    private String mPlaylist;
    private String mGenre;
    private String mSortOrder;
    private int mSelectedPosition;
    private long mSelectedId;
    private static int mLastListPosCourse = -1;
    private static int mLastListPosFine = -1;
    private boolean mUseLastListPos = false;
    private ServiceToken mToken;
    
    private int mCardId = -1;
    
    private IMediaPlaybackService myService = null;

    private AlbumArtFetcher mAsyncAlbumArtFetcher = null;
    
    public DrmManagerClient mDrmClient = null;
    public int mCurTrackPos = -1;
    
    public TrackBrowserActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.getBooleanExtra("withtabs", false)) {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            } else {
            	mCardId = FileUtils.getFatVolumeId(Environment.getExternalStorageDirectory().getPath());
            }
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (icicle != null) {
            mSelectedId = icicle.getLong("selectedtrack");
            mCurTrackPos = icicle.getInt("curtrackpos");
            mAlbumId = icicle.getString("album");
            mArtistId = icicle.getString("artist");
            mPlaylist = icicle.getString("playlist");
            mGenre = icicle.getString("genre");
            mEditMode = icicle.getBoolean("editmode", false);
            mCardId = icicle.getInt("cardid", -1);
            Log.i(LOGTAG, "mCardId retrieved is: " + mCardId);
        } else {
        	//ALPS00122166
        	mSelectedId = intent.getLongExtra("selectedtrack", 0);
        	
            mAlbumId = intent.getStringExtra("album");
            // If we have an album, show everything on the album, not just stuff
            // by a particular artist.
            mArtistId = intent.getStringExtra("artist");
            mPlaylist = intent.getStringExtra("playlist");
            mGenre = intent.getStringExtra("genre");
            mEditMode = intent.getAction().equals(Intent.ACTION_EDIT);
        }

        mCursorCols = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.IS_DRM,
                MediaStore.Audio.Media.DRM_METHOD
        };
        mPlaylistMemberCols = new String[] {
                MediaStore.Audio.Playlists.Members._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                MediaStore.Audio.Playlists.Members.AUDIO_ID,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.IS_DRM,
                MediaStore.Audio.Media.DRM_METHOD
        };

        setContentView(R.layout.media_picker_activity);
        mUseLastListPos = MusicUtils.updateButtonBar(this, R.id.songtab);
        mTrackList = getListView();
        mTrackList.setOnCreateContextMenuListener(this);
        mTrackList.setCacheColorHint(0);
        if (mEditMode) {
            ((TouchInterceptor) mTrackList).setDropListener(mDropListener);
            ((TouchInterceptor) mTrackList).setRemoveListener(mRemoveListener);
            mTrackList.setDivider(null);
            mTrackList.setSelector(R.drawable.list_selector_background);
        } else {
            mTrackList.setTextFilterEnabled(true);
        }
        mAdapter = (TrackListAdapter) getLastNonConfigurationInstance();
        
        if (FeatureOption.MTK_DRM_APP) {
        	mDrmClient = new DrmManagerClient(this);
        }
        if (mAdapter != null) {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
        }
        mToken = MusicUtils.bindToService(this, this);

        // don't set the album art until after the view has been layed out
        
        mAsyncAlbumArtFetcher = new AlbumArtFetcher();
        mTrackList.post(new Runnable() {
            public void run() {
                setAlbumArtBackground();
            }
        });
    }

    public void onServiceConnected(ComponentName name, IBinder service)
    {
        myService = IMediaPlaybackService.Stub.asInterface(service);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addAction(Intent.ACTION_MEDIA_MOUNTED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);

        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new TrackListAdapter(
                    getApplication(), // need to use application context to avoid leaks
                    this,
                    mEditMode ? R.layout.edit_track_list_item : R.layout.track_list_item,
                    null, // cursor
                    new String[] {},
                    new int[] {},
                    "nowplaying".equals(mPlaylist),
                    mPlaylist != null &&
                    !(mPlaylist.equals("podcasts") || mPlaylist.equals("recentlyadded")));
            setListAdapter(mAdapter);
            setTitle(R.string.working_songs);
            getTrackCursor(mAdapter.getQueryHandler(), null, true);
        } else {
            mTrackCursor = mAdapter.getCursor();
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor != null) {
                init(mTrackCursor, false);
            } else {
                setTitle(R.string.working_songs);
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
        }
        if (!mEditMode) {
            MusicUtils.updateNowPlaying(this);
        }
    }
    
    public void onServiceDisconnected(ComponentName name) {
        // we can't really function without the service, so don't
        myService = null;
        finish();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        TrackListAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }
    
    @Override
    public void onDestroy() {
    	mAsyncAlbumArtFetcher.cancel(true);
        ListView lv = getListView();
        if (lv != null) {
            if (mUseLastListPos) {
                mLastListPosCourse = lv.getFirstVisiblePosition();
                View cv = lv.getChildAt(0);
                if (cv != null) {
                    mLastListPosFine = cv.getTop();
                }
            }
            if (mEditMode) {
                // clear the listeners so we won't get any more callbacks
                ((TouchInterceptor) lv).setDropListener(null);
                ((TouchInterceptor) lv).setRemoveListener(null);
            }
        }

        MusicUtils.unbindFromService(mToken);
        myService = null;
        try {
            if ("nowplaying".equals(mPlaylist)) {
                unregisterReceiverSafe(mNowPlayingListener);
            } else {
                unregisterReceiverSafe(mTrackListListener);
            }
        } catch (IllegalArgumentException ex) {
            // we end up here in case we never registered the listeners
        }
        
        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        setListAdapter(null);
        mAdapter = null;
        unregisterReceiverSafe(mScanListener);
        super.onDestroy();
    }
    
    /**
     * Unregister a receiver, but eat the exception that is thrown if the
     * receiver was never registered to begin with. This is a little easier
     * than keeping track of whether the receivers have actually been
     * registered by the time onDestroy() is called.
     */
    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mTrackCursor != null) {
           getListView().invalidateViews();
        }
        MusicUtils.setSpinnerState(this);
        if (!mEditMode) {
            MusicUtils.updateNowPlaying(this);
        }
    }
    @Override
    public void onPause() {
        mReScanHandler.removeCallbacksAndMessages(null);
        //ALPS00122166
        Intent i = new Intent(getIntent());
        i.putExtra("selectedtrack", mSelectedId);
        i.putExtra("curtrackpos", mCurTrackPos);
        setIntent(i);
        
        super.onPause();
    }
    
    /*
     * This listener gets called when the media scanner starts up or finishes, and
     * when the sd card is unmounted.
     */
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOGTAG, "mScanListener.onReceive:" + action);
            if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action) ||
                    Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                MusicUtils.setSpinnerState(TrackBrowserActivity.this);
                mReScanHandler.sendEmptyMessage(0);
            } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            	mReScanHandler.sendEmptyMessage(0);
            } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            	int cardid = FileUtils.getFatVolumeId(intent.getData().getPath());
            	Log.d(LOGTAG, "MEDIA_MOUNTED: new cardid=" + cardid + ", old cardid=" + mCardId);
            	if (mCardId != -1 && mCardId != cardid) {
            		Log.d(LOGTAG, "MEDIA_MOUNTED: SD card changed, quit activity!");
            		finish();
            	}
            }
        }
    };
    
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
            // if the query results in a null cursor, onQueryComplete() will
            // call init(), which will post a delayed message to this handler
            // in order to try again.
        }
    };
    
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong("selectedtrack", mSelectedId);
        outcicle.putString("artist", mArtistId);
        outcicle.putString("album", mAlbumId);
        outcicle.putString("playlist", mPlaylist);
        outcicle.putString("genre", mGenre);
        outcicle.putBoolean("editmode", mEditMode);
        outcicle.putInt("cardid", mCardId);
        outcicle.putInt("curtrackpos", mCurTrackPos);
        super.onSaveInstanceState(outcicle);
    }
    
    public void init(Cursor newCursor, boolean isLimited) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor
        
        if (mTrackCursor == null) {
            MusicUtils.displayDatabaseError(this);
            closeContextMenu();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        MusicUtils.hideDatabaseError(this);
        mUseLastListPos = MusicUtils.updateButtonBar(this, R.id.songtab);
        setTitle();

        // Restore previous position
        if (mLastListPosCourse >= 0 && mUseLastListPos) {
            ListView lv = getListView();
            // this hack is needed because otherwise the position doesn't change
            // for the 2nd (non-limited) cursor
            lv.setAdapter(lv.getAdapter());
            lv.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
            if (!isLimited) {
                mLastListPosCourse = -1;
            }
        }

        // When showing the queue, position the selection on the currently playing track
        // Otherwise, position the selection on the first matching artist, if any
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        if ("nowplaying".equals(mPlaylist)) {
            try {
                int cur = MusicUtils.sService.getQueuePosition();
                setSelection(cur);
                registerReceiver(mNowPlayingListener, new IntentFilter(f));
                mNowPlayingListener.onReceive(this, new Intent(MediaPlaybackService.META_CHANGED));
            } catch (RemoteException ex) {
            }
        } else {
            String key = getIntent().getStringExtra("artist");
            if (key != null) {
                int keyidx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
                mTrackCursor.moveToFirst();
                while (! mTrackCursor.isAfterLast()) {
                    String artist = mTrackCursor.getString(keyidx);
                    if (artist.equals(key)) {
                        setSelection(mTrackCursor.getPosition());
                        break;
                    }
                    mTrackCursor.moveToNext();
                }
            }
            registerReceiver(mTrackListListener, new IntentFilter(f));
            mTrackListListener.onReceive(this, new Intent(MediaPlaybackService.META_CHANGED));
        }
    }

    private void setAlbumArtBackground() {
    	/*
        if (!mEditMode) {
            try {
                long albumid = Long.valueOf(mAlbumId);
                Bitmap bm = MusicUtils.getArtwork(TrackBrowserActivity.this, -1, albumid, false);
                if (bm != null) {
                    MusicUtils.setBackground(mTrackList, bm);
                    mTrackList.setCacheColorHint(0);
                    return;
                }
            } catch (Exception ex) {
            }
        }
        mTrackList.setBackgroundColor(0xff000000);
        mTrackList.setCacheColorHint(0);
        */
        if (mAlbumId == null) {
            return;
        }
    	try {
    		mAsyncAlbumArtFetcher.execute(Long.valueOf(mAlbumId));
    	} catch (Exception ex) {
    		Log.e(LOGTAG, "Exception while fetching album art!!", ex);
    	}
    }

    private void setTitle() {

        CharSequence fancyName = null;
        if (mAlbumId != null) {
            int numresults = mTrackCursor != null ? mTrackCursor.getCount() : 0;
            if (numresults > 0) {
                mTrackCursor.moveToFirst();
                int idx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                fancyName = mTrackCursor.getString(idx);
                // For compilation albums show only the album title,
                // but for regular albums show "artist - album".
                // To determine whether something is a compilation
                // album, do a query for the artist + album of the
                // first item, and see if it returns the same number
                // of results as the album query.
                String where = MediaStore.Audio.Media.ALBUM_ID + "=?  AND " 
                + MediaStore.Audio.Media.ARTIST_ID + "=? ";
                String[] whereArgs = new String[]{
                        String.valueOf(mAlbumId), 
                        String.valueOf(mTrackCursor.getLong(mTrackCursor.getColumnIndexOrThrow(
                                MediaStore.Audio.Media.ARTIST_ID)))
                                }; 
                Cursor cursor = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Audio.Media.ALBUM}, where, whereArgs, null);
                if (cursor != null) {
                    if (cursor.getCount() != numresults) {
                        // compilation album
                        fancyName = mTrackCursor.getString(idx);
                    }    
                    cursor.close();
                }
                if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                    fancyName = getString(R.string.unknown_album_name);
                }
            }
        } else if (mPlaylist != null) {
            if (mPlaylist.equals("nowplaying")) {
                if (MusicUtils.getCurrentShuffleMode() == MediaPlaybackService.SHUFFLE_AUTO) {
                    fancyName = getText(R.string.partyshuffle_title);
                } else {
                    fancyName = getText(R.string.nowplaying_title);
                }
            } else if (mPlaylist.equals("podcasts")){
                fancyName = getText(R.string.podcasts_title);
            } else if (mPlaylist.equals("recentlyadded")){
                fancyName = getText(R.string.recentlyadded_title);
            } else {
                String [] cols = new String [] {
                MediaStore.Audio.Playlists.NAME
                };
                Cursor cursor = MusicUtils.query(this,
                        ContentUris.withAppendedId(Playlists.EXTERNAL_CONTENT_URI, Long.valueOf(mPlaylist)),
                        cols, null, null, null);
                if (cursor != null) {
                    if (cursor.getCount() != 0) {
                        cursor.moveToFirst();
                        fancyName = cursor.getString(0);
                    }
                    cursor.close();
                }
            }
        } else if (mGenre != null) {
            String [] cols = new String [] {
            MediaStore.Audio.Genres.NAME
            };
            Cursor cursor = MusicUtils.query(this,
                    ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, Long.valueOf(mGenre)),
                    cols, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    fancyName = cursor.getString(0);
                }
                cursor.close();
            }
        }

        if (fancyName != null) {
            setTitle(fancyName);
        } else {
            setTitle(R.string.tracks_title);
        }
    }
    
    private TouchInterceptor.DropListener mDropListener =
        new TouchInterceptor.DropListener() {
        public void drop(int from, int to) {
            if (mTrackCursor instanceof NowPlayingCursor) {
                // update the currently playing list
                NowPlayingCursor c = (NowPlayingCursor) mTrackCursor;
                c.moveItem(from, to);
                ((TrackListAdapter)getListAdapter()).notifyDataSetChanged();
                getListView().invalidateViews();
                mDeletedOneRow = true;
            } else {
                // update a saved playlist
                MediaStore.Audio.Playlists.Members.moveItem(getContentResolver(),
                        Long.valueOf(mPlaylist), from, to);
            }
        }
    };
    
    private TouchInterceptor.RemoveListener mRemoveListener =
        new TouchInterceptor.RemoveListener() {
        public void remove(int which) {
            removePlaylistItem(which);
        }
    };

    private void removePlaylistItem(int which) {
        View v = mTrackList.getChildAt(which - mTrackList.getFirstVisiblePosition());
        if (v == null) {
            Log.d(LOGTAG, "No view when removing playlist item " + which);
            return;
        }
        try {
            if (MusicUtils.sService != null
                    && which != MusicUtils.sService.getQueuePosition()) {
                mDeletedOneRow = true;
            }
        } catch (RemoteException e) {
            // Service died, so nothing playing.
            mDeletedOneRow = true;
        }
        v.setVisibility(View.GONE);
        mTrackList.invalidateViews();
        if (mTrackCursor instanceof NowPlayingCursor) {
            ((NowPlayingCursor)mTrackCursor).removeItem(which);
        } else {
            int colidx = mTrackCursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members._ID);
            mTrackCursor.moveToPosition(which);
            long id = mTrackCursor.getLong(colidx);
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                    Long.valueOf(mPlaylist));
            getContentResolver().delete(
                    ContentUris.withAppendedId(uri, id), null, null);
        }
        v.setVisibility(View.VISIBLE);
        mTrackList.invalidateViews();
    }
    
    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
            if (!mEditMode) {
                MusicUtils.updateNowPlaying(TrackBrowserActivity.this);
            }
        }
    };

    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MediaPlaybackService.META_CHANGED)) {
                getListView().invalidateViews();
            } else if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                if (mDeletedOneRow) {
                    // This is the notification for a single row that was
                    // deleted previously, which is already reflected in
                    // the UI.
                    mDeletedOneRow = false;
                    return;
                }
                // The service could disappear while the broadcast was in flight,
                // so check to see if it's still valid
                if (MusicUtils.sService == null) {
                    finish();
                    return;
                }
                if (mAdapter != null) {
                    Cursor c = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
                    if (c.getCount() == 0) {
                        finish();
                        return;
                    }
                    mAdapter.changeCursor(c);
                }
            }
        }
    };

    // Cursor should be positioned on the entry to be checked
    // Returns false if the entry matches the naming pattern used for recordings,
    // or if it is marked as not music in the database.
    private boolean isMusic(Cursor c) {
        int titleidx = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int albumidx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        int artistidx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);

        String title = c.getString(titleidx);
        String album = c.getString(albumidx);
        String artist = c.getString(artistidx);
        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                title != null &&
                title.startsWith("recording")) {
            // not music
            return false;
        }

        int ismusic_idx = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);
        boolean ismusic = true;
        if (ismusic_idx >= 0) {
            ismusic = mTrackCursor.getInt(ismusic_idx) != 0;
        }
        return ismusic;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(this, sub);
        if (mEditMode) {
            menu.add(0, REMOVE, 0, R.string.remove_from_playlist);
        }
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mSelectedPosition =  mi.position;
        mTrackCursor.moveToPosition(mSelectedPosition);
        try {
            int id_idx = mTrackCursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members.AUDIO_ID);
            mSelectedId = mTrackCursor.getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            mSelectedId = mi.id;
        }
        int isDRM = 0;
        if (FeatureOption.MTK_DRM_APP) {
	        isDRM = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_DRM));
	        int drmMethod = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DRM_METHOD));
	        if (isDRM != 1 || (isDRM == 1 && drmMethod == DrmStore.DrmMethod.METHOD_FL)) {
	        	menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
	        }
        } else {
        	menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);

        // only add the 'search' menu if the selected item is music
        if (isMusic(mTrackCursor)) {
            menu.add(0, SEARCH, 0, R.string.search_title);
        }
        mCurrentAlbumName = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM));
        mCurrentArtistNameForAlbum = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST));
        mCurrentTrackName = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE));
        menu.setHeaderTitle(mCurrentTrackName);
        
        if (FeatureOption.MTK_DRM_APP && isDRM == 1) {
        	menu.add(0, DRM_INFO, 0, com.mediatek.internal.R.string.drm_protectioninfo_title);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the track
                int position = mSelectedPosition;
                int isDRM = 0;
                int drm_method = -1;
                //boolean bRightsInvalid = true;
                int rightsStatus = -1;
                Uri uri = null;
                if (FeatureOption.MTK_DRM_APP) {
        	        int oldPos = mTrackCursor.getPosition();
        	        mTrackCursor.moveToPosition(position);
        	
        	        isDRM = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_DRM));
        	        Log.d(LOGTAG, "PLAY_SELECTION: isDRM=" + isDRM);
        	        if (isDRM == 1) {
        	        	mCurTrackPos = position;
        	        	drm_method = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DRM_METHOD));
        	        	Log.d(LOGTAG, "PLAY_SELECTION: drm_method=" + drm_method);
        		        int colIdx = -1;
        		        try {
        		        	colIdx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        		        } catch (IllegalArgumentException ex) {
        		        	colIdx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        		        }
        		        uri = ContentUris.withAppendedId(
        		        		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mTrackCursor.getLong(colIdx));
        		        //bRightsInvalid = (mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY) == DrmStore.RightsStatus.RIGHTS_INVALID);
        		        rightsStatus = mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY);
        	        }
        	        Log.d(LOGTAG, "onListItemClick: rightsStatus=" + rightsStatus);
        	        mTrackCursor.moveToPosition(oldPos);
        	        
        	        if (isDRM == 1) {
        	        	if (drm_method == DrmStore.DrmMethod.METHOD_FL) {
        	    			// if FL, play directly
        	    			MusicUtils.playAll(this, mTrackCursor, position);
        	    			return true;
        	    		}
        	    		switch (rightsStatus) {
        				case DrmStore.RightsStatus.RIGHTS_VALID:
        					// check for possibility of clicking an playing/opened file
        					if (MusicUtils.sService != null) {
        						try {
        							long[] list = MusicUtils.getSongListForCursor(mTrackCursor);
        							long[] curPlaylist = MusicUtils.sService.getQueue();
        							int curPos = MusicUtils.sService.getQueuePosition();
        							Log.d(LOGTAG, "track cursor list=" + list.toString());
        							Log.d(LOGTAG, "curPlaylist=" + curPlaylist.toString());
        							Log.d(LOGTAG, "curPos=" + curPos + ", position=" + position);
        							if (Arrays.equals(list, curPlaylist)
        									&& position == curPos
        									&& !MediaPlaybackService.mTrackCompleted) {
        								// same playlist, same now-playing position
        								// do not need to show the consume dialog
        								// instead, play directly
        								MusicUtils.playAll(this, mTrackCursor, position);
        								return true;
        							}
        						} catch (RemoteException re) {
        							Log.e(LOGTAG, "RemoteException in service call!");
        						}
        					}
        					mDrmClient.showConsumeDialog(this, this, null);
        					return true;
        				case DrmStore.RightsStatus.RIGHTS_INVALID:
        					mDrmClient.showLicenseAcquisitionDialog(this, uri, null);
        					return true;
        				case DrmStore.RightsStatus.SECURE_TIMER_INVALID:
        					mDrmClient.showSecureTimerInvalidDialog(this, null, null);
        					return true;
        				default:
        					Log.e(LOGTAG, "no such rights status for current DRM file!!");
        				}
        	        }
                }
                MusicUtils.playAll(this, mTrackCursor, position);
                return true;
            }

            case QUEUE: {
                long [] list = new long[] { mSelectedId };
                MusicUtils.addToCurrentPlaylist(this, list);
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = new long[] { mSelectedId };
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this, list, playlist);
                return true;
            }

            case USE_AS_RINGTONE:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(this, mSelectedId);
                return true;

            case DELETE_ITEM: {
                long [] list = new long[1];
                list[0] = (int) mSelectedId;
                Bundle b = new Bundle();
                String f;
                if (android.os.Environment.isExternalStorageRemovable()) {
                    f = getString(R.string.delete_song_desc); 
                } else {
                    f = getString(R.string.delete_song_desc_nosdcard); 
                }
                String desc = String.format(f, mCurrentTrackName);
                b.putString("description", desc);
                b.putLongArray("items", list);
                Intent intent = new Intent();
                intent.setClass(this, DeleteItems.class);
                intent.putExtras(b);
                startActivityForResult(intent, -1);
                return true;
            }
            
            case REMOVE:
                removePlaylistItem(mSelectedPosition);
                return true;
                
            case SEARCH:
                doSearch();
                return true;
            
            case DRM_INFO:
            	if (FeatureOption.MTK_DRM_APP) {
            		mDrmClient.showProtectionInfoDialog(this, 
            				ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mSelectedId));
            	}
                return true;
        }
        return super.onContextItemSelected(item);
    }

    void doSearch() {
        CharSequence title = null;
        String query = null;
        
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        title = mCurrentTrackName;
        if (MediaStore.UNKNOWN_STRING.equals(mCurrentArtistNameForAlbum)) {
            query = mCurrentTrackName;
        } else {
            query = mCurrentArtistNameForAlbum + " " + mCurrentTrackName;
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, mCurrentArtistNameForAlbum);
        }
        if (MediaStore.UNKNOWN_STRING.equals(mCurrentAlbumName)) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentAlbumName);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "audio/*");
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    // In order to use alt-up/down as a shortcut for moving the selected item
    // in the list, we need to override dispatchKeyEvent, not onKeyDown.
    // (onKeyDown never sees these events, since they are handled by the list)
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlaylist != null && event.getMetaState() != 0 &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveItem(true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveItem(false);
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    removeItem();
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void removeItem() {
        int curcount = mTrackCursor.getCount();
        int curpos = mTrackList.getSelectedItemPosition();
        if (curcount == 0 || curpos < 0) {
            return;
        }
        
        if ("nowplaying".equals(mPlaylist)) {
            // remove track from queue

            // Work around bug 902971. To get quick visual feedback
            // of the deletion of the item, hide the selected view.
            try {
                if (curpos != MusicUtils.sService.getQueuePosition()) {
                    mDeletedOneRow = true;
                }
            } catch (RemoteException ex) {
            }
            View v = mTrackList.getSelectedView();
            v.setVisibility(View.GONE);
            mTrackList.invalidateViews();
            ((NowPlayingCursor)mTrackCursor).removeItem(curpos);
            v.setVisibility(View.VISIBLE);
            mTrackList.invalidateViews();
        } else {
            // remove track from playlist
            int colidx = mTrackCursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members._ID);
            mTrackCursor.moveToPosition(curpos);
            long id = mTrackCursor.getLong(colidx);
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                    Long.valueOf(mPlaylist));
            getContentResolver().delete(
                    ContentUris.withAppendedId(uri, id), null, null);
            curcount--;
            if (curcount == 0) {
                finish();
            } else {
                mTrackList.setSelection(curpos < curcount ? curpos : curcount);
            }
        }
    }
    
    private void moveItem(boolean up) {
        int curcount = mTrackCursor.getCount(); 
        int curpos = mTrackList.getSelectedItemPosition();
        if ( (up && curpos < 1) || (!up  && curpos >= curcount - 1)) {
            return;
        }

        if (mTrackCursor instanceof NowPlayingCursor) {
            NowPlayingCursor c = (NowPlayingCursor) mTrackCursor;
            c.moveItem(curpos, up ? curpos - 1 : curpos + 1);
            ((TrackListAdapter)getListAdapter()).notifyDataSetChanged();
            getListView().invalidateViews();
            mDeletedOneRow = true;
            if (up) {
                mTrackList.setSelection(curpos - 1);
            } else {
                mTrackList.setSelection(curpos + 1);
            }
        } else {
            int colidx = mTrackCursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members.PLAY_ORDER);
            mTrackCursor.moveToPosition(curpos);
            int currentplayidx = mTrackCursor.getInt(colidx);
            Uri baseUri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                    Long.valueOf(mPlaylist));
            ContentValues values = new ContentValues();
            String where = MediaStore.Audio.Playlists.Members._ID + "=?";
            String [] wherearg = new String[1];
            ContentResolver res = getContentResolver();
            if (up) {
                values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, currentplayidx - 1);
                wherearg[0] = mTrackCursor.getString(0);
                res.update(baseUri, values, where, wherearg);
                mTrackCursor.moveToPrevious();
            } else {
                values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, currentplayidx + 1);
                wherearg[0] = mTrackCursor.getString(0);
                res.update(baseUri, values, where, wherearg);
                mTrackCursor.moveToNext();
            }
            values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, currentplayidx);
            wherearg[0] = mTrackCursor.getString(0);
            res.update(baseUri, values, where, wherearg);
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
    	Log.d(LOGTAG, "onListItemClick");
        if (mTrackCursor.getCount() == 0) {
            return;
        }
        int isDRM = 0;
        int drm_method = -1;
        //boolean bRightsInvalid = true;
        int rightsStatus = -1;
        Uri uri = null;
        if (FeatureOption.MTK_DRM_APP) {
	        int oldPos = mTrackCursor.getPosition();
	        mTrackCursor.moveToPosition(position);
	
	        isDRM = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_DRM));
	        Log.d(LOGTAG, "onListItemClick: isDRM=" + isDRM);
	        if (isDRM == 1) {
	        	mCurTrackPos = position;
	        	drm_method = mTrackCursor.getInt(mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DRM_METHOD));
	        	Log.d(LOGTAG, "onListItemClick: drm_method=" + drm_method);
		        int colIdx = -1;
		        try {
		        	colIdx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
		        } catch (IllegalArgumentException ex) {
		        	colIdx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
		        }
		        uri = ContentUris.withAppendedId(
		        		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mTrackCursor.getLong(colIdx));
		        //bRightsInvalid = (mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY) == DrmStore.RightsStatus.RIGHTS_INVALID);
		        rightsStatus = mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY);
	        }
	        Log.d(LOGTAG, "onListItemClick: rightsStatus=" + rightsStatus);
	        mTrackCursor.moveToPosition(oldPos);
        }
        // When selecting a track from the queue, just jump there instead of
        // reloading the queue. This is both faster, and prevents accidentally
        // dropping out of party shuffle.
        if (mTrackCursor instanceof NowPlayingCursor) {
            if (MusicUtils.sService != null) {
            	if (FeatureOption.MTK_DRM_APP && isDRM == 1) {
            		if (drm_method == DrmStore.DrmMethod.METHOD_FL) {
            			// if FL, play directly
            			MusicUtils.playAll(this, mTrackCursor, position);
            			return;
            		}
            		switch (rightsStatus) {
					case DrmStore.RightsStatus.RIGHTS_VALID:
						// check for possibility of clicking an playing/opened file
						// In now-playing list, always pop up dialog, since file will always be re-opened.
						mDrmClient.showConsumeDialog(this, this, null);
						return;
					case DrmStore.RightsStatus.RIGHTS_INVALID:
						mDrmClient.showLicenseAcquisitionDialog(this, uri, null);
						return;
					case DrmStore.RightsStatus.SECURE_TIMER_INVALID:
						mDrmClient.showSecureTimerInvalidDialog(this, null, null);
						return;
					default:
					}
            		
            		//mDrmClient.showLicenseAcquisitionDialog(this, uri);
            	}            	
                try {
                    MusicUtils.sService.setQueuePosition(position);
                    return;
                } catch (RemoteException ex) {
                }
            }
        }
        
        if (FeatureOption.MTK_DRM_APP && isDRM == 1) {
        	if (drm_method == DrmStore.DrmMethod.METHOD_FL) {
    			// if FL, play directly
    			MusicUtils.playAll(this, mTrackCursor, position);
    			return;
    		}
    		switch (rightsStatus) {
			case DrmStore.RightsStatus.RIGHTS_VALID:
				// check for possibility of clicking an playing/opened file
				if (MusicUtils.sService != null) {
					try {
						long[] list = MusicUtils.getSongListForCursor(mTrackCursor);
						long[] curPlaylist = MusicUtils.sService.getQueue();
						int curPos = MusicUtils.sService.getQueuePosition();
						Log.d(LOGTAG, "track cursor list=" + list.toString());
						Log.d(LOGTAG, "curPlaylist=" + curPlaylist.toString());
						Log.d(LOGTAG, "curPos=" + curPos + ", position=" + position);
						if (Arrays.equals(list, curPlaylist)
								&& position == curPos
								&& !MediaPlaybackService.mTrackCompleted) {
							// same playlist, same now-playing position
							// do not need to show the consume dialog
							// instead, play directly
							MusicUtils.playAll(this, mTrackCursor, position);
							return;
						}
					} catch (RemoteException re) {
						Log.e(LOGTAG, "RemoteException in service call!");
					}
				}
				mDrmClient.showConsumeDialog(this, this, null);
				return;
			case DrmStore.RightsStatus.RIGHTS_INVALID:
				mDrmClient.showLicenseAcquisitionDialog(this, uri, null);
				return;
			case DrmStore.RightsStatus.SECURE_TIMER_INVALID:
				mDrmClient.showSecureTimerInvalidDialog(this, null, null);
				return;
			default:
			}
        	//MusicUtils.playAllInBackground(this, mTrackCursor, position);
        } else {
        	MusicUtils.playAll(this, mTrackCursor, position);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* This activity is used for a number of different browsing modes, and the menu can
         * be different for each of them:
         * - all tracks, optionally restricted to an album, artist or playlist
         * - the list of currently playing songs
         */
        super.onCreateOptionsMenu(menu);
        if (mPlaylist == null) {
            menu.add(0, PLAY_ALL, 0, R.string.play_all).setIcon(R.drawable.ic_menu_play_clip);
        }
        menu.add(0, PARTY_SHUFFLE, 0, R.string.party_shuffle); // icon will be set in onPrepareOptionsMenu()
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all).setIcon(R.drawable.ic_menu_shuffle);
        if (mPlaylist != null) {
            menu.add(0, SAVE_AS_PLAYLIST, 0, R.string.save_as_playlist).setIcon(android.R.drawable.ic_menu_save);
            if (mPlaylist.equals("nowplaying")) {
                menu.add(0, CLEAR_PLAYLIST, 0, R.string.clear_playlist).setIcon(R.drawable.ic_menu_clear_playlist);
            }
        }
        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (getPackageManager().resolveActivity(i, 0) != null) {
            menu.add(0, EFFECTS_PANEL, 0, R.string.effects_list_title).setIcon(R.drawable.ic_menu_eq);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MusicUtils.setPartyShuffleMenuIcon(menu);
        super.onPrepareOptionsMenu(menu);
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
        	return false;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        Cursor cursor;
        switch (item.getItemId()) {
            case PLAY_ALL: {
                MusicUtils.playAll(this, mTrackCursor);
                return true;
            }

            case PARTY_SHUFFLE:
                MusicUtils.togglePartyShuffle();
                break;
                
            case SHUFFLE_ALL:
                // Should 'shuffle all' shuffle ALL, or only the tracks shown?
                cursor = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String [] { MediaStore.Audio.Media._ID}, 
                        MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
                if (cursor != null) {
                    MusicUtils.shuffleAll(this, cursor);
                    cursor.close();
                }
                return true;
                
            case SAVE_AS_PLAYLIST:
                intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, SAVE_AS_PLAYLIST);
                return true;
                
            case CLEAR_PLAYLIST:
                // We only clear the current playlist
                MusicUtils.clearQueue();
                return true;
            case EFFECTS_PANEL:
                try {
                    Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, myService.getAudioSessionId());
                    startActivityForResult(i, EFFECTS_PANEL);
                } catch (RemoteException re) {
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                } else {
                    getTrackCursor(mAdapter.getQueryHandler(), null, true);
                }
                break;
                
            case NEW_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = new long[] { mSelectedId };
                        MusicUtils.addToPlaylist(this, list, Integer.valueOf(uri.getLastPathSegment()));
                    }
                }
                break;

            case SAVE_AS_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = MusicUtils.getSongListForCursor(mTrackCursor);
                        int plid = Integer.parseInt(uri.getLastPathSegment());
                        MusicUtils.addToPlaylist(this, list, plid);
                    }
                }
                break;
        }
    }
    
    private Cursor getTrackCursor(TrackListAdapter.TrackQueryHandler queryhandler, String filter,
            boolean async) {

        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }

        Cursor ret = null;
        mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");

        if (mGenre != null) {
            Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external",
                    Integer.valueOf(mGenre));
            if (!TextUtils.isEmpty(filter)) {
                uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
            }
            mSortOrder = MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER;
            ret = queryhandler.doQuery(uri,
                    mCursorCols, where.toString(), null, mSortOrder, async);
        } else if (mPlaylist != null) {
            if (mPlaylist.equals("nowplaying")) {
                if (MusicUtils.sService != null) {
                    ret = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
                    if (ret.getCount() == 0) {
                        finish();
                    }
                } else {
                    // Nothing is playing.
                }
            } else if (mPlaylist.equals("podcasts")) {
                where.append(" AND " + MediaStore.Audio.Media.IS_PODCAST + "=1");
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                if (!TextUtils.isEmpty(filter)) {
                    uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
                }
                ret = queryhandler.doQuery(uri,
                        mCursorCols, where.toString(), null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER, async);
            } else if (mPlaylist.equals("recentlyadded")) {
                // do a query for all songs added in the last X weeks
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                if (!TextUtils.isEmpty(filter)) {
                    uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
                }
                int X = MusicUtils.getIntPref(this, "numweeks", 2) * (3600 * 24 * 7);
                where.append(" AND " + MediaStore.MediaColumns.DATE_ADDED + ">");
                where.append(System.currentTimeMillis() / 1000 - X);
                ret = queryhandler.doQuery(uri,
                        mCursorCols, where.toString(), null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER, async);
            } else {
                Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                        Long.valueOf(mPlaylist));
                if (!TextUtils.isEmpty(filter)) {
                    uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
                }
                mSortOrder = MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER;
                ret = queryhandler.doQuery(uri, mPlaylistMemberCols,
                        where.toString(), null, mSortOrder, async);
            }
        } else {
            if (mAlbumId != null) {
                where.append(" AND " + MediaStore.Audio.Media.ALBUM_ID + "=" + mAlbumId);
                mSortOrder = MediaStore.Audio.Media.TRACK + ", " + mSortOrder;
            }
            if (mArtistId != null) {
                where.append(" AND " + MediaStore.Audio.Media.ARTIST_ID + "=" + mArtistId);
            }
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            if (!TextUtils.isEmpty(filter)) {
                uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
            }
            ret = queryhandler.doQuery(uri,
                    mCursorCols, where.toString() , null, mSortOrder, async);
        }
        
        // This special case is for the "nowplaying" cursor, which cannot be handled
        // asynchronously using AsyncQueryHandler, so we do some extra initialization here.
        if (ret != null && async) {
            init(ret, false);
            setTitle();
        }
        return ret;
    }

    private class NowPlayingCursor extends AbstractCursor
    {
        public NowPlayingCursor(IMediaPlaybackService service, String [] cols)
        {
            mCols = cols;
            mService  = service;
            makeNowPlayingCursor();
        }
        private void makeNowPlayingCursor() {
            if (mCurrentPlaylistCursor != null) {
                mCurrentPlaylistCursor.close();
            }
            mCurrentPlaylistCursor = null;
            try {
                mNowPlaying = mService.getQueue();
            } catch (RemoteException ex) {
                mNowPlaying = new long[0];
            }
            mSize = mNowPlaying.length;
            if (mSize == 0) {
                return;
            }

            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media._ID + " IN (");
            for (int i = 0; i < mSize; i++) {
                where.append(mNowPlaying[i]);
                if (i < mSize - 1) {
                    where.append(",");
                }
            }
            where.append(")");

            mCurrentPlaylistCursor = MusicUtils.query(TrackBrowserActivity.this,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCols, where.toString(), null, MediaStore.Audio.Media._ID);

            if (mCurrentPlaylistCursor == null) {
                mSize = 0;
                return;
            }
            
            int size = mCurrentPlaylistCursor.getCount();
            mCursorIdxs = new long[size];
            mCurrentPlaylistCursor.moveToFirst();
            int colidx = mCurrentPlaylistCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            for (int i = 0; i < size; i++) {
                mCursorIdxs[i] = mCurrentPlaylistCursor.getLong(colidx);
                mCurrentPlaylistCursor.moveToNext();
            }
            mCurrentPlaylistCursor.moveToFirst();
            mCurPos = -1;
            
            // At this point we can verify the 'now playing' list we got
            // earlier to make sure that all the items in there still exist
            // in the database, and remove those that aren't. This way we
            // don't get any blank items in the list.
            try {
                int removed = 0;
                for (int i = mNowPlaying.length - 1; i >= 0; i--) {
                    long trackid = mNowPlaying[i];
                    int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
                    if (crsridx < 0) {
                        //Log.i("@@@@@", "item no longer exists in db: " + trackid);
                        removed += mService.removeTrack(trackid);
                    }
                }
                if (removed > 0) {
                    mNowPlaying = mService.getQueue();
                    mSize = mNowPlaying.length;
                    if (mSize == 0) {
                        mCursorIdxs = null;
                        return;
                    }
                }
            } catch (RemoteException ex) {
                mNowPlaying = new long[0];
            }
        }

        @Override
        public int getCount()
        {
            return mSize;
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition)
        {
            if (oldPosition == newPosition)
                return true;
            
            if (mNowPlaying == null || mCursorIdxs == null || newPosition >= mNowPlaying.length) {
                return false;
            }

            // The cursor doesn't have any duplicates in it, and is not ordered
            // in queue-order, so we need to figure out where in the cursor we
            // should be.
           
	    //added by zhili.chen@archermind.com
	    if (newPosition >= mNowPlaying.length) {
	        return false;
	    } 
	    //added by zhili.chen@archermind.com end
            long newid = mNowPlaying[newPosition];
            int crsridx = Arrays.binarySearch(mCursorIdxs, newid);
            mCurrentPlaylistCursor.moveToPosition(crsridx);
            mCurPos = newPosition;
            
            return true;
        }

        public boolean removeItem(int which)
        {
            try {
                if (mService.removeTracks(which, which) == 0) {
                    return false; // delete failed
                }
                int i = (int) which;
                mSize--;
                while (i < mSize) {
                    mNowPlaying[i] = mNowPlaying[i+1];
                    i++;
                }
                onMove(-1, (int) mCurPos);
            } catch (RemoteException ex) {
            }
            return true;
        }
        
        public void moveItem(int from, int to) {
            try {
                mService.moveQueueItem(from, to);
                mNowPlaying = mService.getQueue();
                onMove(-1, mCurPos); // update the underlying cursor
            } catch (RemoteException ex) {
            }
        }

        private void dump() {
            String where = "(";
            for (int i = 0; i < mSize; i++) {
                where += mNowPlaying[i];
                if (i < mSize - 1) {
                    where += ",";
                }
            }
            where += ")";
            Log.i("NowPlayingCursor: ", where);
        }

        @Override
        public String getString(int column)
        {
            try {
                return mCurrentPlaylistCursor.getString(column);
            } catch (Exception ex) {
                onChange(true);
                return "";
            }
        }

        @Override
        public short getShort(int column)
        {
            return mCurrentPlaylistCursor.getShort(column);
        }

        @Override
        public int getInt(int column)
        {
            try {
                return mCurrentPlaylistCursor.getInt(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public long getLong(int column)
        {
            try {
                return mCurrentPlaylistCursor.getLong(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public float getFloat(int column)
        {
            return mCurrentPlaylistCursor.getFloat(column);
        }

        @Override
        public double getDouble(int column)
        {
            return mCurrentPlaylistCursor.getDouble(column);
        }

        @Override
        public boolean isNull(int column)
        {
            return mCurrentPlaylistCursor.isNull(column);
        }

        @Override
        public String[] getColumnNames()
        {
            return mCols;
        }
        
        @Override
        public void deactivate()
        {
            if (mCurrentPlaylistCursor != null)
                mCurrentPlaylistCursor.deactivate();
        }

        @Override
        public void close() {
            super.close();
            if (mCurrentPlaylistCursor != null) {
                mCurrentPlaylistCursor.close();
                mCurrentPlaylistCursor = null;
            }
        }
        @Override
        public boolean requery()
        {
            makeNowPlayingCursor();
            return true;
        }

        private String [] mCols;
        private Cursor mCurrentPlaylistCursor;     // updated in onMove
        private int mSize;          // size of the queue
        private long[] mNowPlaying;
        private long[] mCursorIdxs;
        private int mCurPos;
        private IMediaPlaybackService mService;
    }
    
    static class TrackListAdapter extends SimpleCursorAdapter implements SectionIndexer {
        boolean mIsNowPlaying;
        boolean mDisableNowPlayingIndicator;

        int mTitleIdx;
        int mArtistIdx;
        int mDurationIdx;
        int mAudioIdIdx;
        
        int mIsDRMIdx = -1;
        int mDrmMethodIdx = -1;

        private final StringBuilder mBuilder = new StringBuilder();
        private final String mUnknownArtist;
        private final String mUnknownAlbum;
        
        private AlphabetIndexer mIndexer;
        
        private TrackBrowserActivity mActivity = null;
        private TrackQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;
        
        private DrmManagerClient mDrmClient_adapter = null;
        
        static class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView drm_lock;
            ImageView play_indicator;
            CharArrayBuffer buffer1;
            char [] buffer2;
        }

        class TrackQueryHandler extends AsyncQueryHandler {

            class QueryArgs {
                public Uri uri;
                public String [] projection;
                public String selection;
                public String [] selectionArgs;
                public String orderBy;
            }

            TrackQueryHandler(ContentResolver res) {
                super(res);
            }
            
            public Cursor doQuery(Uri uri, String[] projection,
                    String selection, String[] selectionArgs,
                    String orderBy, boolean async) {
                if (async) {
                    // Get 100 results first, which is enough to allow the user to start scrolling,
                    // while still being very fast.
                    Uri limituri = uri.buildUpon().appendQueryParameter("limit", "100").build();
                    QueryArgs args = new QueryArgs();
                    args.uri = uri;
                    args.projection = projection;
                    args.selection = selection;
                    args.selectionArgs = selectionArgs;
                    args.orderBy = orderBy;

                    startQuery(0, args, limituri, projection, selection, selectionArgs, orderBy);
                    return null;
                }
                return MusicUtils.query(mActivity,
                        uri, projection, selection, selectionArgs, orderBy);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                mActivity.init(cursor, cookie != null);
                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }
        }
        
        TrackListAdapter(Context context, TrackBrowserActivity currentactivity,
                int layout, Cursor cursor, String[] from, int[] to,
                boolean isnowplaying, boolean disablenowplayingindicator) {
            super(context, layout, cursor, from, to);
            mActivity = currentactivity;
            getColumnIndices(cursor);
            mIsNowPlaying = isnowplaying;
            mDisableNowPlayingIndicator = disablenowplayingindicator;
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
            mUnknownAlbum = context.getString(R.string.unknown_album_name);
            
            mQueryHandler = new TrackQueryHandler(context.getContentResolver());
            if (FeatureOption.MTK_DRM_APP) {
            	mDrmClient_adapter = currentactivity.mDrmClient;
            }
        }
        
        public void setActivity(TrackBrowserActivity newactivity) {
            mActivity = newactivity;
            if (FeatureOption.MTK_DRM_APP) {
            	mDrmClient_adapter = newactivity.mDrmClient;
            }
        }
        
        public TrackQueryHandler getQueryHandler() {
            return mQueryHandler;
        }
        
        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                if (FeatureOption.MTK_DRM_APP) {
                	mIsDRMIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_DRM);
                	mDrmMethodIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DRM_METHOD);
                }
                try {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }
                
                if (mIndexer != null) {
                    mIndexer.setCursor(cursor);
                } else if (!mActivity.mEditMode && mActivity.mAlbumId == null) {
                    String alpha = mActivity.getString(R.string.fast_scroll_alphabet);
                
                    mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx, alpha);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ImageView iv = (ImageView) v.findViewById(R.id.icon);
            iv.setVisibility(View.GONE);
            
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.drm_lock = (ImageView) v.findViewById(R.id.drm_lock);
            
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            
            ViewHolder vh = (ViewHolder) view.getTag();
            
            cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
            
          	int secs = cursor.getInt(mDurationIdx);
            if (secs == 0) {
            	vh.duration.setText("");
            } else {
            	vh.duration.setText(MusicUtils.makeTimeString(context, secs / 1000));
            }
            
            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(mArtistIdx);
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                builder.append(mUnknownArtist);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            ImageView iv = vh.play_indicator;
            long id = -1;
            if (MusicUtils.sService != null) {
                // TODO: IPC call on each bind??
                try {
                    if (mIsNowPlaying) {
                        id = MusicUtils.sService.getQueuePosition();
                    } else {
                        id = MusicUtils.sService.getAudioId();
                    }
                } catch (RemoteException ex) {
                }
            }
            
            // Determining whether and where to show the "now playing indicator
            // is tricky, because we don't actually keep track of where the songs
            // in the current playlist came from after they've started playing.
            //
            // If the "current playlists" is shown, then we can simply match by position,
            // otherwise, we need to match by id. Match-by-id gets a little weird if
            // a song appears in a playlist more than once, and you're in edit-playlist
            // mode. In that case, both items will have the "now playing" indicator.
            // For this reason, we don't show the play indicator at all when in edit
            // playlist mode (except when you're viewing the "current playlist",
            // which is not really a playlist)
            if ( (mIsNowPlaying && cursor.getPosition() == id) ||
                 (!mIsNowPlaying && !mDisableNowPlayingIndicator && cursor.getLong(mAudioIdIdx) == id)) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
            
            ImageView iv_drm = vh.drm_lock;
            if (FeatureOption.MTK_DRM_APP) {
	            int isDRM = cursor.getInt(mIsDRMIdx);
	            int drmMethod = cursor.getInt(mDrmMethodIdx);
	            if (isDRM == 1) {
	            	int colIdx = -1;
	            	try {
	            		colIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
	            	} catch (IllegalArgumentException iae) {
	            		colIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
	            	}
	            	Uri uri = ContentUris.withAppendedId(
	            			MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(colIdx));
	            	try {
	            		if (drmMethod != DrmStore.DrmMethod.METHOD_FL) {
	            			if (mDrmClient_adapter.checkRightsStatus(uri, DrmStore.Action.PLAY) == DrmStore.RightsStatus.RIGHTS_VALID) {
			            		iv_drm.setImageResource(com.mediatek.internal.R.drawable.drm_green_lock);
			            	} else {
			            		iv_drm.setImageResource(com.mediatek.internal.R.drawable.drm_red_lock);
			            	}
			            	iv_drm.setVisibility(View.VISIBLE);
	            		} else {
	            			iv_drm.setVisibility(View.GONE);
	            		}
		            } catch (Exception ex) {
		            	Log.e(LOGTAG, "bindView: " + ex);
		            	iv_drm.setVisibility(View.GONE);
		            }
	            } else {
	            	iv_drm.setVisibility(View.GONE);
	            }
            } else {
            	iv_drm.setVisibility(View.GONE);
            }
        }
        
        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mTrackCursor) {
                mActivity.mTrackCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }
        
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid && (
                    (s == null && mConstraint == null) ||
                    (s != null && s.equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mActivity.getTrackCursor(mQueryHandler, s, false);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
        
        // SectionIndexer methods
        
        public Object[] getSections() {
            if (mIndexer != null) { 
                return mIndexer.getSections();
            } else {
                return null;
            }
        }
        
        public int getPositionForSection(int section) {
            int pos = mIndexer.getPositionForSection(section);
            return pos;
        }
        
        public int getSectionForPosition(int position) {
            return 0;
        }        
    }
    
    private class AlbumArtFetcher extends AsyncTask<Long, Void, Bitmap> {
    	protected Bitmap doInBackground(Long... albumId) {
    		Bitmap bm = null;
    		try {
    			long id = albumId[0].longValue();
    			bm = MusicUtils.getArtwork(TrackBrowserActivity.this, -1, id, false);
    		} catch (Exception ex) {
    			Log.e(LOGTAG, "AlbumArtFetched called with wrong parameters");
    			return null;
    		}
    		Log.d(LOGTAG, "AlbumArtFetcher: getArtwork returns " + bm);
            return bm;
    	}
    	
    	protected void onPostExecute(Bitmap bm) {
    		if (bm != null) {
    			MusicUtils.setBackground(mTrackList, bm);
    			mTrackList.setCacheColorHint(0);
    			return;
    		}
    		//mTrackList.setBackgroundColor(0xff000000);
    		mTrackList.setBackgroundResource(R.drawable.bg01);//by MengÃÊªª…œ“ª––
    		mTrackList.setCacheColorHint(0);
    	}
    }
    
    public void onClick(DialogInterface dialog, int which) {
    	if (which == DialogInterface.BUTTON_POSITIVE) {
    		if (mTrackCursor instanceof NowPlayingCursor) {
    			if (MusicUtils.sService != null) {
    				try {
    					MusicUtils.sService.setQueuePosition(mCurTrackPos);
    					return;
    				} catch (RemoteException ex) {
    					Log.e(LOGTAG, "RemoteException when setQueuePosition: ", ex);
    				}
    			}
    		} else {
    			MusicUtils.playAll(this, mTrackCursor, mCurTrackPos);
    		}
    	} else if (which == DialogInterface.BUTTON_NEGATIVE) {
    		// do nothing.
    	}
    }
}

