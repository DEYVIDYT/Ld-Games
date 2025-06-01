package com.LDGAMES.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.LDGAMES.models.Game;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class IGDBApiClient {
    private static final String TAG = "IGDBApiClient";
    private static final String TWITCH_AUTH_URL = "https://id.twitch.tv/oauth2/token";
    private static final String IGDB_API_URL = "https://api.igdb.com/v4/";
    
    private static final String CLIENT_ID = "4b2quzgkf4zdf1oh9o6wtpp6fxqu6s";
    private static final String CLIENT_SECRET = "a4d6fakfc0g6b8ny8dbo5c6v5ajapy";
    
    private static final String PREF_NAME = "igdb_prefs";
    private static final String PREF_ACCESS_TOKEN = "access_token";
    private static final String PREF_TOKEN_EXPIRY = "token_expiry";
    
    private static IGDBApiClient instance;
    private final OkHttpClient client;
    private final Context context;
    
    // Interface para callbacks
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }
    
    private IGDBApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public static synchronized IGDBApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new IGDBApiClient(context);
        }
        return instance;
    }
    
    /**
     * Obtém um token de acesso da API Twitch para usar com a API IGDB
     */
    private void getAccessToken(final ApiCallback<String> callback) {
        // Verificar se já temos um token válido
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String accessToken = prefs.getString(PREF_ACCESS_TOKEN, null);
        long tokenExpiry = prefs.getLong(PREF_TOKEN_EXPIRY, 0);
        
        // Se o token ainda é válido, retorná-lo
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            callback.onSuccess(accessToken);
            return;
        }
        
        // Caso contrário, obter um novo token
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Construir a requisição para obter o token
                RequestBody body = RequestBody.create(
                        "client_id=" + CLIENT_ID + 
                        "&client_secret=" + CLIENT_SECRET + 
                        "&grant_type=client_credentials",
                        MediaType.parse("application/x-www-form-urlencoded"));
                
                Request request = new Request.Builder()
                        .url(TWITCH_AUTH_URL)
                        .post(body)
                        .build();
                
                // Executar a requisição
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful()) {
                    throw new IOException("Erro ao obter token: " + response.code() + " - " + response.body().string());
                }
                
                // Processar a resposta
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                
                // Extrair o token e sua validade
                String newToken = json.getString("access_token");
                int expiresIn = json.getInt("expires_in");
                
                // Calcular quando o token expira (em milissegundos)
                long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);
                
                // Salvar o token e sua validade
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_ACCESS_TOKEN, newToken);
                editor.putLong(PREF_TOKEN_EXPIRY, expiryTime);
                editor.apply();
                
                // Retornar o token via callback
                callback.onSuccess(newToken);
                
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter token de acesso", e);
                callback.onError(e);
            }
        });
        executor.shutdown();
    }
    
    /**
     * Executa uma consulta na API IGDB
     */
    private void executeQuery(String endpoint, String query, final ApiCallback<String> callback) {
        getAccessToken(new ApiCallback<String>() {
            @Override
            public void onSuccess(String token) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        // Construir a requisição para a API IGDB
                        RequestBody body = RequestBody.create(
                                query,
                                MediaType.parse("text/plain"));
                        
                        Request request = new Request.Builder()
                                .url(IGDB_API_URL + endpoint)
                                .post(body)
                                .header("Client-ID", CLIENT_ID)
                                .header("Authorization", "Bearer " + token)
                                .build();
                        
                        // Executar a requisição
                        Response response = client.newCall(request).execute();
                        String responseBody = response.body().string(); // Ler o corpo aqui para logar em caso de erro
                        
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "Erro na consulta IGDB (" + response.code() + ") para endpoint: " + endpoint + ", query: " + query + ", resposta: " + responseBody);
                            throw new IOException("Erro na consulta IGDB: " + response.code());
                        }
                        
                        // Processar a resposta
                        callback.onSuccess(responseBody);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao executar consulta IGDB para endpoint: " + endpoint + ", query: " + query, e);
                        callback.onError(e);
                    }
                });
                executor.shutdown();
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Busca jogos populares para exibir na tela inicial
     */
    public void getPopularGames(final ApiCallback<List<Game>> callback) {
        String query = "fields name,cover.url,summary,rating,first_release_date,url;" +
                       "where rating > 75 & cover != null;" +
                       "sort rating desc;" +
                       "limit 50;";
        
        executeQuery("games", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Game> games = parseGames(result);
                    callback.onSuccess(games);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar jogos populares", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Busca jogos recentes para exibir na tela inicial
     */
    public void getRecentGames(final ApiCallback<List<Game>> callback) {
        // Calcular timestamp de 3 meses atrás
        long threeMonthsAgo = System.currentTimeMillis() / 1000 - (90 * 24 * 60 * 60);
        
        String query = "fields name,cover.url,summary,rating,first_release_date,url;" +
                       "where first_release_date > " + threeMonthsAgo + " & cover != null;" +
                       "sort first_release_date desc;" +
                       "limit 50;";
        
        executeQuery("games", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Game> games = parseGames(result);
                    callback.onSuccess(games);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar jogos recentes", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Busca jogos por categoria/gênero
     */
    public void getGamesByGenre(int genreId, final ApiCallback<List<Game>> callback) {
        String query = "fields name,cover.url,summary,rating,first_release_date,url;" +
                       "where genres = " + genreId + " & cover != null;" +
                       "sort rating desc;" +
                       "limit 50;";
        
        executeQuery("games", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Game> games = parseGames(result);
                    callback.onSuccess(games);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar jogos por gênero", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Busca gêneros de jogos
     */
    public void getGenres(final ApiCallback<List<Map<String, Object>>> callback) {
        String query = "fields name,slug;" +
                       "limit 20;";
        
        executeQuery("genres", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Map<String, Object>> genres = new ArrayList<>();
                    JSONArray jsonArray = new JSONArray(result);
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject genreJson = jsonArray.getJSONObject(i);
                        Map<String, Object> genre = new HashMap<>();
                        
                        genre.put("id", genreJson.getInt("id"));
                        genre.put("name", genreJson.getString("name"));
                        genre.put("slug", genreJson.getString("slug"));
                        
                        genres.add(genre);
                    }
                    
                    callback.onSuccess(genres);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar gêneros", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Busca jogos por termo de pesquisa
     */
    public void searchGames(String searchTerm, final ApiCallback<List<Game>> callback) {
        String query = "search \"" + searchTerm + "\";" +
                       "fields name,cover.url,summary,rating,first_release_date,url;" +
                       "where cover != null;" +
                       "limit 50;";
        
        executeQuery("games", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Game> games = parseGames(result);
                    callback.onSuccess(games);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar resultados da pesquisa", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Obtém detalhes de um jogo específico
     */
    public void getGameDetails(int gameId, final ApiCallback<Map<String, Object>> callback) {
        String query = "fields name,cover.url,summary,storyline,rating,rating_count,first_release_date,url," +
                       "screenshots.url,videos.video_id,genres.name,platforms.name,involved_companies.company.name," +
                       "involved_companies.developer,involved_companies.publisher;" +
                       "where id = " + gameId + ";";
        
        executeQuery("games", query, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONArray jsonArray = new JSONArray(result);
                    
                    if (jsonArray.length() > 0) {
                        JSONObject gameJson = jsonArray.getJSONObject(0);
                        Map<String, Object> gameDetails = parseGameDetails(gameJson);
                        callback.onSuccess(gameDetails);
                    } else {
                        callback.onError(new Exception("Jogo não encontrado (ID: " + gameId + ")"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar detalhes do jogo (ID: " + gameId + ")", e);
                    callback.onError(e);
                }
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Processa a resposta JSON da API e converte para uma lista de objetos Game
     */
    private List<Game> parseGames(String jsonResponse) throws JSONException {
        List<Game> games = new ArrayList<>();
        JSONArray jsonArray = new JSONArray(jsonResponse);
        
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject gameJson = jsonArray.getJSONObject(i);
            
            String id = String.valueOf(gameJson.getInt("id"));
            String name = gameJson.optString("name", "Nome Desconhecido");
            
            // Processar URL da imagem de capa
            String imageUrl = "";
            if (gameJson.has("cover") && !gameJson.isNull("cover")) {
                JSONObject cover = gameJson.getJSONObject("cover");
                if (cover.has("url")) {
                    // Converter URL para tamanho maior e usar HTTPS
                    imageUrl = cover.getString("url").replace("t_thumb", "t_cover_big");
                    if (imageUrl.startsWith("//")) {
                        imageUrl = "https:" + imageUrl;
                    }
                } else {
                    Log.w(TAG, "Objeto 'cover' presente mas sem 'url' para o jogo ID: " + id);
                }
            } else {
                 Log.w(TAG, "Campo 'cover' ausente ou nulo para o jogo ID: " + id);
            }
            
            // Construir URL de detalhes (usaremos o ID do jogo)
            String detailUrl = "igdb://" + id;
            
            Game game = new Game(id, name, imageUrl, detailUrl);
            games.add(game);
        }
        
        return games;
    }
    
    /**
     * Processa os detalhes de um jogo a partir do JSON
     */
    private Map<String, Object> parseGameDetails(JSONObject gameJson) throws JSONException {
        Map<String, Object> details = new HashMap<>();
        int gameId = gameJson.optInt("id", -1);
        
        // Informações básicas
        details.put("id", gameId);
        details.put("title", gameJson.optString("name", "Título Desconhecido"));
        
        // Imagem de capa
        String imageUrl = null;
        if (gameJson.has("cover") && !gameJson.isNull("cover")) {
            JSONObject cover = gameJson.getJSONObject("cover");
            if (cover.has("url")) {
                imageUrl = cover.getString("url").replace("t_thumb", "t_cover_big");
                if (imageUrl.startsWith("//")) {
                    imageUrl = "https:" + imageUrl;
                }
                Log.d(TAG, "parseGameDetails - Imagem de capa encontrada para ID " + gameId + ": " + imageUrl);
                details.put("imageUrl", imageUrl);
            } else {
                 Log.w(TAG, "parseGameDetails - Objeto 'cover' presente mas sem 'url' para ID: " + gameId);
            }
        } else {
             Log.w(TAG, "parseGameDetails - Campo 'cover' ausente ou nulo para ID: " + gameId);
        }
        // Garantir que imageUrl nunca seja null no mapa se não for encontrado
        if (imageUrl == null) {
             details.put("imageUrl", ""); // Colocar string vazia se não encontrou
        }
        
        // Descrição
        String description = gameJson.optString("summary", null);
        if (description == null) {
            description = gameJson.optString("storyline", "Sem descrição disponível");
        }
        details.put("description", description);
        
        // Screenshots
        List<String> screenshots = new ArrayList<>();
        if (gameJson.has("screenshots") && !gameJson.isNull("screenshots")) {
            JSONArray screenshotsJson = gameJson.getJSONArray("screenshots");
            for (int i = 0; i < screenshotsJson.length(); i++) {
                JSONObject screenshot = screenshotsJson.getJSONObject(i);
                if (screenshot.has("url")) {
                    String screenshotUrl = screenshot.getString("url").replace("t_thumb", "t_screenshot_big");
                    if (screenshotUrl.startsWith("//")) {
                        screenshotUrl = "https:" + screenshotUrl;
                    }
                    screenshots.add(screenshotUrl);
                }
            }
        }
        details.put("screenshots", screenshots);
        
        // Gêneros
        List<String> genres = new ArrayList<>();
        if (gameJson.has("genres") && !gameJson.isNull("genres")) {
            JSONArray genresJson = gameJson.getJSONArray("genres");
            for (int i = 0; i < genresJson.length(); i++) {
                JSONObject genre = genresJson.getJSONObject(i);
                genres.add(genre.optString("name"));
            }
        }
        details.put("genres", genres);
        
        // Plataformas
        List<String> platforms = new ArrayList<>();
        if (gameJson.has("platforms") && !gameJson.isNull("platforms")) {
            JSONArray platformsJson = gameJson.getJSONArray("platforms");
            for (int i = 0; i < platformsJson.length(); i++) {
                JSONObject platform = platformsJson.getJSONObject(i);
                platforms.add(platform.optString("name"));
            }
        }
        details.put("platforms", platforms);
        
        // Data de lançamento
        if (gameJson.has("first_release_date") && !gameJson.isNull("first_release_date")) {
            long timestamp = gameJson.getLong("first_release_date");
            if (timestamp > 0) {
                java.util.Date date = new java.util.Date(timestamp * 1000);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                details.put("releaseDate", sdf.format(date));
            }
        }
        
        // Avaliação
        if (gameJson.has("rating") && !gameJson.isNull("rating")) {
            double rating = gameJson.getDouble("rating");
            details.put("rating", String.format("%.1f", rating / 10)); // Converter para escala de 0-10
        }
        
        return details;
    }
}

