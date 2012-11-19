package com.android.mms.ui;

import android.app.Activity;  
import android.graphics.drawable.AnimationDrawable;  
import android.graphics.drawable.Drawable;  
import android.os.Bundle;  
import android.view.View;  
import android.widget.ImageView;  
import android.content.Intent;
import android.content.ComponentName;
import com.android.mms.R;
import android.net.Uri;
import android.database.Cursor;
import com.android.mms.data.Conversation;


public class AizhuSmsFrameActivity extends Activity {  
      
    private ImageView image;  
    private long threadId=0;
    private String phoneNum;
    private String MmsPhoneNum;
      
    @Override  
    protected void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.mms_frame);  
        image = (ImageView) findViewById(R.id.frame_image);  
    }  
    
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//android.os.Process.killProcess(android.os.Process.myPid());//kill掉自已，这方法不是很好
	}
	
    @Override  
    public void onWindowFocusChanged(boolean hasFocus) {  
        super.onWindowFocusChanged(hasFocus);  
        image.setBackgroundResource(R.anim.frame);  //将动画资源文件设置为ImageView的背景   
        AnimationDrawable anim = (AnimationDrawable) image.getBackground(); //获取ImageView背景,此时已被编译成AnimationDrawable   
        anim.start();   //开始动画   
    }  
	
    private void getThreadId(){
						
    Intent intent = getIntent();	
    MmsPhoneNum = intent.getStringExtra("phoneAddress");
	Uri uri = Uri.parse("content://sms/inbox");         
	Cursor cur = this.managedQuery(uri,new String[]{"thread_id","address"}, null, null, null);         
	if(cur != null){
		if (cur.moveToFirst()) {         
			do{     
				threadId=cur.getLong(0); 
				phoneNum=cur.getString(1); 
				if(phoneNum.equals(MmsPhoneNum) || phoneNum.equals("+86" + MmsPhoneNum)){ 
					break;
				}
			}while(cur.moveToNext());   
		}
	}
	cur.close();
    }
		
    public void openActivity(View view)
    {
        /*
    	ComponentName componentName = new ComponentName("com.android.aizhuhealthmms","com.android.aizhuhealthmms.AizhuhealthmmsActivity");   
        Intent intent=new Intent();   
        intent.setComponent(componentName);   
        intent.setAction(Intent.ACTION_VIEW);   
        startActivity(intent); 
        */
        getThreadId();
	Intent intent = new Intent(this, ComposeMessageActivity.class);
   	intent.setData(Conversation.getUri(threadId));
	intent.putExtra("is_forbid_slide",true);
	startActivity(intent);
        finish();
    }
    
    public void stopFrame(View view) {  
        AnimationDrawable anim = (AnimationDrawable) image.getBackground();  
        if (anim.isRunning()) { //如果正在运行,就停止   
            anim.stop();  
        }  
    }  
      
    public void runFrame(View view) {  
        //完全编码实现的动画效果   
        AnimationDrawable anim = new AnimationDrawable();  
        for (int i = 0; i <= 5; i++) {  
            //根据资源名称和目录获取R.java中对应的资源ID   
            int id = getResources().getIdentifier("f" + i, "drawable", getPackageName());  
            //根据资源ID获取到Drawable对象   
            Drawable drawable = getResources().getDrawable(id);  
            //将此帧添加到AnimationDrawable中   
            anim.addFrame(drawable, 300);  
        }  
        anim.setOneShot(false); //设置为loop   
        image.setBackgroundDrawable(anim);  //将动画设置为ImageView背景   
        anim.start();   //开始动画   
    }  
}  

