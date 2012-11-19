
package com.android.mms.transaction;

import com.android.mms.transaction.OtaConfigVO.BootStarp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;

public class ApnSetting {
    private static final String TAG = "MMS.ApnSetting";

    private static final Uri PREFERAPN_URI = Telephony.Carriers.CONTENT_URI_PREFERAPN_SIM1;

    private static final Uri PREFERAPN_URI_SIM2 = Telephony.Carriers.CONTENT_URI_PREFERAPN_SIM2;

    private static final String APN_ID = "apn_id";

    private static final String APN_ID_SIM2 = "apn_id_sim2";

    public void setApn(Context context, OtaConfigVO data, int phoneId) {
        String numeric = null;
        String mcc = null;
        String mnc = null;
        TelephonyManager tm = TelephonyManager.getDefault(phoneId);
        if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
            numeric = tm.getSimOperator();
            mcc = numeric.substring(0, 3);
            mnc = numeric.substring(3);
        }
        Uri uri = Telephony.Carriers.getContentUri(phoneId);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Telephony.Carriers.NAME, data.getValue(OtaConfigVO.NAME));
        contentValues.put(Telephony.Carriers.NUMERIC, numeric);
        contentValues.put(Telephony.Carriers.MCC, mcc);
        contentValues.put(Telephony.Carriers.MNC, mnc);
        contentValues.put(Telephony.Carriers.APN, data.getValue(OtaConfigVO.APN));
        contentValues.put(Telephony.Carriers.USER, data.getValue(OtaConfigVO.USER_NAME));
        contentValues.put(Telephony.Carriers.PASSWORD, data.getValue(OtaConfigVO.PWD));
        contentValues.put(Telephony.Carriers.SERVER, data.getValue(OtaConfigVO.SERVER));
        contentValues.put(Telephony.Carriers.PROXY, data.getValue(OtaConfigVO.PROXY));
        contentValues.put(Telephony.Carriers.PORT, data.getValue(OtaConfigVO.PORT));
        contentValues.put(Telephony.Carriers.MMSPROXY, data.getValue(OtaConfigVO.MMSC_PROXY));
        contentValues.put(Telephony.Carriers.MMSPORT, data.getValue(OtaConfigVO.MMSC_PORT));
        contentValues.put(Telephony.Carriers.MMSC, data.getValue(OtaConfigVO.MMSC));
        contentValues.put(Telephony.Carriers.TYPE, data.getValue(OtaConfigVO.APN_TYPE));
        if("PAP".equals(data.getValue(OtaConfigVO.AUTH_TYPE))){
            contentValues.put(Telephony.Carriers.AUTH_TYPE, 1);
        }else if("CHAP".equals(data.getValue(OtaConfigVO.AUTH_TYPE))){
            contentValues.put(Telephony.Carriers.AUTH_TYPE, 2);
        }else if(data.getValue(OtaConfigVO.USER_NAME)!=null && !data.getValue(OtaConfigVO.USER_NAME).equals("")){
            contentValues.put(Telephony.Carriers.AUTH_TYPE, 3);
        }else{
            contentValues.put(Telephony.Carriers.AUTH_TYPE, 0);
        }
        contentValues.put(Telephony.Carriers.CURRENT, 1);
        StringBuilder where = new StringBuilder();
        where.append(Telephony.Carriers.NAME)
                .append(" = '")
                .append(data.getValue(OtaConfigVO.NAME)).append("' and ")
                .append(Telephony.Carriers.NUMERIC)
                .append(" = '")
                .append(numeric)
                .append("'");
        Cursor c = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver.update(uri, contentValues, where.toString(), null) == 0) {
                resolver.insert(uri, contentValues);
                Log.v(TAG, where.toString() + ". | add a new setting.");
            } else {
                Log.v(TAG, where.toString()+". | update a new setting.");
            }
            if ("default".equals(data.getValue(OtaConfigVO.APN_TYPE))) {
                String[] col = {
                    "_id"
                };
                c = resolver.query(uri, col, where.toString(), null, null);
                if (c.moveToFirst()) {
                    ContentValues value = new ContentValues();
                    String a = c.getString(0);
                    if (phoneId == 0) {
                        value.put(APN_ID, a);
                        resolver.update(PREFERAPN_URI, value, null, null);
                    } else {
                        value.put(APN_ID_SIM2, a);
                        resolver.update(PREFERAPN_URI_SIM2, value, null, null);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "merge setting error", e);
        } finally {
            if (c != null)
                c.close();
        }
        String homepage = data.getValue(OtaConfigVO.HOME_PAGE);
        if (homepage == null || homepage.trim().equals("")) {
            return;
        }

        Intent hp = new Intent();
        hp.setAction("android.intent.action.HOMEPAGESETTING");
        Bundle b = new Bundle();
        b.putString("homepage", homepage);
        hp.putExtras(b);
        context.sendBroadcast(hp);
    }

    public void dropApn(Context context, BootStarp data, int phoneId) {
        String numeric = null;
        TelephonyManager tm = TelephonyManager.getDefault(phoneId);
        if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
            numeric = tm.getSimOperator();
        }
        Uri uri = Telephony.Carriers.getContentUri(phoneId);
        StringBuilder where = new StringBuilder();
        where.append(Telephony.Carriers.NAME)
                .append(" = '")
                .append(data.name)
                .append("' and ")
                .append(Telephony.Carriers.NUMERIC)
                .append(" = '")
                .append(numeric)
                .append("'");
        try {
            int dropResult = context.getContentResolver().delete(uri, where.toString(), null);
            Log.v(TAG, where.append(". |").append(dropResult).append(" rows be deleted.").toString());
        } catch (Exception e) {
            Log.e(TAG, "drop setting error", e);
        }
    }
}
