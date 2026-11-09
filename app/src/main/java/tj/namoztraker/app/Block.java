package tj.namoztraker.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Which apps and sites are closed, and when: from adhan for N minutes (until the congregation). */
final class Block {
    private Block() { }

    static final String[][] APPS = {
            {"yt", "YouTube", "com.google.android.youtube", "com.google.android.apps.youtube.music", "app.revanced.android.youtube"},
            {"tt", "TikTok", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.aweme.lite"},
            {"ig", "Instagram", "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona"},
            {"fb", "Facebook", "com.facebook.katana", "com.facebook.lite"},
            {"tg", "Telegram", "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "org.telegram.plus", "com.nekogram.nekox"},
    };

    static final String[][] SITES = {
            {"yt", "youtube.com", "youtu.be"},
            {"tt", "tiktok.com"},
            {"ig", "instagram.com"},
            {"fb", "facebook.com", "fb.com", "fb.watch"},
            {"tg", "telegram.org", "t.me", "telegram.me"},
    };

    /** Active window: prayer name and end time, or null. */
    static final class Win {
        final String name;
        final long start, until;
        Win(String n, long s, long u) { name = n; start = s; until = u; }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nt", Context.MODE_PRIVATE);
    }

    static void save(Context c, String json) {
        prefs(c).edit().putString("block", json).apply();
    }

    private static JSONObject cfg(Context c) {
        try { return new JSONObject(prefs(c).getString("block", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static int minutes(Context c) {
        return Math.max(5, Math.min(90, cfg(c).optInt("min", 20)));
    }

    static Win active(Context c, long now) {
        JSONObject k = cfg(c);
        if (!k.optBoolean("on", false)) return null;
        long len = minutes(c) * 60_000L;
        try {
            JSONArray days = new JSONObject(prefs(c).getString("widget", "{}")).optJSONArray("days");
            if (days == null) return null;
            for (int i = 0; i < days.length(); i++) {
                JSONArray p = days.getJSONObject(i).getJSONArray("p");
                for (int j = 0; j < p.length(); j++) {
                    JSONObject o = p.getJSONObject(j);
                    long s = o.getLong("s");
                    if (now >= s && now < s + len) return new Win(o.optString("n"), s, s + len);
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean enabled(JSONObject k, String id) {
        JSONObject a = k.optJSONObject("apps");
        return a == null || a.optBoolean(id, true);
    }

    /** Display name of the blocked app for this package, or null. */
    static String app(Context c, String pkg) {
        JSONObject k = cfg(c);
        for (String[] a : APPS) {
            if (!enabled(k, a[0])) continue;
            for (int i = 2; i < a.length; i++) if (a[i].equals(pkg)) return a[1];
        }
        return null;
    }

    /** Display name of the blocked site shown in a browser address bar, or null. */
    static String site(Context c, String url) {
        if (url == null) return null;
        String u = url.toLowerCase();
        JSONObject k = cfg(c);
        for (int x = 0; x < SITES.length; x++) {
            String[] s = SITES[x];
            if (!enabled(k, s[0])) continue;
            for (int i = 1; i < s.length; i++) {
                String d = s[i];
                int at = u.indexOf(d);
                if (at < 0) continue;
                char before = at == 0 ? '/' : u.charAt(at - 1);
                int end = at + d.length();
                char after = end >= u.length() ? '/' : u.charAt(end);
                if ((before == '.' || before == '/' || before == ' ') && (after == '/' || after == '?' || after == ' ' || after == '#' || after == ':'))
                    return APPS[x][1];
            }
        }
        return null;
    }
}
