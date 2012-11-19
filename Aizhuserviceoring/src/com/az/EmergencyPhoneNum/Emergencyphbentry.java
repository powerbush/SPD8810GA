package com.az.EmergencyPhoneNum;

import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class Emergencyphbentry {
	private static String TAG = "emergencyphb cathon";

	private Context context;

	public Emergencyphbentry(Context context) {
		this.context = context;
	}

	public ArrayList<emergencyphb> getphb() {
		SQLiteDatabase db = context.openOrCreateDatabase("emergencyphb.db",
				Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE,
				null);
		db.execSQL("create table if not exists emerphb("
				+ "_id integer primary key autoincrement,"
				+ "name text not null," + "phonenum text not null,"
				+ "photo blob" + ")");

		Cursor c = db.query("emerphb", null, null, null, null, null, null);

		Log.i(TAG, "cathon get sqldb  " + c);

		if (c != null) {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			while (c.moveToNext()) {

				emergencyphb phb = new emergencyphb();

				int id = c.getInt(c.getColumnIndex("_id"));
				Log.i(TAG, "cathon get sqldb  " + id);
				phb.setId(id);

				String name = c.getString(c.getColumnIndex("name"));
				Log.i(TAG, "cathon get sqldb  " + name);
				phb.setname(name);

				String phonenum = c.getString(c.getColumnIndex("phonenum"));
				Log.i(TAG, "cathon get sqldb  " + phonenum);
				phb.setphonenum(phonenum);

				phblst.add(phb);
			}
			c.close();
			db.close();
			return phblst;

		} else {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			return phblst;
		}
	}

	// emerphb

	public ArrayList<emergencyphb> delsinglephb(int indexdelte) {
		SQLiteDatabase db = context.openOrCreateDatabase("emergencyphb.db",
				Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE,
				null);
		db.execSQL("delete from emerphb where _id = " + indexdelte);
		// db.execSQL("delete from  database01 where name='"+con.getName()+"'");
		Cursor c = db.query("emerphb", null, null, null, null, null, null);

		Log.i(TAG, "cathon get sqldb  " + c);

		if (c != null) {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			while (c.moveToNext()) {
				emergencyphb phb = new emergencyphb();

				String name = c.getString(c.getColumnIndex("name"));
				Log.i(TAG, "cathon get sqldb  " + name);
				phb.setname(name);

				String phonenum = c.getString(c.getColumnIndex("phonenum"));
				Log.i(TAG, "cathon get sqldb  " + phonenum);
				phb.setphonenum(phonenum);

				phblst.add(phb);
			}
			c.close();
			db.close();
			return phblst;

		} else {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			return phblst;
		}
	}

	public ArrayList<emergencyphb> delallphb() {
		SQLiteDatabase db = context.openOrCreateDatabase("emergencyphb.db",
				Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE,
				null);
		db.execSQL("delete from emerphb");
		Cursor c = db.query("emerphb", null, null, null, null, null, null);

		Log.i(TAG, "cathon get sqldb  " + c);

		if (c != null) {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			while (c.moveToNext()) {
				emergencyphb phb = new emergencyphb();

				String name = c.getString(c.getColumnIndex("phone"));
				Log.i(TAG, "cathon get sqldb  " + name);
				phb.setname(name);
				String phonenum = c.getString(c.getColumnIndex("body"));
				Log.i(TAG, "cathon get sqldb  " + phonenum);
				phb.setphonenum(phonenum);
				phblst.add(phb);
			}
			c.close();
			db.close();
			return phblst;

		} else {
			ArrayList<emergencyphb> phblst = new ArrayList<Emergencyphbentry.emergencyphb>();
			return phblst;
		}
	}

	public class emergencyphb {
		int id;
		String name;
		String phonenum;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getname() {
			return name;
		}

		public void setname(String name) {
			this.name = name;
		}

		public String getphonenum() {
			return phonenum;
		}

		public void setphonenum(String phonenum) {
			this.phonenum = phonenum;
		}

	}
}
