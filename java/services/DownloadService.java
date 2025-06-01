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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.R;
import com.LDGAMES.activities.DownloadProgressActivity;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.utils.DownloadManager;
import com.LDGAMES.utils.DownloadResumeHelper;
import com.LDGAMES.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serviço para gerenciar o processo de download em segundo plano (Modificado para SAF, múltiplas fontes, atualização de link)
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "download_channel";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_URL_RETRIES = 3; // Máximo de voltas completas na lista de URLs

    // Constantes de ação
    public static final String ACTION_START = "com.LDGAMES.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "com.LDGAMES.action.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "com.LDGAMES.action.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.LDGAMES.action.CANCEL_DOWNLOAD";
    public static final String EXTRA_DOWNLOAD_INFO = "com.LDGAMES.extra.DOWNLOAD_INFO";

    private NotificationManager notificationManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    // Usar filePath como chave para identificar a tarefa associada ao arquivo destino
    private final Map<String, DownloadTask> activeTasksByPath = new ConcurrentHashMap<>();
    private final ReentrantLock operationLock = new ReentrantLock();

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        // Ajustar o número de threads conforme necessário ou baseado nas configurações
        executorService = Executors.newFixedThreadPool(DownloadManager.getInstance(this).getConcurrentDownloadLimit());
        mainHandler = new Handler(Looper.getMainLooper());
        DownloadManager.getInstance(this).setServiceRunning(true);
        Log.d(TAG, "Serviço de download criado.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "onStartCommand recebido com intent ou ação nula.");
            stopSelfIfIdle();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        DownloadInfo downloadInfo = intent.getParcelableExtra(EXTRA_DOWNLOAD_INFO);

        // Validar DownloadInfo e FilePath
        if (downloadInfo == null || downloadInfo.getFilePath() == null || !downloadInfo.getFilePath().startsWith("content://")) {
            Log.e(TAG, "DownloadInfo ou FilePath inválido para a ação: " + action);
            stopSelfIfIdle();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Recebida ação: " + action + " para download: " + downloadInfo.getFileName() + " Path: " + downloadInfo.getFilePath());

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
        // Não parar o serviço imediatamente, as tarefas podem estar rodando
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
        // Tentar pausar tarefas ativas antes de desligar
        for (DownloadTask task : activeTasksByPath.values()) {
            if (!task.isFinished()) {
                task.pause(); // Solicita pausa, mas pode não completar a tempo
            }
        }
        executorService.shutdown(); // Inicia desligamento graceful
        activeTasksByPath.clear();
        DownloadManager.getInstance(this).setServiceRunning(false); // Notifica o manager
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

    // Métodos estáticos para iniciar ações no serviço
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
        if (downloadInfo == null || downloadInfo.getFilePath() == null || !downloadInfo.getFilePath().startsWith("content://")) {
            Log.e(TAG, "Tentativa de iniciar ação " + action + " com DownloadInfo ou FilePath inválido.");
            return;
        }
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
        context.startService(intent);
    }

    // Usar filePath para gerar ID único da notificação
    private int getNotificationId(DownloadInfo downloadInfo) {
        return Math.abs(downloadInfo.getFilePath().hashCode());
    }

    private void startDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String filePath = downloadInfo.getFilePath();
            if (activeTasksByPath.containsKey(filePath)) {
                DownloadTask existingTask = activeTasksByPath.get(filePath);
                if (existingTask != null && !existingTask.isFinished()) {
                    Log.d(TAG, "Tarefa de download já existe para o path: " + filePath);
                    updateNotification(downloadInfo); // Atualizar notificação caso o estado tenha mudado
                    return;
                }
            }
            Log.d(TAG, "Iniciando nova tarefa de download para: " + downloadInfo.getFileName());
            // O status já deve ser RUNNING ou RESUMING definido pelo DownloadManager
            // DownloadManager.getInstance(this).updateDownload(downloadInfo); // Manager já atualizou
            startForeground(getNotificationId(downloadInfo), createNotification(downloadInfo));
            DownloadTask downloadTask = new DownloadTask(downloadInfo);
            activeTasksByPath.put(filePath, downloadTask);
            executorService.execute(downloadTask);
        } finally {
            operationLock.unlock();
        }
    }

    private void pauseDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String filePath = downloadInfo.getFilePath();
            DownloadTask task = activeTasksByPath.get(filePath);
            if (task != null && !task.isFinished() && task.isActive()) {
                Log.d(TAG, "Pausando tarefa de download: " + downloadInfo.getFileName());
                task.pause(); // Solicita a pausa da tarefa
                // A tarefa, ao pausar, notificará o DownloadManager
            } else {
                Log.w(TAG, "Tentativa de pausar tarefa que não está ativa ou não existe: " + downloadInfo.getFileName());
                // Se a tarefa não existe mas o manager acha que está rodando, forçar status PAUSED
                if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
                    downloadInfo.setStatus(DownloadInfo.STATUS_PAUSED);
                    downloadInfo.setLastPauseTime(System.currentTimeMillis());
                    mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
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
            String filePath = downloadInfo.getFilePath();
            // Validação do arquivo parcial e ajuste do downloadedSize
            if (!validateAndAdjustPartialFile(downloadInfo)) {
                // Se a validação falhar (arquivo não existe), o manager já deve ter tratado
                // Mas por segurança, podemos falhar aqui também.
                Log.e(TAG, "Falha ao validar arquivo parcial para retomada: " + filePath);
                downloadInfo.setStatus(DownloadInfo.STATUS_FAILED);
                downloadInfo.setErrorMessage("Erro ao validar arquivo para retomar.");
                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                updateNotification(downloadInfo);
                activeTasksByPath.remove(filePath);
                stopSelfIfIdle();
                return;
            }

            DownloadTask existingTask = activeTasksByPath.get(filePath);
            if (existingTask != null && !existingTask.isFinished()) {
                Log.d(TAG, "Tentando retomar tarefa existente para: " + downloadInfo.getFileName());
                if (existingTask.resume()) {
                    Log.d(TAG, "Tarefa existente retomada com sucesso.");
                    // O status RESUMING já foi definido pelo Manager
                    // DownloadManager.getInstance(this).updateDownload(downloadInfo); // Manager já atualizou
                    updateNotification(downloadInfo);
                    return;
                } else {
                    Log.w(TAG, "Falha ao retomar tarefa existente (não estava pausada?), cancelando-a e criando uma nova.");
                    existingTask.cancel(); // Cancelar a tarefa antiga
                    activeTasksByPath.remove(filePath); // Remover do mapa
                }
            }

            Log.d(TAG, "Criando nova tarefa para retomar/iniciar download: " + downloadInfo.getFileName());
            // O status RESUMING/RUNNING já foi definido pelo Manager
            // DownloadManager.getInstance(this).updateDownload(downloadInfo); // Manager já atualizou
            startForeground(getNotificationId(downloadInfo), createNotification(downloadInfo));
            DownloadTask downloadTask = new DownloadTask(downloadInfo);
            activeTasksByPath.put(filePath, downloadTask);
            executorService.execute(downloadTask);
            updateNotification(downloadInfo);

        } catch (Exception e) {
            Log.e(TAG, "Erro geral ao tentar retomar download: " + downloadInfo.getFileName(), e);
            downloadInfo.setStatus(DownloadInfo.STATUS_FAILED);
            downloadInfo.setErrorMessage("Erro ao retomar: " + e.getMessage());
            mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
            updateNotification(downloadInfo);
            activeTasksByPath.remove(downloadInfo.getFilePath());
            stopSelfIfIdle();
        } finally {
            operationLock.unlock();
        }
    }

    private boolean validateAndAdjustPartialFile(DownloadInfo downloadInfo) {
        try {
            // Usar o novo sistema de validação robusto
            DownloadResumeHelper.ValidationResult validation = 
                DownloadResumeHelper.validatePartialFile(this, downloadInfo);
            
            Log.d(TAG, String.format("Validação do arquivo %s: válido=%s, razão=%s", 
                downloadInfo.getFileName(), validation.isValid, validation.reason));
            
            if (!validation.isValid) {
                if (validation.shouldRestart) {
                    Log.w(TAG, "Arquivo deve ser reiniciado: " + validation.reason);
                    downloadInfo.setDownloadedSize(0);
                    downloadInfo.setProgress(0);
                    // Tentar deletar arquivo corrompido
                    deletePartialFileSafely(downloadInfo.getFilePath());
                }
                return false;
            }
            
            // Aplicar correções se necessário
            if (validation.needsAdjustment) {
                DownloadResumeHelper.applyValidationCorrections(downloadInfo, validation);
                // Atualizar o manager com as correções
                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
            }
            
            // Testar capacidade de retomada do servidor se possível
            if (downloadInfo.getDownloadedSize() > 0) {
                String currentUrl = downloadInfo.getUrl();
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    DownloadResumeHelper.ResumeTestResult resumeTest = 
                        DownloadResumeHelper.testResumeCapability(
                            currentUrl, 
                            downloadInfo.getDownloadedSize(),
                            downloadInfo.getCookies(),
                            downloadInfo.getCustomHeaders()
                        );
                    
                    if (!resumeTest.resumeSupported) {
                        Log.w(TAG, "Servidor não suporta retomada: " + resumeTest.reason + 
                             ". Reiniciando download.");
                        downloadInfo.setDownloadedSize(0);
                        downloadInfo.setProgress(0);
                        deletePartialFileSafely(downloadInfo.getFilePath());
                        return false;
                    } else {
                        Log.i(TAG, "Retomada confirmada: " + resumeTest.reason);
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Erro na validação avançada do arquivo: " + e.getMessage(), e);
            // Fallback para validação básica
            downloadInfo.setDownloadedSize(0);
            downloadInfo.setProgress(0);
            return false;
        }
    }

    private void cancelDownloadInternal(DownloadInfo downloadInfo) {
        operationLock.lock();
        try {
            String filePath = downloadInfo.getFilePath();
            DownloadTask task = activeTasksByPath.remove(filePath);
            if (task != null) {
                Log.d(TAG, "Cancelando tarefa de download: " + downloadInfo.getFileName());
                task.cancel(); // Solicita cancelamento da tarefa
            } else {
                Log.w(TAG, "Tentativa de cancelar tarefa sem tarefa ativa: " + downloadInfo.getFileName());
                // Se a tarefa não existe, garantir que o arquivo seja deletado (pode ter sido cancelado antes de iniciar)
                deletePartialFileSafely(downloadInfo.getFilePath());
            }
            notificationManager.cancel(getNotificationId(downloadInfo));
            // O DownloadManager já marcou como CANCELLED e notificou
            stopSelfIfIdle();
        } finally {
            operationLock.unlock();
        }
    }

    // Verifica se o serviço pode parar
    private void stopSelfIfIdle() {
        operationLock.lock();
        try {
            if (activeTasksByPath.isEmpty()) {
                Log.i(TAG, "Nenhuma tarefa ativa. Parando o serviço.");
                stopForeground(true); // Remover notificação persistente se houver
                stopSelf();
            } else {
                Log.d(TAG, activeTasksByPath.size() + " tarefas ativas restantes. Serviço continua.");
            }
        } finally {
            operationLock.unlock();
        }
    }

    // Deleta o arquivo associado a um DownloadInfo
    private void deletePartialFileSafely(String fileUriPath) {
        if (fileUriPath == null || !fileUriPath.startsWith("content://")) return;
        try {
            Uri fileUri = Uri.parse(fileUriPath);
            DocumentFile fileToDelete = DocumentFile.fromSingleUri(this, fileUri);
            if (fileToDelete != null && fileToDelete.exists() && fileToDelete.isFile()) {
                if (fileToDelete.delete()) {
                    Log.d(TAG, "Arquivo deletado (SAF): " + fileUriPath);
                } else {
                    Log.w(TAG, "Falha ao deletar arquivo (SAF): " + fileUriPath);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tentar deletar arquivo (SAF): " + fileUriPath, e);
        }
    }

    // --- Criação e Atualização de Notificações --- 

    private Notification createNotification(DownloadInfo downloadInfo) {
        Intent notificationIntent = new Intent(this, DownloadProgressActivity.class);
        // Passar filePath como identificador único para a activity
        notificationIntent.putExtra("download_path", downloadInfo.getFilePath());
        notificationIntent.setData(Uri.parse(downloadInfo.getFilePath())); // Usar path como data URI
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
                .setSmallIcon(R.drawable.ic_download) // Usar um ícone real
                .setContentIntent(pendingIntent)
                .setOngoing(shouldBeOngoing(downloadInfo))
                .setAutoCancel(shouldAutoCancel(downloadInfo))
                .setOnlyAlertOnce(true);

        addNotificationActions(builder, downloadInfo);

        // Barra de progresso
        if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
            if (downloadInfo.getFileSize() > 0 && downloadInfo.getProgress() >= 0) {
                builder.setProgress(100, downloadInfo.getProgress(), false);
            } else {
                builder.setProgress(0, 0, true); // Indeterminado
            }
        } else {
            builder.setProgress(0, 0, false); // Sem progresso para outros estados
        }
        return builder.build();
    }

    private boolean shouldBeOngoing(DownloadInfo downloadInfo) {
        int status = downloadInfo.getStatus();
        return status == DownloadInfo.STATUS_RUNNING ||
               status == DownloadInfo.STATUS_RESUMING ||
               status == DownloadInfo.STATUS_PAUSED ||
               status == DownloadInfo.STATUS_QUEUED;
    }

    private boolean shouldAutoCancel(DownloadInfo downloadInfo) {
        int status = downloadInfo.getStatus();
        return status == DownloadInfo.STATUS_COMPLETED ||
               status == DownloadInfo.STATUS_FAILED ||
               status == DownloadInfo.STATUS_CANCELLED;
    }

    private void updateNotification(DownloadInfo downloadInfo) {
        if (downloadInfo != null && downloadInfo.getFilePath() != null) {
            notificationManager.notify(getNotificationId(downloadInfo), createNotification(downloadInfo));
        } else {
            Log.w(TAG, "Tentativa de atualizar notificação com DownloadInfo ou FilePath nulo.");
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
                    return String.format("%s (%d%%) - %s", fileName, downloadInfo.getProgress(), downloadInfo.getFormattedSpeed());
                } else {
                    return String.format("%s (%s) - %s", fileName, downloadInfo.getFormattedDownloadedSize(), downloadInfo.getFormattedSpeed());
                }
            case DownloadInfo.STATUS_PAUSED:
                return String.format("%s (%s / %s)", fileName,
                        downloadInfo.getFormattedDownloadedSize(),
                        downloadInfo.getFileSize() > 0 ? downloadInfo.getFormattedFileSize() : "?");
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

        // Ação Pausar/Retomar
        if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
            Intent pauseIntent = new Intent(this, DownloadService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            pauseIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            pauseIntent.setData(Uri.parse("pause://" + downloadInfo.getFilePath())); // Data URI único
            PendingIntent pausePendingIntent = PendingIntent.getService(this, baseRequestCode + 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_pause, pauseActionTitle, pausePendingIntent); // Usar ícone real
        } else if (downloadInfo.getStatus() == DownloadInfo.STATUS_PAUSED) {
            Intent resumeIntent = new Intent(this, DownloadService.class);
            resumeIntent.setAction(ACTION_RESUME);
            resumeIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            resumeIntent.setData(Uri.parse("resume://" + downloadInfo.getFilePath())); // Data URI único
            PendingIntent resumePendingIntent = PendingIntent.getService(this, baseRequestCode + 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_play, resumeActionTitle, resumePendingIntent); // Usar ícone real
        }

        // Ação Cancelar (sempre presente para estados não finais)
        if (downloadInfo.getStatus() != DownloadInfo.STATUS_COMPLETED &&
            downloadInfo.getStatus() != DownloadInfo.STATUS_FAILED &&
            downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
            Intent cancelIntent = new Intent(this, DownloadService.class);
            cancelIntent.setAction(ACTION_CANCEL);
            cancelIntent.putExtra(EXTRA_DOWNLOAD_INFO, downloadInfo);
            cancelIntent.setData(Uri.parse("cancel://" + downloadInfo.getFilePath())); // Data URI único
            PendingIntent cancelPendingIntent = PendingIntent.getService(this, baseRequestCode + 3, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_cancel, cancelActionTitle, cancelPendingIntent); // Usar ícone real
        }
    }

    // --- Classe Interna DownloadTask --- 

    private class DownloadTask implements Runnable {
        private final DownloadInfo downloadInfo; // Usar a instância passada, que é gerenciada pelo DownloadManager
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean active = new AtomicBoolean(false);
        private HttpURLConnection connection = null;
        private InputStream inputStream = null;
        private OutputStream outputStream = null;
        private int urlRetryCount = 0; // Contador de voltas na lista de URLs

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
            Log.d(TAG, "Task pause() solicitado para: " + downloadInfo.getFileName());
            // Não interromper a thread aqui, deixar o loop principal detectar
        }

        public boolean resume() {
            if (paused.compareAndSet(true, false)) {
                synchronized (this) {
                    notifyAll(); // Acordar a thread se estiver em wait()
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
            Log.d(TAG, "Task cancel() solicitado para: " + downloadInfo.getFileName());
            closeResources(); // Tentar fechar recursos imediatamente
            // Interromper a thread pode ajudar a sair de bloqueios de IO
            Thread currentThread = Thread.currentThread();
            if (currentThread.isAlive()) {
                currentThread.interrupt();
            }
        }

        @Override
        public void run() {
            active.set(true);
            Log.i(TAG, "DownloadTask run() iniciado para: " + downloadInfo.getFileName() + " Path: " + downloadInfo.getFilePath());

            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile targetFile = DocumentFile.fromSingleUri(DownloadService.this, fileUri);

            if (targetFile == null || !targetFile.canWrite()) {
                handleError("Não é possível escrever no arquivo de destino (SAF): " + fileUri, false); // Erro fatal
                return;
            }

            boolean downloadSuccessful = false;
            while (!cancelled.get() && !downloadSuccessful && urlRetryCount < MAX_URL_RETRIES) {
                if (paused.get()) {
                    handlePause();
                    if (cancelled.get()) break; // Verificar cancelamento após pausa
                }

                String currentUrl = downloadInfo.getUrl(); // Obter URL ativa atual
                long currentOffset = downloadInfo.getDownloadedSize();
                Log.d(TAG, "Tentando URL: " + currentUrl + " Index: " + downloadInfo.getCurrentUrlIndex() + " Offset: " + currentOffset);

                try {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000); // 15 segundos
                    connection.setReadTimeout(30000); // 30 segundos
                    connection.setInstanceFollowRedirects(true);

                    // Adicionar Cookies e Headers
                    String cookies = downloadInfo.getCookies();
                    if (cookies != null && !cookies.isEmpty()) {
                        connection.setRequestProperty("Cookie", cookies);
                    }
                    Map<String, String> headers = downloadInfo.getCustomHeaders();
                    if (headers != null && !headers.isEmpty()) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    // Configurar Range header para retomar download
                    if (currentOffset > 0) {
                        connection.setRequestProperty("Range", "bytes=" + currentOffset + "-");
                        Log.d(TAG, "Configurando Range header: bytes=" + currentOffset + "-");
                    }

                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response Code para " + currentUrl + ": " + responseCode);

                    boolean isResumeSupported = (responseCode == HttpURLConnection.HTTP_PARTIAL);
                    boolean isNewDownload = (responseCode == HttpURLConnection.HTTP_OK);

                    if (!isNewDownload && !isResumeSupported) {
                        // Erro HTTP não recuperável para esta URL
                        handleHttpError(responseCode, connection.getResponseMessage(), true); // Tentar próxima URL
                        continue; // Próxima iteração do while (tentar próxima URL)
                    }

                    // Obter tamanho total e ajustar se necessário
                    long serverFileSize = connection.getContentLengthLong();
                    long totalSize = -1;
                    if (serverFileSize > 0) {
                        if (isNewDownload) {
                            totalSize = serverFileSize;
                            if (currentOffset > 0) {
                                Log.w(TAG, "Servidor retornou 200 OK, mas esperávamos retomada. Reiniciando download.");
                                downloadInfo.setDownloadedSize(0);
                                currentOffset = 0;
                            }
                        } else { // isResumeSupported (206)
                            // O tamanho total real é o tamanho já baixado + o conteúdo restante retornado
                            totalSize = currentOffset + serverFileSize;
                        }
                        downloadInfo.setFileSize(totalSize);
                    } else if (downloadInfo.getFileSize() <= 0) {
                        Log.w(TAG, "Servidor não retornou Content-Length. Progresso não será exibido.");
                        downloadInfo.setFileSize(-1);
                    }

                    // Abrir OutputStream (modo append se retomando, truncar se novo download)
                    String openMode = (isResumeSupported && currentOffset > 0) ? "wa" : "wt";
                    try {
                        outputStream = getContentResolver().openOutputStream(fileUri, openMode);
                        if (outputStream == null) {
                            throw new FileNotFoundException("Não foi possível abrir OutputStream para " + fileUri + " com modo " + openMode);
                        }
                    } catch (IOException e) {
                        handleError("Erro IO ao abrir arquivo destino (SAF): " + e.getMessage(), false); // Erro fatal
                        return;
                    }

                    inputStream = new BufferedInputStream(connection.getInputStream());
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long lastUpdateTime = System.currentTimeMillis();
                    long bytesSinceLastUpdate = 0;

                    // Atualizar status para RUNNING (mesmo se estava RESUMING)
                    if (downloadInfo.getStatus() != DownloadInfo.STATUS_RUNNING) {
                        downloadInfo.setStatus(DownloadInfo.STATUS_RUNNING);
                        mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                    }

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (paused.get()) {
                            handlePause();
                            if (cancelled.get()) break;
                            // Se retomou, continuar o loop interno
                        }
                        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                            Log.d(TAG, "Download cancelado (detectado no loop de leitura): " + downloadInfo.getFileName());
                            handleCancellation();
                            return; // Sair do run()
                        }

                        try {
                            outputStream.write(buffer, 0, bytesRead);
                            currentOffset += bytesRead;
                            bytesSinceLastUpdate += bytesRead;
                            downloadInfo.setDownloadedSize(currentOffset);

                            // Atualizar progresso e notificação periodicamente
                            long now = System.currentTimeMillis();
                            if (now - lastUpdateTime >= 1000) { // Atualizar a cada segundo
                                if (downloadInfo.getFileSize() > 0) {
                                    int progress = (int) ((currentOffset * 100) / downloadInfo.getFileSize());
                                    downloadInfo.setProgress(Math.min(100, Math.max(0, progress)));
                                }
                                long timeDiff = now - lastUpdateTime;
                                long speed = (bytesSinceLastUpdate * 1000) / timeDiff;
                                downloadInfo.setSpeed(speed);
                                downloadInfo.calculateEstimatedTimeRemaining(); // Calcular ETA

                                // Notificar o Manager e atualizar a notificação
                                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                                updateNotification(downloadInfo);

                                lastUpdateTime = now;
                                bytesSinceLastUpdate = 0;
                            }
                        } catch (IOException e) {
                            handleError("Erro de IO ao escrever no arquivo: " + e.getMessage(), true); // Tentar próxima URL
                            break; // Sair do loop de leitura para tentar próxima URL
                        }
                    }

                    // Se saiu do loop de leitura sem erro de IO, verificar se foi concluído
                    if (bytesRead == -1 && !cancelled.get() && !paused.get()) {
                        // Download concluído com sucesso para esta URL
                        downloadSuccessful = true;
                        Log.i(TAG, "Download concluído com sucesso para: " + downloadInfo.getFileName() + " URL: " + currentUrl);
                        downloadInfo.setStatus(DownloadInfo.STATUS_COMPLETED);
                        downloadInfo.setEndTime(System.currentTimeMillis());
                        downloadInfo.setProgress(100);
                        downloadInfo.setSpeed(0);
                        downloadInfo.setEstimatedTimeRemaining("");
                        mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                        updateNotification(downloadInfo);
                    }

                } catch (IOException e) {
                    // Erro de conexão ou IO antes do loop de leitura
                    handleError("Erro de IO na conexão/leitura inicial: " + e.getMessage(), true); // Tentar próxima URL
                } finally {
                    closeResources();
                }

                // Se o download falhou para esta URL e não foi cancelado/pausado, tentar a próxima
                if (!downloadSuccessful && !cancelled.get() && !paused.get()) {
                    if (!tryNextSourceUrl()) {
                        // Não há mais URLs para tentar ou atingiu limite de retries
                        Log.e(TAG, "Download falhou para todas as URLs: " + downloadInfo.getFileName());
                        // O último erro já foi reportado em handleError ou handleHttpError
                        break; // Sair do while de retries
                    }
                }
            } // Fim do while (!cancelled && !downloadSuccessful && urlRetryCount < MAX_URL_RETRIES)

            // Finalização da tarefa
            finished.set(true);
            active.set(false);
            activeTasksByPath.remove(downloadInfo.getFilePath());
            Log.i(TAG, "Tarefa finalizada para: " + downloadInfo.getFileName() + " Status final: " + downloadInfo.getStatusText());
            stopSelfIfIdle(); // Verificar se o serviço pode parar
        }

        private void handlePause() {
            Log.d(TAG, "Download pausado (detectado): " + downloadInfo.getFileName());
            closeResources(); // Fechar conexão e streams ao pausar
            if (downloadInfo.getStatus() != DownloadInfo.STATUS_PAUSED) {
                downloadInfo.setStatus(DownloadInfo.STATUS_PAUSED);
                downloadInfo.setLastPauseTime(System.currentTimeMillis());
                downloadInfo.setSpeed(0);
                downloadInfo.setEstimatedTimeRemaining("");
                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                updateNotification(downloadInfo);
            }
            // Esperar pela retomada
            synchronized (this) {
                while (paused.get() && !cancelled.get()) {
                    try {
                        wait(5000); // Esperar com timeout para re-verificar cancelamento
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Thread interrompida enquanto pausada: " + downloadInfo.getFileName());
                        Thread.currentThread().interrupt();
                        if (cancelled.get()) break;
                    }
                }
            }
            if (!cancelled.get()) {
                Log.d(TAG, "Download retomado (detectado após wait): " + downloadInfo.getFileName());
                // O status será atualizado para RESUMING/RUNNING no início do próximo loop
                downloadInfo.setLastResumeTime(System.currentTimeMillis());
                // Não precisa notificar aqui, o loop principal fará
            }
        }

        private void handleCancellation() {
            Log.w(TAG, "Tratando cancelamento para: " + downloadInfo.getFileName());
            closeResources();
            deletePartialFileSafely(downloadInfo.getFilePath()); // Deletar arquivo ao cancelar
            if (downloadInfo.getStatus() != DownloadInfo.STATUS_CANCELLED) {
                downloadInfo.setStatus(DownloadInfo.STATUS_CANCELLED);
                mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                // Notificação já removida em cancelDownloadInternal
            }
            finished.set(true);
            active.set(false);
            activeTasksByPath.remove(downloadInfo.getFilePath());
            stopSelfIfIdle();
        }

        private void handleError(String message, boolean tryNextUrl) {
            Log.e(TAG, "Erro no download " + downloadInfo.getFileName() + ": " + message);
            closeResources();
            if (tryNextUrl && !cancelled.get() && !paused.get()) {
                if (!tryNextSourceUrl()) {
                    // Falhou em tentar a próxima URL (sem mais URLs ou limite atingido)
                    reportFailure("Falha após múltiplas tentativas: " + message);
                }
                // Se tryNextSourceUrl() retornou true, o loop while continuará
            } else {
                // Erro fatal ou tarefa foi cancelada/pausada durante o erro
                if (!cancelled.get() && !paused.get()) {
                     reportFailure(message);
                }
            }
        }

        private void handleHttpError(int responseCode, String message, boolean tryNextUrl) {
             String errorMsg = "Erro HTTP " + responseCode + ": " + message;
             Log.e(TAG, "Erro HTTP no download " + downloadInfo.getFileName() + " URL " + downloadInfo.getUrl() + ": " + errorMsg);
             closeResources();
             if (tryNextUrl && !cancelled.get() && !paused.get()) {
                 if (!tryNextSourceUrl()) {
                     reportFailure("Falha após múltiplas tentativas HTTP: " + errorMsg);
                 }
                 // Se tryNextSourceUrl() retornou true, o loop while continuará
             } else {
                 if (!cancelled.get() && !paused.get()) {
                      reportFailure(errorMsg);
                 }
             }
        }

        private void reportFailure(String message) {
             Log.e(TAG, "Reportando falha final para: " + downloadInfo.getFileName());
             downloadInfo.setStatus(DownloadInfo.STATUS_FAILED);
             downloadInfo.setErrorMessage(message);
             downloadInfo.setSpeed(0);
             downloadInfo.setEstimatedTimeRemaining("");
             mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
             updateNotification(downloadInfo);
             // A tarefa será finalizada e removida no fim do run()
        }

        private boolean tryNextSourceUrl() {
            List<String> urls = downloadInfo.getSourceUrls();
            if (urls == null || urls.size() <= 1) {
                return false; // Nenhuma outra URL para tentar
            }
            
            int currentIdx = downloadInfo.getCurrentUrlIndex();
            int startIdx = currentIdx;
            int attempts = 0;
            int maxAttempts = urls.size();

            // Tentar cada URL disponível, mas de forma inteligente
            do {
                attempts++;
                int nextIdx = (currentIdx + attempts) % urls.size();
                
                // Verificar se completou uma volta na lista
                if (nextIdx <= startIdx && attempts > 1) {
                    urlRetryCount++;
                    Log.w(TAG, "Completou volta " + urlRetryCount + " na lista de URLs para: " + downloadInfo.getFileName());
                    if (urlRetryCount >= MAX_URL_RETRIES) {
                        Log.e(TAG, "Atingiu limite máximo de retries (" + MAX_URL_RETRIES + ") para: " + downloadInfo.getFileName());
                        return false; // Atingiu limite de retries
                    }
                }

                String candidateUrl = urls.get(nextIdx);
                Log.i(TAG, "Testando URL candidata [" + nextIdx + "]: " + candidateUrl);

                // Testar se esta URL suporta retomada se há dados parciais
                boolean urlViable = true;
                if (downloadInfo.getDownloadedSize() > 0) {
                    try {
                        DownloadResumeHelper.ResumeTestResult resumeTest = 
                            DownloadResumeHelper.testResumeCapability(
                                candidateUrl,
                                downloadInfo.getDownloadedSize(),
                                downloadInfo.getCookies(),
                                downloadInfo.getCustomHeaders()
                            );
                        
                        if (!resumeTest.resumeSupported) {
                            Log.w(TAG, "URL [" + nextIdx + "] não suporta retomada: " + resumeTest.reason);
                            urlViable = false;
                            
                            // Se é a primeira URL e não suporta retomada, aceitar para reiniciar
                            if (nextIdx == 0) {
                                Log.i(TAG, "Primeira URL não suporta retomada, mas será usada para reiniciar download");
                                urlViable = true;
                                // Resetar arquivo parcial já que vamos reiniciar
                                downloadInfo.setDownloadedSize(0);
                                downloadInfo.setProgress(0);
                                deletePartialFileSafely(downloadInfo.getFilePath());
                            }
                        } else {
                            Log.i(TAG, "URL [" + nextIdx + "] suporta retomada: " + resumeTest.reason);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao testar URL [" + nextIdx + "]: " + e.getMessage());
                        // Em caso de erro no teste, assumir que a URL pode funcionar
                        urlViable = true;
                    }
                } else {
                    // Se não há dados parciais, qualquer URL serve
                    Log.d(TAG, "Sem dados parciais, URL [" + nextIdx + "] será testada");
                }

                if (urlViable) {
                    Log.i(TAG, "Selecionada URL [" + nextIdx + "] para " + downloadInfo.getFileName());
                    downloadInfo.setCurrentUrlIndex(nextIdx);
                    
                    // Atualizar o manager para que a UI possa refletir a URL ativa
                    mainHandler.post(() -> DownloadManager.getInstance(DownloadService.this).updateDownload(downloadInfo));
                    
                    // Pausa curta antes de tentar a próxima URL
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                }
                
            } while (attempts < maxAttempts);

            Log.e(TAG, "Nenhuma URL viável encontrada após testar " + attempts + " URLs");
            return false;
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
            outputStream = null;
            inputStream = null;
            connection = null;
        }
    }
}

