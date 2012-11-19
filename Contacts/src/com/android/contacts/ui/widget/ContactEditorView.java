/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.ui.widget;

import com.android.contacts.ContactsGroupActivity;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Custom view that provides all the editor interaction for a specific
 * {@link Contacts} represented through an {@link EntityDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(EntityDelta, ContactsSource)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link Entity} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link EntityModifier} to ensure that {@link ContactsSource} are enforced.
 */
public class ContactEditorView extends BaseContactEditorView implements OnClickListener {
	static final Uri DIVIDED_GROUP_URI=Uri.parse("content://"+ContactsContract.AUTHORITY+"/divided_group");

    private static final String TAG = "ContactEditorView";
    private TextView mReadOnly;
    private TextView mReadOnlyName;

    private View mPhotoStub;
    private GenericEditorView mName;

    private boolean mIsSourceReadOnly;
    private ViewGroup mGeneral;
    private ViewGroup mSecondary;
    private boolean mSecondaryVisible;

    private TextView mSecondaryHeader;

    private Drawable mSecondaryOpen;
    private Drawable mSecondaryClosed;

    private View mHeaderColorBar;
    private View mSideBar;
    private ImageView mHeaderIcon;
    private TextView mHeaderAccountType;
    private TextView mHeaderAccountName;

	private Button mContactGroupButton;
    private long mRawContactId = -1;

    private static final int GROUP_NAME_STUDENT = 0;
    private static final int GROUP_NAME_FRIEND = 1;
    private static final int GROUP_NAME_FAMILY = 2;
    private static final int GROUP_NAME_COLLEAGUE = 3;

    public ContactEditorView(Context context) {
        super(context);
    }

    public ContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
      //add by 钱剑波 2011-8-19 for 114329 begin
        mContactGroupButton = (Button) this.findViewById(R.id.contact_group_button);
        mContactGroupButton.setOnClickListener(this);
//add by 钱剑波 2011-8-19 for 114329 end
        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhotoStub = findViewById(R.id.stub_photo);

        final int photoSize = getResources().getDimensionPixelSize(R.dimen.edit_photo_size);

        mReadOnly = (TextView)findViewById(R.id.edit_read_only);

        mName = (GenericEditorView)findViewById(R.id.edit_name);
        mName.setMinimumHeight(photoSize);
        mName.setDeletable(false);

        mReadOnlyName = (TextView) findViewById(R.id.read_only_name);

        mGeneral = (ViewGroup)findViewById(R.id.sect_general);
        mSecondary = (ViewGroup)findViewById(R.id.sect_secondary);

        mHeaderColorBar = findViewById(R.id.header_color_bar);
        mSideBar = findViewById(R.id.color_bar);
        mHeaderIcon = (ImageView) findViewById(R.id.header_icon);
        mHeaderAccountType = (TextView) findViewById(R.id.header_account_type);
        mHeaderAccountName = (TextView) findViewById(R.id.header_account_name);

        mSecondaryHeader = (TextView)findViewById(R.id.head_secondary);
        mSecondaryHeader.setOnClickListener(this);

        final Resources res = getResources();
        mSecondaryOpen = res.getDrawable(com.android.internal.R.drawable.expander_ic_maximized);
        mSecondaryClosed = res.getDrawable(com.android.internal.R.drawable.expander_ic_minimized);

