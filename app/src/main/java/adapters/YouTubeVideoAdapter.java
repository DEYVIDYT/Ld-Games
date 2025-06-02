package com.LDGAMES.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.models.YouTubeVideo;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class YouTubeVideoAdapter extends RecyclerView.Adapter<YouTubeVideoAdapter.VideoViewHolder> {

    private static final String TAG = "YouTubeVideoAdapter";
    private List<YouTubeVideo> videos;
    private Context context;
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(YouTubeVideo video);
    }

    public YouTubeVideoAdapter(List<YouTubeVideo> videos, Context context, OnVideoClickListener listener) {
        this.videos = videos;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_youtube_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        YouTubeVideo video = videos.get(position);
        holder.tvVideoTitle.setText(video.getTitle());
        
        // Definir imagem padrão primeiro
        holder.ivVideoThumbnail.setImageResource(R.drawable.placeholder_game);
        
        // Carregar thumbnail com AsyncTask em vez de Picasso
        if (video.getThumbnailUrl() != null && !video.getThumbnailUrl().isEmpty()) {
            try {
                new ImageDownloadTask(holder.ivVideoThumbnail).execute(video.getThumbnailUrl());
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar thumbnail: " + e.getMessage());
                // Já definimos a imagem padrão acima, então não precisamos fazer nada aqui
            }
        }
        
        // Configurar clique
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVideoClick(video);
            }
        });
    }

    @Override
    public int getItemCount() {
        return videos != null ? videos.size() : 0;
    }

    public void updateVideos(List<YouTubeVideo> newVideos) {
        this.videos = newVideos;
        notifyDataSetChanged();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivVideoThumbnail;
        TextView tvVideoTitle;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvVideoTitle = itemView.findViewById(R.id.tv_video_title);
        }
    }
    
    // AsyncTask para download de imagens
    private static class ImageDownloadTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        
        public ImageDownloadTask(ImageView imageView) {
            this.imageView = imageView;
        }
        
        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap bitmap = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Erro no download da imagem: " + e.getMessage());
            }
            return bitmap;
        }
        
        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                imageView.setImageBitmap(result);
            }
            // Se result for null, a imagem padrão já foi definida no onBindViewHolder
        }
    }
}
