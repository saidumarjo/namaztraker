package tj.namoztraker.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widgets: next prayer with live countdown and today's five prayers. */
final class Widgets {
    static final String ACTION_REFRESH = "tj.namoztraker.app.WIDGET_REFRESH";
    static final String ACTION_MARK = "tj.namoztraker.app.WIDGET_MARK";
    private static final long MIN30 = 30 * 60_000L;
    private static final long UNDO_MS = 5_000L;
    private static final int REFRESH_CODE = 901;

    private static final int[] CELL = {R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4};
    private static final int[] NAME = {R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4};
    private static final int[] TIME = {R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4};
    private static final int[] STAT = {R.id.w_s0, R.id.w_s1, R.id.w_s2, R.id.w_s3, R.id.w_s4};

    private Widgets() { }

    static void save(Context c, String json) {
        c.getSharedPreferences("nt", Context.MODE_PRIVATE).edit().putString("widget", json).apply();
        updateAll(c);
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] big = m.getAppWidgetIds(new ComponentName(c, WidgetBig.class));
            int[] small = m.getAppWidgetIds(new ComponentName(c, WidgetSmall.class));
            if (big.length == 0 && small.length == 0) return;

            JSONObject d = new JSONObject(c.getSharedPreferences("nt", Context.MODE_PRIVATE).getString("widget", "{}"));
            JSONArray days = d.optJSONArray("days");
            long now = System.currentTimeMillis();
            PendingIntent open = PendingIntent.getActivity(c, 0,
                    new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            RemoteViews vb = new RemoteViews(c.getPackageName(), R.layout.widget_big);
            RemoteViews vs = new RemoteViews(c.getPackageName(), R.layout.widget_small);
            vb.setOnClickPendingIntent(R.id.w_root, open);
            vs.setOnClickPendingIntent(R.id.w_root, open);

            long nextBoundary = 0;
            if (days != null && days.length() > 0) {
                // the prayer day runs from Fajr to the next Fajr
                int idx = 0;
                for (int i = 0; i < days.length(); i++) {
                    JSONArray p = days.getJSONObject(i).getJSONArray("p");
                    if (p.getJSONObject(0).getLong("s") <= now) idx = i;
                }
                JSONObject day = days.getJSONObject(idx);
                JSONArray p = day.getJSONArray("p");

                JSONObject next = null;
                for (int i = 0; i < days.length() && next == null; i++) {
                    JSONArray q = days.getJSONObject(i).getJSONArray("p");
                    for (int j = 0; j < q.length(); j++) {
                        JSONObject o = q.getJSONObject(j);
                        long s = o.getLong("s"), e = o.getLong("e");
                        if (s > now && (nextBoundary == 0 || s < nextBoundary)) nextBoundary = s;
                        if (e > now && (nextBoundary == 0 || e < nextBoundary)) nextBoundary = e;
                        long iqb = o.optLong("iq", 0L);
                        if (iqb > now && (nextBoundary == 0 || iqb < nextBoundary)) nextBoundary = iqb;
                        if (next == null && s > now) next = o;
                    }
                }

                String city = d.optString("city", "");
                String hijri = day.optString("hijri", "");
                vb.setTextViewText(R.id.w_city, city + (hijri.isEmpty() ? "" : " · " + hijri));

                int done = 0;
                for (int j = 0; j < 5 && j < p.length(); j++) {
                    JSONObject o = p.getJSONObject(j);
                    long s = o.getLong("s"), e = o.getLong("e");
                    String st = o.optString("st", "");
                    if (st.isEmpty() && now >= e) st = "missed";
                    boolean cur = now >= s && now < e;
                    vb.setTextViewText(NAME[j], o.optString("n"));
                    vb.setTextViewText(TIME[j], o.optString("h"));
                    vb.setInt(CELL[j], "setBackgroundResource", cur ? R.drawable.w_cell_cur : R.drawable.w_cell);
                    int bg;
                    String mark;
                    switch (st) {
                        case "ontime": bg = R.drawable.w_st_ontime; mark = "✓"; done++; break;
                        case "jamaat": bg = R.drawable.w_st_jamaat; mark = "✓"; done++; break;
                        case "late": bg = R.drawable.w_st_late; mark = "!"; break;
                        case "missed": bg = R.drawable.w_st_missed; mark = "✕"; break;
                        default: bg = R.drawable.w_st_none; mark = ""; break;
                    }
                    vb.setInt(STAT[j], "setBackgroundResource", bg);
                    Intent mi = new Intent(c, WidgetBig.class).setAction(ACTION_MARK)
                            .setData(android.net.Uri.parse("nt://mark/" + day.optString("d") + "/" + o.optString("k")))
                            .putExtra("d", day.optString("d")).putExtra("k", o.optString("k"));
                    vb.setOnClickPendingIntent(CELL[j], PendingIntent.getBroadcast(c, 910 + j, mi,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    vb.setTextViewText(STAT[j], mark);
                }
                vs.setProgressBar(R.id.w_ring, 5, done, false);
                vs.setTextViewText(R.id.w_done, done + "/5");

                // adhan → iqamah: the current prayer has started, is not yet marked and iqamah is ahead
                JSONObject iqP = null;
                for (int j = 0; j < 5 && j < p.length(); j++) {
                    JSONObject o = p.getJSONObject(j);
                    long iqt = o.optLong("iq", 0L);
                    if (iqt > now && o.getLong("s") <= now && o.optString("st", "").isEmpty()) { iqP = o; break; }
                }
                if (iqP != null) {
                    long iqt = iqP.getLong("iq");
                    long base = SystemClock.elapsedRealtime() + (iqt - now);
                    String at = "Қомат соати " + iqP.optString("qh") + (city.isEmpty() ? "" : " · " + city);
                    for (RemoteViews v : new RemoteViews[]{vb, vs}) {
                        v.setTextViewText(R.id.w_next, "Азон шуд · " + iqP.optString("n"));
                        v.setTextViewText(R.id.w_next_at, at);
                        v.setTextViewText(R.id.w_cnt_lbl, "то қомат");
                        v.setTextColor(R.id.w_count, 0xFFB8F07E);
                        v.setChronometer(R.id.w_count, base, null, true);
                        v.setChronometerCountDown(R.id.w_count, true);
                    }
                    vb.setTextViewText(R.id.w_head, "Вақти намоз расид");
                    vb.setTextViewTextSize(R.id.w_next, android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
                    vs.setTextViewTextSize(R.id.w_next, android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
                } else if (next != null) {
                    long s = next.getLong("s");
                    String nm = next.optString("n");
                    String at = "Оғоз " + next.optString("h") + (city.isEmpty() ? "" : " · " + city);
                    long base = SystemClock.elapsedRealtime() + (s - now);
                    vb.setTextViewText(R.id.w_head, "Намози навбатӣ");
                    vb.setTextViewTextSize(R.id.w_next, android.util.TypedValue.COMPLEX_UNIT_SP, 24f);
                    vs.setTextViewTextSize(R.id.w_next, android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
                    vb.setTextColor(R.id.w_count, 0xFFB8F07E);
                    vs.setTextColor(R.id.w_count, 0xFFFFFFFF);
                    for (RemoteViews v : new RemoteViews[]{vb, vs}) {
                        v.setTextViewText(R.id.w_cnt_lbl, "то оғоз");
                        v.setTextViewText(R.id.w_next, nm);
                        v.setTextViewText(R.id.w_next_at, at);
                        v.setChronometer(R.id.w_count, base, null, true);
                        v.setChronometerCountDown(R.id.w_count, true);
                    }
                }
            }
            if (big.length > 0) m.updateAppWidget(big, vb);
            if (small.length > 0) m.updateAppWidget(small, vs);
            scheduleRefresh(c, nextBoundary);
        } catch (Exception ignored) { }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nt", Context.MODE_PRIVATE);
    }

    /** Tap on a prayer cell: mark it as prayed without opening the app; a second tap within 5 s undoes. */
    static void mark(Context c, String d, String k) {
        if (d == null || k == null) return;
        try {
            SharedPreferences sp = prefs(c);
            JSONObject w = new JSONObject(sp.getString("widget", "{}"));
            JSONObject o = find(w, d, k);
            if (o == null) return;
            String nm = o.optString("n");
            long now = System.currentTimeMillis(), s = o.getLong("s"), e = o.getLong("e");
            JSONObject marks = new JSONObject(sp.getString("wmarks", "{}"));
            String key = d + "|" + k;
            String st = o.optString("st", "");
            if (!st.isEmpty()) {
                JSONObject m = marks.optJSONObject(key);
                if (m != null && now - m.optLong("at") < UNDO_MS) {
                    marks.remove(key);
                    o.put("st", "");
                    sp.edit().putString("wmarks", marks.toString()).putString("widget", w.toString()).apply();
                    toast(c, nm + ": қайд бекор шуд");
                } else {
                    toast(c, nm + " аллакай қайд шудааст ва иваз намешавад");
                }
                updateAll(c);
                return;
            }
            if (now < s) { toast(c, "Вақти " + nm + " ҳанӯз нарасидааст"); return; }
            if (now >= e) { toast(c, "Вақти " + nm + " гузаштааст"); return; }
            String ns = now < e - MIN30 ? "ontime" : "late";
            JSONObject m = new JSONObject();
            m.put("s", ns);
            m.put("at", now);
            marks.put(key, m);
            o.put("st", ns);
            sp.edit().putString("wmarks", marks.toString()).putString("widget", w.toString()).apply();
            Notif.cancelUrgent(c);
            Notif.cancelAdhan(c);
            toast(c, nm + (ns.equals("ontime") ? " · ба вақт" : " · дер") + " қайд шуд ✓\nХато бошад, дар 5 сония боз зер кунед");
            updateAll(c);
        } catch (Exception ignored) { }
    }

    private static JSONObject find(JSONObject w, String d, String k) {
        JSONArray days = w.optJSONArray("days");
        if (days == null) return null;
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day == null || !d.equals(day.optString("d"))) continue;
            JSONArray p = day.optJSONArray("p");
            if (p == null) return null;
            for (int j = 0; j < p.length(); j++) {
                JSONObject o = p.optJSONObject(j);
                if (o != null && k.equals(o.optString("k"))) return o;
            }
        }
        return null;
    }

    /** True if the prayer for key "yyyy-mm-dd|k" is already marked (in the app or from the widget). */
    static boolean isMarked(Context c, String key) {
        if (key == null || key.isEmpty()) return false;
        try {
            if (new JSONObject(prefs(c).getString("wmarks", "{}")).has(key)) return true;
            int bar = key.indexOf('|');
            JSONObject o = find(new JSONObject(prefs(c).getString("widget", "{}")), key.substring(0, bar), key.substring(bar + 1));
            if (o == null) return false;
            String st = o.optString("st", "");
            return st.equals("ontime") || st.equals("late") || st.equals("jamaat");
        } catch (Exception e) {
            return false;
        }
    }

    /** Marks older than the undo window, handed to the app once. */
    static String takeMarks(Context c) {
        try {
            SharedPreferences sp = prefs(c);
            JSONObject all = new JSONObject(sp.getString("wmarks", "{}")), out = new JSONObject(), keep = new JSONObject();
            long now = System.currentTimeMillis();
            JSONArray names = all.names();
            if (names == null) return "{}";
            for (int i = 0; i < names.length(); i++) {
                String k = names.getString(i);
                JSONObject m = all.getJSONObject(k);
                if (now - m.optLong("at") >= UNDO_MS) out.put(k, m); else keep.put(k, m);
            }
            sp.edit().putString("wmarks", keep.toString()).apply();
            return out.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void toast(Context c, String t) {
        try { Toast.makeText(c.getApplicationContext(), t, Toast.LENGTH_LONG).show(); } catch (Exception ignored) { }
    }

    private static void scheduleRefresh(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent in = new Intent(c, WidgetBig.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(c, REFRESH_CODE, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now = System.currentTimeMillis();
        if (at <= now) at = now + 30 * 60_000L;
        at += 1500;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
