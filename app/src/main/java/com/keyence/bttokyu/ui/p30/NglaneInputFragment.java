package com.keyence.bttokyu.ui.p30;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.db.DbRepository;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel3 / 工程31 NGレーン進捗確認
 *
 * VB元ソース: DataMake31 の移植
 *
 * 重要: DB への書き込みは一切行わない（進捗確認・表示のみ）。
 *
 * スキャン順:
 *   ud=false → 上段HT待ち
 *   ud=true  → 下段HT待ち
 *
 * 下段スキャン後に p30Check() を呼び出し、結果を tvStatus に表示:
 *   isAllOk(result)            → tvStatus = "NG項目なし"
 *   isProgress1Ng(result)      → 上段フィールド背景赤 + tvStatus = "進捗NG"
 *   isHold1(result)            → tvStatus 追記 " 保留品"
 *   isProgress2Ng(result)      → 下段フィールド背景赤
 *   isHold2(result)            → tvStatus 追記 " 保留品"
 *
 * 戻るボタン:
 *   データあり → initState()（リセット）
 *   データなし → popBackStack()
 */
public class NglaneInputFragment extends BaseFragment {

    /** スキャン段階: false=上段待ち、true=下段待ち */
    private boolean ud = false;

    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private TextView tvStatus;
    private TextView tvStepUpper, tvStepLower;
    private Button   btnBack;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nglane_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvBn1      = view.findViewById(R.id.tvBn1);
        tvHinban1  = view.findViewById(R.id.tvHinban1);
        tvEban1    = view.findViewById(R.id.tvEban1);
        tvBn2      = view.findViewById(R.id.tvBn2);
        tvHinban2  = view.findViewById(R.id.tvHinban2);
        tvEban2    = view.findViewById(R.id.tvEban2);
        tvStatus   = view.findViewById(R.id.tvStatus);
        tvStepUpper = view.findViewById(R.id.stepUpper);
        tvStepLower = view.findViewById(R.id.stepLower);
        btnBack    = view.findViewById(R.id.btnBack);

        initState();

        btnBack.setOnClickListener(v -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }

    // ---- 初期化 ----

    private void initState() {
        ud    = false;
        upper = null;
        lower = null;
        clearAllFields();
        refreshStepIndicator();
    }

    // ---- QRコード処理 ----

    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) {
            return; // HTかんばん以外は無視
        }

        if (!ud) {
            processUpperScan(raw);
        } else {
            processLowerScan(raw);
        }
    }

    /** 上段スキャン処理 */
    private void processUpperScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, true);

        upper = k;
        lower = null;

        showKanbanFields(tvBn1, tvHinban1, tvEban1, k);
        setFieldOk(tvBn1, tvHinban1, tvEban1);
        clearFields(tvBn2, tvHinban2, tvEban2);
        tvStatus.setText("");
        tvStatus.setBackgroundColor(Color.WHITE);

        ud = true; // 次は下段待ち
        vibrator().ok();
        refreshStepIndicator();
    }

    /** 下段スキャン処理 → p30Check で結果表示 */
    private void processLowerScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, false);
        lower = k;

        showKanbanFields(tvBn2, tvHinban2, tvEban2, k);
        setFieldOk(tvBn2, tvHinban2, tvEban2);
        vibrator().ok();
        refreshStepIndicator();

        // p30Check 呼び出し（DB送信なし。結果表示のみ）
        String ukey1 = upper != null ? upper.ukey : "";
        String ukey2 = lower.ukey;

        db().p30Check(ukey1, ukey2, new DbRepository.DbCallback<String>() {
            @Override
            public void onSuccess(String result) {
                runOnUi(() -> applyCheckResult(result));
            }

            
        });
    }

    /** p30_check 結果を画面に反映 (DataMake31 相当) */
    private void applyCheckResult(String result) {
        // 全フィールドをいったんOK色に戻す（上段・下段）
        setFieldOk(tvBn1, tvHinban1, tvEban1);
        setFieldOk(tvBn2, tvHinban2, tvEban2);
        tvStatus.setBackgroundColor(Color.WHITE);

        if (isAllOk(result)) {
            // 全て正常
            tvStatus.setText("NG項目なし");
            tvStatus.setBackgroundColor(Color.parseColor("#27AE60"));
        } else {
            StringBuilder statusMsg = new StringBuilder();

            // 上段進捗NG
            if (isProgress1Ng(result)) {
                setFieldNg(tvBn1, tvHinban1, tvEban1);
                statusMsg.append("進捗NG");
            }
            // 上段保留
            if (isHold1(result)) {
                if (statusMsg.length() > 0) statusMsg.append(" ");
                statusMsg.append("保留品");
            }
            // 下段進捗NG
            if (isProgress2Ng(result)) {
                setFieldNg(tvBn2, tvHinban2, tvEban2);
                if (statusMsg.length() > 0) statusMsg.append(" / ");
                statusMsg.append("下段進捗NG");
            }
            // 下段保留
            if (isHold2(result)) {
                if (statusMsg.length() > 0) statusMsg.append(" ");
                statusMsg.append("保留品");
            }

            if (statusMsg.length() == 0) {
                statusMsg.append("NG");
            }

            tvStatus.setText(statusMsg.toString());
            tvStatus.setBackgroundColor(Color.parseColor("#E74C3C"));
            vibrator().error();
        }
    }

    // ---- 戻るボタン ----

    /** データあり → initState、データなし → popBackStack */
    private void onBack() {
        // 追加
        vibrator().ok();
        if (upper != null || lower != null) {
            initState();
        } else {
            disableScanner();
            safePopBackStack();
        }
    }

    // ---- ライフサイクル ----

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disableScanner();
    }

    // ---- ヘルパー ----

    private void showKanbanFields(TextView tvBn, TextView tvHinban, TextView tvEban,
                                  KanbanData k) {
        tvBn.setText(k.displayName());
        tvHinban.setText(k.isEmpty() ? "" : k.hinban);
        tvEban.setText(k.eban);
    }

    private void clearFields(TextView... views) {
        for (TextView v : views) {
            if (v != null) {
                v.setText("");
                v.setBackgroundColor(Color.WHITE);
            }
        }
    }

    private void clearAllFields() {
        clearFields(tvBn1, tvHinban1, tvEban1, tvBn2, tvHinban2, tvEban2);
        if (tvStatus != null) {
            tvStatus.setText("");
            tvStatus.setBackgroundColor(Color.WHITE);
        }
    }

    private void refreshStepIndicator() {
        int done   = Color.parseColor("#27AE60");
        int active = Color.parseColor("#E8A020");
        int idle   = Color.LTGRAY;

        if (tvStepUpper != null) {
            tvStepUpper.setBackgroundColor(
                    upper != null ? done : (!ud ? active : idle));
        }
        if (tvStepLower != null) {
            tvStepLower.setBackgroundColor(
                    lower != null ? done : (ud ? active : idle));
        }
    }
}
