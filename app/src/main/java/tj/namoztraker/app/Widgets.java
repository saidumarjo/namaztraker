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

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widgets: next prayer with live countdown and today's five prayers. */
final class Widgets {
    static final String ACTION_REFRESH = "tj.namoztraker.app.WIDGET_REFRESH";
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
                    vb.setTextViewText(STAT[j], mark);
                }
                vs.setProgressBar(R.id.w_ring, 5, done, false);
                vs.setTextViewText(R.id.w_done, done + "/5");

                if (next != null) {
                    long s = next.getLong("s");
                    String nm = next.optString("n");
                    String at = "Оғоз " + next.optString("h") + (city.isEmpty() ? "" : " · " + city);
                    long base = SystemClock.elapsedRealtime() + (s - now);
                    for (RemoteViews v : new RemoteViews[]{vb, vs}) {
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
