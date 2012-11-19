
package com.android.mms.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;

public class ContactContentObserber extends ContentObserver {
    private static String TAG = "ContactContentObserber";
    private static boolean isUpdateThreadsRunning = false;

    private static boolean isRunAgain = false;

    private Context mContext;

    private static Uri THREADS_ALL = Uri.parse("content://mms-sms/threads-all");

    private int ID = 0;

    private int RECIPIENT_ADDRESSES = 1;

    private int RECIPIENT_NAMES = 2;

    public ContactContentObserber(Handler handler, Context context) {
        super(handler);
        mContext = context;
        //threadStart(true);
    }

    public static void init(Context context) {
        ContactContentObserber observer = new ContactContentObserber(new Handler(), context);
        context.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                true, observer);
        observer.threadStart(true);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        threadStart(false);
    }

    private synchronized void threadStart(final boolean first) {
        if (!isUpdateThreadsRunning) {
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    isUpdateThreadsRunning = true;
                    updateThreads(first);

                    if (isRunAgain) {
                        isRunAgain = false;
                        threadStart(false);
                    }
                }
            });
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        } else {
            isRunAgain = true;
        }
    }

    private void sleep(Cursor threads) {
        int sleepTime = 10000;
        if (threads.getCount() > 200) {
            sleepTime = 40000;
        }
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void updateThreads(boolean first) {
        Cursor threads = mContext.getContentResolver().query(THREADS_ALL, null, null, null, null);
        if (threads == null) {
            return;
        }

        if (first) {
            sleep(threads);
        }
        try {
            threads.moveToPosition(-1);
            while (threads.moveToNext()) {
                String number = threads.getString(RECIPIENT_ADDRESSES);
                String[] numbers = number.split(" ");

                String name = threads.getString(RECIPIENT_NAMES);
                Log.d(TAG, "thread number size :" + numbers.length);
                if (numbers.length > 1) {
                    String[] names = name.split(" ");
                    if ( names.length != numbers.length ) {
                        names = new String[numbers.length];
                    }
                    boolean change = false;
                    for (int i = 0; i < numbers.length; i++) {
                        String num = numbers[i];
                        Contact contact = Contact.sync_get(num);
                        if ( names[i] != null && contact.getName().equals(names[i])) {
                            continue;
                        }
                        change = true;
                        names[i] = contact.getName();

                    }

                    if (change) {
                        String nameStr = "";
                        for (int i = 0; i < names.length; i++) {

                            if (i > 0) {
                                nameStr += " ";
                            }
                            nameStr += names[i];

                        }
                        ContentValues values = new ContentValues();
                        values.put("recipient_names", nameStr);
                        mContext.getContentResolver().update(THREADS_ALL, values,
                                "_id = " + threads.getShort(ID), null);
                    }

                } else {
                    Contact contact = Contact.sync_get(number);
                    if (contact.getName().equals(name)) {
                        continue;
                    }
                    Log.d(TAG,
                            "====one phone number--name-b:" + name + ";name-n:"
                                    + contact.getName());
                    ContentValues values = new ContentValues();
                    values.put("recipient_names", contact.getName());
                    mContext.getContentResolver().update(THREADS_ALL, values,
                            "_id = " + threads.getShort(ID), null);
                }
                Thread.yield();
            }
        } finally {
            threads.close();
            isUpdateThreadsRunning = false;
        }
    }

}
