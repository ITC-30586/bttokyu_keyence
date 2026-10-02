package com.keyence.bttokyu.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import com.google.android.material.snackbar.Snackbar;
import com.keyence.bttokyu.MainActivity;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.db.DbRepository;
import com.keyence.bttokyu.hardware.BuzzerHelper;
import com.keyence.bttokyu.hardware.VibratorHelper;
import com.keyence.bttokyu.scanner.ScannerManager;
import com.keyence.bttokyu.util.FileLogger;

public abstract class BaseFragment extends Fragment {

    protected final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ---- MainActivityへのアクセス（null安全） ----

    protected MainActivity getMain() {
        return (MainActivity) requireActivity();
    }

    protected ScannerManager scanner() { return getMain().getScannerManager(); }
    protected DbRepository   db()      { return getMain().getDbRepository();   }
    protected VibratorHelper vibrator(){ return getMain().getVibratorHelper();  }
    protected BuzzerHelper   buzzer()  { return getMain().getBuzzerHelper();    }

    // ---- 安全なUI更新（Fragment が Detach 済みなら何もしない） ----

    /** DB/バックグラウンドコールバックから呼ぶときは必ずこちらを使う */
    protected void runOnUi(Runnable r) {
        uiHandler.post(() -> {
            // Fragment がビュー破棄・デタッチ済みの場合はスキップ
            if (!isAdded() || getView() == null) return;
            r.run();
        });
    }

    // ---- 安全な画面遷移 ----

    /**
     * popBackStack の NPE / IME クラッシュを防ぐ安全版。
     *
     * BT-A2000 はスキャナデータをキーストローク経由で IME に送信する。
     * ビュー破棄前に IME を切断しないと commitText → NPE でクラッシュするため、
     * 遷移前に必ず InputMethodManager で接続を解除する。
     */
    protected void safePopBackStack() {
        if (!isAdded()) return;
        try {
            // IME（ソフトキーボード / スキャナキーストローク）の InputConnection を切断
            disconnectIme();
            NavController nav = getMain().getNavController();
            if (nav != null) nav.popBackStack();
        } catch (Exception e) {
            // ignore
        }
    }

