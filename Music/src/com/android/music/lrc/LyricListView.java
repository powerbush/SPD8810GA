
package com.android.music.lrc;

import com.android.music.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemLongClickListener;

import com.android.music.lrc.widget.TextView;

import java.util.List;

/**
 * LyricListView
 *
 * @author lisc
 */
public class LyricListView extends ListView implements OnItemLongClickListener {

    private static final String TAG = "LyricListView";

    private int colorNormal;

    private int colorHighlight;

    private String path;

    private LRC lrc;

    private int lyricListItem;

    private int listHeight;

    private int itemHeight;

    private int marginHeight;

    private boolean isScrolling;

    private int delay;

    private int offset;

    private int curLrc;

    private int lyricCount;

    private int mFieldId;

    private boolean initialized;

    private LyricsAdapter mAdapter;

    private boolean isLyricModified;

    private LRC.PositionProvider posProvider;

    /**
     * LyricListView
     *
     * @param context
     */
    public LyricListView(Context context) {
        this(context, null);
    }

    /**
     * LyricListView
     *
     * @param context
     * @param attrs
     */
    public LyricListView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.listViewStyle);
    }

    /**
     * LyricListView
     *
     * @param context
     * @param attrs
     * @param defStyle
     */
    public LyricListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.ViewGroup);

        listHeight = a.getDimensionPixelSize(com.android.internal.R.styleable.View_minHeight, 0);
        marginHeight = listHeight / 2;

        a.recycle();

        init();
    }

    private void init() {
        offset = LyricConstants.DEFAULT_OFFSET;
        delay = LyricConstants.DEFAULT_DELAY;
        isScrolling = false;
        curLrc = -1;
        lyricCount = 0;
        mFieldId = R.id.content;
        initialized = false;
        isLyricModified = false;

        colorNormal = getResources().getColor(R.color.lyric_color_normal);
        colorHighlight = getResources().getColor(R.color.lyric_color_highlight);
//        setOnItemLongClickListener(this);
        this.setSelector(android.R.color.transparent);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!initialized && lyricCount > 0) {
            initialized = true;
            // modified by housz ======begin===bug-1030:resolve
            // NullPointerException========
            if (getChildAt(1) != null) {
                itemHeight = getChildAt(1).getHeight();
            }
            // modified by housz ======end===bug-1030:resolve
            // NullPointerException========
        }
    }

    private boolean checkRequirments() {
        if (lrc == null) {
            // throw new
            // IllegalStateException("LRC object is null, you should invoke setLrc() first.");
            Log.i(TAG, "LRC object is null, you should invoke setLrc() first.");
            return false;
        }
        if (posProvider == null) {
            // throw new
            // IllegalStateException("PositionProvider object is null, you should invoke setPositionProvider() first.");
            Log.i(TAG,
                    "PositionProvider object is null, you should invoke setPositionProvider() first.");
            return false;
        }
        return true;
    }

    /**
     * reset
     */
    public void reset() {
        curLrc = -1;
        initialized = false;
    }

    /**
     * start
     */
    public void start() {
        resume();
    }

    /**
     * resume
     */
    public void resume() {
        if (checkRequirments()) {
            Log.d(TAG, "isScrolling="+isScrolling);
            if (!isScrolling) {
                isScrolling = true;
                reset();
                lyricHandler.sendEmptyMessage(LyricConstants.LYRIC_SCROLL);
            }
        }
    }

    /**
     * pause
     */
    public void pause() {
        if (isScrolling) {
            isScrolling = false;
            lyricHandler.removeMessages(LyricConstants.LYRIC_SCROLL);
        }
    }

    /**
     * stop
     */
    public void stop() {
        if (isScrolling) {
            isScrolling = false;
            lyricHandler.removeMessages(LyricConstants.LYRIC_SCROLL);
            removeAllViewsInLayout();
            lrc = null;
        }
    }

    private int computeOffset() {
        // ++curLrc;
        curLrc = 0;
        long pos = posProvider.getPosition();
        List<LRC.Offset> ofs = lrc.getOffsets();
        while (curLrc < lyricCount && pos > ofs.get(curLrc).time) {
            curLrc++;
        }

        int off = 0;
        long start = 0;
        long end = 0;
        if (curLrc >= lyricCount) {
            off = lyricCount * itemHeight;
        } else {
            start = ofs.get(curLrc).time;
            if (pos < start && curLrc > 0) {
                start = lrc.getOffsets().get(--curLrc).time;
            } else if (curLrc <= 0) {
                return 0;
            }
            end = curLrc == lyricCount - 1 ? posProvider.getDuration() : lrc.getOffsets().get(
                    curLrc + 1).time;
            off = (int) (1.0 * itemHeight / (end - start) * (pos - start));
            off += curLrc * itemHeight;
        }
        int result = offset - off;
        offset = off;
        return result;
    }

    private Handler lyricHandler = new Handler() {
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch (what) {
                case LyricConstants.LYRIC_SCROLL:
                    if (isScrolling) {
                        int off = computeOffset();
                        offsetChildrenTopAndBottom(off);
                        invalidateViews();
                        sendEmptyMessageDelayed(LyricConstants.LYRIC_SCROLL, delay);
                    }
                    break;
                case LyricConstants.LYRIC_AJUST:
                    int off = computeOffset();
                    offsetChildrenTopAndBottom(off);
                    invalidateViews();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     *
     * @return boolean
     */
    public boolean isLyricModified() {
        return isLyricModified;
    }

    /**
     *
     * @return String
     */
    public String getLrcPath() {
        return path;
    }

    /**
     * getLrc
     * @return LRC
     */
    public LRC getLrc() {
        return lrc;
    }

    /**
     * setLrc
     */
    public void setLrc(String path, LRC lrc) {
        setLrc(path, lrc, R.layout.lyric_list_item);
    }

    /**
     * setLrc
     */
    public void setLrc(String path, LRC lrc, int item) {
        if (lrc == null){
            throw new IllegalArgumentException("LRC object can not be null.");
        }
        stop();
        this.path = path;
        this.lrc = lrc;
        this.lyricListItem = item;
        this.lyricCount = lrc.getOffsets().size();
        populateViews();
    }

    private void populateViews() {
        String[] lyrics = lrc.listLyrics();
        int len = lyrics.length;
        String[] newLyrics = new String[len + 2];
        newLyrics[0] = newLyrics[len + 1] = "";
        System.arraycopy(lyrics, 0, newLyrics, 1, len);
        if (mAdapter == null) {
            mAdapter = new LyricsAdapter(getContext(), lyricListItem, newLyrics);
            setAdapter(mAdapter);
        } else {
            mAdapter.setLyrics(newLyrics);
        }
        /*
         * The content of the adapter has changed but ListView did not receive a notification.
         * so we call notifyDataSetChanged to send the notification.
         */
        mAdapter.notifyDataSetChanged();
    }

    /**
     * ajustLyrics
     */
    public void ajustLyrics(int row, boolean after, boolean advance, int time) {
        isLyricModified = true;
        lrc.ajust(row, after, advance, time);
        populateViews();
        lyricHandler.sendEmptyMessage(LyricConstants.LYRIC_AJUST);
    }

    /**
     * setPositionProvider
     */
    public void setPositionProvider(LRC.PositionProvider provider) {
        Log.d(TAG, "setPositionProvider");
        posProvider = provider;
    }

    /**
     * onItemLongClick
     * @return boolean
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, final int pos, long id) {
//        new AlertDialog.Builder(getContext()).setTitle(R.string.lyrics_edit_title)
//                .setItems(R.array.lyrics_edit_item, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        switch (which) {
//                            case 0: // current lyric advance 0.5 second
//                                ajustLyrics(pos - 1, false, true, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 1: // current lyric delayed 0.5 second
//                                ajustLyrics(pos - 1, false, false, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 2: // the after lyrics advance 0.5 second
//                                ajustLyrics(pos - 1, true, true, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 3: // the after lyrics delayed 0.5 second
//                                ajustLyrics(pos - 1, true, false, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 4: // all lyrics advance 0.5 second
//                                ajustLyrics(-1, false, true, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 5: // all lyrics delayed 0.5 second
//                                ajustLyrics(-1, false, false, LyricConstants.DEFAULT_AJUST_TIME);
//                                break;
//                            case 6:
//                                break; // do nothing
//                            default:
//                                if (isLyricModified) {
//                                    boolean ret = LRCParser.saveToFile(getLrcPath(), getLrc());
////                                    Toast.makeText(
////                                            getContext(),
////                                            ret ? R.string.lyrics_update_success
////                                                    : R.string.lyrics_update_fail,
////                                            Toast.LENGTH_SHORT).show();
//                                    isLyricModified = !ret;
//                                } else {
////                                    Toast.makeText(getContext(), R.string.lyrics_no_update,
////                                            Toast.LENGTH_SHORT).show();
//                                }
//                        }
//                    }
//                }).show();
///*
// * the call back function has dealt with the long click event ,so it returns true.  
// */
        return true;
    }

    /**
     * getOffset
     * @return int
     */
    public int getOffset() {
        return offset;
    }

    /**
     * setOffset
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * getDelay
     * @return int
     */
    public int getDelay() {
        return delay;
    }

    /**
     * setDelay
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    private class LyricsAdapter extends BaseAdapter {

        private Context cxt;

        private int resId;

        private String[] lyrics;

        private LayoutInflater inflater;

        LyricsAdapter(Context c, int r, String[] l) {
            cxt = c;
            resId = r;
            lyrics = l;
            inflater = (LayoutInflater) cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setLyrics(String[] ls) {
            lyrics = ls;
        }

        public int getCount() {
            return lyrics.length;
        }

        public Object getItem(int position) {
            return lyrics[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(TAG, "===============================");
            View view;
            TextView text;

            if (convertView == null) {
                view = inflater.inflate(resId, parent, false);
            } else {
                view = convertView;
            }

            view.setMinimumHeight(position == 0 ? marginHeight
                    : (position == lyrics.length - 1) ? listHeight : 0);
            view.requestLayout();

            try {
                if (mFieldId == 0) {
                    // If no custom field is assigned, assume the whole resource
                    // is a TextView
                    text = (TextView) view;
                } else {
                    // Otherwise, find the TextView field within the layout
                    text = (TextView) view.findViewById(mFieldId);
                }
            } catch (ClassCastException e) {
                Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
                throw new IllegalStateException(
                        "ArrayAdapter requires the resource ID to be a TextView", e);
            }

            String item = (String) getItem(position);
            text.setText(item);
            text.setTextColorQuiet((curLrc + 1) == position ? colorHighlight : colorNormal);

            return view;
        }
    }

}
