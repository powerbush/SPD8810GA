package com.az.PersonInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.az.Main.R;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

public class ListCheckAdapter extends BaseAdapter {    
	private static final String TAG = "Aizhuservice";
    private LayoutInflater mInflater; 
    private List<String> mData;    
    private Map<Integer, Boolean> mSelectedMap;
    private Context mContext;
    private Map<Integer, CheckBox> mCBMap;
    private int mDataType;
    
    
    public ListCheckAdapter(Context context) {    
        mInflater = LayoutInflater.from(context); 
        mContext = context;
    }
    public void init(String[] strList, int dataType){
    	mDataType = dataType;
    	mData=new ArrayList<String>();
    	mSelectedMap = new HashMap<Integer, Boolean>();
        mCBMap =  new HashMap<Integer, CheckBox> ();
    	for(int i = 0; i < strList.length; i++){
    		Log.i(TAG, "MyAdapter.init  i= " + String.valueOf(i));
        	mData.add(strList[i]);
        	mSelectedMap.put(i, false);
            CheckBox cBox = new CheckBox(mContext);
        	View convertView = mInflater.inflate(R.layout.setting_list_checkbox, null); 
            cBox = (CheckBox) convertView.findViewById(R.id.selected);
            cBox.setText(mData.get(i));
            convertView.setTag(cBox); 
            //mCBMap.put(i, convertView);
            /*
            cBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {			   
         	   
    		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    		     // TODO Auto-generated method stub    		
    		    	CheckBox cb  = (CheckBox) buttonView;
    		    	int currPosition = 0;
    		    	String strText = cb.getText().toString();
    		    	
    		    	Log.i(TAG, "ListCheckAdapter.onCheckedChanged  strText= " + strText);
    		    	
    		    	for(int i = 0; i < mData.size(); i++){
    		    		if(strText.equals(mData.get(i))){
    		    			
    		    			currPosition = i;
    		    			mSelectedMap.put(i, isChecked);
    		    			Log.i(TAG, "ListCheckAdapter.onCheckedChanged  currPosition= " + String.valueOf(currPosition));
    		    			//Log.i(TAG, "ListCheckAdapter.onCheckedChanged  mCBMap.size()= " + String.valueOf(mCBMap.size()));
    		    			CheckBox cbox = (CheckBox)mCBMap.get(i).getTag();
    		    			cbox.setChecked(isChecked);
    		    			break;
    		    		}
    		    	}
    		    	if(isChecked && currPosition == 0){
    		    		for(int i = 1; i < mData.size(); i++){
    		    			CheckBox cbox = (CheckBox)mCBMap.get(i).getTag();
    		    			cbox.setChecked(false);
    		    			mSelectedMap.put(i, false);
        		    	}
    		    	}else if(isChecked && currPosition != 0){
    		    		CheckBox cbox = (CheckBox)mCBMap.get(0).getTag();
		    			cbox.setChecked(false);
		    			mSelectedMap.put(0, false);
    		    	}
    		    }
    		   });*/
        }
      
        
    }
    
    @Override    
    public int getCount() {    
        return mData.size();    
    }    
    
    @Override    
    public Object getItem(int position) {    
        return null;    
    }    
    
    @Override    
    public long getItemId(int position) {    
        return 0;    
    }    
    
