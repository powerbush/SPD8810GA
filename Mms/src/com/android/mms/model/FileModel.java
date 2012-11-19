package com.android.mms.model;

import org.w3c.dom.events.Event;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.HashMap;

import com.android.mms.drm.DrmWrapper;
import com.google.android.mms.MmsException;

public class FileModel extends MediaModel {

    private String detail;

    public FileModel(Context context, String tag, String contentType, String src, Uri uri)
            throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_FILE, contentType, src, uri);
        // TODO Auto-generated constructor stub
    }

    public FileModel(Context context, String contentType, String src, DrmWrapper wrapper)
            throws IOException {
        super(context, SmilHelper.ELEMENT_TAG_FILE, contentType, src, wrapper);
    }

    public FileModel(Context context, String contentType, String src, Uri uri) throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_FILE, contentType, src, uri);
    }

    @Override
    public void handleEvent(Event evt) {
        // TODO Auto-generated method stub
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

}
