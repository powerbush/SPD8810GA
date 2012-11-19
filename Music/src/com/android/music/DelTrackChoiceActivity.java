
package com.android.music;

import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This activity provides a list view for all of the music .
 */
public class DelTrackChoiceActivity extends ListActivity implements
		OnItemClickListener {
	
	private static final String LOGTAG = "MultiTrackChoiceActivity";
    private String mSortOrder;
    private String[] mCursorCols;
    
    private boolean mAdapterSent = false;
    private TrackListAdapter mAdapter;
    private Cursor mTrackCursor;
    
    private CheckBox all;
    private Button btnAdd;
    private Button btnCancel;
    private Button btnCheckAll;
    private TextView  checkBoxTitle;
//    private LinearLayout linear;
//    private ListView lv;
    private RelativeLayout rl;
    
    private String mPlaylist;

    @Override
    protected void onCreate(Bundle icicle) {
    	Log.d(LOGTAG,"Activity create start");
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Intent intent = getIntent();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
		if (icicle != null) {
			mPlaylist = icicle.getString("playlist");
		} else {
			mPlaylist = intent.getStringExtra("playlist");
		}
        setContentView(R.layout.del_track_choice);
        
		mAdapter = (TrackListAdapter) getLastNonConfigurationInstance();

		if (mAdapter != null) {
			mAdapter.setActivity(this);
			setListAdapter(mAdapter);
		}

		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		f.addDataScheme("file");
		registerReceiver(mScanListener, f);
		
		 if (mAdapter == null) {
	            mAdapter = new TrackListAdapter(
	                    getApplication(), // need to use application context to avoid leaks
	                    this,
	                    null);
	            setListAdapter(mAdapter);
	            getTrackCursor(mAdapter.getQueryHandler());
	        } 
        
        mCursorCols = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };
        initializeViews();
        Log.d(LOGTAG,"Activity create end");
    }
    
    /**
     * Initialize all the controls.
     */
    private void initializeViews() {
    	 all = (CheckBox) findViewById(R.id.checkbox_selected_all);
         all.setOnClickListener(new OnClickListener() {
             public void onClick(View v) {
                 for (int i = 0; i < mAdapter.getCount(); i++) {
                     mAdapter.setChecked(i, all.isChecked());
                 }
                 mAdapter.notifyDataSetChanged();
             }
         });
         btnCheckAll = (Button) findViewById(R.id.btn_checkall);
         btnCheckAll.setOnClickListener(new OnClickListener() {
             public void onClick(View v) {
                 all.setChecked(!all.isChecked());
                 for (int i = 0; i < mAdapter.getCount(); i++) {
                     mAdapter.setChecked(i, all.isChecked());
                 }
                 mAdapter.notifyDataSetChanged();
             }
         });
        
        btnAdd = (Button) findViewById(R.id.AddButton);
 		btnAdd.setOnClickListener(new OnClickListener() {
 			public void onClick(View v) {
 				long[] ids = mAdapter.getCheckedIdArray();
 				if (mPlaylist != null) {
 					if (mPlaylist.equals("nowplaying")) {
// 						MusicUtils.addToCurrentPlaylist(
// 								DelTrackChoiceActivity.this, ids);
 					} else {
 					    for (int i = 0; i < ids.length; i++) {
 					        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
 					                Long.valueOf(mPlaylist));
 					        getContentResolver().delete(
 					                ContentUris.withAppendedId(uri, ids[i]), null, null);
                        }
 					}
 					Intent intent = new Intent(Intent.ACTION_EDIT);
 		            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/tracklist");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
 		            intent.putExtra("playlist", mPlaylist);
 		            startActivity(intent);
 					finish();
 				}
 			}
 		});
         btnCancel = (Button) findViewById(R.id.CancelButton);
         btnCancel.setOnClickListener(new OnClickListener() {
 			public void onClick(View v) {
 				finish();
 			}
 		});
         
        checkBoxTitle = (TextView) findViewById(R.id.CheckBoxTitle);
