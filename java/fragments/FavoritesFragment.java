package com.LDGAMES.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.adapters.GameAdapter;
import com.LDGAMES.models.Game;
import com.google.android.material.elevation.SurfaceColors;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment implements GameAdapter.OnGameClickListener {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private List<Game> favoriteGames = new ArrayList<>();
    private GameAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar views
        recyclerView = view.findViewById(R.id.rv_favorites);
        tvEmpty = view.findViewById(R.id.tv_empty);
        
        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GameAdapter(favoriteGames, this);
        recyclerView.setAdapter(adapter);
        
        // Aplicar cor de fundo do Material You
        view.setBackgroundColor(SurfaceColors.SURFACE_0.getColor(requireContext()));
        
        // Carregar favoritos (simulado por enquanto)
        loadFavorites();
    }
    
    private void loadFavorites() {
        // Aqui seria implementada a lógica para carregar jogos favoritos
        // Por enquanto, apenas mostramos a mensagem de lista vazia
        favoriteGames.clear();
        
        if (favoriteGames.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onGameClick(Game game) {
        // Navegar para a tela de detalhes do jogo
        GameDetailFragment detailFragment = new GameDetailFragment();
        Bundle args = new Bundle();
        args.putString("gameUrl", game.getDetailUrl());
        args.putString("gameTitle", game.getTitle());
        args.putString("gameImageUrl", game.getImageUrl());
        detailFragment.setArguments(args);
        
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }
}
