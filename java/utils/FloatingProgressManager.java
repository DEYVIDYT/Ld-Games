package com.LDGAMES.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView; // Use CardView if the root is MaterialCardView

import com.LDGAMES.R;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

/**
 * Singleton manager for the floating progress indicator.
 * Now includes drag functionality.
 */
public class FloatingProgressManager {

    private static final String TAG = "FloatingProgressManager";
    private static FloatingProgressManager instance;
    private MaterialCardView progressView;
    private TextView tvTitle;
    private TextView tvApiStatus;
    private ProgressBar progressBar;
    private TextView tvDetails;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isVisible = false;

    // Variables for dragging
    private float dX, dY;
    private float initialX, initialY;

    private FloatingProgressManager() {}

    public static synchronized FloatingProgressManager getInstance() {
        if (instance == null) {
            instance = new FloatingProgressManager();
        }
        return instance;
    }

    /**
     * Initializes the manager with the view from the activity.
     * Should be called once, preferably in MainActivity's onCreate.
     * @param activity The activity containing the floating progress layout.
     */
    public void initialize(@NonNull Activity activity) {
        if (progressView == null) {
            View rootView = activity.findViewById(android.R.id.content);
            progressView = rootView.findViewById(R.id.card_floating_progress);

            if (progressView != null) {
                tvTitle = progressView.findViewById(R.id.tv_progress_title);
                tvApiStatus = progressView.findViewById(R.id.tv_progress_api_status);
                progressBar = progressView.findViewById(R.id.progress_bar_download);
                tvDetails = progressView.findViewById(R.id.tv_progress_details);
                setupDragListener(); // Setup the drag listener
                Log.i(TAG, "Progress view initialized and drag listener set.");
            } else {
                Log.e(TAG, "Floating progress CardView not found in the activity layout!");
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDragListener() {
        if (progressView == null) return;

        progressView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Record the initial touch point relative to the view's top-left corner
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    initialX = view.getX();
                    initialY = view.getY();
                    Log.d(TAG, "ACTION_DOWN: dX=" + dX + ", dY=" + dY);
                    return true; // Consume the event

                case MotionEvent.ACTION_MOVE:
                    // Calculate new position
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    // Optional: Add constraints to keep view within parent bounds
                    ViewGroup parent = (ViewGroup) view.getParent();
                    if (parent != null) {
                        newX = Math.max(0, Math.min(newX, parent.getWidth() - view.getWidth()));
                        newY = Math.max(0, Math.min(newY, parent.getHeight() - view.getHeight()));
                    }

                    // Update view position
                    view.animate()
                        .x(newX)
                        .y(newY)
                        .setDuration(0) // Move instantly
                        .start();
                    // Log.d(TAG, "ACTION_MOVE: newX=" + newX + ", newY=" + newY);
                    return true; // Consume the event

                 case MotionEvent.ACTION_UP:
                     // Optional: Snap to edge or save position here if needed
                     Log.d(TAG, "ACTION_UP: Final position X=" + view.getX() + ", Y=" + view.getY());
                     // Perform click if movement was negligible (optional)
                     float deltaX = Math.abs(view.getX() - initialX);
                     float deltaY = Math.abs(view.getY() - initialY);
                     if (deltaX < 10 && deltaY < 10) { // Threshold for click vs drag
                         view.performClick();
                     }
                     return true; // Consume the event

                default:
                    return false;
            }
        });
    }

    private void runOnUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    public void showProgress(String title) {
        runOnUiThread(() -> {
            if (progressView == null) {
                 Log.e(TAG, "Cannot show progress, view not initialized.");
                 return;
            }
            tvTitle.setText(title);
            tvApiStatus.setText("");
            tvDetails.setText("");
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
            tvDetails.setVisibility(View.GONE);
            progressView.setVisibility(View.VISIBLE);
            isVisible = true;
            Log.d(TAG, "Showing progress: " + title);
        });
    }

    public void updateProgress(int currentApi, int totalApis, long bytesDownloaded, long totalBytes, boolean indeterminate) {
        runOnUiThread(() -> {
            if (progressView == null || !isVisible) return;

            tvTitle.setText("Atualizando Dados...");
            tvApiStatus.setText(String.format(Locale.getDefault(), "API %d/%d", currentApi, totalApis));

            if (indeterminate || totalBytes <= 0) {
                progressBar.setIndeterminate(true);
                progressBar.setProgress(0); // Reset progress for indeterminate
                tvDetails.setText("Baixando...");
                tvDetails.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
            } else {
                progressBar.setIndeterminate(false);
                progressBar.setMax(100);
                int percentage = (int) ((bytesDownloaded * 100) / totalBytes);
                progressBar.setProgress(percentage);

                double megaBytesDownloaded = bytesDownloaded / (1024.0 * 1024.0);
                double megaBytesTotal = totalBytes / (1024.0 * 1024.0);
                tvDetails.setText(String.format(Locale.getDefault(), "%.1f MB / %.1f MB (%d%%)",
                        megaBytesDownloaded, megaBytesTotal, percentage));
                tvDetails.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
            }
             Log.d(TAG, "Updating progress: API " + currentApi + "/" + totalApis + ", Bytes: " + bytesDownloaded + "/" + totalBytes + ", Indeterminate: " + indeterminate);
        });
    }

    public void showSuccess(String message) {
        runOnUiThread(() -> {
            if (progressView == null || !isVisible) return;
            tvTitle.setText("Sucesso!");
            tvApiStatus.setText(message);
            progressBar.setVisibility(View.GONE);
            tvDetails.setVisibility(View.GONE);
            mainHandler.postDelayed(this::hideProgress, 2500);
             Log.d(TAG, "Showing success: " + message);
        });
    }

    public void showError(String message) {
        runOnUiThread(() -> {
            if (progressView == null || !isVisible) return;
            tvTitle.setText("Erro");
            tvApiStatus.setText(message);
            progressBar.setVisibility(View.GONE);
            tvDetails.setVisibility(View.GONE);
            mainHandler.postDelayed(this::hideProgress, 4000);
             Log.e(TAG, "Showing error: " + message);
        });
    }

    public void hideProgress() {
        runOnUiThread(() -> {
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
            isVisible = false;
             Log.d(TAG, "Hiding progress view.");
        });
    }

    public boolean isVisible() {
        return isVisible;
    }
}

