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
 * Panel11 / 工程75 返却処理
 *
 * <p>VB元ソース DataMake74 / DataMake75 の移植。
 * 上下段HTスキャン（下段任意）。
 * 送信: p70ReturnInTemp(u1, u2orNull, cb)
 */
public class ReturnFragment extends BaseFragment {

    // スキャン状態
    private boolean ud = false;   // false=上段待ち, true=下段待ち（上段スキャン済）
    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private MaterialButton btnSend, btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_return, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tvBn1     = v.findViewById(R.id.tvBn1);
        tvHinban1 = v.findViewById(R.id.tvHinban1);
        tvEban1   = v.findViewById(R.id.tvEban1);
        tvBn2     = v.findViewById(R.id.tvBn2);
        tvHinban2 = v.findViewById(R.id.tvHinban2);
        tvEban2   = v.findViewById(R.id.tvEban2);
        btnSend   = v.findViewById(R.id.btnSend);
        btnBack   = v.findViewById(R.id.btnBack);

        initState();

        btnSend.setOnClickListener(x -> onSend());
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
        btnSend.setEnabled(false);
    }

    /** QRスキャン処理（DataMake74/DataMake75移植） */
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
            btnSend.setEnabled(true);
            vibrator().ok();
        } else {
            // --- 下段スキャン ---
            // 同一UKEYチェック
            if (upper != null && upper.ukey.equals(k.ukey)) {
                showError("上下段が同じかんばんです");
                vibrator().error();
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

    /** 送信ボタン押下 */
    private void onSend() {
        if (upper == null) return;

        String u1 = upper.ukey;
        String u2 = (lower != null) ? lower.ukey : null;

        btnSend.setEnabled(false);

        db().p70ReturnInTemp(u1, u2, result -> runOnUi(() -> {
            // 正常判定: 上段のみ→"0"、上下段→"00"
            boolean ok;
            if (u2 != null) {
                ok = "00".equals(result);
            } else {
                ok = "0".equals(result);
            }

            if (ok) {
                showOk("返却処理しました");
                initState();
            } else {
                setFieldNg(tvBn1, tvHinban1, tvEban1);
                if (lower != null) setFieldNg(tvBn2, tvHinban2, tvEban2);
                vibrator().error();
                vibrator().error();
                showError("返却処理に失敗しました: " + result);
                btnSend.setEnabled(true);
            }
        }));
    }

    /** 戻るボタン押下 */
    private void onBack() {
        vibrator().ok();
        disableScanner();
        safePopBackStack();
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
