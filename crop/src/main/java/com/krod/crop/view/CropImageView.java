package com.krod.crop.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.krod.crop.R;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * <p/>
 * This class adds crop feature, methods to draw crop guidelines, and keep image in correct state.
 * Also it extends parent class methods to add checks for scale; animating zoom in/out.
 */
public class CropImageView extends com.krod.crop.view.TransformImageView {

    public static final int DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = 500;
    public static final float DEFAULT_MAX_SCALE_MULTIPLIER = 10.0f;

    private final RectF mCropRect = new RectF();

    private final Matrix mTempMatrix = new Matrix();

    private float mMaxScaleMultiplier = DEFAULT_MAX_SCALE_MULTIPLIER;

    private Runnable mWrapCropBoundsRunnable, mZoomImageToPositionRunnable = null;

    private float mMaxScale, mMinScale;
    private long mImageToWrapCropBoundsAnimDuration = DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION;
    private float mDiffX = 0f, mDiffY = 0f;
    private boolean isScaleIng = false;
    private boolean isEnableWrap = true;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CropImageView);
        if (ta != null && ta.length() > 0) {
            isEnableWrap = ta.getBoolean(R.styleable.CropImageView_wrapEnable, true);
            ta.recycle();
        }
    }

    public void setIsEnableWrap(boolean isEnableWrap) {
        this.isEnableWrap = isEnableWrap;
    }

    /**
     * This method crops part of image that fills the crop bounds.
     * <p/>
     * First image is downscaled if max size was set and if resulting image is larger that max size.
     * Then image is rotated accordingly.
     * Finally new Bitmap object is created and returned.
     *
     * @return - cropped Bitmap object or null if current Bitmap is invalid or image rectangle is empty.
     */
    @Nullable
    public Bitmap cropImage() {
        setDrawingCacheEnabled(true);
        Bitmap viewBitmap = getDrawingCache();
        return Bitmap.createBitmap(viewBitmap, (viewBitmap.getWidth() - mMaxResultImageSizeX) / 2, (viewBitmap.getHeight() - mMaxResultImageSizeY) / 2, mMaxResultImageSizeX, mMaxResultImageSizeY);
    }

    /**
     * @return - maximum scale value for current image and crop ratio
     */
    public float getMaxScale() {
        return mMaxScale;
    }

    /**
     * @return - minimum scale value for current image and crop ratio
     */
    public float getMinScale() {
        return mMinScale;
    }

    /**
     * This method sets animation duration for image to wrap the crop bounds
     *
     * @param imageToWrapCropBoundsAnimDuration - duration in milliseconds
     */
    public void setImageToWrapCropBoundsAnimDuration(@IntRange(from = 100) long imageToWrapCropBoundsAnimDuration) {
        if (imageToWrapCropBoundsAnimDuration > 0) {
            mImageToWrapCropBoundsAnimDuration = imageToWrapCropBoundsAnimDuration;
        } else {
            throw new IllegalArgumentException("Animation duration cannot be negative value.");
        }
    }

    /**
     * This method sets multiplier that is used to calculate max image scale from min image scale.
     *
     * @param maxScaleMultiplier - (minScale * maxScaleMultiplier) = maxScale
     */
    public void setMaxScaleMultiplier(float maxScaleMultiplier) {
        mMaxScaleMultiplier = maxScaleMultiplier;
    }

    /**
     * This method scales image down for given value related to image center.
     */
    public void zoomOutImage(float deltaScale) {
        zoomOutImage(deltaScale, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method scales image down for given value related given coords (x, y).
     */
    public void zoomOutImage(float scale, float centerX, float centerY) {
        if (scale >= getMinScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY);
        }
    }

    /**
     * This method scales image up for given value related to image center.
     */
    public void zoomInImage(float deltaScale) {
        zoomInImage(deltaScale, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method scales image up for given value related to given coords (x, y).
     */
    public void zoomInImage(float scale, float centerX, float centerY) {
        if (scale <= getMaxScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY);
        }
    }

    /**
     * This method changes image scale for given value related to point (px, py) but only if
     * resulting scale is in min/max bounds.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    public void postScale(float deltaScale, float px, float py) {
        if (deltaScale > 1 && getCurrentScale() * deltaScale <= getMaxScale()) {
            super.postScale(deltaScale, px, py);
        } else if (deltaScale < 1 && getCurrentScale() * deltaScale >= getMinScale()) {
            super.postScale(deltaScale, px, py);
        }
    }

    /**
     * This method rotates image for given angle related to the image center.
     *
     * @param deltaAngle - angle to rotate
     */
    public void postRotate(float deltaAngle) {
        postRotate(deltaAngle, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method cancels all current Runnable objects that represent animations.
     */
    public void cancelAllAnimations() {
        removeCallbacks(mWrapCropBoundsRunnable);
        removeCallbacks(mZoomImageToPositionRunnable);
    }

    /**
     * If image doesn't fill the crop bounds it must be translated and scaled properly to fill those.
     * <p/>
     * Therefore this method calculates delta X, Y and scale values and passes them to the
     * {@link WrapCropBoundsRunnable} which animates image.
     * Scale value must be calculated only if image won't fill the crop bounds after it's translated to the
     * crop bounds rectangle center. Using temporary variables this method checks this case.
     */
    public void setImageToWrapCropBounds() {
        if (isEnableWrap && !isImageWrapCropBounds() && !isScaleIng) {

            float currentX = mCurrentImageCenter[0];
            float currentY = mCurrentImageCenter[1];
            float currentScale = getCurrentScale();

            float deltaX = mCropRect.centerX() - currentX;
            float deltaY = mCropRect.centerY() - currentY;
            if (Math.abs(deltaX) > mDiffX) {
                deltaX = deltaX > 0 ? deltaX - mDiffX : deltaX + mDiffX;
            } else {
                deltaX = 0;
            }
            if (Math.abs(deltaY) > mDiffY) {
                deltaY = deltaY > 0 ? deltaY - mDiffY : deltaY + mDiffY;
            } else {
                deltaY = 0;
            }
            float deltaScale = 0;

            mTempMatrix.reset();
            mTempMatrix.setTranslate(deltaX, deltaY);

            final float[] tempCurrentImageCorners = Arrays.copyOf(mCurrentImageCorners, mCurrentImageCorners.length);
            mTempMatrix.mapPoints(tempCurrentImageCorners);

            boolean willImageWrapCropBoundsAfterTranslate = isImageWrapCropBounds(tempCurrentImageCorners);

            if (willImageWrapCropBoundsAfterTranslate) {
                final float[] imageIndents = calculateImageIndents();
                deltaX = -(imageIndents[0] + imageIndents[2]);
                deltaY = -(imageIndents[1] + imageIndents[3]);
            } else {
                RectF tempCropRect = new RectF(mCropRect);
                mTempMatrix.reset();
                mTempMatrix.setRotate(getCurrentAngle());
                mTempMatrix.mapRect(tempCropRect);

                final float[] currentImageSides = com.krod.crop.util.RectUtils.getRectSidesFromCorners(mCurrentImageCorners);

                deltaScale = Math.max(tempCropRect.width() / currentImageSides[0],
                        tempCropRect.height() / currentImageSides[1]);
                // Ugly but there are always couple pixels that want to hide because of all these calculations
                //deltaScale *= 1.00;
                deltaScale = deltaScale * currentScale - currentScale;
            }

            post(mWrapCropBoundsRunnable = new WrapCropBoundsRunnable(
                    CropImageView.this, mImageToWrapCropBoundsAnimDuration, currentX, currentY, deltaX, deltaY,
                    currentScale, deltaScale, willImageWrapCropBoundsAfterTranslate));
        }
    }

    /**
     * First, un-rotate image and crop rectangles (make image rectangle axis-aligned).
     * Second, calculate deltas between those rectangles sides.
     * Third, depending on delta (its sign) put them or zero inside an array.
     * Fourth, using Matrix, rotate back those points (indents).
     *
     * @return - the float array of image indents (4 floats) - in this order [left, top, right, bottom]
     */
    private float[] calculateImageIndents() {
        mTempMatrix.reset();
        mTempMatrix.setRotate(-getCurrentAngle());

        float[] unrotatedImageCorners = Arrays.copyOf(mCurrentImageCorners, mCurrentImageCorners.length);
        float[] unrotatedCropBoundsCorners = com.krod.crop.util.RectUtils.getCornersFromRect(mCropRect);

        mTempMatrix.mapPoints(unrotatedImageCorners);
        mTempMatrix.mapPoints(unrotatedCropBoundsCorners);

        RectF unrotatedImageRect = com.krod.crop.util.RectUtils.trapToRect(unrotatedImageCorners);
        RectF unrotatedCropRect = com.krod.crop.util.RectUtils.trapToRect(unrotatedCropBoundsCorners);

        float deltaLeft = unrotatedImageRect.left - unrotatedCropRect.left;
        float deltaTop = unrotatedImageRect.top - unrotatedCropRect.top;
        float deltaRight = unrotatedImageRect.right - unrotatedCropRect.right;
        float deltaBottom = unrotatedImageRect.bottom - unrotatedCropRect.bottom;

        float indents[] = new float[4];
        indents[0] = (deltaLeft > 0) ? deltaLeft : 0;
        indents[1] = (deltaTop > 0) ? deltaTop : 0;
        indents[2] = (deltaRight < 0) ? deltaRight : 0;
        indents[3] = (deltaBottom < 0) ? deltaBottom : 0;

        mTempMatrix.reset();
        mTempMatrix.setRotate(getCurrentAngle());
        mTempMatrix.mapPoints(indents);

        return indents;
    }

    /**
     * When image is laid out it must be centered properly to fit current crop bounds.
     */
    @Override
    protected void onImageLaidOut() {
        super.onImageLaidOut();
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();

        setupInitialImagePosition(drawableWidth, drawableHeight);
        setImageMatrix(mCurrentImageMatrix);

        if (mTransformImageListener != null) {
            mTransformImageListener.onScale(getCurrentScale());
            mTransformImageListener.onRotate(getCurrentAngle());
        }
    }

    /**
     * This method checks whether current image fills the crop bounds.
     */
    protected boolean isImageWrapCropBounds() {
        return isImageWrapCropBounds(mCurrentImageCorners);
    }

    /**
     * This methods checks whether a rectangle that is represented as 4 corner points (8 floats)
     * fills the crop bounds rectangle.
     *
     * @param imageCorners - corners of a rectangle
     * @return - true if it wraps crop bounds, false - otherwise
     */
    protected boolean isImageWrapCropBounds(float[] imageCorners) {
        mTempMatrix.reset();
        mTempMatrix.setRotate(-getCurrentAngle());

        float[] unrotatedImageCorners = Arrays.copyOf(imageCorners, imageCorners.length);
        mTempMatrix.mapPoints(unrotatedImageCorners);

        float[] unrotatedCropBoundsCorners = com.krod.crop.util.RectUtils.getCornersFromRect(mCropRect);
        mTempMatrix.mapPoints(unrotatedCropBoundsCorners);

        return com.krod.crop.util.RectUtils.trapToRect(unrotatedImageCorners).contains(com.krod.crop.util.RectUtils.trapToRect(unrotatedCropBoundsCorners));
    }

    /**
     * This method changes image scale (animating zoom for given duration), related to given center (x,y).
     *
     * @param scale      - target scale
     * @param centerX    - scale center X
     * @param centerY    - scale center Y
     * @param durationMs - zoom animation duration
     */
    protected void zoomImageToPosition(float scale, float centerX, float centerY, long durationMs) {
        if (scale > getMaxScale()) {
            scale = getMaxScale();
        }

        final float oldScale = getCurrentScale();
        final float deltaScale = scale - oldScale;

        post(mZoomImageToPositionRunnable = new ZoomImageToPosition(CropImageView.this,
                durationMs, oldScale, deltaScale, centerX, centerY));
    }

    /**
     * This method calculates initial image position so it fits the crop bounds properly.
     * Then it sets those values to the current image matrix.
     *
     * @param drawableWidth  - original image width
     * @param drawableHeight - original image height
     */
    private void setupInitialImagePosition(float drawableWidth, float drawableHeight) {
        float widthScale = (float)mMaxResultImageSizeX / drawableWidth;
        float heightScale = (float)mMaxResultImageSizeY / drawableHeight;

        float halfDiffX;
        float halfDiffY;
        if (heightScale > widthScale) {
            halfDiffY = (mThisHeight - mMaxResultImageSizeY) / 2;
            halfDiffX = (mThisWidth - heightScale * drawableWidth) / 2;
            mCropRect.set(halfDiffX, halfDiffY, heightScale * drawableWidth + halfDiffX, mMaxResultImageSizeY + halfDiffY);
            mDiffX = (heightScale * drawableWidth - mMaxResultImageSizeX) / 2;
        } else {
            halfDiffX = (mThisWidth - mMaxResultImageSizeX) / 2;
            halfDiffY = (mThisHeight - widthScale * drawableHeight) / 2;
            mDiffY = (widthScale * drawableHeight - mMaxResultImageSizeY) / 2;
            mCropRect.set(halfDiffX, halfDiffY, mMaxResultImageSizeX + halfDiffX, widthScale * drawableHeight + halfDiffY);

        }
        float tempMinScale = Math.max(heightScale, widthScale);
        mMaxScale = tempMinScale * mMaxScaleMultiplier;

        float tw = (mCropRect.width() - drawableWidth * tempMinScale) / 2.0f + mCropRect.left;
        float th = (mCropRect.height() - drawableHeight * tempMinScale) / 2.0f + mCropRect.top;
        mMinScale = tempMinScale / 5;
        mCurrentImageMatrix.reset();
        mCurrentImageMatrix.postScale(tempMinScale, tempMinScale);
        mCurrentImageMatrix.postTranslate(tw, th);
    }


    /**
     * This Runnable is used to animate an image so it fills the crop bounds entirely.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie {@link #cancelAllAnimations()} method
     * or when certain conditions inside {@link WrapCropBoundsRunnable#run()} method are triggered.
     */
    private static class WrapCropBoundsRunnable implements Runnable {

        private final WeakReference<CropImageView> mCropImageView;

        private final long mDurationMs, mStartTime;
        private final float mOldX, mOldY;
        private final float mCenterDiffX, mCenterDiffY;
        private final float mOldScale;
        private final float mDeltaScale;
        private final boolean mWillBeImageInBoundsAfterTranslate;

        public WrapCropBoundsRunnable(CropImageView cropImageView,
                                      long durationMs,
                                      float oldX, float oldY,
                                      float centerDiffX, float centerDiffY,
                                      float oldScale, float deltaScale,
                                      boolean willBeImageInBoundsAfterTranslate) {

            mCropImageView = new WeakReference<>(cropImageView);

            mDurationMs = durationMs;
            mStartTime = System.currentTimeMillis();
            mOldX = oldX;
            mOldY = oldY;
            mCenterDiffX = centerDiffX;
            mCenterDiffY = centerDiffY;
            mOldScale = oldScale;
            mDeltaScale = deltaScale;
            mWillBeImageInBoundsAfterTranslate = willBeImageInBoundsAfterTranslate;
        }

        @Override
        public void run() {
            CropImageView cropImageView = mCropImageView.get();
            if (cropImageView == null) {
                return;
            }

            long now = System.currentTimeMillis();
            float currentMs = Math.min(mDurationMs, now - mStartTime);

            float newX = com.krod.crop.util.CubicEasing.easeOut(currentMs, 0, mCenterDiffX, mDurationMs);
            float newY = com.krod.crop.util.CubicEasing.easeOut(currentMs, 0, mCenterDiffY, mDurationMs);
            float newScale = com.krod.crop.util.CubicEasing.easeInOut(currentMs, 0, mDeltaScale, mDurationMs);

            if (currentMs < mDurationMs) {
                cropImageView.postTranslate(newX - (cropImageView.mCurrentImageCenter[0] - mOldX), newY - (cropImageView.mCurrentImageCenter[1] - mOldY));//该语句标示滑动图片是否可以脱离边界
                if (!mWillBeImageInBoundsAfterTranslate && newScale != 0) {
                    cropImageView.zoomInImage(mOldScale + newScale, cropImageView.mCropRect.centerX(), cropImageView.mCropRect.centerY());
                }
                if (!cropImageView.isImageWrapCropBounds()) {
                    cropImageView.post(this);
                }
            }
        }
    }

    /**
     * 中文注释：缩放操作
     * This Runnable is used to animate an image zoom.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie {@link #cancelAllAnimations()} method
     * or when certain conditions inside {@link ZoomImageToPosition#run()} method are triggered.
     */
    private static class ZoomImageToPosition implements Runnable {

        private final WeakReference<CropImageView> mCropImageView;

        private final long mDurationMs, mStartTime;
        private final float mOldScale;
        private final float mDeltaScale;
        private final float mDestX;
        private final float mDestY;

        public ZoomImageToPosition(CropImageView cropImageView,
                                   long durationMs,
                                   float oldScale, float deltaScale,
                                   float destX, float destY) {

            mCropImageView = new WeakReference<>(cropImageView);

            mStartTime = System.currentTimeMillis();
            mDurationMs = durationMs;
            mOldScale = oldScale;
            mDeltaScale = deltaScale;
            mDestX = destX;
            mDestY = destY;
        }

        @Override
        public void run() {
            CropImageView cropImageView = mCropImageView.get();
            if (cropImageView == null) {
                return;
            }

            long now = System.currentTimeMillis();
            float currentMs = Math.min(mDurationMs, now - mStartTime);
            float newScale = com.krod.crop.util.CubicEasing.easeInOut(currentMs, 0, mDeltaScale, mDurationMs);

            if (currentMs < mDurationMs) {
                cropImageView.zoomInImage(mOldScale + newScale, mDestX, mDestY);
                cropImageView.post(this);
            } else {
                cropImageView.setIsScaleIng(false);
                cropImageView.setImageToWrapCropBounds();
            }
        }

    }

    public void setIsScaleIng(boolean isScaleIng) {
        this.isScaleIng = isScaleIng;
    }
}
