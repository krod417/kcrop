package com.krod.yxcrop;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import com.krod.yxcrop.util.BitmapLoadUtils;
import com.krod.yxcrop.view.CropImageView;
import com.krod.yxcrop.view.GestureCropImageView;
import com.krod.yxcrop.view.OverlayView;

import java.io.OutputStream;

/**
 * @author jian.wj
 * @date 16-3-17
 * Copyright 2014 NetEase. All rights reserved.
 */
public class CropActivity extends AppCompatActivity{
    private static final String TAG = "CropActivity";
    public static final int DEFAULT_COMPRESS_QUALITY = 90;
    public static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
    GestureCropImageView mGestureCropImageView;
    OverlayView mOverlayView;
    LinearLayout llMain;

    private Uri mOutputUri;
    private int mCompressQuality = DEFAULT_COMPRESS_QUALITY;
    private Bitmap.CompressFormat mCompressFormat = DEFAULT_COMPRESS_FORMAT;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.ucrop_activity_crop);
        mGestureCropImageView = (GestureCropImageView) findViewById(R.id.image_view_crop);
        mOverlayView = (OverlayView) findViewById(R.id.view_overlay);
        llMain = (LinearLayout) findViewById(R.id.llMain);
        findViewById(R.id.tvSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropAndSaveImage();
            }
        });
        mGestureCropImageView.setScaleEnabled(true);
        mGestureCropImageView.setRotateEnabled(false);
        setImageData(getIntent());
    }

    /**
     * This method extracts all data from the incoming intent and setups views properly.
     */
    private void setImageData(@NonNull Intent intent) {
        llMain.setBackgroundColor(intent.getIntExtra(UCrop.EXTRA_BACKGROUND_COLOR, Color.WHITE));
        mGestureCropImageView.setIsEnableWrap(intent.getBooleanExtra(UCrop.EXTRA_WRAPENABLE, true));
        mOverlayView.setIsShowFrame(intent.getBooleanExtra(UCrop.EXTRA_SHOWFRAME, false));
        mOverlayView.setFrameColor(intent.getIntExtra(UCrop.EXTRA_FRAMECOLOR, Color.WHITE));
        Uri inputUri = intent.getParcelableExtra(UCrop.EXTRA_INPUT_URI);
        mOutputUri = intent.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI);
        processOptions();
        int maxSizeX = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_X, 0);
        int maxSizeY = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_Y, 0);

        if (maxSizeX > 0 && maxSizeY > 0) {
            mGestureCropImageView.setMaxResultImageSizeX(maxSizeX);
            mGestureCropImageView.setMaxResultImageSizeY(maxSizeY);
            mOverlayView.setCropWidthAndHeight(maxSizeX, maxSizeY);
        } else {
            setResultException(new RuntimeException("EXTRA_MAX_SIZE_X and EXTRA_MAX_SIZE_Y must be greater than 0"));
            Log.w(TAG, "EXTRA_MAX_SIZE_X and EXTRA_MAX_SIZE_Y must be greater than 0");
        }
        if (inputUri != null && mOutputUri != null) {
            try {
                mGestureCropImageView.setImageUri(inputUri);
            } catch (Exception e) {
                setResultException(e);
                finish();
            }
        } else {
            setResultException(new NullPointerException(getString(R.string.ucrop_error_input_data_is_absent)));
            finish();
        }

    }

    @SuppressWarnings("deprecation")
    private void processOptions() {
        // Crop image view options
        mGestureCropImageView.setMaxScaleMultiplier(CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER);
        mGestureCropImageView.setImageToWrapCropBoundsAnimDuration(CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION);

    }

    private void cropAndSaveImage() {
        OutputStream outputStream = null;
        try {
            final Bitmap croppedBitmap = mGestureCropImageView.cropImage();
            if (croppedBitmap != null) {
                outputStream = getContentResolver().openOutputStream(mOutputUri);
                croppedBitmap.compress(mCompressFormat, mCompressQuality, outputStream);
                croppedBitmap.recycle();

                setResultUri(mOutputUri);
                finish();
            } else {
                setResultException(new NullPointerException("CropImageView.cropImage() returned null."));
            }
        } catch (Exception e) {
            setResultException(e);
            finish();
        } finally {
            BitmapLoadUtils.close(outputStream);
        }
    }

    private void setResultUri(Uri uri) {
        setResult(RESULT_OK, new Intent()
                .putExtra(UCrop.EXTRA_OUTPUT_URI, uri));
    }

    private void setResultException(Throwable throwable) {
        setResult(UCrop.RESULT_ERROR, new Intent().putExtra(UCrop.EXTRA_ERROR, throwable));
    }

}
