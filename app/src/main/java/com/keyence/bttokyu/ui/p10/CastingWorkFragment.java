package com.keyence.bttokyu.ui.p10;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Panel19 / 工程11 鋳造作業登録
 * スキャン順: DC(段階0) → DE(1) → CA(2) → 上段HT(3) → 下段HT(3)
 */
public class CastingWorkFragment extends BaseFragment {

    // スキャン段階
    private static final int STEP_DC = 0, STEP_DE = 1, STEP_CA = 2, STEP_HT = 3;

    private int step = STEP_DC;
    private boolean ud = false; // false=上段待ち

    private String dcm = "", kat = "", katHid = "", cav = "";
    private KanbanData upper, lower;

    // Views
    private TextView tvDc, tvKat, tvCav, tvBn1, tvHinban1, tvEban1,
                     tvBn2, tvHinban2, tvEban2;
    private TextView stepDcV, stepDeV, stepCaV, stepHt1V, stepHt2V;
    private CheckBox cbHold;
    private Button btnSend, btnBack;

    @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) {
        return i.inflate(R.layout.fragment_casting_work, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        tvDc=v.findViewById(R.id.tvDc); tvKat=v.findViewById(R.id.tvKat);
        tvCav=v.findViewById(R.id.tvCav);
        tvBn1=v.findViewById(R.id.tvBn1); tvHinban1=v.findViewById(R.id.tvHinban1);
        tvEban1=v.findViewById(R.id.tvEban1);
        tvBn2=v.findViewById(R.id.tvBn2); tvHinban2=v.findViewById(R.id.tvHinban2);
        tvEban2=v.findViewById(R.id.tvEban2);
        stepDcV=v.findViewById(R.id.stepDc); stepDeV=v.findViewById(R.id.stepDe);
        stepCaV=v.findViewById(R.id.stepCa); stepHt1V=v.findViewById(R.id.stepHt1);
        stepHt2V=v.findViewById(R.id.stepHt2);
        cbHold=v.findViewById(R.id.cbHold);
        btnSend=v.findViewById(R.id.btnSend);
        btnBack=v.findViewById(R.id.btnBack);

        initState();

        btnSend.setOnClickListener(x -> onSend());
        btnBack.setOnClickListener(x -> onBack());

        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // デバッグ: スキャナー有効化を通知

        // 追加
        vibrator().ok();
    }

    private void initState() {
        step = STEP_DC; ud = false;
        dcm=""; kat=""; katHid=""; cav=""; upper=null; lower=null;
        clearAll();
        setStep(STEP_DC);
        btnSend.setEnabled(false);
        cbHold.setChecked(false);
    }

    private void processQr(String raw) {
        // デバッグ: processQr が呼ばれたことを確認
        switch (step) {
            case STEP_DC:
                if (QRCodeParser.isDcCode(raw)) {
                    dcm = QRCodeParser.parseDc(raw);
                    tvDc.setText("DC" + dcm);
                    setFieldOk(tvDc);
                    step = STEP_DE;
                    setStep(STEP_DE);
                }
                break;
            case STEP_DE:
                if (QRCodeParser.isDeCode(raw)) {
                    String pay = QRCodeParser.parseDe(raw);
                    kat    = QRCodeParser.parseKatCode(pay);
                    katHid = QRCodeParser.parseKatHid(pay);
                    tvKat.setText(kat);
                    setFieldOk(tvKat);
                    //QRコード参照
                    //showQrData(katHid);
                    step = STEP_CA;
                    setStep(STEP_CA);

                }
                break;
            case STEP_CA:
                if (QRCodeParser.isCaCode(raw)) {
                    cav = QRCodeParser.parseCa(raw);
                    tvCav.setText(cav);
                    setFieldOk(tvCav);
                    step = STEP_HT;
                    setStep(STEP_HT);
                }
                break;
            case STEP_HT:
                if (QRCodeParser.isKanban(raw)) {
                    processHt(raw);
                }
                break;
        }
    }
    //確認用処理
    private void showQrData(String raw) {
        new AlertDialog.Builder(requireContext())
                .setTitle("QRコードデータ")
                .setMessage(raw)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void processHt(String raw) {
        KanbanData k = QRCodeParser.parseKanban(raw, !ud);
        if (!ud) {
            // 上段
            if (k.isEmpty()) {
                upper = k;
                tvBn1.setText(""); tvHinban1.setText("空カゴ"); tvEban1.setText(k.eban);
                setFieldOk(tvBn1, tvHinban1, tvEban1);
                stepHt1V.setBackgroundColor(Color.parseColor("#27AE60"));
                ud = true; btnSend.setEnabled(true);
            } else if (k.isTpKanban()) {
                upper = k;
                tvBn1.setText(""); tvHinban1.setText("TPかんばん"); tvEban1.setText(k.eban);
                setFieldOk(tvBn1, tvHinban1, tvEban1);
                stepHt1V.setBackgroundColor(Color.parseColor("#27AE60"));
                ud = true; btnSend.setEnabled(true);
            } else if (katHid.equals(k.hid)) {
                upper = k;
                tvBn1.setText(k.bname); tvHinban1.setText(k.hinban); tvEban1.setText(k.eban);
                setFieldOk(tvBn1, tvHinban1, tvEban1);
                stepHt1V.setBackgroundColor(Color.parseColor("#27AE60"));
                ud = true; btnSend.setEnabled(true);
            } else {
                // 金型品番不一致NG
                tvBn1.setText(""); tvHinban1.setText("品番照合NG"); tvEban1.setText("");

                //QRコード参照
                //showQrData(k.hid);
                //showQrData(katHid);

                setFieldNg(tvBn1, tvHinban1, tvEban1);
                vibrator().error();
            }
        } else {
            // 下段
            if (upper != null && upper.ukey.equals(k.ukey)) {
                showError(getString(R.string.err_same_kanban)); return;
            }
            if (k.isEmpty()) {
                // 下段空カゴはエラー
                tvBn2.setText(""); tvHinban2.setText("下段不可"); tvEban2.setText("");
                setFieldNg(tvBn2, tvHinban2, tvEban2);
                btnSend.setEnabled(false);
                vibrator().error(); return;
            }
            if (!k.isTpKanban() && upper != null && !upper.isEmpty() && !upper.hid.equals(k.hid)) {
                showError(getString(R.string.err_hid_mismatch)); return;
            }
            if (!k.isTpKanban() && !katHid.equals(k.hid)) {
                tvBn2.setText(""); tvHinban2.setText("品番照合NG"); tvEban2.setText("");
                setFieldNg(tvBn2, tvHinban2, tvEban2);
                btnSend.setEnabled(false);
                vibrator().error(); return;
            }
            lower = k;
            tvBn2.setText(k.displayName()); tvHinban2.setText(k.hinban); tvEban2.setText(k.eban);
            setFieldOk(tvBn2, tvHinban2, tvEban2);
            stepHt2V.setBackgroundColor(Color.parseColor("#27AE60"));
            btnSend.setEnabled(true);
        }
    }

    private void onSend() {

        vibrator().ok();

        if (upper == null) return;
        String ukey2 = (lower != null) ? lower.ukey : "";
        String hold  = cbHold.isChecked() ? "C" : "0";

        writeCsv(dcm, kat, cav, upper.ukey, ukey2, hold);

        btnSend.setEnabled(false);
        db().p10InTemp(dcm, kat, cav, upper.ukey, ukey2, hold, result -> {
            runOnUi(() -> {
                showOk(getString(R.string.ok_registered));
                initState();
            });
        });
    }

    /** 無線断時のオフライン用CSVバックアップ */
    private void writeCsv(String dc, String k, String c, String u1, String u2, String h) {
        try {
            File dir = new File(requireContext().getExternalFilesDir(null), "Resut10");
            if (!dir.exists()) dir.mkdirs();
            String date = new SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(new Date());
            File f = new File(dir, date + "_P10.txt");
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write(dc + "," + k + "," + c + "," + u1 + "," + u2 + "," + h + ",b\n");
            }
        } catch (IOException e) { /* 書き込み失敗は無視 */ }
    }

    private void onBack() {
        vibrator().ok();
        disableScanner();
        if (dcm.isEmpty()) {
            getMain().getNavController().popBackStack();
        } else {
            initState(); // データがあればリセット（再入力）
        }
    }

    @Override public void onDestroyView() { super.onDestroyView(); disableScanner(); }

    private void clearAll() {
        for (TextView tv : new TextView[]{tvDc,tvKat,tvCav,tvBn1,tvHinban1,tvEban1,
                                          tvBn2,tvHinban2,tvEban2}) {
            if (tv != null) { tv.setText(""); tv.setBackgroundColor(Color.WHITE); }
        }
    }

    private void setStep(int s) {
        int done   = Color.parseColor("#27AE60");
        int active = Color.parseColor("#E8A020");
        int idle   = Color.LTGRAY;
        stepDcV.setBackgroundColor(s > STEP_DC ? done : s == STEP_DC ? active : idle);
        stepDeV.setBackgroundColor(s > STEP_DE ? done : s == STEP_DE ? active : idle);
        stepCaV.setBackgroundColor(s > STEP_CA ? done : s == STEP_CA ? active : idle);
        stepHt1V.setBackgroundColor(!ud && s == STEP_HT ? active : (upper != null ? done : idle));
        stepHt2V.setBackgroundColor(ud && s == STEP_HT ? active : (lower != null ? done : idle));
    }
}
