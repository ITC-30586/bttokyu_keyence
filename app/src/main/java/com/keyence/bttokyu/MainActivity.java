package com.keyence.bttokyu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.keyence.autoid.sdk.ModelUtil;
import com.keyence.bttokyu.db.DbRepository;
import com.keyence.bttokyu.hardware.BuzzerHelper;
import com.keyence.bttokyu.hardware.VibratorHelper;
import com.keyence.bttokyu.scanner.ScannerManager;
import com.keyence.bttokyu.util.AppSettings;
import com.keyence.bttokyu.util.FileLogger;

/**
 * シングルActivity。NavControllerで19画面のFragmentを管理。
 *
 * スキャンデータ受信方式:
 *   1. DataListener (SDK コールバック) - 全モデル対応（権限ある場合）
 *   2. Intent BroadcastReceiver - DataListenerがNOT_PERMITの場合のフォールバック
 *   3. キーストロークウェジェット - DDモデルの追加フォールバック
 *
 * Fragment側のコードは変更不要。全方式とも ScannerManager.callback を経由する。
 */
public class MainActivity extends AppCompatActivity {

    private ScannerManager scannerManager;
    private DbRepository   dbRepository;
    private VibratorHelper vibratorHelper;
    private BuzzerHelper   buzzerHelper;
    private AppSettings    appSettings;
    private NavController  navController;

    // Intent 経由のスキャンデータ受信
    private BroadcastReceiver scanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // FLAG_ALT_FOCUSABLE_IM は使用しない
        // 理由: DDモデルではウェジェットEditTextでスキャンキーストロークを受け取る必要があり、
        //       このフラグがIMEをブロックするとウェジェットにもデータが届かなくなる。
        //       IMEクラッシュはウェジェットが常時フォーカスを保持することで回避する。

        setContentView(R.layout.activity_main);

        // グローバルクラッシュハンドラー
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                FileLogger fl = FileLogger.getInstance(getApplicationContext());
                fl.log("CRASH", "!!! UNHANDLED EXCEPTION !!!");
                fl.logCrash(thread, throwable);
            } catch (Exception ignored) {}
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
        });

        FileLogger.getInstance(this).section("APP START");
        FileLogger.getInstance(this).log("MainActivity", "App started");

        NavHostFragment host = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (host != null) navController = host.getNavController();

        appSettings    = new AppSettings(this);
        dbRepository   = new DbRepository(appSettings);
        dbRepository.setAppContext(this);
        vibratorHelper = new VibratorHelper(this);
        buzzerHelper   = new BuzzerHelper(this);
        scannerManager = new ScannerManager(this);

        // Intent BroadcastReceiver を登録（DataListener NOT_PERMIT の場合のフォールバック）
        setupIntentReceiver();

        // DDモデルのみウェジェット方式も追加（キーストローク経由）
        if (ModelUtil.isDD()) {
            FileLogger.getInstance(this).log("MainActivity", "DD model: adding keystroke wedge");
            setupScannerWedge();
        }

        FileLogger.getInstance(this).log("MainActivity",
            "ScannerManager ready, isEnabled=" + scannerManager.isEnabled());
    }

    /**
     * Intent BroadcastReceiver のセットアップ。
     * DataListener が NOT_PERMIT の場合でも、スキャナーが Intent を broadcast すれば受信できる。
     * DataListener と Intent 両方が動作しても問題なし（同じ callback に届く）。
     */
    private void setupIntentReceiver() {
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                FileLogger.getInstance(context).log("IntentReceiver",
                    "Received action=" + action + " extras=" + intent.getExtras());

                // スキャンデータを取得（keyData で指定したキー、または一般的なキーを試みる）
                String data = intent.getStringExtra(ScannerManager.INTENT_KEY_DATA);
                if (data == null) data = intent.getStringExtra("scanData");
                if (data == null) data = intent.getStringExtra("com.keyence.autoid.sdk.scan.DATA");
                if (data == null && intent.getExtras() != null) {
                    // どのキーでも最初の文字列を試みる
                    for (String key : intent.getExtras().keySet()) {
                        Object val = intent.getExtras().get(key);
                        if (val instanceof String) {
                            data = (String) val;
                            FileLogger.getInstance(context).log("IntentReceiver",
                                "Found data at key='" + key + "': " + data);
                            break;
                        }
                    }
                }

                if (data != null && !data.isEmpty()) {
                    final String finalData = data;
                    FileLogger.getInstance(context).log("IntentReceiver",
                        "Delivering scan data: [" + finalData + "]");
                    scannerManager.deliverWedgeScan(finalData);
                } else {
                    FileLogger.getInstance(context).log("IntentReceiver",
                        "No scan data found in intent extras");
                }
            }
        };

        IntentFilter filter = new IntentFilter(ScannerManager.INTENT_ACTION);
        registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        FileLogger.getInstance(this).log("MainActivity",
            "IntentReceiver registered for: " + ScannerManager.INTENT_ACTION);
    }

    /** DDモデル専用: キーストロークウェジェット */
    private void setupScannerWedge() {
        EditText wedge = findViewById(R.id.scannerWedge);
        if (wedge == null) return;
        wedge.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (text.isEmpty()) return;
                if (text.contains("\n") || text.contains("\r") || text.length() >= 14) {
                    String data = text.replace("\n", "").replace("\r", "").trim();
                    if (!data.isEmpty()) {
                        FileLogger.getInstance(getApplicationContext())
                            .log("Wedge", "SCAN via keystroke: [" + data + "]");
                        scannerManager.deliverWedgeScan(data);
                    }
                    wedge.post(() -> { wedge.setText(""); wedge.requestFocus(); });
                }
            }
        });
        wedge.requestFocus();
        FileLogger.getInstance(this).log("MainActivity", "Wedge ready");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ModelUtil.isDD()) {
            EditText wedge = findViewById(R.id.scannerWedge);
            if (wedge != null) wedge.post(() -> wedge.requestFocus());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanReceiver != null) {
            try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
        }
        scannerManager.release();  // サンプル: releaseScanManager()
        dbRepository.shutdown();
        buzzerHelper.release();
    }

    // ---- Fragmentからアクセスするゲッター ----
    public ScannerManager getScannerManager() { return scannerManager; }
    public DbRepository   getDbRepository()   { return dbRepository;   }
    public VibratorHelper getVibratorHelper()  { return vibratorHelper; }
    public BuzzerHelper   getBuzzerHelper()    { return buzzerHelper;   }
    public AppSettings    getAppSettings()     { return appSettings;    }
    public NavController  getNavController()   { return navController;  }
}
