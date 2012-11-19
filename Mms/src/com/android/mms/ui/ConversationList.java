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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.Reader;

import org.apache.http.util.EncodingUtils;

import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;
import com.google.android.mms.pdu.PduHeaders;
import android.database.sqlite.SqliteWrapper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony.Mms;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.telephony.TelephonyManager;
/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationList extends ListActivity
            implements DraftCache.OnDraftChangedListener {
    private static final String TAG = "ConversationList";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG;

    private static final int THREAD_LIST_QUERY_TOKEN       = 1701;
    public static final int DELETE_CONVERSATION_TOKEN      = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN     = 1802;
    private static final int DELETE_OBSOLETE_THREADS_TOKEN = 1803;

    // IDs of the main menu items.
    public static final int MENU_COMPOSE_NEW          = 0;
    public static final int MENU_SEARCH               = 1;
    public static final int MENU_DELETE_ALL           = 3;
    public static final int MENU_PREFERENCES          = 4;
    public static final int MENU_CBSMS                = 5;
    public static final int MENU_MSG_FOLDER           = 6;

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE               = 0;
    public static final int MENU_VIEW                 = 1;
    public static final int MENU_VIEW_CONTACT         = 2;
    public static final int MENU_ADD_TO_CONTACTS      = 3;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private CharSequence mTitle;
    private SharedPreferences mPrefs;
    private Handler mHandler;
    private boolean mNeedToMarkAsSeen;
    private boolean[] mSmsReady = {false, false};
    private boolean fromFolder = false;

    private static boolean notDeleting = true;

    private ListView listView;
    static private final String CHECKED_MESSAGE_LIMITS = "checked_message_limits";
    static final String MMS_LAUNCH_MODE_PATH = "/data/data/com.android.mms/launchmode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String launchMode = getLaunchMode(MMS_LAUNCH_MODE_PATH);
        if (!"folder".equals(launchMode)) {
            setLaunchMode(MMS_LAUNCH_MODE_PATH,"conversation");

            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
            setContentView(R.layout.conversation_list_screen);

            mQueryHandler = new ThreadListQueryHandler(getContentResolver());

//            ListView
            listView = getListView();
            LayoutInflater inflater = LayoutInflater.from(this);
            ConversationListItem headerView = (ConversationListItem)
                    inflater.inflate(R.layout.conversation_list_item, listView, false);
            headerView.bind(getString(R.string.new_message),
                    getString(R.string.create_new_message));
            listView.addHeaderView(headerView, null, true);

            listView.setOnCreateContextMenuListener(mConvListOnCreateContextMenuListener);
            listView.setOnKeyListener(mThreadListKeyListener);

            initListAdapter();

            mTitle = getString(R.string.app_label);

            mHandler = new Handler();
            mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean checkedMessageLimits = mPrefs.getBoolean(CHECKED_MESSAGE_LIMITS, false);
            if (DEBUG) Log.v(TAG, "checkedMessageLimits: " + checkedMessageLimits);
            if (!checkedMessageLimits || DEBUG) {
                runOneTimeStorageLimitCheckForLegacyMessages();
            }

            // fixed bug for 19102 19105 start
            if (MmsApp.initMms) {
                Conversation.init(this, true);
            }
            // fixed bug for 19102 19105 end
        } else {
            Intent it = new Intent(this, MessageFolderActivity.class);
            startActivity(it);
            this.finish();
        }
    }

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        public void onContentChanged(ConversationListAdapter adapter) {
            startAsyncQuery();
        }
    };

    private void initListAdapter() {
    	if(mListAdapter == null){
    		mListAdapter = new ConversationListAdapter(this.getBaseContext(), null);
    	}
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);
        listView.setRecyclerListener(mListAdapter);
