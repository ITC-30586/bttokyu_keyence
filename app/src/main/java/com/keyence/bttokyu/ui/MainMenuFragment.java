package com.keyence.bttokyu.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import com.keyence.bttokyu.R;

public class MainMenuFragment extends BaseFragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        // 追加
        vibrator().ok();

        return inf.inflate(R.layout.fragment_main_menu, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        NavController nav = getMain().getNavController();

        v.findViewById(R.id.btnP10).setOnClickListener(x ->
                nav.navigate(R.id.to_casting_work));
        v.findViewById(R.id.btnP20).setOnClickListener(x ->
                nav.navigate(R.id.to_sorting));
        v.findViewById(R.id.btnP30).setOnClickListener(x ->
                nav.navigate(R.id.to_heat_input_menu));
        v.findViewById(R.id.btnP40).setOnClickListener(x ->
                nav.navigate(R.id.to_heat_output_menu));
        v.findViewById(R.id.btnP60).setOnClickListener(x ->
                nav.navigate(R.id.to_stock_menu));
        v.findViewById(R.id.btnP70).setOnClickListener(x ->
                nav.navigate(R.id.to_quality_menu));
        v.findViewById(R.id.btnP100).setOnClickListener(x ->
                nav.navigate(R.id.to_stamp));
        v.findViewById(R.id.btnReboot).setOnClickListener(x -> confirmReboot());
    }

    private void confirmReboot() {
        new AlertDialog.Builder(requireContext())
                .setTitle("再起動")
                .setMessage("システムを再起動しますか？")
                .setPositiveButton("はい", (d, w) -> {
                    vibrator().ok();
                    android.os.Process.killProcess(android.os.Process.myPid());
                })
                .setNegativeButton("いいえ", null)
                .show();
    }
}
