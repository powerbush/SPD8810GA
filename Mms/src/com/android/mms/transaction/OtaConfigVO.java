
package com.android.mms.transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OtaConfigVO {
    public static final String NAME = "NAME";

    public static final String APN = "APN";

    public static final String APN_TYPE = "APN_TYPE";

    public static final String PROXY = "PROXY";

    public static final String PORT = "PORT";

    public static final String USER_NAME = "USER_NAME";

    public static final String PWD = "PWD";

    public static final String SERVER = "SERVER";

    public static final String MMSC = "MMSC";

    public static final String MMSC_PROXY = "MMSC_PROXY";

    public static final String MMSC_PORT = "MMSC_PORT";

    public static final String MCC = "MCC";

    public static final String MNC = "MNC";

    public static final String AUTH_TYPE = "AUTH_TYPE";

    public static final String HOME_PAGE = "HOME_PAGE";

    public static final int OMA_BOOTSP = 11;
    public static final int OMA_W2 = 12;
    public static final int OMA_W4 = 14;
    public static final int OMA_EMAIL = 15;
    public static final int NOKIA_DATA = 21;

    public int dataFlag=0;
    public List<BootStarp> bsList = new ArrayList();

    public List<OtaBookMark> bmList = new ArrayList();

    public List<EMailSetting> emList = new ArrayList();

    public Map<String, String> dataVaule = new HashMap<String, String>();

    public void setValue(String attr, String value) {
        dataVaule.put(attr, value);
    }

    public String getValue(String attr) {
        return dataVaule.get(attr) == null ? "" : dataVaule.get(attr);
    }

    public static class OtaBookMark {
        public String bmName;

        public String bmUrl;

        public OtaBookMark(String bmName, String bmUrl) {
            this.bmName = bmName;
            this.bmUrl = bmUrl;
        }
    }

    public static class BootStarp {
        public String name;

        public String proxyID;

        public BootStarp(String name, String proxyID) {
            this.name = name;
            this.proxyID = proxyID;
        }
    }

    public EMailSetting findEMS(String PROVIDER_ID) {
        if (PROVIDER_ID == null) {
            return null;
        }
        EMailSetting ems = null;
        for (EMailSetting em : emList) {
            if (PROVIDER_ID.equals(em.PROVIDER_ID)) {
                ems = em;
                return ems;
            }

        }
        return null;
    }

    public static class EMailSetting {
        public String PROVIDER_ID; // the id of account

        public String TO_NAPID;// NAPDEF-NAPID

        public String TO_PROXY;

        public boolean send = false;

        public boolean recv = false;

        public String accountName;

        public String userID;

        public String pwd;

        public String returnAddress;

        public String webSession;

        public String protocol;

        public String recvHost;

        public String recvPort;

        public String sendHost;

        public String sendPort;

        public boolean recvSSL = false;

        public boolean sendSSL = false;

    }
}
