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

import com.android.mms.R;
import android.media.MediaMetadataRetriever;        // TODO: remove dependency for SDK build

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

/**
 * This class provides an embedded editor/viewer of video attachment.
 */
public class VideoAttachmentView extends LinearLayout implements
        SlideViewInterface {
    private static final String TAG = "VideoAttachmentView";

    private ImageView mThumbnailView;

    public VideoAttachmentView(Context context) {
        super(context);
    }

    public VideoAttachmentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mThumbnailView = (ImageView) findViewById(R.id.video_thumbnail);
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
        mSize = (TextView) findViewById(R.id.video_size);
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 end =====
    }

    public void startAudio() {
        // TODO Auto-generated method stub
    }

    public void startVideo() {
        // TODO Auto-generated method stub
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        // TODO Auto-generated method stub
    }

    public void setImage(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub
    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setText(String name, String text) {
        // TODO Auto-generated method stub
    }

    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setVideo(String name, Uri video) {
        try {
            Bitmap bitmap = createVideoThumbnail(mContext, video);
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_video);
            }
            mThumbnailView.setImageBitmap(bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setVideo: out of memory: ", e);
        }
    }

    public static Bitmap createVideoThumbnail(Context context, Uri uri) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            bitmap = retriever.getFrameAtTime(-1);
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        return bitmap;
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
        // TODO Auto-generated method stub
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
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

    public void setVcard(Uri uri, String name)
    {
        // TODO Auto-generated method stub
    }
	public void setImage(Uri uri, Bitmap bitmap, boolean isGifImage) {
		// TODO Auto-generated method stub
		
	}
	
	//===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
    private TextView mSize;	
	public void setSize(String size) {
		mSize.setText(size);			
	}
	//===== fixed CR<NEWSM00125959> by luning at 11-09-26 end =====
	
	/* fixed CR<NEWMS119944 NEWMS119757 NEWMS119755 NEWMS120030 NEWMS119256> by lino release memory end */
	public void destroy(){
		Bitmap bt = null;
		if(mThumbnailView != null){
			BitmapDrawable bd = (BitmapDrawable)mThumbnailView.getDrawable();
			if(bd != null){
				bt = bd.getBitmap();
				if(bt != null && !bt.isRecycled()){
					bt.recycle();
					bt = null;
					mThumbnailView.setImageDrawable(null);
					mThumbnailView.setImageBitmap(null);
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

    @Override
    public void setFile(Uri uri, String name) {
        // TODO Auto-generated method stub
        
    }
}
