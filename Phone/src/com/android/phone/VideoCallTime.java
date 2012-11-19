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

package com.android.phone;

import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;

import java.util.List;

/**
 * Helper class used to keep track of various "elapsed time" indications
 * in the Phone app, and also to start and stop tracing / profiling.
 */
public class VideoCallTime extends CallTime {
    private static final String LOG_TAG = "PHONE/VideoCallTime";
    private static final boolean DBG = true;
    private static long mMediaStartTime = 0;
    
    public VideoCallTime(OnTickListener listener) {
        super(listener);
    }

    protected void updateElapsedTime(Call call) {
        if (mListener != null) {
            long duration = getCallDuration(call);
            mListener.onTickForCallTimeElapsed(duration / 1000);
        }
    }

    /* package */ void reset() {
        mMediaStartTime = SystemClock.uptimeMillis();
        super.reset();
    }


    /**
     * Returns a "call duration" value for the specified Call, in msec,
     * suitable for display in the UI.
     */
    /* package */ static long getCallDuration(Call call) {
        long duration = 0;
        /*List connections = call.getConnections();
        int count = connections.size();
        Connection c;

        if (count == 1) {
            c = (Connection) connections.get(0);
            //duration = (state == Call.State.ACTIVE
            //            ? c.getDurationMillis() : c.getHoldDurationMillis());
            duration = c.getDurationMillis();
        } else {
            for (int i = 0; i < count; i++) {
                c = (Connection) connections.get(i);
                //long t = (state == Call.State.ACTIVE
                //          ? c.getDurationMillis() : c.getHoldDurationMillis());
                long t = c.getDurationMillis();
                if (t > duration) {
                    duration = t;
                }
            }
        }

        if (DBG) log("updateElapsedTime, count=" + count + ", duration=" + duration);*/
        if (mMediaStartTime < SystemClock.uptimeMillis()) {
            duration = SystemClock.uptimeMillis() - mMediaStartTime;
        }
        if (DBG) log("updateElapsedTime, duration=" + duration);
        return duration;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, "[VideoCallTime] " + msg);
    }
}

