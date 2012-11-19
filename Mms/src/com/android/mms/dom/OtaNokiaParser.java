
package com.android.mms.dom;

import com.android.mms.transaction.OtaConfigVO;

import android.util.Log;

import java.util.List;
public class OtaNokiaParser extends OtaParser {
    private static final String TAG = "OtaNokiaParser";
    private OtaConfigVO oc =  new OtaConfigVO();
    public OtaNokiaParser(byte[] pushDataStream, int mimeType) {
        super(pushDataStream, mimeType);

        /*
         * set the object to parse OTA Nokia data
         */
        mParser.setTagTable(0, NOKIA_TAG_TOKENS);
        mParser.setTagTable(1, NOKIA_TAG_TOKENS);
        mParser.setAttrStartTable(0, NOKIA_ATTRIBUTE_START_TOKENS);
        mParser.setAttrStartTable(1, NOKIA_ATTRIBUTE_START_TOKENS);
        mParser.setAttrValueTable(0, null);
        mParser.setAttrValueTable(1, null);
        oc.setValue(OtaConfigVO.APN_TYPE, "default");
    }

    void elementParser(String tagName) {

        int attrCount = mParser.getAttributeCount();
        String attrName = null;
        String attrValue = null;

        if (tagName.equalsIgnoreCase(NOKIA_TAG_TOKENS[0])) {
            return;
        } else if (tagName.equalsIgnoreCase(NOKIA_TAG_TOKENS[1])) {
            if (attrCount >= 1) {
                currentTagName = mParser.getAttributeValue(0);
            } else {
                currentTagName = "";
            }
            Log.d(LOG_TAG, "type="+mParser.getAttributeValue(0));
            if (currentTagName.equals(NOKIA_TAG_TYPE[1]) && attrCount >= 2) {// URL
                                                                             // homepage
                oc.setValue(OtaConfigVO.HOME_PAGE, mParser.getAttributeValue(1));
                Log.d(LOG_TAG, "value="+mParser.getAttributeValue(1));
            } else if (currentTagName.equals(NOKIA_TAG_TYPE[2]) && attrCount >= 2) {// MMSURL
                oc.setValue(OtaConfigVO.MMSC, mParser.getAttributeValue(1));
                oc.setValue(OtaConfigVO.APN_TYPE, "mms");
                Log.d(LOG_TAG, "value="+mParser.getAttributeValue(1));
            } else if (currentTagName.equals(NOKIA_TAG_TYPE[4])) {// BookMark
                bmName = "";
                bmUrl = "";
                bookMarkNum++;
            }
        } else if (tagName.equalsIgnoreCase(NOKIA_TAG_TOKENS[2])) {
            if (attrCount >= 2) {
                attrName = mParser.getAttributeValue(0);
                attrValue = mParser.getAttributeValue(1);
            } else {
                return;
            }
            Log.d(LOG_TAG, "  "+attrName + "="+attrValue);
            if (currentTagName.equals(NOKIA_TAG_TYPE[0])) {// ADDRESS
                if (attrName.equals("PPP_AUTHTYPE")) {
                    oc.setValue(OtaConfigVO.AUTH_TYPE, attrValue);
                } else if (attrName.equals("PPP_AUTHNAME")) {
                    oc.setValue(OtaConfigVO.USER_NAME, attrValue);
                } else if (attrName.equals("PPP_AUTHSECRET")) {
                    oc.setValue(OtaConfigVO.PWD, attrValue);
                } else if (attrName.equals("PROXY")) {
                    oc.setValue(OtaConfigVO.PROXY, attrValue);
                } else if (attrName.equals("PORT")) {
                    oc.setValue(OtaConfigVO.PORT, attrValue);
                } else if (attrName.equals("GPRS_ACCESSPOINTNAME")) {
                    Log.v(TAG, "APN attrValue=" + attrValue);
                    oc.setValue(OtaConfigVO.APN, attrValue);
                }
            } else if (currentTagName.equals(NOKIA_TAG_TYPE[3])) {// NAME
                oc.setValue(OtaConfigVO.NAME, attrValue);
            } else if (currentTagName.equals(NOKIA_TAG_TYPE[4])) {// BOOKMARK
                if ("NAME".equals(attrName)) {
                    bmName = attrValue;
                } else if ("URL".equals(attrName)) {
                    bmUrl = attrValue;
                }

                if(!"".equals(bmUrl) && !"".equals(bmName)){
                    // Nokia browser settings first bookmark is homepage
                    if (otaMimeType == OTA_NOKIA_DATA1 && bookMarkNum == 1) {
                        if (oc.getValue(OtaConfigVO.HOME_PAGE) == null) {
                            oc.setValue(OtaConfigVO.HOME_PAGE, bmUrl);
                        }
                    }
                    // Nokia browser settings other bookmark is bookmark
                    else if ((otaMimeType == OTA_NOKIA_DATA1 && bookMarkNum > 1)
                            || (otaMimeType == OTA_NOKIA_DATA2)) {// Nokia  Browser bookmarks
                        oc.bmList.add(new OtaConfigVO.OtaBookMark(bmName, bmUrl));
                    } else {
                        return;
                    }
                }

            } else if (currentTagName.equals(NOKIA_TAG_TYPE[5])) {// ID
                // do nothing
            } else {
                Log.i(LOG_TAG, "Unknown tag = " + mParser.getAttributeName(0));
            }

            Log.i(LOG_TAG, "attrName = " + attrName + ", attrValue =" + attrValue);
        } else {
            Log.i(LOG_TAG, "Unknown tag = " + tagName);
        }

    }

