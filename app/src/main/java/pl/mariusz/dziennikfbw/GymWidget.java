package pl.mariusz.dziennikfbw;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: next training, days since the last one, today's challenge targets. */
public class GymWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, GymWidget.class));
        if (ids.length > 0) m.updateAppWidget(ids, build(c));
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_gym);
        JSONObject s;
        try { s = new JSONObject(c.getSharedPreferences(ShareProvider.PREFS, Context.MODE_PRIVATE).getString("share", "{}")); }
        catch (Exception e) { s = new JSONObject(); }
        JSONObject next = s.optJSONObject("next");
        v.setTextViewText(R.id.tr, next != null ? next.optString("tr", "—") : "—");
        v.setTextViewText(R.id.plan, next != null ? next.optString("plan", "") : "Otwórz dziennik, żeby wybrać plan");

        String last = "";
        JSONArray ss = s.optJSONArray("sessions");
        if (ss != null && ss.length() > 0) {
            long d = ss.optJSONObject(ss.length() - 1).optLong("date");
            long days = (startOfDay(System.currentTimeMillis()) - startOfDay(d)) / 86_400_000L;
            last = days <= 0 ? "dziś ✓" : days == 1 ? "wczoraj" : days + " dni temu";
        }
        v.setTextViewText(R.id.last, last.isEmpty() ? "" : "ostatnio\n" + last);

        StringBuilder ch = new StringBuilder();
        JSONArray a = s.optJSONArray("ch");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (a != null) for (int i = 0; i < a.length() && i < 3; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            boolean fresh = today.equals(x.optString("day"));
            if (ch.length() > 0) ch.append('\n');
            ch.append(x.optString("em")).append(' ').append(fresh ? x.optString("txt") : x.optString("n"));
            if (fresh && x.optBoolean("done")) ch.append("  ✓");
        }
        v.setTextViewText(R.id.ch, ch.toString());

        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 700, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static long startOfDay(long t) {
        java.util.Calendar k = java.util.Calendar.getInstance();
        k.setTimeInMillis(t);
        k.set(java.util.Calendar.HOUR_OF_DAY, 0);
        k.set(java.util.Calendar.MINUTE, 0);
        k.set(java.util.Calendar.SECOND, 0);
        k.set(java.util.Calendar.MILLISECOND, 0);
        return k.getTimeInMillis();
    }
}
