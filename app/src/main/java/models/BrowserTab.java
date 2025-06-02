package com.LDGAMES.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.webkit.WebView;

public class BrowserTab implements Parcelable {
    private int index;
    private String title;
    private String url;
    private WebView webView;

    // Construtor padrão
    public BrowserTab() {
        this.index = 0;
        this.title = "";
        this.url = "";
        this.webView = null;
    }

    // Construtor com parâmetros
    public BrowserTab(int index, String title, String url) {
        this.index = index;
        this.title = title;
        this.url = url;
        this.webView = null;
    }
    
    // Construtor com título e URL (sem índice)
    public BrowserTab(String title, String url) {
        this.index = 0;
        this.title = title;
        this.url = url;
        this.webView = null;
    }

    protected BrowserTab(Parcel in) {
        index = in.readInt();
        title = in.readString();
        url = in.readString();
    }

    public static final Creator<BrowserTab> CREATOR = new Creator<BrowserTab>() {
        @Override
        public BrowserTab createFromParcel(Parcel in) {
            return new BrowserTab(in);
        }

        @Override
        public BrowserTab[] newArray(int size) {
            return new BrowserTab[size];
        }
    };

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public WebView getWebView() {
        return webView;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeString(title);
        dest.writeString(url);
    }
}
