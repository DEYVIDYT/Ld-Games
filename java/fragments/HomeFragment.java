package com.LDGAMES.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.activities.SettingsActivity; // Importar SettingsActivity
// import com.LDGAMES.activities.DownloadProgressActivity; // Comentado - Bloqueado
import com.LDGAMES.adapters.CategoryAdapter;
import com.LDGAMES.adapters.GameAdapter;
import com.LDGAMES.models.Category;
import com.LDGAMES.models.Game;
import com.LDGAMES.utils.IGDBApiClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements GameAdapter.OnGameClickListener {
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView tvError;
    private Toolbar toolbar;
    private List<Category> categories = new ArrayList<>();
    private CategoryAdapter adapter;
    private IGDBApiClient apiClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar API client
        apiClient = IGDBApiClient.getInstance(getContext());

        // Inicializar toolbar
        toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                 ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.app_name); // Usar string resource
            }
            setHasOptionsMenu(true); // Indicar que este fragmento tem itens de menu
        }

        // Inicializar views com findViewById
        progressBar = view.findViewById(R.id.progress_bar);
        recyclerView = view.findViewById(R.id.rv_categories);
        tvError = view.findViewById(R.id.tv_error);

        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CategoryAdapter(categories, this);
        recyclerView.setAdapter(adapter);

        // Carregar dados
        loadData();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.home_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_downloads) {
            // BLOQUEADO: Mostrar toast em vez de abrir a tela de downloads
            if (getContext() != null) {
                Toast.makeText(getContext(), "Função de progresso de download bloqueada no momento.", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemId == R.id.action_settings) {
             // CORRIGIDO: Tratar o clique aqui mesmo para abrir SettingsActivity
             if (getActivity() != null) {
                 Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
                 startActivity(settingsIntent);
                 return true; // Indicar que o evento foi tratado
             }
             return false; // Não foi possível tratar
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        if (!isAdded() || getContext() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);

        // Criar lista para armazenar todas as categorias
        List<Category> allCategories = new ArrayList<>();

        // Carregar jogos populares
        apiClient.getPopularGames(new IGDBApiClient.ApiCallback<List<Game>>() {
            @Override
            public void onSuccess(List<Game> result) {
                if (getActivity() == null || !isAdded()) return;

                Category popularCategory = new Category("Jogos Populares", result);
                allCategories.add(popularCategory);

                // Carregar jogos recentes após carregar os populares
                loadRecentGames(allCategories);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;

                // Mesmo com erro, continuar carregando outras categorias
                loadRecentGames(allCategories);
            }
        });
    }

    private void loadRecentGames(List<Category> allCategories) {
         if (!isAdded() || getContext() == null) return;
        apiClient.getRecentGames(new IGDBApiClient.ApiCallback<List<Game>>() {
            @Override
            public void onSuccess(List<Game> result) {
                if (getActivity() == null || !isAdded()) return;

                Category recentCategory = new Category("Lançamentos Recentes", result);
                allCategories.add(recentCategory);

                // Carregar gêneros após carregar os jogos recentes
                loadGenres(allCategories);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;

                // Mesmo com erro, continuar carregando outras categorias
                loadGenres(allCategories);
            }
        });
    }

    private void loadGenres(List<Category> allCategories) {
         if (!isAdded() || getContext() == null) return;
        apiClient.getGenres(new IGDBApiClient.ApiCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> genres) {
                if (getActivity() == null || !isAdded()) return;

                // Limitar a 3 gêneros para não sobrecarregar a tela
                int genreLimit = Math.min(genres.size(), 3);
                final int[] genresLoaded = {0};
                final Object lock = new Object(); // Lock para sincronizar adição e verificação

                if (genreLimit == 0) {
                    updateUI(allCategories);
                    return;
                }

                for (int i = 0; i < genreLimit; i++) {
                    Map<String, Object> genre = genres.get(i);
                    int genreId = (int) genre.get("id");
                    String genreName = (String) genre.get("name");

                    apiClient.getGamesByGenre(genreId, new IGDBApiClient.ApiCallback<List<Game>>() {
                        @Override
                        public void onSuccess(List<Game> result) {
                            if (getActivity() == null || !isAdded()) return;

                            synchronized (lock) {
                                if (!result.isEmpty()) {
                                    Category genreCategory = new Category(genreName, result);
                                    allCategories.add(genreCategory);
                                }
                                genresLoaded[0]++;
                                if (genresLoaded[0] >= genreLimit) {
                                    updateUI(allCategories);
                                }
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            if (getActivity() == null || !isAdded()) return;

                             synchronized (lock) {
                                genresLoaded[0]++;
                                if (genresLoaded[0] >= genreLimit) {
                                    updateUI(allCategories);
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;

                // Mesmo com erro, atualizar a UI com as categorias já carregadas
                updateUI(allCategories);
            }
        });
    }

    private void updateUI(List<Category> allCategories) {
        if (getActivity() == null || !isAdded()) return;

        getActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);

            if (!allCategories.isEmpty()) {
                categories.clear();

                // Remover categorias duplicadas baseadas no título
                List<Category> uniqueCategories = removeDuplicateCategories(allCategories);

                categories.addAll(uniqueCategories);
                adapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.VISIBLE);
                tvError.setVisibility(View.GONE);
            } else {
                recyclerView.setVisibility(View.GONE);
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("Nenhum jogo encontrado");
            }
        });
    }

    /**
     * Remove categorias duplicadas baseadas no título
     * @param categories Lista de categorias a ser processada
     * @return Lista de categorias sem duplicatas
     */
    private List<Category> removeDuplicateCategories(List<Category> categories) {
        List<Category> uniqueCategories = new ArrayList<>();
        Set<String> categoryTitles = new HashSet<>();

        for (Category category : categories) {
            // Normalizar o título para comparação (remover espaços extras, converter para minúsculas)
            String normalizedTitle = category.getTitle().trim().toLowerCase();

            // Verificar se já temos uma categoria com este título
            if (!categoryTitles.contains(normalizedTitle)) {
                categoryTitles.add(normalizedTitle);
                uniqueCategories.add(category);
            }
        }

        return uniqueCategories;
    }

    @Override
    public void onGameClick(Game game) {
        if (!isAdded() || getActivity() == null) return;
        // Navegar para a tela de detalhes do jogo
        GameDetailFragment detailFragment = new GameDetailFragment();
        Bundle args = new Bundle();
        args.putString("gameUrl", game.getDetailUrl());
        args.putString("gameTitle", game.getTitle());
        args.putString("gameImageUrl", game.getImageUrl());
        args.putString("gameId", game.getId());
        detailFragment.setArguments(args);

        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }
}

