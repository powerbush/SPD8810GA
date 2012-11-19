/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.util;

import android.app.Service;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContacts;

/**
 * Background {@link Service} that is used to keep our process alive long enough
 * for background threads to finish. Started and stopped directly by specific
 * background tasks when needed.
 */
public class Constants {
    /**
     * Specific MIME-type for {@link Phone#CONTENT_ITEM_TYPE} entries that
     * distinguishes actions that should initiate a text message.
     */
    public static final String MIME_SMS_ADDRESS = "vnd.android.cursor.item/sms-address";

    public static final String SCHEME_TEL = "tel";
    public static final String SCHEME_SMSTO = "smsto";
    public static final String SCHEME_MAILTO = "mailto";
    public static final String SCHEME_IMTO = "imto";
    public static final String SCHEME_SIP = "sip";

    public static final String ACTION_ADD_BLACKLIST = "com.yeezonetech.firewall.ui.BlackCallsListAddActivity.action";
    public static final String ACTION_BLACK = "com.yeezonetech.blacklist.action.BLACK_List";


    public static final String SIM_ACCOUNT_NAME = "SIM";
    public static final String SIM_ACCOUNT_TYPE = "com.android.huawei.sim";
    public static final String NOT_DISPLAY_PHONE_CONTACTS = RawContacts.ACCOUNT_NAME + " IS NOT NULL"
            + " AND " + RawContacts.ACCOUNT_NAME + "<>\'\'"
            + " AND " + RawContacts.ACCOUNT_TYPE + " IS NOT NULL"
            + " AND " + RawContacts.ACCOUNT_TYPE + "<>\'\'";
    public static final String NOT_DISPLAY_SIM_CONTACTS = "sim_index=0";
    // the state that ADN cache is ready or not
    public static final int ADNCACHE_STATE_NOT_READY = 0;
    public static final int ADNCACHE_STATE_READY = 1;

    public static final String PHONE_NUMBER_CHECK_EXPR = "^([0-9]|\\+|\\,|\\;|\\*|\\#|N|n|\\-)+$";
}
