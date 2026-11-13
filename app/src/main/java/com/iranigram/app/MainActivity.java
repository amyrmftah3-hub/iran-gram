package ir.irangram.messenger;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final int MEDIA_PERMISSION_REQUEST = 41;
    private WebView webView;
    private PermissionRequest pendingWebPermission;
    private String[] pendingAndroidPermissions = new String[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFFFFFFFF);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    String fallback = "<!doctype html><html lang='fa' dir='rtl'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>اتصال ایرانگرام</title><body style='margin:0;background:#071a36;color:#fff;font-family:Tahoma,sans-serif;display:grid;min-height:100vh;place-items:center;text-align:center'><main style='padding:28px;max-width:420px'><div style='font-size:48px'>✈️</div><h2>اتصال به ایرانگرام برقرار نشد</h2><p style='line-height:2;color:#c9d9ec'>اینترنت یا دسترسی به سایت موقتاً در دسترس نیست. دوباره تلاش کن.</p><button onclick=\"location.href='https://iran-gram.onrender.com/app.html?v=877f655'\" style='border:0;border-radius:24px;padding:13px 28px;background:#168acd;color:white;font-size:16px'>تلاش دوباره</button></main></body></html>";
                    view.loadDataWithBaseURL("https://iran-gram.onrender.com/", fallback, "text/html", "UTF-8", null);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
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
        setContentView(webView);
        if (savedInstanceState == null) {
            webView.loadUrl("https://iran-gram.onrender.com/app.html?v=877f655");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
