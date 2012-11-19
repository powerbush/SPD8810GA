/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.contacts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;

import android.widget.TextView;

import com.android.contacts.util.Config;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.PhoneFactory;

import android.os.ServiceManager;
import com.android.internal.telephony.IccConstants;
import android.os.RemoteException;

/**
 * ContactsMemoryActivity.
 */
public class ContactsMemoryActivity extends Activity {
    private static final String LOG_TAG = "ContactsMemoryActivity";


    private TextView mTextViewPhone;
    private TextView mTextViewSim;
   
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        setContentView(R.layout.memory_query);
        setupView();
    }

    private int getSimContactsTotalCount(){

        IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
        int[] array = new int[3];
        int count = 0;
        try{
            array = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
            count = array[2];
        }catch(RemoteException e){
            //ignore it.
        }
        Log.i(LOG_TAG, "getSimContactsCount " + count);
        return count;
    }
    
    //added for dual sim
    private int getSimContactsTotalCount(int phoneId){

        IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
        int[] array = new int[3];
        int count = 0;
        try{
            array = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
            count = array[2];
        }catch(RemoteException e){
            //ignore it.
        }
        Log.i(LOG_TAG, "getSimContactsCount " + count);
        return count;       
    }
     /**
     * Reflect the changes in the layout that force the user to open
     * the keyboard. 
     */
 
    private void setupView() {
        Intent intent = getIntent();
        int simTocalCount = 0;
        int count = 0;
        boolean hasSim = false;

        int phoneCount = intent.getIntExtra("phone_count", 0);


        mTextViewPhone = (TextView)findViewById(R.id.phone_memory);


        hasSim =  intent.getIntExtra("has_sim", 0) == 0 ? false:true;

        if(hasSim){

            if(Config.isMSMS){

                String count1 = intent.getStringExtra("sim_count1"); 
                String count2 = intent.getStringExtra("sim_count2"); 

                if(null!=count1){
                    int simTocalCount1 = getSimContactsTotalCount(0);
                    TextView textViewSim = (TextView)findViewById(R.id.sim_memory);
                    textViewSim.setVisibility(View.VISIBLE);
                    textViewSim.setText(getString(R.string.sim_card1) + ": " +  count1 + "/"+simTocalCount1);
                }

                if(null!=count2){
                    int simTocalCount2 = getSimContactsTotalCount(1);
                    TextView textViewSim2 = (TextView)findViewById(R.id.sim_memory2);
                    textViewSim2.setVisibility(View.VISIBLE);
                    textViewSim2.setText(getString(R.string.sim_card2) + ": " +  count2 + "/"+simTocalCount2);
                }
            }
            else{
                simTocalCount = getSimContactsTotalCount();
                count = intent.getIntExtra("sim_count", 0); 
                mTextViewSim = (TextView)findViewById(R.id.sim_memory);
                mTextViewSim.setVisibility(View.VISIBLE);
                CharSequence nstrs =  getString(R.string.sim_card) + ": " +  count + "/"+simTocalCount;
                mTextViewSim.setText(nstrs);
            }
        }
        CharSequence nstr = null;


        nstr =  getString(R.string.phone) + ": " +  phoneCount ;

        mTextViewPhone.setText(nstr);

    }



   
}

