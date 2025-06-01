package com.LDGAMES.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.LDGAMES.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class YouTubePlayerDialog extends DialogFragment {

    private static final String ARG_VIDEO_ID = "video_id";
    private WebView webView;

    public static YouTubePlayerDialog newInstance(String videoId) {
        YouTubePlayerDialog fragment = new YouTubePlayerDialog();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String videoId = getArguments().getString(ARG_VIDEO_ID);
        
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_youtube_player, null);
        
        webView = view.findViewById(R.id.youtube_web_view);
        setupWebView(webView, videoId);
        
        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
    }

    private void setupWebView(WebView webView, String videoId) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        
        // Carregar o player do YouTube com o vídeo especificado
        String embedUrl = "https://www.youtube.com/embed/" + videoId + "?autoplay=1";
        String html = "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<style>body { margin: 0; } iframe { width: 100%; height: 100%; }</style>" +
                "</head><body>" +
                "<iframe src=\"" + embedUrl + "\" frameborder=\"0\" allowfullscreen></iframe>" +
                "</body></html>";
                
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = FrameLayout.LayoutParams.MATCH_PARENT;
            int height = FrameLayout.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroyView();
    }
}
