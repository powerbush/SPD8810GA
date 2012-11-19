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

import java.lang.Integer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Collections;
import java.util.Vector;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.android.music.R;

import android.content.Context;
import android.util.Log;

class LyricsBody {
    /*
     * static members.
     */
    public static final int TAG_UNKNOWN = -1;
    public static final int TAG_TRACK_INFO_AL = 0;
    public static final int TAG_TRACK_INFO_AR = 1;
    public static final int TAG_TRACK_INFO_BY = 2;
    public static final int TAG_TRACK_INFO_RE = 3;
    public static final int TAG_TRACK_INFO_TI = 4;
    public static final int TAG_TRACK_INFO_VE = 5;

    public static final int INTERVAL_INVALID = -1;
    public static final int INTERVAL_LAST_SENTENCE = -1;
    public static final int INVALID_INDEX = -1;

    private static final String LOG_TAG = "LyricsBody";

    private static String mLrcErrorMsg = null; // if some error happens with the lyrics file
                                               // the error information is stored here.

    /*
     * data members.
     */
    private LyricSentence mAlbumName = null;
    private LyricSentence mArtistName = null;
    private LyricSentence mTrackTitle = null;
    private LyricSentence mLyricAuthor = null;
    private LyricSentence mLyricBuilder = null;
    private LyricSentence mLyricBuilderVer = null;
    private int mAdjustMilliSec = 0;

    // this is the actual lyric content
    private Vector<LyricSentence> mLyricContent = new Vector<LyricSentence>();

    // for index search
    private int mLastIndex = INVALID_INDEX;
    private int mLastMillisec = 0;

    // the application context
    private Context mContext = null;

    /*
     * constructor
     */
    private LyricsBody(Context context) {
        mContext = context;

        String str = context.getResources().getString(R.string.lrc_unknown_info_tag);
        mAlbumName = new LyricSentence(str);
        mArtistName = new LyricSentence(str);
        mTrackTitle = new LyricSentence(str);
        mLyricAuthor = new LyricSentence(str);
        mLyricBuilder = new LyricSentence(str);
        mLyricBuilderVer = new LyricSentence(str);
    }

    /*
     * parse the lyrics from a .lrc/.LRC file.
     *
     * [in]context: application context
     * [in]filepathname: the absolute path-name of the .lrc/.LRC file
     *
     * return value: a LyricsBody object. null if this method fails; call "getLastLrcErrorMsg()" to
     *               retrieve the failure message.
     */
    public static LyricsBody getLyric(Context context, String filepathname) {
        if (null == filepathname || null == context) {
            throw new IllegalArgumentException();
        }

        LyricsBody lyric = new LyricsBody(context);
        try {
            lyric.parseLrc(filepathname);

        } catch (LrcFileNotFoundException e) {
            mLrcErrorMsg = new String(e.getMessage());
            Log.d(LOG_TAG, "getLyric : .lrc file not found.");
            return null;
        } catch (LrcFileUnsupportedEncodingException e) {
            mLrcErrorMsg = new String(e.getMessage());
            Log.d(LOG_TAG, "getLyric : unsupported textual encoding.");
            return null;
        } catch (LrcFileIOException e) {
            mLrcErrorMsg = new String(e.getMessage());
            Log.d(LOG_TAG, "getLyric : I/O error when accessing .lrc file.");
            return null;
        } catch (LrcFileInvalidFormatException e) {
            mLrcErrorMsg = new String(e.getMessage());
            Log.d(LOG_TAG, "getLyric : Invalid LRC file format.");
            return null;
        }

        return lyric;   // if success
    }

    /*
     * returns the error message of last call to "getLyric"
     */
    public static String getLastLrcErrorMsg() {
        String str = new String(mLrcErrorMsg);
        return str;
    }

    /*
     * get the information of the lyrics.
     */
    public String getLyricInfo(int infoTag) {
        switch (infoTag) {
            case TAG_TRACK_INFO_AL:
                return mAlbumName.getSentence();

            case TAG_TRACK_INFO_AR:
                return mArtistName.getSentence();

            case TAG_TRACK_INFO_BY:
                return mLyricAuthor.getSentence();

            case TAG_TRACK_INFO_RE:
                return mLyricBuilder.getSentence();

            case TAG_TRACK_INFO_TI:
                return mTrackTitle.getSentence();

            case TAG_TRACK_INFO_VE:
                return mLyricBuilderVer.getSentence();

            default:
                Log.d(LOG_TAG, "getLyricInfo : invalid {inforTag} parameter.");
                throw new IllegalArgumentException();
        }
    }