        this.setSecondaryVisible(false);
    }

    // modify by dory.zheng for NEWMS00124705 at 22-09 begin
    private String getOrgancationName(long id){
    	Uri uri = Uri.parse("content://"+"com.android.contacts"+"/divided_group");
//    	select divided_name from divided_group where _id
//    	=( select divided_group_name_id from  data join mimetypes on (data.mimetype_id = mimetypes._id)
//    	join raw_contacts on (data.raw_contact_id = raw_contacts._id) and raw_contact_id=399 );
    	String where =" _id =( select divided_group_name_id from  data " +
    			"join mimetypes on (data.mimetype_id = mimetypes._id) " +
                "join raw_contacts on (data.raw_contact_id = raw_contacts._id) and raw_contact_id=?)";
    	Cursor cursor = this.getContext().getContentResolver().query(uri, new String[]{"divided_name"},where, new String[]{String.valueOf(id)}, null);
    	String name = null;
    	while(cursor.moveToNext()){
    		name = cursor.getString(cursor.getColumnIndex("divided_name"));
    	}
    	if(cursor != null) cursor.close();
    	return name;
    }
    // modify by dory.zheng for NEWMS00124705 at 22-09 end

    /** {@inheritDoc} */
    public void onClick(View v) {
    	//add by 钱剑波 2011-8-19 for 114329 begin
        // Toggle visibility of secondary kinds
		if(v.getId() == R.id.contact_group_button){
    		Log.v(TAG,"onClick--1");
    		showContactGroupNameDialog();
            return;
    	}
    	//add by 钱剑波 2011-8-19 for 114329 end
        final boolean makeVisible = mSecondary.getVisibility() != View.VISIBLE;
        this.setSecondaryVisible(makeVisible);

        // get ScrollView from parent view, and scroll to bottom.
        ViewParent vp = this.getParent();
        if (null != vp) {
            final ScrollView vpScrollView = (ScrollView) vp.getParent();
            if (null != vpScrollView) {
                vpScrollView.post(new Runnable() {
                    public void run() {
                        vpScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        }
    }

	public void showContactGroupNameDialog(){
    	final Context dialogContext = new ContextThemeWrapper(this.getContext(), android.R.style.Theme_Light);
    	final Resources res = dialogContext.getResources();
    	final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    	final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getContext(), android.R.layout.simple_list_item_1){
    		 @Override
             public View getView(int position, View convertView, ViewGroup parent) {
                 if (convertView == null) {
                     convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                             parent, false);
                 }
                 //this.getItem(position).
                 //final int resId = (Integer)this.getItem(position);
                 ((TextView)convertView).setText((String)this.getItem(position));
                 return convertView;
             }
    	};
        Cursor cursor = this.getContext().getContentResolver().query(DIVIDED_GROUP_URI,
        		null, null, null, null);
    	String tmp;
        int groupId;
    	/*if(!cursor.moveToNext()){
    		new AlertDialog.Builder(ContactsGroupActivity.this).setTitle(R.string.contacts_group)
			.setMessage(R.string.contacts_group_name_exist).setPositiveButton(R.string.sure,null).show();
    	}*/
    	boolean bl = cursor.moveToNext();
    	Log.v(TAG,"bl:"+bl);
        if(bl){
            while(bl){
                tmp = cursor.getString(cursor.getColumnIndexOrThrow("divided_name"));
                groupId = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
                if(groupId < 4 && groupId > -1){
                    tmp =  getDeafaultGroupName(groupId, tmp);
                }
                adapter.add(tmp);
                bl = cursor.moveToNext();
    		}
    	}else{
    		new AlertDialog.Builder(this.getContext()).setTitle(R.string.contacts_group)
			.setMessage(R.string.no_contacts_group_name).setPositiveButton(R.string.sure,null).show();
    		cursor.close();
    		return;
    	}
        cursor.close();
    	final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog.dismiss();
				String name = adapter.getItem(which);
				//add by 钱剑波 2011-8-19 for 114329 begin
				mContactGroupButton.setText(name);
				//add by 钱剑波 2011-8-19 for 114329 end
			}
		};

		new AlertDialog.Builder(this.getContext())
        .setTitle(R.string.select_contacts_group)
        .setNegativeButton(android.R.string.cancel, null)
        .setSingleChoiceItems(adapter, -1, clickListener)
        .show();

    }

    private String getDeafaultGroupName(int groupNameId, String tmp){
        String groupName = "";
        ContentValues values = new ContentValues();
        switch(groupNameId){
            case GROUP_NAME_STUDENT:
                groupName = mContext.getString(R.string.group_name_student);
                break;
            case GROUP_NAME_FRIEND:
                groupName = mContext.getString(R.string.group_name_friend);
                break;
            case GROUP_NAME_FAMILY:
                groupName = mContext.getString(R.string.group_name_family);
                break;
            case GROUP_NAME_COLLEAGUE:
                groupName = mContext.getString(R.string.group_name_colleague);

                break;
            default:
                break;
        }
        values.put("divided_name", groupName);
        if(!tmp.equals(groupName)) {
            mContext.getContentResolver().update(DIVIDED_GROUP_URI, values, "_id=" + groupNameId, null);
            return groupName;
        } else {
            return tmp;
        }
    }

    /**
     * Set the visibility of secondary sections, along with header icon.
     *
     * <p>If the source is read-only and there's no secondary fields, the entire secondary section
     * will be hidden.
     */
    private void setSecondaryVisible(boolean makeVisible) {
        mSecondaryVisible = makeVisible;

        if (!mIsSourceReadOnly && mSecondary.getChildCount() > 0) {
            mSecondaryHeader.setVisibility(View.VISIBLE);
            mSecondaryHeader.setCompoundDrawablesWithIntrinsicBounds(
                    makeVisible ? mSecondaryOpen : mSecondaryClosed, null, null, null);
            mSecondary.setVisibility(makeVisible ? View.VISIBLE : View.GONE);
        } else {
            mSecondaryHeader.setVisibility(View.GONE);
            mSecondary.setVisibility(View.GONE);
        }
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    @Override
    public void setState(EntityDelta state, ContactsSource source, ViewIdGenerator vig) {
        // Remove any existing sections
        mGeneral.removeAllViews();
        mSecondary.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        setId(vig.getId(state, null, null, ViewIdGenerator.NO_VIEW_INDEX));

        mIsSourceReadOnly = source.readOnly;

        // Make sure we have StructuredName
        EntityModifier.ensureKindExists(state, source, StructuredName.CONTENT_ITEM_TYPE);

        // Fill in the header info
        ValuesDelta values = state.getValues();
        String accountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        CharSequence accountType = source.getDisplayLabel(mContext);
        if (TextUtils.isEmpty(accountType)) {
            accountType = mContext.getString(R.string.account_phone);
        }
        if (!TextUtils.isEmpty(accountName)) {
            mHeaderAccountName.setText(
                    mContext.getString(R.string.from_account_format, accountName));
        }
        mHeaderAccountType.setText(mContext.getString(R.string.account_type_format, accountType));
        mHeaderIcon.setImageDrawable(source.getDisplayIcon(mContext));

        mRawContactId = values.getAsLong(RawContacts._ID);
        // modify by dory.zheng for NEWMS00124705 at 22-09 begin
        String name = getOrgancationName(mRawContactId);
        if (name != null){
        	mContactGroupButton.setText(name);
        }
        // modify by dory.zheng for NEWMS00124705 at 22-09 end
        // Show photo editor when supported
        EntityModifier.ensureKindExists(state, source, Photo.CONTENT_ITEM_TYPE);
        mHasPhotoEditor = (source.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null);
        mPhoto.setVisibility(mHasPhotoEditor ? View.VISIBLE : View.GONE);
        mPhoto.setEnabled(!mIsSourceReadOnly);
        mName.setEnabled(!mIsSourceReadOnly);

        // Show and hide the appropriate views
        if (mIsSourceReadOnly) {
            mGeneral.setVisibility(View.GONE);
            mName.setVisibility(View.GONE);
            mReadOnly.setVisibility(View.VISIBLE);
            mReadOnly.setText(mContext.getString(R.string.contact_read_only, accountType));
            mReadOnlyName.setVisibility(View.VISIBLE);
        } else {
            mGeneral.setVisibility(View.VISIBLE);
            mName.setVisibility(View.VISIBLE);
            mReadOnly.setVisibility(View.GONE);
            mReadOnlyName.setVisibility(View.GONE);
        }

        boolean anySecondaryFieldFilled = false;
        // Create editor sections for each possible data kind
        for (DataKind kind : source.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                if (!mIsSourceReadOnly) {
                    mName.setValues(kind, primary, state, mIsSourceReadOnly, vig);
                } else {
                    String displayName = primary.getAsString(StructuredName.DISPLAY_NAME);
                    mReadOnlyName.setText(displayName);
                }
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mPhoto.setValues(kind, primary, state, mIsSourceReadOnly, vig);
                if (mIsSourceReadOnly && !mPhoto.hasSetPhoto()) {
                    mPhotoStub.setVisibility(View.GONE);
                } else {
                    mPhotoStub.setVisibility(View.VISIBLE);
                }
            } else if (!mIsSourceReadOnly) {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final ViewGroup parent = kind.secondary ? mSecondary : mGeneral;
                final KindSectionView section = (KindSectionView)mInflater.inflate(
                        R.layout.item_kind_section, parent, false);
                section.setState(kind, state, mIsSourceReadOnly, vig);
                if (kind.secondary && section.isAnyEditorFilledOut()) {
                    anySecondaryFieldFilled = true;
                }
                parent.addView(section);
            }
        }

        setSecondaryVisible(anySecondaryFieldFilled);
    }

    /**
     * Sets the {@link EditorListener} on the name field
     */
    @Override
    public void setNameEditorListener(EditorListener listener) {
        mName.setEditorListener(listener);
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    private static class SavedState extends BaseSavedState {
        public boolean mSecondaryVisible;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mSecondaryVisible = (in.readInt() == 0 ? false : true);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mSecondaryVisible ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * Saves the visibility of the secondary field.
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);

        ss.mSecondaryVisible = mSecondaryVisible;
        return ss;
    }

    /**
     * Restores the visibility of the secondary field.
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        setSecondaryVisible(ss.mSecondaryVisible);
    }
}
