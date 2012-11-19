/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.android.music;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;

import java.lang.IllegalArgumentException;
import java.util.Scanner;
import java.util.regex.Pattern;

import com.android.music.R;

public class ScrollLrcView extends ScrollView {
    /*
     * static members
     */
    private static final String LOG_TAG = "ScrollLrcView";

    public static final int LRC_MODE_SINGLE = 0;
    public static final int LRC_MODE_MULTIPLE = 1;

    /*
     * members
     */
    private LinearLayout mLrcArea = null;    // the lyric TextViews are placed under a LinearLayout.
    private int mLrcMode = LRC_MODE_SINGLE;  // single line, or multiple line

    private LyricsBody mLrc = null;    // the actual lyric object
    private String mLastTrack = null;

    private int[] mLyricPos = null;    // the position (vertical) of each lyric sentences. in pixel
    private int[] mLyricHeight = null; // the height of each lyric sentences (TextView). in pixel
    private int mCurrIndex = -1;       // where the lyrics has been scrolled to (by index)
    
    private int mScrDensity = DisplayMetrics.DENSITY_MEDIUM;

    /*
     * constructors
     * we should define all 3 constructors if we want to use this view in layout .xml files.
     */
    public ScrollLrcView(Context context) {
        this(context, null);
        Log.v(LOG_TAG, "ScrollLrcView : constructed with (Context)");
    }

