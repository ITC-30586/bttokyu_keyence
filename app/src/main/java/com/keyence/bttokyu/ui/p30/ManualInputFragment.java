package com.keyence.bttokyu.ui.p30;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.db.DbRepository;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel4 / 工程32 手動炉投入
 *
 * VB元ソース: DataMake32 / DataMake34 の移植
 *
 * スキャン順:
 *   ud=false → 上段HT待ち
 *   ud=true  → 下段HT待ち
 *
 * 下段スキャン後:
 *   - 下段空カゴ(HID=00000) → エラー（フィールド赤、炉Spinner無効）
 *   - 正常 → p30Check() を実施:
 *       isAllOk → 炉Spinner 活性化
 *       NG      → フィールド赤、Spinner無効
 *
 * 炉番号:
 *   - Spinner 選択肢: 1〜4 (LNUM=4固定)
 *   - LNコードでのスキャンも受け付ける (parseLn)
 *   - Spinner 選択後、投入ボタン活性化
 *
 * 投入ボタン押下:
 *   p30InTemp(ukey1, ukey2, ln, cb) → 成功 → initState() + showOk()
 *
 * 戻るボタン:
 *   データあり → initState()
 *   データなし → popBackStack()
 */
public class ManualInputFragment extends BaseFragment {

    /** 炉番号最大数 (LNUM固定値) */
    private static final int LNUM = 4;

    /** スキャン段階: false=上段待ち、true=下段待ち */
    private boolean ud = false;

    private KanbanData upper = null;
    private KanbanData lower = null;

    /** 選択中の炉番号 (1〜LNUM)、未選択は null */
    private String selectedLn = null;

    // Views
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private TextView tvStepUpper, tvStepLower;
    private Spinner  spLn;
    private Button   btnSend, btnBack;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manual_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvBn1      = view.findViewById(R.id.tvBn1);
        tvHinban1  = view.findViewById(R.id.tvHinban1);
        tvEban1    = view.findViewById(R.id.tvEban1);
        tvBn2      = view.findViewById(R.id.tvBn2);
        tvHinban2  = view.findViewById(R.id.tvHinban2);
        tvEban2    = view.findViewById(R.id.tvEban2);
        tvStepUpper = view.findViewById(R.id.stepUpper);
        tvStepLower = view.findViewById(R.id.stepLower);
        spLn       = view.findViewById(R.id.spLn);
        btnSend    = view.findViewById(R.id.btnSend);
        btnBack    = view.findViewById(R.id.btnBack);

        setupSpinner();
        initState();

        btnSend.setOnClickListener(v -> onSend());
        btnBack.setOnClickListener(v -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();

    }

    // ---- Spinner 初期化 ----

