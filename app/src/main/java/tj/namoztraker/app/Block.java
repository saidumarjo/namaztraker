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
        final String name, day, key;
        final long start, until;
        final boolean qaza;
        Win(String n, long s, long u, boolean q, String d, String k) { name = n; start = s; until = u; qaza = q; day = d; key = k; }
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

    /** Night lock: {start, end} of the current sleep window, or null. */
    static long[] sleep(Context c, long now) {
        JSONObject k = cfg(c);
        if (!k.optBoolean("slp", false)) return null;
        int[] f = hm(k.optString("sf", "00:00")), t = hm(k.optString("st", "04:00"));
        for (int d = -1; d <= 0; d++) {
            java.util.Calendar a = java.util.Calendar.getInstance();
            a.setTimeInMillis(now);
            a.add(java.util.Calendar.DAY_OF_MONTH, d);
            a.set(java.util.Calendar.HOUR_OF_DAY, f[0]);
            a.set(java.util.Calendar.MINUTE, f[1]);
            a.set(java.util.Calendar.SECOND, 0);
            a.set(java.util.Calendar.MILLISECOND, 0);
            java.util.Calendar b = (java.util.Calendar) a.clone();
            b.set(java.util.Calendar.HOUR_OF_DAY, t[0]);
            b.set(java.util.Calendar.MINUTE, t[1]);
            if (!b.after(a)) b.add(java.util.Calendar.DAY_OF_MONTH, 1);
            long s = a.getTimeInMillis(), e = b.getTimeInMillis();
            if (now >= s && now < e) {
                if (prefs(c).getLong("sleep_skip", 0L) >= e) return null;
                return new long[]{s, e};
            }
        }
        return null;
    }

    /** Next Fajr start from the synced prayer times, or 0. */
    static long nextFajr(Context c, long now) {
        try {
            JSONArray days = new JSONObject(prefs(c).getString("widget", "{}")).optJSONArray("days");
            if (days == null) return 0;
            for (int i = 0; i < days.length(); i++) {
                long s = days.getJSONObject(i).getJSONArray("p").getJSONObject(0).getLong("s");
                if (s > now) return s;
            }
        } catch (Exception ignored) { }
        return 0;
    }

    static void skipSleep(Context c, long until) {
        prefs(c).edit().putLong("sleep_skip", until).apply();
    }

    private static int[] hm(String s) {
        try {
            String[] p = s.split(":");
            return new int[]{Math.max(0, Math.min(23, Integer.parseInt(p[0].trim()))), Math.max(0, Math.min(59, Integer.parseInt(p[1].trim())))};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /** Share of the prayer window left when the "becoming qaza" block starts. */
    static final double QAZA_SHARE = 0.20;

    static Win active(Context c, long now) {
        JSONObject k = cfg(c);
        boolean adhan = k.optBoolean("on", false), qz = k.optBoolean("qz", false);
        if (!adhan && !qz) return null;
        long len = minutes(c) * 60_000L;
        try {
            JSONArray days = new JSONObject(prefs(c).getString("widget", "{}")).optJSONArray("days");
            if (days == null) return null;
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                String d = day.optString("d");
                JSONArray p = day.getJSONArray("p");
                for (int j = 0; j < p.length(); j++) {
                    JSONObject o = p.getJSONObject(j);
                    long s = o.getLong("s"), e = o.getLong("e");
                    String key = o.optString("k");
                    // 1) the last 20% of the prayer time, while it is still not prayed: closed until it is marked
                    if (qz && now < e && now >= e - (long) ((e - s) * QAZA_SHARE)
                            && !Widgets.isMarked(c, d + "|" + key))
                        return new Win(o.optString("n"), s, e, true, d, key);
                    // 2) from adhan for N minutes (until the congregation)
                    if (adhan && now >= s && now < s + len) return new Win(o.optString("n"), s, s + len, false, d, key);
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
