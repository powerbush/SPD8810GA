
package com.android.contacts;

import android.text.TextUtils;

public class ContactEntity {

    /**
     * display name
     */
    private String displayName;

    /**
     * temp name
     * for save the ori name when the displayName changed.
     */
    private String tempName;

    /**
     * phone number
     */
    private String phoneNumber;

    /**
     * spell name, parsered by Pinyin
     */
    private String spellName;

    /**
     * every chinese character match along with one item of the spellNames
     * array.
     */
    private String[] spellNames;

    /**
     * judge whether it's name matched or not.
     */
    private boolean nameMatched;

    /**
     * judge whether it's full name matched or not.
     */
    private boolean fullNameMatched;

    /**
     * judge whether it's phone number matched or not.
     */
    private boolean phoneNumberMatched;

    /**
     * contact id
     */
    private long id;

    /**
     * contact photo id
     */
    private String photoId;

    /**
     * call log type
     */
    private int callLogType;

    /**
     * SIM account name
     */
    private String accountName;
    /**
     * SIM index
     */
    private String simIndex;

    public String getDisplayName() {
        return displayName;
    }

	public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTempName() {
        return tempName;
    }

    public void setTempName(String tempName) {
        this.tempName = tempName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getSpellName() {
        return TextUtils.isEmpty(spellName) ? (TextUtils.isEmpty(phoneNumber) ? "" : phoneNumber)
                : spellName;
    }

    public void setSpellName(String spellName) {
        this.spellName = spellName;
    }

    public String[] getSpellNames() {
        return spellNames;
    }

    public void setSpellNames(String[] spellNames) {
        this.spellNames = spellNames;
    }

    public boolean isNameMatched() {
        return nameMatched;
    }

    public void setNameMatched(boolean nameMatched) {
        this.nameMatched = nameMatched;
    }

    public boolean isFullNameMatched() {
        return fullNameMatched;
    }

    public void setFullNameMatched(boolean fullNameMatched) {
        this.fullNameMatched = fullNameMatched;
    }

    public boolean isPhoneNumberMatched() {
        return phoneNumberMatched;
    }

    public void setPhoneNumberMatched(boolean phoneNumberMatched) {
        this.phoneNumberMatched = phoneNumberMatched;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public int getCallLogType() {
        return callLogType;
    }

    public void setCallLogType(int callLogType) {
        this.callLogType = callLogType;
    }

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getSimIndex() {
		return simIndex;
	}

	public void setSimIndex(String simIndex) {
		this.simIndex = simIndex;
	}

}
