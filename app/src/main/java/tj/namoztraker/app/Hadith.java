package tj.namoztraker.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** A short hadith in the notification shade every N minutes; it disappears by itself after 3 minutes. */
final class Hadith {
    static final String ACTION = "tj.namoztraker.app.HADITH";
    private static final String CH = "namoz_hadith";
    private static final int CODE = 903, NOTE_ID = 81;

    private Hadith() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nt", Context.MODE_PRIVATE);
    }

    static void save(Context c, String json) {
        prefs(c).edit().putString("hadith", json).apply();
        schedule(c);
    }

    private static JSONObject cfg(Context c) {
        try { return new JSONObject(prefs(c).getString("hadith", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, CODE, new Intent(c, AlarmReceiver.class).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        JSONObject k = cfg(c);
        if (!k.optBoolean("on", false)) return;
        long every = Math.max(10, Math.min(120, k.optInt("every", 20))) * 60_000L;
        long t = System.currentTimeMillis() + every;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } catch (Exception ignored) { }
    }

    /** Alarm fired: show the next hadith unless it is night, then schedule the following one. */
    static void fire(Context c) {
        try {
            JSONObject k = cfg(c);
            if (!k.optBoolean("on", false)) return;
            long now = System.currentTimeMillis();
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean night = Block.sleep(c, now) != null || h >= 23 || now < fajrToday(c, now);
            JSONArray l = k.optJSONArray("list");
            if (!night && l != null && l.length() > 0) {
                int i = prefs(c).getInt("hadith_i", 0) % l.length();
                prefs(c).edit().putInt("hadith_i", i + 1).apply();
                JSONObject o = l.getJSONObject(i);
                String t = o.optString("t", "Ҳадис"), x = o.optString("x", "");
                if (!OverlayService.showHadith(c, t, x)) show(c, t, x);
            }
        } catch (Exception ignored) { }
        schedule(c);
    }

    /** Start of today's Fajr from the synced prayer times (0 if unknown): nothing is shown before it. */
    private static long fajrToday(Context c, long now) {
        try {
            JSONArray days = new JSONObject(prefs(c).getString("widget", "{}")).optJSONArray("days");
            if (days == null) return 0;
            long best = 0;
            for (int i = 0; i < days.length(); i++) {
                long s = days.getJSONObject(i).getJSONArray("p").getJSONObject(0).getLong("s");
                if (s <= now + 12 * 3600_000L && s > now - 20 * 3600_000L) best = Math.max(best, s);
            }
            return best > now ? best : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    static void show(Context c, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Ҳадиси кӯтоҳ", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Ҳар чанд дақиқа як ҳадис; пас аз 3 дақиқа худаш нест мешавад");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFE6BC5E)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(3 * 60_000L)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        nm.notify(NOTE_ID, n);
    }
}
