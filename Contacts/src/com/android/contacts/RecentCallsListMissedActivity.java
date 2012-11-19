package com.android.contacts;

public class RecentCallsListMissedActivity extends MsmsRecentCallsListActivity{

    @Override
    protected int getCallType() {
        return RecentCallsListActivity.CALL_TYPE_SHOW_MISSED;
    }

}
