package com.LDGAMES.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.LDGAMES.models.DownloadLink;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean; // Import AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HydraApiManager {
    private static final String TAG = "HydraApiManager";
    private static HydraApiManager instance;
    private final OkHttpClient client;
    private final Context context;
    private final List<String> apiUrls = new ArrayList<>();
    private static final String PREF_NAME = "hydra_api_prefs";
    private static final String PREF_API_URLS = "api_urls";
    private static final String CACHE_DIR_NAME = "hydra_api_cache";
    private static final String API_ENABLED_PREFS = "api_enabled_prefs";
    private Handler mainHandler;

    public interface ApiDownloadProgressCallback<T> {
        void onProgressUpdate(int currentApi, int totalApis, long bytesDownloaded, long totalBytes, boolean indeterminate);
        void onSuccess(T result);
        void onError(Exception e);
        void onComplete();
    }

    public interface ApiLocalSearchCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private HydraApiManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        loadApiUrls();
        ensureCacheDirExists();
    }

    public static synchronized HydraApiManager getInstance(Context context) {
        if (instance == null) {
            instance = new HydraApiManager(context);
        }
        return instance;
    }

    private void ensureCacheDirExists() {
        File cacheDir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) {
            if (cacheDir.mkdirs()) {
                Log.i(TAG, "Cache directory created: " + cacheDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create cache directory.");
            }
        }
    }

    private String getCacheFileName(String apiUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(apiUrl.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return "hydra_api_" + hashtext + ".json";
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 not supported, using hashcode as fallback", e);
            return "hydra_api_" + apiUrl.hashCode() + ".json";
        }
    }

    private File getCacheFile(String apiUrl) {
        return new File(context.getFilesDir() + File.separator + CACHE_DIR_NAME, getCacheFileName(apiUrl));
    }

    private void saveApiDataToFile(String apiUrl, String jsonData) throws IOException {
        File file = getCacheFile(apiUrl);
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(jsonData);
            Log.i(TAG, "API data " + apiUrl + " saved to " + file.getName());
        } catch (IOException e) {
            Log.e(TAG, "Error saving API data " + apiUrl + " to file " + file.getName(), e);
            throw e;
        }
    }

    private String readApiDataFromFile(String apiUrl) throws IOException {
        File file = getCacheFile(apiUrl);
        if (!file.exists()) {
            throw new IOException("Cache file not found for " + apiUrl);
        }
        StringBuilder stringBuilder = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, n);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading API data " + apiUrl + " from file " + file.getName(), e);
            throw e;
        }
    }

    public boolean deleteApiCacheFile(String apiUrl) {
        File file = getCacheFile(apiUrl);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public void clearApiCache() {
        File cacheDir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith("hydra_api_") && file.getName().endsWith(".json")) {
                        if (!file.delete()) {
                            Log.w(TAG, "Failed to delete cache file: " + file.getName());
                        }
                    }
                }
                Log.i(TAG, "API cache cleared.");
            }
        }
    }

    // --- URL Management Methods ---
    private void loadApiUrls() {
        apiUrls.clear();
        String savedUrls = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(PREF_API_URLS, "");
        if (!savedUrls.isEmpty()) {
            String[] urls = savedUrls.split(",");
            for (String url : urls) {
                if (!url.trim().isEmpty()) {
                    apiUrls.add(url.trim());
                }
            }
        }
    }

    private void saveApiUrls() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apiUrls.size(); i++) {
            sb.append(apiUrls.get(i));
            if (i < apiUrls.size() - 1) {
                sb.append(",");
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_API_URLS, sb.toString())
                .apply();
    }

    public boolean addApiUrl(String url) {
        if (!apiUrls.contains(url)) {
            apiUrls.add(url);
            saveApiUrls();
            return true;
        }
        return false;
    }

    public boolean removeApiUrl(String url) {
        boolean removed = apiUrls.remove(url);
        if (removed) {
            saveApiUrls();
            deleteApiCacheFile(url);
        }
        return removed;
    }

    public List<String> getApiUrls() {
        loadApiUrls();
        return new ArrayList<>(apiUrls);
    }
    // --- End URL Management Methods ---

    public void forceUpdateAllData(final ApiDownloadProgressCallback<Void> callback) {
        loadApiUrls();
        final List<String> currentApiUrls = new ArrayList<>(apiUrls);

        if (currentApiUrls.isEmpty()) {
            mainHandler.post(() -> {
                callback.onError(new Exception("No APIs configured for update."));
                callback.onComplete();
            });
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        final AtomicInteger apisProcessed = new AtomicInteger(0);
        final int totalApis = currentApiUrls.size();
        final List<Exception> errors = new ArrayList<>();

        mainHandler.post(() -> callback.onProgressUpdate(0, totalApis, 0, 0, true));

        for (int i = 0; i < totalApis; i++) {
            final String apiUrl = currentApiUrls.get(i);
            final int currentApiIndex = i + 1;

            executor.execute(() -> {
                long totalBytes = -1;
                AtomicLong bytesRead = new AtomicLong(0);
                File tempFile = null;

                try {
                    Request request = new Request.Builder().url(apiUrl).build();
                    Response response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        throw new IOException("Error downloading " + apiUrl + ": " + response.code());
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response for " + apiUrl);
                    }

                    totalBytes = body.contentLength();
                    InputStream inputStream = body.byteStream();
                    tempFile = File.createTempFile("api_download_", ".tmp", context.getCacheDir());

                    final long finalTotalBytes = totalBytes;
                    mainHandler.post(() -> callback.onProgressUpdate(currentApiIndex, totalApis, 0, finalTotalBytes, finalTotalBytes <= 0));

                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        final long reportThreshold = finalTotalBytes > 0 ? Math.max(finalTotalBytes / 100, 8192L) : 256 * 1024L;
                        long lastReportedBytes = 0;

                        while ((n = inputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                            long currentBytes = bytesRead.addAndGet(n);

                            if (finalTotalBytes > 0) {
                                if (currentBytes == finalTotalBytes || currentBytes - lastReportedBytes >= reportThreshold) {
                                    final long finalCurrentBytes = currentBytes;
                                    mainHandler.post(() -> callback.onProgressUpdate(currentApiIndex, totalApis, finalCurrentBytes, finalTotalBytes, false));
                                    lastReportedBytes = currentBytes;
                                }
                            }
                        }
                        fos.flush();
                    } finally {
                         if (inputStream != null) inputStream.close();
                         if (body != null) body.close();
                    }

                    if (finalTotalBytes > 0 && bytesRead.get() == finalTotalBytes) {
                         mainHandler.post(() -> callback.onProgressUpdate(currentApiIndex, totalApis, finalTotalBytes, finalTotalBytes, false));
                    }

                    String jsonData = FileUtils.readFileToString(tempFile);
                    saveApiDataToFile(apiUrl, jsonData);

                } catch (Exception e) {
                    Log.e(TAG, "Error downloading/saving API " + apiUrl, e);
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        if (!tempFile.delete()) {
                            Log.w(TAG, "Could not delete temp file: " + tempFile.getAbsolutePath());
                        }
                    }

                    int processed = apisProcessed.incrementAndGet();
                    if (processed == totalApis) {
                        mainHandler.post(() -> {
                            if (errors.isEmpty()) {
                                callback.onSuccess(null);
                            } else {
                                callback.onError(new Exception("Failed to update " + errors.size() + " API(s). First error: " + errors.get(0).getMessage(), errors.get(0)));
                            }
                            callback.onComplete();
                        });
                    }
                }
            });
        }
        executor.shutdown();
    }

    private static String normalizeString(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        String nfdNormalizedString = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        normalized = pattern.matcher(nfdNormalizedString).replaceAll("");
        normalized = normalized.replaceAll("[^a-z0-9\\s]", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    public void searchDownloadLinksLocally(String gameName, final ApiLocalSearchCallback<List<DownloadLink>> callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final List<DownloadLink> allLinks = new ArrayList<>(); // Make final
            final List<Exception> errors = new ArrayList<>(); // Make final
            File cacheDir = new File(context.getFilesDir(), CACHE_DIR_NAME);

            List<String> configuredApiUrls = getApiUrls();
            SharedPreferences apiEnabledPrefs = context.getSharedPreferences(API_ENABLED_PREFS, Context.MODE_PRIVATE);

            if (configuredApiUrls.isEmpty()) {
                 mainHandler.post(() -> callback.onError(new Exception("No APIs configured.")));
                 return;
            }

            final AtomicBoolean anyApiEnabled = new AtomicBoolean(false); // Use AtomicBoolean

            for (String apiUrl : configuredApiUrls) {
                boolean isEnabled = apiEnabledPrefs.getBoolean(apiUrl, true);

                if (!isEnabled) {
                    Log.d(TAG, "Skipping disabled API: " + apiUrl);
                    continue;
                }

                anyApiEnabled.set(true); // Set AtomicBoolean to true
                File cacheFile = getCacheFile(apiUrl);

                if (!cacheFile.exists()) {
                    Log.w(TAG, "Cache file not found for enabled API: " + cacheFile.getName() + ". Skipping.");
                    continue;
                }

                try {
                    String jsonData = FileUtils.readFileToString(cacheFile);
                    List<DownloadLink> linksFromFile = parseJsonAndFindLinks(jsonData, gameName, cacheFile.getName());
                    if (linksFromFile != null && !linksFromFile.isEmpty()) {
                        synchronized (allLinks) {
                            allLinks.addAll(linksFromFile);
                        }
                    }
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error reading or parsing cache file: " + cacheFile.getName(), e);
                    synchronized (errors) {
                        errors.add(new Exception("Error in cache " + cacheFile.getName() + ": " + e.getMessage(), e));
                    }
                }
            }

            mainHandler.post(() -> {
                if (!allLinks.isEmpty()) {
                    callback.onSuccess(allLinks);
                } else if (!errors.isEmpty()) {
                    callback.onError(errors.get(0));
                } else if (!anyApiEnabled.get()) { // Get value from AtomicBoolean
                     callback.onError(new Exception("No APIs enabled for search. Check settings."));
                }
                 else {
                    callback.onError(new Exception("No local links found for \'" + gameName + "\' in enabled APIs."));
                }
            });
        });
        executor.shutdown();
    }

    private List<DownloadLink> parseJsonAndFindLinks(String jsonData, String gameName, String sourceName) throws JSONException {
        List<DownloadLink> links = new ArrayList<>();
        JSONObject json = new JSONObject(jsonData);
        String apiName = json.optString("name", sourceName);
        JSONArray downloadsJson = json.getJSONArray("downloads");

        String normalizedGameName = normalizeString(gameName);
        if (normalizedGameName.isEmpty()) {
            Log.w(TAG, "Normalized game name is empty, skipping search in " + sourceName);
            return links;
        }
        Log.d(TAG, "Searching for normalized name: \'" + normalizedGameName + "\' in source: " + apiName);

        for (int i = 0; i < downloadsJson.length(); i++) {
            JSONObject downloadJson = downloadsJson.getJSONObject(i);
            String title = downloadJson.getString("title");
            String normalizedTitle = normalizeString(title);

            if (normalizedTitle.contains(normalizedGameName)) {
                 Log.d(TAG, "Match found: API Title=\'" + title + "\', Normalized=\'" + normalizedTitle + "\'");
                 String uploadDateStr = downloadJson.optString("uploadDate");
                 Date uploadDate = null;
                 if (!uploadDateStr.isEmpty()) {
                     SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss.SSS\'Z\'", Locale.US);
                     SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                     SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                     try { uploadDate = sdf1.parse(uploadDateStr); }
                     catch (ParseException e1) {
                         try { uploadDate = sdf2.parse(uploadDateStr); }
                         catch (ParseException e2) {
                             try { uploadDate = sdf3.parse(uploadDateStr); }
                             catch (ParseException e3) { Log.w(TAG, "Unrecognized date format: " + uploadDateStr); }
                         }
                     }
                 }

                 String fileSize = downloadJson.optString("fileSize", "N/A");
                 JSONArray urisJson = downloadJson.getJSONArray("uris");

                 for (int j = 0; j < urisJson.length(); j++) {
                     String uri = urisJson.getString(j);
                     DownloadLink link = new DownloadLink();
                     link.setName(title);
                     link.setUrl(uri);
                     link.setSize(fileSize);

                     StringBuilder description = new StringBuilder();
                     description.append("Fonte: ").append(apiName).append(" (Local)");
                     if (uploadDate != null) {
                         SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                         description.append("\\nData: ").append(displayFormat.format(uploadDate));
                     }
                     if (!fileSize.equals("N/A")) {
                         description.append("\\nTamanho: ").append(fileSize);
                     }
                     link.setDescription(description.toString());
                     links.add(link);
                 }
            }
        }
        Log.d(TAG, "Found " + links.size() + " links for \'" + gameName + "\' in " + apiName);
        return links;
    }

    private static class FileUtils {
        static String readFileToString(File file) throws IOException {
            StringBuilder stringBuilder = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                char[] buffer = new char[1024];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    stringBuilder.append(buffer, 0, n);
                }
            }
            return stringBuilder.toString();
        }
    }
}

