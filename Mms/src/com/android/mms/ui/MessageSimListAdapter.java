package com.android.mms.ui;


import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mms.R;


public class MessageSimListAdapter extends BaseAdapter {

    private Context context;
    private Cursor cur;
    ItemView itemView;
    static final int COLUMN_MSG_TYPE            = 0;
    static final int COLUMN_SMS_ADDRESS         = 1;
    static final int COLUMN_SMS_BODY            = 3;
    static final int COLUMN_SMS_DATE            = 4;

    public MessageSimListAdapter(Context context,Cursor cur){
        this.context = context;
        this.cur = cur;
    }
    @Override
    public int getCount() {
        return cur.getCount();
    }

    @Override
    public Object getItem(int position) {
        if (cur.moveToPosition(position)) {
            return cur;
         } else {
            return null;
         }
    }

    @Override
    public long getItemId(int position) {
         return position;
    }

    public void changeCursor(Cursor cursor) {
        if (cur != null) {
            cur.close();
        }
        cur = cursor;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
         if(convertView == null){
             int layout = R.layout.box_messages_sim;
             convertView = LayoutInflater.from(context).inflate(layout,null);     
         }
         itemView = new ItemView();
         itemView.tv_address = (TextView)convertView.findViewById(R.id.box_msg_address);
         itemView.tv_date = (TextView)convertView.findViewById(R.id.box_msg_date);
         itemView.tv_subject=(TextView)convertView.findViewById(R.id.box_msg_subject);
         itemView.checkbox = (CheckBox)convertView.findViewById(R.id.checkbox_select);
         itemView.mImageView = (ImageView)convertView.findViewById(R.id.msg_type_image);
         convertView.setTag(itemView);
         cur.moveToPosition(position);
         ItemView itemView =(ItemView)convertView.getTag();
         String address = cur.getString(COLUMN_SMS_ADDRESS);
         itemView.tv_address.setText(address);
         long datel = cur.getLong(COLUMN_SMS_DATE);
         String date = MessageUtils.formatTimeStampString(context, datel);
         itemView.tv_date.setText(String.valueOf(date));
         String subject = cur.getString(COLUMN_SMS_BODY);
         itemView.tv_subject.setText(subject);
         return convertView;
    }
    class ItemView{
        TextView tv_address;
        TextView tv_date;
        TextView tv_subject;
        ImageView mImageView;
        CheckBox checkbox;
    }
}
