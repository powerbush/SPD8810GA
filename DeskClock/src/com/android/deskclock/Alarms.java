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

package com.android.deskclock;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.os.SystemProperties;
import java.util.Calendar;
import java.util.TimeZone;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormatSymbols;

/**
 * The Alarms provider supplies info about Alarm Clock settings
 */
public class Alarms {

    // This action triggers the AlarmReceiver as well as the AlarmKlaxon. It
    // is a public action used in the manifest for receiving Alarm broadcasts
    // from the alarm manager.
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    // A public action sent by AlarmKlaxon when the alarm has stopped sounding
    // for any reason (e.g. because it has been dismissed from AlarmAlertFullScreen,
    // or killed due to an incoming phone call, etc).
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
    // can snooze the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";

    // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
    // can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";

    // This is a private action used by the AlarmKlaxon to update the UI to
    // show the alarm has been killed.
    public static final String ALARM_KILLED = "alarm_killed";

    // Extra in the ALARM_KILLED intent to indicate to the user how long the
    // alarm played before being killed.
    public static final String ALARM_KILLED_TIMEOUT = "alarm_killed_timeout";

    // This string is used to indicate a silent alarm in the db.
    public static final String ALARM_ALERT_SILENT = "silent";

    // This intent is sent from the notification when the user cancels the
    // snooze alert.
    public static final String CANCEL_SNOOZE = "cancel_snooze";

    // This string is used when passing an Alarm object through an intent.
    public static final String ALARM_INTENT_EXTRA = "intent.extra.alarm";

    // This extra is the raw Alarm object data. It is used in the
    // AlarmManagerService to avoid a ClassNotFoundException when filling in
    // the Intent extras.
    public static final String ALARM_RAW_DATA = "intent.extra.alarm_raw";

    // This string is used to identify the alarm id passed to SetAlarm from the
    // list of alarms.
    public static final String ALARM_ID = "alarm_id";

    final static String PREF_SNOOZE_ID = "snooze_id";
    final static String PREF_SNOOZE_TIME = "snooze_time";

    private final static String DM12 = "E h:mm aa";
    private final static String DM24 = "E k:mm";

    private final static String M12 = "h:mm aa";
    // Shared with DigitalClock
    final static String M24 = "kk:mm";

    // If FIRST_ALERT equals "true",that means it's the first alert after
    // boot_complete,
    // so the first alert's "poweroff" button turns visible,and FIRST_ALERT
    // turns false
    // after the first alert
    public static boolean FIRST_ALERT = false;

    // if FIRST_KLAXON equals "false",means it's the first klaxon since
    // boot_complete,
    // and,before the Alarmreceiver set the next alert,FIRST_KLAXON turns false
    public static boolean FIRST_KLAXON = false;

    // Flag for alarm exist or not
    private static File ALARM_FLAG_FILE = new File("/productinfo/alarm_flag");

    /**
     * Creates a new Alarm and fills in the given alarm's id.
     */
    public static long addAlarm(Context context, Alarm alarm) {
        ContentValues values = createContentValues(alarm);
        Uri uri = context.getContentResolver().insert(
                Alarm.Columns.CONTENT_URI, values);
        alarm.id = (int) ContentUris.parseId(uri);

        long timeInMillis = calculateAlarm(alarm);
        if (alarm.enabled) {
            clearSnoozeIfNeeded(context, timeInMillis);
        }
        setNextAlert(context);
        return timeInMillis;
    }

    /**
     * Removes an existing Alarm.  If this alarm is snoozing, disables
     * snooze.  Sets next alert.
     */
    public static void deleteAlarm(Context context, int alarmId) {
        if (alarmId == -1) return;

        ContentResolver contentResolver = context.getContentResolver();
        /* If alarm is snoozing, lose it */
        disableSnoozeAlert(context, alarmId);

        Uri uri = ContentUris.withAppendedId(Alarm.Columns.CONTENT_URI, alarmId);
        contentResolver.delete(uri, "", null);

        setNextAlert(context);
    }

    public static void deleteAllAlarm(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(Alarm.Columns.CONTENT_URI, new String[]{}, null, null, null);
        if(cursor != null && cursor.getCount() > 0){
            while(cursor.moveToNext()){
                int alarm_id = cursor.getInt(0);
                disableSnoozeAlert(context, alarm_id);
            }
        }
        contentResolver.delete(Alarm.Columns.CONTENT_URI, "", null);
        setNextAlert(context);
    }

