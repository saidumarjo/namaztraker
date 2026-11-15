package tj.namoztraker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.PowerManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Windows drawn over other apps:
 *  - a small pill counting down from adhan to iqamah, which appears and disappears by itself;
 *  - the full-screen night "sleep time" lock that stays until the night window ends.
 */
public class OverlayService extends Service {

    static final String CH = "namoz_overlay";
    static final String CH_AZ = "namoz_adhan_play";
    private static final int NOTE_ID = 79;

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static View pill, sleep;
    private static TextView pillCount, sleepCount, sleepUntil, sleepFajr;
    private static SleepActivity.Ring sleepRing;
    private static long iqUntil, sleepStart, sleepEnd, sleepHiddenUntil;
    private static boolean ticking;
    private static MediaPlayer player;
    private static long adhanStopAt;
    private static String adhanName = "";

    static boolean adhanPlaying() {
        return player != null;
    }

    /** Plays the adhan: the user's own file if chosen, otherwise the phone's alarm sound. */
    static void playAdhan(Context ctx, String name) {
        final Context c = ctx.getApplicationContext();
        android.content.SharedPreferences p = c.getSharedPreferences("nt", MODE_PRIVATE);
        if (!p.getBoolean("adhan_on", true)) return;
        ui.post(() -> {
            stopPlayer();
            try {
                boolean loud = p.getBoolean("adhan_loud", true);
                String u = p.getString("adhan_uri", "");
                Uri src = u.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(u);
                if (src == null) src = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(loud ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK);
                mp.setDataSource(c, src);
                mp.setLooping(u.isEmpty());
                mp.setOnCompletionListener(m -> stopAdhan(c));
                mp.setOnErrorListener((m, w, x) -> { stopAdhan(c); return true; });
                mp.prepare();
                mp.start();
                player = mp;
                adhanName = name == null ? "" : name;
                adhanStopAt = System.currentTimeMillis() + (u.isEmpty() ? 60_000L : 6 * 60_000L);
                keepAlive(c);
                startTick(c);
                refreshNote(c);
            } catch (Exception e) {
                stopPlayer();
            }
        });
    }

    static void stopAdhan(Context ctx) {
        final Context c = ctx.getApplicationContext();
        ui.post(() -> {
            stopPlayer();
            refreshNote(c);
        });
    }

