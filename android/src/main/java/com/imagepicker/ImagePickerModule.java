package com.imagepicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.util.Base64;
import android.app.AlertDialog;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.content.ComponentName;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.io.FileOutputStream;
import java.util.UUID;
import java.net.URL;
import java.net.MalformedURLException;

public class ImagePickerModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    static final String TAG = "ImagePicker";
    static final int REQUEST_LAUNCH_CAMERA = 1;
    static final int REQUEST_LAUNCH_IMAGE_LIBRARY = 2;
    static final int REQUEST_LAUNCH_VIDEO_LIBRARY = 3;
    static final int REQUEST_SHARE = 4;

    private Uri mCameraCaptureURI;
    private Callback mCallback;
    private Boolean noData = false;
    private int maxWidth = 0;
    private int maxHeight = 0;
    private int quality = 100;
    WritableMap response;

    public ImagePickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "FilePickerManager"; // Change from UIImagePickerManager to FilePickerManager
    }

    @ReactMethod
    public void showImagePicker(final ReadableMap options, final Callback callback) {
        Activity activity = getCurrentActivity();
        if (activity == null) return;

        response = Arguments.createMap();

        List<String> mTitles = new ArrayList<String>();
        List<String> mActions = new ArrayList<String>();

        String cancelButtonTitle = "Cancel";

        if (options.hasKey("takePhotoButtonTitle")
                && !options.getString("takePhotoButtonTitle").isEmpty()) {
            mTitles.add(options.getString("takePhotoButtonTitle"));
            mActions.add("photo");
        }
        if (options.hasKey("chooseFromLibraryButtonTitle")
                && !options.getString("chooseFromLibraryButtonTitle").isEmpty()) {
            mTitles.add(options.getString("chooseFromLibraryButtonTitle"));
            mActions.add("library");
        }
        if (options.hasKey("cancelButtonTitle")
                && !options.getString("cancelButtonTitle").isEmpty()) {
            cancelButtonTitle = options.getString("cancelButtonTitle");
        }
        mTitles.add(cancelButtonTitle);
        mActions.add("cancel");

        String[] option = new String[mTitles.size()];
        option = mTitles.toArray(option);

        String[] action = new String[mActions.size()];
        action = mActions.toArray(action);
        final String[] act = action;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.select_dialog_item, option);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        if (options.hasKey("title") && options.getString("title") != null && !options.getString("title").isEmpty()) {
            builder.setTitle(options.getString("title"));
        }

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int index) {
                if (act[index].equals("photo")) {
                    launchCamera(options, callback);
                } else if (act[index].equals("library")) {
                    launchImageLibrary(options, callback);
                } else {
                    response.putBoolean("didCancel", true);
                    callback.invoke(response);
                }
            }
        });

        final AlertDialog dialog = builder.create();
        /**
         * override onCancel method to callback cancel in case of a touch
         * outside of the dialog or the BACK key pressed
         */
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                response.putBoolean("didCancel", true);
                callback.invoke(response);
            }
        });
        dialog.show();
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void launchCamera(final ReadableMap options, final Callback callback) {
        if (mCallback != null) return;

        Activity activity = getCurrentActivity();
        if (activity == null) return;

        response = Arguments.createMap();

        if (options.hasKey("noData")) {
            noData = options.getBoolean("noData");
        }
        if (options.hasKey("maxWidth")) {
            maxWidth = options.getInt("maxWidth");
        }
        if (options.hasKey("maxHeight")) {
            maxHeight = options.getInt("maxHeight");
        }
        if (options.hasKey("quality")) {
            quality = (int)(options.getDouble("quality") * 100);
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(activity.getPackageManager()) == null) {
            response.putString("error", "error resolving activity");
            callback.invoke(response);
            return;
        }

        // we create a tmp file to save the result
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile;
        try {
            // Make sure the Pictures directory exists.
            path.mkdirs();
            imageFile = File.createTempFile("image_picker_capture_", ".jpg", path);
        } catch (IOException e) {
            e.printStackTrace();
            response.putString("error", e.getMessage());
            callback.invoke(response);
            return;
        }
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile));
        mCameraCaptureURI = Uri.fromFile(imageFile);
        mCallback = callback;
        activity.startActivityForResult(cameraIntent, REQUEST_LAUNCH_CAMERA);
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void launchImageLibrary(final ReadableMap options, final Callback callback) {
        if (mCallback != null) return;

        Activity activity = getCurrentActivity();
        if (activity == null) return;

        response = Arguments.createMap();

        if (options.hasKey("noData")) {
            noData = options.getBoolean("noData");
        }
        if (options.hasKey("maxWidth")) {
            maxWidth = options.getInt("maxWidth");
        }
        if (options.hasKey("maxHeight")) {
            maxHeight = options.getInt("maxHeight");
        }
        if (options.hasKey("quality")) {
            quality = (int)(options.getDouble("quality") * 100);
        }

        Intent libraryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        mCallback = callback;
        activity.startActivityForResult(libraryIntent, REQUEST_LAUNCH_IMAGE_LIBRARY);
    }

    @ReactMethod
    public void launchVideoLibrary(final Callback callback) {
        if (mCallback != null) return;

        Activity activity = getCurrentActivity();
        if (activity == null) return;

        response = Arguments.createMap();
        mCallback = callback;

        Log.d("Test", "launchVideoLibrary!!!");
        Intent videoIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        videoIntent.setType("video/*");

        try {
            activity.startActivityForResult(videoIntent, REQUEST_LAUNCH_VIDEO_LIBRARY);
        }
        catch(ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void shareIntent(final ReadableMap options, final Callback callback) {
        if (mCallback != null) return;

        Activity activity = getCurrentActivity();
        if (activity == null) return;

        Log.d("Test", "shareIntent!!!");
        response = Arguments.createMap();
        mCallback = callback;

        String subject = "subject";
        String body = "body";
        String mimeType = "mimeType"; // "text/plain", "image/*", "video/mp4"
        String filePath = "";
        Uri uri = null;

        if (options.hasKey("subject")) {
            subject = options.getString("subject");
        }
        if (options.hasKey("body")) {
            body = options.getString("body");
        }
        if (options.hasKey("mimeType")) {
            mimeType = options.getString("mimeType");
        }
        if (options.hasKey("uri")) {
            filePath = options.getString("uri");
            uri = Uri.parse(filePath);
        }

        // create share intent
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);

        // set intent type, // Set an explicit MIME data type.
        sharingIntent.setType(mimeType);

        // add content to share
        if(uri != null) sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);

        // create chooser
        try {
            activity.startActivityForResult(sharingIntent, REQUEST_SHARE);
        }
        catch(ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        //robustness code
        if (mCallback == null || (mCameraCaptureURI == null && requestCode == REQUEST_LAUNCH_CAMERA) ||
                (requestCode != REQUEST_LAUNCH_CAMERA &&
                        requestCode != REQUEST_LAUNCH_IMAGE_LIBRARY &&
                        requestCode != REQUEST_LAUNCH_VIDEO_LIBRARY &&
                        requestCode != REQUEST_SHARE)) {
            return;
        }

        WritableMap resultResponse = Arguments.createMap();
        resultResponse.merge(response);

        // user cancel
        if (resultCode != Activity.RESULT_OK) {
            resultResponse.putBoolean("didCancel", true);
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        // --- for share intent ---
        if(requestCode == REQUEST_SHARE) {
            Log.d("Share", "requestCode: REQUEST_SHARE... result received!!");
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        Uri uri = (requestCode == REQUEST_LAUNCH_CAMERA)
                ? mCameraCaptureURI
                : data.getData();

        String realPath = getRealPathFromURI(uri);
        if (realPath == null) {
            resultResponse.putString("error", "real path from uri is null");
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        // --- for VideoPicker ---
        if(requestCode == REQUEST_LAUNCH_VIDEO_LIBRARY) {
            resultResponse.putString("uri", realPath);

            // --- check file size ---
            File file = new File(realPath);
            long fileSize = file.length();
            Log.d("TEST", " fileSize=" + fileSize + ", " + fileSize/1024 + "KB, " + fileSize/1024/1024 + "MB");
            resultResponse.putDouble("fileSize", fileSize / 1024 / 1024);

            // --- check file format ---
            String mimeType = getMimeType(realPath);
            Log.d("TEST", " mimeType = " + mimeType);
            resultResponse.putString("mimeType", mimeType);

            // --- compress file ?!---
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        boolean isUrl = true;
        try {
            URL url = new URL(realPath);
        }
        catch (MalformedURLException e) {
            isUrl = false;
        }
        if (isUrl) {
            // @todo handle url as well (Facebook image, etc..)
            resultResponse.putString("uri", uri.toString());
            resultResponse.putString("urlPath", realPath);
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        try {
            ExifInterface exif = new ExifInterface(realPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            boolean isVertical = true ;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    isVertical = false ;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    isVertical = false ;
                    break;
            }
            resultResponse.putBoolean("isVertical", isVertical);
        } catch (IOException e) {
            e.printStackTrace();
            resultResponse.putString("error", e.getMessage());
            mCallback.invoke(resultResponse);
            mCallback = null;
            return;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap photo = BitmapFactory.decodeFile(realPath, options);
        int initialWidth = options.outWidth;
        int initialHeight = options.outHeight;

        // don't create a new file if contraint are respected
        if (((initialWidth < maxWidth && maxWidth > 0) || maxWidth == 0)
                && ((initialHeight < maxHeight && maxHeight > 0) || maxHeight == 0)
                && quality == 100) {
            resultResponse.putInt("width", initialWidth);
            resultResponse.putInt("height", initialHeight);
        } else {
            uri = getResizedImage(getRealPathFromURI(uri), initialWidth, initialHeight);
            realPath = getRealPathFromURI(uri);
            photo = BitmapFactory.decodeFile(realPath, options);
            resultResponse.putInt("width", options.outWidth);
            resultResponse.putInt("height", options.outHeight);
        }

        resultResponse.putString("uri", uri.toString());

        if (!noData) {
          resultResponse.putString("data", getBase64StringFromFile(realPath));
        }
        mCallback.invoke(resultResponse);
        mCallback = null;
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    // mooqoo: added file
    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    private String getRealPathFromURI(Uri uri) {
        String result;
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getReactApplicationContext().getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private String getBase64StringFromFile (String absoluteFilePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(absoluteFilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * Create a resized image to fill the maxWidth and maxHeight values and the
     * quality value
     *
     * @param realPath
     * @param initialWidth
     * @param initialHeight
     * @return uri of resized file
     */
    private Uri getResizedImage (final String realPath, final int initialWidth, final int initialHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;
        Bitmap photo = BitmapFactory.decodeFile(realPath, options);

        Bitmap scaledphoto = null;
        if (maxWidth == 0) {
            maxWidth  = initialWidth;
        }
        if (maxHeight == 0) {
            maxHeight = initialHeight;
        }
        double widthRatio = (double)maxWidth / initialWidth;
        double heightRatio = (double)maxHeight / initialHeight;

        double ratio = (widthRatio < heightRatio)
                ? widthRatio
                : heightRatio;

        int newWidth = (int)(initialWidth * ratio);
        int newHeight = (int)(initialHeight * ratio);

        scaledphoto = Bitmap.createScaledBitmap(photo, newWidth, newHeight, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        scaledphoto.compress(Bitmap.CompressFormat.JPEG, quality, bytes);
        String filname = UUID.randomUUID().toString();
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File f = new File(path, filname +".jpg");
        try {
            // Make sure the Pictures directory exists.
            path.mkdirs();

            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream fo;
        try {
            fo = new FileOutputStream(f);
            try {
                fo.write(bytes.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // recycle to avoid java.lang.OutOfMemoryError
        if (photo != null) {
            photo.recycle();
            photo = null;
        }
        return Uri.fromFile(f);
    }
}
