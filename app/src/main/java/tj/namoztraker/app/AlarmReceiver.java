package tj.namoztraker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        String title = in.getStringExtra("title");
        String text = in.getStringExtra("text");
        int id = in.getIntExtra("id", 0);
        Notif.show(c, title == null ? "НАМОЗХОН TJ" : title, text == null ? "" : text, id);
    }
}
