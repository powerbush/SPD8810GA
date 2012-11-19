package com.android.music.filemanager;

import java.util.List;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.music.R;
public class FileGridAdapter extends BaseAdapter {

    private LayoutInflater inflater;
    private List<FileInfo> fileList;

    public FileGridAdapter(Context context, List<FileInfo> files) {
        fileList = files;
        inflater = LayoutInflater.from(context);
    }

    public int getCount() {

        return fileList.size();
    }

    public Object getItem(int position) {

        return fileList.get(position);
    }

    public long getItemId(int position) {

        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        View v;

        if (convertView == null) {
            v = inflater.inflate(R.layout.grid_item, null);
        }
        else {
            v = convertView;
        }

        FileInfo f = fileList.get(position);
        TextView fileName = (TextView) v.findViewById(R.id.grid_item_name);
        fileName.setText(f.Name);
        ImageView fileIcon = (ImageView) v.findViewById(R.id.grid_item_icon);

        fileIcon.setImageResource(f.getIconResourceId());

        return v;
    }

}
