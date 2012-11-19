/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.transaction;

import static android.provider.Telephony.Sms.Intents.SMSCB_RECEIVED_ACTION;

import com.android.mms.ui.SmsCBClassZeroActivity;

import android.database.sqlite.SqliteWrapper;

import android.app.Service;
import android.content.ContentResolver;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;

import com.android.mms.LogTag;
import com.android.internal.telephony.gsm.SmsCBMessage.SmsCBPage;

import java.io.ByteArrayInputStream;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import com.android.mms.R;
import com.android.mms.ui.CellBroadcastSmsActivity;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import com.android.mms.ui.MessagingPreferenceActivity;
import android.text.TextUtils;
import android.media.AudioManager;

/**
 * This service essentially plays the role of a "worker thread", allowing us to
 * store incoming messages to the database, update notifications, etc. without
 * blocking the main thread that SmsReceiver runs on.
 */
public class SmsCBReceiverService extends Service {
	private static final String TAG = "SmsCBReceiverService";
	private static final Uri CBSMS_URI_T = Uri.parse("content://sms/cbsms");
	private ServiceHandler mServiceHandler;
	private Looper mServiceLooper;

	private String COLUMN_ID = "_id";
	private String COLUMN_ADDRESS = "address";
	private String COLUMN_BODY = "body";
	private String COLUMN_DATE = "date";
	private String COLUMN_READ = "read";
	private String COLUMN_SEEN = "seen";
	private String COLUMN_ICONID = "iconId";
	private String COLUMN_LANGID = "langId";
	private int DEFAULT_ICON = R.drawable.unread_cbsms;
	private int READ_ICON = R.drawable.read_cbsms;
	private int mResultCode;

	@Override
	public void onCreate() {
		// Temporarily removed for this duplicate message track down.
		// if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
		// Log.v(TAG, "onCreate");
		// }
		Log.i(TAG, "onCreate");
		HandlerThread thread = new HandlerThread(TAG,
				Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Temporarily removed for this duplicate message track down.
		// if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
		// Log.v(TAG, "onStart: #" + startId + ": " + intent.getExtras());
		// }

		mResultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);
		return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		// Temporarily removed for this duplicate message track down.
		// if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
		// Log.v(TAG, "onDestroy");
		// }
		mServiceLooper.quit();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		/**
		 * Handle incoming transaction requests. The incoming requests are
		 * initiated by the MMSC Server or by the MMS Client itself.
		 */
		@Override
		public void handleMessage(Message msg) {
			Log.i(TAG, "handleMessage");
			int serviceId = msg.arg1;
			Intent intent = (Intent) msg.obj;
			if (intent != null) {
				String action = intent.getAction();

				int error = intent.getIntExtra("errorCode", 0);
				if (SMSCB_RECEIVED_ACTION.equals(action)) {
					handleSmsCBReceived(intent, error);
				}
				// NOTE: We MUST not call stopSelf() directly, since we need to
				// make sure the wake lock acquired by AlertReceiver is
				// released.
				SmsCBReceiver.finishStartingService(SmsCBReceiverService.this,
						serviceId);
			}
		}

	}

	private static SmsCBPage parsePage(byte[] bytePage) {

		ByteArrayInputStream inStream = new ByteArrayInputStream(bytePage);
		SmsCBPage page = new SmsCBPage();
		page.gs = inStream.read();
		page.messageCode = inStream.read();
		page.updateNum = inStream.read();

		int high = inStream.read();
		int low = inStream.read();
		Log.i(TAG, "parsePage high " + high + "low :" + low);
		page.msgId = (high << 0x8) | low;

		page.dcs = inStream.read();
		page.sequenceNum = inStream.read();
		page.totalNum = inStream.read();

		high = inStream.read();
		low = inStream.read();
		Log.i(TAG, "parsePage high " + high + "low :" + low);
		page.langId = (high << 0x8) | low;

		int length;
		length = inStream.read();
		byte[] byteContent1 = new byte[length];
		Log.i(TAG, "parsePage input " + "length of bytes array :" + length
				+ "page.msgId  " + page.msgId);
		inStream.read(byteContent1, 0, length);
		page.content = new String(byteContent1);
		Log.i(TAG, "parsePage input " + page.content);

		return page;
	}

