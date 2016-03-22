package com.krod.crop.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import com.krod.crop.R;

/**
 * Created by jian.wj
 * <p/>
 * This view is used for drawing the overlay on top of the image.
 */
public class OverlayView extends View {


    private final RectF mCropViewRect = new RectF();
    //protected int mThisWidth, mThisHeight;
    private float cropWidth, cropHeight;
    private boolean isShowFrame = false;

    private int frameColor = Color.WHITE;

    public OverlayView(Context context) {
        this(context, null);
    }

    public OverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.OverlayView);
        if (ta != null && ta.length() > 0) {
            isShowFrame = ta.getBoolean(R.styleable.OverlayView_showFrame, true);
            frameColor = ta.getColor(R.styleable.OverlayView_frameColor, Color.WHITE);
            ta.recycle();
        }
    }

    public void setCropWidthAndHeight(float cropWidth, float cropHeight) {
        this.cropHeight = cropHeight / 2;
        this.cropWidth = cropWidth / 2;
    }

    public void setIsShowFrame(boolean isShowFrame) {
        this.isShowFrame = isShowFrame;
    }

    public void setFrameColor(int frameColor) {
        this.frameColor = frameColor;
    }

    /**
     * 当要考虑padding值时需要通过该方法获取view的高宽
     */
//    @Override
//    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        super.onLayout(changed, left, top, right, bottom);
//        if (changed) {
//            left = getPaddingLeft();
//            top = getPaddingTop();
//            right = getWidth() - getPaddingRight();
//            bottom = getHeight() - getPaddingBottom();
//            mThisWidth = right - left;
//            mThisHeight = bottom - top;
//        }
//    }


    protected void init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
    }

    /**
     * Along with image there are dimmed layer, crop bounds and crop guidelines that must be drawn.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        drawDimmedLayer(canvas);
    }

    /**
     * This method draws dimmed area around the crop bounds.
     *
     * @param canvas - valid canvas object
     */
    protected void drawDimmedLayer(@NonNull Canvas canvas) {
        float centerX = (float)getWidth() / 2;
        float centerY = (float)getHeight() / 2;
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0,
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        canvas.drawARGB(0, 0, 0, 0);
        Paint p = new Paint();
        p.setDither(true);
        mCropViewRect.set(centerX - cropWidth, centerY - cropHeight, centerX + cropWidth, centerY + cropHeight);
        canvas.clipRect(mCropViewRect, Region.Op.DIFFERENCE);
        p.setColor(Color.parseColor("#bb000000"));
        canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        if (isShowFrame) {
            p.setColor(frameColor);
            p.setStrokeWidth(2);
            p.setStyle(Paint.Style.STROKE);
            canvas.drawRect(mCropViewRect, p);
        }
    }


}
