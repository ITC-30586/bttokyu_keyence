package com.keyence.bttokyu.util;

import com.keyence.bttokyu.model.KanbanData;

/**
 * QRコード解析ユーティリティ
 * 元ソース: Form1.vb Get_DATA() の各工程分岐ロジックを移植
 */
public class QRCodeParser {

    // ---- QRコード種別判定 ----

    /**
     * かんばんQRコード判定。
     * 元ソース(Form1.vb Get_DATA): Strings.Left(QD, 2) = "HT" のみ判定。
     * 長さチェックは元ソースに存在しないため行わない。
     */
    public static boolean isKanban(String raw) {
        return raw != null && raw.startsWith("HT");
    }

    /**
     * DCコード判定。
     * 元ソース: Strings.Left(QD,2)="DC" And Len(QD)=14
     * 実機では長さが異なる場合があるため長さ制限を撤廃。
     * HT同様に "DC" プレフィックスのみ必須。
     */
    public static boolean isDcCode(String raw) {
        return raw != null && raw.startsWith("DC") && raw.length() >= 4;
    }

    /**
     * DEコード判定。
     * 元ソース: Strings.Left(QD,2)="DE" のみ（長さチェックなし）
     */
    public static boolean isDeCode(String raw) {
        return raw != null && raw.startsWith("DE") && raw.length() >= 4;
    }

    /**
     * CAコード判定。
     * 元ソース: Strings.Left(QD,2)="CA" のみ
     */
    public static boolean isCaCode(String raw) {
        return raw != null && raw.startsWith("CA") && raw.length() >= 4;
    }

    public static boolean isLnCode(String raw) {
        return raw != null && raw.startsWith("LN") && raw.length() >= 4;
    }

    // ---- 各コードのペイロード取得 ----

    /**
     * DC: "DC" 除去後の残り全文字（マシン番号）
     * 元ソース: Strings.Mid(QD, 3, 2) = 3文字目から2文字
     * 実機フォーマットに合わせて残り全部を取得
     */
    public static String parseDc(String raw) {
        // VB準拠: Strings.Mid(QD, 3, 2) = DCプレフィックス後の先頭2文字のみ
        // 例: DC010000000000 → "01" → TextBox63.Text = "DC01"
        if (raw.length() >= 4) return raw.substring(2, 4);
        return raw.length() > 2 ? raw.substring(2) : "";
    }

    /** DE: プレフィックス除去後の16文字 */
    public static String parseDe(String raw) {
        return raw.substring(2, Math.min(18, raw.length()));
    }

    /** DE: 金型コード（DEペイロードの1〜11文字目） */
    public static String parseKatCode(String dePayload) {
        return dePayload.length() >= 11 ? dePayload.substring(0, 11) : dePayload;
    }

    /** DE: 金型番号（DEペイロードの10〜11文字目） */
    public static String parseKatNo(String dePayload) {
        return dePayload.length() >= 12 ? dePayload.substring(9, 11) : "";
    }

    /** DE: 金型品番ID（DEペイロードの12〜16文字目、5文字）*/
    public static String parseKatHid(String dePayload) {
        return dePayload.length() >= 16 ? dePayload.substring(11, 16) : "";
    }

    /** CA: キャビティ番号（4文字目） */
    public static String parseCa(String raw) {
        return raw.length() >= 4 ? raw.substring(3, 4) : "";
    }

    /** LN: 炉番号（整数変換） */
    public static String parseLn(String raw) {
        String payload = raw.substring(2, Math.min(4, raw.length()));
        try { return String.valueOf(Integer.parseInt(payload.trim())); }
        catch (NumberFormatException e) { return payload; }
    }

    /** HT かんばんデータ（HTプレフィックス除去後） */
    public static String parseKanbanPayload(String raw) {
        return raw.length() > 2 ? raw.substring(2) : "";
    }

    public static KanbanData parseKanban(String raw, boolean isUpper) {
        return new KanbanData(parseKanbanPayload(raw), isUpper);
    }

    // ---- バリデーション ----

    /** 上下段が同一かんばんでないか確認 */
    public static boolean isSameKanban(KanbanData upper, KanbanData lower) {
        return upper.ukey.equals(lower.ukey);
    }

    /** 品番ID不一致チェック（空カゴは除外） */
    public static boolean isHidMismatch(KanbanData upper, KanbanData lower) {
        if (upper.isEmpty()) return false;
        return !upper.hid.equals(lower.hid);
    }
}
