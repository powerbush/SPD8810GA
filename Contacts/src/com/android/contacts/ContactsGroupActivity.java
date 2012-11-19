package com.android.contacts;

import com.android.contacts.ContactsLiveFolders.StarredContacts;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This activity can show, edit and set group info
 */
public class ContactsGroupActivity extends ListActivity implements OnCreateContextMenuListener{
    private boolean isEnableDeleteGroup = false;
    private static final int SYSTEM_GROUP_COUNT = 4;
	static final Uri DIVIDED_GROUP_URI=Uri.parse("content://"+ContactsContract.AUTHORITY+"/divided_group");
	private static final String TAG = "ContactsGroupActivity";
	private static final int ADD = 1;
	private static final int DELETE = 2;
	SimpleCursorAdapter cursorAdapter;
	int mCurrentRecordCount = 0;
	private Cursor mCursor = null;

    private static final int GROUP_NAME_STUDENT = 0;
    private static final int GROUP_NAME_FRIEND = 1;
    private static final int GROUP_NAME_FAMILY = 2;
    private static final int GROUP_NAME_COLLEAGUE = 3;

    // the item for edit group info menu
    private static final int MENU_ITEM_EDIT_GROUP = 1;
    private static final int MENU_ITEM_BULK_SMS = 2;
    private static final int MENU_ITEM_BULK_EMAIL = 3;

    private final static int MODE_BULK_SMS = 0;
    private final static int MODE_BULK_MAIL = 1;

    private static final int MODE_PICK = 0;
    private static final int GROUP_PHONE = 1;

    private static final int MODE_BATH_EMAIL_CONTACTS_PICK = 12;

    // the flag of system group like student, friend...
    private boolean isSystemGroup = false;

    // the flag of has show ringtone pick activity
    private boolean isRingtonePickShowing = false;

    // show ringtone info title
    private TextView mGroupRingtoneTitle;

    /** the launch code when picking a ringtone */
    private static final int RINGTONE_PICKED = 3023;

    /**
     * uri for a custom ringtone associated with the contact. If null or
     * missing,the default ringtone will be used.
     */
    private String mGroupRingtoneUri;

    /** this key for raw_contacts table */
    private final static String CUSTOM_GROUP_RINGTONE = "custom_group_ringtone";

    private final static String DIVIDED_NAME = "divided_name";
    private final static String DIVIDED_RINGTONE = "divided_ringtone";

    /** project both RawContacts._ID and CUSTOM_GROUP_RINGTONE */
    private final String[] mGroupRingtoneProject = new String[] {
        RawContacts._ID, CUSTOM_GROUP_RINGTONE
    };

