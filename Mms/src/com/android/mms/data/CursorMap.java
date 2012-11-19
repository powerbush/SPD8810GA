package com.android.mms.data;

import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

public class CursorMap implements Parcelable {
    public Map<Integer, Map<Integer, String>> map;// = new HashMap<Long, Map<Long, String>>();

    public String name;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeMap(map);
        dest.writeString(name);
    }

    public static final Parcelable.Creator<CursorMap> CREATOR = new Parcelable.Creator<CursorMap>() {
        @SuppressWarnings("unchecked")
        @Override
        public CursorMap createFromParcel(Parcel source) {
            CursorMap c = new CursorMap();
            c.map = source.readHashMap(HashMap.class.getClassLoader());
            c.name = source.readString();
            return c;
        }

        @Override
        public CursorMap[] newArray(int size) {
            // TODO Auto-generated method stub
            return null;
        }
    };
}
