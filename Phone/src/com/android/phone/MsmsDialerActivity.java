/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.telephony.MsmsConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class MsmsDialerActivity extends Activity {
    private static final String TAG = "MsmsDialerActivity";
    private static final boolean DBG = true;

    private Context mContext;
    private String mCallNumber;
    private String mNumber;
    private TextView mTextNumber;
    private Intent mIntent;
    boolean mIsFastDial = false;
    private int mPhoneCount = 0;
    private boolean mSub1IsActive;
    private boolean mSub2IsActive;

    public static final String PHONE_SUBSCRIPTION = "Subscription";
    public static final int INVALID_SUB = 99;
    private static final int AIRPLANE_MODE_ON_DIALOG = 1;
    private static final int FORBIDDEN_CARD_ERROR = 2;
    private static final int GENERIC_ERROR = 3;
    private static final int CHOOSE_SIM_DIALOG = 4;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = getApplicationContext();
        mCallNumber = getResources().getString(R.string.call_number);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mPhoneCount = TelephonyManager.getPhoneCount();
        mIntent = getIntent();
        if (DBG) Log.v(TAG, "Intent = " + mIntent);
        mIsFastDial = mIntent.getBooleanExtra(OutgoingCallBroadcaster.FAST_DIAL, false);

        mNumber = PhoneNumberUtils.getNumberFromIntent(mIntent, this);
        if (DBG) Log.v(TAG, "mNumber " + mNumber);
        if (mNumber != null) {
            mNumber = PhoneNumberUtils.convertKeypadLettersToDigits(mNumber);
            mNumber = PhoneNumberUtils.stripSeparators(mNumber);
        }

        Phone phone = null;
        boolean phoneInCall = false;
        //checking if any of the phones are in use
        for (int i = 0; i < mPhoneCount; i++) {
//             phone = MSimPhoneFactory.getPhone(i);
             phone = PhoneFactory.getPhone(i);
             boolean inCall = isInCall(phone);
             if ((phone != null) && (inCall)) {
                 phoneInCall = true;
                 break;
             }
        }
        if (phoneInCall) {
            if (DBG) Log.v(TAG, "subs [" + phone.getPhoneId() + "] is in call");
            // use the sub which is already in call
            startOutgoingCall(phone.getPhoneId(),false);
        } else {
            if (DBG) Log.v(TAG, "launch dsdsdialer");
            // if none in use, launch the MultiSimDialer
            launchMSDialer();
        }
        Log.d(TAG, "end of onResume()");
    }

    private void launchMSDialer() {
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(mNumber);
        if (isEmergency) {
            Log.d(TAG,"emergency call");
            startOutgoingCall(PhoneApp.getInstance().getVoiceSubscription(), false);
            return;
        } else if (isAirplaneModeOn()) {
            if (DBG)  Log.d(TAG, "isAirplaneModeOn");
            showDialog(AIRPLANE_MODE_ON_DIALOG, null);
            return;
        } else if (PhoneApp.getInstance().getExistSubCount() != 0 && PhoneApp.getInstance().getActiveSubCount() == 0) {
            if (DBG)
                Log.d(TAG, "has exist sub and has no active sub");
            showDialog(FORBIDDEN_CARD_ERROR, null);
            return;
        } else if (PhoneApp.getInstance().getActiveSubCount() == 0) {
            if (DBG) Log.d(TAG, "has no ActiveSub");
            showDialog(GENERIC_ERROR, null);
            return;
        }
        showDialog(CHOOSE_SIM_DIALOG, null);

    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        AlertDialog.Builder builder = null;
        switch (id) {
            case AIRPLANE_MODE_ON_DIALOG:
                if (DBG) Log.d(TAG, "showAirplaneModeOnDialog");
                builder = new AlertDialog.Builder(this)
                        .setTitle(R.string.title_dial_from_cotacts)
                        .setMessage(R.string.error_airplane_mode_on)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (DBG)
                                    Log.d(TAG, "Airplane Mode On AlertDialog: POSITIVE click...");
                                startOutgoingCall(PhoneApp.getInstance().getVoiceSubscription(), true);
                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (DBG) Log.d(TAG, "Airplane Mode On AlertDialog: NEGATIVE click...");
                                dismissDialog(AIRPLANE_MODE_ON_DIALOG);
                                finish();
                            }
                        }).setOnCancelListener(new OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                if (DBG) Log.d(TAG, "Airplane Mode On AlertDialog: CANCEL handler...");
                                dismissDialog(AIRPLANE_MODE_ON_DIALOG);
                                finish();
                            }
                        });
                break;
            case FORBIDDEN_CARD_ERROR:
                if (DBG) Log.d(TAG, "showCardForbiddenErrorDialog...");
                builder = new AlertDialog.Builder(this).setTitle(R.string.title_dial_from_cotacts)
                        .setMessage(R.string.error_no_active_sim)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (DBG) Log.d(TAG, "showCardForbiddenErrorDialog : ok");
                                Intent intent = new Intent(MsmsDialerActivity.this, SelectSimCard.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                MsmsPhoneApp.getInstance().startActivity(intent);
                                dismissDialog(FORBIDDEN_CARD_ERROR);
                                finish();
                            }
                        }).setOnCancelListener(new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dismissDialog(FORBIDDEN_CARD_ERROR);
                                finish();
                            }
                        });
                break;
            case GENERIC_ERROR:
                if (DBG) Log.d(TAG, "showGenericErrorDialog...");
                builder = new AlertDialog.Builder(this).setTitle(R.string.title_dial_from_cotacts)
                        .setMessage(R.string.error_radio_off)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(GENERIC_ERROR);
                                finish();
                            }
                        });
                break;
            case CHOOSE_SIM_DIALOG:
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.dialer_ms,
                        (ViewGroup) findViewById(R.id.layout_root));

                builder = new AlertDialog.Builder(MsmsDialerActivity.this);
                builder.setView(layout);
                builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        Log.d(TAG, "key code is :" + keyCode);
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_BACK: {
                                dismissDialog(CHOOSE_SIM_DIALOG);
                                finish();
                                // startOutgoingCall(INVALID_SUB);
                                return true;
                            }
                            case KeyEvent.KEYCODE_CALL: {
                                Log.d(TAG, "event is" + event.getAction());
                                if (event.getAction() == KeyEvent.ACTION_UP) {
                                    return true;
                                } else {
                                    dismissDialog(CHOOSE_SIM_DIALOG);
                                    startOutgoingCall(PhoneApp.getInstance().getVoiceSubscription(),false);
                                    return true;
                                }
                            }
                            case KeyEvent.KEYCODE_SEARCH:
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                mTextNumber = (TextView) layout.findViewById(R.id.CallNumber);

                String vm = "";
                if (mIntent.getData() != null)
                    vm = mIntent.getData().getScheme();

                if ((vm != null) && (vm.equals("voicemail"))) {
                    mTextNumber.setText(mCallNumber + getString(R.string.voicemail));
                    Log.d(TAG, "its voicemail!!!");
                } else if (mIsFastDial) {
                    String sub1Number = getFastDialNumber(MsmsConstants.SUB1);
                    String sub2Number = getFastDialNumber(MsmsConstants.SUB2);
                    StringBuffer FDString = new StringBuffer(getResources().getString(
                            R.string.fast_dial));
                    FDString.append(":\n").append(getResources().getString(R.string.sim1))
                            .append(":");
                    if (TextUtils.isEmpty(sub1Number)) {
                        FDString.append(getResources().getString(R.string.not_set));
                    } else {
                        FDString.append(sub1Number);
                    }
                    FDString.append(";\n").append(getResources().getString(R.string.sim2))
                            .append(":");
                    if (TextUtils.isEmpty(sub2Number)) {
                        FDString.append(getResources().getString(R.string.not_set));
                    } else {
                        FDString.append(sub2Number);
                    }
                    FDString.append(";");
                    mTextNumber.setText(FDString.toString());
                    FDString = null;
                } else {
                    mTextNumber.setText(mCallNumber
                            + SpecialTextViewTool.commaSemicolonToPW(mNumber));
                }

                Button callCancel = (Button) layout.findViewById(R.id.callcancel);
                callCancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dismissDialog(CHOOSE_SIM_DIALOG);
                        finish();
                        // startOutgoingCall(INVALID_SUB);
                    }
                });
                mSub1IsActive = mIntent.getBooleanExtra(
                        OutgoingCallBroadcaster.EXTRA_SUB1_IS_ACTIVE, false);
                mSub2IsActive = mIntent.getBooleanExtra(
                        OutgoingCallBroadcaster.EXTRA_SUB2_IS_ACTIVE, false);
                if (DBG)
                    Log.d(TAG, "mSub1IsActive = " + mSub1IsActive + ", mSub2IsActive = "
                            + mSub2IsActive);
                Button[] callButton = new Button[mPhoneCount];
                int[] callMark = {
                        R.id.callmark1, R.id.callmark2
                };
                int[] subString = {
                        R.string.sub_1, R.string.sub_2
                };
                boolean[] subActive = {
                        mSub1IsActive, mSub2IsActive
                };
                int index = 0;
                for (index = 0; index < mPhoneCount; index++) {
                    callButton[index] = (Button) layout.findViewById(callMark[index]);
                    callButton[index].setEnabled(subActive[index]);
                    callButton[index].setText(getString(subString[index]));
                    callButton[index].setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            dismissDialog(CHOOSE_SIM_DIALOG);
                            switch (v.getId()) {
                                case R.id.callmark1:
                                    startOutgoingCall(MsmsConstants.SUB1, false);
                                    break;
                                case R.id.callmark2:
                                    startOutgoingCall(MsmsConstants.SUB2, false);
                                    break;
                            }
                        }
                    });
                }
                break;

            default:
                if (builder == null) {
                    builder = new AlertDialog.Builder(MsmsDialerActivity.this);
                }
        }

        return builder.create();
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(getApplicationContext().getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) > 0;
    }

    boolean isInCall(Phone phone) {
        if (phone != null) {
            if ((phone.getForegroundCall().getState().isAlive()) ||
                   (phone.getBackgroundCall().getState().isAlive()) ||
                   (phone.getRingingCall().getState().isAlive()))
                return true;
        }
        return false;
    }

    private void startOutgoingCall(int subscription, boolean isNeedToAirplaneModeOff) {
         mIntent.putExtra(Phone.PHONE_ID, subscription);
         mIntent.setClass(MsmsDialerActivity.this, OutgoingCallBroadcaster.class);
         if (DBG) Log.v(TAG, "startOutgoingCall for sub " +subscription);
         if (mIsFastDial) {
             mNumber = getFastDialNumber(subscription);
             Log.d(TAG, "dsds fast number:" + mNumber);
             mIntent.setData(Uri.fromParts("tel", mNumber, null));
         }
         mIntent.putExtra("isNeedToAirplaneModeOff", isNeedToAirplaneModeOff);
		if (subscription < mPhoneCount) {
			mIntent.putExtra(OutgoingCallBroadcaster.SIM_SELECTED, true);
		} else {
			mIntent.putExtra(OutgoingCallBroadcaster.SIM_SELECTED, false);
			Log.d(TAG, "call cancelled");
		}
		startActivity(mIntent);
		finish();
    }

    private String getFastDialNumber(int subId) {
        String number = "";
        if (mIsFastDial) {
            SharedPreferences fastDialSp = mContext.getSharedPreferences("fast_dial_numbers" + subId,
                    Context.MODE_WORLD_READABLE);
            number = fastDialSp.getString("fast_dial_" + mNumber, "");
        }
        return number;
    }
}
