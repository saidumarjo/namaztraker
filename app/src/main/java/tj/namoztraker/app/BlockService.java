package tj.namoztraker.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/** Closes YouTube, TikTok, Instagram, Facebook and Telegram (apps and sites) from adhan until the congregation. */
public class BlockService extends AccessibilityService {

    private static final String[] URL_IDS = {
            "com.android.chrome:id/url_bar",
            "com.chrome.beta:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/custom_tab_toolbar_url_bar_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.opera.mini.native:id/url_field",
            "com.yandex.browser:id/bro_omnibar_address_title_text",
            "com.mi.globalbrowser:id/url",
            "com.huawei.browser:id/url_bar",
            "com.heytap.browser:id/url_bar",
            "com.vivaldi.browser:id/url_bar",
            "com.kiwibrowser.browser:id/url_bar",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
    };

    private String lastPkg = "";
    private long lastCheck = 0;
    private long lastBlock = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        long now = System.currentTimeMillis();
        boolean stateChange = e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (!stateChange && pkg.equals(lastPkg) && now - lastCheck < 1200) return;
        lastPkg = pkg;
        lastCheck = now;

        Block.Win w = Block.active(this, now);
        if (w == null) return;

        String hit = Block.app(this, pkg);
        if (hit == null) hit = Block.site(this, urlOf(pkg));
        if (hit == null) return;
        if (now - lastBlock < 800) return;
        lastBlock = now;

        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent in = new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("app", hit)
                .putExtra("name", w.name)
                .putExtra("start", w.start)
                .putExtra("until", w.until)
                .putExtra("qaza", w.qaza)
                .putExtra("d", w.day)
                .putExtra("k", w.key);
        try { startActivity(in); } catch (Exception ignored) { }
    }

    private String urlOf(String pkg) {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return null;
            for (String id : URL_IDS) {
                if (!id.startsWith(pkg + ":")) continue;
                List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
                if (l != null) for (AccessibilityNodeInfo n : l) {
                    CharSequence t = n.getText();
                    if (t != null && t.length() > 0) return t.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void onInterrupt() { }

    /** Whether the user has turned this service on in Accessibility settings. */
    static boolean isOn(Context c) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return false;
            ComponentName me = new ComponentName(c, BlockService.class);
            TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
            sp.setString(list);
            while (sp.hasNext()) {
                ComponentName cn = ComponentName.unflattenFromString(sp.next());
                if (me.equals(cn)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
