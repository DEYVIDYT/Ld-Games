package com.LDGAMES.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.URLUtil;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.database.DownloadDatabase;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.services.DownloadService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerenciador de downloads da aplicação (Modificado para usar exclusivamente SAF e corrigir erros)
 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";

    private static DownloadManager instance;
    private final Context context;
    private final DownloadDatabase database;
    private final Map<String, DownloadInfo> activeDownloads = new ConcurrentHashMap<>();
    private final Map<String, DownloadInfo> completedDownloads = new ConcurrentHashMap<>();
    // Usar Collections.synchronizedList para segurança de thread na fila
    private final List<DownloadInfo> downloadQueue = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> originalUrls = new HashMap<>();
    private static final int DEFAULT_CONCURRENT_DOWNLOADS = 1;
    private final AtomicInteger runningDownloadsCount = new AtomicInteger(0);
    private boolean serviceRunning = false;
    private final List<DownloadListener> listeners = new ArrayList<>();
    private long lastPersistTime = 0;

    public interface DownloadListener {
        void onDownloadAdded(DownloadInfo downloadInfo);
        void onDownloadUpdated(DownloadInfo downloadInfo);
        void onDownloadCompleted(DownloadInfo downloadInfo);
        void onDownloadFailed(DownloadInfo downloadInfo, String reason);
        // Adicionar callback para cancelamento se necessário
        // void onDownloadCancelled(DownloadInfo downloadInfo);
    }

    private DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = DownloadDatabase.getInstance(context);
        loadData();
    }

    public static synchronized DownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadManager(context);
        }
        return instance;
    }

    private void loadData() {
        activeDownloads.clear();
        completedDownloads.clear();
        synchronized (downloadQueue) {
            downloadQueue.clear();
        }
        originalUrls.clear();

        List<DownloadInfo> activeList = database.getActiveDownloads();
        int runningCount = 0;
        for (DownloadInfo download : activeList) {
            activeDownloads.put(download.getUrl(), download);
            if (download.getStatus() == DownloadInfo.STATUS_QUEUED) {
                synchronized (downloadQueue) {
                    downloadQueue.add(download);
                }
            } else if (download.getStatus() == DownloadInfo.STATUS_RUNNING || download.getStatus() == DownloadInfo.STATUS_RESUMING) {
                // Se estava rodando/resumindo quando o app fechou, marcar como pausado
                download.setStatus(DownloadInfo.STATUS_PAUSED);
                download.setLastPauseTime(System.currentTimeMillis());
                database.addOrUpdateDownload(download); // Atualizar no BD
            } else if (download.getStatus() == DownloadInfo.STATUS_PAUSED) {
                 // Manter como pausado
            }
        }

        List<DownloadInfo> completedList = database.getCompletedDownloads();
        for (DownloadInfo download : completedList) {
            completedDownloads.put(download.getUrl(), download);
        }

        originalUrls.putAll(database.getAllOriginalUrls());

        // Recontar downloads em execução (deve ser 0 após a lógica acima)
        runningDownloadsCount.set(0);

        Log.d(TAG, "Dados carregados: " + activeDownloads.size() + " downloads ativos (não concluídos), " +
              completedDownloads.size() + " downloads concluídos, " +
              downloadQueue.size() + " na fila.");

        validatePausedDownloads();
        // Processar a fila caso haja downloads pausados que podem ser reiniciados
        processQueue();
    }

    public void addDownloadListener(DownloadListener listener) {
        synchronized (listeners) {
            if (listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeDownloadListener(DownloadListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void setOriginalUrl(String downloadUrl, String originalUrl) {
        if (downloadUrl != null && originalUrl != null) {
            synchronized (originalUrls) {
                originalUrls.put(downloadUrl, originalUrl);
            }
            database.setOriginalUrl(downloadUrl, originalUrl);
            Log.d(TAG, "URL original definida: " + originalUrl + " -> " + downloadUrl);
        }
    }

    public String getOriginalUrl(String downloadUrl) {
        synchronized (originalUrls) {
            return originalUrls.get(downloadUrl);
        }
    }

    public boolean addDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getUrl() == null || downloadInfo.getFilePath() == null) {
            Log.e(TAG, "DownloadInfo inválido, não é possível adicionar download");
            return false;
        }

        String url = downloadInfo.getUrl();

        if (activeDownloads.containsKey(url) || completedDownloads.containsKey(url)) {
            Log.w(TAG, "Download já existe para URL: " + url);
            // Notificar que já existe?
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Download já existe."));
            return false;
        }

        activeDownloads.put(url, downloadInfo);
        database.addOrUpdateDownload(downloadInfo);

        if (downloadInfo.getStatus() == DownloadInfo.STATUS_QUEUED) {
            synchronized (downloadQueue) {
                // Evitar adicionar duplicado na fila
                if (!downloadQueue.contains(downloadInfo)) {
                    downloadQueue.add(downloadInfo);
                }
            }
            database.addToQueue(downloadInfo);
            Log.d(TAG, "Download adicionado à fila: " + downloadInfo.getFileName());
        }

        notifyListeners(listener -> listener.onDownloadAdded(downloadInfo));
        processQueue();
        return true;
    }

    public boolean startDownload(String url, String fileName) {
        if (url == null || url.isEmpty() || !URLUtil.isValidUrl(url)) {
            Log.e(TAG, "URL inválida: " + url);
            notifyListeners(listener -> listener.onDownloadFailed(null, "URL inválida."));
            return false;
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = URLUtil.guessFileName(url, null, null);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "downloadfile";
            }
        }

        if (activeDownloads.containsKey(url) || completedDownloads.containsKey(url)) {
            Log.w(TAG, "Download já existe para URL: " + url);
            notifyListeners(listener -> listener.onDownloadFailed(null, "Download já existe."));
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String downloadUriString = prefs.getString("download_uri", null);

        if (downloadUriString == null || downloadUriString.isEmpty()) {
             Log.e(TAG, "Nenhum diretório de download (SAF URI) selecionado.");
             notifyListeners(listener -> listener.onDownloadFailed(null, "Selecione uma pasta para download."));
             return false;
        }

        Uri downloadUri;
        try {
            downloadUri = Uri.parse(downloadUriString);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parsear a URI do diretório: " + downloadUriString, e);
            notifyListeners(listener -> listener.onDownloadFailed(null, "URI da pasta inválida."));
            return false;
        }

        DocumentFile documentTree = DocumentFile.fromTreeUri(context, downloadUri);

        if (documentTree == null || !documentTree.exists() || !documentTree.canWrite()) {
             Log.e(TAG, "Não foi possível acessar/escrever no diretório SAF: " + downloadUriString);
             notifyListeners(listener -> listener.onDownloadFailed(null, "Verifique as permissões da pasta."));
             return false;
        }

        String mimeType = "application/octet-stream";
        String extension = FileUtils.getFileExtension(fileName);
        if (extension != null && !extension.isEmpty()) {
            String guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (guessedType != null) {
                mimeType = guessedType;
            }
        }

        DocumentFile existingFile = documentTree.findFile(fileName);
        if (existingFile != null && existingFile.isFile()) {
            Log.e(TAG, "Arquivo '" + fileName + "' já existe.");
            final String finalFileNameExists = fileName;
            notifyListeners(listener -> listener.onDownloadFailed(null, "Arquivo '" + finalFileNameExists + "' já existe."));
            return false;
        }

        DocumentFile newFile = documentTree.createFile(mimeType, fileName);
        if (newFile == null) {
            Log.e(TAG, "Falha ao criar arquivo '" + fileName + "' via SAF.");
            final String finalFileNameCreate = fileName;
            notifyListeners(listener -> listener.onDownloadFailed(null, "Falha ao criar o arquivo '" + finalFileNameCreate + "'."));
            return false;
        }

        DownloadInfo downloadInfo = new DownloadInfo(fileName, url);
        downloadInfo.setFilePath(newFile.getUri().toString());
        downloadInfo.setStartTime(System.currentTimeMillis());
        downloadInfo.setStatus(DownloadInfo.STATUS_QUEUED);
        downloadInfo.setMimeType(mimeType);

        return addDownload(downloadInfo);
    }

    public boolean startDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getUrl() == null || !URLUtil.isValidUrl(downloadInfo.getUrl()) || downloadInfo.getFileName() == null || downloadInfo.getFileName().isEmpty()) {
            Log.e(TAG, "DownloadInfo inválido fornecido.");
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Dados de download inválidos."));
            return false;
        }

        String url = downloadInfo.getUrl();
        String fileName = downloadInfo.getFileName();

        if (activeDownloads.containsKey(url) || completedDownloads.containsKey(url)) {
            Log.w(TAG, "Download já existe para URL: " + url);
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Download já existe."));
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String downloadUriString = prefs.getString("download_uri", null);

        if (downloadUriString == null || downloadUriString.isEmpty()) {
             Log.e(TAG, "Nenhum diretório de download (SAF URI) selecionado.");
             notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Selecione uma pasta para download."));
             return false;
        }

        Uri downloadUri;
        try {
            downloadUri = Uri.parse(downloadUriString);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parsear a URI do diretório: " + downloadUriString, e);
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "URI da pasta inválida."));
            return false;
        }

        DocumentFile documentTree = DocumentFile.fromTreeUri(context, downloadUri);

        if (documentTree == null || !documentTree.exists() || !documentTree.canWrite()) {
             Log.e(TAG, "Não foi possível acessar/escrever no diretório SAF: " + downloadUriString);
             notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Verifique as permissões da pasta."));
             return false;
        }

        String mimeType = downloadInfo.getMimeType();
        if (mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream")) {
            String extension = FileUtils.getFileExtension(fileName);
            if (extension != null && !extension.isEmpty()) {
                String guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (guessedType != null) {
                    mimeType = guessedType;
                }
            }
            if (mimeType == null || mimeType.isEmpty()) {
                 mimeType = "application/octet-stream";
            }
            downloadInfo.setMimeType(mimeType);
        }

        DocumentFile existingFile = documentTree.findFile(fileName);
        if (existingFile != null && existingFile.isFile()) {
            Log.e(TAG, "Arquivo '" + fileName + "' já existe.");
            final String finalFileNameExists = fileName;
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Arquivo '" + finalFileNameExists + "' já existe."));
            return false;
        }

        DocumentFile newFile = documentTree.createFile(mimeType, fileName);
        if (newFile == null) {
            Log.e(TAG, "Falha ao criar arquivo '" + fileName + "' via SAF.");
            final String finalFileNameCreate = fileName;
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Falha ao criar o arquivo '" + finalFileNameCreate + "'."));
            return false;
        }

        downloadInfo.setFilePath(newFile.getUri().toString());
        downloadInfo.setStartTime(System.currentTimeMillis());
        downloadInfo.setStatus(DownloadInfo.STATUS_QUEUED);
        downloadInfo.setDownloadedSize(0);
        downloadInfo.setFileSize(0);
        downloadInfo.setProgress(0);

        return addDownload(downloadInfo);
    }

    public void pauseDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null) return;

        String url = downloadInfo.getUrl();
        DownloadInfo activeDownload = activeDownloads.get(url);

        if (activeDownload != null &&
            (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING ||
             activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING ||
             activeDownload.getStatus() == DownloadInfo.STATUS_QUEUED)) {

            boolean wasRunning = (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING || activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING);

            if (activeDownload.getStatus() == DownloadInfo.STATUS_QUEUED) {
                boolean removed;
                synchronized (downloadQueue) {
                    removed = downloadQueue.remove(activeDownload);
                }
                activeDownload.setStatus(DownloadInfo.STATUS_PAUSED);
                activeDownload.setLastPauseTime(System.currentTimeMillis());
                database.addOrUpdateDownload(activeDownload);
                if(removed) database.removeFromQueue(activeDownload);
                notifyListeners(listener -> listener.onDownloadUpdated(activeDownload));
                Log.d(TAG, "Download removido da fila e pausado: " + activeDownload.getFileName());
                // Como não estava rodando, não precisa decrementar contador nem chamar processQueue
            } else {
                // Se estava rodando ou resumindo, enviar comando para o serviço
                DownloadService.pauseDownload(context, activeDownload);
                Log.d(TAG, "Enviando comando de pausa para o serviço: " + activeDownload.getFileName());
                // O serviço atualizará o status para PAUSED via updateDownload,
                // que então decrementará o contador e chamará processQueue.
            }
        } else {
            Log.w(TAG, "Download não está rodando, resumindo ou na fila para pausar: " + url);
        }
    }

    public void resumeDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null) return;

        String url = downloadInfo.getUrl();
        DownloadInfo activeDownload = activeDownloads.get(url);

        if (activeDownload != null && activeDownload.getStatus() == DownloadInfo.STATUS_PAUSED) {
            Log.d(TAG, "Tentando retomar download: " + activeDownload.getFileName());

            if (!validatePartialFile(activeDownload)) {
                Log.w(TAG, "Validação do arquivo parcial falhou, reiniciando download: " + activeDownload.getFileName());
                activeDownload.setDownloadedSize(0);
                activeDownload.setProgress(0);
            }
            // Sempre colocar na fila para retomar ou reiniciar
            activeDownload.setStatus(DownloadInfo.STATUS_QUEUED);

            synchronized (downloadQueue) {
                 // Evitar adicionar duplicado na fila
                if (!downloadQueue.contains(activeDownload)) {
                    downloadQueue.add(activeDownload);
                }
            }

            database.addOrUpdateDownload(activeDownload);
            database.addToQueue(activeDownload);

            Log.d(TAG, "Download adicionado à fila para retomada/reinício: " + activeDownload.getFileName());
            notifyListeners(listener -> listener.onDownloadUpdated(activeDownload));
            processQueue();

        } else {
            Log.w(TAG, "Download não está pausado ou não existe: " + url);
        }
    }

    public void pauseResumeDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null) return;
        DownloadInfo activeDownload = activeDownloads.get(downloadInfo.getUrl());
        if (activeDownload == null) {
             Log.w(TAG, "Tentativa de pausar/retomar download não encontrado: " + downloadInfo.getUrl());
             return;
        }
        int status = activeDownload.getStatus();
        if (status == DownloadInfo.STATUS_RUNNING || status == DownloadInfo.STATUS_RESUMING || status == DownloadInfo.STATUS_QUEUED) {
            pauseDownload(activeDownload);
        } else if (status == DownloadInfo.STATUS_PAUSED) {
            resumeDownload(activeDownload);
        } else {
             Log.w(TAG, "Não é possível pausar/retomar download no estado: " + status);
        }
    }

    public void cancelDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null) return;

        String url = downloadInfo.getUrl();
        DownloadInfo activeDownload = activeDownloads.remove(url);

        if (activeDownload != null) {
            Log.d(TAG, "Cancelando download: " + activeDownload.getFileName());
            boolean removed;
            synchronized (downloadQueue) {
                removed = downloadQueue.remove(activeDownload);
            }
            if(removed) database.removeFromQueue(activeDownload);

            boolean wasRunning = (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING || activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING);

            if (wasRunning) {
                if (runningDownloadsCount.get() > 0) runningDownloadsCount.decrementAndGet();
                DownloadService.cancelDownload(context, activeDownload);
                Log.d(TAG, "Enviando comando de cancelamento para o serviço: " + activeDownload.getFileName());
            }

            activeDownload.setStatus(DownloadInfo.STATUS_CANCELLED);
            database.addOrUpdateDownload(activeDownload);
            deletePartialFileSafely(activeDownload.getFilePath());
            notifyListeners(listener -> listener.onDownloadUpdated(activeDownload));
            Log.d(TAG, "Download cancelado e removido/marcado: " + activeDownload.getFileName());

            // Iniciar próximo download da fila, se houver e se um slot foi liberado
            if (wasRunning) {
                processQueue();
            }
        } else {
            Log.w(TAG, "Download não encontrado para cancelamento: " + url);
            // Tentar remover do BD mesmo assim?
            // database.deleteDownload(url);
        }
    }

    private void deletePartialFileSafely(String fileUriPath) {
        if (fileUriPath == null || !fileUriPath.startsWith("content://")) {
            // Log.w(TAG, "Tentativa de deletar arquivo com URI inválida: " + fileUriPath);
            return;
        }
        try {
            Uri fileUri = Uri.parse(fileUriPath);
            DocumentFile fileToDelete = DocumentFile.fromSingleUri(context, fileUri);
            if (fileToDelete != null && fileToDelete.exists() && fileToDelete.isFile()) {
                if (fileToDelete.delete()) {
                    Log.d(TAG, "Arquivo parcial deletado (SAF): " + fileUriPath);
                } else {
                    Log.w(TAG, "Falha ao deletar arquivo parcial (SAF): " + fileUriPath);
                }
            } else {
                 // Log.w(TAG, "Arquivo parcial não encontrado para deleção (SAF): " + fileUriPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tentar deletar arquivo parcial (SAF): " + fileUriPath, e);
        }
    }

    /**
     * Atualiza as informações de um download ativo.
     * Chamado pelo DownloadService para reportar progresso, conclusão, pausa ou falha.
     * @param downloadInfo Informações atualizadas do download.
     */
    public void updateDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getUrl() == null) return;

        String url = downloadInfo.getUrl();
        DownloadInfo existingDownload = activeDownloads.get(url);

        if (existingDownload != null) {
            int oldStatus = existingDownload.getStatus();
            boolean wasRunning = (oldStatus == DownloadInfo.STATUS_RUNNING || oldStatus == DownloadInfo.STATUS_RESUMING);

            // Atualizar informações no cache
            existingDownload.setStatus(downloadInfo.getStatus());
            existingDownload.setProgress(downloadInfo.getProgress());
            existingDownload.setDownloadedSize(downloadInfo.getDownloadedSize());
            // Atualizar fileSize apenas se for maior que zero (evitar sobrescrever com -1)
            if (downloadInfo.getFileSize() > 0) {
                existingDownload.setFileSize(downloadInfo.getFileSize());
            }
            // Manter startTime original
            // existingDownload.setStartTime(downloadInfo.getStartTime());
            existingDownload.setEndTime(downloadInfo.getEndTime());
            existingDownload.setLastPauseTime(downloadInfo.getLastPauseTime());
            existingDownload.setLastResumeTime(downloadInfo.getLastResumeTime());
            existingDownload.setErrorMessage(downloadInfo.getErrorMessage());
            existingDownload.setSpeed(downloadInfo.getSpeed());
            existingDownload.setEstimatedTimeRemaining(downloadInfo.getEstimatedTimeRemaining());

            database.addOrUpdateDownload(existingDownload);
            notifyListeners(listener -> listener.onDownloadUpdated(existingDownload));

            int newStatus = existingDownload.getStatus();
            boolean isNowRunning = (newStatus == DownloadInfo.STATUS_RUNNING || newStatus == DownloadInfo.STATUS_RESUMING);

            // Lógica de transição de estado e processamento da fila
            if (wasRunning && !isNowRunning) {
                // Transição de RUNNING/RESUMING para PAUSED, COMPLETED, FAILED, CANCELLED
                if (runningDownloadsCount.get() > 0) runningDownloadsCount.decrementAndGet();
                Log.d(TAG, "Download parou (Status: " + newStatus + "): " + existingDownload.getFileName() + ". Em execução: " + runningDownloadsCount.get());

                if (newStatus == DownloadInfo.STATUS_COMPLETED) {
                    handleDownloadCompletion(existingDownload);
                } else if (newStatus == DownloadInfo.STATUS_FAILED) {
                    handleDownloadFailure(existingDownload);
                } else if (newStatus == DownloadInfo.STATUS_CANCELLED) {
                    handleDownloadCancellation(existingDownload);
                } else if (newStatus == DownloadInfo.STATUS_PAUSED) {
                    // Já decrementou o contador, agora processar fila
                    processQueue();
                }
            } else if (!wasRunning && isNowRunning) {
                // Transição de outro estado para RUNNING/RESUMING (normalmente via processQueue)
                // O contador já foi incrementado em processQueue
                Log.d(TAG, "Download iniciou/retomou: " + existingDownload.getFileName() + ". Em execução: " + runningDownloadsCount.get());
            }

            persistStateIfNeeded();

        } else {
            DownloadInfo completed = completedDownloads.get(url);
            if (completed == null) {
                 Log.w(TAG, "Tentativa de atualizar download não encontrado: " + url + " Status recebido: " + downloadInfo.getStatus());
            }
        }
    }

    private void handleDownloadCompletion(DownloadInfo downloadInfo) {
        activeDownloads.remove(downloadInfo.getUrl());
        completedDownloads.put(downloadInfo.getUrl(), downloadInfo);
        // Contador já decrementado em updateDownload
        Log.d(TAG, "Download concluído tratado: " + downloadInfo.getFileName());
        notifyListeners(listener -> listener.onDownloadCompleted(downloadInfo));
        processQueue(); // Processar fila após conclusão
    }

    private void handleDownloadFailure(DownloadInfo downloadInfo) {
        // Manter na lista ativa, mas marcado como falha
        // Contador já decrementado em updateDownload
        Log.e(TAG, "Download falhou tratado: " + downloadInfo.getFileName() + ". Motivo: " + downloadInfo.getErrorMessage());
        notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, downloadInfo.getErrorMessage()));
        processQueue(); // Processar fila após falha
    }

    private void handleDownloadCancellation(DownloadInfo downloadInfo) {
        activeDownloads.remove(downloadInfo.getUrl());
        synchronized (downloadQueue) {
            downloadQueue.remove(downloadInfo);
        }
        // Contador já decrementado em updateDownload
        Log.w(TAG, "Download cancelado tratado: " + downloadInfo.getFileName());
        deletePartialFileSafely(downloadInfo.getFilePath());
        // Notificação já feita em updateDownload
        processQueue(); // Processar fila após cancelamento
    }

    public synchronized void processQueue() {
        Log.d(TAG, "Processando fila. Em execução: " + runningDownloadsCount.get() + ", Limite: " + getConcurrentDownloadLimit() + ", Na fila: " + downloadQueue.size());
        while (runningDownloadsCount.get() < getConcurrentDownloadLimit()) {
            DownloadInfo nextDownload = null;
            synchronized (downloadQueue) {
                if (!downloadQueue.isEmpty()) {
                    nextDownload = downloadQueue.remove(0);
                }
            }

            if (nextDownload == null) {
                break; // Fila vazia
            }

            database.removeFromQueue(nextDownload);
            Log.d(TAG, "Iniciando próximo download da fila: " + nextDownload.getFileName());

            if (nextDownload.getStatus() == DownloadInfo.STATUS_QUEUED || nextDownload.getStatus() == DownloadInfo.STATUS_PAUSED) {
                 boolean isResume = nextDownload.getDownloadedSize() > 0 && validatePartialFile(nextDownload);

                 if (isResume) {
                     Log.d(TAG, "Enviando comando RESUME para: " + nextDownload.getFileName());
                     nextDownload.setStatus(DownloadInfo.STATUS_RESUMING);
                     runningDownloadsCount.incrementAndGet(); // Incrementar ANTES de iniciar
                     database.addOrUpdateDownload(nextDownload);
                     DownloadService.resumeDownload(context, nextDownload);
                 } else {
                     Log.d(TAG, "Enviando comando START para: " + nextDownload.getFileName());
                     nextDownload.setStatus(DownloadInfo.STATUS_RUNNING);
                     nextDownload.setDownloadedSize(0);
                     nextDownload.setProgress(0);
                     runningDownloadsCount.incrementAndGet(); // Incrementar ANTES de iniciar
                     database.addOrUpdateDownload(nextDownload);
                     DownloadService.startDownload(context, nextDownload);
                 }
                 final DownloadInfo finalNextDownload = nextDownload; // Criar cópia final para lambda
                 notifyListeners(listener -> listener.onDownloadUpdated(finalNextDownload));
            } else {
                 Log.w(TAG, "Download na fila não estava QUEUED/PAUSED: " + nextDownload.getFileName() + " Status: " + nextDownload.getStatus());
                 // Não iniciar e tentar o próximo?
            }
        }
        // Após processar a fila, verificar se o serviço pode parar
        checkStopServiceCondition();
    }

    public List<DownloadInfo> getActiveDownloads() {
        return new ArrayList<>(activeDownloads.values());
    }

    public List<DownloadInfo> getCompletedDownloads() {
        return new ArrayList<>(completedDownloads.values());
    }

    public DownloadInfo getDownloadInfo(String url) {
        DownloadInfo info = activeDownloads.get(url);
        if (info == null) {
            info = completedDownloads.get(url);
        }
        return info;
    }

    public void setServiceRunning(boolean running) {
        this.serviceRunning = running;
        if (!running) {
            Log.w(TAG, "Serviço de download parado. Verificando downloads ativos...");
            boolean changed = false;
            synchronized (activeDownloads) { // Sincronizar acesso
                for (DownloadInfo download : activeDownloads.values()) {
                    if (download.getStatus() == DownloadInfo.STATUS_RUNNING || download.getStatus() == DownloadInfo.STATUS_RESUMING) {
                        download.setStatus(DownloadInfo.STATUS_PAUSED);
                        download.setLastPauseTime(System.currentTimeMillis());
                        database.addOrUpdateDownload(download);
                        notifyListeners(listener -> listener.onDownloadUpdated(download));
                        changed = true;
                    }
                }
            }
            runningDownloadsCount.set(0);
            if (changed) {
                 Log.d(TAG, "Downloads em execução foram marcados como pausados devido à parada do serviço.");
            }
        }
    }

    public boolean isServiceRunning() {
        return serviceRunning;
    }

    private void persistStateIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPersistTime > 30000) {
            Log.v(TAG, "Persistindo estado dos downloads ativos...");
            synchronized (activeDownloads) {
                for (DownloadInfo download : activeDownloads.values()) {
                    database.addOrUpdateDownload(download);
                }
            }
            lastPersistTime = currentTime;
        }
    }

    private void validatePausedDownloads() {
        Log.d(TAG, "Validando downloads pausados...");
        List<DownloadInfo> toUpdate = new ArrayList<>();
        synchronized (activeDownloads) {
            for (DownloadInfo download : activeDownloads.values()) {
                if (download.getStatus() == DownloadInfo.STATUS_PAUSED) {
                    if (!validatePartialFile(download)) {
                        Log.w(TAG, "Download pausado inválido: " + download.getFileName() + ". Marcando como falha.");
                        download.setStatus(DownloadInfo.STATUS_FAILED);
                        download.setErrorMessage("Arquivo parcial inválido.");
                        toUpdate.add(download);
                    }
                }
            }
        }
        for (DownloadInfo download : toUpdate) {
            database.addOrUpdateDownload(download);
            notifyListeners(listener -> listener.onDownloadFailed(download, download.getErrorMessage()));
        }
        if (!toUpdate.isEmpty()) {
             Log.d(TAG, toUpdate.size() + " downloads pausados marcados como falha.");
        }
    }

    private boolean validatePartialFile(DownloadInfo downloadInfo) {
        if (downloadInfo.getFilePath() == null || !downloadInfo.getFilePath().startsWith("content://")) {
            return false;
        }
        try {
            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile partialFile = DocumentFile.fromSingleUri(context, fileUri);
            if (partialFile == null || !partialFile.exists() || !partialFile.isFile()) {
                return false;
            }
            long currentFileSize = partialFile.length();
            // Permitir retomar mesmo se o tamanho não bater exatamente?
            // A task de download vai lidar com isso (reiniciar se necessário)
            // Apenas verificar se existe.
            // if (currentFileSize != downloadInfo.getDownloadedSize()) {
            //     return false;
            // }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro validação SAF: " + downloadInfo.getFilePath(), e);
            return false;
        }
    }

    public void clearAllDownloads() {
        try {
             Log.w(TAG, "Iniciando limpeza completa...");
             List<DownloadInfo> currentActive = new ArrayList<>(activeDownloads.values());
             for (DownloadInfo download : currentActive) {
                 if (download.getStatus() == DownloadInfo.STATUS_RUNNING ||
                     download.getStatus() == DownloadInfo.STATUS_RESUMING ||
                     download.getStatus() == DownloadInfo.STATUS_QUEUED) {
                     DownloadService.cancelDownload(context, download);
                 }
                 deletePartialFileSafely(download.getFilePath());
             }
             activeDownloads.clear();
             synchronized (downloadQueue) {
                 downloadQueue.clear();
             }
             completedDownloads.clear();
             synchronized (originalUrls) {
                 originalUrls.clear();
             }
             runningDownloadsCount.set(0);
             // database.clearAllData(); // Método não existe no DB, remover ou implementar
             Log.i(TAG, "Limpeza completa concluída.");
         } catch (Exception e) {
              Log.e(TAG, "Erro durante clearAllDownloads", e);
         }
    }

    private void notifyListeners(java.util.function.Consumer<DownloadListener> action) {
        synchronized (listeners) {
            for (DownloadListener listener : new ArrayList<>(listeners)) {
                try {
                    action.accept(listener);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao notificar listener", e);
                }
            }
        }
    }

    private int getConcurrentDownloadLimit() {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        return Math.max(1, prefs.getInt("max_concurrent_downloads", DEFAULT_CONCURRENT_DOWNLOADS));
    }

    /**
     * Verifica se há downloads ativos (RUNNING ou RESUMING).
     * @return true se houver downloads ativos, false caso contrário.
     */
    public boolean hasActiveDownloads() {
        return runningDownloadsCount.get() > 0;
    }

    /**
     * Verifica se o serviço de download pode parar.
     * Chamado pelo DownloadService e pelo DownloadManager após processar a fila.
     */
    public void checkStopServiceCondition() {
        // O serviço só deve parar se NÃO houver downloads em execução (RUNNING/RESUMING)
        if (!hasActiveDownloads()) {
            Log.i(TAG, "Nenhum download ativo (RUNNING/RESUMING). Solicitando parada do serviço.");
            // Enviar um comando para o próprio serviço parar?
            // Ou o serviço chama stopSelf() internamente quando detecta isso?
            // A lógica atual no DownloadService.checkStopService usa hasActiveOrQueuedDownloads.
            // Vamos manter a lógica lá por enquanto, mas garantir que hasActiveDownloads esteja correto.
        } else {
            Log.d(TAG, "Ainda há downloads ativos. Serviço continua.");
        }
    }

     /**
     * Verifica se há downloads ativos (RUNNING, RESUMING) ou na fila (QUEUED).
     * Usado pelo DownloadService para decidir se pode parar.
     * @return true se houver downloads ativos ou na fila, false caso contrário.
     */
    public boolean hasActiveOrQueuedDownloads() {
        if (hasActiveDownloads()) {
            return true;
        }
        synchronized(downloadQueue) {
            if (!downloadQueue.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}

