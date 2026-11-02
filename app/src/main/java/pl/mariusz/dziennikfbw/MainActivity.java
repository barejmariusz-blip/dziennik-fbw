package pl.mariusz.dziennikfbw;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Hosts the training log (assets/index.html) in a WebView; data lives in the WebView's localStorage. */
public class MainActivity extends Activity {

    /** True while the app is on screen — then the page beeps itself and the rest alarm stays quiet. */
    static volatile boolean visible;

    private static final int FILE_REQ = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RestAlarm.channels(this);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri)); // YouTube etc. open outside the app
                } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Wybierz plik kopii"), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (web != null) web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        CookieManager.getInstance().flush();
    }

    /** Called from the page as window.Android.* */
    class Bridge {
        @JavascriptInterface
        public String saveFile(String name, String content, String mime) {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(bytes);
                    }
                    return "ok";
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File f = new File(dir, name);
                    try (FileOutputStream os = new FileOutputStream(f)) {
                        os.write(bytes);
                    }
                    return "ok:" + f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "fail";
            }
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /**
         * Rest-timer beep. alarm=true plays on the alarm stream (loud like an alarm clock,
         * independent of music volume). vol 0..1.5 — above 1 the tone is soft-clipped for extra loudness.
         */
        /** Rest-timer beep (see Beeper). */
        @JavascriptInterface
        public void tone(final int freq, final int ms, final float vol, final boolean alarm) {
            new Thread(() -> Beeper.play(freq, ms, vol, alarm)).start();
        }

        /** Rest timer that also works with the screen off: system alarm + notification countdown. */
        @JavascriptInterface
        public void restStart(double startMs, int minS, int maxS, String label, float vol, boolean alarm) {
            RestAlarm.start(MainActivity.this, (long) startMs, minS, maxS, label, vol, alarm);
        }

        @JavascriptInterface
        public void restStop() { RestAlarm.stop(MainActivity.this); }

        /** "granted" | "denied" | "ask" */
        @JavascriptInterface
        public String notifyState() {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return "ask";
                return "denied";
            }
            return "granted";
        }

        @JavascriptInterface
        public void askNotify() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 44);
                } else {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    } catch (Exception ignored) { }
                }
            });
        }

        /** Snapshot that Post Przerywany reads through ShareProvider. */
        @JavascriptInterface
        public void publish(String json) {
            getSharedPreferences(ShareProvider.PREFS, MODE_PRIVATE).edit().putString("share", json).apply();
            GymWidget.refresh(MainActivity.this);
        }

        /** Post Przerywany snapshot, "" when the app is missing. */
        @JavascriptInterface
        public String peer() {
            try (Cursor c = getContentResolver().query(Uri.parse("content://pl.mariusz.postprzerywany.share/summary"), null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String v = c.getString(0);
                    return v == null ? "" : v;
                }
            } catch (Exception ignored) { }
            return "";
        }

        @JavascriptInterface
        public void openPeer() {
            runOnUiThread(() -> {
                Intent i = getPackageManager().getLaunchIntentForPackage("pl.mariusz.postprzerywany");
                if (i != null) startActivity(i);
            });
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dziennik FBW", text));
            });
        }
    }
}
