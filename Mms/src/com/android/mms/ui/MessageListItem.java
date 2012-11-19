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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.FileModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.VcardModel;
import com.android.mms.transaction.Transaction;
import com.android.mms.transaction.TransactionBundle;
import com.android.mms.transaction.TransactionService;
import com.android.mms.transaction.TransactionServiceHelper;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.SmileyParser;
import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.PduHeaders;
import android.telephony.SmsManager;
import android.telephony.gsm.SmsMessage;

/**
 * This class provides view of a message in the messages list.
 */
public class MessageListItem extends LinearLayout implements
        SlideViewInterface, OnClickListener {
    public static final String EXTRA_URLS = "com.android.mms.ExtraUrls";

    private static final String TAG = "MessageListItem";
    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    static final int MSG_LIST_EDIT_MMS   = 1;
    static final int MSG_LIST_EDIT_SMS   = 2;

    private View mMsgListItem;
    private View mMmsView;
    private ImageView mImageView;
    private ImageView mLockedIndicator;
    private ImageView mDeliveredIndicator;
    private ImageView mDetailsIndicator;
    private ImageView mWarningDownloadIndicator;
    private ImageView mSimIndicator;
    private ImageButton mSlideShowButton;
    private TextView mBodyTextView;
    private TextView mDownloadLimitText;
    private Button mDownloadButton;
    private TextView mDownloadingLabel;
    private QuickContactBadge mAvatar;
    private Handler mHandler;
    private MessageItem mMessageItem;
    private int mPhoneId = 0;

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        int color = mContext.getResources().getColor(R.color.timestamp_color);
        mColorSpan = new ForegroundColorSpan(color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

//        mMsgListItem = this;
//        = findViewById(R.id.msg_list_item);
        mBodyTextView = (TextView) findViewById(R.id.text_view);
        mDownloadLimitText = (TextView) findViewById(R.id.download_beyond_limit_warnning);
        mLockedIndicator = (ImageView) findViewById(R.id.locked_indicator);
        mDeliveredIndicator = (ImageView) findViewById(R.id.delivered_indicator);
        mDetailsIndicator = (ImageView) findViewById(R.id.details_indicator);
	mSimIndicator = (ImageView) findViewById(R.id.sim_indicator);
        mWarningDownloadIndicator = (ImageView) findViewById(R.id.not_allow_download);
        mAvatar = (QuickContactBadge) findViewById(R.id.avatar);
        mAvatar.setVisibility(View.GONE);
        ViewGroup.MarginLayoutParams badgeParams = (MarginLayoutParams)mAvatar.getLayoutParams();
        //liaobz hide the avatar
        final int badgeWidth = 0;//badgeParams.width + badgeParams.rightMargin + badgeParams.leftMargin;

        int lineHeight = mBodyTextView.getLineHeight();
        int effectiveBadgeHeight = badgeParams.height + badgeParams.topMargin - mBodyTextView.getPaddingTop();
        final int indentLineCount = (int) ((effectiveBadgeHeight-1) / lineHeight) + 1;

        mLeadingMarginSpan = new LeadingMarginSpan.LeadingMarginSpan2() {
            public void drawLeadingMargin(Canvas c, Paint p, int x, int dir,
                    int top, int baseline, int bottom, CharSequence text,
                    int start, int end, boolean first, Layout layout) {
                // no op
            }

            public int getLeadingMargin(boolean first) {
                return first ? badgeWidth : 0;
            }

            public int getLeadingMarginLineCount() {
                //via liaobz 图片挤压效果影响行数
                return 1;
            }
        };

    }

    public void bind(MessageListAdapter.AvatarCache avatarCache, MessageItem msgItem) {
        mMessageItem = msgItem;

        setLongClickable(false);

        switch (msgItem.mMessageType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                bindNotifInd(msgItem);
                break;
            default:
                bindCommonMessage(avatarCache, msgItem);
                break;
        }
    }
    public void setTextSize(float textSize){
        //liaobz mms textSize
        mBodyTextView.setTextSize(30);
    }
    public MessageItem getMessageItem() {
        return mMessageItem;
    }

    public void setMsgListItemHandler(Handler handler) {
        mHandler = handler;
    }

    private void bindNotifInd(final MessageItem msgItem) {
        hideMmsViewIfNeeded();

        String msgSizeText = mContext.getString(R.string.message_size_label)
                                + String.valueOf((msgItem.mMessageSize + 1023) / 1024)
                                + mContext.getString(R.string.kilobyte);

        //===== fixed CR<NEWMS00120677> by luning at 11-09-17 begin=====
//        mBodyTextView.setText(formatMessage(msgItem, msgItem.mContact, null, msgItem.mSubject,
//                msgSizeText + "\n" + msgItem.mTimestamp,
//                msgItem.mHighlight, msgItem.mTextContentType));
        CharSequence formatMessage = formatMessage(msgItem, msgItem.mContact, null, msgItem.mSubject,
                msgSizeText + "\n" + msgItem.mTimestamp,
                msgItem.mHighlight, msgItem.mTextContentType);
        SpannableString s = new SpannableString(formatMessage);
        formatMessage = (SpannableString) Linkify.findLinks(s, Linkify.ALL);
        mBodyTextView.setText(formatMessage);
        s = null;
        //===== fixed CR<NEWMS00120677> by luning at 11-09-17 end=====
        int state = DownloadManager.getInstance().getState(msgItem.mMessageUri);

        if (MessageUtils.isMSMS) {
        	mPhoneId = msgItem.mPhoneId;
    	}else{
    		mPhoneId = 0;
    	}

        switch (state) {
            case DownloadManager.STATE_DOWNLOADING:
                inflateDownloadControls();
                mDownloadingLabel.setVisibility(View.VISIBLE);
                mDownloadButton.setVisibility(View.GONE);
                break;
            case DownloadManager.STATE_UNSTARTED:
            case DownloadManager.STATE_TRANSIENT_FAILURE:
            case DownloadManager.STATE_PERMANENT_FAILURE:
            default:
                setLongClickable(true);
                inflateDownloadControls();

                //20120131
                boolean isBeyondLimit = DownloadManager.getInstance().checkPduTotalSizeLimit(msgItem.mMessageSize);
                if(isBeyondLimit){
                    mDownloadLimitText.setText(mContext.getString(R.string.download_beyond_limit));
                    mDownloadLimitText.setTextColor(Color.RED);
                    mWarningDownloadIndicator.setVisibility(View.VISIBLE);
                }else{
                	mDownloadingLabel.setVisibility(View.GONE);
                    mDownloadButton.setVisibility(View.VISIBLE);
                    mDownloadButton.setOnClickListener(new OnClickListener() {
                        public void onClick(View v)
                        {
                            mDownloadingLabel.setVisibility(View.VISIBLE);
                            mDownloadButton.setVisibility(View.GONE);
                            Intent intent = null;
                            if (MessageUtils.isMSMS){
                            	intent = new Intent(mContext, TransactionServiceHelper
                                        .getTransactionServiceClass(mPhoneId));
                            }
                            else{
                            	intent = new Intent(mContext, TransactionService.class);
                            }
                            intent.putExtra(TransactionBundle.URI, msgItem.mMessageUri.toString());
                            intent.putExtra(TransactionBundle.TRANSACTION_TYPE,
                                    Transaction.RETRIEVE_TRANSACTION);
                            mContext.startService(intent);
                        }
                    });
                }
                //20120131
                break;
        }

        // Hide the indicators.
        mLockedIndicator.setVisibility(View.GONE);
        mDeliveredIndicator.setVisibility(View.GONE);
        mDetailsIndicator.setVisibility(View.GONE);

        drawLeftStatusIndicator(msgItem.mBoxId, msgItem.mStatus);
        //Sim Lock
        drawRightStatusIndicator(msgItem);
    }

    private void bindCommonMessage(final MessageListAdapter.AvatarCache avatarCache, final MessageItem msgItem) {
        if (mDownloadButton != null) {
            mDownloadButton.setVisibility(View.GONE);
            mDownloadingLabel.setVisibility(View.GONE);
        }
        // Since the message text should be concatenated with the sender's
        // address(or name), I have to display it here instead of
        // displaying it by the Presenter.
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());

        String addr = null;
        if (!Sms.isOutgoingFolder(msgItem.mBoxId)) {
            addr = msgItem.mAddress;
        } else {
            addr = "";//MmsApp.getApplication().getTelephonyManager().getLine1Number();//fix for bug 12621
        }
        if (!TextUtils.isEmpty(addr) && !msgItem.isWapPush) {
            MessageListAdapter.AvatarCache.ContactData contactData = avatarCache.get(addr);
            mAvatar.setImageDrawable(contactData.getAvatar());
            Uri contactUri = contactData.getContactUri();
            // Since we load the contact info in the background, on the first screenfull of
            // messages, it's likely we haven't loaded the contact URI info yet. In that case,
            // fall back and use the phone number.
            if (contactUri != null) {
                mAvatar.assignContactUri(contactUri);
            } else {
                mAvatar.assignContactFromPhone(addr, true);
            }
        } else if (msgItem.isWapPush) {
            MessageListAdapter.AvatarCache.ContactData contactData = avatarCache.get(addr);
            mAvatar.setImageDrawable(contactData.getAvatar());
            mAvatar.assignContactUri(null);
        } else {
            //fix bug 13637--start
            // set default avatar for local number
            Bitmap b = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_contact_picture);
            mAvatar.setImageDrawable(new BitmapDrawable(mContext.getResources(), b));
            //fix bug 13637 --end
            mAvatar.assignContactUri(null);
        }

        // Get and/or lazily set the formatted message from/on the
        // MessageItem.  Because the MessageItem instances come from a
        // cache (currently of size ~50), the hit rate on avoiding the
        // expensive formatMessage() call is very high.
        CharSequence formattedMessage = msgItem.getCachedFormattedMessage();
        if (formattedMessage == null) {
            formattedMessage = formatMessage(msgItem, msgItem.mContact, msgItem.mBody,
                                             msgItem.mSubject, msgItem.mTimestamp,
                                             msgItem.mHighlight, msgItem.mTextContentType);
        }
        //===== fixed CR<NEWMS00120677> by luning at 11-09-17 begin=====
