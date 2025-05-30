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
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.LDGAMES.utils.DirectoryInitializer;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.FileUtils;
import com.LDGAMES.utils.HydraApiManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;

import java.io.File;

public class SettingsActivity extends AppCompatActivity implements ColorPaletteDialog.OnPaletteSelectedListener {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_MAX_DOWNLOADS = "max_downloads";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    // Frequência de atualização removida - não é mais necessária

    private Slider sliderMaxDownloads;
    private TextView tvMaxDownloadsValue;
    private TextView tvDownloadPath;
    private MaterialButton btnSelectDownloadFolder;
    private SwitchCompat switchDynamicColor;
    private MaterialCardView cardColorPalette;
    private View colorPalettePreview;
    private MaterialButton btnManageApis;
    private TextView tvAutoSaveFeedback;
    // Componentes de frequência de atualização removidos - não são mais necessários

    private int maxDownloads = 5; // Valor padrão
    private String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    private Uri downloadUri = null;
    // Variável updateFrequency removida - não é mais necessária
    private HydraApiManager hydraApiManager;
    private DynamicThemeManager themeManager;
    private Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private Runnable autoSaveFeedbackRunnable;

    // Launcher para seleção de diretório
    private ActivityResultLauncher<Uri> directoryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Inicializar o gerenciador de temas
        themeManager = DynamicThemeManager.getInstance();

        // Aplicar tema dinâmico antes de inflar o layout
        themeManager.applyDynamicColors(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Inicializar HydraApiManager
        hydraApiManager = HydraApiManager.getInstance(this);

        // Configurar a toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Inicializar componentes da UI
        sliderMaxDownloads = findViewById(R.id.slider_max_downloads);
        tvMaxDownloadsValue = findViewById(R.id.tv_max_downloads_value);
        tvDownloadPath = findViewById(R.id.tv_download_path);
        btnSelectDownloadFolder = findViewById(R.id.btn_select_download_folder);
        switchDynamicColor = findViewById(R.id.switch_dynamic_color);
        cardColorPalette = findViewById(R.id.card_color_palette);
        colorPalettePreview = findViewById(R.id.color_palette_preview);
        btnManageApis = findViewById(R.id.btn_manage_apis);
        tvAutoSaveFeedback = findViewById(R.id.tv_auto_save_feedback);
        // Inicialização de componentes de frequência de atualização removida

        // Inicializar o launcher para seleção de diretório
        initDirectoryPicker();

        // Carregar configurações salvas
        loadSettings();

        // Configurar listeners
        setupListeners();

        // Exibir versão do app
        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvAppVersion.setText("Versão: " + versionName);
        } catch (Exception e) {
            tvAppVersion.setText("Versão: 1.0.0");
        }

        // Configurar feedback de salvamento automático
        autoSaveFeedbackRunnable = () -> {
            tvAutoSaveFeedback.setVisibility(View.INVISIBLE);
        };

        // Atualizar visibilidade do seletor de paleta de cores
        updateColorPaletteVisibility();

        // Atualizar preview da paleta de cores
        updateColorPalettePreview();

        // Registrar como listener de mudanças de tema
        themeManager.registerThemeChangeListener(() -> {
            // Recriar a activity para aplicar o novo tema
            recreate();
        });
        
        // Inicializar diretórios necessários
        DirectoryInitializer.initializeDirectories(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remover registro como listener de mudanças de tema
        themeManager.unregisterThemeChangeListener(() -> {
            // Não é necessário fazer nada aqui
        });
    }

