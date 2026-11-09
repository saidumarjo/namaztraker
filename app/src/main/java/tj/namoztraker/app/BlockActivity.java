package tj.namoztraker.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen "prayer time" page shown instead of a blocked app. */
public class BlockActivity extends Activity {

    private static final String[] HADITH = {
            "«Аввалин чизе, ки рӯзи қиёмат аз банда ҳисоб гирифта мешавад, намоз аст.» (Тирмизӣ)",
            "«Намоз сутуни дин аст.» (Байҳақӣ)",
            "«Намози ҷамоат аз намози танҳо 27 дараҷа афзалтар аст.» (Бухорӣ, Муслим)",
            "«Фарқи байни мусалмон ва кофир тарки намоз аст.» (Муслим)",
            "«Беҳтарин амал намоз дар аввали вақти он аст.» (Тирмизӣ)",
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView count, sub;
    private long until;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long left = until - System.currentTimeMillis();
            if (left <= 0) { finish(); return; }
            long s = left / 1000;
            count.setText(String.format(Locale.US, "%d:%02d", s / 60, s % 60));
            ui.postDelayed(this, 500);
        }
    };

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? 0xFF10230F : 0xFFF2F7F4);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        if (primary) {
            g.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            g.setColors(new int[]{0xFF6DBB3C, 0xFFA6DD6A});
        } else {
            g.setColor(0x14FFFFFF);
            g.setStroke((int) dp(1), 0x33FFFFFF);
        }
        b.setBackground(g);
        b.setStateListAnimator(null);
        b.setPadding(0, (int) dp(14), 0, (int) dp(14));
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0A1412);
        getWindow().setNavigationBarColor(0xFF0A1412);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) dp(24);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1E3B2E, 0xFF0F2119, 0xFF0A1412});
        root.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) dp(88), (int) dp(88));
        ip.bottomMargin = (int) dp(18);
        root.addView(icon, ip);

        TextView pre = text("НАМОЗХОН TJ", 13, 0xFFE6BC5E, true);
        pre.setLetterSpacing(0.12f);
        root.addView(pre);

        TextView title = text("Вақти намоз!", 34, 0xFFF2F7F4, true);
        title.setPadding(0, (int) dp(6), 0, (int) dp(6));
        root.addView(title);

        sub = text("", 16, 0xCCF2F7F4, false);
        sub.setLineSpacing(0, 1.25f);
        root.addView(sub);

        count = text("--:--", 64, 0xFFB8F07E, true);
        count.setPadding(0, (int) dp(18), 0, 0);
        root.addView(count);
        root.addView(text("то кушода шудан", 13, 0x99F2F7F4, false));

        TextView hd = text("", 15, 0xD9F2F7F4, false);
        hd.setTypeface(Typeface.SERIF);
        hd.setLineSpacing(0, 1.3f);
        hd.setPadding((int) dp(8), (int) dp(26), (int) dp(8), (int) dp(28));
        hd.setText(HADITH[(int) ((System.currentTimeMillis() / 60000) % HADITH.length)]);
        root.addView(hd);

        Button go = button("Ба намоз меравам", true);
        go.setOnClickListener(v -> home());
        root.addView(go, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button open = button("НАМОЗХОН TJ-ро кушодан", false);
        open.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        op.topMargin = (int) dp(10);
        root.addView(open, op);

        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(root);
        apply(getIntent());
    }

    private void apply(Intent in) {
        String app = in.getStringExtra("app");
        String name = in.getStringExtra("name");
        long start = in.getLongExtra("start", 0L);
        until = in.getLongExtra("until", System.currentTimeMillis() + 60_000L);
        long mins = Math.max(1, Math.round((until - start) / 60000.0));
        String hm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(until));
        sub.setText("Азони " + (name == null ? "намоз" : name) + " шуд. Аз азон то ҷамоат " + mins + " дақиқа вақти намоз аст.\n"
                + (app == null ? "Ин барнома" : app) + " то соати " + hm + " кушода намешавад.");
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        apply(intent);
    }

    private void home() {
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(h); } catch (Exception ignored) { }
        finish();
    }

    @Override
    public void onBackPressed() {
        home();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
    }
}
