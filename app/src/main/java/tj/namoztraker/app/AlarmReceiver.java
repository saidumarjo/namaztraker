package tj.namoztraker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {

    private static String str(String s) {
        return s == null ? "" : s;
    }

    /** A duty question ("Имрӯз Қуръон хондӣ?") as a popup; the answer is handed to the app when it opens. */
    static boolean askDuty(Context c, String dq, String day, String question) {
        final Context a = c.getApplicationContext();
        boolean lvl = dq.startsWith("lvl:");
        String[] labels = lvl ? new String[]{"Ҳа, пурра", "Қисман", "Не"} : new String[]{"Ҳа, кардам ✓", "Ҳоло не"};
        int[] vals = lvl ? new int[]{100, 50, 10} : new int[]{1, -1};
        Runnable[] acts = new Runnable[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int v = vals[i];
            acts[i] = v < 0 ? null : () -> {
                android.content.SharedPreferences p = a.getSharedPreferences("nt", Context.MODE_PRIVATE);
                try {
                    org.json.JSONObject o = new org.json.JSONObject(p.getString("dmarks", "{}"));
                    o.put(day + "|" + dq, v);
                    p.edit().putString("dmarks", o.toString()).apply();
                } catch (Exception ignored) { }
                android.widget.Toast.makeText(a, "Офарин! Қайд шуд ✓", android.widget.Toast.LENGTH_SHORT).show();
            };
        }
        return OverlayService.showAsk(a, "🤲", "Ӯҳдадорӣ", question, labels, acts);
    }

    /** "Оё таҳорат доред?" as a popup over other apps; answers go through the same logic as the notification buttons. */
    static void askWudu(Context c, String title, String text, long iq, String key, String name) {
        final Context a = c.getApplicationContext();
        Runnable[] acts = new Runnable[3];
        String[] ans = {"yes", "no", "later"};
        for (int i = 0; i < 3; i++) {
            final String an = ans[i];
            acts[i] = () -> {
                Intent w = new Intent(a, AlarmReceiver.class).setAction(Notif.ACTION_WUDU)
                        .putExtra("ans", an).putExtra("title", title).putExtra("text", text)
                        .putExtra("iq", iq).putExtra("key", key).putExtra("name", name);
                if ("no".equals(an) && a.getSharedPreferences("nt", Context.MODE_PRIVATE).getBoolean("wudu_lock", true)) {
                    w.setClass(a, MainActivity.class).setAction(Notif.ACTION_WUDU_LOCK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try { a.startActivity(w); } catch (Exception e) { Notif.onWudu(a, w); }
                } else {
                    Notif.onWudu(a, w);
                }
            };
        }
        OverlayService.showAsk(a, "💧", "Азони " + (name.isEmpty() ? "намоз" : name), "Оё таҳорат доред?",
                new String[]{"Ҳа", "Не", "3 дақиқа пас"}, acts);
    }
    @Override
    public void onReceive(Context c, Intent in) {
        Widgets.updateAll(c);
        if (Hadith.ACTION.equals(in.getAction())) {
            Hadith.fire(c);
            return;
        }
        if (Notif.ACTION_STOP_ADHAN.equals(in.getAction())) {
            OverlayService.stopAdhan(c);
            return;
        }
        if (Block.ACTION_SLEEP.equals(in.getAction())) {
            Block.scheduleSleep(c);
            return;
        }
        if (Notif.ACTION_WUDU.equals(in.getAction())) {
            Notif.onWudu(c, in);
            return;
        }
        if (Widgets.isMarked(c, in.getStringExtra("mk"))) return;
        String title = in.getStringExtra("title");
        String text = in.getStringExtra("text");
        int id = in.getIntExtra("id", 0);
        long iq = in.getLongExtra("iq", 0L);
        String key = in.getStringExtra("key");
        String name = in.getStringExtra("name");
        boolean ask = in.getBooleanExtra("ask", false);
        if (title == null) title = "НАМОЗХОН TJ";
        if (text == null) text = "";
        String dq = in.getStringExtra("dq");
        if (dq != null && !dq.isEmpty()) {
            if (askDuty(c, dq, str(in.getStringExtra("dy")), str(in.getStringExtra("dn")))) return;
        }
        if (in.getBooleanExtra("az", false)) OverlayService.playAdhan(c, name);
        if (iq > System.currentTimeMillis()) OverlayService.showIqama(c, name, iq);
        if (in.getBooleanExtra("u", false)) {
            final String k = key == null ? "" : key;
            int bar = k.indexOf('|');
            final String day = bar > 0 ? k.substring(0, bar) : "", pk = bar > 0 ? k.substring(bar + 1) : "";
            if (!OverlayService.showAsk(c, "⏳", title, text + "\n\nНамозро хондӣ?",
                    new String[]{"Хондам ✓", "Ҳозир мехонам"},
                    new Runnable[]{() -> { if (!day.isEmpty()) Widgets.mark(c.getApplicationContext(), day, pk); }, null}))
                Notif.showUrgent(c, title, text, k);
        } else if (iq > 0 || ask) {
            Notif.showAdhan(c, title, text, iq, key == null ? "" : key, name == null ? "" : name, ask, true);
            if (ask && !(key != null && !key.isEmpty() && Notif.answered(c, key))) askWudu(c, title, text, iq, key == null ? "" : key, name == null ? "" : name);
        } else {
            Notif.show(c, title, text, id);
        }
    }
}
