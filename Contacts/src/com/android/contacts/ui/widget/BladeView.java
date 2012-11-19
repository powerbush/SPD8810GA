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

package com.android.contacts.ui.widget;

import android.graphics.Color;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.PopupWindow;
import android.view.MotionEvent;
import android.graphics.Paint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.RemotableViewMethod;
import android.view.View;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews.RemoteView;


public class BladeView extends View {
    private int mMinCharHeight=5;
    private int mCharHeight=5;
    private OnItemClickListener mOnItemClickListener;
    private PopupWindow mPopupWindow;
    private TextView mPopupText;
    
    public BladeView(Context context) {
        super(context);
    }

    public BladeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BladeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	int widthSize=10+getPaddingLeft()+getPaddingRight();
	int heightSize=MeasureSpec.getSize(heightMeasureSpec);
	int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
	int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);

	int charHeigtht=heightSize/27;	
	if (charHeigtht<mMinCharHeight) {
	    heightSize=mMinCharHeight*27;
	    mCharHeight=mMinCharHeight;
	} else {
	    mCharHeight=charHeigtht;
	}
        setMeasuredDimension(widthSize, heightSize);
    }

    @Override 
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
	Paint paint=new Paint();
	paint.setColor(Color.GRAY);
	paint.setTextSize(mCharHeight-2);
	for (int i=0;i<27;++i) {
	    char currentChar;
	    if (i==0) {
		currentChar='#';
	    } else {
		currentChar=(char)('A'+i-1);
	    }
	    canvas.drawText(Character.toString(currentChar),getPaddingLeft(),(i+1)*mCharHeight,paint);
	}
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
	getParent().requestDisallowInterceptTouchEvent(true);
	final int action = event.getActionMasked();
	Log.d("dengjing", "dddddddddddddddddddd action = " + action);
	if (action==MotionEvent.ACTION_DOWN
	    ||action==MotionEvent.ACTION_MOVE) {
	Log.d("dengjing", "eeeeeeeeeeeeeeeeeeeeeeeeeeee");
	    int item=(int)(event.getY()/mCharHeight);
	    if (item<0 || item>=27) {
		return true;
	    }
	    showPopup(item);
	    performItemClicked(item);
	} else if (action==MotionEvent.ACTION_UP) {
	    dismissPopup();
	}
	return true;
	// return super.onTouchEvent(event);
    }

    private void showPopup(int item) {
	if (mPopupWindow==null) {
	    mPopupText=new TextView(getContext());
	    mPopupText.setBackgroundColor(Color.GRAY);
	    mPopupText.setTextColor(Color.CYAN);
	    mPopupText.setTextSize(50);
	    mPopupText.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
	    mPopupWindow=new PopupWindow(mPopupText,100,100);
	}

	String text="";
	if (item==0) {
	    text="#";
	} else {
	    text=Character.toString((char)('A'+item-1));
	}
	mPopupText.setText(text);
	if (mPopupWindow.isShowing()) {
	    mPopupWindow.update();
	} else {
	    mPopupWindow.showAtLocation(getRootView(),Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL,0,0);	    
	}
    }

    private void dismissPopup() {
	if (mPopupWindow!=null) {
	    mPopupWindow.dismiss();
	} 
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
	mOnItemClickListener=listener;
    }
    
    private void performItemClicked(int item) {
	if (mOnItemClickListener!=null) {
	    mOnItemClickListener.onItemClick(item);	    
	} 
    }

    public interface OnItemClickListener {
	void onItemClick(int item);
    }
}
