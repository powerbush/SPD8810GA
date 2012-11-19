/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera;

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * An activity which disables the search key function.
 *
 * <p> To use it, just inherit from {@code NoSearchActivity} instead of
 * {@code Activity}.
 */
public class NoSearchActivity extends Activity {
    private static final String HISENSE_TV_PROCESS = "com.cmcc.mbbms";
    private static final String IN_VIDEO_CALL_CLASSNAME= "com.android.phone.InVideoCallScreen";
    private static final int HISENSE_TV = 1;
    private static final int INCALL_VT = 2;
    public boolean isTvRunning = false; // add by wangxiaobin

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    // add by yangqingan 2011-11-15 for NEWMS00137877 begin
    @Override
    public void onCreate(Bundle icicle){
        super.onCreate(icicle);
        checkTVIsRunning();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (!isTvRunning) checkTVIsRunning();
    }

    private List<RunningTaskInfo> getRunningTaskInfo() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return am.getRunningTasks(100);
    }

    private void checkTVIsRunning() {
        isTvRunning = false;// add by wangxaiobin
        // fixed bug 15092 start, because cmmb released pmem so delete check
//        List<RunningTaskInfo> list = getRunningTaskInfo();
//        // modify by wangxiaobin 11-28 begin
//        for (RunningTaskInfo info : list) {
//            if (info.baseActivity.getPackageName().equals(HISENSE_TV_PROCESS)
//                    && info.numRunning > 0) {
//                showCheckDialog(HISENSE_TV);
//                break;
//            }
//        }
        // fixed bug 15092 end
    }

    // add by wangxiaobin 2011-11-28 begin
    private AlertDialog mDialog;
    private void showCheckDialog(int id) {
        switch (id) {
            case HISENSE_TV: {
                AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cannot_connect_camera)
                .setMessage(R.string.hisense_tv_is_running)
                .setNegativeButton(
                    R.string.details_ok,
                    new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create();
                dialog.setOnDismissListener(
                    new OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    });
                mDialog = dialog;
                dialog.show();
                isTvRunning = true;
                break;
            }
            case INCALL_VT: {
                AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cannot_connect_camera)
                .setMessage(R.string.in_video_call_is_running)
                .setNegativeButton(
                    R.string.details_ok,
                    new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create();
                dialog.setOnDismissListener(
                    new OnDismissListener(){
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    });
                mDialog = dialog;
                dialog.show();
                break;
            }
            default: break;
        }
    }

    // add by yangqingan 2011-12-05 begin
    protected void checkInCall() {
        isTvRunning = false; // add by wangxaiobin
        List<RunningTaskInfo> list = getRunningTaskInfo();
        // modify by wangxiaobin 11-28 begin
        for (RunningTaskInfo info : list) {
            StringBuffer buff = new StringBuffer();
            if (IN_VIDEO_CALL_CLASSNAME.equals(info.topActivity.getClassName())
                    && info.numRunning > 0) {
                isTvRunning = true;
                if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                    inCallHandler.sendEmptyMessage(0);
                } else {
                    showCheckDialog(INCALL_VT);
                }
                break;
            }
        }
    }

    private Handler inCallHandler = new Handler() {
        public void handleMessage(Message msg) {
            showCheckDialog(INCALL_VT);
        }
    };

    protected void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
        }
        isTvRunning = false;
    }

    protected final boolean doCheck() {
        boolean result = isTvRunning;
        if (!result) {
            checkInCall();
            result = isTvRunning;
        }
        return result;
    }

}
