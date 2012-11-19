package com.android.mms.util;

import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class ZoomViewUtil {
    private static final String TAG = "ZoomUtil";
    private static final boolean DEBUG = true;
    private static final float MAX_TEXT_SIZE = 70.0f;
    private static final float MIN_TEXT_SIZE = 8.0f;
    private static final float STAND_MOVE = 4.0f;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private static float mScale = 0.75f;

    private int mode = NONE;
    private float mDist;
    private float mTextSize;

    private TextResizeable mTR;

    public ZoomViewUtil(TextResizeable tr) {
        mTR = tr;
    }

    public void setView(View view, float size) {
        mTextSize = size;
        Log.d(TAG,"setView() init size:"+size);
        addViewTouchListener(view);
    }

    protected void addViewTouchListener(View view){
        view.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (DEBUG) {
                    Log.i(TAG, "onTouch event:" + event);
                }
                switch (event.getAction()  & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    if (!v.isLongClickable()) {
                        v.setLongClickable(true);
                    }
                    mode = DRAG;
                    break;
                case MotionEvent.ACTION_UP:
                    mode = NONE;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (v.isLongClickable()) {
                        v.setLongClickable(false);
                    }
                    mDist = calculateDist(event);
                    if(mDist >= STAND_MOVE){
                        mode = ZOOM;
                    } else {
                        mode = DRAG;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(mode == ZOOM){
                        float newDist = calculateDist(event);
                        if( newDist > mDist ) {
                            zoomOut();
                        } else if( newDist < mDist ) {
                            zoomIn();
                        }
                        return true;
                    }
                   break;
                default:
                    return false;
                }
                return false;
            }
        });
    }

    protected void zoomIn(){
        mTextSize -= mScale;
        if (mTextSize < MIN_TEXT_SIZE){
            mTextSize = MIN_TEXT_SIZE;
        }
        mTR.onTextResize(mTextSize);
        if(DEBUG) Log.i(TAG, "zoomin:"+mTextSize);
    }

    protected void zoomOut() {
        mTextSize += mScale;
        if (mTextSize > MAX_TEXT_SIZE) {
            mTextSize = MAX_TEXT_SIZE;
        }
        mTR.onTextResize(mTextSize);
        if (DEBUG) {
            Log.i(TAG, "zoomout:" + mTextSize);
        }
    }

    protected float calculateDist(MotionEvent event){
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return FloatMath.sqrt(x * x + y * y);
    }

    public interface TextResizeable {
        void onTextResize(float size);
    }
}
