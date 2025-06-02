package com.LDGAMES.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DownloadInfo implements Parcelable {
    // Constantes de status
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_QUEUED = 6;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_CANCELLED = 5;
    public static final int STATUS_PAUSING = 7;
    public static final int STATUS_RESUMING = 8;

    private String fileName;
    private String url; // URL ativa sendo usada
    private String filePath;
    private long fileSize;
    private long downloadedSize;
    private int progress;
    private int status;
    private long startTime;
    private long endTime;
    private int parts;
    private long speed; // Velocidade em bytes por segundo
    private String estimatedTimeRemaining; // Tempo restante formatado
    private String errorMessage;
    private long lastPauseTime;
    private long lastResumeTime;
    private int resumeAttempts;
    private String mimeType;
    private String cookies;
    private Map<String, String> customHeaders;

    // Novos campos para múltiplas fontes
    private List<String> sourceUrls; // Lista de URLs de origem
    private int currentUrlIndex; // Índice da URL ativa na lista sourceUrls

    // Campos para cálculo de velocidade (transitórios)
    private transient long lastUpdateTime = 0;
    private transient long lastDownloadedSize = 0;
    
    // Novos campos para persistência melhorada
    private long lastPersistTime = 0;
    private int persistCount = 0;
    private boolean autoResumeEnabled = true;
    private String crashRecoveryData = null;

    // Construtor padrão
    public DownloadInfo() {
        this.fileName = "";
        this.url = "";
        this.filePath = "";
        this.fileSize = 0;
        this.downloadedSize = 0;
        this.progress = 0;
        this.status = STATUS_PENDING;
        this.startTime = 0;
        this.endTime = 0;
        this.parts = 0;
        this.speed = 0;
        this.estimatedTimeRemaining = "";
        this.errorMessage = "";
        this.lastPauseTime = 0;
        this.lastResumeTime = 0;
        this.resumeAttempts = 0;
        this.mimeType = "";
        this.cookies = null;
        this.customHeaders = null;
        this.sourceUrls = new ArrayList<>(); // Inicializar lista
        this.currentUrlIndex = 0;
        this.lastPersistTime = 0;
        this.persistCount = 0;
        this.autoResumeEnabled = true;
        this.crashRecoveryData = null;
    }

    // Construtor com parâmetros básicos (agora aceita lista de URLs)
    public DownloadInfo(String fileName, List<String> sourceUrls) {
        this();
        this.fileName = fileName;
        if (sourceUrls != null && !sourceUrls.isEmpty()) {
            this.sourceUrls = new ArrayList<>(sourceUrls); // Copiar lista
            this.url = this.sourceUrls.get(0); // Definir URL ativa inicial
        } else {
            this.sourceUrls = new ArrayList<>();
            this.url = ""; // Ou lançar exceção?
        }
        this.currentUrlIndex = 0;
    }

    // Construtor com URL única (para compatibilidade ou casos simples)
    public DownloadInfo(String fileName, String singleUrl) {
        this();
        this.fileName = fileName;
        this.sourceUrls = new ArrayList<>();
        if (singleUrl != null && !singleUrl.isEmpty()) {
            this.sourceUrls.add(singleUrl);
            this.url = singleUrl;
        } else {
             this.url = "";
        }
        this.currentUrlIndex = 0;
    }


    protected DownloadInfo(Parcel in) {
        fileName = in.readString();
        url = in.readString();
        filePath = in.readString();
        fileSize = in.readLong();
        downloadedSize = in.readLong();
        progress = in.readInt();
        status = in.readInt();
        startTime = in.readLong();
        endTime = in.readLong();
        parts = in.readInt();
        speed = in.readLong();
        estimatedTimeRemaining = in.readString();
        errorMessage = in.readString();
        lastPauseTime = in.readLong();
        lastResumeTime = in.readLong();
        resumeAttempts = in.readInt();
        mimeType = in.readString();
        cookies = in.readString();

        // Read customHeaders map
        int headersSize = in.readInt();
        if (headersSize >= 0) {
            customHeaders = new HashMap<>(headersSize);
            for (int i = 0; i < headersSize; i++) {
                String key = in.readString();
                String value = in.readString();
                if (key != null) {
                    customHeaders.put(key, value);
                }
            }
        } else {
            customHeaders = null;
        }

        // Read sourceUrls list
        sourceUrls = new ArrayList<>();
        in.readStringList(sourceUrls);
        currentUrlIndex = in.readInt();
        
        // Read novos campos de persistência
        lastPersistTime = in.readLong();
        persistCount = in.readInt();
        autoResumeEnabled = in.readByte() != 0;
        crashRecoveryData = in.readString();

        // Não parcelar lastUpdateTime e lastDownloadedSize
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fileName);
        dest.writeString(url);
        dest.writeString(filePath);
        dest.writeLong(fileSize);
        dest.writeLong(downloadedSize);
        dest.writeInt(progress);
        dest.writeInt(status);
        dest.writeLong(startTime);
        dest.writeLong(endTime);
        dest.writeInt(parts);
        dest.writeLong(speed);
        dest.writeString(estimatedTimeRemaining);
        dest.writeString(errorMessage);
        dest.writeLong(lastPauseTime);
        dest.writeLong(lastResumeTime);
        dest.writeInt(resumeAttempts);
        dest.writeString(mimeType);
        dest.writeString(cookies);

        // Write customHeaders map
        if (customHeaders != null) {
            dest.writeInt(customHeaders.size());
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                dest.writeString(entry.getKey());
                dest.writeString(entry.getValue());
            }
        } else {
            dest.writeInt(-1); // Indicate null map
        }

        // Write sourceUrls list
        dest.writeStringList(sourceUrls);
        dest.writeInt(currentUrlIndex);
        
        // Write novos campos de persistência
        dest.writeLong(lastPersistTime);
        dest.writeInt(persistCount);
        dest.writeByte((byte) (autoResumeEnabled ? 1 : 0));
        dest.writeString(crashRecoveryData);

        // Não parcelar lastUpdateTime e lastDownloadedSize
    }

    public static final Creator<DownloadInfo> CREATOR = new Creator<DownloadInfo>() {
        @Override
        public DownloadInfo createFromParcel(Parcel in) {
            return new DownloadInfo(in);
        }

        @Override
        public DownloadInfo[] newArray(int size) {
            return new DownloadInfo[size];
        }
    };

    // --- Getters e Setters --- 
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getUrl() { return url; } // Retorna a URL ativa
    public void setUrl(String url) { this.url = url; } // Define a URL ativa (usado internamente)
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public long getDownloadedSize() { return downloadedSize; }
    public void setDownloadedSize(long downloadedSize) { this.downloadedSize = downloadedSize; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public int getParts() { return parts; }
    public void setParts(int parts) { this.parts = parts; }
    public long getSpeed() { return speed; }
    public void setSpeed(long speed) { this.speed = speed; }
    public String getEstimatedTimeRemaining() { return estimatedTimeRemaining; }
    public void setEstimatedTimeRemaining(String estimatedTimeRemaining) { this.estimatedTimeRemaining = estimatedTimeRemaining; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getLastPauseTime() { return lastPauseTime; }
    public void setLastPauseTime(long lastPauseTime) { this.lastPauseTime = lastPauseTime; }
    public long getLastResumeTime() { return lastResumeTime; }
    public void setLastResumeTime(long lastResumeTime) { this.lastResumeTime = lastResumeTime; }
    public int getResumeAttempts() { return resumeAttempts; }
    public void setResumeAttempts(int resumeAttempts) { this.resumeAttempts = resumeAttempts; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public void incrementResumeAttempts() { this.resumeAttempts++; }
    public void resetResumeAttempts() { this.resumeAttempts = 0; }

    // Getters e Setters para cookies e headers
    public String getCookies() { return cookies; }
    public void setCookies(String cookies) { this.cookies = cookies; }
    public Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }

    // Getters e Setters para múltiplas fontes
    public List<String> getSourceUrls() { return sourceUrls; }
    public void setSourceUrls(List<String> sourceUrls) {
        this.sourceUrls = (sourceUrls != null) ? new ArrayList<>(sourceUrls) : new ArrayList<>();
        // Atualizar a URL ativa se a lista não estiver vazia e o índice for válido
        if (!this.sourceUrls.isEmpty()) {
            this.currentUrlIndex = Math.max(0, Math.min(this.currentUrlIndex, this.sourceUrls.size() - 1));
            this.url = this.sourceUrls.get(this.currentUrlIndex);
        } else {
            this.currentUrlIndex = 0;
            this.url = "";
        }
    }
    public int getCurrentUrlIndex() { return currentUrlIndex; }
    public void setCurrentUrlIndex(int currentUrlIndex) {
        if (this.sourceUrls != null && !this.sourceUrls.isEmpty()) {
            this.currentUrlIndex = Math.max(0, Math.min(currentUrlIndex, this.sourceUrls.size() - 1));
            this.url = this.sourceUrls.get(this.currentUrlIndex);
        } else {
            this.currentUrlIndex = 0;
            this.url = "";
        }
    }

    // Método para avançar para a próxima URL na lista
    public boolean tryNextUrl() {
        if (sourceUrls == null || sourceUrls.size() <= 1) {
            return false; // Não há próxima URL para tentar
        }
        int nextIndex = (currentUrlIndex + 1) % sourceUrls.size();
        setCurrentUrlIndex(nextIndex);
        return true;
    }

    // --- Métodos de Cálculo --- 

    public void calculateSpeed() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            lastDownloadedSize = downloadedSize;
            this.speed = 0;
            return;
        }
        long timeDiff = currentTime - lastUpdateTime;
        if (timeDiff < 500) { return; }
        long bytesDiff = downloadedSize - lastDownloadedSize;
        this.speed = (bytesDiff <= 0) ? 0 : (bytesDiff * 1000) / timeDiff;
        lastUpdateTime = currentTime;
        lastDownloadedSize = downloadedSize;
    }

    public void calculateEstimatedTimeRemaining() {
        if (fileSize <= 0 || downloadedSize >= fileSize || speed <= 0) {
            this.estimatedTimeRemaining = "";
            return;
        }
        long remainingBytes = fileSize - downloadedSize;
        long remainingSeconds = remainingBytes / speed;
        this.estimatedTimeRemaining = formatTimeDuration(remainingSeconds);
    }

    // --- Métodos de Formatação --- 

    public String getStatusText() {
        switch (status) {
            case STATUS_PENDING: return "Pendente";
            case STATUS_QUEUED: return "Na fila";
            case STATUS_RUNNING: return "Baixando";
            case STATUS_PAUSING: return "Pausando...";
            case STATUS_PAUSED: return "Pausado";
            case STATUS_RESUMING: return "Retomando...";
            case STATUS_COMPLETED: return "Concluído";
            case STATUS_FAILED: return "Falhou";
            case STATUS_CANCELLED: return "Cancelado";
            default: return "Desconhecido";
        }
    }

    public String getFormattedProgress() {
        return progress + "%";
    }

    public String getFormattedFileSize() {
        return formatBytes(fileSize);
    }
    
    public String getFormattedDownloadedSize() {
        return formatBytes(downloadedSize);
    }

    public String getFormattedSpeed() {
        return formatBytes(speed) + "/s";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatTimeDuration(long seconds) {
        if (seconds <= 0) { return "0s"; }
        long days = TimeUnit.SECONDS.toDays(seconds);
        seconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);
        StringBuilder sb = new StringBuilder();
        if (days > 0) { sb.append(days).append("d "); }
        if (hours > 0 || days > 0) { sb.append(hours).append("h "); }
        if (minutes > 0 || hours > 0 || days > 0) { sb.append(minutes).append("m "); }
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    // --- Parcelable --- 

    @Override
    public int describeContents() {
        return 0;
    }

    // Getters e Setters para novos campos de persistência
    public long getLastPersistTime() { return lastPersistTime; }
    public void setLastPersistTime(long lastPersistTime) { this.lastPersistTime = lastPersistTime; }
    
    public int getPersistCount() { return persistCount; }
    public void setPersistCount(int persistCount) { this.persistCount = persistCount; }
    
    public boolean isAutoResumeEnabled() { return autoResumeEnabled; }
    public void setAutoResumeEnabled(boolean autoResumeEnabled) { this.autoResumeEnabled = autoResumeEnabled; }
    
    public String getCrashRecoveryData() { return crashRecoveryData; }
    public void setCrashRecoveryData(String crashRecoveryData) { this.crashRecoveryData = crashRecoveryData; }

    // equals() e hashCode() devem ser implementados se DownloadInfo for usado em Sets ou como chave em Maps
    // Baseado na URL ativa ou talvez no filePath?
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadInfo that = (DownloadInfo) o;
        // Considerar igual se o filePath for o mesmo (identificador único do arquivo destino)
        // Ou usar a URL ativa? A URL ativa pode mudar com múltiplas fontes.
        // Usar filePath parece mais robusto para identificar o *mesmo* download.
        return filePath != null ? filePath.equals(that.filePath) : that.filePath == null;
    }

    @Override
    public int hashCode() {
        // Usar filePath para consistência com equals()
        return filePath != null ? filePath.hashCode() : 0;
    }
}

