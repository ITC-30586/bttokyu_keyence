package com.keyence.bttokyu.ui.p70;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;
import java.util.Arrays;
public class FurnaceLostFragment extends BaseFragment {
    private static final String FIXED_LN = "1";
    private boolean ud = false;
    private KanbanData ukey1, ukey2, ukey3;
    private String prn = "", prn2 = "";
    private TextView tvUkey1, tvUkey2, tvUkey3, tvLn;
    private Spinner spQuality;
    private Button btnRegister, btnBack;
    private String jud = "0";
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        return inf.inflate(R.layout.fragment_furnace_lost, c, false);
    }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvUkey1=v.findViewById(R.id.tvUkey1); tvUkey2=v.findViewById(R.id.tvUkey2);
        tvUkey3=v.findViewById(R.id.tvUkey3); tvLn=v.findViewById(R.id.tvLn);
        spQuality=v.findViewById(R.id.spQuality);
        btnRegister=v.findViewById(R.id.btnRegister); btnBack=v.findViewById(R.id.btnBack);
        if (tvLn != null) tvLn.setText(FIXED_LN);
        ArrayAdapter<String> qa = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, Arrays.asList("OK","NG"));
        qa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spQuality.setAdapter(qa); spQuality.setEnabled(false);
        spQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View vi, int pos, long id) {
                jud = pos == 0 ? "0" : "1";
                if (btnRegister != null) btnRegister.setEnabled(true);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        btnRegister.setEnabled(false);
        btnRegister.setOnClickListener(x -> onRegister());
        btnBack.setOnClickListener(x -> onBack());
        initState();
        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();

    }
    private void initState() {
        ud = false; ukey1 = ukey2 = ukey3 = null; prn = ""; prn2 = ""; jud = "0";
        if (tvUkey1 != null) { tvUkey1.setText(""); setFieldNormal(tvUkey1); }
        if (tvUkey2 != null) { tvUkey2.setText(""); setFieldNormal(tvUkey2); }
        if (tvUkey3 != null) { tvUkey3.setText(""); setFieldNormal(tvUkey3); }
        if (spQuality != null) { spQuality.setEnabled(false); spQuality.setSelection(0); }
        if (btnRegister != null) btnRegister.setEnabled(false);
    }
    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) return;
        KanbanData k = QRCodeParser.parseKanban(raw, true);
        if (!ud) {
            ukey1 = k;
            if (tvUkey1 != null) { tvUkey1.setText(k.ukey + "  " + k.displayName()); setFieldOk(tvUkey1); }
            db().checkFurnaceLost(k.ukey, cnt -> runOnUi(() -> {
                if (cnt == null || cnt == 0) {
                    showError(getString(R.string.err_furnace_p30_incomplete));
                    if (tvUkey1 != null) setFieldNg(tvUkey1); ukey1 = null; return;
                }
                db().findLostKanban(k.ukey, k.ukey, row -> runOnUi(() -> {
                    if (row == null) {
                        showError(getString(R.string.err_furnace_no_lost)); ukey1 = null; return;
                    }
                    // row = String[]{ukey, prno, bname, hinban, eban}
                    prn  = row.length > 1 ? row[1] : "";
                    prn2 = row.length > 0 ? row[0] : "";
                    ukey2 = new KanbanData(row[0], false);
                    if (tvUkey2 != null) { tvUkey2.setText(row[0]); setFieldActive(tvUkey2); }
                    ud = true;
                }));
            }));
        } else {
            if (ukey2 != null && !k.hid.equals(ukey2.hid)) { showError(getString(R.string.err_hid_mismatch)); return; }
            ukey3 = k;
            if (tvUkey3 != null) { tvUkey3.setText(k.ukey + "  " + k.displayName()); setFieldOk(tvUkey3); }
            if (spQuality != null) spQuality.setEnabled(true);
        }
    }
    private void onRegister() {
        if (ukey1 == null || ukey2 == null || ukey3 == null) return;
        db().p40InTempLost(ukey1.ukey, ukey2.ukey, ukey3.ukey, prn, prn2, FIXED_LN, jud,
            r -> runOnUi(() -> { showOk(getString(R.string.ok_registered)); initState(); }));
    }
    private void onBack() { vibrator().ok(); disableScanner(); safePopBackStack(); }
    @Override public void onDestroyView() { super.onDestroyView(); disableScanner(); }
}