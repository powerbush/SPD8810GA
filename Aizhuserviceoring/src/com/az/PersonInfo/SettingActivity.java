package com.az.PersonInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.az.Main.MainActivity;
import com.az.Main.R;

public class SettingActivity  extends Activity 
      implements View.OnClickListener {
	private static final String TAG = "Aizhuservice-instantiateItem";
	//������ҳ�������
	private static final int setting_name_phone = 0;
	private static final int setting_sex = 1;
	private static final int setting_age_wei_hei = 2;
	private static final int setting_constitution = 3;
	private static final int setting_certificate = 4;
	private static final int setting_address = 5;
	private static final int setting_emergent = 6;
	private static final int setting_insurance = 7;
	private static final int setting_datatype_remark = 8;
	private static final int setting_disease = 9;
	private static final int setting_insurance_items = 10;

	private static final int INVALID = -1;
	//����ViewPage��ز���
	private ViewPager mSettingViewPager;
	private List<View> mSettingListViews;
	private LayoutInflater mInflater;
	
	private SettingPagerAdapter mSettingPageAdapter;

    //��Ϣ¼�빲11������
	private View layout1 = null;
	private View layout2 = null;
	private View layout3 = null;
	private View layout4 = null;
	private View layout5 = null;
	private View layout6 = null;
	private View layout7 = null;
	private View layout8 = null;
	private View layout9 = null;
	private View layout10 = null;
	private View layout11 = null;
	
	//������Ϣ¼����ɺ���ʾ�Ի���
	private AlertDialog dialog = null;
	
	//������Ϣ�ϴ� �������Ƿ�ɹ���־
	boolean do_sendInfoFlag = false;
	
	//���ر���������Ϣ
	private SharedPreferences mPerferences;
	
	//��������ý����������
	//layout1 setting_name_phone
	private EditText mETName,mETPhone; 
	//layout2 setting_sex	
	private RadioGroup mRadioGroupSex;
	private RadioButton mRadioButtonSex;
	//layout3 setting_age_wei_hei
	private EditText mETAge, mETWeight, mETHeight;
	//layout4 setting_constitution
	private Spinner mSpinnerConstitution;
	//layout5 setting_certificate
	private Spinner mSpinnerCertiType;
	private EditText mETCertiNum;
	//layout6 setting_address
	private Spinner mSpinnerAddrPro;
	private EditText mETAddrCity, mETAddrInfo;
	//layout7 setting_emergent
	private EditText mETEmerName,mETEmerPhone; 
	//layout8 setting_insurance
	private Button mBTInsurStar,mBTInsurEnd; 
	private boolean mBTInsurStarFlag = false;
	private boolean mBTInsurEndFlag = false;
	private SettingDateDialog mDatePickerDialogStar;
	private SettingDateDialog mDatePickerDialogEnd;
	private DateBuffer mDateBufInsurStar, mDateBufInsurEnd;
	private DatePickerDialog.OnDateSetListener mDateSetListener;
	
	//layout9 setting_datatype_remark
	private Spinner mSpinnerDataType;
	private EditText mETRemark;
	
	//layout10 setting_disease	
	private CheckBox mCBoxDisease01;
	private CheckBox mCBoxDisease02;
	private CheckBox mCBoxDisease03;
	private CheckBox mCBoxDisease04;
	private CheckBox mCBoxDisease05;
	private CheckBox mCBoxDisease06;
	private CheckBox mCBoxDisease07;
	private CheckBox mCBoxDisease08;
	private CheckBox mCBoxDisease09;
	private CheckBox mCBoxDisease10;
	private CheckBox mCBoxDisease11;
	private CheckBox mCBoxDisease12;
	private String mStrDisease; 
	
	//layout11 setting_insurance_items
	private CheckBox mCBoxInsurItems01;
	private CheckBox mCBoxInsurItems02;
	private CheckBox mCBoxInsurItems03;
	private CheckBox mCBoxInsurItems04;
	private CheckBox mCBoxInsurItems05;
	private CheckBox mCBoxInsurItems06;
	private CheckBox mCBoxInsurItems07;
	private CheckBox mCBoxInsurItems08;
	private CheckBox mCBoxInsurItems99;
	private String mStrInsurItems; 
	
	//��Ϣ¼����ʾ�Ի��� ��ť��Ӧ�¼�
	public boolean onKeyDown(int keyCode, KeyEvent event) {
  		Log.i(TAG, "Enter onKeyDown");	
	
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
			/*Exit();*/
		    Intent intent=new Intent(this,MainActivity.class);
		    startActivity(intent);
		    finish();
		    return true;
		}	
		return super.onKeyDown(keyCode, event);	 
    }
	
	public void onCreate(Bundle savedInstanceState) {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
	    Log.i(TAG, "Enter SettingActivity::onCreate");
	    
		setContentView(R.layout.setting_main);
		
		//��ñ�����Ϣ�������
		mPerferences = getSharedPreferences("com.az.PersonInfo_preferences",Context.MODE_WORLD_READABLE);
	    
		mSettingPageAdapter = new SettingPagerAdapter();
		mSettingViewPager = (ViewPager) findViewById(R.id.viewpagerLayout_setting);
		mSettingViewPager.setAdapter(mSettingPageAdapter);
	
		mSettingListViews = new ArrayList<View>();
		mInflater = getLayoutInflater();

		
		layout1 = mInflater.inflate(R.layout.setting_name_phone, null);
		layout2 = mInflater.inflate(R.layout.setting_sex, null);
		layout3 = mInflater.inflate(R.layout.setting_age_wei_hei, null);
		layout4 = mInflater.inflate(R.layout.setting_constitution, null);
		layout5 = mInflater.inflate(R.layout.setting_certificate, null);
		layout6 = mInflater.inflate(R.layout.setting_address, null);
		layout7 = mInflater.inflate(R.layout.setting_emergent, null);
		layout8 = mInflater.inflate(R.layout.setting_insurance, null);
		layout9 = mInflater.inflate(R.layout.setting_datatype_remark, null);
		layout10 = mInflater.inflate(R.layout.setting_disease, null);
		layout11 = mInflater.inflate(R.layout.setting_insurance_items, null);
		
		mSettingListViews.add(layout1);
		mSettingListViews.add(layout2);
		mSettingListViews.add(layout3);
		mSettingListViews.add(layout4);
		mSettingListViews.add(layout5);
		mSettingListViews.add(layout6);
		mSettingListViews.add(layout7);
		mSettingListViews.add(layout8);
		mSettingListViews.add(layout9);
		mSettingListViews.add(layout10);
		mSettingListViews.add(layout11);
		
	    
		mSettingViewPager.setCurrentItem(0);
		
		mPerferences = getSharedPreferences("com.az.PersonInfo_preferences",Context.MODE_WORLD_READABLE);
	    
	    //��ø�ҳ��������󲢳�ʼ��
	    //layout1 setting_name_phone
	    mETName = (EditText) layout1.findViewById(R.id.edit_name);
		mETPhone = (EditText) layout1.findViewById(R.id.edit_phone);
		
		layout1.findViewById(R.id.button_name_next).setOnClickListener(this);
		Log.i("onCreate", "strName = " + mPerferences.getString("edit_name", ""));
		Log.i("onCreate", "strPhone = " + mPerferences.getString("edit_phone", ""));
		mETName.setText(mPerferences.getString("edit_name", ""));
		mETPhone.setText(mPerferences.getString("edit_phone", ""));
		
		//layout2 setting_sex
		mRadioGroupSex = (RadioGroup) layout2.findViewById(R.id.radiogroup_sex);
		
		layout2.findViewById(R.id.button_sex_up).setOnClickListener(this);
		layout2.findViewById(R.id.button_sex_next).setOnClickListener(this);
		
		if(mPerferences.getString("selectsex_key","").equals(R.string.Girl)){
			mRadioGroupSex.check(R.id.radio_sex_woman);
		} else if((mPerferences.getString("selectsex_key","").equals(R.string.Boy))){
			mRadioGroupSex.check(R.id.radio_sex_man);
		}
		
		//layout3 setting_age_wei_hei
		
		mETAge = (EditText) layout3.findViewById(R.id.edit_age);
		mETWeight = (EditText) layout3.findViewById(R.id.edit_weight);
		mETHeight = (EditText) layout3.findViewById(R.id.edit_height);
		
		layout3.findViewById(R.id.button_weight_up).setOnClickListener(this);
		layout3.findViewById(R.id.button_weight_next).setOnClickListener(this);
		
		mETAge.setText(mPerferences.getString("edit_age", ""));
		mETWeight.setText(mPerferences.getString("edit_weight", ""));
		mETHeight.setText(mPerferences.getString("edit_height", ""));
		
		//layout4 setting_constitution
		
		mSpinnerConstitution = (Spinner) layout4.findViewById(R.id.spinner_constitution);
		layout4.findViewById(R.id.button_constitution_up).setOnClickListener(this);
		layout4.findViewById(R.id.button_constitution_next).setOnClickListener(this);
		
		//constitution_key.setSummary(mPerferences.getString("constitution_key",""));
		
	    //layout5 setting_certificate	
		mSpinnerCertiType = (Spinner) layout5.findViewById(R.id.
				spinner_certificate_type);
		mETCertiNum = (EditText) layout5.findViewById(R.id.edit_certificate_num);
		
		layout5.findViewById(R.id.button_certificate_up).setOnClickListener(this);
		layout5.findViewById(R.id.button_certificate_next).setOnClickListener(this);
		
		mETCertiNum.setText(mPerferences.getString("edit_certificate_num", ""));
		//mCertificateTypePreference.setSummary(mPerferences.getString("certificateType_key",""));        
	    
		//layout6 setting_address	
		mSpinnerAddrPro = (Spinner) layout6.findViewById(R.id.
				spinner_address_province);
		mETAddrCity = (EditText) layout6.findViewById(R.id.edit_address_city);
		mETAddrInfo = (EditText) layout6.findViewById(R.id.edit_address);
		
		mETAddrCity.setText(mPerferences.getString("edit_address_city", ""));
		mETAddrInfo.setText(mPerferences.getString("edit_address", ""));
		
		layout6.findViewById(R.id.button_address_up).setOnClickListener(this);
		layout6.findViewById(R.id.button_address_next).setOnClickListener(this);
		
		//layout7 setting_emergent	
		mETEmerName = (EditText) layout7.findViewById(R.id.edit_emergent_name);
		mETEmerPhone = (EditText) layout7.findViewById(R.id.edit_emergent_phone);
		
		mETEmerName.setText(mPerferences.getString("edit_emergent_name", ""));
		mETEmerPhone.setText(mPerferences.getString("edit_emergent_phone", ""));
		
		layout7.findViewById(R.id.button_emergent_up).setOnClickListener(this);
		layout7.findViewById(R.id.button_emergent_next).setOnClickListener(this);
		
		//layout8 setting_insurance
		mBTInsurStar = (Button) layout8.findViewById(R.id.button_insurance_star);
		mBTInsurEnd = (Button) layout8.findViewById(R.id.button_insurance_end);
		    
		mDateBufInsurStar = new DateBuffer(mPerferences.getString("R.id.button_insurance_star", ""));
		mDateBufInsurEnd = new DateBuffer(mPerferences.getString("R.id.button_insurance_end", ""));
		
		mDateSetListener = new DatePickerDialog.OnDateSetListener() { 
 	       public void onDateSet(DatePicker view, int year, int monthOfYear,  
 	              int dayOfMonth) {  
 	    	   if(mBTInsurStarFlag && !mBTInsurEndFlag){
 	    			mDateBufInsurStar.SetDate(year, monthOfYear, dayOfMonth);
 		       		mBTInsurStar.setText(mDateBufInsurStar.mDate);
 	       		
 	       	    } else if(!mBTInsurStarFlag && mBTInsurEndFlag){
 	       	    	mDateBufInsurEnd.SetDate(year, monthOfYear, dayOfMonth);
 		       		mBTInsurEnd.setText(mDateBufInsurEnd.mDate);
 	       	    };
 	       	}  
 	    }; 
 	   mDatePickerDialogStar = new SettingDateDialog(this, mDateSetListener, mDateBufInsurStar.YearValue, 
				mDateBufInsurStar.MonthValue, mDateBufInsurStar.DayOfMonthvalue);
		mDatePickerDialogStar.setTitleId(R.string.SettingUserInsuranceStar);
		mDatePickerDialogEnd = new SettingDateDialog(this, mDateSetListener, mDateBufInsurEnd.YearValue, 
				mDateBufInsurEnd.MonthValue, mDateBufInsurEnd.DayOfMonthvalue);
		mDatePickerDialogEnd.setTitleId(R.string.SettingUserInsuranceEnd);
 	   
        /*
 	    mDatePickerDialogStar = new DatePickerDialog(this, mDateSetListener, 
        		mDateBufInsurStar.YearValue, mDateBufInsurStar.MonthValue, mDateBufInsurStar.DayOfMonthvalue);
        mDatePickerDialogEnd =new DatePickerDialog(this, mDateSetListener,
        		mDateBufInsurEnd.YearValue, mDateBufInsurEnd.MonthValue, mDateBufInsurEnd.DayOfMonthvalue);
        */
        mBTInsurStar.setText(mDateBufInsurStar.mDate);
        mBTInsurEnd.setText(mDateBufInsurEnd.mDate);
		mBTInsurStar.setOnClickListener(this); 
		mBTInsurEnd.setOnClickListener(this);
        
		mBTInsurStar.setText(mPerferences.getString("button_insurance_star", ""));
		mBTInsurEnd.setText(mPerferences.getString("button_insurance_end", ""));
		
		mBTInsurStar.setOnClickListener(this);
		layout8.findViewById(R.id.button_insurance_up).setOnClickListener(this);
		layout8.findViewById(R.id.button_insurance_next).setOnClickListener(this);
		
		//layout9 setting_datatype_remark
		
		mSpinnerDataType = (Spinner) layout9.findViewById(R.id.
				spinner_datatype);		
		mETRemark = (EditText) layout9.findViewById(R.id.edit_remark);		
		
		mETRemark.setText(mPerferences.getString("edit_remark", ""));
		//mSpinnerDataType.set
		
		layout9.findViewById(R.id.button_datatype_up).setOnClickListener(this);
		layout9.findViewById(R.id.button_datatype_next).setOnClickListener(this);
		
		//layout10 setting_disease		
		mCBoxDisease01 = (CheckBox) layout10.findViewById(R.id.check_disease_01);
		mStrDisease = mPerferences.getString("diseaseTpye_key", "");
		Log.i(TAG, "SharedPrefCommit mStrDisease = " + mStrDisease);
        
		if(mStrDisease.indexOf("01") != INVALID)
		{
			mCBoxDisease01.setSelected(true);
		}
		mCBoxDisease01.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {			   
	   
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		     Log.i(TAG, "Enter SettingDiseaseActivity::setDisease.onCheckedChanged isChecked = " + String.valueOf(isChecked));
		     // TODO Auto-generated method stub
		     if(mCBoxDisease01.isChecked())
		     {
		    	 
		    	 mCBoxDisease11.setChecked(false);
		     }		     
		    }
		   });
		mCBoxDisease02 = (CheckBox) layout10.findViewById(R.id.check_disease_02);
		if(mStrDisease.indexOf("02") != INVALID)
		{
			mCBoxDisease02.setSelected(true);
		}
		mCBoxDisease02.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
		        // TODO Auto-generated method stub
		        if(mCBoxDisease02.isChecked()){		    	 
		    	    mCBoxDisease11.setChecked(false);
		        }
		    }
		});
		
		mCBoxDisease03 = (CheckBox) layout10.findViewById(R.id.check_disease_03);
		if(mStrDisease.indexOf("03") != INVALID)
		{
			mCBoxDisease03.setSelected(true);
		}
		mCBoxDisease03.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease03.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease04 = (CheckBox) layout10.findViewById(R.id.check_disease_04);
		if(mStrDisease.indexOf("04") != INVALID)
		{
			mCBoxDisease04.setSelected(true);
		}
		mCBoxDisease04.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease04.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease05 = (CheckBox) layout10.findViewById(R.id.check_disease_05);
		if(mStrDisease.indexOf("05") != INVALID)
		{
			mCBoxDisease05.setSelected(true);
		}
		mCBoxDisease05.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease05.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease06 = (CheckBox) layout10.findViewById(R.id.check_disease_06);
		if(mStrDisease.indexOf("06") != INVALID)
		{
			mCBoxDisease06.setSelected(true);
		}
		mCBoxDisease06.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease06.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease07 = (CheckBox) layout10.findViewById(R.id.check_disease_07);
		if(mStrDisease.indexOf("07") != INVALID)
		{
			mCBoxDisease07.setSelected(true);
		}
		mCBoxDisease07.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease07.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease08 = (CheckBox) layout10.findViewById(R.id.check_disease_08);
		if(mStrDisease.indexOf("08") != INVALID)
		{
			mCBoxDisease08.setSelected(true);
		}
		mCBoxDisease08.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease08.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease09 = (CheckBox) layout10.findViewById(R.id.check_disease_09);
		if(mStrDisease.indexOf("09") != INVALID)
		{
			mCBoxDisease09.setSelected(true);
		}
		mCBoxDisease09.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease09.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease10 = (CheckBox) layout10.findViewById(R.id.check_disease_10);
		if(mStrDisease.indexOf("10") != INVALID)
		{
			mCBoxDisease10.setSelected(true);
		}
		mCBoxDisease10.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease10.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease11 = (CheckBox) layout10.findViewById(R.id.check_disease_11);
		if(mStrDisease.indexOf("11") != INVALID)
		{
			mCBoxDisease11.setSelected(true);
		}
		mCBoxDisease11.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
		        // TODO Auto-generated method stub
		        if(mCBoxDisease11.isChecked())
		        {
		    	    mCBoxDisease12.setChecked(false);
		    	    mCBoxDisease11.setChecked(true);
				    mCBoxDisease10.setChecked(false);
				    mCBoxDisease09.setChecked(false);
				    mCBoxDisease08.setChecked(false);
				    mCBoxDisease07.setChecked(false);
				    mCBoxDisease06.setChecked(false);
				    mCBoxDisease05.setChecked(false);
				    mCBoxDisease04.setChecked(false);
				    mCBoxDisease03.setChecked(false);
				    mCBoxDisease02.setChecked(false);
				    mCBoxDisease01.setChecked(false);
		        }		     
		    }
		});
		mCBoxDisease12 = (CheckBox) layout10.findViewById(R.id.check_disease_12);		
		if(mStrDisease.indexOf("12") != INVALID)
		{
			mCBoxDisease12.setSelected(true);
		}
		mCBoxDisease12.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		     
	            // TODO Auto-generated method stub
		        if(mCBoxDisease12.isChecked())
		        {
		    	    mCBoxDisease11.setChecked(false);
		        }		     
		    }
		});
		layout10.findViewById(R.id.button_disease_up).setOnClickListener(this);
		layout10.findViewById(R.id.button_disease_next).setOnClickListener(this);
		
		//layout11 setting_insurance_items
		
		mCBoxInsurItems01 = (CheckBox) layout11.findViewById(R.id.check_insu_item_01);
		mCBoxInsurItems02 = (CheckBox) layout11.findViewById(R.id.check_insu_item_02);
		mCBoxInsurItems03 = (CheckBox) layout11.findViewById(R.id.check_insu_item_03);
		mCBoxInsurItems04 = (CheckBox) layout11.findViewById(R.id.check_insu_item_04);
		mCBoxInsurItems05 = (CheckBox) layout11.findViewById(R.id.check_insu_item_05);
		mCBoxInsurItems06 = (CheckBox) layout11.findViewById(R.id.check_insu_item_06);
		mCBoxInsurItems07 = (CheckBox) layout11.findViewById(R.id.check_insu_item_07);
		mCBoxInsurItems08 = (CheckBox) layout11.findViewById(R.id.check_insu_item_08);
		mCBoxInsurItems99 = (CheckBox) layout11.findViewById(R.id.check_insu_item_99);
		mStrInsurItems = mPerferences.getString("insuranceitems_key", ""); 
		Log.i(TAG, "SharedPrefCommit mStrInsurItems = " + mStrInsurItems);
	    if(mStrInsurItems.equals("01,02,03,04,05,06,07,08,")){
	    	mCBoxInsurItems99.setSelected(true);
	    } else if(mStrInsurItems.indexOf("01") != INVALID){
	    	mCBoxInsurItems01.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("02") != INVALID){
	    	mCBoxInsurItems02.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("03") != INVALID){
	    	mCBoxInsurItems03.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("04") != INVALID){
	    	mCBoxInsurItems04.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("05") != INVALID){
	    	mCBoxInsurItems05.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("06") != INVALID){
	    	mCBoxInsurItems06.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("07") != INVALID){
	    	mCBoxInsurItems07.setSelected(true);	    
	    } else if(mStrInsurItems.indexOf("08") != INVALID){
	    	mCBoxInsurItems08.setSelected(true);	    
	    } 
		
		layout11.findViewById(R.id.button_insu_item_up).setOnClickListener(this);
		layout11.findViewById(R.id.button_insu_item_inputok).setOnClickListener(this);
	
	
		mSettingViewPager.setOnPageChangeListener(new OnPageChangeListener() {	
			public void onPageSelected(int arg0) {
				Log.d(TAG, "onPageSelected - " + arg0);
				
				// activity��1��2������2�����غ���ô˷���
				View v = mSettingListViews.get(arg0);
				//EditText editText = (EditText) v.findViewById(R.id.editText1);
				//editText.setText("��̬����#" + arg0 + "edittext�ؼ���ֵ");
				switch (arg0) {
				case setting_sex:
					if(!setNamePhoneInPut()){
						mSettingViewPager.setCurrentItem(setting_name_phone);
					}				
					break;
				case setting_age_wei_hei:
					mRadioButtonSex = (RadioButton)findViewById(mRadioGroupSex.getCheckedRadioButtonId());
					break;
				case setting_address:
					if(!setCertiInPut()){
						mSettingViewPager.setCurrentItem(setting_certificate);
					}
					break;
				case setting_insurance_items:
					if(!setDiseaseInPut()){
						mSettingViewPager.setCurrentItem(setting_disease);
					}
					break;
	
				default:
					break;
				}
			}
	
			public void onPageScrolled(int arg0, float arg1, int arg2) {
				//Log.d(TAG, "onPageScrolled - arg0 = " + String.valueOf(arg0));
				//Log.d(TAG, "onPageScrolled - arg1 = " + String.valueOf(arg1));
				//Log.d(TAG, "onPageScrolled - arg2 = " + String.valueOf(arg2));
				// ��1��2��������1����ǰ����				
			}
	
	
			public void onPageScrollStateChanged(int arg0) {
				//Log.d(TAG, "onPageScrollStateChanged - " + arg0);
				// ״̬�����0���У�1�����ڻ����У�2Ŀ��������
				
	
			}
		});
		
		//��ʼ����Ϣ¼����ʾ�Ի���
		dialog = new ProgressDialog(this);
        
		dialog.setTitle(getString(R.string.AzWaiting));
		dialog.setMessage(getString(R.string.AzUpdataIng));			
	 	Log.i(TAG, "Exit SettingActivity::onCreate");
	}
    

	public void onClick(View v) {
  		Log.i(TAG, "Enter onClick");
  		switch (v.getId()) {
  		case R.id.button_insurance_star:
  			Log.i(TAG, "In onClick button_insurance_star");	
  			mBTInsurStarFlag = true;
			mBTInsurEndFlag = false;
			showDialog(R.id.button_insurance_star);
			break;
  		case R.id.button_insurance_end:
  			Log.i(TAG, "In onClick button_insurance_end");	
	  		mBTInsurStarFlag = false;
			mBTInsurEndFlag = true;
			showDialog(R.id.button_insurance_end);
			break;
		case R.id.button_name_next: //layout1
			if(!setNamePhoneInPut()){
				return;
			}
			mSettingViewPager.setCurrentItem(setting_sex);
			break;
		case R.id.button_sex_up:   //layout2
			mSettingViewPager.setCurrentItem(0);
			break;
		case R.id.button_sex_next:   //layout2
			mRadioButtonSex = (RadioButton)findViewById(mRadioGroupSex.getCheckedRadioButtonId());
	        mSettingViewPager.setCurrentItem(setting_age_wei_hei);
			break;
			
		case R.id.button_weight_up:   //layout3
			mSettingViewPager.setCurrentItem(setting_sex);
			break;
		case R.id.button_weight_next:   //layout3
			mSettingViewPager.setCurrentItem(setting_constitution);
			break;
		case R.id.button_constitution_up:      //layout4
			mSettingViewPager.setCurrentItem(setting_constitution);
			break;
		case R.id.button_constitution_next:   //layout4
			mSettingViewPager.setCurrentItem(setting_address);
			break;
		case R.id.button_certificate_up:   //layout5
			mSettingViewPager.setCurrentItem(setting_age_wei_hei);
			break;
		case R.id.button_certificate_next:   //layout5
			if(mETCertiNum.getText().toString().length() == 0){
				mETCertiNum.setError(getString(R.string.AzCertificateNumNotice));
				return;
			}
			mSettingViewPager.setCurrentItem(setting_address);
		    break;
		case R.id.button_address_up:   //layout6
			mSettingViewPager.setCurrentItem(setting_certificate);
		    break;
		case R.id.button_address_next:   //layout6
			mSettingViewPager.setCurrentItem(setting_emergent);
		    break;
		    
		case R.id.button_emergent_up:   //layout7
			mSettingViewPager.setCurrentItem(setting_address);			
	        break;
		case R.id.button_emergent_next:   //layout7
			mSettingViewPager.setCurrentItem(setting_insurance);
	        break;
	        
		case R.id.button_insurance_up:   //layout8
			mSettingViewPager.setCurrentItem(setting_emergent);
	        break;
		case R.id.button_insurance_next:   //layout8
			mSettingViewPager.setCurrentItem(setting_datatype_remark);
	        break;
	        
		case R.id.button_datatype_up:   //layout9
			mSettingViewPager.setCurrentItem(setting_insurance);
	        break;
		case R.id.button_datatype_next:   //layout9
			mSettingViewPager.setCurrentItem(setting_disease);
	        break;
		case R.id.button_disease_up:   //layout10
			mSettingViewPager.setCurrentItem(setting_datatype_remark);
	        break;
		case R.id.button_disease_next:   //layout10
			setDiseaseInPut();
			if(mStrDisease==""){
				return;
			}
			mSettingViewPager.setCurrentItem(setting_insurance_items);
	        break;
		case R.id.button_insu_item_up:  //layout11
			mSettingViewPager.setCurrentItem(setting_disease);
	        break;
		case R.id.button_insu_item_inputok:  //layout11
			if(mETName.getText().toString().length() == 0){
				mETName.setError(getString(R.string.AzNameNotice));
				mSettingViewPager.setCurrentItem(0);
				return;
			}
			if(mETCertiNum.getText().toString().length() == 0){
				mETCertiNum.setError(getString(R.string.AzCertificateNumNotice));
				mSettingViewPager.setCurrentItem(setting_certificate);
				return;
			}
			if(!setDiseaseInPut())
			{
				mSettingViewPager.setCurrentItem(setting_disease);
				return;
			}
			if(!setInsurItemsInPut()){
				return;
			}
			SharedPrefCommit();
	        break;
  		}
		Log.i(TAG, "Exit onClick");		
    }
	public boolean setDiseaseInPut(){
		if(mCBoxDisease11.isChecked()){
			mStrDisease = "11,";
        }
        else{
        	mStrDisease = (mCBoxDisease01.isChecked()? "01,":"")
        			+ (mCBoxDisease02.isChecked()? "02,":"")
        			+ (mCBoxDisease03.isChecked()? "03,":"")
        			+ (mCBoxDisease04.isChecked()? "04,":"")
        			+ (mCBoxDisease05.isChecked()? "05,":"")
        			+ (mCBoxDisease06.isChecked()? "06,":"")
        			+ (mCBoxDisease07.isChecked()? "07,":"")
        			+ (mCBoxDisease08.isChecked()? "08,":"")
        			+ (mCBoxDisease09.isChecked()? "09,":"")
        			+ (mCBoxDisease10.isChecked()? "10,":"")
        			+ (mCBoxDisease12.isChecked()? "12,":"");
        }
		if(mStrDisease==""){
			Toast.makeText(this, getString(R.string.AzDiseaseTpyeNotice), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}
	public boolean setInsurItemsInPut(){
		if(mCBoxInsurItems99.isChecked()){
        	mStrInsurItems = "01,02,03,04,05,06,07,08,";
        }
        else{
        	mStrInsurItems = (mCBoxInsurItems01.isChecked()? "01,":"")+(mCBoxInsurItems02.isChecked()? "02,":"")+
        					(mCBoxInsurItems03.isChecked()? "03,":"")+(mCBoxInsurItems04.isChecked()? "04,":"")+
        					(mCBoxInsurItems05.isChecked()? "05,":"")+(mCBoxInsurItems06.isChecked()? "06,":"")+
        					(mCBoxInsurItems07.isChecked()? "07,":"")+(mCBoxInsurItems08.isChecked()? "08,":"");
        }
		if(mStrInsurItems == ""){
		    Toast.makeText(this, getString(R.string.AzInsuranceTpyeNotice), Toast.LENGTH_LONG).show();
		    return false;
		}
		return true;
	}
	public boolean setNamePhoneInPut(){
		if(mETName.getText().toString().length() == 0){
			mETName.setError(getString(R.string.AzNameNotice));
			return false;
		}
		if(mETPhone.getText().toString().length() == 0){
			mETPhone.setError(getString(R.string.AzPhoneNotice));
			return false;
		}
		return true;
	}
	public boolean setCertiInPut(){
		if(mETCertiNum.getText().toString().length() == 0){
			mETCertiNum.setError(getString(R.string.AzCertificateNumNotice));
			return false;
		}
		return true;
	}
	public void UpOk(){
  		Log.i(TAG, "Enter UpOk");	
	
	    new AlertDialog.Builder(this).setTitle(getString(R.string.AzInformationNotice)).setMessage(getString(R.string.AzInfoUpOK)).setPositiveButton(getString(R.string.azconfirm), new DialogInterface.OnClickListener() {		
		
		    public void onClick(DialogInterface dialoginterface, int i) {
			    finish();
		    }
	    }).setNegativeButton(getString(R.string.azcancel), new DialogInterface.OnClickListener() {
		
		    public void onClick(DialogInterface dialoginterface, int i) {
			    dialoginterface.dismiss();			
		    }
	    } ).show();
  		Log.i(TAG, "Exit UpOk");	
	
    }
	private void UpFail(){
  		Log.i(TAG, "Enter UpFail");			
	    new AlertDialog.Builder(this).setTitle(getString(R.string.AzInformationNotice)).setMessage(getString(R.string.AzInfoUpErr)).setNegativeButton(getString(R.string.azcancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialoginterface, int i) {
			    // TODO Auto-generated method stub
			    dialoginterface.dismiss();
		    }
	    }).show();
  		Log.i(TAG, "Exit UpFail");					
    }	
	

	private void SharedPrefCommit() {
  		Log.i(TAG, "Enter SharedPrefCommit");		
	    new AsyncTask<Void, Void, String>() {
		    protected String doInBackground(Void... params) {				
		        try {
		        	SharedPreferences.Editor mEditor = mPerferences.edit();
		        	
			        mEditor.putString("updateCmd","TSCMD4");  //��������4���ϴ��ͻ���Ϣ����
			        mEditor.putString("edit_name", mETName.getText().toString());
			        mEditor.putString("edit_phone",mETPhone.getText().toString());
					Log.i("onCreate", "strName = " + mETName.getText().toString());
					Log.i("onCreate", "strPhone = " + mETPhone.getText().toString());
			
			        mEditor.putString("selectsex_key",(mRadioButtonSex.getText().toString()==null)? "" 
			        		: mRadioButtonSex.getText().toString());						
					
			        mEditor.putString("edit_age", mETAge.getText().toString());;//����
			        mEditor.putString("edit_height", mETHeight.getText().toString());//��� 
			        mEditor.putString("edit_weight", mETWeight.getText().toString());//����			     
			        
			        mEditor.putString("spinner_constitution", (mSpinnerConstitution.getSelectedItem()==null)? "" 
			        		: mSpinnerConstitution.getSelectedItem().toString());
			        
			        
			        mEditor.putString("spinner_certificate_type",(mSpinnerCertiType.getSelectedItem()==null)? "" 
			    		 : mSpinnerCertiType.getSelectedItem().toString()); 			    
			        mEditor.putString("edit_certificate_num",mETCertiNum.getText().toString());			    
			     
			   
			        mEditor.putString("spinner_address_province",(mSpinnerAddrPro.getSelectedItem()==null)? "" 
			    		 : mSpinnerConstitution.getSelectedItem().toString());
			    
			        mEditor.putString("edit_address_city",mETAddrCity.getText().toString()); 			   
			        mEditor.putString("edit_address",mETAddrInfo.getText().toString());  			     
					
			 
			        mEditor.putString("edit_emergent_name",mETEmerName.getText().toString());  			 
			        mEditor.putString("edit_emergent_phone",mETEmerName.getText().toString());  
			     
			        mEditor.putString("button_insurance_star",mBTInsurStar.getText().toString()); 			
			        mEditor.putString("button_insurance_end",mBTInsurEnd.getText().toString());   
		
			        mEditor.putString("spinner_datatype",(mSpinnerDataType.getSelectedItem()==null)? "" 
			        		: mSpinnerDataType.getSelectedItem().toString());
			        mEditor.putString("edit_remark",mETRemark.getText().toString());			     
			
			        Log.i(TAG, "SharedPrefCommit mStrDisease = " + mStrDisease);
			        Log.i(TAG, "SharedPrefCommit mStrInsurItems = " + mStrInsurItems);
			        
			        mEditor.putString("diseaseTpye_key", mStrDisease);//����״��
			        mEditor.putString("insuranceitems_key",mStrInsurItems);		
			        mEditor.commit();
					do_SendInfo();			  
			        return getString(R.string.Succe);			
		        } catch (Exception e) {				
		        	e.printStackTrace();		   
		        }						   
		        return getString(R.string.False);	    
		    }
		
		    protected void onPreExecute() {			
		    	dialog.show();			
		    	super.onPreExecute();		
		    }		
		
		    protected void onPostExecute(String result) {			
		    	dialog.dismiss();			
		    	if(result == getString(R.string.Succe)){				
		    		if(do_sendInfoFlag){					
		    			UpOk();					
		    			//Toast.makeText(SettingActivity.this, "�ϴ���ݳɹ�", Toast.LENGTH_LONG).show();				
		    		}				
		    		else{					
		    			UpFail();					
		    			//Toast.makeText(SettingActivity.this, "�ϴ����ʧ��", Toast.LENGTH_LONG).show();				
		    		}			
		    	}else {				
		    		UpFail();				
		    		//Toast.makeText(SettingActivity.this, "�ϴ����ʧ��", Toast.LENGTH_LONG).show();			
		    	}			
		    	super.onPostExecute(result);		
		    }		
	    }.execute();
	}
	
	private void do_SendInfo()
	{	
		Log.i(TAG, "Enter do_SendInfo");
		/*if(!ConnectState(this)){
		 * 					
		 * 					Log.i("life", "����ر�");
		 * 					openAPN();
		 * 				}*/
		
		/*��rootȨ�޿���ʹ�ô�OpenGprs�������������*/
		/*OpenGprs();*/
		
		/*try {
		 * 					OpenData(this);
		 * 				} catch (Exception e1) {
		 * 					// TODO Auto-generated catch block
		 * 					e1.printStackTrace();
		 * 				}*/
		/*try {
		 * 					setMobileDataEnabled(this, true);
		 * 				} catch (Exception e1) {
		 * 					// TODO Auto-generated catch block
		 * 					e1.printStackTrace();
		 * 				}*/
		
		String LoginURIString = getString(R.string.PersonInfo);//"http://61.143.124.173:8080/io/PersonInfo.aspx";
		/*����HTTP Post����*/
		HttpPost httpRequest = new HttpPost(LoginURIString); 
		//Post�������ͱ���������NameValuePair[]���鴢��
		List <NameValuePair> params = new ArrayList <NameValuePair>(); 

		params.add(new BasicNameValuePair("imei_key", ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId()));
		params.add(new BasicNameValuePair("updateCmd", "TSCMD4"));
		params.add(new BasicNameValuePair("name_key", mETName.getText().toString())); 
		params.add(new BasicNameValuePair("phoneNum_key",mETPhone.getText().toString()));
		
		String selectsex= mRadioButtonSex.getText().toString();
		if(selectsex == null){
			selectsex="";
		}else{
			if(selectsex.equals(getString(R.string.Boy))){
				selectsex="01";
			}else if(selectsex.equals(getString(R.string.Girl))){
				selectsex="02";
			}else{
				selectsex="";
				}
			}
		params.add(new BasicNameValuePair("selectsex_key",selectsex));
		
		params.add(new BasicNameValuePair("age_key", mETAge.getText().toString()));//����
		
		params.add(new BasicNameValuePair("height_key", mETHeight.getText().toString()));//����
		         
		params.add(new BasicNameValuePair("weight_key", mETWeight.getText().toString()));//��� 
		         
		         
		String certificate =mSpinnerCertiType.getSelectedItem().toString();
		if(certificate == null){
			certificate="";
		}else{
			if(certificate.equals(getString(R.string.IdCard))){
				certificate="01";
			}else if(certificate.equals(getString(R.string.Passport))){
				certificate="02";				
			}else if(certificate.equals(getString(R.string.Armyman))){
			    certificate="03";
			}else if(certificate.equals(getString(R.string.Driver))){
			    certificate="04";
			}else if(certificate.equals(getString(R.string.Other))){
			    certificate="05";
			}else {
			    certificate="";
			}
		}
	    
		params.add(new BasicNameValuePair("certificateType_key",certificate)); 
		params.add(new BasicNameValuePair("certificateNum_key",mETCertiNum.getText().toString())); 
				 
		params.add(new BasicNameValuePair("province_key",(mSpinnerAddrPro.getSelectedItem()==null)? "" 
			    		 : mSpinnerAddrPro.getSelectedItem().toString()));
		params.add(new BasicNameValuePair("city_key",mETAddrCity.getText().toString()));  
		params.add(new BasicNameValuePair("address_key",mETAddrInfo.getText().toString()));
				 
		params.add(new BasicNameValuePair("emergencyPerson_key",mETEmerName.getText().toString()));  
		params.add(new BasicNameValuePair("emergencyContact_key",mETEmerPhone.getText().toString())); 
				 
		params.add(new BasicNameValuePair("insuranceStart_key",mBTInsurStar.getText().toString())); 
		params.add(new BasicNameValuePair("insuranceEnd_key",mBTInsurEnd.getText().toString()));  
				 
		params.add(new BasicNameValuePair("insurance",mStrInsurItems));  
		         
		String datatype= mSpinnerDataType.getSelectedItem().toString();
		if(datatype == null){
			datatype="";
		}else{
			if(datatype.equals(getString(R.string.NewInfo))){
				datatype="01";
			}else if(datatype.equals(getString(R.string.UpData))){
			    datatype="02";
			}else if(datatype.equals(getString(R.string.Add))){
				datatype="03";
			}else if(datatype.equals(getString(R.string.Decrease))){
				datatype="04";
			}else {
				datatype="";
			}
		}
		         
		params.add(new BasicNameValuePair("dataTyp_key",datatype));  
		params.add(new BasicNameValuePair("remark_key",mETRemark.getText().toString())); 
		         
		params.add(new BasicNameValuePair("diseaseTpye", mStrDisease));//����״��
		         
		         
		String constitution = mSpinnerConstitution.getSelectedItem().toString();
		if(constitution == null){
			constitution="";
		}else{
			if(constitution.equals(getString(R.string.AType))){
				constitution="01";
			}else if(constitution.equals(getString(R.string.BType))){
				constitution="02";
			}else if(constitution.equals(getString(R.string.CType))){
				constitution="03";
			}else if(constitution.equals(getString(R.string.DType))){
				constitution="04";
			}else if(constitution.equals(getString(R.string.EType))){
				constitution="05";
			}else if(constitution.equals(getString(R.string.FType))){
				constitution="06";
			}else if(constitution.equals(getString(R.string.GType))){
				constitution="07";
			}else if(constitution.equals(getString(R.string.HType))){
			    constitution="08";
			}else if(constitution.equals(getString(R.string.IType))){					
				constitution="09";
			} else {
			    constitution="";
			}
		}
	      
		params.add(new BasicNameValuePair("constitution_key", constitution));
		         
		try 
		{ 
			/*����HTTP request*/
			httpRequest.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8)); 
			/*ȡ��HTTP response*/
			HttpResponse httpResponse = new DefaultHttpClient().execute(httpRequest); 
			/*��״̬��Ϊ200 ok*/
			if(httpResponse.getStatusLine().getStatusCode() == 200)  
			{ 
				/*ȡ����Ӧ�ַ�*/
				String strResult = EntityUtils.toString(httpResponse.getEntity()); 
				Pattern p = Pattern.compile("true"); 
				Matcher m = p.matcher(strResult); 
				while(m.find())
				{ 
					do_sendInfoFlag = true;
				}
			} else { 
				do_sendInfoFlag = false;
			} 
		} 
		catch (ClientProtocolException e) 
		{  
			do_sendInfoFlag = false;
			e.printStackTrace(); 
		} 
		catch (IOException e) 
		{  
			do_sendInfoFlag = false;
			e.printStackTrace(); 
		} 
		catch (Exception e) 
		{  
			do_sendInfoFlag = false;
			e.printStackTrace();  
		}    
	    Log.i(TAG, "Exit do_SendInfo");		
	}


	
	protected void onRestart() {
  		Log.i(TAG, "Enter onRestart");
	    // TODO Auto-generated method stub
	    super.onRestart();
    }


    protected void onDestroy() {
  	    Log.i(TAG, "Enter onDestroy");
	    super.onDestroy();
    }


    protected void onPause() {
  		Log.i(TAG, "Enter onPause");	
	    super.onPause();
    }

    protected void onResume() {
  		Log.i(TAG, "Enter onResume");	
        super.onResume();
    }
     
    protected Dialog onCreateDialog(int id) {  
    	Log.i(TAG, "Enter onCreateDialog");
    	switch (id) {
    	case R.id.button_insurance_star:
    		return mDatePickerDialogStar;
    	case R.id.button_insurance_end:
    		return mDatePickerDialogEnd;
		}
		return null; 
	}
    protected void onPrepareDialog(int id, Dialog dialog) { 
		Log.i(TAG, "Enter onPrepareDialog");
		switch (id) {  
		case R.id.button_insurance_star:  
			((SettingDateDialog) dialog).updateDate(mDateBufInsurStar.YearValue, 
    				mDateBufInsurStar.MonthValue, mDateBufInsurStar.DayOfMonthvalue);
			break;
			case R.id.button_insurance_end:
			((SettingDateDialog) dialog).updateDate(mDateBufInsurEnd.YearValue, 
    				mDateBufInsurEnd.MonthValue, mDateBufInsurEnd.DayOfMonthvalue);
			break;
		}
	} 
    private class SettingPagerAdapter extends PagerAdapter {
	    	
		public void destroyItem(View arg0, int arg1, Object arg2) {
			Log.d(TAG, "destroyItem arg1 = " + String.valueOf(arg1));
			((ViewPager) arg0).removeView(mSettingListViews.get(arg1));
			
		}

		public void finishUpdate(View arg0) {
			Log.d(TAG, "finishUpdate arg0  ");
			/*
			if(!mUpdatePagerFlag){
				mSettingViewPager.setCurrentItem(mCurrViewPosition);
			}
			*/
		}

		public int getCount() {
			Log.d("TAG", "getCount");
			return mSettingListViews.size();
		}

		@Override
		public Object instantiateItem(View arg0, int arg1) {
			Log.d(TAG, "instantiateItem arg1 = " + String.valueOf(arg1));
			/*
			if(!mUpdatePagerFlag && arg1 > 0)
			{
				mSettingViewPager.setCurrentItem(mCurrViewPosition);
				mUpdatePagerFlag = true;
				return mSettingListViews.get(arg1-1);
			}
			*/
			
			((ViewPager) arg0).addView(mSettingListViews.get(arg1), 0);
			return mSettingListViews.get(arg1);	
		}

		public boolean isViewFromObject(View arg0, Object arg1) {
			Log.d("TAG", "isViewFromObject");
			return arg0 == (arg1);
			//return false;
			//return arg0 == mSettingListViews.get(arg1);
		}

		public void restoreState(Parcelable arg0, ClassLoader arg1) {
			Log.d("TAG", "restoreState");
		}
		public Parcelable saveState() {
			Log.d("TAG", "saveState");
			return null;
		}

		public void startUpdate(View arg0) {
			Log.d("TAG", "startUpdate");
		}
	}
}