    public int getLyricOffset() {
        return mAdjustMilliSec;
    }

    public int getSentenceCnt() {
        return mLyricContent.size();
    }

    public String getSentenceText(int index) {
        return mLyricContent.elementAt(index).getSentence();
    }

    public int getSentenceTime(int index) {
        return mLyricContent.elementAt(index).getTime();
    }

    /*
     * get the time interval (in millisecond) between the current sentence and the next one.
     *
     * [in]index: the index of current sentence.
     *
     * return value: the time interval. -1 if the interval is not valid.
     */
    public int getIntervalToNext(int index) {
        if (0 <= index && index <= mLyricContent.size() - 2) {
            return mLyricContent.elementAt(index + 1).getTime()
                    - mLyricContent.elementAt(index).getTime();
        } else if (index == mLyricContent.size() - 1) {
            return INTERVAL_LAST_SENTENCE;  // this is already the last sentence.
        }

        return INTERVAL_INVALID;
    }

    /*
     * get the current lyrics sentence's index according to the duration of the track being played.
     *
     * [in]millisecond: the current duration of the track being played.
     *
     * return value: the index of the current lyrics sentence. -1 if it fails the get the index.
     */
    public int getCurrentIndex(int millisecond) {
        int low = 0;
        int high = mLyricContent.size() - 1;

        int ret = 0;
        if (INVALID_INDEX != mLastIndex) {
            if (millisecond > mLastMillisec) {
                ret = searchIndex(mLastIndex, high, millisecond);
            } else if (millisecond < mLastMillisec) {
                ret = searchIndex(low, mLastIndex, millisecond);
            } else {
                return mLastIndex;
            }
        } else {
            ret = searchIndex(low, high, millisecond);
        }

        mLastIndex = ret;
        mLastMillisec = millisecond;

        return ret;
    }

