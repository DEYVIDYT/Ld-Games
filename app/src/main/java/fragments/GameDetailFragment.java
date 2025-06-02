package com.LDGAMES.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout; // <-- Added missing import
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
import com.LDGAMES.activities.DownloadProgressActivity; // Necessário para o método showDownloadDialog
import com.LDGAMES.activities.InternalBrowserActivity;
import com.LDGAMES.adapters.DownloadLinkAdapter;
import com.LDGAMES.adapters.ScreenshotAdapter;
import com.LDGAMES.adapters.YouTubeVideoAdapter;
import com.LDGAMES.models.DownloadLink;
import com.LDGAMES.models.Game; // Necessário para o método searchDownloadLinksLocally
import com.LDGAMES.models.YouTubeVideo;
import com.LDGAMES.utils.HydraApiManager;
import com.LDGAMES.utils.IGDBApiClient;
import com.LDGAMES.utils.ProtonDbApiClient; // <-- Add ProtonDB API client import
import com.LDGAMES.utils.YouTubeSearchUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File; // Necessário para o método showDownloadDialog
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

    // ProtonDB UI Elements
    private LinearLayout layoutProtonDb;
    private TextView tvProtonDbRating;
    private ProgressBar pbProtonDbLoading;

    private List<String> screenshots = new ArrayList<>();
    private List<DownloadLink> downloadLinks = new ArrayList<>();
    private List<YouTubeVideo> youtubeVideos = new ArrayList<>();
    private ScreenshotAdapter screenshotAdapter;
    private YouTubeVideoAdapter youtubeVideoAdapter;
    private IGDBApiClient apiClient;
    private HydraApiManager hydraApiManager;
    private AlertDialog progressDialog; // Add this line
    private View fragmentView; // Store the view for later use

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
        fragmentView = inflater.inflate(R.layout.fragment_game_detail, container, false);
        return fragmentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            apiClient = IGDBApiClient.getInstance(requireContext());
            hydraApiManager = HydraApiManager.getInstance(requireContext());
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fragment not attached to a context.", e);
            // Show error message on screen if view is available
            if (view != null) {
                progressBar = view.findViewById(R.id.progress_bar);
                tvError = view.findViewById(R.id.tv_error);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (tvError != null) {
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Erro interno ao inicializar.");
                }
            }
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

        // Initialize Views
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
        layoutProtonDb = view.findViewById(R.id.layout_protondb);
        tvProtonDbRating = view.findViewById(R.id.tv_protondb_rating);
        pbProtonDbLoading = view.findViewById(R.id.pb_protondb_loading);

        // Setup Lifecycle Observer for YouTube Player
        getLifecycle().addObserver(youtubePlayerViewCover);

        // Setup RecyclerViews
        setupScreenshotsRecyclerView();
        setupYouTubeVideosRecyclerView();

        // Setup YouTube Player Listener
        setupYouTubePlayerListener();

        // Setup Download Button Listener
        setupDownloadButtonListener();

        // Set initial UI state
        tvGameTitle.setText(gameTitle);
        loadInitialCoverImage();

        // Load Game Details from API
        if (gameId != null && !gameId.isEmpty()) {
            if (gameUrl != null && gameUrl.startsWith("igdb://")) {
                gameId = gameUrl.replace("igdb://", "");
            }
            loadGameDetails();
        } else {
            showError("Erro: ID do jogo não fornecido");
        }
    }

    private void setupScreenshotsRecyclerView() {
        if (!isAdded() || getContext() == null) return;
        rvScreenshots.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        screenshotAdapter = new ScreenshotAdapter(screenshots, requireContext());
        rvScreenshots.setAdapter(screenshotAdapter);
    }

    private void setupYouTubeVideosRecyclerView() {
        if (!isAdded() || getContext() == null) return;
        rvYouTubeVideos.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        youtubeVideoAdapter = new YouTubeVideoAdapter(youtubeVideos, requireContext(), video -> {
            Log.d(TAG, "Thumbnail clicked. Replacing cover with player for video: " + video.getVideoId());
            ivGameCover.setVisibility(View.GONE);
            youtubePlayerViewCover.setVisibility(View.VISIBLE);

            if (youTubePlayerInstance != null) {
                youTubePlayerInstance.loadVideo(video.getVideoId(), 0);
            } else {
                // Initialize player if not ready (listener is already added, just load)
                 Log.w(TAG, "YouTube player instance was null when trying to load video from thumbnail click.");
                 // Re-adding listener might cause issues, rely on the initial setup
                 youtubePlayerViewCover.getYouTubePlayerWhenReady(player -> {
                     youTubePlayerInstance = player;
                     player.loadVideo(video.getVideoId(), 0);
                 });
            }
        });
        rvYouTubeVideos.setAdapter(youtubeVideoAdapter);
    }

    private void setupYouTubePlayerListener() {
        youtubePlayerViewCover.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer initializedYouTubePlayer) {
                youTubePlayerInstance = initializedYouTubePlayer;
                Log.d(TAG, "YouTube Player (Cover) ready.");
            }
        });
    }

    private void setupDownloadButtonListener() {
        btnDownload.setOnClickListener(v -> {
            if (isAdded() && getContext() != null) {
                try {
                    updateApiDataAndSearch();
                } catch (IllegalStateException ise) {
                    Log.e(TAG, "Context not available for download button click.", ise);
                    Toast.makeText(getContext(), "Erro ao iniciar busca de downloads.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Download button clicked but fragment not added or context is null.");
            }
        });
    }

    private void loadInitialCoverImage() {
        if (gameImageUrl != null && !gameImageUrl.isEmpty()) {
            try {
                new Picasso.Builder(requireContext())
                       .build()
                       .load(gameImageUrl)
                       .placeholder(R.drawable.placeholder_game)
                       .error(R.drawable.error_image) // Keep error placeholder
                       .into(ivGameCover, new Callback() { // Add Picasso Callback
                           @Override
                           public void onSuccess() {
                               Log.d(TAG, "Picasso successfully loaded initial image: " + gameImageUrl);
                           }

                           @Override
                           public void onError(Exception e) {
                               Log.e(TAG, "Picasso failed to load initial image: " + gameImageUrl, e);
                           }
                       });
            } catch (Exception picassoEx) {
                 Log.e(TAG, "Error loading initial image with Picasso", picassoEx);
                 if (isAdded() && getContext() != null) {
                    ivGameCover.setImageResource(R.drawable.placeholder_game);
                 }
            }
        } else {
             if (isAdded() && getContext() != null) {
                ivGameCover.setImageResource(R.drawable.placeholder_game);
             }
        }
    }

    private void loadGameDetails() {
        if (!isAdded() || getActivity() == null) return;
        showLoading();
        Log.d(TAG, "Loading game details for ID: " + gameId);

        try {
            int id = Integer.parseInt(gameId);
            apiClient.getGameDetails(id, new IGDBApiClient.ApiCallback<Map<String, Object>>() {
                @Override
                public void onSuccess(Map<String, Object> result) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || getActivity() == null) return;
                        hideLoading();
                        updateUIWithGameDetails(result);
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || getActivity() == null) return;
                        showError("Erro ao carregar detalhes: " + e.getMessage());
                        Log.e(TAG, "Erro ao carregar detalhes do jogo (ID: " + gameId + ")", e);
                    });
                }
            });
        } catch (NumberFormatException nfe) {
            Log.e(TAG, "Erro ao converter gameId para inteiro: " + gameId, nfe);
            showError("Erro: ID do jogo inválido.");
        } catch (IllegalStateException ise) {
            Log.e(TAG, "Estado ilegal ao iniciar carregamento de detalhes.", ise);
            showError("Erro interno ao carregar detalhes.");
        }
    }

    private void updateUIWithGameDetails(Map<String, Object> result) {
        // Update Title
        String fetchedTitle = "Título Desconhecido";
        if (result.containsKey("title")) {
            fetchedTitle = (String) result.get("title");
            tvGameTitle.setText(fetchedTitle);
            if (getActivity() instanceof AppCompatActivity && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(fetchedTitle);
            }
        }

        // Update Description
        if (result.containsKey("description")) {
            tvGameDescription.setText((String) result.get("description"));
        } else {
            tvGameDescription.setText("Sem descrição disponível.");
        }

        // Update Screenshots
        if (fragmentView != null && result.containsKey("screenshots")) {
            List<String> fetchedScreenshots = (List<String>) result.get("screenshots");
            TextView screenshotsLabel = fragmentView.findViewById(R.id.tv_screenshots_label);
            if (fetchedScreenshots != null && !fetchedScreenshots.isEmpty()) {
                screenshots.clear();
                screenshots.addAll(fetchedScreenshots);
                screenshotAdapter.notifyDataSetChanged();
                rvScreenshots.setVisibility(View.VISIBLE);
                if (screenshotsLabel != null) screenshotsLabel.setVisibility(View.VISIBLE);
            } else {
                 rvScreenshots.setVisibility(View.GONE);
                 if (screenshotsLabel != null) screenshotsLabel.setVisibility(View.GONE);
            }
        } else if (fragmentView != null) {
             rvScreenshots.setVisibility(View.GONE);
             TextView screenshotsLabel = fragmentView.findViewById(R.id.tv_screenshots_label);
             if (screenshotsLabel != null) screenshotsLabel.setVisibility(View.GONE);
        }

        // Update Cover Image (only if necessary)
        if (result.containsKey("imageUrl")) {
            String fetchedImageUrl = (String) result.get("imageUrl");
            if (fetchedImageUrl != null && !fetchedImageUrl.isEmpty() && !fetchedImageUrl.equals(gameImageUrl)) {
                gameImageUrl = fetchedImageUrl;
                try {
                    new Picasso.Builder(requireContext())
                           .build()
                           .load(gameImageUrl) // Use the updated gameImageUrl
                           .placeholder(R.drawable.placeholder_game)
                           .error(R.drawable.error_image) // Keep error placeholder
                           .into(ivGameCover, new Callback() { // Add Picasso Callback
                               @Override
                               public void onSuccess() {
                                   Log.d(TAG, "Picasso successfully loaded fetched image: " + gameImageUrl);
                               }

                               @Override
                               public void onError(Exception e) {
                                   Log.e(TAG, "Picasso failed to load fetched image: " + gameImageUrl, e);
                               }
                           });
                } catch (Exception picassoEx) {
                    Log.e(TAG, "Error loading fetched image with Picasso", picassoEx);
                    if (isAdded() && getContext() != null) {
                        ivGameCover.setImageResource(R.drawable.error_image);
                    }
                }
            } else if ((gameImageUrl == null || gameImageUrl.isEmpty()) && (fetchedImageUrl == null || fetchedImageUrl.isEmpty())){
                 // Set placeholder only if both initial and fetched are empty
                 if (isAdded() && getContext() != null) {
                    ivGameCover.setImageResource(R.drawable.placeholder_game);
                 }
            }
        } else if (gameImageUrl == null || gameImageUrl.isEmpty()){
             // Set placeholder if no key and initial was empty
             if (isAdded() && getContext() != null) {
                ivGameCover.setImageResource(R.drawable.placeholder_game);
             }
        }

        // Check and trigger YouTube search
        checkYouTubeSearchEnabled(fetchedTitle);

        // Fetch ProtonDB Compatibility
        if (result.containsKey("steamAppId")) {
            String steamAppId = (String) result.get("steamAppId");
            if (steamAppId != null && !steamAppId.isEmpty()) {
                Log.d(TAG, "Steam App ID encontrado: " + steamAppId + ". Buscando compatibilidade ProtonDB...");
                fetchProtonDbCompatibility(steamAppId);
            } else {
                Log.d(TAG, "Steam App ID não encontrado ou vazio. Pulando busca ProtonDB.");
                layoutProtonDb.setVisibility(View.GONE);
            }
        } else {
             Log.d(TAG, "Chave 'steamAppId' não encontrada nos detalhes do jogo. Pulando busca ProtonDB.");
             layoutProtonDb.setVisibility(View.GONE);
        }
    }

    private void fetchProtonDbCompatibility(String steamAppId) {
        if (!isAdded() || getActivity() == null) return;

        layoutProtonDb.setVisibility(View.VISIBLE);
        pbProtonDbLoading.setVisibility(View.VISIBLE);
        tvProtonDbRating.setVisibility(View.GONE);

        ProtonDbApiClient.getInstance().getGameCompatibility(steamAppId, new ProtonDbApiClient.ProtonApiCallback<String>() {
            @Override
            public void onSuccess(String rating) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) return; // Check again inside runOnUiThread
                    pbProtonDbLoading.setVisibility(View.GONE);
                    tvProtonDbRating.setVisibility(View.VISIBLE);
                    
                    String displayRating;
                    switch (rating) {
                        case "Not Found":
                            displayRating = "Não encontrado";
                            break;
                        case "No Reports":
                            displayRating = "Sem relatos";
                            break;
                        case "Unknown":
                            displayRating = "Desconhecido";
                            break;
                        default:
                            displayRating = rating; // Use the rating directly (e.g., Gold, Platinum)
                            break;
                    }
                    tvProtonDbRating.setText(displayRating);
                    Log.d(TAG, "ProtonDB Rating Display: " + displayRating);
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                     if (!isAdded() || getActivity() == null) return; // Check again inside runOnUiThread
                    pbProtonDbLoading.setVisibility(View.GONE);
                    tvProtonDbRating.setVisibility(View.VISIBLE);
                    tvProtonDbRating.setText("Erro");
                    Log.e(TAG, "Erro ao buscar compatibilidade ProtonDB", e);
                });
            }
        });
    }

    private void updateApiDataAndSearch() {
        if (!isAdded() || getContext() == null) return;

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_api_update_progress, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        com.LDGAMES.views.PulsingDotsLoaderView loaderView = dialogView.findViewById(R.id.loader_view);
        TextView tvApiSource = dialogView.findViewById(R.id.tv_api_source);
        ProgressBar pbProgress = dialogView.findViewById(R.id.pb_api_progress);
        TextView tvProgressPercentage = dialogView.findViewById(R.id.tv_progress_percentage);

        progressDialog = builder.create();
        progressDialog.show();

        try {
            hydraApiManager.forceUpdateAllData(new HydraApiManager.ApiDownloadProgressCallback<Void>() {
                @Override
                public void onProgressUpdate(int currentApi, int totalApis, long bytesDownloaded, long totalBytes, boolean indeterminate, String sourceName) {
                    if (!isAdded() || getActivity() == null || progressDialog == null || !progressDialog.isShowing()) return;
                    getActivity().runOnUiThread(() -> {
                        try {
                            if (!isAdded() || getActivity() == null || progressDialog == null || !progressDialog.isShowing()) return; // Check again
                            tvApiSource.setText("Fonte: " + (sourceName != null && !sourceName.isEmpty() ? sourceName : "API " + currentApi));
                            if (indeterminate || totalBytes <= 0) {
                                pbProgress.setIndeterminate(true);
                                tvProgressPercentage.setText("Baixando...");
                            } else {
                                pbProgress.setIndeterminate(false);
                                int progress = (int) ((bytesDownloaded * 100) / totalBytes);
                                pbProgress.setProgress(progress);
                                tvProgressPercentage.setText(progress + "%");
                            }
                        } catch (Exception e) {
                             Log.e(TAG, "Error updating progress dialog UI", e);
                        }
                    });
                }

                @Override
                public void onSuccess(Void result) {
                    if (!isAdded() || getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(requireContext(), "Atualização concluída!", Toast.LENGTH_SHORT).show();
                        searchDownloadLinksLocally();
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (!isAdded() || getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                         if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Log.e(TAG, "API Update Error", e);
                        Toast.makeText(requireContext(), "Erro na atualização: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        searchDownloadLinksLocally(); // Still try to search with existing data
                    });
                }

                @Override
                public void onComplete() {
                     if (progressDialog != null && progressDialog.isShowing() && isAdded() && getActivity() != null) {
                         getActivity().runOnUiThread(() -> {
                             if (progressDialog != null && progressDialog.isShowing()) { // Check again before dismissing
                                 progressDialog.dismiss();
                             }
                         });
                     }
                }
            });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for API update.", e);
             if (progressDialog != null && progressDialog.isShowing()) {
                 progressDialog.dismiss();
             }
             Toast.makeText(getContext(), "Erro ao iniciar atualização da API.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Methods related to Download ---
    private void searchDownloadLinksLocally() {
        Log.d(TAG, "searchDownloadLinksLocally called for game: " + gameTitle);
        if (!isAdded() || getContext() == null || gameTitle == null || gameTitle.isEmpty()) {
            Log.w(TAG, "Cannot search links: Fragment not added, context null, or game title empty.");
            if (isAdded() && getContext() != null) { // Show toast only if context is available
                 Toast.makeText(requireContext(), "Não foi possível buscar links (sem título).", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Show some indication that search is happening (optional)
        // Toast.makeText(requireContext(), "Buscando links localmente...", Toast.LENGTH_SHORT).show();

        hydraApiManager.searchDownloadLinksLocally(gameTitle, new HydraApiManager.ApiLocalSearchCallback<List<DownloadLink>>() {
            @Override
            public void onSuccess(List<DownloadLink> foundLinks) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) return; // Check again
                    if (foundLinks != null && !foundLinks.isEmpty()) {
                        Log.d(TAG, "Found " + foundLinks.size() + " download links locally.");
                        showDownloadDialog(foundLinks);
                    } else {
                        Log.d(TAG, "No download links found locally for: " + gameTitle);
                        Toast.makeText(requireContext(), "Nenhum link de download encontrado localmente.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                     if (!isAdded() || getActivity() == null) return; // Check again
                    Log.e(TAG, "Error searching download links locally for: " + gameTitle, e);
                    Toast.makeText(requireContext(), "Erro ao buscar links de download.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showDownloadDialog(List<DownloadLink> links) { // Keep the parameter
        Log.d(TAG, "showDownloadDialog called with " + links.size() + " links."); // Keep the log
        if (!isAdded() || getActivity() == null) return;
        try {
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_download_links, null);
            RecyclerView rvDialogLinks = dialogView.findViewById(R.id.rv_dialog_download_links);
            rvDialogLinks.setLayoutManager(new LinearLayoutManager(requireContext()));

            AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Links de Download")
                .setView(dialogView)
                .setPositiveButton("Fechar", null) // Changed from Cancelar to Fechar
                .create();

            // Use the 'links' parameter passed to the method
            DownloadLinkAdapter adapter = new DownloadLinkAdapter(links, link -> {
                // Use InternalBrowserActivity like in 'files' project
                Intent intent = new Intent(requireContext(), InternalBrowserActivity.class);
                intent.putExtra("url", link.getUrl());
                startActivity(intent);
                dialog.dismiss(); // Dismiss dialog on link click
            });
            rvDialogLinks.setAdapter(adapter);

            dialog.show();

        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available for showing download dialog.", e);
            Toast.makeText(requireContext(), "Erro ao exibir diálogo de download.", Toast.LENGTH_SHORT).show(); // Added toast for better feedback
        }
    }

    private void initiateDownload(DownloadLink link) {
        Log.d(TAG, "Initiating download for: " + link.getName() + " URL: " + link.getUrl());
        if (!isAdded() || getContext() == null) return;
        // TODO: Implement download initiation (e.g., using DownloadManager or starting DownloadProgressActivity)
        String url = link.getUrl();
        String fileName = URLUtil.guessFileName(url, null, null);
        Intent intent = new Intent(getContext(), DownloadProgressActivity.class);
        intent.putExtra("downloadUrl", url);
        intent.putExtra("fileName", fileName);
        intent.putExtra("gameTitle", gameTitle);
        intent.putExtra("gameImageUrl", gameImageUrl);
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) downloadDir.mkdirs();
        String destinationPath = new File(downloadDir, fileName).getAbsolutePath();
        intent.putExtra("destinationPath", destinationPath);
        getContext().startActivity(intent);
        Toast.makeText(requireContext(), "Iniciando download de: " + fileName, Toast.LENGTH_SHORT).show();
    }

    // --- Methods related to YouTube Search ---
    private void checkYouTubeSearchEnabled(String titleToSearch) {
        if (!isAdded() || getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_YOUTUBE_SEARCH_ENABLED, false);

        if (enabled && titleToSearch != null && !titleToSearch.isEmpty()) {
            String searchTermTemplate = prefs.getString(KEY_YOUTUBE_SEARCH_TERM, DEFAULT_YOUTUBE_SEARCH_TERM);
            String finalSearchTerm = searchTermTemplate.replace(NAME_PLACEHOLDER, titleToSearch);
            Log.d(TAG, "YouTube search enabled. Searching for: " + finalSearchTerm);
            searchYouTubeVideos(finalSearchTerm);
        } else {
            Log.d(TAG, "YouTube search disabled or title is empty.");
            tvYouTubeLabel.setVisibility(View.GONE);
            rvYouTubeVideos.setVisibility(View.GONE);
        }
    }

    private void searchYouTubeVideos(String query) {
        if (!isAdded() || getContext() == null) return;
        Log.d(TAG, "Searching YouTube for: " + query);
        tvYouTubeLabel.setVisibility(View.VISIBLE); // Show label while searching
        rvYouTubeVideos.setVisibility(View.GONE); // Hide list initially
        // Consider adding a loading indicator for YouTube videos

        YouTubeSearchUtil.searchYouTubeVideos(query, new YouTubeSearchUtil.YouTubeSearchCallback() {
            @Override
            public void onSuccess(List<YouTubeVideo> videos) {
                 if (!isAdded() || getActivity() == null) return;
                 getActivity().runOnUiThread(() -> {
                     if (!isAdded() || getActivity() == null) return; // Check again
                     if (videos != null && !videos.isEmpty()) {
                         Log.d(TAG, "Found " + videos.size() + " YouTube videos.");
                         youtubeVideos.clear();
                         youtubeVideos.addAll(videos);
                         youtubeVideoAdapter.notifyDataSetChanged();
                         tvYouTubeLabel.setVisibility(View.VISIBLE);
                         rvYouTubeVideos.setVisibility(View.VISIBLE);
                     } else {
                         Log.d(TAG, "No YouTube videos found for query: " + query);
                         tvYouTubeLabel.setVisibility(View.GONE);
                         rvYouTubeVideos.setVisibility(View.GONE);
                     }
                 });
            }

            @Override
            public void onError(Exception e) {
                 if (!isAdded() || getActivity() == null) return;
                 getActivity().runOnUiThread(() -> {
                     if (!isAdded() || getActivity() == null) return; // Check again
                     Log.e(TAG, "Error searching YouTube videos", e);
                     tvYouTubeLabel.setVisibility(View.GONE);
                     rvYouTubeVideos.setVisibility(View.GONE);
                 });
            }
        });
    }

    // --- Helper Methods for UI State ---
    private void showLoading() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (tvError != null) tvError.setVisibility(View.GONE);
        // Hide content views if necessary
        if (fragmentView != null) {
             View contentCard = fragmentView.findViewById(R.id.card_content);
             if (contentCard != null) contentCard.setVisibility(View.GONE);
        }
        if (layoutProtonDb != null) layoutProtonDb.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (tvError != null) tvError.setVisibility(View.GONE);
        // Show content views
         if (fragmentView != null) {
             View contentCard = fragmentView.findViewById(R.id.card_content);
             if (contentCard != null) contentCard.setVisibility(View.VISIBLE);
        }
        // ProtonDB visibility is handled separately after fetch
    }

    private void showError(String message) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (tvError != null) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(message);
        }
        // Hide content views
         if (fragmentView != null) {
             View contentCard = fragmentView.findViewById(R.id.card_content);
             if (contentCard != null) contentCard.setVisibility(View.GONE);
        }
        if (layoutProtonDb != null) layoutProtonDb.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (youtubePlayerViewCover != null) {
            // Release YouTube player resources
            getLifecycle().removeObserver(youtubePlayerViewCover);
            youtubePlayerViewCover.release();
            youtubePlayerViewCover = null;
            youTubePlayerInstance = null;
        }
        // Clean up view reference
        fragmentView = null;
        // Consider shutting down ProtonDB client executor if appropriate
        // ProtonDbApiClient.getInstance().shutdown(); 
    }

} // End of GameDetailFragment class

