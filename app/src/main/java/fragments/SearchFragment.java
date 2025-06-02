package com.LDGAMES.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.adapters.GameAdapter;
import com.LDGAMES.models.Game;
import com.LDGAMES.utils.IGDBApiClient;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements GameAdapter.OnGameClickListener {

    private static final String TAG = "SearchFragment";
    private EditText etSearch;
    private MaterialButton btnSearch;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView tvError;
    private TextView tvNoResults;
    private View emptyStateContainer; // Added for placeholder control
    private View errorCard;
    private View noResultsCard;
    
    private List<Game> searchResults = new ArrayList<>();
    private GameAdapter adapter;
    private IGDBApiClient apiClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar API client
        apiClient = IGDBApiClient.getInstance(getContext());

        // Inicializar views
        etSearch = view.findViewById(R.id.et_search);
        btnSearch = view.findViewById(R.id.btn_search);
        progressBar = view.findViewById(R.id.progress_bar);
        recyclerView = view.findViewById(R.id.rv_search_results);
        tvError = view.findViewById(R.id.tv_error);
        tvNoResults = view.findViewById(R.id.tv_no_results);
        emptyStateContainer = view.findViewById(R.id.empty_state_container); // Initialize placeholder
        errorCard = view.findViewById(R.id.error_card); // Initialize error card
        noResultsCard = view.findViewById(R.id.no_results_card); // Initialize no results card
        
        // Configurar RecyclerView com GridLayoutManager para exibir em grade
        int spanCount = 2; // Número de colunas na grade
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        adapter = new GameAdapter(searchResults, this);
        recyclerView.setAdapter(adapter);
        
        // Configurar botão de pesquisa
        btnSearch.setOnClickListener(v -> performSearch());
        
        // Configurar ação de pesquisa no teclado
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }
    
    private void performSearch() {
        String query = etSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(getContext(), "Digite algo para pesquisar", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Iniciando pesquisa por: " + query);
        
        // Mostrar loading e esconder outros elementos
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorCard.setVisibility(View.GONE); // Hide error card
        noResultsCard.setVisibility(View.GONE); // Hide no results card
        emptyStateContainer.setVisibility(View.GONE); // Hide placeholder
        
        // Executar pesquisa usando a API IGDB
        apiClient.searchGames(query, new IGDBApiClient.ApiCallback<List<Game>>() {
            @Override
            public void onSuccess(List<Game> result) {
                if (getActivity() == null || !isAdded()) return;
                
                Log.d(TAG, "Pesquisa concluída com sucesso. Resultados: " + (result != null ? result.size() : 0));
                
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (result != null && !result.isEmpty()) {
                        searchResults.clear();
                        searchResults.addAll(result);
                        adapter.notifyDataSetChanged();
                        recyclerView.setVisibility(View.VISIBLE);
                        noResultsCard.setVisibility(View.GONE); // Hide no results card
                        emptyStateContainer.setVisibility(View.GONE); // Ensure placeholder is hidden
                        errorCard.setVisibility(View.GONE); // Ensure error card is hidden
                        
                        // Verificar URLs das imagens
                        for (Game game : result) {
                            Log.d(TAG, "Jogo: " + game.getTitle() + ", URL da imagem: " + game.getImageUrl());
                        }
                    } else {
                        recyclerView.setVisibility(View.GONE);
                        noResultsCard.setVisibility(View.VISIBLE); // Show no results card
                        emptyStateContainer.setVisibility(View.GONE); // Ensure placeholder is hidden
                        errorCard.setVisibility(View.GONE); // Ensure error card is hidden
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;
                
                Log.e(TAG, "Erro na pesquisa: " + e.getMessage(), e);
                
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    errorCard.setVisibility(View.VISIBLE); // Show error card
                    noResultsCard.setVisibility(View.GONE); // Hide no results card
                    emptyStateContainer.setVisibility(View.GONE); // Ensure placeholder is hidden
                    tvError.setText("Erro ao pesquisar: " + e.getMessage());
                });
            }
        });
    }

    @Override
    public void onGameClick(Game game) {
        // Verificar se temos dados válidos antes de navegar
        if (game == null) {
            Log.e(TAG, "Tentativa de clicar em jogo nulo");
            Toast.makeText(getContext(), "Erro: dados do jogo inválidos", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (game.getDetailUrl() == null || game.getDetailUrl().isEmpty()) {
            Log.e(TAG, "URL do jogo vazia ou nula: " + game.getTitle());
            Toast.makeText(getContext(), "Erro: URL do jogo inválida", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Clique no jogo: " + game.getTitle() + 
              "\nURL: " + game.getDetailUrl() + 
              "\nImagem: " + game.getImageUrl());
        
        // Navegar para a tela de detalhes do jogo
        GameDetailFragment detailFragment = new GameDetailFragment();
        Bundle args = new Bundle();
        args.putString("gameUrl", game.getDetailUrl());
        args.putString("gameTitle", game.getTitle());
        args.putString("gameImageUrl", game.getImageUrl());
        args.putString("gameId", game.getId());
        detailFragment.setArguments(args);
        
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            Log.e(TAG, "Não foi possível navegar: Activity é nula");
            Toast.makeText(getContext(), "Erro ao abrir detalhes do jogo", Toast.LENGTH_SHORT).show();
        }
    }
}
