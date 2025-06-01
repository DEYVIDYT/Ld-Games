package com.LDGAMES.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // Import AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleObserver;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.activities.InternalBrowserActivity;
import com.LDGAMES.adapters.DownloadLinkAdapter;
import com.LDGAMES.adapters.ScreenshotAdapter;
import com.LDGAMES.adapters.YouTubeVideoAdapter;
import com.LDGAMES.models.DownloadLink;
import com.LDGAMES.models.YouTubeVideo;
import com.LDGAMES.utils.HydraApiManager;
import com.LDGAMES.utils.IGDBApiClient;
import com.LDGAMES.utils.YouTubeSearchUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameDetailFragment extends Fragment {

    private static final String TAG = "GameDetailFragment";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_YOUTUBE_SEARCH_ENABLED = "youtube_search_enabled";
    private static final String KEY_YOUTUBE_SEARCH_TERM = "youtube_search_term";
    private static final String DEFAULT_YOUTUBE_SEARCH_TERM = "Gameplay {name} no WINLATOR";
    private static final String NAME_PLACEHOLDER = "{name}";

    private String gameUrl;
    private String gameTitle; // Initial title from arguments
    private String gameImageUrl;
    private String gameId;

    private ProgressBar progressBar;
    private TextView tvError;
    private ShapeableImageView ivGameCover;
    private TextView tvGameTitle;
    private TextView tvGameDescription;
    private RecyclerView rvScreenshots;
    private Button btnDownload;
    private TextView tvYouTubeLabel;
    private RecyclerView rvYouTubeVideos;
    private YouTubePlayerView youtubePlayerViewCover;
    private YouTubePlayer youTubePlayerInstance;

    private List<String> screenshots = new ArrayList<>();
    private List<DownloadLink> downloadLinks = new ArrayList<>();
    private List<YouTubeVideo> youtubeVideos = new ArrayList<>();
    private ScreenshotAdapter screenshotAdapter;
    private YouTubeVideoAdapter youtubeVideoAdapter;
    private IGDBApiClient apiClient;
    private HydraApiManager hydraApiManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            gameUrl = args.getString("gameUrl", "");
            gameTitle = args.getString("gameTitle", ""); // Store initial title
            gameImageUrl = args.getString("gameImageUrl", "");
            gameId = args.getString("gameId", "");
        } else {
            Log.e(TAG, "Nenhum argumento recebido!");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            apiClient = IGDBApiClient.getInstance(requireContext());
            hydraApiManager = HydraApiManager.getInstance(requireContext());
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fragment not attached to a context.", e);
            return;
        }

        if (!isAdded() || getActivity() == null) return;

        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(gameTitle); // Use initial title for toolbar
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        progressBar = view.findViewById(R.id.progress_bar);
        tvError = view.findViewById(R.id.tv_error);
        ivGameCover = view.findViewById(R.id.iv_game_cover);
        tvGameTitle = view.findViewById(R.id.tv_game_title);
        tvGameDescription = view.findViewById(R.id.tv_game_description);
        rvScreenshots = view.findViewById(R.id.rv_screenshots);
        btnDownload = view.findViewById(R.id.btn_download);
        tvYouTubeLabel = view.findViewById(R.id.tv_youtube_label);
        rvYouTubeVideos = view.findViewById(R.id.rv_youtube_videos);
        youtubePlayerViewCover = view.findViewById(R.id.youtube_player_view_cover);

        getLifecycle().addObserver(youtubePlayerViewCover);

        rvScreenshots.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        screenshotAdapter = new ScreenshotAdapter(screenshots, requireContext());
        rvScreenshots.setAdapter(screenshotAdapter); // <-- Added missing setAdapter call

             rvYouTubeVideos.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            youtubeVideoAdapter = new YouTubeVideoAdapter(youtubeVideos, requireContext(), video -> {
                // --- Correction/Clarification ---
                // This logic replaces the main cover image (ivGameCover) in the AppBar
                // with the YouTube player (youtubePlayerViewCover) when a thumbnail is clicked.
                // It does NOT replace the thumbnail itself within the RecyclerView.
                Log.d(TAG, "Thumbnail clicked. Replacing cover with player for video: " + video.getVideoId());
                ivGameCover.setVisibility(View.GONE);
                youtubePlayerViewCover.setVisibility(View.VISIBLE);

                if (youTubePlayerInstance != null) {
                    youTubePlayerInstance.loadVideo(video.getVideoId(), 0);
                } else {
                    // Initialize player if not ready
                    youtubePlayerViewCover.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
                        @Override
                        public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                            youTubePlayerInstance = youTubePlayer;
                            youTubePlayerInstance.loadVideo(video.getVideoId(), 0);
                        }
                    });
                }
            });
            rvYouTubeVideos.setAdapter(youtubeVideoAdapter);

        youtubePlayerViewCover.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                youTubePlayerInstance = youTubePlayer;
                Log.d(TAG, "YouTube Player (Cover) ready.");
            }
        });

        btnDownload.setOnClickListener(v -> {
            if (isAdded()) {
                try {
                    updateApiDataAndSearch();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Context not available for download button click.", e);
                }
            }
        });

        tvGameTitle.setText(gameTitle); // Set initial title

        if (gameImageUrl != null && !gameImageUrl.isEmpty()) {
            try {
                Picasso.get()
                       .load(gameImageUrl)
                       .placeholder(R.drawable.placeholder_game)
                       .error(R.drawable.error_image)
                       .into(ivGameCover);
            } catch (Exception e) {
                 Log.e(TAG, "Error loading initial image", e);
                 ivGameCover.setImageResource(R.drawable.placeholder_game);
            }
        } else {
             ivGameCover.setImageResource(R.drawable.placeholder_game);
        }

        if (gameId != null && !gameId.isEmpty()) {
            if (gameUrl != null && gameUrl.startsWith("igdb://")) {
                gameId = gameUrl.replace("igdb://", "");
            }
            loadGameDetails(); // Load details, which will trigger YouTube search on success
            // Removed checkYouTubeSearchEnabled() call from here
        } else {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Erro: ID do jogo não fornecido");
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (youtubePlayerViewCover != null) {
            getLifecycle().removeObserver(youtubePlayerViewCover);
            youtubePlayerViewCover.release();
            youtubePlayerViewCover = null;
            youTubePlayerInstance = null;
        }
    }

    private void updateApiDataAndSearch() {
        if (!isAdded()) return;
        try {
            progressBar.setVisibility(View.VISIBLE);
            tvError.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Atualizando dados da API...", Toast.LENGTH_SHORT).show();
            hydraApiManager.forceUpdateAllData(new HydraApiManager.ApiDownloadProgressCallback<Void>() {
                @Override
                public void onProgressUpdate(int currentApi, int totalApis, long bytesDownloaded, long totalBytes, boolean indeterminate) {}
                @Override
                public void onSuccess(Void result) {
                    if (!isAdded() || getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Atualização concluída!", Toast.LENGTH_SHORT).show();
                        searchDownloadLinksLocally();
                    });
                }
                @Override
                public void onError(Exception e) {
                    if (!isAdded() || getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Erro na atualização, tentando usar dados existentes", Toast.LENGTH_SHORT).show();
                        searchDownloadLinksLocally();
                    });
                }
                @Override
                public void onComplete() {}
            });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for API update.", e);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void loadGameDetails() {
        if (!isAdded() || getActivity() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
        Log.d(TAG, "Loading game details for ID: " + gameId);

        try {
            int id = Integer.parseInt(gameId);
            apiClient.getGameDetails(id, new IGDBApiClient.ApiCallback<Map<String, Object>>() {
                @Override
                public void onSuccess(Map<String, Object> result) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || getActivity() == null) return;
                        progressBar.setVisibility(View.GONE);

                        // Update Title from API result
                        if (result.containsKey("title")) {
                            String apiTitle = (String) result.get("title");
                            if (apiTitle != null && !apiTitle.isEmpty()) {
                                tvGameTitle.setText(apiTitle);
                                gameTitle = apiTitle; // Update the definitive game title
                                // Update Toolbar title as well
                                if (getActivity() instanceof AppCompatActivity) {
                                    AppCompatActivity activity = (AppCompatActivity) getActivity();
                                    if (activity.getSupportActionBar() != null) {
                                        activity.getSupportActionBar().setTitle(gameTitle);
                                    }
                                }
                            }
                        }

                        // Load Image
                        if (result.containsKey("imageUrl")) {
                            String imageUrl = (String) result.get("imageUrl");
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                Log.d(TAG, "Attempting to load banner image with Picasso: " + imageUrl); // Add logging
                                try {
                                    Picasso.get()
                                           .load(imageUrl)
                                           .placeholder(R.drawable.placeholder_game)
                                           .error(R.drawable.error_image) // Keep error placeholder
                                           .into(ivGameCover, new Callback() { // Add Picasso Callback
                                               @Override
                                               public void onSuccess() {
                                                   Log.d(TAG, "Picasso successfully loaded image: " + imageUrl);
                                               }

                                               @Override
                                               public void onError(Exception e) {
                                                   Log.e(TAG, "Picasso failed to load image: " + imageUrl, e);
                                                   // Optionally set a specific error image again, though .error() should handle it
                                                   // ivGameCover.setImageResource(R.drawable.error_image);
                                               }
                                           });
                                } catch (Exception e) {
                                     // Catch potential exceptions from Picasso.get() or .load() itself, though less common
                                     Log.e(TAG, "Error initiating Picasso load for image: " + imageUrl, e);
                                     ivGameCover.setImageResource(R.drawable.error_image); // Set error image on immediate exception
                                }
                            } else {
                                Log.w(TAG, "Image URL from API is null or empty.");
                                ivGameCover.setImageResource(R.drawable.placeholder_game); // Set placeholder if no URL
                            }
                        } else {
                            Log.w(TAG, "API result does not contain 'imageUrl' key.");
                            // Keep the initially loaded image or placeholder if API doesn't provide one
                            // Or set placeholder explicitly: ivGameCover.setImageResource(R.drawable.placeholder_game);
                        }

                        // Load Description
                        if (result.containsKey("description")) {
                            tvGameDescription.setText((String) result.get("description"));
                        }

                        // Load Screenshots
                        if (result.containsKey("screenshots")) {
                            screenshots.clear();
                            screenshots.addAll((List<String>) result.get("screenshots"));
                            screenshotAdapter.notifyDataSetChanged();
                        }

                        // Enable Download Button
                        btnDownload.setEnabled(true);

                        // Check YouTube Search AFTER title is confirmed
                        checkYouTubeSearchEnabled();
                    });
                }
                @Override
                public void onError(Exception e) {
                    if (getActivity() == null || !isAdded()) return;
                    Log.e(TAG, "Error loading details", e);
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || getActivity() == null) return;
                        progressBar.setVisibility(View.GONE);
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Erro ao carregar detalhes: " + e.getMessage());
                        try {
                            Toast.makeText(requireContext(), "Erro ao carregar detalhes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        } catch (IllegalStateException ise) {
                             Log.e(TAG, "Context not available for error toast.", ise);
                        }
                    });
                }
            });
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid game ID: " + gameId, e);
            progressBar.setVisibility(View.GONE);
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Erro: ID do jogo inválido");
        } catch (IllegalStateException e) {
             Log.e(TAG, "Context not available before loading game details.", e);
        }
    }

    private void searchDownloadLinksLocally() {
        if (!isAdded()) return;
        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Starting local link search for: " + gameTitle);
        try {
            hydraApiManager.searchDownloadLinksLocally(gameTitle, new HydraApiManager.ApiLocalSearchCallback<List<DownloadLink>>() {
                @Override
                public void onSuccess(List<DownloadLink> result) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (result != null && !result.isEmpty()) {
                            Log.d(TAG, "Local search found " + result.size() + " links.");
                            downloadLinks.clear();
                            downloadLinks.addAll(result);
                            showDownloadDialog();
                        } else {
                            Log.d(TAG, "Local search found no links for: " + gameTitle);
                            try {
                                Toast.makeText(requireContext(), "Nenhum link de download encontrado localmente.", Toast.LENGTH_SHORT).show();
                            } catch (IllegalStateException ise) {
                                Log.e(TAG, "Context not available for no links toast.", ise);
                            }
                        }
                    });
                }
                @Override
                public void onError(Exception e) {
                    if (getActivity() == null || !isAdded()) return;
                    Log.e(TAG, "Error searching links locally", e);
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        try {
                            Toast.makeText(requireContext(), "Erro ao buscar links localmente: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        } catch (IllegalStateException ise) {
                            Log.e(TAG, "Context not available for local search error toast.", ise);
                        }
                    });
                }
            });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for local link search.", e);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showDownloadDialog() {
        if (!isAdded() || getActivity() == null) return;
        try {
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_download_links, null);
            RecyclerView rvDialogLinks = dialogView.findViewById(R.id.rv_dialog_download_links);
            rvDialogLinks.setLayoutManager(new LinearLayoutManager(requireContext()));

            AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Links de Download")
                .setView(dialogView)
                .setPositiveButton("Fechar", null)
                .create();

            DownloadLinkAdapter adapter = new DownloadLinkAdapter(downloadLinks, link -> {
                Intent intent = new Intent(requireContext(), InternalBrowserActivity.class);
                intent.putExtra("url", link.getUrl());
                startActivity(intent);
                dialog.dismiss(); // Dismiss dialog on link click
            });
            rvDialogLinks.setAdapter(adapter);

            dialog.show();

        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for showing download dialog.", e);
        }
    }

    private void checkYouTubeSearchEnabled() {
        if (!isAdded() || getContext() == null) return;
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnabled = prefs.getBoolean(KEY_YOUTUBE_SEARCH_ENABLED, false);

            // Ensure gameTitle is not null or empty before attempting search
            if (isEnabled && gameTitle != null && !gameTitle.isEmpty()) {
                String searchTermTemplate = prefs.getString(KEY_YOUTUBE_SEARCH_TERM, DEFAULT_YOUTUBE_SEARCH_TERM);
                String searchTerm = searchTermTemplate.replace(NAME_PLACEHOLDER, gameTitle);
                Log.d(TAG, "YouTube search enabled. Searching for: " + searchTerm);
                searchYouTubeVideos(searchTerm);
            } else {
                Log.d(TAG, "YouTube search disabled or game title is missing. Hiding section.");
                tvYouTubeLabel.setVisibility(View.GONE);
                rvYouTubeVideos.setVisibility(View.GONE);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for checking YouTube search settings.", e);
            // Hide section if context is lost
            tvYouTubeLabel.setVisibility(View.GONE);
            rvYouTubeVideos.setVisibility(View.GONE);
        }
    }

    private void searchYouTubeVideos(String query) {
        if (!isAdded() || getActivity() == null) return;
        Log.d(TAG, "Initiating YouTube search for: " + query);
        // Make sure the views are initially hidden until results arrive
        // tvYouTubeLabel.setVisibility(View.GONE);
        // rvYouTubeVideos.setVisibility(View.GONE);
        // This might cause flicker, let's manage visibility only in callbacks

        YouTubeSearchUtil.searchYouTubeVideos(query, new YouTubeSearchUtil.YouTubeSearchCallback() {
            @Override
            public void onSuccess(List<YouTubeVideo> videos) {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    if (!videos.isEmpty()) {
                        Log.d(TAG, "Found " + videos.size() + " YouTube videos. Updating UI.");
                        youtubeVideos.clear();
                        youtubeVideos.addAll(videos);
                        youtubeVideoAdapter.notifyDataSetChanged();
                        tvYouTubeLabel.setVisibility(View.VISIBLE); // Show label
                        rvYouTubeVideos.setVisibility(View.VISIBLE); // Show recycler view
                    } else {
                        Log.d(TAG, "No YouTube videos found for: " + query + ". Hiding section.");
                        tvYouTubeLabel.setVisibility(View.GONE);
                        rvYouTubeVideos.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;
                Log.e(TAG, "Error searching YouTube videos", e);
                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Error occurred during YouTube search. Hiding section.");
                    tvYouTubeLabel.setVisibility(View.GONE);
                    rvYouTubeVideos.setVisibility(View.GONE);
                    try {
                        Toast.makeText(requireContext(), "Erro ao buscar vídeos no YouTube", Toast.LENGTH_SHORT).show();
                    } catch (IllegalStateException ise) {
                        Log.e(TAG, "Context not available for YouTube search error toast.", ise);
                    }
                });
            }
        });
    }
}

