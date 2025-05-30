package com.LDGAMES.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.R;
import com.LDGAMES.activities.DownloadProgressActivity;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.utils.DownloadManager;
import com.LDGAMES.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serviço para gerenciar o processo de download em segundo plano (Modificado para usar exclusivamente SAF e corrigir erros)
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "download_channel";
    private static final int BUFFER_SIZE = 8192;

    // Constantes de ação
    public static final String ACTION_START = "com.LDGAMES.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "com.LDGAMES.action.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "com.LDGAMES.action.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.LDGAMES.action.CANCEL_DOWNLOAD";
    public static final String EXTRA_DOWNLOAD_INFO = "com.LDGAMES.extra.DOWNLOAD_INFO";

    private NotificationManager notificationManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private final ReentrantLock operationLock = new ReentrantLock();

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        executorService = Executors.newFixedThreadPool(5);
        mainHandler = new Handler(Looper.getMainLooper());
        DownloadManager.getInstance(this).setServiceRunning(true);
        Log.d(TAG, "Serviço de download criado.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "onStartCommand recebido com intent ou ação nula.");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        DownloadInfo downloadInfo = intent.getParcelableExtra(EXTRA_DOWNLOAD_INFO);

        if (downloadInfo == null || downloadInfo.getUrl() == null) {
            Log.e(TAG, "DownloadInfo ou URL é null, não é possível processar a ação: " + action);
            return START_NOT_STICKY;
        }

        if (downloadInfo.getFilePath() == null || !downloadInfo.getFilePath().startsWith("content://")) {
             Log.e(TAG, "FilePath inválido (não é URI SAF) para a ação " + action + ": " + downloadInfo.getFilePath());
             return START_NOT_STICKY;
        }

        Log.d(TAG, "Recebida ação: " + action + " para download: " + downloadInfo.getFileName() + " URL: " + downloadInfo.getUrl());

        switch (action) {
            case ACTION_START:
                startDownloadInternal(downloadInfo);
                break;
            case ACTION_PAUSE:
                pauseDownloadInternal(downloadInfo);
                break;
            case ACTION_RESUME:
                resumeDownloadInternal(downloadInfo);
                break;
            case ACTION_CANCEL:
                cancelDownloadInternal(downloadInfo);
                break;
            default:
                 Log.w(TAG, "Ação desconhecida recebida: " + action);
                 break;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Serviço de download sendo destruído.");
        for (DownloadTask task : activeTasks.values()) {
            if (!task.isFinished()) {
                 task.pause();
            }
        }
        executorService.shutdown();
        activeTasks.clear();
        DownloadManager.getInstance(this).setServiceRunning(false);
        Log.d(TAG, "Serviço de download destruído completamente.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Downloads";
            String description = "Notificações sobre o progresso do download";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void startDownload(Context context, DownloadInfo downloadInfo) {
        startAction(context, ACTION_START, downloadInfo);
    }

    public static void pauseDownload(Context context, DownloadInfo downloadInfo) {
        startAction(context, ACTION_PAUSE, downloadInfo);
    }

    public static void resumeDownload(Context context, DownloadInfo downloadInfo) {
        startAction(context, ACTION_RESUME, downloadInfo);
    }

    public static void cancelDownload(Context context, DownloadInfo downloadInfo) {
        startAction(context, ACTION_CANCEL, downloadInfo);
    }

    private static void startAction(Context context, String action, DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getUrl() == null || downloadInfo.getFilePath() == null || !downloadInfo.getFilePath().startsWith("content://")) {
            Log.e(TAG, "Tentativa de iniciar ação " + action + " com DownloadInfo ou FilePath inválido.");
            return;
        }
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
        context.startService(intent);
    }

    private int getNotificationId(DownloadInfo downloadInfo) {
        return Math.abs(downloadInfo.getUrl().hashCode());
    }

    private void startDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String url = downloadInfo.getUrl();
            if (activeTasks.containsKey(url)) {
                DownloadTask existingTask = activeTasks.get(url);
                if (existingTask != null && !existingTask.isFinished()) {
                    Log.d(TAG, "Download já está em andamento ou pausado: " + downloadInfo.getFileName());
                    updateNotification(downloadInfo);
                    return;
                }
            }
            Log.d(TAG, "Iniciando download: " + downloadInfo.getFileName());
            downloadInfo.setStatus(DownloadInfo.STATUS_RUNNING);
            DownloadManager.getInstance(this).updateDownload(downloadInfo);
            startForeground(getNotificationId(downloadInfo), createNotification(downloadInfo));
            DownloadTask downloadTask = new DownloadTask(downloadInfo);
            activeTasks.put(url, downloadTask);
            executorService.execute(downloadTask);
        } finally {
            operationLock.unlock();
        }
    }

    private void pauseDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String url = downloadInfo.getUrl();
            DownloadTask task = activeTasks.get(url);
            if (task != null && !task.isFinished() && task.isActive()) {
                Log.d(TAG, "Pausando download: " + downloadInfo.getFileName());
                task.pause();
            } else {
                 Log.w(TAG, "Tentativa de pausar download que não está ativo ou não existe: " + downloadInfo.getFileName());
                 if (downloadInfo.getStatus() != DownloadInfo.STATUS_PAUSED) {
                     downloadInfo.setStatus(DownloadInfo.STATUS_PAUSED);
                     downloadInfo.setLastPauseTime(System.currentTimeMillis());
                     DownloadManager.getInstance(this).updateDownload(downloadInfo);
                     updateNotification(downloadInfo);
                 }
            }
        } finally {
            operationLock.unlock();
        }
    }

    private void resumeDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String url = downloadInfo.getUrl();
            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile partialFile = DocumentFile.fromSingleUri(this, fileUri);

            if (partialFile == null || !partialFile.exists() || !partialFile.isFile()) {
                Log.w(TAG, "Arquivo parcial SAF não encontrado ou inválido, reiniciando download: " + downloadInfo.getFilePath());
                downloadInfo.setDownloadedSize(0);
                downloadInfo.setProgress(0);
            } else {
                long currentFileSize = partialFile.length();
                if (currentFileSize != downloadInfo.getDownloadedSize()) {
                    Log.w(TAG, "Tamanho do arquivo parcial SAF (" + currentFileSize +
                          ") não corresponde ao tamanho baixado registrado (" +
                          downloadInfo.getDownloadedSize() + "), atualizando...");
                    downloadInfo.setDownloadedSize(currentFileSize);
                    if (downloadInfo.getFileSize() > 0) {
                        int progress = (int) ((currentFileSize * 100) / downloadInfo.getFileSize());
                        downloadInfo.setProgress(progress);
                    } else {
                        downloadInfo.setProgress(0);
                    }
                }
            }

            DownloadTask existingTask = activeTasks.get(url);
            if (existingTask != null && !existingTask.isFinished()) {
                 Log.d(TAG, "Tentando retomar tarefa existente para: " + downloadInfo.getFileName());
                 if (existingTask.resume()) {
                      Log.d(TAG, "Tarefa existente retomada com sucesso.");
                      downloadInfo.setStatus(DownloadInfo.STATUS_RESUMING);
                      downloadInfo.setLastResumeTime(System.currentTimeMillis());
                      DownloadManager.getInstance(this).updateDownload(downloadInfo);
                      updateNotification(downloadInfo);
                      return;
                 } else {
                     Log.w(TAG, "Falha ao retomar tarefa existente, cancelando-a e criando uma nova.");
                     if (existingTask != null) {
                         existingTask.cancel();
                     }
                     activeTasks.remove(url);
                 }
            }

            Log.d(TAG, "Criando nova tarefa para retomar download: " + downloadInfo.getFileName());
            downloadInfo.setStatus(DownloadInfo.STATUS_RESUMING);
            downloadInfo.setLastResumeTime(System.currentTimeMillis());
            DownloadManager.getInstance(this).updateDownload(downloadInfo);
            startForeground(getNotificationId(downloadInfo), createNotification(downloadInfo));
            DownloadTask downloadTask = new DownloadTask(downloadInfo);
            activeTasks.put(url, downloadTask);
            executorService.execute(downloadTask);
            updateNotification(downloadInfo);

        } catch (Exception e) {
             Log.e(TAG, "Erro geral ao tentar retomar download: " + downloadInfo.getFileName(), e);
             downloadInfo.setStatus(DownloadInfo.STATUS_FAILED);
             downloadInfo.setErrorMessage("Erro ao retomar download: " + e.getMessage());
             DownloadManager.getInstance(this).updateDownload(downloadInfo);
             updateNotification(downloadInfo);
             activeTasks.remove(downloadInfo.getUrl());
             checkStopService();
        } finally {
            operationLock.unlock();
        }
    }

    private void cancelDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String url = downloadInfo.getUrl();
            DownloadTask task = activeTasks.remove(url);
            if (task != null) {
                Log.d(TAG, "Cancelando download: " + downloadInfo.getFileName());
                task.cancel();
            } else {
                 Log.w(TAG, "Tentativa de cancelar download sem tarefa ativa: " + downloadInfo.getFileName());
            }
            notificationManager.cancel(getNotificationId(downloadInfo));
            checkStopService();
        } finally {
            operationLock.unlock();
        }
    }

    private void checkStopService() {
        operationLock.lock();
        try {
            DownloadManager manager = DownloadManager.getInstance(this);
            if (!manager.hasActiveOrQueuedDownloads()) {
                Log.i(TAG, "Nenhum download ativo ou na fila. Parando o serviço.");
                stopSelf();
            } else {
                Log.d(TAG, "Ainda há downloads ativos ou na fila. Serviço continua.");
            }
        } finally {
            operationLock.unlock();
        }
    }

    private Notification createNotification(DownloadInfo downloadInfo) {
        Intent notificationIntent = new Intent(this, DownloadProgressActivity.class);
        notificationIntent.putExtra("download_url", downloadInfo.getUrl());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingIntentRequestCode = getNotificationId(downloadInfo);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                pendingIntentRequestCode,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getNotificationTitle(downloadInfo))
                .setContentText(getNotificationText(downloadInfo))
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pendingIntent)
                .setOngoing(shouldBeOngoing(downloadInfo))
                .setAutoCancel(shouldAutoCancel(downloadInfo))
                .setOnlyAlertOnce(true);

        addNotificationActions(builder, downloadInfo);

        if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
             if (downloadInfo.getFileSize() > 0 && downloadInfo.getProgress() >= 0) {
                 builder.setProgress(100, downloadInfo.getProgress(), false);
             } else {
                 builder.setProgress(0, 0, true);
             }
        } else {
             builder.setProgress(0, 0, false);
        }
        return builder.build();
    }

    private boolean shouldBeOngoing(DownloadInfo downloadInfo) {
        return downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING ||
               downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING ||
               downloadInfo.getStatus() == DownloadInfo.STATUS_PAUSED ||
               downloadInfo.getStatus() == DownloadInfo.STATUS_QUEUED;
    }

    private boolean shouldAutoCancel(DownloadInfo downloadInfo) {
        return downloadInfo.getStatus() == DownloadInfo.STATUS_COMPLETED ||
               downloadInfo.getStatus() == DownloadInfo.STATUS_FAILED ||
               downloadInfo.getStatus() == DownloadInfo.STATUS_CANCELLED;
    }

    private void updateNotification(DownloadInfo downloadInfo) {
         if (downloadInfo != null && downloadInfo.getUrl() != null) {
             notificationManager.notify(getNotificationId(downloadInfo), createNotification(downloadInfo));
         } else {
             Log.w(TAG, "Tentativa de atualizar notificação com DownloadInfo ou URL nulo.");
         }
    }

    private String getNotificationTitle(DownloadInfo downloadInfo) {
        switch (downloadInfo.getStatus()) {
            case DownloadInfo.STATUS_RUNNING:
            case DownloadInfo.STATUS_RESUMING:
                return "Baixando";
            case DownloadInfo.STATUS_PAUSED:
                return "Pausado";
            case DownloadInfo.STATUS_QUEUED:
                return "Na fila";
            case DownloadInfo.STATUS_COMPLETED:
                return "Download Concluído";
            case DownloadInfo.STATUS_FAILED:
                return "Falha no Download";
            case DownloadInfo.STATUS_CANCELLED:
                return "Download Cancelado";
            default:
                return "Download";
        }
    }

    private String getNotificationText(DownloadInfo downloadInfo) {
         String fileName = downloadInfo.getFileName() != null ? downloadInfo.getFileName() : "";
         switch (downloadInfo.getStatus()) {
            case DownloadInfo.STATUS_RUNNING:
            case DownloadInfo.STATUS_RESUMING:
                 if (downloadInfo.getFileSize() > 0) {
                     return String.format("%s (%d%%)", fileName, downloadInfo.getProgress());
                 } else {
                     return String.format("%s (%s)", fileName, FileUtils.formatFileSize(downloadInfo.getDownloadedSize()));
                 }
            case DownloadInfo.STATUS_PAUSED:
                 return String.format("%s (%s / %s)", fileName,
                                      FileUtils.formatFileSize(downloadInfo.getDownloadedSize()),
                                      FileUtils.formatFileSize(downloadInfo.getFileSize()));
            case DownloadInfo.STATUS_FAILED:
                 return String.format("%s - %s", fileName, downloadInfo.getErrorMessage() != null ? downloadInfo.getErrorMessage() : "Erro desconhecido");
            default:
                return fileName;
        }
    }

    private void addNotificationActions(NotificationCompat.Builder builder, DownloadInfo downloadInfo) {
        String pauseActionTitle = "Pausar";
        String resumeActionTitle = "Retomar";
        String cancelActionTitle = "Cancelar";
        int baseRequestCode = getNotificationId(downloadInfo);

        if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
            Intent pauseIntent = new Intent(this, DownloadService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            pauseIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            pauseIntent.setData(Uri.parse(downloadInfo.getUrl()));
            PendingIntent pausePendingIntent = PendingIntent.getService(this, baseRequestCode + 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_pause, pauseActionTitle, pausePendingIntent);
        } else if (downloadInfo.getStatus() == DownloadInfo.STATUS_PAUSED) {
            Intent resumeIntent = new Intent(this, DownloadService.class);
            resumeIntent.setAction(ACTION_RESUME);
            resumeIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            resumeIntent.setData(Uri.parse(downloadInfo.getUrl()));
            PendingIntent resumePendingIntent = PendingIntent.getService(this, baseRequestCode + 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_play, resumeActionTitle, resumePendingIntent);
        }

        if (downloadInfo.getStatus() != DownloadInfo.STATUS_COMPLETED &&
            downloadInfo.getStatus() != DownloadInfo.STATUS_FAILED &&
            downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
            Intent cancelIntent = new Intent(this, DownloadService.class);
            cancelIntent.setAction(ACTION_CANCEL);
            cancelIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            cancelIntent.setData(Uri.parse(downloadInfo.getUrl()));
            PendingIntent cancelPendingIntent = PendingIntent.getService(this, baseRequestCode + 3, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_cancel, cancelActionTitle, cancelPendingIntent);
        }
    }

    // --- Classe Interna DownloadTask ---

    private class DownloadTask implements Runnable {
        private final DownloadInfo downloadInfo;
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean active = new AtomicBoolean(false);
        private HttpURLConnection connection = null;
        private InputStream inputStream = null;
        private OutputStream outputStream = null;

        DownloadTask(DownloadInfo downloadInfo) {
            this.downloadInfo = downloadInfo;
        }

        public DownloadInfo getDownloadInfo() {
            return downloadInfo;
        }

        public boolean isFinished() {
            return finished.get();
        }

        public boolean isActive() {
             return active.get();
        }

        public void pause() {
            paused.set(true);
            Log.d(TAG, "Task pause() chamado para: " + downloadInfo.getFileName());
        }

        public boolean resume() {
             if (paused.compareAndSet(true, false)) {
                 synchronized(this) {
                     notifyAll();
                 }
                 Log.d(TAG, "Task resume() chamado e notificado para: " + downloadInfo.getFileName());
                 return true;
             } else {
                 Log.w(TAG, "Task resume() chamado mas não estava pausado para: " + downloadInfo.getFileName());
                 return false;
             }
        }

        public void cancel() {
            cancelled.set(true);
            Log.d(TAG, "Task cancel() chamado para: " + downloadInfo.getFileName());
            // Interromper a thread para acelerar o cancelamento
            Thread.currentThread().interrupt();
            // Fechar recursos imediatamente se possível
            closeResources();
        }

        @Override
        public void run() {
            active.set(true);
            Log.d(TAG, "DownloadTask run() iniciado para: " + downloadInfo.getFileName());
            long currentDownloadedSize = downloadInfo.getDownloadedSize();
            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile targetFile = DocumentFile.fromSingleUri(DownloadService.this, fileUri);

            if (targetFile == null || !targetFile.canWrite()) {
                handleError("Não é possível escrever no arquivo de destino (SAF): " + fileUri);
                return;
            }

            try {
                URL url = new URL(downloadInfo.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000); // 15 segundos
                connection.setReadTimeout(30000); // 30 segundos
                connection.setInstanceFollowRedirects(true); // Seguir redirecionamentos HTTP

                // *** INÍCIO DAS MODIFICAÇÕES ***
                // Adicionar Cookies se existirem
                String cookies = downloadInfo.getCookies();
                if (cookies != null && !cookies.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookies);
                    Log.d(TAG, "Adicionando Cookie: " + cookies);
                }

                // Adicionar Headers Personalizados (incluindo User-Agent, Referer, etc.)
                Map<String, String> headers = downloadInfo.getCustomHeaders();
                if (headers != null && !headers.isEmpty()) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                        Log.d(TAG, "Adicionando Header: " + entry.getKey() + " = " + entry.getValue());
                    }
                }
                // *** FIM DAS MODIFICAÇÕES ***

                // Configurar Range header para retomar download
                if (currentDownloadedSize > 0) {
                    connection.setRequestProperty("Range", "bytes=" + currentDownloadedSize + "-");
                    Log.d(TAG, "Retomando download. Range: bytes=" + currentDownloadedSize + "-");
                }

                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response Code: " + responseCode);

                // Verificar se o servidor suporta retomada (206 Partial Content) ou se é um novo download (200 OK)
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    handleError("Erro no servidor: " + responseCode + " " + connection.getResponseMessage());
                    return;
                }

                // Obter tamanho total do arquivo (se ainda não conhecido ou se mudou)
                long serverFileSize = connection.getContentLengthLong(); // Usa getContentLengthLong() para > 2GB
                if (serverFileSize > 0) {
                    if (responseCode == HttpURLConnection.HTTP_OK) { // Novo download completo
                        downloadInfo.setFileSize(serverFileSize);
                        downloadInfo.setDownloadedSize(0); // Garantir que começa do zero
                        currentDownloadedSize = 0;
                    } else { // Retomada (HTTP_PARTIAL)
                        // O tamanho total real é o tamanho já baixado + o conteúdo restante
                        long totalSize = currentDownloadedSize + serverFileSize;
                        downloadInfo.setFileSize(totalSize);
                    }
                } else if (downloadInfo.getFileSize() <= 0) {
                    // Se o servidor não fornecer Content-Length, não podemos calcular progresso
                    Log.w(TAG, "Servidor não retornou Content-Length. Progresso não será exibido.");
                    downloadInfo.setFileSize(-1); // Indicar tamanho desconhecido
                }

                // Abrir OutputStream (modo append se retomando, truncar se novo download)
                String openMode = (responseCode == HttpURLConnection.HTTP_PARTIAL && currentDownloadedSize > 0) ? "wa" : "wt";
                try {
                    outputStream = getContentResolver().openOutputStream(fileUri, openMode);
                    if (outputStream == null) {
                        throw new FileNotFoundException("Não foi possível abrir OutputStream para " + fileUri);
                    }
                } catch (FileNotFoundException e) {
                    handleError("Erro ao abrir arquivo de destino (SAF): " + e.getMessage());
                    return;
                }

                inputStream = new BufferedInputStream(connection.getInputStream());
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long lastUpdateTime = System.currentTimeMillis();

                // Atualizar status para RUNNING (mesmo se estava RESUMING)
                downloadInfo.setStatus(DownloadInfo.STATUS_RUNNING);
                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // Verificar pausa ou cancelamento
                    if (paused.get()) {
                        Log.d(TAG, "Download pausado (detectado no loop): " + downloadInfo.getFileName());
                        downloadInfo.setStatus(DownloadInfo.STATUS_PAUSED);
                        downloadInfo.setLastPauseTime(System.currentTimeMillis());
                        mainHandler.post(() -> {
                            DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                            updateNotification(downloadInfo);
                        });
                        // Esperar pela retomada
                        synchronized(this) {
                            while (paused.get()) {
                                try {
                                    wait();
                                } catch (InterruptedException e) {
                                    Log.w(TAG, "Thread interrompida enquanto pausada: " + downloadInfo.getFileName());
                                    Thread.currentThread().interrupt(); // Re-set a flag de interrupção
                                    // Se foi interrompida, verificar se foi cancelada
                                    if (cancelled.get()) break;
                                }
                            }
                        }
                        // Se saiu do wait e não foi cancelado, significa que foi retomado
                        if (!cancelled.get()) {
                             Log.d(TAG, "Download retomado (detectado após wait): " + downloadInfo.getFileName());
                             downloadInfo.setStatus(DownloadInfo.STATUS_RESUMING); // Ou RUNNING?
                             downloadInfo.setLastResumeTime(System.currentTimeMillis());
                             mainHandler.post(() -> {
                                 DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                                 updateNotification(downloadInfo);
                             });
                             // Voltar ao início do loop para re-verificar status
                             continue;
                        }
                    }

                    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "Download cancelado (detectado no loop): " + downloadInfo.getFileName());
                        downloadInfo.setStatus(DownloadInfo.STATUS_CANCELLED);
                        mainHandler.post(() -> {
                            DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                            // A remoção da notificação e limpeza é feita em cancelDownloadInternal
                            // ou pelo DownloadManager
                        });
                        // Sair do loop
                        break;
                    }

                    // Escrever no arquivo
                    outputStream.write(buffer, 0, bytesRead);
                    currentDownloadedSize += bytesRead;
                    downloadInfo.setDownloadedSize(currentDownloadedSize);

                    // Calcular progresso e velocidade e atualizar notificação (com moderação)
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= 1000) { // Atualizar a cada 1 segundo
                        if (downloadInfo.getFileSize() > 0) {
                            int progress = (int) ((currentDownloadedSize * 100) / downloadInfo.getFileSize());
                            downloadInfo.setProgress(progress);
                        }
                        downloadInfo.calculateSpeed();
                        downloadInfo.calculateEstimatedTimeRemaining();

                        // Atualizar no DownloadManager e Notificação na thread principal
                        mainHandler.post(() -> {
                            DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                            updateNotification(downloadInfo);
                        });
                        lastUpdateTime = currentTime;
                    }
                }

                // Verificar se o loop terminou devido a cancelamento ou conclusão
                if (!cancelled.get() && !Thread.currentThread().isInterrupted()) {
                    // Download concluído
                    Log.d(TAG, "Download concluído: " + downloadInfo.getFileName());
                    downloadInfo.setStatus(DownloadInfo.STATUS_COMPLETED);
                    downloadInfo.setProgress(100);
                    downloadInfo.setEndTime(System.currentTimeMillis());
                    downloadInfo.setSpeed(0);
                    downloadInfo.setEstimatedTimeRemaining("");
                    mainHandler.post(() -> {
                        DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                        updateNotification(downloadInfo);
                    });
                }

            } catch (IOException e) {
                if (cancelled.get() || paused.get()) {
                    // Se foi cancelado ou pausado, a IOException pode ser esperada (ex: conexão fechada)
                    Log.w(TAG, "IOException durante pausa/cancelamento: " + e.getMessage());
                    // O status já foi definido como PAUSED ou CANCELLED
                } else {
                    handleError("Erro de I/O durante o download: " + e.getMessage());
                }
            } catch (Exception e) {
                 handleError("Erro inesperado durante o download: " + e.getMessage());
            } finally {
                closeResources();
                finished.set(true);
                active.set(false);
                // Remover a tarefa do mapa de tarefas ativas no serviço
                activeTasks.remove(downloadInfo.getUrl());
                // Parar o foreground e verificar se o serviço pode parar
                mainHandler.post(() -> {
                    stopForeground(false); // Manter notificação se concluído/falhou
                    updateNotification(downloadInfo); // Atualizar notificação final
                    checkStopService();
                });
                Log.d(TAG, "DownloadTask run() finalizado para: " + downloadInfo.getFileName() + " Status: " + downloadInfo.getStatusText());
            }
        }

        private void handleError(String message) {
            Log.e(TAG, "Erro no download (" + downloadInfo.getFileName() + "): " + message);
            downloadInfo.setStatus(DownloadInfo.STATUS_FAILED);
            downloadInfo.setErrorMessage(message);
            downloadInfo.setSpeed(0);
            downloadInfo.setEstimatedTimeRemaining("");
            // Atualizar no DownloadManager e Notificação na thread principal
            mainHandler.post(() -> {
                DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo);
                updateNotification(downloadInfo);
            });
        }

        private void closeResources() {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Erro ao fechar OutputStream", e);
            }
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Erro ao fechar InputStream", e);
            }
            if (connection != null) {
                connection.disconnect();
            }
            Log.d(TAG, "Recursos fechados para: " + downloadInfo.getFileName());
        }
    }
}