//        getListView().setRecyclerListener(mListAdapter);
    }

    /**
     * Checks to see if the number of MMS and SMS messages are under the limits for the
     * recycler. If so, it will automatically turn on the recycler setting. If not, it
     * will prompt the user with a message and point them to the setting to manually
     * turn on the recycler.
     */
    public synchronized void runOneTimeStorageLimitCheckForLegacyMessages() {
        if (Recycler.isAutoDeleteEnabled(this)) {
            if (DEBUG) Log.v(TAG, "recycler is already turned on");
            // The recycler is already turned on. We don't need to check anything or warn
            // the user, just remember that we've made the check.
            markCheckedMessageLimit();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                if (Recycler.checkForThreadsOverLimit(ConversationList.this)) {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit TRUE");
                    // Dang, one or more of the threads are over the limit. Show an activity
                    // that'll encourage the user to manually turn on the setting. Delay showing
                    // this activity until a couple of seconds after the conversation list appears.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Intent intent = new Intent(ConversationList.this,
                                    WarnOfStorageLimitsActivity.class);
                            startActivity(intent);
                        }
                    }, 2000);
                } else {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit silently turning on recycler");
                    // No threads were over the limit. Turn on the recycler by default.
                    runOnUiThread(new Runnable() {
                        public void run() {
                            SharedPreferences.Editor editor = mPrefs.edit();
                            editor.putBoolean(MessagingPreferenceActivity.AUTO_DELETE, true);
                            editor.apply();
                        }
                    });
                }
                // Remember that we don't have to do the check anymore when starting MMS.
                runOnUiThread(new Runnable() {
                    public void run() {
                        markCheckedMessageLimit();
                    }
                });
            }
        }).start();
    }

    /**
     * Mark in preferences that we've checked the user's message limits. Once checked, we'll
     * never check them again, unless the user wipe-data or resets the device.
     */
    private void markCheckedMessageLimit() {
        if (DEBUG) Log.v(TAG, "markCheckedMessageLimit");
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(CHECKED_MESSAGE_LIMITS, true);
        editor.apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        startAsyncQuery();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (notDeleting) {
        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

        Intent it = getIntent();
        Bundle bundle = it.getExtras();
        if (bundle != null) {
            fromFolder = bundle.getBoolean("fromFolder", false);
        }
        if (fromFolder) {
            mNeedToMarkAsSeen = false;
        } else {
            mNeedToMarkAsSeen = true;
        }

        int dropNum = Conversation.cleanExpiredWapPush(this);
        if (0 < dropNum) {
            Toast.makeText(
                    this,
                    getResources().getString(R.string.dl_expired_wap_push,
                            dropNum), Toast.LENGTH_LONG).show();
        }

        startAsyncQuery();

        // We used to refresh the DraftCache here, but
        // refreshing the DraftCache each time we go to the ConversationList seems overly
        // aggressive. We already update the DraftCache when leaving CMA in onStop() and
        // onNewIntent(), and when we delete threads or delete all in CMA or this activity.
        // I hope we don't have to do such a heavy operation each time we enter here.

        // we invalidate the contact cache here because we want to get updated presence
        // and any contact changes. We don't invalidate the cache by observing presence and contact
        // changes (since that's too untargeted), so as a tradeoff we do it here.
        // If we're in the middle of the app initialization where we're loading the conversation
        // threads, don't invalidate the cache because we're in the process of building it.
        // TODO: think of a better way to invalidate cache more surgically or based on actual
        // TODO: changes we care about
        if (!Conversation.loadingThreads()) {
            Contact.invalidateCache();
        }

        // Listen for broadcast intents that indicate the SMS is ready
        IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_IS_SIM_SMS_READY);
        //===== fixed CR<NEWMS00127040> by luning at 11-10-07 begin =====
        filter.addAction(TelephonyIntents.ACTION_IS_SIM_SMS_READY1);
        registerReceiver(mReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (notDeleting) {
            DraftCache.getInstance().removeOnDraftChangedListener(this);
            mListAdapter.changeCursor(null);
        }

    }

    public void onDraftChanged(final long threadId, final boolean hasDraft) {
        // Run notifyDataSetChanged() on the main thread.
        mQueryHandler.post(new Runnable() {
            public void run() {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("onDraftChanged: threadId=" + threadId + ", hasDraft=" + hasDraft);
                }
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void startAsyncQuery() {
    	if(mListAdapter == null){
    		mListAdapter = new ConversationListAdapter(this.getBaseContext(), null);
    		mListAdapter.setOnContentChangedListener(mContentChangedListener);
            setListAdapter(mListAdapter);
            listView.setRecyclerListener(mListAdapter);
    	}
        try {
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);

            Conversation.startQueryForAll(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
            synchronized( Conversation.cacheThreadLock ) {
                Conversation.cacheThreadLock.notify();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        menu.add(0, MENU_MSG_FOLDER, 0, R.string.menu_msg_folder).setIcon(
                R.drawable.ic_menu_folder);

        menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                com.android.internal.R.drawable.ic_menu_compose);

        if (mListAdapter.getCount() > 0) {
            menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all).setIcon(
                    android.R.drawable.ic_menu_delete);
        }

        menu.add(0, MENU_SEARCH, 0, android.R.string.search_go).
            setIcon(android.R.drawable.ic_menu_search).
            setAlphabeticShortcut(android.app.SearchManager.MENU_KEY);

        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(
                android.R.drawable.ic_menu_preferences);

        menu.add(0, MENU_CBSMS, 0, R.string.cell_broadcast_sms).setIcon(
                R.drawable.menu_cb);

        return true;
    }

    public boolean isAnySmsReady() {
        if ((TelephonyManager.getPhoneCount() <= 1 && mSmsReady[0]) ||
             (TelephonyManager.getPhoneCount() > 1 && (mSmsReady[0] || mSmsReady[1]))) {
            return true;
        } else {
            return false;
        }
    }
    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /*appData*/, false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case MENU_COMPOSE_NEW:
                createNewMessage();
                break;
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_DELETE_ALL:
                // The invalid threadId of -1 means all threads here.
                //confirmDeleteThread(-1L, mQueryHandler);
                Intent intentDelete = new Intent(this, MultiSelectSmsDeleteActivity.class);
                startActivity(intentDelete);

                break;
            case MENU_PREFERENCES:
                Log.d(TAG, "[sms]onOptionsItemSelected mSmsReady[0]=" + mSmsReady[0]+" mSmsReady[1]="+mSmsReady[1]);
                if(MessageUtils.isMSMS){
                	Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putString("mode", "conversation");
                    intent.putExtras(bundle);
                    startActivityIfNeeded(intent, -1);
                }else{
                    if (isAnySmsReady()) {
                        Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("mode", "conversation");
                        intent.putExtras(bundle);
                        startActivityIfNeeded(intent, -1);
                    } else {
                        Toast.makeText(this, this.getResources().getString(R.string.sim_no_ready),
                                Toast.LENGTH_LONG).show();
                    }
                }
                break;

                case MENU_CBSMS:
                //Intent intent = new Intent(Intent.ACTION_VIEW);
                            // XXX We need to specify the component here because if we don't
                            // the activity manager will try to resolve the type by calling
                            // the content provider, which causes it to be loaded in a process
                            // other than the Dialer process, which causes a lot of stuff to
                           // break.
                 final Intent intent = new Intent(ConversationList.this, CellBroadcastSmsActivity.class);
                 startActivity(intent);
                 break;

                case MENU_MSG_FOLDER:
                    unregisterReceiver(mReceiver);
                    setLaunchMode(MMS_LAUNCH_MODE_PATH,"folder");
                    Intent it = new Intent(this, MessageFolderActivity.class);
                    startActivity(it);
                    this.finish();
                    break;
            default:
                return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position == 0) {
            createNewMessage();
        } else {
            // Note: don't read the thread id data from the ConversationListItem view passed in.
            // It's unreliable to read the cached data stored in the view because the ListItem
            // can be recycled, and the same view could be assigned to a different position
            // if you click the list item fast enough. Instead, get the cursor at the position
            // clicked and load the data from the cursor.
            // (ConversationListAdapter extends CursorAdapter, so getItemAtPosition() should
            // return the cursor object, which is moved to the position passed in)
            Cursor cursor  = (Cursor) getListView().getItemAtPosition(position);
            Conversation conv = Conversation.from(this, cursor);
            long tid = conv.getThreadId();

            if (LogTag.VERBOSE) {
                Log.d(TAG, "onListItemClick: pos=" + position + ", view=" + v + ", tid=" + tid);
            }

            openThread(tid);
        }
    }

    private void createNewMessage() {
        startActivity(createIntent(this, 0));
    }

    private void openThread(long threadId) {
        startActivity(createIntent(this, threadId));
    }
    private  Intent createIntent(Context context, long threadId) {
        Intent intent = new Intent(context, ComposeMessageActivity.class);

        if (threadId > 0) {
            intent.putExtra("thread_id", threadId);
        }else{
        	intent.putExtra("create", true);
        }

        return intent;
   }
    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        return intent;
    }

    private final OnCreateContextMenuListener mConvListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            Cursor cursor = mListAdapter.getCursor();
            if (cursor == null || cursor.getPosition() < 0) {
                return;
            }
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            ContactList recipients = conv.getRecipients();
            menu.setHeaderTitle(recipients.formatNames(","));

            AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (info.position > 0) {
                menu.add(0, MENU_VIEW, 0, R.string.menu_view);

                // Only show if there's a single recipient
                if (recipients.size() == 1) {
                    // do we have this recipient in contacts?
                    if (recipients.get(0).existsInDatabase()) {
                        menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact);
                    } else {
                        menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts);
                    }
                }
                menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
            }
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Cursor cursor = mListAdapter.getCursor();
        if (cursor != null && cursor.getPosition() >= 0) {
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            long threadId = conv.getThreadId();
            switch (item.getItemId()) {
            case MENU_DELETE: {
                confirmDeleteThread(threadId, mQueryHandler);
                break;
            }
            case MENU_VIEW: {
                openThread(threadId);
                break;
            }
            case MENU_VIEW_CONTACT: {
                Contact contact = conv.getRecipients().get(0);
                Intent intent = new Intent(Intent.ACTION_VIEW, contact.getUri());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                break;
            }
            case MENU_ADD_TO_CONTACTS: {
                String address = conv.getRecipients().get(0).getNumber();
                startActivity(createAddContactIntent(address));
                break;
            }
            default:
                break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened (declared in
        // AndroidManifest.xml).  Because the only translatable text
        // in this activity is "New Message", which has the full width
        // of phone to work with, localization shouldn't be a problem:
        // no abbreviated alternate words should be needed even in
        // 'wide' languages like German or Russian.

        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.v(TAG, "onConfigurationChanged: " + newConfig);
    }

    /**
     * Start the process of putting up a dialog to confirm deleting a thread,
     * but first start a background query to see if any of the threads or thread
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadId id of the thread to delete or -1 for all threads
     * @param handler query handler to do the background locked query
     */
    public static void confirmDeleteThread(long threadId, AsyncQueryHandler handler) {
        Conversation.startQueryHaveLockedMessages(handler, threadId,
                HAVE_LOCKED_MESSAGES_TOKEN);
    }

    /**
     * Build and show the proper delete thread dialog. The UI is slightly different
     * depending on whether there are locked messages in the thread(s) and whether we're
     * deleting a single thread or all threads.
     * @param listener gets called when the delete button is pressed
     * @param deleteAll whether to show a single thread or all threads UI
     * @param hasLockedMessages whether the thread(s) contain locked messages
     * @param context used to load the various UI elements
     */
    public static void confirmDeleteThreadDialog(final DeleteThreadListener listener,
            boolean deleteAll,
            boolean hasLockedMessages,
            Context context) {
        View contents = View.inflate(context, R.layout.delete_thread_dialog_view, null);
        TextView msg = (TextView)contents.findViewById(R.id.message);
        msg.setText(deleteAll
                ? R.string.confirm_delete_all_conversations
                        : R.string.confirm_delete_conversation);
        final CheckBox checkbox = (CheckBox)contents.findViewById(R.id.delete_locked);
        if (!hasLockedMessages) {
            checkbox.setVisibility(View.GONE);
        } else {
            listener.setDeleteLockedMessage(checkbox.isChecked());
            checkbox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    listener.setDeleteLockedMessage(checkbox.isChecked());
                }
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
        .setCancelable(true)
        .setPositiveButton(R.string.delete, listener)
        .setNegativeButton(R.string.no, null)
        .setView(contents)
        .show();
    }

    private final OnKeyListener mThreadListKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL: {
                        long id = getListView().getSelectedItemId();
                        if (id > 0) {
                            confirmDeleteThread(id, mQueryHandler);
                        }
                        return true;
                    }
                }
            }
            return false;
        }
    };

    public static class DeleteThreadListener implements OnClickListener {
        private final long mThreadId;
        private final AsyncQueryHandler mHandler;
        private final Context mContext;
        private boolean mDeleteLockedMessages;

        private ProgressDialog mDelMessProDialog;

        public DeleteThreadListener(long threadId, AsyncQueryHandler handler, Context context) {
            mThreadId = threadId;
            mHandler = handler;
            mContext = context;
        }

        public void setDeleteLockedMessage(boolean deleteLockedMessages) {
            mDeleteLockedMessages = deleteLockedMessages;
        }

        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mThreadId,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                public void run() {
                    int token = DELETE_CONVERSATION_TOKEN;
                    if (mThreadId == -1) {
                        Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages);
                        DraftCache.getInstance().refresh();
                    } else {
                        if(mDelMessProDialog == null){
                            mDelMessProDialog = new ProgressDialog(mContext);
                            mDelMessProDialog.setTitle(mContext.getString(R.string.delete_message));
                            mDelMessProDialog.setMessage(mContext.getText(R.string.wait_message));
                            mDelMessProDialog.setCancelable(false);
                            mDelMessProDialog.show();
                        }
                         notDeleting = false;
                         new DeleteThreadsTask().execute((Void) null);
                    }
                }
            });
            dialog.dismiss();
        }

        private class DeleteThreadsTask extends AsyncTask<Void, Void, Void> {
            @Override
            protected Void doInBackground(Void... params) {
//                Conversation.startDelete(mContext, mDeleteLockedMessages, mThreadId);
                //Pass the token <DELETE_CONVERSATION_TOKEN> to the handler which will be
                //passed to onDeleteComplete.
                Conversation.startDelete(mHandler, DELETE_CONVERSATION_TOKEN,
                        mDeleteLockedMessages, mThreadId);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);

                DraftCache.getInstance().setDraftState(mThreadId, false);
                mDelMessProDialog.dismiss();
                notDeleting = true;
            }
        }
    }

    private final class ThreadListQueryHandler extends AsyncQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                setTitle(mTitle);
                setProgressBarIndeterminateVisibility(false);
                synchronized( Conversation.cacheThreadLock ) {
                    Conversation.cacheThreadLock.notify();
                }

                if (mNeedToMarkAsSeen) {
                    mNeedToMarkAsSeen = false;
                    Conversation.markAllConversationsAsSeen(getApplicationContext());

                    // Delete any obsolete threads. Obsolete threads are threads that aren't
                    // referenced by at least one message in the pdu or sms tables.
                    synchronized( Conversation.ensureThreadIdLock ) {
                        Conversation.asyncDeleteObsoleteThreads(mQueryHandler,
                                DELETE_OBSOLETE_THREADS_TOKEN);
                    }
                }

                break;

            case HAVE_LOCKED_MESSAGES_TOKEN:
                long threadId = (Long)cookie;
                confirmDeleteThreadDialog(new DeleteThreadListener(threadId, mQueryHandler,
                        ConversationList.this), threadId == -1,
                        cursor != null && cursor.getCount() > 0,
                        ConversationList.this);
                if ( cursor != null ) {
                    cursor.close();
                }
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(ConversationList.this, false);

                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.nonBlockingUpdateNewMessageIndicator(ConversationList.this,
                        false, false);
                // Update the notification for failed messages since they
                // may be deleted.
                MessagingNotification.updateSendFailedNotification(ConversationList.this);

                // Make sure the list reflects the delete
                startAsyncQuery();
                break;

            case DELETE_OBSOLETE_THREADS_TOKEN:
                // Nothing to do here.
                break;
            }
        }
    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
