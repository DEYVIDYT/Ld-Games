package com.LDGAMES.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.LDGAMES.models.DownloadInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Classe responsável pelo gerenciamento do banco de dados SQLite para downloads
 * (Modificado para usar filePath como chave primária lógica onde aplicável)
 */
public class DownloadDatabase extends SQLiteOpenHelper {

    private static final String TAG = "DownloadDatabase";

    // Nome do banco de dados e versão
    private static final String DATABASE_NAME = "downloads.db";
    private static final String BACKUP_DATABASE_NAME = "downloads_backup.db";
    // Incrementar versão se o schema mudar (ex: UNIQUE constraint em filePath)
    private static final int DATABASE_VERSION = 3;

    // Nome das tabelas
    private static final String TABLE_DOWNLOADS = "downloads";
    // private static final String TABLE_ORIGINAL_URLS = "original_urls"; // Tabela de URLs originais pode ser removida se não usada
    private static final String TABLE_SOURCE_URLS = "source_urls"; // Nova tabela para múltiplas URLs

    // Colunas da tabela de downloads
    private static final String COLUMN_ID = "_id"; // Convenção SQLite
    private static final String COLUMN_FILE_NAME = "file_name";
    // COLUMN_URL agora representa a URL *ativa* no momento da persistência
    private static final String COLUMN_URL = "active_url";
    // COLUMN_FILE_PATH deve ser a chave lógica principal para identificar um download
    private static final String COLUMN_FILE_PATH = "file_path";
    private static final String COLUMN_FILE_SIZE = "file_size";
    private static final String COLUMN_DOWNLOADED_SIZE = "downloaded_size";
    private static final String COLUMN_PROGRESS = "progress";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    // private static final String COLUMN_PARTS = "parts"; // Remover se não usado
    private static final String COLUMN_SPEED = "speed";
    private static final String COLUMN_ESTIMATED_TIME = "estimated_time";
    private static final String COLUMN_ERROR_MESSAGE = "error_message";
    private static final String COLUMN_LAST_PAUSE_TIME = "last_pause_time";
    private static final String COLUMN_LAST_RESUME_TIME = "last_resume_time";
    private static final String COLUMN_RESUME_ATTEMPTS = "resume_attempts";
    private static final String COLUMN_MIME_TYPE = "mime_type";
    private static final String COLUMN_IS_QUEUED = "is_queued";
    private static final String COLUMN_COOKIES = "cookies";
    private static final String COLUMN_CUSTOM_HEADERS = "custom_headers"; // Armazenar como JSON ou formato serializado
    private static final String COLUMN_CURRENT_URL_INDEX = "current_url_index";
    // Novas colunas para persistência melhorada
    private static final String COLUMN_LAST_PERSIST_TIME = "last_persist_time";
    private static final String COLUMN_PERSIST_COUNT = "persist_count";
    private static final String COLUMN_AUTO_RESUME_ENABLED = "auto_resume_enabled";
    private static final String COLUMN_CRASH_RECOVERY_DATA = "crash_recovery_data";

    // Colunas da tabela de source_urls
    private static final String COLUMN_FK_DOWNLOAD_PATH = "download_path_ref"; // Chave estrangeira para file_path
    private static final String COLUMN_SOURCE_URL = "source_url";
    private static final String COLUMN_URL_ORDER = "url_order"; // Ordem das URLs na lista

    // Instância singleton
    private static DownloadDatabase instance;

