package com.LDGAMES.activities;

import android.content.Context;
import android.content.Intent; // Import Intent
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.LDGAMES.R;
import com.LDGAMES.adapters.CompletedDownloadAdapter;
import com.LDGAMES.adapters.DownloadProgressAdapter;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.utils.DownloadManager;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.FileUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class DownloadProgressActivity extends AppCompatActivity implements
        DownloadManager.DownloadListener,
        DownloadProgressAdapter.DownloadActionListener,
        CompletedDownloadAdapter.OnDownloadActionListener {

    private RecyclerView recyclerView;
    private DownloadProgressAdapter adapter;
    private List<DownloadInfo> downloads;
    private TabLayout tabLayout;
    private View layoutNoActiveDownloads;
    private View layoutNoCompletedDownloads;
    private RecyclerView rvCompletedDownloads;
    private CompletedDownloadAdapter completedAdapter;
    private DownloadManager downloadManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeManager.getInstance().applyDynamicColors(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_progress);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_active_downloads);
        rvCompletedDownloads = findViewById(R.id.rv_completed_downloads);
        layoutNoActiveDownloads = findViewById(R.id.layout_no_active_downloads);
        layoutNoCompletedDownloads = findViewById(R.id.layout_no_completed_downloads);
        tabLayout = findViewById(R.id.tab_layout);

        // Configurar RecyclerView para downloads ativos
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        downloads = new ArrayList<>();
        adapter = new DownloadProgressAdapter(downloads, this);
        recyclerView.setAdapter(adapter);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        // Configurar RecyclerView para downloads concluídos
        rvCompletedDownloads.setLayoutManager(new LinearLayoutManager(this));
        downloadManager = DownloadManager.getInstance(this);
        List<DownloadInfo> completedDownloadsList = downloadManager.getCompletedDownloads();
        completedAdapter = new CompletedDownloadAdapter(completedDownloadsList, this);
        rvCompletedDownloads.setAdapter(completedAdapter);

        // Configurar TabLayout
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateTabVisibility(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        downloadManager.addDownloadListener(this);
        refreshDownloads();
        downloadManager.processQueue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDownloads();
        downloadManager.processQueue();
    }

    private void updateTabVisibility(int position) {
        boolean isActiveTab = (position == 0);

        recyclerView.setVisibility(isActiveTab ? View.VISIBLE : View.GONE);
        rvCompletedDownloads.setVisibility(!isActiveTab ? View.VISIBLE : View.GONE);

        layoutNoActiveDownloads.setVisibility(isActiveTab && downloads.isEmpty() ? View.VISIBLE : View.GONE);
        layoutNoCompletedDownloads.setVisibility(!isActiveTab && completedAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void refreshDownloads() {
        // Atualizar downloads ativos
        downloads.clear();
        downloads.addAll(downloadManager.getActiveDownloads());
        Collections.sort(downloads, (d1, d2) -> Long.compare(d2.getStartTime(), d1.getStartTime()));
        adapter.notifyDataSetChanged();

        // Atualizar downloads concluídos
        refreshCompletedDownloads();

        // Atualizar visibilidade com base na tab selecionada
        updateTabVisibility(tabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        downloadManager.removeDownloadListener(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- Implementação de DownloadManager.DownloadListener --- 

    @Override
    public void onDownloadAdded(DownloadInfo downloadInfo) {
        runOnUiThread(() -> {
            // Evitar adicionar duplicado se já existe na lista (pode acontecer em recriação da activity)
            if (downloads.stream().noneMatch(d -> Objects.equals(d.getFilePath(), downloadInfo.getFilePath()))) {
                 downloads.add(0, downloadInfo);
                 adapter.notifyItemInserted(0);
                 if (tabLayout.getSelectedTabPosition() == 0) {
                     recyclerView.scrollToPosition(0);
                 }
            }
            updateTabVisibility(tabLayout.getSelectedTabPosition());
        });
    }

    @Override
    public void onDownloadUpdated(DownloadInfo downloadInfo) {
        runOnUiThread(() -> {
            int index = -1;
            for (int i = 0; i < downloads.size(); i++) {
                // Usar filePath para identificar o item na lista da UI
                if (Objects.equals(downloads.get(i).getFilePath(), downloadInfo.getFilePath())) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                DownloadInfo existingInfo = downloads.get(index);
                boolean statusChanged = existingInfo.getStatus() != downloadInfo.getStatus();

                // Atualizar dados no objeto da lista
                existingInfo.setStatus(downloadInfo.getStatus());
                existingInfo.setProgress(downloadInfo.getProgress());
                existingInfo.setDownloadedSize(downloadInfo.getDownloadedSize());
                existingInfo.setFileSize(downloadInfo.getFileSize());
                existingInfo.setEndTime(downloadInfo.getEndTime());
                existingInfo.setLastPauseTime(downloadInfo.getLastPauseTime());
                existingInfo.setLastResumeTime(downloadInfo.getLastResumeTime());
                existingInfo.setErrorMessage(downloadInfo.getErrorMessage());
                existingInfo.setSpeed(downloadInfo.getSpeed());
                existingInfo.setEstimatedTimeRemaining(downloadInfo.getEstimatedTimeRemaining());
                // Atualizar URL ativa caso tenha mudado (múltiplas fontes)
                existingInfo.setUrl(downloadInfo.getUrl());

                if (statusChanged) {
                    adapter.notifyItemChanged(index);
                } else {
                    Bundle payload = new Bundle();
                    payload.putInt("progress", existingInfo.getProgress());
                    payload.putLong("downloadedSize", existingInfo.getDownloadedSize());
                    payload.putLong("fileSize", existingInfo.getFileSize());
                    payload.putLong("speed", existingInfo.getSpeed());
                    payload.putString("eta", existingInfo.getEstimatedTimeRemaining());
                    adapter.notifyItemChanged(index, payload);
                }
            } else if (downloadInfo.getStatus() != DownloadInfo.STATUS_COMPLETED &&
                       downloadInfo.getStatus() != DownloadInfo.STATUS_FAILED &&
                       downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
                // Se não encontrou na lista ativa, mas não é um estado final, adicionar
                 downloads.add(0, downloadInfo);
                 adapter.notifyItemInserted(0);
                 if (tabLayout.getSelectedTabPosition() == 0) {
                     recyclerView.scrollToPosition(0);
                 }
            }
            updateTabVisibility(tabLayout.getSelectedTabPosition());
        });
    }

    @Override
    public void onDownloadCompleted(DownloadInfo downloadInfo) {
        runOnUiThread(() -> {
            int index = -1;
            for (int i = 0; i < downloads.size(); i++) {
                // Usar filePath para identificar
                if (Objects.equals(downloads.get(i).getFilePath(), downloadInfo.getFilePath())) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                downloads.remove(index);
                adapter.notifyItemRemoved(index);
                Toast.makeText(this, "Download concluído: " + downloadInfo.getFileName(), Toast.LENGTH_SHORT).show();
                refreshCompletedDownloads(); // Atualiza a lista de concluídos
                updateTabVisibility(tabLayout.getSelectedTabPosition());
            }
        });
    }

    @Override
    public void onDownloadFailed(DownloadInfo downloadInfo, String reason) {
        runOnUiThread(() -> {
            int index = -1;
            for (int i = 0; i < downloads.size(); i++) {
                 // Usar filePath para identificar
                if (Objects.equals(downloads.get(i).getFilePath(), downloadInfo.getFilePath())) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                DownloadInfo failedInfo = downloads.get(index);
                failedInfo.setStatus(DownloadInfo.STATUS_FAILED);
                failedInfo.setErrorMessage(reason);
                adapter.notifyItemChanged(index);
                Toast.makeText(this, "Falha no download: " + reason, Toast.LENGTH_SHORT).show();
                updateTabVisibility(tabLayout.getSelectedTabPosition());
            } else {
                 // Se não estava na lista ativa, apenas mostrar o Toast
                 Toast.makeText(this, "Falha no download: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshCompletedDownloads() {
        if (completedAdapter != null) {
            List<DownloadInfo> completedList = downloadManager.getCompletedDownloads();
            completedAdapter.updateDownloads(completedList);
        }
    }

    // --- Implementação de DownloadProgressAdapter.DownloadActionListener --- 

    @Override
    public void onPauseResume(DownloadInfo downloadInfo) {
        try {
            downloadManager.pauseResumeDownload(downloadInfo);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao pausar/retomar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCancel(DownloadInfo downloadInfo) {
        // Correção Erro 1: Usar getDownloadInfoByPath
        DownloadInfo currentInfo = downloadManager.getDownloadInfoByPath(downloadInfo.getFilePath());
        if (currentInfo != null && currentInfo.getStatus() == DownloadInfo.STATUS_CANCELLED) {
            // Se já está cancelado, apenas remover da UI
            int index = -1;
            for (int i = 0; i < downloads.size(); i++) {
                 // Usar filePath para identificar
                if (Objects.equals(downloads.get(i).getFilePath(), downloadInfo.getFilePath())) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                downloads.remove(index);
                adapter.notifyItemRemoved(index);
                updateTabVisibility(tabLayout.getSelectedTabPosition());
            }
            return;
        }

        // Se não está cancelado, mostrar diálogo de confirmação
        new MaterialAlertDialogBuilder(this)
            .setTitle("Cancelar Download")
            .setMessage("Tem certeza que deseja cancelar o download de " + downloadInfo.getFileName() + "?")
            .setPositiveButton("Sim", (dialog, which) -> {
                try {
                    downloadManager.cancelDownload(downloadInfo);
                    // Remover da UI imediatamente após confirmação (o listener tratará a atualização final)
                    int index = -1;
                    for (int i = 0; i < downloads.size(); i++) {
                         // Usar filePath para identificar
                        if (Objects.equals(downloads.get(i).getFilePath(), downloadInfo.getFilePath())) {
                            index = i;
                            break;
                        }
                    }
                    if (index != -1) {
                        downloads.remove(index);
                        adapter.notifyItemRemoved(index);
                        updateTabVisibility(tabLayout.getSelectedTabPosition());
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao cancelar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Não", null)
            .show();
    }

    @Override
    public void onItemLongClick(DownloadInfo downloadInfo, View view) {
        if (downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
             showDownloadDetailsDialog(downloadInfo);
        }
    }

    // --- Implementação de CompletedDownloadAdapter.OnDownloadActionListener --- 

    @Override
    public void onOpenFileClick(DownloadInfo downloadInfo) {
        FileUtils.openFile(this, downloadInfo.getFilePath(), downloadInfo.getMimeType());
    }

    @Override
    public void onDownloadDeleted(DownloadInfo downloadInfo) {
        Toast.makeText(this, "Download excluído: " + downloadInfo.getFileName(), Toast.LENGTH_SHORT).show();
        updateTabVisibility(tabLayout.getSelectedTabPosition());
    }

    // --- Diálogo de Detalhes --- 

    private void showDownloadDetailsDialog(DownloadInfo downloadInfo) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_details, null);

        TextView tvFileName = dialogView.findViewById(R.id.tv_file_name_value);
        TextView tvPageUrl = dialogView.findViewById(R.id.tv_page_url_value);
        TextView tvDownloadUrl = dialogView.findViewById(R.id.tv_download_url_value);
        TextView tvFilePath = dialogView.findViewById(R.id.tv_file_path_value);
        TextView tvFileSize = dialogView.findViewById(R.id.tv_file_size_value);
        TextView tvDownloaded = dialogView.findViewById(R.id.tv_downloaded_value);
        TextView tvAvgSpeed = dialogView.findViewById(R.id.tv_avg_speed_value);
        TextView tvDateAdded = dialogView.findViewById(R.id.tv_date_added_value);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_close);

        tvFileName.setText(downloadInfo.getFileName());
        // A lógica de 'originalUrl' pode precisar ser revista se não for mais usada
        String originalUrl = downloadManager.getOriginalUrl(downloadInfo.getUrl());
        tvPageUrl.setText(originalUrl != null ? originalUrl : "Não disponível");
        // Mostrar a URL ativa atual
        tvDownloadUrl.setText(downloadInfo.getUrl());
        tvFilePath.setText(downloadInfo.getFilePath());
        tvFileSize.setText(downloadInfo.getFileSize() > 0 ? downloadInfo.getFormattedFileSize() : "Desconhecido");

        String downloadedText = downloadInfo.getFormattedDownloadedSize();
        if (downloadInfo.getProgress() > 0 && downloadInfo.getStatus() != DownloadInfo.STATUS_COMPLETED) {
            downloadedText += " (" + downloadInfo.getProgress() + "%)";
        }
        tvDownloaded.setText(downloadedText);

        long avgSpeed = calculateAverageSpeed(downloadInfo);
        tvAvgSpeed.setText(avgSpeed > 0 ? formatSpeed(avgSpeed) : "Não disponível");

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        tvDateAdded.setText(sdf.format(new Date(downloadInfo.getStartTime())));

        AlertDialog dialog = builder.setView(dialogView).create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private long calculateAverageSpeed(DownloadInfo downloadInfo) {
        if (downloadInfo.getDownloadedSize() <= 0 || downloadInfo.getStartTime() <= 0) {
            return 0;
        }
        long endTime = (downloadInfo.getStatus() == DownloadInfo.STATUS_COMPLETED && downloadInfo.getEndTime() > 0) ?
                downloadInfo.getEndTime() : System.currentTimeMillis();
        long durationMillis = endTime - downloadInfo.getStartTime();
        if (durationMillis <= 0) {
            return 0;
        }
        return (downloadInfo.getDownloadedSize() * 1000) / durationMillis;
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "0 B/s";
        final String[] units = new String[]{"B/s", "KB/s", "MB/s", "GB/s", "TB/s"};
        int digitGroups = (int) (Math.log10(bytesPerSecond) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytesPerSecond / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}