    /** IME を非表示にしてフォーカスを解放する（InputConnection 切断） */
    protected void disconnectIme() {
        try {
            if (!isAdded()) return;
            android.app.Activity act = getActivity();
            if (act == null) return;
            View focused = act.getCurrentFocus();
            InputMethodManager imm = (InputMethodManager)
                    act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                // フォーカス中ビューの InputConnection を閉じる
                if (focused != null) {
                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                    focused.clearFocus();
                }
                // ルートビューへフォーカス移動して IME をリセット
                if (getView() != null) {
                    getView().requestFocus();
                    imm.restartInput(getView());
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ---- 安全なコンテキスト取得 ----

    /** requireContext() の代わりに使う。detach 後は null を返す */
    protected Context safeContext() {
        return isAdded() ? requireContext() : null;
    }

    // ---- エラー/OK 表示 ----

    protected void showError(String msg) {
        runOnUi(() -> {
            if (getView() != null) {
                Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();
            }
            if (isAdded()) {
                vibrator().error();
                buzzer().beepError();   // スキャンNGなどの軽微なエラー: 3連短音
            }
        });
    }

    protected void showOk(String msg) {
        runOnUi(() -> {
            if (getView() != null) {
                Snackbar.make(getView(), msg, Snackbar.LENGTH_SHORT).show();
            }
            if (isAdded()) {
                vibrator().ok();
                buzzer().beepOk();      // 正常完了: 短い1音
            }
        });
    }

    // ---- フィールド色制御 ----

    protected void setFieldNg(TextView... views) {
        for (TextView v : views) {
            if (v != null) v.setBackgroundColor(color(R.color.field_ng));
        }
    }

    protected void setFieldOk(TextView... views) {
        for (TextView v : views) {
            if (v != null) v.setBackgroundColor(color(R.color.field_ok));
        }
    }

    protected void setFieldNormal(TextView... views) {
        for (TextView v : views) {
            if (v != null) v.setBackgroundColor(color(android.R.color.white));
        }
    }

    protected void setFieldActive(TextView... views) {
        for (TextView v : views) {
            if (v != null) v.setBackgroundColor(color(R.color.field_active));
        }
    }

    private int color(@ColorRes int resId) {
        Context ctx = safeContext();
        if (ctx == null) return 0;
        return ContextCompat.getColor(ctx, resId);
    }

    // ---- スキャナ有効化/無効化 ----

    /**
     * このFragmentがスキャナを有効化したかどうかのフラグ。
     * 有効化していないFragmentのonDestroyViewがdisableScanner()を呼んでも
     * 次のFragmentのスキャナを止めてしまわないようにする。
     */
    private boolean scannerOwned = false;

    protected void enableScanner(ScannerManager.ScanCallback cb) {
        String fragName = getClass().getSimpleName();
        FileLogger.getInstance(requireContext()).log("BaseFragment",
            fragName + " enableScanner() scannerOwned=" + scannerOwned + " isEnabled=" + scanner().isEnabled());
        scanner().setCallback(cb);
        scanner().enable();
        scannerOwned = true;
        FileLogger.getInstance(requireContext()).log("BaseFragment",
            fragName + " after enableScanner isEnabled=" + scanner().isEnabled());
    }

    protected void disableScanner() {
        String fragName = getClass().getSimpleName();
        FileLogger.getInstance(requireContext()).log("BaseFragment",
            fragName + " disableScanner() scannerOwned=" + scannerOwned);
        if (scannerOwned) {
            scanner().disable();
            scannerOwned = false;
        }
    }

    // ---- DB エラーハンドラー自動登録（全Fragment共通） ----

    @Override
    public void onResume() {
        super.onResume();
        // 画面が前面に来たときにエラーハンドラーを登録する
        if (isAdded()) {
            db().setErrorHandler((funcName, detail) -> showDbError(funcName, detail));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 画面が背後に回るときはハンドラーを解除する（他Fragmentに影響しないよう）
        db().setErrorHandler(null);
    }

    /**
     * DB通信エラーをAlertDialogで表示する。
     * 接続先・原因・対処方法を詳細に表示してユーザーが原因を把握できるようにする。
     */
    protected void showDbError(String funcName, String detail) {
        runOnUi(() -> {
            if (!isAdded() || getView() == null) return;
            Context ctx = safeContext();
            if (ctx == null) return;

            // バイブレータ + ブザー（DB通信エラーは重大なため警告音2回）
            vibrator().error();
            buzzer().beepAlert();

            // スクロール可能なTextViewでエラー詳細を表示
            TextView tv = new TextView(ctx);
            tv.setText(detail);
            tv.setTextSize(14f);
            tv.setPadding(48, 24, 48, 24);
            ScrollView sv = new ScrollView(ctx);
            sv.addView(tv);

            new AlertDialog.Builder(ctx)
                    .setTitle("⚠ DB通信エラー")
                    .setView(sv)
                    .setPositiveButton("閉じる", null)
                    .setCancelable(true)
                    .show();
        });
    }

    // ---- p__check 戻り値解析（5文字: [上段進捗][上段保留][下段進捗][下段保留][品質]）----

    protected boolean isProgress1Ng(String chk) { return chk != null && chk.length() >= 1 && "1".equals(chk.substring(0, 1)); }
    protected boolean isHold1(String chk)        { return chk != null && chk.length() >= 2 && "1".equals(chk.substring(1, 2)); }
    protected boolean isProgress2Ng(String chk)  { return chk != null && chk.length() >= 3 && "1".equals(chk.substring(2, 3)); }
    protected boolean isHold2(String chk)        { return chk != null && chk.length() >= 4 && "1".equals(chk.substring(3, 4)); }
    protected boolean isQualityNg(String chk)    { return chk != null && chk.length() >= 5 && "1".equals(chk.substring(4, 5)); }
    protected boolean isAllOk(String chk)        { return "00000".equals(chk); }
}