    /**
     * Queries all alarms
     * @return cursor over all alarms
     */
    public static Cursor getAlarmsCursor(ContentResolver contentResolver) {
        return contentResolver.query(
                Alarm.Columns.CONTENT_URI, Alarm.Columns.ALARM_QUERY_COLUMNS,
                null, null, Alarm.Columns.DEFAULT_SORT_ORDER);
    }

    // Private method to get a more limited set of alarms from the database.
    private static Cursor getFilteredAlarmsCursor(
            ContentResolver contentResolver) {
        return contentResolver.query(Alarm.Columns.CONTENT_URI,
                Alarm.Columns.ALARM_QUERY_COLUMNS, Alarm.Columns.WHERE_ENABLED,
                null, null);
    }

    private static ContentValues createContentValues(Alarm alarm) {
        ContentValues values = new ContentValues(8);
        // Set the alarm_time value if this alarm does not repeat. This will be
        // used later to disable expire alarms.
        long time = 0;
        if (!alarm.daysOfWeek.isRepeatSet()) {
            time = calculateAlarm(alarm);
        }

        values.put(Alarm.Columns.ENABLED, alarm.enabled ? 1 : 0);
        values.put(Alarm.Columns.HOUR, alarm.hour);
        values.put(Alarm.Columns.MINUTES, alarm.minutes);
        values.put(Alarm.Columns.ALARM_TIME, alarm.time);
        values.put(Alarm.Columns.DAYS_OF_WEEK, alarm.daysOfWeek.getCoded());
        values.put(Alarm.Columns.VIBRATE, alarm.vibrate);
        values.put(Alarm.Columns.MESSAGE, alarm.label);

        // A null alert Uri indicates a silent alarm.
        values.put(Alarm.Columns.ALERT, alarm.alert == null ? ALARM_ALERT_SILENT
                : alarm.alert.toString());

        return values;
    }

    private static void clearSnoozeIfNeeded(Context context, long alarmTime) {
        // If this alarm fires before the next snooze, clear the snooze to
        // enable this alarm.
        SharedPreferences prefs =
                context.getSharedPreferences(AlarmClock.PREFERENCES, 0);
        long snoozeTime = prefs.getLong(PREF_SNOOZE_TIME, 0);
        if (alarmTime < snoozeTime) {
            clearSnoozePreference(context, prefs);
        }
    }

