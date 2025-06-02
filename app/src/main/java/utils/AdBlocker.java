package com.LDGAMES.utils;

import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

public class AdBlocker {
    private static final Set<String> AD_HOSTS = new HashSet<>();
    
    static {
        // Lista de domínios comuns de anúncios
        AD_HOSTS.add("googleadservices.com");
        AD_HOSTS.add("googlesyndication.com");
        AD_HOSTS.add("doubleclick.net");
        AD_HOSTS.add("adservice.google.com");
        AD_HOSTS.add("pagead2.googlesyndication.com");
        AD_HOSTS.add("ad.doubleclick.net");
        AD_HOSTS.add("adclick.g.doubleclick.net");
        AD_HOSTS.add("securepubads.g.doubleclick.net");
        AD_HOSTS.add("googleads.g.doubleclick.net");
        AD_HOSTS.add("ads.google.com");
        AD_HOSTS.add("adserver.adtechus.com");
        AD_HOSTS.add("adnxs.com");
        AD_HOSTS.add("ads.pubmatic.com");
        AD_HOSTS.add("ads.yahoo.com");
        AD_HOSTS.add("taboola.com");
        AD_HOSTS.add("outbrain.com");
        AD_HOSTS.add("zedo.com");
        AD_HOSTS.add("adbrite.com");
        AD_HOSTS.add("advertising.com");
        AD_HOSTS.add("fastclick.net");
        AD_HOSTS.add("quantserve.com");
        AD_HOSTS.add("scorecardresearch.com");
        AD_HOSTS.add("yieldmanager.com");
        AD_HOSTS.add("adtech.de");
        AD_HOSTS.add("admeld.com");
        AD_HOSTS.add("admob.com");
        AD_HOSTS.add("adwhirl.com");
        AD_HOSTS.add("amazon-adsystem.com");
    }
    
    /**
     * Verifica se uma URL é de um servidor de anúncios conhecido
     * @param host Nome do host a verificar
     * @return true se for um servidor de anúncios, false caso contrário
     */
    private static boolean isAdHost(String host) {
        if (host == null) {
            return false;
        }
        
        // Verificar se o host termina com algum dos domínios de anúncios conhecidos
        for (String adHost : AD_HOSTS) {
            if (host.endsWith(adHost)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Bloqueia requisições para servidores de anúncios
     * @param request Requisição web a ser verificada
     * @return WebResourceResponse vazia se for anúncio, null caso contrário
     */
    public static WebResourceResponse blockAds(WebResourceRequest request) {
        String host = request.getUrl().getHost();
        
        if (isAdHost(host)) {
            // Retornar resposta vazia para bloquear o anúncio
            return new WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    new ByteArrayInputStream("".getBytes())
            );
        }
        
        // Não é anúncio, deixar o WebView processar normalmente
        return null;
    }
}
