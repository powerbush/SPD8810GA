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

package com.android.phone;


import android.app.TabActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.telephony.PhoneFactory;

/**
 * add a tab of the Cell Broadcast Sms.
 */
public class CellBroadcastSmsSettingTabActivity extends TabActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        if (TelephonyManager.getPhoneCount() < 2) {
            Intent it = new Intent();
            Bundle bundleSim = new Bundle();
            bundleSim.putInt("phoneid", 0);
            it.putExtras(bundleSim);
            it.setClass(this, CellBroadcastSmsSettingActivity.class);
            startActivity(it);
            this.finish();
        }else{
        	addTabView();
        }
    }
    
    private boolean simCardReady(int phoneId){
        TelephonyManager telManager = (TelephonyManager) getSystemService(
                PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, phoneId));
        boolean hasSim = (null != telManager) ? telManager.hasIccCard() : false;
        if (hasSim && telManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
        	return true;
        }else{
        	return false;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (TelephonyManager.getPhoneCount() >= 2) {
        	refreshTabView();
        }
    }
    
    private void addTabView(){
    	TabHost tabHost = getTabHost();

        if(simCardReady(0)){
	        Intent intentSim1 = new Intent();
	        Bundle bundleSim1 = new Bundle();
	        bundleSim1.putInt("phoneid", 0);
	        intentSim1.putExtras(bundleSim1);
	        intentSim1.setClass(this, CellBroadcastSmsSettingActivity.class);
	        tabHost.addTab(tabHost.newTabSpec("tab1").setIndicator("SIM1")
	                .setContent(intentSim1));
        }

        if(simCardReady(1)){
	        Intent intentSim2 = new Intent();
	        Bundle bundleSim2 = new Bundle();
	        bundleSim2.putInt("phoneid", 1);
	        intentSim2.putExtras(bundleSim2);
	        intentSim2.setClass(this, CellBroadcastSmsSettingActivity.class);
	        tabHost.addTab(tabHost.newTabSpec("tab2").setIndicator("SIM2")
	                .setContent(intentSim2));
        }
        
        TabWidget mTabWidget = tabHost.getTabWidget();
        if(mTabWidget.getChildCount() <= 0){
        	Toast.makeText(this, R.string.error_no_active_sim_cb, Toast.LENGTH_SHORT).show();
        	this.finish();
        	return;
        }
        for (int i = 0; i < mTabWidget.getChildCount(); i++) {
            mTabWidget.getChildAt(i).getLayoutParams().height = 60;
            final TextView tv = (TextView) mTabWidget.getChildAt(i)
                    .findViewById(android.R.id.title);
            tv.setTextColor(this.getResources().getColorStateList(
                    android.R.color.white));
            tv.setPadding(0, 0, 0, 16);
            mTabWidget.getChildAt(i).setBackgroundResource(R.drawable.tab_bg);
            }
        }
    
    private void refreshTabView(){
    	TabHost tabHost = getTabHost();
    	if(tabHost != null){
                //wangsl
                tabHost.setCurrentTab(0);
                //wangsl
    		tabHost.clearAllTabs();
    	}
    	addTabView();
    }
}
