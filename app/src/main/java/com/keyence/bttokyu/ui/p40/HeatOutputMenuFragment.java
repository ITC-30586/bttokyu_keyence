package com.keyence.bttokyu.ui.p40;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.navigation.NavController;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.ui.BaseFragment;

/**
 * Panel6 / 工程40 熱処理搬出メニュー
 * ナビゲーション先: NGレーン搬出(NglaneOutputFragment) / 手動搬出(ManualOutputFragment)
 */
public class HeatOutputMenuFragment extends BaseFragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_heat_output_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = getMain().getNavController();

        view.findViewById(R.id.btnNglaneOutput).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_nglane_output);
        });

        view.findViewById(R.id.btnManualOutput).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_manual_output);
        });

        view.findViewById(R.id.btnBack).setOnClickListener(x -> {
            vibrator().ok();
            safePopBackStack();
        });

        // 追加
        vibrator().ok();

    }
}
