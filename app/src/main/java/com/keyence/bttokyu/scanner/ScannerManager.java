package com.keyence.bttokyu.scanner;

import android.content.Context;
import android.util.Log;

import com.keyence.autoid.sdk.scan.DecodeResult;
import com.keyence.autoid.sdk.scan.ScanManager;
import com.keyence.autoid.sdk.scan.scanparams.DataOutput;
import com.keyence.bttokyu.util.FileLogger;

/**
 * KEYENCE スキャナー制御ラッパー。
 * サンプル(Sample/MainActivity.java)準拠:
 *   createScanManager() / getConfig-setConfig / addDataListener-removeDataListener / releaseScanManager
 */
public class ScannerManager {

    private static final String TAG = "BtScanner";
    public static final String INTENT_ACTION   = "com.keyence.bttokyu.SCAN_DATA";
    public static final String INTENT_KEY_DATA = "data";

    @FunctionalInterface
    public interface ScanCallback {
        void onScanData(String rawData);
    }

    private final ScanManager sdkScanner;
    private final Context appContext;
    private ScanCallback callback;
    private boolean isEnabled = false;

    private final DataOutput dataOutput = new DataOutput();
    private boolean defaultKeyStrokeEnabled = true;

    // SDK DataListener (コンストラクタで初期化)
    private ScanManager.DataListener internalListener;

    public ScannerManager(Context context) {
        this.appContext = context.getApplicationContext();
        FileLogger.getInstance(appContext).section("ScannerManager init");
        ScanManager tmp = null;
        try {
            // サンプル通り: createScanManager() で自動モデル検出
            tmp = ScanManager.createScanManager(context.getApplicationContext());
            if (tmp != null) {
                Log.i(TAG, "createScanManager OK: " + tmp.getClass().getSimpleName());
                FileLogger.getInstance(appContext).log("Scanner",
                    "createScanManager OK: " + tmp.getClass().getSimpleName());
            } else {
                Log.e(TAG, "createScanManager returned null");
                FileLogger.getInstance(appContext).log("Scanner", "createScanManager returned NULL");
            }
        } catch (Exception e) {
            Log.e(TAG, "createScanManager error", e);
            FileLogger.getInstance(appContext).log("Scanner",
                "createScanManager EXCEPTION: " + e.getMessage(), e);
        }
        sdkScanner = tmp;

        // appContext 設定後にリスナーを初期化
        internalListener = decodeResult -> {
            try {
                if (decodeResult == null) return;
                String d = decodeResult.getData();
                FileLogger.getInstance(appContext).log("DataListener",
                    "FIRED result=" + decodeResult.getResult() + " data=[" + d + "]");
                Log.i(TAG, "DataListener fired! data=[" + d + "]");
                if (d != null && !d.isEmpty() && callback != null) {
                    callback.onScanData(d.trim());
                }
            } catch (Exception ex) {
                Log.e(TAG, "DataListener exception", ex);
            }
        };
    }

    public void setCallback(ScanCallback cb) { this.callback = cb; }

    /** enable(): サンプルの onResume 相当 */
    public void enable() {
        FileLogger.getInstance(appContext).log("Scanner",
            "enable() sdkScanner=" + (sdkScanner!=null?"OK":"NULL") + " isEnabled=" + isEnabled);
        if (sdkScanner == null || isEnabled) return;
        sdkScanner.unlockScanner();
        configScanner();
        com.keyence.autoid.sdk.SdkStatus status = sdkScanner.addDataListener(internalListener);
        isEnabled = true;
        Log.i(TAG, "Scanner ENABLED, addDataListener status=" + status);
        FileLogger.getInstance(appContext).log("Scanner",
            "addDataListener status=" + status + " isEnabled=true");
    }

    /** disable(): サンプルの onPause 相当 */
    public void disable() {
        FileLogger.getInstance(appContext).log("Scanner", "disable() isEnabled=" + isEnabled);
        if (sdkScanner == null || !isEnabled) return;
        sdkScanner.unlockScanner();
        restoreConfig();
        sdkScanner.removeDataListener(internalListener);
        isEnabled = false;
    }

    /** configScanner(): getConfig してから keyStroke を無効化して setConfig */
    private void configScanner() {
        try {
            sdkScanner.getConfig(dataOutput);
            defaultKeyStrokeEnabled = dataOutput.keyStrokeOutput.enabled;
            dataOutput.keyStrokeOutput.enabled = false;
            sdkScanner.setConfig(dataOutput);
            Log.i(TAG, "configScanner: keyStroke disabled (was " + defaultKeyStrokeEnabled + ")");
            FileLogger.getInstance(appContext).log("Scanner",
                "configScanner: keyStroke disabled (was " + defaultKeyStrokeEnabled + ")");
        } catch (Exception e) {
            Log.w(TAG, "configScanner: " + e.getMessage());
            FileLogger.getInstance(appContext).log("Scanner", "configScanner warn: " + e.getMessage());
        }
    }

    private void restoreConfig() {
        try {
            dataOutput.keyStrokeOutput.enabled = defaultKeyStrokeEnabled;
            sdkScanner.setConfig(dataOutput);
        } catch (Exception e) {
            Log.w(TAG, "restoreConfig: " + e.getMessage());
        }
    }

    /** releaseScanManager(): サンプルの onDestroy 相当 */
    public void release() {
        if (sdkScanner != null) {
            try {
                sdkScanner.releaseScanManager();
                FileLogger.getInstance(appContext).log("Scanner", "releaseScanManager called");
            } catch (Exception e) {
                Log.e(TAG, "release error", e);
            }
        }
    }

    /** ウェジェット経由のスキャンデータ配信 (DDモデルフォールバック) */
    public void deliverWedgeScan(String data) {
        FileLogger.getInstance(appContext).log("Wedge",
            "deliverWedgeScan: [" + data + "] callback=" + (callback!=null?"set":"NULL"));
        if (callback != null && data != null && !data.isEmpty()) {
            callback.onScanData(data);
        }
    }

    public boolean isEnabled() { return isEnabled; }
}
