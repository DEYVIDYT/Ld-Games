package com.LDGAMES.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.adapters.BrowserTabAdapter;
import com.LDGAMES.models.BrowserTab;
import com.LDGAMES.models.DownloadInfo;
import com.LDGAMES.services.DownloadService;
import com.LDGAMES.utils.AdBlocker;
import com.LDGAMES.utils.DownloadManager;
import com.LDGAMES.utils.DownloadMetadataResolver;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.FileUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class InternalBrowserActivity extends AppCompatActivity {

    private WebView currentWebView;
    private TextView tvUrl;
    private TextView tvTabCount;
    private MaterialButton btnBack;
    private MaterialButton btnForward;
    private MaterialButton btnRefresh;
    private MaterialButton btnTabs;
    private MaterialButton btnNewTab;
    private FrameLayout tabsContainer;

    private List<BrowserTab> tabs = new ArrayList<>();
    private BrowserTabAdapter tabAdapter;
    private String initialUrl;
    private boolean isRedirectDetected = false;
    private boolean isNewTabDetected = false;
    
    /**
     * Cria uma Intent para iniciar o navegador interno
     * @param context Contexto da aplicação
     * @param url URL a ser carregada
     * @param title Título da página (opcional)
     * @return Intent configurada
     */
    public static Intent createIntent(Context context, String url, String title) {
        Intent intent = new Intent(context, InternalBrowserActivity.class);
        intent.putExtra("url", url);
        if (title != null && !title.isEmpty()) {
            intent.putExtra("title", title);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aplicar Dynamic Color antes de inflar o layout
        DynamicThemeManager.getInstance().applyDynamicColors(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_browser);

        // Inicializar views
        tvUrl = findViewById(R.id.tv_url);
        tvTabCount = findViewById(R.id.tv_tab_count);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnTabs = findViewById(R.id.btn_tabs);
        btnNewTab = findViewById(R.id.btn_new_tab);
        tabsContainer = findViewById(R.id.tabs_container);

        // Obter URL inicial
        initialUrl = getIntent().getStringExtra("url");
        if (initialUrl == null || initialUrl.isEmpty()) {
            initialUrl = "https://www.google.com";
        }

        // Configurar listeners
        setupListeners();

        // Criar primeira guia
        createNewTab(initialUrl);

        // Atualizar contador de guias
        updateTabCount();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (currentWebView != null && currentWebView.canGoBack()) {
                currentWebView.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (currentWebView != null && currentWebView.canGoForward()) {
                currentWebView.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> {
            if (currentWebView != null) {
                currentWebView.reload();
            }
        });

        btnTabs.setOnClickListener(v -> showTabsDialog());

        btnNewTab.setOnClickListener(v -> createNewTab("https://www.google.com"));
    }

    private void showTabsDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_browser_tabs, null);
        
        RecyclerView rvTabs = view.findViewById(R.id.rv_tabs);
        rvTabs.setLayoutManager(new LinearLayoutManager(this));
        
        // Criar o diálogo antes de configurar o adapter para poder referenciá-lo
        final AlertDialog tabsDialog = builder.setView(view).create();
        
        tabAdapter = new BrowserTabAdapter(tabs, new BrowserTabAdapter.TabClickListener() {
            @Override
            public void onTabClick(int position) {
                switchToTab(position);
                tabsDialog.dismiss();
            }

            @Override
            public void onTabClose(int position) {
                closeTab(position);
                if (tabs.isEmpty()) {
                    tabsDialog.dismiss();
                    finish();
                } else {
                    tabAdapter.notifyDataSetChanged();
                }
            }
        });
        
        rvTabs.setAdapter(tabAdapter);
        
        MaterialButton btnNewTab = view.findViewById(R.id.btn_add_tab);
        btnNewTab.setOnClickListener(v -> {
            createNewTab("https://www.google.com");
            tabsDialog.dismiss();
        });
        
        tabsDialog.show();
    }

    private void createNewTab(String url) {
        // Criar nova guia
        BrowserTab tab = new BrowserTab();
        tab.setTitle("Nova guia");
        tab.setUrl(url);
        tab.setIndex(tabs.size() + 1);
        
        // Criar WebView para a guia
        WebView webView = new WebView(this);
        setupWebView(webView);
        
        // Configurar WebView
        tab.setWebView(webView);
        
        // Adicionar à lista de guias
        tabs.add(tab);
        
        // Carregar URL
        webView.loadUrl(url);
        
        // Atualizar contador de guias
        updateTabCount();
        
        // Exibir a nova guia
        switchToTab(tabs.size() - 1);
    }

    private void switchToTab(int position) {
        if (position < 0 || position >= tabs.size()) {
            return;
        }
        
        // Remover WebView atual
        tabsContainer.removeAllViews();
        
        // Adicionar WebView da guia selecionada
        currentWebView = tabs.get(position).getWebView();
        tabsContainer.addView(currentWebView);
        
        // Atualizar URL exibida
        tvUrl.setText(tabs.get(position).getUrl());
        
        // Atualizar estado dos botões
        updateNavigationButtons();
    }

    private void closeTab(int position) {
        if (position < 0 || position >= tabs.size()) {
            return;
        }
        
        // Remover guia
        tabs.remove(position);
        
        // Atualizar índices
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setIndex(i + 1);
        }
        
        // Atualizar contador de guias
        updateTabCount();
        
        // Se não houver mais guias, fechar atividade
        if (tabs.isEmpty()) {
            finish();
            return;
        }
        
        // Exibir última guia
        switchToTab(tabs.size() - 1);
    }

    private void updateTabCount() {
        tvTabCount.setText(String.valueOf(tabs.size()));
    }

    private void updateNavigationButtons() {
        if (currentWebView != null) {
            btnBack.setEnabled(currentWebView.canGoBack());
            btnForward.setEnabled(currentWebView.canGoForward());
        } else {
            btnBack.setEnabled(false);
            btnForward.setEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        private final InternalBrowserActivity activity;
        private String pendingUrl = null;
        
        public CustomWebViewClient(InternalBrowserActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // Bloquear anúncios
            WebResourceResponse response = AdBlocker.blockAds(request);
            return response != null ? response : super.shouldInterceptRequest(view, request);
        }
        
        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
        
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }
        
        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            // Verificar se é um redirecionamento
            if (!isRedirectDetected && !url.equals(view.getUrl())) {
                isRedirectDetected = true;
                pendingUrl = url;
                showRedirectConfirmationDialog(url, view);
                return true;
            }
            
            return false;
        }
        
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            
            // Atualizar URL exibida
            tvUrl.setText(url);
            
            // Atualizar título da guia
            for (BrowserTab tab : tabs) {
                if (tab.getWebView() == view) {
                    tab.setUrl(url);
                    break;
                }
            }
            
            // Resetar flags
            isRedirectDetected = false;
            isNewTabDetected = false;
            pendingUrl = null;
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            
            // Atualizar título da guia
            for (BrowserTab tab : tabs) {
                if (tab.getWebView() == view) {
                    tab.setTitle(view.getTitle() != null ? view.getTitle() : url);
                    break;
                }
            }
            
            // Atualizar estado dos botões
            updateNavigationButtons();
        }
    }

    private class CustomWebChromeClient extends WebChromeClient {
        private android.os.Message pendingMsg;
        private boolean isConfirmationShowing = false;
        
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
            // Guardar a mensagem para processar após a confirmação
            pendingMsg = resultMsg;
            
            // Se o usuário está interagindo (clicou em um link), mostrar confirmação
            if (isUserGesture && !isConfirmationShowing) {
                isConfirmationShowing = true;
                showNewTabConfirmationDialog();
                return true;
            } 
            // Se não é interação do usuário ou já estamos mostrando confirmação, cancelar
            else {
                return false;
            }
        }
        
        /**
         * Processa a criação de uma nova guia após a confirmação do usuário
         */
        private void processNewTabCreation() {
            if (pendingMsg == null) return;
            
            // Criar nova guia
            WebView newWebView = new WebView(InternalBrowserActivity.this);
            newWebView.getSettings().setJavaScriptEnabled(true);
            newWebView.getSettings().setDomStorageEnabled(true);
            newWebView.getSettings().setSupportMultipleWindows(true);
            newWebView.setWebViewClient(new CustomWebViewClient(InternalBrowserActivity.this));
            newWebView.setWebChromeClient(this);
            
            // Configurar transporte de mensagens
            WebView.WebViewTransport transport = (WebView.WebViewTransport) pendingMsg.obj;
            transport.setWebView(newWebView);
            pendingMsg.sendToTarget();
            pendingMsg = null;
            
            // Criar nova guia com o WebView
            BrowserTab tab = new BrowserTab();
            tab.setTitle("Nova guia");
            tab.setUrl("");
            tab.setIndex(tabs.size() + 1);
            tab.setWebView(newWebView);
            
            // Adicionar à lista de guias
            tabs.add(tab);
            
            // Atualizar contador de guias
            updateTabCount();
            
            // Exibir a nova guia
            switchToTab(tabs.size() - 1);
            
            // Resetar flag
            isConfirmationShowing = false;
        }
        
        /**
         * Cancela a criação de uma nova guia
         */
        private void cancelNewTabCreation() {
            pendingMsg = null;
            isConfirmationShowing = false;
        }
    }

    // Método de inicialização do WebView
    private void setupWebView(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        
        // Configurar WebViewClient e WebChromeClient
        webView.setWebViewClient(new CustomWebViewClient(this));
        webView.setWebChromeClient(new CustomWebChromeClient());
        
        // Configurar DownloadListener para capturar eventos de download
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // Este método é chamado quando o navegador detecta um download
            // baseado em headers HTTP e MIME type, sem depender de padrões de URL
            
            // Log para debug
            android.util.Log.d("DownloadListener", "URL: " + url);
            android.util.Log.d("DownloadListener", "Content-Disposition: " + contentDisposition);
            android.util.Log.d("DownloadListener", "MIME Type: " + mimetype);
            android.util.Log.d("DownloadListener", "Content Length: " + contentLength);
            
            // Extrair nome do arquivo do Content-Disposition ou da URL
            String fileName = FileUtils.getFileNameFromContentDisposition(contentDisposition);
            if (fileName == null || fileName.isEmpty()) {
                fileName = FileUtils.getFileNameFromUrl(url);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "download_" + System.currentTimeMillis() + FileUtils.getExtensionFromMimeType(mimetype);
                }
            }
            
            // Mostrar diálogo de confirmação de download
            showDownloadConfirmationDialog(url, fileName, mimetype, contentLength);
        });
    }
    
    private void showRedirectConfirmationDialog(String url, WebView view) {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de confirmação
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_redirect_confirmation, null);
        TextView tvRedirectUrl = dialogView.findViewById(R.id.tv_redirect_url);
        tvRedirectUrl.setText(url);
        
        builder.setView(dialogView)
               .setCancelable(false);
        
        // Criar e mostrar o diálogo
        AlertDialog dialog = builder.create();
        
        // Configurar manualmente os botões para evitar duplicação
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_redirect_cancel);
        MaterialButton btnContinue = dialogView.findViewById(R.id.btn_redirect_proceed);
        
        btnCancel.setOnClickListener(v -> {
            // Cancelar o redirecionamento
            isRedirectDetected = false;
            dialog.dismiss();
        });
        
        btnContinue.setOnClickListener(v -> {
            // Permitir o redirecionamento
            isRedirectDetected = false;
            view.loadUrl(url);
            dialog.dismiss();
        });
        
        // Mostrar o diálogo
        dialog.show();
    }
    
    private void showNewTabConfirmationDialog() {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de confirmação
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_tab_confirmation, null);
        TextView tvUrl = dialogView.findViewById(R.id.tv_new_tab_url);
        
        // Como ainda não temos a URL específica, mostrar informação genérica
        tvUrl.setText("Nova guia (URL ainda não disponível)");
        
        builder.setView(dialogView)
               .setCancelable(true);
        
        // Criar o diálogo
        AlertDialog dialog = builder.create();
        
        // Configurar manualmente os botões para evitar duplicação
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_new_tab_cancel);
        MaterialButton btnAllow = dialogView.findViewById(R.id.btn_new_tab_proceed);
        
        btnCancel.setOnClickListener(v -> {
            // Cancelar a abertura da nova guia
            CustomWebChromeClient client = (CustomWebChromeClient) currentWebView.getWebChromeClient();
            client.cancelNewTabCreation();
            dialog.dismiss();
        });
        
        btnAllow.setOnClickListener(v -> {
            // Permitir a abertura da nova guia
            CustomWebChromeClient client = (CustomWebChromeClient) currentWebView.getWebChromeClient();
            client.processNewTabCreation();
            dialog.dismiss();
        });
        
        // Ao cancelar o diálogo (tocando fora), também cancelar a abertura da guia
        dialog.setOnCancelListener(dialogInterface -> {
            CustomWebChromeClient client = (CustomWebChromeClient) currentWebView.getWebChromeClient();
            client.cancelNewTabCreation();
        });
        
        // Mostrar o diálogo
        dialog.show();
    }
    
    private void showDownloadConfirmationDialog(String url, String fileName, String mimeType, long contentLength) {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de confirmação
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_confirmation, null);
        TextView tvFileName = dialogView.findViewById(R.id.tv_file_name);
        TextView tvFileSize = dialogView.findViewById(R.id.tv_file_size);
        TextView tvFileUrl = dialogView.findViewById(R.id.tv_file_url);
        
        tvFileName.setText(fileName);
        tvFileUrl.setText(url);
        
        // Formatar tamanho do arquivo
        String formattedSize = "Desconhecido";
        if (contentLength > 0) {
            formattedSize = FileUtils.formatFileSize(contentLength);
        }
        tvFileSize.setText(formattedSize);
        
        builder.setView(dialogView)
               .setCancelable(true);
        
        // Criar o diálogo
        AlertDialog dialog = builder.create();
        
        // Configurar manualmente os botões para evitar duplicação
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnDownload = dialogView.findViewById(R.id.btn_download);
        
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
        });
        
        btnDownload.setOnClickListener(v -> {
            // Resolver metadados antes de iniciar o download
            DownloadMetadataResolver.resolveMetadata(this, url, new DownloadMetadataResolver.MetadataCallback() {
                @Override
                public void onMetadataResolved(String resolvedUrl, String resolvedMimeType, long resolvedContentLength) {
                    // Usar o nome do arquivo original, mas a URL e tipo MIME resolvidos
                    startDownload(resolvedUrl, fileName, resolvedMimeType);
                    
                    // Abrir a tela de progresso de download
                    Intent intent = new Intent(InternalBrowserActivity.this, DownloadProgressActivity.class);
                    startActivity(intent);
                }

                @Override
                public void onMetadataError(String errorMessage) {
                    // Informar erro ao usuário na thread principal
                    runOnUiThread(() -> {
                        // Log the detailed error for debugging
                        android.util.Log.e("DownloadMetadataResolver", "Error resolving metadata: " + errorMessage);
                        // Show a user-friendly message
                        Toast.makeText(InternalBrowserActivity.this, "Erro ao obter informações do download. Tente novamente.", Toast.LENGTH_LONG).show();
                    });
                }
            });
            
            dialog.dismiss();
        });
        
        // Mostrar o diálogo
        dialog.show();
    }
    
    private void startDownload(String url, String fileName, String mimeType) {
        // Obter cookies para a URL do WebView
        String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
        
        // Obter User-Agent do WebView atual
        String userAgent = null;
        if (currentWebView != null) {
            userAgent = currentWebView.getSettings().getUserAgentString();
        }
        
        // Criar DownloadInfo e popular com detalhes, incluindo cookies e headers
        DownloadInfo downloadInfo = new DownloadInfo(fileName, url);
        downloadInfo.setMimeType(mimeType);
        downloadInfo.setCookies(cookies);
        
        // Adicionar User-Agent, Referer e outros headers comuns
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (userAgent != null && !userAgent.isEmpty()) {
            headers.put("User-Agent", userAgent);
        }
        // Adicionar Referer se a WebView atual tiver uma URL carregada
        if (currentWebView != null && currentWebView.getUrl() != null && !currentWebView.getUrl().isEmpty()) {
             headers.put("Referer", currentWebView.getUrl());
        }
        // Adicionar outros headers comuns para simular melhor um navegador
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.put("Accept-Language", "en-US,en;q=0.9,pt-BR;q=0.8,pt;q=0.7"); // Exemplo, pode ser ajustado
        headers.put("Accept-Encoding", "gzip, deflate, br"); // O HttpURLConnection geralmente lida com isso, mas enviar pode ajudar
        
        // Adicionar outros headers se necessário no futuro
        
        if (!headers.isEmpty()) {
             downloadInfo.setCustomHeaders(headers);
             android.util.Log.d("InternalBrowser", "Headers adicionados ao DownloadInfo: " + headers.toString()); // Log para debug
        }

        // Utilizar um método no DownloadManager que aceite o DownloadInfo completo
        // (Este método precisará ser criado ou modificado no DownloadManager)
        boolean downloadQueued = DownloadManager.getInstance(this).startDownload(downloadInfo);

        if (downloadQueued) {
            // Mostrar toast
            Toast.makeText(this, "Download adicionado à fila: " + fileName, Toast.LENGTH_SHORT).show();

            // Abrir atividade de progresso de download
            startActivity(new Intent(this, DownloadProgressActivity.class));
        } else {
            // Notificar o usuário sobre a falha ao iniciar/enfileirar o download
            // A notificação de erro específica (ex: pasta não selecionada, arquivo já existe) 
            // já é tratada dentro do DownloadManager.startDownload
            Toast.makeText(this, "Falha ao iniciar o download. Verifique as configurações ou permissões.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void showDownloadEnhancedDialog(String url, String fileName) {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de confirmação
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_enhanced, null);
        TextView tvFileName = dialogView.findViewById(R.id.tv_file_name);
        
        tvFileName.setText(fileName);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton("Download", (dialog, which) -> {
                    // Iniciar download
                    DownloadMetadataResolver.resolveMetadata(this, url, new DownloadMetadataResolver.MetadataCallback() {
                        @Override
                        public void onMetadataResolved(String resolvedUrl, String mimeType, long contentLength) {
                            startDownload(resolvedUrl, fileName, mimeType);
                        }
                        
                        @Override
                        public void onMetadataError(String errorMessage) {
                            Toast.makeText(InternalBrowserActivity.this, 
                                "Erro ao obter informações do download: " + errorMessage, 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null);
        
        // Mostrar o diálogo
        builder.show();
    }
    
    private void showDownloadDetailsDialog(String url, String fileName) {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de detalhes
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_details, null);
        TextView tvFileName = dialogView.findViewById(R.id.tv_file_name);
        TextView tvUrl = dialogView.findViewById(R.id.tv_url);
        
        tvFileName.setText(fileName);
        tvUrl.setText(url);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton("Download", (dialog, which) -> {
                    // Iniciar download
                    DownloadMetadataResolver.resolveMetadata(this, url, new DownloadMetadataResolver.MetadataCallback() {
                        @Override
                        public void onMetadataResolved(String resolvedUrl, String mimeType, long contentLength) {
                            startDownload(resolvedUrl, fileName, mimeType);
                        }
                        
                        @Override
                        public void onMetadataError(String errorMessage) {
                            Toast.makeText(InternalBrowserActivity.this, 
                                "Erro ao obter informações do download: " + errorMessage, 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null);
        
        // Mostrar o diálogo
        builder.show();
    }
    
    private void showLinkExpiredDialog() {
        // Verificar se a atividade ainda está ativa
        if (isFinishing()) return;
        
        // Criar diálogo de link expirado
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_link_expired, null);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton("OK", null);
        
        // Mostrar o diálogo
        builder.show();
    }
}
