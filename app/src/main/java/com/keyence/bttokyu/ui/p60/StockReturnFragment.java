package com.keyence.bttokyu.ui.p60;

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
 * Panel13 / 工程62 返却処理
 * VB元ソース DataMake62 / Button31 の移植。
 * 上段HTスキャン後 btnSend 活性化。下段HTは任意。
 * p70ReturnInTemp(u1, u2またはnull, cb) を呼び出す。
 */
public class StockReturnFragment extends BaseFragment {

    // スキャン段階
    private static final int STEP_HT1 = 0; // 上段HT待ち
    private static final int STEP_HT2 = 1; // 下段HT待ち（任意）

    private int step = STEP_HT1;
    private KanbanData upper = null;
    private KanbanData lower = null;

    // Views
    private TextView tvStep1, tvStep2;
    private TextView tvBn1, tvHinban1, tvEban1;
    private TextView tvBn2, tvHinban2, tvEban2;
    private Button   btnSend;
    private Button   btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stock_return, container, false);
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
        btnSend   = view.findViewById(R.id.btnSend);
        btnBack   = view.findViewById(R.id.btnBack);

        initState();

        btnSend.setOnClickListener(x -> onSend());
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
        btnSend.setEnabled(false);
    }

    // ----- QRスキャン処理 -----

    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) return;

        if (step == STEP_HT1) {
            upper = QRCodeParser.parseKanban(raw, true);
            showKanban1(upper);
            setFieldOk(tvBn1, tvHinban1, tvEban1);
            step = STEP_HT2;
            updateStepIndicator();
            // 上段スキャン後、送信ボタン活性化
            btnSend.setEnabled(true);

        } else { // STEP_HT2 (下段、任意)
            lower = QRCodeParser.parseKanban(raw, false);
            showKanban2(lower);
            setFieldOk(tvBn2, tvHinban2, tvEban2);
            updateStepIndicator();
        }
    }

    // ----- 送信処理 -----

    private void onSend() {
        if (upper == null) return;

        String u1 = upper.ukey;
        String u2 = (lower != null) ? lower.ukey : null;

        btnSend.setEnabled(false);

        db().p70ReturnInTemp(u1, u2, result -> runOnUi(() -> {
            if (result != null) {
                showOk("返却登録しました");
                initState();
            } else {
                showError("返却登録に失敗しました");
                vibrator().error();
                vibrator().error();
                btnSend.setEnabled(true);
            }
        }));
    }

    // ----- 戻るボタン -----

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
            boolean ht2Done = (lower != null);
            tvStep2.setBackgroundColor(ht2Done ? done : (step == STEP_HT2 ? active : idle));
        }
    }
}