//		Contact.removeAllListener();
//		ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
//		am.killBackgroundProcesses(getPackageName());
		String launchMode = getLaunchMode(MMS_LAUNCH_MODE_PATH);
		if (!"folder".equals(launchMode)) {
		    unregisterReceiver(mReceiver);
		    Conversation.dismissInitConcatCacheDialog();
		}

		//clear mListeners,because of it cause of OutOfMemoryException.
		Contact.removeAllListener();
	}

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_IS_SIM_SMS_READY)) {
                mSmsReady[0] = intent.getBooleanExtra("isReady", false);
                Log.d(TAG, "[sms]onReceive ACTION_IS_SIM_SMS_READY mSmsReady[0]=" + mSmsReady[0]);
            } else if (action.equals(TelephonyIntents.ACTION_IS_SIM_SMS_READY1)) {
                mSmsReady[1] = intent.getBooleanExtra("isReady", false);
                Log.d(TAG, "[sms]onReceive ACTION_IS_SIM_SMS_READY mSmsReady[1]=" + mSmsReady[1]);
            }
        }
    };

    private String getLaunchMode(String filename) {
        String launchMode = "";
        try {
            File mFile= new File(filename);
            if (mFile.exists()) {
                FileInputStream is = new FileInputStream(filename);
                int length = is.available();
                byte buffer[] = new byte[length];
                is.read(buffer);
                launchMode = EncodingUtils.getString(buffer, "UTF-8");
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return launchMode;
    }

    private void setLaunchMode(String filename, String mode) {
        try {
            FileOutputStream is = new FileOutputStream(filename);
            byte buffer[] = mode.getBytes();
            is.write(buffer);
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
