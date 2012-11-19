package com.az.PersonInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
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
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.az.Main.MainActivity;
import com.az.Main.R;

public class SettingActivity  extends Activity 
      implements View.OnClickListener {
	private static final String TAG = "Aizhuservice";
	
	public static final String FIRST_BOOT_ACTION_SETTING = "com.az.PersonInfo.boot";
	
	//各设置页面次序定义
	public static final int setting_name_phone = 0;
	public static final int setting_sex = 1;
	public static final int setting_age_wei_hei = 2;
	public static final int setting_constitution = 3;
	public static final int setting_certificate = 4;
	public static final int setting_address = 5;
	public static final int setting_emergent = 6;
	public static final int setting_insurance = 7;
	public static final int setting_datatype_remark = 8;
	public static final int setting_disease = 9;
	public static final int setting_insurance_items = 10;
	
	public static final String SETINFO_SUCC = "SUCC";
	public static final String SETINFO_FAIL = "FAIL";
	
	private static final int INVALID = -1;
	
	private static String mAction = null;
	
	//定义ViewPage相关参数
	private ViewPager mSettingViewPager;
	private List<View> mSettingListViews;
	private LayoutInflater mInflater;
	
	private SettingPagerAdapter mSettingPageAdapter;

    //信息录入共11个界面
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
	
	//定义信息录入完成后提示对话框
	private AlertDialog dialog = null;
	
	//个人信息上传 服务器是否成功标志
	boolean do_sendInfoFlag = false;
	
	//本地保存设置信息
	private SharedPreferences mPerferences;
	
	//定义各设置界面组件对象
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
	//private Spinner mSpinnerDataType;
	private EditText mETRemark;
	
	//layout10 setting_disease	
	
	private ListCheckAdapter mDiseaseAdapter;
	private ListView mDiseaseListView;	
	private String mStrDisease; 
	
	//layout11 setting_insurance_items
	private ListCheckAdapter mInsurItemsAdapter;
	private ListView mInsurItemsListView;	
	private String mStrInsurItems; 
	
	//信息录入提示对话框 按钮响应事件
	public boolean onKeyDown(int keyCode, KeyEvent event) {
  		Log.i(TAG, "Enter onKeyDown");	
	
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	if(mAction != null && mAction.equals(FIRST_BOOT_ACTION_SETTING)){
	    		finish();
	    		return true;
	    	}
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
	    
	    try{
		setContentView(R.layout.setting_main);
		mAction = this.getIntent().getStringExtra("setting_action");
	    Log.i(TAG, "SettingActivity::onCreate mAction = " + mAction);
	    
	    //获得本地信息储存对象
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
	    
	    //获得各页面组件对象并初始化
	    //layout1 setting_name_phone
	    mETName = (EditText) layout1.findViewById(R.id.name_key);
		mETPhone = (EditText) layout1.findViewById(R.id.phoneNum_key);
		
		layout1.findViewById(R.id.button_name_next).setOnClickListener(this);
		Log.i("onCreate", "strName = " + mPerferences.getString("name_key", ""));
		Log.i("onCreate", "strPhone = " + mPerferences.getString("name_key", ""));
		mETName.setText(mPerferences.getString("name_key", ""));
		mETPhone.setText(mPerferences.getString("phoneNum_key", ""));
		
		//layout2 setting_sex
		mRadioGroupSex = (RadioGroup) layout2.findViewById(R.id.selectsex_key);
		
		layout2.findViewById(R.id.button_sex_up).setOnClickListener(this);
		layout2.findViewById(R.id.button_sex_next).setOnClickListener(this);
		
		//Log.d(TAG, "sex = " + mPerferences.getString("selectsex_key",""));
		
		if(mPerferences.getString("selectsex_key","").equals(getString(R.string.Girl))){
			mRadioGroupSex.check(R.id.radio_sex_woman);
		} else if(mPerferences.getString("selectsex_key","").equals(getString(R.string.Boy))){
			mRadioGroupSex.check(R.id.radio_sex_man);
		}
		
		//layout3 setting_age_wei_hei
		
		mETAge = (EditText) layout3.findViewById(R.id.age_key);
		mETWeight = (EditText) layout3.findViewById(R.id.height_key);
		mETHeight = (EditText) layout3.findViewById(R.id.weight_key);
		
		layout3.findViewById(R.id.button_weight_up).setOnClickListener(this);
		layout3.findViewById(R.id.button_weight_next).setOnClickListener(this);
		
		mETAge.setText(mPerferences.getString("age_key", ""));
		mETWeight.setText(mPerferences.getString("height_key", ""));
		mETHeight.setText(mPerferences.getString("weight_key", ""));
		
		//layout4 setting_constitution
		
		mSpinnerConstitution = (Spinner) layout4.findViewById(R.id.constitution_key);
		layout4.findViewById(R.id.button_constitution_up).setOnClickListener(this);
		layout4.findViewById(R.id.button_constitution_next).setOnClickListener(this);
		
		String strConstitution = mPerferences.getString("constitution_key","");
		String[] strConstitutionList = getResources().getStringArray(R.array.constitution_entryvalues);
		for(int i = 0; i< strConstitutionList.length; i++){
			if(strConstitution.equals(strConstitutionList[i])){
				mSpinnerConstitution.setSelection(i);
				break;
			}
		}
		
	    //layout5 setting_certificate	
		mSpinnerCertiType = (Spinner) layout5.findViewById(R.id.
				certificateType_key);
		String strCertiType = mPerferences.getString("certificateType_key","");
		String[] strCertiTypeList = getResources().getStringArray(R.array.certificate_entryname);
		for(int i = 0; i< strCertiTypeList.length; i++){
			if(strCertiType.equals(strCertiTypeList[i])){
				mSpinnerCertiType.setSelection(i);
				break;
			}
		}
		
		mETCertiNum = (EditText) layout5.findViewById(R.id.certificateNum_key);
		
		layout5.findViewById(R.id.button_certificate_up).setOnClickListener(this);
		layout5.findViewById(R.id.button_certificate_next).setOnClickListener(this);
		
		mETCertiNum.setText(mPerferences.getString("certificateNum_key", ""));
		//mCertificateTypePreference.setSummary(mPerferences.getString("certificateType_key",""));        
	    
		//layout6 setting_address	
		mSpinnerAddrPro = (Spinner) layout6.findViewById(R.id.
				province_key);
		String strAddrPro = mPerferences.getString("province_key","");
		String[] strAddrProList = getResources().getStringArray(R.array.province_entryname);
		for(int i = 0; i< strAddrProList.length; i++){
			if(strAddrPro.equals(strAddrProList[i])){
				mSpinnerAddrPro.setSelection(i);
				break;
			}
		}
		mETAddrCity = (EditText) layout6.findViewById(R.id.city_key);
		mETAddrInfo = (EditText) layout6.findViewById(R.id.address_key);
		
		mETAddrCity.setText(mPerferences.getString("city_key", ""));
		mETAddrInfo.setText(mPerferences.getString("address_key", ""));
		
		layout6.findViewById(R.id.button_address_up).setOnClickListener(this);
		layout6.findViewById(R.id.button_address_next).setOnClickListener(this);
		
		//layout7 setting_emergent	
		mETEmerName = (EditText) layout7.findViewById(R.id.emergencyPerson_key);
		mETEmerPhone = (EditText) layout7.findViewById(R.id.emergencyContact_key);
		
		mETEmerName.setText(mPerferences.getString("emergencyPerson_key", ""));
		mETEmerPhone.setText(mPerferences.getString("emergencyContact_key", ""));
		
		layout7.findViewById(R.id.button_emergent_up).setOnClickListener(this);
		layout7.findViewById(R.id.button_emergent_next).setOnClickListener(this);
		
		//layout8 setting_insurance
		mBTInsurStar = (Button) layout8.findViewById(R.id.insuranceStart_key);
		mBTInsurEnd = (Button) layout8.findViewById(R.id.insuranceEnd_key);
		    
	    String strStarDay = mPerferences.getString("insuranceStart_key", "");
	    String strEndDay = mPerferences.getString("insuranceEnd_key", "");
	    
		Calendar c = Calendar.getInstance();
		int yearValue = c.get(Calendar.YEAR);
		int monthValue = c.get(Calendar.MONTH);
		int dayOfMonthvalue = c.get(Calendar.DAY_OF_MONTH);
		
		if(strStarDay.equals("")){
			mDateBufInsurStar = new DateBuffer(yearValue, monthValue, dayOfMonthvalue);
		} else {
			mDateBufInsurStar = new DateBuffer(strStarDay);
		}
		
		if(strEndDay.equals("")){
			mDateBufInsurEnd = new DateBuffer(yearValue + 1, monthValue, dayOfMonthvalue);
		} else {
			mDateBufInsurEnd = new DateBuffer(strEndDay);
		}
		
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
        
		mBTInsurStar.setText(mPerferences.getString("insuranceStart_key", ""));
		mBTInsurEnd.setText(mPerferences.getString("insuranceEnd_key", ""));
		
		mBTInsurStar.setOnClickListener(this);
		layout8.findViewById(R.id.button_insurance_up).setOnClickListener(this);
		layout8.findViewById(R.id.button_insurance_next).setOnClickListener(this);
		
		//layout9 setting_datatype_remark
		
		/*
		mSpinnerDataType = (Spinner) layout9.findViewById(R.id.
				dataTyp_key);	
		String strDataType = mPerferences.getString("dataType_entryname","");
		String[] strDataTypeList = getResources().getStringArray(R.array.constitution_entryvalues);
		for(int i = 0; i< strDataTypeList.length; i++){
			if(strDataType.equals(strDataTypeList[i])){
				mSpinnerDataType.setSelection(i);
				break;
			}
		}
		*/
		mETRemark = (EditText) layout9.findViewById(R.id.remark_key);		
		
		mETRemark.setText(mPerferences.getString("remark_key", ""));
		//mSpinnerDataType.set
		
		layout9.findViewById(R.id.button_datatype_up).setOnClickListener(this);
		layout9.findViewById(R.id.button_datatype_next).setOnClickListener(this);
		
		//layout10 setting_disease		
		
		layout10.findViewById(R.id.button_disease_up).setOnClickListener(this);
		layout10.findViewById(R.id.button_disease_next).setOnClickListener(this);
		mStrDisease = mPerferences.getString("diseaseTpye", "");
		
		String [] diseaseList;
        diseaseList = getResources().getStringArray(R.array.disease_entryvalues);
        mDiseaseAdapter = new ListCheckAdapter(this); 
        mDiseaseAdapter.init(diseaseList, setting_disease);
        initDiseaseView();
        mDiseaseListView =(ListView)SettingActivity.this.mSettingListViews.get(setting_disease).findViewById(R.id.list_disease);
        mDiseaseListView.setAdapter(mDiseaseAdapter);
 		
		//layout11 setting_insurance_items
        layout11.findViewById(R.id.button_insu_item_up).setOnClickListener(this);
		layout11.findViewById(R.id.button_insu_item_inputok).setOnClickListener(this);
		
		
		mStrInsurItems = mPerferences.getString("insurance", "");
		String [] insuranceItemsList;
        insuranceItemsList = getResources().getStringArray(R.array.insurance_entryvalues);
        mInsurItemsAdapter = new ListCheckAdapter(this); 
        mInsurItemsAdapter.init(insuranceItemsList, setting_insurance_items);
        initInsurItemsView();
        mInsurItemsListView =(ListView)SettingActivity.this.mSettingListViews.get(setting_insurance_items).findViewById(R.id.list_insuranceitems);
        mInsurItemsListView.setAdapter(mInsurItemsAdapter);
        
		/*
		mCBoxInsurItems01 = (CheckBox) layout11.findViewById(R.id.check_insu_item_01);
		mCBoxInsurItems02 = (CheckBox) layout11.findViewById(R.id.check_insu_item_02);
		mCBoxInsurItems03 = (CheckBox) layout11.findViewById(R.id.check_insu_item_03);
		mCBoxInsurItems04 = (CheckBox) layout11.findViewById(R.id.check_insu_item_04);
		mCBoxInsurItems05 = (CheckBox) layout11.findViewById(R.id.check_insu_item_05);
		mCBoxInsurItems06 = (CheckBox) layout11.findViewById(R.id.check_insu_item_06);
		mCBoxInsurItems07 = (CheckBox) layout11.findViewById(R.id.check_insu_item_07);
		mCBoxInsurItems08 = (CheckBox) layout11.findViewById(R.id.check_insu_item_08);
		mCBoxInsurItems99 = (CheckBox) layout11.findViewById(R.id.check_insu_item_99);
		
		*/ 
		initInsurItemsView();
		Log.i(TAG, "SharedPrefCommit mStrInsurItems = " + mStrInsurItems);

		
		
		mSettingViewPager.setOnPageChangeListener(new OnPageChangeListener() {	
			public void onPageSelected(int arg0) {
				Log.d(TAG, "onPageSelected - " + arg0);
				//View v = mSettingListViews.get(arg0);
				//EditText editText = (EditText) v.findViewById(R.id.editText1);
				//editText.setText("动态设置#" + arg0 + "edittext控件的值");
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
				// 从1到2滑动，在1滑动前调用				
			}
	
	
			public void onPageScrollStateChanged(int arg0) {
				//Log.d(TAG, "onPageScrollStateChanged - " + arg0);
				// 状态有三个0空闲，1是增在滑行中，2目标加载完毕
				
			}
		});
	    }catch (Exception e){
			e.printStackTrace();
		}
		
		//初始化信息录入提示对话框
		dialog = new ProgressDialog(this);
        
		dialog.setTitle(getString(R.string.AzWaiting));
		dialog.setMessage(getString(R.string.AzUpdataIng));
		
	 	Log.i(TAG, "Exit SettingActivity::onCreate");
	 	
	}
    
	public void onClick(View v) {
  		Log.i(TAG, "Enter onClick");
  		switch (v.getId()) {
  		case R.id.insuranceStart_key:
  			Log.i(TAG, "In onClick button_insurance_star");	
  			mBTInsurStarFlag = true;
			mBTInsurEndFlag = false;
			showDialog(R.id.insuranceStart_key);
			break;
  		case R.id.insuranceEnd_key:
  			Log.i(TAG, "In onClick button_insurance_end");	
	  		mBTInsurStarFlag = false;
			mBTInsurEndFlag = true;
			showDialog(R.id.insuranceEnd_key);
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
	
	public void initDiseaseView() {
		if(mStrDisease.indexOf("01") != INVALID)
		{
			mDiseaseAdapter.setSelectedMap(0, true);
		} else {
			if(mStrDisease.indexOf("02") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(1, true);
			}
			if(mStrDisease.indexOf("03") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(2, true);
			}
			if(mStrDisease.indexOf("04") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(3, true);
			}
			if(mStrDisease.indexOf("05") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(4, true);
			}
			if(mStrDisease.indexOf("06") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(5, true);
			}
			if(mStrDisease.indexOf("07") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(6, true);
			}
			if(mStrDisease.indexOf("08") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(7, true);
			}
			if(mStrDisease.indexOf("09") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(8, true);
			}
			if(mStrDisease.indexOf("10") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(9, true);
			}		
			if(mStrDisease.indexOf("11") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(10, true);
			}
			if(mStrDisease.indexOf("12") != INVALID)
			{
				mDiseaseAdapter.setSelectedMap(11, true);
			}
		}
	}
	public void initInsurItemsView() {
	    if(mStrInsurItems.equals("01,02,03,04,05,06,07,08,")){
	    	mInsurItemsAdapter.setSelectedMap(8,true);
	    } else {
	    	if(mStrInsurItems.indexOf("01") != INVALID){
	    		mInsurItemsAdapter.setSelectedMap(0,true);	    
	    	}
	        if(mStrInsurItems.indexOf("02") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(1,true);
	        }
	        if(mStrInsurItems.indexOf("03") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(2,true);     	
	        }
	        if(mStrInsurItems.indexOf("04") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(3,true);      	
	        }
	        if(mStrInsurItems.indexOf("05") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(4,true);     	
	        }
	        if(mStrInsurItems.indexOf("06") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(5,true);
	        }
	        if(mStrInsurItems.indexOf("07") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(6,true);
	        }
	        if(mStrInsurItems.indexOf("08") != INVALID){
	        	mInsurItemsAdapter.setSelectedMap(7,true);
	        }
	    } 
	}
	
	public boolean setDiseaseInPut(){
		Map<Integer, Boolean> isSelected = mDiseaseAdapter.getSelectedMap();
		if(isSelected.get(0)){
			mStrDisease = "01,";
        }
        else{
        	mStrDisease = (isSelected.get(1)? "02,":"")        			
        			+ (isSelected.get(2)? "03,":"")
        			+ (isSelected.get(3)? "04,":"")
        			+ (isSelected.get(4)? "05,":"")
        			+ (isSelected.get(5)? "06,":"")
        			+ (isSelected.get(6)? "07,":"")
        			+ (isSelected.get(7)? "08,":"")
        			+ (isSelected.get(8)? "09,":"")
        			+ (isSelected.get(9)? "10,":"")
        			+ (isSelected.get(10)? "11,":"")
        			+ (isSelected.get(11)? "12,":"");
        			
        }
		if(mStrDisease==""){
			Toast.makeText(this, getString(R.string.AzDiseaseTpyeNotice), Toast.LENGTH_LONG).show();
			return false;
		}
		
		return true;
	}
	public boolean setInsurItemsInPut(){
		Map<Integer, Boolean> isSelected = mInsurItemsAdapter.getSelectedMap();
		if(isSelected.get(isSelected.size() - 1)){
        	mStrInsurItems = "01,02,03,04,05,06,07,08,";
        }
        else{
        	mStrInsurItems = (isSelected.get(0)? "01,":"")+(isSelected.get(1)? "02,":"")+
        					(isSelected.get(2)? "03,":"")+(isSelected.get(3)? "04,":"")+
        					(isSelected.get(4)? "05,":"")+(isSelected.get(5)? "06,":"")+
        					(isSelected.get(6)? "07,":"")+(isSelected.get(7)? "08,":"");
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
  		SharedPreferences.Editor mEditor = mPerferences.edit();
		mEditor.putString("setinfo_flag_key",SETINFO_SUCC);
		mEditor.commit();
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
  		String setFlag = mPerferences.getString("setinfo_flag_key", "");
  		if(setFlag == null || setFlag.equals("")|| !setFlag.equals(SettingActivity.SETINFO_SUCC)){
		    SharedPreferences.Editor mEditor = mPerferences.edit();
			mEditor.putString("setinfo_flag_key",SETINFO_FAIL);
			mEditor.commit();
  		}
		
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
			        mEditor.putString("updateCmd","TSCMD4");  //命令类型4，上传客户信息内容
			        mEditor.putString("name_key", mETName.getText().toString());
			        mEditor.putString("phoneNum_key",mETPhone.getText().toString());
					Log.i(TAG, "strName = " + mETName.getText().toString());
					Log.i(TAG, "strPhone = " + mETPhone.getText().toString());
					
					Log.i(TAG, "selectsex_key = " + mRadioButtonSex.getText().toString());
			
			        mEditor.putString("selectsex_key",(mRadioButtonSex.getText().toString() == null)? "" 
			        		: mRadioButtonSex.getText().toString());		
					
			        mEditor.putString("age_key", mETAge.getText().toString());;//年龄
			        mEditor.putString("height_key", mETHeight.getText().toString());//身高 
			        mEditor.putString("weight_key", mETWeight.getText().toString());//体重			     
			        
			        mEditor.putString("constitution_key", (mSpinnerConstitution.getSelectedItem()==null)? "" 
			        		: mSpinnerConstitution.getSelectedItem().toString());
			        
			        
			        mEditor.putString("certificateType_key",(mSpinnerCertiType.getSelectedItem()==null)? "" 
			    		 : mSpinnerCertiType.getSelectedItem().toString()); 			    
			        mEditor.putString("certificateNum_key",mETCertiNum.getText().toString());			    
			     
			   
			        mEditor.putString("province_key",(mSpinnerAddrPro.getSelectedItem()==null)? "" 
			    		 : mSpinnerAddrPro.getSelectedItem().toString());
			    
			        mEditor.putString("city_key",mETAddrCity.getText().toString()); 			   
			        mEditor.putString("address_key",mETAddrInfo.getText().toString());  			     
					
			 
			        mEditor.putString("emergencyPerson_key",mETEmerName.getText().toString());  			 
			        mEditor.putString("emergencyContact_key",mETEmerName.getText().toString());  
			     
			        mEditor.putString("insuranceStart_key",mBTInsurStar.getText().toString()); 			
			        mEditor.putString("insuranceEnd_key",mBTInsurEnd.getText().toString());   
			        
			        //mEditor.putString("dataTyp_key",(mSpinnerDataType.getSelectedItem()==null)? "" 
			        //		: mSpinnerDataType.getSelectedItem().toString());
			        mEditor.putString("remark_key",mETRemark.getText().toString());			     
			
			        Log.i(TAG, "SharedPrefCommit mStrDisease = " + mStrDisease);
			        Log.i(TAG, "SharedPrefCommit mStrInsurItems = " + mStrInsurItems);
			        
			        mEditor.putString("diseaseTpye", mStrDisease);//身体状况
			        mEditor.putString("insurance",mStrInsurItems);		
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
		    			//Toast.makeText(SettingActivity.this, "上传数据成功", Toast.LENGTH_LONG).show();				
		    		}				
		    		else{			
		    			UpFail();					
		    			//Toast.makeText(SettingActivity.this, "上传数据失败", Toast.LENGTH_LONG).show();				
		    		}			
		    	}else {				
		    		UpFail();				
		    		//Toast.makeText(SettingActivity.this, "上传数据失败", Toast.LENGTH_LONG).show();			
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
		 * 					Log.i("life", "网络关闭");
		 * 					openAPN();
		 * 				}*/
		
		/*有root权限可以使用此OpenGprs方法打开数据连接*/
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
		/*建立HTTP Post联机*/
		HttpPost httpRequest = new HttpPost(LoginURIString); 
		//Post运作传送变量必须用NameValuePair[]数组储存
		List <NameValuePair> params = new ArrayList <NameValuePair>(); 
		params.add(new BasicNameValuePair("imei_key", ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId()));
		params.add(new BasicNameValuePair("updateCmd", "TSCMD4"));
		params.add(new BasicNameValuePair("name_key", mETName.getText().toString())); //姓名
		params.add(new BasicNameValuePair("phoneNum_key",mETPhone.getText().toString())); //电话号码
		
		String selectsex= mRadioButtonSex.getText().toString();
		Log.d(TAG, "selectsex = " + selectsex);
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
		params.add(new BasicNameValuePair("selectsex_key",selectsex)); //性别
		
		params.add(new BasicNameValuePair("age_key", mETAge.getText().toString()));//年龄
		
		params.add(new BasicNameValuePair("height_key", mETHeight.getText().toString()));//体重
		         
		params.add(new BasicNameValuePair("weight_key", mETWeight.getText().toString()));//身高 
		         
		         
		String certificate = mSpinnerCertiType.getSelectedItem().toString();
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
		         
		/*
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
		*/  
		params.add(new BasicNameValuePair("remark_key",mETRemark.getText().toString())); 
		         
		params.add(new BasicNameValuePair("diseaseTpye", mStrDisease));//身体状况
		         
		         
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
			/*发出HTTP request*/
			httpRequest.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8)); 
			/*取得HTTP response*/
			HttpResponse httpResponse = new DefaultHttpClient().execute(httpRequest); 
			/*若状态码为200 ok*/
			if(httpResponse.getStatusLine().getStatusCode() == 200)  
			{ 
				/*取出响应字符串*/
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
    	case R.id.insuranceStart_key:
    		return mDatePickerDialogStar;
    	case R.id.insuranceEnd_key:
    		return mDatePickerDialogEnd;
		}
		return null; 
	}
    protected void onPrepareDialog(int id, Dialog dialog) { 
		Log.i(TAG, "Enter onPrepareDialog");
		switch (id) {  
		case R.id.insuranceStart_key:  
			((SettingDateDialog) dialog).updateDate(mDateBufInsurStar.YearValue, 
    				mDateBufInsurStar.MonthValue, mDateBufInsurStar.DayOfMonthvalue);
			break;
			case R.id.insuranceEnd_key:
			((SettingDateDialog) dialog).updateDate(mDateBufInsurEnd.YearValue, 
    				mDateBufInsurEnd.MonthValue, mDateBufInsurEnd.DayOfMonthvalue);
			break;
		}
	} 
    private class SettingPagerAdapter extends PagerAdapter {
	    	
		public void destroyItem(View arg0, int arg1, Object arg2) {
			//Log.d(TAG, "destroyItem arg1 = " + String.valueOf(arg1));
			((ViewPager) arg0).removeView(mSettingListViews.get(arg1));
			
		}

		public void finishUpdate(View arg0) {
			//Log.d(TAG, "finishUpdate arg0  ");			

		}

		public int getCount() {
			//Log.d("TAG", "getCount");
			return mSettingListViews.size();
		}

		@Override
		public Object instantiateItem(View arg0, int arg1) {
			//Log.d(TAG, "instantiateItem arg1 = " + String.valueOf(arg1));
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
			//Log.d("TAG", "isViewFromObject");
			return arg0 == (arg1);
			//return false;
			//return arg0 == mSettingListViews.get(arg1);
		}

		public void restoreState(Parcelable arg0, ClassLoader arg1) {
			//Log.d("TAG", "restoreState");
		}
		public Parcelable saveState() {
			//Log.d("TAG", "saveState");
			return null;
		}

		public void startUpdate(View arg0) {
			//Log.d("TAG", "startUpdate");
		}
	}
}
