package com.android.music.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.android.music.*;
import android.os.SystemClock;
public class FileManager extends Activity implements View.OnClickListener,
        AdapterView.OnItemClickListener {

    private static final String TAG = "FileManager";
    private List<FileInfo> files = new ArrayList<FileInfo>();
    private String rootPath = FileUtil.getSDPath();
    private String currentPath = rootPath;
    private PopupWindow popup = null;
    private ImageButton backImageButton;
    private TextView filePath;
    private RelativeLayout fileslayout;
    private boolean isListMode = true;
    private BaseAdapter adapter = null;
    private String mPlaylist;
    private String fileAbsolutePath;
    static private final HashMap<Context, MediaScannerConnection> mConnectionMap = new HashMap<Context, MediaScannerConnection>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        Intent intent = getIntent();
		if (savedInstanceState != null) {
			mPlaylist = savedInstanceState.getString("playlist");
		} else {
			mPlaylist = intent.getStringExtra("playlist");
		}
		
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_EJECT);
		f.addDataScheme("file");
		registerReceiver(mExternalStorageListener, f);
        fileslayout = (RelativeLayout) findViewById(R.id.files_layout);
        backImageButton =(ImageButton) findViewById(R.id.back);
        backImageButton.setOnClickListener(this);
        filePath = (TextView) findViewById(R.id.file_path);
        if (isListMode) {
            View v = getLayoutInflater().inflate(R.layout.list_file,
                    fileslayout);
            adapter = new FileListAdapter(this, files);
            ListView listView = (ListView) v.findViewById(R.id.list_files);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(this);
        }
        else {
            View v = getLayoutInflater().inflate(R.layout.grid_file,
                    fileslayout);
            adapter = new FileGridAdapter(this, files);
            GridView gridView = (GridView) v.findViewById(R.id.grid_files);
            gridView.setAdapter(adapter);
            gridView.setOnItemClickListener(this);
        }
        viewFiles(currentPath);

    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString("playlist", mPlaylist);
        super.onSaveInstanceState(outcicle);
    }
    
    @Override
    protected void onDestroy() {
    	unregisterReceiver(mExternalStorageListener);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backUp();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onClick(View v) {
        Log.d(TAG, " onClick");
        switch (v.getId()) {
            case R.id.back:
                Log.d(TAG, "back onClick");
                backUp();
                break;
            default:
                break;
        }

    }
    
    private Handler addToPlaylistHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	Log.d("mydebug","addToPlaylistHandler");
        	long songId = MusicUtils.getSongIdForData(FileManager.this, fileAbsolutePath);
			long[] ids = new long[] { songId };
			if (mPlaylist != null) {
				if (mPlaylist.equals("nowplaying")) {
					MusicUtils.addToCurrentPlaylist(FileManager.this, ids);
				} else {
					long playlist = Long.valueOf(mPlaylist);
					MusicUtils.addToPlaylist(FileManager.this, ids, playlist);
				}
			}
        }
    };
    
    private BroadcastReceiver mExternalStorageListener = new BroadcastReceiver() {
        @Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_MEDIA_EJECT)) {
				finish();
			}
		}
    };
    
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        FileInfo f = files.get(position);

        if (f.IsDirectory) {
            viewFiles(f.Path);
        }
        else {
        	addToPlaylist(f.Path);
        }

    }

	private void addToPlaylist(final String path) {
		Log.d(TAG, "path is :" + path);
		if (path == null) {
			return;
		}
		final String newPath = path.replace("'", "''");
		Log.d(TAG, "newpath is :" + newPath);
		long songId = MusicUtils.getSongIdForData(FileManager.this, newPath);
		if (songId == -1) {
			addToPlaylistHandler.post(new Runnable() {
				public void run() {
					scanFile(newPath);
				}
			});
		} else {
			long[] ids = new long[] { songId };
			if (mPlaylist != null) {
				if (mPlaylist.equals("nowplaying")) {
					MusicUtils.addToCurrentPlaylist(FileManager.this, ids);
				} else {
					long playlist = Long.valueOf(mPlaylist);
					MusicUtils.addToPlaylist(FileManager.this, ids, playlist);
				}
			}
		}
	}
    
	private void scanFile(String path) {
		this.fileAbsolutePath = path;
		MediaScannerConnection connection = new MediaScannerConnection(FileManager.this, client);
		mConnectionMap.put(FileManager.this, connection);
		connection.connect();
	}
	
	private MediaScannerConnection.MediaScannerConnectionClient client = new MediaScannerConnection.MediaScannerConnectionClient() {
		public void onMediaScannerConnected() {
			MediaScannerConnection connection = mConnectionMap.get(FileManager.this);  
			if (connection != null) {
				try {
					if (fileAbsolutePath != null) {
						Log.d(TAG, "start to scan a file and file path is"+fileAbsolutePath);
						connection.scanFile(fileAbsolutePath, null);
					} else {
						Log.d(TAG, "file path is null");
						disconnect();
					}
				} catch (Exception e) {
					Log.d(TAG, "exception in the progress of scanning");
					disconnect();
				}
			}
		}

		public void onScanCompleted(String path, Uri uri) {
			Log.d(TAG, "file scan completed");
			addToPlaylistHandler.removeCallbacksAndMessages(null);
			Message msg = addToPlaylistHandler.obtainMessage();
			addToPlaylistHandler.sendMessageDelayed(msg, 500);	
			disconnect();
		}
		
		public void disconnect() {
			MediaScannerConnection connection = mConnectionMap.get(FileManager.this);
			if (connection != null) {
				connection.disconnect();
				mConnectionMap.put(FileManager.this, null);
			}
		}
	};
	
    
    private void backUp() {
        File f = new File(currentPath);  
        String parentPath = f.getParent();
        if (parentPath != null && !parentPath.equals("/mnt") && !parentPath.equals("/")) {
            viewFiles(parentPath);
        }
        else {
            finish();
        }
    }

    private void viewFiles(String path) {
        ArrayList<FileInfo> tmp = FileUtil.getFiles(this, path);
        if (tmp != null) {

            files.clear();
            files.addAll(tmp);
            tmp.clear();

            currentPath = path;
            filePath.setText(path);
            adapter.notifyDataSetChanged();
        }
    }

//	private void openFile(String path) {
//		 Intent intent = new Intent();connection
//		 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		 intent.setAction(android.content.Intent.ACTION_VIEW);
//		
//		 File f = new File(path);
//		 String type = FileUtil.getMIMEType(f.getName());
//		 intent.setDataAndType(Uri.fromFile(f), type);
//		 startActivity(intent);
//}
    
}
