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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

/**
 * This class provides an embedded editor/viewer of audio attachment.
 */
public class FileAttachmentView extends LinearLayout implements
        SlideViewInterface {

    private TextView mNameView;
    private TextView mErrorMsgView;
    private ImageView mIcon;


    public FileAttachmentView(Context context) {
        super(context);
    }

    public FileAttachmentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mNameView = (TextView) findViewById(R.id.file_name);
        mErrorMsgView = (TextView) findViewById(R.id.file_error_msg);
        mIcon = (ImageView)findViewById(R.id.ic_file_icon);
    }

    public void startVideo() {
        // TODO Auto-generated method stub

    }

    public void setName(String name) {
        Log.d("VcardAttachment:setFile Name", "Name:"+name);
        mNameView.setText(name);
        mIcon.setImageResource(MessageUtils.getFileIconId(name));
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
        // TODO Auto-generated method stub

    }

    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }


    public void stopVideo() {
        // TODO Auto-generated method stub

    }


    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    private void showErrorMessage(String msg) {
        mErrorMsgView.setText(msg);
        mErrorMsgView.setVisibility(VISIBLE);
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

    public void setAudio(Uri audio, String name, Map<String, ?> extras)
    {
        // TODO Auto-generated method stub

    }

    public void startAudio()
    {
        // TODO Auto-generated method stub

    }

    public void stopAudio()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setImage(Uri uri, Bitmap bitmap, boolean isGifImage) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setSize(String size) {
        // TODO Auto-generated method stub
        mNameView.setText(size);
    }

    public void reset()
    {
        // TODO Auto-generated method stub

    }

    public void destroy(){
        
    }

    @Override
    public void setVcard(ArrayList vCards) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFile(ArrayList files) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setVcard(Uri uri, String name) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setFile(Uri uri, String name) {
        // TODO Auto-generated method stub
        mIcon.setBackgroundResource(MessageUtils.getFileIconId(name));
    }

}
