/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import com.android.camera.gallery.BaseImageList;
import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.gallery.ImageList;
import com.android.camera.gallery.ImageListUber;
import com.android.camera.gallery.VideoList;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@code ImageManager} is used to retrieve and store images
 * in the media content provider.
 */
public class ImageManager {

    private static final String TAG = "ImageManager";

    private static final Uri STORAGE_URI = Images.Media.EXTERNAL_CONTENT_URI;
    private static final Uri VIDEO_STORAGE_URI =
            Uri.parse("content://media/external/video/media");

    private ImageManager() {
        Log.d(TAG, "ImageManager construct .");
    }

    /**
     * {@code ImageListParam} specifies all the parameters we need to create an
     * image list (we also need a ContentResolver).
     */
    public static class ImageListParam implements Parcelable {
        public DataLocation mLocation;
        public int mInclusion;
        public int mSort;
        public String mBucketId;

        // This is only used if we are creating an empty image list.
        public boolean mIsEmptyImageList;

        public ImageListParam() {
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mLocation.ordinal());
            out.writeInt(mInclusion);
            out.writeInt(mSort);
            out.writeString(mBucketId);
            out.writeInt(mIsEmptyImageList ? 1 : 0);
        }

        private ImageListParam(Parcel in) {
            mLocation = DataLocation.values()[in.readInt()];
            mInclusion = in.readInt();
            mSort = in.readInt();
            mBucketId = in.readString();
            mIsEmptyImageList = (in.readInt() != 0);
        }

        @Override
        public String toString() {
            return String.format("ImageListParam{loc=%s,inc=%d,sort=%d," +
                "bucket=%s,empty=%b}", mLocation, mInclusion,
                mSort, mBucketId, mIsEmptyImageList);
        }

        public static final Parcelable.Creator<ImageListParam> CREATOR
                = new Parcelable.Creator<ImageListParam>() {
            public ImageListParam createFromParcel(Parcel in) {
                return new ImageListParam(in);
            }

            public ImageListParam[] newArray(int size) {
                return new ImageListParam[size];
            }
        };

        public int describeContents() {
            return 0;
        }
    }

    // Location
    public static enum DataLocation { NONE, INTERNAL, EXTERNAL, ALL }

    // Inclusion
    public static final int INCLUDE_IMAGES = (1 << 0);
    public static final int INCLUDE_VIDEOS = (1 << 2);

    // Sort
    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    // DCIM name
    private static final String CAMERA_DCIM_NAME = "/DCIM";
    // internal bucket path
    private static final String CAMERA_IMAGE_INTERNAL_BUCKET = "/data/internal_memory";
    // camera bucket name
    private static final String CAMERA_IMAGE_BUCKET = (CAMERA_DCIM_NAME + "/Camera");
    // thumbnails bucket name
    private static final String THUMBNAIL_NAME = "/.thumbnails";
    // last image thumb
    private static final String LAST_IMAGE_THUMBNAIL = "/image_last_thumb";
    // last video thumb
    private static final String LAST_VIDEO_THUMBNAIL = "/video_last_thumb";
    // temp "JEPG" path
    private static final String TEMP_JPEG_BUCKET = "/.tempjpeg";
    // the flag is used bucket path, if the bucket is "CAMERA_IMAGE_INTERNAL_BUCKET"
    // then flag is true, if the bucket is "CAMERA_IMAGE_EXTERNAL_BUCKET" then flag
    // is false; set flag in the "checkExternalStorage()" and "checkInternalStorage()"
    // method.
    private static boolean isInternalBucket = false;

