package com.LDGAMES;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.LDGAMES.activities.DownloadProgressActivity;
import com.LDGAMES.activities.SettingsActivity;
import com.LDGAMES.fragments.FavoritesFragment;
import com.LDGAMES.fragments.HomeFragment;
import com.LDGAMES.fragments.SearchFragment;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.HydraApiManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private BottomNavigationView bottomNavigationView;
    // Remover a referência ao Toast de progresso
    // private Toast progressToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeManager.getInstance().applyDynamicColors(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar o FloatingProgressManager
       

        getWindow().setNavigationBarColor(SurfaceColors.SURFACE_2.getColor(this));
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        bottomNavigationView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up));

        if (savedInstanceState == null) {
            // Remover a verificação de frequência de atualização e a atualização automática
            loadFragment(new HomeFragment());
            
            // Verificar se deve iniciar downloads automaticamente
            checkAutoStartDownloads();
        }
    }

    // Método triggerApiUpdate removido - não é mais necessário

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            // Abrir configurações
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_downloads) {
            // Abrir tela de downloads
            Intent intent = new Intent(this, DownloadProgressActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void loadFragment(Fragment fragment) {
        fragment.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
        fragment.setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        int itemId = item.getItemId();

        if (itemId == R.id.navigation_home) {
            fragment = new HomeFragment();
        } else if (itemId == R.id.navigation_search) {
            fragment = new SearchFragment();
        } else if (itemId == R.id.navigation_favorites) {
            fragment = new FavoritesFragment();
        }

        if (fragment != null) {
            loadFragment(fragment);
            return true;
        }
        return false;
    }
    
    /**
     * Verifica se deve iniciar downloads automaticamente ao abrir o app
     */
    private void checkAutoStartDownloads() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            boolean autoStartDownloads = prefs.getBoolean("auto_start_downloads", false);
            
            if (autoStartDownloads) {
                Log.d("MainActivity", "Auto-start de downloads habilitado, iniciando downloads pausados");
                
                // Aguardar um pouco para que a UI carregue completamente
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    resumePausedDownloadsOnAppStart();
                }, 1000); // 1 segundo de delay
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao verificar auto-start: " + e.getMessage(), e);
        }
    }
    
    /**
     * Retoma downloads pausados que têm auto-resume habilitado
     */
    private void resumePausedDownloadsOnAppStart() {
        try {
            com.LDGAMES.utils.DownloadManager downloadManager = 
                com.LDGAMES.utils.DownloadManager.getInstance(this);
            
            // Obter downloads ativos
            java.util.List<com.LDGAMES.models.DownloadInfo> activeDownloads = 
                downloadManager.getActiveDownloads();
            
            int resumedCount = 0;
            for (com.LDGAMES.models.DownloadInfo download : activeDownloads) {
                if (download.getStatus() == com.LDGAMES.models.DownloadInfo.STATUS_PAUSED && 
                    download.isAutoResumeEnabled()) {
                    
                    Log.d("MainActivity", "Retomando download: " + download.getFileName());
                    downloadManager.resumeDownload(download);
                    resumedCount++;
                }
            }
            
            if (resumedCount > 0) {
                android.widget.Toast.makeText(this, 
                    "Retomados " + resumedCount + " downloads automaticamente", 
                    android.widget.Toast.LENGTH_SHORT).show();
                Log.i("MainActivity", "Retomados " + resumedCount + " downloads automaticamente");
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao retomar downloads: " + e.getMessage(), e);
        }
    }
}