    @Override    
    public View getView(final int position, View view,  ViewGroup parent) {    
    	Log.i(TAG, "MyAdapter.getView position = " + String.valueOf(position));
    	if(view == null){
    		view = mInflater.inflate(R.layout.setting_list_checkbox, null); 
    	}
    	//convertView = (View)mCBMap.get(position);
    	
    	//convertView.setTag(cBox); 
    	CheckBox cbox = (CheckBox)view.findViewById(R.id.selected);
    	cbox.setChecked(mSelectedMap.get(position));
    	cbox.setText(mData.get(position));
    	mCBMap.put(position, cbox);
    	 
    	cbox.setOnClickListener(new View.OnClickListener(){			   
      	   
 		    public void onClick(View v) {
 		     // TODO Auto-generated method stub    		
 		    	Log.i(TAG, "ListCheckAdapter.onCheckedChanged  position= " + String.valueOf(position));
 		    	Log.i(TAG, "ListCheckAdapter.onCheckedChanged  mDataType= " + String.valueOf(mDataType));
 		    	mSelectedMap.put(position, !mSelectedMap.get(position));
 		    	if(mDataType == SettingActivity.setting_disease){
	 		    	if(mSelectedMap.get(position) && position == 0){
	 		    		for(int i = 1; i < mData.size(); i++){
	 		    			mSelectedMap.put(i, false);
	 		    		}
	 		    	}else if(mSelectedMap.get(position) && position != 0){
			    		mSelectedMap.put(0, false);
	 		    	}	 		    	
 		    	} else if(mDataType == SettingActivity.setting_insurance_items){
 		    		if(position == mData.size() - 1 && mSelectedMap.get(position)){
 		    			for(int i =0; i < mData.size() - 1; i++){
	 		    			mSelectedMap.put(i, true);
	 		    		}
 		    		} else if(position != mData.size() -1 && !mSelectedMap.get(position)){
 		    			mSelectedMap.put(mData.size() - 1, false);
 		    		}
 		    	}
 		    	notifyDataSetChanged();
 		    	/*
 		    	Log.i(TAG, "ListCheckAdapter.onCheckedChanged  position= " + String.valueOf(position));
 		    	CheckBox cb  = (CheckBox) buttonView;
 		    	int currPosition = position;
 		    	String strText = cb.getText().toString();
 		    	Log.i(TAG, "ListCheckAdapter.onCheckedChanged  strText= " + strText);
 		    	
 		    	mSelectedMap.put(currPosition, isChecked);
 		    	CheckBox cbox = (CheckBox)mCBMap.get(currPosition);
	    			/*if(cbox!=null){
		    			cbox.setChecked(isChecked);
		    		}*/
 		    	
 		    	/*
 		    	for(int i = 0; i < mData.size(); i++){
 		    		if(strText.equals(mData.get(i))){
 		    			currPosition = i;
 		    			mSelectedMap.put(i, isChecked);
 		    			Log.i(TAG, "ListCheckAdapter.onCheckedChanged  currPosition= " + String.valueOf(currPosition));
 		    			//Log.i(TAG, "ListCheckAdapter.onCheckedChanged  mCBMap.size()= " + String.valueOf(mCBMap.size()));
 		    			CheckBox cbox = (CheckBox)mCBMap.get(i);
 		    			if(cbox!=null){
 			    			cbox.setChecked(isChecked);
 			    		}
 		    			break;
 		    		}
 		    	}
 		    	
 		    	if(isChecked && currPosition == 0){
 		    		for(int i = 1; i < mData.size(); i++){
 		    			CheckBox cbox = (CheckBox)mCBMap.get(i);
 		    			if(cbox!=null){
 		    				cbox.setChecked(false);
 		    			}
 		    			mSelectedMap.put(i, false);
     		    	}
 		    	}else if(isChecked && currPosition != 0){
 		    		CheckBox cbox = (CheckBox)mCBMap.get(0);
 		    		if(cbox!=null){
		    			cbox.setChecked(false);
		    		}
		    		mSelectedMap.put(0, false);
 		    	}
 		    	*/
 		    }

			
 		   });
    	
    	return view;
    	/*if(cBox == null)
    	{
    		cBox = new CheckBox(mContext);
    		mCBMap.put(position, cBox);
    	}
		convertView.setTag(cBox); 
        
    	//CheckBox cBox = null;
    	Log.i(TAG, "MyAdapter.getView position = " + String.valueOf(position));
        //convertView为null的时候初始化convertView。    
        if (convertView == null) {
        	Log.i(TAG, "MyAdapter.getView convertView==null position = " + String.valueOf(position));
        	cBox = new CheckBox(mContext);
        	convertView = mInflater.inflate(R.layout.list, null); 
            //holder.img = (ImageView) convertView.findViewById(R.id.img);    
           // holder.title = (TextView) convertView.findViewById(R.id.title);    
            cBox = (CheckBox) convertView.findViewById(R.id.cb);
            cBox.setId(position);
            mCheckBoxList.add(cBox);
            Log.i(TAG, "MyAdapter.getView mCheckBoxList.size = " + String.valueOf(mCheckBoxList.size()));
            cBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {			   
            	   
    		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    		     // TODO Auto-generated method stub    		
    		    	CheckBox cb  = (CheckBox) buttonView;
    		    	int currPosition = 0;
    		    	String strText = cb.getText().toString();
    		    	for(int i = 0; i < mData.size(); i++){
    		    		if(strText.equals(mData.get(i))){
    		    			Log.i(TAG, "MyAdapter.onCheckedChanged  currPosition= " + String.valueOf(mCheckBoxList.size()));
    		    			currPosition = i;
    		    			mSelectedMap.put(i, isChecked);
    		    			Log.i(TAG, "MyAdapter.onCheckedChanged  currPosition= " + String.valueOf(currPosition));
    		    			Log.i(TAG, "MyAdapter.onCheckedChanged  mCheckBoxList.size()= " + String.valueOf(mCheckBoxList.size()));
    		    			
    		    			if(i < mCheckBoxList.size()){
    		    				mCheckBoxList.get(i).setChecked(isChecked);
    		    			}
    		    			
    		    			break;
    		    		}
    		    	}
    		    	if(isChecked && currPosition == 0){
    		    		for(int i = 1; i < mData.size(); i++){
    		    			if(i < mCheckBoxList.size()){
    		    				mCheckBoxList.get(i).setChecked(false);
    		    			}
        		    		mSelectedMap.put(i, false);        		    		
        		    	}
    		    	}else if(isChecked && currPosition != 0){
    		    		mCheckBoxList.get(0).setChecked(false);    		    	
    		    		mSelectedMap.put(0, false);
    		    	}
    		    }
    		   });
            convertView.setTag(cBox); 
        } else {    
        	cBox = (CheckBox) convertView.getTag();
        }    
        //holder.img.setBackgroundResource((Integer) mData.get(position).get(    
        //        "img"));    
        //holder.title.setText(mData.get(position));
        Log.i(TAG, "MyAdapter.getView position = " + String.valueOf(position) + "mData.get(position) = " + mData.get(position));
        cBox.setChecked(mSelectedMap.get(position));    
        cBox.setText(mData.get(position));
        */
        //return convertView;    
    }   
    public Map<Integer, Boolean> getSelectedMap(){
    	return mSelectedMap;
    }
    
    public void setSelectedMap(int postion, boolean isSelected){
    	mSelectedMap.put(postion, isSelected);
    }
    
    
 }    
