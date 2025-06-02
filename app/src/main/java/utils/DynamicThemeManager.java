package com.LDGAMES.utils;

import android.app.Activity;
import android.app.Application;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.LDGAMES.R;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de temas dinâmicos para implementação avançada do Material You Dynamic Color
 * e paletas de cores personalizadas
 */
public class DynamicThemeManager {

    private static final String PREFS_NAME = "theme_settings";
    private static final String KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled";
    private static final String KEY_COLOR_PALETTE = "color_palette";
    
    private static DynamicThemeManager instance;
    private boolean isDynamicColorEnabled = true;
    private int selectedPalette = 0; // 0 = padrão
    private List<ThemeChangeListener> listeners = new ArrayList<>();
    
    // Interface para notificar sobre mudanças de tema
    public interface ThemeChangeListener {
        void onThemeChanged();
    }
    
    private DynamicThemeManager() {
        // Construtor privado para Singleton
    }
    
    public static DynamicThemeManager getInstance() {
        if (instance == null) {
            instance = new DynamicThemeManager();
        }
        return instance;
    }
    
    /**
     * Inicializa as configurações de tema e registra callbacks para monitorar o ciclo de vida
     * @param application Aplicação
     */
    public void initialize(Application application) {
        // Forçar tema escuro independente das configurações do sistema
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        // Carregar configurações salvas
        loadSettings(application);
        
        // Registrar callbacks para monitorar o ciclo de vida das activities
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // Aplicar tema quando a activity é criada
                applyDynamicColors(activity);
                
                // Se for uma FragmentActivity, registrar callbacks para fragments
                if (activity instanceof FragmentActivity) {
                    FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
                    fragmentManager.registerFragmentLifecycleCallbacks(
                            new FragmentManager.FragmentLifecycleCallbacks() {
                                @Override
                                public void onFragmentViewCreated(@NonNull FragmentManager fm, 
                                                                 @NonNull Fragment f, 
                                                                 @NonNull View v, 
                                                                 @Nullable Bundle savedInstanceState) {
                                    // Garantir que o tema seja aplicado quando a view do fragment é criada
                                    applyThemeToView(v, activity);
                                }
                            }, true);
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
    
    /**
     * Aplica o tema à view do fragment
     * @param view View do fragment
     * @param activity Activity pai
     */
    private void applyThemeToView(View view, Activity activity) {
        // Aplicar cores específicas da paleta à view
        if (!isDynamicColorEnabled) {
            // Aplicar cores da paleta selecionada
            applyPaletteColorsToView(view, activity);
        }
    }
    
    /**
     * Aplica cores da paleta selecionada à view
     * @param view View alvo
     * @param context Contexto
     */
    private void applyPaletteColorsToView(View view, Context context) {
        // Aplicar cores específicas da paleta à view
        // Isso garante que mesmo os fragments recebam as cores corretas
        
        // Exemplo: aplicar cor de fundo baseada na paleta
        int backgroundColor = getPaletteColor(context, R.attr.colorSurface);
        view.setBackgroundColor(backgroundColor);
        
        // Percorrer todos os filhos da view para aplicar cores
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                applyPaletteColorsToView(child, context);
            }
        }
    }
    
    /**
     * Obtém uma cor da paleta atual
     * @param context Contexto
     * @param colorAttr Atributo da cor
     * @return Cor da paleta
     */
    public int getPaletteColor(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }
    
    /**
     * Registra um listener para mudanças de tema
     * @param listener Listener a ser registrado
     */
    public void registerThemeChangeListener(ThemeChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove um listener de mudanças de tema
     * @param listener Listener a ser removido
     */
    public void unregisterThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notifica todos os listeners sobre mudança de tema
     */
    private void notifyThemeChanged() {
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged();
        }
    }
    
