
package com.android.phone;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

/**
 * VideoPhoneTwelveKeyDialerView is the view logic that the DTMFDialer uses.
 * This is really a thin wrapper around Linear Layout that intercepts
 * some user interactions to provide the correct UI behaviour for the
 * dialer.
 */
class AdjustMenuView extends LinearLayout implements SeekBar.OnSeekBarChangeListener{

    private static final String LOG_TAG = "PHONE/AdjustMenuView";
    private static final boolean DBG = false;
    private float progress = 0;
    private float max = 10;
    private float min = 0;
    private int rank = 10;
    private String title = null;
    private View mValidRect = null;
    private TextView mIndicationView = null;
    private SeekBar mSeekBar = null;
    private EventHandler mEventHandler;

    private static final int CLOSE_DELAY = 3000;
    private static final int ADJUSTMENU_CLOSE = 1;

    public interface OnSeekBarChangeListener{
    	void onProgressChanged(AdjustMenuView adjustMenu, float progress, int rank, boolean fromUser);
    }
    OnSeekBarChangeListener mOnSeekBarChangeListener;
    
    public AdjustMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.AdjustMenuView, 0, 0);
        title = a.getString(R.styleable.AdjustMenuView_title);
        progress = a.getFloat(R.styleable.AdjustMenuView_progress, progress);
        max = a.getFloat(R.styleable.AdjustMenuView_max, max);
        min = a.getFloat(R.styleable.AdjustMenuView_min, min);
        rank = a.getInt(R.styleable.AdjustMenuView_rank, rank);
        log("title: " + title + ", progress: " + progress + ", max: " + max);
        
        LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout ln = (LinearLayout) layoutInflater.inflate(R.xml.adjustmenuview, this);
        mValidRect =  ln.findViewById(R.id.valid_rect);
        mIndicationView = (TextView) ln.findViewById(R.id.seekbarindication);
        mSeekBar =  (SeekBar) ln.findViewById(R.id.ratingbar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(rank);
        mSeekBar.setProgress(out2in(progress));
        mSeekBar.setOnTouchListener(new OnTouchListener(){
        	public boolean onTouch(View v, MotionEvent event) {
        		if (event.getAction() == MotionEvent.ACTION_DOWN){
        			setDelayClose();
        		}
				return false;
        	}
        });
        ln.setOnTouchListener(new OnTouchListener(){
        	public boolean onTouch(View v, MotionEvent event) {
        		if (event.getAction() == MotionEvent.ACTION_DOWN){
        			if (isOutOfBounds(event)){
        				setVisibility(View.GONE);
        			} else{
        				setDelayClose();
        			}
        		}
				return false;
        	}
        });
        
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(looper);
        } else {
            mEventHandler = null;
        }
    }
 
    private void log(String msg) {
	if (DBG) {
        	Log.d(LOG_TAG, msg);
	}
    }
    
    private float in2out(int inValue){
    	float outValue = 0;
    	
    	if (inValue >= rank){
    		outValue = max;
    	} else if (inValue <= 0){
    		outValue = min;
    	} else {
    		outValue = inValue*(max-min)/rank + min;
    	}

		log("in2out("+inValue+")="+outValue);
    	return outValue;
    }
    
    private int out2in(float outValue){
    	int inValue = 0;
    	
    	if (outValue >= max){
    		inValue = rank;
    	} else if (outValue <= min){
    		inValue = 0;    		
    	} else {
    		inValue = (int) ((outValue-min)*rank/(max-min));
    	}

		log("out2in("+outValue+")="+inValue);
    	return inValue;
    }
    
    public void setMax(float value){
    	max = value;
        mSeekBar.setProgress(out2in(progress));
    }
    
    public void setMin(float value){
    	min = value;
        mSeekBar.setProgress(out2in(progress));
    }

    public void setProgress(float value){
    	progress = value;
    	mSeekBar.setProgress(out2in(progress));
    }
    
    public void setIndication(String str){
    	mIndicationView.setText(title+ ": " + str);
    }
    
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        mOnSeekBarChangeListener = l;
    }   
    
	public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
		//progress = in2out(value);
        if (mOnSeekBarChangeListener != null) {
            mOnSeekBarChangeListener.onProgressChanged(this, in2out(value), value, fromUser);
        }		
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}
	
	private void setDelayClose(){
		mEventHandler.removeMessages(ADJUSTMENU_CLOSE);
        mEventHandler.sendEmptyMessageDelayed(ADJUSTMENU_CLOSE, CLOSE_DELAY);		
	}
	
	private boolean isOutOfBounds(MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        
        if ((y <= mValidRect.getBottom())
        		&& (y >= mValidRect.getTop())
        		&& (x <= mValidRect.getRight())
        		&& (x >= mValidRect.getLeft())){
        	return false;
        }
        return true;
    }
	
	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		// TODO Auto-generated method stub
		if ((this == changedView) && (visibility == View.VISIBLE)){
			setDelayClose();
		}
		super.onVisibilityChanged(changedView, visibility);
	}

	private class EventHandler extends Handler{
		public EventHandler(Looper looper) {
            super(looper);
        }
		@Override
        public void handleMessage(Message msg) {
			switch (msg.what){
			case ADJUSTMENU_CLOSE:
				setVisibility(View.GONE);
				break;
			}
		}
	}
}
