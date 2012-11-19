
package com.android.mms.dom;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.util.Config;
import android.util.Log;
import com.android.mms.dom.WbxmlParser;
import com.android.mms.transaction.OtaConfigVO;
import com.android.mms.transaction.WapPushMsg;

public abstract class OtaParser {
    /**
     * The log tag.
     */
    static final String LOG_TAG = "OTAParser";

    private static final boolean DEBUG = true;

    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    /**
     * MIME
     */
    public static final int OTA_OMA_DATA = 1; // Moto OMA

    public static final int OTA_NOKIA_DATA1 = 2; // Nokia browser settings

    public static final int OTA_NOKIA_DATA2 = 3; // Nokia Browser bookmarks

    public static final int OTA_MSG_OK=0;
    public static final int OTA_MSG_ERROR=1;
    public static final int OTA_MSG_INVALIDATE=2;

    /**
     * The wap push data.
     */
    private ByteArrayInputStream mOTADataStream = null;

    public List<OtaConfigVO> data = new ArrayList();

    WbxmlParser mParser = new WbxmlParser(); // Parser

    String currentTagName;

    int otaMimeType;

    /**
     * Constructor.
     *
     * @param wapPushDataStream wap push data to be parsed
     */
    public OtaParser(byte[] pushDataStream, int mimeType) {
        mOTADataStream = new ByteArrayInputStream(pushDataStream);
        otaMimeType = mimeType;
    }

    /**
     * @param tagName
     * @param oc
     */
    abstract void elementParser(String tagName);

    abstract public int getParseData();

    /**
     * Parse the wap push. type the push message type WAP_PUSH_TYPE_SI or
     * WAP_PUSH_TYPE_SL
     *
     * @return the push structure if parsing successfully. null if parsing error
     *         happened or mandatory fields are not set.
     */
    public int parse() {
        String tagName = null;

        if (mOTADataStream == null) {
            Log.e(LOG_TAG, "mWapPushDataStream is not set!");
            return OTA_MSG_ERROR;
        }

        try {
            mParser.setInput(mOTADataStream, null);
            if (LOCAL_LOGV) {
                Log.i(LOG_TAG, "Document charset : " + mParser.getInputEncoding());
            }

            int eventType = mParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        Log.i(LOG_TAG, "Start document");
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        Log.i(LOG_TAG, "End document");
                        break;
                    case XmlPullParser.START_TAG:
                        Log.i(LOG_TAG, "Start tag = " + mParser.getName());
                        elementParser(mParser.getName());
                        break;
                    case XmlPullParser.END_TAG:
                        Log.i(LOG_TAG, "End tag = " + mParser.getName());
                        break;
                    case XmlPullParser.TEXT:
                        Log.i(LOG_TAG, "Text = " + mParser.getText());
                        break;
                    default:
                        Log.i(LOG_TAG, "unknown event type =  " + eventType);
                        break;
                }
                if(eventType==XmlPullParser.END_TAG && "wap-provisioningdoc".equals(mParser.getName())){
                    break;
                }
                eventType = mParser.next();
            }
        } catch (IOException e) {
            if (LOCAL_LOGV) {
                Log.e(LOG_TAG, e.toString());
            }
            return OTA_MSG_ERROR;
        } catch (XmlPullParserException e) {
            if (LOCAL_LOGV) {
                Log.e(LOG_TAG, e.toString());
            }
            return OTA_MSG_ERROR;
        }
        return getParseData();
    }
}