    /** the id of one group */
    private int mGroupId = -1;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "oncreate");
		setTitle(R.string.group_manager);	//yeezone:jinwei CR:00104343 2011-8-22
		setContentView(R.layout.group_manage);
		getListView().setOnCreateContextMenuListener(this);
		this.getContentResolver().registerContentObserver(DIVIDED_GROUP_URI, true, observer);
		fillData();
	}

	private ContentObserver observer = new ContentObserver(new Handler()) {
		public void onChange(boolean selfChange) {
			fillData();
		};
	};

	private void fillData(){
		Log.v(TAG, "fillData()");
		if(mCursor != null){
			mCursor.close();
			mCursor = null;
		}
		mCursor = this.getContentResolver().query(DIVIDED_GROUP_URI,
        		null, null, null, null);
        Log.i(TAG, "cursor.getCount" + mCursor.getCount());
        String displayGroupName = "";
        while(mCursor.moveToNext()){
            int groupNameId = mCursor.getInt(mCursor.getColumnIndexOrThrow(BaseColumns._ID));
            Log.i(TAG, "groupNameId = " + groupNameId);
            String groupName = mCursor.getString(mCursor.getColumnIndexOrThrow("divided_name"));
            ContentValues values = new ContentValues();
            switch(groupNameId){
                case GROUP_NAME_STUDENT:
                    displayGroupName = getString(R.string.group_name_student);
                    values.put("divided_name", displayGroupName);
                    break;
                case GROUP_NAME_FRIEND:
                    displayGroupName = getString(R.string.group_name_friend);
                    values.put("divided_name", displayGroupName);
                    break;
                case GROUP_NAME_FAMILY:
                    displayGroupName = getString(R.string.group_name_family);
                    values.put("divided_name", displayGroupName);
                    break;
                case GROUP_NAME_COLLEAGUE:
                    displayGroupName = getString(R.string.group_name_colleague);
                    values.put("divided_name", displayGroupName);
                    break;
                default:
                    displayGroupName = groupName;
                    break;
            }

            if(!displayGroupName.equals(groupName)){
                this.getContentResolver().update(DIVIDED_GROUP_URI, values, "_id=" + groupNameId, null);
            }
        }

       //startManagingCursor(cursor);
       mCurrentRecordCount = mCursor.getCount();
       cursorAdapter = new SimpleCursorAdapter(this, R.layout.group_manage_item,
             mCursor, new String[]{"divided_name"}, new int[]{R.id.group_item});
       setListAdapter(cursorAdapter);
       Log.i(TAG, "groupCursor.getCount() = " + mCursor.getCount());
       isSystemGroupEnabled();
    }

    @Override
    protected void onResume() {
       super.onResume();
       isRingtonePickShowing = false;
       isSystemGroupEnabled();
    }

    @Override
    protected void onDestroy() {
       super.onDestroy();
       this.getContentResolver().unregisterContentObserver(observer);
       if(mCursor != null){
           mCursor.close();
           mCursor = null;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
       super.onListItemClick(l, v, position, id);

       Cursor cursor = (Cursor) l.getItemAtPosition(position);
       int groupNameId = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
       String groupName = cursor.getString(cursor.getColumnIndexOrThrow(DIVIDED_NAME));
       String groupRingtone = cursor.getString(cursor.getColumnIndexOrThrow(DIVIDED_RINGTONE));
       Log.v(TAG, "groupNameId: " + groupNameId + ", " + "groupName: " + groupName
               + ", groupRingtone: " + groupRingtone);
       Intent intent = new Intent(this,ContactsListActivity.class);
       intent.putExtra("mContactsGroupNameId", groupNameId);
       intent.putExtra("mContactsGroupName", groupName);
       intent.putExtra("mContactsGroupRingtone", groupRingtone);
       startActivity(intent);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ADD, 1, R.string.add_group);
		menu.add(0, DELETE, 2, R.string.delete_group);
		return super.onCreateOptionsMenu(menu);
	}

   @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
       menu.findItem(DELETE).setEnabled(isEnableDeleteGroup);
       return super.onPrepareOptionsMenu(menu);
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case ADD:
            editContactsGroup(false, null);
			break;
		case DELETE:
			if(mCurrentRecordCount > 0){
				deleteContactsGroupName();
			}else{
				Toast.makeText(this, R.string.noGroupInfo, Toast.LENGTH_SHORT).show();
			}

			break;
		default:
			break;
		}

		return super.onOptionsItemSelected(item);
	}
	private void deleteContactsGroupName() {
		startActivity(new Intent(this, DeleteContactsGroupNameActivity.class));
	}

    /**
     * pick a ringtone for group
     */
    private void pickGroupRingtone() {
        if (isRingtonePickShowing) {
            Log.d(TAG, "ringtone pick has show");
            return;
        }

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Don't show 'Silent'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        Uri ringtoneUri;
        if (mGroupRingtoneUri != null) {
            ringtoneUri = Uri.parse(mGroupRingtoneUri);
        } else {
            // Otherwise pick default ringtone Uri so that something is
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);

        // Launch!
        startActivityForResult(intent, RINGTONE_PICKED);
        isRingtonePickShowing = true;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case RINGTONE_PICKED: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
                    mGroupRingtoneUri = null;
                } else {
                    mGroupRingtoneUri = pickedUri.toString();
                }

                isRingtonePickShowing = false;
                // update UI about ringtone view
                updateGroupRingtoneView();
                break;
            }
        }
    }

    /**
     * update group edit view when ringtone has changed
     */
    private void updateGroupRingtoneView() {
        if (mGroupRingtoneUri == null || mGroupRingtoneUri.trim().length() <= 0) {
            mGroupRingtoneTitle.setText(getString(R.string.default_ringtone));
        } else {
            Uri ringtoneUri = Uri.parse(mGroupRingtoneUri);
            Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone == null) {
                Log.w(TAG, "ringtone's URI doesn't resolve to a Ringtone");
                return;
            }
            mGroupRingtoneTitle.setText(ringtone.getTitle(this));
        }
    }

    /**
     * update group ringtone for raw_contacts
     */
    private void updateGroupRingtone() {
        String selection = null;
        if (mGroupId != -1) {
            selection = "divided_group_name_id=" + mGroupId;
            // reset mGroupId
            mGroupId = -1;
        }

        if (null != selection) {
            Cursor cursor = getContentResolver().query(RawContacts.CONTENT_URI,
                    mGroupRingtoneProject, selection, null, null);
            try {
                while (cursor.moveToNext()) {
                    String currentUri = cursor.getString(1);// only have
                                                            // CUSTOM_GROUP_RINGTONE
                    if (TextUtils.equals(currentUri, mGroupRingtoneUri)) {
                        continue;
                    } else {
                        long id = cursor.getLong(0);
                        currentUri = mGroupRingtoneUri;
                        ContentValues values = new ContentValues(2);
                        values.put(CUSTOM_GROUP_RINGTONE, currentUri);
                        final String where = RawContacts._ID + "=" + id;

                        getContentResolver().update(RawContacts.CONTENT_URI, values, where,
                                null);
                    }
                }
            } catch (Exception e) {
                final String msg = e.getMessage();
                if (null != msg) {
                    Log.e(TAG, msg);
                } else {
                    e.printStackTrace();
                }
            } finally {
                if (null != cursor) {
                    cursor.close();
                }
            }
        }
    }

    private String queryGroupRingtoneUriByGroupName(String name) {

        String[] projects = new String[] {BaseColumns._ID, DIVIDED_RINGTONE};
        String selection = DIVIDED_NAME + "=?";
        String[] selectionArgs = new String[] {name};
        Cursor cursor = getContentResolver().query(DIVIDED_GROUP_URI, projects, selection, selectionArgs, null);

        String ringtoneUri = null;
        try {
            if (null != cursor && cursor.moveToFirst()) {
                mGroupId = cursor.getInt(0);
                ringtoneUri = cursor.getString(1);
            }
        } catch (Exception e) {
            final String msg = e.getMessage();
            if (null != msg) {
                Log.e(TAG, msg);
            } else {
                e.printStackTrace();
            }
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }

        return ringtoneUri;
    }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        Log.d(TAG, "info.position ======= " + info.position);

        menu.add(0, MENU_ITEM_EDIT_GROUP, 1, R.string.edit_group_info);
        menu.add(0, MENU_ITEM_BULK_SMS, 1, R.string.bulk_sms);
        menu.add(0, MENU_ITEM_BULK_EMAIL, 1, R.string.bulk_email);
    }

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int id = item.getItemId();
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        final String groupName = cursor.getString(cursor
                .getColumnIndexOrThrow(DIVIDED_NAME));
        final int groupNameId =
                cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
		switch (id) {
            case MENU_ITEM_EDIT_GROUP:
                if (info.position == GROUP_NAME_STUDENT || info.position == GROUP_NAME_FRIEND
                        || info.position == GROUP_NAME_FAMILY
                        || info.position == GROUP_NAME_COLLEAGUE) {
                    isSystemGroup = true;
                }
                editContactsGroup(true, groupName);
                break;
            case MENU_ITEM_BULK_SMS:
                startBulkIntent(groupNameId, MODE_BULK_SMS);
		        break;
            case MENU_ITEM_BULK_EMAIL:
                startBulkIntent(groupNameId, MODE_BULK_MAIL);
	            break;
            default:
		        break;
		}
		return super.onContextItemSelected(item);
	}

    private void startBulkIntent(final int groupNameId, final int bulkAction) {
        Intent intent = new Intent("com.android.contacts.MULTIOPERATELIST");
        if (groupNameId != -1) {
            intent.putExtra("groupNameId", groupNameId);
        }
        intent.putExtra("group", MODE_PICK);
        intent.putExtra("bulkaction", bulkAction);
        startActivity(intent);
    }

    /**
     * save group with edit status and oldName
     * @param isEdit
     * @param oldName
     */
    private void editContactsGroup(final boolean isEdit, final String oldName) {

        int dialogTitleRes = isEdit ? R.string.edit_group_info : R.string.group_new;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(dialogTitleRes);
        LayoutInflater li = LayoutInflater.from(this);
        final View view = li.inflate(R.layout.promt, null);

        // add code for group ringtone
        View ringtoneLayout = view.findViewById(R.id.ringtone);
        final EditText groupNameEditText = (EditText) view.findViewById(R.id.group_name);
        groupNameEditText.setEnabled(true);

        TextView label = (TextView) ringtoneLayout.findViewById(R.id.label);
        label.setText(getString(R.string.label_ringtone));

        mGroupRingtoneTitle = (TextView) ringtoneLayout.findViewById(R.id.data);

        if (isEdit) {
            groupNameEditText.setText(oldName);
            if (isSystemGroup) {
                groupNameEditText.setEnabled(false);
                isSystemGroup = false;
            }
            mGroupRingtoneUri = queryGroupRingtoneUriByGroupName(oldName);
        } else {
            mGroupRingtoneUri = null;
        }

        updateGroupRingtoneView();

        ringtoneLayout.setFocusable(true);
        ringtoneLayout.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                pickGroupRingtone();
            }
        });

        builder.setView(view);

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON1 == which) {

                    // check groupName is empty or not
                    String groupName = groupNameEditText.getText().toString();
                    if (null != groupName) {
                        groupName = groupName.trim();
                       }
                    if (TextUtils.isEmpty(groupName)) {
                        Toast.makeText(ContactsGroupActivity.this,
                                getString(R.string.group_name_cannot_empty), Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    // check repeat name
                    ContentValues values = new ContentValues();
                    ContentResolver contentResolver = ContactsGroupActivity.this
                            .getContentResolver();

                    boolean isRepeat = false;
                    boolean isNeedCheck = true;
                    if (isEdit) {
                        if (oldName.equals(groupName)) {
                            isNeedCheck = false;
                        }
                    }

                    if (isNeedCheck) {
                        Cursor cursor = contentResolver.query(DIVIDED_GROUP_URI, null,
                                " divided_name=?", new String[] {
                                    groupName
                                }, null);

                        if (null != cursor /* && cursor.moveToNext() */) {
                            if (cursor.getCount() > 0) {
                                isRepeat = true;
                            }
                            cursor.close();
                        }
                    }

                    if (isRepeat) {
                        new AlertDialog.Builder(ContactsGroupActivity.this)
                                .setTitle(R.string.contacts_group)
                                .setMessage(R.string.contacts_group_name_exist)
                                .setPositiveButton(R.string.sure, null).show();
                    } else {
                        values.put(DIVIDED_NAME, groupName);
                        values.put(DIVIDED_RINGTONE, mGroupRingtoneUri);

                        if (isEdit) {
                            // update table divided group
                            contentResolver = ContactsGroupActivity.this.getContentResolver();
                            int id = contentResolver.update(DIVIDED_GROUP_URI, values,
                                    " divided_name=?", new String[] {
                                        oldName
                                    });
                            fillData();

                            // update table row contacts
                            updateGroupRingtone();
                        } else {
                            contentResolver.insert(DIVIDED_GROUP_URI, values);
                        }
                    }
                }
            }
        };
        builder.setPositiveButton(R.string.save, listener);
        builder.setNegativeButton(R.string.cancel, listener);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.show();
    }

    private void isSystemGroupEnabled() {
        if(null != mCursor && mCursor.getCount() > SYSTEM_GROUP_COUNT){
	         isEnableDeleteGroup = true;
    	  }else{
	         isEnableDeleteGroup = false;
	      }
    }
}

