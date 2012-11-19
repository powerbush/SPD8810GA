package com.az.PersonInfo;

import android.util.Log;

public class DateBuffer{
	public int YearValue=2012;
	public int MonthValue=6;
	public int DayOfMonthvalue=20;
	public String mDate;
	private static final String TAG = "Aizhuservice-Setting";
	public DateBuffer (String strDate){
		if(strDate.length() > 0){
			SetDate(strDate);
		}
	}
	public void SetDate (String strDate){
		mDate = strDate;
      	String strDateTemp = strDate;
      	String strTemp;
      	int strLen = strDate.length();
      	int pos1 = 0;
		int pos2 =strDateTemp.indexOf("-");
		if(pos2 > pos1){
			strTemp = strDateTemp.substring(pos1, pos2);
			YearValue =Integer.parseInt(strTemp);
			strDateTemp = strDateTemp.substring(pos2+1, strLen-1);
		}
		strLen = strDateTemp.length();
		pos2 =strDateTemp.indexOf("-");
		if(pos2 > pos1){
			strTemp = strDateTemp.substring(pos1, pos2);
			MonthValue =Integer.parseInt(strTemp);
			strDateTemp = strDateTemp.substring(pos2+1);
		}
		DayOfMonthvalue = Integer.parseInt(strDateTemp);
	}
	public void SetDate (int year, int month, int day){
		YearValue = year;
		MonthValue = month;
		DayOfMonthvalue = day;
		mDate = String.valueOf(year);
	    mDate += "-" + ((month + 1) < 10 ? "0" + String.valueOf(month + 1) : String.valueOf((month + 1)));
	    mDate += "-" + ((day < 10) ? "0" + String.valueOf(day) : String.valueOf(day));
	    Log.i(TAG, "Enter SetDate mDate = " + mDate);
	}
}
