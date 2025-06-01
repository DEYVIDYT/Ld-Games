package com.LDGAMES.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.utils.DownloadManager; // Importar DownloadManager para deletar
import com.LDGAMES.utils.FileUtils; // Importar FileUtils para deletar
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CompletedDownloadAdapter extends RecyclerView.Adapter<CompletedDownloadAdapter.CompletedDownloadViewHolder> {

    private List<DownloadInfo> downloads;
    private final OnDownloadActionListener listener;

    public interface OnDownloadActionListener {
        void onOpenFileClick(DownloadInfo downloadInfo);
        // Adicionar callback para notificar a Activity sobre a exclusão
        void onDownloadDeleted(DownloadInfo downloadInfo);
    }

    public CompletedDownloadAdapter(List<DownloadInfo> downloads, OnDownloadActionListener listener) {
        // Usar uma cópia da lista para evitar modificações externas inesperadas
        this.downloads = new ArrayList<>(downloads);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CompletedDownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download_completed, parent, false);
        return new CompletedDownloadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CompletedDownloadViewHolder holder, int position) {
        DownloadInfo downloadInfo = downloads.get(position);
        holder.bind(downloadInfo);
    }

    @Override
    public int getItemCount() {
        return downloads.size();
    }

    /**
     * Atualiza a lista de downloads exibida pelo adapter.
     *
     * @param newDownloads A nova lista de downloads concluídos.
     */
    @SuppressLint("NotifyDataSetChanged") // Justificado pois a lista inteira está mudando
    public void updateDownloads(List<DownloadInfo> newDownloads) {
        this.downloads.clear();
        this.downloads.addAll(newDownloads);
        notifyDataSetChanged(); // Notifica o RecyclerView para redesenhar tudo
    }

    class CompletedDownloadViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFileName;
        private final TextView tvFileSizeDate;
        private final MaterialButton btnOpenFile;
        private final MaterialButton btnDeleteFile;

        public CompletedDownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSizeDate = itemView.findViewById(R.id.tv_file_size); // ID corrigido
            btnOpenFile = itemView.findViewById(R.id.btn_open_file);
            btnDeleteFile = itemView.findViewById(R.id.btn_delete_file);
        }

        public void bind(final DownloadInfo downloadInfo) {
            tvFileName.setText(downloadInfo.getFileName());

            // Formatar informações do arquivo
            String sizeInfo = downloadInfo.getFormattedFileSize();

            // Adicionar data de conclusão se disponível
            if (downloadInfo.getEndTime() > 0) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                String completedDate = dateFormat.format(new Date(downloadInfo.getEndTime()));
                sizeInfo += " • Concluído em " + completedDate;
            }

            tvFileSizeDate.setText(sizeInfo);

            // Configurar botões
            btnOpenFile.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenFileClick(downloadInfo);
                }
            });

            btnDeleteFile.setOnClickListener(v -> {
                // Adicionar diálogo de confirmação para exclusão
                new MaterialAlertDialogBuilder(itemView.getContext())
                    .setTitle("Excluir Arquivo")
                    .setMessage("Tem certeza que deseja excluir o arquivo '" + downloadInfo.getFileName() + "'? Esta ação não pode ser desfeita.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Excluir", (dialog, which) -> {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            // Remover do DownloadManager - Método inexistente, a lógica de exclusão deve ser tratada na Activity ou no próprio Manager
                            // DownloadManager.getInstance(itemView.getContext()).deleteCompletedDownload(downloadInfo);
                            
                            // Remover da lista local do adapter
                            downloads.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, downloads.size()); // Atualizar posições
                            
                            // Notificar a Activity
                            if (listener != null) {
                                listener.onDownloadDeleted(downloadInfo);
                            }
                        }
                    })
                    .show();
            });

            // Configurar clique no item para abrir o arquivo
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenFileClick(downloadInfo);
                }
            });
        }
    }
}

