package com.android.contacts.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;

import com.android.contacts.ContactsListActivity;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.SimInitReceiver;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.PhoneFactory;

import android.telephony.TelephonyManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TextView;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemClickListener;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.content.ContentValues;
import android.widget.Toast;
import android.util.Log;
import com.android.contacts.ui.SimUtils;
import com.android.internal.telephony.IIccPhoneBook;

//added for dual sim
import android.accounts.Account;
import android.widget.ImageView;

import com.android.contacts.util.CommonUtil;
import com.android.contacts.util.Config;
import com.android.contacts.util.DialerKeyListener;

//import android.os.FM;
public class SimEditContactActivity extends Activity implements OnClickListener{
    private EditText mNameText;
//    private EditText mNumberText;
    private EditText mNumberMoblieText;
    private EditText mNumberHomeText;
    private EditText mNumberWorkText;
    private EditText mNumberOtherText;
    private EditText mEmailText;
    private Button mBtnDone;
    private Button mBtnDiscard;
    String mTag;
//    String mNumber;
    String mNumberMobile = "";
    String mNumberphoneHome = "";
    String mNumberWork = "";
    String mNumberOther = "";
    String mEmail = "";
    String mSimAnr ;
    String mSimNewAnr;
    String mSimIndex;
    int anrNum = 0 ;
    private ArrayList<String> mData;
    private ArrayList<String> oldData; //modify by dory.zheng for contacts is replace
    QueryHandler mQueryHandler = null;
    // Dialog IDs
    final static int DELETE_CONFIRMATION_DIALOG = 2;
    // Menu item IDs
    public static final int MENU_ITEM_DELETE = 1;
    public static final int MENU_ITEM_INSERT = 2;

    private static final int MODE_EDIT = 1;
    private static final int MODE_INSERT = 2;
    private static final String TAG = "SimEditContactActivity";
    private int mMode=MODE_EDIT;
    public static final String SIM_ADDRESS = "phone_id";
    public static final String SIM1_ADDRESS = "0";
    public static  final String SIM_EMAIL = "email";

    public static final int LIMIT_LENGTH = 20;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        RawContacts.SIM_INDEX,              // 11
    };

    //added for dual sim
    private String mAccountName = Account.SIM1_ACCOUNT_NAME;

    protected class EncodeLengthFilter implements InputFilter{

        private int mMax = 0;
	private int mMaxChinese = 0;
        private EncodeLengthFilter(){};
        public EncodeLengthFilter(int max){
            mMax = max;
	    mMaxChinese = max - 2 ;
        };
        private byte[] getbytes(CharSequence source, int start,
                int end, Spanned dest, int dstart, int dend){
	    String destString = dest.toString();
	    if(isChinese(source.toString())){
		Log.d(TAG, "source has chinese sourceString = " + source.toString() );
	        mMax = mMaxChinese ;
	    }
            StringBuilder allChares = new StringBuilder()
                .append(destString.subSequence(0, dend == dstart ? dstart : dstart + 1))
                .append(source.subSequence(start, end))
                .append(destString.subSequence(dend == dstart ? dend : dend - 1, destString.length()));
            byte[] bytes = null;
            try {
                bytes = GsmAlphabet
                        .isAsciiStringToGsm8BitUnpackedField(allChares.toString());
             } catch (EncodeException ex) {
                try {
                    bytes = allChares.toString().getBytes("utf-16be");
                } catch (java.io.UnsupportedEncodingException ex2) {
                    Log.e(TAG, allChares +
                            " convert byte excepiton");
                }
            }
            return bytes;
        }
        @Override
        public CharSequence filter(CharSequence source, int start,
                int end, Spanned dest, int dstart, int dend) {
            byte[] bytes = getbytes(source, start, end, dest, dstart, dend);
            CharSequence replace = null;
            boolean isChange = false;
            while (bytes != null && bytes.length > mMax && end > start){
                isChange = true;
                end--;
                bytes = getbytes(source, start, end, dest, dstart, dend);
            }
            if (isChange && end >= start){
                replace = source.subSequence(start, end);
            }
            return replace;
        }
    }
    //added for cq NEWMS00191999 begin 
    // GENERAL_PUNCTUATION for chinese "   
    // CJK_SYMBOLS_AND_PUNCTUATION for chinese .  
    // HALFWIDTH_AND_FULLWIDTH_FORMS for chinese ,  
    private static final boolean isChinese(char c) {  
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);  
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS  
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS  
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A  
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION  
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION  
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {  
            return true;  
        }  
        return false;  
    }  
  
    public static final boolean isChinese(String strName) {  
        char[] ch = strName.toCharArray();  
        for (int i = 0; i < ch.length; i++) {  
            char c = ch[i];  
            if (isChinese(c)) {  
                return true;  
            }  
        }  
        return false;  
    }  

    //added for cq NEWMS00191999 end 

    protected class PhoneNumberLengthFilter implements InputFilter{
        private int mNoPlusMax = 0;
        private PhoneNumberLengthFilter(){};
        public PhoneNumberLengthFilter(int noPlusMax){
            mNoPlusMax = noPlusMax;
        };
        @Override
        public CharSequence filter(CharSequence source, int start,
                int end, Spanned dest, int dstart, int dend) {
            int keep = mNoPlusMax - (dest.length() - (dend - dstart));
            int _end = start + keep;
            String dest_before = dest.toString().substring(0, dend == dstart ? dstart : dstart + 1);
            String dest_after = dest.toString().substring(dend == dstart ? dend : dend - 1);
            CharSequence input = source.subSequence(start, _end < end ? _end + 1 : end);
            boolean sourceHasPlus = input.toString().contains("+");
            boolean destHasPlus = dest_before.toString().contains("+") || dest_after.toString().contains("+");
            boolean hasplus = sourceHasPlus || destHasPlus;
            if (hasplus) {
                return input;
            } else if (keep <= 0){
                return "";
            } else if (keep >= end - start) {
                return null;
            } else {
                return source.subSequence(start, _end);
            }
        }
    }
