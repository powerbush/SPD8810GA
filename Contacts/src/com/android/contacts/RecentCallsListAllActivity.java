package com.android.contacts;

public class RecentCallsListAllActivity extends MsmsRecentCallsListActivity {

    @Override
    protected int getCallType() {
        return RecentCallsListActivity.CALL_TYPE_SHOW_ALL;
    }

}

