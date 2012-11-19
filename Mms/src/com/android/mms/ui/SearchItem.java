package com.android.mms.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SearchItem extends LinearLayout {

    private long threadId = 0L;
    private long rowid = 0L;
    private String searchString = null;
    
    
    public SearchItem(Context context) {
        super(context);
    }
    
    public SearchItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }



    public long getThreadId() {
        return threadId;
    }



    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }



    public long getRowid() {
        return rowid;
    }



    public void setRowid(long rowid) {
        this.rowid = rowid;
    }



    public String getSearchString() {
        return searchString;
    }



    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

}
