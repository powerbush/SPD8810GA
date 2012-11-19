package com.android.contacts.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.android.contacts.model.BlackCallsEntity;

public class BlackCallsDb {

	 protected static final String TAG = "BlackCallsDb";
	 private final Context context;
	 public static Vector<BlackCallsEntity> BlackCallsVector = new Vector<BlackCallsEntity>();
	 public static Vector<BlackCallsEntity> BlackRecorderVector = new Vector<BlackCallsEntity>();
	 
	public static final String AUTHORITY  = "com.android.providers.contacts.block";

	 public static final class BlockMumber implements BaseColumns {
	        public static final Uri CONTENT_URI  = Uri.parse("content://com.android.providers.contacts.block/block_mumbers");

	        public static final String MUMBER_VALUE = "mumber_value";
	        public static final String BLOCK_TYPE	= "block_type";
	        public static final String NOTES		= "notes";
	    }

	    public static final class BlockRecorder implements BaseColumns {
	        public static final Uri CONTENT_URI  = Uri.parse("content://com.android.providers.contacts.block/block_recorded");

	        public static final String MUMBER_VALUE = "mumber_value";
	        public static final String BLOCK_DATE = "block_date";
	    }
	    public BlackCallsDb(Context context) {
			this.context = context;
		}
		
	    public List<BlackCallsEntity> QueryBlackRecoder(){
	    	ContentResolver cr = context.getContentResolver(); 
	    	String[] columns = new String[]{BlockRecorder.MUMBER_VALUE,BlockRecorder.BLOCK_DATE};
	    	
	    	Cursor cursor = cr.query(BlockRecorder.CONTENT_URI, columns, null, null, null);
	    	BlackRecorderVector = new Vector<BlackCallsEntity>();
	    	List<BlackCallsEntity> result = new ArrayList<BlackCallsEntity>();
	    	
    	 	
	 		if (cursor.getCount() != 0) {
				cursor.moveToFirst();
				for (int i = 0; i < cursor.getCount(); i++) {
					BlackCallsEntity BlackCalls = new BlackCallsEntity();
					BlackCalls.setBlackCallsNumber(cursor.getString(cursor.getColumnIndex(BlockRecorder.MUMBER_VALUE)));
					BlackCalls.setBlackRecoderDate(cursor.getString(cursor.getColumnIndex(BlockRecorder.BLOCK_DATE)));
					result.add(BlackCalls);
					// Goto next row
					cursor.moveToNext();
					BlackCallsVector.add(BlackCalls);
				}
				cursor.close();
			}
	 		else
	 		{
	 			String examplenumber = "10086";
	 			String date = "2011-4-1";
	 			AddBlackCalls(examplenumber,date);
	 			
	 			QueryBlackCalls();
	 			
	 			cursor.close();
	 		}
	 		return result;	
	    }
	    
