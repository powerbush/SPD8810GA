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

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.model.AudioModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.RegionModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.model.VideoModel;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;

/**
 * An utility to edit contents of a slide.
 */
public class SlideshowEditor {
    private static final String TAG = "Mms:slideshow";

    public static final int MAX_SLIDE_NUM = 20;

    private final Context mContext;
    private final SlideshowModel mModel;

    public SlideshowEditor(Context context, SlideshowModel model) {
        mContext = context;
        mModel = model;
    }

    /**
     * Add a new slide to the end of message.
     *
     * @return true if success, false if reach the max slide number.
     */
    public boolean addNewSlide() {
        int position = mModel.size();
        return addNewSlide(position);
    }

    /**
     * Add a new slide at the specified position in the message.
     *
     * @return true if success, false if reach the max slide number.
     * @throws IndexOutOfBoundsException - if position is out of range
     *         (position < 0 || position > size()).
     */
    public boolean addNewSlide(int position) {
        int size = mModel.size();
        if (size < MAX_SLIDE_NUM) {
            SlideModel slide = new SlideModel(mModel);

            TextModel text = new TextModel(
                    mContext, ContentType.TEXT_PLAIN, "text_" + size + ".txt",
                    mModel.getLayout().getTextRegion());
            slide.add(text);

            mModel.add(position, slide);
            return true;
        } else {
            Log.w(TAG, "The limitation of the number of slides is reached.");
            return false;
        }
    }

    /**
     * Remove one slide.
     *
     * @param position
     */
    public void removeSlide(int position) {
        mModel.remove(position);
    }

    /**
     * Remove all slides.
     */
    public void removeAllSlides() {
        while (mModel.size() > 0) {
            removeSlide(0);
        }
    }

    /**
     * Remove the text of the specified slide.
     *
     * @param position index of the slide
     * @return true if success, false if no text in the slide.
     */
    public boolean removeText(int position) {
        return mModel.get(position).removeText();
    }

    public boolean removeImage(int position) {
        return mModel.get(position).removeImage();
    }

    public boolean removeVideo(int position) {
        return mModel.get(position).removeVideo();
    }

    public boolean removeAudio(int position) {
        return mModel.get(position).removeAudio();
    }

    public void changeText(int position, String newText) {
        if (newText != null) {
            SlideModel slide = mModel.get(position);
            TextModel text = slide.getText();
            /*fixed CR<NEWMS00148018> by luning at 2011.12.13 begin*/
//            if (text == null) {
//                text = new TextModel(mContext,
//                        ContentType.TEXT_PLAIN, "text_" + position + ".txt",
//                        mModel.getLayout().getTextRegion());
//                text.setText(newText);
//                slide.add(text);
//            } else if (!newText.equals(text.getText())) {
//                text.setText(newText);
//            }

            int oldSize = 0;
            int newSize = 0;
            if (text == null) {
                text = new TextModel(mContext, ContentType.TEXT_PLAIN, "text_" + position + ".txt",
                        mModel.getLayout().getTextRegion());
                slide.add(text);
                newSize = newText.getBytes().length;
            } else if (!newText.equals(text.getText())) {
                oldSize = text.getMediaSize();
                newSize = newText.getBytes().length;
            }
            if (null != mModel) {
                int finalSize = mModel.getTotalMsgSizeWithAllHead(mModel.getTotalMessageSize()) - oldSize + newSize;
                String message = getResourcesString(R.string.failed_to_add_media, getTextString());
                if (finalSize > MmsConfig.getPduMaxTotalSize()) {
                    MessageUtils.showErrorDialog(mContext, mContext
                            .getString(R.string.exceed_message_size_limitation),message);
                    slide.updateText(text.getText());
                    return;
                }
            }
            if (newText.length() > MmsConfig.getmMaxTextLength()) {
                Toast.makeText(mContext,
                        mContext.getString(R.string.exceed_text_length_limitation),
                        Toast.LENGTH_SHORT).show();
                slide.updateText(newText.substring(0, MmsConfig.getmMaxTextLength()));
                return;
            }
            slide.updateText(newText);
            /*fixed CR<NEWMS00148018> by luning at 2011.12.13 end*/
        }
    }

    private String getResourcesString(int id, String mediaName) {
        Resources r = mContext.getResources();
        return r.getString(id, mediaName);
    }

    private String getResourcesString(int id) {
        Resources r = mContext.getResources();
        return r.getString(id);
    }

    private String getTextString() {
        return getResourcesString(R.string.type_text);
    }

    public void changeImage(int position, Uri newImage) throws MmsException {
        mModel.get(position).add(new ImageModel(
                mContext, newImage, mModel.getLayout().getImageRegion()));
    }

    public void changeAudio(int position, Uri newAudio) throws MmsException {
        AudioModel audio = new AudioModel(mContext, newAudio);
        SlideModel slide = mModel.get(position);
        slide.add(audio);
        slide.updateDuration(audio.getDuration());
    }

    public void changeVideo(int position, Uri newVideo) throws MmsException {
        VideoModel video = new VideoModel(mContext, newVideo,
                mModel.getLayout().getImageRegion());
        SlideModel slide = mModel.get(position);
        slide.add(video);
        slide.updateDuration(video.getDuration());
    }

    public void moveSlideUp(int position) {
        mModel.add(position - 1, mModel.remove(position));
    }

    public void moveSlideDown(int position) {
        mModel.add(position + 1, mModel.remove(position));
    }

    public void changeDuration(int position, int dur) {
        if (dur >= 0) {
            mModel.get(position).setDuration(dur);
        }
    }

    public void changeLayout(int layout) {
        mModel.getLayout().changeTo(layout);
    }

    public RegionModel getImageRegion() {
        return mModel.getLayout().getImageRegion();
    }

    public RegionModel getTextRegion() {
        return mModel.getLayout().getTextRegion();
    }
}
