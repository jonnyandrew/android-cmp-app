package com.sourcepoint.cmplibrary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.HashSet;

abstract public class ConsentWebView extends WebView {
    private static final String TAG = "ConsentWebView";
    @SuppressWarnings("unused")
    private class MessageInterface {
        // called when message loads, brings the WebView to the front when the message is ready
        @JavascriptInterface
        public void onReceiveMessageData(boolean willShowMessage, String _msgJSON) {
            ConsentWebView.this.flushOrSyncCookies();
            ConsentWebView.this.onMessageReady(willShowMessage);
        }

        // called when a choice is selected on the message
        @JavascriptInterface
        public void onMessageChoiceSelect(int choiceType) {
            if(ConsentWebView.this.hasLostInternetConnection()) {
                ConsentWebView.this.onErrorOccurred(new ConsentLibException.NoInternetConnectionException());
            }
            ConsentWebView.this.onMessageChoiceSelect(choiceType);
        }

        // called when interaction with message is complete
        @JavascriptInterface
        public void sendConsentData(String euconsent, String consentUUID) {
            ConsentWebView.this.flushOrSyncCookies();
            ConsentWebView.this.onInteractionComplete(euconsent, consentUUID);
        }

        @JavascriptInterface
        public void onErrorOccurred(String errorType) {
            ConsentLibException error = ConsentWebView.this.hasLostInternetConnection() ?
                    new ConsentLibException.NoInternetConnectionException() :
                    new ConsentLibException("Something went wrong in the javascript world.");
            ConsentWebView.this.onErrorOccurred(error);
        }
    }

    // A simple mechanism to keep track of the urls being loaded by the WebView
    private class ConnectionPool {
        private HashSet<String> connections;
        private static final String INITIAL_LOAD = "data:text/html,";
        ConnectionPool() { connections = new HashSet<>(); }
        void add(String url) {
            // on API level < 21 the initial load is not recognized by the WebViewClient#onPageStarted callback
            if (url.equalsIgnoreCase(ConnectionPool.INITIAL_LOAD)) return;
            connections.add(url);
        }
        void remove(String url) { connections.remove(url); }
        boolean contains(String url) { return connections.contains(url); }
    }

    private long timeoutMillisec;
    private ConnectionPool connectionPool;

    public static long DEFAULT_TIMEOUT = 10000;

    public ConsentWebView(Context context, long timeoutMillisec) {
        super(context);
        this.timeoutMillisec = timeoutMillisec;
        connectionPool = new ConnectionPool();
        setup();
    }

    public ConsentWebView(Context context) {
        super(context);
        this.timeoutMillisec = DEFAULT_TIMEOUT;
         connectionPool = new ConnectionPool();
        setup();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWebContentsDebuggingEnabled(true);
            enableSlowWholeDocumentDraw();
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        getSettings().setAppCacheEnabled(false);
        getSettings().setBuiltInZoomControls(false);
        getSettings().setSupportZoom(false);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        getSettings().setAllowFileAccess(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setSupportMultipleWindows(true);
        getSettings().setDomStorageEnabled(true);
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                connectionPool.add(url);
                Runnable run = new Runnable() {
                    public void run() {
                        if(connectionPool.contains(url))
                            onErrorOccurred(new ConsentLibException.ApiException("TIMED OUT: "+url));
                    }
                };
                Handler myHandler = new Handler(Looper.myLooper());
                myHandler.postDelayed(run, timeoutMillisec);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                flushOrSyncCookies();
                connectionPool.remove(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.d(TAG, "onReceivedError: "+error.toString());
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.d(TAG, "onReceivedError: Error "+errorCode+": "+description);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                Log.d(TAG, "onReceivedSslError: Error "+error);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "The WebView rendering process crashed!");
                return false;
            }
        });
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String data = result.getExtra();
                Context context = view.getContext();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(data));
                context.startActivity(browserIntent);
                return false;
            }
        });
        setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                WebView webView = (WebView) view;
                if(event.getAction() == KeyEvent.ACTION_DOWN &&
                        KeyEvent.KEYCODE_BACK == keyCode &&
                        webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                return false;
            }
        });
        addJavascriptInterface(new MessageInterface(), "JSReceiver");
        resumeTimers();
    }

    boolean hasLostInternetConnection() {
        ConnectivityManager manager = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager == null) { return true; }

        NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
        return activeNetwork == null || !activeNetwork.isConnectedOrConnecting();
    }

    private void flushOrSyncCookies() {
        // forces the cookies sync between RAM and local storage
        // https://developer.android.com/reference/android/webkit/CookieSyncManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().flush();
        else CookieSyncManager.getInstance().sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        flushOrSyncCookies();
    }

    abstract public void onMessageReady(boolean willShowMessage);
    abstract public void onErrorOccurred(ConsentLibException error);
    abstract public void onInteractionComplete(String euConsent, String consentUUID);
    abstract public void onMessageChoiceSelect(int choiceType);

    public void loadMessage(String messageUrl) throws ConsentLibException.NoInternetConnectionException {
        if(hasLostInternetConnection()) throw new ConsentLibException.NoInternetConnectionException();

        // On API level >= 21, the JavascriptInterface is not injected on the page until the *second* page load
        // so we need to issue blank load with loadData
        loadData("", "text/html", null);
        Log.d(TAG, "Loading Webview with: "+messageUrl);
        Log.d(TAG, "User-Agent: "+getSettings().getUserAgentString());
        loadUrl(messageUrl);
    }

    public void display() {
        setLayoutParams(new ViewGroup.LayoutParams(0, 0));
        getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        bringToFront();
        requestLayout();
    }
}