	private void handleSmsCBReceived(Intent intent, int error) {

		Object[] messages = (Object[]) intent.getSerializableExtra("pages");
		byte[][] bytePages = new byte[messages.length][];
		Log.i(TAG, "handleSmsCBReceived length" + messages.length);
		SmsCBPage[] pages = new SmsCBPage[messages.length];
		for (int i = 0; i < messages.length; i++) {
			bytePages[i] = (byte[]) messages[i];
		}

		for (int i = 0; i < messages.length; i++) {
			pages[i] = new SmsCBPage();
			pages[i] = parsePage(bytePages[i]);
			Log.i(TAG, "handleSmsCBReceived content " + pages[i].content);

		}

		Uri messageUri = insertMessage(this, pages, error);

		if (messageUri != null) {
			// Called off of the UI thread so ok to block.
			String string = "SMS CB !";
			showNotification(string, null, null, R.drawable.stat_notify_sms,
					R.drawable.stat_notify_sms, intent);
			if (CellBroadcastSmsActivity.IschangeList()) {

				notifyListChanged();
			}
		}
	}

	/**
	 * If the message is a class-zero message, display it immediately and return
	 * null. Otherwise, store it using the <code>ContentResolver</code> and
	 * return the <code>Uri</code> of the thread containing this message so that
	 * we can use it for notification.
	 */
	private Uri insertMessage(Context context, SmsCBPage[] msgs, int error) {
		// Build the helper classes to parse the messages.
		Log.i(TAG, "insertMessage gs" + msgs[0].gs);

		if (msgs[0].gs == 0) {
			displayClassZeroMessage(context, msgs);
			return null;
		}
		// else if (sms.isReplace()) {
		// return replaceMessage(context, msgs, error);
		// }
		else {
			return storeMessage(context, msgs, error);
		}
	}

	private Uri storeMessage(Context context, SmsCBPage[] msgs, int error) {
		SmsCBPage msg = new SmsCBPage();
		msg = msgs[0];
		int pageCount = msgs.length;
		Log.i(TAG, "storeMessage : ");
		// Store the message in the content provider.
		ContentValues values = extractContentValues(msg);

		if (pageCount == 1) {
			// There is only one part, so grab the body directly.
			values.put(COLUMN_BODY, msg.content);
		} else {
			// Build up the body from the parts.
			StringBuilder body = new StringBuilder();
			for (int i = 0; i < pageCount; i++) {

				body.append(msgs[i].content);
			}
			values.put(COLUMN_BODY, body.toString());
		}
		Log.i(TAG, "storeMessage : msgId " + msg.msgId);
		values.put(COLUMN_ADDRESS, msg.msgId);
		values.put(COLUMN_ICONID, DEFAULT_ICON);
		values.put(COLUMN_LANGID, msg.langId);
		// values.put(COLUMN_DATE, enable);

		ContentResolver resolver = context.getContentResolver();

		Uri insertedUri = SqliteWrapper.insert(context, resolver, CBSMS_URI_T,
				values);

		// Now make sure we're not over the limit in stored messages
		// Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(),
		// threadId);

		return insertedUri;
	}

	/**
	 * Extract all the content values except the body from an SMS message.
	 */
	private ContentValues extractContentValues(SmsCBPage msg) {
		// Store the message in the content provider.
		ContentValues values = new ContentValues();

		values.put(COLUMN_READ, 0);
		values.put(COLUMN_SEEN, 0);

		return values;
	}

	/**
	 * Displays a class-zero message immediately in a pop-up window with the
	 * number from where it received the Notification with the body of the
	 * message
	 * 
	 */

