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

import java.util.Iterator;
import com.android.mms.R;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.mms.MmsConfig;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;

/**
 * This is an embedded editor/view to add photos and sound/video clips
 * into a multimedia message.
 */
public class AttachmentEditor extends LinearLayout {
    private static final String TAG = "AttachmentEditor";

    static final int MSG_EDIT_SLIDESHOW   = 1;
    static final int MSG_SEND_SLIDESHOW   = 2;
    static final int MSG_PLAY_SLIDESHOW   = 3;
    static final int MSG_REPLACE_IMAGE    = 4;
    static final int MSG_REPLACE_VIDEO    = 5;
    static final int MSG_REPLACE_AUDIO    = 6;
    static final int MSG_PLAY_VIDEO       = 7;
    static final int MSG_PLAY_AUDIO       = 8;
    static final int MSG_VIEW_IMAGE       = 9;
    static final int MSG_REMOVE_ATTACHMENT = 10;
    // ======fixed CR<NEWMS00120798> by luning at 2011.11.09 begin======
    static final int MSG_VIEW_VCARD = 11;
    // ======fixed CR<NEWMS00120798> by luning at 2011.11.09 end======
    

    private final Context mContext;
    private Handler mHandler;

    private SlideViewInterface mView;
    private SlideshowModel mSlideshow;
    private Presenter mPresenter;
    private boolean mCanSend;
    private Button mSendButton;

    public AttachmentEditor(Context context, AttributeSet attr) {
        super(context, attr);
        mContext = context;
    }

