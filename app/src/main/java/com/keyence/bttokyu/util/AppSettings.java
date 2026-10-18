package com.keyence.bttokyu.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * DB接続設定管理 (INI.iniの内容をSharedPreferencesへ移行)
 * 初回起動時はデフォルト値を設定 → 設定画面から変更可能
 */
public class AppSettings {

    private static final String PREFS_NAME = "bttokyu_settings";
    private static final String KEY_DB_HOST = "db_host";
    private static final String KEY_DB_PORT = "db_port";
    private static final String KEY_DB_NAME = "db_name";
    private static final String KEY_DB_USER = "db_user";
    private static final String KEY_DB_PASS = "db_pass";

    // 元INI.iniのデフォルト値
    private static final String DEFAULT_HOST = "10.152.208.33";
    // private static final String DEFAULT_HOST = "133.222.186.64";

    private static final String DEFAULT_PORT = "25432";
    private static final String DEFAULT_NAME = "dbtokyu";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASS = "postgres";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getDbHost() { return prefs.getString(KEY_DB_HOST, DEFAULT_HOST); }
    public String getDbPort() { return prefs.getString(KEY_DB_PORT, DEFAULT_PORT); }
    public String getDbName() { return prefs.getString(KEY_DB_NAME, DEFAULT_NAME); }
    public String getDbUser() { return prefs.getString(KEY_DB_USER, DEFAULT_USER); }
    public String getDbPass() { return prefs.getString(KEY_DB_PASS, DEFAULT_PASS); }

    public void save(String host, String port, String name, String user, String pass) {
        prefs.edit()
                .putString(KEY_DB_HOST, host)
                .putString(KEY_DB_PORT, port)
                .putString(KEY_DB_NAME, name)
                .putString(KEY_DB_USER, user)
                .putString(KEY_DB_PASS, pass)
                .apply();
    }

    /** JDBC接続URL生成 */
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName();
    }
}
