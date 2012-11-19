package com.android.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Message;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.Log;

public class VideoPhoneView extends SurfaceView implements SurfaceHolder.Callback{

	private String TAG = "VideoPhoneView";
	private SurfaceHolder mSurfaceHolder = null;
	private Message mMessage;
	
	public VideoPhoneView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initVideoPhoneView();
	}

	public VideoPhoneView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initVideoPhoneView();
	}

	public VideoPhoneView(Context context) {
		super(context);
		initVideoPhoneView();
	}

       public SurfaceHolder getReadyHolder() {
           return mSurfaceHolder;
       }	

       public void setMessage(Message msg){
	   	mMessage = msg;
       	}

	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		
	}

	public void surfaceCreated(SurfaceHolder arg0) {
		mSurfaceHolder = arg0;	
                Log.d(TAG, "surfaceCreated");		
		if (mMessage != null)
			mMessage.sendToTarget();
		
		mMessage = null;
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
                Log.d(TAG, "surfaceDestroyed");
		mSurfaceHolder = null;		
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// TODO Auto-generated method stub
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
	private void initVideoPhoneView(){
    Log.d(TAG, "initVideoPhoneView");
		getHolder().addCallback(this);
	        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	        setFocusable(true);
	        setFocusableInTouchMode(true);
	        requestFocus();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		super.onDraw(canvas);        
	        Paint paint = new Paint();
	        paint.setColor(android.graphics.Color.RED);
	        canvas.drawLine(0, 0, this.getWidth() - 1, 0, paint);
	        canvas.drawLine(0, 0, 0, this.getHeight() - 1, paint);
	        canvas.drawLine(this.getWidth() - 1, 0, this.getWidth() - 1, this.getHeight() - 1, paint);
	        canvas.drawLine(0, this.getHeight() - 1, this.getWidth() - 1, this.getHeight() - 1, paint);
	}

}
