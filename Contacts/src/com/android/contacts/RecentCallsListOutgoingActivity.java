package com.android.contacts;

public class RecentCallsListOutgoingActivity extends MsmsRecentCallsListActivity{

    @Override
    protected int getCallType() {
        return RecentCallsListActivity.CALL_TYPE_SHOW_OUTGOING;
    }

}
