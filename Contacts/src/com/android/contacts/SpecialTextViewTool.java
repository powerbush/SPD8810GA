
package com.android.contacts;

import android.text.method.SingleLineTransformationMethod;
import android.widget.TextView;

public class SpecialTextViewTool {
    public static void specialDisplayPWForPhoneNumberTextView(TextView textView) {
        textView.setTransformationMethod(t);
    }

    public static String commaSemicolonToPW(String phoneNumberStr) {
        String retStr = "";
        if (phoneNumberStr == null || phoneNumberStr.length() == 0) {
            return retStr;
        }
        if (phoneNumberStr.indexOf(',') != -1) {
            phoneNumberStr = phoneNumberStr.replace(",", "P");
        }
        if (phoneNumberStr.indexOf(';') != -1) {
            phoneNumberStr = phoneNumberStr.replace(";", "W");
        }
        return phoneNumberStr;
    }

    private static specialReplacementTransformationMethod t = new specialReplacementTransformationMethod();

    static class specialReplacementTransformationMethod extends SingleLineTransformationMethod {

        @Override
        protected char[] getOriginal() {
            // TODO Auto-generated method stub
            char[] singleLineCharArray = super.getOriginal();
            int singleLineLength = singleLineCharArray.length;
            char[] retOriginalCharArray = new char[singleLineLength + 2];
            System.arraycopy(singleLineCharArray, 0, retOriginalCharArray, 0, singleLineLength);
            retOriginalCharArray[singleLineLength] = ',';
            retOriginalCharArray[singleLineLength + 1] = ';';
            return retOriginalCharArray;
        }

        @Override
        protected char[] getReplacement() {
            // TODO Auto-generated method stub
            char[] singleLineReplaceCharArray = super.getOriginal();
            int singleLineReplaceLength = singleLineReplaceCharArray.length;
            char[] retOriginalReplaceCharArray = new char[singleLineReplaceLength + 2];
            System.arraycopy(singleLineReplaceCharArray, 0, retOriginalReplaceCharArray, 0,
                    singleLineReplaceLength);
            retOriginalReplaceCharArray[singleLineReplaceLength] = 'P';
            retOriginalReplaceCharArray[singleLineReplaceLength + 1] = 'W';
            return retOriginalReplaceCharArray;
        }

    }

}
