
package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.dom.OtaNokiaParser;
import com.android.mms.dom.OtaOmaParser;
import com.android.mms.dom.OtaParser;
import com.android.mms.transaction.ApnSetting;
import com.android.mms.transaction.BookmarkSetting;
import com.android.mms.transaction.OtaConfigVO;
import com.android.mms.transaction.OtaConfigVO.BootStarp;
import com.android.mms.transaction.OtaConfigVO.EMailSetting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ConfirmOTAActivity extends Activity {
    /** Called when the activity is first created. */

    private static final String TAG = "ConfirmOTAActivity";

    private int phoneId = 0;

    private Map<AlertDialog, Intent> dialogMap = new HashMap<AlertDialog, Intent>();

    public static final String CONTENT_MIME_TYPE_B_OTA_OMA = "application/vnd.wap.connectivity-wbxml";

    public static final String CONTENT_MIME_TYPE_B_OTA_NOKIA_SETTINGS = "application/x-wap-prov.browser-settings";

    public static final String CONTENT_MIME_TYPE_B_OTA_NOKIA_BOOKMARKS = "application/x-wap-prov.browser-bookmarks";

    private List<EditText> etList = new ArrayList();

    private static String byteToChar(byte b) {
        StringBuilder result = new StringBuilder();
        int a1 = (b >>> 4);
        result.append("0123456789ABCDEF".charAt((int) (a1 % 16)));
        a1 = (b & 0x0f);
        result.append("0123456789ABCDEF".charAt((int) (a1 % 16)));
        return result.toString();
    }

    private byte[] processImsi(String strImsi) {
        int strLen = strImsi.length();
        if ((strLen % 2) == 0) {
            strImsi = "1" + strImsi + "F";
        } else {
            strImsi = "9" + strImsi;
        }
        char[] charImsi = strImsi.toCharArray();
        int len = charImsi.length / 2;
        byte[] byteImsi = new byte[len];
        for (int i = 0; i < len; i++) {
            int index = i * 2;
            int temp1 = Integer.parseInt(String.valueOf(charImsi[index]));
            int temp0 = Integer.parseInt(String.valueOf(charImsi[index + 1]));
            byteImsi[i] = (byte) ((temp0 << 4) | temp1);
        }
        return byteImsi;
    }

    private int checkPin(byte[] pin, byte[] pushData, String macData) {
        Mac mac;
        try {
            Log.v(TAG, "pin=" + pin+";pushData="+pushData+";macData"+macData+";");
            mac = Mac.getInstance("HmacSHA1");
            Key key = new SecretKeySpec(pin, "HmacSHA1");
            mac.init(key);
            byte[] hashValue = mac.doFinal(pushData);
            StringBuilder result = new StringBuilder();
            for (byte b : hashValue) {
                result.append(byteToChar(b));
            }
            //macData = macData.substring(0, macData.indexOf("00"));
            Log.v(TAG, "pin==" + pin);
            Log.v(TAG, "sec==" + macData);
            String rr = result.toString();
            macData = getHexChar(macData);
            Log.v(TAG, "sec==" + macData);
            Log.v(TAG, "result =" + rr);
            Log.v(TAG, "result =" + macData.equals(rr));
            if (macData.equals(rr)) {
                return 0;
            } else {
                return 1;
            }
        } catch (Exception e) {
            Log.v(TAG, "exception check pin ");
            e.printStackTrace();
            return 2;
        }
    }

    private final OnClickListener mOKListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            // Get raw PDU push-data from the message and parse it
            Intent it = dialogMap.get(dialog);
            byte[] pushData = it.getByteArrayExtra("data");
            String mimeType = it.getStringExtra("mimeType");
            phoneId = it.getIntExtra("phone_id", 0);

            String secflag = it.getStringExtra("secflag");
            String macData = it.getStringExtra("macData");

            if (secflag != null) {
                TelephonyManager tm = TelephonyManager.getDefault(phoneId);
                String strImsi = tm.getSubscriberId();
                Log.v(TAG, "strImsi==:" + strImsi);

                EditText et = etList.get(etList.size() - 1);

                String pin = et.getText().toString();// "1234";
                Log.v(TAG, "strPin==:" + pin);
                byte[] key = null;
                boolean macFlag = false;
                if (secflag.equals("80")) {
                    macFlag = true;
                    key = processImsi(strImsi);
                } else if (secflag.equals("81")) {
                    macFlag = true;
                    key = pin.getBytes();
                } else if (secflag.equals("82")) {
                    macFlag = true;
                    byte[] byteImsi = processImsi(strImsi);
                    byte[] bytePin = pin.getBytes();
                    key = new byte[byteImsi.length + pin.length()];
                    System.arraycopy(key, 0, byteImsi, 0, byteImsi.length);
                    System.arraycopy(key, byteImsi.length, bytePin, 0, bytePin.length);
                } else if (secflag.equals("83")) {
                    macFlag = false;
                } else {
                    macFlag = false;
                }
                if (macFlag && key != null) {
                    int checkResult = checkPin(key, pushData, macData);
                    if(checkResult==1 && secflag.equals("80")){
                        processDialog(4, dialog);
                        return;
                    }else if (checkResult > 0) {
                        processDialog(checkResult, dialog);
                        return;
                    }
                }
            }

            Log.v(TAG, "mimeType==" + mimeType);
            OtaParser parser;
            if (mimeType.equals(CONTENT_MIME_TYPE_B_OTA_OMA)) {
                // Moto OMA
                parser = new OtaOmaParser(pushData, OtaParser.OTA_OMA_DATA);
            } else if (mimeType.equals(CONTENT_MIME_TYPE_B_OTA_NOKIA_SETTINGS)) {
                // Nokia setting
                parser = new OtaNokiaParser(pushData, OtaParser.OTA_NOKIA_DATA1);
            } else if (mimeType.equals(CONTENT_MIME_TYPE_B_OTA_NOKIA_BOOKMARKS)) {
                // Nokia bookmark
                parser = new OtaNokiaParser(pushData, OtaParser.OTA_NOKIA_DATA2);
            } else {
                processDialog(3, dialog);
                return;
            }
            int result=parser.parse();
            if(result==OtaParser.OTA_MSG_INVALIDATE || result==OtaParser.OTA_MSG_ERROR){
                processDialog(3, dialog);
                return;
            }
            List<OtaConfigVO> data = parser.data;
            List<Map> emailList = new ArrayList();
            Intent i = new Intent("android.email.AutoSetup");
            Bundle bb = new Bundle();
            int eamilSize = 0;
            if (data != null) {
                for (OtaConfigVO config : data) {
                    if (config == null) {
                        continue;
                    }
                    if (null != config.getValue(OtaConfigVO.NAME)) {
                        ApnSetting setting = new ApnSetting();
                        for (BootStarp bootstartp : config.bsList) {
                            if (!config.getValue(OtaConfigVO.NAME).equals(bootstartp.name))
                                setting.dropApn(ConfirmOTAActivity.this, bootstartp, phoneId);
                        }
                        String apn = config.getValue(OtaConfigVO.APN);
                        if (null != apn
                                && !apn.equals("")
                                && (config.dataFlag == OtaConfigVO.OMA_W2
                                        || config.dataFlag == OtaConfigVO.OMA_W4 || config.dataFlag == OtaConfigVO.NOKIA_DATA)) {
                            setting.setApn(ConfirmOTAActivity.this, config, phoneId);
                        }
                    }
                    if (config.bmList.size() > 0) {
                        BookmarkSetting bmStting = new BookmarkSetting();
                        bmStting.setBookmark(ConfirmOTAActivity.this, config);
                    }
                    if (config.emList.size() > 0) {
                        for (EMailSetting es : config.emList) {
                            eamilSize++;
                            Bundle tb = new Bundle();
                            tb.putString("accountName", es.accountName);
                            tb.putString("userID", es.userID);
                            tb.putString("pwd", es.pwd);
                            tb.putString("returnAddress", es.returnAddress);
                            tb.putString("webSession", es.webSession);
                            tb.putString("protocol", es.protocol);
                            tb.putString("recvHost", es.recvHost);
                            tb.putString("recvPort", es.recvPort);
                            tb.putString("sendHost", es.sendHost);
                            tb.putString("sendPort", es.sendPort);
                            tb.putBoolean("send", es.send);
                            tb.putBoolean("recv", es.recv);
                            tb.putBoolean("recvSSL", es.recvSSL);
                            tb.putBoolean("sendSSL", es.sendSSL);
                            i.putExtra("email" + eamilSize, tb);
                            Log.d(TAG, "accountName:" + es.accountName);
                            Log.d(TAG, "userID:" + es.userID);
                        }
                    }
                }
            }
            if (eamilSize > 0) {
                i.putExtra("size", eamilSize);
                sendBroadcast(i);
                Log.v(TAG, "sendBroadcast result ok");
            }
            Log.v(TAG, "OTA configure ok!");
            processDialog(0, dialog);
            Intent apnIt = new Intent("com.android.broadcasttest.OTAAPNChangge");
            sendBroadcast(apnIt);
        }
    };

    private void processDialog(int flag, DialogInterface dialog) {
        // setDialogCloseEnable(dialog, false);
        try {
            Field field = dialog.getClass().getSuperclass().getDeclaredField("mShowing");
            field.setAccessible(true);
            if (flag == 1) {
                //mShowing=false,not close the dialog
                field.set(dialog, false);
            } else {
                //mShowing=true,close the dialog
                field.set(dialog, true);
                dialog.dismiss();
                synchronized (dialogMap) {
                    etList.remove(etList.size() - 1);
                    dialogMap.remove(dialog);
                    if (dialogMap.size() == 0)
                        ConfirmOTAActivity.this.finish();
                }
            }
        } catch (Exception e) {

        }

        if (flag == 0) {// process ota message ok
            Toast.makeText(ConfirmOTAActivity.this, R.string.OTAConfiy_Success, Toast.LENGTH_SHORT)
                    .show();
        } else if (flag == 1) {// pin error
            Toast.makeText(ConfirmOTAActivity.this, R.string.ota_pin_error, Toast.LENGTH_SHORT)
                    .show();
        } else if (flag == 2) {// exception
            Toast.makeText(ConfirmOTAActivity.this, R.string.ota_pin_exception, Toast.LENGTH_SHORT)
                    .show();
        } else if (flag == 3) {// error mimetype  // OTA message is invalidate  // OTA message has error
            Toast.makeText(ConfirmOTAActivity.this, R.string.ota_mime_error, Toast.LENGTH_SHORT)
                    .show();
        }else if (flag == 4) {// error mimetype  // OTA message is invalidate  // OTA message has error
            Toast.makeText(ConfirmOTAActivity.this, R.string.ota_imsi_error, Toast.LENGTH_SHORT)
            .show();
}
    }

    private static String getHexChar(String num) {
        String result = "";
        for (int i = 0; i < num.length(); i += 2) {
            result += (char) Integer.parseInt(num.substring(i, i + 2), 16);
        }
        return result;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent it = this.getIntent();
        byte[] header = it.getByteArrayExtra("header");
        AlertDialog.Builder dialog = showConfirmDialog();
        String secflag = "";

        /***
        * Encode security method (SEC) (WAP-183-PROVCONT 5.3)
        * 0x00 = NETWPIN
        * 0x01 = USERPIN
        * 0x02 = USERNETWPIN
        * 0x03 = USERPINMAC
        *   [91] SEC (0x80 | 0x11 = 0x91) (WAP-230-WSP Table 38)
        *   <security-method> The security method (NETWPIN, USERPIN, etc)
        *   [92] MAC (0x80 | 0x12 = 0x92) (WAP-230-WSP Table 38)
        *   <hmac-value> MAC value, always 40 bytes
        *   [00] EOF
        */
        if (header != null && header.length > 0) {
            int length = header.length;
            StringBuilder headerdata = new StringBuilder();
            for (int i = 0; i < length; i++) {
                headerdata.append(byteToChar(header[i]));
            }
            Log.v(TAG, "headerdata:" + headerdata);
            int index = headerdata.indexOf("91");// 91 sec
            length = headerdata.length();

            if (index > 0) {
                String macData = "";
                secflag = headerdata.substring(index + 2, index + 4);
                String macflag = headerdata.substring(index + 4, index + 6);
                if (macflag.equals("92")) {
                    macData = headerdata.substring(index + 6,(index + 6 + 80));
                }
                EditText et = etList.get(etList.size() - 1);
                if (secflag.equals("80")) {
                    // do nothing
                } else if (secflag.equals("81")) {
                    et.setVisibility(EditText.VISIBLE);
                } else if (secflag.equals("82")) {
                    et.setVisibility(EditText.VISIBLE);
                } else {
                    // do nothing
                }
                it.putExtra("secflag", secflag);
                it.putExtra("macData", macData);
                Log.v(TAG, "secflag==:" + secflag);
                Log.v(TAG, "macData==:" + macData);
            }
        }

        synchronized (dialogMap) {
            AlertDialog ad = dialog.create();
            ad.show();
            dialogMap.put(ad, it);
        }
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
    }

    private AlertDialog.Builder showConfirmDialog() {
        Builder dialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) this
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.ota_dialog, null);
        etList.add((EditText) layout.findViewById(R.id.idEtOTAPin));

        dialog.setView(layout);
        dialog.setTitle(R.string.OTAConfig_title).setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(true);
        dialog.setPositiveButton(R.string.yes, mOKListener)
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (dialogMap) {
                            // setDialogCloseEnable(dialog, false);
                            dialogMap.remove(dialog);
                            if (dialogMap.size() == 0)
                                ConfirmOTAActivity.this.finish();
                        }
                    }
                }).setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // setDialogCloseEnable(dialog, false);
                        synchronized (dialogMap) {
                            dialogMap.remove(dialog);
                            if (dialogMap.size() == 0)
                                ConfirmOTAActivity.this.finish();
                        }
                    }
                });
        return dialog;
    }

}
