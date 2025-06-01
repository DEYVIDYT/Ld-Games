package com.LDGAMES.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.LDGAMES.MainActivity;
import com.LDGAMES.services.AutoStartService;

/**
 * Receiver para inicialização automática com o sistema
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_AUTO_START_SYSTEM = "auto_start_system";
    private static final String KEY_AUTO_START_DOWNLOADS = "auto_start_downloads";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            
            Log.d(TAG, "Recebido evento de boot/atualização: " + intent.getAction());
            
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoStartSystem = prefs.getBoolean(KEY_AUTO_START_SYSTEM, false);
            boolean autoStartDownloads = prefs.getBoolean(KEY_AUTO_START_DOWNLOADS, false);
            
            if (autoStartSystem) {
                Log.i(TAG, "Iniciando app automaticamente após boot");
                
                // Iniciar o serviço de auto-start que cuidará da lógica
                Intent serviceIntent = new Intent(context, AutoStartService.class);
                serviceIntent.putExtra("auto_start_downloads", autoStartDownloads);
                
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "AutoStartService iniciado com sucesso");
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao iniciar AutoStartService: " + e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "Auto-start com sistema não está habilitado");
            }
        }
    }
}