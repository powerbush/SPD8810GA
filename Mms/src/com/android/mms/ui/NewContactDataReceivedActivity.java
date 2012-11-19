
package com.android.mms.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.mms.R;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.pim.vcard.VCardConfig;
import android.pim.vcard.VCardEntry;
import android.pim.vcard.VCardEntryConstructor;
import android.pim.vcard.VCardEntryHandler;
import android.pim.vcard.VCardInterpreter;
import android.pim.vcard.VCardParser;
import android.pim.vcard.VCardParser_V21;
import android.pim.vcard.VCardParser_V30;
import android.pim.vcard.VCardSourceDetector;
import android.pim.vcard.VCardEntry.EmailData;
import android.pim.vcard.VCardEntry.OrganizationData;
import android.pim.vcard.VCardEntry.PhoneData;
import android.pim.vcard.VCardEntry.PostalData;
import android.pim.vcard.exception.VCardException;
import android.pim.vcard.exception.VCardNotSupportedException;
import android.pim.vcard.exception.VCardVersionException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class NewContactDataReceivedActivity extends ListActivity {
    private static final String TAG = "NewContactDataReceivedActivity";

    // Contact
    private static final Pattern patternVCard = Pattern.compile("(?ms)^BEGIN:VCARD$.+?^END:VCARD$");

    // full name
    private static final Pattern patternFullName = Pattern.compile("(?m)^FN:([^;\\r\\n]+)*.*$");

    // name
    private static final Pattern patternName = Pattern
            .compile("(?m)^N:([^;\\r\\n]*)(?:;([^;\\r\\n]+))*.*$");

    // home phone
    private static final Pattern patternHomePhone = Pattern
            .compile("(?m)^TEL;(TYPE=)?HOME.*:([\\(\\)\\d-_\\+]{1,20})$");

    // cell
    private static final Pattern patternCell = Pattern
            .compile("(?m)^TEL;(TYPE=)?(CELL|PREF).*:([\\(\\)\\d-_\\+]{1,20})$");

    // office phone
    private static final Pattern patternOfficePhone = Pattern
            .compile("(?m)^TEL;(TYPE=)?WORK.*:([\\(\\)\\d-_\\+]{1,20})$");

    // other phone
    private static final Pattern patternOtherPhone = Pattern
            .compile("(?m)^TEL;(TYPE=)?VOICE.*:([\\(\\)\\d-_\\+]{1,20})$");

    // fax
    private static final Pattern patternFax = Pattern
            .compile("(?m)^TEL;(TYPE=)?FAX.*:([\\(\\)\\d-_\\+]{1,20})$");

    // email
    private static final Pattern patternEmail = Pattern.compile("(?m)^EMAIL;(HOME)?:(.{1,100})$");

    // birthday
    private static final Pattern patternBirthday = Pattern
            .compile("(?m)^BDAY:(\\d{4})-?(\\d{2})-?(\\d{2})$");

    // address
    private static final Pattern patternAddress = Pattern.compile("(?m)^ADR([^:]+):;*(.+)$"); // backup:
                                                                                              // "(?m)^ADR([^:]+):(.+)$"

    // Organization
    private static final Pattern patternOrganization = Pattern
            .compile("(?m)^ORG:([^;\\r\\n]+)*.*$");

    // Job title
    private static final Pattern patternJobTitle = Pattern.compile("(?m)^TITLE:([^;\\r\\n]+)*.*$");

    Matcher matcher;

    boolean bl;

    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        Log.v(TAG, "oncreate()");
        setContentView(R.layout.received_contact_list);
        Intent intent = this.getIntent();
        String content;
        /*
         * if(intent.getExtras().getString("mode").equalsIgnoreCase("test")){
         * content = intent.getExtras().getString("contents"); }else { content =
         * receivedMessage(intent); }
         */

        content = receivedMessage(intent);
        Log.v(TAG, "content is " + content);
        readVCardContent(content);
        // addView();
        Button saveButton = (Button) this.findViewById(R.id.save_a_new_contact);
        Button cancelButton = (Button) this.findViewById(R.id.cancel_new_contact);
        saveButton.setOnClickListener(listener);
        cancelButton.setOnClickListener(listener);
    }

    private VCardParser mVCardParser;

    private boolean parseVcardFile(String content, String charset,
            VCardInterpreter builder, VCardSourceDetector detector) {

        InputStream is;

        try {
            //is = mResolver.openInputStream(uri);
            is = new ByteArrayInputStream(content.getBytes());
//            mVCardParser = new VCardParser_V21(detector);
            mVCardParser = new VCardParser_V21();

            try {
//                mVCardParser.parse(is, charset, builder, false);
                mVCardParser.parse(is, builder);
            } catch (VCardVersionException e1) {
                try {
                    is.close();
                } catch (IOException e) {
                }
                if (builder instanceof VCardEntryConstructor) {
                    // Let the object clean up internal temporal objects,
                    ((VCardEntryConstructor)builder).clear();
                }
                //is = mResolver.openInputStream(uri);
                is = new ByteArrayInputStream("".getBytes());

                try {
                    mVCardParser = new VCardParser_V30();
//                    mVCardParser.parse(is, charset, builder, false);
                    mVCardParser.parse(is, builder);
                } catch (VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "parse vcard io exception!");
            return false;
        } catch (VCardNotSupportedException e) {
            Log.e(TAG, "unsupported vcard file!");
            return false;
        } catch (VCardException e) {
            Log.e(TAG, "Vcard parse failed!");
            return false;
        }
        return true;
    }

    private int vcardType = -1;
    private void readVCardContent(String content) {
        vcardType = VCardConfig.getVCardTypeFromString("default");
//        String charset = VCardConfig.DEFAULT_CHARSET;
//        VCardEntryConstructor builder = new VCardEntryConstructor(charset, charset, false, vcardType, null);
        String charset = VCardConfig.DEFAULT_INTERMEDIATE_CHARSET;
        VCardEntryConstructor builder = new VCardEntryConstructor(vcardType, null,charset ,false);

        EntryCreateDoneHandler entryCreateDoneHandler = new EntryCreateDoneHandler();
        builder.addEntryHandler(entryCreateDoneHandler);

        VCardSourceDetector detector = new VCardSourceDetector();

        // do parsing work
        parseVcardFile(content, charset, builder, detector);
    }
    
    class EntryCreateDoneHandler implements VCardEntryHandler {

        public EntryCreateDoneHandler() {
        }

        public void onStart() {
        }

        public void onEntryCreated(VCardEntry contactStruct) {
            NewContactDataReceivedActivity.this.contactStruct = contactStruct;
            if(contactStruct != null){
                setFields(contactStruct);
            }else{
                Log.e(TAG, "onEntryCreated: Entry is null");
            }
//            if (!contactStruct.isIgnorable()) {
//                Log.d(TAG, "entryName:"+contactStruct.getDisplayName());
//                Log.d(TAG, "entry:"+contactStruct);
//            }

        }

        public void onEnd() {
        }
    }


    private VCardEntry contactStruct = null;
    OnClickListener listener = new OnClickListener() {

        public void onClick(View v) {
            Button button = (Button) v;
            switch (button.getId()) {
                case R.id.save_a_new_contact:
                    Log.v(TAG, "save message Contact");
                    if(contactStruct != null){
                        contactStruct.pushIntoContentResolver(NewContactDataReceivedActivity.this.getContentResolver());//store the contact
                    }else{
                        Log.e(TAG, "save message Contact: Contact is null");
                    }
                    NewContactDataReceivedActivity.this.finish();
                    break;

                case R.id.cancel_new_contact:
                    NewContactDataReceivedActivity.this.finish();
                    break;
                default:
                    break;
            }
        }
    };


    private void setFields(VCardEntry entry) {
        ListView listView = getListView();
        List<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
        HashMap<String, String> map;
        if (contactStruct != null) {
            //name 
            String displayname = entry.getDisplayName();
            if (!TextUtils.isEmpty(displayname)) {
                map = new HashMap<String, String>();
                map.put("field", "Dislayname:");
                map.put("content", displayname);
                list.add(map);
            } else {
                Log.d(TAG, "can't pattern Dislayname");
            }

            //number
            List<PhoneData> phonelist = entry.getPhoneList();
            if(phonelist != null && phonelist.size() > 0){
                for(int x = 0; x < phonelist.size(); x++){
                    PhoneData phonedata = phonelist.get(x);
                    String lable = ContactsContract.CommonDataKinds.Phone.getTypeLabel(this.getResources(), phonedata.type, phonedata.label).toString();
                    map = new HashMap<String, String>();
                    map.put("field", lable+":");
                    map.put("content", phonedata.data);
                    list.add(map);
                }
            }else{
                Log.d(TAG, "can't pattern Phone number");
            }

            //Email
            List<EmailData> emaillist = entry.getEmailList();
            if(emaillist != null && emaillist.size() > 0){
                for(int x = 0; x < emaillist.size(); x++){
                    EmailData emaildata = emaillist.get(x);
                    String lable = ContactsContract.CommonDataKinds.Email.getTypeLabel(this.getResources(), emaildata.type, emaildata.label).toString();
                    map = new HashMap<String, String>();
                    map.put("field", lable+":");
                    map.put("content", emaildata.data);
                    list.add(map);
                }
            }else{
                Log.d(TAG, "can't pattern Email");
            }

            //address
            List<PostalData> postallist = entry.getPostalList();
            if(postallist != null && postallist.size() > 0){
                for(int x = 0; x < postallist.size(); x++){
                    PostalData postaldata = postallist.get(x);
                    String lable = ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(this.getResources(), postaldata.type, postaldata.label).toString();
                    map = new HashMap<String, String>();
                    map.put("field", lable+":");
                    map.put("content", postaldata.getFormattedAddress(vcardType));
                    list.add(map);
                }
            }else{
                Log.d(TAG, "can't pattern PostalData");
            }

            //birthday
            String birthday = entry.getBirthday();
            if(!TextUtils.isEmpty(birthday)){
//                map = new HashMap<String, String>();
//                map.put("field", "Birthday:");
//                map.put("content", birthday);
//                list.add(map);
            }else{
                Log.d(TAG, "can't pattern birthday");
            }

            // organization
            List<OrganizationData> orglist = entry.getOrganizationList();
            if(orglist != null && orglist.size() > 0){
                for(int x = 0; x < orglist.size(); x++){
                    OrganizationData orgdata = orglist.get(x);
                    String lable = ContactsContract.CommonDataKinds.Organization.getTypeLabel(this.getResources(), orgdata.type, "").toString();
                    map = new HashMap<String, String>();
                    map.put("field", lable+":");
                    map.put("content", orgdata.getFormattedString());
                    list.add(map);
                }
            }else{
                Log.v(TAG, "can't pattern Organization");
            }

            // title
//            matcher = patternJobTitle.matcher(strOneContact);
//            if (matcher.find()) {
//                String job_title = matcher.group(1);
//                map = new HashMap<String, String>();
//                map.put("field", "Job title:");
//                map.put("content", job_title);
//                list.add(map);
//            } else {
//                Log.v(TAG, "can't pattern Job title");
//            }

        }else{
            Log.e(TAG, "setFields: contactStruct is null!!");
        }
        SimpleAdapter adapter = new SimpleAdapter(this, list, R.layout.received_contact_item,
                new String[] {
                        "field", "content"
                }, new int[] {
                        R.id.field, R.id.content
                });
        listView.setAdapter(adapter);

    }

    private String receivedMessage(Intent intent) {
        Bundle bundle = intent.getExtras();
        Log.v(TAG, "bundle:" + bundle.toString());
        Object[] pdus = (Object[]) bundle.get("pdus");
        SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
        }
        StringBuilder sb = new StringBuilder();
        String content = null;
        for (SmsMessage tmp : messages) {
            /*
             * String content = tmp.getDisplayMessageBody();
             * Log.v(TAG,"1:"+content); Log.v(TAG,"2:"+tmp.getMessageBody());
             * Log.v(TAG,"3:"+tmp.getMessageClass());
             */
            Log.v(TAG, "4:" + tmp.getOriginatingAddress());
            /*
             * Log.v(TAG,"5:"+tmp.getPseudoSubject());
             * Log.v(TAG,"6:"+tmp.getStatus());
             * Log.v(TAG,"7:"+tmp.ENCODING_8BIT);
             */
            content = new String(tmp.getUserData());
            sb.append(content);
        }
        return sb.toString();
        // Log.v(TAG,"content:"+sb.toString());
    }

}
