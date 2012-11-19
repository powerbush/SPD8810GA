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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.android.mms.R;
import com.android.mms.model.AudioModel;
import com.android.mms.model.FileModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.VcardModel;
import com.android.mms.model.VideoModel;

public class MmsThumbnailPresenter extends Presenter {
    private static final String TAG = "MmsThumbnailPresenter";

    public MmsThumbnailPresenter(Context context, ViewInterface view, Model model) {
        super(context, view, model);
    }

    @Override
    public void present() {
        SlideModel slide = ((SlideshowModel) mModel).get(0);
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
        if(mView != null){       	
        	mView.reset();
        }
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
        if (slide != null) {
            presentFirstSlide((SlideViewInterface) mView, slide);
        }
        
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
        if (((SlideshowModel) mModel).hasVcards()) {
            presentVcardThumbnail((SlideViewInterface) mView, ((SlideshowModel) mModel).mVcards);
        }
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
        

        if(((SlideshowModel) mModel).hasOtherFile()){ /* fixed CR<NEWMS00144166> by luning at 2011.11.28 begin*/
            presentFileThumbnail((SlideViewInterface) mView,((SlideshowModel) mModel).mFiles);
        }
    }

    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
    private void presentVcardThumbnail(SlideViewInterface view, ArrayList<VcardModel> vCards) {
    	view.setVcard(vCards);
    }
    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
    
    /**
     *  fixed CR<NEWMS00144166> by luning at 2011.11.28 
     */
    private void presentFileThumbnail(SlideViewInterface view, ArrayList<FileModel> files){
        view.setFile(files);
    }
     
    private void presentFirstSlide(SlideViewInterface view, SlideModel slide) {
    	//===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
//        view.reset();
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
        if (slide.hasImage()) {
            presentImageThumbnail(view, slide.getImage());
        } else if (slide.hasVideo()) {
            presentVideoThumbnail(view, slide.getVideo());
        } else if (slide.hasAudio()) {
            presentAudioThumbnail(view, slide.getAudio());
        }
    }

    private void presentVideoThumbnail(SlideViewInterface view, VideoModel video) {
        if (video.isDrmProtected()) {
            showDrmIcon(view, video.getSrc());
        } else {
            view.setVideo(video.getSrc(), video.getUri());
        }
    }

    private void presentImageThumbnail(SlideViewInterface view, ImageModel image) {
        if (image.isDrmProtected()) {
            showDrmIcon(view, image.getSrc());
        } else {
            view.setImage(image.getSrc(), image.getBitmap());
        }
    }

    protected void presentAudioThumbnail(SlideViewInterface view, AudioModel audio) {
        if (audio.isDrmProtected()) {
            showDrmIcon(view, audio.getSrc());
        } else {
            view.setAudio(audio.getUri(), audio.getSrc(), audio.getExtras());
        }
    }

    // Show an icon instead of real content in the thumbnail.
    private void showDrmIcon(SlideViewInterface view, String name) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(
                    mContext.getResources(), R.drawable.ic_mms_drm_protected);
            view.setImage(name, bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "showDrmIcon: out of memory: ", e);
        }
    }

    public void onModelChanged(Model model, boolean dataChanged) {
        // TODO Auto-generated method stub
    }
}
