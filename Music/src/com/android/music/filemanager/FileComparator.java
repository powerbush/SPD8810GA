package com.android.music.filemanager;

import java.util.Comparator;



public class FileComparator implements Comparator<FileInfo> {

	public int compare(FileInfo file1, FileInfo file2) {
		
		if (file1.IsDirectory && !file2.IsDirectory) {
			return -1000;
		} else if (!file1.IsDirectory && file2.IsDirectory) {
			return 1000;
		}
		
		return file1.Name.compareTo(file2.Name);
	}
}