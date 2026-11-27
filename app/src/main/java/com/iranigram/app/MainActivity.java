package com.amyrmftah3.irangram;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.provider.ContactsContract;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int MEDIA_PERMISSION_REQUEST = 41;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 43;
    private static final int CONTACTS_PERMISSION_REQUEST = 44;
    private static final String NOTIFICATION_CHANNEL_ID = "irangram_messages";
    private int nextNotificationId = 1000;
    private static final int FILE_CHOOSER_REQUEST = 42;
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/app.html?v=1.3.16";
    private static final int NAVY = Color.rgb(7, 22, 55);

    private FrameLayout root;
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private LinearLayout loadingPanel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button retryButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pageLoaded = false;
    private PermissionRequest pendingWebPermission;
    private String[] pendingAndroidPermissions = new String[0];

    private final Runnable loadTimeout = () -> {
        if (!pageLoaded) showError("بارگذاری ایرانگرام بیشتر از حد معمول طول کشید. اتصال اینترنت را بررسی کن و دوباره بزن.");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        buildScreen();
        createWebView();
        if (savedInstanceState != null) {
            Bundle webState = savedInstanceState.getBundle("web_state");
            if (webState != null && webView.restoreState(webState) != null) {
                showLoading("در حال بازگردانی ایرانگرام...");
                handler.postDelayed(loadTimeout, 30000);
                return;
            }
        }
        loadApp();
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        setContentView(root);

        loadingPanel = new LinearLayout(this);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setGravity(Gravity.CENTER);
        loadingPanel.setPadding(32, 32, 32, 32);
        loadingPanel.setBackgroundColor(NAVY);

        TextView mark = new TextView(this);
        mark.setText("✈");
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(54);
        mark.setGravity(Gravity.CENTER);
        loadingPanel.addView(mark, new LinearLayout.LayoutParams(-2, -2));

        TextView title = new TextView(this);
        title.setText("ایران‌گرام");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = 12;
        loadingPanel.addView(title, titleParams);

        statusText = new TextView(this);
        statusText.setText("در حال اتصال به ایرانگرام...");
        statusText.setTextColor(Color.rgb(205, 222, 245));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = 18;
        loadingPanel.addView(statusText, statusParams);

        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(180, -2);
        progressParams.topMargin = 22;
        loadingPanel.addView(progressBar, progressParams);

        retryButton = new Button(this);
        retryButton.setText("تلاش دوباره");
        retryButton.setOnClickListener(v -> loadApp());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, -2);
        retryParams.topMargin = 18;
        loadingPanel.addView(retryButton, retryParams);
        retryButton.setVisibility(View.GONE);

        root.addView(loadingPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(NAVY);
        WebSettings settings = webView.getSettings();
        webView.addJavascriptInterface(new AndroidNotificationBridge(), "IranGramAndroid");
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageLoaded = false;
                showLoading("در حال باز کردن ایرانگرام...");
                handler.removeCallbacks(loadTimeout);
                handler.postDelayed(loadTimeout, 30000);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                handler.removeCallbacks(loadTimeout);
                loadingPanel.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    handler.removeCallbacks(loadTimeout);
                    showError("اتصال به سرور برقرار نشد. اینترنت را بررسی کن و دوباره تلاش کن.");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (request.isForMainFrame()) {
                    handler.removeCallbacks(loadTimeout);
                    showError("سرور ایرانگرام پاسخ مناسب نداد (کد " + response.getStatusCode() + "). دوباره تلاش کن.");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                sslHandler.cancel();
                handler.removeCallbacks(loadTimeout);
                showError("اتصال امن برقرار نشد. تاریخ و ساعت گوشی و اینترنت را بررسی کن.");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                handler.removeCallbacks(loadTimeout);
                showError("بخش نمایش برنامه متوقف شد. برای راه‌اندازی دوباره تلاش کن.");
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    java.util.LinkedHashSet<String> accepted = new java.util.LinkedHashSet<>();
                    String[] acceptTypes = params.getAcceptTypes();
                    if (acceptTypes != null) {
                        for (String value : acceptTypes) {
                            if (value == null) continue;
                            for (String type : value.split(",")) {
                                String clean = type.trim().toLowerCase(java.util.Locale.ROOT);
                                if (clean.contains("/")) accepted.add(clean);
                            }
                        }
                    }
                    String mime = accepted.isEmpty() ? "*/*" : (accepted.size() == 1 ? accepted.iterator().next() : "*/*");
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType(mime);
                    if (accepted.size() > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, accepted.toArray(new String[0]));
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(Intent.createChooser(picker, "انتخاب عکس، ویدئو یا فایل"), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception firstError) {
                    try {
                        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                        picker.addCategory(Intent.CATEGORY_OPENABLE);
                        picker.setType("*/*");
                        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(Intent.createChooser(picker, "انتخاب فایل"), FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (Exception secondError) {
                        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                        filePathCallback = null;
                        Toast.makeText(MainActivity.this, "باز کردن گالری یا فایل‌گزین ممکن نشد؛ دوباره تلاش کن.", Toast.LENGTH_LONG).show();
                        return true;
                    }
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean needsAudio = false, needsVideo = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) needsAudio = true;
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) needsVideo = true;
                    }
                    boolean audioOk = !needsAudio || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    boolean videoOk = !needsVideo || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    if (audioOk && videoOk) {
                        request.grant(request.getResources());
                    } else {
                        pendingWebPermission = request;
                        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
                        if (needsAudio && !audioOk) needed.add(Manifest.permission.RECORD_AUDIO);
                        if (needsVideo && !videoOk) needed.add(Manifest.permission.CAMERA);
                        pendingAndroidPermissions = needed.toArray(new String[0]);
                        ActivityCompat.requestPermissions(MainActivity.this, pendingAndroidPermissions, MEDIA_PERMISSION_REQUEST);
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }
        });

        root.addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    private final class AndroidNotificationBridge {
        @JavascriptInterface
        public void requestContacts() {
            String currentUrl = webView == null ? "" : String.valueOf(webView.getUrl());
            if (!currentUrl.startsWith("https://appassets.androidplatform.net/assets/")) return;
            runOnUiThread(() -> {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) sendContactsToWeb();
                else ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST);
            });
        }

        @JavascriptInterface
        public void dialNumber(String number) {
            String currentUrl = webView == null ? "" : String.valueOf(webView.getUrl());
            if (!currentUrl.startsWith("https://appassets.androidplatform.net/assets/")) return;
            String safe = number == null ? "" : number.replaceAll("[^0-9+*#]", "");
            if (safe.isEmpty() || safe.length() > 32) return;
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(safe)))); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "بازکردن شماره‌گیر ممکن نشد.", Toast.LENGTH_SHORT).show(); }
            });
        }

        @JavascriptInterface
        public void requestPermission() {
            String currentUrl = webView == null ? "" : String.valueOf(webView.getUrl());
            if (!currentUrl.startsWith("https://appassets.androidplatform.net/assets/")) return;
            runOnUiThread(() -> requestNativeNotificationPermission());
        }

        @JavascriptInterface
        public void showNotification(String title, String body) {
            String currentUrl = webView == null ? "" : String.valueOf(webView.getUrl());
            if (!currentUrl.startsWith("https://appassets.androidplatform.net/assets/")) return;
            runOnUiThread(() -> showNativeNotification(title, body));
        }
    }

    private void sendContactsToWeb() {
        if (webView == null) return;
        JSONArray contacts = new JSONArray();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (cursor != null) {
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                while (cursor.moveToNext() && contacts.length() < 5000) {
                    String name = cursor.getString(0), number = cursor.getString(1);
                    if (number == null || number.trim().isEmpty()) continue;
                    String key = number.replaceAll("[^0-9]", "");
                    if (key.isEmpty() || !seen.add(key)) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", name == null ? "" : name);
                    item.put("phone", number);
                    contacts.put(item);
                }
            }
        } catch (Exception e) { android.util.Log.e("IranGram", "Reading contacts failed", e); }
        finally { if (cursor != null) cursor.close(); }
        String payload = contacts.toString();
        webView.post(() -> webView.evaluateJavascript("window.onIranGramContacts && window.onIranGramContacts(" + JSONObject.quote(payload) + ");", null));
    }

    private void requestNativeNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        } else {
            ensureNotificationChannel();
            Toast.makeText(this, "اعلان‌های ایرانگرام فعال است.", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "پیام‌های ایرانگرام", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("اعلان پیام‌های جدید ایرانگرام");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNativeNotification(String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        ensureNotificationChannel();
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title == null || title.trim().isEmpty() ? "ایرانگرام" : title.substring(0, Math.min(title.length(), 80)))
                .setContentText(body == null ? "پیام جدید" : body.substring(0, Math.min(body.length(), 180)))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(nextNotificationId++, builder.build());
    }

    private void showLoading(String message) {
        statusText.setText(message);
        progressBar.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        pageLoaded = false;
        statusText.setText(message);
        progressBar.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        loadingPanel.setVisibility(View.VISIBLE);
    }

    private void loadApp() {
        if (webView == null) return;
        pageLoaded = false;
        showLoading("در حال اتصال به ایرانگرام...");
        handler.removeCallbacks(loadTimeout);
        handler.postDelayed(loadTimeout, 30000);
        webView.loadUrl(APP_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                    if ((results == null || results.length == 0) && data.getClipData() != null) {
                        android.content.ClipData clip = data.getClipData();
                        results = new Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) results[i] = clip.getItemAt(i).getUri();
                    }
                    if ((results == null || results.length == 0) && data.getData() != null) results = new Uri[]{data.getData()};
                }
                ValueCallback<Uri[]> callback = filePathCallback;
                filePathCallback = null;
                callback.onReceiveValue(results);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) sendContactsToWeb();
            else if (webView != null) webView.evaluateJavascript("window.onIranGramContacts && window.onIranGramContacts('[]')", null);
            return;
        }
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureNotificationChannel();
                Toast.makeText(this, "اعلان‌های ایرانگرام فعال شد.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "اجازه اعلان داده نشد.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == MEDIA_PERMISSION_REQUEST && pendingWebPermission != null) {
            boolean allGranted = grantResults.length == pendingAndroidPermissions.length;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            pendingAndroidPermissions = new String[0];
            if (allGranted) pendingWebPermission.grant(pendingWebPermission.getResources());
            else {
                pendingWebPermission.deny();
                Toast.makeText(this, "برای تماس، دسترسی میکروفون و دوربین لازم است.", Toast.LENGTH_LONG).show();
            }
            pendingWebPermission = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            Bundle webState = new Bundle();
            webView.saveState(webState);
            outState.putBundle("web_state", webState);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loadTimeout);
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
