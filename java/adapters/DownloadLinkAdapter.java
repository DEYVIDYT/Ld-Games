package com.LDGAMES.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.activities.InternalBrowserActivity;
import com.LDGAMES.models.DownloadLink;

import java.util.List;

public class DownloadLinkAdapter extends RecyclerView.Adapter<DownloadLinkAdapter.DownloadLinkViewHolder> {
    
    private List<DownloadLink> downloadLinks;
    private DownloadLinkClickListener listener;
    
    /**
     * Interface para tratar cliques em links de download
     */
    public interface DownloadLinkClickListener {
        void onLinkClick(DownloadLink link);
    }
    
    /**
     * Construtor com listener para tratamento personalizado de cliques
     */
    public DownloadLinkAdapter(List<DownloadLink> downloadLinks, DownloadLinkClickListener listener) {
        this.downloadLinks = downloadLinks;
        this.listener = listener;
    }
    
    /**
     * Construtor sem listener (para compatibilidade com código existente)
     */
    public DownloadLinkAdapter(List<DownloadLink> downloadLinks) {
        this.downloadLinks = downloadLinks;
        this.listener = null;
    }
    
    @NonNull
    @Override
    public DownloadLinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download_link, parent, false);
        return new DownloadLinkViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DownloadLinkViewHolder holder, int position) {
        DownloadLink downloadLink = downloadLinks.get(position);
        holder.bind(downloadLink, listener);
    }
    
    @Override
    public int getItemCount() {
        return downloadLinks.size();
    }
    
    static class DownloadLinkViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDownloadName;
        private TextView tvDownloadUrl;
        private TextView tvDownloadSource;
        private TextView tvDownloadSize;
        private Button btnCopyLink;
        
        public DownloadLinkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDownloadName = itemView.findViewById(R.id.tv_download_name);
            tvDownloadUrl = itemView.findViewById(R.id.tv_download_url);
            tvDownloadSource = itemView.findViewById(R.id.tv_download_source);
            tvDownloadSize = itemView.findViewById(R.id.tv_download_size);
            btnCopyLink = itemView.findViewById(R.id.btn_copy_link);
        }
        
        public void bind(final DownloadLink downloadLink, final DownloadLinkClickListener listener) {
            tvDownloadName.setText(downloadLink.getName());
            tvDownloadUrl.setText(downloadLink.getUrl());
            
            // Extrair e exibir o nome da fonte da descrição
            String description = downloadLink.getDescription();
            if (description != null && description.startsWith("Fonte: ")) {
                String sourceName = description.substring("Fonte: ".length());
                // Se houver quebra de linha, pegar apenas a primeira linha
                if (sourceName.contains("\n")) {
                    sourceName = sourceName.substring(0, sourceName.indexOf("\n"));
                }
                tvDownloadSource.setText("Fonte: " + sourceName);
            } else {
                tvDownloadSource.setText("Fonte: Desconhecida");
            }
            
            // Exibir o tamanho do arquivo
            String size = downloadLink.getSize();
            if (size != null && !size.isEmpty()) {
                tvDownloadSize.setText(size);
            } else {
                tvDownloadSize.setText("Tamanho desconhecido");
            }
            
            // Configurar clique no URL
            tvDownloadUrl.setOnClickListener(v -> {
                if (listener != null) {
                    // Se tiver listener, usar tratamento personalizado
                    listener.onLinkClick(downloadLink);
                } else {
                    // Comportamento padrão: abrir no navegador interno
                    openInInternalBrowser(v.getContext(), downloadLink.getUrl(), downloadLink.getName());
                }
            });
            
            // Configurar clique no botão para copiar link
            btnCopyLink.setOnClickListener(v -> {
                Context context = itemView.getContext();
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Download Link", downloadLink.getUrl());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Link copiado para a área de transferência", Toast.LENGTH_SHORT).show();
            });
            
            // Configurar clique no item inteiro
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    // Se tiver listener, usar tratamento personalizado
                    listener.onLinkClick(downloadLink);
                } else {
                    // Comportamento padrão: abrir no navegador interno
                    openInInternalBrowser(v.getContext(), downloadLink.getUrl(), downloadLink.getName());
                }
            });
        }
        
        private void openInInternalBrowser(Context context, String url, String title) {
            Intent intent = InternalBrowserActivity.createIntent(context, url, title);
            context.startActivity(intent);
        }
    }
}
