package com.example.myapplication;

import android.view.TextureView;
import android.content.Context;
import android.util.AttributeSet;


public class AutoFitTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            // Calculate the actual ratios of the width and height
            double ratio = (double) mRatioWidth / mRatioHeight;
            double viewRatio = (double) width / height;

            if (viewRatio > ratio) {
                // View is wider than the camera ratio; calculate a reduced width to fit the view's height
                width = (int) (height * ratio);
            } else if (viewRatio < ratio) {
                // View is taller than the camera ratio; calculate a reduced height to fit the view's width
                height = (int) (width / ratio);
            }
            // The dimensions are set with respect to the aspect ratio
            setMeasuredDimension(width, height);
        }
    }



}
