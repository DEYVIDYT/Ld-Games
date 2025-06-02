package com.LDGAMES.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.LDGAMES.models.YouTubeVideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeSearchUtil {

    private static final String TAG = "YouTubeSearchUtil";
    private static final String YOUTUBE_SEARCH_URL_FORMAT = "https://www.youtube.com/results?search_query=%s";
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script.*?>\\s*var\\s+ytInitialData\\s*=\\s*(\\{.*?\\});?\\s*</script>", Pattern.DOTALL);

    public interface YouTubeSearchCallback {
        void onSuccess(List<YouTubeVideo> videos);
        void onError(Exception e);
    }

    // Modificado: Aceita a query completa ao invés de apenas o nome do jogo
    public static void searchYouTubeVideos(String fullQuery, YouTubeSearchCallback callback) {
        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(fullQuery, StandardCharsets.UTF_8.toString());
                String searchUrl = String.format(YOUTUBE_SEARCH_URL_FORMAT, encodedQuery);
                Log.d(TAG, "Searching YouTube with URL: " + searchUrl);

                URL url = new URL(searchUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setConnectTimeout(15000); // 15 seconds
                connection.setReadTimeout(15000); // 15 seconds

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "YouTube search response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    List<YouTubeVideo> videos = parseYouTubeHtml(response.toString());
                    if (videos.isEmpty()) {
                         Log.w(TAG, "Parsing might have failed or no results found in HTML for query: " + fullQuery);
                    }
                    callback.onSuccess(videos);

                } else {
                    throw new Exception("YouTube search failed with HTTP code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching YouTube videos", e);
                callback.onError(e);
            }
        }).start();
    }

    private static List<YouTubeVideo> parseYouTubeHtml(String htmlContent) {
        List<YouTubeVideo> videos = new ArrayList<>();
        try {
            Matcher matcher = SCRIPT_PATTERN.matcher(htmlContent);
            if (matcher.find()) {
                String jsonData = matcher.group(1);
                JSONObject initialData = new JSONObject(jsonData);

                JSONArray contents = initialData.getJSONObject("contents")
                                                .getJSONObject("twoColumnSearchResultsRenderer")
                                                .getJSONObject("primaryContents")
                                                .getJSONObject("sectionListRenderer")
                                                .getJSONArray("contents");

                JSONArray videoItems = contents.getJSONObject(0)
                                              .getJSONObject("itemSectionRenderer")
                                              .getJSONArray("contents");

                for (int i = 0; i < videoItems.length(); i++) {
                    JSONObject item = videoItems.getJSONObject(i);
                    if (item.has("videoRenderer")) {
                        JSONObject videoRenderer = item.getJSONObject("videoRenderer");
                        String videoId = videoRenderer.getString("videoId");

                        String title = "N/A";
                        if (videoRenderer.has("title") && videoRenderer.getJSONObject("title").has("runs") && videoRenderer.getJSONObject("title").getJSONArray("runs").length() > 0) {
                             title = videoRenderer.getJSONObject("title").getJSONArray("runs").getJSONObject(0).getString("text");
                        }

                        String thumbnailUrl = "N/A";
                         if (videoRenderer.has("thumbnail") && videoRenderer.getJSONObject("thumbnail").has("thumbnails") && videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails").length() > 0) {
                             int thumbIndex = Math.min(1, videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails").length() - 1);
                             thumbnailUrl = videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails").getJSONObject(thumbIndex).getString("url");
                         }

                        Log.d(TAG, "Found video: ID=" + videoId + ", Title=" + title);
                        videos.add(new YouTubeVideo(videoId, title, thumbnailUrl));

                        if (videos.size() >= 10) { // Limit to 10 videos
                            break;
                        }
                    }
                }
            } else {
                 Log.w(TAG, "Could not find ytInitialData JSON in the HTML response.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing YouTube HTML response", e);
        }
        return videos;
    }
}

