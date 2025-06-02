package com.LDGAMES.models;

import java.util.List;

public class Category {
    private String title;
    private List<Game> games;
    
    public Category(String title, List<Game> games) {
        this.title = title;
        this.games = games;
    }
    
    public String getTitle() {
        return title;
    }
    
    public List<Game> getGames() {
        return games;
    }
}
