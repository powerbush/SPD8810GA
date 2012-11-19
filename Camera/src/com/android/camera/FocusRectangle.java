package com.android.camera;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.Camera;
import com.android.camera.R;

public class FocusRectangle extends View {
    @SuppressWarnings("unused")
    private static final String TAG = "FocusRectangle";
    private static final String ONE_FINGER_TOUCHED = "one_finger_touch";
    private static final String TWO_FINGER_TOUCHED = "two_finger_touch";
    private static final String THREE_FINGER_TOUCHED = "three_finger_touch";

    private static final String MULTI_TOUCH_MODE = android.hardware.Camera.Parameters.FOCUS_MODE_AUTO_MULTI;
    private static final String AUTO_MODE = android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
    private static final String MACRO_MODE = android.hardware.Camera.Parameters.FOCUS_MODE_MACRO;
    private static final int UPDATE_VIEW_DELAYED = 1;
    private static final int CLEAR_FOCUS_STATE = 2;
    private String touchCount = ONE_FINGER_TOUCHED;
    private ArrayList<Pointer> mPointers = new ArrayList<FocusRectangle.Pointer>(3);
    private String mMode = AUTO_MODE;
    private boolean resetToDefault = false;
    private Camera mCameraSwitch = null;
    private boolean isFocus = false;
    private boolean multiFocusModeSupported;

    private Handler handler = new UpdateHandler();

    public FocusRectangle(Context context, AttributeSet attrs) {
        super(context, attrs);
        Pointer pointer = new Pointer();
        pointer.updatePointBitmap(false);
        mPointers.add(pointer);
        resetToDefault = true;
    }

    public void setCamera(Camera camera){
        mCameraSwitch = camera;
    }

    public String getMode(){
        return mMode;
    }

    public void setMode(String mode) {
        if(mMode.equals(mode)){
            return;
        }

        mMode = mode;
        if (mMode.equals(MULTI_TOUCH_MODE)) {
            reset();
            Pointer pointer1 = mPointers.get(0);
            if(mPointers.size() == 1){
                Pointer pointer = new Pointer();
                pointer.updatePointBitmap(false);
                pointer.x = pointer1.x;
                pointer.y = pointer1.y;
                mPointers.add(pointer);
            }

            if(mPointers.size() == 2){
                Pointer pointer = new Pointer();
                pointer.updatePointBitmap(false);
                pointer.x = pointer1.x;
                pointer.y = pointer1.y;
                mPointers.add(pointer);
            }
            invalidate();
            return;
        }

        while(mPointers.size() > 1){
            Pointer pointer = mPointers.get(mPointers.size() - 1);
            pointer.clear();
            mPointers.remove(pointer);
            pointer = null;
        }

        reset();
        invalidate();
    }

    public ArrayList<Pointer> getPointers(){
        int count = 0;
        if(ONE_FINGER_TOUCHED.equals(touchCount) && mPointers.size() > 0)
            count = 1;
        else if (TWO_FINGER_TOUCHED.equals(touchCount)){
            count = mPointers.size();
            count = count > 2 ? 2 : count;
        } else if(THREE_FINGER_TOUCHED.equals(touchCount)){
            count = mPointers.size();
            count = count > 3 ? 3 : count;
        }

        if(!MULTI_TOUCH_MODE.equals(mMode))
            count = count > 1 ? 1 : count;

        ArrayList<Pointer> pointers = new ArrayList<Pointer>();
        for(int i = 0; i < count; i++){
            Pointer p = new Pointer();
            Pointer p1 = mPointers.get(i);
            p.x = p1.x;
            p.y = p1.y;
            pointers.add(p);
        }
        return pointers;
    }

    private void setDrawable(int resid) {
        setBackgroundDrawable(getResources().getDrawable(resid));
    }

    public void showStart() {
        if(!multiFocusModeSupported){
            setDrawable(R.drawable.focus_focusing);
            return;
        }
        for(int i = 0; i < mPointers.size(); ++i){
            Pointer pointer = mPointers.get(i);
            pointer.showStart();
        }
        invalidate();
    }

