package tj.namoztraker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Widgets.updateAll(c);
        if (Notif.ACTION_WUDU.equals(in.getAction())) {
            Notif.onWudu(c, in);
            return;
        }
        String title = in.getStringExtra("title");
        String text = in.getStringExtra("text");
        int id = in.getIntExtra("id", 0);
        long iq = in.getLongExtra("iq", 0L);
        String key = in.getStringExtra("key");
        String name = in.getStringExtra("name");
        boolean ask = in.getBooleanExtra("ask", false);
        if (title == null) title = "НАМОЗХОН TJ";
        if (text == null) text = "";
        if (iq > 0 || ask) {
            Notif.showAdhan(c, title, text, iq, key == null ? "" : key, name == null ? "" : name, ask, true);
        } else {
            Notif.show(c, title, text, id);
        }
    }
}
