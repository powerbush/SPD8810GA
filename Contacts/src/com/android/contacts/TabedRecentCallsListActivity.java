/*
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

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Intents.UI;
import android.util.Log;
import android.view.Window;
import android.widget.TabHost;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
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
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabWidget;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Displays a list of call log entries.
 */
public class TabedRecentCallsListActivity extends TabActivity implements
        TabHost.OnTabChangeListener {
    private static final String TAG = "RecentCallsList";

    private static final int TAB_INDEX_ALL = 0;
    private static final int TAB_INDEX_MISSED = 1;
    private static final int TAB_INDEX_OUTGOING = 2;
    private static final int TAB_INDEX_RECEIVED = 3;

    /** Last manually selected tab index */
    private static final String PREF_LAST_MANUALLY_SELECTED_TAB = "last_manually_selected_tab";
    private static final int PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT = TAB_INDEX_ALL;

    /** Name of the tabedRecentCalls shared preferences */
    static final String PREFS_TABED_RECENT_CALLS = "tabedRecentCalls";

    private TabHost mTabHost;
    private TabWidget mTabWidget;

    private int mLastManuallySelectedTab;

    public void onTabChanged(String tabId) {

    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.tabed_recent_calls);
        Log.d(TAG, "TabedRecentCallsListActivity :  onCreate" );
        
        mTabHost = getTabHost();
        mTabHost.setOnTabChangedListener(this);

        setupTabs();
        mTabWidget = mTabHost.getTabWidget();
        mTabWidget.setStripEnabled(false);

        for (int i = 0; i < mTabWidget.getChildCount(); i++) {

            TextView tv = (TextView) mTabWidget.getChildAt(i).findViewById(
                    android.R.id.title);
            tv.setTextColor(this.getResources().getColorStateList(
                    android.R.color.white));
            tv.setTextSize(20);   //通话记录底部按钮的字体大小
            
            Log.d(TAG, "mTabWidget.getChildAt(i).getLayoutParams().height :  " +tv.getTextSize());
            tv.setPadding(0, 0, 0,(int) tv.getTextSize());
            mTabWidget.getChildAt(i).getLayoutParams().height =(int ) (3* tv.getTextSize());
 
            mTabWidget.getChildAt(i).setBackgroundResource(R.drawable.tab_bg);
        }
        // Load the last manually loaded tab
        final SharedPreferences prefs = getSharedPreferences(
                PREFS_TABED_RECENT_CALLS, MODE_PRIVATE);
        mLastManuallySelectedTab = prefs.getInt(
                PREF_LAST_MANUALLY_SELECTED_TAB,
                PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT);

        Intent intent = getIntent();
        int missed_call = intent.getIntExtra("missed_call", 0);
        //add by phone_09,fix bug 3595
        Log.d(TAG, "missed_call:" + missed_call);
        if (missed_call == 1) {
            mTabHost.setCurrentTab(TAB_INDEX_MISSED);
        } else {
            mTabHost.setCurrentTab(TAB_INDEX_ALL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void setupTabs() {
        mTabHost.addTab(mTabHost.newTabSpec("all").setIndicator(
                getString(R.string.recentCallsAllIconLabel)).setContent(
                new Intent(this, RecentCallsListAllActivity.class)));
        mTabHost.addTab(mTabHost.newTabSpec("Missed").setIndicator(
                getString(R.string.recentCallsMissedIconLabel)).setContent(
                new Intent(this, RecentCallsListMissedActivity.class)));
        mTabHost.addTab(mTabHost.newTabSpec("Outgoing").setIndicator(
                getString(R.string.recentCallsOutgoingIconLabel)).setContent(
                new Intent(this, RecentCallsListOutgoingActivity.class)));
        mTabHost.addTab(mTabHost.newTabSpec("Received").setIndicator(
                getString(R.string.recentCallsReceivedIconLabel)).setContent(
                new Intent(this, RecentCallsListReceivedActivity.class)));

    }

    public void setupCurrentTab(Intent intent) {

        // Dismiss menu provided by any children activities
        Activity activity = getLocalActivityManager().getActivity(
                mTabHost.getCurrentTabTag());
        if (activity != null) {
            activity.closeOptionsMenu();
        }

        int missed_call = intent.getIntExtra("missed_call", 0);
        Log.d(TAG, "setupCurrentTab missed_call:" + missed_call);
        if (missed_call == 1) {
            mTabHost.setCurrentTab(TAB_INDEX_MISSED);
        } else {
            mTabHost.setCurrentTab(TAB_INDEX_ALL);
        }
    }

}