    private void setupSpinner() {
        // 先頭に「炉番号」プレースホルダー、次いで 1〜LNUM
        String[] items = new String[LNUM + 1];
        items[0] = "炉番号";
        for (int i = 1; i <= LNUM; i++) {
            items[i] = String.valueOf(i);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLn.setAdapter(adapter);

        spLn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedLn = null;
                    btnSend.setEnabled(false);
                } else {
                    selectedLn = String.valueOf(position); // 1〜LNUM
                    // 下段が正常スキャン済みの場合のみ送信ボタンを活性化
                    if (lower != null && !lower.isEmpty()) {
                        btnSend.setEnabled(true);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedLn = null;
                btnSend.setEnabled(false);
            }
        });
    }

    // ---- 初期化 ----

    private void initState() {
        ud         = false;
        upper      = null;
        lower      = null;
        selectedLn = null;
        clearAllFields();
        spLn.setSelection(0);
        spLn.setEnabled(false);
        btnSend.setEnabled(false);
        refreshStepIndicator();
    }

    // ---- QRコード処理 ----

    private void processQr(String raw) {
        // LNコード（炉番号スキャン）の場合は炉番号をセット
        if (QRCodeParser.isLnCode(raw)) {
            processLnScan(raw);
            return;
        }

        if (!QRCodeParser.isKanban(raw)) {
            return; // その他のコードは無視
        }

        if (!ud) {
            processUpperScan(raw);
        } else {
            processLowerScan(raw);
        }
    }

    /** LNコードスキャン → Spinner を該当炉番号に変更 */
    private void processLnScan(String raw) {
        if (!spLn.isEnabled()) {
            showError("炉番号は下段スキャン後に入力してください。");
            return;
        }
        String lnStr = QRCodeParser.parseLn(raw);
        try {
            int ln = Integer.parseInt(lnStr);
            if (ln >= 1 && ln <= LNUM) {
                spLn.setSelection(ln); // index = ln (0=プレースホルダー, 1〜LNUM)
                vibrator().ok();
            } else {
                showError("炉番号の範囲外です: " + ln);
            }
        } catch (NumberFormatException e) {
            showError("炉番号が無効です: " + lnStr);
        }
    }

    /** 上段スキャン処理 (DataMake32 相当) */
    private void processUpperScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, true);

        upper = k;
        lower = null;
        selectedLn = null;

        showKanbanFields(tvBn1, tvHinban1, tvEban1, k);
        setFieldOk(tvBn1, tvHinban1, tvEban1);
        clearFields(tvBn2, tvHinban2, tvEban2);
        spLn.setSelection(0);
        spLn.setEnabled(false);
        btnSend.setEnabled(false);

        ud = true; // 次は下段待ち
        vibrator().ok();
        refreshStepIndicator();
    }

    /** 下段スキャン処理 (DataMake34 相当) */
    private void processLowerScan(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, false);

        // 下段空カゴはエラー
        if (k.isEmpty()) {
            lower = null;
            showKanbanFields(tvBn2, tvHinban2, tvEban2, k);
            setFieldNg(tvBn2, tvHinban2, tvEban2);
            spLn.setEnabled(false);
            btnSend.setEnabled(false);
            vibrator().error();
            showError("下段は空カゴを投入できません。");
            return;
        }

        lower = k;
        showKanbanFields(tvBn2, tvHinban2, tvEban2, k);
        setFieldOk(tvBn2, tvHinban2, tvEban2);
        vibrator().ok();
        refreshStepIndicator();

        // p30Check を実施して炉Spinnerの活性化を判断
        String ukey1 = upper != null ? upper.ukey : "";
        String ukey2 = lower.ukey;

        db().p30Check(ukey1, ukey2, new DbRepository.DbCallback<String>() {
            @Override
            public void onSuccess(String result) {
                runOnUi(() -> applyCheckResult(result));
            }

            
        });
    }

    /** p30_check 結果に応じて炉Spinner活性化 or エラー表示 */
    private void applyCheckResult(String result) {
        if (isAllOk(result)) {
            // 進捗OK → 炉番号入力を受け付ける
            spLn.setEnabled(true);
            // Spinnerがすでに選択済みなら送信ボタンも活性化
            if (selectedLn != null) {
                btnSend.setEnabled(true);
            }
        } else {
            // 進捗NG → フィールド赤、Spinner無効
            if (isProgress1Ng(result)) {
                setFieldNg(tvBn1, tvHinban1, tvEban1);
            }
            if (isProgress2Ng(result)) {
                setFieldNg(tvBn2, tvHinban2, tvEban2);
            }
            spLn.setEnabled(false);
            btnSend.setEnabled(false);
            vibrator().error();
            showError("進捗NGです。確認してください。");
        }
    }

    // ---- 投入処理 ----

    /** 投入ボタン押下: p30InTemp を呼び出す */
    private void onSend() {
        if (upper == null || lower == null || selectedLn == null) return;

        btnSend.setEnabled(false);
        String ukey1 = upper.ukey;
        String ukey2 = lower.ukey;
        String ln    = selectedLn;

        db().p30InTemp(ukey1, ukey2, ln, new DbRepository.DbCallback<String>() {
            @Override
            public void onSuccess(String result) {
                runOnUi(() -> {
                    showOk("炉投入完了（炉" + ln + "号）");
                    initState();
                });
            }

            
        });
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
