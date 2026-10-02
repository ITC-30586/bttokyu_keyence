package com.keyence.bttokyu.ui.p40;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.*;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel7 / 工程41 搬出進捗確認（NGレーン）
 * VB元ソース DataMake41 の移植。
 * DB送信は行わない。上下段HTスキャン後 p40_check() を呼んで結果を表示するのみ。
 */
public class NglaneOutputFragment extends BaseFragment {

    // スキャン段階
    private static final int STEP_HT1 = 0; // 上段待ち
    private static final int STEP_HT2 = 1; // 下段待ち
    private static final int STEP_DONE = 2; // 照会済み

    private int step = STEP_HT1;
    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvStep1, tvStep2;
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private TextView tvStatus;
    private Button   btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nglane_output, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvStep1   = view.findViewById(R.id.tvStep1);
        tvStep2   = view.findViewById(R.id.tvStep2);
        tvBn1     = view.findViewById(R.id.tvBn1);
        tvHinban1 = view.findViewById(R.id.tvHinban1);
        tvEban1   = view.findViewById(R.id.tvEban1);
        tvBn2     = view.findViewById(R.id.tvBn2);
        tvHinban2 = view.findViewById(R.id.tvHinban2);
        tvEban2   = view.findViewById(R.id.tvEban2);
        tvStatus  = view.findViewById(R.id.tvStatus);
        btnBack   = view.findViewById(R.id.btnBack);

        initState();

        btnBack.setOnClickListener(x -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }

    private void initState() {
        step  = STEP_HT1;
        upper = null;
        lower = null;
        clearAll();
        updateStepIndicator();
        tvStatus.setText("");
    }

    // ----- QRスキャン処理 -----

    private void processQr(String raw) {
        if (step == STEP_DONE) return; // 照会完了後は無視

        if (!QRCodeParser.isKanban(raw)) return;

        if (step == STEP_HT1) {
            upper = QRCodeParser.parseKanban(raw, true);
            showKanban1(upper);
            setFieldOk(tvBn1, tvHinban1, tvEban1);
            step = STEP_HT2;
            updateStepIndicator();

        } else { // STEP_HT2
            lower = QRCodeParser.parseKanban(raw, false);
            showKanban2(lower);
            setFieldOk(tvBn2, tvHinban2, tvEban2);
            step = STEP_DONE;
            updateStepIndicator();
            doCheck();
        }
    }

    // ----- p40_check 呼び出し -----

    private void doCheck() {
        String u1 = (upper != null) ? upper.ukey : "";
        String u2 = (lower != null) ? lower.ukey : "";

        db().p40Check(u1, u2, result -> runOnUi(() -> showCheckResult(result)));
    }

    private void showCheckResult(String jdg) {
        if (jdg == null || jdg.isEmpty()) {
            tvStatus.setText("照会エラー");
            vibrator().error();
            return;
        }

        // 上段フィールド背景 (進捗NG or 保留)
        if (isProgress1Ng(jdg) || isHold1(jdg)) {
            setFieldNg(tvBn1, tvHinban1, tvEban1);
        }

        // 下段フィールド背景 (進捗NG or 保留)
        if (isProgress2Ng(jdg) || isHold2(jdg)) {
            setFieldNg(tvBn2, tvHinban2, tvEban2);
        }

        // ステータス文字列構築
        if (isAllOk(jdg)) {
            tvStatus.setText("NG項目なし");
            vibrator().ok();
            return;
        }

        // 品質NG が最優先
        if (isQualityNg(jdg)) {
            tvStatus.setText("品質NG");
            vibrator().error();
            return;
        }

        // 進捗NG / 保留
        StringBuilder sb = new StringBuilder();
        if (isProgress1Ng(jdg) || isProgress2Ng(jdg)) {
            sb.append("進捗NG");
        }
        if (isHold1(jdg) || isHold2(jdg)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("保留品");
        }
        tvStatus.setText(sb.toString());
        vibrator().error();
    }

    // ----- 戻るボタン -----

    private void onBack() {
        vibrator().ok();
        if (tvStatus.getText() != null && tvStatus.getText().length() > 0) {
            // ステータス表示中はリセット
            initState();
        } else if (step != STEP_HT1) {
            // スキャン途中はリセット
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

    // ----- ヘルパ -----

    private void showKanban1(KanbanData k) {
        if (k == null) return;
        tvBn1.setText(k.bname);
        tvHinban1.setText(k.hinban);
        tvEban1.setText(k.eban);
    }

    private void showKanban2(KanbanData k) {
        if (k == null) return;
        tvBn2.setText(k.bname);
        tvHinban2.setText(k.hinban);
        tvEban2.setText(k.eban);
    }

    private void clearAll() {
        for (TextView tv : new TextView[]{
                tvBn1, tvHinban1, tvEban1,
                tvBn2, tvHinban2, tvEban2}) {
            if (tv != null) {
                tv.setText("");
                tv.setBackgroundColor(Color.WHITE);
            }
        }
    }

    private void updateStepIndicator() {
        int done   = Color.parseColor("#27AE60");
        int active = Color.parseColor("#E8A020");
        int idle   = Color.LTGRAY;
        if (tvStep1 != null) {
            tvStep1.setBackgroundColor(step > STEP_HT1 ? done : step == STEP_HT1 ? active : idle);
        }
        if (tvStep2 != null) {
            tvStep2.setBackgroundColor(step == STEP_DONE ? done : step == STEP_HT2 ? active : idle);
        }
    }
}
