package com.LDGAMES.adapters;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.DownloadInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

public class DownloadProgressAdapter extends RecyclerView.Adapter<DownloadProgressAdapter.DownloadProgressViewHolder> {

    private final List<DownloadInfo> downloads;
    private final DownloadActionListener listener;

    public interface DownloadActionListener {
        void onPauseResume(DownloadInfo downloadInfo);
        void onCancel(DownloadInfo downloadInfo);
        void onItemLongClick(DownloadInfo downloadInfo, View view);
    }

    public DownloadProgressAdapter(List<DownloadInfo> downloads, DownloadActionListener listener) {
        this.downloads = downloads;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DownloadProgressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download_progress, parent, false);
        return new DownloadProgressViewHolder(view);
    }

    // Overload for full bind
    @Override
    public void onBindViewHolder(@NonNull DownloadProgressViewHolder holder, int position) {
        DownloadInfo downloadInfo = downloads.get(position);
        holder.bind(downloadInfo, false); // Full bind
    }

    // Overload for partial bind with payloads
    @Override
    public void onBindViewHolder(@NonNull DownloadProgressViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // No payload, do a full bind
            onBindViewHolder(holder, position);
        } else {
            // Payload received, do a partial bind
            DownloadInfo downloadInfo = downloads.get(position);
            Bundle payloadBundle = (Bundle) payloads.get(0); // Assuming the payload is a Bundle
            holder.updateProgress(downloadInfo, payloadBundle);
        }
    }


    @Override
    public int getItemCount() {
        return downloads.size();
    }

    class DownloadProgressViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDownloadName;
        private final TextView tvDownloadStatus;
        private final LinearProgressIndicator progressBar;
        private final TextView tvDownloadProgress;
        private final TextView tvDownloadSpeed;
        private final TextView tvDownloadParts; // Mantido, mas pode não ser atualizado no payload
        private final TextView tvDownloadEta;
        private final MaterialButton btnPauseResume;
        private final MaterialButton btnCancel;

        public DownloadProgressViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDownloadName = itemView.findViewById(R.id.tv_download_name);
            tvDownloadStatus = itemView.findViewById(R.id.tv_download_status);
            progressBar = itemView.findViewById(R.id.progress_bar);
            tvDownloadProgress = itemView.findViewById(R.id.tv_download_progress);
            tvDownloadSpeed = itemView.findViewById(R.id.tv_download_speed);
            tvDownloadParts = itemView.findViewById(R.id.tv_download_parts);
            tvDownloadEta = itemView.findViewById(R.id.tv_download_eta);
            btnPauseResume = itemView.findViewById(R.id.btn_pause_resume);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
            
            // Configurar long click listener para mostrar detalhes do download
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition(); // Use getBindingAdapterPosition()
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    DownloadInfo info = downloads.get(position);
                    // Não mostrar detalhes para downloads cancelados (já tratado na Activity, mas reforça aqui)
                    if (info.getStatus() != DownloadInfo.STATUS_CANCELLED) {
                         listener.onItemLongClick(info, v);
                    }
                    return true;
                }
                return false;
            });
        }

        // Método para bind completo
        public void bind(final DownloadInfo downloadInfo, boolean isPayloadUpdate) {
            // Atualizar campos que não mudam com frequência ou precisam de rebind completo
            tvDownloadName.setText(downloadInfo.getFileName());
            tvDownloadStatus.setText(downloadInfo.getStatusText());
            updateButtonState(downloadInfo);

            // Atualizar campos de progresso (também chamados no updateProgress)
            updateProgressFields(downloadInfo, downloadInfo.getProgress(), downloadInfo.getDownloadedSize(), downloadInfo.getFileSize(), downloadInfo.getSpeed(), downloadInfo.getEstimatedTimeRemaining());

            // Configurar listeners apenas no bind completo para evitar duplicação
            if (!isPayloadUpdate) {
                btnPauseResume.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onPauseResume(downloads.get(position));
                    }
                });

                btnCancel.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onCancel(downloads.get(position));
                    }
                });
            }
        }

        // Método para atualização parcial via payload
        public void updateProgress(DownloadInfo downloadInfo, Bundle payload) {
            // Atualizar apenas os campos relevantes do payload
            int progress = payload.getInt("progress", downloadInfo.getProgress());
            long downloadedSize = payload.getLong("downloadedSize", downloadInfo.getDownloadedSize());
            long fileSize = payload.getLong("fileSize", downloadInfo.getFileSize());
            long speed = payload.getLong("speed", downloadInfo.getSpeed());
            String eta = payload.getString("eta", downloadInfo.getEstimatedTimeRemaining());

            updateProgressFields(downloadInfo, progress, downloadedSize, fileSize, speed, eta);

            // O status pode ter mudado (ex: PAUSING, RESUMING), então atualizamos os botões também
            // Mas pegamos o status atual do objeto, não do payload
            updateButtonState(downloadInfo);
        }

        // Método auxiliar para atualizar campos relacionados ao progresso
        private void updateProgressFields(DownloadInfo downloadInfo, int progress, long downloadedSize, long fileSize, long speed, String eta) {
            progressBar.setProgressCompat(progress, true); // Usar setProgressCompat para animação suave

            StringBuilder progressText = new StringBuilder();
            progressText.append(progress).append("%");
            if (fileSize > 0) {
                progressText.append(" • ")
                          .append(formatFileSize(downloadedSize))
                          .append(" de ")
                          .append(formatFileSize(fileSize)); // Usar formatFileSize para consistência
            } else {
                 progressText.append(" • ").append(formatFileSize(downloadedSize));
            }
            tvDownloadProgress.setText(progressText.toString());

            // Velocidade
            if ((downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) && speed > 0) {
                tvDownloadSpeed.setText(formatSpeed(speed));
                tvDownloadSpeed.setVisibility(View.VISIBLE);
            } else {
                tvDownloadSpeed.setVisibility(View.GONE);
            }

            // ETA
            if ((downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) && eta != null && !eta.isEmpty()) {
                tvDownloadEta.setText("Tempo restante: " + eta);
                tvDownloadEta.setVisibility(View.VISIBLE);
            } else {
                tvDownloadEta.setVisibility(View.GONE);
            }
            
            // Informações de partes (atualizar se necessário, mas não incluído no payload atual)
            int parts = downloadInfo.getParts();
            if (parts > 0) {
                int currentPart = 1;
                if (progress > 0) {
                    currentPart = Math.min(parts, (progress * parts / 100) + 1);
                }
                tvDownloadParts.setText(String.format(Locale.getDefault(), "%d partes • Parte atual: %d/%d", parts, currentPart, parts));
                tvDownloadParts.setVisibility(View.VISIBLE);
            } else {
                tvDownloadParts.setVisibility(View.GONE);
            }
        }
        
        /**
         * Atualiza o estado dos botões com base no status do download
         */
        private void updateButtonState(DownloadInfo downloadInfo) {
            // Habilitar botões por padrão
            btnPauseResume.setEnabled(true);
            btnCancel.setEnabled(true);
            
            switch (downloadInfo.getStatus()) {
                case DownloadInfo.STATUS_RUNNING:
                case DownloadInfo.STATUS_RESUMING: // Tratar RESUMING como RUNNING para o botão
                    btnPauseResume.setText("Pausar");
                    btnPauseResume.setIconResource(R.drawable.ic_pause); // Usar ícone do projeto
                    break;
                    
                case DownloadInfo.STATUS_PAUSED:
                    btnPauseResume.setText("Retomar");
                    btnPauseResume.setIconResource(R.drawable.ic_play); // Usar ícone do projeto
                    break;
                    
                case DownloadInfo.STATUS_PAUSING: // Estado visual intermediário
                    btnPauseResume.setText("Pausando...");
                    btnPauseResume.setIconResource(R.drawable.ic_pause);
                    btnPauseResume.setEnabled(false); // Desabilitar durante a transição
                    break;
                    
                // Não há estado RESUMING visual intermediário no botão, onDownloadUpdated trata
                    
                case DownloadInfo.STATUS_QUEUED:
                    btnPauseResume.setText("Pausar"); // Permitir pausar mesmo na fila
                    btnPauseResume.setIconResource(R.drawable.ic_pause);
                    break;
                    
                case DownloadInfo.STATUS_COMPLETED:
                case DownloadInfo.STATUS_FAILED:
                case DownloadInfo.STATUS_CANCELLED:
                    btnPauseResume.setVisibility(View.GONE); // Ocultar botão Pausar/Retomar
                    btnCancel.setVisibility(View.GONE); // Ocultar botão Cancelar
                    // Opcional: Mostrar um botão para limpar/abrir?
                    break;
                default:
                    // Estado desconhecido, ocultar botões por segurança
                     btnPauseResume.setVisibility(View.GONE);
                     btnCancel.setVisibility(View.GONE);
                    break;
            }
            
            // Garantir visibilidade se não estiverem ocultos por status finalizado
            if (downloadInfo.getStatus() != DownloadInfo.STATUS_COMPLETED &&
                downloadInfo.getStatus() != DownloadInfo.STATUS_FAILED &&
                downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
                btnPauseResume.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.VISIBLE);
            }
        }
        
        // Método formatFileSize (pode ser movido para FileUtils)
        private String formatFileSize(long size) {
            if (size <= 0) {
                return "0 B";
            }
            final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            digitGroups = Math.min(digitGroups, units.length - 1); // Evitar ArrayIndexOutOfBounds
            return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
        }
        
        // Método formatSpeed (pode ser movido para uma classe utilitária)
        private String formatSpeed(long bytesPerSecond) {
            if (bytesPerSecond <= 0) {
                return "0 B/s";
            }
            final String[] units = new String[] { "B/s", "KB/s", "MB/s", "GB/s" };
            int digitGroups = (int) (Math.log10(bytesPerSecond) / Math.log10(1024));
            digitGroups = Math.min(digitGroups, units.length - 1); // Evitar ArrayIndexOutOfBounds
            return String.format(Locale.getDefault(), "%.1f %s", bytesPerSecond / Math.pow(1024, digitGroups), units[digitGroups]);
        }
    }
}

