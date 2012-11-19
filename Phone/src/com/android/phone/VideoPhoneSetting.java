
package com.android.phone;

import java.util.ArrayList;
import android.app.Activity;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.sql.Date;
import android.provider.MediaStore;
import android.net.Uri;
import android.widget.Toast;
import android.media.MediaScannerConnection;
import android.database.Cursor;

public class VideoPhoneSetting extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "VideoPhoneSetting";

    static final String FAKE_LOCAL_IMAGE = "thumb_local.jpg";
    static final String FAKE_REMOTE_IMAGE = "thumb_remote.jpg";

    static final String KEY_BUNDLE_URI = "uri";
	
    static final String KEY_SET_REMOTE_STATIC_IMAGE = "static_remote_image_set_key";
    static final String KEY_SET_LOCAL_STATIC_IMAGE = "static_local_image_set_key";
    static final String KEY_SCREEN_LAYOUT = "layout_key";
    static final String KEY_FALLBACK = "fallback_key";
    static final String KEY_STATIC_IMAGE = "static_image_enable_key";
	static final String KEY_LOCAL_STATIC_IMAGE_PATH = "static_local_image_path";
	static final String KEY_REMOTE_STATIC_IMAGE_PATH = "static_remote_image_path";
	static final String DEF_SUBSTITUTE_IMAGE_PATH =  "/system/opl/etc/vt_substitute.jpg";
    
    private ListPreference mRemoteStaticImage;
    private ListPreference mLocalStaticImage;
    private ListPreference mScreenLayout;
    private ListPreference mFallBack;

    private static final int PHOTO_PICKED_WITH_DATA = 3021;
    private static final int CAMERA_WITH_DATA = 3023;
    private static final File PHOTO_DIR = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
    
    private File mCurrentPhotoFile;
    private Uri mCurrentPhotoUri;
    
    private ListPreference CurrentStaticImageSelect;
    
    static String StaticImagePath  = null ;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.videophone_setting);
        
        mRemoteStaticImage =  (ListPreference) findPreference(KEY_SET_REMOTE_STATIC_IMAGE);
        mLocalStaticImage =  (ListPreference) findPreference(KEY_SET_LOCAL_STATIC_IMAGE);
        mScreenLayout = (ListPreference) findPreference(KEY_SCREEN_LAYOUT);
        mFallBack = (ListPreference) findPreference(KEY_FALLBACK);
        mScreenLayout.setOnPreferenceChangeListener(this);
        mFallBack.setOnPreferenceChangeListener(this);
        mRemoteStaticImage.setOnPreferenceChangeListener(this);
        mLocalStaticImage.setOnPreferenceChangeListener(this);
	if (savedInstanceState != null){
		mCurrentPhotoUri = savedInstanceState.getParcelable(KEY_BUNDLE_URI);
		Log.w(TAG, "onCreate, mCurrentPhotoUri: " + mCurrentPhotoUri);
	}
    }
	
    @Override
    protected void onResume() {
        super.onResume();

		updateScreenLayoutSummary(mScreenLayout.getValue());
		updateFallBackSummary(mFallBack.getValue());
		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		String tempFn = null;
		tempFn = mPrefs.getString(KEY_LOCAL_STATIC_IMAGE_PATH, DEF_SUBSTITUTE_IMAGE_PATH);
		if (tempFn.compareTo(DEF_SUBSTITUTE_IMAGE_PATH) == 0) {
			tempFn = getResources().getString(R.string.videophone_setting_default_image);
		}
		mLocalStaticImage.setSummary(tempFn);
		tempFn = mPrefs.getString(KEY_REMOTE_STATIC_IMAGE_PATH, DEF_SUBSTITUTE_IMAGE_PATH);
		if (tempFn.compareTo(DEF_SUBSTITUTE_IMAGE_PATH) == 0) {
			tempFn = getResources().getString(R.string.videophone_setting_default_image);
		}
		mRemoteStaticImage.setSummary(tempFn);
    }

	 @Override
	 protected void onSaveInstanceState(Bundle outState) {
	 	super.onSaveInstanceState(outState);
	 	Log.w(TAG, "onSaveInstanceState, mCurrentPhotoUri: " + mCurrentPhotoUri);
		outState.putParcelable(KEY_BUNDLE_URI, mCurrentPhotoUri);
	 }
	 
	private void updateScreenLayoutSummary(Object value) {
        CharSequence[] summaries = getResources().getTextArray(R.array.videophone_setting_layout_summaries);
        CharSequence[] values = mScreenLayout.getEntryValues();
        for (int i=0; i<values.length; i++) {
            Log.w("VideoPhoneSetting", "Comparing mScreenLayout entry "+ values[i] + " to current "
                    + mScreenLayout.getValue());
            if (values[i].equals(value)) {
                mScreenLayout.setSummary(summaries[i]);
                break;
            }
        }
    }	
	private void updateFallBackSummary(Object value) {
        CharSequence[] summaries = getResources().getTextArray(R.array.videophone_setting_fallback_summaries);
        CharSequence[] values = mFallBack.getEntryValues();
        for (int i=0; i<values.length; i++) {
            Log.w("VideoPhoneSetting", "Comparing mFallBack entry "+ values[i] + " to current "
                    + mFallBack.getValue());
            if (values[i].equals(value)) {
            	mFallBack.setSummary(summaries[i]);
                break;
            }
        }
    }
	
	

    private String getPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
        return dateFormat.format(date) + ".jpg";
    }
    
    public static Intent getTakePickIntent(File f) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        return intent;
    }

	private void updateSetStaticImagekSummary(Object value,ListPreference mStaticImage) {
       // CharSequence[] summaries = getResources().getTextArray(R.array.videophone_setting_fallback_summaries);
        CharSequence[] values = mStaticImage.getEntryValues();
        Log.i(TAG,"updateSetStaticImagekSummary   "+value);
        if(value.equals("1") == true)
        {
   			Log.i(TAG,"updateSetStaticImagekSummary  take photo ");
   			
   	        try 
   	        {
   	            PHOTO_DIR.mkdirs();
   	            mCurrentPhotoFile = new File(PHOTO_DIR, getPhotoFileName());
			if(mCurrentPhotoFile == null)
			{
			         Log.i(TAG,"updateSetStaticImagekSummary  mCurrentPhotoFile == null ");
			         Toast.makeText(this, R.string.videophone_setting_photoPickerNotFoundText, Toast.LENGTH_LONG).show();
				 return ;
			}
			else
			{
			    mCurrentPhotoUri = Uri.fromFile(mCurrentPhotoFile);
			    Log.i(TAG,"updateSetStaticImagekSummary  mCurrentPhotoFile "+ mCurrentPhotoUri);
			    if (mCurrentPhotoUri == null) {
					return;
			    }
			}
   	            final Intent intent = getTakePickIntent(mCurrentPhotoFile);
   	            startActivityForResult(intent, CAMERA_WITH_DATA);
   	        } 
   	        catch (ActivityNotFoundException e) 
   	        {
   	            Toast.makeText(this, R.string.videophone_setting_photoPickerNotFoundText, Toast.LENGTH_LONG).show();
   	        }
        }
        else if(value.equals("2") == true)
        {
   			Log.i(TAG,"updateSetStaticImagekSummary  pick photo ");
   	        try 
   	        {
   	            final Intent intent = getPhotoPickIntent();
   	            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
   	        } 
   	        catch (ActivityNotFoundException e)
   	        {
   	   		Log.i(TAG,"doPickPhotoFromGallery ActivityNotFoundException   ");
			Toast.makeText(this, R.string.videophone_setting_photoPickerNotFoundText, Toast.LENGTH_LONG).show();
   	        }
   	    }
        else
        {
		Log.i(TAG,"updateSetStaticImagekSummary  delete photo ");
		StaticImagePath = getResources().getString(R.string.videophone_setting_default_image);
		if (CurrentStaticImageSelect == mLocalStaticImage){
			saveToPreference(KEY_LOCAL_STATIC_IMAGE_PATH, DEF_SUBSTITUTE_IMAGE_PATH);
		} else {
			saveToPreference(KEY_REMOTE_STATIC_IMAGE_PATH, DEF_SUBSTITUTE_IMAGE_PATH);
		}
     		mStaticImage.setSummary(StaticImagePath);
        }
	}
	
	
	public boolean onPreferenceChange(Preference preference, Object objValue) {
		final String key = preference.getKey();
		Log.w("VideoPhoneSetting", "onPreferenceChange(), " + key);
		if (KEY_SCREEN_LAYOUT.equals(key)) {
            updateScreenLayoutSummary(objValue);
        }
		else if (KEY_FALLBACK.equals(key)) {
            updateFallBackSummary(objValue);
        }
        else if(KEY_SET_REMOTE_STATIC_IMAGE.equals(key))
        {
        	CurrentStaticImageSelect = mRemoteStaticImage;
        	updateSetStaticImagekSummary(objValue,CurrentStaticImageSelect);
        }else if(KEY_SET_LOCAL_STATIC_IMAGE.equals(key))
        {
        	CurrentStaticImageSelect = mLocalStaticImage;
        	updateSetStaticImagekSummary(objValue,CurrentStaticImageSelect);
        }
		return true;
	}

	

    
    public static Intent getPhotoPickIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        return intent;
    }
    
    
	public String getPath(Uri uri) {
		String path = "";
		String[] projection={MediaStore.Images.Media.DATA};
	    Cursor cursor=managedQuery(uri,projection,null,null,null);
		try {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			if(cursor.moveToFirst()){
				path = cursor.getString(column_index);
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	    return path;
	}

	private boolean saveToPreference(String key, String value) {
		Log.w(TAG, "saveToPreference(" + key +", " + value + ")");

        if (0 == key.compareTo(KEY_LOCAL_STATIC_IMAGE_PATH)) {
    	    createThumb(getApplicationContext(), value, (0 == value.compareTo(DEF_SUBSTITUTE_IMAGE_PATH)), FAKE_LOCAL_IMAGE);
        } else if (0 == key.compareTo(KEY_REMOTE_STATIC_IMAGE_PATH)) {
    	    createThumb(getApplicationContext(), value, (0 == value.compareTo(DEF_SUBSTITUTE_IMAGE_PATH)), FAKE_REMOTE_IMAGE);
        }
        
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor mEditor = mPrefs.edit();
		
		mEditor.putString(key, value);
		mEditor.commit();
		return true;
	}


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode != RESULT_OK) return;
        
        switch (requestCode) 
        {
            case PHOTO_PICKED_WITH_DATA: 
            {
			Log.i(TAG,"onActivityResult  PHOTO_PICKED_WITH_DATA  ");

			Uri uri = data.getData();
			if(uri == null )
			{
				return ;	
			}

			String path = uri.toString();

			if(path == null )
			{
				return ;	
			}

			if(path.contains((MediaStore.Images.Media.EXTERNAL_CONTENT_URI).toString()))
			{
				File file = new File( getPath(uri));
				Log.i(TAG,"onActivityResult "+file.getPath());
				StaticImagePath =  file.getPath();
			}
			else
			{
				Log.i(TAG,"uri.getPath  "+uri.getPath());
				StaticImagePath =  uri.getPath();
			}

			if(CurrentStaticImageSelect == mLocalStaticImage)
			{
				Log.i(TAG,"mLocalStaticImage  getPath  "+StaticImagePath);
				if (saveToPreference(KEY_LOCAL_STATIC_IMAGE_PATH, StaticImagePath)) {
					mLocalStaticImage.setSummary(StaticImagePath);
				}     
			}
			else
			{
				Log.i(TAG,"mRemoteStaticImage  getPath  "+StaticImagePath);
				if (saveToPreference(KEY_REMOTE_STATIC_IMAGE_PATH, StaticImagePath)) {
					mRemoteStaticImage.setSummary(StaticImagePath);
			}
			}
            }
     
            break;
            
            case CAMERA_WITH_DATA:
            {
            	
        		Log.i(TAG,"onActivityResult  CAMERA_WITH_DATA  ");
        		
        		if(mCurrentPhotoUri == null )
        		{
				Log.e(TAG,"onActivityResult  mCurrentPhotoUri == null  ");
				return ;	
        		}
        		
         		StaticImagePath =  mCurrentPhotoUri.getPath();
         		
			if(CurrentStaticImageSelect == mLocalStaticImage)
			{
				Log.i(TAG,"mLocalStaticImage  getPath  "+StaticImagePath);
				if (saveToPreference(KEY_LOCAL_STATIC_IMAGE_PATH, StaticImagePath)) {
					mLocalStaticImage.setSummary( StaticImagePath);
				}
			}
			else
			{
				Log.i(TAG,"mRemoteStaticImage  getPath  "+StaticImagePath);
				if (saveToPreference(KEY_REMOTE_STATIC_IMAGE_PATH, StaticImagePath)) {
				    	mRemoteStaticImage.setSummary( StaticImagePath);
				}
			}
            }
             break;
        }
    }

	private int checkImageSize(String fn) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
	        BitmapFactory.decodeFile(fn, options);

		Log.i(TAG, "checkImageSize(), height: " + options.outHeight + ", width: " + options.outWidth);
		if ((options.outHeight * options.outWidth) > (640*480)) {
			return R.string.videophone_setting_picture_oversize;
		} else {
			if (options.outHeight < 64) {
				return R.string.videophone_setting_picture_heightoosmall;
			} else if (options.outWidth < 64) {
				return R.string.videophone_setting_picture_widthtoosmall;
			}
		}
		return 0;
	}
	
	private boolean isSurffixJPG(String str){
		int len = str.length();
		if (len<=5)
			return false;
		return ((str.regionMatches(true, len - 4, ".jpg", 0, 4)) 
			|| (str.regionMatches(true, len - 5, ".jpeg", 0, 5)));
	}
    
    static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            Log.e(TAG, "cannot read exif", ex);
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }
        return degree;
    }

    static boolean createThumb(Context context, String srcFn, boolean bDefaultPic, String destFn) {
        Matrix mMatrix = new Matrix();
        Bitmap mBitmapSrc = null;
        int degree = 0;

        if (bDefaultPic) {
            mBitmapSrc = BitmapFactory.decodeResource(context.getResources(), R.drawable.picture_unknown);
        } else {
            mBitmapSrc = BitmapFactory.decodeFile(srcFn);
            degree = getExifOrientation(srcFn);
        }
        
        int width = mBitmapSrc.getWidth();
        int height = mBitmapSrc.getHeight();
        Log.i(TAG, "createThumb(), degree: " + degree + ", mBitmapSrc width: " + width + ", height: " + height);
        
        float fWidth = (float)width;
        float fHeight = (float)height;
        mMatrix.reset();
        mMatrix.postRotate(degree);

        float sx = 0;
        float sy = 0;
        if ((degree == 0) || (degree == 180)) {
            sx = 176/fWidth;
            sy = 144/fHeight;
        } else {
            sx = 176/fHeight;
            sy = 144/fWidth;
        }
        Log.i(TAG, "createThumb(), fWidth: " + fWidth + ", fHeight: " + fHeight + ", sx: " + sx + ", sy: " + sy);
        mMatrix.postScale(sx,sy);
        
        Bitmap mBitmapDest = Bitmap.createBitmap(mBitmapSrc, 0, 0, width, height, mMatrix, true);
        mBitmapSrc.recycle(); // need recycle bitmap after use
        try {
            saveMyBitmap(context, mBitmapDest, destFn);
        } catch (Exception e){
            e.printStackTrace();
        }
        mBitmapDest.recycle();
        return true;
    }
    
	static void saveMyBitmap(Context context, Bitmap image, String bitName) throws IOException {
        //File f = new File("/data/" + bitName + ".bmp");
        //f.createNewFile();
        FileOutputStream fOut = null;
        try {
                //fOut = new FileOutputStream(f);                
            fOut = context.openFileOutput(bitName, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
                e.printStackTrace();
        }
        image.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        try {
                fOut.flush();
        } catch (IOException e) {
                e.printStackTrace();
        }
        try {
                fOut.close();
        } catch (IOException e) {
                e.printStackTrace();
        }
    }
}
