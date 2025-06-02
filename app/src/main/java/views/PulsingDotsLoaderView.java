package com.LDGAMES.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.LDGAMES.R;

public class PulsingDotsLoaderView extends View {

    private static final int NUM_DOTS = 3;
    private static final float DOT_RADIUS_DP = 6f;
    private static final float DOT_SPACING_DP = 12f;
    private static final long ANIMATION_DURATION = 1000; // ms
    private static final float MAX_SCALE = 1.0f;
    private static final float MIN_SCALE = 0.5f;

    private Paint dotPaint;
    private float dotRadiusPx;
    private float dotSpacingPx;
    private float[] dotScales = new float[NUM_DOTS];
    private ValueAnimator animator;

    public PulsingDotsLoaderView(Context context) {
        super(context);
        init(context);
    }

    public PulsingDotsLoaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PulsingDotsLoaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dotRadiusPx = dpToPx(DOT_RADIUS_DP);
        dotSpacingPx = dpToPx(DOT_SPACING_DP);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        try {
            // Attempt to get colorPrimary from theme attribute
            int colorAttr = R.attr.colorPrimary;
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(colorAttr, typedValue, true);
            dotPaint.setColor(typedValue.data);
        } catch (Exception e) {
            // Fallback color if attribute resolution fails
            dotPaint.setColor(Color.parseColor("#FF6200EE")); // Default purple
        }

        // Initialize scales
        for (int i = 0; i < NUM_DOTS; i++) {
            dotScales[i] = MIN_SCALE;
        }

        // Setup animator
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            updateDotScales(fraction);
            invalidate(); // Redraw the view
        });
    }

    private void updateDotScales(float fraction) {
        for (int i = 0; i < NUM_DOTS; i++) {
            // Calculate phase for each dot
            float phase = (fraction + (float) i / NUM_DOTS) % 1.0f;
            // Use a sine wave or similar function for smooth pulsing
            // Map phase [0, 1] to scale [MIN_SCALE, MAX_SCALE] and back
            float scaleFactor = (float) (Math.sin(phase * Math.PI * 2) + 1) / 2; // Range [0, 1]
            dotScales[i] = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * scaleFactor;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float desiredWidth = (NUM_DOTS * 2 * dotRadiusPx) + ((NUM_DOTS - 1) * dotSpacingPx);
        float desiredHeight = 2 * dotRadiusPx * MAX_SCALE; // Max height based on max scale

        int width = resolveSize((int) Math.ceil(desiredWidth), widthMeasureSpec);
        int height = resolveSize((int) Math.ceil(desiredHeight), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float totalWidth = (NUM_DOTS * 2 * dotRadiusPx) + ((NUM_DOTS - 1) * dotSpacingPx);
        float startX = (getWidth() - totalWidth) / 2f + dotRadiusPx;
        float centerY = getHeight() / 2f;

        for (int i = 0; i < NUM_DOTS; i++) {
            float currentRadius = dotRadiusPx * dotScales[i];
            float currentX = startX + i * (2 * dotRadiusPx + dotSpacingPx);
            canvas.drawCircle(currentX, centerY, currentRadius, dotPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
