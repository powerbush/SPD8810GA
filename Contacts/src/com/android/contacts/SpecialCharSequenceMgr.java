/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.contacts;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneFactory;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.EditText;
import com.android.contacts.ui.SimUtils;
import android.widget.Toast;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
	private static final String DM_SETTING = "#*4560#"; // Liuhongxing 20110602

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    static boolean handleChars(Context context, String input, EditText textField) {
        return handleChars(context, input, false, textField);
    }

    static boolean handleChars(Context context, String input) {
        return handleChars(context, input, false, null);
    }

    static boolean handleChars(Context context, String input, boolean useSystemWindow,
            EditText textField) {

        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (handleIMEIDisplay(context, dialString, useSystemWindow)
                || handlePinEntry(context, dialString, textField)
                || handleAdnEntry(context, dialString, textField)
                || handleSecretCode(context, dialString)
                || handleDmCode(context, dialString) // Liuhongxing 20110602
                ) {
            return true;
        }

        return false;
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            Intent intent = new Intent(Intents.SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        if (textField == null) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                //search from sim1 first
                sc.simIndex = 0;
                sc.simCount = 0;

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                // display the progress dialog
                sc.progressDialog.show();

                // run the query.
                handler.startQuery(ADN_QUERY_TOKEN, sc, sc.getSimUri(),
                        new String[]{ADN_PHONE_NUMBER_COLUMN_NAME}, null, null, null);
                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    static boolean handlePinEntry(Context context, String input, EditText textField) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            if (TelephonyManager.getPhoneCount() > 1) {
                boolean[] hsaIccCard = {false, false};
                hsaIccCard[0] = ((TelephonyManager) context.getSystemService(PhoneFactory
                        .getServiceName(Context.TELEPHONY_SERVICE, 0))).hasIccCard();
                hsaIccCard[1] = ((TelephonyManager) context.getSystemService(PhoneFactory
                        .getServiceName(Context.TELEPHONY_SERVICE, 1))).hasIccCard();
                if (hsaIccCard[0] && hsaIccCard[1]) {
                    handlePinBySimChooseDlg(context, input, textField);
                    return false;
                } else {
                    for (int i = 0; i < hsaIccCard.length; i++) {
                        if (hsaIccCard[i]) {
                            try {
                                return ITelephony.Stub.asInterface(
                                        ServiceManager.getService(PhoneFactory.getServiceName(
                                                Context.TELEPHONY_SERVICE, i))).handlePinMmi(input);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                                return false;
                            }
                        }
                    }
                }
            } else {
                try {
                    return ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                            .handlePinMmi(input);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Handle Pin requests by selected SIM card
     * @param context
     * @param input
     * @param textField
     */
    private static void handlePinBySimChooseDlg(Context context, final String input, final EditText textField) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.sim_choose_dialog, null);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.selected)).setView(layout).create();
        OnClickListener listener = new OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
                int phoneId = -1;
                switch (v.getId()) {
                    case R.id.sim1:
                        phoneId = 0;
                        break;
                    case R.id.sim2:
                        phoneId = 1;
                        break;
                }
                boolean handleResult = false;
                try {
                    if (phoneId != -1) {
                        handleResult = ITelephony.Stub.asInterface(
                                ServiceManager.getService(PhoneFactory.getServiceName(
                                        Context.TELEPHONY_SERVICE, phoneId))).handlePinMmi(input);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "DS: Failed to handlePinMmi due to remote exception");
                }
                if (handleResult) {
                    textField.getText().clear();
                }
            }
        };
        layout.findViewById(R.id.sim1).setOnClickListener(listener);
        layout.findViewById(R.id.sim2).setOnClickListener(listener);
        dialog.show();
}

    static boolean handleIMEIDisplay(Context context, String input, boolean useSystemWindow) {
        if (input.equals(MMI_IMEI_DISPLAY)) {
            int phoneType = ((TelephonyManager)context.getSystemService(
                    Context.TELEPHONY_SERVICE)).getPhoneType();

            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                showIMEIPanel(context, useSystemWindow);
                return true;
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                showMEIDPanel(context, useSystemWindow);
                return true;
            }
        }

        return false;
    }

    // Liuhongxing add 2011.06.02 begin
	static boolean handleDmCode(Context context, String input)
	{
		if(input.equals(DM_SETTING))
		{
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setClassName("com.spreadtrum.dm", "com.spreadtrum.dm.DmDebugMenu");
			try {
				context.startActivity(intent);
			} catch (ActivityNotFoundException e) {
				// TODO Auto-generated catch block
				Log.i(TAG, "===ActivityNotFoundException===");
				return false;
			}
			return true;
		}

		return false;
	}
    // End liu 2011.06.02

    static void showIMEIPanel(Context context, boolean useSystemWindow) {
        //wangsl
        //String msg1 = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
	//	String msg = msg1;
	//	if (PhoneFactory.getPhoneCount() > 1) {
	//		String msg2 = ((TelephonyManager) context.getSystemService(PhoneFactory
	//				.getServiceName(Context.TELEPHONY_SERVICE, 1)))
	//				.getDeviceId();
	//		msg = "Device1:" + msg1 + "\n" + "Device2:" + msg2;
	//	}
        StringBuffer imeiBuffer = new StringBuffer();
        int phoneCnt = TelephonyManager.getPhoneCount();
        if (phoneCnt == 0) {
            // single card.
            imeiBuffer.append(TelephonyManager.getDefault().getDeviceId());
        } else {
            for (int i = 0; i < phoneCnt; i++) {
                if (i != 0) {
                    imeiBuffer.append("\n");
                }
                imeiBuffer.append("IMEI");
                imeiBuffer.append((i + 1));
                imeiBuffer.append("\n");
                imeiBuffer.append(((TelephonyManager) context.getSystemService(PhoneFactory
                        .getServiceName(Context.TELEPHONY_SERVICE, i))).getDeviceId());
            }
        }
        String imeiStr = imeiBuffer.toString();
        //wangsl

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.imei)
                .setMessage(imeiStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
    }

    static void showMEIDPanel(Context context, boolean useSystemWindow) {
        //wangsl
        //String meidStr = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE))
        //        .getDeviceId();
        StringBuffer meidBuffer = new StringBuffer();
        int phoneCnt = TelephonyManager.getPhoneCount();
        if (phoneCnt == 0) {
            // single card.
            meidBuffer.append(TelephonyManager.getDefault().getDeviceId());
        } else {
            for (int i = 0; i < phoneCnt; i++) {
                if (i != 0) {
                    meidBuffer.append("\n");
                }
                meidBuffer.append("MEID");
                meidBuffer.append((i + 1));
                meidBuffer.append("\n");
                meidBuffer.append(((TelephonyManager) context.getSystemService(PhoneFactory
                        .getServiceName(Context.TELEPHONY_SERVICE, i))).getDeviceId());

            }
        }
        String meidStr = meidBuffer.toString();
        //wangsl
        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.meid)
                .setMessage(meidStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;
        //which sim card we will to query 0 or 1
        public int simIndex;
        //how many contacts in last sim card
        public int simCount;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }

        public Uri getSimUri() {
            if (PhoneFactory.isMultiSim()) {
                if (simIndex == 1) {
                    // return Uri.parse("content://icc1/adn");
                    return SimUtils.SIM2_URI;
                }
                // return Uri.parse("content://icc0/adn");
                return SimUtils.SIM1_URI;
            }
            // return Uri.parse("content://icc/adn");
            return SimUtils.SIM_URI;
        }

    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link handleAdnEntry}.
     */
    private static class QueryHandler extends AsyncQueryHandler {

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
            SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

            // close the progress dialog.
            sc.progressDialog.dismiss();

            // get the EditText to update or see if the request was cancelled.
            EditText text = sc.getTextField();

            // if the textview is valid, and the cursor is valid and postionable
            // on the Nth number, then we update the text field and display a
            // toast indicating the caller name.
            if ((c != null)){
                sc.contactNum = sc.contactNum - sc.simCount;
                if(c.moveToPosition(sc.contactNum)) {
                    String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                    String number = c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                    // fill the text in.
                    if(number != null && null != text && null != text.getText()) {
                        text.getText().replace(0, 0, number);
                    }

                    // display the name as a toast
                    Context context = sc.progressDialog.getContext();
                    name = context.getString(R.string.menu_callNumber, name);
                    Toast.makeText(context, name, Toast.LENGTH_SHORT).show();
                    return;//if find the contact,finish query
                }
                //save the count of contacts in sim1
                sc.simCount = c.getCount();
            }
            if (sc.simIndex == 0) {
                sc.simIndex = 1;
                startQuery(ADN_QUERY_TOKEN, sc, sc.getSimUri(), new String[] {
                    ADN_PHONE_NUMBER_COLUMN_NAME}, null, null, null);
            }
        }
    }
}
