package com.LDGAMES.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.squareup.picasso.Picasso;

import java.util.List;

public class ScreenshotAdapter extends RecyclerView.Adapter<ScreenshotAdapter.ScreenshotViewHolder> {
    
    private List<String> screenshots;
    private Context context;
    
    public ScreenshotAdapter(List<String> screenshots, Context context) {
        this.screenshots = screenshots;
        this.context = context;
    }
    
    @NonNull
    @Override
    public ScreenshotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screenshot, parent, false);
        return new ScreenshotViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ScreenshotViewHolder holder, int position) {
        String screenshotUrl = screenshots.get(position);
        holder.bind(screenshotUrl, context);
    }
    
    @Override
    public int getItemCount() {
        return screenshots.size();
    }
    
    static class ScreenshotViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivScreenshot;
        
        public ScreenshotViewHolder(@NonNull View itemView) {
            super(itemView);
            ivScreenshot = itemView.findViewById(R.id.iv_screenshot);
        }
        
        public void bind(String screenshotUrl, Context context) {
            if (screenshotUrl != null && !screenshotUrl.isEmpty()) {
                // Revertendo para instância local do Picasso para evitar crash
                new Picasso.Builder(context)
                    .build()
                    .load(screenshotUrl)
                    .placeholder(R.drawable.placeholder_game)
                    .error(R.drawable.error_image)
                    .into(ivScreenshot);
            } else {
                ivScreenshot.setImageResource(R.drawable.placeholder_game);
            }
        }
    }
}