    /*
     * parse the real lyrics file (.lrc format)
     *
     * [in] filepathname: the absolute file path name of the .lrc file.
     */
    private void parseLrc(String filepathname) throws LrcFileNotFoundException,
                                                      LrcFileUnsupportedEncodingException,
                                                      LrcFileIOException,
                                                      LrcFileInvalidFormatException {
        String file = new String(filepathname);

        // dealing with different text file encodings.
        // (1) try reading BOM from text file
        FileInputStream lrcFileStream = null;
        try {
            lrcFileStream = new FileInputStream(new File(file));

        } catch (FileNotFoundException e) {
            // if failed to load at the same folder then try /sdcard/Music/Lyrics
            // change the path to specified folder
            Scanner s = new Scanner(file);
            String fileName = s.findInLine(Pattern.compile("(?!.*/).*"));
            String anotherFile = new StringBuilder(mContext.getResources().getString(R.string.lrc_file_path)).append(fileName).toString();

            try {
                lrcFileStream = new FileInputStream(new File(anotherFile));
                file = anotherFile;

            } catch (FileNotFoundException ee) {
                throw new LrcFileNotFoundException(
                        mContext.getResources().getString(R.string.lrc_file_not_found));
            }
        }

        BufferedInputStream bin = null;
        String code = null;
        try {
            bin = new BufferedInputStream(lrcFileStream);
            int p = (bin.read() << 8) + bin.read(); // first 2 bytes
            int q = bin.read(); // 3rd byte

            // first check to see if it's Unicode Transition Format (UTF-8, UTF-16)
            switch (p) {
                // first check UTF-8 with BOM
                case 0xefbb:
                    if (q == 0xbf) {
                        code = "UTF-8"; // on windows, UTF-8 text files have a BOM: "EF BB BF";
                    } else {
                        code = "UNKNOWN"; // however, on linux, there's no BOM for UTF-8
                    }
	                break;

                // UTF-16 with BOM
                case 0xfffe: // little endian
                case 0xfeff: // big endian
	                code = "UTF-16"; // the Scanner can recognize big endian or little endian
	                break;

                default:
	                code = "UNKNOWN";
	                break;
            }

        } catch (IOException e) {
            Log.d(LOG_TAG, "parseLrc : I/O error when reading .lrc file");
            throw new LrcFileIOException(
                    mContext.getResources().getString(R.string.lrc_file_io_error));
        }

        // (2) if no BOM detected, we don't know if it's Unicode or ISO-8859-1 compatible encoding
        // try firstly to detect UTF-8 without BOM
        // by going through all the text file to see if there's one "character unit" that does not 
        // match the UTF-8 encoding rule.
        if ("UNKNOWN".equals(code)) {
            try {
                lrcFileStream = new FileInputStream(new File(file));

            } catch (FileNotFoundException e) {
                throw new LrcFileNotFoundException(
                        mContext.getResources().getString(R.string.lrc_file_not_found));
            }

            try {
                bin = new BufferedInputStream(lrcFileStream);
                byte[] value = new byte[3];
                int result = 1;

                boolean isUTF8 = true;
                int byte1 = 0;
                int byte2 = 0;
                int byte3 = 0;
                while (result > 0) {
                    result = bin.read(value, 0, 1);
                    if (result <= 0) {
                        break;
                    }

                    byte1 = value[0] & 0xff;
                    if ((byte1 <= 0x7f) && (byte1 >= 0x01)) {
                        // matches 1 byte encoding
                        continue;
                    } else {
                        // need read one more byte
                        result = bin.read(value, 1, 1);
                        if (result <= 0) {
                            break;
                        }

                        byte2 = value[1] & 0xff;
                        if ((byte1 <= 0xdf) && (byte1 >= 0xc0)
                                && (byte2 <= 0xbf) && (byte2 >= 0x80)) {
                            // matches 2 bytes encoding
                            continue;
                        } else {
                            // need read one more byte
                            result = bin.read(value, 2, 1);
                            if (result <= 0) {
                                break;
                            }
                            
                            byte3 = value[2] & 0xff;
                            if ((byte1 <= 0xef) && (byte1 >= 0xe0) && (byte2 <= 0xbf)
                                    && (byte2 >= 0x80) && (byte3 <= 0xbf) && (byte3 >= 0x80)) {
                                continue;
                            } else {
                                // don't match any of this it should not be UTF-8
                                isUTF8 = false;
                                break;
                            }
                        }
                    }

                }
                

                if (isUTF8) {
                    code = "UTF-8"; // if detected as UTF-8 then change the "UNKNOWN" result
                }

            } catch (IOException e) {
                Log.d(LOG_TAG, "parseLrc : I/O error when reading .lrc file");
                throw new LrcFileIOException(
                        mContext.getResources().getString(R.string.lrc_file_io_error));
            }
        }

        // if cannot be detected as Unicode series
        // then try with ISO-8859-1 compatible encoding according to device's default locale setting
        // create a scanner object according to the file name
        Scanner s = null;
        try {
            if ("UNKNOWN".equals(code)) {
                s = new Scanner(new File(file), LyricsLocale.defLocale2CharSet());
            } else {
                s = new Scanner(new File(file), code); // UTF-8 or UTF-16
            }

        } catch (FileNotFoundException e) {
            throw new LrcFileNotFoundException(
                    mContext.getResources().getString(R.string.lrc_file_not_found));
        } catch (IllegalArgumentException e) { // when the defLocale2CharSet returns null
            Log.d(LOG_TAG, "parseLrc : unsupported textual encoding");
            throw new LrcFileUnsupportedEncodingException(
                    mContext.getResources().getString(R.string.lrc_file_invalid_encoding));
        }

        // (3) scanner success, clean up
        mLyricContent.clear();

        // parse and add all possible lyrics sentences & information
        // the unrecognized lines in the file are omitted.
        while (s.hasNextLine()) {
            String next = s.nextLine();  // get a valid line
            if (next.length() < 1) {
                continue; // the empty line is omitted
            }

            // try to parse this line as a lyric sentence
            Integer[] integerArray = analyzeLrc(next);
            int len = integerArray.length;

            if (0 == len) { // no timeTag, thus it is a line of information or unrecognized line
                Scanner scn = new Scanner(next);

                // should match the .lrc file's "infoTag" format
                String info = scn.findInLine("\\[(al|ar|by|re|ti|ve):.*\\]");
                if (null != info) { // if matches one of the info tags
                    String tag = info.substring(1, 3);
                    LyricSentence tmp = new LyricSentence(info.substring(4, info.length() - 1));
                    if (tag.equals("al") || tag.equals("AL")) {
                        mAlbumName = tmp;
                    } else if (tag.equals("ar") || tag.equals("AR")) {
                        mArtistName = tmp;
                    } else if (tag.equals("by") || tag.equals("BY")) {
                        mLyricAuthor = tmp;
                    } else if (tag.equals("re") || tag.equals("RE")) {
                        mLyricBuilder = tmp;
                    } else if (tag.equals("ti") || tag.equals("TI")) {
                        mTrackTitle = tmp;
                    } else if (tag.equals("ve") | tag.equals("VE")) {
                        mLyricBuilderVer = tmp;
                    }
                } else {  // it's possible to be "offset"
                    String offset = scn.findInLine("\\[offset:[\\+|-]{1}\\d+\\]");
                    if (null != offset) { // if matches offset tag
                        mAdjustMilliSec
                                = Integer.parseInt(offset.substring(9, offset.length() - 1))
                                        * ( (offset.charAt(8) == '+') ? 1 : -1 );
                    }
                }
            } else {  // has time tags, then it's a line of lyrics sentence
                for (int k = 0; k < len; k++) {
                    int time = integerArray[k].intValue();
                    LyricSentence lrcSentence
                            = new LyricSentence(resolveLrc(next), time + mAdjustMilliSec);
                    mLyricContent.add(lrcSentence);
                }
            }
        }

        // sort the lyrics sentences as the sequence of time tags
        Collections.sort(mLyricContent);
        
        if (mLyricContent.isEmpty()) {
            throw new LrcFileInvalidFormatException(
                    mContext.getResources().getString(R.string.lrc_file_invalid_format));
        }
    }

