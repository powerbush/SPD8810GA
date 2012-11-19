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


import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.LogTag;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.util.AddressUtils;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.SendReq;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.Telephony.Mms;
import android.provider.MediaStore;
import android.media.CamcorderProfile;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.util.Log;
import android.widget.Toast;
import android.view.KeyEvent;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.media.ExifInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.OutputStream;
/**
 * An utility class for managing messages.
 */
public class MessageUtils {
	static final int UNCONSTRAINED = -1;
	static final int MAX_SIZE = 1000*1000;
    interface ResizeImageResultCallback {
        void onResizeResult(PduPart part, boolean append);
    }

    private static final String TAG = LogTag.TAG;
    private static String sLocalNumber;
    private static boolean needLocalNumber = true;
    static AlertDialog.Builder builder = null;
    // Cache of both groups of space-separated ids to their full
    // comma-separated display names, as well as individual ids to
    // display names.
    // TODO: is it possible for canonical address ID keys to be
    // re-used?  SQLite does reuse IDs on NULL id_ insert, but does
    // anything ever delete from the mmssms.db canonical_addresses
    // table?  Nothing that I could find.
    private static final Map<String, String> sRecipientAddress =
            new ConcurrentHashMap<String, String>(20 /* initial capacity */);


    /**
     * MMS address parsing data structures
     */
    // allowable phone number separators
    private static final char[] NUMERIC_CHARS_SUGAR = {
        '-', '.', ',', '(', ')', ' ', '/', '\\', '*', '#', '+'
    };

    private static HashMap numericSugarMap = new HashMap (NUMERIC_CHARS_SUGAR.length);

    static {
        for (int i = 0; i < NUMERIC_CHARS_SUGAR.length; i++) {
            numericSugarMap.put(NUMERIC_CHARS_SUGAR[i], NUMERIC_CHARS_SUGAR[i]);
        }
    }


    private MessageUtils() {
        // Forbidden being instantiated.
    }

    public static String getMessageDetails(Context context, Cursor cursor, int size) {
        if (cursor == null) {
            return null;
        }

        if ("mms".equals(cursor.getString(MessageListAdapter.COLUMN_MSG_TYPE))) {
            int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, cursor);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, cursor, size);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, cursor);
        }
    }

    private static String getNotificationIndDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        NotificationInd nInd;

        try {
            nInd = (NotificationInd) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        String service_center = cursor.getString(MessageListAdapter.COLUMN_MMS_SERVICE_CENTER);
        Log.d(TAG,"getNotificationIndDetails() service_center:"+service_center);
        details.append('\n');
        details.append(res.getString(R.string.service_center));
        details.append(!TextUtils.isEmpty(service_center)? service_center:"");

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
        details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        details.append(new String(nInd.getMessageClass()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }

    public static String getMultimediaMessageDetails(
            Context context, Cursor cursor, int size) {
        int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
        if (type == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            return getNotificationIndDetails(context, cursor);
        }

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_message));

        if (msg instanceof RetrieveConf) {
            // From: ***
            String from = extractEncStr(context, ((RetrieveConf) msg).getFrom());
            details.append('\n');
            details.append(res.getString(R.string.from_label));
            details.append(!TextUtils.isEmpty(from)? from:
                                  res.getString(R.string.hidden_sender_address));
        }

        // To: ***
        details.append('\n');
        details.append(res.getString(R.string.to_address_label));
        EncodedStringValue[] to = msg.getTo();
        if (to != null) {
            details.append(EncodedStringValue.concat(to));
        }
        else {
            Log.w(TAG, "recipient list is empty!");
        }


        // Bcc: ***
        if (msg instanceof SendReq) {
            EncodedStringValue[] values = ((SendReq) msg).getBcc();
            if ((values != null) && (values.length > 0)) {
                details.append('\n');
                details.append(res.getString(R.string.bcc_label));
                details.append(EncodedStringValue.concat(values));
            }
        }

        // Date: ***
        details.append('\n');
        int msgBox = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_BOX);
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        details.append(MessageUtils.formatTimeStampString(
                context, msg.getDate() * 1000L, true));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size should include size of subject.
            size += subStr.getBytes().length;
            details.append(subStr);
        }
        // ======fixed CR<NEWMS00110179> by luning at 11-08-12 begin======
        else{
        	details.append(context.getResources().getString(R.string.no_subject_view));
        }
        // ======fixed CR<NEWMS00110179> by luning at 11-08-12 end======

        // Priority: High/Normal/Low
        details.append('\n');
        details.append(res.getString(R.string.priority_label));
        details.append(getPriorityDescription(context, msg.getPriority()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(MmsConfig.getMessageWithPduHeadSize(size)/1024);
        details.append(" KB");
        return details.toString();
    }

    private static String getTextMessageDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append('\n');
        int smsType = cursor.getInt(MessageListAdapter.COLUMN_SMS_TYPE);
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
        details.append(cursor.getString(MessageListAdapter.COLUMN_SMS_ADDRESS));

        // Date: ***
        details.append('\n');
        if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE);
        details.append(MessageUtils.formatTimeStampString(context, date, true));

        // Error code: ***
        int errorCode = cursor.getInt(MessageListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }

    static private String getPriorityDescription(Context context, int PriorityValue) {
        Resources res = context.getResources();
        switch(PriorityValue) {
            case PduHeaders.PRIORITY_HIGH:
                return res.getString(R.string.priority_high);
            case PduHeaders.PRIORITY_LOW:
                return res.getString(R.string.priority_low);
            case PduHeaders.PRIORITY_NORMAL:
            default:
                return res.getString(R.string.priority_normal);
        }
    }

    public static int getAttachmentType(SlideshowModel model) {
        if (model == null) {
            return WorkingMessage.TEXT;
        }

        int numberOfSlides = model.size();
        if (numberOfSlides > 1) {
            return WorkingMessage.SLIDESHOW;
        } else if (numberOfSlides == 1) {
            // Only one slide in the slide-show.
            SlideModel slide = model.get(0);
            if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            }

            if (slide.hasAudio() && slide.hasImage()) {
                return WorkingMessage.SLIDESHOW;
            }

            if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }

            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            }

            if (slide.hasVcard()) {
                return WorkingMessage.VCARD;
            }

            if (slide.hasOtherFile()) {
                return WorkingMessage.OTHER_FILE;
            }

