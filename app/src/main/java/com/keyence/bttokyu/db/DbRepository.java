package com.keyence.bttokyu.db;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.keyence.bttokyu.util.AppSettings;
import com.keyence.bttokyu.util.FileLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL DB アクセス層。
 * ログタグ: "DbRepo"
 *   [INIT]   - 初期化
 *   [CONNECT] - 接続試行（接続先情報）
 *   [CALL]   - 関数呼び出し（引数）
 *   [SQL]    - 実行SQL
 *   [RESULT] - 実行結果（値・実行時間）
 *   [WARN]   - 警告（結果なし等）
 *   [ERROR]  - エラー（例外）
 *   [SHUTDOWN] - 終了
 */
public class DbRepository {

    private static final String TAG = "DbRepo";
    private static final String JDBC_DRIVER = "org.postgresql.Driver";

    public interface DbErrorHandler {
        void onDbError(String functionName, String errorDetail);
    }

    @FunctionalInterface
    public interface DbCallback<T> {
        void onSuccess(T result);
    }

    private final AppSettings settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile DbErrorHandler errorHandler;

    public void setErrorHandler(DbErrorHandler handler) { this.errorHandler = handler; }
    public void setAppContext(android.content.Context ctx) { this.appContext = ctx; }

    private android.content.Context appContext;

