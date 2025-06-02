package com.LDGAMES.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.LDGAMES.R;
import com.LDGAMES.MainActivity;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.utils.DownloadManager;

import java.util.List;

/**
 * Serviço para gerenciar a inicialização automática de downloads
 */
public class AutoStartService extends Service {
    private static final String TAG = "AutoStartService";
    private static final String CHANNEL_ID = "auto_start_channel";
    private static final int NOTIFICATION_ID = 9999;
    private static final long AUTO_START_DELAY = 3000; // 3 segundos de delay após o boot
    
    private NotificationManager notificationManager;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "AutoStartService criado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AutoStartService iniciado");
        
        boolean autoStartDownloads = intent != null && intent.getBooleanExtra("auto_start_downloads", false);
        
        // Criar notificação de foreground
        startForeground(NOTIFICATION_ID, createNotification("Inicializando sistema de downloads..."));
        
        // Aguardar um tempo antes de iniciar downloads (para garantir que o sistema esteja estável)
        handler.postDelayed(() -> {
            try {
                if (autoStartDownloads) {
                    resumePausedDownloads();
                }
                
                // Iniciar MainActivity se necessário
                startMainActivity();
                
                // Parar o serviço após completar a inicialização
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Erro na inicialização automática: " + e.getMessage(), e);
                stopSelf();
            }
        }, AUTO_START_DELAY);
        
        return START_NOT_STICKY; // Não precisa ser reiniciado se morrer
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AutoStartService destruído");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Inicialização Automática";
            String description = "Notificações sobre a inicialização automática do sistema";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Download Manager")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void resumePausedDownloads() {
        try {
            Log.i(TAG, "Iniciando retomada automática de downloads pausados");
            
            DownloadManager downloadManager = DownloadManager.getInstance(this);
            
            // Obter downloads ativos (incluindo pausados)
            List<DownloadInfo> activeDownloads = downloadManager.getActiveDownloads();
            
            int resumedCount = 0;
            for (DownloadInfo download : activeDownloads) {
                if (download.getStatus() == DownloadInfo.STATUS_PAUSED && download.isAutoResumeEnabled()) {
                    Log.d(TAG, "Retomando download: " + download.getFileName());
                    downloadManager.resumeDownload(download);
                    resumedCount++;
                }
            }
            
            if (resumedCount > 0) {
                Log.i(TAG, "Retomados " + resumedCount + " downloads automaticamente");
                updateNotification("Retomados " + resumedCount + " downloads");
            } else {
                Log.d(TAG, "Nenhum download pausado encontrado para retomar");
                updateNotification("Nenhum download para retomar");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro ao retomar downloads: " + e.getMessage(), e);
            updateNotification("Erro ao retomar downloads");
        }
    }

    private void startMainActivity() {
        try {
            SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            boolean shouldStartMainActivity = prefs.getBoolean("auto_start_system", false);
            
            if (shouldStartMainActivity) {
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(mainIntent);
                Log.d(TAG, "MainActivity iniciada automaticamente");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar MainActivity: " + e.getMessage(), e);
        }
    }

    private void updateNotification(String message) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(message));
        }
    }
}