    /**
     * Carrega as configurações de tema salvas
     * @param context Contexto da aplicação
     */
    private void loadSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isDynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, true);
        selectedPalette = prefs.getInt(KEY_COLOR_PALETTE, 0);
    }
    
    /**
     * Salva as configurações de tema
     * @param context Contexto da aplicação
     */
    private void saveSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_DYNAMIC_COLOR_ENABLED, isDynamicColorEnabled);
        editor.putInt(KEY_COLOR_PALETTE, selectedPalette);
        editor.apply();
    }
    
    /**
     * Verifica se o dynamic color está habilitado
     * @return true se o dynamic color estiver habilitado
     */
    public boolean isDynamicColorEnabled() {
        return isDynamicColorEnabled;
    }
    
    /**
     * Define se o dynamic color está habilitado
     * @param context Contexto da aplicação
     * @param enabled true para habilitar o dynamic color
     */
    public void setDynamicColorEnabled(Context context, boolean enabled) {
        isDynamicColorEnabled = enabled;
        saveSettings(context);
        notifyThemeChanged();
    }
    
    /**
     * Obtém a paleta de cores selecionada
     * @return Índice da paleta selecionada
     */
    public int getSelectedPalette() {
        return selectedPalette;
    }
    
    /**
     * Define a paleta de cores selecionada
     * @param context Contexto da aplicação
     * @param paletteIndex Índice da paleta
     */
    public void setSelectedPalette(Context context, int paletteIndex) {
        selectedPalette = paletteIndex;
        saveSettings(context);
        notifyThemeChanged();
    }
    
    /**
     * Aplica cores dinâmicas à atividade
     * @param activity Atividade alvo
     */
    public void applyDynamicColors(@NonNull Activity activity) {
        // Forçar tema escuro independente das configurações do sistema
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        if (isDynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ - Usa API nativa do Material You
            DynamicColors.applyToActivityIfAvailable(activity);
        } else if (isDynamicColorEnabled) {
            // Versões anteriores - Implementa extração de cores do papel de parede
            applyWallpaperColors(activity);
        } else {
            // Dynamic color desabilitado - Aplicar paleta selecionada
            applySelectedPalette(activity);
        }
    }
    
    /**
     * Aplica a paleta de cores selecionada
     * @param activity Atividade alvo
     */
    private void applySelectedPalette(Activity activity) {
        // Aplicar paleta de cores selecionada
        int styleResId;
        
        switch (selectedPalette) {
            case 1: // Azul
                styleResId = R.style.ThemeOverlay_SteamRip_Blue;
                break;
            case 2: // Verde
                styleResId = R.style.ThemeOverlay_SteamRip_Green;
                break;
            case 3: // Roxo
                styleResId = R.style.ThemeOverlay_SteamRip_Purple;
                break;
            case 4: // Vermelho
                styleResId = R.style.ThemeOverlay_SteamRip_Red;
                break;
            case 5: // Laranja
                styleResId = R.style.ThemeOverlay_SteamRip_Orange;
                break;
            case 6: // Rosa
                styleResId = R.style.ThemeOverlay_SteamRip_Pink;
                break;
            case 7: // Amarelo
                styleResId = R.style.ThemeOverlay_SteamRip_Yellow;
                break;
            case 8: // Ciano
                styleResId = R.style.ThemeOverlay_SteamRip_Cyan;
                break;
            case 9: // Lime
                styleResId = R.style.ThemeOverlay_SteamRip_Lime;
                break;
            case 10: // Indigo
                styleResId = R.style.ThemeOverlay_SteamRip_Indigo;
                break;
            default: // Padrão (Teal)
                styleResId = R.style.ThemeOverlay_SteamRip_Default;
                break;
        }
        
        activity.getTheme().applyStyle(styleResId, true);
    }
    
    /**
     * Extrai cores do papel de parede para dispositivos sem suporte nativo ao Dynamic Color
     * @param activity Atividade alvo
     */
    private void applyWallpaperColors(Activity activity) {
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
            Bitmap wallpaper = ((BitmapDrawable) wallpaperManager.getDrawable()).getBitmap();
            
            // Implementação de extração de cores do papel de parede
            // Esta é uma implementação simplificada para compatibilidade
            
            // Aplicar cores extraídas ao tema
            // activity.getTheme().applyStyle(R.style.Theme_SteamRip, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Obtém uma cor harmônica baseada na cor primária do tema
     * @param context Contexto da aplicação
     * @param baseColorAttr Atributo da cor base
     * @param targetColorAttr Atributo da cor alvo para harmonização
     * @return Cor harmonizada
     */
    public int getHarmonizedColor(Context context, int baseColorAttr, int targetColorAttr) {
        int baseColor = MaterialColors.getColor(context, baseColorAttr, 0);
        int targetColor = MaterialColors.getColor(context, targetColorAttr, 0);
        
        return MaterialColors.harmonizeWithPrimary(context, targetColor);
    }
    
    /**
     * Cria uma variação tonal de uma cor do tema
     * @param context Contexto da aplicação
     * @param colorAttr Atributo da cor
     * @param tonalOffset Deslocamento tonal (-100 a 100)
     * @return Cor com variação tonal
     */
    public int getTonalColor(Context context, int colorAttr, float tonalOffset) {
        int color = MaterialColors.getColor(context, colorAttr, 0);
        
        // Implementação simplificada de variação tonal
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        
        // Ajusta o valor (brilho) mantendo matiz e saturação
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] + (tonalOffset / 100f)));
        
        return android.graphics.Color.HSVToColor(hsv);
    }
    
    /**
     * Obtém informações sobre uma paleta de cores
     * @param paletteIndex Índice da paleta
     * @return Informações da paleta
     */
    public PaletteInfo getPaletteInfo(int paletteIndex) {
        switch (paletteIndex) {
            case 1:
                return new PaletteInfo("Azul", 
                        0xFF0052CC, 0xFFD4E5FF, 0xFF3B5F8D);
            case 2:
                return new PaletteInfo("Verde", 
                        0xFF006E1C, 0xFF9CF68E, 0xFF386A35);
            case 3:
                return new PaletteInfo("Roxo", 
                        0xFF6750A4, 0xFFE9DDFF, 0xFF7D5260);
            case 4:
                return new PaletteInfo("Vermelho", 
                        0xFFB3261E, 0xFFF9DEDC, 0xFF984061);
            case 5:
                return new PaletteInfo("Laranja", 
                        0xFFE65100, 0xFFFFD9C2, 0xFFB55400);
            case 6:
                return new PaletteInfo("Rosa", 
                        0xFFD81B60, 0xFFFCE4EC, 0xFFC2185B);
            case 7:
                return new PaletteInfo("Amarelo", 
                        0xFFFFC107, 0xFFFFF9C4, 0xFFFFA000);
            case 8:
                return new PaletteInfo("Ciano", 
                        0xFF00BCD4, 0xFFE0F7FA, 0xFF0097A7);
            case 9:
                return new PaletteInfo("Lime", 
                        0xFFCDDC39, 0xFFF9FBE7, 0xFFAFB42B);
            case 10:
                return new PaletteInfo("Indigo", 
                        0xFF3F51B5, 0xFFE8EAF6, 0xFF303F9F);
            default:
                return new PaletteInfo("Teal (Padrão)", 
                        0xFF006874, 0xFF97F0FF, 0xFF4A6267);
        }
    }
    
    /**
     * Classe para armazenar informações sobre uma paleta de cores
     */
    public static class PaletteInfo {
        private String name;
        private int primaryColor;
        private int primaryContainerColor;
        private int secondaryColor;
        
        public PaletteInfo(String name, int primaryColor, int primaryContainerColor, int secondaryColor) {
            this.name = name;
            this.primaryColor = primaryColor;
            this.primaryContainerColor = primaryContainerColor;
            this.secondaryColor = secondaryColor;
        }
        
        public String getName() {
            return name;
        }
        
        public int getPrimaryColor() {
            return primaryColor;
        }
        
        public int getPrimaryContainerColor() {
            return primaryContainerColor;
        }
        
        public int getSecondaryColor() {
            return secondaryColor;
        }
    }
}
