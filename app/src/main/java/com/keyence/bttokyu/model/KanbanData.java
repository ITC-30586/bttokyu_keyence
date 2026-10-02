package com.keyence.bttokyu.model;

/**
 * かんばんQRコード解析結果（HTプレフィックス除去後のデータから生成）。
 *
 * 実機QRコード例: HT11020030002RO3BJ012-145（25文字）
 * ペイロード構成（ダミー文字なし形式）:
 *   index 0     : KT（かんばんタイプ）
 *   index 1     : HT（熱処理有無 0/1）
 *   index 2-6   : HID（品番ID 5文字）
 *   index 7-10  : EBAN（枝番 4文字）
 *   index 11-12 : BN（部品名 2文字）
 *   index 13〜  : HINBAN（商品品番、残り全文字）
 */
public class KanbanData {

    public final String kt;      // かんばんタイプ
    public final String ht;      // 熱処理有無 "0"=なし / "1"=あり
    public final String hid;     // 品番ID（5文字）
    public final String eban;    // 枝番（4文字）
    public final String bname;   // 部品名（2文字）
    public final String hinban;  // 商品品番（index 13〜末尾）
    public final String ukey;    // DBキー = kt+ht+hid+eban（11文字）

    /** 上段/下段フラグ */
    public final boolean isUpper;

    public KanbanData(String rawQr, boolean isUpper) {
        // rawQr は "HT" プレフィックス除去後の文字列
        this.isUpper = isUpper;
        this.kt      = rawQr.length() >= 1  ? rawQr.substring(0, 1)   : "";
        this.ht      = rawQr.length() >= 2  ? rawQr.substring(1, 2)   : "";
        this.hid     = rawQr.length() >= 7  ? rawQr.substring(2, 7)   : "";
        this.eban    = rawQr.length() >= 11 ? rawQr.substring(7, 11)  : "";
        this.bname   = rawQr.length() >= 13 ? rawQr.substring(11, 13) : "";
        // HINBAN: index 14 から末尾まで
        // 元ソース: Strings.Mid(D_QR, 15, 11) → 1-based位置15 = 0-based index 14
        // 実機確認済み: HT11020030002RO3BJ012-145 → hinban = "BJ012-145"
        // index 13 の文字（"3"等）は VBでも使用していない「不明フィールド」
        this.hinban  = rawQr.length() > 14  ? rawQr.substring(14)     : "";
        this.ukey    = rawQr.length() >= 11 ? rawQr.substring(0, 11)  : rawQr;
    }

    public boolean isEmpty()   { return "00000".equals(hid); }
    public boolean isTpKanban(){ return "00001".equals(hid); }

    public String displayName() {
        if (isEmpty())    return "空カゴ";
        if (isTpKanban()) return "TPかんばん";
        return bname;
    }
}
