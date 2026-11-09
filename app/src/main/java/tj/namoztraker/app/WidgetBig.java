package tj.namoztraker.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetBig extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.updateAll(c);
    }

    @Override
    public void onReceive(Context c, Intent in) {
        super.onReceive(c, in);
        if (Widgets.ACTION_REFRESH.equals(in.getAction())) Widgets.updateAll(c);
    }
}
