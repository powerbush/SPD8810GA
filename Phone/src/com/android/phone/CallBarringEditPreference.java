package com.android.phone;

import com.android.internal.telephony.CallBarringInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import android.widget.EditText;
import android.widget.Toast;

import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

interface  CallBarringEditPreferencePreferenceListener {
    public void onChange(Preference preference, int reason);
}

public class CallBarringEditPreference extends EditPassWordPreference {
    private static final String LOG_TAG = "CallBarringEditPreference";
    private static final boolean DBG = true;//(PhoneApp.DBG_LEVEL >= 2);
    private static final boolean ENABLE_RIL = true;

    private static final String SRC_TAGS[]       = {"{0}"};
    private CharSequence mSummaryOnTemplate;
    private int mButtonClicked;
    private int mServiceClass;
	private boolean mNeedEcho = true;
    private MyHandler mHandler = new MyHandler();
    int mReason;
    Phone phone;
    CallBarringInfo mCallBarringInfo = new CallBarringInfo();
    TimeConsumingPreferenceListener tcpListener;
    CallBarringEditPreferencePreferenceListener mListener;

	private String reasonToString(int reason){
		switch (reason){
			case CommandsInterface.CB_REASON_AO:
				return CommandsInterface.CB_FACILITY_BAOC;
			case CommandsInterface.CB_REASON_OI:
				return CommandsInterface.CB_FACILITY_BAOIC;
			case CommandsInterface.CB_REASON_OX:
				return CommandsInterface.CB_FACILITY_BAOICxH;
			case CommandsInterface.CB_REASON_AI:
				return CommandsInterface.CB_FACILITY_BAIC;
			case CommandsInterface.CB_REASON_IR:
				return CommandsInterface.CB_FACILITY_BAICr;
			case CommandsInterface.CB_REASON_AB:
				return CommandsInterface.CB_FACILITY_BA_ALL;
			default:
				return CommandsInterface.CB_FACILITY_BAOC;
			}
	}
	
