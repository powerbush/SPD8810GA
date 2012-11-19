/*
 * Copyright (C) 2008 Esmertec AG.
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

package com.android.mms.ui;

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This is a basic view to show and edit a slide.
 */
public class BasicSlideEditorView extends LinearLayout implements
        SlideViewInterface {
    private static final String TAG = "BasicSlideEditorView";

    private ImageView mImageView;
    private Context mContext;
    private View mAudioView;
    private TextView mAudioNameView;
    private EditText mEditText;
    private boolean mOnTextChangedListenerEnabled = true;
    private OnTextChangedListener mOnTextChangedListener;

    public BasicSlideEditorView(Context context) {
        super(context);
        mContext = context;
    }

    public BasicSlideEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    public void onFinishInflate() {
        mImageView = (ImageView) findViewById(R.id.image);
        mAudioView = findViewById(R.id.audio);
        mAudioNameView = (TextView) findViewById(R.id.audio_name);
        mEditText = (EditText) findViewById(R.id.text_message);
        mEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                // TODO Auto-generated method stub
            }

            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                try{
                    if (mOnTextChangedListenerEnabled && (mOnTextChangedListener != null)) {
                        if(!TextUtils.isEmpty(s)){
                            mOnTextChangedListener.onTextChanged(s.toString());
                        }
                    }
                }catch(ExceedMessageSizeException e){
                    Log.d(TAG, "Do onTextChanged hanppend ExceedMessageSizeException :"+e.toString(), e);
                    String title = mContext.getResources().getString(R.string.exceed_message_size_limitation);
                    String message = mContext.getResources().getString(R.string.exceed_message_size_limitation);
                    MessageUtils.showErrorDialog(mContext, title, message);
                    s = "";
                    mEditText.setText(s);
                }
                
            }

            public void afterTextChanged(Editable s) {     
            }
        });
    }

    public void startAudio() {
        // TODO Auto-generated method stub
    }

    public void startVideo() {
        // TODO Auto-generated method stub
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        mAudioView.setVisibility(View.VISIBLE);
        mAudioNameView.setText(name);
    }

    public void setImage(String name, Bitmap bitmap) {
        try {
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_picture);
            }
            mImageView.setImageBitmap(bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage: out of memory: ", e);
        }
    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setText(String name, String text) {
        mOnTextChangedListenerEnabled = false;
        if ((text != null) && !text.equals(mEditText.getText().toString())) {
            mEditText.setText(text);
            mEditText.setSelection(text.length());
        }
        mOnTextChangedListenerEnabled = true;
    }

    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setVideo(String name, Uri video) {
        try {
            Bitmap bitmap = VideoAttachmentView.createVideoThumbnail(mContext, video);
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_video);
            }
            mImageView.setImageBitmap(bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setVideo: out of memory: ", e);
        }
    }

    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void stopAudio() {
        // TODO Auto-generated method stub
    }

    public void stopVideo() {
        // TODO Auto-generated method stub
    }

    public void reset() {
        mImageView.setImageDrawable(null);
        mAudioView.setVisibility(View.GONE);
        mOnTextChangedListenerEnabled = false;
        mEditText.setText("");
        mOnTextChangedListenerEnabled = true;
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setOnTextChangedListener(OnTextChangedListener l) {
        mOnTextChangedListener = l;
    }

    public interface OnTextChangedListener {
        void onTextChanged(String s);
    }

    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }

	public void setImage(Uri uri, Bitmap bitmap, boolean isGifImage) {
		//===== fixed CR<NEWMS00125849> by luning at 11-10-10 begin =====
		setImage(null, bitmap);
		//===== fixed CR<NEWMS00125849> by luning at 11-10-10 end =====
	}

	public void setSize(String size) {
		// TODO Auto-generated method stub

	}

    public void setVcard(Uri audio, String name)
    {
        //mEditText.setText(R.string.slide_show_vcard);
        setImage(name, BitmapFactory.decodeResource(getResources(), R.drawable.vcf));
    }

    @Override
    public void setFile(Uri uri, String name) {
        setImage(name, BitmapFactory.decodeResource(getResources(), MessageUtils.getFileIconId(uri.toString())));
    }

	public void destroy(){
		Bitmap bt = null;
		if(mImageView != null){
			BitmapDrawable bd = (BitmapDrawable)mImageView.getDrawable();
			if(bd != null){
				bt = bd.getBitmap();
				if(bt != null && !bt.isRecycled()){
					bt.recycle();
					bt = null;
					mImageView.setImageDrawable(null);
			    	mImageView.setImageBitmap(null);
				}
			}
			bd = null;
		}
	}

	@Override
	public void setFile(ArrayList files) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setVcard(ArrayList vCards) {
		// TODO Auto-generated method stub
		
	}
}