	private void displayClassZeroMessage(Context context, SmsCBPage[] msgs) {
		// Using NEW_TASK here is necessary because we're calling
		// startActivity from outside an activity.
		Intent smscbDialogIntent = new Intent(context,
				SmsCBClassZeroActivity.class)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
						| Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

		Log.i(TAG, "displayClassZeroMessage length :" + msgs.length);
		smscbDialogIntent.putExtra("length", msgs.length);

		for (int i = 0; i < msgs.length; i++) {

			String gsS = "gs" + i;
			smscbDialogIntent.putExtra(gsS, msgs[i].gs);
			String messageCodeS = "messageCode" + i;
			smscbDialogIntent.putExtra(messageCodeS, msgs[i].messageCode);
			String updateNumS = "updateNum" + i;
			smscbDialogIntent.putExtra(updateNumS, msgs[i].updateNum);
			String msgIdS = "msgId" + i;
			smscbDialogIntent.putExtra(msgIdS, msgs[i].msgId);
			Log.i(TAG, "storeMessage : msgId " + msgs[i].msgId);
			String dcsS = "dcs" + i;
			smscbDialogIntent.putExtra(dcsS, msgs[i].dcs);
			String sequenceNumS = "sequenceNum" + i;
			smscbDialogIntent.putExtra(sequenceNumS, msgs[i].gs);
			String totalNumS = "totalNum" + i;
			smscbDialogIntent.putExtra(totalNumS, msgs[i].totalNum);
			String langIdS = "langId" + i;
			smscbDialogIntent.putExtra(langIdS, msgs[i].langId);
			Log.i(TAG, "onCreate langId :" + msgs[i].langId);
			String contentS = "content" + i;
			smscbDialogIntent.putExtra(contentS, msgs[i].content);
			Log.i(TAG, "displayClassZeroMessage content :" + msgs[i].content);
		}

		context.startActivity(smscbDialogIntent);
	}

	private void registerForServiceStateChanges() {
		Context context = getApplicationContext();
		unRegisterForServiceStateChanges();

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
		if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
			Log.v(TAG, "registerForServiceStateChanges");
		}

		context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
	}

	private void unRegisterForServiceStateChanges() {
		if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
			Log.v(TAG, "unRegisterForServiceStateChanges");
		}
		try {
			Context context = getApplicationContext();
			context.unregisterReceiver(SmsReceiver.getInstance());
		} catch (IllegalArgumentException e) {
			// Allow un-matched register-unregister calls
		}
	}

	private void showNotification(String tickerText, String contentTitle,
			String contentText, int id, int resId, Intent intent) {

		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);

		Log.i(TAG, "showNotification content :" + tickerText);
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		Notification notification = new Notification(resId, tickerText,
				System.currentTimeMillis());

		String vibrateWhen;
		if (sp.contains(MessagingPreferenceActivity.NOTIFICATION_VIBRATE_WHEN)) {
			vibrateWhen = sp
					.getString(
							MessagingPreferenceActivity.NOTIFICATION_VIBRATE_WHEN,
							null);
		} else if (sp
				.contains(MessagingPreferenceActivity.NOTIFICATION_VIBRATE)) {
			vibrateWhen = sp.getBoolean(
					MessagingPreferenceActivity.NOTIFICATION_VIBRATE, false) ? getString(R.string.prefDefault_vibrate_true)
					: getString(R.string.prefDefault_vibrate_false);
		} else {
			vibrateWhen = getString(R.string.prefDefault_vibrateWhen);
		}

		boolean vibrateAlways = vibrateWhen.equals("always");
		boolean vibrateSilent = vibrateWhen.equals("silent");
		AudioManager audioManager = (AudioManager) this
				.getSystemService(Context.AUDIO_SERVICE);
		boolean nowSilent = audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;

		if (vibrateAlways || vibrateSilent && nowSilent) {
			notification.defaults |= Notification.DEFAULT_VIBRATE;
		}

		String ringtoneStr = sp.getString(
				MessagingPreferenceActivity.NOTIFICATION_RINGTONE, null);
		notification.sound = TextUtils.isEmpty(ringtoneStr) ? null : Uri
				.parse(ringtoneStr);

		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		notification.defaults |= Notification.DEFAULT_LIGHTS;

		Log.i(TAG, "showNotification content :" + tickerText);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				intent, 0);
		notification.setLatestEventInfo(this, contentTitle, contentText,
				contentIntent);
		nm.notify(id, notification);

	}

	private void notifyListChanged() {

		Log.i(TAG, "notifyListChanged :  ");

		Context context = getApplicationContext();

		Intent intent = new Intent(context, CellBroadcastSmsActivity.class);

		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);

	}

}
