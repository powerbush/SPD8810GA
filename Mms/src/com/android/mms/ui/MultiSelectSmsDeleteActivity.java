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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Threads;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;
import com.google.android.mms.pdu.PduHeaders;

/**
 * This activity provides a list view of existing conversations.
 */
public class MultiSelectSmsDeleteActivity extends ListActivity
            implements DraftCache.OnDraftChangedListener ,View.OnClickListener {
    private static final String TAG = "MultiSelectSmsDeleteActivity";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOGV = DEBUG;

    private static final int THREAD_LIST_QUERY_TOKEN       = 1701;
    public static final int DELETE_CONVERSATION_TOKEN      = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN     = 1802;
    private static final int DELETE_OBSOLETE_THREADS_TOKEN = 1803;

    static public ArrayList<Long> SelectState;

    static private Cursor CurrentCursor= null;

    static private Button DeleteButton = null;
    static private Button CancelButton = null;

    static private CheckBox  SelectAll ;

    public static final int MENU_SELECT_ALL               = 0;
    public static final int MENU_SELECT_ALL_CANCEL                 = 1;

    private ThreadListQueryHandler mQueryHandler;
    private MultiSelectSmsDeleteListAdapter mListAdapter;
    private CharSequence mTitle;
    private SharedPreferences mPrefs;
    private Handler mHandler;
    private boolean mNeedToMarkAsSeen;
    private static boolean MultiSelectDeleteLockedMessages;

    static private final String CHECKED_MESSAGE_LIMITS = "checked_message_limits";
    private boolean mDeleteLockedMessages;
    private ProgressDialog mDelMessProDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.multi_select_sms);

        mQueryHandler = new ThreadListQueryHandler(getContentResolver());

        DeleteButton = (Button)findViewById(R.id.DeleteButton);
        CancelButton = (Button)findViewById(R.id.CancelButton);

        SelectAll = (CheckBox)findViewById(R.id.checkbox_selected_all);
        SelectAll.setFocusableInTouchMode(false);
        SelectAll.setFocusable(false);
        SelectAll.setOnClickListener(this);


        DeleteButton.setOnClickListener(new DeleteButtonListener());
        CancelButton.setOnClickListener(new CancelButtonListener());


        SelectState =  new ArrayList<Long>();

        mListAdapter = new MultiSelectSmsDeleteListAdapter(this, null);
        //mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);
        getListView().setRecyclerListener(mListAdapter);

        mTitle = getString(R.string.app_label);

        mHandler = new Handler();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean checkedMessageLimits = mPrefs.getBoolean(CHECKED_MESSAGE_LIMITS, false);
        if (DEBUG) Log.v(TAG, "checkedMessageLimits: " + checkedMessageLimits);
        if (!checkedMessageLimits || DEBUG) {
            runOneTimeStorageLimitCheckForLegacyMessages();
        }
    }

    private final MultiSelectSmsDeleteListAdapter.OnContentChangedListener mContentChangedListener =
        new MultiSelectSmsDeleteListAdapter.OnContentChangedListener() {
        public void onContentChanged(MultiSelectSmsDeleteListAdapter adapter) {
            Log.v(TAG, "MultiSelectSmsDeleteListAdapter onContentChanged: ");
            startAsyncQuery();
        }
    };


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
                if (Recycler.checkForThreadsOverLimit(MultiSelectSmsDeleteActivity.this)) {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit TRUE");
                    // Dang, one or more of the threads are over the limit. Show an activity
                    // that'll encourage the user to manually turn on the setting. Delay showing
                    // this activity until a couple of seconds after the conversation list appears.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Intent intent = new Intent(MultiSelectSmsDeleteActivity.this,
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
                            editor.commit();
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
        editor.commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        startAsyncQuery();
    }

    @Override
    protected void onStart() {
        super.onStart();

        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

        mNeedToMarkAsSeen = true;

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
//        if (!Conversation.loadingThreads()) {
//            Contact.invalidateCache();
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        DraftCache.getInstance().removeOnDraftChangedListener(this);
        mListAdapter.changeCursor(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( CurrentCursor != null ) {
            CurrentCursor.close();
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
        try {
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);

            Conversation.startQueryForAll(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

       menu.add(0, MENU_SELECT_ALL, 0, R.string.Conversation_menu_type_selected_all).setIcon(
             com.android.internal.R.drawable.ic_menu_mark);

       menu.add(0, MENU_SELECT_ALL_CANCEL, 0, R.string.Conversation_menu_type_cancel_selected_all).setIcon(
               android.R.drawable.ic_menu_revert);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId()) {

            case MENU_SELECT_ALL:
                multiSelectSMSSelectAll();
                break;

            case MENU_SELECT_ALL_CANCEL:
                multiSelectSMSUnSelectAll();
                break;

            default:
                return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        Log.v(TAG, "onListItemClick: position=" + position + ", id=" + id);
        MultiSelectSmsDeleteListItem headerView = (MultiSelectSmsDeleteListItem) v;
        ConversationListItemData ch = headerView.getConversationHeader();
        long threadId  = ch.getThreadId();

        if(SelectState.contains(threadId))
        {
            SelectState.remove((Object) threadId);
            headerView.CheckBoxSetChecked(false);
        }
        else
        {
            SelectState.add(threadId);
            headerView.CheckBoxSetChecked(true);
        }

        updateCheckboxButtoonViewStatus();

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


       public static void setDeleteLockedMessage(boolean deleteLockedMessages) {
        MultiSelectDeleteLockedMessages = deleteLockedMessages;
    }


    private final class ThreadListQueryHandler extends AsyncQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                Log.v(TAG, "onQueryComplete: THREAD_LIST_QUERY_TOKEN");
                mListAdapter.changeCursor(cursor);
                setTitle(mTitle);
                setProgressBarIndeterminateVisibility(false);

                if (CurrentCursor != null && !CurrentCursor.isClosed()) {
                    CurrentCursor.close();
                }

                CurrentCursor = cursor;

                if (mNeedToMarkAsSeen) {
                    mNeedToMarkAsSeen = false;
                    Conversation.markAllConversationsAsSeen(getApplicationContext());

                    // Delete any obsolete threads. Obsolete threads are threads that aren't
                    // referenced by at least one message in the pdu or sms tables.
                    Conversation.asyncDeleteObsoleteThreads(mQueryHandler,
                            DELETE_OBSOLETE_THREADS_TOKEN);
                }
                updateCheckboxButtoonViewStatus();
                break;

            case HAVE_LOCKED_MESSAGES_TOKEN:
                long threadId = (Long)cookie;
                Log.i(TAG, "onQueryComplete: HAVE_LOCKED_MESSAGES_TOKEN"+threadId);
                startMutiSelectDeleteThread(threadId,mQueryHandler,MultiSelectDeleteLockedMessages && ( cursor != null && cursor.getCount() > 0),MultiSelectSmsDeleteActivity.this);
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
                            // Make sure the list reflects the delete
                if(SelectState.size()>0)
                {
                    long threadId = SelectState.get(0);
                    Log.i(TAG, "onDeleteComplete: onClick  SelectState.size() > 0 "+threadId);
                    startMutiSelectDeleteThread(threadId,mQueryHandler,MultiSelectDeleteLockedMessages ,MultiSelectSmsDeleteActivity.this);
                    SelectState.remove((Object)threadId);
                }
                else
                {
                    //move this code,because of it's not necessary.--start
//                    // Make sure the conversation cache reflects the threads in the DB.
//                    Conversation.init(MultiSelectSmsDeleteActivity.this, false);
                    //move this code,because of it's not necessary.--start

                    // Update the notification for new messages since they
                    // may be deleted.
                    MessagingNotification.nonBlockingUpdateNewMessageIndicator(MultiSelectSmsDeleteActivity.this,
                            false, false);
                    // Update the notification for failed messages since they
                    // may be deleted.
                    MessagingNotification.updateSendFailedNotification(MultiSelectSmsDeleteActivity.this);


                    Log.i(TAG, "onDeleteComplete: onClick  SelectState.size() == 0 ");
                    finish();
                }
                break;

            case DELETE_OBSOLETE_THREADS_TOKEN:
                // Nothing to do here.
                break;
            }
        }

    }


    public class DeleteThreadListener implements OnClickListener {

        public void onClick(DialogInterface dialog, final int whichButton) {

            if(SelectState.size()>0)
            {
                long threadId = SelectState.get(0);
                SelectState.remove((Object)threadId);

                Log.i(TAG, "DeleteThreadListener: onClick  "+threadId);
                startMutiSelectDeleteThread(threadId, mQueryHandler);
            }
        }
    }



    public static void confirmMutiSelectDeleteThreadDialog(final DeleteThreadListener listener,
            Context context) {
        View contents = View.inflate(context, R.layout.delete_thread_dialog_view, null);
        TextView msg = (TextView)contents.findViewById(R.id.message);
        msg.setText( R.string.confirm_delete_conversation);
        final CheckBox checkbox = (CheckBox)contents.findViewById(R.id.delete_locked);

        setDeleteLockedMessage(checkbox.isChecked());
        checkbox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setDeleteLockedMessage(checkbox.isChecked());
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
        .setCancelable(true)
        .setPositiveButton(R.string.delete, listener)
        .setNegativeButton(R.string.no, null)
        .setView(contents)
        .show();
    }

    class CancelButtonListener implements  android.view.View.OnClickListener{
       @Override
       public void onClick(View v) {
           finish();
       }
   }

    class DeleteButtonListener implements android.view.View.OnClickListener{
       @Override
       public void onClick(View v) {

           Log.i(TAG, "confirmMutiSelectDeleteThreadDialog: onClick  ");
           if(SelectState.size() > 0)
           {
            confirmMutiSelectDeleteThreadDialog(new DeleteThreadListener(),MultiSelectSmsDeleteActivity.this);
           }
       }
    }


    /**
     * Start the process of putting up a dialog to confirm deleting a thread,
     * but first start a background query to see if any of the threads or thread
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadId id of the thread to delete or -1 for all threads
     * @param handler query handler to do the background locked query
     */
    public static void startMutiSelectDeleteThread(long threadId, AsyncQueryHandler handler) {
        Conversation.startQueryHaveLockedMessages(handler, threadId,HAVE_LOCKED_MESSAGES_TOKEN);
    }

    private void  startMutiSelectDeleteThread(long threadId, AsyncQueryHandler handler,boolean DeleteLockedMessages, Context context)
    {
        final long mThreadId;
        final AsyncQueryHandler mHandler;
        final Context mContext;

        mThreadId = threadId;
        mHandler = handler;
        mContext = context;


        mDeleteLockedMessages = DeleteLockedMessages;

        Log.i(TAG, "startMutiSelectDeleteThread "+threadId +"mDeleteLockedMessages"+mDeleteLockedMessages);
        if(mDelMessProDialog == null){
            mDelMessProDialog = new ProgressDialog(MultiSelectSmsDeleteActivity.this);
            mDelMessProDialog.setTitle(getString(R.string.delete_message));
            mDelMessProDialog.setMessage(getText(R.string.wait_message));
            mDelMessProDialog.setCancelable(false);
            mDelMessProDialog.show();
        }

        MessageUtils.handleReadReport(mContext, mThreadId,
                PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {

            public void run() {
                int token = DELETE_CONVERSATION_TOKEN;
                if (SelectAll.isChecked()) {
                    new DeleteAllThreadsTask().execute((Void) null);
                } else if (mThreadId == -1) {
                    Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages);
                    DraftCache.getInstance().refresh();
                } else {
                    Conversation.startDelete(mHandler, token, mDeleteLockedMessages,
                            mThreadId);
                    DraftCache.getInstance().setDraftState(mThreadId, false);
                }
            }
        });
    }

    private void multiSelectSMSSelectAll()
    {
        SelectState.clear();

        if (CurrentCursor != null){
        try {

            if (CurrentCursor.moveToFirst())
            {
                do
                {
                     long p = CurrentCursor.getLong(0);

                     if(!SelectState.contains(p))
                     SelectState.add(p);

                }while (CurrentCursor.moveToNext());
            }
        }
        catch (Exception e)
        {
            Log.i(TAG, "Exception");
        }

        startAsyncQuery();
      }
    }

    private void  multiSelectSMSUnSelectAll()
    {
        SelectState.clear();
        startAsyncQuery();
    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }


    public static void updateCheckboxButtoonViewStatus()
    {
        Log.i(TAG, "updateCheckboxButtoonViewStatus "+SelectState.size()+CurrentCursor.getCount());

        if(SelectState.size()== CurrentCursor.getCount() )
        {
            if(!SelectAll.isChecked())
            SelectAll.setChecked(true);

            DeleteButton.setEnabled(true);
        }
        else if(SelectState.size()>0)
        {
             DeleteButton.setEnabled(true);

             if(SelectAll.isChecked())
             SelectAll.setChecked(false);
         }
         else
         {
             DeleteButton.setEnabled(false);

             if(SelectAll.isChecked())
             SelectAll.setChecked(false);
         }


    }
    public void onClick(View view) {
        CheckBox checkView = (CheckBox) view ;

        if(checkView.isChecked())
        {
            multiSelectSMSSelectAll();
        }
        else
        {
            multiSelectSMSUnSelectAll();
        }

     }

    private class DeleteAllThreadsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String selection = mDeleteLockedMessages ? null : "locked=0";
            getContentResolver().delete(Threads.CONTENT_URI, selection, null);
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            DraftCache.getInstance().refresh();
            MultiSelectSmsDeleteActivity.this.finish();
            mDelMessProDialog.dismiss();
        }
    }
}
