package com.keyence.bttokyu.hardware;

import android.content.Context;
import android.util.Log;

import com.keyence.autoid.sdk.notification.Notification;

/**
 * KEYENCE BT-A シリーズ ブザー制御ラッパー。
 *
 * リファレンス: BTA2000_1000Reference.md — Notification クラス
 *   startBuzzer(tone, onPeriod, offPeriod, repeatCount)
 *     tone        : 1〜16（音のトーン）
 *     onPeriod    : 1〜5000ms（鳴動時間）
 *     offPeriod   : 1〜5000ms（消音時間）
 *     repeatCount : 1〜10（繰り返し回数）
 *
 * 使い分け:
 *   beepOk()    → 正常完了: 短い1音 (高め)
 *   beepError() → エラー: 3連短音 (低め) ← DBエラー・スキャンNG時
 *   beepAlert() → 警告: 2回長音 (中程度)
 */
public class BuzzerHelper {

    private static final String TAG = "BuzzerHelper";

    // ブザートーン番号（1〜16: 数値が大きいほど高音）
    private static final int TONE_OK    = 12;   // 高め：正常
    private static final int TONE_ERROR = 4;    // 低め：エラー
    private static final int TONE_ALERT = 8;    // 中程度：警告

    private final Notification notification;

    public BuzzerHelper(Context context) {
        Notification n = null;
        try {
            // createNotification() は ModelUtil でモデルを自動検出するファクトリーメソッド
            n = Notification.createNotification(context);
            if (n != null) {
                Log.i(TAG, "BuzzerHelper initialized: " + n.getClass().getSimpleName());
            } else {
                Log.w(TAG, "createNotification() returned null");
            }
        } catch (Exception e) {
            Log.e(TAG, "BuzzerHelper init error", e);
        }
        this.notification = n;
    }

    /**
     * 正常完了音: 短い1音（高め）
     * 読取成功・登録完了など
     */
    public void beepOk() {
        beep(TONE_OK, 150, 0, 1);
    }

    /**
     * エラー音: 3連短音（低め）
     * DB通信エラー・スキャンNG・品番不一致など
     */
    public void beepError() {
        beep(TONE_ERROR, 200, 100, 3);
    }

    /**
     * 警告音: 2回長音（中程度）
     * 保留品・前工程未完了など注意が必要な場合
     */
    public void beepAlert() {
        beep(TONE_ALERT, 400, 150, 2);
    }

    /** ブザー停止 */
    public void stop() {
        if (notification == null) return;
        try {
            notification.stopBuzzer();
        } catch (Exception e) {
            Log.w(TAG, "stopBuzzer error", e);
        }
    }

    private void beep(int tone, int onMs, int offMs, int repeat) {
        if (notification == null) return;
        try {
            // offPeriod=0 はSDKが最小値1として扱う可能性があるため最小値を保証
            int off = (offMs < 1 && repeat > 1) ? 1 : offMs;
            notification.startBuzzer(tone, onMs, off, repeat);
        } catch (Exception e) {
            Log.w(TAG, "startBuzzer error", e);
        }
    }

    /** リソース解放（MainActivity.onDestroy() で呼ぶ） */
    public void release() {
        if (notification == null) return;
        try {
            notification.releaseNotification();
        } catch (Exception e) {
            Log.w(TAG, "releaseNotification error", e);
        }
    }
}