    /**
     * Return an Alarm object representing the alarm id in the database.
     * Returns null if no alarm exists.
     */
    public static synchronized Alarm getAlarm(Context context,ContentResolver contentResolver, int alarmId) {
        if(alarmId < 1){
            return null;
        }
        Cursor cursor = contentResolver.query(
                ContentUris.withAppendedId(Alarm.Columns.CONTENT_URI, alarmId),
                Alarm.Columns.ALARM_QUERY_COLUMNS,
                null, null, null);
        Alarm alarm = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                alarm = new Alarm(context,cursor);
            }
            cursor.close();
        }
        return alarm;
    }


    /**
     * A convenience method to set an alarm in the Alarms
     * content provider.
     * @return Time when the alarm will fire.
     */
    public static long setAlarm(Context context, Alarm alarm) {
        ContentValues values = createContentValues(alarm);
        ContentResolver resolver = context.getContentResolver();
        resolver.update(
                ContentUris.withAppendedId(Alarm.Columns.CONTENT_URI, alarm.id),
                values, null, null);

        long timeInMillis = calculateAlarm(alarm);

        if (alarm.enabled) {
            // Disable the snooze if we just changed the snoozed alarm. This
            // only does work if the snoozed alarm is the same as the given
            // alarm.
            // TODO: disableSnoozeAlert should have a better name.
            disableSnoozeAlert(context, alarm.id);

            // Disable the snooze if this alarm fires before the snoozed alarm.
            // This works on every alarm since the user most likely intends to
            // have the modified alarm fire next.
            clearSnoozeIfNeeded(context, timeInMillis);
        }

        setNextAlert(context);

        return timeInMillis;
    }

    /**
     * A convenience method to enable or disable an alarm.
     *
     * @param id             corresponds to the _id column
     * @param enabled        corresponds to the ENABLED column
     */

    public static void enableAlarm(
            final Context context, final int id, boolean enabled) {
        enableAlarmInternal(context, id, enabled);
        setNextAlert(context);
    }

    private static void enableAlarmInternal(final Context context,
            final int id, boolean enabled) {
        enableAlarmInternal(context, getAlarm(context,context.getContentResolver(), id),
                enabled);
    }

    private static void enableAlarmInternal(final Context context,
            final Alarm alarm, boolean enabled) {
        if (alarm == null) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues(2);
        values.put(Alarm.Columns.ENABLED, enabled ? 1 : 0);

        // If we are enabling the alarm, calculate alarm time since the time
        // value in Alarm may be old.
        if (enabled) {
            long time = 0;
            if (!alarm.daysOfWeek.isRepeatSet()) {
                time = calculateAlarm(alarm);
            }
            values.put(Alarm.Columns.ALARM_TIME, time);
        } else {
            // Clear the snooze if the id matches.
            disableSnoozeAlert(context, alarm.id);
        }

        resolver.update(ContentUris.withAppendedId(
                Alarm.Columns.CONTENT_URI, alarm.id), values, null, null);
    }

    public static Alarm calculateNextAlert(final Context context) {
        Alarm alarm = null;
        long minTime = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        Cursor cursor = getFilteredAlarmsCursor(context.getContentResolver());
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    Alarm a = new Alarm(context,cursor);
                    // A time of 0 indicates this is a repeating alarm, so
                    // calculate the time to get the next alert.
                    if (a.time == 0) {
                        a.time = calculateAlarm(a);
                    } else if (a.time < now) {
                        Log.v("Disabling expired alarm set for " +
                              Log.formatTime(a.time));
                        // Expired alarm, disable it and move along.
                        enableAlarmInternal(context, a, false);
                        continue;
                    }
                    if (a.time < minTime) {
                        minTime = a.time;
                        alarm = a;
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return alarm;
    }

    /**
     * Disables non-repeating alarms that have passed.  Called at
     * boot.
     */
    public static void disableExpiredAlarms(final Context context) {
        Cursor cur = getFilteredAlarmsCursor(context.getContentResolver());
        long now = System.currentTimeMillis();

        if (cur.moveToFirst()) {
            do {
                Alarm alarm = new Alarm(context,cur);
                // A time of 0 means this alarm repeats. If the time is
                // non-zero, check if the time is before now.
                if (alarm.time != 0 && alarm.time < now) {
                    Log.v("Disabling expired alarm set for " +
                          Log.formatTime(alarm.time));
                    enableAlarmInternal(context, alarm, false);
                }
            } while (cur.moveToNext());
        }
        cur.close();
    }

    /**
     * Called at system startup, on time/timezone change, and whenever
     * the user changes alarm settings.  Activates snooze if set,
     * otherwise loads all alarms, activates next alert.
     */
    public static void setNextAlert(final Context context) {
    	//add by niezhong for NEWMS00134096 11-02-11 start 
//        if (!enableSnoozeAlert(context)) {
//            Alarm alarm = calculateNextAlert(context);
//            if (alarm != null) {
//                enableAlert(context, alarm, alarm.time);
//            } else {
//                disableAlert(context);
//            }
//        } 
    	Alarm alarm = calculateNextAlert(context);
    	long time = getSnoozeAlert(context);
		if ((alarm != null && alarm.time <= time) 
				|| (alarm != null && time == -1)) {	    	  
			enableAlert(context, alarm, alarm.time);
		} 
		else if((alarm != null && time < alarm.time) 
				|| (alarm == null && time != -1)) {
			enableSnoozeAlert(context);
		}
		else {
			disableAlert(context);
		}
		//add by niezhong for NEWMS00134096 11-02-11 end
    }

    /**
     * Sets alert in AlarmManger and StatusBar.  This is what will
     * actually launch the alert when the alarm triggers.
     *
     * @param alarm Alarm.
     * @param atTimeInMillis milliseconds since epoch
     */
    private static void enableAlert(Context context, final Alarm alarm,
            final long atTimeInMillis) {
        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        if (Log.LOGV) {
            Log.v("** setAlert id " + alarm.id + " atTime " + atTimeInMillis);
        }

        Intent intent = new Intent(ALARM_ALERT_ACTION);

        // XXX: This is a slight hack to avoid an exception in the remote
        // AlarmManagerService process. The AlarmManager adds extra data to
        // this Intent which causes it to inflate. Since the remote process
        // does not know about the Alarm class, it throws a
        // ClassNotFoundException.
        //
        // To avoid this, we marshall the data ourselves and then parcel a plain
        // byte[] array. The AlarmReceiver class knows to build the Alarm
        // object from the byte[] array.
        Parcel out = Parcel.obtain();
        alarm.writeToParcel(out, 0);
        out.setDataPosition(0);
        intent.putExtra(ALARM_RAW_DATA, out.marshall());

        PendingIntent sender = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        am.set(AlarmManager.POWER_OFF_WAKEUP, atTimeInMillis, sender);
        alarm_flag_setup(context,atTimeInMillis,alarm);


        setStatusBarIcon(context, true);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(atTimeInMillis);
        String timeString = formatDayAndTime(context, c);
        saveNextAlarm(context, timeString);
    }

    private static String getAlarmPath(Context context,Alarm alarm){
        String alert = alarm.alert.toString();
        Uri alertUri = null;
        if(alert.contains("alarm_alert")){
            String value = Settings.System.getString(context.getContentResolver(), "alarm_alert");
            alertUri = Uri.parse(value);
        }else{
            alertUri = alarm.alert;
        }
        String [] project = {
                "_data"
        };
        String path = "";
        Cursor cursor = context.getContentResolver().query(alertUri, project, null, null, null);
        try{
            if(cursor != null && cursor.moveToFirst()){
                    path = cursor.getString(0);
                    Log.v("path" + path);
            }
        } catch (Exception ex){

        }finally{
            if(cursor != null){
                cursor.close();
                cursor = null;
            }
        }
        return path;
    }
    /**
     * Disables alert in AlarmManger and StatusBar.
     *
     * @param id Alarm ID.
     */
    static void disableAlert(Context context) {
        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent sender = PendingIntent.getBroadcast(
                context, 0, new Intent(ALARM_ALERT_ACTION),
                PendingIntent.FLAG_CANCEL_CURRENT);
        //am.cancel(sender);
        am.cancelAlarm(sender);
        alarm_flag_cancel();
        setStatusBarIcon(context, false);
        saveNextAlarm(context, "");
    }

    static void saveSnoozeAlert(final Context context, final int id,
            final long time) {
        SharedPreferences prefs = context.getSharedPreferences(
                AlarmClock.PREFERENCES, 0);
        if (id == -1) {
            clearSnoozePreference(context, prefs);
        } else {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putInt(PREF_SNOOZE_ID, id);
            ed.putLong(PREF_SNOOZE_TIME, time);
            ed.apply();
        }
        // Set the next alert after updating the snooze.
        setNextAlert(context);
    }

    /**
     * Disable the snooze alert if the given id matches the snooze id.
     */
    static void disableSnoozeAlert(final Context context, final int id) {
        SharedPreferences prefs = context.getSharedPreferences(
                AlarmClock.PREFERENCES, 0);
        int snoozeId = prefs.getInt(PREF_SNOOZE_ID, -1);
        if (snoozeId == -1) {
            // No snooze set, do nothing.
            return;
        } else if (snoozeId == id) {
            // This is the same id so clear the shared prefs.
            clearSnoozePreference(context, prefs);
        }
    }

    // Helper to remove the snooze preference. Do not use clear because that
    // will erase the clock preferences. Also clear the snooze notification in
    // the window shade.
    private static void clearSnoozePreference(final Context context,
            final SharedPreferences prefs) {
        final int alarmId = prefs.getInt(PREF_SNOOZE_ID, -1);
        if (alarmId != -1) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(alarmId);
        }

        final SharedPreferences.Editor ed = prefs.edit();
        ed.remove(PREF_SNOOZE_ID);
        ed.remove(PREF_SNOOZE_TIME);
        ed.apply();
    };

    /**
     * If there is a snooze set, enable it in AlarmManager
     * @return true if snooze is set
     */
    private static boolean enableSnoozeAlert(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                AlarmClock.PREFERENCES, 0);

        int id = prefs.getInt(PREF_SNOOZE_ID, -1);
        if (id == -1) {
            return false;
        }
        long time = prefs.getLong(PREF_SNOOZE_TIME, -1);

        // Get the alarm from the db.
        final Alarm alarm = getAlarm(context,context.getContentResolver(), id);
        if (alarm == null) {
            return false;
        }
        // The time in the database is either 0 (repeating) or a specific time
        // for a non-repeating alarm. Update this value so the AlarmReceiver
        // has the right time to compare.
        alarm.time = time;

        enableAlert(context, alarm, time);
        return true;
    }
    //add by niezhong for NEWMS00134096 11-02-11 start 
    /**
     * If there is a snooze set,return the time else return -1	 
     */
    private static long getSnoozeAlert(final Context context) {
    	SharedPreferences prefs = context.getSharedPreferences(
                AlarmClock.PREFERENCES, 0);

        int id = prefs.getInt(PREF_SNOOZE_ID, -1);
        if (id == -1) {
            return -1;
        }
        long time = prefs.getLong(PREF_SNOOZE_TIME, -1);

        // Get the alarm from the db.
        final Alarm alarm = getAlarm(context,context.getContentResolver(), id);
        if (alarm == null) {
            return -1;
        }
        // The time in the database is either 0 (repeating) or a specific time
        // for a non-repeating alarm. Update this value so the AlarmReceiver
        // has the right time to compare.
        alarm.time = time;

        return alarm.time;
    }
    //add by niezhong for NEWMS00134096 11-02-11 end
    /**
     * Tells the StatusBar whether the alarm is enabled or disabled
     */
    private static void setStatusBarIcon(Context context, boolean enabled) {
        Intent alarmChanged = new Intent("android.intent.action.ALARM_CHANGED");
        alarmChanged.putExtra("alarmSet", enabled);
        context.sendBroadcast(alarmChanged);
    }

    private static long calculateAlarm(Alarm alarm) {
        return calculateAlarm(alarm.hour, alarm.minutes, alarm.daysOfWeek)
                .getTimeInMillis();
    }

    /**
     * Given an alarm in hours and minutes, return a time suitable for
     * setting in AlarmManager.
     */
    static Calendar calculateAlarm(int hour, int minute,
            Alarm.DaysOfWeek daysOfWeek) {

        // start with now
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());

        int nowHour = c.get(Calendar.HOUR_OF_DAY);
        int nowMinute = c.get(Calendar.MINUTE);
		int nowSecond = c.get(Calendar.SECOND);


        // if alarm is behind current time, advance one day for power off clock
        if (hour < nowHour || hour == nowHour && minute < nowMinute || hour == nowHour
                && minute == nowMinute && 30 < nowSecond && Alarms.FIRST_KLAXON
                && SystemProperties.get("ro.bootmode", "unknown").equals("alarm")
                || hour == nowHour && minute == nowMinute && !Alarms.FIRST_KLAXON) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        // if alarm is behind current time, advance one day
