package com.LDGAMES.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * Obtém um caminho legível para exibição a partir de uma URI de documento
     * @param context Contexto da aplicação
     * @param uri URI do documento/diretório
     * @return Caminho legível para exibição
     */
    public static String getDisplayPath(Context context, Uri uri) {
        if (uri == null) return "";
        
        try {
            DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
            if (documentFile == null) return uri.toString();
            
            String path = documentFile.getName();
            DocumentFile parent = documentFile.getParentFile();
            
            // Tentar construir um caminho mais completo
            while (parent != null && parent.getName() != null) {
                path = parent.getName() + "/" + path;
                parent = parent.getParentFile();
            }
            
            if (path == null || path.isEmpty()) {
                // Fallback para o último segmento da URI
                path = uri.getLastPathSegment();
            }
            
            return path;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter caminho legível", e);
            return uri.toString();
        }
    }
    
    /**
     * Obtém o diretório de download configurado pelo usuário
     * @param context Contexto da aplicação
     * @return File apontando para o diretório de download
     */
    public static File getDownloadDirectory(Context context) {
        try {
            // Obter as configurações salvas
            String path = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("download_path", null);
            
            if (path != null) {
                File directory = new File(path);
                if (directory.exists() && directory.isDirectory() && directory.canWrite()) {
                    return directory;
                }
                
                // Tentar criar o diretório se ele não existir
                if (!directory.exists()) {
                    if (directory.mkdirs()) {
                        Log.d(TAG, "Diretório de download criado com sucesso: " + directory.getAbsolutePath());
                        return directory;
                    } else {
                        Log.e(TAG, "Falha ao criar diretório de download: " + directory.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter diretório de download personalizado", e);
        }
        
        // Fallback para o diretório padrão
        File defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        
        // Garantir que o diretório padrão exista
        if (!defaultDir.exists()) {
            if (!defaultDir.mkdirs()) {
                Log.e(TAG, "Falha ao criar diretório de download padrão: " + defaultDir.getAbsolutePath());
            }
        }
        
        return defaultDir;
    }
    
    /**
     * Obtém o caminho completo para um arquivo no diretório de downloads
     * @param context Contexto da aplicação
     * @param fileName Nome do arquivo
     * @return Caminho completo do arquivo
     */
    public static String getDownloadFilePath(Context context, String fileName) {
        File downloadDir = getDownloadDirectory(context);
        return new File(downloadDir, fileName).getAbsolutePath();
    }
    
    /**
     * Obtém a URI do diretório de download configurado pelo usuário
     * @param context Contexto da aplicação
     * @return Uri do diretório de download ou null se não estiver configurado
     */
    public static Uri getDownloadDirectoryUri(Context context) {
        try {
            // Obter as configurações salvas
            String uriString = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("download_uri", null);
            
            if (uriString != null) {
                Uri uri = Uri.parse(uriString);
                
                // Verificar se ainda temos permissão para esta URI
                try {
                    DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
                    if (documentFile != null && documentFile.canWrite()) {
                        return uri;
                    } else {
                        Log.e(TAG, "Permissão de escrita perdida para URI: " + uriString);
                        // Limpar a URI inválida das configurações
                        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                               .edit()
                               .remove("download_uri")
                               .apply();
                        return null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao verificar permissão para URI: " + e.getMessage());
                    // Limpar a URI inválida das configurações
                    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                           .edit()
                           .remove("download_uri")
                           .apply();
                    return null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter URI do diretório de download", e);
        }
        
        return null;
    }
    
    /**
     * Verifica e renova as permissões para a URI do diretório de download
     * @param context Contexto da aplicação
     * @return true se as permissões estão válidas, false caso contrário
     */
    public static boolean verifyAndRenewUriPermissions(Context context) {
        try {
            String uriString = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("download_uri", null);
            
            if (uriString != null) {
                Uri uri = Uri.parse(uriString);
                
                // Verificar se temos permissão persistente
                boolean hasPermission = false;
                for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(uri) && 
                        permission.isReadPermission() && 
                        permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (!hasPermission) {
                    Log.e(TAG, "Permissão persistente perdida para URI: " + uriString);
                    // Limpar a URI inválida das configurações
                    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                           .edit()
                           .remove("download_uri")
                           .apply();
                    return false;
                }
                
                // Verificar se o DocumentFile é válido e tem permissão de escrita
                DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
                if (documentFile == null || !documentFile.canWrite()) {
                    Log.e(TAG, "DocumentFile inválido ou sem permissão de escrita para URI: " + uriString);
                    // Limpar a URI inválida das configurações
                    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                           .edit()
                           .remove("download_uri")
                           .apply();
                    return false;
                }
                
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar permissões de URI: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Cria ou abre um arquivo no diretório de download configurado pelo usuário
     * @param context Contexto da aplicação
     * @param filename Nome do arquivo a ser criado
     * @param append Se deve abrir em modo append (para continuar download)
     * @return OutputStream para escrever no arquivo ou null em caso de erro
     */
    public static OutputStream createDownloadFile(Context context, String filename, boolean append) {
        try {
            Uri directoryUri = getDownloadDirectoryUri(context);
            
            if (directoryUri != null) {
                // Verificar e renovar permissões
                if (!verifyAndRenewUriPermissions(context)) {
                    Log.e(TAG, "Permissões inválidas para URI, usando método tradicional");
                    directoryUri = null;
                } else {
                    // Usar Storage Access Framework para criar/abrir o arquivo
                    DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
                    if (directory != null && directory.canWrite()) {
                        // Verificar se o arquivo já existe
                        DocumentFile existingFile = findFileInDirectory(directory, filename);
                        
                        if (existingFile != null) {
                            // Arquivo existe, abrir para escrita
                            try {
                                OutputStream outputStream = context.getContentResolver().openOutputStream(existingFile.getUri(), append ? "wa" : "w");
                                if (outputStream != null) {
                                    return outputStream;
                                } else {
                                    Log.e(TAG, "Falha ao abrir OutputStream para arquivo existente: " + filename);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao abrir arquivo existente via SAF: " + e.getMessage(), e);
                            }
                        } else {
                            // Arquivo não existe, criar novo
                            try {
                                // Determinar o tipo MIME se possível
                                String mimeType = "*/*";
                                String extension = getFileExtension(filename);
                                if (!extension.isEmpty()) {
                                    String guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                                    if (guessedType != null) {
                                        mimeType = guessedType;
                                    }
                                }
                                
                                DocumentFile file = directory.createFile(mimeType, filename);
                                if (file != null) {
                                    OutputStream outputStream = context.getContentResolver().openOutputStream(file.getUri());
                                    if (outputStream != null) {
                                        return outputStream;
                                    } else {
                                        Log.e(TAG, "Falha ao abrir OutputStream para novo arquivo: " + filename);
                                    }
                                } else {
                                    Log.e(TAG, "Falha ao criar novo arquivo via SAF: " + filename);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao criar novo arquivo via SAF: " + e.getMessage(), e);
                            }
                        }
                    } else {
                        Log.e(TAG, "Diretório SAF inválido ou sem permissão de escrita");
                    }
                }
            }
            
            // Fallback para o método tradicional
            File directory = getDownloadDirectory(context);
            
            // Garantir que o diretório existe
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Não foi possível criar diretório de download: " + directory.getAbsolutePath());
                    // Tentar usar o diretório de download padrão como último recurso
                    directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!directory.exists() && !directory.mkdirs()) {
                        Log.e(TAG, "Não foi possível criar diretório de download padrão: " + directory.getAbsolutePath());
                        return null;
                    }
                }
            }
            
            File file = new File(directory, filename);
            
            // Garantir que o diretório pai do arquivo existe
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Não foi possível criar diretório pai: " + parentDir.getAbsolutePath());
                }
            }
            
            return new FileOutputStream(file, append);
        } catch (IOException e) {
            Log.e(TAG, "Erro ao criar arquivo de download: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Versão simplificada para compatibilidade com código existente
     */
    public static OutputStream createDownloadFile(Context context, String filename) {
        return createDownloadFile(context, filename, false);
    }
    
    /**
     * Encontra um arquivo em um diretório DocumentFile pelo nome
     * @param directory Diretório onde procurar
     * @param filename Nome do arquivo a encontrar
     * @return DocumentFile encontrado ou null se não existir
     */
    private static DocumentFile findFileInDirectory(DocumentFile directory, String filename) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        
        try {
            for (DocumentFile file : directory.listFiles()) {
                if (file.isFile() && filename.equals(file.getName())) {
                    return file;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar arquivos no diretório: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Verifica se um arquivo existe no diretório de downloads
     * @param context Contexto da aplicação
     * @param filename Nome do arquivo
     * @return true se o arquivo existir, false caso contrário
     */
    public static boolean fileExistsInDownloads(Context context, String filename) {
        Uri directoryUri = getDownloadDirectoryUri(context);
        
        if (directoryUri != null) {
            try {
                // Usar Storage Access Framework
                DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
                if (directory != null) {
                    return findFileInDirectory(directory, filename) != null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar existência de arquivo via SAF: " + e.getMessage(), e);
            }
        }
        
        // Método tradicional
        try {
            File directory = getDownloadDirectory(context);
            File file = new File(directory, filename);
            return file.exists();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar existência de arquivo via método tradicional: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Verificar se um diretório ou URI de documento é gravável
     * @param context Contexto da aplicação
     * @param directory Diretório ou URI a ser verificado
     * @return true se o diretório puder ser escrito, false caso contrário
     */
    public static boolean isDirectoryWritable(Context context, String directory) {
        if (directory == null || directory.isEmpty()) {
            return false;
        }
        
        // Verificar se é uma URI (SAF)
        if (directory.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(directory);
                
                // Verificar se temos permissão persistente
                boolean hasPermission = false;
                for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(uri) && 
                        permission.isReadPermission() && 
                        permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (!hasPermission) {
                    Log.e(TAG, "Sem permissão persistente para URI: " + directory);
                    return false;
                }
                
                DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
                return documentFile != null && documentFile.canWrite();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar permissão de escrita em URI: " + e.getMessage(), e);
                return false;
            }
        } else {
            // É um diretório normal
            File dir = new File(directory);
            
            // Tentar criar o diretório se não existir
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e(TAG, "Não foi possível criar diretório: " + dir.getAbsolutePath());
                    return false;
                }
            }
            
            return dir.exists() && dir.isDirectory() && dir.canWrite();
        }
    }
    
    /**
     * Formata o tamanho do arquivo para exibição legível
     * @param size Tamanho em bytes
     * @return String formatada (ex: "1.5 MB")
     */
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        // Limitar a no máximo "TB"
        digitGroups = Math.min(digitGroups, 4);
        
        DecimalFormat df = new DecimalFormat("#,##0.#");
        return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    /**
     * Extrai o nome do arquivo a partir da URL
     * @param url URL do arquivo
     * @return Nome do arquivo extraído ou um nome genérico
     */
    public static String getFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "download.bin";
        }
        
        try {
            // Remover parâmetros de consulta
            String cleanUrl = url;
            int queryIndex = url.indexOf('?');
            if (queryIndex > 0) {
                cleanUrl = url.substring(0, queryIndex);
            }
            
            // Obter o último segmento da URL
            String[] segments = cleanUrl.split("/");
            String lastSegment = segments[segments.length - 1];
            
            // Se o último segmento estiver vazio, usar um nome genérico
            if (lastSegment.isEmpty()) {
                return "download.bin";
            }
            
            // Decodificar caracteres especiais na URL
            lastSegment = Uri.decode(lastSegment);
            
            return lastSegment;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao extrair nome de arquivo da URL", e);
            return "download.bin";
        }
    }
    
    /**
     * Extrai o nome do arquivo do cabeçalho Content-Disposition
     * @param contentDisposition Valor do cabeçalho Content-Disposition
     * @return Nome do arquivo extraído ou null se não encontrado
     */
    public static String extractFilenameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }
        
        try {
            // Padrão para "filename=" ou "filename*="
            Pattern pattern = Pattern.compile("filename\\*?=['\"]?(?:UTF-\\d['\"]*)?([^'\";\\s]+)['\"]?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(contentDisposition);
            
            if (matcher.find()) {
                String fileName = matcher.group(1);
                // Decodificar caracteres especiais
                return Uri.decode(fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao extrair nome de arquivo do Content-Disposition", e);
        }
        
        return null;
    }
    
    /**
     * Obtém o nome do arquivo a partir do cabeçalho Content-Disposition
     * @param contentDisposition Valor do cabeçalho Content-Disposition
     * @return Nome do arquivo extraído ou null se não encontrado
     */
    public static String getFileNameFromContentDisposition(String contentDisposition) {
        return extractFilenameFromContentDisposition(contentDisposition);
    }
    
    /**
     * Obtém a extensão de arquivo com base no tipo MIME
     * @param mimeType Tipo MIME do arquivo
     * @return Extensão do arquivo com ponto (ex: ".pdf") ou string vazia se não for possível determinar
     */
    public static String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return "";
        }
        
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension != null && !extension.isEmpty()) {
            return "." + extension;
        }
        
        // Fallback para tipos MIME comuns que podem não estar no MimeTypeMap
        if (mimeType.equals("application/zip")) return ".zip";
        if (mimeType.equals("application/x-rar-compressed")) return ".rar";
        if (mimeType.equals("application/x-7z-compressed")) return ".7z";
        if (mimeType.equals("application/pdf")) return ".pdf";
        if (mimeType.equals("application/msword")) return ".doc";
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return ".docx";
        if (mimeType.equals("application/vnd.ms-excel")) return ".xls";
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) return ".xlsx";
        if (mimeType.equals("application/vnd.ms-powerpoint")) return ".ppt";
        if (mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) return ".pptx";
        if (mimeType.equals("text/plain")) return ".txt";
        if (mimeType.equals("text/html")) return ".html";
        if (mimeType.equals("text/css")) return ".css";
        if (mimeType.equals("text/javascript")) return ".js";
        if (mimeType.equals("image/jpeg")) return ".jpg";
        if (mimeType.equals("image/png")) return ".png";
        if (mimeType.equals("image/gif")) return ".gif";
        if (mimeType.equals("image/webp")) return ".webp";
        if (mimeType.equals("image/svg+xml")) return ".svg";
        if (mimeType.equals("audio/mpeg")) return ".mp3";
        if (mimeType.equals("audio/ogg")) return ".ogg";
        if (mimeType.equals("audio/wav")) return ".wav";
        if (mimeType.equals("video/mp4")) return ".mp4";
        if (mimeType.equals("video/webm")) return ".webm";
        if (mimeType.equals("video/x-matroska")) return ".mkv";
        
        // Se não conseguir determinar, retornar string vazia
        return "";
    }
    
    /**
     * Abre um arquivo usando a aplicação padrão do sistema
     * @param context Contexto da aplicação
     * @param filePath Caminho do arquivo a ser aberto
     */
    public static void openFile(Context context, String filePath) {
        openFile(context, filePath, null);
    }
    
    /**
     * Abre um arquivo usando a aplicação padrão do sistema com um MIME type específico
     * @param context Contexto da aplicação
     * @param filePath Caminho do arquivo a ser aberto
     * @param mimeType Tipo MIME do arquivo (pode ser null para autodetecção)
     */
    public static void openFile(Context context, String filePath, String mimeType) {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(context, "Caminho do arquivo inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Verificar se é uma URI de content provider
            if (filePath.startsWith("content://")) {
                Uri fileUri = Uri.parse(filePath);
                
                // Usar o MIME type fornecido ou detectar a partir da URI
                String fileMimeType = mimeType;
                if (fileMimeType == null || fileMimeType.isEmpty()) {
                    fileMimeType = context.getContentResolver().getType(fileUri);
                    if (fileMimeType == null) {
                        fileMimeType = "*/*";
                    }
                }
                
                // Criar intent para abrir o arquivo
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, fileMimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                // Verificar se existe alguma aplicação para abrir este tipo de arquivo
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Nenhum aplicativo encontrado para abrir este tipo de arquivo", Toast.LENGTH_SHORT).show();
                }
                
                return;
            }
            
            // Caminho de arquivo tradicional
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo não encontrado", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Usar o MIME type fornecido ou detectar a partir do arquivo
            String fileMimeType = mimeType;
            if (fileMimeType == null || fileMimeType.isEmpty()) {
                fileMimeType = getMimeType(file);
                if (fileMimeType == null) {
                    fileMimeType = "*/*";
                }
            }
            
            // Criar URI usando FileProvider para compatibilidade com Android 7.0+
            Uri fileUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fileUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file);
            } else {
                fileUri = Uri.fromFile(file);
            }
            
            // Criar intent para abrir o arquivo
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, fileMimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Verificar se existe alguma aplicação para abrir este tipo de arquivo
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Nenhum aplicativo encontrado para abrir este tipo de arquivo", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir arquivo", e);
            Toast.makeText(context, "Erro ao abrir arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Obtém o tipo MIME de um arquivo com base em sua extensão
     * @param file Arquivo
     * @return Tipo MIME ou null se não for possível determinar
     */
    private static String getMimeType(File file) {
        String extension = getFileExtension(file.getName());
        if (extension != null && !extension.isEmpty()) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.getDefault()));
        }
        return null;
    }
    
    /**
     * Obtém a extensão de um arquivo
     * @param fileName Nome do arquivo
     * @return Extensão do arquivo sem o ponto
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
