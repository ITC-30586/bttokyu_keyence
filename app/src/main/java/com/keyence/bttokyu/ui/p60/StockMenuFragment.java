package com.keyence.bttokyu.ui.p60;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.navigation.NavController;
import com.keyence.bttokyu.R;
import com.keyence.bttokyu.ui.BaseFragment;

/**
 * Panel9 / 工程60 中間在庫置場メニュー
 * ナビゲーション先: 出庫(StockOutFragment) / 返却(StockReturnFragment)
 */
public class StockMenuFragment extends BaseFragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stock_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = getMain().getNavController();

        view.findViewById(R.id.btnStockOut).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_stock_out);
        });

        view.findViewById(R.id.btnStockReturn).setOnClickListener(x -> {
            vibrator().ok();
            nav.navigate(R.id.to_stock_return);
        });

        view.findViewById(R.id.btnBack).setOnClickListener(x -> {
            vibrator().ok();
            safePopBackStack();
        });

        // 追加
        vibrator().ok();
    }
}
