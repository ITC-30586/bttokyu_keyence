package com.keyence.bttokyu.ui.p20;

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
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel10 / 工程20 振り分け
 * 上下段HTかんばんをスキャンして整合性チェック後に p20_in_temp へ投入。
 *
 * VB元ソース: DataMake21 / DataMake22 の移植
 *
 * スキャン順:
 *   ud=false → 上段HT待ち
 *   ud=true  → 下段HT待ち
 *
 * 整合性ルール:
 *   - 上段空カゴ(HID=00000) + 下段空カゴ → バイブレータ2回エラー、入力拒否
 *   - 上段通常品 → 下段が同一UKEY → エラー
 *   - 上段通常品 → 下段とHID不一致 → エラー
 *
 * 送信ボタン:
 *   下段スキャン後に活性化。
 *   押下時: p20Check() → jd1="0" AND jd2="0" → p20InTemp()
 *            NG時: フィールド背景赤、エラーバイブレータ
 */
public class SortingFragment extends BaseFragment {

    /** スキャン段階: false=上段待ち、true=下段待ち */
    private boolean ud = false;

    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private TextView tvStepUpper, tvStepLower;
    private Button   btnSend, btnBack;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sorting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // フィールド
        tvBn1      = view.findViewById(R.id.tvBn1);
        tvHinban1  = view.findViewById(R.id.tvHinban1);
        tvEban1    = view.findViewById(R.id.tvEban1);
        tvBn2      = view.findViewById(R.id.tvBn2);
        tvHinban2  = view.findViewById(R.id.tvHinban2);
        tvEban2    = view.findViewById(R.id.tvEban2);
        // ステップインジケータ
        tvStepUpper = view.findViewById(R.id.stepUpper);
        tvStepLower = view.findViewById(R.id.stepLower);
        // ボタン
        btnSend = view.findViewById(R.id.btnSend);
        btnBack = view.findViewById(R.id.btnBack);

        initState();

        btnSend.setOnClickListener(v -> onSend());
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
        btnSend.setEnabled(false);
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

    /** 上段スキャン処理 (DataMake21 相当) */
    private void processUpperScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, true);

        upper = k;
        lower = null;
        btnSend.setEnabled(false);

        // 上段フィールドを表示
        showKanbanFields(tvBn1, tvHinban1, tvEban1, k);
        setFieldOk(tvBn1, tvHinban1, tvEban1);
        // 下段フィールドをリセット
        clearFields(tvBn2, tvHinban2, tvEban2);

        ud = true; // 次は下段待ち
        vibrator().ok();
        refreshStepIndicator();
    }

    /** 下段スキャン処理 (DataMake22 相当) */
    private void processLowerScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, false);

        // --- 整合性チェック ---

        // 上段空カゴ + 下段空カゴ → エラー（バイブレータ2回）
        if (upper != null && upper.isEmpty() && k.isEmpty()) {
            vibrator().error();
            vibrator().error();
            showError("上下段ともに空カゴです。投入できません。");
            clearFields(tvBn2, tvHinban2, tvEban2);
            setFieldNg(tvBn2, tvHinban2, tvEban2);
            return;
        }

        // 上段通常品: 同一UKEY → エラー
        if (upper != null && !upper.isEmpty() && upper.ukey.equals(k.ukey)) {
            showError("上段と同一のかんばんです。");
            clearFields(tvBn2, tvHinban2, tvEban2);
            setFieldNg(tvBn2, tvHinban2, tvEban2);
            return;
        }

        // 上段通常品: HID不一致 → エラー（上段が空カゴ または TPかんばんなら照合不要）
        if (upper != null && !upper.isEmpty() && !upper.isTpKanban()
                && !k.isEmpty() && !k.isTpKanban()
                && !upper.hid.equals(k.hid)) {
            showError("上段と下段の品番が一致しません。");
            clearFields(tvBn2, tvHinban2, tvEban2);
            setFieldNg(tvBn2, tvHinban2, tvEban2);
            return;
        }

        // --- 正常 ---
        lower = k;
        showKanbanFields(tvBn2, tvHinban2, tvEban2, k);
        setFieldOk(tvBn2, tvHinban2, tvEban2);
        btnSend.setEnabled(true);
        vibrator().ok();
        refreshStepIndicator();
    }

    // ---- 送信処理 ----

    /** 送信ボタン押下: p20Check → OK なら p20InTemp */
    private void onSend() {
        if (upper == null || lower == null) return;

        btnSend.setEnabled(false);
        String ukey1 = upper.ukey;
        String ukey2 = lower.ukey;

        db().p20Check(ukey1, ukey2, new com.keyence.bttokyu.db.DbRepository.DbCallback<String>() {
            @Override
            public void onSuccess(String result) {
                runOnUi(() -> {
                    String jd1 = (result != null && result.length() >= 1) ? result.substring(0, 1) : "1";
                    String jd2 = (result != null && result.length() >= 3) ? result.substring(2, 3) : "1";

                    if ("0".equals(jd1) && "0".equals(jd2)) {
                        // 進捗OK → 投入
                        doInTemp(ukey1, ukey2);
                    } else {
                        // 進捗NG → フィールド赤表示
                        vibrator().error();
                        if (!"0".equals(jd1)) {
                            setFieldNg(tvBn1, tvHinban1, tvEban1);
                        }
                        if (!"0".equals(jd2)) {
                            setFieldNg(tvBn2, tvHinban2, tvEban2);
                        }
                        showError("進捗NGです。確認してください。");
                        btnSend.setEnabled(true);
                    }
                });
            }

            
        });
    }

    /** p20InTemp を呼び出して実際に投入 */
    private void doInTemp(String ukey1, String ukey2) {
        db().p20InTemp(ukey1, ukey2, new com.keyence.bttokyu.db.DbRepository.DbCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUi(() -> {
                    showOk("投入完了");
                    initState();
                });
            }

            
        });
    }

    // ---- 戻るボタン ----

    private void onBack() {
        vibrator().ok();
        disableScanner();
        safePopBackStack();
    }

    // ---- ライフサイクル ----

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disableScanner();
    }

    // ---- ヘルパー ----

    /** かんばんデータをフィールドに表示 */
    private void showKanbanFields(TextView tvBn, TextView tvHinban, TextView tvEban,
                                  KanbanData k) {
        tvBn.setText(k.displayName());
        tvHinban.setText(k.isEmpty() ? "" : k.hinban);
        tvEban.setText(k.eban);
    }

    /** 指定フィールドをクリア（白背景） */
    private void clearFields(TextView... views) {
        for (TextView v : views) {
            if (v != null) {
                v.setText("");
                v.setBackgroundColor(Color.WHITE);
            }
        }
    }

    /** 全フィールドをクリア */
    private void clearAllFields() {
        clearFields(tvBn1, tvHinban1, tvEban1, tvBn2, tvHinban2, tvEban2);
    }

    /** ステップインジケータを現在の ud 状態に合わせて更新 */
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
