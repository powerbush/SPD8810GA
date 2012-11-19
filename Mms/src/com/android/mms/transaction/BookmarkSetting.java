
package com.android.mms.transaction;

import com.android.mms.transaction.OtaConfigVO.OtaBookMark;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.provider.Browser;
import android.util.Log;

import java.util.Date;

public class BookmarkSetting {
    private static final String TAG = "MMS.BookmarkSetting";

    public void setBookmark(Context context, OtaConfigVO data) {
        ContentValues contentValues = new ContentValues();
        Cursor cursor = null;
        ContentResolver cr = context.getContentResolver();
        for (OtaBookMark bookmark : data.bmList) {
            //this method will show a dialog.
            //Browser.saveBookmark(context, bookmark.bmName, bookmark.bmUrl);
            long creationTime = new Date().getTime();
            try {
                cursor = Browser.getVisitedLike(context.getContentResolver(), bookmark.bmUrl);
                if (cursor.moveToFirst()
                        && cursor.getInt(Browser.HISTORY_PROJECTION_BOOKMARK_INDEX) == 0) {
                    contentValues.put(Browser.BookmarkColumns.CREATED, creationTime);
                    contentValues.put(Browser.BookmarkColumns.TITLE, bookmark.bmName);
                    contentValues.put(Browser.BookmarkColumns.BOOKMARK, 1);
                    cr.update(Browser.BOOKMARKS_URI, contentValues, "_id = " + cursor.getInt(0),
                            null);
                } else {
                    int count = cursor.getCount();
                    boolean matchedTitle = false;
                    for (int i = 0; i < count; i++) {
                        cursor.moveToPosition(i);
                        if (cursor.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX).equals(
                                bookmark.bmName)) {
                            contentValues.put(Browser.BookmarkColumns.CREATED, creationTime);
                            cr.update(Browser.BOOKMARKS_URI, contentValues,
                                    "_id = " + cursor.getInt(0), null);
                            matchedTitle = true;
                            break;
                        }
                    }
                    if (!matchedTitle) {
                        contentValues.put(Browser.BookmarkColumns.TITLE, bookmark.bmName);
                        contentValues.put(Browser.BookmarkColumns.URL, bookmark.bmUrl);
                        contentValues.put(Browser.BookmarkColumns.CREATED, creationTime);
                        contentValues.put(Browser.BookmarkColumns.BOOKMARK, 1);
                        contentValues.put(Browser.BookmarkColumns.DATE, 0);
                        int visits = 0;
                        if (count > 0) {
                            visits = cursor.getInt(Browser.HISTORY_PROJECTION_VISITS_INDEX);
                        }
                        contentValues.put(Browser.BookmarkColumns.VISITS, visits + 3);
                        cr.insert(Browser.BOOKMARKS_URI, contentValues);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "add setting error", e);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }
        if (context != null) {
//            Toast.makeText(context, R.string.added_to_bookmarks, Toast.LENGTH_LONG).show();
        }
    }

}
