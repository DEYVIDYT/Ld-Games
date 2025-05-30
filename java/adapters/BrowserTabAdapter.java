package com.LDGAMES.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.BrowserTab;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class BrowserTabAdapter extends RecyclerView.Adapter<BrowserTabAdapter.TabViewHolder> {

    private final List<BrowserTab> tabs;
    private final TabClickListener listener;

    public interface TabClickListener {
        void onTabClick(int position);
        void onTabClose(int position);
    }

    public BrowserTabAdapter(List<BrowserTab> tabs, TabClickListener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_browser_tab, parent, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        BrowserTab tab = tabs.get(position);
        holder.bind(tab, position);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    class TabViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTabNumber;
        private final TextView tvTabTitle;
        private final MaterialButton btnCloseTab;
        private final View iconContainer;

        public TabViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTabNumber = itemView.findViewById(R.id.tv_tab_number);
            tvTabTitle = itemView.findViewById(R.id.tv_tab_title);
            btnCloseTab = itemView.findViewById(R.id.btn_close_tab);
            iconContainer = itemView.findViewById(R.id.icon_container);
        }

        public void bind(final BrowserTab tab, final int position) {
            // Configurar número da guia
            tvTabNumber.setText(String.valueOf(position + 1) + ".");
            
            // Configurar título da guia
            tvTabTitle.setText(tab.getTitle());
            
            // Configurar clique na guia
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClick(position);
                }
            });
            
            // Configurar botão de fechar
            btnCloseTab.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClose(position);
                }
            });
        }
    }
}
