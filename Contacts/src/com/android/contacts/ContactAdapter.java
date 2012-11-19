
package com.android.contacts;

import com.android.contacts.util.Config;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.PhoneFactory;

import android.accounts.Account;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;

public class ContactAdapter extends BaseAdapter {
    private static final String TAG = "ContactAdapter";

    public static final int RESUME = 0;
    public static final int PAUSE = 1;
    public static final int STOP = 2;
    public static final int CLEAR = 3;

    private ContactPhotoLoader mPhotoLoader;
    private Context context;

    /**
     * the final list of contact & call log that be shown
     */
    private ArrayList<ContactEntity> mList = new ArrayList<ContactEntity>();

    LayoutInflater mInflater;

    /**
     * search string
     */
    private String mSearchStr = "";

    // make click reflection a little faster End
    /**
     * NUMBER_ALPHABETS[0]:2('a','b','c'),...
     */
    private final char[][] NUMBER_ALPHABETS = {
            {
                    'a', 'b', 'c'
            }, {
                    'd', 'e', 'f'
            }, {
                    'g', 'h', 'i'
            }, {
                    'j', 'k', 'l'
            }, {
                    'm', 'n', 'o'
            }, {
                    'p', 'q', 'r', 's'
            }, {
                    't', 'u', 'v'
            }, {
                    'w', 'x', 'y', 'z'
            }
    };

    public ContactAdapter(Context context, ArrayList<ContactEntity> clist) {
        this.context = context;
        mInflater = LayoutInflater.from(context);
        mPhotoLoader = new ContactPhotoLoader(context, R.drawable.ic_contact_list_picture);
        setList(clist);
    }

    public int getCount() {
        return mList.size();
    }

    public ContactEntity getItem(int position) {
        return mList.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    // make click reflection a little faster Start
    class ViewHolder {
        ImageView contactPhoto;
        TextView contactName;
        TextView contactNumber;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        Log.d(TAG, "getView");
        ViewHolder holder = null;

        if (convertView == null && mInflater != null) {
            convertView = mInflater.inflate(R.layout.dialer_contacts_item, parent, false);
            holder = new ViewHolder();
            holder.contactPhoto = (ImageView) convertView.findViewById(R.id.photo);
            holder.contactName = (TextView) convertView.findViewById(R.id.contactName);
            holder.contactNumber = (TextView) convertView.findViewById(R.id.contactNumber);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ContactEntity ce = getItem(position);

        int photoId = 0;
        String accountName = ce.getAccountName();
        String simIndex = ce.getSimIndex();
        if (0 != ce.getCallLogType()) {
            switch (ce.getCallLogType()) {
	//bond mod 20120710 begin		
	/*
                case CallLog.Calls.INCOMING_TYPE:
                    photoId = R.drawable.ic_call_log_list_incoming_call;    
                    break;

                case CallLog.Calls.OUTGOING_TYPE:
                    photoId = R.drawable.ic_call_log_list_outgoing_call;
                    break;

                case CallLog.Calls.MISSED_TYPE:
                    photoId = R.drawable.ic_call_log_list_missed_call;
                    break;
                default:
                    break;
                    */
                case CallLog.Calls.INCOMING_TYPE:
                    photoId = R.drawable.ic_dialer_header_incoming_call;    
                    break;

                case CallLog.Calls.OUTGOING_TYPE:
                    photoId = R.drawable.ic_dialer_header_outgoing_call;
                    break;

                case CallLog.Calls.MISSED_TYPE:
                    photoId = R.drawable.ic_dialer_header_missed_call;
                    break;
                default:
                    break;
	//bond mod 20120710 end
					
            }
            holder.contactPhoto.setImageResource(photoId);
        } else if (!TextUtils.isEmpty(ce.getPhotoId())) {
            try {
                photoId = Integer.parseInt(ce.getPhotoId());
            } catch (NumberFormatException e) {
                photoId = 0;
            }
            mPhotoLoader.loadPhoto(holder.contactPhoto, photoId);
		} else if (Account.SIM1_ACCOUNT_NAME.equals(accountName)) {
			photoId = R.drawable.ic_menu_add_sim1;
			mPhotoLoader.loadPhoto(holder.contactPhoto, photoId);
		} else if (Account.SIM2_ACCOUNT_NAME.equals(accountName)) {
			photoId = R.drawable.ic_menu_add_sim2;
			mPhotoLoader.loadPhoto(holder.contactPhoto, photoId);
		} else {
			if(!"0".equals(simIndex)){
				photoId = R.drawable.ic_sim_contact_list_picture;
				mPhotoLoader.loadPhoto(holder.contactPhoto, photoId);
			} else {
				mPhotoLoader.loadPhoto(holder.contactPhoto, 0);
			}
		}

        holder.contactNumber.setVisibility(View.VISIBLE);
        if (ce.getPhoneNumber() != null) {
            TelephonyManager tm1 = (TelephonyManager) context.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, 0));
            TelephonyManager tm2 = (TelephonyManager) context.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, 1));
            String vmNumber1 = (tm1 != null ? tm1.getVoiceMailNumber() : "");
            String vmNumber2 = (tm2 != null ? tm2.getVoiceMailNumber() : "");

