
package com.android.contacts.ui.widget;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.*;
import android.widget.DatePicker;
import android.widget.TextView;
import android.app.*;

public class DateTimePickerHooker implements View.OnTouchListener, DatePickerDialog.OnDateSetListener {

    private final static String TAG = "DateTimePickerHooker";
    private Context mContext;
    private TextView mTextView;
    private java.text.DateFormat mDateFormat;
    private java.util.Calendar mCalendar;

    public DateTimePickerHooker(Context itemContext, TextView editText) {
        mContext = itemContext;
        mTextView = editText;
        mDateFormat = DateFormat.getDateFormat(itemContext); // get system default
        mCalendar = java.util.Calendar.getInstance();
        // mCalendar.set(1990, 0, 1); // set default date 1900-1-1
        // showDatePicker();
	}

    /**
     * show time picker 
     */
    private void showDatePicker() {
        try {
            String str = mTextView.getText().toString();
            if(!TextUtils.isEmpty(str)){
                Date date = mDateFormat.parse(str);
                mCalendar.setTime(date);
            }
            showDatePickerDialog();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG,"showDatePicker error");
        }
    }

    private void showDatePickerDialog() {
        new DatePickerDialog(
            mContext, this,
            mCalendar.get(java.util.Calendar.YEAR),
            mCalendar.get(java.util.Calendar.MONTH),
            mCalendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_UP) {
            showDatePicker();
        }
        return true;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        mCalendar.set(year, monthOfYear, dayOfMonth);
        Date localDate = mCalendar.getTime();
        String mBirthdayString = mDateFormat.format(localDate);
        String[] mBirthdayArray = mBirthdayString.split("/");
        String newString = "";

        if(mBirthdayArray.length == 3) {
        	newString = mBirthdayArray[2] + "-" + mBirthdayArray[0] + "-" + mBirthdayArray[1];
        	newString.trim();
        	mTextView.setText(newString);
        } else {
        	mTextView.setText(mBirthdayString);
        }

    }
}
