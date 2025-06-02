package com.LDGAMES.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.utils.DynamicThemeManager;

import java.util.ArrayList;
import java.util.List;

public class ColorPaletteAdapter extends RecyclerView.Adapter<ColorPaletteAdapter.PaletteViewHolder> {

    private List<DynamicThemeManager.PaletteInfo> palettes;
    private Context context;
    private int selectedPosition = 0;
    private OnPaletteSelectedListener listener;

    public interface OnPaletteSelectedListener {
        void onPaletteSelected(int paletteIndex);
    }

    public ColorPaletteAdapter(Context context, OnPaletteSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.palettes = new ArrayList<>();
        
        // Preencher a lista de paletas
        DynamicThemeManager themeManager = DynamicThemeManager.getInstance();
        for (int i = 0; i <= 10; i++) {
            palettes.add(themeManager.getPaletteInfo(i));
        }
        
        // Definir a posição selecionada inicialmente
        selectedPosition = themeManager.getSelectedPalette();
    }

    @NonNull
    @Override
    public PaletteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color_palette, parent, false);
        return new PaletteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaletteViewHolder holder, int position) {
        DynamicThemeManager.PaletteInfo palette = palettes.get(position);
        
        // Definir as cores nos círculos
        holder.colorPrimary.setBackgroundColor(palette.getPrimaryColor());
        holder.colorPrimaryContainer.setBackgroundColor(palette.getPrimaryContainerColor());
        holder.colorSecondary.setBackgroundColor(palette.getSecondaryColor());
        
        // Ocultar o nome da paleta (mostrar apenas as cores)
        holder.tvPaletteName.setVisibility(View.GONE);
        
        // Definir o estado do radio button
        holder.radioSelect.setChecked(position == selectedPosition);
        
        // Configurar o clique no item
        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            
            // Notificar o listener
            if (listener != null) {
                listener.onPaletteSelected(selectedPosition);
            }
            
            // Atualizar os itens afetados
            notifyItemChanged(previousSelected);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return palettes.size();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < palettes.size()) {
            int previousSelected = selectedPosition;
            selectedPosition = position;
            
            // Atualizar os itens afetados
            notifyItemChanged(previousSelected);
            notifyItemChanged(selectedPosition);
        }
    }

    static class PaletteViewHolder extends RecyclerView.ViewHolder {
        View colorPrimary;
        View colorPrimaryContainer;
        View colorSecondary;
        TextView tvPaletteName;
        RadioButton radioSelect;

        PaletteViewHolder(@NonNull View itemView) {
            super(itemView);
            colorPrimary = itemView.findViewById(R.id.color_primary);
            colorPrimaryContainer = itemView.findViewById(R.id.color_primary_container);
            colorSecondary = itemView.findViewById(R.id.color_secondary);
            tvPaletteName = itemView.findViewById(R.id.tv_palette_name);
            radioSelect = itemView.findViewById(R.id.radio_select);
        }
    }
}
