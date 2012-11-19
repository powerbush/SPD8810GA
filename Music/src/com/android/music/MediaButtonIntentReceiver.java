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
import android.content.ContextWrapper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.util.Log;

/**
 * 
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {

    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int LONG_PRESS_DELAY = 1000;

    private static long mLastClickTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;
    
	//Modified by MM05
	private static boolean isVideoPlaying=false;
	private static boolean isSoundRecordPlaying=false;
	private static boolean isFMPlaying=false;
	private static boolean isFMServiceRunning=false;
	public static final String FM_SHUTDOWN = "com.android.fm.shutdown";
	
	private static final String TAG = "MediaButtonIntentReceiver";
    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (!mLaunched) {
                        Context context = (Context)msg.obj;
                        Intent i = new Intent();
                        i.putExtra("autoshuffle", "true");
                        i.setClass(context, MusicBrowserActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(i);
                        mLaunched = true;
                    }
                    break;
            }
        }
    };
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();
        Log.d(TAG, "intent from :" + intentAction);
		// Modified by MM05
		if ("com.android.video.stopmusicservice".equals(intentAction)) {
			boolean isVideoOpen = intent.getBooleanExtra("playingvideo", false);
			if (isVideoOpen) {
				isVideoPlaying = true;
			} else {
				isVideoPlaying = false;
			}
		}

		if ("com.android.soudrecorder.stopmusicservice".equals(intentAction)) {
			boolean isSoundRecordOpen = intent.getBooleanExtra("recordering",
					false);
			if (isSoundRecordOpen) {
				isSoundRecordPlaying = true;
			} else {
				isSoundRecordPlaying = false;
			}
		}

		if ("com.android.fm.stopmusicservice".equals(intentAction)) {
			boolean isFMOpen = intent.getBooleanExtra("playingfm", false);
			if (isFMOpen) {
				isFMPlaying = true;
			} else {
				isFMPlaying = false;
			}
		}

		if ("com.android.fmservice.stopmusicservice".equals(intentAction)) {
			boolean isFMServiceRun = intent.getBooleanExtra("playingfmservice",
					false);
			if (isFMServiceRun) {
				isFMServiceRunning = true;
			} else {
				isFMServiceRunning = false;
			}
		}
//        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
//            Intent i = new Intent(context, MediaPlaybackService.class);
//            i.setAction(MediaPlaybackService.SERVICECMD);
//            i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDPAUSE);
//            context.startService(i);
//        } else
        if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
        	//Modified by MM05
			if (isVideoPlaying) {
				return;
			}
			if (isSoundRecordPlaying) {
				return;
			}
			if (isFMPlaying) {
				return;
			}
			// If FM is running in the background,
			// send a broadcast to shutdown FM while pressing the button of headset
			if (isFMServiceRunning && !isFMPlaying) {
				Intent fmIntent = new Intent(FM_SHUTDOWN);
				new ContextWrapper(context).sendBroadcast(fmIntent);
				return;
			}
            KeyEvent event = (KeyEvent)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            
            if (event == null) {
                return;
            }

            int keycode = event.getKeyCode();
            int action = event.getAction();
            long eventtime = event.getEventTime();

            // single quick press: pause/resume. 
            // double press: next track
            // long press: start auto-shuffle mode.
            
            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = MediaPlaybackService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = MediaPlaybackService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = MediaPlaybackService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = MediaPlaybackService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    command = MediaPlaybackService.CMDREWIND;
                    break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    command = MediaPlaybackService.CMDFASTFORWARD;
                    break;
            }

            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
//                        if (MediaPlaybackService.CMDTOGGLEPAUSE.equals(command)
//                                && mLastClickTime != 0 
//                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
//                            mHandler.sendMessage(
//                                    mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context));
//                        }
                    } else {
                        // if this isn't a repeat event

                        // The service may or may not be running, but we need to send it
                        // a command.
                        Intent i = new Intent(context, MediaPlaybackService.class);
                        i.setAction(MediaPlaybackService.SERVICECMD);
                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK &&
                                eventtime - mLastClickTime < 300) {
                            i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDNEXT);
                            context.startService(i);
                            mLastClickTime = 0;
                        } else {
                            i.putExtra(MediaPlaybackService.CMDNAME, command);
                            context.startService(i);
                            mLastClickTime = eventtime;
                        }

                        mLaunched = false;
                        mDown = true;
                    }
                } else {
                    mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
                if(command.equals(MediaPlaybackService.CMDREWIND)){
                    Intent intent2 = new Intent(context, MediaPlaybackService.class);
                    intent2.setAction(MediaPlaybackService.REWIND_ACTION);
                    context.startService(intent2);
                }else if(command.equals(MediaPlaybackService.CMDFASTFORWARD)){
                    Intent intent2 = new Intent(context, MediaPlaybackService.class);
                    intent2.setAction(MediaPlaybackService.FAST_FORWARD_ACTION);
                    context.startService(intent2);
                }
            }
        }
    }
}
