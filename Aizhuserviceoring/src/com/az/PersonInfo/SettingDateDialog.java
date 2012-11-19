package com.az.PersonInfo;

import com.az.Main.R;

import android.app.DatePickerDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Button;
import android.widget.DatePicker;
import android.app.Dialog;

public class SettingDateDialog  extends DatePickerDialog {
	private static final String TAG = "Aizhuservice-Setting";
	
	private int mTitleId = 0;
	
	public SettingDateDialog(Context context,
            OnDateSetListener callBack, int year, int monthOfYear,
            int dayOfMonth) {
        super(context, callBack, year, monthOfYear, dayOfMonth);
    }
	
	//when date change set the titleid
	public void onDateChanged(DatePicker view, int year, int month, int day) {
        super.onDateChanged(view, year, month, day);
        setTitle(mTitleId);
    }
	
	//save the titleid
	public void setTitleId(int titleId) {
		Log.i(TAG, "Enter setTitle setTitleId" + String.valueOf(titleId));	
        setTitle(titleId);
        mTitleId = titleId;        
    }

}