//    public static final String CAMERA_IMAGE_BUCKET_NAME =
//            Environment.getExternalStorageDirectory().toString()
//            + "/DCIM/Camera";
//    public static final String CAMERA_IMAGE_BUCKET_ID =
//            getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    public static String getCameraImageBucketPath() {
        Log.d(TAG, "----- getCameraImageBucketPath() -----");
        String result = null;
        // get bucket root path
        // external: "/mnt/sdcard/DCIM/Camera"
        // internal: "/data/internal_sdcard/DCIM/Camera"
        if ((result = getBucketRootPath()) != null) {
            result = result.concat(CAMERA_IMAGE_BUCKET);
            // if current bucket name is "CAMERA_IMAGE_INTERNAL_BUCKET"
            // then ensure directory exists and set permission
            ensureDirectoryExists(result);
        }
        Log.d(TAG, String.format("return getCameraImageBucketPath(%s)", result));
        return result;
    }

    public static String getCameraImageBucketId() {
        Log.d(TAG, "--- getCameraImageBucketId() ---");
        String result = getBucketId(getCameraImageBucketPath());
        Log.d(TAG, String.format("return getCameraImageBucketId(%s)", result));
        return result;
    }

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    public static String getBucketRootPath() {
        Log.d(TAG, "----- getBucketRootPath() -----");
        String result = null;
        // validate external sdcard
        if (checkExternalStorage(true)) {
            result = Environment.getExternalStorageDirectory().toString();
        }
        // validate internal sdcard
        else if (checkInternalStorage(true)) {
            result = CAMERA_IMAGE_INTERNAL_BUCKET;
        }

        if (result == null) {
            Log.d(TAG, "getBucketRootPath() is NULL");
            throw new RuntimeException("getBucketRootPath() is NULL, PLS check external/internal log .");
        }

        Log.d(TAG, String.format("return getBucketRootPath(%s)", result));
        return result;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatibleFolder() {
//        File nnnAAAAA = new File(
//            Environment.getExternalStorageDirectory().toString()
//            + "/DCIM/100ANDRO");
//        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
//            Log.e(TAG, String.format("create NNNAAAAA file: %s failed", nnnAAAAA.getPath()));
//        }
        Log.d(TAG, "----- ensureOSXCompatibleFolder() -----");
        String bucket_root_path = getBucketRootPath();
        if (bucket_root_path != null) {
            bucket_root_path =
                (bucket_root_path + CAMERA_DCIM_NAME + "/100ANDRO");

            File nnnAAAAA = new File(bucket_root_path);
            if (!ensureDirectoryExists(nnnAAAAA)) {
                Log.e(TAG, String.format("create nnnAAAAA file: %s failed", nnnAAAAA));
            }
        }
        Log.d(TAG, String.format("return ensureOSXCompatibleFolder(%s)", bucket_root_path));
    }

    //
    // Stores a bitmap or a jpeg byte array to a file (using the specified
    // directory and filename). Also add an entry to the media store for
    // this picture. The title, dateTaken, location are attributes for the
    // picture. The degree is a one element array which returns the orientation
    // of the picture.
    //
    public static Uri addImage(ContentResolver cr, String title, long dateTaken,
            Location location, String directory, String filename,
            Bitmap source, byte[] jpegData, int[] degree) {
        // We should store image data earlier than insert it to ContentProvider,
        // otherwise we may not be able to generate thumbnail in time.
        OutputStream outputStream = null;
        String filePath = directory + "/" + filename;
        try {
            File dir = new File(directory);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(directory, filename);
            outputStream = new FileOutputStream(file);
            if (source != null) {
                source.compress(CompressFormat.JPEG, 75, outputStream);
                degree[0] = 0;
            } else {
                outputStream.write(jpegData);
                degree[0] = getExifOrientation(filePath);
            }
        } catch (FileNotFoundException ex) {
            Log.w(TAG, ex);
            return null;
        } catch (IOException ex) {
            Log.w(TAG, ex);
            return null;
        } finally {
            Util.closeSilently(outputStream);
        }

        // Read back the compressed file size.
        File tmp = ensureFilePermission(directory, filename);
        long size = (tmp == null ? 0 : tmp.length());

        ContentValues values = new ContentValues(9);
        values.put(Images.Media.TITLE, title);

        // That filename is what will be handed to Gmail when a user shares a
        // photo. Gmail gets the name of the picture attachment from the
        // "DISPLAY_NAME" field.
        values.put(Images.Media.DISPLAY_NAME, filename);
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.ORIENTATION, degree[0]);
        values.put(Images.Media.DATA, filePath);
        values.put(Images.Media.SIZE, size);

        if (location != null) {
            values.put(Images.Media.LATITUDE, location.getLatitude());
            values.put(Images.Media.LONGITUDE, location.getLongitude());
        }

        Log.d(TAG, String.format("storage uri = %s, values = %s", new Object[] { STORAGE_URI, values }));
        // fixed bug 16191, 16230 start
        Uri result = cr.insert(STORAGE_URI, values);
        Log.d(TAG, String.format("return insert uri = %s", result));
        return result;
        // fixed bug 16191, 16230 end
    }

    public static File ensureFilePermission(String path) {
        if (path != null)
            return ensureFilePermission(new File(path));
        Log.d(TAG, String.format("return NULL ensureFilePermission(path = %s)", path));
        return null;
    }

    public static File ensureFilePermission(String dir, String name) {
        if (dir != null && name != null)
            return ensureFilePermission(new File(dir, name));
        Log.d(TAG, String.format("return NULL ensureFilePermission(dir = %s, name = %s)", new Object[] { dir, name }));
        return null;
    }

    private static File ensureFilePermission(File path) {
        Log.d(TAG, "----- ensureFilePermission() -----");
        if (path != null) {
            // if current bucket is "CAMERA_IMAGE_INTERNAL_BUCKET"
            // then set permission is 777 to "result"
            boolean need_set_perm = isInternalBucket;
            if (need_set_perm && path.exists()) {
                path.setReadable(need_set_perm, !need_set_perm);
                path.setWritable(need_set_perm, !need_set_perm);
                path.setExecutable(need_set_perm, !need_set_perm);
            }
            Log.d(TAG, String.format(
                "result need_set_perm = %b, canRead = %b, canWrite = %b, canExecute = %b",
                    new Object[] { need_set_perm, path.canRead(), path.canWrite(), path.canExecute()}));
        }
        Log.d(TAG, String.format("return ensureFilePermission(%s)", path));
        return path;
    }

    public static boolean ensureDirectoryExists(String path) {
        if (path != null)
            return ensureDirectoryExists(new File(path));
        Log.d(TAG, String.format("return false ensureDirectoryExists(dir = %s)", path));
        return false;
    }

    private static boolean ensureDirectoryExists(File path) {
        Log.d(TAG, "----- ensureDirectoryExists() -----");
        boolean result = (path != null);
        if (result) {
            if (!path.isDirectory() || !path.exists()) {
                result = path.mkdirs();
                Log.d(TAG, String.format("result ensureDirectoryExists execute mkdir = %b", result));
                // if current bucket name is "CAMERA_IMAGE_INTERNAL_BUCKET" then set permission
                if (result) ensureFilePermission(path);
            }
            Log.d(TAG, String.format("result ensureDirectoryExists path = %s", path));
        }
        Log.d(TAG, String.format("return ensureDirectoryExists(%b)", result));
        return result;
    }

    public static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            Log.e(TAG, "cannot read exif", ex);
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }
        return degree;
    }

    // This is the factory function to create an image list.
    public static IImageList makeImageList(ContentResolver cr,
            ImageListParam param) {
        DataLocation location = param.mLocation;
        int inclusion = param.mInclusion;
        int sort = param.mSort;
        String bucketId = param.mBucketId;
        boolean isEmptyImageList = param.mIsEmptyImageList;

        if (isEmptyImageList || cr == null) {
            return new EmptyImageList();
        }

        // false ==> don't require write access
        boolean haveSdCard = hasStorage(false);

        // use this code to merge videos and stills into the same list
        ArrayList<BaseImageList> l = new ArrayList<BaseImageList>();

        if (haveSdCard && location != DataLocation.INTERNAL) {
            if ((inclusion & INCLUDE_IMAGES) != 0) {
                l.add(new ImageList(cr, STORAGE_URI, sort, bucketId));
            }
            if ((inclusion & INCLUDE_VIDEOS) != 0) {
                l.add(new VideoList(cr, VIDEO_STORAGE_URI, sort, bucketId));
            }
        }
        if (location == DataLocation.INTERNAL || location == DataLocation.ALL) {
            if ((inclusion & INCLUDE_IMAGES) != 0) {
                l.add(new ImageList(cr,
                        Images.Media.INTERNAL_CONTENT_URI, sort, bucketId));
            }
        }

        // Optimization: If some of the lists are empty, remove them.
        // If there is only one remaining list, return it directly.
        Iterator<BaseImageList> iter = l.iterator();
        while (iter.hasNext()) {
            BaseImageList sublist = iter.next();
            if (sublist.isEmpty()) {
                sublist.close();
                iter.remove();
            }
        }

        if (l.size() == 1) {
            BaseImageList list = l.get(0);
            return list;
        }

        ImageListUber uber = new ImageListUber(
                l.toArray(new IImageList[l.size()]), sort);
        return uber;
    }

    private static class EmptyImageList implements IImageList {
        public void close() {
        }

        public int getCount() {
            return 0;
        }

        public IImage getImageAt(int i) {
            return null;
        }
    }

    public static ImageListParam getImageListParam(DataLocation location,
         int inclusion, int sort, String bucketId) {
         ImageListParam param = new ImageListParam();
         param.mLocation = location;
         param.mInclusion = inclusion;
         param.mSort = sort;
         param.mBucketId = bucketId;
         return param;
    }

    public static IImageList makeImageList(ContentResolver cr,
            DataLocation location, int inclusion, int sort, String bucketId) {
        ImageListParam param = getImageListParam(location, inclusion, sort,
                bucketId);
        return makeImageList(cr, param);
    }

