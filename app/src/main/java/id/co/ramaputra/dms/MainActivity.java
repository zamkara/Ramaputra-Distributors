package id.co.ramaputra.dms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
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
        if (Build.VERSION.SDK_INT >= 35) getWindow().setDecorFitsSystemWindows(false);

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
            @Override public void onPageCommitVisible(WebView view, String url) {
                injectBarScript(view, url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                injectBarScript(view, url);
            }
        });
        if (state == null || webView.restoreState(state) == null) webView.loadUrl(HOME);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (webView.canGoBack()) webView.goBack(); else finish(); }
            );
        }
    }

    private boolean isFirstParty(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost());
    }

    private void injectBarScript(WebView view, String url) {
        if (barScript != null && url != null && isFirstParty(Uri.parse(url))) {
            view.evaluateJavascript(barScript, null);
        }
    }

    private void applyBarColors() {
        getWindow().setStatusBarColor(statusColor);
        getWindow().setNavigationBarColor(navigationColor);
        statusBackdrop.setBackgroundColor(statusColor);
        navigationBackdrop.setBackgroundColor(navigationColor);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (isLight(statusColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (isLight(navigationColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private boolean isLight(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) >= 128;
    }

    private final class SystemBarsBridge {
        @JavascriptInterface public void postMessage(String message) {
            runOnUiThread(() -> {
                if (!isFirstParty(Uri.parse(webView.getUrl()))) return;
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

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        webView.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
