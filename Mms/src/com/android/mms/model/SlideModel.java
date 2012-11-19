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
import com.android.mms.R;
import com.android.mms.dom.smil.SmilParElementImpl;
import com.google.android.mms.ContentType;

import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.smil.ElementTime;

import android.util.Config;
import android.util.Log;
import android.widget.Toast;
import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class SlideModel extends Model implements List<MediaModel>, EventListener {
    public static final String TAG = "Mms/slideshow";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;
    private static final int DEFAULT_SLIDE_DURATION = 5000;

    //private final ArrayList<MediaModel> mMedia = new ArrayList<MediaModel>();
    private final List<MediaModel> mMedia = Collections.synchronizedList(new ArrayList<MediaModel>());

    private MediaModel mText;
    private MediaModel mImage;
    private MediaModel mAudio;
    private MediaModel mVideo;
    private MediaModel mVcard;
    private MediaModel mFile;

    private boolean mCanAddImage = true;
    private boolean mCanAddAudio = true;
    private boolean mCanAddVideo = true;
    private boolean mCanAddVcard = true;

    private int mDuration;
    private boolean mVisible = true;
    private short mFill;
    private int mSlideSize;
    private SlideshowModel mParent;

    public SlideModel(SlideshowModel slideshow) {
        this(DEFAULT_SLIDE_DURATION, slideshow);
    }

    public SlideModel(int duration, SlideshowModel slideshow) {
        mDuration = duration;
        mParent = slideshow;
    }

    /**
     * Create a SlideModel with exist media collection.
     *
     * @param duration The duration of the slide.
     * @param mediaList The exist media collection.
     *
     * @throws IllegalStateException One or more media in the mediaList cannot
     *         be added into the slide due to a slide cannot contain image
     *         and video or audio and video at the same time.
     */
    public SlideModel(int duration, ArrayList<MediaModel> mediaList, Context context, boolean slideFomatError) {
        mDuration = duration;

        int maxDur = 0;
        for (MediaModel media : mediaList) {
            try {
                internalAdd(media);
            } catch (IllegalStateException e) {
                if (slideFomatError) {
                    Toast.makeText(context, R.string.slide_fomat_error, Toast.LENGTH_SHORT).show();
                }
            }

            int mediaDur = media.getDuration();
            if (mediaDur > maxDur) {
                maxDur = mediaDur;
            }
        }

        updateDuration(maxDur);
    }

    private void internalAdd(MediaModel media) throws IllegalStateException {
        if (media == null) {
            // Don't add null value into the list.
            return;
        }

        if (media.isText()) {
            String contentType = media.getContentType();
            if (TextUtils.isEmpty(contentType) || ContentType.TEXT_PLAIN.equals(contentType)
                    || ContentType.TEXT_HTML.equals(contentType)) {
                internalAddOrReplace(mText, media);
                mText = media;
            } else {
                Log.w(TAG, "[SlideModel] content type " + media.getContentType() +
                        " isn't supported (as text)");
            }
        } else if (media.isImage()) {
            if (mCanAddImage) {
                internalAddOrReplace(mImage, media);
                mImage = media;
                mCanAddVideo = false;
            } else {
                throw new IllegalStateException();
            }
        } else if (media.isAudio()) {
            if (mCanAddAudio) {
                internalAddOrReplace(mAudio, media);
                mAudio = media;
                mCanAddVideo = false;
            } else {
                throw new IllegalStateException();
            }
        } else if (media.isVideo()) {
            if (mCanAddVideo) {
                internalAddOrReplace(mVideo, media);
                mVideo = media;
                mCanAddImage = false;
                mCanAddAudio = false;
            } else {
                throw new IllegalStateException();
            }
        } else if (media.isVcard()) {
//            if (mCanAddVcard) {
                internalAddOrReplace(mVcard, media);
                mVcard = media;
//                mCanAddVcard = false;
//            } else {
//                throw new IllegalStateException();
//            }
        } else if (media.isOtherFile()) {
            internalAddOrReplace(mFile, media);
            mFile = media;
        }
    }

    private void internalAddOrReplace(MediaModel old, MediaModel media) {
        // If the media is resizable, at this point consider it to be zero length.
        // Just before we send the slideshow, we take the remaining space in the
        // slideshow and equally allocate it to all the resizeable media items and resize them.
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
//    	int addSize = media.getMediaResizable() ? 0 : media.getMediaSize();
        int addSize = media.getMediaSize();
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 end =====
        int removeSize;
        if (old == null) {
            if (null != mParent) {
                mParent.checkMessageSize(addSize);
            }
            mMedia.add(media);
            increaseSlideSize(addSize);
            increaseMessageSize(addSize);
        } else {
            removeSize = old.getMediaSize();
            if (addSize > removeSize) {
                if (null != mParent) {
                    mParent.checkMessageSize(addSize - removeSize);
                }
                increaseSlideSize(addSize - removeSize);
                increaseMessageSize(addSize - removeSize);
            } else {
                decreaseSlideSize(removeSize - addSize);
                decreaseMessageSize(removeSize - addSize);
            }
            mMedia.set(mMedia.indexOf(old), media);
            old.unregisterAllModelChangedObservers();
        }

        for (IModelChangedObserver observer : mModelChangedObservers) {
            media.registerModelChangedObserver(observer);
        }
    }

    private boolean internalRemove(Object object) {
        if (mMedia.remove(object)) {
            if (object instanceof TextModel) {
                mText = null;
            } else if (object instanceof ImageModel) {
                mImage = null;
                mCanAddVideo = true;
            } else if (object instanceof AudioModel) {
                mAudio = null;
                mCanAddVideo = true;
            } else if (object instanceof VideoModel) {
                mVideo = null;
                mCanAddImage = true;
                mCanAddAudio = true;
            } else if (object instanceof VcardModel) {
                mVcard = null;
                mCanAddImage = true;
                mCanAddAudio = true;
            } else if (object instanceof FileModel) {
                mFile = null;
                mCanAddImage = true;
                mCanAddAudio = true;
            }
            // If the media is resizable, at this point consider it to be zero length.
            // Just before we send the slideshow, we take the remaining space in the
            // slideshow and equally allocate it to all the resizeable media items and resize them.
            //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
//            int decreaseSize = ((MediaModel) object).getMediaResizable() ? 0
//                                        : ((MediaModel) object).getMediaSize();
            int decreaseSize = ((MediaModel) object).getMediaSize();
            //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
            decreaseSlideSize(decreaseSize);
            decreaseMessageSize(decreaseSize);

            ((Model) object).unregisterAllModelChangedObservers();

            return true;
        }

        return false;
    }

    /**
     * @return the mDuration
     */
    public int getDuration() {
        return mDuration;
    }

    /**
     * @param duration the mDuration to set
     */
    public void setDuration(int duration) {
        mDuration = duration;
        notifyModelChanged(true);
    }

    public int getSlideSize() {
        return mSlideSize;
    }

    public void increaseSlideSize(int increaseSize) {
        if (increaseSize > 0) {
            mSlideSize += increaseSize;
        }
    }

    public void decreaseSlideSize(int decreaseSize) {
        if (decreaseSize > 0) {
            mSlideSize -= decreaseSize;
        }
    }

    public void setParent(SlideshowModel parent) {
        mParent = parent;
    }

    public void increaseMessageSize(int increaseSize) {
        if ((increaseSize > 0) && (null != mParent)) {
            int size = mParent.getCurrentMessageSize();
            size += increaseSize;
            mParent.setCurrentMessageSize(size);
        }
    }

    public void decreaseMessageSize(int decreaseSize) {
        if ((decreaseSize > 0) && (null != mParent)) {
            int size = mParent.getCurrentMessageSize();
            size -= decreaseSize;
            mParent.setCurrentMessageSize(size);
        }
    }

    //
    // Implement List<E> interface.
    //

    /**
     * Add a MediaModel to the slide. If the slide has already contained
     * a media object in the same type, the media object will be replaced by
     * the new one.
     *
     * @param object A media object to be added into the slide.
     * @return true
     * @throws IllegalStateException One or more media in the mediaList cannot
     *         be added into the slide due to a slide cannot contain image
     *         and video or audio and video at the same time.
     * @throws ContentRestrictionException when can not add this object.
     *
     */
    public boolean add(MediaModel object) {
        internalAdd(object);
        notifyModelChanged(true);
        return true;
    }

    public boolean addAll(Collection<? extends MediaModel> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public void clear() {
        if (mMedia.size() > 0) {
            for (MediaModel media : mMedia) {
                media.unregisterAllModelChangedObservers();
                int decreaseSize = media.getMediaSize();
                decreaseSlideSize(decreaseSize);
                decreaseMessageSize(decreaseSize);
            }
            mMedia.clear();

            mText = null;
            mImage = null;
            mAudio = null;
            mVideo = null;
            mVcard = null;
            mFile = null;

            mCanAddImage = true;
            mCanAddAudio = true;
            mCanAddVideo = true;
            mCanAddVcard = true;

            notifyModelChanged(true);
        }
    }

    public boolean contains(Object object) {
        return mMedia.contains(object);
    }

    public boolean containsAll(Collection<?> collection) {
        return mMedia.containsAll(collection);
    }

    public boolean isEmpty() {
        return mMedia.isEmpty();
    }

    public Iterator<MediaModel> iterator() {
        return mMedia.iterator();
    }

    public boolean remove(Object object) {
        if ((object != null) && (object instanceof MediaModel)
                && internalRemove(object)) {
            notifyModelChanged(true);
            return true;
        }
        return false;
    }

    public boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public int size() {
        return mMedia.size();
    }

    public Object[] toArray() {
        return mMedia.toArray();
    }

    public <T> T[] toArray(T[] array) {
        return mMedia.toArray(array);
    }

    public void add(int location, MediaModel object) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public boolean addAll(int location,
            Collection<? extends MediaModel> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public MediaModel get(int location) {
        if (mMedia.size() == 0) {
            return null;
        }

        return mMedia.get(location);
    }

    public int indexOf(Object object) {
        return mMedia.indexOf(object);
    }

    public int lastIndexOf(Object object) {
        return mMedia.lastIndexOf(object);
    }

    public ListIterator<MediaModel> listIterator() {
        return mMedia.listIterator();
    }

    public ListIterator<MediaModel> listIterator(int location) {
        return mMedia.listIterator(location);
    }

    public MediaModel remove(int location) {
        MediaModel media = mMedia.get(location);
        if ((media != null) && internalRemove(media)) {
            notifyModelChanged(true);
        }
        return media;
    }

    public MediaModel set(int location, MediaModel object) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public List<MediaModel> subList(int start, int end) {
        return mMedia.subList(start, end);
    }

    /**
     * @return the mVisible
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * @param visible the mVisible to set
     */
    public void setVisible(boolean visible) {
        mVisible = visible;
        notifyModelChanged(true);
    }

    /**
     * @return the mFill
     */
    public short getFill() {
        return mFill;
    }

    /**
     * @param fill the mFill to set
     */
    public void setFill(short fill) {
        mFill = fill;
        notifyModelChanged(true);
    }

    @Override
    protected void registerModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        for (MediaModel media : mMedia) {
            media.registerModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        for (MediaModel media : mMedia) {
            media.unregisterModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterAllModelChangedObserversInDescendants() {
        for (MediaModel media : mMedia) {
            media.unregisterAllModelChangedObservers();
        }
    }

    // EventListener Interface
    public void handleEvent(Event evt) {
        if (evt.getType().equals(SmilParElementImpl.SMIL_SLIDE_START_EVENT)) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Start to play slide: " + this);
            }
            mVisible = true;
        } else if (mFill != ElementTime.FILL_FREEZE) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Stop playing slide: " + this);
            }
            mVisible = false;
        }

        notifyModelChanged(false);
    }

    public boolean hasText() {
        return mText != null;
    }

    public boolean hasImage() {
        return mImage != null;
    }

    public boolean hasAudio() {
        return mAudio != null;
    }

    public boolean hasVideo() {
        return mVideo != null;
    }

    public boolean hasVcard() {
        return mVcard != null;
    }

    public boolean hasOtherFile() {
        return mFile != null;
    }

    public boolean removeText() {
        return remove(mText);
    }

    public boolean removeImage() {
        return remove(mImage);
    }

    public boolean removeAudio() {
        return remove(mAudio);
    }

    public boolean removeVideo() {
        return remove(mVideo);
    }

    public boolean removeVcard() {
        return remove(mVcard);
    }

    public boolean removeOtherFile() {
        return remove(mFile);
    }

    public TextModel getText() {
        return (TextModel) mText;
    }

    public ImageModel getImage() {
        return (ImageModel) mImage;
    }

    public AudioModel getAudio() {
        return (AudioModel) mAudio;
    }

    public VideoModel getVideo() {
        return (VideoModel) mVideo;
    }

    public VcardModel getVcard() {
        return (VcardModel) mVcard;
    }

    public FileModel getOtherFile() {
        return (FileModel) mFile;
    }

    public void updateDuration(int duration) {
        if (duration <= 0) {
            return;
        }
        //update by lishengjie@spreadst start 2012-3-27
        /*if ((duration > mDuration)
                || (mDuration == DEFAULT_SLIDE_DURATION)) {
            mDuration = duration;
        }*/
        mDuration = duration;
        //update by lishengjie@spreadst end 2012-3-27
    }
    public void updateText(CharSequence text) {
        if(mText!=null){
	        int Size = ((MediaModel) mText).getMediaSize();
	        decreaseSlideSize(Size);
	        decreaseMessageSize(Size);
	        ((TextModel)mText).setText(text);
	        Size = ((MediaModel) mText).getMediaSize();
	        increaseSlideSize(Size);
	        increaseMessageSize(Size);
	        notifyModelChanged(true);
        }
    }
}
