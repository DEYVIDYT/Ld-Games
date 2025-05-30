package com.LDGAMES.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.LDGAMES.models.DownloadInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe responsável pelo gerenciamento do banco de dados SQLite para downloads
 */
public class DownloadDatabase extends SQLiteOpenHelper {
    
    private static final String TAG = "DownloadDatabase";
    
    // Nome do banco de dados e versão
    private static final String DATABASE_NAME = "downloads.db";
    private static final int DATABASE_VERSION = 1;
    
    // Nome das tabelas
    private static final String TABLE_DOWNLOADS = "downloads";
    private static final String TABLE_ORIGINAL_URLS = "original_urls";
    
    // Colunas da tabela de downloads
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_FILE_NAME = "file_name";
    private static final String COLUMN_URL = "url";
    private static final String COLUMN_FILE_PATH = "file_path";
    private static final String COLUMN_FILE_SIZE = "file_size";
    private static final String COLUMN_DOWNLOADED_SIZE = "downloaded_size";
    private static final String COLUMN_PROGRESS = "progress";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_PARTS = "parts";
    private static final String COLUMN_SPEED = "speed";
    private static final String COLUMN_ESTIMATED_TIME = "estimated_time";
    private static final String COLUMN_ERROR_MESSAGE = "error_message";
    private static final String COLUMN_LAST_PAUSE_TIME = "last_pause_time";
    private static final String COLUMN_LAST_RESUME_TIME = "last_resume_time";
    private static final String COLUMN_RESUME_ATTEMPTS = "resume_attempts";
    private static final String COLUMN_MIME_TYPE = "mime_type";
    private static final String COLUMN_IS_QUEUED = "is_queued";
    
    // Colunas da tabela de URLs originais
    private static final String COLUMN_DOWNLOAD_URL = "download_url";
    private static final String COLUMN_ORIGINAL_URL = "original_url";
    
    // Instância singleton
    private static DownloadDatabase instance;
    
    /**
     * Obtém a instância da base de dados
     * @param context Contexto da aplicação
     * @return Instância do DownloadDatabase
     */
    public static synchronized DownloadDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadDatabase(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Construtor privado para implementação do padrão Singleton
     * @param context Contexto da aplicação
     */
    private DownloadDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Criar tabela de downloads
        String createDownloadsTable = "CREATE TABLE " + TABLE_DOWNLOADS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FILE_NAME + " TEXT NOT NULL, " +
                COLUMN_URL + " TEXT NOT NULL UNIQUE, " +
                COLUMN_FILE_PATH + " TEXT NOT NULL, " +
                COLUMN_FILE_SIZE + " INTEGER DEFAULT 0, " +
                COLUMN_DOWNLOADED_SIZE + " INTEGER DEFAULT 0, " +
                COLUMN_PROGRESS + " INTEGER DEFAULT 0, " +
                COLUMN_STATUS + " INTEGER DEFAULT 0, " +
                COLUMN_START_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_END_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_PARTS + " INTEGER DEFAULT 0, " +
                COLUMN_SPEED + " INTEGER DEFAULT 0, " +
                COLUMN_ESTIMATED_TIME + " TEXT, " +
                COLUMN_ERROR_MESSAGE + " TEXT, " +
                COLUMN_LAST_PAUSE_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_LAST_RESUME_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_RESUME_ATTEMPTS + " INTEGER DEFAULT 0, " +
                COLUMN_MIME_TYPE + " TEXT, " +
                COLUMN_IS_QUEUED + " INTEGER DEFAULT 0" +
                ");";
        
        // Criar tabela de URLs originais
        String createOriginalUrlsTable = "CREATE TABLE " + TABLE_ORIGINAL_URLS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_DOWNLOAD_URL + " TEXT NOT NULL UNIQUE, " +
                COLUMN_ORIGINAL_URL + " TEXT NOT NULL" +
                ");";
        
        // Executar criação das tabelas
        db.execSQL(createDownloadsTable);
        db.execSQL(createOriginalUrlsTable);
        
        Log.d(TAG, "Banco de dados criado com sucesso");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Implementar lógica de migração se necessário
        if (oldVersion < newVersion) {
            // Versão 1 -> 2: adicionar novas colunas, etc.
            Log.d(TAG, "Atualizando banco de dados da versão " + oldVersion + " para " + newVersion);
        }
    }
    
