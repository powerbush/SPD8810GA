package com.android.mms.ui;

import java.util.ArrayList;
import java.util.HashMap;
import android.app.ListActivity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.content.pm.ActivityInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import com.android.mms.R;
//import com.android.mms.block.BlockListActivity;
import com.android.mms.block.BlockSettingActivity;
import com.android.mms.data.Conversation;
import android.net.Uri;
import android.database.Cursor;
import android.graphics.Color; 


public class ConversationMainList extends ListActivity {
   
	private long threadId=0;
    private String phoneNum;
    
	//ListActivity一个以列表的方式显示数据源、数组的Activity
	//ListActivity Class Overview(此描述摘自官方文档说的非常清楚了)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_main_list1);
	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        
        ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String,Object>>();
        HashMap<String, Object> map1 = new HashMap<String, Object>();
        HashMap<String, Object> map2 = new HashMap<String, Object>();
        //HashMap<String, Object> map3 = new HashMap<String, Object>();
        
        //一个map对象对应一条数据
        map1.put("user_name", getString(R.string.user_newmms));
        map1.put("user_icon", R.drawable.newmmms);
        
        map2.put("user_name", getString(R.string.user_mms1));
        map2.put("user_icon", R.drawable.mms1);
        
        //map3.put("user_name", getString(R.string.user_mms2));
        //map3.put("user_icon", R.drawable.mms2);
        
        list.add(map1);
        list.add(map2);
        //list.add(map3);
        
        /*
         * 参数一 Context
         * 参数二 就是上边声明的那个ArrayList对象
         * 参数三 这个参数用来指定 我们一行数据 的key 也就是一个map对象的key 上下结合看一下 因为我们一条数据也就是一行
         * 对应一个map对象 一个map对象包含2个数据 即 user_name 和 user_icon 这个参数就是用来指定这2个key 这里是通过String数组的方式
         * 参数四  大家一看就知道了 意思是 user_name 这条数据用 R.id.user_name 这个TextView显示  user_icon 这条数据用 
         * R.id.user_icon 显示
         */
        
        SimpleAdapter listAdapter = new SimpleAdapter(this,list,
        		R.layout.conversation_main_list2, new String[] {"user_name","user_icon"},
        		new int[] {R.id.user_name,R.id.user_icon});
        		
        //这是Adapter setListAdapter()此方法来自ListActivity
        setListAdapter(listAdapter);

	ListView lv = getListView();
	lv.setCacheColorHint(0);  //设置拖动列表的时候防止出现黑色背景  
       
    }
    
    
    //当我们点击一条数据 或者说一行时 触发的Click事件
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	Intent intent;
    	super.onListItemClick(l, v, position, id);
    	switch(position){
    		case 0:
                //liao
                intent=ComposeMessageActivity.createIntent(this, 0);
                intent.putExtra("is_forbid_slide",true); 
    			startActivity(intent);	
    			break;
    		case 1:
    			intent = new Intent(this, ConversationList.class);
    			startActivity(intent);
    			break;
    		case 2:
			//intent = new Intent(this, BlockListActivity.class);
			getThreadId();
			intent = new Intent(this, ComposeMessageActivity.class);
           		intent.setData(Conversation.getUri(threadId));
			intent.putExtra("is_forbid_slide",true); //liao
    			startActivity(intent);
    			break;
    	}
    }
    
    private void getThreadId(){
        /*liao*/
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        String MmsPhoneNum = preferences.getString("phone_num", "");
        //Log.i("info_liao", MmsPhoneNum + "");
        /*liao*/
		Uri uri = Uri.parse("content://sms/inbox");         
		Cursor cur = this.managedQuery(uri,new String[]{"thread_id","address"}, null, null, null);         
		if(cur != null){
			if (cur.moveToFirst()) {         
				do{     
					//for(int j = 0; j < cur.getColumnCount(); j++){    
					threadId=cur.getLong(0); 
					phoneNum=cur.getString(1); 
					if(phoneNum.equals(MmsPhoneNum) || phoneNum.equals("+86" + MmsPhoneNum)){  //liao
						break;
					}
					//} 
				}while(cur.moveToNext());   
			}
		}
		cur.close();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        menu.add(1,1,1,R.string.menu_preferences).setIcon(android.R.drawable.ic_menu_preferences);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        Intent intent  = new Intent();
        switch (item.getItemId()) {
        case 1:
            intent.setClass(this, BlockSettingActivity.class);
            break;
        }
        startActivity(intent);
        return super.onOptionsItemSelected(item);
    }

}


