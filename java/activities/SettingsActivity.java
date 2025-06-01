package com.LDGAMES.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.R;
import com.LDGAMES.dialogs.ColorPaletteDialog;
import com.LDGAMES.receivers.BootReceiver;
import com.LDGAMES.utils.DirectoryInitializer;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.FileUtils;
import com.LDGAMES.utils.HydraApiManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;

public class SettingsActivity extends AppCompatActivity implements ColorPaletteDialog.OnPaletteSelectedListener {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_MAX_DOWNLOADS = "max_downloads";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    private static final String KEY_YOUTUBE_SEARCH_ENABLED = "youtube_search_enabled";
    private static final String KEY_YOUTUBE_SEARCH_TERM = "youtube_search_term";
    private static final String DEFAULT_YOUTUBE_SEARCH_TERM = "Gameplay {name} no WINLATOR";
    private static final String NAME_PLACEHOLDER = "{name}";
    // Novas constantes para auto-start
    private static final String KEY_AUTO_START_DOWNLOADS = "auto_start_downloads";
    private static final String KEY_AUTO_START_SYSTEM = "auto_start_system";

    private Slider sliderMaxDownloads;
    private TextView tvMaxDownloadsValue;
    private TextView tvDownloadPath;
    private MaterialButton btnSelectDownloadFolder;
    private SwitchCompat switchDynamicColor;
    private SwitchCompat switchYouTubeSearch;
    private TextInputLayout tilYouTubeSearchTerm;
    private TextInputEditText etYouTubeSearchTerm;
    private MaterialCardView cardColorPalette;
    private View colorPalettePreview;
    private MaterialButton btnManageApis;
    private TextView tvAutoSaveFeedback;
    // Novos controles para auto-start
    private SwitchCompat switchAutoStartDownloads;
    private SwitchCompat switchAutoStartSystem;
    private MaterialButton btnCheckIntegrity;

    private int maxDownloads = 5;
    private String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    private Uri downloadUri = null;
    private String currentYouTubeSearchTerm = DEFAULT_YOUTUBE_SEARCH_TERM;
    private String previousValidYouTubeSearchTerm = DEFAULT_YOUTUBE_SEARCH_TERM;
    private boolean isUpdatingYouTubeTerm = false;

    private HydraApiManager hydraApiManager;
    private DynamicThemeManager themeManager;
    private Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private Runnable autoSaveFeedbackRunnable;

    private ActivityResultLauncher<Uri> directoryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themeManager = DynamicThemeManager.getInstance();
        themeManager.applyDynamicColors(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        hydraApiManager = HydraApiManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sliderMaxDownloads = findViewById(R.id.slider_max_downloads);
        tvMaxDownloadsValue = findViewById(R.id.tv_max_downloads_value);
        tvDownloadPath = findViewById(R.id.tv_download_path);
        btnSelectDownloadFolder = findViewById(R.id.btn_select_download_folder);
        switchDynamicColor = findViewById(R.id.switch_dynamic_color);
        switchYouTubeSearch = findViewById(R.id.switch_youtube_search);
        tilYouTubeSearchTerm = findViewById(R.id.til_youtube_search_term);
        etYouTubeSearchTerm = findViewById(R.id.et_youtube_search_term);
        cardColorPalette = findViewById(R.id.card_color_palette);
        colorPalettePreview = findViewById(R.id.color_palette_preview);
        btnManageApis = findViewById(R.id.btn_manage_apis);
        tvAutoSaveFeedback = findViewById(R.id.tv_auto_save_feedback);
        // Inicializar novos controles
        switchAutoStartDownloads = findViewById(R.id.switch_auto_start_downloads);
        switchAutoStartSystem = findViewById(R.id.switch_auto_start_system);
        btnCheckIntegrity = findViewById(R.id.btn_check_integrity);

        initDirectoryPicker();
        loadSettings();
        setupListeners();

        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvAppVersion.setText("Versão: " + versionName);
        } catch (Exception e) {
            tvAppVersion.setText("Versão: 1.0.0");
        }

