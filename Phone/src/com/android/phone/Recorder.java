package com.android.phone;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class Recorder implements OnCompletionListener, OnErrorListener {
	private static final String LOG_TAG = "Recorder";
	
    static final String SAMPLE_PREFIX = "recording";
    static final String SAMPLE_PATH_KEY = "sample_path";
    static final String SAMPLE_LENGTH_KEY = "sample_length";

    public static final int IDLE_STATE = 0;
    public static final int RECORDING_STATE = 1;
    public static final int PLAYING_STATE = 2;
    
    int mState = IDLE_STATE;

    public static final int NO_ERROR = 0;
    public static final int SDCARD_ACCESS_ERROR = 1;
    public static final int INTERNAL_ERROR = 2;
    public static final int IN_CALL_RECORD_ERROR = 3;
	
	private static final String DEFAULT_STORE_SUBDIR = "/voicecall";
	private static final String DEFAULT_RECORD_SUFFIX = ".3gpp";
	private static final long MINIMUM_FREE_SIZE = 1024 * 1024;
    
    public interface OnStateChangedListener {
        public void onStateChanged(int state);
        public void onError(int error);
    }
    OnStateChangedListener mOnStateChangedListener = null;
    
    long mSampleStart = 0;       // time at which latest record or play operation started
    int mSampleLength = 0;      // length of current sample
    File mSampleFile = null;
    
    MediaRecorder mRecorder = null;
    MediaPlayer mPlayer = null;
    private Context mContext;
    
	public Recorder(Context context) {
		mContext = context;
	}
    
    public void saveState(Bundle recorderState) {
        recorderState.putString(SAMPLE_PATH_KEY, mSampleFile.getAbsolutePath());
        recorderState.putInt(SAMPLE_LENGTH_KEY, mSampleLength);
    }
    
    public int getMaxAmplitude() {
        if (mState != RECORDING_STATE)
            return 0;
        return mRecorder.getMaxAmplitude();
    }
    
    public void restoreState(Bundle recorderState) {
        String samplePath = recorderState.getString(SAMPLE_PATH_KEY);
        if (samplePath == null)
            return;
        int sampleLength = recorderState.getInt(SAMPLE_LENGTH_KEY, -1);
        if (sampleLength == -1)
            return;

        File file = new File(samplePath);
        if (!file.exists())
            return;
        if (mSampleFile != null
                && mSampleFile.getAbsolutePath().compareTo(file.getAbsolutePath()) == 0)
            return;
        
        delete();
        mSampleFile = file;
        mSampleLength = sampleLength;

        signalStateChanged(IDLE_STATE);
    }
    
    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }
    
    public int state() {
        return mState;
    }
    
    public int progress() {
        if (mState == RECORDING_STATE || mState == PLAYING_STATE)
            return (int) ((System.currentTimeMillis() - mSampleStart)/1000);
        return 0;
    }
    
    public int sampleLength() {
        return mSampleLength;
    }

    public File sampleFile() {
        return mSampleFile;
    }
    
    /**
     * Resets the recorder state. If a sample was recorded, the file is deleted.
     */
    public void delete() {
        stop();
        
        if (mSampleFile != null)
            mSampleFile.delete();

        mSampleFile = null;
        mSampleLength = 0;
        
        signalStateChanged(IDLE_STATE);
    }
    
    /**
     * Resets the recorder state. If a sample was recorded, the file is left on disk and will 
     * be reused for a new recording.
     */
    public void clear() {
        stop();
        
        mSampleLength = 0;
        
        signalStateChanged(IDLE_STATE);
    }
    
	private File getRecordFile(){
		File base = null;
		String root = Environment.getExternalStorageDirectory().getPath();
		base = new File(root + DEFAULT_STORE_SUBDIR);
		if (!base.isDirectory() && !base.mkdir()) {
			Log.e(LOG_TAG, "Recording File aborted - can't create base directory " + base.getPath());
			return null;
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat("'voicecall'-yyyyMMddHHmmss");
		String fn = sdf.format(new Date());
		fn = base.getPath() + File.separator + fn + DEFAULT_RECORD_SUFFIX;
		
		StatFs stat = null;
		stat = new StatFs(base.getPath());
		long available_size = stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4);
		if (available_size < MINIMUM_FREE_SIZE){
			Log.e(LOG_TAG, "Recording File aborted - not enough free space");
			return null;			
		}

		File outFile = new File(fn);
		try{
			if (outFile.exists()){
				outFile.delete();
			}
			boolean bRet = outFile.createNewFile();
			if (!bRet) {
				Log.e(LOG_TAG, "getRecordFile, fn: " + fn + ", failed");
				return null;
			}
		} catch (SecurityException e){
			Log.e(LOG_TAG, "getRecordFile, fn: " + fn + ", " + e);
			return null;
		} catch (IOException e){
			Log.e(LOG_TAG, "getRecordFile, fn: " + fn + ", " + e);
			return null;
		}
		
		return outFile;		 
	}
	
    public boolean startRecording(int outputfileformat, String extension, Context context) {
        stop();

		mSampleFile = getRecordFile();
		if (mSampleFile == null){
			return false;
		}
        
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
        mRecorder.setOutputFormat(outputfileformat);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile(mSampleFile.getAbsolutePath());

        // Handle IOException
        try {
            mRecorder.prepare();
        } catch(IOException exception) {
            setError(INTERNAL_ERROR);
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            return false;
        }
        // Handle RuntimeException if the recording couldn't start
        try {
            mRecorder.start();
        } catch (RuntimeException exception) {
            AudioManager audioMngr = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            boolean isInCall = audioMngr.getMode() == AudioManager.MODE_IN_CALL;
            if (isInCall) {
                setError(IN_CALL_RECORD_ERROR);
            } else {
                setError(INTERNAL_ERROR);
            }
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            return false;
        }
        mSampleStart = System.currentTimeMillis();
        setState(RECORDING_STATE);
		return true;
    }
    
	public void stopRecording() {
		if (mRecorder == null)
			return;

		try {
			mRecorder.stop();
		} catch (RuntimeException exception) {
			Log.d(LOG_TAG, "stop recording failed.");
			AudioManager audioMngr = (AudioManager) mContext
					.getSystemService(Context.AUDIO_SERVICE);
			boolean isInCall = audioMngr.getMode() == AudioManager.MODE_IN_CALL;
			if (isInCall) {
				setError(IN_CALL_RECORD_ERROR);
			} else {
				setError(INTERNAL_ERROR);
			}
			mRecorder.reset();
		}
		mRecorder.release();
		mRecorder = null;

		mSampleLength = (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
		setState(IDLE_STATE);
	}
    
    public void startPlayback() {
        stop();
        
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mSampleFile.getAbsolutePath());
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            setError(INTERNAL_ERROR);
            mPlayer = null;
            return;
        } catch (IOException e) {
            setError(SDCARD_ACCESS_ERROR);
            mPlayer = null;
            return;
        }
        
        mSampleStart = System.currentTimeMillis();
        setState(PLAYING_STATE);
    }
    
    public void stopPlayback() {
        if (mPlayer == null) // we were not in playback
            return;

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        setState(IDLE_STATE);
    }
    
    public void stop() {
        stopRecording();
        stopPlayback();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(SDCARD_ACCESS_ERROR);
        return true;
    }

    public void onCompletion(MediaPlayer mp) {
        stop();
    }
    
    private void setState(int state) {
        if (state == mState)
            return;
        
        mState = state;
        signalStateChanged(mState);
    }
    
    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onStateChanged(state);
    }
    
    private void setError(int error) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onError(error);
    }
}
