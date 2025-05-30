package com.LDGAMES.models;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DownloadInfo implements Parcelable {
    // Constantes de status
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_QUEUED = 6;  // Novo status para garantir início imediato
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_CANCELLED = 5;
    // Novo status para indicar que o download está em processo de pausa
    public static final int STATUS_PAUSING = 7;
    // Novo status para indicar que o download está em processo de retomada
    public static final int STATUS_RESUMING = 8;

    private String fileName;
    private String url;
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
    // Novo campo para armazenar o timestamp da última pausa
    private long lastPauseTime;
    // Novo campo para armazenar o timestamp da última retomada
    private long lastResumeTime;
    // Novo campo para controlar tentativas de retomada
    private int resumeAttempts;
    // Novo campo para armazenar o tipo MIME
    private String mimeType;

    // Novos campos para cookies e headers
    private String cookies;
    private java.util.Map<String, String> customHeaders;

    // Campos para cálculo de velocidade
    private long lastUpdateTime = 0;
    private long lastDownloadedSize = 0;

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
        this.cookies = null; // Inicializar como null
        this.customHeaders = null; // Inicializar como null
    }

    // Construtor com parâmetros básicos
    public DownloadInfo(String fileName, String url) {
        this();
        this.fileName = fileName;
        this.url = url;
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
        cookies = in.readString(); // Read cookies
        // Read customHeaders map
        int headersSize = in.readInt();
        if (headersSize >= 0) { // Check for valid size (-1 indicates null map)
            customHeaders = new java.util.HashMap<>(headersSize);
            for (int i = 0; i < headersSize; i++) {
                String key = in.readString();
                String value = in.readString();
                if (key != null) { // Basic null check for key
                    customHeaders.put(key, value);
                }
            }
        } else {
            customHeaders = null;
        }
        // Não parcelar lastUpdateTime e lastDownloadedSize, são transitórios
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

    // --- Getters e Setters --- (Omitidos para brevidade, são os mesmos de antes)
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
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
    public java.util.Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(java.util.Map<String, String> customHeaders) { this.customHeaders = customHeaders; }

    // --- Métodos de Cálculo --- 

    /**
     * Calcula a velocidade instantânea do download.
     * Deve ser chamado periodicamente durante o download.
     */
    public void calculateSpeed() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime == 0) { // Primeira chamada
            lastUpdateTime = currentTime;
            lastDownloadedSize = downloadedSize;
            this.speed = 0;
            return;
        }

        long timeDiff = currentTime - lastUpdateTime;
        if (timeDiff < 500) { // Calcular apenas se passou tempo suficiente (evita divisão por zero e flutuações)
            return;
        }

        long bytesDiff = downloadedSize - lastDownloadedSize;
        if (bytesDiff <= 0) { // Nenhum progresso
            this.speed = 0;
        } else {
            // Velocidade em bytes por segundo
            this.speed = (bytesDiff * 1000) / timeDiff;
        }

        // Atualizar para o próximo cálculo
        lastUpdateTime = currentTime;
        lastDownloadedSize = downloadedSize;
    }

    /**
     * Calcula o tempo estimado restante para o download.
     * Deve ser chamado após calcular a velocidade.
     */
    public void calculateEstimatedTimeRemaining() {
        if (fileSize <= 0 || downloadedSize >= fileSize || speed <= 0) {
            this.estimatedTimeRemaining = ""; // Não é possível calcular
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
        if (seconds <= 0) {
            return "0s";
        }
        long days = TimeUnit.SECONDS.toDays(seconds);
        seconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) { // Mostrar horas se houver dias
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) { // Mostrar minutos se houver horas ou dias
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    // --- Parcelable --- 

    @Override
    public int describeContents() {
        return 0;
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
        dest.writeString(cookies); // Write cookies
        // Write customHeaders map
        if (customHeaders != null) {
            dest.writeInt(customHeaders.size());
            for (java.util.Map.Entry<String, String> entry : customHeaders.entrySet()) {
                dest.writeString(entry.getKey());
                dest.writeString(entry.getValue());
            }
        } else {
            dest.writeInt(-1); // Indicate null map
        }
        // Não parcelar lastUpdateTime e lastDownloadedSize
    }
}

