package com.android.music;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.content.Intent;
import android.drm.*;
import android.util.Log;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.provider.MediaStore;
import android.net.Uri;

import com.mediatek.featureoption.FeatureOption;


public class AudioPreviewStarter extends Activity
		implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
	
	public static final String LOGTAG = "AudioPreviewStarter";
	
	private DrmManagerClient mDrmClient = null;
	
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.w(LOGTAG, ">> handleMessage");
			finish();
			Log.w(LOGTAG, "<< handleMessage");
		}
	};
	
	@Override
	public void onCreate(Bundle icicle) {
		Log.d(LOGTAG, ">> onCreate");
		super.onCreate(icicle);
		Uri uri = getIntent().getData();
		
		if (uri == null) {
			Log.d(LOGTAG, "onCreate, uri is NULL");
			finish();
			return;
		}
		Log.d(LOGTAG, "onCreate, uri=" + uri);
		
		Intent i = new Intent(getIntent());
		i.setClass(this, AudioPreview.class);	// change to explicit intent

		if (!FeatureOption.MTK_DRM_APP) {
			Log.w(LOGTAG, "onCreate, !MTK_DRM_APP");
			startActivity(i);
			finish();
			return;
		}
		
		mDrmClient = new DrmManagerClient(this);
		int rightsStatus = -1;
		
		
		String scheme = uri.getScheme();
		String host = uri.getHost();
		Log.d(LOGTAG, "onCreate, scheme=" + scheme + ", host=" + host);
		ContentResolver resolver = getContentResolver();
		if ("content".equals(scheme)) {
			if ("media".equals(host)) {
				// query DB for drm info
				Cursor c = resolver.query(uri, 
						new String[] {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.IS_DRM, MediaStore.Audio.Media.DRM_METHOD}, 
						null, 
						null, 
						null);
				Log.d(LOGTAG, "onCreate, cursor=" + c);
				if (c != null) {
					if (c.moveToFirst()) {
						// cursor is valid
						int is_drm = c.getInt(1);
						Log.d(LOGTAG, "onCreate, is_drm=" + is_drm);
						if (is_drm == 1) {
							// is a DRM file
							int drm_method = c.getInt(2);
							Log.d(LOGTAG, "onCreate, drm_method=" + drm_method);
							if (drm_method == DrmStore.DrmMethod.METHOD_FL) {
								// FL does not have constraints
								startActivity(i);
								finish();
								return;
							}
							rightsStatus = mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY);
							Log.d(LOGTAG, "onCreate, rightsStatus=" + rightsStatus);
							switch (rightsStatus) {
							case DrmStore.RightsStatus.RIGHTS_VALID:
								mDrmClient.showConsumeDialog(this, this, this);
								break;
							case DrmStore.RightsStatus.RIGHTS_INVALID:
								mDrmClient.showLicenseAcquisitionDialog(this, uri, this);
								if (drm_method == DrmStore.DrmMethod.METHOD_CD) {
									finish();
									return;
								}
								break;
							case DrmStore.RightsStatus.SECURE_TIMER_INVALID:
								mDrmClient.showSecureTimerInvalidDialog(this, null, this);
								break;
							default:
							}
						} else {
							startActivity(i);
							finish();
							return;
						}
					} else {
						startActivity(i);
						finish();
						return;
					}
					c.close();
				} else {
					startActivity(i);
					finish();
					return;
				}
				
			} else {
				startActivity(i);
				finish();
				return;
			}
		} else if ("file".equals(scheme)) {
			// a file opened from FileManager/ other app
			String path = uri.getPath();
			Log.d(LOGTAG, "onCreate, file path=" + path);
			if (path == null) {
				finish();
				return;
			}
			if (path.toLowerCase().endsWith(".dcf")) {
				// we consider this to be a DRM file
				// check for FL first
				int drm_method = mDrmClient.getMethod(uri);
				Log.d(LOGTAG, "onCreate, drm_method=" + drm_method);
				if (drm_method == DrmStore.DrmMethod.METHOD_FL) {
					startActivity(i);
					finish();
					return;
				}
				rightsStatus = mDrmClient.checkRightsStatusForTap(uri, DrmStore.Action.PLAY);
				switch (rightsStatus) {
				case DrmStore.RightsStatus.RIGHTS_VALID:
					mDrmClient.showConsumeDialog(this, this, this);
					break;
				case DrmStore.RightsStatus.RIGHTS_INVALID:
					mDrmClient.showLicenseAcquisitionDialog(this, uri, this);
					if (drm_method == DrmStore.DrmMethod.METHOD_CD) {
						finish();
						return;
					}
					break;
				case DrmStore.RightsStatus.SECURE_TIMER_INVALID:
					mDrmClient.showSecureTimerInvalidDialog(this, null, this);
					break;
				default:
				}
			} else {
				startActivity(i);
				finish();
				return;
			}
		} else {
			startActivity(i);
			finish();
			return;
		}
		
		Log.d(LOGTAG, "<< onCreate");
	}
	
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			// continue to play
			Log.d(LOGTAG, "onClick: BUTTON_POSITIVE");
			Intent i = new Intent(getIntent());
			i.setClass(this, AudioPreview.class);	// change to explicit intent
			startActivity(i);
		} else if (which == DialogInterface.BUTTON_NEGATIVE) {
			// do nothing but finish self
			Log.d(LOGTAG, "onClick: BUTTON_NEGATIVE");
		} else {
			Log.e(LOGTAG, "undefined button on DRM consume dialog!");
		}
		//dialog.dismiss();
		//finish();
	}
	
	public void onDismiss(DialogInterface dialog) {
		Log.d(LOGTAG, ">> onDismiss");
		mHandler.sendEmptyMessage(1);
		Log.d(LOGTAG, "<< onDismiss");
	}
	
	public void onPause() {
		Log.d(LOGTAG, ">> onPause");
		super.onPause();
		Log.d(LOGTAG, "<< onPause");
	}
	
	public void onStop() {
		Log.d(LOGTAG, ">> onStop");
		super.onStop();
		Log.d(LOGTAG, "<< onStop");
	}
	
	public void onDestroy() {
		Log.d(LOGTAG, ">> onDestroy");
		super.onDestroy();
		Log.d(LOGTAG, "<< onDestroy");
	}
}
