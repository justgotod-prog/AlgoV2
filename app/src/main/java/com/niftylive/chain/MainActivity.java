package com.niftylive.chain;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private String fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + ": " + sb);
        return sb.toString();
    }

    private JSONObject buildChainPayload() throws Exception {
        String expiryRaw = fetchJson("https://algostraddle.com/api/live/expiries?index=NIFTY");
        String expiryDate = "2026-11-03";
        if (expiryRaw.trim().startsWith("[")) {
            JSONArray arr = new JSONArray(expiryRaw);
            if (arr.length() > 0) expiryDate = arr.getString(0);
        }

        String chainJson = fetchJson(
                "https://algostraddle.com/api/live/oi-chain?index=NIFTY&expiry="
                        + expiryDate + "&strikes_range=50");
        JSONObject data = new JSONObject(chainJson);
        JSONArray chain = data.optJSONArray("chain");
        if (chain == null) chain = new JSONArray();

        double callInv = 0, putInv = 0;
        JSONArray outChain = new JSONArray();
        for (int i = 0; i < chain.length(); i++) {
            JSONObject row = chain.getJSONObject(i);
            double callOi = row.optDouble("call_oi", 0);
            double putOi = row.optDouble("put_oi", 0);
            double callLtp = row.optDouble("call_ltp", 0);
            double putLtp = row.optDouble("put_ltp", 0);
            callInv += callOi * callLtp;
            putInv += putOi * putLtp;

            JSONObject o = new JSONObject();
            o.put("strike", row.optInt("strike"));
            o.put("call_oi", callOi);
            o.put("call_ltp", callLtp);
            o.put("put_oi", putOi);
            o.put("put_ltp", putLtp);
            o.put("is_atm", row.optBoolean("is_atm", false));
            outChain.put(o);
        }

        double pctDiff = putInv > 0 ? ((callInv - putInv) / putInv) * 100.0 : 0;

        JSONObject out = new JSONObject();
        out.put("spot", data.optDouble("spot", 0));
        out.put("expiry", data.optString("expiry", expiryDate));
        out.put("pcr", data.optDouble("pcr", 0));
        out.put("max_pain", data.optDouble("max_pain", 0));
        out.put("call_investment", callInv);
        out.put("put_investment", putInv);
        out.put("total_investment", callInv + putInv);
        out.put("pct_diff", pctDiff);
        out.put("strike_count", outChain.length());
        out.put("chain", outChain);

        // Yahoo OHLC
        try {
            String y = fetchJson(
                    "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1d&range=5d");
            JSONObject chart = new JSONObject(y)
                    .getJSONObject("chart")
                    .getJSONArray("result")
                    .getJSONObject(0);
            JSONObject meta = chart.getJSONObject("meta");
            JSONObject quote = chart.getJSONObject("indicators")
                    .getJSONArray("quote").getJSONObject(0);

            JSONArray closes = quote.optJSONArray("close");
            JSONArray opens = quote.optJSONArray("open");
            Double prevClose = null;
            if (closes != null) {
                for (int i = closes.length() - 2; i >= 0; i--) {
                    if (!closes.isNull(i)) {
                        prevClose = closes.getDouble(i);
                        break;
                    }
                }
            }
            if (prevClose == null && meta.has("chartPreviousClose")) {
                prevClose = meta.getDouble("chartPreviousClose");
            }

            Double dayOpen = null;
            if (opens != null) {
                for (int i = opens.length() - 1; i >= 0; i--) {
                    if (!opens.isNull(i)) {
                        dayOpen = opens.getDouble(i);
                        break;
                    }
                }
            }

            Double high = meta.has("regularMarketDayHigh") ? meta.getDouble("regularMarketDayHigh") : null;
            Double low = meta.has("regularMarketDayLow") ? meta.getDouble("regularMarketDayLow") : null;
            Double price = meta.has("regularMarketPrice") ? meta.getDouble("regularMarketPrice") : null;

            if (prevClose != null) out.put("prev_close", prevClose);
            else out.put("prev_close", JSONObject.NULL);
            if (dayOpen != null) out.put("open", dayOpen);
            else out.put("open", JSONObject.NULL);
            if (high != null) out.put("high", high);
            else out.put("high", JSONObject.NULL);
            if (low != null) out.put("low", low);
            else out.put("low", JSONObject.NULL);

            if (price != null && prevClose != null && prevClose != 0) {
                double chg = price - prevClose;
                out.put("nifty_change", chg);
                out.put("nifty_change_pct", (chg / prevClose) * 100.0);
            } else {
                out.put("nifty_change", JSONObject.NULL);
                out.put("nifty_change_pct", JSONObject.NULL);
            }
        } catch (Exception e) {
            out.put("prev_close", JSONObject.NULL);
            out.put("open", JSONObject.NULL);
            out.put("high", JSONObject.NULL);
            out.put("low", JSONObject.NULL);
            out.put("nifty_change", JSONObject.NULL);
            out.put("nifty_change_pct", JSONObject.NULL);
        }

        return out;
    }

    public class WebAppInterface {
        @JavascriptInterface
        public String fetchChain() {
            try {
                return buildChainPayload().toString();
            } catch (Exception e) {
                try {
                    JSONObject err = new JSONObject();
                    err.put("error", e.getMessage() != null ? e.getMessage() : "fetch failed");
                    return err.toString();
                } catch (Exception e2) {
                    return "{\"error\":\"unknown\"}";
                }
            }
        }
    }
}
