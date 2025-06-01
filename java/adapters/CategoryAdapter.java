package com.LDGAMES.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.Category;
import com.LDGAMES.models.Game;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    
    private List<Category> categories;
    private GameAdapter.OnGameClickListener listener;
    
    public CategoryAdapter(List<Category> categories, GameAdapter.OnGameClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category, listener);
    }
    
    @Override
    public int getItemCount() {
        return categories.size();
    }
    
    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCategoryTitle;
        private RecyclerView rvGames;
        
        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryTitle = itemView.findViewById(R.id.tv_category_title);
            rvGames = itemView.findViewById(R.id.rv_games);
        }
        
        public void bind(final Category category, final GameAdapter.OnGameClickListener listener) {
            tvCategoryTitle.setText(category.getTitle());
            
            // Configurar RecyclerView horizontal para os jogos
            rvGames.setLayoutManager(new LinearLayoutManager(
                    itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            GameAdapter adapter = new GameAdapter(category.getGames(), listener);
            rvGames.setAdapter(adapter);
        }
    }
}
