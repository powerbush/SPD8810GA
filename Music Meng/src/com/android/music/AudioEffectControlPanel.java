package com.android.music;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;

public class AudioEffectControlPanel extends ListActivity 
		implements  CompoundButton.OnCheckedChangeListener, View.OnClickListener{
	
	private static final int NUM_OF_EFFECTS = 4;
	
	private static final String LOGTAG = "AudioEffectControlPanel";
	
	public static final String ATTACHAUXAUDIOEFFECT = "com.android.music.attachauxaudioeffect";
    public static final String DETACHAUXAUDIOEFFECT = "com.android.music.detachauxaudioeffect";
	
    private static final String PREFTAG_EFFECTS_ENABLED = "effectsenabled";
	private static final String PREFTAG_SELECTED_EFFECTS = "selectedeffecttype";
	private static final String PREFTAG_BASSBOOST_LEVEL = "bassboostlevel";
	private static final String PREFTAG_VIRTUALIZER_LEVEL = "virtualizerlevel";
	private static final String PREFTAG_REVERB_PRESET = "reverbpreset";
	private static final String PREFTAG_EQ_PRESET = "equalizerpreset";
	private static final String PREFTAG_AUDIOSESSIONID = "audiosessionid";
	private static final String SHAREDPREF_NAME = "com.android.music_effect_settings";

	private static int DEF_EFFECT_LEVEL = 500;
	
	private ListView mLvEffects;
	private CheckBox mCBEffectsEnabled;

	private short mReverbPreset = PresetReverb.PRESET_SMALLROOM;
	private short mNewPreset = 0;

	private static CharSequence[] mReverbPresetList = null;
	private CharSequence[] mEQPresetList = null;
	private short mEQPreset;

	// make them static to avoid been GCed
	public static Equalizer mEqualizer = null;
	public static BassBoost mBassBoost = null;
	public static Virtualizer mVirtualizer = null;
	public static PresetReverb mPresetReverb = null;

	private boolean mEffectsEnabled = false;

	private SharedPreferences mPreference;

	private SimpleAdapter mAdapter;

	public static int mAudioSession;

	private int mCheckedEffects = 0;
	private int mConfigPos = 0;
	private int mBassLevel = DEF_EFFECT_LEVEL;
	private int mVirtualizerLevel = DEF_EFFECT_LEVEL;

	private AlertDialog mDlgEffectStrength = null;
	private AlertDialog mDlgEQPresetList = null;
	private AlertDialog mDlgReverbPresetList = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audiofx_main);
        setTitle(R.string.effects_panel_window_title);

        mReverbPresetList = getResources().getStringArray(R.array.reverb_preset_list);
        
        mLvEffects = getListView();
        mCBEffectsEnabled = (CheckBox)findViewById(R.id.cb_enabled);
        mCBEffectsEnabled.setOnCheckedChangeListener(this);
        
        RelativeLayout RelLayout = (RelativeLayout)findViewById(R.id.RLEnable);
        RelLayout.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mCBEffectsEnabled.setChecked(!mCBEffectsEnabled.isChecked());
			}
		});
			RelLayout.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				public void onFocusChange(View v, boolean hasFocus) {
					TextView tv_enabled = (TextView)findViewById(R.id.txtEnable);
					tv_enabled.setSelected(hasFocus);
				}
			});
        
        mPreference = getSharedPreferences(SHAREDPREF_NAME, MODE_PRIVATE);
        if (savedInstanceState == null) {
	        Intent it = getIntent();
	        mAudioSession = it.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
	        mEffectsEnabled = mPreference.getBoolean(PREFTAG_EFFECTS_ENABLED, false);
	        mCheckedEffects = mPreference.getInt(PREFTAG_SELECTED_EFFECTS, 0);
	        mBassLevel = mPreference.getInt(PREFTAG_BASSBOOST_LEVEL, DEF_EFFECT_LEVEL);
	        mVirtualizerLevel = mPreference.getInt(PREFTAG_VIRTUALIZER_LEVEL, DEF_EFFECT_LEVEL);
	        mReverbPreset = (short) mPreference.getInt(PREFTAG_REVERB_PRESET, PresetReverb.PRESET_SMALLROOM);
	        mEQPreset = (short) mPreference.getInt(PREFTAG_EQ_PRESET, 0);
        } else {
        	Log.i(LOGTAG, "retrieve info from savedInstanceState!!");
        	mAudioSession = savedInstanceState.getInt(PREFTAG_AUDIOSESSIONID, 0);
        	mEffectsEnabled = savedInstanceState.getBoolean(PREFTAG_EFFECTS_ENABLED, false);
        	mCheckedEffects = savedInstanceState.getInt(PREFTAG_SELECTED_EFFECTS, 0);
        	mBassLevel = savedInstanceState.getInt(PREFTAG_BASSBOOST_LEVEL, DEF_EFFECT_LEVEL);
        	mVirtualizerLevel = savedInstanceState.getInt(PREFTAG_VIRTUALIZER_LEVEL, DEF_EFFECT_LEVEL);
        	mReverbPreset = savedInstanceState.getShort(PREFTAG_REVERB_PRESET, PresetReverb.PRESET_SMALLROOM);
        	mEQPreset = savedInstanceState.getShort(PREFTAG_EQ_PRESET, (short) 0);
        }

        // Restore previously selected audio effects
        if ((mCheckedEffects & 1) != 0) {
        	Log.d(LOGTAG, "restore BassBoost");
        	if (mBassBoost == null) {
        			mBassBoost = new BassBoost(0, mAudioSession);
        	}
			if(mBassBoost.getStrengthSupported()) {
				mBassBoost.setStrength((short) mBassLevel);
			}
			mBassBoost.setEnabled(mEffectsEnabled);
        }
        
        if ((mCheckedEffects & (1 << 1)) != 0) {
        	Log.d(LOGTAG, "restore Virtualizer");
        	if (mVirtualizer == null) {
        			mVirtualizer = new Virtualizer(0, mAudioSession);
        	}
			if(mVirtualizer.getStrengthSupported()) {
				mVirtualizer.setStrength((short) mVirtualizerLevel);
			}
			mVirtualizer.setEnabled(mEffectsEnabled);
			Log.i(LOGTAG, "Virtualizer effect has been restored to audio session [" + mAudioSession + "] !!");
        }
        
        if ((mCheckedEffects & (1 << 2)) != 0) {
        	Log.d(LOGTAG, "restore PresetReverb");
        	// PresetReverb is an auxiliary effect, so attach this effect directly to session 0, i.e. the main audio output mix
        	if (mPresetReverb == null) {
        			mPresetReverb = new PresetReverb(0, 0);
        	}
        	mPresetReverb.setPreset(mReverbPreset);
        	mPresetReverb.setEnabled(mEffectsEnabled);
			// Send a broadcast containing the effect id to Music app to attach auxiliary effect to MediaPlayer instance
			Intent it_aux = new Intent(ATTACHAUXAUDIOEFFECT);
			it_aux.putExtra("auxaudioeffectid", mPresetReverb.getId());
			sendBroadcast(it_aux);
			Log.i(LOGTAG, "PresetReverb effect has been restored to audio session [" + mAudioSession + "] !!");
        }
        
        if ((mCheckedEffects & (1 << 3)) != 0) {
        	Log.d(LOGTAG, "restore Equalizer");
        	if (mEqualizer == null) {
        			mEqualizer = new Equalizer(0, mAudioSession);
        	}
        	mEqualizer.setEnabled(mEffectsEnabled);
        	mEqualizer.usePreset(mEQPreset);
        	Log.i(LOGTAG, "Equalizer effect has been restored to audio session[" + mAudioSession + "] !!");
        }
        
        Log.d(LOGTAG, "onCreate: BassBoost=" + mBassBoost + "," + mBassLevel + 
        		" | Virtualizer=" + mVirtualizer + "," + mVirtualizerLevel + 
        		" | PresetReverb=" + mPresetReverb + "," + mReverbPreset + 
        		" | EQ=" + mEqualizer + "," + mEQPreset); 
        
        HashMap<String, Object> hm = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> effectsItemList = new ArrayList<HashMap<String,Object>>(10);

        // BassBoost
        hm = new HashMap<String, Object>();
        hm.put("EFFECT_NAME", getString(R.string.bassboost_name));
        hm.put("EFFECT_STATE", (mCheckedEffects & 1) != 0);
        effectsItemList.add(hm);
        
        // Virtualizer
        hm = new HashMap<String, Object>();
        hm.put("EFFECT_NAME", getString(R.string.virtualizer_name));
        hm.put("EFFECT_STATE", (mCheckedEffects & (1 << 1)) != 0);
        effectsItemList.add(hm);
        
        // PresetReverb
        hm = new HashMap<String, Object>();
        hm.put("EFFECT_NAME", getString(R.string.presetreverb_name));
        hm.put("EFFECT_STATE", (mCheckedEffects & (1 << 2)) != 0);
        effectsItemList.add(hm);
        
        // Equalizer
        hm = new HashMap<String, Object>();
        hm.put("EFFECT_NAME", getString(R.string.equalizer_name));
        hm.put("EFFECT_STATE", (mCheckedEffects & (1 << 3)) != 0);
        effectsItemList.add(hm);
        
        mAdapter = new MyAdapter(this, 
        		effectsItemList, 
        		R.layout.audiofx_main_panel_item, 
        		new String[] {"EFFECT_NAME", "EFFECT_STATE"}, 
        		new int[] {R.id.effect_name, R.id.checkbox});

        ViewBinder vb = new ViewBinder() {
			public boolean setViewValue(View view, Object data,
					String textRepresentation) {
				if (view instanceof CheckBox) {
					Log.i(LOGTAG, "viewbinder: CheckBox");
					((CheckBox)view).setOnClickListener(AudioEffectControlPanel.this);
				}
				return false;
			}
		};
		mAdapter.setViewBinder(vb);
        setListAdapter(mAdapter);
        
        mCBEffectsEnabled.setChecked(mEffectsEnabled);
        mLvEffects.setEnabled(mEffectsEnabled);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	Log.v(LOGTAG, ">>>onListItemClick()");
    	switch (position) {
    	case 0:
    		mConfigPos = 1;
    		showProgressDialog(getString(R.string.bassboost_strength));
    		break;
    	case 1:
    		mConfigPos = 2;
    		showProgressDialog(getString(R.string.virtualizer_strength));
    		break;
    	case 2:
    		showReverbPresetListDialog();
    		break;
    	case 3:
    		showEQPresetListDialog();
    		break;
    	default:
    		Log.e(LOGTAG, "Invalid item clicked!!!");
    	}
    }
    
    private void showProgressDialog(String dlgTitle) {
    	if (mDlgEffectStrength != null && mDlgEffectStrength instanceof AlertDialog) {
			Log.d(LOGTAG, "Progress dialog already showed once!!");
			mDlgEffectStrength.setTitle(dlgTitle);
			((SeekBar)mDlgEffectStrength.findViewById(R.id.SeekBar01)).setProgress(mConfigPos == 1 ? mBassLevel : mVirtualizerLevel);
			mDlgEffectStrength.show();
			return;
		}
    	final View dialogView = View.inflate(AudioEffectControlPanel.this, R.layout.audiofx_effect_strength, null);
		SeekBar sb = (SeekBar)dialogView.findViewById(R.id.SeekBar01);
		sb.setMax(1000);
		sb.setProgress(DEF_EFFECT_LEVEL);
		
		if (mConfigPos == 1) {
			// BassBoost
			if (0 <= mBassLevel && mBassLevel <= 1000) {
				sb.setProgress(mBassLevel);
			}
		} else if (mConfigPos == 2) {
			// Virtualizer
			if (0 <= mVirtualizerLevel && mVirtualizerLevel <= 1000) {
				sb.setProgress(mVirtualizerLevel);
			}
		}
		
		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onStopTrackingTouch(SeekBar seekBar) {
				int progress = seekBar.getProgress();
				if (mConfigPos == 1) {
					if (mBassBoost != null) {
						mBassBoost.setStrength((short) progress);
						Log.i(LOGTAG, "BassBoost level = " + progress + "/1000");
					}
				} else if (mConfigPos == 2) {
					if (mVirtualizer != null) {
						mVirtualizer.setStrength((short) progress);
						Log.i(LOGTAG, "Virtualizer level = " + progress + "/1000");
					}
				}
			}
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
			}
		});

		mDlgEffectStrength = new AlertDialog.Builder(AudioEffectControlPanel.this)
		.setView(dialogView)
		.setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (mConfigPos == 1) {
					// BassBoost
					Log.i(LOGTAG, "Positive button: BassBoost level saved!!");
					mBassLevel = ((SeekBar)dialogView.findViewById(R.id.SeekBar01)).getProgress();
				} else if (mConfigPos == 2) {
					// Virtualizer
					Log.i(LOGTAG, "Positive button: Virtualizer level saved!!");
					mVirtualizerLevel = ((SeekBar)dialogView.findViewById(R.id.SeekBar01)).getProgress();
				}
				mDlgEffectStrength.dismiss();
			}
		})
		.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
					// Restore preview level to unchanged level
				if (mConfigPos == 1 && mBassBoost != null) {
					mBassBoost.setStrength((short) mBassLevel);
					Log.i(LOGTAG, "BassBoost level restored to " + mBassLevel + "/1000");
				} else if (mConfigPos == 2 && mVirtualizer != null) {
					mVirtualizer.setStrength((short) mVirtualizerLevel);
					Log.i(LOGTAG, "Virtualizer level restored to " + mVirtualizerLevel + "/1000");
				}
				mDlgEffectStrength.dismiss();
			}
		}).setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
					// Restore preview level to unchanged level
				if (mConfigPos == 1 && mBassBoost != null) {
					mBassBoost.setStrength((short) mBassLevel);
					Log.i(LOGTAG, "BassBoost level restored to " + mBassLevel + "/1000");
				} else if (mConfigPos == 2 && mVirtualizer != null) {
					mVirtualizer.setStrength((short) mVirtualizerLevel);
					Log.i(LOGTAG, "Virtualizer level restored to " + mVirtualizerLevel + "/1000");
				}
			}
		}).create();
	
		mDlgEffectStrength.setTitle(dlgTitle);
		mDlgEffectStrength.show();
		
    }
    
    private void showReverbPresetListDialog() {
    	// PresetReverb has 6 presets:
    	// PRESET_SMALLROOM = 1; PRESET_MEDIUMROOM = 2;
    	// PRESET_LARGEROOM = 3; PRESET_MEDIUMHALL = 4;
    	// PRESET_LARGEHALL = 5; PRESET_PLATE = 6; 
    	// We skip PRESET_NONE('0') here
    	int initialSelection = (mReverbPreset < 1 || mReverbPreset > 6) ? 0 : mReverbPreset - 1;
    	mDlgReverbPresetList = new AlertDialog.Builder(this)
    		.setSingleChoiceItems(mReverbPresetList, initialSelection, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// PRESET_NONE has been removed from list,
					// so skip preset '0'
					mNewPreset = (short) (which + 1);
					if (mPresetReverb != null) {
						mPresetReverb.setPreset((short) mNewPreset);
						Log.i(LOGTAG, "New reverb preset [" + mNewPreset + "] has been applied for preview");
					}
					Log.i(LOGTAG, "New reverb preset is:" + mNewPreset);
				}
			}).setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mReverbPreset = mNewPreset;
					dialog.dismiss();
				}
			}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (mPresetReverb != null && mNewPreset != mReverbPreset) {
						// User has changed preset but does not intent to save the change
						mPresetReverb.setPreset(mReverbPreset);
					}
					dialog.dismiss();
				}
			}).setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					if (mPresetReverb != null && mNewPreset != mReverbPreset) {
						// User has changed preset but does not intent to save the change
						mPresetReverb.setPreset(mReverbPreset);
					}
				}
			}).create();
    	
    	mDlgReverbPresetList.setTitle(getString(R.string.reverb_preset_title));
    	mDlgReverbPresetList.show();
    }
    
    private void showEQPresetListDialog() {
    	if (mEqualizer == null) {
   			mEqualizer = new Equalizer(0, mAudioSession);
   		}

    	// The number of Equalizer presets are fetched at RUN-TIME
    	// and we can compare them to known types for MUI concern
    	short numOfEQPresets = mEqualizer.getNumberOfPresets();
    	if (mEQPresetList == null) {
	    	mEQPresetList = new CharSequence[numOfEQPresets];
	    	String sPresetName;
	    	for (short i = 0; i < numOfEQPresets; ++i) {
	    		sPresetName = mEqualizer.getPresetName(i);
	    		if ("Normal".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_normal);
	    		} else if ("Classical".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_classical);
	    		} else if ("Dance".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_dance);
	    		} else if ("Flat".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_flat);
	    		} else if ("Folk".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_folk);
	    		} else if ("Heavy Metal".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_heavymetal);
	    		} else if ("Hip Hop".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_hiphop);
	    		} else if ("Jazz".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_jazz);
	    		} else if ("Pop".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_pop);
	    		} else if ("Rock".equalsIgnoreCase(sPresetName)) {
	    			mEQPresetList[i] = getString(R.string.eq_preset_rock);
	    		} else {
	    			// The preset name string does not match any of our known preset name
	    			// so use the default string returned from getPresetName()
	    			mEQPresetList[i] = sPresetName;
	    		}
	    	}
    	}
    	int initialSelection = (mEQPreset < 0 || mEQPreset > (numOfEQPresets - 1)) ? 0 : mEQPreset;
    	
    	mDlgEQPresetList = new AlertDialog.Builder(this)
    		.setSingleChoiceItems(mEQPresetList, initialSelection, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mNewPreset = (short) which;
					if (mEqualizer != null) {
						mEqualizer.usePreset((short) mNewPreset);
						Log.i(LOGTAG, "New EQ preset [" + mNewPreset + "] has been applied for preview");
					}
					Log.i(LOGTAG, "New EQ preset is:" + mNewPreset);
				}
			}).setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mEQPreset = mNewPreset;
					dialog.dismiss();
				}
			}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (mEqualizer != null && mNewPreset != mEQPreset) {
						// User has changed preset to different one but not saved the change,
						Log.d(LOGTAG, "set Equalizer preset back to [" + mEQPreset + "] !!");
						mEqualizer.usePreset(mEQPreset);
					}
					dialog.dismiss();
				}
			}).setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					if (mEqualizer != null && mNewPreset != mEQPreset) {
						// User has changed preset to different one but not saved the change,
						Log.d(LOGTAG, "set Equalizer preset back to [" + mEQPreset + "] !!");
						mEqualizer.usePreset(mEQPreset);
					}
				}
			}).create();

    	mDlgEQPresetList.setTitle(getString(R.string.equalizer_preset_title));
    	mDlgEQPresetList.show();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	// mAudioSession, mEffectsEnabled, mCheckedEffects, 
    	// mBassLevel, mVirtualizerLevel, mReverbPreset, mEQPreset
    	Log.d(LOGTAG, ">>>onSaveInstanceState");
    	outState.putInt(PREFTAG_AUDIOSESSIONID, mAudioSession);
    	outState.putBoolean(PREFTAG_EFFECTS_ENABLED, mEffectsEnabled);
    	outState.putInt(PREFTAG_SELECTED_EFFECTS, mCheckedEffects);
    	outState.putInt(PREFTAG_BASSBOOST_LEVEL, mBassLevel);
    	outState.putInt(PREFTAG_VIRTUALIZER_LEVEL, mVirtualizerLevel);
    	outState.putShort(PREFTAG_REVERB_PRESET, mReverbPreset);
    	outState.putShort(PREFTAG_EQ_PRESET, mEQPreset);
    	super.onSaveInstanceState(outState);
    	Log.d(LOGTAG, "<<<onSaveInstanceState");
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	Editor ed = mPreference.edit();
    	ed.putBoolean(PREFTAG_EFFECTS_ENABLED, mEffectsEnabled);
    	ed.putInt(PREFTAG_SELECTED_EFFECTS, mCheckedEffects);
    	ed.putInt(PREFTAG_BASSBOOST_LEVEL, mBassLevel);
    	ed.putInt(PREFTAG_VIRTUALIZER_LEVEL, mVirtualizerLevel);
    	ed.putInt(PREFTAG_REVERB_PRESET, mReverbPreset);
    	ed.putInt(PREFTAG_EQ_PRESET, mEQPreset);
    	ed.commit();
    	Log.i(LOGTAG, "Effect settings saved to preference!!");
    }

    public class MyAdapter extends SimpleAdapter {

		public MyAdapter(Context context, List<? extends Map<String, ?>> data,
				int resource, String[] from, int[] to) {
			super(context, data, resource, from, to);
		}
    	
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);
			if (AudioEffectControlPanel.this.mEffectsEnabled == false && v != null) {
				v.findViewById(R.id.effect_name).setEnabled(false);
				v.findViewById(R.id.checkbox).setEnabled(false);
			}
			return v;
		}
    }

	public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
		Log.w(LOGTAG, "onCheckedChanged(enable effects): mEffectsEnabled=" + isChecked);
		mEffectsEnabled = isChecked;
		// Enable effects indicated by mCheckedEffects
		if ((mCheckedEffects & 1) != 0 && mBassBoost != null) {
			// BassBoost
			mBassBoost.setEnabled(isChecked);
		}
		if ((mCheckedEffects & (1 << 1)) != 0 && mVirtualizer != null) {
			// Virtualizer
			mVirtualizer.setEnabled(isChecked);
		}
		if ((mCheckedEffects & (1 << 2)) != 0 && mPresetReverb != null) {
			// PresetReverb
			mPresetReverb.setEnabled(isChecked);
		}
		if ((mCheckedEffects & (1 << 3)) != 0 && mEqualizer != null) {
			// Equalizer
			mEqualizer.setEnabled(isChecked);
		}
		mLvEffects.setEnabled(isChecked);
		int lvitemcnt = mLvEffects.getChildCount();
		View cv;
		for (int i = 0; i < lvitemcnt; ++i) {
			cv = mLvEffects.getChildAt(i);
			cv.findViewById(R.id.checkbox).setEnabled(isChecked);
			cv.findViewById(R.id.effect_name).setEnabled(isChecked);
		}
	}

	public void onClick(View v) {
		if (! (v instanceof CheckBox)) {
			return;
		}
		boolean isChecked = ((CheckBox)v).isChecked();
		Log.d(LOGTAG, ">> checkbox.onClick!! " + isChecked);
		
		// Find the clicked item
		View cv;
		int clickeditem = -1;
		for (int i = 0; i < NUM_OF_EFFECTS; ++i) {
			cv = mLvEffects.getChildAt(i);
			if (cv != null && cv.findViewById(R.id.checkbox).equals(v)) {
				Log.i(LOGTAG, "onClick: item #" + i + " is now " + (isChecked ? "CHECKED" : "UNCHECKED"));
				clickeditem = i;
				break;
			}
		}
		
		switch (clickeditem) {
		case -1:
			Log.e(LOGTAG, "Checkbox.onClick: no effects selected!!!");
			break;
		case 0:
			// BassBoost
			if (isChecked) {
				mCheckedEffects |= 1;
				Log.i(LOGTAG, "BassBoost: new checked effects=" + Integer.toHexString(mCheckedEffects));
				if (mBassBoost != null) {
					mBassBoost.setEnabled(true);
					break;
				} else {
					mBassBoost = new BassBoost(0, mAudioSession);
					if (mBassBoost.getStrengthSupported()) {
						// Default strength is 500 of 1000
						mBassBoost.setStrength((short) mBassLevel);
					}
					mBassBoost.setEnabled(true);
				}
				Log.i(LOGTAG, "BassBoost set to audio session [" + mAudioSession + "] !!");
			} else{
				if (mBassBoost != null) {
					mBassBoost.setEnabled(false);
				}
				mCheckedEffects &= ~1;
				Log.i(LOGTAG, "UNCHECK BassBoost: checked effects=" + Integer.toHexString(mCheckedEffects));
			}
			break;
			
		case 1:
			// Virtualizer
			if (isChecked) {
				mCheckedEffects |= 1 << 1;
				Log.i(LOGTAG, "Virtualizer: new checked effects=" + Integer.toHexString(mCheckedEffects));
				if (mVirtualizer != null) {
					mVirtualizer.setEnabled(true);
				} else {
					mVirtualizer = new Virtualizer(0, mAudioSession);
					if(mVirtualizer.getStrengthSupported()) {
						mVirtualizer.setStrength((short) mVirtualizerLevel);
					}
					mVirtualizer.setEnabled(true);
				}
				Log.i(LOGTAG, "Virtualizer set to audio session [" + mAudioSession + "] !!");
			} else{
				if (mVirtualizer != null) {
					mVirtualizer.setEnabled(false);
				}
				mCheckedEffects &= ~(1 << 1);
				Log.i(LOGTAG, "<<UNCHECK Virtualizer: checked effects=" + Integer.toHexString(mCheckedEffects));
			}
			break;
			
		case 2:
			// PresetReverb
			if (isChecked) {
				mCheckedEffects |= 1 << 2;
				Log.i(LOGTAG, "PresetReverb: new checked effects=" + Integer.toHexString(mCheckedEffects));
				if (mPresetReverb != null) {
					Intent it_aux = new Intent(ATTACHAUXAUDIOEFFECT);
					it_aux.putExtra("auxaudioeffectid", mPresetReverb.getId());
					sendBroadcast(it_aux);
					mPresetReverb.setEnabled(true);
					Log.i(LOGTAG, "PresetReverb set to audio session [0]!!");
				} else {
					mPresetReverb = new PresetReverb(0, 0);
					// Since PresetReverb is an auxiliary effect, 
					// attach this effect to MediaPlayer on audio session 0 (main ouput mix)
					mPresetReverb.setPreset(mReverbPreset);
					mPresetReverb.setEnabled(true);
					// Send broadcast to Music app to attach auxiliary effect to MediaPlayer instance
					Intent it_aux = new Intent(ATTACHAUXAUDIOEFFECT);
					it_aux.putExtra("auxaudioeffectid", mPresetReverb.getId());
					sendBroadcast(it_aux);
				}
				Log.i(LOGTAG, "PresetReverb set to audio session [0]!!");
			} else {
				if (mPresetReverb != null) {
					mPresetReverb.setEnabled(false);
					Intent it_aux = new Intent(DETACHAUXAUDIOEFFECT);
					it_aux.putExtra("auxaudioeffectid", mPresetReverb.getId());
					sendBroadcast(it_aux);
					Log.i(LOGTAG, "PresetReverb is disabled now!!");
				}
				mCheckedEffects &= ~(1 << 2);
				Log.i(LOGTAG, "UNCHECK Virtualizer: checked effects=" + Integer.toHexString(mCheckedEffects));
			}
			break;
			
		case 3:
			// Equalizer
			if (isChecked) {
				mCheckedEffects |= 1 << 3;
				Log.i(LOGTAG, "Equalizer: new checked effects=" + Integer.toHexString(mCheckedEffects));
				if (mEqualizer != null) {
					mEqualizer.setEnabled(true);
				} else {
					mEqualizer = new Equalizer(0, mAudioSession);
					mEqualizer.usePreset(mEQPreset);
					mEqualizer.setEnabled(true);
				}
				Log.i(LOGTAG, "Equalizer set to audio session [" + mAudioSession + "] !!");
			} else {
				if (mEqualizer != null) {
					mEqualizer.setEnabled(false);
				}
				mCheckedEffects &= ~(1 << 3);
				Log.i(LOGTAG, "UNCHECK Equalizer: checked effects=" + Integer.toHexString(mCheckedEffects));
			}
			break;
			
		default:
			Log.e(LOGTAG, "unknown audio effect selected!!");
		}
	}
    
}

