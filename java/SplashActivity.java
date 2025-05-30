package com.LDGAMES;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.activities.ApiManagementActivity;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.FileUtils;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    private static final String KEY_FIRST_RUN = "first_run";

    private Button btnSelectFolder;
    private Button btnConfigureApi;
    private Button btnContinue;
    private TextView tvDownloadPath;
    private Uri downloadUri = null;
    private String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

    private ActivityResultLauncher<Uri> directoryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeManager.getInstance().applyDynamicColors(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Verificar se é a primeira execução
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true);

        // Se não for a primeira execução, ir diretamente para a MainActivity
        if (!isFirstRun) {
            startMainActivity();
            return;
        }

        // Inicializar componentes da UI
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnConfigureApi = findViewById(R.id.btn_configure_api);
        btnContinue = findViewById(R.id.btn_continue);
        tvDownloadPath = findViewById(R.id.tv_download_path);

        // Configurar o launcher para seleção de diretório
        initDirectoryPicker();

        // Definir listeners
        btnSelectFolder.setOnClickListener(v -> {
            Uri initialUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:" + Environment.DIRECTORY_DOWNLOADS
            );
            directoryPickerLauncher.launch(initialUri);
        });

        btnConfigureApi.setOnClickListener(v -> {
            Intent intent = new Intent(this, ApiManagementActivity.class);
            startActivity(intent);
        });

        btnContinue.setOnClickListener(v -> {
            // Salvar que a configuração inicial foi concluída
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FIRST_RUN, false);
            editor.apply();
            
            // Iniciar a MainActivity
            startMainActivity();
        });

        // Inicialmente, o botão continuar estará desabilitado até que o usuário selecione uma pasta
        updateContinueButtonState();
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
                    } catch (Exception e) {
                        Toast.makeText(this, "Erro ao persistir permissão: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Salvar URI e caminho legível
                    downloadUri = uri;
                    DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                    if (documentFile != null) {
                        downloadPath = FileUtils.getDisplayPath(this, uri);
                        tvDownloadPath.setText(downloadPath);

                        // Salvar configurações
                        saveDownloadPathSettings();
                        
                        // Atualizar estado do botão continuar
                        updateContinueButtonState();
                    }
                }
            }
        );
    }

    private void saveDownloadPathSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DOWNLOAD_PATH, downloadPath);
        if (downloadUri != null) {
            editor.putString(KEY_DOWNLOAD_URI, downloadUri.toString());
        }
        editor.apply();
    }

    private void updateContinueButtonState() {
        // O botão continuar estará habilitado somente se uma pasta de download for selecionada
        btnContinue.setEnabled(downloadUri != null);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}