//            if (slide.hasText()) {
//                return WorkingMessage.TEXT;
//            }
        }
        //===== fixed CR<NEWMS00137442> by luning at 11-10-26 begin =====
        if(model.hasVcard()){
        	return WorkingMessage.VCARD;
        }
        //===== fixed CR<NEWMS00137442> by luning at 11-10-26 end =====
        if(model.hasOtherFile()){/*fixed CR<NEWMS00144166> by luning at 2011.11.28*/
            return WorkingMessage.OTHER_FILE;
        }
        return WorkingMessage.TEXT;
    }

    public static String formatTimeStampString(Context context, long when) {
        return formatTimeStampString(context, when, false);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        // If the caller has asked for full details, make sure to show the date
        // and time no matter what we've determined above (but still make showing
        // the year only happen if it is a different year from today).
        if (fullFormat) {
            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }

        return DateUtils.formatDateTime(context, when, format_flags);
    }

    /**
     * @parameter recipientIds space-separated list of ids
     */
    public static String getRecipientsByIds(Context context, String recipientIds,
                                            boolean allowQuery) {
        String value = sRecipientAddress.get(recipientIds);
        if (value != null) {
            return value;
        }
        if (!TextUtils.isEmpty(recipientIds)) {
            StringBuilder addressBuf = extractIdsToAddresses(
                    context, recipientIds, allowQuery);
            if (addressBuf == null) {
                // temporary error?  Don't memoize.
                return "";
            }
            value = addressBuf.toString();
        } else {
            value = "";
        }
        sRecipientAddress.put(recipientIds, value);
        return value;
    }

    private static StringBuilder extractIdsToAddresses(Context context, String recipients,
                                                       boolean allowQuery) {
        StringBuilder addressBuf = new StringBuilder();
        String[] recipientIds = recipients.split(" ");
        boolean firstItem = true;
        for (String recipientId : recipientIds) {
            String value = sRecipientAddress.get(recipientId);

            if (value == null) {
                if (!allowQuery) {
                    // when allowQuery is false, if any value from sRecipientAddress.get() is null,
                    // return null for the whole thing. We don't want to stick partial result
                    // into sRecipientAddress for multiple recipient ids.
                    return null;
                }

                Uri uri = Uri.parse("content://mms-sms/canonical-address/" + recipientId);
                Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                                               uri, null, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            value = c.getString(0);
                            sRecipientAddress.put(recipientId, value);
                        }
                    } finally {
                        c.close();
                    }
                }
            }
            if (value == null) {
                continue;
            }
            if (firstItem) {
                firstItem = false;
            } else {
                addressBuf.append(";");
            }
            addressBuf.append(value);
        }

        return (addressBuf.length() == 0) ? null : addressBuf;
    }

    public static void selectAudio(Context context, int requestCode) {
        if (context instanceof Activity) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    context.getString(R.string.select_audio));
            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

    public static void recordSound(Context context, int requestCode) {
        if (context instanceof Activity) {
            long sizeLimit = MmsConfig.getMaxMessageSize() - SlideshowModel.SLIDESHOW_SLOP;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(ContentType.AUDIO_AMR);
            intent.putExtra("android.intent.extra.sizeLimit", sizeLimit);
            intent.setClassName("com.android.soundrecorder",
                    "com.android.soundrecorder.SoundRecorder");

            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

	public static void recordVideo(Context context, int requestCode, long sizeLimit) {
		if (context instanceof Activity) {
			if (sizeLimit > 0) {
				int durationLimit = CamcorderProfile
						.get(CamcorderProfile.QUALITY_LOW).duration;
				Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
				intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
				intent.putExtra("android.intent.extra.sizeLimit", sizeLimit);
				intent.putExtra("android.intent.extra.durationLimit",
						durationLimit);
				((Activity) context)
				.startActivityForResult(intent, requestCode);
			}

		}
	}
    public static void selectVideo(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.VIDEO_UNSPECIFIED);
    }

    public static void selectImage(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.IMAGE_UNSPECIFIED);
    }

    private static void selectMediaByType(
            Context context, int requestCode, String contentType) {
         if (context instanceof Activity) {

            Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);

            innerIntent.setType(contentType);

            Intent wrapperIntent = Intent.createChooser(innerIntent, null);

            ((Activity) context).startActivityForResult(wrapperIntent, requestCode);
        }
    }

    public static void viewSimpleSlideshow(Context context, SlideshowModel slideshow) {
        if (!slideshow.isSimple()) {
            throw new IllegalArgumentException(
                    "viewSimpleSlideshow() called on a non-simple slideshow");
        }
        SlideModel slide = slideshow.get(0);
        MediaModel mm = null;
        if (slide.hasImage()) {
            mm = slide.getImage();
        } else if (slide.hasVideo()) {
            mm = slide.getVideo();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        //CR NEWMS00107171 Modify Start
        intent.putExtra("isFromMms", true);
        //CR NEWMS00107171 Modify End

        String contentType;
        if (mm.isDrmProtected()) {
            contentType = mm.getDrmObject().getContentType();
        } else {
            contentType = mm.getContentType();
        }
        intent.setDataAndType(mm.getUri(), contentType);
        context.startActivity(intent);
    }

    public static void showErrorDialog(Context context,
            String title, String message) {
        if(builder!=null){
            return;
        }
        builder = new AlertDialog.Builder(context);
        String messagebody = message;
        builder.setIcon(R.drawable.ic_sms_mms_not_delivered);
        builder.setTitle(title);
        if(title.equals(context.getString(R.string.exceed_message_size_limitation))){
            messagebody = messagebody + "\n" + context.getString(R.string.size_limit_comments_all);
        }
        builder.setMessage(messagebody);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    dialog.dismiss();
                    builder = null;
                }
            }
        });
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_BACK) {
                        dialog.dismiss();
                        builder = null;
                    }
                    return false;
            }
        });
        builder.show();
    }

    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 80;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 50;

    public static Uri saveBitmapAsPart(Context context, Uri messageUri, Bitmap bitmap)
            throws MmsException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, IMAGE_COMPRESSION_QUALITY, os);

        PduPart part = new PduPart();

        part.setContentType("image/jpeg".getBytes());
        String contentId = "Image" + System.currentTimeMillis();
        part.setContentLocation((contentId + ".jpg").getBytes());
        part.setContentId(contentId.getBytes());
        part.setData(os.toByteArray());

        Uri retVal = PduPersister.getPduPersister(context).persistPart(part,
                        ContentUris.parseId(messageUri));

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("saveBitmapAsPart: persisted part with uri=" + retVal);
        }

        return retVal;
    }

    /**
     * Message overhead that reduces the maximum image byte size.
     * 5000 is a realistic overhead number that allows for user to also include
     * a small MIDI file or a couple pages of text along with the picture.
     */
    public static final int MESSAGE_OVERHEAD = 5000;

    public static void resizeImageAsync(final Context context,
            final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb,
            final boolean append) {

        // Show a progress toast if the resize hasn't finished
        // within one second.
        // Stash the runnable for showing it away so we can cancel
        // it later if the resize completes ahead of the deadline.
        final Runnable showProgress = new Runnable() {
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 begin =====
        // Schedule it for one second from now.
//        handler.postDelayed(showProgress, 1000);
        // Schedule it at once!
        handler.post(showProgress);
        //===== fixed CR<NEWSM00125959> by luning at 11-09-26 end =====
        new Thread(new Runnable() {
            public void run() {
                final PduPart part;
                try {
                    UriImage image = new UriImage(context, imageUri);
                    part = image.getResizedImageAsPart(
                        MmsConfig.getMaxImageWidth(),
                        MmsConfig.getMaxImageHeight(),
                        MmsConfig.getMaxMessageSize() - MESSAGE_OVERHEAD);
                } finally {
                    // Cancel pending show of the progress toast if necessary.
                    handler.removeCallbacks(showProgress);
                }

                handler.post(new Runnable() {
                    public void run() {
                        cb.onResizeResult(part, append);
                    }
                });
            }
        }).start();
    }

    public static void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.discard_message)
                .setMessage(R.string.discard_message_reason)
                .setPositiveButton(R.string.yes, listener)
                //.setNeutralButton(R.string.save_as_draft, listener)  //add by liguxiang 10-11-11 for NEWM00129822
                .setNegativeButton(R.string.no, null)
                .show();
    }

    public static String getLocalNumber() {
        //if (null == sLocalNumber) {
        if ( needLocalNumber ) {
            sLocalNumber = MmsApp.getApplication().getTelephonyManager().getLine1Number();
            needLocalNumber = false;
        }
        return sLocalNumber;
    }

    public static boolean isLocalNumber(String number) {
        if (number == null) {
            return false;
        }

        // we don't use Mms.isEmailAddress() because it is too strict for comparing addresses like
        // "foo+caf_=6505551212=tmomail.net@gmail.com", which is the 'from' address from a forwarded email
        // message from Gmail. We don't want to treat "foo+caf_=6505551212=tmomail.net@gmail.com" and
        // "6505551212" to be the same.
        if (number.indexOf('@') >= 0) {
            return false;
        }

        return PhoneNumberUtils.compare(number, getLocalNumber());
    }

    public static void handleReadReport(final Context context,
            final long threadId,
            final int status,
            final Runnable callback) {
        String selection = Mms.MESSAGE_TYPE + " = " + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
            + " AND " + Mms.READ + " = 0"
            + " AND " + Mms.READ_REPORT + " = " + PduHeaders.VALUE_YES;

        if (threadId != -1) {
            selection = selection + " AND " + Mms.THREAD_ID + " = " + threadId;
        }

        final Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                        Mms.Inbox.CONTENT_URI, new String[] {Mms._ID, Mms.MESSAGE_ID},
                        selection, null, null);

        if (c == null) {
            return;
        }

        final Map<String, String> map = new HashMap<String, String>();
        try {
            if (c.getCount() == 0) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            while (c.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, c.getLong(0));
                map.put(c.getString(1), AddressUtils.getFrom(context, uri));
            }
        } finally {
            c.close();
        }

        OnClickListener positiveListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                for (final Map.Entry<String, String> entry : map.entrySet()) {
                    MmsMessageSender.sendReadRec(context, entry.getValue(),
                                                 entry.getKey(), status);
                }

                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnClickListener negativeListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        confirmReadReportDialog(context, positiveListener,
                                         negativeListener,
                                         cancelListener);
    }

    //===== fixed CR<NEWMS00127040> by luning at 11-10-07 begin =====
	public static void handleReadReport(final Context context,
			final HashMap<String, String> map, final int status,
			final Runnable callback) {

		OnClickListener positiveListener = new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				for (final Map.Entry<String, String> entry : map.entrySet()) {
					MmsMessageSender.sendReadRec(context, entry.getValue(),
							entry.getKey(), status);
				}

				if (callback != null) {
					callback.run();
				}
			}
		};

		OnClickListener negativeListener = new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (callback != null) {
					callback.run();
				}
			}
		};

		OnCancelListener cancelListener = new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				if (callback != null) {
					callback.run();
				}
			}
		};

		confirmReadReportDialog(context, positiveListener, negativeListener,
				cancelListener);
	}
	//===== fixed CR<NEWMS00127040> by luning at 11-10-07 end =====


    private static void confirmReadReportDialog(Context context,
            OnClickListener positiveListener, OnClickListener negativeListener,
            OnCancelListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.message_send_read_report);
        builder.setPositiveButton(R.string.yes, positiveListener);
        builder.setNegativeButton(R.string.no, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.show();
    }

    public static String extractEncStrFromCursor(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (TextUtils.isEmpty(rawBytes)) {
            return "";
        } else if (charset == CharacterSets.ANY_CHARSET) {
            return rawBytes;
        } else {
            return new EncodedStringValue(charset, PduPersister.getBytes(rawBytes)).getString();
        }
    }

    private static String extractEncStr(Context context, EncodedStringValue value) {
        if (value != null) {
            return value.getString();
        } else {
            return "";
        }
    }

    public static ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            accumulator.add(spans[i].getURL());
        }
        return accumulator;
    }

    /**
     * Play/view the message attachments.
     * TOOD: We need to save the draft before launching another activity to view the attachments.
     *       This is hacky though since we will do saveDraft twice and slow down the UI.
     *       We should pass the slideshow in intent extra to the view activity instead of
     *       asking it to read attachments from database.
     * @param context
     * @param msgUri the MMS message URI in database
     * @param slideshow the slideshow to save
     * @param persister the PDU persister for updating the database
     * @param sendReq the SendReq for updating the database
     */
    public static void viewMmsMessageAttachment(Context context, Uri msgUri,
            SlideshowModel slideshow) {
        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
//        if (isSimple) {
//            // In attachment-editor mode, we only ever have one slide.
//            MessageUtils.viewSimpleSlideshow(context, slideshow);
//        } else {
//            // If a slideshow was provided, save it to disk first.
            if (slideshow != null) {
                PduPersister persister = PduPersister.getPduPersister(context);
                try {
                    PduBody pb = slideshow.toPduBody();
                    persister.updateParts(msgUri, pb);
                    slideshow.sync(pb);
                } catch (MmsException e) {
                    Log.e(TAG, "Unable to save message for preview");
                    return;
                }
            }
            // Launch the slideshow activity to play/view.
            Intent intent = new Intent(context, SlideshowActivity.class);
            intent.setData(msgUri);
            context.startActivity(intent);
//        }
    }

    public static void viewMmsMessageAttachment(Context context, WorkingMessage msg) {
        if (msg.isDiscarded()) {
            return;
        }
        SlideshowModel slideshow = msg.getSlideshow();
        if (slideshow == null) {
            throw new IllegalStateException("msg.getSlideshow() == null");
        }
        if (slideshow.isSimple()) {
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            Uri uri = msg.saveAsMms(false);
            viewMmsMessageAttachment(context, uri, slideshow);
        }
    }

    /**
     * Debugging
     */
    public static void writeHprofDataToFile(){
        String filename = Environment.getExternalStorageDirectory() + "/mms_oom_hprof_data";
        try {
            android.os.Debug.dumpHprofData(filename);
            Log.i(TAG, "written hprof data to " + filename);
        } catch (IOException ex) {
            Log.e(TAG, "writeHprofDataToFile: caught IOException --> " + ex);
        }
        // ===== fixed CR<NEWMS00121388> by luning at 11-09-15 begin=====
        catch (Exception ex){
        	Log.e(TAG, "writeHprofDataToFile: caught Exception --> " + ex);
        }
        // ===== fixed CR<NEWMS00121388> by luning at 11-09-15 end=====
    }

    public static boolean isAlias(String string) {
        if (!MmsConfig.isAliasEnabled()) {
            return false;
        }

        if (TextUtils.isEmpty(string)) {
            return false;
        }

        // TODO: not sure if this is the right thing to use. Mms.isPhoneNumber() is
        // intended for searching for things that look like they might be phone numbers
        // in arbitrary text, not for validating whether something is in fact a phone number.
        // It will miss many things that are legitimate phone numbers.
        if (Mms.isPhoneNumber(string)) {
            return false;
        }

        if (!isAlphaNumeric(string)) {
            return false;
        }

        int len = string.length();

        if (len < MmsConfig.getAliasMinChars() || len > MmsConfig.getAliasMaxChars()) {
            return false;
        }

        return true;
    }

    public static boolean isAlphaNumeric(String s) {
        char[] chars = s.toCharArray();
        for (int x = 0; x < chars.length; x++) {
            char c = chars[x];

            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= '0') && (c <= '9')) {
                continue;
            }

            return false;
        }
        return true;
    }




    /**
     * Given a phone number, return the string without syntactic sugar, meaning parens,
     * spaces, slashes, dots, dashes, etc. If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String parsePhoneNumberForMms(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);

            // accept the first '+' in the address
            if (c == '+' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (numericSugarMap.get(c) == null) {
                return null;
            }
        }
        return builder.toString();
    }

    /**
     * Returns true if the address passed in is a valid MMS address.
     */
    public static boolean isValidMmsAddress(String address) {
        String retVal = parseMmsAddress(address);
        Log.d(TAG, "[isValidMmsAddress] address =" + address);
        return (retVal != null && !retVal.trim().equals(""));
    }

    /**
     * parse the input address to be a valid MMS address.
     * - if the address is an email address, leave it as is.
     * - if the address can be parsed into a valid MMS phone number, return the parsed number.
     * - if the address is a compliant alias address, leave it as is.
     */
    public static String parseMmsAddress(String address) {
        // if it's a valid Email address, use that.
        if (Mms.isEmailAddress(address)) {
            return address;
        }

        // if we are able to parse the address to a MMS compliant phone number, take that.
        String retVal = parsePhoneNumberForMms(address);
        if (retVal != null) {
            return retVal;
        }

        // if it's an alias compliant address, use that.
        if (isAlias(address)) {
            return address;
        }

        // it's not a valid MMS address, return null
        return null;
    }

    public static boolean isSimMemFull(int phoneId) {
        SmsManager smsManager = SmsManager.getDefault(phoneId);
        String capaStr = smsManager.getSimCapacity();
        Log.d(TAG, "[sms]isSimMemFull =" + capaStr);
        if (capaStr != null) {
            String[] splitStr = capaStr.split(":");
            Log.d(TAG, "[sms]isSimMemFull simUsed:" + splitStr[0]);
            Log.d(TAG, "[sms]isSimMemFull simTotal:" + splitStr[1]);
            return splitStr[0].equals(splitStr[1]);
        } else {
            Log.d(TAG, "[sms]isSimMemFull get capaStr fail");
            return false;
        }
    }

    private static void log(String msg) {
        Log.d(TAG, "[MsgUtils] " + msg);
    }

    // lino add begin
    public static final int NO_SDCARD = -1;

    public static final int SDCARD_READ_ONLY = 0;

    public static final int SDCARD_BUSY = 1;

    public static final int SDCARD_WRITE = 2;

    public static final int SDCARD_NO_SIZE = 3;

    public static final int SDCARD_AVAILABLE = 4;

    public static final String SAVE_MMS_DIR = "mms/";

    public static boolean showSaveErroDialog(Context context, String filename, long filesize) {
        int sdcardstatus = getSdcardStatus();
        int error;
        if (sdcardstatus == SDCARD_WRITE) {
            long sdsize = getSdcardAvailableSize();
            Log.d(TAG,"showSaveErroDialog() filesize:"+filesize+" sdsize:"+sdsize+" (filesize < sdsize):"+(filesize < sdsize));
            if (filesize < sdsize) {
                error = SDCARD_AVAILABLE;
            } else {
                error = SDCARD_NO_SIZE;
            }
        } else {
            error = sdcardstatus;
        }
        if (error == SDCARD_AVAILABLE) {
            return true;
        }
        int title = 0;
        String msg = "";
        switch (error) {
            case NO_SDCARD:
                title = R.string.save_no_sdcard_dlg_title;
                msg = context.getString(R.string.save_no_sdcard_dlg_msg, filename);
                break;
            case SDCARD_READ_ONLY:
                title = R.string.save_file_error_read_only_title;
                msg = context.getString(R.string.save_file_error_read_only_msg);
                break;
            case SDCARD_BUSY:
                title = R.string.save_sdcard_busy_dlg_title;
                msg = context.getString(R.string.save_sdcard_busy_dlg_msg);
                break;
            case SDCARD_NO_SIZE:
                title = R.string.save_file_error_dlg_title;
                msg = context.getString(R.string.save_file_error_dlg_msg, filename);
                break;
        }
        new AlertDialog.Builder(context).setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert).setMessage(msg).setPositiveButton(
                        R.string.yes, null).show();
        return false;
    }

    // Check to see if we have an SDCard and Sdcard can been written
    public static int getSdcardStatus() {
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            return NO_SDCARD;
        }
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            return SDCARD_READ_ONLY;
        }
        if (status.equals(Environment.MEDIA_SHARED)) {
            return SDCARD_BUSY;
        }
        return SDCARD_WRITE;
    }

    // get the available size of sdcard
    public static long getSdcardAvailableSize() {
        StatFs statfs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long size = (long)statfs.getBlockSize() * (long)statfs.getAvailableBlocks();
        return size;
    }

    public static boolean saveFile(Context context, MediaModel mediaModel, String dir) {
        File sdcard = Environment.getExternalStorageDirectory();
        File fdir = new File(sdcard, dir);
        if (!fdir.isDirectory() || !fdir.exists()) {
            fdir.mkdir();
        }

        String filename = "";//fix for bug 11792
        if (mediaModel.getSrc().startsWith("cid:")) {
            filename = mediaModel.getSrc().substring("cid:".length());
        }else{
            filename = mediaModel.getSrc();
        }
        // add for some media donot have suffix
        if (filename.indexOf(".") == -1) {
            String type = new String(mediaModel.getContentType());
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            filename = filename + "." + extension;
        }
        File file = new File(fdir, filename);
        FileOutputStream fops;
        InputStream ips;

        try {
            ips = context.getContentResolver().openInputStream(mediaModel.getUri());
            fops = new FileOutputStream(file);
            byte[] data = new byte[1024];
            try {
                while (ips.read(data) != -1) {
                    fops.write(data);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            } finally {
                if (ips != null) {
                    try {
                        ips.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                if (fops != null) {
                    try {
                        fops.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        // Notify other applications listening to scanner events
        // that a media file has been added to the sd card
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                             Uri.fromFile(file)));
        return true;
    }
    // lino add end

    //Add by huibin For Image Rotate
    public static Bitmap makeBitmap(String str) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(str,options);
            if (options.mCancel || options.outWidth == -1
                    || options.outHeight == -1) {
                return null;
            }
            options.inSampleSize = computeSampleSize(
                    options, UNCONSTRAINED, MmsConfig.getMaxImageHeight() * MmsConfig.getMaxImageWidth());
            
            Log.i(TAG, "MmsConfig.getMaxImageHeight() is"+MmsConfig.getMaxImageHeight());
            Log.i(TAG, "MmsConfig.getMaxImageWidth() is"+MmsConfig.getMaxImageWidth());
            options.inJustDecodeBounds = false;

            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(str,options);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Got oom exception ", ex);
            return null;
        }
    }

    public static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            Log.e(TAG, "cannot read exif", ex);
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }
        return degree;
    }

    public static int computeSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(options, minSideLength,
                maxNumOfPixels);

        int roundedSize;
        if (initialSize <= 8) {
            roundedSize = 1;
            while (roundedSize < initialSize) {
                roundedSize <<= 1;
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }

        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        double w = options.outWidth;
        double h = options.outHeight;

        int lowerBound = (maxNumOfPixels == UNCONSTRAINED) ? 1 :
                (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength == UNCONSTRAINED) ? 128 :
                (int) Math.min(Math.floor(w / minSideLength),
                Math.floor(h / minSideLength));

        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }

        if ((maxNumOfPixels == UNCONSTRAINED) &&
                (minSideLength == UNCONSTRAINED)) {
            return 1;
        } else if (minSideLength == UNCONSTRAINED) {
            return lowerBound;
        } else {
            return upperBound;
        }
    }

    public static void rotatePicture(String str){
        int degree  = MessageUtils.getExifOrientation(str);
        Bitmap bit = makeBitmap(str);
        if(bit != null)
        {
        	Bitmap newBitmap = rotate(bit,degree);
            OutputStream stream = null;
            try {
                    stream = new FileOutputStream(str);
            } catch (FileNotFoundException e) {
                    e.printStackTrace();
            }
            if(newBitmap != null){
            	newBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            	if(!newBitmap.isRecycled())
            	{
                    Log.i(TAG,"newBitmap.isRecycled()");
            		newBitmap.recycle();
                }
            }
        }
        if (bit != null) {
            bit.recycle();
            bit = null;
        }
    }

    public static Bitmap rotate(Bitmap b, int degrees) {
        return rotateAndMirror(b, degrees, false);
    }
    public static Bitmap rotateAndMirror(Bitmap b, int degrees, boolean mirror) {
        if ((degrees != 0 || mirror) && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees,
                    (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            if (mirror) {
                m.postScale(-1, 1);
                degrees = (degrees + 360) % 360;
                if (degrees == 0 || degrees == 180) {
                    m.postTranslate((float) b.getWidth(), 0);
                } else if (degrees == 90 || degrees == 270) {
                    m.postTranslate((float) b.getHeight(), 0);
                } else {
                    throw new IllegalArgumentException("Invalid degrees=" + degrees);
                }
            }
            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
            	ex.printStackTrace();
            }
        }
        return b;
    }

    public static boolean isMSMS = TelephonyManager.getPhoneCount() > 1;

    public static int getFileIconId(String fileName) {
        if (null == fileName) {
            return R.drawable.unknown;
        }
        int index = fileName.indexOf('.');
        if (index == -1) {
            return R.drawable.unknown;
        } else {
            String suffix = fileName.substring(index + 1);
            if ("txt".equalsIgnoreCase(suffix)) {
                return R.drawable.txt;
            } else if ("html".equalsIgnoreCase(suffix) || "htm".equalsIgnoreCase(suffix)) {
                return R.drawable.html;
            } else if ("xls".equalsIgnoreCase(suffix)) {
                return R.drawable.xls;
            } else if ("doc".equalsIgnoreCase(suffix)) {
                return R.drawable.doc;
            } else if ("pdf".equalsIgnoreCase(suffix)) {
                return R.drawable.pdf;
            } else if ("ppt".equalsIgnoreCase(suffix)) {
                return R.drawable.ppt;
            } else if ("vcs".equalsIgnoreCase(suffix)) {
                return R.drawable.vcs;
            } else if ("rar".equalsIgnoreCase(suffix)) {
                return R.drawable.rar;
            } else if ("zip".equalsIgnoreCase(suffix)) {
                return R.drawable.zip;
            } else if ("apk".equalsIgnoreCase(suffix)) {
                return R.drawable.apk;
            } else if ("bin".equalsIgnoreCase(suffix)) {
                return R.drawable.bin;
            } else {
                return R.drawable.unknown;
            }
        }
    }

    public static byte[] GetSctsTime(Time time) {
        Calendar rightNow = Calendar.getInstance();
        byte[] data = new byte[7];

        int year = time.year > 2000 ? time.year - 2000 : time.year -1900;
        data[0] = (byte) (year);
        data[1] = (byte) (time.month+1);
        data[2] = (byte) (time.monthDay);
        data[3] = (byte) (time.hour);
        data[4] = (byte) (time.minute);
        data[5] = (byte) (time.second);

        int tmp = rightNow.getTimeZone().getOffset(rightNow.getTimeInMillis());
        data[6] = (byte)(tmp / 1000 / 900);
        for (int i = 0; i < 6; i++) {
            data[i] = (byte) (((data[i] / 10) & 0xF) | ((data[i] % 10) << 4));
        }
        tmp = Math.abs(data[6]);
        tmp = ((tmp / 10) & 0xF) | ((tmp % 10) << 4);
        if (data[6] < 0) {
            tmp |= 0x8;
        }
        data[6] = (byte)tmp;

        return data;
    }

    public static boolean hasAngel(final Context context, final Uri imageUri) {
        UriImage image = new UriImage(context, imageUri);
        if (image != null && image.getOrientation() != 0) {
            return true;
        }

        return false;
    }
}