    private static void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    /** Updates the service notification (shows "stop adhan" while it plays). */
    private static void refreshNote(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            ensureChannels(nm);
            nm.notify(NOTE_ID, buildNote(c));
        } catch (Exception ignored) { }
    }

    private static void ensureChannels(NotificationManager nm) {
        NotificationChannel ch = new NotificationChannel(CH, "Ҳисоби қомат ва вақти хоб", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        NotificationChannel az = new NotificationChannel(CH_AZ, "Садои азон", NotificationManager.IMPORTANCE_LOW);
        az.setShowBadge(false);
        az.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(az);
    }

    private static Notification buildNote(Context c) {
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean az = player != null;
        Notification.Builder b = new Notification.Builder(c, az ? CH_AZ : CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF93D45C)
                .setContentIntent(pi)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (az) {
            b.setContentTitle("Азон · " + (adhanName.isEmpty() ? "вақти намоз" : adhanName))
                    .setContentText("Садои азон садо медиҳад")
                    .addAction(Notif.stopAdhanAction(c))
                    .setDeleteIntent(Notif.stopAdhanIntent(c));
        } else {
            b.setContentTitle(sleep != null || sleepEnd > System.currentTimeMillis() ? "Вақти хоб" : "Ҳисоби баръакс то қомат");
        }
        return b.build();
    }

    static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("nt", MODE_PRIVATE).getBoolean("overlay_on", true);
    }

    static boolean sleepShowing() {
        return sleep != null;
    }

    private static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    private static WindowManager wm(Context c) {
        return (WindowManager) c.getApplicationContext().getSystemService(WINDOW_SERVICE);
    }

    private static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    /* ---------------- adhan → iqamah pill ---------------- */

    static void showIqama(Context ctx, String name, long until) {
        final Context c = ctx.getApplicationContext();
        if (!canDraw(c) || !enabled(c) || until <= System.currentTimeMillis()) return;
        ui.post(() -> {
            try {
                iqUntil = until;
                if (pill == null) {
                    LinearLayout box = new LinearLayout(c);
                    box.setOrientation(LinearLayout.HORIZONTAL);
                    box.setGravity(Gravity.CENTER_VERTICAL);
                    int px = (int) dp(c, 16), py = (int) dp(c, 9);
                    box.setPadding(px, py, px, py);
                    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF21E3B2E, 0xF20F2119});
                    g.setCornerRadius(dp(c, 30));
                    g.setStroke((int) dp(c, 1.5f), 0xCCE6BC5E);
                    box.setBackground(g);
                    box.setElevation(dp(c, 8));
                    LinearLayout col = new LinearLayout(c);
                    col.setOrientation(LinearLayout.VERTICAL);
                    TextView t1 = text(c, "Азони " + (name == null || name.isEmpty() ? "намоз" : name), 12, 0xFFE6BC5E, true);
                    t1.setGravity(Gravity.START);
                    TextView t2 = text(c, "то қомат · " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(until)), 11, 0xBFF2F7F4, false);
                    t2.setGravity(Gravity.START);
                    col.addView(t1);
                    col.addView(t2);
                    box.addView(col);
                    pillCount = text(c, "--:--", 24, 0xFFB8F07E, true);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                    cp.leftMargin = (int) dp(c, 14);
                    box.addView(pillCount, cp);
                    box.setOnClickListener(v -> {
                        stopAdhan(c);
                        try {
                            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        } catch (Exception ignored) { }
                    });
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    lp.y = (int) dp(c, 44);
                    wm(c).addView(box, lp);
                    pill = box;
                }
                keepAlive(c);
                startTick(c);
            } catch (Exception ignored) { }
        });
    }

    private static void removePill(Context c) {
        if (pill != null) {
            try { wm(c).removeView(pill); } catch (Exception ignored) { }
            pill = null;
            pillCount = null;
        }
    }

    /* ---------------- night sleep lock ---------------- */

    static void showSleep(Context ctx, long start, long end) {
        final Context c = ctx.getApplicationContext();
        if (!canDraw(c) || end <= System.currentTimeMillis()) return;
        ui.post(() -> {
            try {
                sleepStart = start;
                sleepEnd = end;
                if (sleep == null && System.currentTimeMillis() >= sleepHiddenUntil) addSleepView(c);
                keepAlive(c);
                startTick(c);
            } catch (Exception ignored) { }
        });
    }

    private static void addSleepView(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) dp(c, 24);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1E3B2E, 0xFF0F2119, 0xFF0A1412}));
        root.setClickable(true);

        TextView moon = text(c, "☾", 52, 0xFFE6BC5E, false);
        moon.setShadowLayer(dp(c, 14), 0, 0, 0x99E6BC5E);
        root.addView(moon);
        TextView pre = text(c, "НАМОЗХОН TJ", 12, 0xFFE6BC5E, true);
        pre.setLetterSpacing(0.14f);
        root.addView(pre);
        TextView title = text(c, "Алон вақти хоб аст", 30, 0xFFF2F7F4, true);
        title.setPadding(0, (int) dp(c, 6), 0, (int) dp(c, 6));
        root.addView(title);
        root.addView(text(c, "Хоб кун то субҳ, то барои намози Бомдод бедор шуда тавонӣ.", 16, 0xCCF2F7F4, false));

        FrameLayout rf = new FrameLayout(c);
        sleepRing = new SleepActivity.Ring(c);
        rf.addView(sleepRing, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout in = new LinearLayout(c);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        sleepCount = text(c, "--:--:--", 44, 0xFFB8F07E, true);
        sleepUntil = text(c, "", 13, 0xA6F2F7F4, false);
        in.addView(sleepCount);
        in.addView(sleepUntil);
        rf.addView(in, new FrameLayout.LayoutParams(-1, -1));
        int size = (int) Math.min(dp(c, 270), c.getResources().getDisplayMetrics().widthPixels * 0.72f);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(size, size);
        rp.topMargin = (int) dp(c, 26);
        rp.bottomMargin = (int) dp(c, 14);
        root.addView(rf, rp);
        sleepFajr = text(c, "", 15, 0xFFE6BC5E, true);
        root.addView(sleepFajr);
        TextView hd = text(c, "«Ҳар кӣ намози Бомдодро бихонад, дар паноҳи Аллоҳ аст.» (Муслим)", 14, 0xBFF2F7F4, false);
        hd.setTypeface(Typeface.SERIF);
        hd.setPadding((int) dp(c, 10), (int) dp(c, 14), (int) dp(c, 10), (int) dp(c, 22));
        root.addView(hd);

        Button call = new Button(c);
        call.setText("☎  Занги фавқулода ё будилник (3 дақиқа)");
        call.setAllCaps(false);
        call.setTextSize(14);
        call.setTextColor(0xD9F2F7F4);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 14));
        g.setColor(0x10FFFFFF);
        g.setStroke((int) dp(c, 1), 0x26FFFFFF);
        call.setBackground(g);
        call.setStateListAnimator(null);
        call.setOnClickListener(v -> {
            sleepHiddenUntil = System.currentTimeMillis() + 3 * 60_000L;
            removeSleep(c);
            try {
                c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        });
        root.addView(call, new LinearLayout.LayoutParams(-1, -2));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        wm(c).addView(root, lp);
        sleep = root;
    }

    private static void removeSleep(Context c) {
        if (sleep != null) {
            try { wm(c).removeView(sleep); } catch (Exception ignored) { }
            sleep = null;
            sleepRing = null;
            sleepCount = null;
        }
    }

    /* ---------------- one ticker for both ---------------- */

    private static void startTick(Context c) {
        if (ticking) return;
        ticking = true;
        ui.post(new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                if (pill != null) {
                    long l = iqUntil - now;
                    if (l <= 0) removePill(c);
                    else if (pillCount != null) {
                        long s = l / 1000;
                        pillCount.setText(String.format(Locale.US, "%d:%02d", s / 60, s % 60));
                    }
                }
                if (sleepEnd > now) {
                    if (sleep == null && now >= sleepHiddenUntil) {
                        try { addSleepView(c); } catch (Exception ignored) { }
                    }
                    if (sleep != null) {
                        long l = sleepEnd - now, s = l / 1000;
                        sleepCount.setText(String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
                        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                        sleepUntil.setText("то бедор шудан · " + f.format(new Date(sleepEnd)));
                        long fj = Block.nextFajr(c, now);
                        sleepFajr.setText(fj > 0 ? "Бомдод соати " + f.format(new Date(fj)) : "");
                        sleepRing.setProgress((float) l / Math.max(1f, (float) (sleepEnd - sleepStart)));
                    }
                } else if (sleep != null || sleepEnd != 0) {
                    removeSleep(c);
                    sleepEnd = 0;
                    Block.scheduleSleep(c);
                }
                if (player != null && now >= adhanStopAt) { stopPlayer(); refreshNote(c); }
                if (pill == null && sleepEnd <= now && player == null) {
                    ticking = false;
                    try { c.stopService(new Intent(c, OverlayService.class)); } catch (Exception ignored) { }
                    return;
                }
                ui.postDelayed(this, 1000);
            }
        });
    }

    /** A foreground service keeps the process (and the countdown) alive while a window is shown. */
    private static void keepAlive(Context c) {
        try {
            Intent i = new Intent(c, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) ensureChannels(nm);
        Notification n = buildNote(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTE_ID, n);
        } catch (Exception e) {
            stopSelf();
        }
        if (pill == null && sleepEnd <= System.currentTimeMillis() && player == null) stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