        autoSaveFeedbackRunnable = () -> {
            tvAutoSaveFeedback.setVisibility(View.INVISIBLE);
        };

        updateColorPaletteVisibility();
        updateColorPalettePreview();
        updateYouTubeSearchTermVisibility(switchYouTubeSearch.isChecked());

        themeManager.registerThemeChangeListener(this::recreate);
        DirectoryInitializer.initializeDirectories(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        themeManager.unregisterThemeChangeListener(this::recreate);
    }

    private void initDirectoryPicker() {
        directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        Log.d(TAG, "Permissão persistente obtida para URI: " + uri);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao persistir permissão: " + e.getMessage(), e);
                        Toast.makeText(this, "Erro ao obter permissão para o diretório selecionado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    downloadUri = uri;
                    DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                    if (documentFile != null) {
                        downloadPath = FileUtils.getDisplayPath(this, uri);
                        tvDownloadPath.setText(downloadPath);
                        
                        if (!documentFile.canWrite()) {
                            Toast.makeText(this, "Aviso: Você pode não ter permissão de escrita neste diretório.", Toast.LENGTH_LONG).show();
                        }
                        
                        try {
                            if (documentFile.createFile("text/plain", "test_write.tmp") != null) {
                                DocumentFile testFile = documentFile.findFile("test_write.tmp");
                                if (testFile != null) testFile.delete();
                                Log.d(TAG, "Teste de escrita OK.");
                            } else {
                                 Toast.makeText(this, "Aviso: Não foi possível escrever no diretório selecionado.", Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                             Toast.makeText(this, "Aviso: Erro ao testar escrita: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }

                        saveDownloadPathSettings();
                        showAutoSaveFeedback();
                        DirectoryInitializer.initializeDirectories(this);
                    }
                }
            }
        );
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        maxDownloads = prefs.getInt(KEY_MAX_DOWNLOADS, 5);
        sliderMaxDownloads.setValue(maxDownloads);
        tvMaxDownloadsValue.setText(String.valueOf(maxDownloads));

        boolean isDynamicColorEnabled = themeManager.isDynamicColorEnabled();
        switchDynamicColor.setChecked(isDynamicColorEnabled);

        downloadPath = prefs.getString(KEY_DOWNLOAD_PATH, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        String uriString = prefs.getString(KEY_DOWNLOAD_URI, null);
        if (uriString != null) {
            try {
                downloadUri = Uri.parse(uriString);
                boolean hasPermission = false;
                for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(downloadUri) && permission.isReadPermission() && permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                if (!hasPermission) {
                    Log.w(TAG, "Permissão perdida para URI: " + uriString + ". Resetando para Downloads.");
                    downloadUri = null;
                    prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                    downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar URI de download", e);
                downloadUri = null;
                prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            }
        }
        tvDownloadPath.setText(downloadPath);

        boolean isYouTubeSearchEnabled = prefs.getBoolean(KEY_YOUTUBE_SEARCH_ENABLED, false);
        switchYouTubeSearch.setChecked(isYouTubeSearchEnabled);

        currentYouTubeSearchTerm = prefs.getString(KEY_YOUTUBE_SEARCH_TERM, DEFAULT_YOUTUBE_SEARCH_TERM);
        previousValidYouTubeSearchTerm = currentYouTubeSearchTerm;
        etYouTubeSearchTerm.setText(currentYouTubeSearchTerm);
        
        // Carregar configurações de auto-start
        boolean autoStartDownloads = prefs.getBoolean(KEY_AUTO_START_DOWNLOADS, false);
        switchAutoStartDownloads.setChecked(autoStartDownloads);
        
        boolean autoStartSystem = prefs.getBoolean(KEY_AUTO_START_SYSTEM, false);
        switchAutoStartSystem.setChecked(autoStartSystem);
    }

    private void setupListeners() {
        sliderMaxDownloads.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvMaxDownloadsValue.setText(String.valueOf(intValue));
            maxDownloads = intValue;
            saveMaxDownloadsSettings();
            com.LDGAMES.utils.DownloadManager.getInstance(SettingsActivity.this).processQueue();
            showAutoSaveFeedback();
        });

        switchYouTubeSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_YOUTUBE_SEARCH_ENABLED, isChecked).apply();
            updateYouTubeSearchTermVisibility(isChecked);
            showAutoSaveFeedback();
        });

