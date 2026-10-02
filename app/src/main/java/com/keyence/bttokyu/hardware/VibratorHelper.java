package com.keyence.bttokyu.hardware;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * バイブレータ制御 (元: Vibrator(N) 関数の移植)
 * 正常時1回 / エラー時2回、各500msON→100msOFF
 */
public class VibratorHelper {

    private static final long ON_MS  = 500L;
    private static final long OFF_MS = 100L;

    private final Vibrator vibrator;

    public VibratorHelper(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /** n回鳴動。正常=1、エラー=2 */
    public void vibrate(int times) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = buildPattern(times);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    /** OK: 1回 */
    public void ok()    { vibrate(1); }
    /** NG: 2回 */
    public void error() { vibrate(2); }

    private long[] buildPattern(int times) {
        // [delay, on, off, on, off, ...]
        long[] p = new long[1 + times * 2];
        p[0] = 0; // 初期遅延なし
        for (int i = 0; i < times; i++) {
            p[1 + i * 2]     = ON_MS;
            p[1 + i * 2 + 1] = OFF_MS;
        }
        return p;
    }
}
