package com.LDGAMES;

import android.app.Application;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.LDGAMES.utils.DownloadManager;
import com.LDGAMES.utils.DynamicThemeManager;

public class SteamRipApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Inicializar o gerenciador de temas
        DynamicThemeManager themeManager = DynamicThemeManager.getInstance();
        themeManager.initialize(this);
        
        // Inicializar o gerenciador de downloads na aplicação
        DownloadManager.getInstance(this);
        
        // Forçar tema escuro independente das configurações do sistema
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
}
