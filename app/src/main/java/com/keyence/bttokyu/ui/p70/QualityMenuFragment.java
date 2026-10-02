package com.keyence.bttokyu.ui.p70;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.navigation.NavController;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.ui.BaseFragment;

/**
 * Panel14 / 工程70 品質管理メニュー
 */
public class QualityMenuFragment extends BaseFragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quality_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        NavController nav = getMain().getNavController();

        // 返却処理
        v.findViewById(R.id.btnReturn).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_return);
        });

        // 保留品登録
        v.findViewById(R.id.btnHoldRegister).setOnClickListener(x -> {
            vibrator().ok();
            Bundle args = new Bundle();
            args.putString("mode", "register");
            nav.navigate(R.id.to_hold, args);
        });

        // 保留品解除
        v.findViewById(R.id.btnHoldRelease).setOnClickListener(x -> {
            vibrator().ok();
            Bundle args = new Bundle();
            args.putString("mode", "release");
            nav.navigate(R.id.to_hold, args);
        });

        // 廃棄処理
        v.findViewById(R.id.btnDiscard).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_discard);
        });

        // 炉内紛失対応
        v.findViewById(R.id.btnFurnaceLost).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_furnace_lost);
        });

        // 戻る
        v.findViewById(R.id.btnBack).setOnClickListener(x -> {
            vibrator().ok();
            safePopBackStack();
        });

        // 追加
        vibrator().ok();
    }
}
