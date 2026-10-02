package com.keyence.bttokyu.ui.p70;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

/**
 * Panel17 / 工程74 廃棄処理
 *
 * <p>VB元ソース DataMake73 / Button41 の移植。
 * 1品番ずつ廃棄（上段のみ、UDなし）。
 * HID="00000" は空カゴとしてスキャン無視。
 * 廃棄前に確認ダイアログを表示。
 */
public class DiscardFragment extends BaseFragment {

    private KanbanData current = null;

    // Views
    private TextView tvBname, tvHinban, tvEban;
    private MaterialButton btnDiscard, btnBack;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tvBname  = v.findViewById(R.id.tvBname);
        tvHinban = v.findViewById(R.id.tvHinban);
        tvEban   = v.findViewById(R.id.tvEban);
        btnDiscard = v.findViewById(R.id.btnDiscard);
        btnBack    = v.findViewById(R.id.btnBack);

        initState();

        btnDiscard.setOnClickListener(x -> onDiscardClick());
        btnBack.setOnClickListener(x -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }

    /** 初期状態にリセット */
    private void initState() {
        current = null;
        tvBname.setText("");
        tvHinban.setText("");
        tvEban.setText("");
        tvBname.setBackgroundColor(Color.WHITE);
        tvHinban.setBackgroundColor(Color.WHITE);
        tvEban.setBackgroundColor(Color.WHITE);
        btnDiscard.setEnabled(false);
    }

    /** QRスキャン処理（DataMake73移植） */
    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) return;

        KanbanData k = QRCodeParser.parseKanban(raw, true);

        // 空カゴ（HID="00000"）はスキャン無視
        if (k.isEmpty()) {
            tvBname.setText("空カゴ");
            tvHinban.setText("スキャン無視");
            tvEban.setText(k.eban);
            tvBname.setBackgroundColor(Color.LTGRAY);
            tvHinban.setBackgroundColor(Color.LTGRAY);
            tvEban.setBackgroundColor(Color.LTGRAY);
            btnDiscard.setEnabled(false);
            current = null;
            return;
        }

        current = k;
        tvBname.setText(k.displayName());
        tvHinban.setText(k.hinban);
        tvEban.setText(k.eban);
        setFieldOk(tvBname, tvHinban, tvEban);
        btnDiscard.setEnabled(true);
        vibrator().ok();
    }

    /** 廃棄ボタン押下（Button41移植）: 確認ダイアログ表示後に実行 */
    private void onDiscardClick() {
        if (current == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("廃棄確認")
                .setMessage("この操作は取消不可です。廃棄しますか？")
                .setPositiveButton("廃棄する", (dialog, which) -> executeDiscard())
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /** 廃棄処理実行 */
    private void executeDiscard() {
        if (current == null) return;
        String u1 = current.ukey;
        btnDiscard.setEnabled(false);

        db().pLotout(u1, null, "D", result -> runOnUi(() -> {
            boolean ok = "0".equals(result);
            if (ok) {
                showOk("廃棄処理しました");
                initState();
            } else {
                setFieldNg(tvBname, tvHinban, tvEban);
                vibrator().error();
                vibrator().error();
                showError("廃棄処理に失敗しました: " + result);
                btnDiscard.setEnabled(true);
            }
        }));
    }

    /** 戻るボタン押下 */
    private void onBack() {
        vibrator().ok();
        if (current != null) {
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
}
