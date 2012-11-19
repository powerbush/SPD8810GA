package com.android.music.filemanager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import com.android.music.R;
public class FileUtil {

    public static String getSDPath() {

        if (Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED)) {
            File sdDir = Environment.getExternalStorageDirectory();
            return sdDir.getPath();
        }
        return "/sdcard";
    }

    public static FileInfo getFileInfo(File f) {
        FileInfo info = new FileInfo();
        info.Name = f.getName();
        info.IsDirectory = f.isDirectory();
        calcFileContent(info, f);
        return info;
    }

    private static void calcFileContent(FileInfo info, File f) {
        if (f.isFile()) {
            info.Size += f.length();
        }
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; ++i) {
                    File tmp = files[i];
                    if (tmp.isDirectory()) {
                        info.FolderCount++;
                    }
                    else if (tmp.isFile()) {
                        info.FileCount++;
                    }
                    if (info.FileCount + info.FolderCount >= 10000) {
                        break;
                    }
                    calcFileContent(info, tmp);
                }
            }
        }
    }

    public static String formetFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        if (fileS < 1024) {
            fileSizeString = fileS + " B";
        }
        else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + " K";
        }
        else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + " M";
        }
        else {
            fileSizeString = df.format((double) fileS / 1073741824) + " G";
        }
        return fileSizeString;
    }

    public static String combinPath(String path, String fileName) {
        return path + (path.endsWith(File.separator) ? "" : File.separator)
                + fileName;
    }

    public static boolean copyFile(File src, File tar) throws Exception {
        if (src.isFile()) {
            InputStream is = new FileInputStream(src);
            OutputStream op = new FileOutputStream(tar);
            BufferedInputStream bis = new BufferedInputStream(is);
            BufferedOutputStream bos = new BufferedOutputStream(op);
            byte[] bt = new byte[1024 * 8];
            int len = bis.read(bt);
            while (len != -1) {
                bos.write(bt, 0, len);
                len = bis.read(bt);
            }
            bis.close();
            bos.close();
        }
        if (src.isDirectory()) {
            File[] f = src.listFiles();
            tar.mkdir();
            for (int i = 0; i < f.length; i++) {
                copyFile(f[i].getAbsoluteFile(), new File(tar.getAbsoluteFile()
                        + File.separator + f[i].getName()));
            }
        }
        return true;
    }

    public static boolean moveFile(File src, File tar) throws Exception {
        if (copyFile(src, tar)) {
            deleteFile(src);
            return true;
        }
        return false;
    }

    public static void deleteFile(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; ++i) {
                    deleteFile(files[i]);
                }
            }
        }
        f.delete();
    }

    public static String getMIMEType(String name) {
        String type = "";
        String end = name.substring(name.lastIndexOf(".") + 1, name.length()).toLowerCase();
        if (end.equals("apk")) {
            return "application/vnd.android.package-archive";
        }
        else if (end.equals("mp4") || end.equals("avi") || end.equals("3gp")
                || end.equals("m4v") || end.equals("rmvb")) {
            type = "video";
        }
        else if (end.equals("m4a") || end.equals("mp3") || end.equals("mid")
                || end.equals("xmf") || end.equals("ogg") || end.equals("wav")) {
            type = "audio";
        }
        else if (end.equals("jpg") || end.equals("gif") || end.equals("png")
                || end.equals("jpeg") || end.equals("bmp")) {
            type = "image";
        }
        else if (end.equals("txt") || end.equals("log")) {
            type = "text";
        }
        else {
            type = "*";
        }
        type += "/*";
        return type;
    }

    public static ArrayList<FileInfo> getFiles(Activity activity, String path) {
        File f = new File(path);
        File[] files = f.listFiles();
        if (files == null) {
            Toast.makeText(activity, R.string.invalid, Toast.LENGTH_SHORT).show();
            return null;
        }

        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            Log.d("FileUtil", "file.getName:"+file.getName());
            if (isSupportType(file)) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.Name = file.getName();
                fileInfo.IsDirectory = file.isDirectory();
                fileInfo.Path = file.getPath();
                fileInfo.Size = file.length();
                fileList.add(fileInfo);
            }
        }

        Collections.sort(fileList, new FileComparator());
        return fileList;
    }
    public static boolean isSupportType(File file) {
        if(file==null||(file.isFile()&&!isMusicType(file.getName()))){
            return false;
        }
        return true;
    }
    public static boolean isMusicType(String fileName) {
        if(fileName==null||fileName.length()==0){
            return false;
        }
        String lower = fileName.toLowerCase();
		if (lower.endsWith(".mp3") || lower.endsWith(".m4a")
				|| lower.endsWith(".wav") || lower.endsWith(".amr")
				|| lower.endsWith(".awb") || lower.endsWith(".ogg")
				|| lower.endsWith(".oga") || lower.endsWith(".aac")
				|| lower.endsWith(".mka") || lower.endsWith(".mid")
				|| lower.endsWith(".midi") || lower.endsWith(".xmf")
				|| lower.endsWith(".rtttl") || lower.endsWith(".smf")
				|| lower.endsWith(".imy") || lower.endsWith(".rtx")
				|| lower.endsWith(".ota") || lower.endsWith(".wma")) {
			return true;
		}
        return false;
    }
}
