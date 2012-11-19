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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import com.android.mms.R;

/**
 * An adapter to store icons.
 */
public class IconListAdapter extends ArrayAdapter<IconListAdapter.IconListItem> {
    protected LayoutInflater mInflater;
    private static final int mResource = R.layout.icon_list_item;

    public IconListAdapter(Context context,
            List<IconListItem> items) {
        super(context, mResource, items);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        IconListHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(mResource, parent, false);
            holder = new IconListHolder();
            holder.text = (TextView) convertView.findViewById(R.id.text1);
            holder.image = (ImageView) convertView.findViewById(R.id.icon);
            convertView.setTag(holder);
        } else {
            holder = (IconListHolder) convertView.getTag();
        }

        // Set text field
        holder.text.setText(getItem(position).getTitle());

        // Set resource icon
        holder.image.setImageResource(getItem(position).getResource());

        return convertView;
    }

    public static class IconListHolder {
        TextView text;
        ImageView image;
    }

    public static class IconListItem {
        private final String mTitle;
        private final int mResource;

        public IconListItem(String title, int resource) {
            mResource = resource;
            mTitle = title;
        }

        public String getTitle() {
            return mTitle;
        }

        public int getResource() {
            return mResource;
        }
    }
}
