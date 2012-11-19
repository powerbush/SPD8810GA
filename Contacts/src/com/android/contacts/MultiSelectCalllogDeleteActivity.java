/**
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.util.Config;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

/***
 * Displays a list of call log entries.
 */
public class MultiSelectCalllogDeleteActivity extends ListActivity
        implements View.OnCreateContextMenuListener ,View.OnClickListener{
    private static final String TAG = "MultiSelectCalllogDeleteActivity";
    private static final boolean DBG = true;

    public static final String SIM_INDEX = RecentCallsListActivity.SIM_INDEX;

    /*** The projection to use when querying the call log table */
    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL,
            Calls.PHONE_ID
    };

    static final int ID_COLUMN_INDEX = 0;
    static final int NUMBER_COLUMN_INDEX = 1;
    static final int DATE_COLUMN_INDEX = 2;
    static final int DURATION_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    static final int CALLER_NAME_COLUMN_INDEX = 5;
    static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;

    static final int SIM_COLUMN_INDEX = 8;

    /*** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            ContactsContract.RawContacts.ACCOUNT_NAME
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;
    static final int ACCOUNT_NAME_COLUMN_INDEX = 5;

    private static final int MENU_ITEM_SELECT_ALL = 1;
    private static final int MENU_ITEM_UNSELECT_ALL = 2;


    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;

    private static final int DIALOG_CONFIRM_DELETE_ALL = 1;

	static private Cursor CurrentCursor= null;
    private static  String CallTypeSelect = null;
    
    static private ArrayList<Long> SelectState;
    
    static private Button DeleteButton = null;
	static private Button CancelButton = null;
    static private CheckBox  SelectAll ;
    
    
    RecentCallsAdapter mAdapter;
    private QueryHandler mQueryHandler;
    String mVoiceMailNumber;

    static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;

        public static ContactInfo EMPTY = new ContactInfo();
    }

    public static final class RecentCallsListItemViews {
        TextView line1View;
        TextView labelView;
        TextView numberView;
        TextView dateView;
        ImageView iconView;
        ImageView simView;
        ImageView groupIndicator;
        TextView groupSize;
        CheckBox checkView;
    }



    static final class CallerInfoQuery {
        String number;
        int position;
        String name;
        int numberType;
        String numberLabel;
    }

    /***
     * Shared builder used by {@link #formatPhoneNumber(String)} to minimize
     * allocations when formatting phone numbers.
     */
    private static final SpannableStringBuilder sEditable = new SpannableStringBuilder();

    /***
     * Invalid formatting type constant for {@link #sFormattingType}.
     */
    private static final int FORMATTING_TYPE_INVALID = -1;

    /***
     * Cached formatting type for current {@link Locale}, as provided by
     * {@link PhoneNumberUtils#getFormatTypeForLocale(Locale)}.
     */
    private static int sFormattingType = FORMATTING_TYPE_INVALID;

    final class  CheckboxTag
    {
        int position; 
        Long id;
    }

    
    /*** Adapter class to fill in data for the Call Log */
    final class RecentCallsAdapter extends GroupingListAdapter
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener {
        HashMap<String,ContactInfo> mContactInfo;
        private final LinkedList<CallerInfoQuery> mRequests;
        private volatile boolean mDone;
        private boolean mLoading = true;
        ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        private CharSequence[] mLabelArray;

        private Drawable mDrawableIncoming;
        private Drawable mDrawableOutgoing;
        private Drawable mDrawableMissed;
        private LayoutInflater mInflater;

        /***
         * Reusable char array buffers.
         */
        private CharArrayBuffer mBuffer1 = new CharArrayBuffer(128);
        private CharArrayBuffer mBuffer2 = new CharArrayBuffer(128);
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View viewitem = super.getView(position,convertView,parent);
            final RecentCallsListItemViews views = (RecentCallsListItemViews) viewitem.getTag();
			if (views != null && views.checkView.getTag() != null){
                CheckboxTag tag = (CheckboxTag)views.checkView.getTag() ;
                tag.position = position;
            }
            return viewitem ;
        }
        
        public void onClick(View view) {
           CheckBox checkView = (CheckBox) view ;
           
            CheckboxTag tag = (CheckboxTag) checkView.getTag() ;

            Cursor cursor = (Cursor)mAdapter.getItem(tag.position);
            int groupSize = 1;
            if (mAdapter.isGroupHeader(tag.position)) {
                groupSize = mAdapter.getGroupSize(tag.position);
                for (int i = 0; i < groupSize; i++) {
                    Long  id = cursor.getLong(ID_COLUMN_INDEX);
                    if(checkView.isChecked()){       
                        if(!SelectState.contains(id))
                            SelectState.add(id);
                    }else{
                        if (SelectState.contains(id))
                	        SelectState.remove((Object) id);
                    }
                    cursor.moveToNext();
                }
                mAdapter.notifyDataSetChanged();
            }else{
                if(checkView.isChecked()){       
                    if(!SelectState.contains(tag.id))
                        SelectState.add(tag.id);
                }else{
                   if (SelectState.contains(tag.id))
                	  SelectState.remove((Object) tag.id);
                }
            }
            updateCheckboxButtoonViewStatus();
        }

        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD, 1000);
                mFirst = false;
            }
            return true;
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REDRAW:
                        notifyDataSetChanged();
                        break;
                    case START_THREAD:
                        startRequestProcessing();
                        break;
                }
            }
        };

        public RecentCallsAdapter() {
            super(MultiSelectCalllogDeleteActivity.this);

            mContactInfo = new HashMap<String,ContactInfo>();
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;

            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            
            SelectState = new ArrayList<Long>();

            mLabelArray = getResources().getTextArray(com.android.internal.R.array.phoneTypes);
			mInflater = (LayoutInflater) MultiSelectCalllogDeleteActivity.this
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        /***
         * Requery on background thread when {@link Cursor} changes.
         */
        @Override
        protected void onContentChanged() {
            // Start async requery
//            startQuery(CallTypeSelect);
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfo.get(number);
        }

        public void startRequestProcessing() {
            mDone = false;
            mCallerIdThread = new Thread(this);
            mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
            mCallerIdThread.start();
        }

        public void stopRequestProcessing() {
            mDone = true;
            if (mCallerIdThread != null) mCallerIdThread.interrupt();
        }

        public void clearCache() {
            synchronized (mContactInfo) {
                mContactInfo.clear();
            }
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
			boolean nameNoChange = TextUtils.equals(ciq.name, ci.name);
			boolean labelNoChange = TextUtils.equals(ciq.numberLabel, ci.label);
			boolean typeNoChange = (ciq.numberType == ci.type);
			if (nameNoChange && labelNoChange && typeNoChange) {
        		return;
			} else if (DBG) {
				Log.d(TAG,"---------------------update call log-------------------------");
				Log.d(TAG, "ContactInfo     : name = " + ci.name + ",type = " + ci.type + ",lable = " + ci.label);
				Log.d(TAG, "CallerInfoQuery : name = " + ciq.name + ",type = " + ciq.numberType + ",lable = " + ciq.numberLabel);
			}
            ContentValues values = new ContentValues(3);
            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);

            try {
            	if(DBG)Log.d(TAG, "Update number=" + ciq.number + " Calls {name="
						+ ci.name + ",type= " + ci.type + ",lable=" + ci.label + "}");
            	MultiSelectCalllogDeleteActivity.this.getContentResolver().update(Calls.CONTENT_URI, values,
                        Calls.NUMBER + "='" + ciq.number + "'", null);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "SQLiteDiskIOException while updating call info", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "SQLiteFullException while updating call info", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "SQLiteDatabaseCorruptException while updating call info", e);
            }
        }

        private void enqueueRequest(String number, int position,
                String name, int numberType, String numberLabel) {
            CallerInfoQuery ciq = new CallerInfoQuery();
            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
        }

        private boolean queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfo.get(ciq.number);
            boolean needNotify = false;
            if (info != null && info != ContactInfo.EMPTY) {
            	if(DBG)Log.d(TAG, "not to query ! update");
//                return true;
            } else {
            	if(DBG)Log.d(TAG, "query ! update");
                Cursor phonesCursor =
                	MultiSelectCalllogDeleteActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                    Uri.encode(ciq.number)),
                    PHONES_PROJECTION, null, null, SIM_INDEX);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        info = new ContactInfo();
                        info.personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                        info.name = phonesCursor.getString(NAME_COLUMN_INDEX);
                        info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
                        info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);

                        boolean isSimNumber = ContactsUtils.isSimNumber(MultiSelectCalllogDeleteActivity.this, info.personId) > 0 ? true : false;
                        if(isSimNumber){
                        	info.type = 0;
                        }
                        info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                        String accountName = phonesCursor.getString(ACCOUNT_NAME_COLUMN_INDEX);
                        if (Account.SIM1_ACCOUNT_NAME.equals(accountName) || "SIM".equals(accountName)) {
                            if(Config.isMSMS){
                                info.label = "SIM1";
                            } else {
                                info.label = "SIM";
                            }
                        } else if (Account.SIM2_ACCOUNT_NAME.equals(accountName)) {
                            info.label = "SIM2";
                        } else {
	                        info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
	                        info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        }
                        info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);

                        // New incoming phone number invalidates our formatted
                        // cache. Any cache fills happen only on the GUI thread.
                        info.formattedNumber = null;

                        mContactInfo.put(info.number, info);
                        // Inform list to update this item, if in view
                        needNotify = true;
                    }
                    phonesCursor.close();
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
            return needNotify;
        }

        /**
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        public void run() {
			boolean needNotify = false;
            while (!mDone) {
                CallerInfoQuery ciq = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        if (needNotify) {
                            needNotify = false;
                            mHandler.sendEmptyMessage(REDRAW);
                        }
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {
                            // Ignore and continue processing requests
                        }
                    }
                }
                if (ciq != null && queryContactInfo(ciq)) {
					needNotify = true;
                }
            }
        }

        @Override
        protected void addGroups(Cursor cursor) {

            int count = cursor.getCount();
            if (count == 0 || !cursor.moveToFirst()) {
                return;
            }
            int groupItemCount = 1;
            CharArrayBuffer currentValue = mBuffer1;
            CharArrayBuffer value = mBuffer2;
            cursor.copyStringToBuffer(NUMBER_COLUMN_INDEX, currentValue);
            int currentCallType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
            for (int i = 1; i < count; i++) {
                cursor.moveToNext();
                cursor.copyStringToBuffer(NUMBER_COLUMN_INDEX, value);
                boolean sameNumber = equalPhoneNumbers(value, currentValue);
                // Group adjacent calls with the same number. Make an exception
                // for the latest item if it was a missed call.  We don't want
                // a missed call to be hidden inside a group.
                if (sameNumber && currentCallType != Calls.MISSED_TYPE) {
                    groupItemCount++;
                } else {
                    if (groupItemCount > 1) {
                        addGroup(i - groupItemCount, groupItemCount, false);
                    }

                    groupItemCount = 1;

                    // Swap buffers
                    CharArrayBuffer temp = currentValue;
                    currentValue = value;
                    value = temp;

                    // If we have just examined a row following a missed call, make
                    // sure that it is grouped with subsequent calls from the same number
                    // even if it was also missed.
                    if (sameNumber && currentCallType == Calls.MISSED_TYPE) {
                        currentCallType = 0;       // "not a missed call"
                    } else {
                        currentCallType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
                    }
                }
            }
            if (groupItemCount > 1) {
                addGroup(count - groupItemCount, groupItemCount, false);
            }
        }

        protected boolean equalPhoneNumbers(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {

            // TODO add PhoneNumberUtils.compare(CharSequence, CharSequence) to avoid
            // string allocation
            return PhoneNumberUtils.compare(new String(buffer1.data, 0, buffer1.sizeCopied),
                    new String(buffer2.data, 0, buffer2.sizeCopied));
        }


        @Override
        protected View newStandAloneView(Context context, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.multi_select_calllog_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindStandAloneView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @Override
        protected View newChildView(Context context, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.multi_select_calllog_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindChildView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @Override
        protected View newGroupView(Context context, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.multi_select_calllog_list_group_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
                boolean expanded) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();
            int groupIndicator = expanded
                    ? com.android.internal.R.drawable.expander_ic_maximized
                    : com.android.internal.R.drawable.expander_ic_minimized;
            views.groupIndicator.setImageResource(groupIndicator);
            views.groupSize.setText("(" + groupSize + ")");
  
            bindView(context, view, cursor);
        }

        private void findAndCacheViews(View view) {

            // Get the views to bind to
            RecentCallsListItemViews views = new RecentCallsListItemViews();
            views.line1View = (TextView) view.findViewById(R.id.line1);
            views.labelView = (TextView) view.findViewById(R.id.label);
            views.numberView = (TextView) view.findViewById(R.id.number);
            views.dateView = (TextView) view.findViewById(R.id.date);
            views.simView = (ImageView) view.findViewById(R.id.sim);
            views.iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            views.groupIndicator = (ImageView) view.findViewById(R.id.groupIndicator);
            views.groupSize = (TextView) view.findViewById(R.id.groupSize);
            views.checkView = (CheckBox) view.findViewById(R.id.checkbox_select);
            views.checkView.setOnClickListener(this);
            view.setTag(views);
        }

        public void bindView(Context context, View view, Cursor c) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = null;
            String callerName = c.getString(CALLER_NAME_COLUMN_INDEX);
            int callerNumberType = c.getInt(CALLER_NUMBERTYPE_COLUMN_INDEX);
            String callerNumberLabel = c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);

            // Store away the number so we can call it directly if you click on the call icon

            // Lookup contacts with this number
            ContactInfo info = mContactInfo.get(number);
            if (info == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                mContactInfo.put(number, info);
                enqueueRequest(number, c.getPosition(),
                        callerName, callerNumberType, callerNumberLabel);
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
            	boolean nameChange = !TextUtils.equals(info.name, callerName);
            	boolean typeChange = info.type != callerNumberType;
            	boolean lableChange = !TextUtils.equals(info.label, callerNumberLabel);
            	Log.d(TAG, "nameChange = " + nameChange + ",typeChange = " + typeChange + ",lableChange = " + lableChange);
                if (nameChange || typeChange || lableChange) {
					if (DBG)Log.d(TAG,"---------request update call log---------");
					if (DBG)Log.d(TAG, "ContactInfo : name = " + info.name + ",type = " + info.type + ",lable = " + info.label);
					if (DBG)Log.d(TAG, "Databases   : name = " + callerName + ",type = " + callerNumberType + ",lable = " + callerNumberLabel);
                	// Something is amiss, so sync up.
                    enqueueRequest(number, c.getPosition(),
                            callerName, callerNumberType, callerNumberLabel);
                }

                // Format and cache phone number for found contact
                if (info.formattedNumber == null) {
                    info.formattedNumber = formatPhoneNumber(info.number);
                }
                formattedNumber = info.formattedNumber;
            }

            String name = info.name;
            int ntype = info.type;
            String label = info.label;
            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;

                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number);
            }
            // Set the text lines and call icon.
            // Assumes the call back feature is on most of the
            // time. For private and unknown numbers: hide it.
            //views.callView.setVisibility(View.VISIBLE);

            if (!TextUtils.isEmpty(name)) {
                views.line1View.setText(name);
                views.labelView.setVisibility(View.VISIBLE);
                CharSequence numberLabel = Phone.getDisplayLabel(context, ntype, label,
                        mLabelArray);
                views.numberView.setVisibility(View.VISIBLE);
                views.numberView.setText(formattedNumber);
                if (!TextUtils.isEmpty(numberLabel)) {
                    views.labelView.setText(numberLabel);
                    views.labelView.setVisibility(View.VISIBLE);
                } else {
                    views.labelView.setVisibility(View.GONE);
                }
            } else {
                if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    number = getString(R.string.unknown);
                   // views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                    number = getString(R.string.private_num);
                    //views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    number = getString(R.string.payphone);
                } else if (number.equals(mVoiceMailNumber)) {
                    number = getString(R.string.voicemail);
                } else {
                    // Just a raw number, and no cache, so format it nicely
                    number = formatPhoneNumber(number);
                }

                views.line1View.setText(number);
                views.numberView.setVisibility(View.GONE);
                views.labelView.setVisibility(View.GONE);
            }

            long date = c.getLong(DATE_COLUMN_INDEX);

            // Set the date/time field by mixing relative and absolute times.
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

            views.dateView.setText(DateUtils.getRelativeTimeSpanString(date,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, flags));

            if (views.iconView != null) {
                int sim = c.getInt(SIM_COLUMN_INDEX);
                if (Config.isMSMS && !PhoneNumberUtils.isEmergencyNumber(number)) {
                    views.simView.setVisibility(View.VISIBLE);
                    if (0 == sim) {
                        views.simView.setImageResource(R.drawable.ico_list_sim1);
                    } else if (1 == sim) {
                        views.simView.setImageResource(R.drawable.ico_list_sim2);
                    }
                } else {
                    views.simView.setVisibility(View.GONE);
                }

                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                // Set the icon
                switch (type) {
                    case Calls.INCOMING_TYPE:
                        views.iconView.setImageDrawable(mDrawableIncoming);
                        break;

                    case Calls.OUTGOING_TYPE:
                        views.iconView.setImageDrawable(mDrawableOutgoing);
                        break;

                    case Calls.MISSED_TYPE:
                        views.iconView.setImageDrawable(mDrawableMissed);
                        break;
                }
            }

			if (views.checkView != null) {
				views.checkView.setFocusableInTouchMode(false);
				views.checkView.setFocusable(false);
				if (SelectState.contains(c.getLong(ID_COLUMN_INDEX))) {
					views.checkView.setChecked(true);
				} else {
					views.checkView.setChecked(false);
				}
				CheckboxTag tags = new CheckboxTag();
				tags.id = c.getLong(ID_COLUMN_INDEX);
				views.checkView.setTag(tags);
				// views.checkView.setTag(c.getString(ID_COLUMN_INDEX));
			}

            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }
    }

    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<MultiSelectCalllogDeleteActivity> mActivity;

        /***
         * Simple handler that wraps background calls to catch
         * {@link SQLiteException}, such as when the disk is full.
         */
        protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
            public CatchingWorkerHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    // Perform same query while catching any exceptions
                    super.handleMessage(msg);
                } catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "SQLiteDiskIOException on background worker thread", e);
                } catch (SQLiteFullException e) {
                    Log.w(TAG, "SQLiteFullException on background worker thread", e);
                } catch (SQLiteDatabaseCorruptException e) {
                    Log.w(TAG, "SQLiteDatabaseCorruptException on background worker thread", e);
                }
            }
        }

        @Override
        protected Handler createHandler(Looper looper) {
            // Provide our special handler that catches exceptions
            return new CatchingWorkerHandler(looper);
        }

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MultiSelectCalllogDeleteActivity>(
                    (MultiSelectCalllogDeleteActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final MultiSelectCalllogDeleteActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                final MultiSelectCalllogDeleteActivity.RecentCallsAdapter callsAdapter = activity.mAdapter;
                callsAdapter.setLoading(false);
                Log.i(TAG, "onQueryComplete changeCursor");
                callsAdapter.changeCursor(cursor);
                if (CurrentCursor != null) {
                    CurrentCursor.close();
                }
                CurrentCursor = cursor;
                updateCheckboxButtoonViewStatus();
            } else {
                cursor.close();
            }
        }
    }

    
     class CancelButtonListener implements OnClickListener{
    	@Override
		public void onClick(View v) {         	
    		finish();
		}
    }
     
     class DeleteButtonListener implements OnClickListener{
     	@Override
 		public void onClick(View v) {         	
            if (SelectState.isEmpty()) {
               Toast.makeText(MultiSelectCalllogDeleteActivity.this, getString(R.string.select_one),
                       Toast.LENGTH_LONG).show();
           } else {
               showDialog(DIALOG_CONFIRM_DELETE_ALL);
           }

 		}
     }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.multi_select_calllog);

        // Typing here goes to the dialer
        setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mAdapter = new RecentCallsAdapter();
        getListView().setOnCreateContextMenuListener(this);
        setListAdapter(mAdapter);

        SelectAll = (CheckBox)findViewById(R.id.checkbox_selected_all);
        SelectAll.setFocusableInTouchMode(false);   
        SelectAll.setFocusable(false); 
        SelectAll.setOnClickListener(this);
        
        DeleteButton = (Button)findViewById(R.id.DeleteButton);
        CancelButton = (Button)findViewById(R.id.CancelButton);
       
        
        DeleteButton.setOnClickListener(new DeleteButtonListener());
        CancelButton.setOnClickListener(new CancelButtonListener());
        
        mQueryHandler = new QueryHandler(this);

        // Reset locale-based formatting cache
        sFormattingType = FORMATTING_TYPE_INVALID;
        
        Intent intent = getIntent();
        CallTypeSelect = intent.getStringExtra("CALLTYPE");
        Log.i(TAG, "onCreate");
    }

    @Override
    protected void onResume() {
        // The adapter caches looked up numbers, clear it so they will get
        // looked up again.
        if (mAdapter != null) {
            mAdapter.clearCache();
        }

        startQuery(CallTypeSelect);
        resetNewCallsFlag();

        super.onResume();

        mAdapter.mPreDrawListener = null; // Let it restart the thread after next draw
        cancelMissedCallsNotification();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        if (CurrentCursor != null && !CurrentCursor.isClosed()) {
            CurrentCursor.close();
        }
        if (mAdapter != null && mAdapter.getCursor() != null && !mAdapter.getCursor().isClosed()) {
            mAdapter.getCursor().close();
        }
        mAdapter.changeCursor(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Clear notifications only when window gains focus.  This activity won't
        // immediately receive focus if the keyguard screen is above it.
		// if (hasFocus) {
		// try {
		// ITelephony iTelephony =
		// ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
		// if (iTelephony != null) {
		// iTelephony.cancelMissedCallsNotification();
		// } else {
		// Log.w(TAG, "Telephony service is null, can't call " +
		// "cancelMissedCallsNotification");
		// }
		// } catch (RemoteException e) {
		// Log.e(TAG,
		// "Failed to clear missed calls notification due to remote exception");
		// }
		// }
    }

    /***
     * Format the given phone number using
     * {@link PhoneNumberUtils#formatNumber(android.text.Editable, int)}. This
     * helper method uses {@link #sEditable} and {@link #sFormattingType} to
     * prevent allocations between multiple calls.
     * <p>
     * Because of the shared {@link #sEditable} builder, <b>this method is not
     * thread safe</b>, and should only be called from the GUI thread.
     * <p>
     * If the given String object is null or empty, return an empty String.
     */
    private String formatPhoneNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }

        // Cache formatting type if not already present
        if (sFormattingType == FORMATTING_TYPE_INVALID) {
            sFormattingType = PhoneNumberUtils.getFormatTypeForLocale(Locale.getDefault());
        }

        sEditable.clear();
        sEditable.append(number);

        PhoneNumberUtils.formatNumber(sEditable, sFormattingType);
        return sEditable.toString();
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI,
                values, where.toString(), null);
    }

    private void startQuery() {
        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI,
                CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }

    private void startQuery(String CallTypeSelect) {
        mAdapter.setLoading(true);
        Log.w(TAG, "startQuery CallTypeSelect: " + CallTypeSelect);
        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI,
                CALL_LOG_PROJECTION, CallTypeSelect, null, Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
       menu.add(0, MENU_ITEM_SELECT_ALL, 0, R.string.recent_Call_log_checkbox_selecte_all)
                .setIcon(R.drawable.ic_menu_mark);
    	
       menu.add(0, MENU_ITEM_UNSELECT_ALL, 0, R.string.recentCalls_button_Cancel)
       .setIcon(android.R.drawable.ic_menu_revert);

        return true;
    }
    
    
    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE_ALL:
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.clearCallLogConfirmation_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.clearCallLogConfirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new  android.content.DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                     		multiSelectCalllogDelete();
           
                        }
                    })
                    .setCancelable(false)
                    .create();
        }
        return null;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_SELECT_ALL: {
            	multiSelectCalllogSelectAll();
            	break;
            }

            case MENU_ITEM_UNSELECT_ALL: {
            	multiSelectCalllogUnSelectAll();
            	break;
            }
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Log.i(TAG, "onListItemClick: " + position);
        if (mAdapter.isGroupHeader(position)) {
            mAdapter.toggleGroup(position);
            Log.i(TAG, "onListItemClick Adapter.toggleGroup: " + position);
        } else {
            
            if (!(v.getTag() instanceof RecentCallsListItemViews)) {
                Log.e(TAG, "Unexpected bound view: " + v);
                return;
            }

            RecentCallsListItemViews itemviews = (RecentCallsListItemViews)v.getTag();

            CheckboxTag  column_id= (CheckboxTag) itemviews.checkView.getTag();

            Log.w(TAG, "onListItemClick: " + column_id.id);

            Long p= column_id.id;

            if(SelectState.contains(p))
            {
                SelectState.remove((Object) p);
                itemviews.checkView.setChecked(false);
            }
            else
            {
                SelectState.add(p);
                itemviews.checkView.setChecked(true);
            }
            updateCheckboxButtoonViewStatus();
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    private void  multiSelectCalllogDelete() {
	    int count = SelectState.size();

	    StringBuilder sb = new StringBuilder();
		// RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();
		for (int i = 0; i < count; i++) {
			if (i != 0) {
				sb.append(",");
			}
			long id = SelectState.get(i);
			sb.append(id);
		}
        Log.i(TAG, "multiSelectCalllogDelete: " + sb);
	    getContentResolver().delete(Calls.CONTENT_URI, Calls._ID + " IN (" + sb + ")",null);
	    finish();
    }

    private void  multiSelectCalllogSelectAll() {
        SelectState.clear();
        if (CurrentCursor != null){
            try {
                if (CurrentCursor.moveToFirst()) {
                    do {
                        Long p = CurrentCursor.getLong(ID_COLUMN_INDEX);
                        if(!SelectState.contains(p))
                            SelectState.add(p);
                    }while (CurrentCursor.moveToNext());
                }
            } catch (Exception e) {
               Log.i(TAG, "Exception");
            }
            if (SelectState.size() > 0) {
                DeleteButton.setEnabled(true);
            }
            mAdapter.notifyDataSetChanged();
            updateCheckboxButtoonViewStatus();
        }
    }

    private void  multiSelectCalllogUnSelectAll() {
    	SelectState.clear();
        mAdapter.notifyDataSetChanged();
        updateCheckboxButtoonViewStatus();
    }
    
    public static void updateCheckboxButtoonViewStatus() {
        if(SelectState.size()== CurrentCursor.getCount()) {
            if(!SelectAll.isChecked())
            SelectAll.setChecked(true);

            DeleteButton.setEnabled(true);
        } else if(SelectState.size()>0) {
             DeleteButton.setEnabled(true);

             if(SelectAll.isChecked())
             SelectAll.setChecked(false);
         } else {
             DeleteButton.setEnabled(false);

             if(SelectAll.isChecked())
             SelectAll.setChecked(false);
         }
    }

    public void onClick(View view) {
        CheckBox checkView = (CheckBox) view ;
        if(checkView.isChecked()) {
            multiSelectCalllogSelectAll();
        } else {
            multiSelectCalllogUnSelectAll();
        }
     }

	// clear statusbar missed calls notification
	private void cancelMissedCallsNotification() {
		try {
			ITelephony iTelephony = ITelephony.Stub.asInterface(ServiceManager
					.getService("phone"));
			if (iTelephony != null) {
				iTelephony.cancelMissedCallsNotification();
			} else {
				Log.w(TAG, "Telephony service is null, can't call "
						+ "cancelMissedCallsNotification");
			}
		} catch (RemoteException e) {
			Log.e(TAG,
					"Failed to clear missed calls notification due to remote exception");
		}
	}
}