    public ScrollLrcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false); // should set this. Otherwise the view will cause track ball behavior abnormal
        
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(dm);
        mScrDensity = dm.densityDpi;

        resetLrcState();
        Log.v(LOG_TAG, "ScrollLrcView : constructed with (Context, AttributeSet)");
    }

    public ScrollLrcView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFocusable(false); // should set this. Otherwise the view will cause track ball behavior abnormal

        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(dm);
        mScrDensity = dm.densityDpi;

        resetLrcState();
        Log.v(LOG_TAG, "ScrollLrcView : constructed with (Context, AttributeSet, int)");
    }

    /*
     * after the ScrollLrcView is created, call this to load and setup lyrics
     *
     * [in]context: application context.
     * [in]file: the absolute path-name of the audio sound track file.
     * [in]lrcMode: the lyrics displaying mode (single-line / multiple-line)
     * [in]duration: in milliseconds, the duration of the track being played.
     *
     * return value: true if the lyrics are loaded and parsed successfully.
     */
    public boolean loadLyrics(Context context, String file) {
    	if (file.equals(mLastTrack)) { // if the same track, avoid loading it again
    		return true;
    	}

    	resetLrcState(); // reset first
    	String lrcfile = null;
		Scanner scn = new Scanner(file);

		// the lyric file, with surffix .lrc, should have the same name with the audio file.
		// try to find a valid lyrics file for this track.
		String tmp = scn.findInLine(Pattern.compile(".*/.*\\."));
		if (null != tmp) {
    		lrcfile = new StringBuilder(tmp).append("lrc").toString();
    		mLrc = LyricsBody.getLyric(context, lrcfile);
		}

        return (mLrc != null);
    }

    /*
     * when the first time the lyrics are loaded, the layout and views need to be setup.
     *
     * [in]context: application context.
     * [in]file: the current file to be load for lyrics.
     * [in]lrcMode: the lyrics displaying mode (single-line / multiple-line)
     * [in]duration: in milliseconds, the duration of the track being played.
     *
     * return value: true if the lyrics view is setup successfully.
     */
    public boolean setupLyrics(Context context, String file, int lrcMode, int duration) {
        if (file == null) {
            return false;
        }

    	if (file.equals(mLastTrack)) { // if the same track, avoid to setup the ScrollView again
    		return true;
    	}
    	mLastTrack = new String(file);

    	return reSetupLyrics(context, lrcMode, duration);
    }

    /*
     * when the lyrics mode (single-line / multiple-line) is changed, the layout and views need to
     * be re-setup again.
     *
     * [in]context: application context.
     * [in]lrcMode: the lyrics displaying mode (single-line / multiple-line)
     * [in]duration: in milliseconds, the duration of the track being played.
     *
     * return value: true if the lyrics view is setup successfully.
     */
    public boolean reSetupLyrics(Context context, int lrcMode, int duration) {
        removeAllViews();  // clean up first

        switch (lrcMode) {
            case LRC_MODE_SINGLE:
                mLrcArea = (LinearLayout) View.inflate(context, R.layout.lrcbody_single, null);
                addView(mLrcArea);
                break;
                
            case LRC_MODE_MULTIPLE:
                mLrcArea = (LinearLayout) View.inflate(context, R.layout.lrcbody_multiple, null);
                addView(mLrcArea);
                break;
                
            default:
                throw new IllegalArgumentException();
        }
        mLrcMode = lrcMode;

        // if lyrics is not valid then display error message
        if (null == mLrc) {
            TextView tv = (TextView) View.inflate(context, R.layout.lrcsentence_single, null);
            tv.setText(LyricsBody.getLastLrcErrorMsg());
            tv.setTextColor(getResources().getColor(R.color.normal_sentence));
            mLrcArea.addView(tv);

            return false;
        }

        // the LyricsBody object {mLrc} has been successfully loaded and parsed
        switch (lrcMode) {
            case LRC_MODE_SINGLE: // only one line of lyric sentence is displayed.
                mCurrIndex = mLrc.getCurrentIndex(duration);
                TextView tv_single = (TextView) View.inflate(context, R.layout.lrcsentence_single, null);
                tv_single.setText(mLrc.getSentenceText(mCurrIndex));
                mLrcArea.addView(tv_single, 0);
                break;

            case LRC_MODE_MULTIPLE:
                mCurrIndex = -1;
                int cnt = mLrc.getSentenceCnt();
                for (int k = 0; k < cnt; k++) {
                    TextView tv = (TextView) View.inflate(context, R.layout.lrcsentence, null);
                    tv.setText(mLrc.getSentenceText(k));
                    mLrcArea.addView(tv, k);  // add a child view for one sentence at position <k>
                }
                break;

            default:
                throw new IllegalArgumentException();
        };

        return true;
    }

    /*
     * under multiple-line mode, update the content of mLrcArea so that it can get correct child
     * views' layout height.
     * should be called asynchronously, normally by sending a message and dealing with handler.
     */
    public void update() {
        resetLrcState();
        if (null == mLrc) {
            return;
        }

        switch (mLrcMode) {
	        case LRC_MODE_MULTIPLE:
	            int cnt = mLrc.getSentenceCnt();
	            mLyricPos = new int[cnt];
	            mLyricPos[0] = 0;             // the first sentence's position should be ZERO
	            mLyricHeight = new int[cnt];

	            int totalHeight = 0;
	            for (int j = 0; j < cnt; j++) {
	                TextView t = (TextView) mLrcArea.getChildAt(j);
	                int height = t.getHeight();
	                mLyricHeight[j] = height;

	                totalHeight += height;
	                if (j < cnt - 1) {
	                    mLyricPos[j + 1] = totalHeight;
	                }
	            }
	        	break;

	        case LRC_MODE_SINGLE:
	        	break;

	        default:
        		break;
        };
    }

    /*
     * scroll the ScrollLrcView to its intended position.
     *
     * [in]context: the application context.
     * [in]currMillisec: the current duration of the music track being played. in millisecond.
     *
     * there's 2 modes of lyrics displaying: single line mode / multiple line
     */
    public void scrollLyrics(Context context, int currMillisec) {
        // avoid invalid lyric state
        // 20110516 evening
        if (!isValidLrcState()) {
            return;
        }

        int destIndex = mLrc.getCurrentIndex(currMillisec);

        switch (mLrcMode) {
            case LRC_MODE_SINGLE:
            {
            	if (mCurrIndex != destIndex && destIndex >= 0) {
                    TextView tv_single = (TextView) mLrcArea.getChildAt(0); // only one TextView
                    tv_single.setText(mLrc.getSentenceText(destIndex));

                    // the animation
                    Animation a = new TranslateAnimation(Animation.ABSOLUTE, 0.0f,
                                              Animation.ABSOLUTE, 0.0f,
                                              Animation.ABSOLUTE, (float)tv_single.getHeight(),
                                              Animation.ABSOLUTE, 0.0f);
                    a.setDuration(500);
                    a.setRepeatCount(0);
                    a.setInterpolator(AnimationUtils.loadInterpolator(context,
                                                             android.R.anim.decelerate_interpolator));

                    tv_single.startAnimation(a);

                    mCurrIndex = destIndex; // has been scrolled
            	}

                break;
            } // case LRC_MODE_SINGLE

            case LRC_MODE_MULTIPLE:
            {
                // avoiding illegal array operation, 20110516 evening
                if (destIndex < 0 || destIndex > mLyricPos.length - 1) {
                    return;
                }

                int destPos = mLyricPos[destIndex]; // the position it needs to scroll to

                // the text color state should be changed
                if (mCurrIndex != destIndex && destIndex >= 0) {
                    TextView tv = (TextView) mLrcArea.getChildAt(destIndex);
                    tv.setTextColor(getResources().getColor(R.color.highlight_sentence)); // lrccolor.xml @ /values

                    // the current one, restore the default color
                    if (mCurrIndex >= 0) {
                        tv = (TextView) mLrcArea.getChildAt(mCurrIndex);
                        tv.setTextColor(getResources().getColor(R.color.normal_sentence)); // lrccolor.xml @ /values
                    }

                    mCurrIndex = destIndex; // has been scrolled
                }

                int destScroll = destPos;   // the real position where the scrollView should scrolls to
                // (1)
                int delta;
                float ratio = (float)(currMillisec - mLrc.getSentenceTime(destIndex));
                if (LyricsBody.INTERVAL_LAST_SENTENCE == mLrc.getIntervalToNext(destIndex)) {
                    delta = 0;
                } else {
                    ratio /= (float)mLrc.getIntervalToNext(destIndex);
                    delta = (int)( ratio * (float)mLyricHeight[destIndex] );
                }

                // (2)
                destScroll += delta;

                // (3)
                if (destScroll < getHeight() / 2) {
                    destScroll = 0;
                }
                else {
                    destScroll -= getHeight() / 2;
                }

                // (4)
                scrollTo(getScrollX(), destScroll);

                break;
            } // case LRC_MODE_MULTIPLE

            default:
                break;
        };
    }
    
    public void resetHighlight() {
        if (!isValidLrcState()) {
            return;
        }
        
        if (mLrcMode == LRC_MODE_MULTIPLE) {
            for (int i = 0; i < mLrc.getSentenceCnt(); i++) {
                TextView tv = (TextView) mLrcArea.getChildAt(i);
                tv.setTextColor(getResources().getColor(R.color.normal_sentence));
            }
        }
    }
    
    public int singleLineModeHeightPixel() {
        switch (mScrDensity) {
            case DisplayMetrics.DENSITY_HIGH:
                return 66;
                
            case DisplayMetrics.DENSITY_MEDIUM:
                return 44;

            case DisplayMetrics.DENSITY_LOW:
                return 33;

            default:
                return 44;
        }
    }

    public int singleLineModePaddingPixel() {
        switch (mScrDensity) {
            case DisplayMetrics.DENSITY_HIGH:
                return 15;
                
            case DisplayMetrics.DENSITY_MEDIUM:
                return 10;

            case DisplayMetrics.DENSITY_LOW:
                return 7;

            default:
                return 10;
        }
    }

    /*
     * getters
     */
    public int getLrcListHeight() {
        return mLyricPos[mLyricPos.length - 2];
    }

    private void resetLrcState() {
        mLyricPos = null;
        mLyricHeight = null;
        mCurrIndex = -1;
    }

    private boolean isValidLrcState() {
        if (mLrc != null) {
            if (mLrcMode == LRC_MODE_MULTIPLE) {
                return (mLyricPos != null && mLyricHeight != null);
            }
            return true;
        }
        return false;
    }
}