//    private ProgressDialog mProgressDialog;
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.v(TAG, "onCreate");

        mMode = getIntent().getIntExtra("mode", MODE_EDIT);
        if(Config.isMSMS){
            mAccountName = getIntent().getStringExtra("account_name");
        }

        int mFreeCapacity = getFreeCapacity(SimEditContactActivity.this);
        if(mFreeCapacity < 1 && mMode == MODE_INSERT) {
            //added for dual sim
            IIccPhoneBook iccIpb;
            //NEWMS00170745
            if (mFreeCapacity == -1) {
                Toast.makeText(SimEditContactActivity.this, R.string.toast_sim_failed, Toast.LENGTH_SHORT).show();
            } else {
                if(Config.isMSMS){
                    if(Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
                        Toast.makeText(SimEditContactActivity.this, R.string.toast_sim1_overflow, Toast.LENGTH_SHORT).show();
                    }
                    else{
                        Toast.makeText(SimEditContactActivity.this, R.string.toast_sim2_overflow, Toast.LENGTH_SHORT).show();
                    }
                }
                else{
                    Toast.makeText(SimEditContactActivity.this, R.string.toast_sim_overflow, Toast.LENGTH_SHORT).show();
                }
            }
            finish();
            return;
        }

        mTag = getIntent().getStringExtra("tag");
//      mNumber = getIntent().getStringExtra("number");
        mSimIndex = getIntent().getStringExtra("sim_index");
        mData = getIntent().getExtras().getStringArrayList("numbers");
        oldData = getIntent().getExtras().getStringArrayList("oldData"); //modify by dory.zheng for contacts is replace
        setContentView(R.layout.sim_act_edit); //before is
        mNameText = (EditText)this.findViewById(R.id.name);
        mNumberHomeText = (EditText)this.findViewById(R.id.number1);
