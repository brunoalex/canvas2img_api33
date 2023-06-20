package com.rodrigograca.canvas2image;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public class Canvas2ImagePlugin extends CordovaPlugin {
    public static final String ACTION = "saveImageDataToLibrary";
    public static final int WRITE_PERM_REQUEST_CODE = 1;
    private final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private CallbackContext callbackContext;
    private String format;
    private Bitmap bmp;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.format = args.optString(1);

        if (action.equals(ACTION)) {
            String base64 = args.optString(0);
            if (base64.equals(""))
                callbackContext.error("Missing base64 string");

            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (bmp == null) {
                callbackContext.error("The image could not be decoded");
            } else {
                this.bmp = bmp;
                this.callbackContext = callbackContext;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    // For Android 11 and above, directly save the photo
                    savePhoto();
                } else {
                    // For Android 10 and below, check and request permission
                    askPermissionAndSave();
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void askPermissionAndSave() {
        if (PermissionHelper.hasPermission(this, WRITE_EXTERNAL_STORAGE)) {
            Log.d("SaveImage", "Permissions already granted, or Android version is lower than 6");
            savePhoto();
        } else {
            Log.d("SaveImage", "Requesting permissions for WRITE_EXTERNAL_STORAGE");
            PermissionHelper.requestPermission(this, WRITE_PERM_REQUEST_CODE, WRITE_EXTERNAL_STORAGE);
        }
    }

    private void savePhoto() {
        Uri imageUri = null;

        try {
            ContentResolver contentResolver = this.cordova.getContext().getContentResolver();

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "c2i_" + System.currentTimeMillis() + (this.format.equals("png") ? ".png" : ".jpg"));
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, this.format.equals("png") ? "image/png" : "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);


            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            OutputStream out = contentResolver.openOutputStream(imageUri);

            this.bmp.compress(this.format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            Log.e("Canvas2ImagePlugin", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("Canvas2ImagePlugin", "IO exception: " + e.toString());
        } catch (NullPointerException e) {
            Log.e("Canvas2ImagePlugin", "Null pointer exception: " + e.toString());
        }

        if (imageUri == null) {
            callbackContext.error("Error while saving image");
        } else {
            scanPhoto(imageUri);
            callbackContext.success(imageUri.toString());
        }
    }

    private void scanPhoto(Uri imageUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == WRITE_PERM_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            savePhoto();
        } else {
            callbackContext.error("Permission denied");
        }
    }
}
