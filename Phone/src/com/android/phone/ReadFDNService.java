/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.database.Cursor;
import android.net.Uri;
import android.content.Context;


public class ReadFDNService extends Service {

    private final static String TAG = "ReadFDNService";

    private final Uri sim1FdnUri = Uri.parse("content://icc/fdn");

    private final Uri sim2FdnUri = Uri.parse("content://icc1/fdn");

    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent,int flags, int startId) {
      Log.i(TAG, "onStartCommand");
      new ReadFDN1Thread().start();
      if (TelephonyManager.getPhoneCount() > 1) {
          new ReadFDN2Thread().start();
    }
      return START_STICKY;
	}

    private class ReadFDN1Thread extends Thread {
        public void run() {
            Log.i(TAG, "clearFdn1");
            FDNInfo.clearFdn(0);
            queryFDN(ReadFDNService.this,0);
        }
    }

    private class ReadFDN2Thread extends Thread {
        public void run() {
            Log.i(TAG, "clearFdn2");
            FDNInfo.clearFdn(1);
            queryFDN(ReadFDNService.this,1);
        }
    }

     /**
     *query fdn list
     * @param context
     * @param uri
     */
    public  void queryFDN(Context context ,int subId) {
        Cursor cur = null;
        Uri fdnUri = sim1FdnUri;
        if (subId == 1){
            fdnUri = sim2FdnUri;
        }
        Log.i(TAG,"queryFDN");
        try {
            do {
                cur = context.getContentResolver().query(fdnUri, new String[] {"name", "number"}, null, null, null);
                if (null != cur && 0 == cur.getCount()) {
                    cur.close();
                    break;
                }
                if (null != cur && 0 < cur.getCount()) {
                    if (cur.moveToFirst()) {
                        do {
                            String number = cur.getString(1);
                            FDNInfo.addFdn(number, subId);
                            Log.i(TAG, cur.getColumnName(0) + ":" + cur.getString(0) + "\n"
                                    + cur.getColumnName(1) + ":" + cur.getString(1));
                        } while (cur.moveToNext());
                        break;
                    }
                }
            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != cur && !cur.isClosed()) {
                cur.close();
            }
        }
    }

}
