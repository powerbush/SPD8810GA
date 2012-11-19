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

import java.util.List;

import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Intents;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.QuickContactBadge;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * This class manages the view for given conversation.
 */
public class MultiSelectSmsDeleteListItem extends RelativeLayout 
implements Contact.UpdateListener ,View.OnClickListener {
    private static final String TAG = "MultiSelectSmsDeleteListItem";
    private static final boolean DEBUG = true;

    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDraft;
    private TextView mDateView;
    private View mAttachmentView;
    private View mErrorIndicator;
    private ImageView mPresenceView;
    private QuickContactBadge mAvatarView;
    private CheckBox checkbox;
    
    static private Drawable sDefaultContactImage;

    // For posting UI update Runnables from other threads:
    private Handler mHandler = new Handler();

    private ConversationListItemData mConversationHeader;

    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    public MultiSelectSmsDeleteListItem(Context context) {
        super(context);
    }

    public MultiSelectSmsDeleteListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFromView = (TextView) findViewById(R.id.from);
        mSubjectView = (TextView) findViewById(R.id.subject);

        mDateView = (TextView) findViewById(R.id.date);
        mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = findViewById(R.id.error);
        mPresenceView = (ImageView) findViewById(R.id.presence);
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);
        checkbox = (CheckBox) findViewById(R.id.Conversation_checkbox_selected);
        checkbox.setOnClickListener(this);
        
        //mDraft
        mDraft = (TextView) findViewById(R.id.draft);
    }

    public void setPresenceIcon(int iconId) {
        if (iconId == 0) {
            mPresenceView.setVisibility(View.GONE);
        } else {
            mPresenceView.setImageResource(iconId);
            mPresenceView.setVisibility(View.VISIBLE);
        }
    }

    public ConversationListItemData getConversationHeader() {
        return mConversationHeader;
    }

    private void setConversationHeader(ConversationListItemData header) {
        mConversationHeader = header;
    }

    /**
     * Only used for header binding.
     */
    public void bind(String title, String explain) {
       mFromView.setText(title);
       mSubjectView.setText(explain);
    }
    
    public void bind(String checkboxtitle) {
        checkbox.setText(checkboxtitle);
     }

 
    private CharSequence formatMessage(ConversationListItemData ch) {
        String from = ch.getFrom();
        SpannableStringBuilder buf = new SpannableStringBuilder(from);
        if (ch.getMessageCount() > 1) {
            buf.append(" (" + ch.getMessageCount() + ") ");
        }
        // Unread messages are shown in bold
        if (!ch.isRead()) {
            buf.setSpan(STYLE_BOLD, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }
    
    private void setDraft(ConversationListItemData ch) {
        final int size = android.R.style.TextAppearance_Small;
        final int color = android.R.styleable.Theme_textColorSecondary;
        if (ch.hasDraft()) {
            SpannableStringBuilder buf = new SpannableStringBuilder(mContext.getResources().getString(R.string.has_draft));
            buf.setSpan(new TextAppearanceSpan(mContext, size, color), 0,
            		buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            buf.setSpan(new ForegroundColorSpan(
                    mContext.getResources().getColor(R.drawable.text_color_red)),
                    0, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            mDraft.setText(buf);
            mDraft.setVisibility(View.VISIBLE);
        }else{
        	mDraft.setVisibility(View.GONE);
        }
    }

    private void updateAvatarView() {
        ConversationListItemData ch = mConversationHeader;

        Drawable avatarDrawable;
        if (ch.getContacts().size() == 1) {
            Contact contact = ch.getContacts().get(0);
            avatarDrawable = contact.getAvatar(mContext, sDefaultContactImage);

            if (contact.existsInDatabase()) {
                mAvatarView.assignContactUri(contact.getUri());
            } else {
                mAvatarView.assignContactFromPhone(contact.getNumber(), true);
            }
        } else {
            // TODO get a multiple recipients asset (or do something else)
            avatarDrawable = sDefaultContactImage;
            mAvatarView.assignContactUri(null);
        }
        mAvatarView.setImageDrawable(avatarDrawable);
        mAvatarView.setVisibility(View.VISIBLE);
    }

    private void updateFromView() {
        ConversationListItemData ch = mConversationHeader;
        ch.updateRecipients();
        mFromView.setText(formatMessage(ch));
        // Draft
        setDraft(ch);
        setPresenceIcon(ch.getContacts().getPresenceResId());
        updateAvatarView();
    }

    public void onUpdate(Contact updated) {
        mHandler.post(new Runnable() {
            public void run() {
                updateFromView();
            }
        });
    }

    
    public final void bind(Context context, final ConversationListItemData ch) {
        if (DEBUG) Log.v(TAG, "bind()");

        setConversationHeader(ch);

        Drawable background = ch.isRead()?
                mContext.getResources().getDrawable(R.drawable.conversation_item_background_read) :
                mContext.getResources().getDrawable(R.drawable.conversation_item_background_unread);

        setBackgroundDrawable(background);

        LayoutParams attachmentLayout = (LayoutParams)mAttachmentView.getLayoutParams();
        boolean hasError = ch.hasError();
        // When there's an error icon, the attachment icon is left of the error icon.
        // When there is not an error icon, the attachment icon is left of the date text.
        // As far as I know, there's no way to specify that relationship in xml.
        if (hasError) {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.error);
        } else {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.date);
        }

        boolean hasAttachment = ch.hasAttachment();
        mAttachmentView.setVisibility(hasAttachment ? VISIBLE : GONE);

        // Date
        mDateView.setText(ch.getDate());

        // Draft
        setDraft(ch);
        // From.
        mFromView.setText(formatMessage(ch));

        // Register for updates in changes of any of the contacts in this conversation.
        ContactList contacts = ch.getContacts();

        if (DEBUG) Log.v(TAG, "bind: contacts.addListeners " + this);
        Contact.addListener(this);
        setPresenceIcon(contacts.getPresenceResId());

        // Subject
        mSubjectView.setText(ch.getSubject());
        LayoutParams subjectLayout = (LayoutParams)mSubjectView.getLayoutParams();
        // We have to make the subject left of whatever optional items are shown on the right.
        subjectLayout.addRule(RelativeLayout.LEFT_OF, hasAttachment ? R.id.attachment :
            (hasError ? R.id.error : R.id.date));

        // Transmission error indicator.
        mErrorIndicator.setVisibility(hasError ? VISIBLE : GONE);

        long threadId= ch.getThreadId();
        checkbox.setFocusableInTouchMode(false);   
        checkbox.setFocusable(false);  
        if (MultiSelectSmsDeleteActivity.SelectState.contains(threadId)) {
            checkbox.setChecked(true);
        } else {
            checkbox.setChecked(false);
        }
        checkbox.setTag(Long.toString(threadId));
        
        updateAvatarView();
    }

    public final void unbind() {
        if (DEBUG) Log.v(TAG, "unbind: contacts.removeListeners " + this);
        // Unregister contact update callbacks.
        Contact.removeListener(this);
    }
    
    public void CheckBoxSetChecked(boolean checked) {
        checkbox.setChecked(checked);
    }
    
    public void onClick(View view) {
        CheckBox checkView = (CheckBox) view ;
        String  thread_id= (String) view.getTag();
      
        Log.w(TAG, "onClick"+thread_id);
        
        long p= Long.parseLong(thread_id);
        
        if(checkView.isChecked())
        {       
            if(!MultiSelectSmsDeleteActivity.SelectState.contains(p))
                MultiSelectSmsDeleteActivity.SelectState.add(p);
        }
        else
        {
           if (MultiSelectSmsDeleteActivity.SelectState.contains(p))
               MultiSelectSmsDeleteActivity.SelectState.remove((Object) p);
        
        }
        
        MultiSelectSmsDeleteActivity.updateCheckboxButtoonViewStatus();

    }
    
    

}
