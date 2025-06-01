package com.LDGAMES.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.adapters.ColorPaletteAdapter;
import com.LDGAMES.utils.DynamicThemeManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ColorPaletteDialog extends BottomSheetDialogFragment implements ColorPaletteAdapter.OnPaletteSelectedListener {

    private RecyclerView rvColorPalettes;
    private Button btnCancel;
    private Button btnApply;
    private ColorPaletteAdapter adapter;
    private OnPaletteSelectedListener listener;
    private int initialPaletteIndex;
    private int selectedPaletteIndex;

    public interface OnPaletteSelectedListener {
        void onPaletteSelected(int paletteIndex);
    }

    public static ColorPaletteDialog newInstance(int currentPaletteIndex) {
        ColorPaletteDialog dialog = new ColorPaletteDialog();
        Bundle args = new Bundle();
        args.putInt("paletteIndex", currentPaletteIndex);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getArguments() != null) {
            initialPaletteIndex = getArguments().getInt("paletteIndex", 0);
            selectedPaletteIndex = initialPaletteIndex;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_color_palette, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        rvColorPalettes = view.findViewById(R.id.rv_color_palettes);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnApply = view.findViewById(R.id.btn_apply);
        
        // Configurar RecyclerView com GridLayoutManager (3 colunas)
        rvColorPalettes.setLayoutManager(new GridLayoutManager(getContext(), 3));
        
        // Inicializar adapter
        adapter = new ColorPaletteAdapter(getContext(), this);
        rvColorPalettes.setAdapter(adapter);
        
        // Definir a posição selecionada inicialmente
        adapter.setSelectedPosition(initialPaletteIndex);
        
        // Configurar botões
        btnCancel.setOnClickListener(v -> dismiss());
        
        btnApply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPaletteSelected(selectedPaletteIndex);
            }
            dismiss();
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnPaletteSelectedListener) {
            listener = (OnPaletteSelectedListener) context;
        } else {
            throw new RuntimeException(context.toString() + " deve implementar OnPaletteSelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onPaletteSelected(int paletteIndex) {
        selectedPaletteIndex = paletteIndex;
    }
}