    public DbRepository(AppSettings settings) {
        this.settings = settings;
        // appContextはScannerManager等から設定される（後述）
        try {
            Class.forName(JDBC_DRIVER);
            Log.i(TAG, "[INIT] JDBC driver loaded: " + JDBC_DRIVER);
            Log.i(TAG, "[INIT] Target: " + settings.getJdbcUrl());
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "[INIT] JDBC driver not found", e);
        }
    }

    // ---- 時刻スタンプ ----

    private static String ts() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN).format(new Date());
    }

    // ---- エラーメッセージ生成 ----

    private String buildErrorMessage(String funcName, Exception e) {
        String raw = e.getMessage();
        if (raw == null) raw = e.getClass().getSimpleName();
        String server = settings.getDbHost() + ":" + settings.getDbPort();
        StringBuilder sb = new StringBuilder();
        sb.append("実行処理 : ").append(funcName).append("\n");
        sb.append("接続先   : ").append(server).append("\n\n");
        if (raw.contains("ENETUNREACH") || raw.contains("Network is unreachable")) {
            sb.append("原因 : ネットワーク未接続\n").append("対処 : 端末が工場LANに接続されているか確認してください");
        } else if (raw.contains("Connection refused")) {
            sb.append("原因 : DBサーバーへの接続拒否\n").append("対処 : PostgreSQLサービスが起動しているか確認してください");
        } else if (raw.toLowerCase().contains("timeout")) {
            sb.append("原因 : 接続タイムアウト\n").append("対処 : ネットワーク遅延またはサーバー無応答です");
        } else if (raw.contains("password") || raw.contains("authentication")) {
            sb.append("原因 : DB認証エラー\n").append("対処 : ユーザーID/パスワードを確認してください");
        } else if (raw.contains("does not exist")) {
            sb.append("原因 : テーブル/関数が見つかりません\n").append("対処 : DB構成を確認してください");
        } else {
            sb.append("原因 : ").append(raw);
        }
        return sb.toString();
    }

    private void notifyError(String funcName, Exception e) {
        Log.e(TAG, "[ERROR] " + ts() + " " + funcName, e);
        // スタックトレースをファイルに記録
        if (appContext != null) {
            FileLogger.getInstance(appContext).log("DB-Error", "[" + funcName + "] " + e.getMessage(), e);
        }
        if (errorHandler == null) return;
        String msg = buildErrorMessage(funcName, e);
        mainHandler.post(() -> { if (errorHandler != null) errorHandler.onDbError(funcName, msg); });
    }

    private static String extractFuncName(String sql) {
        if (sql == null) return "unknown";
        int s = sql.indexOf("SELECT ") + 7;
        int e = sql.indexOf("(", s);
        if (s > 6 && e > s) return sql.substring(s, e).trim();
        return sql.length() > 40 ? sql.substring(0, 40) + "..." : sql;
    }

    // ---- 接続 ----

    private Connection connect() throws Exception {
        String info = "host=" + settings.getDbHost()
                + " port=" + settings.getDbPort()
                + " db=" + settings.getDbName()
                + " user=" + settings.getDbUser();
        Log.d(TAG, "[CONNECT] " + ts() + " " + info);
        if (appContext != null) FileLogger.getInstance(appContext).log("DB-Connect", "Connecting: " + info);
        long t0 = System.currentTimeMillis();
        try {
            Connection conn = DriverManager.getConnection(settings.getJdbcUrl(), settings.getDbUser(), settings.getDbPass());
            long ms = System.currentTimeMillis() - t0;
            Log.d(TAG, "[CONNECT] connected in " + ms + "ms");
            if (appContext != null) FileLogger.getInstance(appContext).log("DB-Connect", "Connected OK in " + ms + "ms");
            return conn;
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            if (appContext != null) {
                FileLogger.getInstance(appContext).log("DB-Connect", "FAILED after " + ms + "ms: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    // ---- 汎用実行 ----

    private void exec(String sql, DbCallback<String> cb) {
        executor.execute(() -> {
            String func = extractFuncName(sql);
            long t0 = System.currentTimeMillis();
            Log.d(TAG, "[SQL] " + ts() + " " + func + " >> " + sql);
            try (Connection conn = connect();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                String result = rs.next() ? rs.getString(1) : "";
                if (result == null) result = "";
                long ms = System.currentTimeMillis() - t0;
                Log.i(TAG, "[RESULT] " + ts() + " " + func + " => [" + result + "] (" + ms + "ms)");
                if (cb != null) cb.onSuccess(result);
            } catch (Exception e) {
                long ms = System.currentTimeMillis() - t0;
                Log.e(TAG, "[ERROR] " + ts() + " " + func + " failed in " + ms + "ms", e);
                notifyError(func, e);
            }
        });
    }

    private void execNoResult(String sql, DbCallback<Void> cb) {
        executor.execute(() -> {
            String func = extractFuncName(sql);
            long t0 = System.currentTimeMillis();
            Log.d(TAG, "[SQL] " + ts() + " " + func + " >> " + sql);
            try (Connection conn = connect(); Statement st = conn.createStatement()) {
                st.execute(sql);
                long ms = System.currentTimeMillis() - t0;
                Log.i(TAG, "[RESULT] " + ts() + " " + func + " => OK (" + ms + "ms)");
                if (cb != null) cb.onSuccess(null);
            } catch (Exception e) {
                long ms = System.currentTimeMillis() - t0;
                Log.e(TAG, "[ERROR] " + ts() + " " + func + " failed in " + ms + "ms", e);
                notifyError(func, e);
            }
        });
    }

    // ---- 工程10 ----

    public void p10InTemp(String dc, String kat, String cav,
                          String ukey1, String ukey2, String hold, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p10_in_temp dc=" + dc + " kat=" + kat + " cav=" + cav
                + " ukey1=" + ukey1 + " ukey2=" + ukey2 + " hold=" + hold);
        exec("SELECT p10_in_temp('" + dc + "','" + kat + "','" + cav + "','" + ukey1 + "','" + ukey2 + "','" + hold + "','b')", cb);
    }
    // ---- 工程20 ----

    public void p20Check(String ukey1, String ukey2, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p20_check ukey1=" + ukey1 + " ukey2=" + ukey2);
        exec("SELECT p20_check('" + ukey1 + "','" + ukey2 + "')", cb);
    }
    public void p20InTemp(String ukey1, String ukey2, DbCallback<Void> cb) {
        Log.i(TAG, "[CALL] p20_in_temp ukey1=" + ukey1 + " ukey2=" + ukey2);
        execNoResult("SELECT p20_in_temp('" + ukey1 + "','" + ukey2 + "','b')", cb);
    }

    // ---- 工程30 ----

    public void p30Check(String ukey1, String ukey2, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p30_check ukey1=" + ukey1 + " ukey2=" + ukey2);
        exec("SELECT p30_check('" + ukey1 + "','" + ukey2 + "')", cb);
    }
    public void p30InTemp(String ukey1, String ukey2, String ln, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p30_in_temp ukey1=" + ukey1 + " ukey2=" + ukey2 + " ln=" + ln);
        exec("SELECT p30_in_temp('" + ukey1 + "','" + ukey2 + "','" + ln + "','b')", cb);
    }

    // ---- 工程40 ----

    public void p40Check(String ukey1, String ukey2, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p40_check ukey1=" + ukey1 + " ukey2=" + ukey2);
        exec("SELECT p40_check('" + ukey1 + "','" + ukey2 + "')", cb);
    }
    public void p40InTemp(String ukey1, String ukey2, String ln, String jud, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p40_in_temp ukey1=" + ukey1 + " ukey2=" + ukey2 + " ln=" + ln + " jud=" + jud);
        exec("SELECT p40_in_temp('" + ukey1 + "','" + ukey2 + "','" + ln + "','b','" + jud + "')", cb);
    }
    public void p40InTempLost(String ukey1, String ukey2, String ukey3,
                               String prn, String prn2, String ln, String jud, DbCallback<Void> cb) {
        Log.i(TAG, "[CALL] p40_in_temp_lost ukey1=" + ukey1 + " ukey2=" + ukey2
                + " ukey3=" + ukey3 + " prn=" + prn + " prn2=" + prn2 + " ln=" + ln + " jud=" + jud);
        execNoResult("SELECT p40_in_temp_lost('" + ukey1 + "','" + ukey2 + "','" + ukey3 + "','"
                + prn + "','" + prn2 + "','" + ln + "','" + jud + "')", cb);
    }

    // ---- 工程60 ----

    public void p60InTemp(String ukey1, String ukey2, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p60_in_temp ukey1=" + ukey1 + " ukey2=" + ukey2);
        exec("SELECT p60_in_temp('" + ukey1 + "','" + ukey2 + "','b')", cb);
    }

    // ---- 工程70 ----

    public void p70InTemp(String ukey, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p70_in_temp ukey=" + ukey);
        exec("SELECT p70_in_temp('" + ukey + "','0','b')", cb);
    }
    public void p70ReturnInTemp(String ukey1, String ukey2, DbCallback<Void> cb) {
        Log.i(TAG, "[CALL] p70_return_in_temp ukey1=" + ukey1 + " ukey2=" + ukey2);
        String sql = (ukey2 == null || ukey2.isEmpty())
                ? "SELECT p70_return_in_temp('" + ukey1 + "')"
                : "SELECT p70_return_in_temp('" + ukey1 + "','" + ukey2 + "')";
        execNoResult(sql, cb);
    }
    public void pLotout(String ukey1, String ukey2, String flag, DbCallback<String> cb) {
        Log.i(TAG, "[CALL] p_lotout ukey1=" + ukey1 + " ukey2=" + ukey2 + " flag=" + flag);
        String sql = (ukey2 == null || ukey2.isEmpty())
                ? "SELECT p_lotout('" + ukey1 + "','" + flag + "')"
                : "SELECT p_lotout('" + ukey1 + "','" + ukey2 + "','" + flag + "')";
        exec(sql, cb);
    }

    // ---- 炉内紛失 ----

    public void checkFurnaceLost(String ukey1, DbCallback<Integer> cb) {
        Log.i(TAG, "[CALL] checkFurnaceLost ukey1=" + ukey1);
        executor.execute(() -> {
            long t0 = System.currentTimeMillis();
            Log.d(TAG, "[SQL] " + ts() + " SELECT COUNT(*) FROM process_log WHERE ukey='" + ukey1 + "' AND en='3'");
            try (Connection conn = connect(); Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM process_log WHERE ukey='" + ukey1 + "' AND en='3'")) {
                int count = rs.next() ? rs.getInt(1) : 0;
                Log.i(TAG, "[RESULT] " + ts() + " checkFurnaceLost count=" + count + " (" + (System.currentTimeMillis()-t0) + "ms)");
                if (cb != null) cb.onSuccess(count);
            } catch (Exception e) {
                Log.e(TAG, "[ERROR] " + ts() + " checkFurnaceLost failed", e);
                notifyError("checkFurnaceLost", e);
            }
        });
    }
    public void findLostKanban(String prn2, String excludeUkey, DbCallback<String[]> cb) {
        Log.i(TAG, "[CALL] findLostKanban prn2=" + prn2 + " exclude=" + excludeUkey);
        executor.execute(() -> {
            long t0 = System.currentTimeMillis();
            Log.d(TAG, "[SQL] " + ts() + " SELECT * FROM view_process_log_1 WHERE prno2='" + prn2 + "' AND ukey<>'" + excludeUkey + "'");
            try (Connection conn = connect(); Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM view_process_log_1 WHERE prno2='" + prn2 + "' AND ukey<>'" + excludeUkey + "'")) {
                if (rs.next()) {
                    String[] row = { rs.getString("ukey"), rs.getString("prno"), rs.getString("bname"), rs.getString("hinban"), rs.getString("eban") };
                    Log.i(TAG, "[RESULT] " + ts() + " findLostKanban ukey=" + row[0] + " (" + (System.currentTimeMillis()-t0) + "ms)");
                    if (cb != null) cb.onSuccess(row);
                } else {
                    Log.w(TAG, "[WARN] " + ts() + " findLostKanban: no record for prn2=" + prn2);
                    notifyError("findLostKanban", new Exception("紛失かんばんが見つかりません"));
                }
            } catch (Exception e) {
                Log.e(TAG, "[ERROR] " + ts() + " findLostKanban failed", e);
                notifyError("findLostKanban", e);
            }
        });
    }

    public void shutdown() {
        Log.i(TAG, "[SHUTDOWN] DbRepository executor shutting down");
        executor.shutdown();
    }
}