    private void initDirectoryPicker() {
        directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    // Persistir permissões para acessar o diretório
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        Log.d(TAG, "Permissão persistente obtida para URI: " + uri);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao persistir permissão: " + e.getMessage(), e);
                        Toast.makeText(this, "Erro ao obter permissão para o diretório selecionado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Salvar URI e caminho legível
                    downloadUri = uri;
                    DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                    if (documentFile != null) {
                        downloadPath = FileUtils.getDisplayPath(this, uri);
                        tvDownloadPath.setText(downloadPath);
                        
                        // Verificar se temos permissão de escrita
                        if (!documentFile.canWrite()) {
                            Toast.makeText(this, "Aviso: Você pode não ter permissão de escrita neste diretório. Escolha outro local se ocorrerem erros de download.", Toast.LENGTH_LONG).show();
                        }
                        
                        // Testar escrita no diretório selecionado
                        try {
                            String testFileName = "test_" + System.currentTimeMillis() + ".tmp";
                            DocumentFile testFile = documentFile.createFile("application/octet-stream", testFileName);
                            
                            if (testFile != null) {
                                try {
                                    android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(testFile.getUri(), "w");
                                    if (pfd != null) {
                                        pfd.close();
                                        testFile.delete();  // Limpar após o teste
                                        Log.d(TAG, "Teste de escrita bem-sucedido no diretório selecionado");
                                    } else {
                                        Log.e(TAG, "Não foi possível abrir FileDescriptor para arquivo de teste");
                                        Toast.makeText(this, "Aviso: Não foi possível escrever no diretório selecionado", Toast.LENGTH_LONG).show();
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Erro ao testar escrita no diretório selecionado", e);
                                    Toast.makeText(this, "Aviso: Erro ao escrever no diretório selecionado: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Log.e(TAG, "Não foi possível criar arquivo de teste no diretório selecionado");
                                Toast.makeText(this, "Aviso: Não foi possível criar arquivos no diretório selecionado", Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao testar diretório selecionado", e);
                            Toast.makeText(this, "Aviso: Erro ao testar diretório selecionado: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }

                        // Salvar automaticamente
                        saveDownloadPathSettings();
                        showAutoSaveFeedback();
                        
                        // Inicializar diretórios para garantir que tudo está configurado corretamente
                        DirectoryInitializer.initializeDirectories(this);
                    }
                }
            }
        );
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Carregar número máximo de downloads
        maxDownloads = prefs.getInt(KEY_MAX_DOWNLOADS, 5);
        sliderMaxDownloads.setValue(maxDownloads);
        tvMaxDownloadsValue.setText(String.valueOf(maxDownloads));

        // Carregar configuração de dynamic color
        boolean isDynamicColorEnabled = themeManager.isDynamicColorEnabled();
        switchDynamicColor.setChecked(isDynamicColorEnabled);

        // Carregar pasta de download
        downloadPath = prefs.getString(KEY_DOWNLOAD_PATH, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        String uriString = prefs.getString(KEY_DOWNLOAD_URI, null);
        if (uriString != null) {
            try {
                downloadUri = Uri.parse(uriString);
                
                // Verificar se ainda temos permissão para esta URI
                boolean hasPermission = false;
                for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(downloadUri) && 
                        permission.isReadPermission() && 
                        permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (!hasPermission) {
                    Log.e(TAG, "Permissão persistente perdida para URI: " + uriString);
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

        // Exibir caminho de download
        tvDownloadPath.setText(downloadPath);

        // Carregamento de frequência de atualização removido
    }

    private void setupListeners() {
        // Listener para o slider de downloads
        sliderMaxDownloads.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvMaxDownloadsValue.setText(String.valueOf(intValue));
            maxDownloads = intValue;

            // Salvar automaticamente
            saveMaxDownloadsSettings();
            // Notificar o DownloadManager para processar a fila imediatamente após mudança no limite
            com.LDGAMES.utils.DownloadManager.getInstance(SettingsActivity.this).processQueue();
            showAutoSaveFeedback();
        });

        // Listener para o switch de dynamic color
        switchDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Salvar configuração de dynamic color
            themeManager.setDynamicColorEnabled(this, isChecked);
            showAutoSaveFeedback();

            // Atualizar visibilidade do seletor de paleta de cores
            updateColorPaletteVisibility();
        });

        // Listener para o card de paleta de cores
        cardColorPalette.setOnClickListener(v -> {
            // Abrir dialog de seleção de paleta
            ColorPaletteDialog dialog = ColorPaletteDialog.newInstance(themeManager.getSelectedPalette());
            dialog.show(getSupportFragmentManager(), "color_palette_dialog");
        });

        // Listener para o botão de seleção de pasta
        btnSelectDownloadFolder.setOnClickListener(v -> {
            // Iniciar com a pasta atual, se possível
            Uri initialUri = null;
            if (downloadUri != null) {
                initialUri = downloadUri;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Em Android 8.0+, podemos usar o DocumentsContract para abrir uma pasta específica
                initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:" + Environment.DIRECTORY_DOWNLOADS
                );
            }

            directoryPickerLauncher.launch(initialUri);
        });

        // Listener para o botão de gerenciar APIs
        btnManageApis.setOnClickListener(v -> {
            Intent intent = new Intent(this, ApiManagementActivity.class);
            startActivity(intent);
        });

        // Listener para o RadioGroup de frequência de atualização removido
    }

    private void updateColorPaletteVisibility() {
        boolean isDynamicColorEnabled = switchDynamicColor.isChecked();
        cardColorPalette.setVisibility(isDynamicColorEnabled ? View.GONE : View.VISIBLE);
    }

    private void updateColorPalettePreview() {
        // Obter informações da paleta atual
        DynamicThemeManager.PaletteInfo paletteInfo = themeManager.getPaletteInfo(themeManager.getSelectedPalette());

        // Atualizar a visualização da paleta
        View primaryColor = findViewById(R.id.preview_primary);
        View primaryContainerColor = findViewById(R.id.preview_primary_container);
        View secondaryColor = findViewById(R.id.preview_secondary);

        if (primaryColor != null) {
            primaryColor.setBackgroundColor(paletteInfo.getPrimaryColor());
        }

        if (primaryContainerColor != null) {
            primaryContainerColor.setBackgroundColor(paletteInfo.getPrimaryContainerColor());
        }

        if (secondaryColor != null) {
            secondaryColor.setBackgroundColor(paletteInfo.getSecondaryColor());
        }
    }

    private void saveMaxDownloadsSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_MAX_DOWNLOADS, maxDownloads);
        editor.apply();
    }

    private void saveDownloadPathSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DOWNLOAD_PATH, downloadPath);
        if (downloadUri != null) {
            editor.putString(KEY_DOWNLOAD_URI, downloadUri.toString());
        }
        editor.apply();
        
        // Verificar e corrigir permissões de URI
        DirectoryInitializer.verifyAndFixUriPermissions(this);
    }