    public void showSuccess() {
        if(!multiFocusModeSupported){
            setDrawable(R.drawable.focus_focused);
            return;
        }

        for(int i = 0; i < mPointers.size(); ++i){
            Pointer pointer = mPointers.get(i);
            pointer.showSuccess();
        }
        invalidate();
    }

    public void showFail() {
        if(!multiFocusModeSupported){
            setDrawable(R.drawable.focus_focus_failed);
            return;
        }

        for(int i = 0; i < mPointers.size(); ++i){
            Pointer pointer = mPointers.get(i);
            pointer.showFail();
        }
        invalidate();
    }

    public void clear() {
        if(!multiFocusModeSupported){
            setBackgroundDrawable(null);
            return;
        }
        invalidate();
    }

    private boolean isEqual(float x1, float y1, float x2, float y2) {
        boolean flag = false;
        double distance = (x1 - y1) * (x1 - y1) + (x2 - y2) * (x2 - y2);
        if (Math.sqrt(distance) == 0) {
            flag = true;
        }
        return flag;
    }

    public void reset() {
        resetToDefault = true;
        Pointer pointer1 = mPointers.get(0);
        pointer1.x = getWidth() / 2;
        pointer1.y = getHeight() / 2;
        if (!mMode.equals(MULTI_TOUCH_MODE)) {
            return;
        }

        for(int i = 1; i < mPointers.size(); ++ i){
            final Pointer pointer = mPointers.get(i);
            pointer.x = getWidth() / 2;
            pointer.y = getHeight() / 2;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(!multiFocusModeSupported){
            super.onDraw(canvas);
            return;
        }

        Pointer pointer1 = mPointers.get(0);
        float halfWidth = pointer1.getDistanceFromLeftToCenter();
        float halfHeight = pointer1.getDistanceFromTopToCenter();

        if (resetToDefault) {
            // add for bug pointers x = 0 && y = 0 at init
            //fixed 15641
            int width = getWidth();
            int height = getHeight();
            pointer1.x = width / 2;
            pointer1.y = height / 2;
            int screenWidth = mCameraSwitch.getWindowManager().getDefaultDisplay().getWidth();
            int screenHeight = mCameraSwitch.getWindowManager().getDefaultDisplay().getHeight();
            if (width > screenWidth) {
                width = screenWidth;
            }
            if (height > screenHeight) {
                height = screenHeight;
            }
            Bitmap bitmap = pointer1.getShowBitmap();
            if(bitmap != null && !bitmap.isRecycled()){
                canvas.drawBitmap(bitmap, width / 2 - halfWidth, height / 2 - halfHeight, pointer1.getPaint());
            }
            return;
        }

        Bitmap bitmap = pointer1.getShowBitmap();
        if(bitmap != null && !bitmap.isRecycled()){
            canvas.drawBitmap(pointer1.getShowBitmap(), pointer1.x - halfWidth, pointer1.y - halfHeight, pointer1.getPaint());
        }

        if (!mMode.equals(MULTI_TOUCH_MODE))
            return;

        if (!touchCount.equals(ONE_FINGER_TOUCHED) && mPointers.size() > 1) {
            Pointer pointer2 = mPointers.get(1);
            Bitmap showBm = pointer2.getShowBitmap();
            if(showBm != null){
                float halfWidth2 = pointer2.getDistanceFromLeftToCenter();
                float halfHeight2 = pointer2.getDistanceFromTopToCenter();
                canvas.drawBitmap(showBm, pointer2.x - halfWidth2, pointer2.y - halfHeight2, pointer2.getPaint());
            }
        }

        if (touchCount.equals(THREE_FINGER_TOUCHED) && mPointers.size() > 2) {
            Pointer pointer3 = mPointers.get(2);
            Bitmap showBm = pointer3.getShowBitmap();
            if(showBm != null){
                float halfWidth3 = pointer3.getDistanceFromLeftToCenter();
                float halfHeight3 = pointer3.getDistanceFromTopToCenter();
                canvas.drawBitmap(showBm, pointer3.x - halfWidth3, pointer3.y - halfHeight3, pointer3.getPaint());
            }
        }
    }

    boolean isTouch = false;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //fixed bug 17024
        /*if(!multiFocusModeSupported){
            isTouch = false;
            return isTouch;
        }*/

        if(mCameraSwitch.isFocusing())
            return isTouch;

        resetToDefault = false;
        if(isFocus)
            return isTouch;

        if (!mMode.equals(MULTI_TOUCH_MODE)) {
            onSingleTouchEvent(event);
            invalidate();
            return isTouch;
        }

        onMultiTouchEvent(event);
        invalidate();
        return isTouch;
    }

