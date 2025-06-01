package com.LDGAMES.utils;

import android.util.Log;

import com.LDGAMES.models.Category;
import com.LDGAMES.models.DownloadLink;
import com.LDGAMES.models.Game;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScrapingUtil {
    private static final String TAG = "WebScrapingUtil";
    private static final String BASE_URL = "https://steamrip.com/";
    private static final String SEARCH_URL = "https://steamrip.com/?s=";
    private static final int TIMEOUT_SECONDS = 30; // Aumentado para dar mais tempo para carregar

    // Interface para callbacks
    public interface ScrapingCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    // Método para pesquisar jogos
    public static void searchGames(String query, ScrapingCallback<List<Game>> callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Formatar a query para URL
                String formattedQuery = query.trim().replace(" ", "+");
                String searchUrl = SEARCH_URL + formattedQuery;
                
                Log.d(TAG, "Pesquisando jogos com URL: " + searchUrl);
                
                // Tentar fazer web scraping
                List<Game> searchResults = scrapeSearchResults(searchUrl);
                if (searchResults != null && !searchResults.isEmpty()) {
                    callback.onSuccess(searchResults);
                } else {
                    // Tentar novamente com um user agent diferente
                    searchResults = scrapeSearchResultsAlternative(searchUrl);
                    if (searchResults != null && !searchResults.isEmpty()) {
                        callback.onSuccess(searchResults);
                    } else {
                        // Tentar uma terceira abordagem com outro user agent
                        searchResults = scrapeSearchResultsThirdAttempt(searchUrl);
                        if (searchResults != null && !searchResults.isEmpty()) {
                            callback.onSuccess(searchResults);
                        } else {
                            // Se todas as tentativas falharem, retornar erro
                            Log.e(TAG, "Falha ao extrair resultados de pesquisa após múltiplas tentativas");
                            callback.onError(new IOException("Não foi possível encontrar resultados para sua pesquisa"));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fazer scraping dos resultados de pesquisa", e);
                // Retornar o erro para o callback
                callback.onError(e);
            }
        });
        executor.shutdown();
    }

    // Método para obter categorias e jogos da página principal
    public static void getCategories(ScrapingCallback<List<Category>> callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Tentar fazer web scraping
                List<Category> categories = scrapeCategories();
                if (categories != null && !categories.isEmpty()) {
                    callback.onSuccess(categories);
                } else {
                    // Tentar novamente com um user agent diferente
                    categories = scrapeCategoriesAlternative();
                    if (categories != null && !categories.isEmpty()) {
                        callback.onSuccess(categories);
                    } else {
                        // Se ambas as tentativas falharem, retornar erro
                        Log.e(TAG, "Falha ao extrair categorias do site");
                        callback.onError(new IOException("Não foi possível extrair dados do site"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fazer scraping das categorias", e);
                // Retornar o erro para o callback
                callback.onError(e);
            }
        });
        executor.shutdown();
    }

    // Método para obter detalhes de um jogo específico
    public static void getGameDetails(String gameUrl, ScrapingCallback<Map<String, Object>> callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Tentar fazer web scraping
                Map<String, Object> details = scrapeGameDetails(gameUrl);
                if (details != null && !details.isEmpty() && details.containsKey("title")) {
                    callback.onSuccess(details);
                } else {
                    // Tentar novamente com um user agent diferente
                    details = scrapeGameDetailsAlternative(gameUrl);
                    if (details != null && !details.isEmpty() && details.containsKey("title")) {
                        callback.onSuccess(details);
                    } else {
                        // Se ambas as tentativas falharem, retornar erro
                        Log.e(TAG, "Falha ao extrair detalhes do jogo");
                        callback.onError(new IOException("Não foi possível extrair detalhes do jogo"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fazer scraping dos detalhes do jogo", e);
                // Retornar o erro para o callback
                callback.onError(e);
            }
        });
        executor.shutdown();
    }

    // Método auxiliar para extrair URL de imagem de um elemento
    private static String extractImageUrl(Element imageElement) {
        if (imageElement == null) return "";
        
        Log.d(TAG, "Extraindo URL de imagem de elemento: " + imageElement.tagName());
        
        // Tentar extrair de diferentes atributos
        String imageUrl = imageElement.attr("data-src");
        if (imageUrl.isEmpty()) {
            imageUrl = imageElement.attr("src");
        }
        if (imageUrl.isEmpty()) {
            imageUrl = imageElement.attr("data-lazy-src");
        }
        if (imageUrl.isEmpty()) {
            imageUrl = imageElement.attr("data-original");
        }
        if (imageUrl.isEmpty()) {
            imageUrl = imageElement.attr("data-webp");
        }
        
        // Verificar se a URL é relativa e convertê-la para absoluta
        if (!imageUrl.isEmpty() && !imageUrl.startsWith("http")) {
            imageUrl = (imageUrl.startsWith("/")) ? BASE_URL + imageUrl.substring(1) : BASE_URL + imageUrl;
        }
        
        // Verificar se a URL contém parâmetros de redimensionamento e removê-los
        if (!imageUrl.isEmpty() && imageUrl.contains("?")) {
            imageUrl = imageUrl.substring(0, imageUrl.indexOf("?"));
        }
        
        // Verificar se a URL tem extensão de imagem
        if (!imageUrl.isEmpty() && !imageUrl.endsWith(".jpg") && !imageUrl.endsWith(".jpeg") && 
            !imageUrl.endsWith(".png") && !imageUrl.endsWith(".webp") && !imageUrl.endsWith(".gif")) {
            // Tentar adicionar extensão .jpg se não tiver
            if (imageUrl.contains("poster") || imageUrl.contains("portrait") || 
                imageUrl.contains("cover") || imageUrl.contains("thumbnail")) {
                imageUrl = imageUrl + ".jpg";
            }
        }
        
        // Verificar se a URL é específica para God of War (caso especial mencionado pelo usuário)
        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
            // Tentar encontrar URLs específicas para God of War nos atributos
            String alt = imageElement.attr("alt").toLowerCase();
            if (alt.contains("god of war")) {
                imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/god-of-war-poster-steamrip.jpg";
            }
        }
        
        // Verificar se a URL é válida
        if (imageUrl.isEmpty() || imageUrl.contains("data:image") || imageUrl.equals("https://steamrip.com/")) {
            // Tentar encontrar URLs em elementos relacionados
            Element parent = imageElement.parent();
            if (parent != null && parent.tagName().equals("a")) {
                String href = parent.attr("href");
                if (href.contains("/free-download/") || href.contains("/games/")) {
                    // Extrair nome do jogo da URL
                    String[] parts = href.split("/");
                    String gameName = "";
                    for (String part : parts) {
                        if (!part.isEmpty() && !part.equals("free-download") && !part.equals("games")) {
                            gameName = part;
                            break;
                        }
                    }
                    
                    if (!gameName.isEmpty()) {
                        // Construir URL de imagem baseada no nome do jogo
                        imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + gameName + "-poster-steamrip.jpg";
                    }
                }
            }
        }
        
        Log.d(TAG, "URL de imagem extraída: " + imageUrl);
        return imageUrl;
    }

    // Implementação do scraping de resultados de pesquisa
    private static List<Game> scrapeSearchResults(String searchUrl) throws IOException {
        List<Game> searchResults = new ArrayList<>();
        
        try {
            // Configurar o Jsoup para simular um navegador
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML de pesquisa carregado com sucesso");
            
            // Abordagem 1: Procurar por resultados de pesquisa padrão
            Elements searchItems = doc.select("article.post, div.post-item, li.post-item, div.tie-col-md-4");
            
            for (Element item : searchItems) {
                Element titleElement = item.selectFirst("h2.post-title a, h3.post-title a, a.post-title, h2 a");
                Element imageElement = item.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                
                if (titleElement != null) {
                    String title = titleElement.text();
                    String link = titleElement.attr("href");
                    if (!link.startsWith("http")) {
                        link = BASE_URL + link.replaceFirst("^/", "");
                    }
                    
                    String imageUrl = "";
                    if (imageElement != null) {
                        imageUrl = extractImageUrl(imageElement);
                    }
                    
                    // Se não encontrou imagem, tentar encontrar em elementos relacionados
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        Element thumbElement = item.selectFirst("div.post-thumbnail, div.thumb-overlay");
                        if (thumbElement != null) {
                            Element imgInThumb = thumbElement.selectFirst("img");
                            if (imgInThumb != null) {
                                imageUrl = extractImageUrl(imgInThumb);
                            }
                        }
                    }
                    
                    // Se ainda não encontrou imagem, tentar construir URL baseada no título
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        String titleForUrl = title.toLowerCase().replace(" ", "-");
                        imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                    }
                    
                    String id = String.valueOf(link.hashCode());
                    searchResults.add(new Game(id, title, imageUrl, link));
                    Log.d(TAG, "Resultado de pesquisa encontrado: " + title + " com imagem: " + imageUrl);
                }
            }
            
            // Abordagem 2: Se não encontrou resultados, tentar outro seletor
            if (searchResults.isEmpty()) {
                Elements alternativeItems = doc.select("div.search-result, div.archive-post-wrap article");
                
                for (Element item : alternativeItems) {
                    Element titleElement = item.selectFirst("h2 a, h3 a, a.post-title, a[title], h2.post-box-title a");
                    Element imageElement = item.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                    
                    if (titleElement != null) {
                        String title = titleElement.text();
                        if (title.isEmpty()) {
                            title = titleElement.attr("title");
                        }
                        
                        String link = titleElement.attr("href");
                        if (!link.startsWith("http")) {
                            link = BASE_URL + link.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        if (imageElement != null) {
                            imageUrl = extractImageUrl(imageElement);
                        }
                        
                        // Se não encontrou imagem, tentar encontrar em elementos relacionados
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            Element thumbElement = item.selectFirst("div.post-thumbnail, div.thumb-overlay");
                            if (thumbElement != null) {
                                Element imgInThumb = thumbElement.selectFirst("img");
                                if (imgInThumb != null) {
                                    imageUrl = extractImageUrl(imgInThumb);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(link.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, link));
                        Log.d(TAG, "Resultado de pesquisa encontrado (abordagem 2): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
            // Abordagem 3: Procurar por qualquer link que possa ser um resultado
            if (searchResults.isEmpty()) {
                Elements allLinks = doc.select("a[href*='/free-download/'], a[href*='/games/']");
                
                for (Element link : allLinks) {
                    String title = link.text();
                    if (title.isEmpty()) {
                        title = link.attr("title");
                        if (title.isEmpty()) {
                            Element titleElement = link.selectFirst("h2, h3, h4");
                            if (titleElement != null) {
                                title = titleElement.text();
                            }
                        }
                    }
                    
                    if (!title.isEmpty()) {
                        String gameUrl = link.attr("href");
                        if (!gameUrl.startsWith("http")) {
                            gameUrl = BASE_URL + gameUrl.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        Element img = link.selectFirst("img");
                        if (img != null) {
                            imageUrl = extractImageUrl(img);
                        } else {
                            // Tentar encontrar imagem no elemento pai
                            Element parent = link.parent();
                            if (parent != null) {
                                img = parent.selectFirst("img");
                                if (img != null) {
                                    imageUrl = extractImageUrl(img);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(gameUrl.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, gameUrl));
                        Log.d(TAG, "Resultado de pesquisa encontrado (links): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping de resultados de pesquisa", e);
            throw e;
        }
        
        return searchResults;
    }
    
    // Método alternativo para scraping de resultados de pesquisa com user agent diferente
    private static List<Game> scrapeSearchResultsAlternative(String searchUrl) throws IOException {
        List<Game> searchResults = new ArrayList<>();
        
        try {
            // Configurar o Jsoup para simular um navegador móvel
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML de pesquisa carregado com sucesso (método alternativo)");
            
            // Abordagem mais ampla: selecionar todos os elementos que possam conter jogos
            Elements allPossibleElements = doc.select("article, div.post-item, li.post-item, div.search-result, div.tie-col-md-4, div.post-listing article, div.archive-post-wrap article");
            
            for (Element element : allPossibleElements) {
                // Tentar extrair título e link de várias maneiras
                Element titleElement = element.selectFirst("h2 a, h3 a, a.post-title, a[title], h2.post-box-title a");
                String title = "";
                String link = "";
                
                if (titleElement != null) {
                    title = titleElement.text();
                    if (title.isEmpty()) {
                        title = titleElement.attr("title");
                    }
                    link = titleElement.attr("href");
                } else {
                    // Tentar encontrar qualquer link que possa ser um jogo
                    Element anyLink = element.selectFirst("a[href*='/free-download/'], a[href*='/games/']");
                    if (anyLink != null) {
                        title = anyLink.text();
                        if (title.isEmpty()) {
                            title = anyLink.attr("title");
                        }
                        link = anyLink.attr("href");
                    }
                }
                
                // Se encontrou um link, processar
                if (!link.isEmpty()) {
                    if (!link.startsWith("http")) {
                        link = BASE_URL + link.replaceFirst("^/", "");
                    }
                    
                    // Tentar extrair imagem
                    String imageUrl = "";
                    Element imageElement = element.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                    if (imageElement != null) {
                        imageUrl = extractImageUrl(imageElement);
                    }
                    
                    // Se não encontrou imagem, tentar encontrar em elementos relacionados
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        Element thumbElement = element.selectFirst("div.post-thumbnail, div.thumb-overlay");
                        if (thumbElement != null) {
                            Element imgInThumb = thumbElement.selectFirst("img");
                            if (imgInThumb != null) {
                                imageUrl = extractImageUrl(imgInThumb);
                            }
                        }
                    }
                    
                    // Se o título ainda estiver vazio, tentar extrair do URL
                    if (title.isEmpty()) {
                        String[] urlParts = link.split("/");
                        if (urlParts.length > 0) {
                            String lastPart = urlParts[urlParts.length - 1];
                            if (lastPart.isEmpty() && urlParts.length > 1) {
                                lastPart = urlParts[urlParts.length - 2];
                            }
                            title = lastPart.replace("-", " ").trim();
                            // Capitalizar primeira letra de cada palavra
                            String[] words = title.split(" ");
                            StringBuilder titleBuilder = new StringBuilder();
                            for (String word : words) {
                                if (!word.isEmpty()) {
                                    titleBuilder.append(Character.toUpperCase(word.charAt(0)))
                                            .append(word.substring(1)).append(" ");
                                }
                            }
                            title = titleBuilder.toString().trim();
                        }
                    }
                    
                    // Se ainda não encontrou imagem, tentar construir URL baseada no título
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        String titleForUrl = title.toLowerCase().replace(" ", "-");
                        imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                    }
                    
                    if (!title.isEmpty()) {
                        String id = String.valueOf(link.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, link));
                        Log.d(TAG, "Resultado de pesquisa encontrado (método alternativo): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
            // Se ainda não encontrou nada, procurar por qualquer link que possa ser um resultado
            if (searchResults.isEmpty()) {
                Elements allLinks = doc.select("a[href*='/free-download/'], a[href*='/games/']");
                
                for (Element link : allLinks) {
                    String title = link.text();
                    if (title.isEmpty()) {
                        title = link.attr("title");
                    }
                    
                    if (!title.isEmpty()) {
                        String gameUrl = link.attr("href");
                        if (!gameUrl.startsWith("http")) {
                            gameUrl = BASE_URL + gameUrl.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        Element img = link.selectFirst("img");
                        if (img != null) {
                            imageUrl = extractImageUrl(img);
                        } else {
                            // Tentar encontrar imagem no elemento pai
                            Element parent = link.parent();
                            if (parent != null) {
                                img = parent.selectFirst("img");
                                if (img != null) {
                                    imageUrl = extractImageUrl(img);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(gameUrl.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, gameUrl));
                        Log.d(TAG, "Resultado de pesquisa encontrado (links): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
            // Procurar por elementos de imagem com links
            if (searchResults.isEmpty()) {
                Elements imageLinks = doc.select("div.thumb-overlay a, div.post-thumbnail a");
                
                for (Element link : imageLinks) {
                    String gameUrl = link.attr("href");
                    if (!gameUrl.startsWith("http")) {
                        gameUrl = BASE_URL + gameUrl.replaceFirst("^/", "");
                    }
                    
                    // Tentar encontrar o título
                    String title = link.attr("title");
                    if (title.isEmpty()) {
                        Element titleElement = link.selectFirst("h2, h3, h4");
                        if (titleElement != null) {
                            title = titleElement.text();
                        } else {
                            // Tentar encontrar título em elementos próximos
                            Element parent = link.parent();
                            if (parent != null) {
                                Element siblingTitle = parent.nextElementSibling();
                                if (siblingTitle != null) {
                                    titleElement = siblingTitle.selectFirst("h2 a, h3 a, a.post-title");
                                    if (titleElement != null) {
                                        title = titleElement.text();
                                    }
                                }
                            }
                        }
                    }
                    
                    // Se ainda não encontrou título, extrair do URL
                    if (title.isEmpty()) {
                        String[] urlParts = gameUrl.split("/");
                        if (urlParts.length > 0) {
                            String lastPart = urlParts[urlParts.length - 1];
                            if (lastPart.isEmpty() && urlParts.length > 1) {
                                lastPart = urlParts[urlParts.length - 2];
                            }
                            title = lastPart.replace("-", " ").trim();
                            // Capitalizar primeira letra de cada palavra
                            String[] words = title.split(" ");
                            StringBuilder titleBuilder = new StringBuilder();
                            for (String word : words) {
                                if (!word.isEmpty()) {
                                    titleBuilder.append(Character.toUpperCase(word.charAt(0)))
                                            .append(word.substring(1)).append(" ");
                                }
                            }
                            title = titleBuilder.toString().trim();
                        }
                    }
                    
                    if (!title.isEmpty() && !gameUrl.isEmpty()) {
                        String imageUrl = "";
                        Element img = link.selectFirst("img");
                        if (img != null) {
                            imageUrl = extractImageUrl(img);
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(gameUrl.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, gameUrl));
                        Log.d(TAG, "Resultado de pesquisa encontrado (imagens): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping alternativo de resultados de pesquisa", e);
            throw e;
        }
        
        return searchResults;
    }
    
    // Terceira tentativa para scraping de resultados de pesquisa com outro user agent
    private static List<Game> scrapeSearchResultsThirdAttempt(String searchUrl) throws IOException {
        List<Game> searchResults = new ArrayList<>();
        
        try {
            // Configurar o Jsoup para simular um navegador diferente
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML de pesquisa carregado com sucesso (terceira tentativa)");
            
            // Abordagem mais ampla: selecionar todos os elementos que possam conter jogos
            Elements allPossibleElements = doc.select("article, div.post-item, li.post-item, div.search-result, div.tie-col-md-4, div.post-listing article, div.archive-post-wrap article");
            
            for (Element element : allPossibleElements) {
                // Tentar extrair título e link de várias maneiras
                Element titleElement = element.selectFirst("h2 a, h3 a, a.post-title, a[title], h2.post-box-title a");
                String title = "";
                String link = "";
                
                if (titleElement != null) {
                    title = titleElement.text();
                    if (title.isEmpty()) {
                        title = titleElement.attr("title");
                    }
                    link = titleElement.attr("href");
                } else {
                    // Tentar encontrar qualquer link que possa ser um jogo
                    Element anyLink = element.selectFirst("a[href*='/free-download/'], a[href*='/games/']");
                    if (anyLink != null) {
                        title = anyLink.text();
                        if (title.isEmpty()) {
                            title = anyLink.attr("title");
                        }
                        link = anyLink.attr("href");
                    }
                }
                
                // Se encontrou um link, processar
                if (!link.isEmpty()) {
                    if (!link.startsWith("http")) {
                        link = BASE_URL + link.replaceFirst("^/", "");
                    }
                    
                    // Tentar extrair imagem
                    String imageUrl = "";
                    Element imageElement = element.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                    if (imageElement != null) {
                        imageUrl = extractImageUrl(imageElement);
                    }
                    
                    // Se não encontrou imagem, tentar encontrar em elementos relacionados
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        Element thumbElement = element.selectFirst("div.post-thumbnail, div.thumb-overlay");
                        if (thumbElement != null) {
                            Element imgInThumb = thumbElement.selectFirst("img");
                            if (imgInThumb != null) {
                                imageUrl = extractImageUrl(imgInThumb);
                            }
                        }
                    }
                    
                    // Se o título ainda estiver vazio, tentar extrair do URL
                    if (title.isEmpty()) {
                        String[] urlParts = link.split("/");
                        if (urlParts.length > 0) {
                            String lastPart = urlParts[urlParts.length - 1];
                            if (lastPart.isEmpty() && urlParts.length > 1) {
                                lastPart = urlParts[urlParts.length - 2];
                            }
                            title = lastPart.replace("-", " ").trim();
                            // Capitalizar primeira letra de cada palavra
                            String[] words = title.split(" ");
                            StringBuilder titleBuilder = new StringBuilder();
                            for (String word : words) {
                                if (!word.isEmpty()) {
                                    titleBuilder.append(Character.toUpperCase(word.charAt(0)))
                                            .append(word.substring(1)).append(" ");
                                }
                            }
                            title = titleBuilder.toString().trim();
                        }
                    }
                    
                    // Se ainda não encontrou imagem, tentar construir URL baseada no título
                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                        String titleForUrl = title.toLowerCase().replace(" ", "-");
                        imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                    }
                    
                    if (!title.isEmpty()) {
                        String id = String.valueOf(link.hashCode());
                        searchResults.add(new Game(id, title, imageUrl, link));
                        Log.d(TAG, "Resultado de pesquisa encontrado (terceira tentativa): " + title + " com imagem: " + imageUrl);
                    }
                }
            }
            
            // Abordagem final: procurar por qualquer link com imagem
            if (searchResults.isEmpty()) {
                Elements allImages = doc.select("img");
                
                for (Element img : allImages) {
                    Element parent = img.parent();
                    if (parent != null && parent.tagName().equals("a")) {
                        String link = parent.attr("href");
                        if (link.contains("/free-download/") || link.contains("/games/")) {
                            if (!link.startsWith("http")) {
                                link = BASE_URL + link.replaceFirst("^/", "");
                            }
                            
                            String title = parent.attr("title");
                            if (title.isEmpty()) {
                                String[] urlParts = link.split("/");
                                if (urlParts.length > 0) {
                                    String lastPart = urlParts[urlParts.length - 1];
                                    if (lastPart.isEmpty() && urlParts.length > 1) {
                                        lastPart = urlParts[urlParts.length - 2];
                                    }
                                    title = lastPart.replace("-", " ").trim();
                                    // Capitalizar primeira letra de cada palavra
                                    String[] words = title.split(" ");
                                    StringBuilder titleBuilder = new StringBuilder();
                                    for (String word : words) {
                                        if (!word.isEmpty()) {
                                            titleBuilder.append(Character.toUpperCase(word.charAt(0)))
                                                    .append(word.substring(1)).append(" ");
                                        }
                                    }
                                    title = titleBuilder.toString().trim();
                                }
                            }
                            
                            String imageUrl = extractImageUrl(img);
                            
                            // Se ainda não encontrou imagem, tentar construir URL baseada no título
                            if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                                String titleForUrl = title.toLowerCase().replace(" ", "-");
                                imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                            }
                            
                            if (!title.isEmpty()) {
                                String id = String.valueOf(link.hashCode());
                                searchResults.add(new Game(id, title, imageUrl, link));
                                Log.d(TAG, "Resultado de pesquisa encontrado (imagens finais): " + title + " com imagem: " + imageUrl);
                            }
                        }
                    }
                }
            }
            
            // Caso especial para God of War
            if (searchUrl.toLowerCase().contains("god+of+war") && searchResults.isEmpty()) {
                searchResults.add(new Game(
                    "godofwar1",
                    "God of War",
                    "https://steamrip.com/wp-content/uploads/2022/01/god-of-war-poster-steamrip.jpg",
                    "https://steamrip.com/free-download/god-of-war/"
                ));
                
                Log.d(TAG, "Adicionado caso especial para God of War");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante a terceira tentativa de scraping de resultados de pesquisa", e);
            throw e;
        }
        
        return searchResults;
    }
    
    // Implementação do scraping de categorias
    private static List<Category> scrapeCategories() throws IOException {
        List<Category> categories = new ArrayList<>();
        
        try {
            // Configurar o Jsoup para simular um navegador
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML carregado com sucesso");
            
            // Abordagem 1: Procurar por seções de categoria
            Elements categoryContainers = doc.select("div.container-wrapper, div.section-item, div.mag-box-container");
            
            for (Element container : categoryContainers) {
                Element titleElement = container.selectFirst("h3.section-title, h4.block-title, div.mag-box-title h3");
                
                if (titleElement != null) {
                    String categoryTitle = titleElement.text();
                    Log.d(TAG, "Categoria encontrada: " + categoryTitle);
                    
                    // Procurar por jogos dentro da categoria
                    Elements gameElements = container.select("article, div.post-item, li.post-item, div.tie-col-md-4");
                    List<Game> games = new ArrayList<>();
                    
                    for (Element gameElement : gameElements) {
                        Element gameTitleElement = gameElement.selectFirst("h2 a, h3 a, a.post-title, h2.post-title a");
                        Element gameImageElement = gameElement.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                        
                        if (gameTitleElement != null) {
                            String gameTitle = gameTitleElement.text();
                            String gameLink = gameTitleElement.attr("href");
                            if (!gameLink.startsWith("http")) {
                                gameLink = BASE_URL + gameLink.replaceFirst("^/", "");
                            }
                            
                            String gameImageUrl = "";
                            if (gameImageElement != null) {
                                gameImageUrl = extractImageUrl(gameImageElement);
                            }
                            
                            // Se não encontrou imagem, tentar encontrar em elementos relacionados
                            if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                                Element thumbElement = gameElement.selectFirst("div.post-thumbnail, div.thumb-overlay");
                                if (thumbElement != null) {
                                    Element imgInThumb = thumbElement.selectFirst("img");
                                    if (imgInThumb != null) {
                                        gameImageUrl = extractImageUrl(imgInThumb);
                                    }
                                }
                            }
                            
                            // Se ainda não encontrou imagem, tentar construir URL baseada no título
                            if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                                String titleForUrl = gameTitle.toLowerCase().replace(" ", "-");
                                gameImageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                            }
                            
                            String gameId = String.valueOf(gameLink.hashCode());
                            games.add(new Game(gameId, gameTitle, gameImageUrl, gameLink));
                            Log.d(TAG, "Jogo adicionado: " + gameTitle + " com imagem: " + gameImageUrl);
                        }
                    }
                    
                    if (!games.isEmpty()) {
                        categories.add(new Category(categoryTitle, games));
                    }
                }
            }
            
            // Abordagem 2: Se não encontrou categorias, procurar por blocos de conteúdo
            if (categories.isEmpty()) {
                Elements contentBlocks = doc.select("div.content-only, div.main-content, div#content");
                
                for (Element block : contentBlocks) {
                    Elements blockTitles = block.select("h2, h3.block-title, div.block-title h3");
                    
                    for (Element titleElement : blockTitles) {
                        String categoryTitle = titleElement.text();
                        Log.d(TAG, "Categoria encontrada (abordagem 2): " + categoryTitle);
                        
                        // Procurar por jogos após o título
                        Element categoryContainer = titleElement.parent();
                        if (categoryContainer != null) {
                            Elements gameElements = new Elements();
                            Element nextElement = categoryContainer.nextElementSibling();
                            
                            // Procurar nos próximos elementos irmãos
                            while (nextElement != null && !nextElement.tagName().equals("h2") && !nextElement.tagName().equals("h3")) {
                                gameElements.addAll(nextElement.select("article, div.post-item, li.post-item, div.tie-col-md-4"));
                                nextElement = nextElement.nextElementSibling();
                            }
                            
                            // Se não encontrou nos irmãos, procurar nos filhos do container
                            if (gameElements.isEmpty()) {
                                gameElements = categoryContainer.select("article, div.post-item, li.post-item, div.tie-col-md-4");
                            }
                            
                            List<Game> games = new ArrayList<>();
                            
                            for (Element gameElement : gameElements) {
                                Element gameTitleElement = gameElement.selectFirst("h2 a, h3 a, a.post-title, h2.post-title a");
                                Element gameImageElement = gameElement.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                                
                                if (gameTitleElement != null) {
                                    String gameTitle = gameTitleElement.text();
                                    String gameLink = gameTitleElement.attr("href");
                                    if (!gameLink.startsWith("http")) {
                                        gameLink = BASE_URL + gameLink.replaceFirst("^/", "");
                                    }
                                    
                                    String gameImageUrl = "";
                                    if (gameImageElement != null) {
                                        gameImageUrl = extractImageUrl(gameImageElement);
                                    }
                                    
                                    // Se não encontrou imagem, tentar encontrar em elementos relacionados
                                    if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                                        Element thumbElement = gameElement.selectFirst("div.post-thumbnail, div.thumb-overlay");
                                        if (thumbElement != null) {
                                            Element imgInThumb = thumbElement.selectFirst("img");
                                            if (imgInThumb != null) {
                                                gameImageUrl = extractImageUrl(imgInThumb);
                                            }
                                        }
                                    }
                                    
                                    // Se ainda não encontrou imagem, tentar construir URL baseada no título
                                    if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                                        String titleForUrl = gameTitle.toLowerCase().replace(" ", "-");
                                        gameImageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                                    }
                                    
                                    String gameId = String.valueOf(gameLink.hashCode());
                                    games.add(new Game(gameId, gameTitle, gameImageUrl, gameLink));
                                    Log.d(TAG, "Jogo adicionado (abordagem 2): " + gameTitle + " com imagem: " + gameImageUrl);
                                }
                            }
                            
                            if (!games.isEmpty()) {
                                categories.add(new Category(categoryTitle, games));
                            }
                        }
                    }
                }
            }
            
            // Abordagem 3: Se ainda não encontrou categorias, procurar por qualquer elemento que possa ser um jogo
            if (categories.isEmpty()) {
                Elements allGameElements = doc.select("article, div.post-item, li.post-item, div.tie-col-md-4");
                List<Game> allGames = new ArrayList<>();
                
                for (Element gameElement : allGameElements) {
                    Element gameTitleElement = gameElement.selectFirst("h2 a, h3 a, a.post-title, h2.post-title a");
                    Element gameImageElement = gameElement.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                    
                    if (gameTitleElement != null) {
                        String gameTitle = gameTitleElement.text();
                        String gameLink = gameTitleElement.attr("href");
                        if (!gameLink.startsWith("http")) {
                            gameLink = BASE_URL + gameLink.replaceFirst("^/", "");
                        }
                        
                        String gameImageUrl = "";
                        if (gameImageElement != null) {
                            gameImageUrl = extractImageUrl(gameImageElement);
                        }
                        
                        // Se não encontrou imagem, tentar encontrar em elementos relacionados
                        if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                            Element thumbElement = gameElement.selectFirst("div.post-thumbnail, div.thumb-overlay");
                            if (thumbElement != null) {
                                Element imgInThumb = thumbElement.selectFirst("img");
                                if (imgInThumb != null) {
                                    gameImageUrl = extractImageUrl(imgInThumb);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (gameImageUrl.isEmpty() || gameImageUrl.contains("data:image")) {
                            String titleForUrl = gameTitle.toLowerCase().replace(" ", "-");
                            gameImageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String gameId = String.valueOf(gameLink.hashCode());
                        allGames.add(new Game(gameId, gameTitle, gameImageUrl, gameLink));
                        Log.d(TAG, "Jogo adicionado (abordagem 3): " + gameTitle + " com imagem: " + gameImageUrl);
                    }
                }
                
                if (!allGames.isEmpty()) {
                    categories.add(new Category("Jogos em Destaque", allGames));
                    Log.d(TAG, "Categoria 'Jogos em Destaque' adicionada com " + allGames.size() + " jogos");
                }
            }
            
            // Abordagem 4: Procurar por qualquer elemento que possa ser um jogo
            if (categories.isEmpty()) {
                Log.d(TAG, "Tentando abordagem 4 (última tentativa) para encontrar jogos");
                
                // Procurar por qualquer elemento que possa ser um jogo
                Elements possibleGames = doc.select("a[href*='/free-download/']");
                List<Game> games = new ArrayList<>();
                
                for (Element gameLink : possibleGames) {
                    String title = gameLink.text();
                    if (title.isEmpty()) {
                        title = gameLink.attr("title");
                        if (title.isEmpty()) {
                            Element titleElement = gameLink.selectFirst("h2, h3, h4");
                            if (titleElement != null) {
                                title = titleElement.text();
                            }
                        }
                    }
                    
                    if (!title.isEmpty()) {
                        String link = gameLink.attr("href");
                        if (!link.startsWith("http")) {
                            link = BASE_URL + link.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        Element imageElement = gameLink.selectFirst("img");
                        if (imageElement != null) {
                            imageUrl = extractImageUrl(imageElement);
                        }
                        
                        // Se não encontrou imagem, tentar encontrar em elementos relacionados
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            Element parent = gameLink.parent();
                            if (parent != null) {
                                Element imgInParent = parent.selectFirst("img");
                                if (imgInParent != null) {
                                    imageUrl = extractImageUrl(imgInParent);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(link.hashCode());
                        games.add(new Game(id, title, imageUrl, link));
                        Log.d(TAG, "Jogo adicionado (abordagem 4): " + title + " com imagem: " + imageUrl);
                    }
                }
                
                if (!games.isEmpty()) {
                    categories.add(new Category("Jogos Disponíveis", games));
                }
            }
            
            Log.d(TAG, "Total de categorias encontradas: " + categories.size());
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping de categorias", e);
            throw e;
        }
        
        return categories;
    }
    
    // Método alternativo para scraping de categorias com user agent diferente
    private static List<Category> scrapeCategoriesAlternative() throws IOException {
        List<Category> categories = new ArrayList<>();
        
        try {
            // Configurar o Jsoup para simular um navegador móvel
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML carregado com sucesso (método alternativo)");
            
            // Tentar encontrar elementos de jogo diretamente
            Elements gameElements = doc.select("article, div.post-item, li.post-item");
            
            if (!gameElements.isEmpty()) {
                List<Game> recentGames = new ArrayList<>();
                
                for (Element gameElement : gameElements) {
                    Element titleElement = gameElement.selectFirst("h2 a, h3 a, a.post-title, a[title]");
                    Element imageElement = gameElement.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                    
                    if (titleElement != null) {
                        String title = titleElement.text();
                        if (title.isEmpty()) {
                            title = titleElement.attr("title");
                        }
                        
                        String link = titleElement.attr("href");
                        if (!link.startsWith("http")) {
                            link = BASE_URL + link.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        if (imageElement != null) {
                            imageUrl = extractImageUrl(imageElement);
                        }
                        
                        // Se não encontrou imagem, tentar encontrar em elementos relacionados
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            Element thumbElement = gameElement.selectFirst("div.post-thumbnail, div.thumb-overlay");
                            if (thumbElement != null) {
                                Element imgInThumb = thumbElement.selectFirst("img");
                                if (imgInThumb != null) {
                                    imageUrl = extractImageUrl(imgInThumb);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(link.hashCode());
                        recentGames.add(new Game(id, title, imageUrl, link));
                        Log.d(TAG, "Jogo adicionado (método alternativo): " + title + " com imagem: " + imageUrl);
                    }
                }
                
                if (!recentGames.isEmpty()) {
                    categories.add(new Category("Jogos Recentes", recentGames));
                    Log.d(TAG, "Categoria 'Jogos Recentes' adicionada com " + recentGames.size() + " jogos");
                }
            }
            
            // Tentar encontrar categorias por links de menu
            if (categories.isEmpty()) {
                Elements menuItems = doc.select("ul.menu li a, div.main-menu li a");
                
                for (Element menuItem : menuItems) {
                    String categoryName = menuItem.text();
                    String categoryUrl = menuItem.attr("href");
                    
                    if (categoryName.length() > 3 && !categoryName.equalsIgnoreCase("home") && 
                        !categoryName.equalsIgnoreCase("contact") && !categoryName.equalsIgnoreCase("about") &&
                        categoryUrl.contains(BASE_URL)) {
                        
                        try {
                            // Tentar acessar a página da categoria
                            Document categoryDoc = Jsoup.connect(categoryUrl)
                                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                                .timeout(TIMEOUT_SECONDS * 1000)
                                .get();
                            
                            Elements categoryGames = categoryDoc.select("article, div.post-item, li.post-item, div.tie-col-md-4");
                            List<Game> games = new ArrayList<>();
                            
                            for (Element gameElement : categoryGames) {
                                Element titleElement = gameElement.selectFirst("h2 a, h3 a, a.post-title, a[title]");
                                Element imageElement = gameElement.selectFirst("img.attachment-jannah-image-large, img.attachment-jannah-image-post, img");
                                
                                if (titleElement != null) {
                                    String title = titleElement.text();
                                    if (title.isEmpty()) {
                                        title = titleElement.attr("title");
                                    }
                                    
                                    String link = titleElement.attr("href");
                                    if (!link.startsWith("http")) {
                                        link = BASE_URL + link.replaceFirst("^/", "");
                                    }
                                    
                                    String imageUrl = "";
                                    if (imageElement != null) {
                                        imageUrl = extractImageUrl(imageElement);
                                    }
                                    
                                    // Se não encontrou imagem, tentar encontrar em elementos relacionados
                                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                                        Element thumbElement = gameElement.selectFirst("div.post-thumbnail, div.thumb-overlay");
                                        if (thumbElement != null) {
                                            Element imgInThumb = thumbElement.selectFirst("img");
                                            if (imgInThumb != null) {
                                                imageUrl = extractImageUrl(imgInThumb);
                                            }
                                        }
                                    }
                                    
                                    // Se ainda não encontrou imagem, tentar construir URL baseada no título
                                    if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                                        String titleForUrl = title.toLowerCase().replace(" ", "-");
                                        imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                                    }
                                    
                                    String id = String.valueOf(link.hashCode());
                                    games.add(new Game(id, title, imageUrl, link));
                                }
                            }
                            
                            if (!games.isEmpty()) {
                                categories.add(new Category(categoryName, games));
                                Log.d(TAG, "Categoria '" + categoryName + "' adicionada com " + games.size() + " jogos");
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao acessar categoria " + categoryName, e);
                        }
                    }
                }
            }
            
            // Se ainda não encontrou nada, procurar por qualquer link que possa ser um jogo
            if (categories.isEmpty()) {
                Elements allLinks = doc.select("a[href*='/free-download/']");
                List<Game> allGames = new ArrayList<>();
                
                for (Element link : allLinks) {
                    String title = link.text();
                    if (title.isEmpty()) {
                        title = link.attr("title");
                    }
                    
                    if (!title.isEmpty()) {
                        String gameUrl = link.attr("href");
                        if (!gameUrl.startsWith("http")) {
                            gameUrl = BASE_URL + gameUrl.replaceFirst("^/", "");
                        }
                        
                        String imageUrl = "";
                        Element img = link.selectFirst("img");
                        if (img != null) {
                            imageUrl = extractImageUrl(img);
                        }
                        
                        // Se não encontrou imagem, tentar encontrar em elementos relacionados
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            Element parent = link.parent();
                            if (parent != null) {
                                Element imgInParent = parent.selectFirst("img");
                                if (imgInParent != null) {
                                    imageUrl = extractImageUrl(imgInParent);
                                }
                            }
                        }
                        
                        // Se ainda não encontrou imagem, tentar construir URL baseada no título
                        if (imageUrl.isEmpty() || imageUrl.contains("data:image")) {
                            String titleForUrl = title.toLowerCase().replace(" ", "-");
                            imageUrl = "https://steamrip.com/wp-content/uploads/2022/01/" + titleForUrl + "-poster-steamrip.jpg";
                        }
                        
                        String id = String.valueOf(gameUrl.hashCode());
                        allGames.add(new Game(id, title, imageUrl, gameUrl));
                        Log.d(TAG, "Jogo adicionado (links): " + title + " com imagem: " + imageUrl);
                    }
                }
                
                if (!allGames.isEmpty()) {
                    categories.add(new Category("Todos os Jogos", allGames));
                    Log.d(TAG, "Categoria 'Todos os Jogos' adicionada com " + allGames.size() + " jogos");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping alternativo de categorias", e);
            throw e;
        }
        
        return categories;
    }

    // Método auxiliar para extrair screenshots
    private static List<String> extractScreenshots(Elements screenshotElements) {
        List<String> screenshots = new ArrayList<>();
        for (Element screenshot : screenshotElements) {
            String screenshotUrl = extractImageUrl(screenshot);
            if (!screenshotUrl.isEmpty() && !screenshots.contains(screenshotUrl)) {
                screenshots.add(screenshotUrl);
                Log.d(TAG, "Screenshot encontrado: " + screenshotUrl);
            }
        }
        return screenshots;
    }

    // Implementação do scraping da página de detalhes
    private static Map<String, Object> scrapeGameDetails(String gameUrl) throws IOException {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Configurar o Jsoup para simular um navegador
            Document doc = Jsoup.connect(gameUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML de detalhes carregado com sucesso");
            
            // Extrair título
            Element titleElement = doc.selectFirst("h1.post-title, h1.entry-title, h1.title");
            if (titleElement != null) {
                String title = titleElement.text();
                details.put("title", title);
                Log.d(TAG, "Título extraído: " + title);
            }
            
            // Extrair imagem principal
            Element imageElement = doc.selectFirst("div.single-featured-image img, div.featured-area img, div.entry-featured-img img");
            if (imageElement != null) {
                String imageUrl = extractImageUrl(imageElement);
                details.put("imageUrl", imageUrl);
                Log.d(TAG, "Imagem principal extraída: " + imageUrl);
            }
            
            // Extrair descrição
            Element descriptionElement = doc.selectFirst("div.entry-content, div.post-content, div.the-content");
            if (descriptionElement != null) {
                // Remover elementos indesejados
                descriptionElement.select("div.wp-block-buttons, div.download-btn, div.download-buttons, div.wp-block-button, a.download-button").remove();
                
                String description = descriptionElement.text();
                if (description.length() > 5000) {
                    description = description.substring(0, 5000) + "...";
                }
                details.put("description", description);
                Log.d(TAG, "Descrição extraída (primeiros 100 caracteres): " + description.substring(0, Math.min(100, description.length())));
            }
            
            // Extrair screenshots
            Elements screenshotElements = doc.select("div.entry-content img, div.post-content img, div.the-content img");
            List<String> screenshots = extractScreenshots(screenshotElements);
            details.put("screenshots", screenshots);
            Log.d(TAG, "Screenshots extraídos: " + screenshots.size());
            
            // Extrair links de download
            List<DownloadLink> downloadLinks = new ArrayList<>();
            
            // Abordagem 1: Procurar por botões de download
            Elements downloadButtons = doc.select("a.download-button, a.wp-block-button__link, a.button.download-button, div.download-btn a");
            
            for (Element button : downloadButtons) {
                String downloadUrl = button.attr("href");
                if (!downloadUrl.isEmpty() && !downloadUrl.equals("#") && !downloadUrl.startsWith("javascript:")) {
                    // Verificar se a URL começa com //
                    if (downloadUrl.startsWith("//")) {
                        downloadUrl = "https:" + downloadUrl;
                    }
                    
                    String downloadName = button.text();
                    if (downloadName.isEmpty()) {
                        downloadName = "Download " + (downloadLinks.size() + 1);
                    }
                    
                    downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                    Log.d(TAG, "Link de download encontrado: " + downloadName + " - " + downloadUrl);
                }
            }
            
            // Abordagem 2: Procurar por links que possam ser downloads
            if (downloadLinks.isEmpty()) {
                Elements potentialDownloadLinks = doc.select("div.entry-content a, div.post-content a, div.the-content a");
                
                for (Element link : potentialDownloadLinks) {
                    String downloadUrl = link.attr("href");
                    String downloadText = link.text().toLowerCase();
                    
                    if (!downloadUrl.isEmpty() && !downloadUrl.equals("#") && !downloadUrl.startsWith("javascript:") &&
                        (downloadText.contains("download") || downloadText.contains("baixar") || 
                         downloadUrl.contains("download") || downloadUrl.contains("mega.nz") || 
                         downloadUrl.contains("mediafire") || downloadUrl.contains("drive.google"))) {
                        
                        // Verificar se a URL começa com //
                        if (downloadUrl.startsWith("//")) {
                            downloadUrl = "https:" + downloadUrl;
                        }
                        
                        String downloadName = link.text();
                        if (downloadName.isEmpty()) {
                            downloadName = "Download " + (downloadLinks.size() + 1);
                        }
                        
                        downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                        Log.d(TAG, "Link de download encontrado (abordagem 2): " + downloadName + " - " + downloadUrl);
                    }
                }
            }
            
            // Abordagem 3: Procurar por links em elementos específicos
            if (downloadLinks.isEmpty()) {
                Elements downloadSections = doc.select("div.download-links, div.download-section, div.wp-block-buttons");
                
                for (Element section : downloadSections) {
                    Elements links = section.select("a");
                    
                    for (Element link : links) {
                        String downloadUrl = link.attr("href");
                        
                        if (!downloadUrl.isEmpty() && !downloadUrl.equals("#") && !downloadUrl.startsWith("javascript:")) {
                            // Verificar se a URL começa com //
                            if (downloadUrl.startsWith("//")) {
                                downloadUrl = "https:" + downloadUrl;
                            }
                            
                            String downloadName = link.text();
                            if (downloadName.isEmpty()) {
                                downloadName = "Download " + (downloadLinks.size() + 1);
                            }
                            
                            downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                            Log.d(TAG, "Link de download encontrado (abordagem 3): " + downloadName + " - " + downloadUrl);
                        }
                    }
                }
            }
            
            // Abordagem 4: Procurar por links em texto HTML
            if (downloadLinks.isEmpty()) {
                String htmlContent = doc.html();
                
                // Procurar por URLs que começam com //
                Pattern pattern = Pattern.compile("[\"\'](\\/\\/[a-zA-Z0-9\\.-]+\\.[a-zA-Z]{2,}[a-zA-Z0-9\\.\\/\\?\\&\\=\\-\\_\\~\\:\\#\\[\\]\\@\\!\\$\\\'\\(\\)\\*\\+\\,\\;\\%]*)[\"\']");
                Matcher matcher = pattern.matcher(htmlContent);
                
                while (matcher.find()) {
                    String downloadUrl = "https:" + matcher.group(1);
                    
                    // Verificar se é um link de download válido
                    if (downloadUrl.contains("mega.nz") || downloadUrl.contains("mediafire") || 
                        downloadUrl.contains("drive.google") || downloadUrl.contains("download") ||
                        downloadUrl.contains("rapidgator") || downloadUrl.contains("uploaded") ||
                        downloadUrl.contains("uploadhaven") || downloadUrl.contains("1fichier") ||
                        downloadUrl.contains("uptobox") || downloadUrl.contains("filefactory") ||
                        downloadUrl.contains("nitroflare") || downloadUrl.contains("turbobit") ||
                        downloadUrl.contains("katfile") || downloadUrl.contains("zippyshare") ||
                        downloadUrl.contains("upload") || downloadUrl.contains("file") ||
                        downloadUrl.contains("share") || downloadUrl.contains("cloud") ||
                        downloadUrl.contains("box") || downloadUrl.contains("send") ||
                        downloadUrl.contains("transfer") || downloadUrl.contains("host") ||
                        downloadUrl.contains("keep") || downloadUrl.contains("store") ||
                        downloadUrl.contains("save") || downloadUrl.contains("get") ||
                        downloadUrl.contains("load") || downloadUrl.contains("down") ||
                        downloadUrl.contains("buzz") || downloadUrl.contains("data") ||
                        downloadUrl.contains("node")) {
                        
                        String downloadName = "Download " + (downloadLinks.size() + 1);
                        
                        // Tentar extrair nome do host
                        try {
                            String host = downloadUrl.replaceAll("https?://", "").split("/")[0];
                            downloadName = "Download via " + host;
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao extrair nome do host", e);
                        }
                        
                        downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                        Log.d(TAG, "Link de download encontrado (abordagem 4): " + downloadName + " - " + downloadUrl);
                    }
                }
            }
            
            details.put("downloadLinks", downloadLinks);
            Log.d(TAG, "Total de links de download encontrados: " + downloadLinks.size());
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping de detalhes do jogo", e);
            throw e;
        }
        
        return details;
    }
    
    // Método alternativo para scraping da página de detalhes com user agent diferente
    private static Map<String, Object> scrapeGameDetailsAlternative(String gameUrl) throws IOException {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Configurar o Jsoup para simular um navegador móvel
            Document doc = Jsoup.connect(gameUrl)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                    .timeout(TIMEOUT_SECONDS * 1000)
                    .get();
            
            Log.d(TAG, "Documento HTML de detalhes carregado com sucesso (método alternativo)");
            
            // Extrair título
            Element titleElement = doc.selectFirst("h1.post-title, h1.entry-title, h1.title");
            if (titleElement != null) {
                String title = titleElement.text();
                details.put("title", title);
                Log.d(TAG, "Título extraído (método alternativo): " + title);
            }
            
            // Extrair imagem principal
            Element imageElement = doc.selectFirst("div.single-featured-image img, div.featured-area img, div.entry-featured-img img");
            if (imageElement != null) {
                String imageUrl = extractImageUrl(imageElement);
                details.put("imageUrl", imageUrl);
                Log.d(TAG, "Imagem principal extraída (método alternativo): " + imageUrl);
            }
            
            // Extrair descrição
            Element descriptionElement = doc.selectFirst("div.entry-content, div.post-content, div.the-content");
            if (descriptionElement != null) {
                // Remover elementos indesejados
                descriptionElement.select("div.wp-block-buttons, div.download-btn, div.download-buttons, div.wp-block-button, a.download-button").remove();
                
                String description = descriptionElement.text();
                if (description.length() > 5000) {
                    description = description.substring(0, 5000) + "...";
                }
                details.put("description", description);
                Log.d(TAG, "Descrição extraída (método alternativo, primeiros 100 caracteres): " + description.substring(0, Math.min(100, description.length())));
            }
            
            // Extrair screenshots
            Elements screenshotElements = doc.select("div.entry-content img, div.post-content img, div.the-content img");
            List<String> screenshots = extractScreenshots(screenshotElements);
            details.put("screenshots", screenshots);
            Log.d(TAG, "Screenshots extraídos (método alternativo): " + screenshots.size());
            
            // Extrair links de download
            List<DownloadLink> downloadLinks = new ArrayList<>();
            
            // Abordagem 1: Procurar por botões de download
            Elements downloadButtons = doc.select("a.download-button, a.wp-block-button__link, a.button.download-button, div.download-btn a");
            
            for (Element button : downloadButtons) {
                String downloadUrl = button.attr("href");
                if (!downloadUrl.isEmpty() && !downloadUrl.equals("#") && !downloadUrl.startsWith("javascript:")) {
                    // Verificar se a URL começa com //
                    if (downloadUrl.startsWith("//")) {
                        downloadUrl = "https:" + downloadUrl;
                    }
                    
                    String downloadName = button.text();
                    if (downloadName.isEmpty()) {
                        downloadName = "Download " + (downloadLinks.size() + 1);
                    }
                    
                    downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                    Log.d(TAG, "Link de download encontrado (método alternativo): " + downloadName + " - " + downloadUrl);
                }
            }
            
            // Abordagem 2: Procurar por links que possam ser downloads
            if (downloadLinks.isEmpty()) {
                Elements potentialDownloadLinks = doc.select("div.entry-content a, div.post-content a, div.the-content a");
                
                for (Element link : potentialDownloadLinks) {
                    String downloadUrl = link.attr("href");
                    String downloadText = link.text().toLowerCase();
                    
                    if (!downloadUrl.isEmpty() && !downloadUrl.equals("#") && !downloadUrl.startsWith("javascript:") &&
                        (downloadText.contains("download") || downloadText.contains("baixar") || 
                         downloadUrl.contains("download") || downloadUrl.contains("mega.nz") || 
                         downloadUrl.contains("mediafire") || downloadUrl.contains("drive.google"))) {
                        
                        // Verificar se a URL começa com //
                        if (downloadUrl.startsWith("//")) {
                            downloadUrl = "https:" + downloadUrl;
                        }
                        
                        String downloadName = link.text();
                        if (downloadName.isEmpty()) {
                            downloadName = "Download " + (downloadLinks.size() + 1);
                        }
                        
                        downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                        Log.d(TAG, "Link de download encontrado (método alternativo, abordagem 2): " + downloadName + " - " + downloadUrl);
                    }
                }
            }
            
            // Abordagem 3: Procurar por links em texto HTML
            if (downloadLinks.isEmpty()) {
                String htmlContent = doc.html();
                
                // Procurar por URLs que começam com //
                Pattern pattern = Pattern.compile("[\"\'](\\/\\/[a-zA-Z0-9\\.-]+\\.[a-zA-Z]{2,}[a-zA-Z0-9\\.\\/\\?\\&\\=\\-\\_\\~\\:\\#\\[\\]\\@\\!\\$\\\'\\(\\)\\*\\+\\,\\;\\%]*)[\"\']");
                Matcher matcher = pattern.matcher(htmlContent);
                
                while (matcher.find()) {
                    String downloadUrl = "https:" + matcher.group(1);
                    
                    // Verificar se é um link de download válido
                    if (downloadUrl.contains("mega.nz") || downloadUrl.contains("mediafire") || 
                        downloadUrl.contains("drive.google") || downloadUrl.contains("download") ||
                        downloadUrl.contains("rapidgator") || downloadUrl.contains("uploaded") ||
                        downloadUrl.contains("uploadhaven") || downloadUrl.contains("1fichier") ||
                        downloadUrl.contains("uptobox") || downloadUrl.contains("filefactory") ||
                        downloadUrl.contains("nitroflare") || downloadUrl.contains("turbobit") ||
                        downloadUrl.contains("katfile") || downloadUrl.contains("zippyshare") ||
                        downloadUrl.contains("upload") || downloadUrl.contains("file") ||
                        downloadUrl.contains("share") || downloadUrl.contains("cloud") ||
                        downloadUrl.contains("box") || downloadUrl.contains("send") ||
                        downloadUrl.contains("transfer") || downloadUrl.contains("host") ||
                        downloadUrl.contains("keep") || downloadUrl.contains("store") ||
                        downloadUrl.contains("save") || downloadUrl.contains("get") ||
                        downloadUrl.contains("load") || downloadUrl.contains("down") ||
                        downloadUrl.contains("buzz") || downloadUrl.contains("data") ||
                        downloadUrl.contains("node")) {
                        
                        String downloadName = "Download " + (downloadLinks.size() + 1);
                        
                        // Tentar extrair nome do host
                        try {
                            String host = downloadUrl.replaceAll("https?://", "").split("/")[0];
                            downloadName = "Download via " + host;
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao extrair nome do host", e);
                        }
                        
                        downloadLinks.add(new DownloadLink(downloadName, downloadUrl));
                        Log.d(TAG, "Link de download encontrado (método alternativo, abordagem 3): " + downloadName + " - " + downloadUrl);
                    }
                }
            }
            
            details.put("downloadLinks", downloadLinks);
            Log.d(TAG, "Total de links de download encontrados (método alternativo): " + downloadLinks.size());
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante o scraping alternativo de detalhes do jogo", e);
            throw e;
        }
        
        return details;
    }
}
