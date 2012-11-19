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

package com.android.deskclock;

import java.lang.reflect.Method;
import com.android.internal.telephony.ITelephony;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * Manages alarms and vibe. Runs as a service so that it can continue to play
 * if another activity overrides the AlarmAlert dialog.
 */
public class AlarmKlaxon extends Service {

    /** Play alarm up to 10 minutes before silencing */
    private static final int ALARM_TIMEOUT_SECONDS = 55;

    private static final long[] sVibratePattern = new long[] { 500, 500 };

    private boolean mPlaying = false;
    private Vibrator mVibrator;
    private MediaPlayer mMediaPlayer;
    private Alarm mCurrentAlarm;
    private long mStartTime;
    private TelephonyManager mTelephonyManager;
    private TelephonyManager mTelephonyManager1;
    private ITelephony mITelephony;
//    private ITelephony mITelephony2;
    private int mInitialCallState;
    private int mInitialCallState1;
	private boolean isVTCall;
//	private boolean isVTCall2;

    // Internal messages
    private static final int KILLER = 1000;
    public static final int SNOOZE = 2000;
    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KILLER:
                    if (Log.LOGV) {
                        Log.v("Alarm killer triggered");
                    }
                    sendKillBroadcast((Alarm) msg.obj);
                    stopSelf();
                    break;
                case SNOOZE:
                    Intent snooze = new Intent(Alarms.ALARM_SNOOZE_ACTION);
                    sendBroadcast(snooze);
            }
        }
    };

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            // The user might already be in a call when the alarm fires. When
            // we register onCallStateChanged, we get the initial in-call state
            // which kills the alarm. Check against the initial call state so
            // we don't kill the alarm during a call.
        	if(TelephonyManager.getPhoneCount()>1){
        		int state0 = mTelephonyManager.getCallState();
			    int state1 = mTelephonyManager1.getCallState();
                if (state != TelephonyManager.CALL_STATE_IDLE
                		&& (state0 != mInitialCallState||state1 != mInitialCallState1)) {
//                sendKillBroadcast(mCurrentAlarm);
                //this for send snooze intent
                	Intent intent = new Intent(Alarms.ALARM_SNOOZE_ACTION);
                    sendBroadcast(intent);
                    stopSelf();
                    }
        	}else{
        		int state0 = mTelephonyManager.getCallState();
        		if (state != TelephonyManager.CALL_STATE_IDLE
                		&& state0 != mInitialCallState) {
//                sendKillBroadcast(mCurrentAlarm);
                //this for send snooze intent
                	Intent intent = new Intent(Alarms.ALARM_SNOOZE_ACTION);
                    sendBroadcast(intent);
                    stopSelf();
                    }
        	}
        	/*
            //add by niezhong for NEWMS00148154 12-08-11 begin
            else if(state == TelephonyManager.CALL_STATE_IDLE &&
            		state != mInitialCallState){
            	mInitialCallState = state;
            	if(mCurrentAlarm != null) {
            		play(mCurrentAlarm);
            	}
            };
            //add by niezhong for NEWMS00148154 12-08-11 end
*/
        }
    };

    @Override
    public void onCreate() {
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Listen for incoming calls to kill the alarm.
        mTelephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        if(TelephonyManager.getPhoneCount()>1){
        	mTelephonyManager1 =
        	    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE+1);
        	mTelephonyManager1.listen(
        		mPhoneStateListener,PhoneStateListener.LISTEN_CALL_STATE);
        }
    //add for check TVCall
        try{
            mITelephony = getITelephony(mTelephonyManager);
        }catch(Exception e){
            e.printStackTrace();
        }
//        try{
//        	mITelephony2 = getITelephony(mTelephonyManager1);
//        }catch (Exception e) {
//        	e.printStackTrace();
//		}
        AlarmAlertWakeLock.acquireCpuWakeLock(this);
        
        mService = this;
    }
    
    private static AlarmKlaxon mService;
    
    public static AlarmKlaxon getInstance(){
        return mService;
    }