    public CallBarringEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSummaryOnTemplate = this.getSummaryOn();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CallBarringEditPreference, 0, R.style.EditPassWordPreference);
        mServiceClass = a.getInt(R.styleable.CallBarringEditPreference_aserviceClass,
                CommandsInterface.SERVICE_CLASS_VOICE);
        mReason = a.getInt(R.styleable.CallBarringEditPreference_areason, 0);
        a.recycle();
		
		mCallBarringInfo.password = null;
		mCallBarringInfo.reason = mReason;
		mCallBarringInfo.serviceClass = mServiceClass;
		mCallBarringInfo.status = 0;

        if (DBG) Log.d(LOG_TAG, "mServiceClass=" + mServiceClass + ", reason=" + mReason);
    }

    public CallBarringEditPreference(Context context) {
        this(context, null);
    }

   void setListener(CallBarringEditPreferencePreferenceListener listener){
   	mListener = listener;
   }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int subId) {
        phone = PhoneApp.getInstance().getPhone(subId);
        tcpListener = listener;
        if (!skipReading) {
			if (ENABLE_RIL) {
			   	if (DBG) Log.d(LOG_TAG, "queryFacilityLock: " + reasonToString(mReason));
	            phone.queryFacilityLock(reasonToString(mReason), "", mServiceClass, 
	                    mHandler.obtainMessage(MyHandler.MESSAGE_GET_LOCK,
	                            // unused in this case
	                            CommandsInterface.CF_ACTION_DISABLE,
	                            MyHandler.MESSAGE_GET_LOCK, null));
			} else {
	            mHandler.sendMessageDelayed(mHandler.obtainMessage(MyHandler.MESSAGE_GET_LOCK,
	                            // unused in this case
	                            CommandsInterface.CB_ACTION_DISABLE,
	                            MyHandler.MESSAGE_GET_LOCK, null), 300);
			}
            if (tcpListener != null) {
                tcpListener.onStarted(this, true);
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);
        mButtonClicked = which;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (DBG) Log.d(LOG_TAG, "mButtonClicked=" + mButtonClicked
                + ", positiveResult=" + positiveResult);
        if (this.mButtonClicked == DialogInterface.BUTTON_POSITIVE) {
            int action = (isToggled()? CommandsInterface.CB_ACTION_DISABLE : CommandsInterface.CB_ACTION_ENABLE);
	        EditText editText = getEditText();
			final String password = getEditText().getText().toString();
			if (DBG) Log.d(LOG_TAG, "onDialogClosed(), action: " + action + ", isToggled()" + isToggled());

            if (DBG) Log.d(LOG_TAG, "mCallBarringInfo=" + mCallBarringInfo);

            if (action == CommandsInterface.CB_ACTION_ENABLE
                    && mCallBarringInfo != null
                    && mCallBarringInfo.status == 1
                    /*&& password.equals(mCallBarringInfo.password*/) {
                // no change, do nothing
                if (DBG) Log.d(LOG_TAG, "no change, do nothing");
            } else {
                // set to network
                if (DBG) Log.d(LOG_TAG, "reason=" + mReason + ", action=" + action
                        + ", password=" + password);

                // Display no forwarding password while we're waiting for
                // confirmation
                //setSummaryOn("");

                // the interface of Phone.setCallForwardingOption has error:
                // should be action, reason...
               if (ENABLE_RIL){
			   		if (DBG) Log.d(LOG_TAG, "setFacilityLock: " + reasonToString(mReason));
		            setPassWord(null);
			   		phone.setFacilityLock(reasonToString(mReason), (action==CommandsInterface.CB_ACTION_ENABLE) , password, mServiceClass,
                        mHandler.obtainMessage(MyHandler.MESSAGE_SET_LOCK, action, MyHandler.MESSAGE_SET_LOCK));
               	} else{
            		mHandler.sendMessageDelayed(mHandler.obtainMessage(MyHandler.MESSAGE_SET_LOCK,
                                action,
                                MyHandler.MESSAGE_SET_LOCK), 300);
               	}

                if (tcpListener != null) {
                    tcpListener.onStarted(this, false);
                }
            }
        }
    }

    void handleCallBarringResult(CallBarringInfo cf) {
        mCallBarringInfo = cf;
        if (DBG) Log.d(LOG_TAG, "handleCallBarringResult done, mCallBarringInfo=" + mCallBarringInfo);

        setToggled(mCallBarringInfo.status == 1);
        setPassWord(null);
    }

    private void updateSummaryText() {
        if (isToggled()) {
			Log.d(LOG_TAG, "updateSummaryText");/*
            CharSequence summaryOn;
            final String password = getPassWord();
            if (password != null && password.length() > 0) {
                String values[] = { password };
                summaryOn = TextUtils.replace(mSummaryOnTemplate, SRC_TAGS, values);
				Log.d(LOG_TAG, "1 updateSummaryText: " + summaryOn);
            } else {
                summaryOn = getContext().getString(R.string.sum_cfu_enabled_no_number);
				Log.d(LOG_TAG, "2 updateSummaryText: " + summaryOn);
            }
            setSummaryOn(summaryOn);*/
        }

    }

	public void setNeedEcho(boolean bValue){
		mNeedEcho = bValue;
	}

	public boolean getNeedEcho(){
		return mNeedEcho;
	}

    // Message protocol:
    // what: get vs. set
    // arg1: action -- register vs. disable
    // arg2: get vs. set for the preceding request
    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_LOCK = 0;
        private static final int MESSAGE_SET_LOCK = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_LOCK:
                    handleGetCBResponse(msg);
                    break;
                case MESSAGE_SET_LOCK:
                    handleSetCBResponse(msg);
                    break;
            }
        }
		private int getStatus(int iStatus, int iClass, int myClass) {
			int myStatus = 0;
			
			if ((myClass & iClass) == myClass){
				myStatus = iStatus;
			} else {
				myStatus = ((iStatus == 1)?0:1);
			}
			if (DBG) Log.d(LOG_TAG, "getStatus(), myClass: " + myClass + ", iClass: " + iClass);
			if (DBG) Log.d(LOG_TAG, "getStatus(), myStatus: " + myStatus + ", iStatus: " + iStatus);
			return myStatus;
		}
		
        private void handleGetCBResponse(Message msg) {
            if (DBG) Log.d(LOG_TAG, "handleGetCBResponse: done");

            if (msg.arg2 == MESSAGE_SET_LOCK) {
                tcpListener.onFinished(CallBarringEditPreference.this, false);
            } else {
                tcpListener.onFinished(CallBarringEditPreference.this, true);
            }

			if (ENABLE_RIL) {
	            AsyncResult ar = (AsyncResult) msg.obj;

	            //mCallBarringInfo = null;
	            if (ar.exception != null) {
	                if (DBG) Log.d(LOG_TAG, "handleGetCBResponse: ar.exception=" + ar.exception);
	                setEnabled(false);
	                tcpListener.onError(CallBarringEditPreference.this, EXCEPTION_ERROR);
	            } else {
	                if (ar.userObj instanceof Throwable) {
	                    tcpListener.onError(CallBarringEditPreference.this, RESPONSE_ERROR);
	                }
	                int infoArray[] = (int[]) ar.result;
	                if (infoArray.length == 0) {
	                    if (DBG) Log.d(LOG_TAG, "handleGetCBResponse: infoArray.length==0");
	                    setEnabled(false);
	                    tcpListener.onError(CallBarringEditPreference.this, RESPONSE_ERROR);
                } else {
                        // corresponding class
						mCallBarringInfo.status = getStatus(infoArray[0], infoArray[1], mServiceClass);
						if (mNeedEcho){
                            handleCallBarringResult(mCallBarringInfo);
						} else{
					        setPassWord(null);
						}								
						
						CharSequence s = null;

                        // Show an alert if we got a success response but
                        // with unexpected values.
                        // Currently only handle the fail-to-disable case
                        // since we haven't observed fail-to-enable.
                        if (msg.arg2 == MESSAGE_SET_LOCK){		
                            if  (msg.arg1 == CommandsInterface.CB_ACTION_DISABLE && mCallBarringInfo.status == 1) {
                                switch (mReason) {
                                    case CommandsInterface.CB_REASON_AO:
                                        s = getContext().getText(R.string.disable_ao_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_OI:
                                        s = getContext().getText(R.string.disable_oi_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_OX:
                                        s = getContext().getText(R.string.disable_ox_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_AI:
                                        s = getContext().getText(R.string.disable_ai_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_IR:
                                        s = getContext().getText(R.string.disable_ir_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.disable_ao_forbidden);
                                }
                        	}								
                            if  (msg.arg1 == CommandsInterface.CB_ACTION_ENABLE && mCallBarringInfo.status == 0) {
                                switch (mReason) {
                                    case CommandsInterface.CB_REASON_AO:
                                        s = getContext().getText(R.string.enable_ao_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_OI:
                                        s = getContext().getText(R.string.enable_oi_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_OX:
                                        s = getContext().getText(R.string.enable_ox_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_AI:
                                        s = getContext().getText(R.string.enable_ai_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_IR:
                                        s = getContext().getText(R.string.enable_ir_forbidden);
                                        break;
                                    case CommandsInterface.CB_REASON_AB:
                                        s = getContext().getText(R.string.enable_ab_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.enable_ao_forbidden);
                                }
                        	}
							if (s != null){
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext().getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
							}
                        }
                	}
	            }
			}

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            if (mNeedEcho){
            	updateSummaryText();
            }
        }

        private void handleSetCBResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            CharSequence s = null;

            if (ar == null)
                return;

            if (ar.exception != null) {
                if (DBG) Log.d(LOG_TAG, "handleSetCBResponse: ar.exception=" + ar.exception);
                tcpListener.onFinished(CallBarringEditPreference.this, false);
                setPassWord(null);
                s = getContext().getText(R.string.cb_error);
                if (s != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setNeutralButton(R.string.close_dialog, null);
                    builder.setTitle(getContext().getText(R.string.error_updating_title));
                    builder.setMessage(s);
                    builder.setCancelable(true);
                    builder.create().show();
                }
            } else {
                tcpListener.onFinished(CallBarringEditPreference.this, false);
                mListener.onChange(CallBarringEditPreference.this,mReason);
            }
        }
    }
}