    /*
     * analyze and retrieve the time tags from one line of the lyrics file; It's possible that
     * there are more than one time tags for each lyrics sentence.
     *
     * [in]origin: the original string of one line of the lyrics file
     *
     * return value: an Integer array, which will contain all the millisecond value of the timeTag.
     *               if no timeTag is found in this line, the length of this array would be ZERO.
     */
    private Integer[] analyzeLrc(String origin) {
        String str = new String(origin);

        Integer[] result = new Integer[0];  // first we assume that there's no timeTag

        List<Integer> list = new ArrayList<Integer>();
        list.clear();

        Scanner scn = new Scanner(str);
        String tmp = null;
        int tmpTimeValue = 0;   // millisecond value

        // scan by regular expression, and calculate each millisecond value
        // there could be more than one timeTag for each lyric sentence.
        tmp = scn.findInLine("\\[\\d\\d:[0-5]\\d\\.\\d\\d\\]|\\[\\d\\d:[0-5]\\d\\]"); // [mm:ss.xx] or [mm:ss]
        while (null != tmp) {
            if (10 == tmp.length()) { // [mm:ss.xx]
                tmpTimeValue = Integer.parseInt(tmp.substring(1,3)) * 60000    // minutes
                               + Integer.parseInt(tmp.substring(4,6)) * 1000   // second
                               + Integer.parseInt(tmp.substring(7,9)) * 10;    // 1/100 second
            } else if (7 == tmp.length()) { // [mm:ss]
                tmpTimeValue = Integer.parseInt(tmp.substring(1,3)) * 60000    // minutes
                               + Integer.parseInt(tmp.substring(4,6)) * 1000;  // second
            }

            list.add(Integer.valueOf(tmpTimeValue));
            tmp = scn.findInLine("\\[\\d\\d:[0-5]\\d\\.\\d\\d\\]|\\[\\d\\d:[0-5]\\d\\]"); // next one
        }

        // convert the Integer list to actual Integer[] array
        // an suitable array that fits the length will be created.
        return list.toArray(result);
    }

