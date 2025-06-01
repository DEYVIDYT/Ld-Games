package com.LDGAMES.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Cliente para a API do Datanodes para obtenção de links diretos de download
 */
public class DatanodesApiClient {
    private static final String TAG = "DatanodesApiClient";
    private static final String BASE_URL = "https://datanodes.to/api/v1/file/";
    private static final String API_KEY = "24738qjbzqb7mgnayjxjh"; // Key fornecida pelo usuário
    
    // Instância singleton
    private static DatanodesApiClient instance;
    
    // Contexto da aplicação
    private final Context context;
    
    /**
     * Interface para callback de operações da API
     */
    public interface DatanodesApiCallback {
        void onSuccess(String directDownloadUrl);
        void onError(Exception e);
    }
    
    /**
     * Construtor privado para implementação do padrão Singleton
     * @param context Contexto da aplicação
     */
    private DatanodesApiClient(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Obtém a instância do DatanodesApiClient
     * @param context Contexto da aplicação
     * @return Instância do DatanodesApiClient
     */
    public static synchronized DatanodesApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new DatanodesApiClient(context);
        }
        return instance;
    }
    
    /**
     * Obtém o link direto de download para um arquivo do Datanodes
     * @param datanodesUrl URL do Datanodes (ex: https://datanodes.to/ie7g8ysv6gyf/DRAGON_BALL_Sparking!_ZERO.7z)
     * @param callback Callback para receber o resultado
     */
    public void getDirectDownloadLink(String datanodesUrl, DatanodesApiCallback callback) {
        // Verificar se a URL é do Datanodes
        if (!isDatanodesUrl(datanodesUrl)) {
            callback.onError(new IllegalArgumentException("A URL fornecida não é do Datanodes"));
            return;
        }
        
        // Extrair o file_code da URL
        String fileCode = extractFileCode(datanodesUrl);
        if (fileCode == null || fileCode.isEmpty()) {
            callback.onError(new IllegalArgumentException("Não foi possível extrair o código do arquivo da URL"));
            return;
        }
        
        // Montar a URL da API
        String apiUrl = BASE_URL + fileCode + "/dl?key=" + API_KEY;
        
        // Executar a tarefa assíncrona
        new GetDirectLinkTask(callback).execute(apiUrl);
    }
    
    /**
     * Verifica se a URL é do Datanodes
     * @param url URL a ser verificada
     * @return true se a URL for do Datanodes, false caso contrário
     */
    public static boolean isDatanodesUrl(String url) {
        return url != null && url.contains("datanodes.to/");
    }
    
    /**
     * Extrai o código do arquivo da URL do Datanodes
     * @param url URL do Datanodes
     * @return Código do arquivo ou null se não encontrado
     */
    private String extractFileCode(String url) {
        try {
            // Exemplo: https://datanodes.to/ie7g8ysv6gyf/DRAGON_BALL_Sparking!_ZERO.7z
            // O código é "ie7g8ysv6gyf"
            String[] parts = url.split("datanodes.to/");
            if (parts.length > 1) {
                String path = parts[1];
                // Extrair o código (parte antes da primeira barra)
                if (path.contains("/")) {
                    return path.substring(0, path.indexOf("/"));
                } else {
                    return path;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao extrair código do arquivo", e);
        }
        return null;
    }
    
    /**
     * AsyncTask para fazer a requisição HTTP em background
     */
    private static class GetDirectLinkTask extends AsyncTask<String, Void, String> {
        private DatanodesApiCallback callback;
        private Exception error;
        
        public GetDirectLinkTask(DatanodesApiCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected String doInBackground(String... urls) {
            if (urls.length == 0) return null;
            
            String apiUrl = urls[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            
            try {
                // Configurar a conexão
                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                
                // Obter o código de resposta
                int responseCode = connection.getResponseCode();
                
                // Ler a resposta (independentemente do código de status)
                InputStream inputStream;
                if (responseCode >= 400) {
                    inputStream = connection.getErrorStream();
                } else {
                    inputStream = connection.getInputStream();
                }
                
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                String responseBody = response.toString();
                Log.d(TAG, "Response: " + responseBody);
                
                // Verificar se a resposta é HTML (erro)
                if (responseBody.startsWith("<") || responseBody.contains("<html")) {
                    throw new Exception("API retornou HTML em vez de JSON: verifique sua API key ou o código do arquivo");
                }
                
                // Tentar processar como JSON
                try {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    // Verificar o status
                    int status = jsonResponse.getInt("status");
                    if (status == 200) {
                        // Extrair a URL direta
                        return jsonResponse.getString("download_url");
                    } else {
                        // Erro retornado pela API
                        String message = jsonResponse.optString("message", "Erro desconhecido");
                        throw new Exception("Erro da API do Datanodes: " + message);
                    }
                } catch (JSONException e) {
                    throw new Exception("Erro ao processar resposta da API: " + e.getMessage() + ". Resposta: " + responseBody);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter link direto", e);
                error = e;
                return null;
            } finally {
                // Fechar conexões
                if (connection != null) {
                    connection.disconnect();
                }
                
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Erro ao fechar reader", e);
                    }
                }
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError(error != null ? error : new Exception("Erro desconhecido ao obter link direto"));
            }
        }
    }
}