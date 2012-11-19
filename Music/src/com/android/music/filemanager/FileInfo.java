package com.android.music.filemanager;
import com.android.music.R;
public class FileInfo {
    public String Name;
    public String Path;
    public long Size;
    public boolean IsDirectory = false;
    public int FileCount = 0;
    public int FolderCount = 0;

    public int getIconResourceId() {
        if (IsDirectory) {
            return R.drawable.folder_icon;
        }
        else if(FileUtil.isMusicType(Name)){
/*            String type = FileUtil.getMIMEType(Name);
            if (type.equals("apk/*")) {
                return R.drawable.apk;
            }
            else if (type.equals("image/*")) {
                return R.drawable.image;
            }
            else if (type.equals("video/*")) {
                return R.drawable.video;
            }
            else if (type.equals("audio/*")) {
                return R.drawable.audio;
            }
            else if (type.equals("text/*")) {
                return R.drawable.text;
            }
            else {
                return R.drawable.unknown;
            }*/
            return R.drawable.music_icon;
        }
        return R.drawable.music_icon;
    }
}