//        mNumberMoblieText = (EditText)this.findViewById(R.id.number1);

        ImageView accountImg = (ImageView)this.findViewById(R.id.account_img);
        TextView accountTitle = (TextView)this.findViewById(R.id.account_title);

        //added for dual sim
        IIccPhoneBook iccIpb;
        if(Config.isMSMS){
            Log.i(TAG, "onCreate, mAccountName:"+mAccountName);
            if(Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
                iccIpb= IIccPhoneBook.Stub.asInterface(
                        ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", 0)));
                accountImg.setImageResource(R.drawable.ic_menu_add_sim1);
                accountTitle.setText(R.string.account_sim1);
            }
            else{
                iccIpb= IIccPhoneBook.Stub.asInterface(
                        ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", 1)));
                accountImg.setImageResource(R.drawable.ic_menu_add_sim2);
                accountTitle.setText(R.string.account_sim2);
            }
        }
        else{
            iccIpb= IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
        }

        mNumberMoblieText = (EditText)this.findViewById(R.id.number2);
//        mNumberHomeText = (EditText)this.findViewById(R.id.number2);
        mNumberWorkText = (EditText)this.findViewById(R.id.number3);
        mNumberOtherText = (EditText)this.findViewById(R.id.number4);
        mEmailText =  (EditText)this.findViewById(R.id.email);

        mNumberHomeText.setKeyListener(DialerKeyListener.getInstance());
        mNumberMoblieText.setKeyListener(DialerKeyListener.getInstance());
        mNumberWorkText.setKeyListener(DialerKeyListener.getInstance());
        mNumberOtherText.setKeyListener(DialerKeyListener.getInstance());

        final Intent intent = getIntent();

        int emailNum = 0 ;
        int emailSize = 0;
        int[] adnRecords = new int[]{};

        try {
            anrNum = iccIpb.getAnrNum();
            emailNum = iccIpb.getEmailNum();
            adnRecords = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
            int[] size = iccIpb.getEmailRecordsSize();
            if (size != null) {
                emailSize = size[0] - 2;
            }
        } catch (RemoteException ex) {
            Log.v(TAG,"excetpion");
        } catch (SecurityException ex) {
            Log.v(TAG,"excetpion");
        }
        final int nameSize = adnRecords[0] - 14;
        Log.d(TAG,"anrNum------------"+anrNum+"---emailNum---------"+emailNum + "-------nameSize-----"+nameSize);
        // modify by dory.zheng for NEWMS00123348 at 21-09 begin
        if(anrNum == 0){
	    TextView phoneNumText = (TextView)findViewById(R.id.phonenumber);
            phoneNumText.setText(R.string.phone_number);
            mNumberHomeText.setHint(R.string.phone_number);
        }
        // modify by dory.zheng for NEWMS00123348 at 21-09 end
        if (anrNum > 0)  { //1
            findViewById(R.id.llnumber2).setVisibility(View.VISIBLE);
            if (anrNum > 1)  { //2
                findViewById(R.id.llnumber3).setVisibility(View.VISIBLE);
                 if (anrNum > 2)  { //
                     findViewById(R.id.llnumber4).setVisibility(View.VISIBLE);
                 }
            }
        }
        if(emailNum != 0){
            findViewById(R.id.llemail).setVisibility(View.VISIBLE);
        }
        mNameText.setFilters(new InputFilter[]{
                new EncodeLengthFilter(nameSize)
        });
        mNumberMoblieText.setFilters(new InputFilter[]{
                new PhoneNumberLengthFilter(LIMIT_LENGTH)
        });
        mNumberHomeText.setFilters(new InputFilter[]{
                new PhoneNumberLengthFilter(LIMIT_LENGTH)
        });
        mNumberWorkText.setFilters(new InputFilter[]{
                new PhoneNumberLengthFilter(LIMIT_LENGTH)
        });
        mNumberOtherText.setFilters(new InputFilter[]{
                new PhoneNumberLengthFilter(LIMIT_LENGTH)
        });
        mEmailText.setFilters(new InputFilter[]{
                new EncodeLengthFilter(emailSize)
        });

        mNameText.setText(mTag);
        if (mTag != null) {
            int editTextLength = mNameText.getText().length();
            mNameText.setSelection(editTextLength);
        }

        if (mData != null) {
            mNumberphoneHome = mData.get(0);
            if (!TextUtils.isEmpty(mNumberphoneHome)) {
                mNumberphoneHome = mNumberphoneHome.length() > LIMIT_LENGTH ? mNumberphoneHome
                        .substring(0, LIMIT_LENGTH) : mNumberphoneHome;
            }
            mNumberMobile = mData.get(1);
            if (!TextUtils.isEmpty(mNumberMobile)) {
                mNumberMobile = mNumberMobile.length() > LIMIT_LENGTH ? mNumberMobile.substring(0,
                        LIMIT_LENGTH) : mNumberMobile;
            }
            mNumberWork = mData.get(2);
            if (!TextUtils.isEmpty(mNumberWork)) {
                mNumberWork = mNumberWork.length() > LIMIT_LENGTH ? mNumberWork.substring(0,
                        LIMIT_LENGTH) : mNumberWork;
            }
            mNumberOther = mData.get(3);
            if (!TextUtils.isEmpty(mNumberOther)) {
                mNumberOther = mNumberOther.length() > LIMIT_LENGTH ? mNumberOther.substring(0,
                        LIMIT_LENGTH) : mNumberOther;
            }
            mEmail = mData.get(4);
        }

        String email = intent.getExtras().getString(SIM_EMAIL);
        if(!TextUtils.isEmpty(email)){
            mEmail = email;
        }
        //modify by dory.zheng for huawei call phone can't save to sim begin
        String number = getIntent().getStringExtra("number");
        number = ContactsUtils.CommaAndSemicolonTopAndw(number);
        if(!TextUtils.isEmpty(number)){
			number = number.length() > LIMIT_LENGTH ? number.substring(0,
					LIMIT_LENGTH) : number;
			mNumberHomeText.setText(number);
			mNumberHomeText.setSelection(number.length());
        }else{
            mNumberphoneHome = ContactsUtils.CommaAndSemicolonTopAndw(mNumberphoneHome);
            mNumberMobile = ContactsUtils.CommaAndSemicolonTopAndw(mNumberMobile);
            mNumberWork = ContactsUtils.CommaAndSemicolonTopAndw(mNumberWork);
            mNumberOther = ContactsUtils.CommaAndSemicolonTopAndw(mNumberOther);

            mNumberHomeText.setText(mNumberphoneHome);
            mNumberHomeText.setSelection(mNumberphoneHome != null
                    ? mNumberphoneHome.length() : 0);
            mNumberMoblieText.setText(mNumberMobile);
            mNumberMoblieText.setSelection(mNumberMobile != null
                    ? mNumberMobile.length() : 0);
            mNumberWorkText.setText(mNumberWork);
            mNumberWorkText.setSelection(mNumberWork != null
                    ? mNumberWork.length() : 0);
            mNumberOtherText.setText(mNumberOther);
            mNumberOtherText.setSelection(mNumberOther != null
                    ? mNumberOther.length() : 0);
        }

	//added by dengjing for cr 13915
	boolean isUsim = false;
	if(Config.isMSMS){
		int phoneId = 0;
		if(Account.SIM2_ACCOUNT_NAME.equals(mAccountName))
		    phoneId = 1;
		final TelephonyManager t = (TelephonyManager) getSystemService(PhoneFactory
                                             .getServiceName(Context.TELEPHONY_SERVICE, phoneId));
		isUsim = t.isUsimCard();
		Log.d(TAG, "For dual sim isUsimCard = " + isUsim);
		    		
	}else{
	        final TelephonyManager t = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		isUsim = t.isUsimCard();
                Log.d(TAG, "isUsimCard = " + isUsim);
	}
	
	if(isUsim){		
		if(!TextUtils.isEmpty(mEmail)){
		    mEmailText.setText(mEmail);
		    mEmailText.setSelection(mEmail != null
				? mEmail.length() : 0);
		}
	}
        //modify by dory.zheng for huawei call phone can't save to sim end
        mBtnDone = (Button)this.findViewById(R.id.btn_done);
        mBtnDiscard = (Button)this.findViewById(R.id.btn_discard);

        mBtnDone.setOnClickListener(this);
        mBtnDiscard.setOnClickListener(this);
        mQueryHandler = new QueryHandler(getContentResolver());
        Log.v(TAG, "onCreate end");
     }

    @Override
    protected void onPause() {
        super.onPause();
        CommonUtil.hideSoftKeyboard(this);
    }

    boolean saving = false;
    public void onClick(View v) {
        int btnId = v.getId();

        switch(btnId) {
            case R.id.btn_done:

                if(saving){
                    break;
                }
                try{
                    saving = true;
                    update();
//                    saving = true; //modify by dory.zheng for NEWMS00144355
                }catch (Exception e){
                    e.printStackTrace();
                }
                break;
            case R.id.btn_discard:
                try{
                    finish();
                }catch (Exception e){
                    e.printStackTrace();
                }
        }
    }

    protected Uri getSimUri() {
        return  Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "sim_contacts");
    }

    @Override
    public void onBackPressed() {
        saving = true;
        if (!update()) {
            finish();
        }
    }

    boolean update()
    {
        String newTag = mNameText.getText().toString();    //yeezone:jinwei.trim();
        String newNumberphoneHome = mNumberHomeText.getText().toString();
        String newNumberMobile = mNumberMoblieText.getText().toString();    //yeezone:jinwei.trim();
        String newNumberWork = mNumberWorkText.getText().toString();
        String newNumberOther = mNumberOtherText.getText().toString();
        String newEmail = mEmailText.getText().toString();
	

        //modify by dory.zheng for contacts is replace begin
        if (oldData != null) {
            mNumberphoneHome = oldData.get(0);
            mNumberMobile = oldData.get(1);
            mNumberWork = oldData.get(2);
            mNumberOther = oldData.get(3);
            mEmail = oldData.get(4);
        }
        //modify by dory.zheng for contacts is replace end
        String mAnr = mNumberMobile+AdnRecord.ANR_SPLIT_FLG+mNumberWork+AdnRecord.ANR_SPLIT_FLG+mNumberOther;
        String newAnr = newNumberMobile+AdnRecord.ANR_SPLIT_FLG+newNumberWork+AdnRecord.ANR_SPLIT_FLG+newNumberOther;
//        String mAnr = mNumberphoneHome+";"+mNumberWork+";"+mNumberOther;
//        String newAnr = newNumberphoneHome+";"+newNumberWork+";"+newNumberOther;

        if (anrNum > 0)  { //1
            mSimAnr = mNumberMobile;
            mSimNewAnr = newNumberMobile;
//            mSimAnr = mNumberphoneHome;
//            mSimNewAnr = newNumberphoneHome;
            if (anrNum > 1)  { //2
                mSimAnr += AdnRecord.ANR_SPLIT_FLG + mNumberWork;
                mSimNewAnr += AdnRecord.ANR_SPLIT_FLG + newNumberWork;
//                mSimAnr = mNumberphoneHome + ";" + mNumberWork;
//                mSimNewAnr = newNumberphoneHome + ";" + newNumberWork;
                 if (anrNum > 2)  { //
                     mSimAnr = mAnr;
                     mSimNewAnr = newAnr;
                 }
            }
        }
//        Log.d(TAG, "mTag="+mTag+";mNumberphoneHome="+mNumberphoneHome+";mNumberMobile="+mNumberMobile
//                +";mNumberWork="+mNumberWork+";mNumberOther="+mNumberOther+";mEmail="+mEmail);
//        Log.d(TAG, "mSimAnr="+mSimAnr);
//        Log.d(TAG, "mSimNewAnr="+mSimNewAnr);
//        Log.d(TAG, "newTag="+newTag+";newNumberphoneHome="+newNumberphoneHome+";newNumberMobile="+newNumberMobile+
//                ";newNumberWork="+newNumberWork+";newNumberOther="+newNumberOther+";newEmail="+newEmail);

        if((newTag !=null && !newTag.equals(""))
                ||(newNumberphoneHome !=null && !newNumberphoneHome.equals(""))
                ||(newNumberMobile !=null && !newNumberMobile.equals(""))
                    ||(newNumberWork !=null && !newNumberWork.equals(""))
                            ||(newNumberOther !=null && !newNumberOther.equals(""))
                                ||(newEmail !=null && !newEmail.equals(""))){

        }else{
            saving = false; //modify by dory.zheng for NEWMS00144355
            Toast.makeText(this, R.string.contacts_is_null, Toast.LENGTH_SHORT).show();
            return false;
        }
	if(!Config.isMSMS){
        // check phone number format
        if ((!TextUtils.isEmpty(newNumberphoneHome) && !CommonUtil.isPhoneNumber(newNumberphoneHome))
                || (!TextUtils.isEmpty(newNumberMobile) && !CommonUtil.isPhoneNumber(newNumberMobile))
                || (!TextUtils.isEmpty(newNumberWork) && !CommonUtil.isPhoneNumber(newNumberWork))
                || (!TextUtils.isEmpty(newNumberOther) && !CommonUtil.isPhoneNumber(newNumberOther))) {
            saving = false;
            Toast.makeText(this, R.string.contact_invalid_phone_number_error_toast,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // check email format
        if (!TextUtils.isEmpty(newEmail) && !CommonUtil.isEmailAddress(newEmail)) {
            saving = false;
            Toast.makeText(this, R.string.contact_invalid_email_error_toast,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
	}
        if(mTag == null){
            mTag = "";
        }
        if(mEmail == null){
            mEmail = "";
        }
        if(newTag == null){
            newTag = "";
        }
        if(newEmail == null){
            newEmail = "";
        }
        ContentValues map = new ContentValues();

		//added for dual sim
		if(Config.isMSMS){
		    map.put("account_name", mAccountName);
		    map.put("account_type", Account.SIM_ACCOUNT_TYPE);
		}

		 Log.d(TAG,"update or insert, newAnr:"+newAnr+", mSimNewAnr:"+mSimNewAnr);
        newNumberphoneHome = ContactsUtils.pAndwToCommaAndSemicolon(newNumberphoneHome);
        newAnr = ContactsUtils.CommaAndSemicolonTopAndw(newAnr);

        mNumberphoneHome = ContactsUtils.pAndwToCommaAndSemicolon(mNumberphoneHome);
        mAnr = ContactsUtils.CommaAndSemicolonTopAndw(mAnr);
        if (mMode==MODE_EDIT) {
        	  //old data
            map.put("tag", mTag);
            map.put("number", mNumberphoneHome);
            map.put("anr",mAnr);
            map.put("email", mEmail);

             // new data
            map.put("newTag", newTag);
            map.put("newNumber", newNumberphoneHome);
            map.put("newAnr", newAnr);
            map.put("newEmail", newEmail);
            map.put("sim_index", mSimIndex);
//            map.put("newAnr", mSimNewAnr);

            Intent intent=new Intent();
            intent.putExtra("sim_index", mSimIndex);
            intent.putExtra("tag", newTag);
            intent.putExtra("number", newNumberphoneHome);

//          intent.putExtra("number", newNumberMobile);
            mQueryHandler.startUpdate(0, intent, getSimUri(), map, null, null);
        }else if (mMode==MODE_INSERT) {
            map.put("newTag", newTag);
            map.put("newNumber", newNumberphoneHome);
            map.put("newAnr", newAnr);
            map.put("newEmail", newEmail);
            mQueryHandler.startInsert(0, null, getSimUri(), map);
        }

        return true;
    }
    //<UH, 2010-04-17, Yoyo yang, Add "current edited contact delete function in SIM card"/>
     /**
      * no use the method
      */
    private DialogInterface.OnClickListener mDeleteContactDialogListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            String[] selectionArgs = { mSimIndex };
       /*     try {
                mProgressDialog = ProgressDialog.show(SimEditContactActivity.this, null, getText(R.string.on_progress));
            } catch (Exception e) {
                mProgressDialog.dismiss();
            }*/

            mQueryHandler.startDelete(0, null, getSimUri(), "sim_index=?", selectionArgs);
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DELETE_CONFIRMATION_DIALOG:
                return new AlertDialog.Builder(SimEditContactActivity.this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteContactDialogListener)
                        .setCancelable(false)
                        .create();
        }
        return super.onCreateDialog(id);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
//            menu.add(0, MENU_ITEM_INSERT, 0, R.string.menu_newContact)
//            .setIcon(android.R.drawable.ic_menu_add);
//            menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
//            .setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE:
                // Get confirmation
                //modify by dory.zheng for NEWMS00138007 begin
                if(!SimInitReceiver.isDelete){
                    showDialog(DELETE_CONFIRMATION_DIALOG);
                }else{
                    Toast.makeText(SimEditContactActivity.this, R.string.deleting, Toast.LENGTH_SHORT).show();
                }
                //modify by dory.zheng for NEWMS00138007 end
                return true;
            case MENU_ITEM_INSERT:
                // Get confirmation
                mNameText.setText("");
                mNumberHomeText.setText("");
                mNumberMoblieText.setText("");
                mNumberWorkText.setText("");
                mNumberOtherText.setText("");
                mEmailText.setText("");
                mMode=MODE_INSERT;
                return true;
        }
        return false;
    }



    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            // mProgressDialog.dismiss();
            if ( result > 0) {
                Toast.makeText(SimEditContactActivity.this, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SimEditContactActivity.this, R.string.contactSavedErrorToast, Toast.LENGTH_SHORT).show();
            }
            setResult(Activity.RESULT_OK, (Intent)cookie);
            finish();
        }
        @Override
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            if (null == uri) {
                saving = false;
                Toast.makeText(SimEditContactActivity.this,
                        R.string.save_contact_faliue, Toast.LENGTH_LONG)
                        .show();
                return;
            }
            // mProgressDialog.dismiss();
            // Intent intent = new Intent();
            // intent.setClassName("com.android.contacts", "com.android.contacts.ContactsListActivity");
            // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // startActivity(intent);
            Toast.makeText(SimEditContactActivity.this, R.string.add_contact_success,
                    Toast.LENGTH_LONG).show();
            
            //for bugzilla 13896, the uri got by single is different from dual sim
            if(Config.isMSMS){
                final Intent resultIntent = new Intent();
                Uri lookupUri = RawContacts.getContactLookupUri(getContentResolver(),uri);
                resultIntent.setData(lookupUri);
                setResult(RESULT_OK, resultIntent);
            }

            finish();
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            // mProgressDialog.dismiss();
            finish();
        }
    }

    private int getFreeCapacity(Context context) {
        int total = getSimCardLength();
        //NEWMS00170745
        if (total == 0 || total == -1) 
            return -1;
        int ContactorNum = getSimContactorNum(context);
        int remain = total - ContactorNum;
        Log.d(TAG, mAccountName+"'s Free Capacity is "+remain);
        return remain < 0 ? -1 : remain;
    }

    /**
     * Get capacity of SIM card
     *
     * @param phoneId
     * @return
     */
    public int getSimCardLength() {

        int size = -1;
        try {
            IIccPhoneBook iccIpb = getIccPhoneBook();
            if (iccIpb != null) {
                int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                if(sizes != null){
                    if (sizes.length == 3){
                        size = sizes[2];
                    } else if(sizes.length == 2){
                        size = sizes[1] / sizes[0];
                    }
                }
            }
        } catch (RemoteException ex) {
            Log.d(TAG, "RemoteException: " + ex.toString());
        } catch (SecurityException ex) {
            Log.d(TAG, "SecurityException: " + ex.toString());
        }
        return size;
    }

    private IIccPhoneBook getIccPhoneBook(){

        //added for dual sim
        IIccPhoneBook iccIpb;
        if(Config.isMSMS){
            if(Account.SIM1_ACCOUNT_NAME.equals(mAccountName)){
                iccIpb= IIccPhoneBook.Stub.asInterface(
                        ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", 0)));
            }
            else{
                iccIpb= IIccPhoneBook.Stub.asInterface(
                        ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", 1)));
            }
        }
        else{
            iccIpb= IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
        }
        return iccIpb;
    }

    /**
     * Get number of contactor in Sim card
     *
     * @param context
     * @param phoneId
     * @return
     */
    private int getSimContactorNum(Context context) {
        Cursor cur = null;
	//String simUri = "content://icc/adn";
	Uri simUri = SimUtils.SIM1_URI;
        //added for dual sim
        String selection = null;
        if(Config.isMSMS){
	    if(Account.SIM2_ACCOUNT_NAME.equals(mAccountName))
	//	simUri = "content://icc1/adn";
		simUri = SimUtils.SIM2_URI;
	    
        }
        else{
            selection = "sim_index<>0 ";
        }
        try {
           if(Config.isMSMS){ 
	        cur = context.getContentResolver().query(simUri, null, null, null, null);
	    	Log.d(TAG, "mAccountname =  " + mAccountName + "maccount uri = " + simUri);
	   }else {
                cur = context.getContentResolver().query(Contacts.CONTENT_URI, CONTACTS_SUMMARY_PROJECTION, selection, null, null);
	    }
            Log.d(TAG, "cur.getCount  = " + (cur == null ? "cursor is null" : cur.getCount()));
            return null == cur ? -1 : cur.getCount();
        } finally {
            if(cur != null){
                cur.close();
                cur = null;
            }
        }
    }

}