    public static synchronized DownloadDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private DownloadDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Criando banco de dados versão " + DATABASE_VERSION);
        // Criar tabela de downloads com filePath como UNIQUE
        String createDownloadsTable = "CREATE TABLE " + TABLE_DOWNLOADS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FILE_NAME + " TEXT NOT NULL, " +
                COLUMN_URL + " TEXT, " + // URL ativa pode ser nula inicialmente
                COLUMN_FILE_PATH + " TEXT NOT NULL UNIQUE, " +
                COLUMN_FILE_SIZE + " INTEGER DEFAULT 0, " +
                COLUMN_DOWNLOADED_SIZE + " INTEGER DEFAULT 0, " +
                COLUMN_PROGRESS + " INTEGER DEFAULT 0, " +
                COLUMN_STATUS + " INTEGER DEFAULT 0, " +
                COLUMN_START_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_END_TIME + " INTEGER DEFAULT 0, " +
                // COLUMN_PARTS + " INTEGER DEFAULT 0, " +
                COLUMN_SPEED + " INTEGER DEFAULT 0, " +
                COLUMN_ESTIMATED_TIME + " TEXT, " +
                COLUMN_ERROR_MESSAGE + " TEXT, " +
                COLUMN_LAST_PAUSE_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_LAST_RESUME_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_RESUME_ATTEMPTS + " INTEGER DEFAULT 0, " +
                COLUMN_MIME_TYPE + " TEXT, " +
                COLUMN_IS_QUEUED + " INTEGER DEFAULT 0, " +
                COLUMN_COOKIES + " TEXT, " +
                COLUMN_CUSTOM_HEADERS + " TEXT, " +
                COLUMN_CURRENT_URL_INDEX + " INTEGER DEFAULT 0, " +
                COLUMN_LAST_PERSIST_TIME + " INTEGER DEFAULT 0, " +
                COLUMN_PERSIST_COUNT + " INTEGER DEFAULT 0, " +
                COLUMN_AUTO_RESUME_ENABLED + " INTEGER DEFAULT 1, " +
                COLUMN_CRASH_RECOVERY_DATA + " TEXT" +
                ");";

        // Criar tabela de source_urls
        String createSourceUrlsTable = "CREATE TABLE " + TABLE_SOURCE_URLS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FK_DOWNLOAD_PATH + " TEXT NOT NULL, " +
                COLUMN_SOURCE_URL + " TEXT NOT NULL, " +
                COLUMN_URL_ORDER + " INTEGER NOT NULL, " +
                "FOREIGN KEY(" + COLUMN_FK_DOWNLOAD_PATH + ") REFERENCES " + TABLE_DOWNLOADS + "(" + COLUMN_FILE_PATH + ") ON DELETE CASCADE" +
                ");";

        // Criar índice para FK
        String createSourceUrlsIndex = "CREATE INDEX idx_source_urls_path ON " + TABLE_SOURCE_URLS + " (" + COLUMN_FK_DOWNLOAD_PATH + ");";

        db.execSQL(createDownloadsTable);
        db.execSQL(createSourceUrlsTable);
        db.execSQL(createSourceUrlsIndex);

