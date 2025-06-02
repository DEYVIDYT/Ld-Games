package com.LDGAMES.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.LDGAMES.models.DownloadInfo;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Classe utilitária para gerenciar a retomada robusta de downloads
 */
public class DownloadResumeHelper {
    private static final String TAG = "DownloadResumeHelper";
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_SIZE_DIFFERENCE = 1024 * 1024; // 1MB de diferença tolerável
    private static final int MAX_VALIDATION_ATTEMPTS = 3;

    /**
     * Valida e corrige informações de arquivo parcial para retomada
     */
    public static ValidationResult validatePartialFile(Context context, DownloadInfo downloadInfo) {
        ValidationResult result = new ValidationResult();
        
        try {
            Uri fileUri = Uri.parse(downloadInfo.getFilePath());
            DocumentFile partialFile = DocumentFile.fromSingleUri(context, fileUri);

            // Verificação básica de existência
            if (partialFile == null || !partialFile.exists() || !partialFile.isFile()) {
                Log.w(TAG, "Arquivo parcial não encontrado: " + downloadInfo.getFilePath());
                result.isValid = false;
                result.shouldRestart = true;
                result.reason = "Arquivo não encontrado";
                return result;
            }

            long currentFileSize = partialFile.length();
            long recordedSize = downloadInfo.getDownloadedSize();
            
            Log.d(TAG, String.format("Validando arquivo: tamanho atual=%d, registrado=%d", 
                currentFileSize, recordedSize));

            // Se tamanhos são exatamente iguais, validação OK
            if (currentFileSize == recordedSize) {
                result.isValid = true;
                result.reason = "Tamanhos correspondem exatamente";
                return result;
            }

            // Verificar se diferença é aceitável
            long sizeDifference = Math.abs(currentFileSize - recordedSize);
            if (sizeDifference <= MAX_SIZE_DIFFERENCE) {
                Log.i(TAG, String.format("Diferença de tamanho aceitável (%d bytes), ajustando", sizeDifference));
                result.isValid = true;
                result.needsAdjustment = true;
                result.adjustedSize = currentFileSize;
                result.reason = "Tamanho ajustado automaticamente";
                return result;
            }

            // Diferença muito grande - verificar integridade
            if (currentFileSize > recordedSize) {
                Log.w(TAG, "Arquivo maior que esperado, pode estar corrompido");
                result.isValid = false;
                result.shouldRestart = true;
                result.reason = "Arquivo maior que esperado";
                return result;
            }

            // Arquivo menor que esperado - pode ter sido truncado
            if (currentFileSize < recordedSize) {
                Log.w(TAG, "Arquivo menor que esperado, ajustando para tamanho real");
                result.isValid = true;
                result.needsAdjustment = true;
                result.adjustedSize = currentFileSize;
                result.reason = "Arquivo truncado, ajustado";
                return result;
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro na validação do arquivo parcial: " + e.getMessage(), e);
            result.isValid = false;
            result.shouldRestart = true;
            result.reason = "Erro na validação: " + e.getMessage();
        }

        return result;
    }

    /**
     * Verifica se o servidor suporta range requests
     */
    public static boolean checkServerRangeSupport(String url, String cookies, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL serverUrl = new URL(url);
            connection = (HttpURLConnection) serverUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Adicionar cookies e headers
            if (cookies != null && !cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookies);
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            connection.connect();
            
            String acceptRanges = connection.getHeaderField("Accept-Ranges");
            boolean supportsRanges = "bytes".equals(acceptRanges);
            
            Log.d(TAG, String.format("URL %s - Suporte a Range: %s (Accept-Ranges: %s)", 
                url, supportsRanges, acceptRanges));
                
            return supportsRanges;
            
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar suporte a range: " + e.getMessage(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Testa a retomada fazendo uma requisição range pequena
     */
    public static ResumeTestResult testResumeCapability(String url, long fromByte, String cookies, Map<String, String> headers) {
        ResumeTestResult result = new ResumeTestResult();
        HttpURLConnection connection = null;
        
        try {
            URL serverUrl = new URL(url);
            connection = (HttpURLConnection) serverUrl.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Solicitar apenas alguns bytes para testar
            long toByte = fromByte + 1023; // 1KB de teste
            connection.setRequestProperty("Range", String.format("bytes=%d-%d", fromByte, toByte));

            // Adicionar cookies e headers
            if (cookies != null && !cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookies);
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            connection.connect();
            
            int responseCode = connection.getResponseCode();
            result.responseCode = responseCode;
            
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                result.resumeSupported = true;
                result.reason = "Servidor suporta HTTP 206 Partial Content";
                
                // Verificar content-range header
                String contentRange = connection.getHeaderField("Content-Range");
                if (contentRange != null) {
                    Log.d(TAG, "Content-Range recebido: " + contentRange);
                    result.contentRange = contentRange;
                }
                
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                result.resumeSupported = false;
                result.reason = "Servidor não suporta range requests (HTTP 200 retornado)";
                
            } else {
                result.resumeSupported = false;
                result.reason = "Resposta inesperada: " + responseCode;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro no teste de retomada: " + e.getMessage(), e);
            result.resumeSupported = false;
            result.reason = "Erro: " + e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        return result;
    }

    /**
     * Calcula checksum MD5 de uma parte do arquivo para verificação de integridade
     */
    public static String calculatePartialChecksum(Context context, String filePath, long startByte, long endByte) {
        try {
            Uri fileUri = Uri.parse(filePath);
            DocumentFile file = DocumentFile.fromSingleUri(context, fileUri);
            
            if (file == null || !file.exists()) {
                return null;
            }

            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                 BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {
                
                // Pular para a posição inicial
                bufferedStream.skip(startByte);
                
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = endByte - startByte;
                
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = bufferedStream.read(buffer, 0, toRead);
                    
                    if (bytesRead <= 0) break;
                    
                    md5.update(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
                
                byte[] digest = md5.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                
                return sb.toString();
                
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular checksum: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Aplica correções baseadas na validação
     */
    public static void applyValidationCorrections(DownloadInfo downloadInfo, ValidationResult validation) {
        if (validation.needsAdjustment && validation.adjustedSize >= 0) {
            long oldSize = downloadInfo.getDownloadedSize();
            downloadInfo.setDownloadedSize(validation.adjustedSize);
            
            // Recalcular progresso
            if (downloadInfo.getFileSize() > 0) {
                int progress = (int) ((validation.adjustedSize * 100) / downloadInfo.getFileSize());
                downloadInfo.setProgress(Math.min(100, Math.max(0, progress)));
            }
            
            Log.i(TAG, String.format("Tamanho ajustado de %d para %d bytes (%s)", 
                oldSize, validation.adjustedSize, validation.reason));
        }
    }

    /**
     * Classe para resultado de validação
     */
    public static class ValidationResult {
        public boolean isValid = false;
        public boolean needsAdjustment = false;
        public boolean shouldRestart = false;
        public long adjustedSize = -1;
        public String reason = "";
    }

    /**
     * Classe para resultado de teste de retomada
     */
    public static class ResumeTestResult {
        public boolean resumeSupported = false;
        public int responseCode = -1;
        public String contentRange = "";
        public String reason = "";
    }
}