//        linear = (LinearLayout) findViewById(R.id.ButtonLinearLayout);
//        lv=(ListView)findViewById(android.R.id.list);
        rl=(RelativeLayout)findViewById(R.id.CheckboxLinearLayout);  
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        TrackListAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }
    
    @Override
    public void onResume() {
    	Log.d(LOGTAG,"Activity resumed start");
        super.onResume();
        if (mTrackCursor != null) {
            getListView().invalidateViews();
        }
        MusicUtils.setSpinnerState(this);
        Log.d(LOGTAG,"Activity resumed end");
    }
    
    @Override
    public void onPause() {
    	Log.d(LOGTAG,"Activity pause start");
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
        Log.d(LOGTAG,"Activity pause end");
    }
    
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("playlist", mPlaylist);
        super.onSaveInstanceState(outcicle);
    }
    
    @Override
    public void onDestroy() {
    	Log.d(LOGTAG,"Activity destroy start");
        
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
        Log.d(LOGTAG,"Activity destroy end");
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
    
    private void getTrackCursor(TrackListAdapter.TrackQueryHandler queryhandler) {
        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }
		mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
		StringBuilder where = new StringBuilder();
		where.append(MediaStore.Audio.Media.TITLE + " != ''");
		where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
//		Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",Long.parseLong(mPlaylist));

		queryhandler.doQuery(uri, mCursorCols, where.toString(), null,
				mSortOrder);
    }
    
    /*
     * This listener gets called when the media scanner starts up or finishes, and
     * when the sd card is unmounted.
     */
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action) ||
                    Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                MusicUtils.setSpinnerState(DelTrackChoiceActivity.this);
            }
            mReScanHandler.sendEmptyMessage(0);
        }
    };
    
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getTrackCursor(mAdapter.getQueryHandler());
            }
        }
    };
   
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mAdapter.setChecked(position, !mAdapter.isChecked(position));
        if (mAdapter.getCheckedCount() == mAdapter.getCount()) {
            all.setChecked(true);
        } else {
            all.setChecked(false);
        }
        mAdapter.notifyDataSetChanged();
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mAdapter.setChecked(position, !mAdapter.isChecked(position));
		if (mAdapter.getCheckedCount() == mAdapter.getCount()) {
			all.setChecked(true);
		} else {
			all.setChecked(false);
		}
		mAdapter.notifyDataSetChanged();
	}
    
    public void init(Cursor newCursor, boolean isLimited) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor
        if (mTrackCursor == null) {
        	//MusicUtils.displayDatabaseError(MultiTrackChoiceActivity.this);
            closeContextMenu();
            all.setVisibility(View.GONE);
			btnCheckAll.setVisibility(View.GONE);
			//linear.setVisibility(View.GONE);
			btnAdd.setVisibility(View.GONE);
			btnCancel.setVisibility(View.GONE);
			checkBoxTitle.setVisibility(View.GONE);
//			lv.setVisibility(View.GONE);
			rl.setVisibility(View.GONE);
			  mReScanHandler.sendEmptyMessageDelayed(0, 1000);
		} else {
        	MusicUtils.hideDatabaseError(DelTrackChoiceActivity.this);
			all.setVisibility(View.VISIBLE);
			btnCheckAll.setVisibility(View.VISIBLE);
			//linear.setVisibility(View.VISIBLE);
			btnAdd.setVisibility(View.VISIBLE);
			btnCancel.setVisibility(View.VISIBLE);
			checkBoxTitle.setVisibility(View.VISIBLE);
//			lv.setVisibility(View.VISIBLE);
			rl.setVisibility(View.VISIBLE);
		}

    }
    static class TrackListAdapter extends CursorAdapter {

        int mTitleIdx;
        int mArtistIdx;
        int mDurationIdx;
        int mAudioIdIdx;

        private final StringBuilder mBuilder = new StringBuilder();
        private final String mUnknownArtist;
        private LayoutInflater mInflater;


        
        private DelTrackChoiceActivity mActivity = null;
        private TrackQueryHandler mQueryHandler;
        
        static class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            CheckBox checkbox;
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
            
            public void doQuery(Uri uri, String[] projection,
                    String selection, String[] selectionArgs,
                    String orderBy) {
          
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
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                mActivity.init(cursor, cookie != null);
                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }
        }
        
		TrackListAdapter(Context context,
				DelTrackChoiceActivity currentactivity, Cursor cursor) {
			super(context, cursor);
			mInflater = LayoutInflater.from(context);
			mActivity = currentactivity;
			getColumnIndices(cursor);
			mUnknownArtist = context.getString(R.string.unknown_artist_name);
			mQueryHandler = new TrackQueryHandler(context.getContentResolver());
		}
        
        public void setActivity(DelTrackChoiceActivity newactivity) {
            mActivity = newactivity;
        }
        
        public TrackQueryHandler getQueryHandler() {
            return mQueryHandler;
        }
        
        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                try {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }
                
            }
        }
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return null;
        }
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
        	return;
        }
        
        public View getView(final int position, View convertView, ViewGroup parent) {
        	 ViewHolder vh = new ViewHolder();
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.multi_track_choice_list, null);
            }
            vh.line1 = (TextView) convertView.findViewById(R.id.line1);
            vh.line2 = (TextView) convertView.findViewById(R.id.line2);
            vh.duration = (TextView) convertView.findViewById(R.id.duration);
            vh.checkbox = (CheckBox) convertView.findViewById(R.id.music_checkbox_selected);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            
            final Cursor cursor = (Cursor) getItem(position);
            cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
            
            int secs = cursor.getInt(mDurationIdx) / 1000;
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(MusicUtils.makeTimeString(convertView.getContext(), secs));
            }     
            vh.checkbox.setVisibility(View.VISIBLE);
            vh.checkbox.setChecked(isChecked(position));
            vh.checkbox.setClickable(false);
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
            convertView.setTag(getItemId(position));
            return convertView;
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
        private SparseBooleanArray mCheckArray = new SparseBooleanArray();
        private int mCheckCount = 0;
        
        public long[] getCheckedIdArray(){
            long[] ids = new long[mCheckCount];
            int pos = 0;
            for (int i = getCount() - 1; i >= 0; i--) {
                if (isChecked(i)) {
                    ids[pos++] = getItemId(i);
                }
            }
            return ids;
        }

        public boolean hasCheckedItem() {
            return mCheckCount > 0;
        }

        public boolean isChecked(int position){
            int id = (int) getItemId(position);
            return mCheckArray.get(id);
        }

        public int getCheckedCount(){
            return mCheckCount;
        }

        public void setChecked(int position, boolean checked){
            int id = (int) getItemId(position);
            boolean checked_before = isChecked(position);
            if (checked_before != checked) {
                mCheckCount += checked ? 1 : -1;
            }
            mCheckArray.put(id, checked);
        }

    }
}
