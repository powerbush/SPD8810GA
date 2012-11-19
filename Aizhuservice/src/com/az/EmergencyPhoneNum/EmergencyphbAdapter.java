package com.az.EmergencyPhoneNum;

import java.util.ArrayList;
import java.util.zip.Inflater;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.az.Main.R;

public class EmergencyphbAdapter extends BaseAdapter {

	private Context context;
	private ArrayList<Emergencyphbentry.emergencyphb> phblist;
	private LayoutInflater inflater;

	public EmergencyphbAdapter(Context context,
			ArrayList<Emergencyphbentry.emergencyphb> phblist) {
		this.context = context;
		this.phblist = phblist;
		inflater = LayoutInflater.from(context);
	}

	@Override
	public int getCount() {
		return phblist.size();
	}

	@Override
	public Object getItem(int i) {
		// TODO Auto-generated method stub
		return phblist.get(i);
	}

	@Override
	public long getItemId(int i) {
		// TODO Auto-generated method stub
		return ((Emergencyphbentry.emergencyphb) phblist.get(i)).getId();
	}

	@Override
	public View getView(int i, View view, ViewGroup viewgroup) {
		// TODO Auto-generated method stub
		view = inflater.inflate(R.layout.listcontactitem, null);
		TextView phonetx = (TextView) view.findViewById(R.id.name_contact_db);
		TextView bodytx = (TextView) view.findViewById(R.id.phone_content_db);
		Emergencyphbentry.emergencyphb phblst = phblist.get(i);
		// cathon xiong set smscontent size
		phonetx.setText(phblst.getname());
		phonetx.setTextSize(40);
		bodytx.setText(phblst.getphonenum());
		bodytx.setTextSize(40);
		return view;
	}

	public String getPhbAll() {
		int count = 0;
		StringBuffer stringBuffer = new StringBuffer("[");
		for (Emergencyphbentry.emergencyphb emergency : phblist) {
			stringBuffer.append(emergency.name);
			stringBuffer.append("@@");
			stringBuffer.append(emergency.phonenum);
			if (++count < phblist.size())
				stringBuffer.append(",");
		}
		stringBuffer.append("]");

		return stringBuffer.toString();
	}
}