    /*
     * analyze one line of the lyrics file and retrieve the lyric sentence
     *
     * [in]origin: the original string of one line of the lyrics file
     *
     * return value: a string, which is the pure part of one lyric sentence.
     */
    private String resolveLrc(String origin) {
        String str = new String(origin);

        // remove all the valid time tags ([mm:ss.xx]) with an empty string
        str = str.replaceAll("\\[\\d\\d:[0-5]\\d\\.\\d\\d\\]|\\[\\d\\d:[0-5]\\d\\]", "");
        // remove all extended time tags (<mm:ss.xx> format)
        str = str.replaceAll("\\<\\d\\d:[0-5]\\d\\.\\d\\d\\>|\\<\\d\\d:[0-5]\\d\\>", "");

        str.trim();
        return str;
    }

    /*
     * according to the duration of track being played, find the index of current sentence
     *
     * [in]low: the lower range of sentence index
     * [in]high: the higher range of sentence index
     * [in]millisecond: the current duration
     *
     * return value: the index representing the current lyrics sentence. -1 if it fails to search.
     */
    private int searchIndex(int low, int high, int millisecond) {

        int mid;

        int cnt = mLyricContent.size() - 1;
        if (low < 0 || low > cnt || high < 0 || high > cnt || high < low) {
            throw new IllegalArgumentException();
        }

        // should be the first sentence
        if (low == 0 && millisecond <= mLyricContent.elementAt(low).getTime() ) {
            return low;
        }

        // should be the last sentence
        if ((mLyricContent.size() - 1) == high
                    && mLyricContent.elementAt(high).getTime() <= millisecond) {
            return high;
        }

        while (low < high) {
            mid = (high + low) / 2;
            if (mLyricContent.elementAt(mid).getTime() <= millisecond) {
                if (millisecond < mLyricContent.elementAt(mid + 1).getTime()) {
                    return mid;
                } else {
                    low = mid + 1;
                    continue;
                }
            } else {
                if (mLyricContent.elementAt(mid - 1).getTime() <= millisecond) {
                    return mid - 1;
                } else {
                    high = mid - 1;
                    continue;
                }
            }
        }

        return INVALID_INDEX; // -1
    }
}

/*
 * =================================================================================================
 */

/*
 * a single sentence of the real lyrics
 */
class LyricSentence implements Comparable<LyricSentence> {

    private final String text;   // the actual lyrics sentence
    private final int milliSec;  // in millisecond, the time interval from the beginning of track.
                                 // by default 0 milliseconds.

    /*
     * constructor
     */
    public LyricSentence (String str) {
        this(str, 0);
    }

    public LyricSentence (String str, int min, int sec, int millisecond) {
        this(str, (min * 60 + sec) * 1000 + millisecond);
    }

    public LyricSentence (String str, int millisecond) {
        text = new String(str);
        milliSec = millisecond;
    }

    /*
     * to implement Comparable interface.
     */
    public int compareTo(LyricSentence o) {
        if (this.milliSec < o.milliSec) {
            return -1; // earlier than the latter one
        } else if (this.milliSec > o.milliSec) {
            return 1; // the latter one is earlier
        } else {
            return 0; // equals
        }
    }

    public String getSentence() {
        return text;
    }

    public int getTime () {
        return milliSec;
    }
}

/*
 * =================================================================================================
 */

class LyricsLocale {
    // add the locale value & default textual encoding name here if want to support more languages.
    private static final Locale zh_CN = new Locale("zh", "CN");
    private static final Locale zh_TW = new Locale("zh", "TW");
    private static final Locale en_US = new Locale("en", "US");

    private static final String GBK = "GBK";
    private static final String BIG5 = "Big5";
    private static final String ISO_8859_1 = "ISO-8859-1";

    // we use gbk encoding by default under English system setting
    // for CR ALPS00051690
    private static final String DEF_ENCODING = "GBK";

    /*
     * returns the character set to be used corresponding to device's default locale setting
     */
    public static String defLocale2CharSet() {
        Locale loc = Locale.getDefault();

        if (loc.equals(zh_CN)) {
            return GBK;
        } else if (loc.equals(zh_TW)) {
            return BIG5;
        } else if (loc.getLanguage().equals(en_US.getLanguage())) {
            return DEF_ENCODING; // default encoding for English language setting, CR ALPS00051690
        }

        return null; // will cause IllegalArgumentException in LyricsBody -> parseLrc function
    }
}