        Log.d(TAG, "Banco de dados criado com sucesso");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Atualizando banco de dados da versão " + oldVersion + " para " + newVersion);
        if (oldVersion < 2) {
            // Migração da v1 para v2
            Log.d(TAG, "Executando migração para v2...");
            try {
                db.beginTransaction();

                // 1. Renomear tabela antiga
                db.execSQL("ALTER TABLE " + TABLE_DOWNLOADS + " RENAME TO downloads_old_v1;");

                // 2. Criar novas tabelas (schema v2)
                onCreate(db); // Chama a criação das tabelas v2

                // 3. Copiar dados da tabela antiga para a nova, adaptando colunas
                // (COLUMN_URL v1 -> COLUMN_SOURCE_URL v2, COLUMN_URL v2 -> ativa)
                // (Adicionar novas colunas com valores default ou nulos)
                db.execSQL("INSERT INTO " + TABLE_DOWNLOADS + " (" +
                           COLUMN_FILE_NAME + ", " + COLUMN_URL + ", " + COLUMN_FILE_PATH + ", " +
                           COLUMN_FILE_SIZE + ", " + COLUMN_DOWNLOADED_SIZE + ", " + COLUMN_PROGRESS + ", " +
                           COLUMN_STATUS + ", " + COLUMN_START_TIME + ", " + COLUMN_END_TIME + ", " +
                           COLUMN_SPEED + ", " + COLUMN_ESTIMATED_TIME + ", " + COLUMN_ERROR_MESSAGE + ", " +
                           COLUMN_LAST_PAUSE_TIME + ", " + COLUMN_LAST_RESUME_TIME + ", " + COLUMN_RESUME_ATTEMPTS + ", " +
                           COLUMN_MIME_TYPE + ", " + COLUMN_IS_QUEUED + ", " +
                           COLUMN_COOKIES + ", " + COLUMN_CUSTOM_HEADERS + ", " + COLUMN_CURRENT_URL_INDEX +
                           ") SELECT " +
                           COLUMN_FILE_NAME + ", " + COLUMN_URL + ", " + COLUMN_FILE_PATH + ", " +
                           COLUMN_FILE_SIZE + ", " + COLUMN_DOWNLOADED_SIZE + ", " + COLUMN_PROGRESS + ", " +
                           COLUMN_STATUS + ", " + COLUMN_START_TIME + ", " + COLUMN_END_TIME + ", " +
                           COLUMN_SPEED + ", " + COLUMN_ESTIMATED_TIME + ", " + COLUMN_ERROR_MESSAGE + ", " +
                           COLUMN_LAST_PAUSE_TIME + ", " + COLUMN_LAST_RESUME_TIME + ", " + COLUMN_RESUME_ATTEMPTS + ", " +
                           COLUMN_MIME_TYPE + ", " + COLUMN_IS_QUEUED + ", " +
                           "NULL, NULL, 0 " + // cookies, headers, current_url_index
                           "FROM downloads_old_v1;");

                // 4. Popular a tabela source_urls com a URL antiga
                db.execSQL("INSERT INTO " + TABLE_SOURCE_URLS + " (" +
                           COLUMN_FK_DOWNLOAD_PATH + ", " + COLUMN_SOURCE_URL + ", " + COLUMN_URL_ORDER +
                           ") SELECT " +
                           COLUMN_FILE_PATH + ", " + COLUMN_URL + ", 0 " +
                           "FROM downloads_old_v1;");

                // 5. Remover tabela antiga
                db.execSQL("DROP TABLE downloads_old_v1;");

                db.setTransactionSuccessful();
                Log.d(TAG, "Migração para v2 concluída com sucesso.");
            } catch (Exception e) {
                Log.e(TAG, "Erro durante a migração para v2: " + e.getMessage(), e);
                // Em caso de erro, reverter pode ser complexo. Uma abordagem simples é dropar tudo e recriar.
                Log.e(TAG, "Falha na migração. Recriando o banco do zero.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SOURCE_URLS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
                db.execSQL("DROP TABLE IF EXISTS downloads_old_v1;");
                onCreate(db);
            } finally {
                db.endTransaction();
            }
        }
        
        // Migração da v2 para v3 - Adicionar colunas de persistência melhorada
        if (oldVersion < 3) {
            Log.d(TAG, "Executando migração para v3...");
            try {
                db.beginTransaction();
                
                // Adicionar novas colunas à tabela existente
                db.execSQL("ALTER TABLE " + TABLE_DOWNLOADS + " ADD COLUMN " + COLUMN_LAST_PERSIST_TIME + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_DOWNLOADS + " ADD COLUMN " + COLUMN_PERSIST_COUNT + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_DOWNLOADS + " ADD COLUMN " + COLUMN_AUTO_RESUME_ENABLED + " INTEGER DEFAULT 1;");
                db.execSQL("ALTER TABLE " + TABLE_DOWNLOADS + " ADD COLUMN " + COLUMN_CRASH_RECOVERY_DATA + " TEXT;");
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Migração para v3 concluída com sucesso.");
            } catch (Exception e) {
                Log.e(TAG, "Erro durante migração para v3: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        }
        
        // Adicionar mais blocos `if (oldVersion < X)` para futuras migrações
    }

    /**
     * Adiciona ou atualiza um download no banco de dados (usando filePath como chave)
     * @param downloadInfo Informações do download
     * @return ID interno do banco de dados, -1 se falhar
     */
    public long addOrUpdateDownload(DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) {
            Log.e(TAG, "Não é possível adicionar/atualizar downloadInfo null ou sem filePath");
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();
        long rowId = -1;

        try {
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(COLUMN_FILE_NAME, downloadInfo.getFileName());
            values.put(COLUMN_URL, downloadInfo.getUrl()); // URL ativa
            values.put(COLUMN_FILE_PATH, downloadInfo.getFilePath());
            values.put(COLUMN_FILE_SIZE, downloadInfo.getFileSize());
            values.put(COLUMN_DOWNLOADED_SIZE, downloadInfo.getDownloadedSize());
            values.put(COLUMN_PROGRESS, downloadInfo.getProgress());
            values.put(COLUMN_STATUS, downloadInfo.getStatus());
            values.put(COLUMN_START_TIME, downloadInfo.getStartTime());
            values.put(COLUMN_END_TIME, downloadInfo.getEndTime());
            // values.put(COLUMN_PARTS, downloadInfo.getParts());
            values.put(COLUMN_SPEED, downloadInfo.getSpeed());
            values.put(COLUMN_ESTIMATED_TIME, downloadInfo.getEstimatedTimeRemaining());
            values.put(COLUMN_ERROR_MESSAGE, downloadInfo.getErrorMessage());
            values.put(COLUMN_LAST_PAUSE_TIME, downloadInfo.getLastPauseTime());
            values.put(COLUMN_LAST_RESUME_TIME, downloadInfo.getLastResumeTime());
            values.put(COLUMN_RESUME_ATTEMPTS, downloadInfo.getResumeAttempts());
            values.put(COLUMN_MIME_TYPE, downloadInfo.getMimeType());
            values.put(COLUMN_IS_QUEUED, downloadInfo.getStatus() == DownloadInfo.STATUS_QUEUED ? 1 : 0);
            values.put(COLUMN_COOKIES, downloadInfo.getCookies());
            // Serializar headers (ex: JSON) - requer biblioteca como Gson ou org.json
            // values.put(COLUMN_CUSTOM_HEADERS, serializeHeaders(downloadInfo.getCustomHeaders()));
            values.put(COLUMN_CURRENT_URL_INDEX, downloadInfo.getCurrentUrlIndex());
            // Novas colunas para persistência melhorada
            values.put(COLUMN_LAST_PERSIST_TIME, System.currentTimeMillis());
            values.put(COLUMN_PERSIST_COUNT, downloadInfo.getPersistCount() + 1);
            values.put(COLUMN_AUTO_RESUME_ENABLED, downloadInfo.isAutoResumeEnabled() ? 1 : 0);
            values.put(COLUMN_CRASH_RECOVERY_DATA, downloadInfo.getCrashRecoveryData());

            // Usar INSERT OR REPLACE (ou INSERT com ON CONFLICT REPLACE) devido ao UNIQUE no filePath
            rowId = db.insertWithOnConflict(TABLE_DOWNLOADS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            if (rowId != -1) {
                // Atualizar source URLs
                // 1. Deletar URLs antigas para este filePath
                db.delete(TABLE_SOURCE_URLS, COLUMN_FK_DOWNLOAD_PATH + " = ?", new String[]{downloadInfo.getFilePath()});
                // 2. Inserir novas URLs
                if (downloadInfo.getSourceUrls() != null) {
                    int order = 0;
                    for (String sourceUrl : downloadInfo.getSourceUrls()) {
                        ContentValues urlValues = new ContentValues();
                        urlValues.put(COLUMN_FK_DOWNLOAD_PATH, downloadInfo.getFilePath());
                        urlValues.put(COLUMN_SOURCE_URL, sourceUrl);
                        urlValues.put(COLUMN_URL_ORDER, order++);
                        db.insert(TABLE_SOURCE_URLS, null, urlValues);
                    }
                }
                Log.d(TAG, "Download adicionado/atualizado para filePath: " + downloadInfo.getFilePath() + " (ID: " + rowId + ")");
            } else {
                Log.e(TAG, "Falha ao adicionar/atualizar download para filePath: " + downloadInfo.getFilePath());
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro DB em addOrUpdateDownload: " + e.getMessage(), e);
            rowId = -1; // Indicar falha
        } finally {
            db.endTransaction();
        }

        return rowId;
    }

    /**
     * Adiciona um download à fila (apenas atualiza o flag no DB)
     * @param downloadInfo Informações do download
     * @return true se atualizado com sucesso, false caso contrário
     */
    public boolean addToQueue(DownloadInfo downloadInfo) {
        return updateQueueStatus(downloadInfo, true);
    }

    /**
     * Remove um download da fila (apenas atualiza o flag no DB)
     * @param downloadInfo Informações do download
     * @return true se atualizado com sucesso, false caso contrário
     */
    public boolean removeFromQueue(DownloadInfo downloadInfo) {
        return updateQueueStatus(downloadInfo, false);
    }

    private boolean updateQueueStatus(DownloadInfo downloadInfo, boolean isQueued) {
        if (downloadInfo == null || downloadInfo.getFilePath() == null) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;

        try {
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_QUEUED, isQueued ? 1 : 0);
            // Opcionalmente, atualizar status se estiver sendo adicionado à fila
            if (isQueued) {
                 values.put(COLUMN_STATUS, DownloadInfo.STATUS_QUEUED);
            }

            int rowsAffected = db.update(
                    TABLE_DOWNLOADS,
                    values,
                    COLUMN_FILE_PATH + " = ?",
                    new String[]{downloadInfo.getFilePath()});

            success = rowsAffected > 0;

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar status da fila: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }

        return success;
    }

    /**
     * Remove um download do banco de dados usando o filePath.
     * @param filePath Caminho do arquivo (URI string) do download.
     * @return true se removido com sucesso, false caso contrário.
     */
    public boolean deleteDownload(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;

        try {
            db.beginTransaction();

            // A exclusão em cascata removerá as entradas em TABLE_SOURCE_URLS
            int rowsAffected = db.delete(
                    TABLE_DOWNLOADS,
                    COLUMN_FILE_PATH + " = ?",
                    new String[]{filePath});

            success = rowsAffected > 0;
            if (success) {
                 Log.d(TAG, "Download removido do DB para filePath: " + filePath);
            } else {
                 Log.w(TAG, "Nenhum download encontrado no DB para filePath: " + filePath);
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao remover download por filePath: " + e.getMessage(), e);
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
        return getDownloadsByStatus(new int[]{
                DownloadInfo.STATUS_PENDING,
                DownloadInfo.STATUS_RUNNING,
                DownloadInfo.STATUS_PAUSED,
                DownloadInfo.STATUS_QUEUED,
                DownloadInfo.STATUS_RESUMING
        }, COLUMN_START_TIME + " DESC");
    }

    /**
     * Obtém todos os downloads concluídos com sucesso
     * @return Lista de downloads concluídos
     */
    public List<DownloadInfo> getCompletedDownloads() {
        return getDownloadsByStatus(new int[]{DownloadInfo.STATUS_COMPLETED}, COLUMN_END_TIME + " DESC");
    }

    /**
     * Obtém todos os downloads na fila
     * @return Lista de downloads na fila
     */
    public List<DownloadInfo> getQueuedDownloads() {
        return getDownloadsByStatus(new int[]{DownloadInfo.STATUS_QUEUED}, COLUMN_START_TIME + " ASC");
    }

    private List<DownloadInfo> getDownloadsByStatus(int[] statuses, String orderBy) {
        List<DownloadInfo> downloads = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;

        if (statuses == null || statuses.length == 0) {
            return downloads;
        }

        try {
            StringBuilder selection = new StringBuilder(COLUMN_STATUS + " IN (");
            String[] selectionArgs = new String[statuses.length];
            for (int i = 0; i < statuses.length; i++) {
                selection.append("?");
                selectionArgs[i] = String.valueOf(statuses[i]);
                if (i < statuses.length - 1) {
                    selection.append(",");
                }
            }
            selection.append(")");

            cursor = db.query(
                    TABLE_DOWNLOADS,
                    null, // Todas as colunas
                    selection.toString(),
                    selectionArgs,
                    null, null, orderBy);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    DownloadInfo downloadInfo = cursorToDownloadInfo(cursor);
                    // Carregar source URLs para este download
                    downloadInfo.setSourceUrls(getSourceUrls(db, downloadInfo.getFilePath()));
                    downloads.add(downloadInfo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter downloads por status: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return downloads;
    }

    private List<String> getSourceUrls(SQLiteDatabase db, String filePath) {
        List<String> urls = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_SOURCE_URLS,
                    new String[]{COLUMN_SOURCE_URL},
                    COLUMN_FK_DOWNLOAD_PATH + " = ?",
                    new String[]{filePath},
                    null, null, COLUMN_URL_ORDER + " ASC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    urls.add(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_URL)));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter source URLs para " + filePath + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return urls;
    }

    /**
     * Converte um cursor para um objeto DownloadInfo
     * @param cursor Cursor posicionado na linha desejada
     * @return Objeto DownloadInfo populado
     */
    private DownloadInfo cursorToDownloadInfo(Cursor cursor) {
        String fileName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME));
        // Source URLs são carregadas separadamente
        DownloadInfo downloadInfo = new DownloadInfo(fileName, new ArrayList<>());

        downloadInfo.setUrl(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)));
        downloadInfo.setFilePath(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_PATH)));
        downloadInfo.setFileSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FILE_SIZE)));
        downloadInfo.setDownloadedSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOADED_SIZE)));
        downloadInfo.setProgress(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROGRESS)));
        downloadInfo.setStatus(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS)));
        downloadInfo.setStartTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)));
        downloadInfo.setEndTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)));
        // downloadInfo.setParts(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PARTS)));
        downloadInfo.setSpeed(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SPEED)));
        downloadInfo.setEstimatedTimeRemaining(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ESTIMATED_TIME)));
        downloadInfo.setErrorMessage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ERROR_MESSAGE)));
        downloadInfo.setLastPauseTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_PAUSE_TIME)));
        downloadInfo.setLastResumeTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_RESUME_TIME)));
        downloadInfo.setResumeAttempts(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RESUME_ATTEMPTS)));
        downloadInfo.setMimeType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME_TYPE)));
        downloadInfo.setCookies(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COOKIES)));
        // Deserializar headers
        // downloadInfo.setCustomHeaders(deserializeHeaders(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CUSTOM_HEADERS))));
        downloadInfo.setCurrentUrlIndex(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_URL_INDEX)));
        // Novas colunas para persistência melhorada
        downloadInfo.setLastPersistTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_PERSIST_TIME)));
        downloadInfo.setPersistCount(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PERSIST_COUNT)));
        downloadInfo.setAutoResumeEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AUTO_RESUME_ENABLED)) == 1);
        downloadInfo.setCrashRecoveryData(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CRASH_RECOVERY_DATA)));

        return downloadInfo;
    }

    /**
     * Cria um backup do banco de dados atual
     * @return true se o backup foi criado com sucesso
     */
    public boolean createBackup() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            String dbPath = db.getPath();
            String backupPath = dbPath.replace(DATABASE_NAME, BACKUP_DATABASE_NAME);
            
            java.io.File sourceFile = new java.io.File(dbPath);
            java.io.File backupFile = new java.io.File(backupPath);
            
            if (sourceFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(backupFile);
                
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                
                fos.flush();
                fos.close();
                fis.close();
                
                Log.d(TAG, "Backup criado com sucesso: " + backupPath);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao criar backup: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Restaura o banco de dados a partir do backup
     * @return true se a restauração foi bem-sucedida
     */
    public boolean restoreFromBackup() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            String dbPath = db.getPath();
            String backupPath = dbPath.replace(DATABASE_NAME, BACKUP_DATABASE_NAME);
            
            java.io.File backupFile = new java.io.File(backupPath);
            java.io.File dbFile = new java.io.File(dbPath);
            
            if (backupFile.exists()) {
                db.close(); // Fechar conexão atual
                
                java.io.FileInputStream fis = new java.io.FileInputStream(backupFile);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(dbFile);
                
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                
                fos.flush();
                fos.close();
                fis.close();
                
                Log.d(TAG, "Banco restaurado do backup com sucesso");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao restaurar do backup: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Marca downloads como sendo processados para detecção de crash
     * @param filePath Caminho do arquivo do download
     * @param recoveryData Dados de recuperação (JSON com estado atual)
     */
    public void markDownloadAsProcessing(String filePath, String recoveryData) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_CRASH_RECOVERY_DATA, recoveryData);
            values.put(COLUMN_LAST_PERSIST_TIME, System.currentTimeMillis());
            
            db.update(TABLE_DOWNLOADS, values, COLUMN_FILE_PATH + " = ?", new String[]{filePath});
            Log.d(TAG, "Download marcado como processando: " + filePath);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao marcar download como processando: " + e.getMessage(), e);
        }
    }
    
    /**
     * Limpa dados de recuperação de crash após conclusão bem-sucedida
     * @param filePath Caminho do arquivo do download
     */
    public void clearCrashRecoveryData(String filePath) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.putNull(COLUMN_CRASH_RECOVERY_DATA);
            
            db.update(TABLE_DOWNLOADS, values, COLUMN_FILE_PATH + " = ?", new String[]{filePath});
            Log.d(TAG, "Dados de recuperação limpos para: " + filePath);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao limpar dados de recuperação: " + e.getMessage(), e);
        }
    }
    
    /**
     * Encontra downloads que possivelmente falharam devido a crash
     * @return Lista de downloads que precisam de recuperação
     */
    public List<DownloadInfo> getDownloadsNeedingRecovery() {
        List<DownloadInfo> downloads = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            cursor = db.query(
                TABLE_DOWNLOADS,
                null,
                COLUMN_CRASH_RECOVERY_DATA + " IS NOT NULL AND " +
                COLUMN_STATUS + " IN (?, ?, ?)",
                new String[]{
                    String.valueOf(DownloadInfo.STATUS_RUNNING),
                    String.valueOf(DownloadInfo.STATUS_RESUMING),
                    String.valueOf(DownloadInfo.STATUS_QUEUED)
                },
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    DownloadInfo downloadInfo = cursorToDownloadInfo(cursor);
                    downloadInfo.setSourceUrls(getSourceUrls(db, downloadInfo.getFilePath()));
                    downloads.add(downloadInfo);
                } while (cursor.moveToNext());
            }
            
            Log.d(TAG, "Encontrados " + downloads.size() + " downloads que precisam de recuperação");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar downloads para recuperação: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return downloads;
    }
    
    /**
     * Força persistência de todos os downloads ativos no banco
     */
    public void forceFullPersist(List<DownloadInfo> downloads) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            
            for (DownloadInfo download : downloads) {
                addOrUpdateDownload(download);
            }
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Persistência forçada de " + downloads.size() + " downloads");
        } catch (Exception e) {
            Log.e(TAG, "Erro na persistência forçada: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
    }

    // Métodos para serializar/deserializar headers (exemplo)
    /*
    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        // Usar Gson ou org.json
        // return new Gson().toJson(headers);
        return null; // Implementar
    }

    private Map<String, String> deserializeHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) {
            return new HashMap<>();
        }
        // Usar Gson ou org.json
        // Type type = new TypeToken<Map<String, String>>() {}.getType();
        // return new Gson().fromJson(headersJson, type);
        return new HashMap<>(); // Implementar
    }
    */
}

