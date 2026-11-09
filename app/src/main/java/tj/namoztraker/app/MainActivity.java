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
    protected void onResume() {
        super.onResume();
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
        public String wuduAnswers() {
            return Notif.answers(getApplicationContext());
        }

        @JavascriptInterface
        public void setWudu(String key, String ans) {
            Notif.setAnswer(getApplicationContext(), key, ans);
        }

        @JavascriptInterface
        public void testNotify() {
            Notif.show(getApplicationContext(), "НАМОЗХОН TJ", "Огоҳӣ кор мекунад.", 999);
        }
    }
}
