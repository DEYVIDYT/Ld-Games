package com.LDGAMES.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProtonDbApiClient {
    private static final String TAG = "ProtonDbApiClient";
    private static final String API_BASE_URL = "https://protondb.max-p.me/";

    private static ProtonDbApiClient instance;
    private final OkHttpClient client;
    private final ExecutorService executor;

    // Interface for callbacks
    public interface ProtonApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private ProtonDbApiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized ProtonDbApiClient getInstance() {
        if (instance == null) {
            instance = new ProtonDbApiClient();
        }
        return instance;
    }

    /**
     * Fetches the compatibility rating for a given Steam App ID from the unofficial ProtonDB API.
     * It currently retrieves the rating from the most recent report.
     */
    public void getGameCompatibility(String appId, final ProtonApiCallback<String> callback) {
        if (appId == null || appId.isEmpty()) {
            callback.onError(new IllegalArgumentException("App ID cannot be null or empty"));
            return;
        }

        String url = API_BASE_URL + "games/" + appId + "/reports/";
        Log.d(TAG, "Fetching ProtonDB compatibility from: " + url);

        executor.execute(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 404) {
                         // Game not found on ProtonDB is not necessarily an error in the app flow
                         Log.w(TAG, "Game not found on ProtonDB (404): App ID " + appId);
                         callback.onSuccess("Not Found"); // Indicate not found
                    } else {
                        throw new IOException("Unexpected code " + response.code() + " for URL: " + url);
                    }
                    return; // Exit after handling non-success
                }

                String responseBody = response.body().string();
                JSONArray reports = new JSONArray(responseBody);

                if (reports.length() > 0) {
                    // Get the rating from the most recent report (first in the array)
                    JSONObject latestReport = reports.getJSONObject(0);
                    String rating = latestReport.optString("rating", "Unknown");
                    Log.d(TAG, "ProtonDB rating for App ID " + appId + ": " + rating);
                    callback.onSuccess(rating);
                } else {
                    Log.w(TAG, "No reports found on ProtonDB for App ID: " + appId);
                    callback.onSuccess("No Reports"); // Indicate no reports found
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching ProtonDB compatibility for App ID: " + appId, e);
                callback.onError(e);
            }
        });
    }

    // Consider adding a method to shutdown the executor when the app closes
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

