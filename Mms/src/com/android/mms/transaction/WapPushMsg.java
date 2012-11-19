package com.android.mms.transaction;

public class WapPushMsg {

    private String hrefText = null;
    private String siText = null;
    private String expires = null;  //add by liguxiang 08-18-11 for NEWMS00109865
    private String si_id = null;
    private String action = null;

    public static int WAP_PUSH_TYPE_SI = 0;
    public static int WAP_PUSH_TYPE_SL = 1;
    //public static Object WAP_PUSH_PROJECTION_SI_TEXT;
    public static Integer WAP_PUSH_PROJECTION_PRIOR = 1;
    public static Integer WAP_PUSH_PROJECTION_HREF = 2;
    public static Integer WAP_PUSH_PROJECTION_SI_EXPIRED = 3;
    public static Integer WAP_PUSH_PROJECTION_SI_CREATED = 4;
    public static Integer WAP_PUSH_PROJECTION_SI_ID = 5;
    public static Integer WAP_PUSH_PROJECTION_SI_TEXT = 6;

    public static Integer WAP_PUSH_PRIO_NONE = 7;
    public static Integer WAP_PUSH_PRIO_LOW = 8;
    public static Integer WAP_PUSH_PRIO_MEDIUM = 9;
    public static Integer WAP_PUSH_PRIO_HIGH = 10;
    public static Integer WAP_PUSH_PRIO_DELETE = 11;

    public static Integer WAP_PUSH_SL_PRIO_LOW = 12;
    public static Integer WAP_PUSH_SL_PRIO_HIGH = 13;
    public static Integer WAP_PUSH_SL_PRIO_CACHE = 14;

    public WapPushMsg(int type) {
        // TODO Auto-generated constructor stub
    }

    public void setAttributeValue(Integer type, String text) {
        if (type == WAP_PUSH_PROJECTION_HREF) {
            hrefText = text;
        } else if (type == WAP_PUSH_PROJECTION_SI_TEXT) {
            siText = text;
        //add by liguxiang 08-18-11 for NEWMS00109865 begin
        } else if (type == WAP_PUSH_PROJECTION_SI_EXPIRED) {
            expires = text;
        } else if (type == WAP_PUSH_PROJECTION_PRIOR) {
            action = text;
        } else if (type == WAP_PUSH_PROJECTION_SI_ID) {
            si_id = text;
        }
        //add by liguxiang 08-18-11 for NEWMS00109865 end
    }

    public String getAttributeValueString(Integer type) {
        if (type == WAP_PUSH_PROJECTION_HREF) {
            return hrefText;
        } else if (type == WAP_PUSH_PROJECTION_SI_TEXT) {
            return siText;
        //add by liguxiang 08-18-11 for NEWMS00109865 begin
        } else if (type == WAP_PUSH_PROJECTION_SI_EXPIRED) {
            return expires;
        } else if (type == WAP_PUSH_PROJECTION_PRIOR) {
            return action;
        } else if (type == WAP_PUSH_PROJECTION_SI_ID) {
            return si_id;
        }
        //add by liguxiang 08-18-11 for NEWMS00109865 end
        return null;
    }

}
