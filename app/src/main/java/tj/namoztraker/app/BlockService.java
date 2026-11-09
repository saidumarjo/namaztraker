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

        // night lock: every app except phone, alarm clock, keyboard and system UI
        long[] sl = Block.sleep(this, now);
        if (sl != null) {
            if (allowedAtNight(pkg)) return;
            if (now - lastBlock < 700) return;
            lastBlock = now;
            if (!pkg.equals(homePkg())) performGlobalAction(GLOBAL_ACTION_HOME);
            Intent in = new Intent(this, SleepActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("start", sl[0]).putExtra("until", sl[1]);
            try { startActivity(in); } catch (Exception ignored) { }
            return;
        }

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

    private static final String[] NIGHT_OK = {
            "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.android.emergency",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
            "com.android.incallui", "com.google.android.apps.safetyhub", "com.sec.android.app.safetyassurance",
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock", "com.oneplus.deskclock", "com.miui.clock", "com.huawei.deskclock",
            "com.android.contacts", "com.samsung.android.app.contacts",
    };
    private String home;

    private String homePkg() {
        if (home == null) {
            try {
                android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
                home = r != null && r.activityInfo != null ? r.activityInfo.packageName : "";
            } catch (Exception e) { home = ""; }
        }
        return home;
    }

    private boolean allowedAtNight(String pkg) {
        for (String p : NIGHT_OK) if (p.equals(pkg)) return true;
        String l = pkg.toLowerCase();
        return l.contains("inputmethod") || l.contains("keyboard") || l.contains("honeyboard") || l.contains("swiftkey");
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
