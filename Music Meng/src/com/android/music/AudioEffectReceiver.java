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
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.util.Log;

public class AudioEffectReceiver extends BroadcastReceiver {
    private static final String TAG = "AudioEffectReceiver";
    private static final String ATTACHAUXAUDIOEFFECT = "com.android.music.attachauxaudioeffect";
    private static final String DETACHAUXAUDIOEFFECT = "com.android.music.detachauxaudioeffect";
    private static final String PREFTAG_EFFECTS_ENABLED = "effectsenabled";
    private static final String PREFTAG_SELECTED_EFFECTS = "selectedeffecttype";
    private static final String PREFTAG_BASSBOOST_LEVEL = "bassboostlevel";
    private static final String PREFTAG_VIRTUALIZER_LEVEL = "virtualizerlevel";
    private static final String PREFTAG_REVERB_PRESET = "reverbpreset";
    private static final String PREFTAG_EQ_PRESET = "equalizerpreset";
    private Context mContext;
    //private static int mCurrentSession = 0;

    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "[onReceive] action = " + intent.getAction());

        mContext = context;
        String action = intent.getAction();
        String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        int audioSession = 0;
        if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
            audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            setAudioEffect(audioSession, packageName);
            //mCurrentSession = audioSession;
            AudioEffectControlPanel.mAudioSession = audioSession;
        } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
        	// TODO: close effect control sessions
        	audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        	if (audioSession == AudioEffectControlPanel.mAudioSession) {
        		closeEffects();
        	}
        }
    }

    private void setAudioEffect(int audioSession, String packageName) {
    	Log.d(TAG, "setAudioEffect(" + audioSession + ", " + packageName + ")");
        AudioEffect audioEffect;
        SharedPreferences preference;
        if (packageName == mContext.getPackageName()) {
        	Log.d(TAG, "setAudioEffect: com.android.music package..");
        	preference = mContext.getSharedPreferences("com.android.music_effect_settings", Context.MODE_PRIVATE);
        } else {
        	preference = mContext.getSharedPreferences("com.android.music_effect_settings", Context.MODE_PRIVATE);
        }
        boolean enabled = preference.getBoolean(PREFTAG_EFFECTS_ENABLED, false);
        Log.d(TAG, "setAudioEffect: enabled=" + enabled);
        if (!enabled)
        	return;
        boolean bSameSession = AudioEffectControlPanel.mAudioSession == audioSession;
        Log.d(TAG, "setAudioEffect: sameSession=" + bSameSession);
		int checkedEffects = preference.getInt(PREFTAG_SELECTED_EFFECTS, 0);
		int bassLevel = preference.getInt(PREFTAG_BASSBOOST_LEVEL, 500);
		int virtualizerLevel = preference.getInt(PREFTAG_VIRTUALIZER_LEVEL, 500);
		short reverbPreset = (short) preference.getInt(PREFTAG_REVERB_PRESET, PresetReverb.PRESET_NONE);
        short eqPreset = (short) preference.getInt(PREFTAG_EQ_PRESET, 0);

        
        Log.d(TAG, "setAudioEffect: mBassBoost=" + AudioEffectControlPanel.mBassBoost + 
        		", mVirtualizer=" + AudioEffectControlPanel.mVirtualizer + 
        		", mPresetReverb=" + AudioEffectControlPanel.mPresetReverb + 
        		", mEqualizer=" + AudioEffectControlPanel.mEqualizer);
        
        // Restore previously selected audio effects
        if ((checkedEffects & 1) != 0) {
        	Log.d(TAG, "restore BassBoost");
        	if (AudioEffectControlPanel.mBassBoost == null) {
        		AudioEffectControlPanel.mBassBoost = new BassBoost(0, audioSession);
        	} else if (!bSameSession) {
        		AudioEffectControlPanel.mBassBoost.release();
        		AudioEffectControlPanel.mBassBoost = new BassBoost(0, audioSession);
        	}
			if(AudioEffectControlPanel.mBassBoost.getStrengthSupported()) {
				AudioEffectControlPanel.mBassBoost.setStrength((short) bassLevel);
			}
			AudioEffectControlPanel.mBassBoost.setEnabled(true);
			Log.i(TAG, "BassBoost restored to session [" + audioSession + "] !!");
        }
        
        if ((checkedEffects & (1 << 1)) != 0) {
        	Log.d(TAG, "restore Virtualizer");
        	if (AudioEffectControlPanel.mVirtualizer == null) {
        		AudioEffectControlPanel.mVirtualizer = new Virtualizer(0, audioSession);
        	} else if (!bSameSession) {
        		AudioEffectControlPanel.mVirtualizer.release();
        		AudioEffectControlPanel.mVirtualizer = new Virtualizer(0, audioSession);
        	}
			if(AudioEffectControlPanel.mVirtualizer.getStrengthSupported()) {
				AudioEffectControlPanel.mVirtualizer.setStrength((short) virtualizerLevel);
			}
			AudioEffectControlPanel.mVirtualizer.setEnabled(true);
			Log.i(TAG, "Virtualizer restored to session [" + audioSession + "] !!");
        }
        
        if ((checkedEffects & (1 << 2)) != 0) {
        	Log.d(TAG, "restore PresetReverb");
        	// PresetReverb is an auxiliary effect, so attach this effect directly to session 0, i.e. the main audio output mix
        	if (AudioEffectControlPanel.mPresetReverb == null) {
        		AudioEffectControlPanel.mPresetReverb = new PresetReverb(0, 0);
        		//AudioEffectControlPanel.mPresetReverb = new PresetReverb(0, audioSession);
        	} else if (!bSameSession) {
        		AudioEffectControlPanel.mPresetReverb.release();
        		//AudioEffectControlPanel.mPresetReverb = new PresetReverb(0, audioSession);
        		AudioEffectControlPanel.mPresetReverb = new PresetReverb(0, 0);
        	}
        	AudioEffectControlPanel.mPresetReverb.setPreset(reverbPreset);
        	AudioEffectControlPanel.mPresetReverb.setEnabled(true);
			// Send a broadcast containing the effect id to Music app to attach auxiliary effect to MediaPlayer instance
			Intent it_aux = new Intent(ATTACHAUXAUDIOEFFECT);
			it_aux.putExtra("auxaudioeffectid", AudioEffectControlPanel.mPresetReverb.getId());
			mContext.sendBroadcast(it_aux);
			Log.i(TAG, "PresetReverb restored to session [0] !");
        }
        
        if ((checkedEffects & (1 << 3)) != 0) {
        	Log.d(TAG, "restore Equalizer");
        	if (AudioEffectControlPanel.mEqualizer == null) {
        		AudioEffectControlPanel.mEqualizer = new Equalizer(0, audioSession);
        	} else if (!bSameSession) {
        		AudioEffectControlPanel.mEqualizer.release();
        		AudioEffectControlPanel.mEqualizer = new Equalizer(0, audioSession);
        	}
        	AudioEffectControlPanel.mEqualizer.setEnabled(true);
        	AudioEffectControlPanel.mEqualizer.usePreset(eqPreset);
        	Log.i(TAG, "Equalizer restored to session[" + audioSession + "] !!");
        }
        
        
/*
        switch (checkedPos) {
        // "case 0" is ignored
        case 1:
            audioEffect = new BassBoost(0, audioSession);
            if(((BassBoost)audioEffect).getStrengthSupported()) {
                ((BassBoost)audioEffect).setStrength((short) bassLevel);
            }
            audioEffect.setEnabled(true);
            Log.i(TAG, "BassBoost effect has been restored to audio session [" + audioSession + "] !!");
            break;
        case 2:
            audioEffect = new Virtualizer(0, audioSession);
            if(((Virtualizer)audioEffect).getStrengthSupported()) {
                ((Virtualizer)audioEffect).setStrength((short) virtualizerLevel);
            }
            audioEffect.setEnabled(true);
            Log.i(TAG, "Virtualizer effect has been restored to audio session [" + audioSession + "] !!");
            break;
        case 3:
            // PresetReverb is an auxiliary effect, so attach this effect directly to session 0, i.e. the main audio output mix
            audioEffect = new PresetReverb(0, 0);
            ((PresetReverb)audioEffect).setPreset(reverbPreset);
            audioEffect.setEnabled(true);
            // Send a broadcast containing the effect id to Music app to attach auxiliary effect to MediaPlayer instance
            Intent it_aux = new Intent(ATTACHAUXAUDIOEFFECT);
            it_aux.putExtra("auxaudioeffectid", audioEffect.getId());
            mContext.sendBroadcast(it_aux);
            Log.i(TAG, "PresetReverb effect has been restored to audio session [" + audioSession + "] !!");
            break;
        case 4:
            audioEffect = new Equalizer(0, audioSession);
            ((Equalizer)audioEffect).setEnabled(true);
            ((Equalizer)audioEffect).usePreset(eqPreset);
            Log.i(TAG, "Equalizer effect has been restored to audio session[" + audioSession + "] !!");
            break;
        default:
            Log.e(TAG, "Invalid selected effect!!");
        }
        */
    }
    
    private void closeEffects() {
    	Log.d(TAG, "closeEffects");
    	if (AudioEffectControlPanel.mBassBoost != null) {
    		AudioEffectControlPanel.mBassBoost.setEnabled(false);
    		AudioEffectControlPanel.mBassBoost.release();
    		AudioEffectControlPanel.mBassBoost = null;
    		Log.d(TAG, "closeEffects: bassboost disabled");
    	}
    	if (AudioEffectControlPanel.mVirtualizer != null) {
    		AudioEffectControlPanel.mVirtualizer.setEnabled(false);
    		AudioEffectControlPanel.mVirtualizer.release();
    		AudioEffectControlPanel.mVirtualizer = null;
    		Log.d(TAG, "closeEffects: virtualizer disabled");
    	}
    	if (AudioEffectControlPanel.mPresetReverb != null) {
    		AudioEffectControlPanel.mPresetReverb.setEnabled(false);
    		Intent i = new Intent(DETACHAUXAUDIOEFFECT);
    		i.putExtra("auxaudioeffectid", AudioEffectControlPanel.mPresetReverb.getId());
    		mContext.sendBroadcast(i);
    		AudioEffectControlPanel.mPresetReverb.release();
    		AudioEffectControlPanel.mPresetReverb = null;
    		Log.d(TAG, "closeEffects: presetreverb disabled");
    	}
    	if (AudioEffectControlPanel.mEqualizer != null) {
    		AudioEffectControlPanel.mEqualizer.setEnabled(false);
    		AudioEffectControlPanel.mEqualizer.release();
    		AudioEffectControlPanel.mEqualizer = null;
    		Log.d(TAG, "closeEffects: equalizer disabled");
    	}
    }
    
}
