package com.LDGAMES.utils;

import android.content.Context;
import android.content.Intent; // Correção Erro 5: Importar Intent
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.database.DownloadDatabase;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.services.DownloadService;
import com.LDGAMES.utils.DownloadResumeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerenciador de downloads da aplicação (Modificado para SAF, múltiplas fontes, atualização de link)
 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";

    private static DownloadManager instance;
    private final Context context;
    private final DownloadDatabase database;
    // Usar filePath como chave para identificar unicamente o download no destino
    private final Map<String, DownloadInfo> activeDownloadsByPath = new ConcurrentHashMap<>();
    private final Map<String, DownloadInfo> completedDownloadsByPath = new ConcurrentHashMap<>();
    private final List<DownloadInfo> downloadQueue = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> originalUrls = new HashMap<>(); // Mapeia URL ativa para URL original (se aplicável)
    private static final int DEFAULT_CONCURRENT_DOWNLOADS = 3; // Valor padrão aumentado
    public static final String PREF_CONCURRENT_DOWNLOADS = "concurrent_downloads_limit"; // Chave para SharedPreferences
    private final AtomicInteger runningDownloadsCount = new AtomicInteger(0);
    private boolean serviceRunning = false;
    private final List<DownloadListener> listeners = Collections.synchronizedList(new ArrayList<>());
    private long lastPersistTime = 0;
    private static final long PERSIST_INTERVAL = 2000; // Persistir a cada 2 segundos (mais frequente)
    private static final long BACKUP_INTERVAL = 30000; // Backup a cada 30 segundos
    private long lastBackupTime = 0;
    private static final long CRASH_RECOVERY_TIMEOUT = 10000; // 10 segundos para detectar crash

    public interface DownloadListener {
        void onDownloadAdded(DownloadInfo downloadInfo);
        void onDownloadUpdated(DownloadInfo downloadInfo);
        void onDownloadCompleted(DownloadInfo downloadInfo);
        void onDownloadFailed(DownloadInfo downloadInfo, String reason);
        // void onDownloadCancelled(DownloadInfo downloadInfo); // Opcional
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

    public int getConcurrentDownloadLimit() {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        // Retorna o valor salvo ou o padrão se não encontrado, garantindo que seja pelo menos 1.
        return Math.max(1, prefs.getInt(PREF_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS));
    }

    private void loadData() {
        activeDownloadsByPath.clear();
        completedDownloadsByPath.clear();
        synchronized (downloadQueue) {
            downloadQueue.clear();
        }
        originalUrls.clear();

        List<DownloadInfo> activeList = database.getActiveDownloads();
        for (DownloadInfo download : activeList) {
            if (download.getFilePath() == null) continue; // Ignorar downloads inválidos
            activeDownloadsByPath.put(download.getFilePath(), download);
            if (download.getStatus() == DownloadInfo.STATUS_QUEUED) {
                synchronized (downloadQueue) {
                    // Evitar duplicatas na fila ao carregar
                    if (!downloadQueue.contains(download)) {
                        downloadQueue.add(download);
                    }
                }
            } else if (download.getStatus() == DownloadInfo.STATUS_RUNNING || download.getStatus() == DownloadInfo.STATUS_RESUMING) {
                // Se o app foi fechado enquanto rodava, marcar como pausado
                download.setStatus(DownloadInfo.STATUS_PAUSED);
                download.setLastPauseTime(System.currentTimeMillis());
                database.addOrUpdateDownload(download); // Atualizar no BD
            }
        }

        List<DownloadInfo> completedList = database.getCompletedDownloads();
        for (DownloadInfo download : completedList) {
             if (download.getFilePath() == null) continue;
            completedDownloadsByPath.put(download.getFilePath(), download);
        }

        // originalUrls precisa ser carregado do DB se for persistido
        // originalUrls.putAll(database.getAllOriginalUrls());

        runningDownloadsCount.set(0);

        Log.d(TAG, "Dados carregados: " + activeDownloadsByPath.size() + " downloads ativos, " +
              completedDownloadsByPath.size() + " concluídos, " + downloadQueue.size() + " na fila.");

        // Realizar recuperação de crash após carregar dados
        performCrashRecovery();
        
        // Verificar integridade dos downloads
        performIntegrityCheck();
        
        validatePausedDownloads();
        processQueue();
    }

    public void addDownloadListener(DownloadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeDownloadListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    // Método setOriginalUrl pode precisar ser revisto dependendo do uso
    public void setOriginalUrl(String activeDownloadUrl, String originalUrl) {
        if (activeDownloadUrl != null && originalUrl != null) {
            synchronized (originalUrls) {
                originalUrls.put(activeDownloadUrl, originalUrl);
            }
            // database.setOriginalUrl(activeDownloadUrl, originalUrl); // Persistir se necessário
            Log.d(TAG, "URL original definida: " + originalUrl + " -> " + activeDownloadUrl);
        }
    }

    public String getOriginalUrl(String activeDownloadUrl) {
        synchronized (originalUrls) {
            return originalUrls.get(activeDownloadUrl);
        }
    }

    /**
     * Inicia um novo download ou atualiza um existente com o mesmo nome de arquivo.
     *
     * @param sourceUrls Lista de URLs de origem.
     * @param fileName Nome do arquivo desejado.
     * @param cookies Cookies opcionais.
     * @param customHeaders Headers opcionais.
     * @return true se o download foi adicionado ou atualizado, false caso contrário.
     */
    public boolean startOrUpdateDownload(List<String> sourceUrls, String fileName, String cookies, Map<String, String> customHeaders) {
        if (sourceUrls == null || sourceUrls.isEmpty() || sourceUrls.stream().anyMatch(url -> url == null || url.isEmpty() || !URLUtil.isValidUrl(url))) {
            Log.e(TAG, "Lista de URLs inválida.");
            notifyListeners(listener -> listener.onDownloadFailed(null, "URL(s) inválida(s)."));
            return false;
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = URLUtil.guessFileName(sourceUrls.get(0), null, null);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "downloadfile";
            }
        }

        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String downloadUriString = prefs.getString("download_uri", null);

        if (downloadUriString == null || downloadUriString.isEmpty()) {
            Log.e(TAG, "Nenhum diretório de download (SAF URI) selecionado.");
            notifyListeners(listener -> listener.onDownloadFailed(null, "Selecione uma pasta para download."));
            return false;
        }

        Uri downloadDirUri;
        try {
            downloadDirUri = Uri.parse(downloadUriString);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parsear a URI do diretório: " + downloadUriString, e);
            notifyListeners(listener -> listener.onDownloadFailed(null, "URI da pasta inválida."));
            return false;
        }

        DocumentFile documentTree = DocumentFile.fromTreeUri(context, downloadDirUri);
        if (documentTree == null || !documentTree.exists() || !documentTree.canWrite()) {
            Log.e(TAG, "Não foi possível acessar/escrever no diretório SAF: " + downloadUriString);
            notifyListeners(listener -> listener.onDownloadFailed(null, "Verifique as permissões da pasta."));
            return false;
        }

        // --- Lógica de Atualização por Nome --- 
        final String finalFileName = fileName;
        Optional<DownloadInfo> existingActiveDownloadOpt = activeDownloadsByPath.values().stream()
                .filter(d -> finalFileName.equals(d.getFileName()))
                .findFirst();

        if (existingActiveDownloadOpt.isPresent()) {
            DownloadInfo existingDownload = existingActiveDownloadOpt.get();
            Log.i(TAG, "Download ativo encontrado com o mesmo nome: " + fileName + ". Atualizando URLs e reiniciando.");

            // Pausar a tarefa atual se estiver rodando (o serviço precisa lidar com isso)
            if (existingDownload.getStatus() == DownloadInfo.STATUS_RUNNING || existingDownload.getStatus() == DownloadInfo.STATUS_RESUMING) {
                 DownloadService.pauseDownload(context, existingDownload); // Solicitar pausa
                 // A atualização ocorrerá após a confirmação da pausa pelo serviço ou imediatamente se já pausado/na fila
            }

            // Atualizar informações
            existingDownload.setSourceUrls(sourceUrls);
            existingDownload.setCurrentUrlIndex(0); // Reinicia índice da URL
            existingDownload.setCookies(cookies);
            existingDownload.setCustomHeaders(customHeaders);
            existingDownload.setDownloadedSize(0);
            existingDownload.setProgress(0);
            existingDownload.setFileSize(0); // Resetar tamanho, será redescoberto
            existingDownload.setErrorMessage(null);
            existingDownload.setStatus(DownloadInfo.STATUS_QUEUED); // Colocar na fila para reiniciar
            existingDownload.resetResumeAttempts();

            database.addOrUpdateDownload(existingDownload);

            synchronized (downloadQueue) {
                // Remover da fila se já estiver lá (para garantir que vá para o fim ou posição correta se houver prioridade)
                downloadQueue.remove(existingDownload);
                // Adicionar de volta à fila
                downloadQueue.add(existingDownload);
            }
            database.addToQueue(existingDownload); // Informar DB sobre a fila

            Log.d(TAG, "Download atualizado e re-enfileirado: " + existingDownload.getFileName());
            notifyListeners(listener -> listener.onDownloadUpdated(existingDownload));
            processQueue();
            return true;
        }
        // --- Fim da Lógica de Atualização por Nome ---

        // Se não encontrou download ativo com mesmo nome, proceder para criar novo

        // Verificar se já existe um download CONCLUÍDO com o mesmo nome/path
        // A criação do arquivo via SAF pode falhar ou sobrescrever.
        // Vamos permitir a tentativa de criação e tratar a falha.

        String mimeType = "application/octet-stream";
        String extension = FileUtils.getFileExtension(fileName);
        if (extension != null && !extension.isEmpty()) {
            String guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (guessedType != null) {
                mimeType = guessedType;
            }
        }

        // --- Lógica de Verificação de Arquivo Existente ---
        // Verificar se já existe um arquivo com o mesmo nome na pasta
        DocumentFile existingFile = documentTree.findFile(fileName);
        DocumentFile targetFile = null;
        long initialDownloadedSize = 0;
        
        if (existingFile != null && existingFile.exists() && existingFile.isFile()) {
            Log.i(TAG, "Arquivo '" + fileName + "' já existe. Verificando se é um download incompleto ou completo.");
            
            // Verificar se é um download concluído existente nos registros
            Optional<DownloadInfo> existingCompletedDownloadOpt = completedDownloadsByPath.values().stream()
                    .filter(d -> finalFileName.equals(d.getFileName()))
                    .findFirst();
            
            if (existingCompletedDownloadOpt.isPresent()) {
                Log.i(TAG, "Arquivo é um download já concluído. Sobrescrevendo automaticamente.");
                if (existingFile.delete()) {
                    Log.d(TAG, "Arquivo existente deletado para sobrescrever.");
                    targetFile = documentTree.createFile(mimeType, fileName);
                } else {
                    Log.e(TAG, "Falha ao deletar arquivo existente para sobrescrever.");
                    notifyListeners(listener -> listener.onDownloadFailed(null, "Não foi possível sobrescrever arquivo existente."));
                    return false;
                }
            } else {
                // Arquivo existe mas não está nos registros - pode ser um download parcial perdido
                Log.i(TAG, "Arquivo existe mas não está nos registros. Verificando se pode ser retomado.");
                
                // Verificar tamanho do arquivo para decidir se vale a pena tentar retomar
                long existingFileSize = existingFile.length();
                if (existingFileSize > 0) {
                    Log.i(TAG, "Arquivo parcial encontrado (" + existingFileSize + " bytes). Tentando retomar download.");
                    targetFile = existingFile; // Usar arquivo existente para retomar
                    initialDownloadedSize = existingFileSize;
                } else {
                    // Arquivo vazio ou corrompido, deletar e criar novo
                    Log.w(TAG, "Arquivo existente está vazio ou corrompido. Deletando e criando novo.");
                    if (existingFile.delete()) {
                        targetFile = documentTree.createFile(mimeType, fileName);
                    } else {
                        Log.e(TAG, "Falha ao deletar arquivo vazio/corrompido.");
                        notifyListeners(listener -> listener.onDownloadFailed(null, "Não foi possível limpar arquivo existente."));
                        return false;
                    }
                }
            }
        } else {
            // Nenhum arquivo existe, criar novo
            Log.d(TAG, "Nenhum arquivo existente encontrado. Criando novo arquivo: " + fileName);
            targetFile = documentTree.createFile(mimeType, fileName);
        }
        
        // Verificar se conseguimos obter um arquivo válido
        if (targetFile == null) {
            Log.e(TAG, "Falha ao criar/obter arquivo de destino: " + fileName);
            notifyListeners(listener -> listener.onDownloadFailed(null, "Falha ao criar arquivo '" + finalFileName + "'."));
            return false;
        }

        // Criar novo DownloadInfo
        DownloadInfo downloadInfo = new DownloadInfo(fileName, sourceUrls);
        downloadInfo.setFilePath(targetFile.getUri().toString());
        downloadInfo.setStartTime(System.currentTimeMillis());
        downloadInfo.setStatus(DownloadInfo.STATUS_QUEUED);
        downloadInfo.setMimeType(mimeType);
        downloadInfo.setCookies(cookies);
        downloadInfo.setCustomHeaders(customHeaders);
        
        // Se estamos retomando um arquivo existente, definir o tamanho já baixado
        if (initialDownloadedSize > 0) {
            downloadInfo.setDownloadedSize(initialDownloadedSize);
            Log.i(TAG, "Download configurado para retomar a partir de " + initialDownloadedSize + " bytes");
        }
        return addDownloadInternal(downloadInfo);
    }

    // Método interno para adicionar um download já validado e com arquivo criado
    private boolean addDownloadInternal(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) {
            Log.e(TAG, "addDownloadInternal: DownloadInfo ou FilePath inválido.");
            return false;
        }

        // Usar filePath como chave única
        if (activeDownloadsByPath.containsKey(downloadInfo.getFilePath()) || completedDownloadsByPath.containsKey(downloadInfo.getFilePath())) {
            Log.w(TAG, "Download com o mesmo FilePath já existe: " + downloadInfo.getFilePath());
            // Isso não deveria acontecer se a lógica de startOrUpdateDownload estiver correta
            // Mas como segurança, notificar e retornar false.
            notifyListeners(listener -> listener.onDownloadFailed(downloadInfo, "Download já existe (path)."));
            // Tentar deletar o arquivo recém-criado?
            deletePartialFileSafely(downloadInfo.getFilePath());
            return false;
        }

        activeDownloadsByPath.put(downloadInfo.getFilePath(), downloadInfo);
        database.addOrUpdateDownload(downloadInfo);

        if (downloadInfo.getStatus() == DownloadInfo.STATUS_QUEUED) {
            synchronized (downloadQueue) {
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

    // Métodos startDownload antigos podem ser mantidos para compatibilidade ou removidos/adaptados
    @Deprecated
    public boolean startDownload(String url, String fileName) {
         List<String> urls = new ArrayList<>();
         urls.add(url);
         return startOrUpdateDownload(urls, fileName, null, null);
    }
    @Deprecated
    public boolean startDownload(DownloadInfo downloadInfo) {
         // Este método é problemático pois recebe um DownloadInfo pré-construído
         // que pode não ter passado pela lógica de verificação de nome/arquivo.
         // Melhor adaptar a UI para chamar startOrUpdateDownload diretamente.
         Log.w(TAG, "Método startDownload(DownloadInfo) está obsoleto e pode causar inconsistências.");
         if (downloadInfo == null) return false;
         // Tentar simular a lógica, mas é arriscado
         return startOrUpdateDownload(downloadInfo.getSourceUrls(), downloadInfo.getFileName(), downloadInfo.getCookies(), downloadInfo.getCustomHeaders());
    }


    public void pauseDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;

        DownloadInfo activeDownload = activeDownloadsByPath.get(downloadInfo.getFilePath());

        if (activeDownload != null &&
            (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING ||
             activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING ||
             activeDownload.getStatus() == DownloadInfo.STATUS_QUEUED)) {

            if (activeDownload.getStatus() == DownloadInfo.STATUS_QUEUED) {
                boolean removed;
                synchronized (downloadQueue) {
                    removed = downloadQueue.remove(activeDownload);
                }
                if (removed) {
                    activeDownload.setStatus(DownloadInfo.STATUS_PAUSED);
                    activeDownload.setLastPauseTime(System.currentTimeMillis());
                    database.addOrUpdateDownload(activeDownload);
                    database.removeFromQueue(activeDownload);
                    Log.d(TAG, "Download removido da fila e pausado: " + activeDownload.getFileName());
                    notifyListeners(listener -> listener.onDownloadUpdated(activeDownload));
                }
            } else {
                // Se estiver RUNNING ou RESUMING, solicitar ao serviço para pausar
                Log.d(TAG, "Solicitando pausa ao serviço para: " + activeDownload.getFileName());
                DownloadService.pauseDownload(context, activeDownload);
                // O serviço notificará a mudança de status quando a pausa for efetivada
            }
        } else {
            Log.w(TAG, "Tentativa de pausar download que não está ativo ou na fila: " + downloadInfo.getFileName());
        }
    }

    public void resumeDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;

        DownloadInfo activeDownload = activeDownloadsByPath.get(downloadInfo.getFilePath());

        if (activeDownload != null && activeDownload.getStatus() == DownloadInfo.STATUS_PAUSED) {
            Log.d(TAG, "Tentando retomar download: " + activeDownload.getFileName());

            // --- CORREÇÃO: Adicionar validação ANTES de colocar na fila ---
            DownloadResumeHelper.ValidationResult validation = DownloadResumeHelper.validatePartialFile(context, activeDownload);
            Log.d(TAG, String.format("Validação para retomada %s: válido=%s, razão=%s",
                activeDownload.getFileName(), validation.isValid, validation.reason));

            if (!validation.isValid) {
                if (validation.shouldRestart) {
                    Log.w(TAG, "Validação falhou, reiniciando download: " + validation.reason);
                    activeDownload.setDownloadedSize(0);
                    activeDownload.setProgress(0);
                    // Tentar deletar arquivo corrompido (melhor deixar para a DownloadTask tratar)
                    // deletePartialFileSafely(activeDownload.getFilePath());
                    // Continuar para colocar na fila e reiniciar
                } else {
                    Log.e(TAG, "Validação falhou, marcando como falha: " + validation.reason);
                    activeDownload.setStatus(DownloadInfo.STATUS_FAILED);
                    activeDownload.setErrorMessage("Falha na validação do arquivo: " + validation.reason);
                    database.addOrUpdateDownload(activeDownload);
                    notifyListeners(listener -> listener.onDownloadFailed(activeDownload, activeDownload.getErrorMessage()));
                    return; // Não colocar na fila se a validação falhou e não é para reiniciar
                }
            }
            // Aplicar correções se necessário (ex: tamanho baixado maior que o real)
            if (validation.needsAdjustment) {
                DownloadResumeHelper.applyValidationCorrections(activeDownload, validation);
                // A atualização do DB ocorrerá abaixo ao definir como QUEUED
            }
            // --- FIM CORREÇÃO ---

            Log.d(TAG, "Colocando download na fila para retomar: " + activeDownload.getFileName());
            activeDownload.setStatus(DownloadInfo.STATUS_QUEUED);
            activeDownload.setLastResumeTime(System.currentTimeMillis());
            activeDownload.setErrorMessage(null); // Limpar erro anterior
            activeDownload.resetResumeAttempts(); // Resetar tentativas de URL
            database.addOrUpdateDownload(activeDownload);

            synchronized (downloadQueue) {
                if (!downloadQueue.contains(activeDownload)) {
                    downloadQueue.add(activeDownload);
                }
            }
            database.addToQueue(activeDownload);
            notifyListeners(listener -> listener.onDownloadUpdated(activeDownload)); // Notifica que está na fila
            processQueue();
        } else {
            Log.w(TAG, "Tentativa de retomar download que não está pausado: " + downloadInfo.getFileName());
        }
    }

    public void pauseResumeDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;
        DownloadInfo activeDownload = activeDownloadsByPath.get(downloadInfo.getFilePath());
        if (activeDownload == null) {
            Log.w(TAG, "pauseResumeDownload: Download não encontrado no mapa ativo: " + downloadInfo.getFilePath());
            return;
        }

        if (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING ||
            activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING ||
            activeDownload.getStatus() == DownloadInfo.STATUS_QUEUED) {
            pauseDownload(activeDownload);
        } else if (activeDownload.getStatus() == DownloadInfo.STATUS_PAUSED) {
            resumeDownload(activeDownload);
        } else {
            Log.d(TAG, "pauseResumeDownload: Nenhuma ação para o status " + activeDownload.getStatusText());
        }
    }

    public void cancelDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;

        DownloadInfo activeDownload = activeDownloadsByPath.remove(downloadInfo.getFilePath());

        if (activeDownload != null) {
            Log.d(TAG, "Cancelando download: " + activeDownload.getFileName());
            boolean removedFromQueue = false;
            synchronized (downloadQueue) {
                removedFromQueue = downloadQueue.remove(activeDownload);
            }
            if (removedFromQueue) {
                database.removeFromQueue(activeDownload);
            }

            if (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING || activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING) {
                // Se estiver rodando, solicitar cancelamento ao serviço
                DownloadService.cancelDownload(context, activeDownload);
            } else {
                // Se estava pausado ou na fila, apenas atualizar o status e deletar o arquivo
                activeDownload.setStatus(DownloadInfo.STATUS_CANCELLED);
                activeDownload.setEndTime(System.currentTimeMillis());
                database.addOrUpdateDownload(activeDownload);
                deletePartialFileSafely(activeDownload.getFilePath());
                notifyListeners(listener -> listener.onDownloadUpdated(activeDownload)); // Notificar UI
            }
            // Decrementar contador se estava rodando (o serviço fará isso ao finalizar a task)
            // if (activeDownload.getStatus() == DownloadInfo.STATUS_RUNNING || activeDownload.getStatus() == DownloadInfo.STATUS_RESUMING) {
            //     decrementRunningCount();
            // }
        } else {
            Log.w(TAG, "Tentativa de cancelar download não encontrado no mapa ativo: " + downloadInfo.getFilePath());
            // Tentar remover do DB e deletar arquivo caso exista
            database.deleteDownload(downloadInfo.getFilePath());
            deletePartialFileSafely(downloadInfo.getFilePath());
        }
        processQueue(); // Verificar se pode iniciar próximo
    }

    public void deleteCompletedDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;

        DownloadInfo completedDownload = completedDownloadsByPath.remove(downloadInfo.getFilePath());
        if (completedDownload != null) {
            Log.d(TAG, "Deletando download concluído: " + completedDownload.getFileName());
            // Correção Erro 3: Usar método correto do DB
            database.deleteDownload(completedDownload.getFilePath());
            deletePartialFileSafely(completedDownload.getFilePath());
            // Notificar listeners sobre a remoção (pode ser um novo método no listener)
            // notifyListeners(listener -> listener.onDownloadRemoved(completedDownload));
        } else {
            Log.w(TAG, "Tentativa de deletar download concluído não encontrado: " + downloadInfo.getFilePath());
        }
    }

    // Método chamado pelo DownloadService para atualizar o estado
    public void updateDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) return;

        DownloadInfo activeDownload = activeDownloadsByPath.get(downloadInfo.getFilePath());
        if (activeDownload == null) {
            // Pode ser um download que foi cancelado enquanto o serviço processava
            Log.w(TAG, "updateDownload: Download não encontrado no mapa ativo: " + downloadInfo.getFilePath());
            // Se o status final for concluído, adicionar aos concluídos
            if (downloadInfo.getStatus() == DownloadInfo.STATUS_COMPLETED) {
                 handleCompletion(downloadInfo);
            }
            return;
        }

        // Atualizar o objeto no mapa
        Log.d(TAG, "Updating download status: " + downloadInfo.getFileName() + " -> " + downloadInfo.getStatusText()); // Log status change
        activeDownloadsByPath.put(downloadInfo.getFilePath(), downloadInfo);

        // Persistir no banco de dados periodicamente para não sobrecarregar
        long now = System.currentTimeMillis();
        boolean shouldPersist = now - lastPersistTime > PERSIST_INTERVAL || isFinalStatus(downloadInfo.getStatus());
        
        if (shouldPersist) {
            // Marcar como sendo processado para crash recovery se estiver rodando
            if (downloadInfo.getStatus() == DownloadInfo.STATUS_RUNNING || 
                downloadInfo.getStatus() == DownloadInfo.STATUS_RESUMING) {
                markDownloadAsProcessing(downloadInfo);
            }
            
            database.addOrUpdateDownload(downloadInfo);
            lastPersistTime = now;
            
            // Criar backup periodicamente
            createBackupIfNeeded();
        }

        // Notificar listeners da UI
        notifyListeners(listener -> listener.onDownloadUpdated(downloadInfo));

        // Tratar estados finais
        switch (downloadInfo.getStatus()) {
            case DownloadInfo.STATUS_COMPLETED:
                handleCompletion(downloadInfo);
                break;
            case DownloadInfo.STATUS_FAILED:
                handleFailure(downloadInfo);
                break;
            case DownloadInfo.STATUS_CANCELLED:
                // O cancelamento já removeu do mapa ativo, mas caso a notificação chegue depois
                handleCancellation(downloadInfo);
                break;
            case DownloadInfo.STATUS_PAUSED:
                // Se estava rodando e pausou, decrementar contador
                if (runningDownloadsCount.get() > 0) { // Checagem extra
                    decrementRunningCount();
                }
                break;
            case DownloadInfo.STATUS_RUNNING:
            case DownloadInfo.STATUS_RESUMING:
                // O contador é incrementado ao iniciar a tarefa no processQueue
                break;
        }
    }

    private void handleCompletion(DownloadInfo completedDownload) {
        Log.i(TAG, "Download concluído: " + completedDownload.getFileName());
        activeDownloadsByPath.remove(completedDownload.getFilePath());
        completedDownloadsByPath.put(completedDownload.getFilePath(), completedDownload);
        
        // Limpar dados de crash recovery
        database.clearCrashRecoveryData(completedDownload.getFilePath());
        completedDownload.setCrashRecoveryData(null);
        
        database.addOrUpdateDownload(completedDownload); // Garantir que o estado final está no DB
        decrementRunningCount();
        notifyListeners(listener -> listener.onDownloadCompleted(completedDownload));
        processQueue();
    }

    private void handleFailure(DownloadInfo failedDownload) {
        Log.e(TAG, "Download falhou: " + failedDownload.getFileName() + " Razão: " + failedDownload.getErrorMessage());
        // Manter no mapa ativo para a UI mostrar o erro
        database.addOrUpdateDownload(failedDownload);
        decrementRunningCount();
        notifyListeners(listener -> listener.onDownloadFailed(failedDownload, failedDownload.getErrorMessage()));
        processQueue();
    }

    private void handleCancellation(DownloadInfo cancelledDownload) {
        Log.w(TAG, "Download cancelado (tratado no manager): " + cancelledDownload.getFileName());
        // Já deve ter sido removido do mapa ativo em cancelDownload()
        // Garantir que está removido
        activeDownloadsByPath.remove(cancelledDownload.getFilePath());
        database.deleteDownload(cancelledDownload.getFilePath()); // Remover do DB
        decrementRunningCount();
        // Notificar listeners? (Pode ser redundante se cancelDownload já notificou)
        // notifyListeners(listener -> listener.onDownloadCancelled(cancelledDownload));
        processQueue();
    }

    private boolean isFinalStatus(int status) {
        return status == DownloadInfo.STATUS_COMPLETED ||
               status == DownloadInfo.STATUS_FAILED ||
               status == DownloadInfo.STATUS_CANCELLED;
    }

    private void decrementRunningCount() {
        int currentCount = runningDownloadsCount.decrementAndGet();
        if (currentCount < 0) {
            Log.e(TAG, "Contador de downloads rodando ficou negativo!");
            runningDownloadsCount.set(0);
        }
        Log.d(TAG, "Downloads rodando: " + runningDownloadsCount.get());
    }

    public void processQueue() {
        synchronized (downloadQueue) {
            if (downloadQueue.isEmpty()) {
                Log.d(TAG, "Fila de downloads vazia.");
                checkStopService();
                return;
            }

            int maxConcurrent = getConcurrentDownloadLimit();
            while (runningDownloadsCount.get() < maxConcurrent && !downloadQueue.isEmpty()) {
                DownloadInfo nextDownload = downloadQueue.remove(0);
                database.removeFromQueue(nextDownload);

                if (nextDownload.getStatus() != DownloadInfo.STATUS_QUEUED) {
                    Log.w(TAG, "Item removido da fila não estava no estado QUEUED: " + nextDownload.getFileName());
                    continue; // Pular para o próximo
                }

                // Validar arquivo antes de iniciar
                if (!validatePartialFile(nextDownload)) {
                    Log.e(TAG, "Arquivo parcial inválido para iniciar download: " + nextDownload.getFilePath());
                    nextDownload.setStatus(DownloadInfo.STATUS_FAILED);
                    nextDownload.setErrorMessage("Erro ao validar arquivo para iniciar.");
                    database.addOrUpdateDownload(nextDownload);
                    // Correção Erro 4: Usar variável final ou efetivamente final no lambda
                    final DownloadInfo failedDownload = nextDownload;
                    notifyListeners(listener -> listener.onDownloadFailed(failedDownload, failedDownload.getErrorMessage()));
                    continue; // Tentar o próximo da fila
                }

                Log.i(TAG, "Iniciando download da fila: " + nextDownload.getFileName());
                nextDownload.setStatus(DownloadInfo.STATUS_RESUMING); // Ou RUNNING se downloadedSize == 0
                nextDownload.setLastResumeTime(System.currentTimeMillis());
                database.addOrUpdateDownload(nextDownload);
                runningDownloadsCount.incrementAndGet();
                Log.d(TAG, "Downloads rodando: " + runningDownloadsCount.get());
                DownloadService.startDownload(context, nextDownload);
                notifyListeners(listener -> listener.onDownloadUpdated(nextDownload));
            }

            if (runningDownloadsCount.get() == 0 && downloadQueue.isEmpty()) {
                 checkStopService();
            }
        }
    }

    private boolean validatePartialFile(DownloadInfo downloadInfo) {
        try {
            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile partialFile = DocumentFile.fromSingleUri(context, fileUri);

            if (partialFile == null || !partialFile.exists() || !partialFile.isFile()) {
                Log.w(TAG, "Arquivo parcial SAF não encontrado ou inválido ao validar: " + downloadInfo.getFilePath());
                // Se o arquivo não existe, mas o download tem tamanho > 0, algo está errado.
                if (downloadInfo.getDownloadedSize() > 0) {
                    Log.e(TAG, "Inconsistência: DownloadedSize > 0 mas arquivo não existe!");
                    downloadInfo.setDownloadedSize(0); // Resetar
                    downloadInfo.setProgress(0);
                    database.addOrUpdateDownload(downloadInfo); // Salvar correção
                }
                // Considerar válido para iniciar (ou reiniciar) se o arquivo não existe
                return true;
            }

            long currentFileSize = partialFile.length();
            if (currentFileSize != downloadInfo.getDownloadedSize()) {
                Log.w(TAG, "Tamanho do arquivo parcial SAF (" + currentFileSize +
                      ") não corresponde ao tamanho registrado (" +
                      downloadInfo.getDownloadedSize() + "), ajustando.");
                downloadInfo.setDownloadedSize(currentFileSize);
                if (downloadInfo.getFileSize() > 0) {
                    int progress = (int) ((currentFileSize * 100) / downloadInfo.getFileSize());
                    downloadInfo.setProgress(Math.min(100, Math.max(0, progress)));
                } else {
                    downloadInfo.setProgress(0);
                }
                database.addOrUpdateDownload(downloadInfo); // Salvar correção
            }
            return true; // Arquivo existe e tamanho foi ajustado
        } catch (Exception e) {
            Log.e(TAG, "Erro ao validar arquivo parcial SAF: " + downloadInfo.getFilePath(), e);
            return false; // Considerar inválido em caso de erro
        }
    }

    private void validatePausedDownloads() {
        List<DownloadInfo> downloadsToValidate = new ArrayList<>(activeDownloadsByPath.values());
        for (DownloadInfo download : downloadsToValidate) {
            if (download.getStatus() == DownloadInfo.STATUS_PAUSED) {
                if (!validatePartialFile(download)) {
                    Log.e(TAG, "Arquivo parcial inválido para download pausado: " + download.getFilePath() + ". Marcando como falha.");
                    download.setStatus(DownloadInfo.STATUS_FAILED);
                    download.setErrorMessage("Erro ao validar arquivo pausado.");
                    database.addOrUpdateDownload(download);
                    notifyListeners(listener -> listener.onDownloadFailed(download, download.getErrorMessage()));
                }
            }
        }
    }

    public List<DownloadInfo> getActiveDownloads() {
        List<DownloadInfo> list = new ArrayList<>(activeDownloadsByPath.values());
        Collections.sort(list, (d1, d2) -> Long.compare(d2.getStartTime(), d1.getStartTime()));
        return list;
    }

    public List<DownloadInfo> getCompletedDownloads() {
        List<DownloadInfo> list = new ArrayList<>(completedDownloadsByPath.values());
        Collections.sort(list, (d1, d2) -> Long.compare(d2.getEndTime(), d1.getEndTime()));
        return list;
    }

    // Correção Erro 1: Implementar getDownloadInfoByPath
    public DownloadInfo getDownloadInfoByPath(String filePath) {
        if (filePath == null) return null;
        DownloadInfo info = activeDownloadsByPath.get(filePath);
        if (info == null) {
            info = completedDownloadsByPath.get(filePath);
        }
        return info;
    }

    // Manter getDownloadInfo(String url) se ainda for usado em algum lugar?
    // Se for, precisa iterar pelos mapas.
    @Deprecated
    public DownloadInfo getDownloadInfo(String url) {
        if (url == null) return null;
        for (DownloadInfo info : activeDownloadsByPath.values()) {
            if (url.equals(info.getUrl())) { // Compara URL ativa atual
                return info;
            }
            // Comparar com todas as sourceUrls?
            if (info.getSourceUrls() != null && info.getSourceUrls().contains(url)) {
                 return info;
            }
        }
        for (DownloadInfo info : completedDownloadsByPath.values()) {
             if (url.equals(info.getUrl())) {
                return info;
            }
             if (info.getSourceUrls() != null && info.getSourceUrls().contains(url)) {
                 return info;
            }
        }
        return null;
    }

    public boolean hasActiveOrQueuedDownloads() {
        return !activeDownloadsByPath.isEmpty() || !downloadQueue.isEmpty();
    }

    public void setServiceRunning(boolean running) {
        this.serviceRunning = running;
        if (!running) {
            // Se o serviço parou inesperadamente, pausar downloads que estavam rodando
            for (DownloadInfo download : activeDownloadsByPath.values()) {
                if (download.getStatus() == DownloadInfo.STATUS_RUNNING || download.getStatus() == DownloadInfo.STATUS_RESUMING) {
                    Log.w(TAG, "Serviço parou, marcando download como pausado: " + download.getFileName());
                    download.setStatus(DownloadInfo.STATUS_PAUSED);
                    download.setLastPauseTime(System.currentTimeMillis());
                    database.addOrUpdateDownload(download);
                    notifyListeners(listener -> listener.onDownloadUpdated(download));
                }
            }
            runningDownloadsCount.set(0);
        }
    }

    private void checkStopService() {
        if (!hasActiveOrQueuedDownloads() && runningDownloadsCount.get() == 0) {
            Log.i(TAG, "Nenhum download ativo ou na fila. Solicitando parada do serviço.");
            try {
                context.stopService(new Intent(context, DownloadService.class));
            } catch (Exception e) {
                Log.e(TAG, "Erro ao tentar parar o DownloadService", e);
            }
        }
    }

    private void deletePartialFileSafely(String fileUriPath) {
        if (fileUriPath == null || !fileUriPath.startsWith("content://")) return;
        try {
            Uri fileUri = Uri.parse(fileUriPath);
            DocumentFile fileToDelete = DocumentFile.fromSingleUri(context, fileUri);
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


    private void notifyListeners(DownloadNotification notification) {
        synchronized (listeners) {
            for (DownloadListener listener : listeners) {
                try {
                    notification.notify(listener);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao notificar listener", e);
                }
            }
        }
    }
    
    /**
     * Cria backup do banco de dados se necessário
     */
    private void createBackupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastBackupTime > BACKUP_INTERVAL) {
            try {
                if (database.createBackup()) {
                    lastBackupTime = now;
                    Log.d(TAG, "Backup do banco criado com sucesso");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao criar backup: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Força persistência de todos os downloads ativos
     */
    public void forceFullPersist() {
        try {
            List<DownloadInfo> allActiveDownloads = new ArrayList<>();
            allActiveDownloads.addAll(activeDownloadsByPath.values());
            allActiveDownloads.addAll(downloadQueue);
            
            database.forceFullPersist(allActiveDownloads);
            createBackupIfNeeded();
            
            Log.d(TAG, "Persistência forçada concluída para " + allActiveDownloads.size() + " downloads");
        } catch (Exception e) {
            Log.e(TAG, "Erro na persistência forçada: " + e.getMessage(), e);
        }
    }
    
    /**
     * Recupera downloads que podem ter sido interrompidos por crash
     */
    public void performCrashRecovery() {
        try {
            List<DownloadInfo> recoveryDownloads = database.getDownloadsNeedingRecovery();
            
            for (DownloadInfo download : recoveryDownloads) {
                if (download.isAutoResumeEnabled()) {
                    // Marcar como pausado para permitir retomada manual ou automática
                    download.setStatus(DownloadInfo.STATUS_PAUSED);
                    download.setLastPauseTime(System.currentTimeMillis());
                    
                    Log.i(TAG, "Download recuperado de possível crash: " + download.getFileName());
                    
                    // Adicionar de volta aos downloads ativos
                    activeDownloadsByPath.put(download.getFilePath(), download);
                    database.addOrUpdateDownload(download);
                    
                    // Limpar dados de crash recovery
                    database.clearCrashRecoveryData(download.getFilePath());
                    
                    notifyListeners(listener -> listener.onDownloadAdded(download));
                } else {
                    // Se auto-resume não está habilitado, marcar como falhou
                    download.setStatus(DownloadInfo.STATUS_FAILED);
                    download.setErrorMessage("Download interrompido inesperadamente");
                    database.addOrUpdateDownload(download);
                    database.clearCrashRecoveryData(download.getFilePath());
                }
            }
            
            if (!recoveryDownloads.isEmpty()) {
                Log.i(TAG, "Recuperação de crash concluída para " + recoveryDownloads.size() + " downloads");
                processQueue(); // Tentar continuar downloads pendentes
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro na recuperação de crash: " + e.getMessage(), e);
        }
    }
    
    /**
     * Força verificação manual de integridade (chamado pela UI)
     */
    public int forceIntegrityCheck() {
        Log.i(TAG, "Verificação de integridade forçada pela UI");
        performIntegrityCheck();
        
        // Retornar número de downloads problemáticos encontrados
        int problematicCount = 0;
        for (DownloadInfo download : activeDownloadsByPath.values()) {
            if (download.getStatus() == DownloadInfo.STATUS_FAILED) {
                problematicCount++;
            }
        }
        return problematicCount;
    }
    
    /**
     * Verifica e corrige downloads com problemas de integridade
     */
    private void performIntegrityCheck() {
        try {
            Log.d(TAG, "Iniciando verificação de integridade dos downloads");
            
            List<DownloadInfo> problematicDownloads = new ArrayList<>();
            
            // Verificar downloads ativos
            for (DownloadInfo download : activeDownloadsByPath.values()) {
                if (download.getStatus() == DownloadInfo.STATUS_PAUSED || 
                    download.getStatus() == DownloadInfo.STATUS_FAILED) {
                    
                    DownloadResumeHelper.ValidationResult validation = 
                        DownloadResumeHelper.validatePartialFile(context, download);
                    
                    if (!validation.isValid && validation.shouldRestart) {
                        Log.w(TAG, "Download com problema detectado: " + download.getFileName() + 
                             " - " + validation.reason);
                        problematicDownloads.add(download);
                    } else if (validation.needsAdjustment) {
                        Log.i(TAG, "Corrigindo download: " + download.getFileName());
                        DownloadResumeHelper.applyValidationCorrections(download, validation);
                        database.addOrUpdateDownload(download);
                    }
                }
            }
            
            // Processar downloads problemáticos
            for (DownloadInfo download : problematicDownloads) {
                Log.i(TAG, "Reiniciando download corrompido: " + download.getFileName());
                download.setDownloadedSize(0);
                download.setProgress(0);
                download.setStatus(DownloadInfo.STATUS_QUEUED);
                download.setErrorMessage(null);
                download.resetResumeAttempts();
                
                // Adicionar à fila para tentar novamente
                synchronized (downloadQueue) {
                    if (!downloadQueue.contains(download)) {
                        downloadQueue.add(download);
                    }
                }
                
                database.addOrUpdateDownload(download);
                notifyListeners(listener -> listener.onDownloadUpdated(download));
            }
            
            if (!problematicDownloads.isEmpty()) {
                Log.i(TAG, "Verificação concluída. Reiniciados " + problematicDownloads.size() + " downloads");
                processQueue(); // Processar fila atualizada
            } else {
                Log.d(TAG, "Verificação concluída. Nenhum problema encontrado");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro na verificação de integridade: " + e.getMessage(), e);
        }
    }
    
    /**
     * Marca um download como sendo processado para detecção de crash
     */
    private void markDownloadAsProcessing(DownloadInfo downloadInfo) {
        try {
            String recoveryData = createRecoveryData(downloadInfo);
            downloadInfo.setCrashRecoveryData(recoveryData);
            database.markDownloadAsProcessing(downloadInfo.getFilePath(), recoveryData);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao marcar download para crash recovery: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cria dados de recuperação em formato JSON simples
     */
    private String createRecoveryData(DownloadInfo downloadInfo) {
        // Criar um JSON simples com informações críticas
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        json.append("\"status\":").append(downloadInfo.getStatus()).append(",");
        json.append("\"downloadedSize\":").append(downloadInfo.getDownloadedSize()).append(",");
        json.append("\"currentUrlIndex\":").append(downloadInfo.getCurrentUrlIndex());
        json.append("}");
        return json.toString();
    }

    @FunctionalInterface
    private interface DownloadNotification {
        void notify(DownloadListener listener);
    }
}