//    private static boolean checkFsWritable() {
//        // Create a temporary file to see whether a volume is really writeable.
//        // It's important not to put it in the root directory which may have a
//        // limit on the number of files.
//        String directoryName =
//                Environment.getExternalStorageDirectory().toString() + "/DCIM";
//        File directory = new File(directoryName);
//        if (!directory.isDirectory()) {
//            if (!directory.mkdirs()) {
//                return false;
//            }
//        }
//        return directory.canWrite();
//    }

    private static boolean checkFsWritable(String path) {
        boolean result = false;
        if (path != null)
            result = checkFsWritable(new File(path));
        return result;
    }

    private static boolean checkFsReadable(String path) {
        boolean result = false;
        if (path != null)
            result = checkFsReadable(new File(path));
        return result;
    }

    private static boolean checkFsWritable(File path) {
        Log.d(TAG, "----- checkFsWritable() -----");
        boolean result = false;
        // Create a temporary file to see whether a volume is really writable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        if (path != null) {
            File dir = new File(path, CAMERA_DCIM_NAME);
            if (ensureDirectoryExists(dir)) {
                result = dir.canWrite();
            }
        }
        Log.d(TAG, String.format("return checkFsWritable(%b)", result));
        return result;
    }

    private static boolean checkFsReadable(File path) {
        Log.d(TAG, "----- checkFsReadable() -----");
        boolean result = false;
        // Create a temporary file to see whether a volume is really readable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        if (path != null) {
            File dir = new File(path, CAMERA_DCIM_NAME);
            if (ensureDirectoryExists(dir)) {
                result = dir.canRead();
            }
        }
        Log.d(TAG, String.format("return checkFsReadable(%b)", result));
        return result;
    }

    public static boolean hasStorage() {
        return hasStorage(true);
    }

    public static boolean hasStorage(boolean requireWriteAccess) {
        Log.d(TAG, String.format("----- hasStorage(require = %b) -----", requireWriteAccess));
//        String state = Environment.getExternalStorageState();
//
//        if (Environment.MEDIA_MOUNTED.equals(state)) {
//            if (requireWriteAccess) {
//                boolean writable = checkFsWritable();
//                return writable;
//            } else {
//                return true;
//            }
//        } else if (!requireWriteAccess
//                && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
//            return true;
//        }
//        return false;
        boolean result = false;
        result = (checkExternalStorage(requireWriteAccess)
                     || checkInternalStorage(requireWriteAccess));
        Log.d(TAG, String.format("return hasStorage(%b)", result));
        return result;
    }

    private static boolean checkExternalStorage(boolean requireWriteAccess) {
        Log.d(TAG, "----- checkExternalStorage() -----");
        boolean result = false;
        // set "isInternalBucket" flag
        isInternalBucket = false;
        // get environment state and validate state
        String state = Environment.getExternalStorageState();
        // mounted state, we can read/write state
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            result =
                (requireWriteAccess ?
                    checkFsWritable(Environment.getExternalStorageDirectory())
                        : true);
        }
        // read-only state and requireWriteAccess is false, we can read state
        else if (!requireWriteAccess &&
                        Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            result = true;
        }
        Log.d(TAG, String.format("return checkExternalStorage(%b)", result));
        return result;
    }

    private static boolean checkInternalStorage(boolean requireWriteAccess) {
        Log.d(TAG, "----- checkInternalStorage() -----");
        boolean result = false;
        // set "isInternalBucket" flag
        isInternalBucket = true;
        result =
            (requireWriteAccess ?
                checkFsWritable(CAMERA_IMAGE_INTERNAL_BUCKET) : // we can write state
                    checkFsReadable(CAMERA_IMAGE_INTERNAL_BUCKET)); // we can read state
        Log.d(TAG, String.format("return checkInternalStorage(%b)", result));
        return result;
    }

    private static Cursor query(ContentResolver resolver, Uri uri,
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        try {
            if (resolver == null) {
                return null;
            }
            return resolver.query(
                    uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static boolean isMediaScannerScanning(ContentResolver cr) {
        boolean result = false;
        Cursor cursor = query(cr, MediaStore.getMediaScannerUri(),
                new String [] {MediaStore.MEDIA_SCANNER_VOLUME},
                null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        return result;
    }

    private static String getLastThumbPath(String lastPath) {
        Log.d(TAG, "----- getLastThumbPath() -----");
        String result = getBucketRootPath().concat(CAMERA_DCIM_NAME).concat(lastPath);
        // ensure parent directory exists
        File file = new File(result);
        ensureDirectoryExists(file.getParentFile());
        Log.d(TAG, String.format("return getLastThumbPath(%s)", result));
        return result;
    }

    public static String getLastImageThumbPath() {
//        return Environment.getExternalStorageDirectory().toString() +
//               "/DCIM/.thumbnails/image_last_thumb";
        return getLastThumbPath((THUMBNAIL_NAME + LAST_IMAGE_THUMBNAIL));
    }

    public static String getLastVideoThumbPath() {
//        return Environment.getExternalStorageDirectory().toString() +
//               "/DCIM/.thumbnails/video_last_thumb";
        return getLastThumbPath((THUMBNAIL_NAME + LAST_VIDEO_THUMBNAIL));
    }

    public static String getTempJpegPath() {
//        return Environment.getExternalStorageDirectory().toString() +
//               "/DCIM/.tempjpeg";
        return getLastThumbPath(TEMP_JPEG_BUCKET);
    }

    // fixed not storage hint start
    public static boolean getIsInternalBucket() {
        return isInternalBucket;
    }
    // fixed bot storage hint end
}
