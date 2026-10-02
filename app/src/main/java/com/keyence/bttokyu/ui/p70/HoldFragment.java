package com.keyence.bttokyu.ui.p70;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import com.google.android.material.button.MaterialButton;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel16 / 工程72 保留品登録 / 工程73 保留品解除
 *
 * <p>VB元ソース DataMake71 / DataMake72 / Button39 の移植。
 * Bundle引数 "mode"="register" or "release" で動作を切り替える。
 */
public class HoldFragment extends BaseFragment {

    private static final String MODE_REGISTER = "register";
    private static final String MODE_RELEASE  = "release";

    private String mode = MODE_REGISTER;

    // スキャン状態
    private boolean ud = false;          // false=上段待ち, true=下段待ち（または上段スキャン済）
    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvTitle;
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private MaterialButton btnRegister, btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hold, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        // 引数取得
        if (getArguments() != null) {
            mode = getArguments().getString("mode", MODE_REGISTER);
        }

        // View取得
        tvTitle    = v.findViewById(R.id.tvTitle);
        tvBn1      = v.findViewById(R.id.tvBn1);
        tvHinban1  = v.findViewById(R.id.tvHinban1);
        tvEban1    = v.findViewById(R.id.tvEban1);
        tvBn2      = v.findViewById(R.id.tvBn2);
        tvHinban2  = v.findViewById(R.id.tvHinban2);
        tvEban2    = v.findViewById(R.id.tvEban2);
        btnRegister = v.findViewById(R.id.btnRegister);
        btnBack    = v.findViewById(R.id.btnBack);

        // mode別タイトル・ボタンラベル設定
        if (MODE_REGISTER.equals(mode)) {
            tvTitle.setText("工程72 保留品登録");
            btnRegister.setText("保留登録");
        } else {
            tvTitle.setText("工程73 保留品解除");
            btnRegister.setText("保留解除");
        }

        initState();

        btnRegister.setOnClickListener(x -> onRegister());
        btnBack.setOnClickListener(x -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }

    /** 初期状態にリセット */
    private void initState() {
        ud = false;
        upper = null;
        lower = null;
        clearFields();
        btnRegister.setEnabled(false);
    }

    /** QRスキャン処理（DataMake71/DataMake72移植） */
    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) return;

        KanbanData k = QRCodeParser.parseKanban(raw, true);

        if (!ud) {
            // --- 上段スキャン ---
            upper = k;
            tvBn1.setText(k.displayName());
            tvHinban1.setText(k.hinban);
            tvEban1.setText(k.eban);
            setFieldOk(tvBn1, tvHinban1, tvEban1);
            ud = true;
            btnRegister.setEnabled(true);
            vibrator().ok();
        } else {
            // --- 下段スキャン ---
            // 同一UKEY チェック
            if (upper != null && upper.ukey.equals(k.ukey)) {
                showError("上下段が同じかんばんです");
                vibrator().error();
                vibrator().error();
                setFieldNg(tvBn2, tvHinban2, tvEban2);
                return;
            }
            // HID不一致チェック
            if (upper != null && !upper.isEmpty() && !k.isEmpty()
                    && !upper.hid.equals(k.hid)) {
                showError("上下段の品番(HID)が一致しません");
                vibrator().error();
                vibrator().error();
                setFieldNg(tvBn2, tvHinban2, tvEban2);
                return;
            }
            lower = k;
            tvBn2.setText(k.displayName());
            tvHinban2.setText(k.hinban);
            tvEban2.setText(k.eban);
            setFieldOk(tvBn2, tvHinban2, tvEban2);
            vibrator().ok();
        }
    }

    /** 登録/解除ボタン押下（Button39移植） */
    private void onRegister() {
        if (upper == null) return;

        String u1   = upper.ukey;
        String u2   = (lower != null) ? lower.ukey : null;
        String flag = MODE_REGISTER.equals(mode) ? "C" : "R";

        btnRegister.setEnabled(false);

        db().pLotout(u1, u2, flag, result -> runOnUi(() -> {
            // 正常判定: 上段のみ→"0"、上下段→"00"
            boolean ok;
            if (u2 != null) {
                ok = "00".equals(result);
            } else {
                ok = "0".equals(result);
            }

            if (ok) {
                showOk(MODE_REGISTER.equals(mode) ? "保留登録しました" : "保留解除しました");
                initState();
            } else {
                // エラー: フィールド背景赤+バイブ2回
                setFieldNg(tvBn1, tvHinban1, tvEban1);
                if (lower != null) setFieldNg(tvBn2, tvHinban2, tvEban2);
                vibrator().error();
                vibrator().error();
                showError("処理に失敗しました: " + result);
                btnRegister.setEnabled(true);
            }
        }));
    }

    /** 戻るボタン押下 */
    private void onBack() {
        vibrator().ok();
        if (upper != null) {
            // データがある場合はリセット
            initState();
        } else {
            disableScanner();
            safePopBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disableScanner();
    }

    /** 全フィールドをクリア */
    private void clearFields() {
        for (TextView tv : new TextView[]{tvBn1, tvHinban1, tvEban1, tvBn2, tvHinban2, tvEban2}) {
            if (tv != null) {
                tv.setText("");
                tv.setBackgroundColor(Color.WHITE);
            }
        }
    }
}