    @SuppressWarnings("deprecation")
    private void onMultiTouchEvent(MotionEvent event) {
        // fixed bug 19284 start
//        int action = event.getAction();
//        float dx1 = event.getX(0);
//        float dy1 = event.getY(0);
//
//        float dx2 = event.getX(1);
//        float dy2 = event.getY(1);
//
//        float dx3 = event.getX(2);
//        float dy3 = event.getY(2);
//
//        Pointer pointer1 = mPointers.get(0);
//        Pointer pointer2 = mPointers.get(1);
//        Pointer pointer3 = mPointers.get(2);
        int action = event.getAction();
        float dx1, dy1, dx2, dy2, dx3, dy3;
        Pointer pointer1, pointer2, pointer3;
        try {
            dx1 = event.getX(0);
            dy1 = event.getY(0);
            pointer1 = mPointers.get(0);

            dx2 = event.getX(1);
            dy2 = event.getY(1);
            pointer2 = mPointers.get(1);

            dx3 = event.getX(2);
            dy3 = event.getY(2);
            pointer3 = mPointers.get(2);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.d(TAG,
                String.format("onMultiTouchEvent() failed, action = %d", action), e);
            return;
        }
        // fixed bug 19284 end
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            Log.v(TAG, "onMultiTouchEvent: ACTION_DOWN");
            touchCount = ONE_FINGER_TOUCHED;
            multiTouchDown(event);
            break;
        case MotionEvent.ACTION_UP:
            Log.v(TAG, "onMultiTouchEvent: ACTION_UP");
            multiTouchUp(event);
            break;
        case MotionEvent.ACTION_POINTER_1_DOWN:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_1_DOWN");

            setPointer(dx1, dy1, pointer1);
            pointer1.touched = true;
            pointer1.updatePointBitmap(true);
            break;
        case MotionEvent.ACTION_POINTER_1_UP:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_1_UP");
            pointer1.touched = false;
            pointer1.updatePointBitmap(false);
            break;
        case MotionEvent.ACTION_POINTER_2_DOWN:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_2_DOWN");
            if(touchCount != THREE_FINGER_TOUCHED){
                touchCount = TWO_FINGER_TOUCHED;
            }

            if (pointer2.touched && (isEqual(dx1, dy1, dx2, dy2) == false || isEqual(dx2,dy2, dx3, dy3) == false)) {
                setPointer(dx2, dy2, pointer2);
            }

            pointer2.touched = true;
            pointer2.updatePointBitmap(true);
            break;
        case MotionEvent.ACTION_POINTER_2_UP:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_2_UP");
            pointer2.touched = false;
            pointer2.updatePointBitmap(false);
            break;

        case MotionEvent.ACTION_POINTER_3_DOWN:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_3_DOWN");

            touchCount = THREE_FINGER_TOUCHED;
            if ((isEqual(dx1, dy1, dx3, dy3) == false || isEqual(dx2, dy2, dx3,dy3) == false)) {
                setPointer(dx3, dy3, pointer3);
            }

            pointer3.touched = true;
            pointer3.updatePointBitmap(true);
            break;
        case MotionEvent.ACTION_POINTER_3_UP:
            Log.v(TAG, "onMultiTouchEvent: ACTION_POINTER_3_UP");
            pointer3.touched = false;
            pointer3.updatePointBitmap(false);
            break;

        case MotionEvent.ACTION_MOVE:
//            Log.v(TAG, "ACTION_MOVE: x1 = " + mPointer1.x + " , y1 = " + mPointer1.y + ", x2 = " + mPointer2.x + " , y2 = " + mPointer2.y
//                    + " ,x3 = " + pointer3.x + " ,y3 = " + pointer3.y);
            if (pointer1.touched && (isEqual(dx1, dy1, dx2, dy2) == false || isEqual(dx1, dy1, dx3, dy3) == false)) {
                setPointer(dx1, dy1, pointer1);
            }

            if (pointer2.touched && (isEqual(dx1, dy1, dx2, dy2) == false || isEqual(dx2,dy2, dx3, dy3) == false)) {
                setPointer(dx2, dy2, pointer2);
            }

//            System.out.println("111 dx3" + dx3 + " dy3 = " + dy3 + " dx1 = " + dx1 + " dy1 = " + dy1);
            if (pointer3.touched && (isEqual(dx1, dy1, dx3, dy3) == false || isEqual(dx2,dy2, dx3, dy3) == false)) {
                if(pointer2.touched == false){
                    setPointer(dx2, dy2, pointer3);
                    return;
                }
                setPointer(dx3, dy3, pointer3);
//                System.out.println("222dx3" + dx3 + " dy3 = " + dy3 + " dx1 = " + dx1 + " dy1 = " + dy1);
            }
            break;
        default:
            break;
        }
    }

    private void multiTouchUp(MotionEvent event) {

        for(int i = 0; i < mPointers.size(); ++i){
            Pointer pointer = mPointers.get(i);
            pointer.touched = false;
            pointer.updatePointBitmap(false);
        }

        isTouch = false;
        if (handler.hasMessages(UPDATE_VIEW_DELAYED)) {
            handler.removeMessages(UPDATE_VIEW_DELAYED);
        }

        handler.sendEmptyMessageDelayed(UPDATE_VIEW_DELAYED, 200);
        isFocus = true;
    }

    private void multiTouchDown(MotionEvent event) {
        mCameraSwitch.cancelAutoFocus();
        resetToDefault = false;
        Pointer pointer = mPointers.get(0);
        setPointer(event.getX(0), event.getY(0), pointer);
        pointer.touched = true;
        pointer.updatePointBitmap(true);
        isTouch = true;
    }

    private void onSingleTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            Log.v(TAG, "onTouchEvent: ACTION_DOWN");
            singleTouchDown(event, x, y);
            break;
        case MotionEvent.ACTION_UP:
            Log.v(TAG, "onTouchEvent: ACTION_UP");
            singleTouchUp(event, x, y);
            break;
        case MotionEvent.ACTION_MOVE:
            singleTouchMove(event, x, y);
            break;
        default:
            break;
        }
    }

    private void singleTouchMove(MotionEvent event, float x, float y) {
        Pointer pointer = mPointers.get(0);
        setPointer(x, y, pointer);
    }

    private void singleTouchUp(MotionEvent event, float x, float y) {
        Pointer pointer = mPointers.get(0);
        setPointer(x, y, pointer);
        pointer.touched = false;
        pointer.updatePointBitmap(false);
        isTouch = false;

        if (handler.hasMessages(UPDATE_VIEW_DELAYED)) {
            handler.removeMessages(UPDATE_VIEW_DELAYED);
        }
        handler.sendEmptyMessageDelayed(UPDATE_VIEW_DELAYED, 200);
        isFocus = true;
    }

    private void setPointer(float x, float y, Pointer p){
        if(x < 0)
            p.x = 0;
        else if(x >= getWidth())
            p.x = getWidth() - 1;
        else
            p.x = x;

        if(y < 0)
            p.y = 0;
        else if(y >= getHeight())
            p.y = getHeight() - 1;
        else
            p.y = y;
    }

    private boolean singleTouchDown(MotionEvent event, float x, float y) {
        mCameraSwitch.cancelAutoFocus();
        isTouch = true;
        Pointer pointer = mPointers.get(0);
        pointer.touched = true;
        pointer.x = x;
        pointer.y = y;
        pointer.updatePointBitmap(true);
        return true;
    }

    public class Pointer {
        public float x;
        public float y;
        public boolean touched;
        private Bitmap downBm;
        private Bitmap upBm;
        private Bitmap showBm;
        private Paint mPaint = new Paint();

        public Bitmap updatePointBitmap(boolean touch) {
            if (touch) {
                if (downBm == null || downBm.isRecycled()) {
                    downBm = getBitmap(R.drawable.focus_focusing);
                    Bitmap temp = downBm;
                    downBm = Bitmap.createScaledBitmap( downBm
                            , downBm .getWidth() * 3 / 2
                            , downBm .getHeight() * 3 / 2
                            , true
                            );
                    temp.recycle();
                }
                showBm = downBm;
                return showBm;
            }

            if (upBm == null || upBm.isRecycled()) {
                upBm = getBitmap(R.drawable.focus_focusing);
            }

            showBm = upBm;
            return showBm;
        }

        public Paint getPaint(){
            return mPaint;
        }

        public void showFail() {
            Bitmap bm = showBm;
            showBm = getBitmap(R.drawable.focus_focus_failed);
            if(bm != showBm){
                bm.recycle();
                bm = null;
            }
        }

        public void showSuccess() {
            Bitmap bm = showBm;
            showBm = getBitmap(R.drawable.focus_focused);
            if(bm != showBm){
                bm.recycle();
                bm = null;
            }
        }

        public void showStart() {
            Bitmap bm = showBm;
            showBm = getBitmap(R.drawable.focus_focusing);
            if(bm != showBm){
                bm.recycle();
                bm = null;
            }
        }

        public void clear(){
            if(downBm != null && !downBm.isRecycled()){
                downBm.recycle();
                downBm = null;
            }

              if(upBm != null && !upBm.isRecycled()){
                  upBm.recycle();
                  upBm = null;
            }
        }

        public float getDistanceFromTopToCenter() {
            float distance = 0.0f;
            if (showBm != null) {
                distance = showBm.getWidth() / 2;
            }
            return distance;
        }

        public float getDistanceFromLeftToCenter() {
            float distance = 0.0f;
            if (showBm != null) {
                distance = showBm.getHeight() / 2;
            }
            return distance;
        }

        private Bitmap getShowBitmap() {
            return showBm;
        }

        private Bitmap getBitmap(int resId) {
            return BitmapFactory.decodeResource(getResources(), resId);
        }

        public void recycle() {
            if (downBm != null && downBm.isRecycled() == false) {
                downBm.recycle();
                downBm = null;
            }

            if (upBm != null && upBm.isRecycled() == false) {
                upBm.recycle();
                upBm = null;
            }
        }
    }
    public void dofocus(){
        isTouch = false;
        if (handler.hasMessages(UPDATE_VIEW_DELAYED)) {
            handler.removeMessages(UPDATE_VIEW_DELAYED);
        }

        handler.sendEmptyMessageDelayed(UPDATE_VIEW_DELAYED, 200);
        isFocus = true;
    }

    class UpdateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch (what) {
            case UPDATE_VIEW_DELAYED:
                //fixed 17024
                if(multiFocusModeSupported){
                    mCameraSwitch.doFocus(true);
                }
                if (handler.hasMessages(CLEAR_FOCUS_STATE)) {
                    handler.removeMessages(CLEAR_FOCUS_STATE);
                }
                handler.sendMessageDelayed(handler.obtainMessage(CLEAR_FOCUS_STATE), 0);
                break;

            case CLEAR_FOCUS_STATE:
                mCameraSwitch.enableMenu();
                isFocus = false;
            default:
                break;
            }

            super.handleMessage(msg);
        }
    }

    public boolean isMultiFocusModeSupported() {
        return multiFocusModeSupported;
    }

    public void setMultiFocusModeSupported(boolean b) {
        multiFocusModeSupported = b;
    }
}
