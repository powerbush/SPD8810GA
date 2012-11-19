/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.phone.CallLogAsync;
import com.android.phone.HapticFeedback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.CallLog;
import android.provider.Contacts.Intents.Insert;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialer activity that displays the typical twelve key interface.
 */
@SuppressWarnings("deprecation")
public class TwelveKeyDialer extends Activity implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener, View.OnTouchListener,
        AdapterView.OnItemClickListener, TextWatcher {
    private static final String EMPTY_NUMBER = "";
    private static final String TAG = "TwelveKeyDialer";

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_SYSTEM;

    private EditText mDigits;
    private View mDelete;
    private MenuItem mAddToContactMenuItem;
    private ToneGenerator mToneGenerator;
    private Object mToneGeneratorLock = new Object();
    private Drawable mDigitsBackground;
    private Drawable mDigitsEmptyBackground;
    private View mDialpad;
    private View mVoicemailDialAndDeleteRow;
    private View mVoicemailButton;
    private View mDialButton;
    private ImageView mHideButton;
    private View mDialVideoButton;
    private ListView mDialpadChooser;
    private ListView mContactList;
    private ContactAdapter mAdapter;
    private ArrayList<ContactEntity> mCustomArrayList = new ArrayList<ContactEntity>();
    private boolean mIsContactChanged = false;
    private DialpadChooserAdapter mDialpadChooserAdapter;
    //Member variables for dialpad options
    private MenuItem m2SecPauseMenuItem;
    private MenuItem mWaitMenuItem;
    private static final int MENU_ADD_CONTACTS = 1;
    private static final int MENU_2S_PAUSE = 2;
    private static final int MENU_WAIT = 3;
    private static final int MENU_VOICE_MAIL_CALL = 4;
    private MenuItem mVoiceMailMenuItem;
    private View mEmergencyLayout;
    private View mEmergencyButton;

    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private HapticFeedback mHaptic = new HapticFeedback();

    /** Identifier for the "Add Call" intent extra. */
    static final String ADD_CALL_MODE_KEY = "add_call_mode";

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an ITelephony call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the ITelephony API.
     */
    static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    /** Indicates if we are opening this dialer to add a call from the InCallScreen. */
    private boolean mIsAddCallMode;

    private static final int CONTEXT_MENU_ITEM_VIEW_CONTACT = 1;
    private static final int CONTEXT_MENU_ITEM_ADD_CONTACT = 2;

    /**For fast dial*/
    private static final String FAST_DIAL = "com.android.phone.extra.FAST_DIAL";
    private boolean mFastDial = false;

    private Handler queryContactHandler;
    private HandlerThread queryContactHandlerThread;

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
            /**
             * Listen for phone state changes so that we can take down the
             * "dialpad chooser" if the phone becomes idle while the
             * chooser UI is visible.
             */
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
                //       + state + ", '" + incomingNumber + "'");
                if ((state == TelephonyManager.CALL_STATE_IDLE) && dialpadChooserVisible() && !phoneIsInUse()) {
                    // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                    // Note there's a race condition in the UI here: the
                    // dialpad chooser could conceivably disappear (on its
                    // own) at the exact moment the user was trying to select
                    // one of the choices, which would be confusing.  (But at
                    // least that's better than leaving the dialpad chooser
                    // onscreen, but useless...)
                    showDialpadChooser(false);
                }
            }
        };

    private static final int UPDATE_CONTACT_LIST = 1;

    private static final int DIALPAD_TOSHOW = 1;
    private static final int DIALPAD_TOHIDE = 2;
    private static final int DIALPAD_AUTO = 3;
    private static final int SUB_1 = 0;
    private static final int SUB_2 = 1;
    private static final int QUERY_CONTACTS = 100;

    private Context mContext;
    private SharedPreferences mSharedPreferences1 = null;
    private SharedPreferences mSharedPreferences2 = null;
    private String[] dialType;

    private class QueryContactHandler extends Handler{
        public QueryContactHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case QUERY_CONTACTS:
                    queryAndUpdateContactList();
                    break;

                default:
                    break;
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_CONTACT_LIST:
                    // update view and close cursor.
                    Log.i(TAG, "UPDATE_CONTACT_LIST,listsize: " + mCustomArrayList.size());
                    if (null != mAdapter) {
                        mAdapter.setList(mCustomArrayList);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        mAdapter = new ContactAdapter(TwelveKeyDialer.this, mCustomArrayList);
                        mContactList.setAdapter(mAdapter);
                    }
                    break;
            }
        }
    };

    ContentObserver mContactsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mIsContactChanged = true;
        }
    };
    
    ContentObserver mCallLogObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mIsContactChanged = true;
        }
    };

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        // Do nothing
        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    public void afterTextChanged(Editable input) {
        if (SpecialCharSequenceMgr.handleChars(this, input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        //quick dial with the input number.
        if (input == null || mFastDial) {
            return;
        }
        if (mAdapter != null) {
            String temps = input.toString().toLowerCase();
            if (!PinYin.isValidPhoneNumber(temps)) {
                mDigits.setText(PinYin.filterInvalidNumbers(temps));
                return;
            }
            if (temps != null && temps.indexOf('+') == 0) {
                temps = temps.substring(1);
            }
            mAdapter.filter(temps , mCustomArrayList);
        }

        if (!isDigitsEmpty()) {
            mDigits.setCursorVisible(true);
            mDigits.setBackgroundDrawable(mDigitsBackground);
        } else {
            mDigits.setCursorVisible(false);
            mDigits.setBackgroundDrawable(mDigitsEmptyBackground);
        }
        updateDialAndDeleteButtonEnabledState();
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.d(TAG, "onCreate()");

        Resources r = getResources();
        // Do not show title in the case the device is in carmode.
        if ((r.getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_CAR) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        // Set the content view
        setContentView(getContentViewResource());

        // Load up the resources for the text field.
        mDigitsBackground = r.getDrawable(R.drawable.btn_dial_textfield_active);
        mDigitsEmptyBackground = r.getDrawable(R.drawable.btn_dial_textfield);

        mDigits = (EditText) findViewById(R.id.digits);
        mDigits.setKeyListener(DialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        SpecialTextViewTool.specialDisplayPWForPhoneNumberTextView(mDigits);

        maybeAddNumberFormatting();

        // Check for the presence of the keypad
        View view = findViewById(R.id.one);
        if (view != null) {
            setupKeypad();
        }

        mVoicemailDialAndDeleteRow = findViewById(R.id.voicemailAndDialAndDelete);

		initVoicemailButton();
		initDialVideoButton();
		initDialButton();

		// init Emergency layout
		mEmergencyLayout = mVoicemailDialAndDeleteRow.findViewById(R.id.emergency_button_layout);
		mEmergencyButton = mVoicemailDialAndDeleteRow.findViewById(R.id.emergency_button);
		mEmergencyButton.setOnClickListener(this);


        view = mVoicemailDialAndDeleteRow.findViewById(R.id.deleteButton);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        mDelete = view;

        mHideButton = (ImageView)findViewById(R.id.hideButton);
        mHideButton.setOnClickListener(this);

        mContactList = (ListView)findViewById(R.id.contact_list);
        mContactList.setOnItemClickListener(this);
        mContactList.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                AdapterView.AdapterContextMenuInfo info;
                try {
                    info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                } catch (ClassCastException e) {
                    Log.e(TAG, "bad menuInfo", e);
                    return;
                }
                if (mAdapter != null) {
                    long id = mAdapter.getItem(info.position).getId();
                    String name = mAdapter.getItem(info.position).getDisplayName();
                    Log.d(TAG, "ContextMenu id: " + id + "; ContextMenu name:" + name);
                    menu.setHeaderTitle(name);
                    if (0 != id) {
                        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
                        final Intent viewContactIntent = new Intent(Intent.ACTION_VIEW, contactUri);
                        menu.add(0, CONTEXT_MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact).setIntent(viewContactIntent);
                    } else {
                        String number = mAdapter.getItem(info.position).getPhoneNumber();
                        Intent addContactIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                        addContactIntent.setType(Contacts.CONTENT_ITEM_TYPE);
                        addContactIntent.putExtra(Insert.PHONE, number);
                        menu.add(0, CONTEXT_MENU_ITEM_ADD_CONTACT, 0, R.string.menu_newContact).setIntent(addContactIntent);
                    }
                }
            }
        });
        mContactList.setOnScrollListener(new AbsListView.OnScrollListener() {

            public void onScrollStateChanged(AbsListView view, int scrollState) {
                Log.d(TAG, "listScrollState: " + scrollState);
                switch (scrollState) {
                    case OnScrollListener.SCROLL_STATE_IDLE:
                        if (mAdapter != null) {
                            mAdapter.controlPhotoLoader(ContactAdapter.RESUME);
                        }
                        break;
                    case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                        hideOrShowDialPad(DIALPAD_TOHIDE);
                        break;
                    case OnScrollListener.SCROLL_STATE_FLING:
                        hideOrShowDialPad(DIALPAD_TOHIDE);
                        if (mAdapter != null) {
                            mAdapter.controlPhotoLoader(ContactAdapter.PAUSE);
                        }
                        break;

                    default:
                        break;
                }

            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });
        mDialpad = findViewById(R.id.dialpad);  // This is null in landscape mode.

        // In landscape we put the keyboard in phone mode.
        // In portrait we prevent the soft keyboard to show since the
        // dialpad acts as one already.
        if (null == mDialpad) {
            mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } else {
            mDigits.setInputType(android.text.InputType.TYPE_NULL);
        }

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);

        if (!resolveIntent() && icicle != null) {
            super.onRestoreInstanceState(icicle);
        }

        try {
            mHaptic.init(this, r.getBoolean(R.bool.config_enable_dialer_key_vibration));
        } catch (Resources.NotFoundException nfe) {
             Log.e(TAG, "Vibrate control bool missing.", nfe);
        }
        getContentResolver().registerContentObserver(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, false, mContactsObserver);
        getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, false, mCallLogObserver);

        //query contacts
        queryContactHandlerThread = new HandlerThread("queryContactHandlerThread");
        queryContactHandlerThread.start();
        queryContactHandler = new QueryContactHandler(queryContactHandlerThread.getLooper());
        queryContactHandler.sendEmptyMessage(QUERY_CONTACTS);

        try {
            mContext = createPackageContext("com.android.phone", Context.CONTEXT_IGNORE_SECURITY);
            mSharedPreferences1 = mContext.getSharedPreferences("fast_dial_numbers" + SUB_1,Context.MODE_WORLD_READABLE);
            if (TelephonyManager.getPhoneCount() > 1) {
                mSharedPreferences2 = mContext.getSharedPreferences("fast_dial_numbers" + SUB_2,Context.MODE_WORLD_READABLE);
            }
            if( !SystemProperties.getBoolean("ro.device.support.vt", true)){
                dialType= new String[]{this.getString(R.string.fast_call)};
            }else{
                dialType= new String[]{this.getString(R.string.fast_call),this.getString(R.string.fast_vt_call)};
            }
        } catch (Exception e) {

        }
    }

    private void reInitSharePreference() {
        if (mContext != null) {
            mSharedPreferences1 = mContext.getSharedPreferences("fast_dial_numbers" + SUB_1,
                    Context.MODE_WORLD_READABLE);
            if (TelephonyManager.getPhoneCount() > 1) {
                mSharedPreferences2 = mContext.getSharedPreferences("fast_dial_numbers" + SUB_2,
                        Context.MODE_WORLD_READABLE);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        // Do nothing, state is restored in onCreate() if needed
    }

    protected void maybeAddNumberFormatting() {
        mDigits.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
    }

    /**
     * Overridden by subclasses to control the resource used by the content view.
     */
    protected int getContentViewResource() {
        return R.layout.dialer_main;
    }

    private boolean resolveIntent() {
        boolean ignoreState = false;

        // Find the proper intent
        final Intent intent;
        if (isChild()) {
            intent = getParent().getIntent();
            ignoreState = intent.getBooleanExtra(DialtactsActivity.EXTRA_IGNORE_STATE, false);
        } else {
            intent = getIntent();
        }
        // Log.i(TAG, "==> resolveIntent(): intent: " + intent);

        // by default we are not adding a call.
        mIsAddCallMode = false;
        mFastDial = false;

        // By default we don't show the "dialpad chooser" UI.
        boolean needToShowDialpadChooser = false;

        // Resolve the intent
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            mIsAddCallMode = intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);

            Uri uri = intent.getData();
            if (uri != null) {
                if ("tel".equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    setFormattedDigits(data);
                } else {
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER}, null, null, null);
                        if (c != null) {
                            if (c.moveToFirst()) {
                                // Put the number into the input area
                                setFormattedDigits(c.getString(0));
                            }
                            c.close();
                        }
                    }
                }
            } else {
                // ACTION_DIAL or ACTION_VIEW with no data.
                // This behaves basically like ACTION_MAIN: If there's
                // already an active call, bring up an intermediate UI to
                // make the user confirm what they really want to do.
                // Be sure *not* to show the dialpad chooser if this is an
                // explicit "Add call" action, though.
                if (!mIsAddCallMode && phoneIsInUse()) {
                    needToShowDialpadChooser = true;
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            // The MAIN action means we're bringing up a blank dialer
            // (e.g. by selecting the Home shortcut, or tabbing over from
            // Contacts or Call log.)
            //
            // At this point, IF there's already an active call, there's a
            // good chance that the user got here accidentally (but really
            // wanted the in-call dialpad instead).  So we bring up an
            // intermediate UI to make the user confirm what they really
            // want to do.
            if (phoneIsInUse()) {
                // Log.i(TAG, "resolveIntent(): phone is in use; showing dialpad chooser!");
                needToShowDialpadChooser = true;
            }
        }

        // Bring up the "dialpad chooser" IFF we need to make the user
        // confirm which dialpad they really want.
        showDialpadChooser(needToShowDialpadChooser);

        return ignoreState;
    }

    protected void setFormattedDigits(String data) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        dialString = PhoneNumberUtils.formatNumber(dialString);
        if (!TextUtils.isEmpty(dialString)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), dialString);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        resolveIntent();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // This can't be done in onCreate(), since the auto-restoring of the digits
        // will play DTMF tones for all the old digits if it is when onRestoreSavedInstanceState()
        // is called. This method will be called every time the activity is created, and
        // will always happen after onRestoreSavedInstanceState().
        mDigits.addTextChangedListener(this);
    }

    private void setupKeypad() {
        // Setup the listeners for the buttons
        View view = findViewById(R.id.one);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
		view.setOnTouchListener(this);

        findViewById(R.id.two).setOnClickListener(this);
        findViewById(R.id.three).setOnClickListener(this);
        findViewById(R.id.four).setOnClickListener(this);
        findViewById(R.id.five).setOnClickListener(this);
        findViewById(R.id.six).setOnClickListener(this);
        findViewById(R.id.seven).setOnClickListener(this);
        findViewById(R.id.eight).setOnClickListener(this);
        findViewById(R.id.nine).setOnClickListener(this);
        findViewById(R.id.star).setOnClickListener(this);

        findViewById(R.id.two).setOnTouchListener(this);
        findViewById(R.id.three).setOnTouchListener(this);
        findViewById(R.id.four).setOnTouchListener(this);
        findViewById(R.id.five).setOnTouchListener(this);
        findViewById(R.id.six).setOnTouchListener(this);
        findViewById(R.id.seven).setOnTouchListener(this);
        findViewById(R.id.eight).setOnTouchListener(this);
        findViewById(R.id.nine).setOnTouchListener(this);
        findViewById(R.id.star).setOnTouchListener(this);

        view = findViewById(R.id.zero);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
		view.setOnTouchListener(this);

        findViewById(R.id.two).setOnLongClickListener(this);
        findViewById(R.id.three).setOnLongClickListener(this);
        findViewById(R.id.four).setOnLongClickListener(this);
        findViewById(R.id.five).setOnLongClickListener(this);
        findViewById(R.id.six).setOnLongClickListener(this);
        findViewById(R.id.seven).setOnLongClickListener(this);
        findViewById(R.id.eight).setOnLongClickListener(this);
        findViewById(R.id.nine).setOnLongClickListener(this);

        findViewById(R.id.pound).setOnClickListener(this);
        findViewById(R.id.pound).setOnTouchListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        //resume the photoLoader load if not load completed before.
        if (mAdapter != null) {
            mAdapter.controlPhotoLoader(ContactAdapter.RESUME);
            mAdapter.notifyDataSetChanged();
        }

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 0) == 1;

        // Retrieve the haptic feedback setting.
        mHaptic.checkSystemSetting();

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized(mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    // we want the user to be able to control the volume of the dial tones
                    // outside of a call, so we use the stream type that is also mapped to the
                    // volume control keys for this activity
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
//                    setVolumeControlStream(DIAL_TONE_STREAM_TYPE);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }

        Activity parent = getParent();
        // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
        // digits in the dialer field.
        if (parent != null && parent instanceof DialtactsActivity) {
            Uri dialUri = ((DialtactsActivity) parent).getAndClearDialUri();
            if (dialUri != null) {
                resolveIntent();
            }
        }

        // While we're in the foreground, listen for phone state changes,
        // purely so that we can take down the "dialpad chooser" if the
        // phone becomes idle while the chooser UI is visible.
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        // Potentially show hint text in the mDigits field when the user
        // hasn't typed any digits yet.  (If there's already an active call,
        // this hint text will remind the user that he's about to add a new
        // call.)
        //
        // TODO: consider adding better UI for the case where *both* lines
        // are currently in use.  (Right now we let the user try to add
        // another call, but that call is guaranteed to fail.  Perhaps the
        // entire dialer UI should be disabled instead.)
        if (phoneIsInUse()) {
            mDigits.setHint(R.string.dialerDialpadHintText);
        } else {
            // Common case; no hint necessary.
            mDigits.setHint(null);

            // Also, a sanity-check: the "dialpad chooser" UI should NEVER
            // be visible if the phone is idle!
            showDialpadChooser(false);
        }

        if (mIsContactChanged && queryContactHandler != null) {
            Log.d(TAG, "query contacts in onResume.");
            if (mCustomArrayList != null) {
                mCustomArrayList.clear();
            }
            queryContactHandler.removeMessages(QUERY_CONTACTS);
            queryContactHandler.sendEmptyMessage(QUERY_CONTACTS);
            mIsContactChanged = false;
        }
        updateDialAndDeleteButtonEnabledState();
        updateEmergencyLayout();
    }

    private void queryAndUpdateContactList() {
        Log.i(TAG, "queryAndUpdateContactList is start...");
        Cursor contactCursor = null;
        Cursor callLogCursor = null;
        ContactEntity contactentity;
        // get cursor.
        String[] mCallLogProjection = new String[] {
                CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                CallLog.Calls.DATE
        };

        String[] mContactsProjection = new String[] {
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_ID,
                RawContacts.ACCOUNT_NAME,
                RawContacts.SIM_INDEX
        };

        try {
            // clear list.
            mCustomArrayList.clear();
            Map<String, Integer> contactMap = new HashMap<String, Integer>();
            //query call log
            callLogCursor = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    mCallLogProjection, null, null, CallLog.Calls.DEFAULT_SORT_ORDER);
            if (callLogCursor != null && callLogCursor.moveToFirst()) {
                Log.d(TAG, "callLogCursor size:" + callLogCursor.getCount());
                int realOrder = 0;
                for (int i = 0; i < callLogCursor.getCount(); i++) {
                    callLogCursor.moveToPosition(i);
                    String number = callLogCursor.getString(0);
                    //for remove duplicate number.
                    if (contactMap.get(number) != null) {
                        continue;
                    }
                    String displayNumber = ("-1".equals(number) ? getString(R.string.unknown) : number);
                    contactentity = new ContactEntity();
                    contactentity.setPhoneNumber(displayNumber);
                    //we don't use name to name mDisplayName,because when the contact has been deleted, but calllog database has not been updated.
                    contactentity.setDisplayName(displayNumber);
                    contactentity.setCallLogType(callLogCursor.getInt(2));
                    mCustomArrayList.add(contactentity);
                    contactMap.put(number, realOrder);
                    realOrder++;
                }
            }
            mHandler.sendEmptyMessage(UPDATE_CONTACT_LIST);

            //query contact info
            contactCursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, mContactsProjection,
                    null, null, ContactsContract.Contacts.DISPLAY_NAME);
            if (contactCursor != null && contactCursor.moveToFirst()) {
                Log.d(TAG, "contactCursor size:" + contactCursor.getCount());
                while (contactCursor.getPosition() != contactCursor.getCount()) {
                    String number = PhoneNumberUtils.stripSeparators(contactCursor.getString(2));
                    String name = contactCursor.getString(3);
                    long id = contactCursor.getLong(0);
                    Integer order = contactMap.get(number);
                    if (order != null) {
                        contactentity = (ContactEntity) mCustomArrayList.get(order);
                        contactentity.setId(id);
                        contactentity.setDisplayName(name);
                        contactentity.setSpellName(PinYin.getInstance(TwelveKeyDialer.this)
                                .getPinyinString(name));
                        contactentity.setSpellNames(PinYin.getInstance(TwelveKeyDialer.this)
                                .getPinYinStringArray(name));
                    }//show call log and contacts at the same time
                        contactentity = new ContactEntity();
                        contactentity.setId(id);
                        contactentity.setPhoneNumber(number);
                        contactentity.setDisplayName(name);
                        contactentity.setPhotoId(contactCursor.getString(4));
                        // spell name can
                        contactentity.setSpellName(PinYin.getInstance(TwelveKeyDialer.this)
                                .getPinyinString(name));
                        // get spell name arrays
                        contactentity.setSpellNames(PinYin.getInstance(TwelveKeyDialer.this)
                                .getPinYinStringArray(name));
                        contactentity.setAccountName(contactCursor.getString(5));
                        contactentity.setSimIndex(contactCursor.getString(6));
                        mCustomArrayList.add(contactentity);

                    contactCursor.moveToNext();
                }
            }
            Log.d(TAG, "query mCustomArrayList size: " + mCustomArrayList.size());
            mHandler.sendEmptyMessage(UPDATE_CONTACT_LIST);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        } finally {
            Log.i(TAG, "close cursor...");
            if (null != callLogCursor) {
                callLogCursor.close();
                callLogCursor = null;
            }
            if (null != contactCursor) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        Log.i(TAG, "queryAndUpdateContactList is end...");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            // Hide soft keyboard, if visible (it's fugly over button dialer).
            // The only known case where this will be true is when launching the dialer with
            // ACTION_DIAL via a soft keyboard.  we dismiss it here because we don't
            // have a window token yet in onCreate / onNewIntent
            InputMethodManager inputMethodManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mDigits.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.controlPhotoLoader(ContactAdapter.CLEAR);
        }
        // Stop listening for phone state changes.
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        synchronized(mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mIsContactChanged) {
            Log.d(TAG, "Is ContactChanged");
            mCustomArrayList.clear();
            if (mAdapter != null) {
                mAdapter.setList(mCustomArrayList);
                mAdapter.controlPhotoLoader(ContactAdapter.STOP);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mContactsObserver);
        getContentResolver().unregisterContentObserver(mCallLogObserver);
        if (queryContactHandler != null) {
            queryContactHandler.removeMessages(QUERY_CONTACTS);
        }
        if (queryContactHandlerThread != null) {
            queryContactHandlerThread.quit();
        }
        mFastDial = false;
        mCustomArrayList.clear();
        if (mAdapter != null) {
            mAdapter.setList(mCustomArrayList);
            mAdapter.controlPhotoLoader(ContactAdapter.STOP);
            mAdapter.notifyDataSetChanged();
        }
        mAdapter = null;
        mContactList.setAdapter(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mAddToContactMenuItem = menu.add(0, MENU_ADD_CONTACTS, 0, R.string.recentCalls_addToContact)
                .setIcon(android.R.drawable.ic_menu_add);
        m2SecPauseMenuItem = menu.add(0, MENU_2S_PAUSE, 0, R.string.add_2sec_pause)
                .setIcon(R.drawable.ic_menu_2sec_pause);
        mWaitMenuItem = menu.add(0, MENU_WAIT, 0, R.string.add_wait)
                .setIcon(R.drawable.ic_menu_wait);

        if (SystemProperties.getBoolean("ro.device.support.vt", true)){
	     mVoiceMailMenuItem = menu.add(0, MENU_VOICE_MAIL_CALL, 0, R.string.voicemail)
	     .setIcon(R.drawable.ic_menu_voice_mail);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // We never show a menu if the "choose dialpad" UI is up.
        if (dialpadChooserVisible()) {
            return false;
        }

        if (isDigitsEmpty()) {
            mAddToContactMenuItem.setVisible(false);
            m2SecPauseMenuItem.setVisible(false);
            mWaitMenuItem.setVisible(false);
        } else {
            // fix bug 9736 ,set voiceMail menuItum.
            if (mVoiceMailMenuItem != null) {
                mVoiceMailMenuItem.setVisible(true);
            }
            CharSequence digits = mDigits.getText();

            // Put the current digits string into an intent
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            Bundle b = new Bundle();
            b.putString(Insert.PHONE, digits.toString());
            intent.putExtras(b);
            intent.setType(People.CONTENT_ITEM_TYPE);
            mAddToContactMenuItem.setIntent(intent);
            mAddToContactMenuItem.setVisible(true);

            // Check out whether to show Pause & Wait option menu items
            int selectionStart;
            int selectionEnd;
            String strDigits = digits.toString();

            selectionStart = mDigits.getSelectionStart();
            selectionEnd = mDigits.getSelectionEnd();

            if (selectionStart != -1) {
                if (selectionStart > selectionEnd) {
                    // swap it as we want start to be less then end
                    int tmp = selectionStart;
                    selectionStart = selectionEnd;
                    selectionEnd = tmp;
                }

                if (selectionStart != 0) {
                    // Pause can be visible if cursor is not in the begining
                    m2SecPauseMenuItem.setVisible(true);

                    // For Wait to be visible set of condition to meet
                    mWaitMenuItem.setVisible(showWait(selectionStart,
                                                      selectionEnd, strDigits));
                } else {
                    // cursor in the beginning both pause and wait to be invisible
                    m2SecPauseMenuItem.setVisible(false);
                    mWaitMenuItem.setVisible(false);
                }
            } else {
                // cursor is not selected so assume new digit is added to the end
                int strLength = strDigits.length();
                mWaitMenuItem.setVisible(showWait(strLength,
                                                      strLength, strDigits));
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                long callPressDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Launch voice dialer
                    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_1: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    callVoicemail();
                }
                return true;
            }
            case KeyEvent.KEYCODE_2: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(2);
                }
                return true;
            }
            case KeyEvent.KEYCODE_3: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(3);
                }
                return true;
            }
            case KeyEvent.KEYCODE_4: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(4);
                }
                return true;
            }
            case KeyEvent.KEYCODE_5: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(5);
                }
                return true;
            }
            case KeyEvent.KEYCODE_6: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(6);
                }
                return true;
            }
            case KeyEvent.KEYCODE_7: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(7);
                }
                return true;
            }
            case KeyEvent.KEYCODE_8: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(8);
                }
                return true;
            }
            case KeyEvent.KEYCODE_9: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    fastCall(9);
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // TODO: In dialButtonPressed we do some of these
                // tests again. We should try to consolidate them in
                // one place.
                if (!phoneIsCdma() && mIsAddCallMode && isDigitsEmpty()) {
                    // For CDMA phones, we always call
                    // dialButtonPressed() because we may need to send
                    // an empty flash command to the network.
                    // Otherwise, if we are adding a call from the
                    // InCallScreen and the phone number entered is
                    // empty, we just close the dialer to expose the
                    // InCallScreen under it.
                    finish();
                }

                // If we're CDMA, regardless of where we are adding a call from (either
                // InCallScreen or Dialtacts), the user may need to send an empty
                // flash command to the network. So let's call dialButtonPressed() regardless
                // and dialButtonPressed will handle this functionality for us.
                // otherwise, we place the call.
                dialButtonPressed();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void keyPressed(int keyCode) {
        mHaptic.vibrate();
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);
    }

    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialButtonPressed();
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * added by phone_03 for play tone on touch button start 201221
     */
    public boolean onTouch(View v, MotionEvent event) {
        int viewId = v.getId();
        if(event.getAction() == MotionEvent.ACTION_DOWN){
		switch (viewId) {
		case R.id.one:
			playTone(ToneGenerator.TONE_DTMF_1);
			break;
		case R.id.two:
			playTone(ToneGenerator.TONE_DTMF_2);
			break;
		case R.id.three:
			playTone(ToneGenerator.TONE_DTMF_3);
			break;
		case R.id.four:
			playTone(ToneGenerator.TONE_DTMF_4);
			break;
		case R.id.five:
			playTone(ToneGenerator.TONE_DTMF_5);
			break;
		case R.id.six:
			playTone(ToneGenerator.TONE_DTMF_6);
			break;
		case R.id.seven:
			playTone(ToneGenerator.TONE_DTMF_7);
			break;
		case R.id.eight:
			playTone(ToneGenerator.TONE_DTMF_8);
			break;
		case R.id.nine:
			playTone(ToneGenerator.TONE_DTMF_9);
			break;
		case R.id.zero:
			playTone(ToneGenerator.TONE_DTMF_0);
			break;
		case R.id.pound:
			playTone(ToneGenerator.TONE_DTMF_P);
			break;
		case R.id.star:
			playTone(ToneGenerator.TONE_DTMF_S);
			break;
		default:
			break;
		}
		}
	    // do not return true [handled] here, since we want the
	    // press / click animation to be handled by the framework.
        return false;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.one: {
                keyPressed(KeyEvent.KEYCODE_1);
                return;
            }
            case R.id.two: {
                keyPressed(KeyEvent.KEYCODE_2);
                return;
            }
            case R.id.three: {
                keyPressed(KeyEvent.KEYCODE_3);
                return;
            }
            case R.id.four: {
                keyPressed(KeyEvent.KEYCODE_4);
                return;
            }
            case R.id.five: {
                keyPressed(KeyEvent.KEYCODE_5);
                return;
            }
            case R.id.six: {
                keyPressed(KeyEvent.KEYCODE_6);
                return;
            }
            case R.id.seven: {
                keyPressed(KeyEvent.KEYCODE_7);
                return;
            }
            case R.id.eight: {
                keyPressed(KeyEvent.KEYCODE_8);
                return;
            }
            case R.id.nine: {
                keyPressed(KeyEvent.KEYCODE_9);
                return;
            }
            case R.id.zero: {
                keyPressed(KeyEvent.KEYCODE_0);
                return;
            }
            case R.id.pound: {
                keyPressed(KeyEvent.KEYCODE_POUND);
                return;
            }
            case R.id.star: {
                keyPressed(KeyEvent.KEYCODE_STAR);
                return;
            }
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                return;
            }
            case R.id.hideButton:{
                hideOrShowDialPad(DIALPAD_AUTO);
                return;
            }
            case R.id.dialButton: {
                mHaptic.vibrate();  // Vibrate here too, just like we do for the regular keys
                dialButtonPressed();
                return;
            }
			case R.id.dialvideoButton: {
				mHaptic.vibrate();
				dialVideoButtonPressed();
				return;
			}
            case R.id.voicemailButton: {
                callVoicemail();
                mHaptic.vibrate();
                return;
            }
            case R.id.digits: {
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                hideOrShowDialPad(DIALPAD_TOSHOW);
                return;
            }
			case R.id.emergency_button: {
				mHaptic.vibrate();
				dialButtonPressed();
				return;
			}
        }
    }

    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();
        int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                return true;
            }
            case R.id.one: {
                if (isDigitsEmpty()) {
                    callVoicemail();
                    return true;
                }
                return false;
            }
            case R.id.two: {
                return fastCall(2);
            }
            case R.id.three: {
                return fastCall(3);
            }
            case R.id.four: {
                return fastCall(4);
              }
            case R.id.five: {
                return fastCall(5);
              }
            case R.id.six: {
                return fastCall(6);
              }
            case R.id.seven: {
                return fastCall(7);
              }
            case R.id.eight: {
                return fastCall(8);
              }
            case R.id.nine: {
                return fastCall(9);
              }
            case R.id.zero: {
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        }
        return false;
    }

    void callVoicemail() {
        StickyTabs.saveTab(this, getIntent());
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts("voicemail", EMPTY_NUMBER, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mDigits.getText().clear();
        finish();
    }

    private void hideOrShowDialPad(int ctrType) {
		if (mDialpad == null) {
			return;
		}

        switch (ctrType) {
            case DIALPAD_TOSHOW:
                if (!mDialpad.isShown()) {
                    mDialpad.setVisibility(View.VISIBLE);
                    mHideButton.setImageResource(R.drawable.dialer_key_hide_btn_up);
                }
                break;
            case DIALPAD_TOHIDE:
                if (mDialpad.isShown()) {
                    mDialpad.setVisibility(View.GONE);
                    mHideButton.setImageResource(R.drawable.dialer_key_hide_btn_down);
                }
                break;
            case DIALPAD_AUTO:
                if (mDialpad.isShown()) {
                    mDialpad.setVisibility(View.GONE);
                    mHideButton.setImageResource(R.drawable.dialer_key_hide_btn_down);
                } else {
                    mDialpad.setVisibility(View.VISIBLE);
                    mHideButton.setImageResource(R.drawable.dialer_key_hide_btn_up);
                }
                break;
            default:
                break;
        }
    }

    void dialButtonPressed() {
        final String number = mDigits.getText().toString();
        boolean sendEmptyFlash = false;
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);

        if (isDigitsEmpty()) { // There is no number entered.
            if (phoneIsCdma() && phoneIsOffhook()) {
                // On CDMA phones, if we're already on a call, pressing
                // the Dial button without entering any digits means "send
                // an empty flash."
                intent.setData(Uri.fromParts("tel", EMPTY_NUMBER, null));
                intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
                sendEmptyFlash = true;
            } else if (!TextUtils.isEmpty(mLastNumberDialed)) {
                // Otherwise, pressing the Dial button without entering
                // any digits means "recall the last number dialed".
                mDigits.setText(mLastNumberDialed);
                mDigits.selectAll();
                return;
            } else {
                // Rare case: there's no "last number dialed".  There's
                // nothing useful for the Dial button to do in this case.
                playTone(ToneGenerator.TONE_PROP_NACK);
                return;
            }
        } else {
            if (mFastDial) {
                intent.putExtra(FAST_DIAL, mFastDial);
                mFastDial = false;
            }
            // There is a number.
            intent.setData(Uri.fromParts("tel", number, null));
        }

        StickyTabs.saveTab(this, getIntent());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mDigits.getText().clear();

        // Don't finish TwelveKeyDialer yet if we're sending a blank flash for CDMA. CDMA
        // networks use Flash messages when special processing needs to be done, mainly for
        // 3-way or call waiting scenarios. Presumably, here we're in a special 3-way scenario
        // where the network needs a blank flash before being able to add the new participant.
        // (This is not the case with all 3-way calls, just certain CDMA infrastructures.)
        if (!sendEmptyFlash) {
            finish();
        }
    }

    void dialVideoButtonPressed() {
        final String number = mDigits.getText().toString();
        boolean sendEmptyFlash = false;
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);
        if (isDigitsEmpty()) { // There is no number entered.
            if (phoneIsCdma() && phoneIsOffhook()) {
                // On CDMA phones, if we're already on a call, pressing
                // the Dial button without entering any digits means "send
                // an empty flash."
                intent.setData(Uri.fromParts("tel", EMPTY_NUMBER, null));
                intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
                sendEmptyFlash = true;
            } else if (!TextUtils.isEmpty(mLastNumberDialed)) {
                // Otherwise, pressing the Dial button without entering
                // any digits means "recall the last number dialed".
                mDigits.setText(mLastNumberDialed);
                mDigits.selectAll();
                return;
            } else {
                // Rare case: there's no "last number dialed".  There's
                // nothing useful for the Dial button to do in this case.
                playTone(ToneGenerator.TONE_PROP_NACK);
                return;
            }
        } else {
            if (mFastDial) {
                intent.putExtra(FAST_DIAL, mFastDial);
                mFastDial = false;
            } // There is a number.
            intent.setData(Uri.fromParts("tel", number, null));
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(ContactsUtils.getVPIntent(intent));
        mDigits.getText().clear();

        // Don't finish TwelveKeyDialer yet if we're sending a blank flash for CDMA. CDMA
        // networks use Flash messages when special processing needs to be done, mainly for
        // 3-way or call waiting scenarios. Presumably, here we're in a special 3-way scenario
        // where the network needs a blank flash before being able to add the new participant.
        // (This is not the case with all 3-way calls, just certain CDMA infrastructures.)
        if (!sendEmptyFlash) {
            finish();
        }
    }


    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * @param tone a tone code from {@link ToneGenerator}
     */
    void playTone(int tone) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized(mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: "+tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, TONE_LENGTH_MS);
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        if (enabled) {
            // Log.i(TAG, "Showing dialpad chooser!");
            mDigits.setVisibility(View.GONE);
            if (mDialpad != null) mDialpad.setVisibility(View.GONE);
            if (mContactList != null) {
                mContactList.setVisibility(View.GONE);
            }
            mVoicemailDialAndDeleteRow.setVisibility(View.GONE);
            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(this);
                mDialpadChooser.setAdapter(mDialpadChooserAdapter);
            }
        } else {
            // Log.i(TAG, "Displaying normal Dialer UI.");
            mDigits.setVisibility(View.VISIBLE);
            if (mDialpad != null) mDialpad.setVisibility(View.VISIBLE);
            if (mContactList != null) {
                mContactList.setVisibility(View.VISIBLE);
            }
            mVoicemailDialAndDeleteRow.setVisibility(View.VISIBLE);
            mDialpadChooser.setVisibility(View.GONE);
        }
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean dialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        switch (parent.getId()) {
            case R.id.dialpadChooser:
                DialpadChooserAdapter.ChoiceItem item = (DialpadChooserAdapter.ChoiceItem) parent
                        .getItemAtPosition(position);
                int itemId = item.id;
                switch (itemId) {
                    case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                        // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                        // Fire off an intent to go back to the in-call UI
                        // with the dialpad visible.
                        returnToInCallScreen(true);
                        break;

                    case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                        // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                        // Fire off an intent to go back to the in-call UI
                        // (with the dialpad hidden).
                        returnToInCallScreen(false);
                        break;

                    case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                        // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                        // Ok, guess the user really did want to be here (in the
                        // regular Dialer) after all. Bring back the normal
                        // Dialer UI.
                        showDialpadChooser(false);
                        break;

                    default:
                        Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                        break;
                }
                break;

            case R.id.contact_list:
                if (mAdapter != null) {
                    mDigits.setText(mAdapter.getItem(position).getPhoneNumber());
                    mDigits.selectAll();
                }
                break;

            default:
                Log.w(TAG, "onItemClick : unexpected viewId :" + v.getId());
                break;
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phone.showCallScreenWithDialpad(showDialpad);
        } catch (RemoteException e) {
            Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
        }

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    private boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneInUse = !phone.isIdle();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    /**
     * @return true if the phone state is OFFHOOK
     */
    private boolean phoneIsOffhook() {
        boolean phoneOffhook = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneOffhook = phone.isOffhook();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isOffhook() failed", e);
        }
        return phoneOffhook;
    }


    /**
     * Returns true whenever any one of the options from the menu is selected.
     * Code changes to support dialpad options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_2S_PAUSE:
                updateDialString(",");
                return true;
            case MENU_WAIT:
                updateDialString(";");
                return true;

	    case MENU_VOICE_MAIL_CALL:
                // {
                // boolean hasVoicemail = false;
                // try {
                // hasVoicemail =
                // TelephonyManager.getDefault().getVoiceMailNumber() != null;
                // } catch (SecurityException se) {
                // // Possibly no READ_PHONE_STATE privilege.
                // }

                // if(hasVoicemail)
                // {
                callVoicemail();
                mHaptic.vibrate();
                // }
                // }
	       return true;
        }
        return false;
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(String newDigits) {
        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        Editable digits = mDigits.getText();
        if (selectionStart != -1 ) {
            if (selectionStart == selectionEnd) {
                // then there is no selection. So insert the pause at this
                // position and update the mDigits.
                digits.replace(selectionStart, selectionStart, newDigits);
            } else {
                digits.replace(selectionStart, selectionEnd, newDigits);
                // Unselect: back to a regular cursor, just pass the character inserted.
                mDigits.setSelection(selectionStart + 1);
            }
        } else {
            int len = mDigits.length();
            digits.replace(len, len, newDigits);
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonEnabledState() {
        final boolean digitsNotEmpty = !isDigitsEmpty();

        if (mDialButton != null) {
            // On CDMA phones, if we're already on a call, we *always*
            // enable the Dial button (since you can press it without
            // entering any digits to send an empty flash.)
            if (phoneIsCdma() && phoneIsOffhook()) {
                mDialButton.setEnabled(true);
                mDialVideoButton.setEnabled(true);
            } else {
                // Common case: GSM, or CDMA but not on a call.
                // Enable the Dial button if some digits have
                // been entered, or if there is a last dialed number
                // that could be redialed.
				boolean enable = digitsNotEmpty || !TextUtils.isEmpty(mLastNumberDialed);
				mDialButton.setEnabled(enable);
				mDialVideoButton.setEnabled(enable);
            }
        }
        mDelete.setEnabled(digitsNotEmpty);
    }
    
	private void updateEmergencyLayout() {
		ITelephony iTele1 = ITelephony.Stub.asInterface(ServiceManager
				.getService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, 0)));
		ITelephony iTele2 = ITelephony.Stub.asInterface(ServiceManager
				.getService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, 1)));
		boolean hasCard1 = false;
		boolean hasCard2 = false;
		try {
			hasCard1 = iTele1 != null ? iTele1.hasIccCard() && iTele1.isRadioOn() : false;
			hasCard2 = iTele2 != null ? iTele2.hasIccCard() && iTele2.isRadioOn() : false;
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		if (hasCard1 || hasCard2) {
			mEmergencyLayout.setVisibility(View.GONE);
			initDialButton();
			initDialVideoButton();
			initVoicemailButton();
		} else {
			mEmergencyLayout.setVisibility(View.VISIBLE);
			mVoicemailButton.setVisibility(View.GONE);
			mDialVideoButton.setVisibility(View.GONE);
			mDialButton.setVisibility(View.GONE);
		}
	}
    	
	private void initDialVideoButton() {
		Resources r = getResources();
		mDialVideoButton = mVoicemailDialAndDeleteRow.findViewById(R.id.dialvideoButton);
		if (SystemProperties.getBoolean("ro.device.support.vt", true)) {
			if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
				mDialVideoButton.setOnClickListener(this);
				mDialVideoButton.setVisibility(View.VISIBLE);
			} else {
				mDialVideoButton.setVisibility(View.GONE);
				//mDialVideoButton = null;
			}
		} else {
			mDialVideoButton.setVisibility(View.GONE);
		}
	}
    	
	private void initDialButton() {
		Resources r = getResources();
		// Check whether we should show the onscreen "Dial" button.
		mDialButton = mVoicemailDialAndDeleteRow.findViewById(R.id.dialButton);

		if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
			mDialButton.setOnClickListener(this);
			mDialButton.setVisibility(View.VISIBLE);
		} else {
			mDialButton.setVisibility(View.GONE); // It's VISIBLE by default
		}
	}

    /**
     * Check if voicemail is enabled/accessible.
     */
    private void initVoicemailButton() {
    	Resources r = getResources();
        boolean hasVoicemail = false;
        try {
            hasVoicemail = TelephonyManager.getDefault().getVoiceMailNumber() != null;
        } catch (SecurityException se) {
            // Possibly no READ_PHONE_STATE privilege.
        }

        mVoicemailButton = mVoicemailDialAndDeleteRow.findViewById(R.id.voicemailButton);
        if (hasVoicemail) {
            mVoicemailButton.setOnClickListener(this);
        } else {
            mVoicemailButton.setEnabled(false);
        }
		if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
			mVoicemailButton.setVisibility(View.GONE);
		} else {
			mVoicemailButton.setVisibility(View.VISIBLE);
		}
    }

    /**
     * This function return true if Wait menu item can be shown
     * otherwise returns false. Assumes the passed string is non-empty
     * and the 0th index check is not required.
     */
    private boolean showWait(int start, int end, String digits) {
        if (start == end) {
            // visible false in this case
            if (start > digits.length()) return false;

            // preceding char is ';', so visible should be false
            if (digits.charAt(start-1) == ';') return false;

            // next char is ';', so visible should be false
            if ((digits.length() > start) && (digits.charAt(start) == ';')) return false;
        } else {
            // visible false in this case
            if (start > digits.length() || end > digits.length()) return false;

            // In this case we need to just check for ';' preceding to start
            // or next to end
            if (digits.charAt(start-1) == ';') return false;
        }
        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    this,
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            mLastNumberDialed = number;
                            updateDialAndDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    private boolean fastCall(int key) {
        if (!isDigitsEmpty()) {
            return false;
        }
//        mDigits.getText().clear();
        String phoneNumber1 = "";
        String phoneNumber2 = "";
        reInitSharePreference();
        if (mSharedPreferences1 != null) {
            phoneNumber1 = mSharedPreferences1.getString("fast_dial_" + key, "");
        }
        if (mSharedPreferences2 != null) {
            phoneNumber2 = mSharedPreferences2.getString("fast_dial_" + key, "");
        }
        if (phoneNumber1.equals("") && phoneNumber2.equals("")) {
            Toast toast = Toast.makeText(this,getText(R.string.no_fast_dial),Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER,0,-150);
            toast.show();
            return true;
        }
        mFastDial = true;
        mDigits.append(Integer.toString(key));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.fast_dial_choose)
                .setItems(dialType, myListenser).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

            public void onDismiss(DialogInterface dialog) {
                mFastDial = false;
                mDigits.getText().clear();
            }
        });
        dialog.show();
        return true;
    }

    android.content.DialogInterface.OnClickListener myListenser = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
                mHaptic.vibrate();  // Vibrate here too, just like we do for the regular keys
                dialButtonPressed();
            } else {
                mHaptic.vibrate();
                dialVideoButtonPressed();
            }
        }
    };

}
