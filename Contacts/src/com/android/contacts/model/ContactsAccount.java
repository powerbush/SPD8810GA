package com.android.contacts.model;

import android.net.Uri;

public class ContactsAccount {

	private String account_name;
	private String account_type;
    private Uri account_uri;

    public ContactsAccount(String accountName, String accountType, Uri accountUri){
    	this.account_name = accountName;
    	this.account_type = accountType;
    	this.account_uri = accountUri;
    }

    public void setContactsAccountName(String name){
    	this.account_name = name;
    }
    public void setContactsAccountType(String type){
    	this.account_type = type;
    }

    public void setContactsAccountUri(Uri uri){
    	this.account_uri = uri;
    }

    public String getContactsAccountName(){
    	return this.account_name;
    }

    public String getContactsAccountType(){
    	return this.account_type;
    }

    public Uri getContactsAccountUri(){
    	return this.account_uri;
    }

}
