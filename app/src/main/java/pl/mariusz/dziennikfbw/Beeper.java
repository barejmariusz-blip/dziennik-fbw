package pl.mariusz.dziennikfbw;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Loud, bright beep on the alarm (or media) stream. vol 0..1.5 — above 1 the tone is soft-clipped for extra loudness. */
final class Beeper {
    private Beeper() { }

    static void play(int freq, int ms, float vol, boolean alarm) {
        AudioTrack t = null;
        try {
            int rate = 44100, n = Math.max(1, rate * Math.max(60, Math.min(ms, 3000)) / 1000);
            short[] buf = new short[n];
            float gain = Math.max(0f, Math.min(vol, 1.5f));
            int fade = rate / 100;
            for (int i = 0; i < n; i++) {
                double ph = 2 * Math.PI * freq * i / rate;
                double v = Math.sin(ph) + 0.35 * Math.sin(3 * ph) + 0.2 * Math.sin(5 * ph);
                v = Math.tanh(v * gain * 1.6) / Math.tanh(1.6);
                double env = Math.min(1.0, Math.min(i, n - 1 - i) / (double) fade);
                buf[i] = (short) (v * env * 32000);
            }
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(alarm ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            AudioFormat af = new AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            t = new AudioTrack(aa, af, n * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            t.write(buf, 0, n);
            t.play();
            Thread.sleep(ms + 80L);
        } catch (Exception ignored) {
        } finally {
            if (t != null) try { t.release(); } catch (Exception ignored) { }
        }
    }

    /** End of rest: three beeps, the last one higher and longer. Blocks ~1.4 s — call off the main thread. */
    static void triple(int freq, float vol, boolean alarm) {
        play(freq, 260, vol, alarm);
        sleep(120);
        play(freq, 260, vol, alarm);
        sleep(120);
        play(Math.round(freq * 1.25f), 520, vol, alarm);
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