//        if (hour < nowHour  ||
//            hour == nowHour && minute < nowMinute || 
//			hour == nowHour && minute == nowMinute && 30 < nowSecond) {
//            c.add(Calendar.DAY_OF_YEAR, 1);
//        }

	// add by niezhong for deskclock 08-27-11 begin (NEWMS00114442)
//	if (hour < nowHour  ||
//            hour == nowHour && minute <= nowMinute) {
//            c.add(Calendar.DAY_OF_YEAR, 1);
//        }
	// add by niezhong for deskclock 08-27-11 end (NEWMS00114442)
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        int addDays = daysOfWeek.getNextAlarm(c);
        if (addDays > 0) c.add(Calendar.DAY_OF_WEEK, addDays);
        return c;
    }

    static String formatTime(final Context context, int hour, int minute,
                             Alarm.DaysOfWeek daysOfWeek) {
        Calendar c = calculateAlarm(hour, minute, daysOfWeek);
        return formatTime(context, c);
    }

    /* used by AlarmAlert */
    static String formatTime(final Context context, Calendar c) {
        String format = get24HourMode(context) ? M24 : M12;
        return (c == null) ? "" : (String)DateFormat.format(format, c);
    }

    /**
     * Shows day and time -- used for lock screen
     */
    private static String formatDayAndTime(final Context context, Calendar c) {
        String format = get24HourMode(context) ? DM24 : DM12;
        return (c == null) ? "" : (String)DateFormat.format(format, c);
    }

    /**
     * Save time of the next alarm, as a formatted string, into the system
     * settings so those who care can make use of it.
     */
    static void saveNextAlarm(final Context context, String timeString) {
        Settings.System.putString(context.getContentResolver(),
                                  Settings.System.NEXT_ALARM_FORMATTED,
                                  timeString);
    }

    /**
     * @return true if clock is set to 24-hour mode
     */
    static boolean get24HourMode(final Context context) {
        return android.text.format.DateFormat.is24HourFormat(context);
    }

    /**
     * check if there is a alarm which has the same hour and minute
     * 
     * @param cr
     * @param hour
     * @param minute
     * @param alarmId
     * @return
     */
    public static boolean isSametimeAlarm(ContentResolver cr, int hour, int minute, int alarmId) {
        boolean flag = false;
        Cursor cursor = cr.query(Alarm.Columns.CONTENT_URI, Alarm.Columns.ALARM_QUERY_COLUMNS,
                Alarm.Columns.HOUR + " = " + new Integer(hour).toString() + " AND "
                        + Alarm.Columns.MINUTES + " = " + new Integer(minute).toString()
                        + " AND _id != " + alarmId, null, Alarm.Columns.DEFAULT_SORT_ORDER);
        flag = cursor.getCount() > 0;
        cursor.close();
        return flag;
    }

    /**
     * function to set up alarm flag
     * 
     * @return
     */
    private static void alarm_flag_setup(Context mContext,final long alarmTimeInMillis,Alarm alarm) {
        Calendar c = Calendar.getInstance();
        c.set(2011, 0, 1, 0, 0, 0);
        Calendar to = Calendar.getInstance();
        to.setTimeInMillis(alarmTimeInMillis);
        TimeZone zone = c.getTimeZone();
        long dstOffset = zone.getOffset(alarmTimeInMillis);
        long startTimeInMillis = c.getTimeInMillis();
        long dstAlarmTimeInMillis = alarmTimeInMillis - dstOffset;
        long timeDiffInMillis = dstAlarmTimeInMillis - startTimeInMillis;
        long timeDiffInSecs = timeDiffInMillis/1000;
      //bug[20603] to resolve clock don't alarm on right time 2012.06.20 add begin
        String prfsName = mContext.getPackageName();
        prfsName += "_preferences";
        SharedPreferences  prefs   = mContext.getSharedPreferences(prfsName,Context.MODE_WORLD_READABLE);
        String snooze_duration =  prefs.getString("snooze_duration", "10");
   //bug[20603] to resolve clock don't alarm on right time 2012.06.20  add end
        Log.v("write " + String.valueOf(timeDiffInSecs) + " to" + ALARM_FLAG_FILE);
        
        if (ALARM_FLAG_FILE.exists()) {
            Log.v(ALARM_FLAG_FILE + " already exist, delete it");
            try {
                ALARM_FLAG_FILE.delete();
                Log.v(ALARM_FLAG_FILE + " delete before write success");
            } catch (Exception e) {
                Log.v(ALARM_FLAG_FILE + " delete before write failed");
            }            
        } 
        
        try {
            FileWriter command = new FileWriter(ALARM_FLAG_FILE);
            try {
                command.write(String.valueOf(timeDiffInSecs));
                command.write("\n");
                command.write(String.valueOf(alarmTimeInMillis/1000));
                command.write("\n");
                alarm_duration_setup(mContext,command);
                alarm_path_setup(getAlarmPath(mContext,alarm),command);
	//bug[20603] to resolve clock don't alarm on right time 2012.06.20 add begin
	     command.write(snooze_duration);
	     command.write("\n");
     //bug[20603] to resolve clock don't alarm on right time  2012.06.20 add end
            } finally {
                command.close();
                Log.v(ALARM_FLAG_FILE + " write done");
            }
        } catch (Exception e) {
            Log.v(ALARM_FLAG_FILE + "write error");
        }
    }


    /**
     * function to set up alarm flag
     *
     * @return
     */
    private  static void alarm_duration_setup(Context context, FileWriter command) {

        final String dur =
                PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsActivity.KEY_DURATION,
                        "10");
        int duration = Integer.parseInt(dur);
        try {
                command.write(String.valueOf(duration));
                command.write("\n");
        } catch (Exception e) {
            Log.v(ALARM_FLAG_FILE + "write error");
        }finally{

        }
    }


    /**
     * function to set up alarm flag
     *
     * @return
     */
    private static  void alarm_path_setup(String alarmPath,FileWriter command) {
        try {
            command.write(alarmPath);
            command.write("\n");
        } catch (Exception e) {
            Log.v(ALARM_FLAG_FILE + "write error");
        } finally {

            Log.v(ALARM_FLAG_FILE + " write done");
        }
    }

    /**
     * function to cancel alarm flag
     * 
     * @return
     */
    private static void alarm_flag_cancel() {
        if (ALARM_FLAG_FILE.exists()) {
            Log.v(ALARM_FLAG_FILE + " exist");
            try {
                ALARM_FLAG_FILE.delete();
                Log.v(ALARM_FLAG_FILE + " delete success");
            } catch (Exception e) {
                Log.v(ALARM_FLAG_FILE + " delete failed");
            }
        } else {
            Log.v(ALARM_FLAG_FILE + " already delete");
        }
    }
}
