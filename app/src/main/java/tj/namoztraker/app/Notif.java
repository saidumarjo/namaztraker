package tj.namoztraker.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Notif {
    static final String CHANNEL = "namoz_times";
    static final String ACTION_WUDU = "tj.namoztraker.app.WUDU";
    static final String ACTION_WUDU_LOCK = "tj.namoztraker.app.WUDU_LOCK";
    private static final String PREFS = "nt";
    private static final int MAX = 200;
    private static final int IQAMA_ID = 77;
    private static final int REASK_CODE = 600;

    private Notif() { }

    static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Вақти намоз", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Азон, саволи таҳорат, ҳисоби баръакс то қомат ва ёдрасҳо");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        nm.createNotificationChannel(ch);
    }

    static void saveAndSchedule(Context c, String json) {
        prefs(c).edit().putString("schedule", json).apply();
        schedule(c);
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        int count = 0;
        try {
            JSONArray a = new JSONArray(prefs(c).getString("schedule", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length() && count < MAX; i++) {
                JSONObject o = a.getJSONObject(i);
                long t = o.getLong("t");
                if (t <= now) continue;
                Intent in = new Intent(c, AlarmReceiver.class);
                in.putExtra("title", o.optString("title"));
                in.putExtra("text", o.optString("text"));
                in.putExtra("id", count);
                in.putExtra("iq", o.optLong("iq", 0L));
                in.putExtra("key", o.optString("k", ""));
                in.putExtra("name", o.optString("n", ""));
                in.putExtra("ask", o.optBoolean("ask", false));
                PendingIntent pi = PendingIntent.getBroadcast(c, count, in,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                setAlarm(am, t, pi);
                count++;
            }
        } catch (Exception ignored) { }
        for (int i = count; i < MAX; i++) {
            Intent in = new Intent(c, AlarmReceiver.class);
            PendingIntent old = PendingIntent.getBroadcast(c, i, in,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
        }
    }

    private static void setAlarm(AlarmManager am, long t, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    /** Plain reminder notification. */
    static void show(Context c, String title, String text, int id) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        createChannels(c);
        Notification.Builder b = base(c, title, text).setAutoCancel(true);
        nm.notify(1000 + id, b.build());
    }

    /** Adhan notification: live countdown to iqamah, optionally asking about wudu. */
    static void showAdhan(Context c, String title, String text, long iqama, String key, String name, boolean ask, boolean alert) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        createChannels(c);
        long now = System.currentTimeMillis();
        String answer = key.isEmpty() ? "" : prefs(c).getString("wudu_" + key, "");
        if (!answer.isEmpty()) ask = false;
        String body = ask ? "Оё таҳорат доред?" + (text.isEmpty() ? "" : "\n" + text) : text;
        Notification.Builder b = base(c, title, body);
        if (iqama > now) {
            b.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(iqama)
                    .setShowWhen(true)
                    .setOngoing(ask)
                    .setTimeoutAfter(iqama - now)
                    .setSubText("то қомат");
        } else {
            b.setAutoCancel(true);
        }
        if (!alert) b.setOnlyAlertOnce(true);
        if (ask) {
            b.addAction(action(c, "yes", "Ҳа", title, text, iqama, key, name, 1));
            b.addAction(action(c, "no", "Не", title, text, iqama, key, name, 2));
            b.addAction(action(c, "later", "3 дақиқа пас", title, text, iqama, key, name, 3));
        }
        nm.notify(IQAMA_ID, b.build());
    }

    /** Handles a tap on Ҳа / Не / 3 дақиқа пас. */
    static void onWudu(Context c, Intent in) {
        String ans = in.getStringExtra("ans");
        String title = str(in.getStringExtra("title"));
        String text = str(in.getStringExtra("text"));
        String key = str(in.getStringExtra("key"));
        String name = str(in.getStringExtra("name"));
        long iq = in.getLongExtra("iq", 0L);
        long now = System.currentTimeMillis();
        String q = iq > now ? "Қомат соати " + hm(iq) + "." : "";
        if ("yes".equals(ans)) {
            if (!key.isEmpty()) saveAnswer(c, key, "yes");
            showAdhan(c, title, "Офарин! Ба намоз омода шавед. " + q, iq, key, name, false, false);
        } else if ("no".equals(ans)) {
            if (!key.isEmpty()) saveAnswer(c, key, "no");
            showAdhan(c, title, "Ҳозир таҳорат гиред: рӯй, ду даст то оринҷ, масҳи сар, ду пой то буҷулак. " + q, iq, key, name, false, false);
            reask(c, title, text, iq, key, name, now + 5 * 60_000L);
        } else {
            showAdhan(c, title, "Пас аз 3 дақиқа боз мепурсам. " + q, iq, key, name, false, false);
            reask(c, title, text, iq, key, name, now + 3 * 60_000L);
        }
    }

    private static void reask(Context c, String title, String text, long iq, String key, String name, long at) {
        if (iq > 0 && at >= iq) return;
        if (!key.isEmpty()) prefs(c).edit().remove("wudu_" + key).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent in = new Intent(c, AlarmReceiver.class);
        in.putExtra("title", title.startsWith("Таҳорат") ? title : "Таҳорат гирифтед? · " + name);
        in.putExtra("text", text);
        in.putExtra("id", REASK_CODE);
        in.putExtra("iq", iq);
        in.putExtra("key", key);
        in.putExtra("name", name);
        in.putExtra("ask", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, REASK_CODE, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(am, at, pi);
    }

    private static Notification.Action action(Context c, String ans, String label, String title, String text,
                                              long iq, String key, String name, int code) {
        Intent in = new Intent(c, AlarmReceiver.class);
        in.setAction(ACTION_WUDU);
        in.putExtra("ans", ans);
        in.putExtra("title", title);
        in.putExtra("text", text);
        in.putExtra("iq", iq);
        in.putExtra("key", key);
        in.putExtra("name", name);
        PendingIntent pi;
        if ("no".equals(ans) && prefs(c).getBoolean("wudu_lock", true)) {
            // open the app and lock other apps for 5 minutes
            in.setClass(c, MainActivity.class);
            in.setAction(ACTION_WUDU_LOCK);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pi = PendingIntent.getActivity(c, 700 + code, in,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pi = PendingIntent.getBroadcast(c, 700 + code, in,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        return new Notification.Action.Builder(null, label, pi).build();
    }

    private static Notification.Builder base(Context c, String title, String text) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF93D45C)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi);
    }

    private static void saveAnswer(Context c, String key, String ans) {
        SharedPreferences p = prefs(c);
        try {
            JSONObject o = new JSONObject(p.getString("wudu_all", "{}"));
            o.put(key, ans);
            p.edit().putString("wudu_" + key, ans).putString("wudu_all", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    static String answers(Context c) {
        return prefs(c).getString("wudu_all", "{}");
    }

    static void setAnswer(Context c, String key, String ans) {
        saveAnswer(c, key, ans);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && "yes".equals(ans)) nm.cancel(IQAMA_ID);
    }

    private static String hm(long t) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t));
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