   // public List<BlackCallsEntity> QueryBlackCalls(Context base){
    public List<BlackCallsEntity> QueryBlackCalls(){
    	// ContentResolver cr = base.getContentResolver();
    	 ContentResolver cr = context.getContentResolver(); 
    	 String[] columns = new String[]{BlockMumber.MUMBER_VALUE,BlockMumber.BLOCK_TYPE};
    	 Cursor cursor = cr.query(BlockMumber.CONTENT_URI, columns, null, null, null);
    	 BlackCallsVector = new Vector<BlackCallsEntity>();
 		List<BlackCallsEntity> result = new ArrayList<BlackCallsEntity>();
    	 	
 		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			for (int i = 0; i < cursor.getCount(); i++) {
				BlackCallsEntity BlackCalls = new BlackCallsEntity();
				BlackCalls.setBlackCallsNumber(cursor.getString(cursor.getColumnIndex(BlockMumber.MUMBER_VALUE)));
				BlackCalls.setBlackCallsType(cursor.getString(cursor.getColumnIndex(BlockMumber.BLOCK_TYPE)));
				result.add(BlackCalls);
				// Goto next row
				cursor.moveToNext();
				BlackCallsVector.add(BlackCalls);
			}
			cursor.close();
		}
 		else
 		{
 			String examplenumber = "10086";
 			String type = "Call";
 			AddBlackCalls(examplenumber,type);
 			
 			QueryBlackCalls();
 			
 			cursor.close();
 		}
 		return result;	
    }
    
    public boolean AddBlackRecoders(String BlackCallsNumber,String date)
    {
    	//ContentResolver cr = base.getContentResolver();
    	ContentResolver cr = context.getContentResolver(); 
		ContentValues values = new ContentValues();
		values.put(BlockRecorder.MUMBER_VALUE,BlackCallsNumber);
		values.put(BlockRecorder.BLOCK_DATE,date);
		
		//values.put("block_number", "" + count++);
		return cr.insert(BlockRecorder.CONTENT_URI, values) != null;
    }
    
    public boolean AddBlackCalls(String BlackCallsNumber,int Type)
    {
    	//ContentResolver cr = base.getContentResolver();
    	ContentResolver cr = context.getContentResolver(); 
		ContentValues values = new ContentValues();
		values.put(BlockMumber.MUMBER_VALUE,BlackCallsNumber);
		values.put(BlockMumber.BLOCK_TYPE,Type);
		
		//values.put("block_number", "" + count++);
		return cr.insert(BlockMumber.CONTENT_URI, values) != null;
    }
    
   // public boolean AddBlackCalls(Context base,String BlackCallsNumber,String Type)
    public boolean AddBlackCalls(String BlackCallsNumber,String Type)
    {
    	//ContentResolver cr = base.getContentResolver();
    	ContentResolver cr = context.getContentResolver(); 
		ContentValues values = new ContentValues();
		values.put(BlockMumber.MUMBER_VALUE,BlackCallsNumber);
		values.put(BlockMumber.BLOCK_TYPE,Type);
		
		//values.put("block_number", "" + count++);
		return cr.insert(BlockMumber.CONTENT_URI, values) != null;
    }
    
    //public boolean DelBlackCalls(Context base,String BlackCallsNumber)
    public boolean DelBlackCalls(String BlackCallsNumber)
    {
    	//ContentResolver cr = base.getContentResolver();
    	ContentResolver cr = context.getContentResolver(); 
    	return cr.delete(BlockMumber.CONTENT_URI, BlockMumber.MUMBER_VALUE + "='" + BlackCallsNumber + "'", null)>0;
    	//int result = cr.delete(BlockMumber.CONTENT_URI, BlockMumber.MUMBER_VALUE + "='" + BlackCallsNumber + "'", null);
    }
    
    public boolean UpdateBlackType(String BlackCallsNumber,int Type)
    {
    	ContentResolver cr = context.getContentResolver(); 
    	ContentValues values = new ContentValues();
    	values.put(BlockMumber.BLOCK_TYPE,Type);
    	return cr.update(BlockMumber.CONTENT_URI, values, BlockMumber.MUMBER_VALUE + "='" + BlackCallsNumber + "'", null)>0;
    }
    
    public boolean UpdateBlackType(String BlackCallsNumber,String Type)
    {
    	ContentResolver cr = context.getContentResolver(); 
    	ContentValues values = new ContentValues();
    	values.put(BlockMumber.BLOCK_TYPE,Type);
    	return cr.update(BlockMumber.CONTENT_URI, values, BlockMumber.MUMBER_VALUE + "='" + BlackCallsNumber + "'", null)>0;
    }
    
    
    public boolean DelBlackLogs(String BlackLogsNumber)
    {
    	
    	ContentResolver cr = context.getContentResolver(); 
    	return cr.delete(BlockRecorder.CONTENT_URI, BlockRecorder.MUMBER_VALUE + "='" + BlackLogsNumber + "'", null)>0;
    	
    }
}
