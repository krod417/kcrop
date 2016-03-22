package com.krod.crop.sample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.krod.crop.KCrop;

import java.io.File;

/**
 * @author jian.wj
 * @date 16-3-21
 * Copyright 2014 NetEase. All rights reserved.
 */
public class YixinActivity extends BaseActivity {
    private final static String TAG = "YixinActivity";
    private static final int REQUEST_SELECT_PICTURE = 0x01;
    private EditText etResultWidth, etResultHeight;
    private CheckBox cbShowFrame, cbWrapEnable;
    private static final String SAMPLE_CROPPED_IMAGE_NAME = "SampleCropImage.jpeg";
    private Uri destinationUri;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        destinationUri = Uri.fromFile(new File(getCacheDir(), SAMPLE_CROPPED_IMAGE_NAME));
        etResultWidth = (EditText) findViewById(R.id.etResultWidth);
        etResultHeight = (EditText) findViewById(R.id.etResultHeight);
        cbShowFrame = (CheckBox) findViewById(R.id.cbShowFrame);
        cbWrapEnable = (CheckBox) findViewById(R.id.cbWrapEnable);
        findViewById(R.id.button_crop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFromGallery();
            }
        });
    }


    private void pickFromGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN // Permission was added in API Level 16
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
                    getString(R.string.permission_read_storage_rationale),
                    REQUEST_STORAGE_READ_ACCESS_PERMISSION);
        } else {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.label_select_picture)), REQUEST_SELECT_PICTURE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_SELECT_PICTURE) {
                final Uri selectedUri = data.getData();
                if (selectedUri != null) {
                    startCropActivity(data.getData());
                } else {
                    Toast.makeText(YixinActivity.this, R.string.toast_cannot_retrieve_selected_image, Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == KCrop.REQUEST_CROP) {
                handleCropResult(data);
            }
        }
        if (resultCode == KCrop.RESULT_ERROR) {
            handleCropError(data);
        }
    }

    private void startCropActivity(@NonNull Uri uri) {
        KCrop kCrop = KCrop.of(uri, destinationUri);
        try {
            int resultWidth = Integer.valueOf(etResultWidth.getText().toString().trim());
            int resultHeight = Integer.valueOf(etResultHeight.getText().toString().trim());
            if (resultWidth > 0 && resultHeight > 0) {
                kCrop = kCrop.withMaxResultSize(resultWidth, resultHeight);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number please", e);
        }
        kCrop.setBackgroundColor(Color.WHITE);
        //以下属性可以配置出微信版头像截取

        boolean wrapEnable = cbWrapEnable.isChecked();
        boolean showFrame = cbShowFrame.isChecked();
        kCrop.setWrapenable(wrapEnable);
        if (!wrapEnable) {
            kCrop.setBackgroundColor(Color.parseColor("#000000"));
            kCrop.setShowFrame(showFrame);
            if (showFrame) {
                kCrop.setFrameColor(Color.WHITE);
            }
        }
        kCrop.start(YixinActivity.this);
    }

    private void handleCropResult(@NonNull Intent result) {
        final Uri resultUri = KCrop.getOutput(result);
        if (resultUri != null) {
            ResultActivity.startWithUri(YixinActivity.this, resultUri);
        } else {
            Toast.makeText(YixinActivity.this, R.string.toast_cannot_retrieve_cropped_image, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void handleCropError(@NonNull Intent result) {
        final Throwable cropError = KCrop.getError(result);
        if (cropError != null) {
            Log.e(TAG, "handleCropError: ", cropError);
            Toast.makeText(YixinActivity.this, cropError.getMessage(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(YixinActivity.this, R.string.toast_unexpected_error, Toast.LENGTH_SHORT).show();
        }
    }
}
