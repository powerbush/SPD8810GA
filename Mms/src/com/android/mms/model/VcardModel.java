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

package com.android.mms.model;

import com.android.mms.ContentRestrictionException;
import com.android.mms.ExceedMessageSizeException;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.dom.smil.SmilMediaElementImpl;
import android.drm.mobile1.DrmException;
import com.android.mms.drm.DrmWrapper;
import com.android.mms.ui.UriImage;
import com.android.mms.ui.MessageUtils;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;

import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementTime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;


public class VcardModel extends MediaModel {
    private static final String TAG = "Mms/vcard";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;
    private String detail;

    private final HashMap<String, String> mExtras;

    public VcardModel(Context context, String contentType, String src, Uri uri) throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_VCARD, contentType, src, uri);
        mExtras = new HashMap<String, String>();
    }

    public VcardModel(Context context, String contentType, String src,
            DrmWrapper wrapper) throws IOException {
        super(context, SmilHelper.ELEMENT_TAG_VCARD, contentType, src, wrapper);
        mExtras = new HashMap<String, String>();
    }

    public VcardModel(Context context, String contentType, String src,
           byte[] data) throws IOException {
        super(context, SmilHelper.ELEMENT_TAG_VCARD, contentType, src, data);
        mExtras = new HashMap<String, String>();
    }

    // EventListener Interface
    public void handleEvent(Event evt) {
        notifyModelChanged(false);
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
