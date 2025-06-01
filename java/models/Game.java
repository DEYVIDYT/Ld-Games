package com.LDGAMES.models;

public class Game {
    private String id;
    private String title;
    private String imageUrl;
    private String detailUrl;
    
    public Game(String id, String title, String imageUrl, String detailUrl) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.detailUrl = detailUrl;
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public String getDetailUrl() {
        return detailUrl;
    }
}