    public void update(WorkingMessage msg) {
        hideView();
        mView = null;

        // If there's no attachment, we have nothing to do.
        if (!msg.hasAttachment()) {
            return;
        }

        // Get the slideshow from the message.
        mSlideshow = msg.getSlideshow();

        mView = createView();
        if (mView == null) {
            Log.i("TAG", "AttachmentEditor update, mView is null, return");
            return;
        }
    
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
        String currentSize = Formatter.formatFileSize(mContext, mSlideshow.getTotalMsgSizeWithAllHead());
        String maxSize = Formatter.formatFileSize(mContext, MmsConfig.getPduMaxTotalSize());
        StringBuffer size = new StringBuffer();
        size.append(currentSize).append("/").append(maxSize);
        mView.setSize(size.toString());
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 end =====

        if ((mPresenter == null) || !mSlideshow.equals(mPresenter.getModel())) {
            mPresenter = PresenterFactory.getPresenter(
                    "MmsThumbnailPresenter", mContext, mView, mSlideshow);
        } else {
            mPresenter.setView(mView);
        }

        mPresenter.present();
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    public void setCanSend(boolean enable) {
        if (mCanSend != enable) {
            mCanSend = enable;
            updateSendButton();
        }
    }

    private void updateSendButton() {
        if (null != mSendButton) {
            mSendButton.setEnabled(mCanSend);
            mSendButton.setFocusable(mCanSend);
        }
    }

    public void hideView() {
        if (mView != null) {
            ((View)mView).setVisibility(View.GONE);
        }
    }

    private View getStubView(int stubId, int viewId) {
        View view = findViewById(viewId);
        if (view == null) {
            ViewStub stub = (ViewStub) findViewById(stubId);
            view = stub.inflate();
        }

        return view;
    }

    private class MessageOnClick implements OnClickListener {
        private int mWhat;

        public MessageOnClick(int what) {
            mWhat = what;
        }

        public void onClick(View v) {
            Message msg = Message.obtain(mHandler, mWhat);
            msg.sendToTarget();
        }
    }

    private SlideViewInterface createView() {
        boolean inPortrait = inPortraitMode();
        if (mSlideshow.size() > 1) {
            return createSlideshowView(inPortrait);
        }

        SlideModel slide = mSlideshow.get(0);
        // fixed CR<NEWMS00150391>
        if (slide == null || slide.getSlideSize() == 0) {
            return null;
        }
      	// ======fixed CR<NEWMS00110179> by luning at 11-08-12 begin======
        if(slide.hasImage() && slide.hasAudio()){
        	 return createSlideshowView(inPortrait);
        }
        // ======fixed CR<NEWMS00110179> by luning at 11-08-12 end======
        if (slide.hasImage()) {
            return createMediaView(
                    inPortrait ? R.id.image_attachment_view_portrait_stub :
                        R.id.image_attachment_view_landscape_stub,
                    inPortrait ? R.id.image_attachment_view_portrait :
                        R.id.image_attachment_view_landscape,
                    R.id.view_image_button, R.id.replace_image_button, R.id.remove_image_button,
                    MSG_VIEW_IMAGE, MSG_REPLACE_IMAGE, MSG_REMOVE_ATTACHMENT);
        } else if (slide.hasVideo()) {
            return createMediaView(
                    inPortrait ? R.id.video_attachment_view_portrait_stub :
                        R.id.video_attachment_view_landscape_stub,
                    inPortrait ? R.id.video_attachment_view_portrait :
                        R.id.video_attachment_view_landscape,
                    R.id.view_video_button, R.id.replace_video_button, R.id.remove_video_button,
                    MSG_PLAY_VIDEO, MSG_REPLACE_VIDEO, MSG_REMOVE_ATTACHMENT);
        } else if (slide.hasAudio()) {
            return createMediaView(
                    inPortrait ? R.id.audio_attachment_view_portrait_stub :
                        R.id.audio_attachment_view_landscape_stub,
                    inPortrait ? R.id.audio_attachment_view_portrait :
                        R.id.audio_attachment_view_landscape,
                    R.id.play_audio_button, R.id.replace_audio_button, R.id.remove_audio_button,
                    MSG_PLAY_AUDIO, MSG_REPLACE_AUDIO, MSG_REMOVE_ATTACHMENT);
        } else if (slide.hasVcard()) {
            return createVcardView(inPortrait, R.id.remove_vcard_button, MSG_REMOVE_ATTACHMENT);
        } else if (mSlideshow.hasVcard()) {//fixed bug 9748,at 20120202
            return createVcardView(inPortrait, R.id.remove_vcard_button, MSG_REMOVE_ATTACHMENT);
        } else if (slide.hasOtherFile()) {
            return createFileView(R.id.remove_vcard_button, MSG_REMOVE_ATTACHMENT, slide.getOtherFile().getSrc());
        } else if (mSlideshow.hasOtherFile()) {
            return createFileView(R.id.remove_vcard_button, MSG_REMOVE_ATTACHMENT, mSlideshow.mFiles.get(0).getSrc());
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * What is the current orientation?
     */
    private boolean inPortraitMode() {
        final Configuration configuration = mContext.getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private SlideViewInterface createMediaView(
            int stub_view_id, int real_view_id,
            int view_button_id, int replace_button_id, int remove_button_id,
            int view_message, int replace_message, int remove_message) {
        LinearLayout view = (LinearLayout)getStubView(stub_view_id, real_view_id);
        view.setVisibility(View.VISIBLE);

        Button viewButton = (Button) view.findViewById(view_button_id);
        Button replaceButton = (Button) view.findViewById(replace_button_id);
        Button removeButton = (Button) view.findViewById(remove_button_id);

        viewButton.setOnClickListener(new MessageOnClick(view_message));
        replaceButton.setOnClickListener(new MessageOnClick(replace_message));
        removeButton.setOnClickListener(new MessageOnClick(remove_message));

        return (SlideViewInterface) view;
    }

    private SlideViewInterface createSlideshowView(boolean inPortrait) {
        LinearLayout view =(LinearLayout) getStubView(inPortrait ?
                R.id.slideshow_attachment_view_portrait_stub :
                R.id.slideshow_attachment_view_landscape_stub,
                inPortrait ? R.id.slideshow_attachment_view_portrait :
                    R.id.slideshow_attachment_view_landscape);
        view.setVisibility(View.VISIBLE);

        Button editBtn = (Button) view.findViewById(R.id.edit_slideshow_button);
        mSendButton = (Button) view.findViewById(R.id.send_slideshow_button);
        updateSendButton();
        final ImageButton playBtn = (ImageButton) view.findViewById(
                R.id.play_slideshow_button);

        editBtn.setOnClickListener(new MessageOnClick(MSG_EDIT_SLIDESHOW));
        mSendButton.setOnClickListener(new MessageOnClick(MSG_SEND_SLIDESHOW));
        playBtn.setOnClickListener(new MessageOnClick(MSG_PLAY_SLIDESHOW));

        return (SlideViewInterface) view;
    }
	/* fixed CR<NEWMS119944 NEWMS119757 NEWMS119755 NEWMS120030 NEWMS119256> by lino release memory end */
    public void destroy(){
    	if(mView != null){
    		mView.destroy();
    	}
    	if(mSendButton != null){
    		mSendButton.setOnClickListener(null);
    		mSendButton = null;
    	}
    	mHandler = null;
	/*
    	Iterator<SlideModel> slideModels = null;
    	SlideModel slideModel = null;
    	if(mSlideshow != null){
    		slideModels = mSlideshow.iterator();
    		while(slideModels.hasNext()){
    			slideModel = slideModels.next();
    			slideModel.clear();
    			slideModel = null;
    		}
    		slideModels = null;
    	}
    	mSlideshow = null;
    	mPresenter = null;
	*/
    }

    private SlideViewInterface createVcardView(boolean inPortrait, int remove_vcard_button, int remove_message) {
        LinearLayout view =(LinearLayout) getStubView(inPortrait ?
                R.id.vcard_attachment_view_portrait_stub :
                R.id.vcard_attachment_view_landscape_stub,
                inPortrait ? R.id.vcard_attachment_view_portrait :
                    R.id.vcard_attachment_view_landscape);
        // fixed CR<NEWMS00210974>
        if (view != null) {
            view.setVisibility(View.VISIBLE);

            Button removeButton = (Button) view.findViewById(remove_vcard_button);
            removeButton.setOnClickListener(new MessageOnClick(remove_message));

            return (SlideViewInterface) view;
        } else {
            return null;
        }
    }

    private SlideViewInterface createFileView( int remove_vcard_button, int remove_message, String name) {
        FileAttachmentView view =(FileAttachmentView) getStubView(
                R.id.file_attachment_view_portrait_stub, 
                    R.id.file_attachment_view_portrait);
        view.setVisibility(View.VISIBLE);
        view.setName(name);

        Button removeButton = (Button) view.findViewById(remove_vcard_button);
        removeButton.setOnClickListener(new MessageOnClick(remove_message));

        return (SlideViewInterface) view;
    }
}