    // Método saveUpdateFrequencySettings removido - não é mais necessário

    private void showAutoSaveFeedback() {
        // Cancelar qualquer feedback pendente
        autoSaveHandler.removeCallbacks(autoSaveFeedbackRunnable);

        // Mostrar feedback
        tvAutoSaveFeedback.setText("Configurações salvas automaticamente");
        tvAutoSaveFeedback.setVisibility(View.VISIBLE);

        // Agendar para esconder o feedback após 2 segundos
        autoSaveHandler.postDelayed(autoSaveFeedbackRunnable, 2000);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPaletteSelected(int paletteIndex) {
        // Salvar a paleta selecionada
        themeManager.setSelectedPalette(this, paletteIndex);
        showAutoSaveFeedback();

        // Atualizar preview da paleta
        updateColorPalettePreview();
    }

    /**
     * Método estático para obter o número máximo de downloads configurado
     * @param context Contexto da aplicação
     * @return Número máximo de downloads configurado
     */
    public static int getMaxDownloads(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_MAX_DOWNLOADS, 5); // Valor padrão é 5
    }

    // Método getUpdateFrequency removido - não é mais necessário

    /**
     * Método estático para mostrar um toast de progresso no canto inferior direito
     * @param context Contexto
     * @param message Mensagem a ser exibida
     */
    public static void showProgressToast(Context context, String message) {
        if (context == null) return;
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        // Posicionar no canto inferior direito (aproximado)
        // Os valores de offset podem precisar de ajuste dependendo da densidade da tela
        toast.setGravity(Gravity.BOTTOM | Gravity.END, 50, 50);
        toast.show();
    }
}
