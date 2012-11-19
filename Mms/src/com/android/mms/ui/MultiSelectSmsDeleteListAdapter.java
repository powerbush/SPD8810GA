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

import com.android.mms.R;
import com.android.mms.data.Conversation;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;

/**
 * The back-end data adapter for ConversationList.
 */
//TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class MultiSelectSmsDeleteListAdapter  extends CursorAdapter implements AbsListView.RecyclerListener {
    private static final String TAG = "MultiSelectSmsDeleteListAdapter";
    private static final boolean LOCAL_LOGV = true;

    public final LayoutInflater mFactory;//??private
    private OnContentChangedListener mOnContentChangedListener;

    boolean MenuDelete = false;
    Bundle bundle;
    
    public MultiSelectSmsDeleteListAdapter (Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mFactory = LayoutInflater.from(context);
    }
    
  
    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        if (!(view instanceof MultiSelectSmsDeleteListItem)) {
            Log.e(TAG, "Unexpected bound view: " + view);
            return;
        }
        
        MultiSelectSmsDeleteListItem headerView = (MultiSelectSmsDeleteListItem) view;
       
     
        Conversation conv = Conversation.from(context, cursor);
        ConversationListItemData ch = new ConversationListItemData(context, conv);
        headerView.bind(context, ch);

        
    }

    public void onMovedToScrapHeap(View view) {
        MultiSelectSmsDeleteListItem headerView = (MultiSelectSmsDeleteListItem)view;
        headerView.unbind();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
       if (LOCAL_LOGV) Log.v(TAG, "inflating new view");
        return mFactory.inflate(R.layout.multiselect_sms_delete_list_item, parent, false);
    }

    public interface OnContentChangedListener {
        void onContentChanged(MultiSelectSmsDeleteListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        if (mCursor != null && !mCursor.isClosed()) {
            if (mOnContentChangedListener != null) {
                mOnContentChangedListener.onContentChanged(this);
            }
        }
    }
}
