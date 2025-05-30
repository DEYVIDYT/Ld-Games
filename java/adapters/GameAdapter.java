package com.LDGAMES.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.Game;
import com.squareup.picasso.Picasso;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
    
    // Interface para o listener de clique em jogos
    public interface OnGameClickListener {
        void onGameClick(Game game);
    }
    
    private List<Game> games;
    private OnGameClickListener listener;
    private Context context;
    
    public GameAdapter(List<Game> games, OnGameClickListener listener) {
        this.games = games;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = games.get(position);
        holder.bind(game, listener, context);
    }
    
    @Override
    public int getItemCount() {
        return games.size();
    }
    
    static class GameViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivGameCover;
        private TextView tvGameTitle;
        
        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGameCover = itemView.findViewById(R.id.iv_game_cover);
            tvGameTitle = itemView.findViewById(R.id.tv_game_title);
        }
        
        public void bind(final Game game, final OnGameClickListener listener, Context context) {
            tvGameTitle.setText(game.getTitle());
            
            // Carregar imagem com Picasso usando instância criada com contexto
            if (game.getImageUrl() != null && !game.getImageUrl().isEmpty()) {
                // Criar uma nova instância do Picasso em vez de usar o singleton
                new Picasso.Builder(context)
                    .build()
                    .load(game.getImageUrl())
                    .placeholder(R.drawable.placeholder_game)
                    .error(R.drawable.error_image)
                    .into(ivGameCover);
            } else {
                ivGameCover.setImageResource(R.drawable.placeholder_game);
            }
            
            // Configurar clique
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGameClick(game);
                }
            });
        }
    }
}