    @Override
    public int getParseData(){
        if(oc.getValue(OtaConfigVO.NAME)!=null && !oc.getValue(OtaConfigVO.NAME).trim().equals("")){
            oc.dataFlag=OtaConfigVO.NOKIA_DATA;
            //mms setting
            if("mms".equals(oc.getValue(OtaConfigVO.APN_TYPE))){
                oc.setValue(OtaConfigVO.MMSC_PROXY, oc.getValue(OtaConfigVO.PROXY));
                oc.setValue(OtaConfigVO.MMSC_PORT, oc.getValue(OtaConfigVO.PORT));
                oc.setValue(OtaConfigVO.PROXY, "");
                oc.setValue(OtaConfigVO.PORT, "");
            }
            data.add(oc);
        }else if(oc.bmList.size()>0){
            data.add(oc);
        }
        return 0;
    }
    /**
     * ****************************************** ****Nokia OTA TOKENS and
     * Attributes******* ******************************************
     */
    private String bmName="";

    private String bmUrl="";

    private int bookMarkNum = 0;

    /**
     * All tokens are defined for code page 0.
     */
    private static final String[] NOKIA_TAG_TOKENS = {
            "CHARACTERISTIC-LIST", // 0x05
            "CHARACTERISTIC", // 0x06
            "PARM" // 0x07
    };

    private static final String[] NOKIA_TAG_TYPE = {
            "ADDRESS", "URL", "MMSURL", "NAME", "BOOKMARK", "ID"
    };

    private static final String[] NOKIA_ATTRIBUTE_START_TOKENS = {
            "", // 0x05
            "TYPE=ADDRESS", // 0x06
            "TYPE=URL", // 0x07
            "TYPE=NAME", // 0x08
            "", // 0x09
            "", // 0x0A
            "", // 0x0B
            "", // 0x0C
            "", // 0x0D
            "", // 0x0E
            "", // 0x0F
            "NAME", // 0x10
            "VALUE", // 0x11
            "NAME=BEARER", // 0x12
            "name=PROXY", // 0x13
            "name=PORT", // 0x14
            "name=NAME", // 0x15
            "name=PROXY_TYPE", // 0x16
            "name=URL", // 0x17
            "name=PROXY_AUTHNAME", // 0x18
            "name=PROXY_AUTHSECRET", // 0x19
            "name=SMS_SMSC_ADDRESS", // 0x1A
            "name=USSD_SERVICE_CODE", // 0x1B
            "name=GPRS_ACCESSPOINTNAME", // 0x1C
            "name=PPP_LOGINTYPE", // 0x1D
            "name=PROXY_LOGINTYPE", // 0x1E
            "", // 0x1F
            "", // 0x20
            "name=CSD_DIALSTRING", // 0x21
            "name=PPP_AUTHTYPE", // 0x22
            "name=PPP_AUTHNAME", // 0x23
            "name=PPP_AUTHSECRET", // 0x24
            "", // 0x25
            "", // 0x26
            "", // 0x27
            "name=CSD_CALLTYPE", // 0x28
            "name=CSD_CALLSPEED", // 0x29
            "", // 0x2a
            "", // 0x2b
            "", // 0x2c
            "", // 0x2d
            "", // 0x2e
            "", // 0x2f
            "", // 0x30
            "", // 0x31
            "", // 0x32
            "", // 0x33
            "", // 0x34
            "", // 0x35
            "", // 0x36
            "", // 0x37
            "", // 0x38
            "", // 0x39
            "", // 0x3a
            "", // 0x3b
            "", // 0x3c
            "", // 0x3d
            "", // 0x3e
            "", // 0x3f
            "", // 0x40
            "", // 0x41
            "", // 0x42
            "", // 0x43
            "", // 0x44
            "value=GSM/CSD", // 0x45
            "value=GSM/SMS", // 0x46
            "value=GSM/USSD", // 0x47
            "value=IS-136/CSD", // 0x48
            "value=GPRS", // 0x49
            "", // 0x4a
            "", // 0x4b
            "", // 0x4c
            "", // 0x4d
            "", // 0x4e
            "", // 0x4f
            "", // 0x50
            "", // 0x51
            "", // 0x52
            "", // 0x53
            "", // 0x54
            "", // 0x55
            "", // 0x56
            "", // 0x57
            "", // 0x58
            "", // 0x59
            "", // 0x5a
            "", // 0x5b
            "", // 0x5c
            "", // 0x5d
            "", // 0x5e
            "", // 0x5f
            "value=9200", // 0x60
            "value=9201", // 0x61
            "value=9202", // 0x62
            "value=9203", // 0x63
            "value=AUTOMATIC", // 0x64
            "value=MANUAL", // 0x65
            "", // 0x66
            "", // 0x67
            "", // 0x68
            "", // 0x69
            "value=AUTO", // 0x6A
            "value=9600", // 0x6B
            "value=14400", // 0x6C
            "value=19200", // 0x6D
            "value=28800", // 0x6E
            "value=38400", // 0x6F
            "value=PAP", // 0x70
            "value=CHAP", // 0x71
            "value=ANALOGUE", // 0x72
            "value=ISDN", // 0x73
            "value=43200", // 0x74
            "value=57600", // 0x75
            "value=MSISDN_NO", // 0x76
            "value=IPV4", // 0x77
            "value=MS_CHAP", // 0x78
            "", // 0x79
            "", // 0x7a
            "", // 0x7b
            "TYPE=MMSURL", // 0x7C
            "TYPE=ID", // 0x7D
            "NAME=ISP_NAME", // 0x7E
            "TYPE=BOOKMARK" // 0x7F
    };

}
