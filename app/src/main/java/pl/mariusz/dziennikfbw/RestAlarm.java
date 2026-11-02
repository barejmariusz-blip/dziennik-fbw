package pl.mariusz.dziennikfbw;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Rest timer that keeps working with the screen off or the app in the background:
 * exact system alarms at "min" and "max" rest, a loud beep, and a countdown in the notification bar.
 */
public class RestAlarm extends BroadcastReceiver {

    static final String CH_READY = "rest_ready";
    static final String CH_RUN = "rest_run";
    static final int ID_RUN = 501, ID_READY = 502;
    private static final String P = "rest";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel r = new NotificationChannel(CH_READY, "Koniec przerwy", NotificationManager.IMPORTANCE_HIGH);
        r.setDescription("Sygnał, że możesz zaczynać kolejną serię");
        r.setSound(null, null); // the app plays its own loud tone
        r.enableVibration(true);
        r.setVibrationPattern(new long[]{0, 300, 150, 300, 150, 500});
        r.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(r);
        NotificationChannel o = new NotificationChannel(CH_RUN, "Trwająca przerwa", NotificationManager.IMPORTANCE_LOW);
        o.setDescription("Odliczanie przerwy w pasku powiadomień");
        o.setShowBadge(false);
        o.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(o);
    }

    static void start(Context c, long startMs, int minS, int maxS, String label, float vol, boolean alarm) {
        prefs(c).edit().putLong("start", startMs).putInt("min", minS).putInt("max", maxS)
                .putString("label", label).putFloat("vol", vol).putBoolean("alarm", alarm).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        cancelAlarms(c, am);
        long now = System.currentTimeMillis();
        long a = startMs + minS * 1000L, b = startMs + maxS * 1000L;
        if (a > now) set(c, am, a, 1);
        if (maxS > minS && b > now) set(c, am, b, 2);
        ongoing(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_READY);
    }

    static void stop(Context c) {
        prefs(c).edit().putLong("start", 0).apply();
        cancelAlarms(c, (AlarmManager) c.getSystemService(Context.ALARM_SERVICE));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) { nm.cancel(ID_RUN); nm.cancel(ID_READY); }
    }

    private static void set(Context c, AlarmManager am, long at, int which) {
        if (am == null) return;
        PendingIntent pi = pending(c, which);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static void cancelAlarms(Context c, AlarmManager am) {
        if (am == null) return;
        am.cancel(pending(c, 1));
        am.cancel(pending(c, 2));
    }

    private static PendingIntent pending(Context c, int which) {
        Intent i = new Intent(c, RestAlarm.class).setAction("fbw.rest." + which).putExtra("which", which);
        return PendingIntent.getBroadcast(c, 600 + which, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent open(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(c, 610, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Builder builder(Context c, String ch) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
    }

    /** Countdown to "min" rest; after that counts up from the start of the rest. */
    static void ongoing(Context c) {
        channels(c);
        SharedPreferences p = prefs(c);
        long start = p.getLong("start", 0);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (start == 0) { nm.cancel(ID_RUN); return; }
        long ready = start + p.getInt("min", 0) * 1000L;
        boolean before = System.currentTimeMillis() < ready;
        Notification.Builder b = builder(c, CH_RUN)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF8FCF5C)
                .setContentTitle(before ? "Przerwa — odpoczywasz" : "Przerwa minęła — czas na serię")
                .setContentText(p.getString("label", "Przerwa"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(before ? ready : start)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open(c));
        if (before && Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        nm.notify(ID_RUN, b.build());
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        SharedPreferences p = prefs(c);
        if (p.getLong("start", 0) == 0) return;
        int which = intent.getIntExtra("which", 1);
        ongoing(c);
        if (MainActivity.visible) return; // the open app beeps by itself
        final PendingResult pr = goAsync();
        final float vol = p.getFloat("vol", 1f);
        final boolean alarm = p.getBoolean("alarm", true);
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                long[] pat = {0, 300, 150, 300, 150, 500};
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1));
                else v.vibrate(pat, -1);
            }
        } catch (Exception ignored) { }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            channels(c);
            Notification.Builder b = builder(c, CH_READY)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(0xFF8FCF5C)
                    .setContentTitle(which == 1 ? "Koniec przerwy — możesz zaczynać" : "Maksymalna przerwa minęła")
                    .setContentText(p.getString("label", "Przerwa"))
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(open(c));
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
            nm.notify(ID_READY, b.build());
        }
        if (vol <= 0) { pr.finish(); return; }
        new Thread(() -> {
            try { Beeper.triple(which == 1 ? 880 : 660, vol, alarm); } finally { pr.finish(); }
        }).start();
    }
}
