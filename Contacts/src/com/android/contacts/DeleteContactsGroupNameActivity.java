package com.android.contacts;


import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class DeleteContactsGroupNameActivity extends ListActivity{

	private static final String TAG = "DeleteContactsGroupNameActivity";
	private Bundle mMarkForDelete;
	private CheckBox mSelectAll;
	private boolean mMonitorSelectAll = false;
	private int mCurrentCursorCount = 0;
	private CheckBox box ;
	private TextView contactsGroupTextView ;

	private ContactsGroupNameListAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.contacts_group_delete_list);
		setTitle(R.string.group_manager_delete);
		mMarkForDelete = new Bundle();
		mSelectAll = (CheckBox) this.findViewById(R.id.selete_all);
		mSelectAll.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {
				Log.v(TAG, "onTouch");
				mMonitorSelectAll = true;
				return false;
			}

		});
		mSelectAll.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				Log.v(TAG, "setOnCheckedChangeListener");
				Log.v(TAG, "isChecked: " + isChecked);
				if (isChecked) {
					if (mMonitorSelectAll) {
						Cursor cur = getGroupNameListCursor();
						try {
							if (cur.moveToFirst()) {
								do {
									mMarkForDelete.putBoolean(
											cur.getString(cur
													.getColumnIndex("divided_name")),
											true);
								} while (cur.moveToNext());
							}
						} catch (Exception e) {
							// process exception
						} finally {
							cur.close();
						}
						mMonitorSelectAll = false;
					}

				} else {
					if (mMonitorSelectAll) {
						mMarkForDelete.clear();
						mMonitorSelectAll = false;
					}
				}
				mAdapter.notifyDataSetChanged();
			}
		});
		drawList();
	}

	protected void drawList() {
		Log.v(TAG, "drawList()");
		//mSelectAll.setVisibility(View.VISIBLE);

        if (null != mAdapter) {
            Cursor c = mAdapter.getCursor();
            if (null != c) {
                stopManagingCursor(c);
                c.close();
                c = null;
            }
            mAdapter = null;
        }

		mAdapter = new ContactsGroupNameListAdapter(this,
				getGroupNameListCursor());
		setListAdapter(mAdapter);
		if(mCurrentCursorCount < 1){
			mSelectAll.setVisibility(View.GONE);
			finish();
		}else{
			mSelectAll.setVisibility(View.VISIBLE);
		}

	}

	protected Cursor getGroupNameListCursor() {
		Cursor cursor = this.getContentResolver().query(Uri.parse("content://"+ContactsContract.AUTHORITY+"/divided_group"), null, "_id>3", null, null);
		mCurrentCursorCount = cursor.getCount();
		return cursor;
	}

	private class ContactsGroupNameListAdapter extends CursorAdapter{

		private LayoutInflater mInflater;
		private String name;

		public ContactsGroupNameListAdapter(Context context, Cursor c) {
			super(context, c);
			((Activity)context).startManagingCursor(c);
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			Log.v(TAG, "newView()");
			View convertView;
			convertView = mInflater.inflate(R.layout.contacts_group_delete_list_item, null);
			return convertView;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			Log.v(TAG,"bindView()");
			/*ViewHolder holder;
			holder = new ViewHolder();

			holder.select = (CheckBox) view.findViewById(R.id.select);
			holder.select.setFocusable(false);
			holder.select.setFocusableInTouchMode(false);
			holder.select.setClickable(true);*/
			box = (CheckBox) view.findViewById(R.id.select);
			contactsGroupTextView = (TextView) view.findViewById(R.id.contacts_group_name);
			box.setFocusable(false);
			box.setFocusableInTouchMode(false);
			box.setClickable(false);
			box.setVisibility(View.VISIBLE);

			name = cursor.getString(cursor
					.getColumnIndex("divided_name"));
			Log.v(TAG, "name: " + name);
			if(mMarkForDelete.containsKey(name)){
				box.setChecked(true);
			}else{
				box.setChecked(false);
			}
			contactsGroupTextView.setText(name);
			/*holder.contactsGroupName = (TextView) view.findViewById(R.id.contacts_group_name);
			holder.contactsGroupName.setText(name);
			view.setTag(holder);*/
		}
		/*class ViewHolder {
			CheckBox select;
			TextView contactsGroupName;
		}*/
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// TODO Auto-generated method stub
		super.onListItemClick(l, v, position, id);
		Log.v(TAG, "onListItemClick");
		Cursor item = (Cursor) l.getItemAtPosition(position);
		String name = item.getString(item
				.getColumnIndex("divided_name"));
		Log.v(TAG, "onClick-Name: " + name);
		if (mMarkForDelete.containsKey(name)) {
			mMarkForDelete.remove(name);
			mSelectAll.setChecked(false);
			Log.v(TAG, "remove");
		} else {
			mMarkForDelete.putBoolean(name, true);
			if (mMarkForDelete.size() == mCurrentCursorCount) {
				mSelectAll.setChecked(true);
			}
			Log.v(TAG, "put");
		}
		mAdapter.notifyDataSetChanged();
	}

	private void deleteSelected(){
		if (mMarkForDelete.isEmpty()==true) {
			Toast.makeText(this, R.string.recentCalls_delete_toest,
					Toast.LENGTH_SHORT).show();

		}else{
			ContentResolver cr = getContentResolver();
			String mumberkey;
			int id;
			Cursor cur = getGroupNameListCursor();

			try {
				if (cur.moveToFirst()) {
					do {
						mumberkey = cur.getString(cur.getColumnIndex("divided_name"));
						if (mMarkForDelete.containsKey(mumberkey)) {
							id = cur.getInt(cur.getColumnIndexOrThrow(BaseColumns._ID));
							cr.delete(Uri.parse("content://"+ContactsContract.AUTHORITY+"/divided_group"),
									" _id=?",new String[]{String.valueOf(id)});

							// clear group info from table raw_contacts
							ContentValues contentValues = new ContentValues();
							contentValues.put("divided_group_name_id", -1);
							contentValues.put("custom_group_ringtone", "");
							cr.update(Uri.parse("content://"+ContactsContract.AUTHORITY+"/raw_contacts"),
									contentValues, "divided_group_name_id = ?", new String[]{String.valueOf(id)});

							mMarkForDelete.remove(mumberkey);
						}
					} while (cur.moveToNext());
				}
			} catch (Exception e) {

			} finally {
				cur.close();
				drawList();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		menu.add(0, 1, 1, R.string.delete);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch (item.getItemId()) {
		case 1:
			deleteSelected();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

}
