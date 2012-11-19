
package com.android.mms.dom;

import com.android.mms.transaction.OtaConfigVO;
import com.android.mms.transaction.OtaConfigVO.BootStarp;
import com.android.mms.transaction.OtaConfigVO.EMailSetting;
import com.android.mms.transaction.OtaConfigVO.OtaBookMark;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OtaOmaParser extends OtaParser {

    private static final String TAG = "OtaOmaParser";

    public OtaOmaParser(byte[] pushDataStream, int mimeType) {
        super(pushDataStream, mimeType);

        /*
         * set the object to parse OTA Moto Oma data
         */
        mParser.setTagTable(0, TAG_TABLE_OTA);
        mParser.setTagTable(1, TAG_TABLE_OTA1);
        mParser.setAttrStartTable(0, ATTR_START_TABLE_OTA);
        mParser.setAttrStartTable(1, ATTR_START_TABLE_OTA1);
        mParser.setAttrValueTable(0, ATTR_VALUE_TABLE_OTA);
        mParser.setAttrValueTable(1, ATTR_VALUE_TABLE_OTA1);
    }

    private String currentTagNameL2;

    private String currentTagNameL3;

    private String currentTagNameL4;

    private List bootStarp = new ArrayList();

    private List pxlogical = new ArrayList();

    private List napdef = new ArrayList();

    private List applist = new ArrayList();

    private String clientIdentity = "";

    private Map currentMapL2;

    private Map currentMapL3;

    private Map currentMapL4;

    public boolean parseNAPDEF(Map napDefMap, OtaConfigVO config) {
        String strBEARER = (String) napDefMap.get("BEARER");
        if (strBEARER != null && strBEARER.equals("GSM-CSD")) {
            return false;
        }
        // APN
        String napAddr = (String) napDefMap.get("NAP-ADDRESS");
        String napType = (String) napDefMap.get("NAP-ADDRTYPE");
        if (napType != null && napAddr != null && napType.equals("APN")) {
            config.setValue(OtaConfigVO.APN, napAddr);
        } else {
            return false;
        }

        // NAPAUTHINFO
        List<Map> napAuth = (List) napDefMap.get("NAPAUTHINFO");
        if (napAuth != null && napAuth.size() > 0) {
            for (Map napAuthObj : napAuth) {
                if (napAuthObj != null) {
                    String AUTHNAME = (String) napAuthObj.get("AUTHNAME");
                    String AUTHSECRET = (String) napAuthObj.get("AUTHSECRET");
                    String AUTH_TYPE = (String) napAuthObj.get("AUTHTYPE");
                    Log.i(LOG_TAG, "AUTHNAME=" + AUTHNAME + ":" + AUTHSECRET + ":" + AUTH_TYPE
                            + ":");
                    if (AUTH_TYPE == null || AUTH_TYPE.contains("PAP")
                            || AUTH_TYPE.contains("CHAP")) {
                        config.setValue(OtaConfigVO.AUTH_TYPE, AUTH_TYPE);
                    } else {
                        continue;
                    }
                    if (AUTHNAME != null && AUTHSECRET != null) {
                        config.setValue(OtaConfigVO.USER_NAME, AUTHNAME);
                        config.setValue(OtaConfigVO.PWD, AUTHSECRET);
                        break;
                    }
                }
            }
        }

        // VALIDITY
        Map validity = (Map) napDefMap.get("VALIDITY");
        if (validity != null) {
            config.setValue(OtaConfigVO.MCC, (String) validity.get("COUNTRY"));
            config.setValue(OtaConfigVO.MNC, (String) validity.get("NETWORK"));
        }
        return true;
    }

    @Override
    public int getParseData() {
        OtaConfigVO config;
        OtaConfigVO mailConfig = new OtaConfigVO();
        mailConfig.dataFlag = OtaConfigVO.OMA_EMAIL;

        if (applist.size() <= 0)
            return OtaParser.OTA_MSG_ERROR;
        boolean mail = false;
        for (int i = 0; i < bootStarp.size(); i++) {
            config = new OtaConfigVO();
            config.dataFlag = OtaConfigVO.OMA_BOOTSP;
            Map bsObj = (Map) bootStarp.get(i);
            config.bsList.add(new BootStarp((String) bsObj.get("NAME"), (String) bsObj
                    .get("PROXY-ID")));
            data.add(config);
        }

        for (int i = 0; i < applist.size(); i++) {
            Map appObj = (Map) applist.get(i);
            String appid = (String) appObj.get("APPID");
            if (appid == null) {
                continue;
            } else if (appid.equals("w2") || appid.equals("w4")) {// w2: Browser
                                                                  // setting;
                                                                  // w4: MMSC;
                config = new OtaConfigVO();
                data.add(config);

                // Application infor: APN type; NAME; MMSC
                if (appid.equals("w2")) {
                    config.dataFlag = OtaConfigVO.OMA_W2;
                    config.setValue(OtaConfigVO.APN_TYPE, "default");
                } else {
                    config.setValue(OtaConfigVO.APN_TYPE, "mms");
                    config.dataFlag = OtaConfigVO.OMA_W4;
                }
                String strName = (String) appObj.get("NAME");
                if (strName != null && !strName.equals("")) {
                    config.setValue(OtaConfigVO.NAME, strName);
                }

                if (appid.equals("w4"))// MMSC
                    config.setValue(OtaConfigVO.MMSC, (String) appObj.get("ADDR"));
                // bookmark
                if (appid.equals("w2")) {
                    Map resource = (Map) appObj.get("RESOURCE");
                    if (resource != null) {
                        config.bmList.add(new OtaBookMark((String) resource.get("NAME"),
                                (String) resource.get("URI")));
                        String homepage = (String) resource.get("STARTPAGE");
                        if (homepage == null || homepage.equals("")) {
                            homepage = (String) appObj.get("ADDR");
                        }
                        config.setValue(OtaConfigVO.HOME_PAGE, homepage);
                    }
                }

                List toProxyList = (List) appObj.get("TO-PROXY");
                List toNapIDList = (List) appObj.get("TO-NAPID");

                boolean findNAPbyName = false;
                if (strName != null && !strName.equals("")
                        && (toProxyList == null || toProxyList.size() <= 0)
                        && (toNapIDList == null || toNapIDList.size() <= 0)) {
                    findNAPbyName = true;
                }

                boolean find = false;
                // PXLOGICAL infor:
                if (toProxyList != null && toProxyList.size() > 0) {
                    for (int k = 0; k < toProxyList.size(); k++) {
                        if (find)
                            break;
                        for (int m = 0; m < pxlogical.size(); m++) {
                            Map logical = (Map) pxlogical.get(m);
                            String toProxy = (String) toProxyList.get(k);
                            if (!toProxy.equals((String) logical.get("PROXY-ID"))) {
                                continue;
                            }
                            String strWspVersion = (String) logical.get("WSP-VERSION");
                            if (strWspVersion != null && strWspVersion.equals("2.0")) {
                                continue;
                            }
                            find = true;
                            String logicalHomepage = (String) logical.get("STARTPAGE");
                            if (appid.equals("w2") && logicalHomepage != null
                                    && !logicalHomepage.equals(""))
                                config.setValue(OtaConfigVO.HOME_PAGE,
                                        (String) logical.get("STARTPAGE"));

                            strName = (String) logical.get("NAME");
                            if (config.getValue(OtaConfigVO.NAME) != null && strName != null
                                    && !strName.equals("")) {
                                config.setValue(OtaConfigVO.NAME, strName);
                            }
                            // PHYSICAL
                            List physicalList = (List) logical.get("PXPHYSICAL");
                            if (physicalList != null && physicalList.size() > 0) {
                                Map physical = (Map) physicalList.get(0);

                                if (physical == null) {
                                    continue;
                                }

                                if (appid.equals("w2"))
                                    config.setValue(OtaConfigVO.PROXY,
                                            (String) physical.get("PXADDR"));
                                else {
                                    config.setValue(OtaConfigVO.MMSC_PROXY,
                                            (String) physical.get("PXADDR"));
                                }
                                Map port = (Map) physical.get("PORT");
                                if (port != null) {
                                    Integer intPort = Integer
                                            .parseInt((String) port.get("PORTNBR"));
                                    if (intPort < 0) {
                                        intPort = 0;
                                    } else if (intPort > 65535) {
                                        intPort = 80;
                                    }
                                    if (appid.equals("w2"))
                                        config.setValue(OtaConfigVO.PORT, intPort.toString());
                                    else
                                        config.setValue(OtaConfigVO.MMSC_PORT, intPort.toString());
                                }

                                // NAPDEF
                                toNapIDList = (List) physical.get("TO-NAPID");
                            }
                            // PXAUTHINFO
                            // Map pxAuth = (Map) logical.get("PXAUTHINFO");
                            // if (pxAuth != null) {
                            // if (pxAuth.get("PXAUTH-ID") == null &&
                            // clientIdentity != null
                            // && !clientIdentity.equals("")) {
                            // config.setValue(OtaConfigVO.USER_NAME,
                            // clientIdentity);
                            // } else if (pxAuth.get("PXAUTH-ID") != null) {
                            // config.setValue(OtaConfigVO.USER_NAME,
                            // (String) pxAuth.get("PXAUTH-ID"));
                            // } else {
                            // break;
                            // }
                            // config.setValue(OtaConfigVO.PWD, (String)
                            // pxAuth.get("PXAUTH-PW"));
                            // }
                            break;
                        }
                    }
                }

                // NAPDEF infro:
                find = false;
                if (findNAPbyName) {
                    for (int j = 0; j < napdef.size(); j++) {
                        Map napDefMap = (Map) napdef.get(j);
                        if (napDefMap == null)
                            continue;
                        String strNapName = (String) napDefMap.get("NAME");
                        if (findNAPbyName && strNapName != null && strNapName.equals(strName)) {
                            if (parseNAPDEF(napDefMap, config))
                                break;
                        }
                    }
                } else if (toNapIDList != null && toNapIDList.size() > 0) {
                    for (int m = 0; m < toNapIDList.size(); m++) {
                        if (find)
                            break;
                        for (int j = 0; j < napdef.size(); j++) {
                            Map napDefMap = (Map) napdef.get(j);
                            if (napDefMap == null)
                                continue;
                            String strNapID = (String) napDefMap.get("NAPID");
                            if ((strNapID != null && strNapID.equals((String) toNapIDList.get(m)))
                                    || ("INTERNET".equals((String) toNapIDList.get(m)) && napDefMap
                                            .get("INTERNET") != null)) {
                                if (parseNAPDEF(napDefMap, config)) {
                                    find = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                // if(config.getValue(OtaConfigVO.APN)==null ||
                // config.getValue(OtaConfigVO.APN).equals(""))
                // return OtaParser.OTA_MSG_ERROR;
            } else if (appid.equals("110") || appid.equals("143")) {// POP3 and
                                                                    // IMAP4,
                                                                    // Mail recv
                                                                    // setting
                mail = true;
                EMailSetting em = mailConfig.findEMS((String) appObj.get("PROVIDER-ID"));
                if (em == null) {
                    em = new EMailSetting();
                    mailConfig.emList.add(em);
                }

                if (em != null && !em.send) {
                    em.send = true;
                    em.PROVIDER_ID = (String) appObj.get("PROVIDER-ID");
                    em.accountName = (String) appObj.get("NAME");
                    if (em.accountName != null && em.accountName.contains("@")) {
                        em.accountName = em.accountName.split("@")[0];
                    }
//                    em.TO_NAPID = (String) appObj.get("TO-NAPID");
//                    em.TO_PROXY = (String) appObj.get("TO-PROXY");
                    em.protocol = appid.equals("110") ? "POP3" : "IMAP4";
                    em.recvPort = appid;

                    Map auth = (Map) appObj.get("APPAUTH");
                    if (auth != null) {
                        em.userID = (String) auth.get("AAUTHNAME");
                        em.pwd = (String) auth.get("AAUTHSECRET");
                    }
                    Map addr = (Map) appObj.get("APPADDR");
                    if (addr != null) {
                        em.recvHost = (String) addr.get("ADDR");
                        if (em.recvHost != null && em.recvHost.startsWith("http://")) {
                            em.recvHost = em.recvHost.substring(7);
                        }
                        Map port = (Map) addr.get("PORT");
                        if (port != null) {
                            em.recvPort = (String) port.get("PORTNBR");
                            if (port.get("SERVICE") != null) {
                                em.recvSSL = true;
                            }
                        }
                    }
                }
            } else if (appid.equals("25")) {// SMTP , Mail Send setting
                mail = true;
                EMailSetting em = mailConfig.findEMS((String) appObj.get("PROVIDER-ID"));
                if (em == null) {
                    em = new EMailSetting();
                    mailConfig.emList.add(em);
                }
                if (em != null && !em.recv) {
                    em.recv = true;
                    em.PROVIDER_ID = (String) appObj.get("PROVIDER-ID");
                    em.returnAddress = (String) appObj.get("FROM");
                    em.sendPort = "25";
                    Map addr = (Map) appObj.get("APPADDR");
                    if (addr != null) {
                        em.sendHost = (String) addr.get("ADDR");
                        if (em.sendHost != null && em.sendHost.startsWith("http://")) {
                            em.sendHost = em.sendHost.substring(7);
                        }
                        Map port = (Map) addr.get("PORT");
                        if (port != null) {
                            em.sendPort = (String) port.get("PORTNBR");
                            if (port.get("SERVICE") != null) {
                                em.sendSSL = true;
                            }
                        }
                    }
                }
            }
        }

        if (mail) {
            data.add(mailConfig);
        }

        return 0;
    }

    /*
     * wap-provisioningdoc L1 characteristic type="PXLOGICAL" L2 characteristic
     * type="PXPHYSICAL" L3 characteristic type="PORT" L4 <parm name="PORTNBR"
     * value="9203"/> L5
     */
    @Override
    void elementParser(String tagName) {
        int attrCount = mParser.getAttributeCount();
        String attrName = null;
        String attrValue = null;
        int depth = mParser.getDepth();

        if (tagName.equalsIgnoreCase(TAG_TABLE_OTA1[1])) {// characteristic
            if (attrCount >= 1) {
                Log.i(LOG_TAG, "characteristic type =  " + mParser.getAttributeValue(0));
                if (depth == 2) {
                    currentTagNameL2 = mParser.getAttributeValue(0);
                    currentMapL2 = new HashMap<String, Object>();

                    if (currentTagNameL2.equals(CH_ATTR_PXLOGICAL)) {
                        pxlogical.add(currentMapL2);
                    } else if (currentTagNameL2.equals(CH_ATTR_NAPDEF)) {
                        napdef.add(currentMapL2);
                    } else if (currentTagNameL2.equals(CH_ATTR_APPLICATION)) {
                        applist.add(currentMapL2);
                    } else if (currentTagNameL2.equals(CH_ATTR_BOOTSTRAP)) {
                        bootStarp.add(currentMapL2);
                    }
                } else if (depth == 3) {
                    currentTagNameL3 = mParser.getAttributeValue(0);
                    currentMapL3 = new HashMap<String, Object>();

                    if (currentTagNameL3.equals(CH_ATTR_PXPHYSICAL)) {
                        if (currentMapL2.get(CH_ATTR_PXPHYSICAL) == null) {
                            List ll = new ArrayList();
                            ll.add(currentMapL3);
                            currentMapL2.put(CH_ATTR_PXPHYSICAL, ll);
                        } else {
                            List ll = (List) currentMapL2.get(CH_ATTR_PXPHYSICAL);
                            ll.add(currentMapL3);
                        }
                    } else if (currentTagNameL3.equals("NAPAUTHINFO")) {
                        if (currentMapL2.get("NAPAUTHINFO") == null) {
                            List ll = new ArrayList();
                            ll.add(currentMapL3);
                            currentMapL2.put("NAPAUTHINFO", ll);
                        } else {
                            List ll = (List) currentMapL2.get("NAPAUTHINFO");
                            ll.add(currentMapL3);
                        }
                    } else if (currentTagNameL3.equals(CH_ATTR_PXAUTHINFO)) {
                        if (currentMapL2.get(CH_ATTR_PXAUTHINFO) == null) {
                            currentMapL2.put(currentTagNameL3, currentMapL3);
                        }
                    } else {
                        if (currentMapL2.get(currentTagNameL3) == null) {
                            currentMapL2.put(currentTagNameL3, currentMapL3);
                        } else {
                            currentMapL3 = (Map) currentMapL2.get(currentTagNameL3);
                        }
                    }
                } else if (depth == 4) {
                    currentTagNameL4 = mParser.getAttributeValue(0);
                    currentMapL4 = (Map) currentMapL3.get(currentTagNameL4);
                    if (currentMapL4 == null) {
                        currentMapL4 = new HashMap<String, Object>();
                        currentMapL3.put(currentTagNameL4, currentMapL4);
                    }
                } else {
                    currentTagNameL2 = "";
                    currentTagNameL3 = "";
                    currentTagNameL4 = "";
                    currentMapL2 = null;
                    currentMapL3 = null;
                    currentMapL4 = null;
                }
            } else {
                currentTagNameL2 = "";
                currentTagNameL3 = "";
                currentTagNameL4 = "";
                currentMapL2 = null;
                currentMapL3 = null;
                currentMapL4 = null;
            }
        } else if (tagName.equalsIgnoreCase(TAG_TABLE_OTA1[2])) {// parm
            attrValue = "";
            attrName = mParser.getAttributeValue(0);
            if (attrCount == 2) {
                attrValue = mParser.getAttributeValue(1);
            } else if (attrCount != 1) {
                return;
            }
            Log.d(LOG_TAG, "  " + attrName + "=" + attrValue);
            if (currentTagNameL2.equals("CLIENTIDENTITY") && depth == 3
                    && attrName.equals("CLIENT-ID")) {
                clientIdentity = attrValue;
                return;
            }

            if (depth == 3) {
                if (currentTagNameL2.equals("APPLICATION")
                        && (attrName.equals("TO-NAPID") || attrName.equals("TO-PROXY"))) {
                    List napID = (List) currentMapL2.get(attrName);
                    if (napID == null) {
                        napID = new ArrayList();
                    }
                    napID.add(attrValue);
                    currentMapL2.put(attrName, napID);
                } else {
                    currentMapL2.put(attrName, attrValue);
                }
            } else if (depth == 4) {
                if (currentTagNameL2.equals("PXLOGICAL") && currentTagNameL3.equals("PXPHYSICAL")
                        && attrName.equals("TO-NAPID")) {
                    List napID = (List) currentMapL3.get(attrName);
                    if (napID == null) {
                        napID = new ArrayList();
                    }
                    napID.add(attrValue);
                    currentMapL3.put(attrName, napID);
                } else {
                    if (currentMapL3.get(attrName) == null)
                        currentMapL3.put(attrName, attrValue);
                }
            } else if (depth == 5) {
                if (currentMapL4.get(attrName) == null)
                    currentMapL4.put(attrName, attrValue);
            } else {
                currentTagNameL2 = "";
                currentTagNameL3 = "";
                currentTagNameL4 = "";
                currentMapL2 = null;
                currentMapL3 = null;
                currentMapL4 = null;
            }

        } else {
            Log.i(LOG_TAG, "Unknown tag = " + tagName);
        }
    }

    /**
     * ****************************************** **OMA OTA TOKENS,Attributes
     * and values**** ******************************************
     */

    private static final String CH_ATTR_PXLOGICAL = "PXLOGICAL";

    private static final String CH_ATTR_PXAUTHINFO = "PXAUTHINFO";

    private static final String CH_ATTR_PXPHYSICAL = "PXPHYSICAL";

    private static final String CH_ATTR_PORT = "PORT";

    private static final String CH_ATTR_NAPDEF = "NAPDEF";

    private static final String CH_ATTR_NAPAUTHINFO = "NAPAUTHINFO";

    private static final String CH_ATTR_APPLICATION = "APPLICATION";

    private static final String CH_ATTR_BOOTSTRAP = "BOOTSTRAP";

    private static final String DATA_PXLOGICAL_NAME = "NAME"; // NAME

    private static final String DATA_PXLOGICAL_STARTPAGE = "STARTPAGE"; // HomePage

    private static final String DATA_PXLOGICA_PXAUTHINFO_PXAUTHID = "PXAUTH-ID"; // id

    private static final String DATA_PXLOGICA_PXAUTHINFO_PXAUTHPW = "PXAUTH-PW"; // NAME

    private static final String DATA_PXLOGICA_PXPHYSICAL_PXADDR = "PXADDR"; // proxy

    private static final String DATA_PXLOGICAL_PXPHYSICAL_PORT_PORTNBR = "PORTNBR"; // port

    private static final String DATA_NAPDEF_NAPADDRESS = "NAP-ADDRESS"; // APN

    private static final String DATA_NAPDEF_NAPADDRTYPE = "NAP-ADDRTYPE"; // APN
                                                                          // type

    private static final String DATA_NAPDEF_AUTHTYPE = "AUTHTYPE";

    private static final String DATA_APPLICATION_ADDR = "ADDR"; // MMSC
                                                                // APPID==w4

    private static final String DATA_APPLICATION_APPID = "APPID"; // MMSC
                                                                  // APPID==w4,
                                                                  // 110, 143,
                                                                  // 23

    private static final String MMSC_APPID_VALUE = "w4";

    private static final String MMSC_APPID_VALUE_POP3 = "110";

    private static final String MMSC_APPID_VALUE_IMAP4 = "143";

    private static final String MMSC_APPID_VALUE_SMTP = "25";

    private static final String POP3 = "POP3";

    private static final String IMAP4 = "IMAP4";

    private static final String SMTP = "SMTP";

    private static final String[] TAG_TABLE_OTA = {
            "wap-provisioningdoc", // 05
            "characteristic", // 06
            "parm", // 07
    };

    private static final String[] TAG_TABLE_OTA1 = {
            "", // 05
            "characteristic", // 06
            "parm", // 07
    };

    private static final String[] ATTR_START_TABLE_OTA = {
            "name", // 0x05
            "value", // 0x06
            "name=NAME", // 0x07
            "name=NAP-ADDRESS", // 0x08
            "name=NAP-ADDRTYPE", // 0x09
            "name=CALLTYPE", // 0x0A
            "name=VALIDUNTIL", // 0x0B
            "name=AUTHTYPE", // 0x0C
            "name=AUTHNAME", // 0x0D
            "name=AUTHSECRET", // 0x0E
            "name=LINGER", // 0x0F
            "name=BEARER", // 0x10
            "name=NAPID", // 0x11
            "name=COUNTRY", // 0x12
            "name=NETWORK", // 0x13
            "name=INTERNET", // 0x14
            "name=PROXY-ID", // 0x15
            "name=PROXY-PROVIDER-ID", // 0x16
            "name=DOMAIN", // 0x17
            "name=PROVURL", // 0x18
            "name=PXAUTH-TYPE", // 0x19
            "name=PXAUTH-ID", // 0x1A
            "name=PXAUTH-PW", // 0x1B
            "name=STARTPAGE", // 0x1C
            "name=BASAUTH-ID", // 0x1D
            "name=BASAUTH-PW", // 0x1E
            "name=PUSHENABLED", // 0x1F
            "name=PXADDR", // 0x20
            "name=PXADDRTYPE", // 0x21
            "name=TO-NAPID", // 0x22
            "name=PORTNBR", // 0x23
            "name=SERVICE", // 0x24
            "name=LINKSPEED", // 0x25
            "name=DNLINKSPEED", // 0x26
            "name=LOCAL-ADDR", // 0x27
            "name=LOCAL-ADDRTYPE", // 0x28
            "name=CONTEXT-ALLOW", // 0x29
            "name=TRUST", // 0x2A
            "name=MASTER", // 0x2B
            "name=SID", // 0x2C
            "name=SOC", // 0x2D
            "name=WSP-VERSION", // 0x2E
            "name=PHYSICAL-PROXY-ID", // 0x2F
            "name=CLIENT-ID", // 0x30
            "name=DELIVERY-ERR-SDU", // 0x31
            "name=DELIVERY-ORDER", // 0x32
            "name=TRAFFIC-CLASS", // 0x33
            "name=MAX-SDU-SIZE", // 0x34
            "name=MAX-BITRATE-UPLINK", // 0x35
            "name=MAX-BITRATE-DNLINK", // 0x36
            "name=RESIDUAL-BER", // 0x37
            "name=SDU-ERROR-RATIO", // 0x38
            "name=TRAFFIC-HANDL-PRIO", // 0x39
            "name=TRANSFER-DELAY", // 0x3A
            "name=GUARANTEED-BITRATE-UPLINK", // 0x3B
            "name=GUARANTEED-BITRATE-DNLINK", // 0x3C
            "name=PXADDR-FQDN", // 0x3D
            "name=PROXY-PW", // 0x3E
            "name=PPGAUTH-TYPE", // 0x3F
            "", // 0x40
            "", // 0x41
            "", // 0x42
            "", // 0x43
            "", // 0x44
            "version", // 0x45
            "version=1.0", // 0x46
            "", // 0x47
            "", // 0x48
            "", // 0x49
            "", // 0x4A
            "", // 0x4B
            "", // 0x4C
            "", // 0x4D
            "name=AUTH-ENTITY", // 0x4E
            "name=SPI", // 0x4F
            "type", // 0x50
            "type=PXLOGICAL", // 0x51
            "type=PXPHYSICAL", // 0x52
            "type=PORT", // 0x53
            "type=VALIDITY", // 0x54
            "type=NAPDEF", // 0x55
            "type=BOOTSTRAP", // 0x56
            "type=VENDORCONFIG", // 0x57
            "type=CLIENTIDENTITY", // 0x58
            "type=PXAUTHINFO", // 0x59
            "type=NAPAUTHINFO", // 0x5A
            "type=ACCESS", // 0x5B
    };

    private static final String[] ATTR_START_TABLE_OTA1 = {
            "name", // 0x05
            "value", // 0x06
            "name=NAME", // 0x07
            "", // 0x08
            "", // 0x09
            "", // 0x0A
            "", // 0x0B
            "", // 0x0C
            "", // 0x0D
            "", // 0x0E
            "", // 0x0F
            "", // 0x10
            "", // 0x11
            "", // 0x12
            "", // 0x13
            "name=INTERNET", // 0x14
            "", // 0x15
            "", // 0x16
            "", // 0x17
            "", // 0x18
            "", // 0x19
            "", // 0x1A
            "", // 0x1B
            "name=STARTPAGE", // 0x1C
            "", // 0x1D
            "", // 0x1E
            "", // 0x1F
            "", // 0x20
            "", // 0x21
            "name=TO-NAPID", // 0x22
            "name=PORTNBR", // 0x23
            "name=SERVICE", // 0x24
            "", // 0x25
            "", // 0x26
            "", // 0x27
            "", // 0x28
            "", // 0x29
            "", // 0x2A
            "", // 0x2B
            "", // 0x2C
            "", // 0x2D
            "name=AACCEPT", // 0x2E
            "name=AAUTHDATA", // 0x2F
            "name=AAUTHLEVEL", // 0x30
            "name=AAUTHNAME", // 0x31
            "name=AAUTHSECRET", // 0x32
            "name=AAUTHTYPE", // 0x33
            "name=ADDR", // 0x34
            "name=ADDRTYPE", // 0x35
            "name=APPID", // 0x36
            "name=APROTOCOL", // 0x37
            "name=PROVIDER-ID", // 0x38
            "name=TO-PROXY", // 0x39
            "name=URI", // 0x3A
            "name=RULE", // 0x3B
            "", // 0x3C
            "", // 0x3D
            "", // 0x3E
            "", // 0x3F
            "", // 0x40
            "", // 0x41
            "", // 0x42
            "", // 0x43
            "", // 0x44
            "", // 0x45
            "", // 0x46
            "", // 0x47
            "", // 0x48
            "", // 0x49
            "", // 0x4A
            "", // 0x4B
            "", // 0x4C
            "", // 0x4D
            "", // 0x4E
            "", // 0x4F
            "type", // 0x50
            "", // 0x51
            "", // 0x52
            "type=PORT", // 0x53
            "", // 0x54
            "type=APPLICATION", // 0x55
            "type=APPADDR", // 0x56
            "type=APPAUTH", // 0x57
            "", // 0x58
            "type=RESOURCE", // 0x59
    };

    private static final String[] ATTR_VALUE_TABLE_OTA = {
            "IPV4", // 0x85
            "IPV6", // 0x86
            "E164", // 0x87
            "ALPHA", // 0x88
            "APN", // 0x89
            "SCODE", // 0x8A
            "TETRA-ITSI", // 0x8B
            "MAN", // 0x8C
            "APPSRV", // 0x8D
            "OBEX", // 0x8E
            "", // 0x8F
            "ANALOG-MODEM", // 0x90
            "V.120", // 0x91
            "V.110", // 0x92
            "X.31", // 0x93
            "BIT-TRANSPARENT", // 0x94
            "DIRECT-ASYNCHRONOUS-DATA-SERVICE", // 0x95
            "", // 0x96
            "", // 0x97
            "", // 0x98
            "", // 0x99
            "PAP", // 0x9A
            "CHAP", // 0x9B
            "HTTP-BASIC", // 0x9C
            "HTTP-DIGEST", // 0x9D
            "WTLS-SS", // 0x9E
            "MD5", // 0x9F
            "", // 0xA0
            "", // 0xA1
            "GSM-USSD", // 0xA2
            "GSM-SMS", // 0xA3
            "ANSI-136-GUTS", // 0xA4
            "IS-95-CDMA-SMS", // 0xA5
            "IS-95-CDMA-CSD", // 0xA6
            "IS-95-CDMA-PACKET", // 0xA7
            "ANSI-136-CSD", // 0xA8
            "ANSI-136-GPRS", // 0xA9
            "GSM-CSD", // 0xAA
            "GSM-GPRS", // 0xAB
            "AMPS-CDPD", // 0xAC
            "PDC-CSD", // 0xAD
            "PDC-PACKET", // 0xAE
            "IDEN-SMS", // 0xAF
            "IDEN-CSD", // 0xB0
            "IDEN-PACKET", // 0xB1
            "FLEX/REFLEX", // 0xB2
            "PHS-SMS", // 0xB3
            "PHS-CSD", // 0xB4
            "TETRA-SDS", // 0xB5
            "TETRA-PACKET", // 0xB6
            "ANSI-136-GHOST", // 0xB7
            "MOBITEX-MPAK", // 0xB8
            "CDMA2000-1X-SIMPLE-IP", // 0xB9
            "CDMA2000-1X-MOBILE-IP", // 0xBA
            "", // 0xBB
            "", // 0xBC
            "", // 0xBD
            "", // 0xBE
            "", // 0xBF
            "", // 0xC0
            "", // 0xC1
            "", // 0xC2
            "", // 0xC3
            "", // 0xC4
            "AUTOBAUDING", // 0xC5
            "", // 0xC6
            "", // 0xC7
            "", // 0xC8
            "", // 0xC9
            "CL-WSP", // 0xCA
            "CO-WSP", // 0xCB
            "CL-SEC-WSP", // 0xCC
            "CO-SEC-WSP", // 0xCD
            "CL-SEC-WTA", // 0xCE
            "CO-SEC-WTA", // 0xCF
            "OTA-HTTP-TO", // 0xD0
            "OTA-HTTP-TLS-TO", // 0xD1
            "OTA-HTTP-PO", // 0xD2
            "OTA-HTTP-TLS-PO", // 0xD3
            "", // 0xD4
            "", // 0xD5
            "", // 0xD6
            "", // 0xD7
            "", // 0xD8
            "", // 0xD9
            "", // 0xDA
            "", // 0xDB
            "", // 0xDC
            "", // 0xDD
            "", // 0xDE
            "", // 0xDF
            "AAA", // 0xE0
            "HA", // 0xE1
    };

    private static final String[] ATTR_VALUE_TABLE_OTA1 = {
            "value=IPV4", // 0x85
            "IPV6", // 0x86
            "E164", // 0x87
            "ALPHA", // 0x88
            "", // 0x89
            "", // 0x8A
            "", // 0x8B
            "", // 0x8C
            "APPSRV", // 0x8D
            "OBEX", // 0x8E
            "", // 0x8F
            ",", // 0x90
            "HTTP-", // 0x91
            "BASIC", // 0x92
            "DIGEST", // 0x93
    };

}