    /**
     * Adiciona ou atualiza um download no banco de dados
     * @param downloadInfo Informações do download
     * @return ID do download no banco de dados, -1 se falhar
     */
    public long addOrUpdateDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null) {
            Log.e(TAG, "Não é possível adicionar um downloadInfo null");
            return -1;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        long result = -1;
        
        try {
            db.beginTransaction();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_FILE_NAME, downloadInfo.getFileName());
            values.put(COLUMN_URL, downloadInfo.getUrl());
            values.put(COLUMN_FILE_PATH, downloadInfo.getFilePath());
            values.put(COLUMN_FILE_SIZE, downloadInfo.getFileSize());
            values.put(COLUMN_DOWNLOADED_SIZE, downloadInfo.getDownloadedSize());
            values.put(COLUMN_PROGRESS, downloadInfo.getProgress());
            values.put(COLUMN_STATUS, downloadInfo.getStatus());
            values.put(COLUMN_START_TIME, downloadInfo.getStartTime());
            values.put(COLUMN_END_TIME, downloadInfo.getEndTime());
            values.put(COLUMN_PARTS, downloadInfo.getParts());
            values.put(COLUMN_SPEED, downloadInfo.getSpeed());
            values.put(COLUMN_ESTIMATED_TIME, downloadInfo.getEstimatedTimeRemaining());
            values.put(COLUMN_ERROR_MESSAGE, downloadInfo.getErrorMessage());
            values.put(COLUMN_LAST_PAUSE_TIME, downloadInfo.getLastPauseTime());
            values.put(COLUMN_LAST_RESUME_TIME, downloadInfo.getLastResumeTime());
            values.put(COLUMN_RESUME_ATTEMPTS, downloadInfo.getResumeAttempts());
            values.put(COLUMN_MIME_TYPE, downloadInfo.getMimeType());
            
            // Verificar se o download já existe
            Cursor cursor = db.query(
                    TABLE_DOWNLOADS,
                    new String[]{COLUMN_ID},
                    COLUMN_URL + " = ?",
                    new String[]{downloadInfo.getUrl()},
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                // Download já existe, atualizar
                int id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                result = db.update(
                        TABLE_DOWNLOADS,
                        values,
                        COLUMN_ID + " = ?",
                        new String[]{String.valueOf(id)});
                Log.d(TAG, "Download atualizado para ID: " + id);
            } else {
                // Novo download, inserir
                result = db.insertWithOnConflict(
                        TABLE_DOWNLOADS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                Log.d(TAG, "Novo download inserido com ID: " + result);
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar/atualizar download: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        return result;
    }
    
    /**
     * Adiciona um download à fila
     * @param downloadInfo Informações do download
     * @return true se adicionado com sucesso, false caso contrário
     */
    public boolean addToQueue(DownloadInfo downloadInfo) {
        if (downloadInfo == null) {
            return false;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;
        
        try {
            db.beginTransaction();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_QUEUED, 1);
            
            int rowsAffected = db.update(
                    TABLE_DOWNLOADS,
                    values,
                    COLUMN_URL + " = ?",
                    new String[]{downloadInfo.getUrl()});
            
            success = rowsAffected > 0;
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar download à fila: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        return success;
    }
    
    /**
     * Remove um download da fila
     * @param downloadInfo Informações do download
     * @return true se removido com sucesso, false caso contrário
     */
    public boolean removeFromQueue(DownloadInfo downloadInfo) {
        if (downloadInfo == null) {
            return false;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;
        
        try {
            db.beginTransaction();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_QUEUED, 0);
            
            int rowsAffected = db.update(
                    TABLE_DOWNLOADS,
                    values,
                    COLUMN_URL + " = ?",
                    new String[]{downloadInfo.getUrl()});
            
            success = rowsAffected > 0;
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao remover download da fila: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        return success;
    }
    
    /**
     * Remove um download do banco de dados
     * @param url URL do download
     * @return true se removido com sucesso, false caso contrário
     */
    public boolean removeDownload(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;
        
        try {
            db.beginTransaction();
            
            int rowsAffected = db.delete(
                    TABLE_DOWNLOADS,
                    COLUMN_URL + " = ?",
                    new String[]{url});
            
            success = rowsAffected > 0;
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao remover download: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        return success;
    }
    
    /**
     * Obtém todos os downloads ativos (não concluídos, não falhos, não cancelados)
     * @return Lista de downloads ativos
     */
    public List<DownloadInfo> getActiveDownloads() {
        List<DownloadInfo> activeDownloads = new ArrayList<>();
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            // Selecionar downloads ativos (pendentes, em execução, pausados, na fila)
            String selection = COLUMN_STATUS + " != ? AND " +
                               COLUMN_STATUS + " != ? AND " +
                               COLUMN_STATUS + " != ?";
            String[] selectionArgs = {
                    String.valueOf(DownloadInfo.STATUS_COMPLETED),
                    String.valueOf(DownloadInfo.STATUS_FAILED),
                    String.valueOf(DownloadInfo.STATUS_CANCELLED)
            };
            
            cursor = db.query(
                    TABLE_DOWNLOADS,
                    null,
                    selection,
                    selectionArgs,
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    DownloadInfo downloadInfo = cursorToDownloadInfo(cursor);
                    activeDownloads.add(downloadInfo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter downloads ativos: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return activeDownloads;
    }
    
    /**
     * Obtém todos os downloads concluídos com sucesso
     * @return Lista de downloads concluídos
     */
    public List<DownloadInfo> getCompletedDownloads() {
        List<DownloadInfo> completedDownloads = new ArrayList<>();
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            // Selecionar apenas downloads concluídos
            String selection = COLUMN_STATUS + " = ?";
            String[] selectionArgs = {String.valueOf(DownloadInfo.STATUS_COMPLETED)};
            
            cursor = db.query(
                    TABLE_DOWNLOADS,
                    null,
                    selection,
                    selectionArgs,
                    null, null, 
                    COLUMN_END_TIME + " DESC"); // Ordenar por data de conclusão (mais recentes primeiro)
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    DownloadInfo downloadInfo = cursorToDownloadInfo(cursor);
                    completedDownloads.add(downloadInfo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter downloads concluídos: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return completedDownloads;
    }
    
    /**
     * Obtém todos os downloads na fila
     * @return Lista de downloads na fila
     */
    public List<DownloadInfo> getQueuedDownloads() {
        List<DownloadInfo> queuedDownloads = new ArrayList<>();
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            // Selecionar downloads na fila
            String selection = COLUMN_STATUS + " = ?";
            String[] selectionArgs = {String.valueOf(DownloadInfo.STATUS_QUEUED)};
            
            cursor = db.query(
                    TABLE_DOWNLOADS,
                    null,
                    selection,
                    selectionArgs,
                    null, null, 
                    COLUMN_START_TIME + " ASC"); // Ordenar por data de adição (mais antigos primeiro)
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    DownloadInfo downloadInfo = cursorToDownloadInfo(cursor);
                    queuedDownloads.add(downloadInfo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter downloads na fila: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return queuedDownloads;
    }
    
    /**
     * Obtém um download específico pelo URL
     * @param url URL do download
     * @return Informações do download ou null se não encontrado
     */
    public DownloadInfo getDownloadByUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        DownloadInfo downloadInfo = null;
        
        try {
            String selection = COLUMN_URL + " = ?";
            String[] selectionArgs = {url};
            
            cursor = db.query(
                    TABLE_DOWNLOADS,
                    null,
                    selection,
                    selectionArgs,
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                downloadInfo = cursorToDownloadInfo(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter download por URL: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return downloadInfo;
    }
    
    /**
     * Define a URL original para uma URL de download
     * @param downloadUrl URL de download
     * @param originalUrl URL original
     * @return true se definido com sucesso, false caso contrário
     */
    public boolean setOriginalUrl(String downloadUrl, String originalUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty() || originalUrl == null || originalUrl.isEmpty()) {
            return false;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;
        
        try {
            db.beginTransaction();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_DOWNLOAD_URL, downloadUrl);
            values.put(COLUMN_ORIGINAL_URL, originalUrl);
            
            // Verificar se já existe um mapeamento para esta URL
            Cursor cursor = db.query(
                    TABLE_ORIGINAL_URLS,
                    new String[]{COLUMN_ID},
                    COLUMN_DOWNLOAD_URL + " = ?",
                    new String[]{downloadUrl},
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                // Mapeamento já existe, atualizar
                int id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                int rowsAffected = db.update(
                        TABLE_ORIGINAL_URLS,
                        values,
                        COLUMN_ID + " = ?",
                        new String[]{String.valueOf(id)});
                success = rowsAffected > 0;
            } else {
                // Novo mapeamento, inserir
                long result = db.insertWithOnConflict(
                        TABLE_ORIGINAL_URLS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                success = result != -1;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao definir URL original: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        return success;
    }
    
    /**
     * Obtém a URL original para uma URL de download
     * @param downloadUrl URL de download
     * @return URL original ou a própria URL de download se não houver mapeamento
     */
    public String getOriginalUrl(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            return downloadUrl;
        }
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        String originalUrl = downloadUrl; // Valor padrão é a própria URL
        
        try {
            String selection = COLUMN_DOWNLOAD_URL + " = ?";
            String[] selectionArgs = {downloadUrl};
            
            cursor = db.query(
                    TABLE_ORIGINAL_URLS,
                    new String[]{COLUMN_ORIGINAL_URL},
                    selection,
                    selectionArgs,
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                originalUrl = cursor.getString(cursor.getColumnIndex(COLUMN_ORIGINAL_URL));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter URL original: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return originalUrl;
    }
    
    /**
     * Obtém todas as URLs originais
     * @return Mapa de URLs de download para URLs originais
     */
    public Map<String, String> getAllOriginalUrls() {
        Map<String, String> originalUrls = new HashMap<>();
        
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            cursor = db.query(
                    TABLE_ORIGINAL_URLS,
                    new String[]{COLUMN_DOWNLOAD_URL, COLUMN_ORIGINAL_URL},
                    null, null, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String downloadUrl = cursor.getString(cursor.getColumnIndex(COLUMN_DOWNLOAD_URL));
                    String originalUrl = cursor.getString(cursor.getColumnIndex(COLUMN_ORIGINAL_URL));
                    originalUrls.put(downloadUrl, originalUrl);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter todas as URLs originais: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return originalUrls;
    }
    
    /**
     * Converte um cursor em um objeto DownloadInfo
     * @param cursor Cursor posicionado na linha a ser convertida
     * @return Objeto DownloadInfo
     */
    private DownloadInfo cursorToDownloadInfo(Cursor cursor) {
        DownloadInfo downloadInfo = new DownloadInfo();
        
        downloadInfo.setFileName(cursor.getString(cursor.getColumnIndex(COLUMN_FILE_NAME)));
        downloadInfo.setUrl(cursor.getString(cursor.getColumnIndex(COLUMN_URL)));
        downloadInfo.setFilePath(cursor.getString(cursor.getColumnIndex(COLUMN_FILE_PATH)));
        downloadInfo.setFileSize(cursor.getLong(cursor.getColumnIndex(COLUMN_FILE_SIZE)));
        downloadInfo.setDownloadedSize(cursor.getLong(cursor.getColumnIndex(COLUMN_DOWNLOADED_SIZE)));
        downloadInfo.setProgress(cursor.getInt(cursor.getColumnIndex(COLUMN_PROGRESS)));
        downloadInfo.setStatus(cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS)));
        downloadInfo.setStartTime(cursor.getLong(cursor.getColumnIndex(COLUMN_START_TIME)));
        downloadInfo.setEndTime(cursor.getLong(cursor.getColumnIndex(COLUMN_END_TIME)));
        downloadInfo.setParts(cursor.getInt(cursor.getColumnIndex(COLUMN_PARTS)));
        downloadInfo.setSpeed(cursor.getLong(cursor.getColumnIndex(COLUMN_SPEED)));
        downloadInfo.setEstimatedTimeRemaining(cursor.getString(cursor.getColumnIndex(COLUMN_ESTIMATED_TIME)));
        downloadInfo.setErrorMessage(cursor.getString(cursor.getColumnIndex(COLUMN_ERROR_MESSAGE)));
        downloadInfo.setLastPauseTime(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_PAUSE_TIME)));
        downloadInfo.setLastResumeTime(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_RESUME_TIME)));
        downloadInfo.setResumeAttempts(cursor.getInt(cursor.getColumnIndex(COLUMN_RESUME_ATTEMPTS)));
        downloadInfo.setMimeType(cursor.getString(cursor.getColumnIndex(COLUMN_MIME_TYPE)));
        
        return downloadInfo;
    }
}