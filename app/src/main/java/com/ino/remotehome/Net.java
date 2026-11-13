package com.ino.remotehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** インターネットとのやり取り：要望の送信と、新しい版の確認・ダウンロードだけ。 */
final class Net {
    private Net() {}

    interface Progress {
        void onProgress(int percent);
    }

    static boolean formReady() {
        return !AppConfig.FORM_ID.isEmpty() && !AppConfig.FORM_ENTRY.isEmpty();
    }

    /**
     * 改善要望を Googleフォームへ送る（アカウント不要・メールアドレスは使わない）。
     * 本文の後ろに区切り「----」とアプリの版・画面・端末を付ける。
     */
    static boolean sendFeedback(Context c, String body, String screen) {
        if (!formReady()) return false;
        HttpURLConnection con = null;
        try {
            String info = "\n\n----\n"
                    + "app: RemoteHome " + versionName(c) + " (" + versionCode(c) + ")\n"
                    + "screen: " + screen + "\n"
                    + "device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE + "\n"
                    + "lang: " + java.util.Locale.getDefault();
            String data = "entry." + AppConfig.FORM_ENTRY + "=" + URLEncoder.encode(body.trim() + info, "UTF-8");
            URL u = new URL("https://docs.google.com/forms/d/e/" + AppConfig.FORM_ID + "/formResponse");
            con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setDoOutput(true);
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            OutputStream os = con.getOutputStream();
            os.write(data.getBytes("UTF-8"));
            os.close();
            int code = con.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static final class Latest {
        int versionCode;
        String versionName = "";
        String url = "";
        long size;
    }

    /**
     * 最新の Release を読む。Release の説明に「versionCode=数字」「versionName=文字」が書いてある。
     * 失敗・形式が違うときは null。
     */
    static Latest fetchLatest() {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(AppConfig.LATEST_API).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setUseCaches(false);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            if (con.getResponseCode() != 200) return null;
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) {
                bo.write(b, 0, n);
                if (bo.size() > 512 * 1024) break;
            }
            in.close();
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            String body = o.optString("body", "");
            Latest l = new Latest();
            for (String line : body.split("\n")) {
                line = line.trim();
                if (line.startsWith("versionCode=")) {
                    l.versionCode = Integer.parseInt(line.substring(12).trim());
                } else if (line.startsWith("versionName=")) {
                    l.versionName = line.substring(12).trim();
                }
            }
            JSONArray as = o.optJSONArray("assets");
            if (as != null) {
                for (int i = 0; i < as.length(); i++) {
                    JSONObject a = as.getJSONObject(i);
                    if (a.optString("name", "").endsWith(".apk")) {
                        l.url = a.optString("browser_download_url", "");
                        l.size = a.optLong("size", 0);
                        break;
                    }
                }
            }
            if (l.versionCode <= 0 || !l.url.startsWith(AppConfig.DOWNLOAD_PREFIX)) return null;
            return l;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** APK を取ってきて、アプリ専用の置き場所（cache）に保存する。失敗は null。 */
    static File download(Context c, Latest l, Progress pr) {
        if (!l.url.startsWith(AppConfig.DOWNLOAD_PREFIX)) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(l.url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            if (con.getResponseCode() != 200) return null;
            long total = con.getContentLengthLong();
            if (total <= 0) total = l.size;
            File dir = new File(c.getCacheDir(), "update");
            dir.mkdirs();
            File f = new File(dir, "update.apk");
            InputStream in = con.getInputStream();
            FileOutputStream out = new FileOutputStream(f);
            byte[] b = new byte[16384];
            long got = 0;
            int n, last = -1;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                got += n;
                if (total > 0 && pr != null) {
                    int p = (int) (got * 100 / total);
                    if (p != last) {
                        last = p;
                        pr.onProgress(p);
                    }
                }
            }
            out.close();
            in.close();
            if (total > 0 && got != total) return null;
            return f;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static SharedPreferences up(Context c) {
        return c.getSharedPreferences("update", Context.MODE_PRIVATE);
    }

    /** 1日1回まで確認する。新しい版が見つかったら true（結果は保存する）。 */
    static boolean checkDaily(Context c) {
        long now = System.currentTimeMillis();
        if (now - up(c).getLong("lastCheck", 0) < 20L * 60 * 60 * 1000) return false;
        up(c).edit().putLong("lastCheck", now).apply();
        Latest l = fetchLatest();
        if (l == null) return false;
        store(c, l);
        return l.versionCode > versionCode(c);
    }

    static void store(Context c, Latest l) {
        up(c).edit().putInt("latestCode", l.versionCode).putString("latestName", l.versionName).apply();
    }

    static boolean hasUpdate(Context c) {
        return up(c).getInt("latestCode", 0) > versionCode(c);
    }

    static String latestName(Context c) {
        return up(c).getString("latestName", "");
    }

    static int versionCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
