package com.LDGAMES.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.LDGAMES.models.DownloadInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadMetadataResolver {
    private static final String TAG = "DownloadMetadataResolver";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper()); // Handler for main thread

    public interface MetadataCallback {
        void onMetadataResolved(String resolvedUrl, String mimeType, long contentLength);
        void onMetadataError(String errorMessage);
    }
    
    public interface DownloadInfoCallback {
        void onMetadataResolved(DownloadInfo downloadInfo);
        void onMetadataError(String errorMessage);
    }
    
    public interface LinkValidationCallback {
        void onLinkValid(DownloadInfo downloadInfo);
        void onLinkExpired(DownloadInfo downloadInfo, String errorMessage);
    }

    /**
     * Resolve metadados de URL para download
     * @param context Contexto da aplicação (pode ser removido se não for usado)
     * @param url URL do arquivo
     * @param callback Callback para retornar os metadados
     */
    public static void resolveMetadata(Context context, String url, MetadataCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Tentar obter informações do servidor
                URL urlObj = new URL(url);
                connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000); // 15 seconds timeout
                connection.setReadTimeout(15000); // 15 seconds timeout
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                    // Obter tamanho do arquivo
                    long contentLength = connection.getContentLengthLong(); // Use Long version
                    
                    // Obter tipo MIME
                    String mimeType = connection.getContentType();
                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = "application/octet-stream";
                    }
                    
                    // Obter URL final (após redirecionamentos)
                    String resolvedUrl = connection.getURL().toString();
                    
                    // Fazer cópias finais para usar no lambda
                    final String finalResolvedUrl = resolvedUrl;
                    final String finalMimeType = mimeType;
                    final long finalContentLength = contentLength;
                    
                    // Chamar callback na thread principal
                    mainThreadHandler.post(() -> callback.onMetadataResolved(finalResolvedUrl, finalMimeType, finalContentLength));
                } else {
                    final String errorMessage = "Erro ao obter metadados: código " + responseCode;
                    // Chamar callback de erro na thread principal
                    mainThreadHandler.post(() -> callback.onMetadataError(errorMessage));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Erro ao resolver metadados para " + url, e);
                final String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido ao conectar";
                // Chamar callback de erro na thread principal
                mainThreadHandler.post(() -> callback.onMetadataError(errorMessage));
            } finally {
                 if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // Sobrecarga do método resolveMetadata para DownloadInfoCallback
    public static void resolveMetadata(Context context, String url, String initialFileName, long initialContentLength, DownloadInfoCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Criar objeto de download com informações iniciais
                DownloadInfo downloadInfo = new DownloadInfo();
                downloadInfo.setUrl(url);
                
                // Definir nome do arquivo
                String fileName = initialFileName;
                if (fileName == null || fileName.isEmpty()) {
                    fileName = FileUtils.getFileNameFromUrl(url);
                    if (fileName == null || fileName.isEmpty()) {
                         fileName = "download_" + System.currentTimeMillis(); // Default name
                    }
                }
                downloadInfo.setFileName(fileName);
                
                // Definir tamanho do arquivo inicial
                if (initialContentLength > 0) {
                    downloadInfo.setFileSize(initialContentLength);
                }
                
                // Definir caminho do arquivo inicial
                String filePath = FileUtils.getDownloadFilePath(context, fileName);
                downloadInfo.setFilePath(filePath);
                
                // Tentar obter mais informações do servidor
                try {
                    URL urlObj = new URL(url);
                    connection = (HttpURLConnection) urlObj.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        // Obter tamanho do arquivo
                        long fileSize = connection.getContentLengthLong();
                        if (fileSize > 0) {
                            downloadInfo.setFileSize(fileSize);
                        }
                        
                        // Obter tipo MIME
                        String mimeType = connection.getContentType();
                        if (mimeType != null && !mimeType.isEmpty()) {
                            downloadInfo.setMimeType(mimeType);
                        } else {
                            downloadInfo.setMimeType("application/octet-stream");
                        }
                        
                        // Obter nome do arquivo do Content-Disposition
                        String contentDisposition = connection.getHeaderField("Content-Disposition");
                        String extractedFileName = null;
                        if (contentDisposition != null) {
                            extractedFileName = FileUtils.extractFilenameFromContentDisposition(contentDisposition);
                        }
                        
                        // Se o nome do arquivo foi extraído e é diferente, atualiza
                        if (extractedFileName != null && !extractedFileName.isEmpty() && !extractedFileName.equals(downloadInfo.getFileName())) {
                            downloadInfo.setFileName(extractedFileName);
                            // Atualizar caminho do arquivo
                            filePath = FileUtils.getDownloadFilePath(context, extractedFileName);
                            downloadInfo.setFilePath(filePath);
                        }
                        // Se não extraiu do header, mas o nome inicial era genérico, tenta pegar da URL final
                        else if ((initialFileName == null || initialFileName.isEmpty()) && (fileName == null || fileName.startsWith("download_"))) {
                             String nameFromUrl = FileUtils.getFileNameFromUrl(connection.getURL().toString());
                             if (nameFromUrl != null && !nameFromUrl.isEmpty()) {
                                 downloadInfo.setFileName(nameFromUrl);
                                 filePath = FileUtils.getDownloadFilePath(context, nameFromUrl);
                                 downloadInfo.setFilePath(filePath);
                             }
                        }
                        
                        // Atualizar URL final (após redirecionamentos)
                        downloadInfo.setUrl(connection.getURL().toString());
                        
                    } else {
                         Log.w(TAG, "Server returned error code " + responseCode + " for HEAD request to " + url);
                         // Manter informações iniciais se HEAD falhar, mas logar aviso
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao obter metadados HEAD do servidor para " + url, e);
                    // Continuar com as informações básicas se a conexão HEAD falhar
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                
                // Fazer cópia final para usar no lambda
                final DownloadInfo finalDownloadInfo = downloadInfo;
                // Chamar callback com as informações obtidas na thread principal
                mainThreadHandler.post(() -> callback.onMetadataResolved(finalDownloadInfo));
                
            } catch (Exception e) {
                Log.e(TAG, "Erro geral ao resolver metadados para " + url, e);
                final String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido ao processar metadados";
                // Chamar callback de erro na thread principal
                mainThreadHandler.post(() -> callback.onMetadataError(errorMessage));
            }
        });
    }
    
    /**
     * Verifica se um link de download ainda é válido
     * @param downloadInfo Informações do download
     * @param callback Callback para retornar o resultado
     */
    public static void validateDownloadLink(DownloadInfo downloadInfo, LinkValidationCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(downloadInfo.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(10000); // 10 seconds
                connection.setReadTimeout(10000); // 10 seconds
                
                // Se for retomada de download, definir range
                if (downloadInfo.getDownloadedSize() > 0) {
                    connection.setRequestProperty("Range", "bytes=" + downloadInfo.getDownloadedSize() + "-");
                }
                
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                
                // Fazer cópia final para usar no lambda
                final DownloadInfo finalDownloadInfo = downloadInfo;
                
                // Verificar se o link ainda é válido
                if (responseCode == HttpURLConnection.HTTP_OK || 
                    responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    // Link válido
                    mainThreadHandler.post(() -> callback.onLinkValid(finalDownloadInfo));
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || 
                           responseCode == HttpURLConnection.HTTP_GONE) {
                    // Link expirado ou não encontrado
                    final String errorMessage = "Link de download expirado ou não encontrado (Erro " + responseCode + ")";
                    mainThreadHandler.post(() -> callback.onLinkExpired(finalDownloadInfo, errorMessage));
                } else {
                    // Outros erros
                    final String errorMessage = "Erro ao verificar link de download (Código " + responseCode + ")";
                    mainThreadHandler.post(() -> callback.onLinkExpired(finalDownloadInfo, errorMessage));
                }
            } catch (IOException e) {
                Log.e(TAG, "Erro de IO ao validar link de download para " + downloadInfo.getUrl(), e);
                final String errorMessage = "Erro ao verificar link: " + e.getMessage();
                final DownloadInfo finalDownloadInfo = downloadInfo;
                mainThreadHandler.post(() -> callback.onLinkExpired(finalDownloadInfo, errorMessage));
            } catch (Exception e) {
                 Log.e(TAG, "Erro geral ao validar link de download para " + downloadInfo.getUrl(), e);
                 final String errorMessage = "Erro inesperado ao verificar link: " + e.getMessage();
                 final DownloadInfo finalDownloadInfo = downloadInfo;
                 mainThreadHandler.post(() -> callback.onLinkExpired(finalDownloadInfo, errorMessage));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}