//        mBodyTextView.setText(formattedMessage);
        SpannableString s = new SpannableString(formattedMessage);
        formattedMessage = (SpannableString) Linkify.findLinks(s, Linkify.ALL);
        mBodyTextView.setText(formattedMessage);
        s = null;
        //===== fixed CR<NEWMS00120677> by luning at 11-09-17 end=====
        if (msgItem.isSms()) {
            hideMmsViewIfNeeded();
        } else {
            Presenter presenter = PresenterFactory.getPresenter(
                    "MmsThumbnailPresenter", mContext,
                    this, msgItem.mSlideshow);
            if (msgItem.mAttachmentType != WorkingMessage.TEXT) {
                inflateMmsView();
                mMmsView.setVisibility(View.VISIBLE);
                setOnClickListener(msgItem);
                drawPlaybackButton(msgItem);
            } else {
                hideMmsViewIfNeeded();
            }
            presenter.present();
        }

        drawLeftStatusIndicator(msgItem.mBoxId, msgItem.mStatus);
        drawRightStatusIndicator(msgItem);

        requestLayout();
    }

    private void hideMmsViewIfNeeded() {
        if (mMmsView != null) {
            mMmsView.setVisibility(View.GONE);
        }
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
        inflateMmsView();

        try {
            if (null == bitmap) {

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(getResources(),R.drawable.ic_missing_thumbnail_picture,options);

                options.inSampleSize = ImageModel.computeSampleSize(
                        options, -1, 178*178);
                options.inJustDecodeBounds = false;

                options.inDither = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_picture,options);
            }
            mImageView.setImageBitmap(bitmap);
            mImageView.setVisibility(VISIBLE);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage: out of memory: ", e);
        }
    }

    private void inflateMmsView() {
        if (mMmsView == null) {
            //inflate the surrounding view_stub
            findViewById(R.id.mms_layout_view_stub).setVisibility(VISIBLE);

            mMmsView = findViewById(R.id.mms_view);
            mImageView = (ImageView) findViewById(R.id.image_view);
            mSlideShowButton = (ImageButton) findViewById(R.id.play_slideshow_button);
            //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
            mVcardsView = (LinearLayout)findViewById(R.id.vcards_view);
            //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
            mOtherFilesView = (LinearLayout)findViewById(R.id.files_view);/*fixed CR<NEWMS00144166> by luning at 2011.11.28*/
        }
    }

    private void inflateDownloadControls() {
        if (mDownloadButton == null) {
            //inflate the download controls
            findViewById(R.id.mms_downloading_view_stub).setVisibility(VISIBLE);

            mDownloadButton = (Button) findViewById(R.id.btn_download_msg);
            mDownloadingLabel = (TextView) findViewById(R.id.label_downloading);
        }
    }

    private LeadingMarginSpan mLeadingMarginSpan;

    private LineHeightSpan mSpan = new LineHeightSpan() {
        public void chooseHeight(CharSequence text, int start,
                int end, int spanstartv, int v, FontMetricsInt fm) {
            fm.ascent -= 10;
        }
    };

    TextAppearanceSpan mTextSmallSpan =
        new TextAppearanceSpan(mContext, android.R.style.TextAppearance_Small);

    ForegroundColorSpan mColorSpan = null;  // set in ctor

    private CharSequence formatMessage(MessageItem msgItem, String contact, String body,
                                       String subject, String timestamp, Pattern highlight,
                                       String contentType) {
        CharSequence template = mContext.getResources().getText(R.string.name_colon);
        //liaobz hide name
        SpannableStringBuilder buf =
            new SpannableStringBuilder(TextUtils.replace(template,
                new String[] { "%s" },
                new CharSequence[] { "" }));

        boolean hasSubject = !TextUtils.isEmpty(subject);
        if (hasSubject) {
            buf.append(mContext.getResources().getString(R.string.inline_subject, subject));
        }

        if (!TextUtils.isEmpty(body)) {
            // Converts html to spannable if ContentType is "text/html".
            if (contentType != null && ContentType.TEXT_HTML.equals(contentType)) {
                buf.append("\n");
                buf.append(Html.fromHtml(body));
            } else {
                if (hasSubject) {
                    buf.append(" - ");
                }
                SmileyParser parser = SmileyParser.getInstance();
                buf.append(parser.addSmileySpans(body));
            }
        }
        // If we're in the process of sending a message (i.e. pending), then we show a "Sending..."
        // string in place of the timestamp.
        if (msgItem.isSending()) {
        	/*delete for CR<NEWMS00132656> by luning at 2011.11.15*/
//            if (msgItem.isMmsRetry()) {
//                timestamp = mContext.getResources().getString(R.string.retry_sending_message);
//            } else {
//                timestamp = mContext.getResources().getString(R.string.sending_message);
//            }
        	timestamp = mContext.getResources().getString(R.string.sending_message);
        }
        // We always show two lines because the optional icon bottoms are aligned with the
        // bottom of the text field, assuming there are two lines for the message and the sent time.
        // liaobz appendAfter -> insertFront
        buf.insert(0, "\n\n");
        int endOffset = timestamp == null ? 0 : timestamp.length();

        buf.insert(0, TextUtils.isEmpty(timestamp) ? " " : timestamp);

        buf.setSpan(mTextSmallSpan, 0, endOffset + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buf.setSpan(mSpan, 0+1, endOffset + 1, 0);

        // Make the timestamp text not as dark
        buf.setSpan(mColorSpan, 0, endOffset + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find()) {
                buf.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), 0);
            }
        }
        buf.setSpan(mLeadingMarginSpan, 0, buf.length(), 0);
        return buf;
    }

    private void drawPlaybackButton(MessageItem msgItem) {
        switch (msgItem.mAttachmentType) {
            case WorkingMessage.SLIDESHOW:
            case WorkingMessage.AUDIO:
            case WorkingMessage.VIDEO:
                // Show the 'Play' button and bind message info on it.
                mSlideShowButton.setTag(msgItem);
                // Set call-back for the 'Play' button.
//                mSlideShowButton.setOnClickListener(this);
                mSlideShowButton.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						MessageItem mi = (MessageItem) v.getTag();
						switch (mi.mAttachmentType) {
						case WorkingMessage.VIDEO:
						case WorkingMessage.AUDIO:
						case WorkingMessage.SLIDESHOW:
							// ======fixed CR<NEWMS00110179> by luning at 11-08-12 begin======
							// MessageUtils.viewMmsMessageAttachment(mContext, mi.mMessageUri,
							// mi.mSlideshow);
							Intent detail = new Intent(mContext, MmsDetailViewActivity.class);
							detail.setData(mi.mMessageUri);
							detail.putExtra("msg_locked", mi.mLocked);
							detail.putExtra("msg_details", mi.details);
							if (null != mi.mSubject) {
								detail.putExtra("msg_subject", mi.mSubject);
							}
							mContext.startActivity(detail);
							// ======fixed CR<NEWMS00110179> by luning at 11-08-12 end======
							break;
						}
					}
				});
                mSlideShowButton.setVisibility(View.VISIBLE);
                setLongClickable(true);

                // When we show the mSlideShowButton, this list item's onItemClickListener doesn't
                // get called. (It gets set in ComposeMessageActivity:
                // mMsgListView.setOnItemClickListener) Here we explicitly set the item's
                // onClickListener. It allows the item to respond to embedded html links and at the
                // same time, allows the slide show play button to work.
                setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        onMessageListItemClick();
                    }
                });
                break;
            case WorkingMessage.VCARD:
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_vcard_layout);
                mImageView.setImageBitmap(bitmap);
                mImageView.setVisibility(VISIBLE);
                mImageView.setTag(msgItem);
                mImageView.setOnClickListener(this);
                mSlideShowButton.setVisibility(View.GONE);
                break;
            default:
                mSlideShowButton.setVisibility(View.GONE);
                break;
        }
    }

    // OnClick Listener for the playback button
	public void onClick(View v) {
		MessageItem mi = (MessageItem) v.getTag();
		switch (mi.mAttachmentType) {
		case WorkingMessage.VIDEO:
		case WorkingMessage.AUDIO:
		case WorkingMessage.SLIDESHOW:
			// ======fixed CR<NEWMS00110179> by luning at 11-08-12 begin======
			// MessageUtils.viewMmsMessageAttachment(mContext, mi.mMessageUri,
			// mi.mSlideshow);
			Intent detail = new Intent(mContext, MmsDetailViewActivity.class);
			detail.setData(mi.mMessageUri);
			detail.putExtra("msg_locked", mi.mLocked);
			detail.putExtra("msg_details", mi.details);
			if (null != mi.mSubject) {
				detail.putExtra("msg_subject", mi.mSubject);
			}
			mContext.startActivity(detail);
			// ======fixed CR<NEWMS00110179> by luning at 11-08-12 end======
			break;
		}
	}

    public void onMessageListItemClick() {
        URLSpan[] spans = mBodyTextView.getUrls();

        if (spans.length == 0) {
            // Do nothing.
        } /*else if (spans.length == 1) {
        	//yeezone:jinwei open if only one uri in content
            Uri uri = Uri.parse(spans[0].getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            mContext.startActivity(intent);
        } */else {
            final java.util.ArrayList<String> urls = MessageUtils.extractUris(spans);

            if (mMessageItem.isWapPush && urls.size() > 1) {//fix bug 9080,at 20120214
                urls.remove(0);
            }

            ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(mContext, android.R.layout.select_dialog_item, urls) {
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    try {
                        String url = getItem(position).toString();
                        TextView tv = (TextView) v;
                        Drawable d = mContext.getPackageManager().getActivityIcon(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        if (d != null) {
                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                            tv.setCompoundDrawablePadding(10);
                            tv.setCompoundDrawables(d, null, null, null);
                        }
                        final String telPrefix = "tel:";
                        if (url.startsWith(telPrefix)) {
                            url = PhoneNumberUtils.formatNumber(url.substring(telPrefix.length()));
                        }
                        tv.setText(url);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                        ;
                    }
                    return v;
                }
            };

            AlertDialog.Builder b = new AlertDialog.Builder(mContext);

            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    if (which >= 0) {
                        Uri uri = Uri.parse(urls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        //add by spreadst_lishengjie start 2012-1-27 : fix bug9249
                        ResolveInfo info = mContext.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
//                        if(info == null){
//                            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
//                            return;
//                        }
                        //add by spreadst_lishengjie end 2012-1-27
                        try{
                            mContext.startActivity(intent);
                        }catch(Exception e){
                            Log.e(TAG, "Start activity happened exception:"+e.toString(),e);
                            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                        }
                    }
                    dialog.dismiss();
                }
            };

            b.setTitle(R.string.select_link_title);
            b.setCancelable(true);
            b.setAdapter(adapter, click);

            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            b.show();
        }
    }


	private void setOnClickListener(final MessageItem msgItem) {
		switch (msgItem.mAttachmentType) {
		case WorkingMessage.IMAGE:
		case WorkingMessage.VIDEO:
			mImageView.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					// ======fixed CR<NEWMS00110179> by luning at 11-08-12 begin======
					// MessageUtils.viewMmsMessageAttachment(mContext, null,
					// msgItem.mSlideshow);
					Intent detail = new Intent(mContext,
							MmsDetailViewActivity.class);
					detail.setData(msgItem.mMessageUri);
					detail.putExtra("msg_locked", msgItem.mLocked);
					detail.putExtra("msg_details", msgItem.details);
					if (null != msgItem.mSubject) {
						detail.putExtra("msg_subject", msgItem.mSubject);
					}
					mContext.startActivity(detail);
					// ======fixed CR<NEWMS00110179> by luning at 11-08-12 end======
				}
			});
			mImageView.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View v) {
					return v.showContextMenu();
				}
			});
			break;

        default:
            mImageView.setOnClickListener(null);
            break;
        }
    }

    private void drawLeftStatusIndicator(int msgBoxId, long status) {
        // liaobz change mms background
        switch (msgBoxId) {
            case Mms.MESSAGE_BOX_INBOX:
            	this.setBackgroundResource(R.drawable.bg_inbox_normal);
//                mMsgListItem.setBackgroundResource(R.drawable.listitem_background_lightblue);
                break;

            case Mms.MESSAGE_BOX_DRAFTS:
            case Sms.MESSAGE_TYPE_FAILED:
            case Sms.MESSAGE_TYPE_QUEUED:
            case Mms.MESSAGE_BOX_OUTBOX:
                this.setBackgroundResource(R.drawable.bg_outbox_normal);
//                mMsgListItem.setBackgroundResource(R.drawable.listitem_background);
                break;

            default:
                if(status == SmsManager.STATUS_ON_ICC_READ || status == SmsManager.STATUS_ON_ICC_UNREAD){
                    this.setBackgroundResource(R.drawable.bg_inbox_normal);
                }else{
                    this.setBackgroundResource(R.drawable.bg_outbox_normal);
//                mMsgListItem.setBackgroundResource(R.drawable.listitem_background);
                }
                break;
        }
    }

    private void setErrorIndicatorClickListener(final MessageItem msgItem) {
        String type = msgItem.mType;
        final int what;
        if (type.equals("sms")) {
            what = MSG_LIST_EDIT_SMS;
        } else {
            what = MSG_LIST_EDIT_MMS;
        }
        mDeliveredIndicator.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (null != mHandler) {
                    Message msg = Message.obtain(mHandler, what);
                    msg.obj = new Long(msgItem.mMsgId);
                    msg.sendToTarget();
                }
            }
        });
    }

    private void drawRightStatusIndicator(MessageItem msgItem) {
        // Locked icon
        if (msgItem.mLocked) {
            mLockedIndicator.setImageResource(R.drawable.ic_lock_message_sms);
            mLockedIndicator.setVisibility(View.VISIBLE);
        } else {
            mLockedIndicator.setVisibility(View.GONE);
        }

        // Delivery icon
        if (msgItem.isOutgoingMessage() && msgItem.isFailedMessage()) {
            mDeliveredIndicator.setImageResource(R.drawable.ic_list_alert_sms_failed);
            setErrorIndicatorClickListener(msgItem);
            mDeliveredIndicator.setVisibility(View.VISIBLE);
        } else if (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED) {
            mDeliveredIndicator.setImageResource(R.drawable.ic_list_alert_sms_failed);
            mDeliveredIndicator.setVisibility(View.VISIBLE);
        } else if (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED) {
            mDeliveredIndicator.setImageResource(R.drawable.ic_sms_mms_delivered);
            mDeliveredIndicator.setVisibility(View.VISIBLE);
        } else {
            mDeliveredIndicator.setVisibility(View.GONE);
        }

        // Message details icon
        if (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.INFO || msgItem.mReadReport) {
            mDetailsIndicator.setImageResource(R.drawable.ic_sms_mms_details);
            mDetailsIndicator.setVisibility(View.VISIBLE);
        } else {
            mDetailsIndicator.setVisibility(View.GONE);
        }

	if (TelephonyManager.getPhoneCount()>1) {
	    mSimIndicator.setVisibility(View.VISIBLE);
	    if (msgItem.mPhoneId==0) {
		mSimIndicator.setImageResource(R.drawable.sim_indicator1);
	    } else if (msgItem.mPhoneId==1) {
		mSimIndicator.setImageResource(R.drawable.sim_indicator2);
	    } else {
		mSimIndicator.setVisibility(View.GONE);
	    }
	}

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
        inflateMmsView();

        try {
            Bitmap bitmap = VideoAttachmentView.createVideoThumbnail(mContext, video);
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_video);
            }
            mImageView.setImageBitmap(bitmap);
            mImageView.setVisibility(VISIBLE);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setVideo: out of memory: ", e);
        }
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
        if (mImageView != null) {
            mImageView.setVisibility(GONE);
        }
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
        if(mVcardsView != null){
        	mVcardsView.removeAllViews();
        	mVcardsView.setVisibility(GONE);
        }
        //===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====
        if(mOtherFilesView != null){/*fixed CR<NEWMS00144166> by luning at 2011.11.28*/
            mOtherFilesView.removeAllViews();
            mOtherFilesView.setVisibility(GONE);
        }
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
    public void setVcard(Uri audio, String name)
    {
	    // TODO Auto-generated method stub
    }

	public void setImage(Uri uri, Bitmap bitmap, boolean isGifImage) {
		// TODO Auto-generated method stub

	}

	public void setSize(String size) {
		// TODO Auto-generated method stub

	}

	/* fixed CR<NEWMS119944 NEWMS119757 NEWMS119755 NEWMS120030 NEWMS119256> by lino release memory end */
	public void destroy(){

	    if(mImageView != null){
	    	BitmapDrawable bd = (BitmapDrawable)mImageView.getDrawable();
	    	Bitmap bt = null;
			if (bd != null) {
				bt = bd.getBitmap();
				if (bt != null && !bt.isRecycled()) {
					bt.recycle();
					bt = null;
				}
			}
			bd = null;
	    	mImageView.setImageDrawable(null);
	    	mImageView.setImageBitmap(null);
	    }
	    if(mLockedIndicator != null){
	    	mLockedIndicator.setImageDrawable(null);
	    	mLockedIndicator.setImageBitmap(null);
	    }
	    if(mDeliveredIndicator != null){
	    	mDeliveredIndicator.setImageDrawable(null);
	    	mDeliveredIndicator.setImageBitmap(null);
	    }
	    if(mDetailsIndicator != null){
	    	mDetailsIndicator.setImageDrawable(null);
	    	mDetailsIndicator.setImageBitmap(null);
	    }
	    if(mWarningDownloadIndicator != null){
	    	mWarningDownloadIndicator.setImageDrawable(null);
	    	mWarningDownloadIndicator.setImageBitmap(null);
	    }
	    if(mSlideShowButton != null){
	    	mSlideShowButton.setImageDrawable(null);
	    	mSlideShowButton.setImageBitmap(null);
	    }
	    if(mBodyTextView != null){
	    	mBodyTextView.setText(null);
	    }
	    if(mDownloadButton != null){
	    	mDownloadButton.setOnClickListener(null);
	    }
	    if(mDownloadingLabel != null){
	    }
	    if(mAvatar != null){
	    	mAvatar.setImageBitmap(null);
	    }
	}

    // ===== fixed CR<NEWMS00133151> by luning at 11-10-26 begin =====
    private LinearLayout mVcardsView;

    public void setVcard(ArrayList vCards) {
        inflateMmsView();
        if (null != mVcardsView) {
            final Context context = mContext;
            LinearLayout linearLayout = null;
            ImageView vcardIcon = null;
            TextView vcardName = null;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.rightMargin = 5;
            for (int i = 0; i < vCards.size(); i++) {
                final VcardModel vcard = (VcardModel) vCards.get(i);
                vcardIcon = new ImageView(context);
                vcardName = new TextView(context);
                linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setGravity(Gravity.CENTER);
                linearLayout.addView(vcardIcon, params);
                linearLayout.addView(vcardName, params);
                vcardIcon.setImageResource(R.drawable.vcf);
                vcardName.setText(vcard.getSrc());

                // final DialogInterface.OnClickListener listener = new
                // DialogInterface.OnClickListener() {
                // public void onClick(DialogInterface dialog,
                // int which) {
                // if (which == 0) {/* view */
                // new AlertDialog.Builder(context)
                // .setIcon(R.drawable.vcf)
                // .setTitle(vcard.getSrc())
                // .setMessage(vcard.getDetail())
                // .setPositiveButton(
                // context
                // .getString(R.string.yes),
                // null).show();
                // } else {/* import contact */
                // Intent intent = new Intent(
                // Intent.ACTION_VIEW);
                // intent.setData(vcard.getUri());
                // intent
                // .setClassName(
                // "com.android.contacts",
                // "com.android.contacts.ImportVCardActivity");
                // context.startActivity(intent);
                // }
                // }
                // };
                //
                // linearLayout.setOnClickListener(new OnClickListener() {
                // public void onClick(View v) {
                // new AlertDialog.Builder(context)
                // .setIcon(R.drawable.vcf)
                // .setTitle(vcard.getSrc())
                // .setItems(
                // new String[] {
                // context
                // .getString(R.string.view),
                // context
                // .getString(
                // R.string.menu_add_address_to_contacts,
                // vcard.getSrc()) },
                // listener).setPositiveButton(
                // context.getString(R.string.yes), null)
                // .show();
                // }
                // });

                linearLayout.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        new AlertDialog.Builder(context).setMessage(
                                context.getString(R.string.menu_add_address_to_contacts, vcard
                                        .getSrc())).setIcon(R.drawable.vcf).setTitle(
                                R.string.menu_add_to_contacts).setPositiveButton(
                                context.getString(R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setData(vcard.getUri());
                                        intent.setClassName("com.android.contacts",
                                                "com.android.contacts.ImportVCardActivity");
                                        context.startActivity(intent);
                                    }
                                }).setNegativeButton(context.getString(R.string.no), null).show();
                    }
                });
                mVcardsView.addView(linearLayout, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            mVcardsView.setVisibility(VISIBLE);
        }
    }
    // ===== fixed CR<NEWMS00133151> by luning at 11-10-26 end =====

    /*fixed CR<NEWMS00144166> by luning at 2011.11.28 begin*/
    private LinearLayout mOtherFilesView;

    public void setFile(ArrayList files) {
        inflateMmsView();
        if (null != mOtherFilesView) {
            final Context context = mContext;
            LinearLayout linearLayout = null;
            ImageView fileIcon = null;
            TextView fileName = null;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.rightMargin = 5;
            for (int i = 0; i < files.size(); i++) {
                final FileModel file = (FileModel) files.get(i);
                final int IconId = MessageUtils.getFileIconId(file.getSrc());
                fileIcon = new ImageView(context);
                fileName = new TextView(context);
                linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setGravity(Gravity.CENTER);
                linearLayout.addView(fileIcon, params);
                linearLayout.addView(fileName, params);
                fileIcon.setImageResource(IconId);
                fileName.setText(file.getSrc());
                linearLayout.setOnClickListener(new OnClickListener() {

                    public void onClick(View v) {
                        new AlertDialog.Builder(context).setItems(new String[] {
                            context.getString(R.string.copy_to_sdcard)
                        }, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (MessageUtils.showSaveErroDialog(context, file.getSrc(), file
                                        .getMediaSize())) {
	                                //fix for bug 14796 ---start----
	                                String dir = Environment.getExternalStorageDirectory() + "/"
	                                             + Environment.DIRECTORY_DOWNLOADS + "/";
	                                if (!MessageUtils.saveFile(context, file,
                                           Environment.DIRECTORY_DOWNLOADS + "/")) {
                                    //fix for bug 14796 ---end----
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.save_file_error_msg),
                                                 Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.copy_to_sdcard_success,dir), Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        }).setIcon(IconId).setTitle(file.getSrc()).setNegativeButton(R.string.no,
                                null).show();
                    }
                });
                mOtherFilesView.addView(linearLayout, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            mOtherFilesView.setVisibility(VISIBLE);
        }
    }
    public TextView getBodyTextView(){
        return mBodyTextView;
    }

    @Override
    public void setFile(Uri uri, String name) {
        // TODO Auto-generated method stub
        
    }
}
