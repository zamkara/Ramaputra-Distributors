package id.co.ramaputra.dms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String HOME = "https://dmsx.ramaputra.co.id";
    private static final String HOST = "dmsx.ramaputra.co.id";
    private static final String OFFLINE_HTML =
            "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<style>:root{color-scheme:light dark}body{margin:0;min-height:100vh;display:grid;place-items:center;"
            + "font:15px/1.5 system-ui,sans-serif;text-align:center;padding:24px}"
            + "button{margin-top:16px;padding:10px 20px;border:0;border-radius:8px;font:inherit;cursor:pointer}</style>"
            + "<div><strong>Tidak dapat terhubung</strong><p>Periksa koneksi internet Anda lalu coba lagi.</p>"
            + "<button onclick=\"location.replace('" + HOME + "')\">Muat Ulang</button></div>";

    private WebView webView;
    private View statusBackdrop;
    private View navigationBackdrop;
    private int statusColor = Color.rgb(23, 23, 23);
    private int navigationColor = statusColor;
    private String barScript;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES) {
            statusColor = Color.WHITE;
            navigationColor = Color.WHITE;
        }
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(statusColor);
        webView = new WebView(this);
        webView.setBackgroundColor(statusColor);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        statusBackdrop = new View(this);
        navigationBackdrop = new View(this);
        root.addView(statusBackdrop, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
        root.addView(navigationBackdrop, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
                FrameLayout.LayoutParams page = (FrameLayout.LayoutParams) webView.getLayoutParams();
                page.topMargin = top;
                page.bottomMargin = Math.max(bottom, keyboard);
                webView.setLayoutParams(page);
                FrameLayout.LayoutParams status = (FrameLayout.LayoutParams) statusBackdrop.getLayoutParams();
                status.height = top;
                statusBackdrop.setLayoutParams(status);
                FrameLayout.LayoutParams navigation = (FrameLayout.LayoutParams) navigationBackdrop.getLayoutParams();
                navigation.height = bottom;
                navigationBackdrop.setLayoutParams(navigation);
                return insets;
            });
            root.requestApplyInsets();
        }
        applyBarColors();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new SystemBarsBridge(), "AndroidSystemBars");
        try (InputStream input = getAssets().open("system-bars.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            barScript = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            barScript = null;
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isFirstParty(uri)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                    // Ignore unsupported schemes.
                }
                return true;
            }

            @Override public void onPageCommitVisible(WebView view, String url) {
                injectBarScript(view, url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                injectBarScript(view, url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadDataWithBaseURL(HOME, OFFLINE_HTML, "text/html", "utf-8", null);
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == webView) {
                    webView = null;
                    view.destroy();
                    recreate();
                    return true;
                }
                return false;
            }
        });
        if (state == null || webView.restoreState(state) == null) webView.loadUrl(HOME);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (webView != null && webView.canGoBack()) webView.goBack(); else finish(); }
            );
        }
    }

    private boolean isFirstParty(Uri uri) {
        return uri != null && "https".equals(uri.getScheme()) && HOST.equals(uri.getHost());
    }

    private void injectBarScript(WebView view, String url) {
        if (barScript != null && url != null && isFirstParty(Uri.parse(url))) {
            view.evaluateJavascript(barScript, null);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyBarColors() {
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(statusColor);
            getWindow().setNavigationBarColor(navigationColor);
        }
        statusBackdrop.setBackgroundColor(statusColor);
        navigationBackdrop.setBackgroundColor(navigationColor);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = 0;
                if (isLight(statusColor)) appearance |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (isLight(navigationColor)) appearance |= WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if (isLight(statusColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (isLight(navigationColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private boolean isLight(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) >= 128;
    }

    private final class SystemBarsBridge {
        @JavascriptInterface public void postMessage(String message) {
            runOnUiThread(() -> {
                if (webView == null) return;
                String currentUrl = webView.getUrl();
                if (currentUrl == null || !isFirstParty(Uri.parse(currentUrl))) return;
                try {
                    JSONObject colors = new JSONObject(message);
                    statusColor = Color.parseColor(colors.getString("status"));
                    navigationColor = Color.parseColor(colors.getString("navigation"));
                    applyBarColors();
                } catch (Exception ignored) {
                    // Keep the last valid colors while the page changes.
                }
            });
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (webView != null) webView.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidSystemBars");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
