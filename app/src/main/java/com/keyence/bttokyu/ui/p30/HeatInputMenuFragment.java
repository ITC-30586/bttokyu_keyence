package com.keyence.bttokyu.ui.p30;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.ui.BaseFragment;

/**
 * Panel2 / 工程30 熱処理投入メニュー
 *
 * ボタン一覧:
 *   NGレーン（進捗確認） → NglaneInputFragment  (to_nglane_input)
 *   手動炉投入           → ManualInputFragment   (to_manual_input)
 *   戻る                 → popBackStack
 */
public class HeatInputMenuFragment extends BaseFragment {

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_heat_input_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button btnNglane = view.findViewById(R.id.btnNglane);
        Button btnManual = view.findViewById(R.id.btnManual);
        Button btnBack   = view.findViewById(R.id.btnBack);

        btnNglane.setOnClickListener(v ->
                getMain().getNavController().navigate(R.id.to_nglane_input));

        btnManual.setOnClickListener(v ->
                getMain().getNavController().navigate(R.id.to_manual_input));

        btnBack.setOnClickListener(v ->
                safePopBackStack());

        // 追加
        vibrator().ok();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disableScanner();
    }
}
