package tj.namoztraker.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Night lock screen: "it is time to sleep" with a big circular countdown until the lock ends. */
public class SleepActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private long start, until;
    private boolean preview;
    private Ring ring;
    private TextView count, untilTv, fajrTv, holdBtnLabel;
    private long holdStart = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            long left = until - now;
            if (left <= 0) { finish(); return; }
            long s = left / 1000;
            count.setText(String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
            ring.setProgress((float) left / Math.max(1f, (float) (until - start)));
            if (holdStart > 0) {
                long h = 5 - (now - holdStart) / 1000;
                if (h <= 0) {
                    holdStart = 0;
                    if (!preview) Block.skipSleep(getApplicationContext(), until);
                    Toast.makeText(SleepActivity.this, "Имшаб вақти хоб хомӯш шуд", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                holdBtnLabel.setText("Нигоҳ доред… " + h);
            }
            ui.postDelayed(this, holdStart > 0 ? 200 : 1000);
        }
    };

    /** Big countdown ring. */
    static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float p = 1f;

        Ring(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(0x14FFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            dot.setColor(0xFFB8F07E);
        }

        void setProgress(float v) { p = Math.max(0f, Math.min(1f, v)); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), sw = Math.min(w, h) * 0.055f;
            track.setStrokeWidth(sw);
            arc.setStrokeWidth(sw);
            float r = Math.min(w, h) / 2f - sw;
            box.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r);
            arc.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom, 0xFFE6BC5E, 0xFF93D45C, Shader.TileMode.CLAMP));
            c.drawOval(box, track);
            float sweep = 360f * p;
            c.drawArc(box, -90f + (360f - sweep), sweep, false, arc);
            double a = Math.toRadians(-90f + (360f - sweep));
            c.drawCircle((float) (w / 2f + r * Math.cos(a)), (float) (h / 2f + r * Math.sin(a)), sw * 0.38f, dot);
        }
    }

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

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(0xD9F2F7F4);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(0x10FFFFFF);
        g.setStroke((int) dp(1), 0x26FFFFFF);
        b.setBackground(g);
        b.setStateListAnimator(null);
        b.setPadding((int) dp(12), (int) dp(10), (int) dp(12), (int) dp(10));
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0F2119);
        getWindow().setNavigationBarColor(0xFF0A1412);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) dp(22);
        root.setPadding(pad, (int) dp(40), pad, pad);

        TextView moon = text("☾", 52, 0xFFE6BC5E, false);
        moon.setShadowLayer(dp(14), 0, 0, 0x99E6BC5E);
        root.addView(moon);
        TextView pre = text("НАМОЗХОН TJ", 12, 0xFFE6BC5E, true);
        pre.setLetterSpacing(0.14f);
        root.addView(pre);
        TextView title = text("Алон вақти хоб аст", 30, 0xFFF2F7F4, true);
        title.setPadding(0, (int) dp(6), 0, (int) dp(6));
        root.addView(title);
        TextView sub = text("Хоб кун то субҳ, то барои намози Бомдод бедор шуда тавонӣ.", 16, 0xCCF2F7F4, false);
        sub.setLineSpacing(0, 1.25f);
        root.addView(sub);

        FrameLayout rf = new FrameLayout(this);
        ring = new Ring(this);
        rf.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        count = text("--:--:--", 44, 0xFFB8F07E, true);
        untilTv = text("", 13, 0xA6F2F7F4, false);
        in.addView(count);
        in.addView(untilTv);
        rf.addView(in, new FrameLayout.LayoutParams(-1, -1));
        int size = (int) Math.min(dp(270), getResources().getDisplayMetrics().widthPixels * 0.72f);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(size, size);
        rp.topMargin = (int) dp(26);
        rp.bottomMargin = (int) dp(14);
        root.addView(rf, rp);

        fajrTv = text("", 15, 0xFFE6BC5E, true);
        root.addView(fajrTv);
        TextView hd = text("«Ҳар кӣ намози Бомдодро бихонад, дар паноҳи Аллоҳ аст.» (Муслим)", 14, 0xBFF2F7F4, false);
        hd.setTypeface(Typeface.SERIF);
        hd.setPadding((int) dp(10), (int) dp(14), (int) dp(10), (int) dp(22));
        root.addView(hd);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button call = button("☎  Занги фавқулода");
        call.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        });
        Button hold = button("Хомӯш кардан (5 сония пахш кунед)");
        holdBtnLabel = hold;
        hold.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                holdStart = System.currentTimeMillis();
                ui.removeCallbacks(tick);
                ui.post(tick);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                holdStart = 0;
                hold.setText(preview ? "Пӯшидани намуна (5 сония пахш кунед)" : "Хомӯш кардан (5 сония пахш кунед)");
            }
            return true;
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        bp.rightMargin = (int) dp(5);
        row.addView(call, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.leftMargin = (int) dp(5);
        row.addView(hold, bp2);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1E3B2E, 0xFF0F2119, 0xFF0A1412}));
        sv.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        apply(getIntent());
        if (preview) hold.setText("Пӯшидани намуна (5 сония пахш кунед)");
    }

    private void apply(Intent in) {
        long now = System.currentTimeMillis();
        preview = in.getBooleanExtra("preview", false);
        start = in.getLongExtra("start", now);
        until = in.getLongExtra("until", now + 60_000L);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        untilTv.setText("то бедор шудан · " + f.format(new Date(until)));
        long fj = Block.nextFajr(this, now);
        fajrTv.setText(fj > 0 ? "Бомдод соати " + f.format(new Date(fj)) : "");
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        apply(intent);
    }

    @Override
    public void onBackPressed() {
        if (preview) finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
    }
}
