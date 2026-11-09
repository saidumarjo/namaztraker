package tj.namoztraker.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_LOCATION = 11;
    private static final int REQ_NOTIFY = 12;

    private WebView web;
    private SensorManager sensors;
    private Sensor rotation;
    private long lastSent = 0;
    private float lastHeading = -999f;
    private volatile float declination = 0f;

    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Notif.createChannels(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocation()) {
                    callback.invoke(origin, true, false);
                } else {
                    geoCallback = callback;
                    geoOrigin = origin;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }
        });

        web.addJavascriptInterface(new Bridge(), "NT");
        web.loadUrl("https://" + HOST + "/assets/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }

        handleWuduIntent(getIntent());
        handleUrgentIntent(getIntent());

        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            geoCallback.invoke(geoOrigin, hasLocation(), false);
            geoCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWuduIntent(intent);
        handleUrgentIntent(intent);
    }

    /* ---------- "prayer is becoming qaza" full-screen alert ---------- */
    private volatile String pendingUrgent = null;

    private void handleUrgentIntent(Intent in) {
        if (in == null || !Notif.ACTION_URGENT.equals(in.getAction())) return;
        String key = in.getStringExtra("key");
        in.setAction(null);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        Notif.cancelUrgent(getApplicationContext());
        if (key == null || key.isEmpty()) return;
        pendingUrgent = key;
        if (web != null) {
            String k = key.replace("'", "");
            web.evaluateJavascript("(function(){if(window.ntUrgent){window.ntUrgent('" + k + "');return 1}return 0})()",
                    v -> { if ("1".equals(v)) pendingUrgent = null; });
        }
    }

    /* ---------- 5-minute focus lock after "no wudu" ---------- */
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable unlockRun = this::endLock;

    private SharedPreferences ntPrefs() {
        return getSharedPreferences("nt", MODE_PRIVATE);
    }

    private void handleWuduIntent(Intent in) {
        if (in == null || !Notif.ACTION_WUDU_LOCK.equals(in.getAction())) return;
        Notif.onWudu(getApplicationContext(), in);
        in.setAction(null);
        startLock(5);
    }

    private void startLock(int minutes) {
        long until = System.currentTimeMillis() + minutes * 60_000L;
        ntPrefs().edit().putLong("lock_until", until).apply();
        try { startLockTask(); } catch (Exception ignored) { }
        ui.removeCallbacks(unlockRun);
        ui.postDelayed(unlockRun, until - System.currentTimeMillis());
        if (web != null) web.evaluateJavascript("window.ntLockSync&&window.ntLockSync()", null);
    }

    private void endLock() {
        ntPrefs().edit().putLong("lock_until", 0L).apply();
        ui.removeCallbacks(unlockRun);
        try { stopLockTask(); } catch (Exception ignored) { }
        if (web != null) web.evaluateJavascript("window.ntLockSync&&window.ntLockSync()", null);
    }

    private void checkLock() {
        long until = ntPrefs().getLong("lock_until", 0L);
        long now = System.currentTimeMillis();
        if (until > now) {
            ui.removeCallbacks(unlockRun);
            ui.postDelayed(unlockRun, until - now);
        } else if (until != 0L) {
            endLock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLock();
        web.onResume();
        if (rotation != null) sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
        web.evaluateJavascript("window.dispatchEvent&&window.dispatchEvent(new Event('focus'))", null);
    }

    @Override
    protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        web.onPause();
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float[] r = new float[9];
        float[] o = new float[3];
        SensorManager.getRotationMatrixFromVector(r, e.values);
        SensorManager.getOrientation(r, o);
        float heading = (float) ((Math.toDegrees(o[0]) + declination + 720) % 360);
        long now = System.currentTimeMillis();
        float diff = Math.abs(((heading - lastHeading + 540) % 360) - 180);
        if (now - lastSent < 80 || diff < 0.8f) return;
        lastSent = now;
        lastHeading = heading;
        web.evaluateJavascript("window.ntHeading&&window.ntHeading(" + heading + ")", null);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){if(location.hash&&location.hash!=='#today'){location.hash='today';return 1}return 0})()",
                v -> { if (!"1".equals(v)) finish(); });
    }

    private class Bridge {
        @JavascriptInterface
        public void schedule(String json) {
            Notif.saveAndSchedule(getApplicationContext(), json);
        }

        @JavascriptInterface
        public void setLocation(double lat, double lng) {
            try {
                GeomagneticField f = new GeomagneticField((float) lat, (float) lng, 0f, System.currentTimeMillis());
                declination = f.getDeclination();
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public boolean hasCompass() {
            return rotation != null;
        }

        @JavascriptInterface
        public void widget(String json) {
            Widgets.save(getApplicationContext(), json);
        }

        @JavascriptInterface
        public void lockFor(int minutes) {
            runOnUiThread(() -> startLock(Math.max(1, Math.min(minutes, 15))));
        }

        @JavascriptInterface
        public void unlock() {
            runOnUiThread(MainActivity.this::endLock);
        }

        @JavascriptInterface
        public long lockUntil() {
            return ntPrefs().getLong("lock_until", 0L);
        }

        @JavascriptInterface
        public void setWuduLock(boolean on) {
            ntPrefs().edit().putBoolean("wudu_lock", on).apply();
        }

        @JavascriptInterface
        public String wuduAnswers() {
            return Notif.answers(getApplicationContext());
        }

        @JavascriptInterface
        public void setWudu(String key, String ans) {
            Notif.setAnswer(getApplicationContext(), key, ans);
        }

        @JavascriptInterface
        public void setBlock(String json) {
            Block.save(getApplicationContext(), json);
        }

        @JavascriptInterface
        public boolean blockReady() {
            return BlockService.isOn(getApplicationContext());
        }

        @JavascriptInterface
        public void openBlockSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public String takeMarks() {
            return Widgets.takeMarks(getApplicationContext());
        }

        @JavascriptInterface
        public String takeUrgent() {
            String k = pendingUrgent;
            pendingUrgent = null;
            return k == null ? "" : k;
        }

        @JavascriptInterface
        public void urgentDone() {
            Notif.cancelUrgent(getApplicationContext());
            if (Build.VERSION.SDK_INT >= 27) runOnUiThread(() -> { setShowWhenLocked(false); setTurnScreenOn(false); });
        }

        @JavascriptInterface
        public void testNotify() {
            Notif.show(getApplicationContext(), "НАМОЗХОН TJ", "Огоҳӣ кор мекунад.", 999);
        }
    }
}
