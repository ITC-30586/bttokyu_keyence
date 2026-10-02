package com.keyence.bttokyu.ui.p100;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.model.KanbanData;
import com.keyence.bttokyu.ui.BaseFragment;
import com.keyence.bttokyu.util.QRCodeParser;
/** Panel5 / KN=100: Stamp inspection */
public class StampFragment extends BaseFragment {
    private TextView tvStampCode, tvBname, tvHinban, tvEban;
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        return inf.inflate(R.layout.fragment_stamp, c, false);
    }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvStampCode = v.findViewById(R.id.tvStampCode);
        tvBname     = v.findViewById(R.id.tvBname);
        tvHinban    = v.findViewById(R.id.tvHinban);
        tvEban      = v.findViewById(R.id.tvEban);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> {
            vibrator().ok();
            disableScanner();
            safePopBackStack();
        });
        enableScanner(raw -> runOnUi(() -> processQr(raw)));

        // 追加
        vibrator().ok();
    }
    private void processQr(String raw) {
        if (!QRCodeParser.isKanban(raw)) return;
        KanbanData k = QRCodeParser.parseKanban(raw, true);
        if (k.isEmpty()) {
            if (tvBname != null) tvBname.setText("empty cage");
            if (tvHinban != null) tvHinban.setText("");
            if (tvEban != null) tvEban.setText(k.eban);
            if (tvStampCode != null) { tvStampCode.setText(""); tvStampCode.setBackgroundColor(Color.WHITE); }
            return;
        }
        if (tvBname  != null) tvBname.setText(k.displayName());
        if (tvHinban != null) tvHinban.setText(k.isTpKanban() ? "TP" : k.hinban);
        if (tvEban   != null) tvEban.setText(k.eban);
        if (tvStampCode != null) { tvStampCode.setText(""); tvStampCode.setBackgroundColor(Color.WHITE); }
        db().p70InTemp(k.ukey, result -> runOnUi(() -> {
            if (tvStampCode == null) return;
            if (result == null || result.isEmpty()) { tvStampCode.setText("---"); return; }
            String code  = result.length() >= 3 ? result.substring(0, 3) : result;
            String flags = result.length() >= 2 ? result.substring(result.length() - 2) : "00";
            tvStampCode.setText(code);
            android.content.Context ctx = safeContext();
            if ("00".equals(flags)) {
                tvStampCode.setBackgroundColor(Color.WHITE);
                vibrator().ok();
            } else {
                if (ctx != null) tvStampCode.setBackgroundColor(ContextCompat.getColor(ctx, R.color.field_ng));
                vibrator().error();
            }
        }));
    }
    @Override public void onDestroyView() { super.onDestroyView(); disableScanner(); }
}