package com.keyence.bttokyu.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ファイルベースデバッグロガー
 *
 * ログ保存先: /sdcard/Android/data/com.keyence.bttokyu/files/debug_YYYYMMDD.log
 *
 * ログ取得方法:
 *   adb pull /sdcard/Android/data/com.keyence.bttokyu/files/ C:\logs\
 *
 * またはファイルマネージャーアプリから:
 *   内部ストレージ/Android/data/com.keyence.bttokyu/files/
 */
public class FileLogger {

    private static final String TAG = "FileLogger";
    private static FileLogger instance;

    private File logFile;
    private final SimpleDateFormat dtf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN);
    private final SimpleDateFormat datef = new SimpleDateFormat("yyyyMMdd", Locale.JAPAN);

    private FileLogger(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir(); // フォールバック
            if (!dir.exists()) dir.mkdirs();

            String date = datef.format(new Date());
            logFile = new File(dir, "debug_" + date + ".log");
            write("===== BTTokyu Debug Log Started =====");
            write("Date: " + dtf.format(new Date()));
            write("File: " + logFile.getAbsolutePath());
            write("=====================================");
            Log.i(TAG, "FileLogger initialized: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "FileLogger init failed", e);
        }
    }

    public static synchronized FileLogger getInstance(Context context) {
        if (instance == null) {
            instance = new FileLogger(context.getApplicationContext());
        }
        return instance;
    }

    /** ログを書き込む */
    public synchronized void log(String tag, String message) {
        String line = dtf.format(new Date()) + " [" + tag + "] " + message;
        Log.d(TAG, line);
        write(line);
    }

    /** 例外付きログ */
    public synchronized void log(String tag, String message, Throwable t) {
        log(tag, message + " | Exception: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        // スタックトレースも書く
        if (logFile != null) {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                t.printStackTrace(pw);
            } catch (IOException ignored) {}
        }
    }

    /** セパレーター行 */
    public void section(String title) {
        log("----", "--- " + title + " ---");
    }

    /**
     * クラッシュ情報をファイルに書き込む（UncaughtExceptionHandler から呼ぶ）
     * スタックトレース全体を記録する
     */
    public synchronized void logCrash(Thread thread, Throwable t) {
        write("===== CRASH DETECTED =====");
        write(dtf.format(new Date()) + " Thread: " + thread.getName());
        write("Exception: " + t.getClass().getName() + ": " + t.getMessage());
        // スタックトレース全行
        for (StackTraceElement el : t.getStackTrace()) {
            write("  at " + el.toString());
        }
        // Cause チェーン
        Throwable cause = t.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            write("Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
            for (StackTraceElement el : cause.getStackTrace()) {
                write("  at " + el.toString());
            }
            cause = cause.getCause();
            depth++;
        }
        write("===== END OF CRASH =====");
    }

    /** ログファイルのパスを取得 */
    public String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "不明";
    }

    private void write(String line) {
        if (logFile == null) return;
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Write failed", e);
        }
    }
}
