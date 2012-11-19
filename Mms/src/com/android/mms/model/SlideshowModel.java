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
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import android.drm.mobile1.DrmException;
import com.android.mms.drm.DrmWrapper;
import com.android.mms.layout.LayoutManager;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.CharacterSets;

import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRootLayoutElement;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class SlideshowModel extends Model
        implements List<SlideModel>, IModelChangedObserver {
    private static final String TAG = "Mms/slideshow";

    private final LayoutModel mLayout;
    private final List<SlideModel> mSlides;
    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
    public ArrayList<VcardModel> mVcards;
    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end ===== 
    public ArrayList<FileModel> mFiles; /* fixed CR<NEWMS00144166> by luning at 2011.11.28 */
    private SMILDocument mDocumentCache;
    private PduBody mPduBodyCache;
    private int mCurrentMessageSize;
    private Context mContext;
    private int mSubjectSize;

    // amount of space to leave in a slideshow for text and overhead.
    public static final int SLIDESHOW_SLOP = MmsConfig.getMaxSingleSlideSize();

    private SlideshowModel(Context context) {
        mLayout = new LayoutModel();
        mSlides = Collections.synchronizedList(new ArrayList<SlideModel>());
        mContext = context;
        mSubjectSize = 0;
    }

    private SlideshowModel(LayoutModel layouts, List<SlideModel> slides,
            SMILDocument documentCache, PduBody pbCache, Context context) {
        mContext = context;

        mDocumentCache = documentCache;
        mPduBodyCache = pbCache;
        /* fixed CR<NEWMS00137442> by luning at 11-11-07 begin */
        if (null == slides) {
            mSlides = Collections.synchronizedList(new ArrayList<SlideModel>());
        } else {
            mSlides = slides;
        }
        if (null == layouts) {
            mLayout = new LayoutModel();
        } else {
            mLayout = layouts;
        }
        /* fixed CR<NEWMS00137442> by luning at 11-11-07 end */
        for (SlideModel slide : mSlides) {
            increaseMessageSize(slide.getSlideSize());
            slide.setParent(this);
        }
        mSubjectSize = 0;
    }

    public static SlideshowModel createNew(Context context) {
        return new SlideshowModel(context);
    }

    public static SlideshowModel createFromMessageUri(
            Context context, Uri uri, boolean slideFomatError) throws MmsException {
        return createFromPduBody(context, getPduBody(context, uri), slideFomatError);
    }

    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
    public boolean hasVcard(){
    	if(null == mVcards || mVcards.size() == 0){  
    		return false;
    	}
    	return true;
    }

    public boolean hasVcards(){
        if(null == mVcards || mVcards.size() > 1){
            return false;
        }
        return true;
    }

    public void setVcards(ArrayList<VcardModel> vCards){
        if(null != vCards){
            mVcards = vCards;
        }else{
            mVcards = new ArrayList<VcardModel>();
        }    
    }
    
    public void addVcard(VcardModel vcard){
        if(null == mVcards){
            mVcards = new ArrayList<VcardModel>();
        }
        mVcards.add(vcard);
    }
    //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
    
    /* fixed CR<NEWMS00144166> by luning at 2011.11.28 begin*/
    public boolean hasOtherFile(){
         if(null == mFiles || mFiles.size() == 0){
             return false;
         }
         return true;
    }
    public void setOtherFiles(ArrayList<FileModel> files){
        if(null != files){
            mFiles = files;
        }else{
            mFiles = new ArrayList<FileModel>();
        }    
    }
    /* fixed CR<NEWMS00144166> by luning at 2011.11.28 end*/
    public static SlideshowModel createFromPduBody(Context context, PduBody pb, boolean slideFomatError) throws MmsException {
        SMILDocument document = SmilHelper.getDocument(pb);

        // Create root-layout model.
        SMILLayoutElement sle = document.getLayout();
        SMILRootLayoutElement srle = sle.getRootLayout();
        int w = srle.getWidth();
        int h = srle.getHeight();
        if ((w == 0) || (h == 0)) {
            w = LayoutManager.getInstance().getLayoutParameters().getWidth();
            h = LayoutManager.getInstance().getLayoutParameters().getHeight();
            srle.setWidth(w);
            srle.setHeight(h);
        }
        RegionModel rootLayout = new RegionModel(
                null, 0, 0, w, h);

        // Create region models.
        ArrayList<RegionModel> regions = new ArrayList<RegionModel>();
        NodeList nlRegions = sle.getRegions();
        int regionsNum = nlRegions.getLength();

        for (int i = 0; i < regionsNum; i++) {
            SMILRegionElement sre = (SMILRegionElement) nlRegions.item(i);
            RegionModel r = new RegionModel(sre.getId(), sre.getFit(),
                    sre.getLeft(), sre.getTop(), sre.getWidth(), sre.getHeight(),
                    sre.getBackgroundColor());
            regions.add(r);
        }
        LayoutModel layouts = new LayoutModel(rootLayout, regions);

        /* fixed CR<NEWMS00133151,NEWMS00144166> by luning at 2011.12.06 end */
        ArrayList<VcardModel> vCardsModels = new ArrayList<VcardModel>();
        ArrayList<FileModel> fileModels = new ArrayList<FileModel>();

        // Create slide models.
        SMILElement docBody = document.getBody();
        NodeList slideNodes = docBody.getChildNodes();
        int slidesNum = slideNodes.getLength();
        List<SlideModel> slides = Collections.synchronizedList(new ArrayList<SlideModel>(slidesNum));

        for (int i = 0; i < slidesNum; i++) {
            // FIXME: This is NOT compatible with the SMILDocument which is
            // generated by some other mobile phones.
            SMILParElement par = (SMILParElement) slideNodes.item(i);
            
            //======fixed CR<NEWMS00114698> by luning at 11-08-12 begin======
            int parDuration = (int) (par.getDur() * 1000);
            //======fixed CR<NEWMS00114698> by luning at 11-08-12 end======
            
            // Create media models for each slide.
            NodeList mediaNodes = par.getChildNodes();
            int mediaNum = mediaNodes.getLength();
            ArrayList<MediaModel> mediaSet = new ArrayList<MediaModel>(mediaNum);

            for (int j = 0; j < mediaNum; j++) {
                SMILMediaElement sme = (SMILMediaElement) mediaNodes.item(j);
                try {
                    MediaModel media = MediaModelFactory.getMediaModel(

                            context, sme, layouts, pb);  
                    
                    //fix for bug 10852
                    if(media.isVcard()){
                        VcardModel vcard = (VcardModel)media;
                        vCardsModels.add(vcard);
                    }
                    
                    if(media.isOtherFile()){
                        FileModel file = (FileModel)media;
                        fileModels.add(file);
                    }

                    //======fixed CR<NEWMS00114698> by luning at 11-08-12 begin======
                    if(media.getDuration() > parDuration){
                    	media.setDuration(parDuration);
                    } else if((media.isImage()||media.isText()) && media.getDuration() < parDuration){
                    	media.setDuration(parDuration);
                    }
                   //======fixed CR<NEWMS00114698> by luning at 11-08-12 end======
                 

//                            context, sme, layouts, pb);

                    /*
                    * This is for slide duration value set.
                    * If mms server does not support slide duration.
                    */
                    if (!MmsConfig.getSlideDurationEnabled()) {
                        int mediadur = media.getDuration();
                        float dur = par.getDur();
                        if (dur == 0) {
                            mediadur = MmsConfig.getMinimumSlideElementDuration() * 1000;
                            media.setDuration(mediadur);
                        }

                        if ((int)mediadur / 1000 != dur) {
                            String tag = sme.getTagName();

                            if (ContentType.isVideoType(media.mContentType)
                              || tag.equals(SmilHelper.ELEMENT_TAG_VIDEO)
                              || ContentType.isAudioType(media.mContentType)
                              || tag.equals(SmilHelper.ELEMENT_TAG_AUDIO)) {
                                /*
                                * add 1 sec to release and close audio/video
                                * for guaranteeing the audio/video playing.
                                * because the mmsc does not support the slide duration.
                                */
                                par.setDur((float)mediadur / 1000 + 1);
                            } else {
                                /*
                                * If a slide has an image and an audio/video element
                                * and the audio/video element has longer duration than the image,
                                * The Image disappear before the slide play done. so have to match
                                * an image duration to the slide duration.
                                */
                                if ((int)mediadur / 1000 < dur) {
                                    media.setDuration((int)dur * 1000);
                                } else {
                                    if ((int)dur != 0) {
                                        media.setDuration((int)dur * 1000);
                                    } else {
                                        par.setDur((float)mediadur / 1000);
                                    }
                                }
                            }
                        }
                    }

                    SmilHelper.addMediaElementEventListeners(
                            (EventTarget) sme, media);
                    mediaSet.add(media);
                } catch (DrmException e) {
                    Log.e(TAG, e.getMessage(), e);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            SlideModel slide = new SlideModel(parDuration, mediaSet, context, slideFomatError);
            slide.setFill(par.getFill());
            SmilHelper.addParElementEventListeners((EventTarget) par, slide);
            slides.add(slide);
        }
        //for NEWMS00198557
        //  take notes  for bug 19247   --start
        for (PduPart part : pb.getParts()) {
            if (!part.findBySmil) {
                byte[] bytes = part.getContentType();
                if (null != bytes) {
                    String contentType = new String(bytes);
                    Uri uri = part.getDataUri();
                    String contentLocation = null;
                    if (null == part.getContentLocation()) {
                        contentLocation = "";
                    } else {
                        contentLocation = new String(part.getContentLocation());
                    }
                    if (ContentType.TEXT_VCARD.equalsIgnoreCase(contentType)) {
                        if (null != uri) {
                            VcardModel vCard = new VcardModel(context,
                                    ContentType.TEXT_VCARD, contentLocation, uri);
                            vCardsModels.add(vCard);
                        }
                    } else if (!ContentType.APP_SMIL.equalsIgnoreCase(contentType)) {
                        if (null != uri) {
                            try {
                                FileModel file = new FileModel(context, null,
                                        ContentType.FILE_UNSPECIFIED, contentLocation, uri);
                                fileModels.add(file);
                            } catch (MmsException e) {
                                Log.e(TAG, "MmsException caught while creating FileModel", e);
                            }
                        }
                    }
                }
            }
        }
        //  take notes  for bug 19247   --end

        SlideshowModel slideshow = new SlideshowModel(layouts, slides, document, pb, context);
        if (null != vCardsModels) {
            slideshow.setVcards(vCardsModels);
        }
        if (null != fileModels) {
            slideshow.setOtherFiles(fileModels);
        }
        /* fixed CR<NEWMS00133151,NEWMS00144166> by luning at 2011.12.06 end */     
        slideshow.registerModelChangedObserver(slideshow);
        return slideshow;
    }

    public PduBody toPduBody() {
        if (mPduBodyCache == null) {
            mDocumentCache = SmilHelper.getDocument(this);
            mPduBodyCache = makePduBody(mDocumentCache);
        }
        return mPduBodyCache;
    }

    private PduBody makePduBody(SMILDocument document) {
        return makePduBody(null, document, false);
    }

    private PduBody makePduBody(Context context, SMILDocument document, boolean isMakingCopy) {
        PduBody pb = new PduBody();

        boolean hasForwardLock = false;
        List<SlideModel> slides = new ArrayList<SlideModel>(Arrays.asList(mSlides.toArray(new SlideModel[mSlides.size()])));
        Collections.copy(slides, mSlides);
        for (SlideModel slide : slides) {
            for (MediaModel media : slide) {
                if (isMakingCopy) {
                    if (media.isDrmProtected() && !media.isAllowedToForward()) {
                        hasForwardLock = true;
                        continue;
                    }
                }

                PduPart part = new PduPart();

                if (media.isText()) {
                    TextModel text = (TextModel) media;
                    // Don't create empty text part.
                    if (TextUtils.isEmpty(text.getText())) {
                        continue;
                    }
                    // Set Charset if it's a text media.
                    part.setCharset(text.getCharset());
                }

                // Set Content-Type.
                part.setContentType(media.getContentType().getBytes());

                String src = media.getSrc();
                String location;
                boolean startWithContentId = src.startsWith("cid:");
                if (startWithContentId) {
                    location = src.substring("cid:".length());
                } else {
                    location = src;
                }

                // Set Content-Location.
                part.setContentLocation(location.getBytes());

                // Set Content-Id.
                if (startWithContentId) {
                    //Keep the original Content-Id.
                    part.setContentId(location.getBytes());
                } else {
                    int index = location.lastIndexOf(".");
                    String contentId = (index == -1) ? location
                            : location.substring(0, index);
                    part.setContentId(contentId.getBytes());
                }

                if (media.isDrmProtected()) {
                    DrmWrapper wrapper = media.getDrmObject();
                    part.setDataUri(wrapper.getOriginalUri());
                    part.setData(wrapper.getOriginalData());
                } else if (media.isText()) {
                    part.setData(((TextModel) media).getText().getBytes());
                }else if(media.isVcard() || media.isOtherFile()){//2012-03-02 fix for bug 10852 phone02 start
                    try {
                        part.setDataUri(media.getUri());
                        part.setData(media.getData());
                    } catch (DrmException e) {
                        Log.e(TAG, "makePduBody hanppened DrmException :"+e.toString(), e);
                        e.printStackTrace();
                    }//2012-03-02 fix for bug 10852 phone02 end
                } else if (media.isImage() || media.isVideo() || media.isAudio()) {

                    part.setDataUri(media.getUri());
                } else {
                    Log.w(TAG, "Unsupport media: " + media);
                }

                pb.addPart(part);
            }
        }

        if (pb.getPartsNum() == 0 && mFiles != null) {
            for (FileModel file : mFiles) {
                PduPart part = createPduPart(file);
                pb.addPart(part);
            }
        }

        if (pb.getPartsNum() == 0 && mVcards != null) {
            for (VcardModel vcard : mVcards) {
                PduPart part = createPduPart(vcard);
                pb.addPart(part);
            }
        }

        if (hasForwardLock && isMakingCopy && context != null) {
            Toast.makeText(context, context.getString(R.string.cannot_forward_drm_obj),
                    Toast.LENGTH_LONG).show();
            document = SmilHelper.getDocument(pb);
        }

        // ===== fixed CR<NEWMS00120798> by luning at 2011.11.08 begin =====
        //fix for bug fix for bug 10852 ,cover this code ,the vcard add to slide
//        if(hasVcard()){
//        	for(VcardModel vcardModel :mVcards){
//        		 PduPart part = new PduPart();
//        		 // Set Content-Type.
//                 part.setContentType(vcardModel.getContentType().getBytes());
//                 // Set Content-Location.
//                 part.setContentLocation(vcardModel.getSrc().getBytes());
//                 // Set Content-Id.
//                 part.setContentId(vcardModel.getSrc().getBytes());
//                 // Set Uri
//                 part.setDataUri(vcardModel.getUri());
//                 // add vcardPart to body
//                 pb.addPart(part);
//        	}
//        }
        // ===== fixed CR<NEWMS00120798> by luning at 2011.11.08 end =====
        
        // Create and insert SMIL part(as the first part) into the PduBody.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(document, out);
        PduPart smilPart = new PduPart();
        smilPart.setCharset(CharacterSets.DEFAULT_CHARSET);
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        pb.addPart(0, smilPart);
        return pb;
    }

    private PduPart createPduPart(MediaModel media) {
        PduPart part = new PduPart();
        try {
            part.setContentType(media.getContentType().getBytes());

            String src = media.getSrc();
            String location;
            boolean startWithContentId = src.startsWith("cid:");
            if (startWithContentId) {
                location = src.substring("cid:".length());
            } else {
                location = src;
            }

            // Set Content-Id.
            if (startWithContentId) {
                // Keep the original Content-Id.
                part.setContentId(location.getBytes());
            } else {
                int index = location.lastIndexOf(".");
                String contentId = (index == -1) ? location : location.substring(0, index);
                part.setContentId(contentId.getBytes());
            }

            // Set Content-Location.
            part.setContentLocation(location.getBytes());

            part.setDataUri(media.getUri());
            part.setData(media.getData());
        } catch (DrmException e) {
            Log.e(TAG, "makePduBody hanppened DrmException :" + e.toString(), e);
            e.printStackTrace();
        }
        return part;
    }

    public PduBody makeCopy(Context context) {
        return makePduBody(context, SmilHelper.getDocument(this), true);
    }

    public SMILDocument toSmilDocument() {
        if (mDocumentCache == null) {
            mDocumentCache = SmilHelper.getDocument(this);
        }
        return mDocumentCache;
    }

    public static PduBody getPduBody(Context context, Uri msg) throws MmsException {
        PduPersister p = PduPersister.getPduPersister(context);
        GenericPdu pdu = p.load(msg);

        int msgType = pdu.getMessageType();
        if ((msgType == PduHeaders.MESSAGE_TYPE_SEND_REQ)
                || (msgType == PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF)) {
            return ((MultimediaMessagePdu) pdu).getBody();
        } else {
            throw new MmsException();
        }
    }

    public void setCurrentMessageSize(int size) {
        mCurrentMessageSize = size;
    }

    public int getCurrentMessageSize() {
        return mCurrentMessageSize;
    }

    public void increaseMessageSize(int increaseSize) {
        if (increaseSize > 0) {
            mCurrentMessageSize += increaseSize;
        }
    }

    public void decreaseMessageSize(int decreaseSize) {
        if (decreaseSize > 0) {
            mCurrentMessageSize -= decreaseSize;
        }
    }

    public LayoutModel getLayout() {
        return mLayout;
    }

    //
    // Implement List<E> interface.
    //
    public boolean add(SlideModel object) {
        int increaseSize = object.getSlideSize();
        checkMessageSize(increaseSize);

        if ((object != null) && mSlides.add(object)) {
            increaseMessageSize(increaseSize);
            object.registerModelChangedObserver(this);
            for (IModelChangedObserver observer : mModelChangedObservers) {
                object.registerModelChangedObserver(observer);
            }
            notifyModelChanged(true);
            return true;
        }
        return false;
    }

    public boolean addAll(Collection<? extends SlideModel> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public void clear() {
        if (mSlides.size() > 0) {
            for (SlideModel slide : mSlides) {
                slide.unregisterModelChangedObserver(this);
                for (IModelChangedObserver observer : mModelChangedObservers) {
                    slide.unregisterModelChangedObserver(observer);
                }
            }
            mCurrentMessageSize = 0;
            mSlides.clear();
            notifyModelChanged(true);
        }
    }

    public boolean contains(Object object) {
        return mSlides.contains(object);
    }

    public boolean containsAll(Collection<?> collection) {
        return mSlides.containsAll(collection);
    }

    public boolean isEmpty() {
        return mSlides.isEmpty();
    }

    public Iterator<SlideModel> iterator() {
        return mSlides.iterator();
    }

    public boolean remove(Object object) {
        if ((object != null) && mSlides.remove(object)) {
            SlideModel slide = (SlideModel) object;
            decreaseMessageSize(slide.getSlideSize());
            slide.unregisterAllModelChangedObservers();
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
        return mSlides.size();
    }

    public Object[] toArray() {
        return mSlides.toArray();
    }

    public <T> T[] toArray(T[] array) {
        return mSlides.toArray(array);
    }

    public void add(int location, SlideModel object) {
        if (object != null) {
            int increaseSize = object.getSlideSize();
            checkMessageSize(increaseSize);

            mSlides.add(location, object);
            increaseMessageSize(increaseSize);
            object.registerModelChangedObserver(this);
            for (IModelChangedObserver observer : mModelChangedObservers) {
                object.registerModelChangedObserver(observer);
            }
            notifyModelChanged(true);
        }
    }

    public boolean addAll(int location,
            Collection<? extends SlideModel> collection) {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    public SlideModel get(int location) {
        return (location >= 0 && location < mSlides.size()) ? mSlides.get(location) : null;
    }

    public int indexOf(Object object) {
        return mSlides.indexOf(object);
    }

    public int lastIndexOf(Object object) {
        return mSlides.lastIndexOf(object);
    }

    public ListIterator<SlideModel> listIterator() {
        return mSlides.listIterator();
    }

    public ListIterator<SlideModel> listIterator(int location) {
        return mSlides.listIterator(location);
    }

    public SlideModel remove(int location) {
        SlideModel slide = mSlides.remove(location);
        if (slide != null) {
            decreaseMessageSize(slide.getSlideSize());
            slide.unregisterAllModelChangedObservers();
            notifyModelChanged(true);
        }
        return slide;
    }

    public SlideModel set(int location, SlideModel object) {
        SlideModel slide = mSlides.get(location);
        if (null != object) {
            int removeSize = 0;
            int addSize = object.getSlideSize();
            if (null != slide) {
                removeSize = slide.getSlideSize();
            }
            if (addSize > removeSize) {
                checkMessageSize(addSize - removeSize);
                increaseMessageSize(addSize - removeSize);
            } else {
                decreaseMessageSize(removeSize - addSize);
            }
        }

        slide =  mSlides.set(location, object);
        if (slide != null) {
            slide.unregisterAllModelChangedObservers();
        }

        if (object != null) {
            object.registerModelChangedObserver(this);
            for (IModelChangedObserver observer : mModelChangedObservers) {
                object.registerModelChangedObserver(observer);
            }
        }

        notifyModelChanged(true);
        return slide;
    }

    public List<SlideModel> subList(int start, int end) {
        return mSlides.subList(start, end);
    }

    @Override
    protected void registerModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        mLayout.registerModelChangedObserver(observer);

        for (SlideModel slide : mSlides) {
            slide.registerModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        mLayout.unregisterModelChangedObserver(observer);

        for (SlideModel slide : mSlides) {
            slide.unregisterModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterAllModelChangedObserversInDescendants() {
        mLayout.unregisterAllModelChangedObservers();

        for (SlideModel slide : mSlides) {
            slide.unregisterAllModelChangedObservers();
        }
    }

    public void onModelChanged(Model model, boolean dataChanged) {
        if (dataChanged) {
            mDocumentCache = null;
            mPduBodyCache = null;
        }
    }

    public void sync(PduBody pb) {
        for (SlideModel slide : mSlides) {
            for (MediaModel media : slide) {
                PduPart part = pb.getPartByContentLocation(media.getSrc());
                if (part != null) {
                    media.setUri(part.getDataUri());
                }
            }
        }
    }

    public void checkMessageSize(int increaseSize) throws ContentRestrictionException {
        ContentRestriction cr = ContentRestrictionFactory.getContentRestriction();
        cr.checkMessageSize(getTotalMsgSizeWithAllHead(), increaseSize, mContext.getContentResolver());
    }

    /**
     * Determines whether this is a "simple" slideshow.
     * Criteria:
     * - Exactly one slide
     * - Exactly one multimedia attachment, but no audio
     * - It can optionally have a caption
    */
    public boolean isSimple() {
        // There must be one (and only one) slide.
        if (size() != 1)
            return false;

        SlideModel slide = get(0);
        // The slide must have either an image or video, but not both.
        if (!(slide.hasImage() ^ slide.hasVideo()))
            return false;

        // No audio allowed.
        if (slide.hasAudio())
            return false;

        return true;
    }

    /**
     * add by luning at 2011.11.23
     * @return
     */
    public boolean isSimpleSlide() {
        if (size() > 1)
            return false;
        SlideModel slide = get(0);
        int size = slide.size();
        if (size < 2)
            return true;
        else if (size == 2 && slide.hasText())
            return true;
        else
            return false;
    }
    
    /**
     * Make sure the text in slide 0 is no longer holding onto a reference to the text
     * in the message text box.
     */
    public void prepareForSend() {
        if (size() == 1) {
            TextModel text = get(0).getText();
            if (text != null) {
                text.cloneText();
            }
        }
    }
    public int getTotalMessageSize()//for bug newms00110111
    {
        int totalSize = 0;
        for (SlideModel slide : mSlides) {
            totalSize+=slide.getSlideSize();
        }
        // ======fixed CR<NEWMS00122616> by luning at 11-08-12 begin====== 
//        totalSize +=getCurrentMessageSize();
        // ======fixed CR<NEWMS00122616> by luning at 11-08-12 end======
        totalSize += mSubjectSize;
        return totalSize;
    }
    public int getTotalSlideCount() {
        return mSlides.size();
    }
    public int getTotalMsgSizeWithSlideHead() {
        return (getCurrentMessageSize()+getTotalSlideCount()*MmsConfig.getMaxSingleSlideSize());
    }
    public int getTotalMsgSizeWithAllHead() {
        return (MmsConfig.getMessageWithPduHeadSize(getTotalMsgSizeWithSlideHead()));
    }
    public int getTotalMsgSizeWithAllHead(int size) {
        return (MmsConfig.getMessageWithPduHeadSize(size+getTotalSlideCount()*MmsConfig.getMaxSingleSlideSize()));
    }
    // ===== fixed CR<NEWMS00129480> by luning at 11-10-13 begin =====
    public int getMediaNum(){
    	int num = 0;
        for (SlideModel slide : mSlides) {
            for (MediaModel media : slide) {
            	num ++;
            }
        }
        return num;
    }
    // ===== fixed CR<NEWMS00129480> by luning at 11-10-13 end =====
    /**
     * Resize all the resizeable media objects to fit in the remaining size of the slideshow.
     * This should be called off of the UI thread.
     *
     * @throws MmsException, ExceedMessageSizeException
     */
    public void finalResize(Uri messageUri) throws MmsException, ExceedMessageSizeException {
//        Log.v(TAG, "Original message size: " + getCurrentMessageSize() + " getMaxMessageSize: "
//                + MmsConfig.getMaxMessageSize());

        // Figure out if we have any media items that need to be resized and total up the
        // sizes of the items that can't be resized.
        int totalSize = 0;
        int resizableCnt = 0;
        int fixedSizeTotal = 0;
        boolean isSupportResize=false;

        fixedSizeTotal = totalSize = mSubjectSize;
        for (SlideModel slide : mSlides) {
            for (MediaModel media : slide) {
                totalSize += media.getMediaSize();//check resize or not for bug newms00110175
                if (media.getMediaResizable()) {
                    ++resizableCnt;
                } else {
                    fixedSizeTotal += media.getMediaSize();
                }
            }
        }
        if (!isSupportResize || totalSize < MmsConfig.getMaxMessageSize()) {//check resize or not
                return ;
        }
        if (resizableCnt > 0) {
            int remainingSize = MmsConfig.getMaxMessageSize() - fixedSizeTotal - SLIDESHOW_SLOP;
            if (remainingSize <= 0) {
                throw new ExceedMessageSizeException("No room for pictures");
            }
            long messageId = ContentUris.parseId(messageUri);
            int bytesPerMediaItem = remainingSize / resizableCnt;
            // Resize the resizable media items to fit within their byte limit.
            for (SlideModel slide : mSlides) {
                for (MediaModel media : slide) {
                    if (media.getMediaResizable()) {
                        media.resizeMedia(bytesPerMediaItem, messageId);
                    }
                }
            }
            // One last time through to calc the real message size.
            totalSize = mSubjectSize;
            for (SlideModel slide : mSlides) {
                for (MediaModel media : slide) {
                    totalSize += media.getMediaSize();
                }
            }
//            Log.v(TAG, "New message size: " + totalSize + " getMaxMessageSize: "
//                    + MmsConfig.getMaxMessageSize());

            if (totalSize > MmsConfig.getMaxMessageSize()) {
                throw new ExceedMessageSizeException("After compressing pictures, message too big");
            }
            setCurrentMessageSize(totalSize);

            onModelChanged(this, true);     // clear the cached pdu body
            PduBody pb = toPduBody();
            // This will write out all the new parts to:
            //      /data/data/com.android.providers.telephony/app_parts
            // and at the same time delete the old parts.
            PduPersister.getPduPersister(mContext).updateParts(messageUri, pb);
        }
    }
    public void setSubjectSize(int subject_size) {
        if(subject_size<0) return;

        if(subject_size>mSubjectSize) {
            //checkMessageSize(subject_size - mSubjectSize);
            increaseMessageSize(subject_size - mSubjectSize);
        } else if(subject_size<mSubjectSize) {
            decreaseMessageSize(mSubjectSize-subject_size);
        }
        mSubjectSize = subject_size;
    }
}