            if ((ce.getPhoneNumber()).equals(CallerInfo.UNKNOWN_NUMBER)) {
                ce.setTempName(ce.getDisplayName());
                ce.setDisplayName(context.getString(R.string.unknown));
                holder.contactNumber.setVisibility(View.INVISIBLE);
            } else if ((ce.getPhoneNumber()).equals(CallerInfo.PRIVATE_NUMBER)) {
                ce.setTempName(ce.getDisplayName());
                ce.setDisplayName(context.getString(R.string.private_num));
                holder.contactNumber.setVisibility(View.INVISIBLE);
            } else if ((ce.getPhoneNumber()).equals(CallerInfo.PAYPHONE_NUMBER)) {
                ce.setTempName(ce.getDisplayName());
                ce.setDisplayName(context.getString(R.string.payphone));
            } else if (PhoneNumberUtils.extractNetworkPortion(ce.getPhoneNumber()).equals(vmNumber1)
                    || PhoneNumberUtils.extractNetworkPortion(ce.getPhoneNumber()).equals(vmNumber2)) {
                ce.setTempName(ce.getDisplayName());      //save the ori name when the displayName changed.
                ce.setDisplayName(context.getString(R.string.voicemail));
            } else if (!TextUtils.isEmpty(ce.getTempName())) {
                ce.setDisplayName(ce.getTempName());
            }
        }
        Log.d(TAG,
                "getView mPhoneNumber:" + ce.getPhoneNumber() + " ce.mDisplayName:"
                        + ce.getDisplayName() + " mSearchStr:" + mSearchStr);

        if (mSearchStr != null && !"".equals(mSearchStr)) {
            if(holder != null ) {
                if (ce.isPhoneNumberMatched() && holder.contactNumber != null) {
                    // make click reflection a little faster Start
                    // String phone_number = ce.mPhoneNumber.replaceAll(mSearchStr
                    // .replaceAll("[*]", "[*]").replaceAll("[+]", "[+]"),
                    // "<font color='red'>" + mSearchStr + "</font>");
                    // contactNumber.setText(Html.fromHtml(phone_number));
                    holder.contactNumber.setText(transferStringBySearchWord(SpecialTextViewTool.commaSemicolonToPW(ce.getPhoneNumber()),
                            SpecialTextViewTool.commaSemicolonToPW(mSearchStr)));
                    // make click reflection a little faster End
                } else {
                    holder.contactNumber.setText(SpecialTextViewTool.commaSemicolonToPW(ce.getPhoneNumber()));
                }
                if (holder.contactName != null) {
                    if (ce.isNameMatched()) {
						Log.d(TAG, "getView is name matched!");
                        StringBuffer sb = new StringBuffer(ce.getDisplayName());
                        insertHtmlTag(sb, mSearchStr.length());
                        holder.contactName.setText(Html.fromHtml(sb.toString()));
                    } else if (ce.isFullNameMatched()) {
						Log.d(TAG, "getView is full name matched!");
                        int echoIndex = 1;
                        int remainLength = mSearchStr.length();
                        if (ce.getSpellNames() != null) {
                            for (int i = 0; i < ce.getSpellNames().length; i++) {
                                if (ce.getSpellNames()[i] != null) {
                                    remainLength = remainLength - ce.getSpellNames()[i].length();
                                }
                                if ((remainLength) > 0) {
                                    echoIndex++;
                                } else {
                                    break;
                                }
                            }
                        }
                        StringBuffer sb = new StringBuffer(ce.getDisplayName());
                        insertHtmlTag(sb, echoIndex);
                        holder.contactName.setText(Html.fromHtml(sb.toString()));
                    } else {
                        holder.contactName.setText(ce.getDisplayName());
                    }
                }
            }
        } else{
            if (holder.contactNumber != null) {
                holder.contactNumber.setText(SpecialTextViewTool.commaSemicolonToPW(ce.getPhoneNumber()));
            }
            if (holder.contactName != null) {
                holder.contactName.setText(ce.getDisplayName());
            }
        }
        return convertView;
    }

    private void insertHtmlTag(StringBuffer sb, int endIndex) {
        try {
            sb.insert(endIndex, "</font>");
            sb.insert(0, "<font color='red'>");
        } catch (Exception e) {
            Log.e(TAG, "insertHtmlTag", e);
            Log.e(TAG, "insertHtmlTag endIndex:" + String.valueOf(endIndex));
            Log.e(TAG, "insertHtmlTag sb:" + sb.toString());
        }
    }

    /**
     * filter mAdapter
     * 
     * @param constraint
     * @param mCustomArrayList
     */
    public void filter(String constraint, ArrayList<ContactEntity> mCustomArrayList) {
        if (mCustomArrayList.size() > 0) {
            binSearch(constraint, mCustomArrayList);
            notifyDataSetChanged();
        }
    }

    /**
     * Binsearch.
     * 
     * @param constraint The string to compare with.
     * @param mCustomArrayList
     */
    private void binSearch(String constraint, ArrayList<ContactEntity> mCustomArrayList) {
        Log.d(TAG, "binSearch");
        if (TextUtils.isEmpty(constraint)) {
            mSearchStr = "";
            setList(mCustomArrayList);
        } else {
            mList.clear();
            // intelligent match
            ContactEntity contact;
            for (int i = 0; i < mCustomArrayList.size(); i++) {
                contact = mCustomArrayList.get(i);
                /*
                 * we need to judge both number match or name match.
                 */
                // whether phone number is matched or not
                if (contact != null) {
                    contact.setPhoneNumberMatched(false);
                    contact.setNameMatched(false);
                    contact.setFullNameMatched(false);
                    if (contact.getPhoneNumber() != null
                            && (-1 != contact.getPhoneNumber().replace("-", "").indexOf(constraint))) {
                        contact.setPhoneNumberMatched(true);
                        mList.add(contact);
                    }

                    // whether name is matched or not
                    if (nameMatch(contact.getSpellNames(), constraint)) {
                        contact.setNameMatched(true);
                        if (!mList.contains(contact)) {
                            mList.add(contact);
                        }
                    } else if (fullNameMatch(contact.getSpellName(), constraint)) {
                        contact.setFullNameMatched(true);
                        if (!mList.contains(contact)) {
                            mList.add(contact);
                        }
                    }
                }
            }
            mSearchStr = constraint;
        }
        mPhotoLoader.clear();
    }

    /**
     * Match Rule: The initial letter in every Chinese character being matched
     * 
     * @param spellNames
     * @param constraint The numbers that user pressed.
     * @return return true if name matched.
     */
    private boolean nameMatch(String[] spellNames, String constraint) {

        if (spellNames == null || constraint == null) {
            return false;
        }
        // number -- alphabet
        if (constraint.length() > spellNames.length) {
            return false;
        }

        boolean isMatched = false;
        int size = constraint.length();

        /*
         * get the first character in one chinese character, and compare it with
         * the corresponding character in the constraint. if matched, continue
         * it; otherwise return false.
         */
        for (int i = 0; i < size; i++) {
            String spell = spellNames[i];

            try {
                // get every character in the constraint
                if (isInvolved(spell.charAt(0), constraint.charAt(i))) {
                    isMatched = true;
                    continue;
                }
            } catch (NumberFormatException nfe) {
                /*
                 * if NumberFormatException occurred, we can conclude that maybe
                 * other keys was pressed by user, so we compare the key value
                 * with the one character in the constraint directly.
                 */
                if (spell.charAt(0) == constraint.charAt(i)) {
                    isMatched = true;
                    continue;
                }
            }
            isMatched = false;
            break;
        }
        return isMatched;
    }

    /**
     * Match Rule: The full letter in every Chinese character being matched
     * 
     * @param mSpellName
     * @param constraint
     * @return true if full name matched.
     */
    private boolean fullNameMatch(String mSpellName, String constraint) {
        if (mSpellName == null || constraint == null) {
            return false;
        }
        if (constraint.length() > mSpellName.length()) {
            return false;
        }
        boolean isMatched = false;
        for (int i = 0; i < constraint.length(); i++) {
            try {
                if (isInvolved(mSpellName.charAt(i), constraint.charAt(i))) {
                    isMatched = true;
                    continue;
                }
            } catch (NumberFormatException e) {
                if (mSpellName.charAt(i) == constraint.charAt(i)) {
                    isMatched = true;
                    continue;
                }
            }
            isMatched = false;
            break;
        }
        return isMatched;
    }

    /**
     * to decide whether the character is in the characters that the special
     * number stated.
     * 
     * @param ch
     * @param number the number button, related index of NUMBER_ALPHABETS
     * @return
     */
    private boolean isInvolved(char ch, char inputChar) throws NumberFormatException{
		Log.d(TAG, "--isInvolved(ch = " + ch + ", inputChar = " + inputChar + ")...");
        if (ch == inputChar) {
            return true;
        }
        if (inputChar < '2' || inputChar > '9' ) {
            return false;
        }
        int number = inputChar -'0';
        Log.d(TAG, "inputNumber:"+number);
        int index = number - 2;
        if (index < 0 || index >= NUMBER_ALPHABETS.length) {
            return false;
        }

        if (ch >='A' && ch <='Z') {
            ch = (char)(ch + ('a'-'A'));
        }
        for (int i = 0; i < NUMBER_ALPHABETS[index].length; i++) {
            if (NUMBER_ALPHABETS[index][i] == ch) {
                return true;
            }
        }
        return false;
    }

    // make click reflection a little faster Start
    private SpannableString transferStringBySearchWord(String phoneNum, String searchWord) {
        String mPhoneNum = new String(phoneNum);
        SpannableString result = new SpannableString(mPhoneNum);
        int length = searchWord.length();
        int index = mPhoneNum.indexOf(searchWord);
        while (index != -1) {
            result.setSpan(new ForegroundColorSpan(Color.RED), index, index + length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            index = mPhoneNum.indexOf(searchWord, index + 1);
        }
        return result;
    }

    public void setList(ArrayList<ContactEntity> mCustomArrayList) {
        mList.clear();
        mList.addAll(mCustomArrayList);
        mPhotoLoader.clear();
    }

    public void controlPhotoLoader(int command) {
        if (mPhotoLoader != null) {
            switch (command) {
                case RESUME:
                    mPhotoLoader.resume();
                    break;
                case PAUSE:
                    mPhotoLoader.pause();
                    break;
                case STOP:
                    mPhotoLoader.stop();
                    break;
                case CLEAR:
                    mPhotoLoader.clear();

                default:
                    break;
            }
        }
    }

}
