package com.LDGAMES.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Classe utilitária para inicialização e verificação de diretórios
 * Garante que os diretórios necessários existam antes de qualquer operação de arquivo
 */
public class DirectoryInitializer {
    private static final String TAG = "DirectoryInitializer";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    
    /**
     * Inicializa os diretórios necessários para o funcionamento do aplicativo
     * @param context Contexto da aplicação
     * @return true se a inicialização foi bem-sucedida, false caso contrário
     */
    public static boolean initializeDirectories(Context context) {
        boolean success = true;
        
        // Verificar e criar diretório de downloads
        success &= initializeDownloadDirectory(context);
        
        // Verificar e criar diretório de cache
        success &= initializeCacheDirectory(context);
        
        // Verificar e criar diretório temporário
        success &= initializeTempDirectory(context);
        
        return success;
    }
    
    /**
     * Inicializa o diretório de downloads
     * @param context Contexto da aplicação
     * @return true se a inicialização foi bem-sucedida, false caso contrário
     */
    private static boolean initializeDownloadDirectory(Context context) {
        try {
            // Verificar se temos um URI de diretório salvo
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String downloadUriString = prefs.getString(KEY_DOWNLOAD_URI, null);
            
            if (downloadUriString != null && !downloadUriString.isEmpty()) {
                Uri downloadUri = Uri.parse(downloadUriString);
                
                // Verificar se temos permissão persistente
                boolean hasPermission = false;
                for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(downloadUri) && 
                        permission.isReadPermission() && 
                        permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (!hasPermission) {
                    Log.e(TAG, "Permissão persistente perdida para URI: " + downloadUriString);
                    // Limpar a URI inválida das configurações
                    prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                } else {
                    DocumentFile documentFile = DocumentFile.fromTreeUri(context, downloadUri);
                    
                    if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
                        // Diretório SAF existe e tem permissão de escrita
                        // Testar criação de arquivo para garantir que tudo funciona
                        try {
                            String testFileName = "test_" + System.currentTimeMillis() + ".tmp";
                            DocumentFile testFile = documentFile.createFile("application/octet-stream", testFileName);
                            
                            if (testFile != null) {
                                try {
                                    OutputStream os = context.getContentResolver().openOutputStream(testFile.getUri());
                                    if (os != null) {
                                        os.write("test".getBytes());
                                        os.close();
                                        testFile.delete();  // Limpar após o teste
                                        return true;
                                    } else {
                                        Log.e(TAG, "Não foi possível abrir OutputStream para arquivo de teste");
                                        testFile.delete();  // Tentar limpar mesmo assim
                                    }
                                } catch (IOException e) {
                                    Log.e(TAG, "Erro de I/O ao testar escrita em diretório SAF", e);
                                    testFile.delete();  // Tentar limpar mesmo assim
                                }
                            } else {
                                Log.e(TAG, "Não foi possível criar arquivo de teste em diretório SAF");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao testar escrita em diretório SAF", e);
                        }
                    } else {
                        Log.e(TAG, "URI de diretório de download inválido ou sem permissão: " + downloadUriString);
                        // Remover das configurações
                        prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                    }
                }
            }
            
            // Verificar se temos um caminho de diretório salvo
            String downloadPath = prefs.getString(KEY_DOWNLOAD_PATH, null);
            
            if (downloadPath != null && !downloadPath.isEmpty()) {
                File directory = new File(downloadPath);
                
                // Tentar criar o diretório se não existir
                if (!directory.exists()) {
                    if (directory.mkdirs()) {
                        Log.d(TAG, "Diretório de download criado com sucesso: " + directory.getAbsolutePath());
                        return true;
                    } else {
                        Log.e(TAG, "Falha ao criar diretório de download: " + directory.getAbsolutePath());
                        // Remover das configurações
                        prefs.edit().remove(KEY_DOWNLOAD_PATH).apply();
                    }
                } else if (directory.isDirectory() && directory.canWrite()) {
                    // Diretório existe e tem permissão de escrita
                    // Testar criação de arquivo para garantir que tudo funciona
                    try {
                        File testFile = new File(directory, "test_" + System.currentTimeMillis() + ".tmp");
                        if (testFile.createNewFile()) {
                            testFile.delete();  // Limpar após o teste
                            return true;
                        } else {
                            Log.e(TAG, "Não foi possível criar arquivo de teste em diretório tradicional");
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Erro de I/O ao testar escrita em diretório tradicional", e);
                    }
                } else {
                    // Diretório existe mas não tem permissão ou não é um diretório
                    Log.e(TAG, "Diretório de download existe mas não tem permissão ou não é um diretório: " + directory.getAbsolutePath());
                    // Remover das configurações
                    prefs.edit().remove(KEY_DOWNLOAD_PATH).apply();
                }
            }
            
            // Usar o diretório de download padrão do sistema
            File defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            
            // Criar diretório se não existir
            if (!defaultDir.exists()) {
                if (defaultDir.mkdirs()) {
                    Log.d(TAG, "Diretório de download padrão criado com sucesso: " + defaultDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Falha ao criar diretório de download padrão: " + defaultDir.getAbsolutePath());
                    return false;
                }
            }
            
            // Verificar permissões e testar escrita
            if (defaultDir.isDirectory() && defaultDir.canWrite()) {
                try {
                    File testFile = new File(defaultDir, "test_" + System.currentTimeMillis() + ".tmp");
                    if (testFile.createNewFile()) {
                        testFile.delete();  // Limpar após o teste
                        return true;
                    } else {
                        Log.e(TAG, "Não foi possível criar arquivo de teste em diretório padrão");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Erro de I/O ao testar escrita em diretório padrão", e);
                }
            } else {
                Log.e(TAG, "Diretório de download padrão não tem permissão de escrita: " + defaultDir.getAbsolutePath());
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar diretório de download", e);
            return false;
        }
    }
    
    /**
     * Inicializa o diretório de cache
     * @param context Contexto da aplicação
     * @return true se a inicialização foi bem-sucedida, false caso contrário
     */
    private static boolean initializeCacheDirectory(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            
            // Criar diretório se não existir
            if (!cacheDir.exists()) {
                if (cacheDir.mkdirs()) {
                    Log.d(TAG, "Diretório de cache criado com sucesso: " + cacheDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Falha ao criar diretório de cache: " + cacheDir.getAbsolutePath());
                    return false;
                }
            }
            
            // Verificar permissões e testar escrita
            if (cacheDir.isDirectory() && cacheDir.canWrite()) {
                try {
                    File testFile = new File(cacheDir, "test_" + System.currentTimeMillis() + ".tmp");
                    if (testFile.createNewFile()) {
                        testFile.delete();  // Limpar após o teste
                        return true;
                    } else {
                        Log.e(TAG, "Não foi possível criar arquivo de teste em diretório de cache");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Erro de I/O ao testar escrita em diretório de cache", e);
                }
            } else {
                Log.e(TAG, "Diretório de cache não tem permissão de escrita: " + cacheDir.getAbsolutePath());
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar diretório de cache", e);
            return false;
        }
    }
    
    /**
     * Inicializa o diretório temporário
     * @param context Contexto da aplicação
     * @return true se a inicialização foi bem-sucedida, false caso contrário
     */
    private static boolean initializeTempDirectory(Context context) {
        try {
            File tempDir = new File(context.getCacheDir(), "temp");
            
            // Criar diretório se não existir
            if (!tempDir.exists()) {
                if (tempDir.mkdirs()) {
                    Log.d(TAG, "Diretório temporário criado com sucesso: " + tempDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Falha ao criar diretório temporário: " + tempDir.getAbsolutePath());
                    return false;
                }
            }
            
            // Verificar permissões e testar escrita
            if (tempDir.isDirectory() && tempDir.canWrite()) {
                try {
                    File testFile = new File(tempDir, "test_" + System.currentTimeMillis() + ".tmp");
                    if (testFile.createNewFile()) {
                        testFile.delete();  // Limpar após o teste
                        return true;
                    } else {
                        Log.e(TAG, "Não foi possível criar arquivo de teste em diretório temporário");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Erro de I/O ao testar escrita em diretório temporário", e);
                }
            } else {
                Log.e(TAG, "Diretório temporário não tem permissão de escrita: " + tempDir.getAbsolutePath());
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar diretório temporário", e);
            return false;
        }
    }
    
    /**
     * Obtém o diretório temporário
     * @param context Contexto da aplicação
     * @return File apontando para o diretório temporário
     */
    public static File getTempDirectory(Context context) {
        File tempDir = new File(context.getCacheDir(), "temp");
        
        // Garantir que o diretório existe
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        return tempDir;
    }
    
    /**
     * Verifica e corrige as permissões de URI para o diretório de download
     * @param context Contexto da aplicação
     * @return true se as permissões estão válidas, false caso contrário
     */
    public static boolean verifyAndFixUriPermissions(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String uriString = prefs.getString(KEY_DOWNLOAD_URI, null);
            
            if (uriString != null && !uriString.isEmpty()) {
                Uri uri = Uri.parse(uriString);
                
                // Verificar se temos permissão persistente
                boolean hasPermission = false;
                for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(uri)) {
                        if (permission.isReadPermission() && permission.isWritePermission()) {
                            hasPermission = true;
                            break;
                        }
                    }
                }
                
                if (!hasPermission) {
                    Log.e(TAG, "Permissão persistente perdida para URI: " + uriString);
                    // Limpar a URI inválida das configurações
                    prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                    return false;
                }
                
                // Verificar se o DocumentFile é válido e tem permissão de escrita
                DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
                if (documentFile == null || !documentFile.canWrite()) {
                    Log.e(TAG, "DocumentFile inválido ou sem permissão de escrita para URI: " + uriString);
                    // Limpar a URI inválida das configurações
                    prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                    return false;
                }
                
                // Testar criação de arquivo para garantir que tudo funciona
                try {
                    String testFileName = "test_" + System.currentTimeMillis() + ".tmp";
                    DocumentFile testFile = documentFile.createFile("application/octet-stream", testFileName);
                    
                    if (testFile != null) {
                        try {
                            OutputStream os = context.getContentResolver().openOutputStream(testFile.getUri());
                            if (os != null) {
                                os.write("test".getBytes());
                                os.close();
                                testFile.delete();  // Limpar após o teste
                                return true;
                            } else {
                                Log.e(TAG, "Não foi possível abrir OutputStream para arquivo de teste");
                                testFile.delete();  // Tentar limpar mesmo assim
                                prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                                return false;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Erro de I/O ao testar escrita em diretório SAF", e);
                            testFile.delete();  // Tentar limpar mesmo assim
                            prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                            return false;
                        }
                    } else {
                        Log.e(TAG, "Não foi possível criar arquivo de teste em diretório SAF");
                        prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                        return false;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao testar escrita em diretório SAF", e);
                    prefs.edit().remove(KEY_DOWNLOAD_URI).apply();
                    return false;
                }
            }
            
            return false;  // Não há URI configurada
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar permissões de URI", e);
            return false;
        }
    }
}
