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

final class Notif {
    static final String CHANNEL = "namoz_times";
    private static final String PREFS = "nt";
    private static final int MAX = 80;

    private Notif() { }

    static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Вақти намоз", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Огоҳӣ дар оғози намоз ва 30 дақиқа пеш аз қазо");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400});
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
                PendingIntent pi = pending(c, count, o.optString("title"), o.optString("text"));
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                }
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

    private static PendingIntent pending(Context c, int code, String title, String text) {
        Intent in = new Intent(c, AlarmReceiver.class);
        in.putExtra("title", title);
        in.putExtra("text", text);
        in.putExtra("id", code);
        return PendingIntent.getBroadcast(c, code, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void show(Context c, String title, String text, int id) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        createChannels(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF1D4F8A)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(1000 + id, n);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
