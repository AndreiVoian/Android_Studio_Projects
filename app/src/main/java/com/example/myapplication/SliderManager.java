package com.example.myapplication;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

public class SliderManager {
    private SeekBar slider;
    private Handler resetHandler = new Handler();
    private int defaultValue; // Default value for the slider
    private SliderChangeListener sliderChangeListener;

    public interface SliderChangeListener {
        void onSliderChanged(int value, SliderType type);
    }

    public enum SliderType {
        ZOOM,
        BRIGHTNESS
    }

    public SliderManager(SeekBar slider, int defaultValue, SliderChangeListener listener) {
        this.slider = slider;
        this.defaultValue = defaultValue;
        this.sliderChangeListener = listener;
        setupSlider();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSlider() {
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sliderChangeListener.onSliderChanged(progress, getSliderType());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }
        });

        slider.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    resetHandler.postDelayed(() -> resetSlider(), 2000);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    resetHandler.removeCallbacksAndMessages(null);
                }
                return false;
            }
        });
    }

    public void resetSlider() {
        slider.setProgress(defaultValue);
        sliderChangeListener.onSliderChanged(defaultValue, getSliderType());
    }

    private SliderType getSliderType() {
        if (slider.getId() == R.id.zoom_seekbar) {
            return SliderType.ZOOM;
        } else if (slider.getId() == R.id.exposure_seekbar) {
            return SliderType.BRIGHTNESS;
        } else {
            throw new IllegalStateException("Unknown slider type.");
        }
    }

    public void setMaxValue(int maxValue) {
        slider.setMax(maxValue);
    }
}
