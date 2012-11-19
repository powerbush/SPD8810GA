package com.globalLock.location;


public class DataPack {
	 public static int DEF_MAX_DPD_LEN = 1400; // 鏁版嵁鍖呴粯璁ょ殑锟�锟斤拷鏁版嵁闀垮害

     public static byte DP_HEADER = 0x40; // 鍖呭ご

     public static byte DP_TAIL = 0x0d; // 鍖呭熬

     public static byte SIGN_NORMAL = 0x00; // 鏃犵壒娈婂睘锟�

     public static byte SIGN_AFFAIR = 0x01; // 浜嬪姟

     public static byte SIGN_AFFAIR_BEGIN = 0x03; // 浜嬪姟锟�锟斤拷

     public static byte SIGN_AFFAIR_DOING = 0x05; // 浜嬪姟杩涜锟�

     public static byte SIGN_AFFAIR_END = 0x07; // 浜嬪姟缁撴潫

     public static byte SIGN_AFFAIR_TEST = 0x07; // 浜嬪姟绫诲瀷娴嬭瘯锟�

     public static byte SIGN_TODEV = 0x08; // 鍚戣澶囷拷?锟�

     public static byte SIGN_NEED_RETURN = 0x10; // 锟�锟斤拷鍥炲

     public static byte SIGN_RETURN = 0x20; // 杩斿洖鏁版嵁

     public static byte SIGN_NOCHECKCODE = 0x40; // 涓嶉渶瑕佽绠楁牎楠岀爜
}
