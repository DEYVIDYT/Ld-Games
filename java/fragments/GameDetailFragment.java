package com.LDGAMES.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.activities.InternalBrowserActivity;
import com.LDGAMES.activities.SettingsActivity;
import com.LDGAMES.adapters.DownloadLinkAdapter;
import com.LDGAMES.adapters.ScreenshotAdapter;
import com.LDGAMES.models.DownloadLink;
import com.LDGAMES.utils.FloatingProgressManager;
import com.LDGAMES.utils.HydraApiManager;
import com.LDGAMES.utils.IGDBApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GameDetailFragment extends Fragment {

    private static final String TAG = "GameDetailFragment";
    private String gameUrl;
    private String gameTitle;
    private String gameImageUrl;
    private String gameId;

    private ProgressBar progressBar;
    private TextView tvError;
    private ShapeableImageView ivGameCover;
    private TextView tvGameTitle;
    private TextView tvGameDescription;
    private RecyclerView rvScreenshots;
    private Button btnDownload;

    private List<String> screenshots = new ArrayList<>();
    private List<DownloadLink> downloadLinks = new ArrayList<>();
    private ScreenshotAdapter screenshotAdapter;
    // Context is handled by requireContext() when needed
    private IGDBApiClient apiClient;
    private HydraApiManager hydraApiManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            gameUrl = args.getString("gameUrl", "");
            gameTitle = args.getString("gameTitle", "");
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

        // Initialize APIs using requireContext() for safety
        try {
            apiClient = IGDBApiClient.getInstance(requireContext());
            hydraApiManager = HydraApiManager.getInstance(requireContext());
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fragment not attached to a context.", e);
            return; // Cannot proceed without context
        }

        if (!isAdded() || getActivity() == null) return;

        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(gameTitle);
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

        // Use requireContext() for adapters and layout managers
        rvScreenshots.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        screenshotAdapter = new ScreenshotAdapter(screenshots, requireContext());
        rvScreenshots.setAdapter(screenshotAdapter);

        btnDownload.setOnClickListener(v -> {
            if (isAdded()) {
                try {
                    // Sempre forçar a atualização da API ao clicar no botão de download
                    updateApiDataAndSearch();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Context not available for download button click.", e);
                }
            }
        });

        tvGameTitle.setText(gameTitle);

        // PICASSO FIX: Ensure context is valid before calling Picasso
        // This is likely the area around line 146 from the error
        if (gameImageUrl != null && !gameImageUrl.isEmpty()) {
            try {
                Log.d(TAG, "Loading initial image: " + gameImageUrl);
                Picasso.get() // Picasso.get() should work if initialized correctly (often in Application class)
                       // If it still fails, ensure Picasso is initialized in your Application class
                       // or try Picasso.with(requireContext()) if using an older version
                       .load(gameImageUrl)
                       .placeholder(R.drawable.placeholder_game)
                       .error(R.drawable.error_image)
                       .into(ivGameCover, new Callback() {
                            @Override public void onSuccess() { Log.d(TAG, "Initial image loaded."); }
                            @Override public void onError(Exception e) { Log.e(TAG, "Error loading initial image", e); }
                        });
            } catch (IllegalStateException e) {
                 Log.e(TAG, "Context not available for initial Picasso load.", e);
            } catch (Exception e) {
                 Log.e(TAG, "General error during initial Picasso load", e);
            }
        } else {
             Log.w(TAG, "Initial game image URL is null or empty.");
             // Set placeholder if URL is invalid
             try {
                ivGameCover.setImageResource(R.drawable.placeholder_game);
             } catch (Exception e) {
                 Log.e(TAG, "Error setting placeholder resource", e);
             }
        }

        if (gameId != null && !gameId.isEmpty()) {
            if (gameUrl != null && gameUrl.startsWith("igdb://")) {
                gameId = gameUrl.replace("igdb://", "");
            }
            loadGameDetails();
        } else {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Erro: ID do jogo não fornecido");
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateApiDataAndSearch() {
        if (!isAdded()) return;
        try {
            progressBar.setVisibility(View.VISIBLE);
            tvError.setVisibility(View.GONE);
            
            // Mostrar toast informando que está atualizando
            Toast.makeText(requireContext(), "Atualizando dados da API...", Toast.LENGTH_SHORT).show();
            
            hydraApiManager.forceUpdateAllData(new HydraApiManager.ApiDownloadProgressCallback<Void>() {
                @Override
                public void onProgressUpdate(int currentApi, int totalApis, long bytesDownloaded, long totalBytes, boolean indeterminate) {
                    // Não mostrar progresso
                }
                
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
                public void onComplete() {
                    // Não fazer nada
                }
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
                        if (result.containsKey("title")) {
                            tvGameTitle.setText((String) result.get("title"));
                            gameTitle = (String) result.get("title");
                        }
                        if (result.containsKey("imageUrl")) {
                            String imageUrl = (String) result.get("imageUrl");
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                try {
                                    // PICASSO FIX: Use Picasso.get() here too
                                    Picasso.get()
                                           .load(imageUrl)
                                           .placeholder(R.drawable.placeholder_game)
                                           .error(R.drawable.error_image)
                                           .into(ivGameCover, new Callback() {
                                                @Override public void onSuccess() { Log.d(TAG, "Details image loaded."); }
                                                @Override public void onError(Exception e) { Log.e(TAG, "Error loading details image", e); }
                                            });
                                } catch (IllegalStateException e) {
                                     Log.e(TAG, "Context not available for details Picasso load.", e);
                                } catch (Exception e) {
                                     Log.e(TAG, "General error during details Picasso load", e);
                                }
                            }
                        }
                        if (result.containsKey("description")) {
                            tvGameDescription.setText((String) result.get("description"));
                        }
                        if (result.containsKey("screenshots")) {
                            screenshots.clear();
                            screenshots.addAll((List<String>) result.get("screenshots"));
                            screenshotAdapter.notifyDataSetChanged();
                        }
                        btnDownload.setEnabled(true);
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
                            Log.w(TAG, "No local links found for " + gameTitle);
                            try {
                                Toast.makeText(requireContext(), "Nenhum link encontrado localmente para " + gameTitle + ". Verifique se os dados foram atualizados.", Toast.LENGTH_LONG).show();
                            } catch (IllegalStateException ise) {
                                Log.e(TAG, "Context not available for 'no links found' toast.", ise);
                            }
                        }
                    });
                }
                @Override
                public void onError(Exception e) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "Error during local link search", e);
                         try {
                            Toast.makeText(requireContext(), "Erro ao buscar links locais: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            if (downloadLinks.isEmpty()) {
                Toast.makeText(requireContext(), "Nenhum link de download disponível", Toast.LENGTH_SHORT).show();
                return;
            }
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
            RecyclerView rvDownloadLinks = dialogView.findViewById(R.id.rv_download_links);
            rvDownloadLinks.setLayoutManager(new LinearLayoutManager(requireContext()));
            DownloadLinkAdapter adapter = new DownloadLinkAdapter(downloadLinks, link -> {
                if (link == null || link.getUrl() == null || link.getUrl().isEmpty()) {
                    Toast.makeText(requireContext(), "Erro: URL inválida", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Abrir todos os links no navegador interno
                try {
                    Intent browserIntent = InternalBrowserActivity.createIntent(requireContext(), link.getUrl(), link.getName());
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Erro ao abrir navegador interno: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            rvDownloadLinks.setAdapter(adapter);
            MaterialButton btnCloseDialog = dialogView.findViewById(R.id.btn_close_dialog);
            Dialog dialog = new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create();
            btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Context not available to show download dialog.", e);
        }
    }
    

    


    // No need for onDetach context nulling if using requireContext()
}