        etYouTubeSearchTerm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingYouTubeTerm) {
                    return;
                }
                String currentText = s.toString();
                if (!currentText.contains(NAME_PLACEHOLDER)) {
                    isUpdatingYouTubeTerm = true;
                    etYouTubeSearchTerm.setText(previousValidYouTubeSearchTerm);
                    etYouTubeSearchTerm.setSelection(previousValidYouTubeSearchTerm.length());
                    Toast.makeText(SettingsActivity.this, "O termo '" + NAME_PLACEHOLDER + "' não pode ser removido.", Toast.LENGTH_SHORT).show();
                    isUpdatingYouTubeTerm = false;
                } else {
                    previousValidYouTubeSearchTerm = currentText;
                    currentYouTubeSearchTerm = currentText;
                    saveYouTubeSearchTermSetting();
                    showAutoSaveFeedback();
                }
            }
        });

        switchDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            themeManager.setDynamicColorEnabled(this, isChecked);
            showAutoSaveFeedback();
            updateColorPaletteVisibility();
        });

        cardColorPalette.setOnClickListener(v -> {
            // Corrigido: Usar getSelectedPalette() que retorna int
            ColorPaletteDialog dialog = ColorPaletteDialog.newInstance(themeManager.getSelectedPalette());
            dialog.show(getSupportFragmentManager(), "color_palette_dialog");
        });

        btnSelectDownloadFolder.setOnClickListener(v -> {
            Uri initialUri = null;
            if (downloadUri != null) {
                initialUri = downloadUri;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    initialUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:" + Environment.DIRECTORY_DOWNLOADS
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Não foi possível construir URI inicial para Downloads", e);
                }
            }
            directoryPickerLauncher.launch(initialUri);
        });

        btnManageApis.setOnClickListener(v -> {
            Intent intent = new Intent(this, ApiManagementActivity.class);
            startActivity(intent);
        });
        
        // Listeners para auto-start
        switchAutoStartDownloads.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_AUTO_START_DOWNLOADS, isChecked).apply();
            showAutoSaveFeedback();
        });
        
        setupAutoStartSystemListener();
        
        // Listener para verificação de integridade
        btnCheckIntegrity.setOnClickListener(v -> {
            performIntegrityCheck();
        });
    }

    private void updateYouTubeSearchTermVisibility(boolean isVisible) {
        tilYouTubeSearchTerm.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateColorPaletteVisibility() {
        cardColorPalette.setVisibility(themeManager.isDynamicColorEnabled() ? View.GONE : View.VISIBLE);
    }

    private void updateColorPalettePreview() {
        try {
            // Corrigido: Usar getSelectedPalette() e getPaletteInfo(int)
            int selectedPaletteIndex = themeManager.getSelectedPalette();
            DynamicThemeManager.PaletteInfo paletteInfo = themeManager.getPaletteInfo(selectedPaletteIndex);

            if (paletteInfo != null) {
                colorPalettePreview.findViewById(R.id.preview_primary).getBackground().setTint(paletteInfo.getPrimaryColor());
                colorPalettePreview.findViewById(R.id.preview_primary_container).getBackground().setTint(paletteInfo.getPrimaryContainerColor());
                colorPalettePreview.findViewById(R.id.preview_secondary).getBackground().setTint(paletteInfo.getSecondaryColor());
            } else {
                 Log.w(TAG, "Informações da paleta nulas para o índice: " + selectedPaletteIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter ou aplicar cores da paleta no preview", e);
            // Lidar com o erro, talvez mostrando cores padrão
        }
    }

    private void saveMaxDownloadsSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_MAX_DOWNLOADS, maxDownloads).apply();
    }

    private void saveDownloadPathSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DOWNLOAD_PATH, downloadPath);
        if (downloadUri != null) {
            editor.putString(KEY_DOWNLOAD_URI, downloadUri.toString());
        } else {
            editor.remove(KEY_DOWNLOAD_URI);
        }
        editor.apply();
    }

    private void saveYouTubeSearchTermSetting() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_YOUTUBE_SEARCH_TERM, currentYouTubeSearchTerm).apply();
    }

    private void showAutoSaveFeedback() {
        tvAutoSaveFeedback.setVisibility(View.VISIBLE);
        autoSaveHandler.removeCallbacks(autoSaveFeedbackRunnable);
        autoSaveHandler.postDelayed(autoSaveFeedbackRunnable, 2000);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPaletteSelected(int paletteIndex) {
        themeManager.setSelectedPalette(this, paletteIndex);
        showAutoSaveFeedback();
        updateColorPalettePreview();
        // O listener de tema cuidará de recriar a activity se necessário
    }
    
    /**
     * Habilita ou desabilita o Boot Receiver para inicialização com o sistema
     */
    private void setBootReceiverEnabled(boolean enabled) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.ComponentName componentName = new android.content.ComponentName(this, 
                com.LDGAMES.receivers.BootReceiver.class);
            
            int newState = enabled ? 
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED : 
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                
            pm.setComponentEnabledSetting(componentName, newState, 
                android.content.pm.PackageManager.DONT_KILL_APP);
                
            Log.d(TAG, "BootReceiver " + (enabled ? "habilitado" : "desabilitado") + " com sucesso");
            
            // Feedback para o usuário
            String message = enabled ? 
                "Inicialização automática ativada com sucesso" : 
                "Inicialização automática desativada";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Erro ao configurar BootReceiver: " + e.getMessage(), e);
            // Reverter o switch se houver erro
            switchAutoStartSystem.setOnCheckedChangeListener(null);
            switchAutoStartSystem.setChecked(!enabled);
            setupAutoStartSystemListener(); // Reconfigurar listener
            
            Toast.makeText(this, "Erro ao configurar inicialização automática: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Configura o listener para o switch de auto-start do sistema
     */
    private void setupAutoStartSystemListener() {
        switchAutoStartSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_AUTO_START_SYSTEM, isChecked).apply();
            
            // Configurar ou remover o BootReceiver
            setBootReceiverEnabled(isChecked);
            showAutoSaveFeedback();
        });
    }
    
    /**
     * Executa verificação de integridade dos downloads
     */
    private void performIntegrityCheck() {
        // Desabilitar botão temporariamente
        btnCheckIntegrity.setEnabled(false);
        btnCheckIntegrity.setText("Verificando...");
        
        // Executar verificação em thread separada
        new Thread(() -> {
            try {
                com.LDGAMES.utils.DownloadManager downloadManager = 
                    com.LDGAMES.utils.DownloadManager.getInstance(this);
                
                int problematicCount = downloadManager.forceIntegrityCheck();
                
                // Voltar para UI thread para mostrar resultado
                runOnUiThread(() -> {
                    btnCheckIntegrity.setEnabled(true);
                    btnCheckIntegrity.setText("Verificar Integridade dos Downloads");
                    
                    if (problematicCount > 0) {
                        Toast.makeText(this, 
                            "Verificação concluída. " + problematicCount + " downloads corrigidos.",
                            Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, 
                            "Verificação concluída. Nenhum problema encontrado.",
                            Toast.LENGTH_SHORT).show();
                    }
                    
                    showAutoSaveFeedback();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Erro na verificação de integridade: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    btnCheckIntegrity.setEnabled(true);
                    btnCheckIntegrity.setText("Verificar Integridade dos Downloads");
                    Toast.makeText(this, "Erro durante a verificação", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}

