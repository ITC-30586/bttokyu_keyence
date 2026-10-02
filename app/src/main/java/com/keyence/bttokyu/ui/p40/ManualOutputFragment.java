package com.keyence.bttokyu.ui.p40;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel8 / 工程42 手動搬出
 * VB元ソース DataMake42 / DataMake43 の移植。
 * スキャン順: 上段HT → 下段HT(任意) → 炉番号Spinner → 品質Spinner → 搬出ボタン
 */
public class ManualOutputFragment extends BaseFragment {

    // スキャン段階
    private static final int STEP_HT1  = 0; // 上段HT待ち
    private static final int STEP_HT2  = 1; // 下段HT待ち（任意）
    private static final int STEP_DONE = 2; // スキャン完了

    private int step = STEP_HT1;
    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvStep1, tvStep2;
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private Spinner  spLane;       // 炉番号 1〜4
    private Spinner  spQuality;    // 品質 OK / NG
    private Button   btnSend;
    private Button   btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manual_output, container, false);
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
        spLane    = view.findViewById(R.id.spLane);
        spQuality = view.findViewById(R.id.spQuality);
        btnSend   = view.findViewById(R.id.btnSend);
        btnBack   = view.findViewById(R.id.btnBack);

        setupSpinners();
        initState();

        btnSend.setOnClickListener(x -> onSend());
        btnBack.setOnClickListener(x -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }

    private void setupSpinners() {
        // 炉番号 Spinner (1〜4)
        ArrayAdapter<String> laneAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"-- 炉番号を選択 --", "1", "2", "3", "4"});
        laneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLane.setAdapter(laneAdapter);

        // 品質 Spinner (OK / NG)
        ArrayAdapter<String> qualAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"-- 品質を選択 --", "OK", "NG"});
        qualAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spQuality.setAdapter(qualAdapter);

        // 炉番号選択後 → 品質Spinner活性化
        spLane.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (pos > 0) {
                    spQuality.setEnabled(true);
                } else {
                    spQuality.setEnabled(false);
                    spQuality.setSelection(0);
                    btnSend.setEnabled(false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 品質選択後 → 搬出ボタン活性化
        spQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                boolean laneOk = spLane.getSelectedItemPosition() > 0;
                btnSend.setEnabled(laneOk && pos > 0 && upper != null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void initState() {
        step  = STEP_HT1;
        upper = null;
        lower = null;
        clearAll();
        updateStepIndicator();
        spLane.setSelection(0);
        spLane.setEnabled(false);
        spQuality.setSelection(0);
        spQuality.setEnabled(false);
        btnSend.setEnabled(false);
    }

    // ----- QRスキャン処理 -----

    private void processQr(String raw) {
        if (step == STEP_DONE) return;

        if (!QRCodeParser.isKanban(raw)) return;

        if (step == STEP_HT1) {
            upper = QRCodeParser.parseKanban(raw, true);
            showKanban1(upper);
            setFieldOk(tvBn1, tvHinban1, tvEban1);
            step = STEP_HT2;
            updateStepIndicator();
            // 上段スキャン後、炉番号Spinnerを活性化（下段は任意のため）
            spLane.setEnabled(true);

        } else { // STEP_HT2 (下段)
            lower = QRCodeParser.parseKanban(raw, false);
            showKanban2(lower);
            setFieldOk(tvBn2, tvHinban2, tvEban2);
            step = STEP_DONE;
            updateStepIndicator();
        }
    }

    // ----- 搬出送信 -----

    private void onSend() {
        if (upper == null) return;

        int lanePos = spLane.getSelectedItemPosition();
        int qualPos = spQuality.getSelectedItemPosition();
        if (lanePos <= 0 || qualPos <= 0) return;

        String ln  = String.valueOf(lanePos);          // "1"〜"4"
        String jud = (qualPos == 1) ? "0" : "1";      // OK=0, NG=1
        String u1  = upper.ukey;
        String u2  = (lower != null) ? lower.ukey : "";

        btnSend.setEnabled(false);

        db().p40InTemp(u1, u2, ln, jud, result -> runOnUi(() -> {
            if ("00000".equals(result)) {
                showOk("搬出登録しました");
                initState();
            } else {
                // 結果に応じてフィールド背景赤 + バイブレータ2回
                applyNgHighlight(result);
                vibrator().error();
                vibrator().error();
                btnSend.setEnabled(true);
            }
        }));
    }

    private void applyNgHighlight(String jdg) {
        if (jdg == null) return;
        if (isProgress1Ng(jdg) || isHold1(jdg)) {
            setFieldNg(tvBn1, tvHinban1, tvEban1);
        }
        if (isProgress2Ng(jdg) || isHold2(jdg)) {
            setFieldNg(tvBn2, tvHinban2, tvEban2);
        }
    }

    // ----- 戻るボタン -----

    private void onBack() {
        vibrator().ok();
        if (upper != null) {
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
