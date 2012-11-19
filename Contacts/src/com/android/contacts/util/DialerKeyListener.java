package com.android.contacts.util;

import android.text.InputType;
import android.text.method.NumberKeyListener;

public class DialerKeyListener extends NumberKeyListener
{
    @Override
    protected char[] getAcceptedChars()
    {
        return CHARACTERS;
    }

    public static DialerKeyListener getInstance() {
        if (sInstance != null)
            return sInstance;

        sInstance = new DialerKeyListener();
        return sInstance;
    }

    public int getInputType() {
        return InputType.TYPE_CLASS_PHONE;
    }


    /**
     * The characters that are used.
     *
     * @see KeyEvent#getMatch
     * @see #getAcceptedChars
     */
    public static final char[] CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*',
            '+', '(', ')',  '/', ' ', 'P','p','W','w','-'
        };

    private static DialerKeyListener sInstance;
}