//add for check TVCall
    @Override
    public void onDestroy() {
        stop();
        Alarms.FIRST_ALERT = false;
        // Stop listening for incoming calls.
        mTelephonyManager.listen(mPhoneStateListener, 0);
        if(TelephonyManager.getPhoneCount()>1){
        	mTelephonyManager1.listen(mPhoneStateListener, 0);
        }
        AlarmAlertWakeLock.releaseCpuLock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // No intent, tell the system not to restart us.
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        final Alarm alarm = intent.getParcelableExtra(
                Alarms.ALARM_INTENT_EXTRA);

        if (alarm == null) {
            Log.v("AlarmKlaxon failed to parse the alarm from the intent");
            stopSelf();
            return START_NOT_STICKY;
        }
        //Sometimes,the Application receives two broadcast contains the same
        //alarm,so we add "mCurrentAlarm.id != alarm.id" to avoid the sevice
        //kill it's own alert activity
        if (mCurrentAlarm != null&&mCurrentAlarm.id != alarm.id) {
            sendKillBroadcast(mCurrentAlarm);
        }

        play(alarm);
        mCurrentAlarm = alarm;
        // Record the initial call state here so that the new alarm has the
        // newest state.
        mInitialCallState = mTelephonyManager.getCallState();
        if(TelephonyManager.getPhoneCount()>1){
        	mInitialCallState1 = mTelephonyManager1.getCallState();
        }
        return START_STICKY;
    }

    private void sendKillBroadcast(Alarm alarm) {
        long millis = System.currentTimeMillis() - mStartTime;
        int minutes = (int) Math.round(millis / 60000.0);
        Intent alarmKilled = new Intent(Alarms.ALARM_KILLED);
        alarmKilled.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
        alarmKilled.putExtra(Alarms.ALARM_KILLED_TIMEOUT, minutes);
        sendBroadcast(alarmKilled);
    }

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    private void play(Alarm alarm) {
        // stop() checks to see if we are already playing.
        stop();

        //Check if we are in a call. If we are, don't play alarm
        if(checkCallIsUsing()||checkisVTCall()){
            Log.v("in-call , AlarmKlaxon don't play alarm");
            return ;
        }

        if (Log.LOGV) {
            Log.v("AlarmKlaxon.play() " + alarm.id + " alert " + alarm.alert);
        }

        if (!alarm.silent) {
            Uri alert = alarm.alert;
            // Fall back on the default alarm if the database does not have an
            // alarm stored.
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM);
                if (Log.LOGV) {
                    Log.v("Using default alarm: " + alert.toString());
                }
            }

            // TODO: Reuse mMediaPlayer instead of creating a new one and/or use
            // RingtoneManager.
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnErrorListener(new OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("Error occurred while playing audio.");
                    mp.stop();
                    mp.release();
                    mMediaPlayer = null;
                    return true;
                }
            });

            try {
                // Check if we are in a call. If we are, use the in-call alarm
                // resource at a low volume to not disrupt the call.
                if (mTelephonyManager.getCallState()
                        != TelephonyManager.CALL_STATE_IDLE) {
                    Log.v("Using the in-call alarm");
                    mMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
                    setDataSourceFromResource(getResources(), mMediaPlayer,
                            R.raw.in_call_alarm);
                } else {
                    mMediaPlayer.setDataSource(this, alert);
                }
                startAlarm(mMediaPlayer);
            } catch (Exception ex) {
                Log.v("Using the fallback ringtone");
                // The alert may be on the sd card which could be busy right
                // now. Use the fallback ringtone.
                try {
                    // Must reset the media player to clear the error state.
                    mMediaPlayer.reset();
                    setDataSourceFromResource(getResources(), mMediaPlayer,
                            R.raw.fallbackring);
                    startAlarm(mMediaPlayer);
                } catch (Exception ex2) {
                    // At this point we just don't play anything.
                    Log.e("Failed to play fallback ringtone", ex2);
                }
            }
        }

        /* Start the vibrator after everything is ok with the media player */
        if (alarm.vibrate) {
            mVibrator.vibrate(sVibratePattern, 0);
        } else {
            mVibrator.cancel();
        }

        enableKiller(alarm);
        mPlaying = true;
        mStartTime = System.currentTimeMillis();
    }
//add for TVCall
    public ITelephony getITelephony(TelephonyManager telMgr) throws Exception {
        Method getITelephonyMethod = telMgr.getClass().getDeclaredMethod("getITelephony");
        getITelephonyMethod.setAccessible(true);
        return (ITelephony)getITelephonyMethod.invoke(telMgr);
    }
//add for TVCall
    // Do the common stuff when starting the alarm.
    private void startAlarm(MediaPlayer player)
            throws java.io.IOException, IllegalArgumentException,
                   IllegalStateException {
        final AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        // do not play alarms if stream volume is 0
        // (typically because ringer mode is silent).
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            player.start();
        }
    }

    private void setDataSourceFromResource(Resources resources,
            MediaPlayer player, int res) throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    /**
     * Stops alarm audio and disables alarm if it not snoozed and not
     * repeating
     */
    public void stop() {
        if (Log.LOGV) Log.v("AlarmKlaxon.stop()");
        if (mPlaying) {
            mPlaying = false;

            Intent alarmDone = new Intent(Alarms.ALARM_DONE_ACTION);
            sendBroadcast(alarmDone);

            // Stop audio playing
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }

            // Stop vibrator
            mVibrator.cancel();
        }
        disableKiller();
    }

    /**
     * Kills alarm audio after ALARM_TIMEOUT_SECONDS, so the alarm
     * won't run all day.
     *
     * This just cancels the audio, but leaves the notification
     * popped, so the user will know that the alarm tripped.
     */
    private void enableKiller(Alarm alarm) {

        final String dur =
                PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsActivity.KEY_DURATION,
                        "10");
        int duration = Integer.parseInt(dur);
        Log.v("AlarmKlaxon"+"enableKiller duration:"+ duration);
        mHandler.removeMessages(SNOOZE);
        mHandler.sendEmptyMessageDelayed(SNOOZE, 1000*60*duration);
//        mHandler.sendMessageDelayed(mHandler.obtainMessage(SNOOZE, alarm),
//        //        1000 * ALARM_TIMEOUT_SECONDS);
//        1000 * 60 * duration);
    }

    private void disableKiller() {
        mHandler.removeMessages(KILLER);
    }

    public boolean checkisVTCall(){
        try{
            isVTCall = mITelephony.isVTCall();
//          isVTCall2 = mITelephony2.isVTCall();
            }catch (RemoteException e) {
                e.printStackTrace();
                }
            if(!isVTCall){
                return false;
            }else{
                return true;
            }
    }
    //check the callstate of  sim card
    public  boolean checkCallIsUsing() {
    	if(TelephonyManager.getPhoneCount() >1){
    		int callstate0 = TelephonyManager.getDefault(0).getCallState();
    		int callstate1 = TelephonyManager.getDefault(1).getCallState();
		    if (callstate0 == TelephonyManager.CALL_STATE_IDLE
					    && callstate1 == TelephonyManager.CALL_STATE_IDLE ) {
    				return false;
    				} else {
    					return true;
    					}
    			}else{
    				int callState = TelephonyManager.getDefault().getCallState();
                boolean callstate = callState == TelephonyManager.CALL_STATE_IDLE;
                if (callState == TelephonyManager.CALL_STATE_IDLE) {
                	return false;
                	} else {
                		return true;
                		}
                }
    	}
}
