package com.ppfha.keuangan;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PageRange;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;
import android.content.ClipData;
import android.net.Uri;
import android.provider.Settings;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    // Build acuan: PDF web sudah diuji kembali ke Dashboard setelah cetak.
    private WebView webView;
    private WebView printWebView;
    private PrintDocumentAdapter printAdapter;
    private static final int PICK_PDF_FOR_WHATSAPP = 4101;
    private static final int PICK_IMAGE_FOR_WEBVIEW = 4102;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingWhatsAppMessage = "";
    private boolean returningFromPrint = false;
    private String selectedRole = "";
    private String pendingFinanceAction = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.enableSlowWholeDocumentDraw();
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        webView.addJavascriptInterface(new AndroidBridge(), "BendaharaAndroid");
        // Aktifkan pemilih berkas untuk input type=file pada portal pembayaran online.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                }
                fileChooserCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams != null
                            ? fileChooserParams.createIntent()
                            : new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        fileChooserCallback.onReceiveValue(null);
                        fileChooserCallback = null;
                        Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk memilih foto.", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    startActivityForResult(intent, PICK_IMAGE_FOR_WEBVIEW);
                    return true;
                } catch (Exception e) {
                    if (fileChooserCallback != null) {
                        fileChooserCallback.onReceiveValue(null);
                        fileChooserCallback = null;
                    }
                    Toast.makeText(MainActivity.this, "Pemilih foto tidak dapat dibuka.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return bukaTautanEksternal(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return bukaTautanEksternal(Uri.parse(url));
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("bendahara".equals(selectedRole) && url != null && url.startsWith("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/")) {
                    // Jangan menimpa fungsi dashboard dari index.html; biarkan sistem web memakai ringkasan asli.

                }
                if ("bendahara".equals(selectedRole) && url != null && url.startsWith("https://spp-pesantren-zeta.vercel.app/")) {
                    view.evaluateJavascript("(function(){if(document.getElementById('ppfha-menu-keuangan'))return;var b=document.createElement('button');b.id='ppfha-menu-keuangan';b.textContent='⌂ Beranda Bendahara';b.setAttribute('style','position:fixed;z-index:2147483647;right:12px;top:12px;background:#064e3b;color:white;border:2px solid #a7f3d0;border-radius:14px;padding:11px 14px;font:700 12px Arial;box-shadow:0 4px 14px #0005;');b.onclick=function(){if(window.BendaharaAndroid)BendaharaAndroid.openHome();};document.body.appendChild(b);})();", null);
                }
                if ("laporan".equals(pendingFinanceAction) && url != null && url.startsWith("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/")) {
                    pendingFinanceAction = "";
                    view.postDelayed(() -> view.evaluateJavascript("(function(){if(typeof bukaLaporanKeuanganPDF==='function')bukaLaporanKeuanganPDF();})()", null), 700);
                }
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
                request.addRequestHeader("User-Agent", userAgent);
                String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Bendahara/" + fileName);
                } else {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                }
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) dm.enqueue(request);
                Toast.makeText(MainActivity.this, "Download dimulai: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Gagal mengunduh: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        tampilkanPilihPeran();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7001);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 7002);
        }
    }

    /**
     * Tangani tautan aplikasi seperti whatsapp:// di luar WebView.
     * Jika skema WhatsApp tidak dapat dibuka, coba tautan HTTPS resmi
     * agar pengguna tetap dapat meneruskan pesan kuitansi.
     */
    private boolean bukaTautanEksternal(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null || "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme)
                || "data".equalsIgnoreCase(scheme)) {
            return false;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            if ("whatsapp".equalsIgnoreCase(scheme)) {
                try {
                    String phone = uri.getQueryParameter("phone");
                    String text = uri.getQueryParameter("text");
                    Uri.Builder fallback = Uri.parse("https://wa.me/").buildUpon();
                    if (phone != null && !phone.trim().isEmpty()) {
                        fallback.appendPath(phone.replaceAll("[^0-9]", ""));
                    }
                    if (text != null) fallback.appendQueryParameter("text", text);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, fallback.build());
                    startActivity(browserIntent);
                    return true;
                } catch (Exception ignored) {
                    Toast.makeText(this,
                            "WhatsApp tidak dapat dibuka. Pastikan WhatsApp terpasang.",
                            Toast.LENGTH_LONG).show();
                    return true;
                }
            }
            Toast.makeText(this, "Tautan tidak dapat dibuka.", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void tampilkanPilihPeran() {
        selectedRole = "";
        String html = "<!doctype html><html lang='id'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#052e24;font-family:Arial,sans-serif;color:#fff;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:22px;box-sizing:border-box'><main style='width:100%;max-width:390px;text-align:center'><img src='https://lh3.googleusercontent.com/d/1nisPkmO93MQycG2iEWxMduwWb6ZOh0kA' style='width:100px;height:100px;object-fit:contain'><h2 style='margin:12px 0 4px'>Pondok Pesantren</h2><h3 style='margin:0;color:#a7f3d0'>Fathul Huda Al-Qorrobiyah</h3><p style='color:#d1fae5;font-size:13px;margin:12px 0 26px'>Silakan pilih akses sesuai kebutuhan</p><button onclick='BendaharaAndroid.pilihPeran(\"wali\")' style='width:100%;padding:17px;border:0;border-radius:16px;background:#10b981;color:#052e24;font-size:16px;font-weight:bold;margin-bottom:12px'>👨‍👩‍👦 Wali Santri</button><button onclick='BendaharaAndroid.pilihPeran(\"bendahara\")' style='width:100%;padding:17px;border:1px solid #6ee7b7;border-radius:16px;background:#064e3b;color:white;font-size:16px;font-weight:bold'>🔐 Bendahara</button><button onclick='BendaharaAndroid.pilihPeran("uangjajan")' style='width:100%;padding:17px;border:1px solid #6ee7b7;border-radius:16px;background:#064e3b;color:white;font-size:16px;font-weight:bold;margin-top:12px'>💰 Uang Jajan Santri</button><p style='font-size:11px;color:#a7f3d0;margin-top:28px'>SPP PPFHA • Keuangan Pesantren • Uang Jajan Santri</p></main></body></html>";
        webView.loadDataWithBaseURL("https://spp-pesantren-zeta.vercel.app/", html, "text/html", "UTF-8", null);
    }

    private void pilihPeran(String role) {
        if ("bendahara".equals(role)) {
            mintaSandiBendahara();
            return;
        }
        if ("uangjajan".equals(role)) {
            selectedRole = "uangjajan";
            webView.loadUrl("https://uang-jajan-santri-ppfha.vercel.app/");
            return;
        }
        selectedRole = "wali";
        webView.loadUrl("https://spp-pesantren-zeta.vercel.app/");
    }

    private void mintaSandiBendahara() {
        selectedRole = "";
        String html = "<!doctype html><html lang='id'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
            + "<meta name='theme-color' content='#032e25'><title>Akses Bendahara</title>"
            + "<style>*{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;font-family:Arial,sans-serif;color:#fff8e7;background:radial-gradient(ellipse at top,#0c6650,#04372c 55%,#021e19)}"
            + "body:before{content:'';position:fixed;inset:0;pointer-events:none;opacity:.3;background:repeating-radial-gradient(circle at 0 0,transparent 0 18px,#c6a75a22 19px 20px,transparent 21px 38px)}"
            + ".card{position:relative;width:100%;max-width:750px;text-align:center;padding:56px 34px 32px;border:1px solid #c6a75a88;border-radius:28px;background:linear-gradient(145deg,#043c30f5,#01271ff7);box-shadow:0 22px 65px #0008}"
            + ".logo{position:absolute;top:-45px;left:50%;transform:translateX(-50%);width:90px;height:90px;object-fit:contain;filter:drop-shadow(0 5px 8px #0008)}"
            + ".lock{font-size:40px;color:#dfc477;margin:12px 0 8px}h1{font:500 clamp(28px,4vw,42px) Georgia,serif;margin:0 0 10px}.sub{font-size:clamp(14px,2.2vw,20px);line-height:1.5;color:#c2d4cc;margin:0 auto 28px}"
            + ".field{display:flex;align-items:center;gap:12px;border:2px solid #cbb36e;border-radius:20px;padding:6px 15px;margin-bottom:20px;background:linear-gradient(110deg,#082f2c,#123d3c)}.field span{font-size:25px;color:#d9d9c8}.field input{flex:1;min-width:0;border:0;outline:0;background:transparent;color:white;font-size:17px;padding:15px 0}.field input::placeholder{color:#9eaaa5}.eye{border:0;background:transparent;color:#c6d4ce;font-size:23px;padding:8px}"
            + ".btn{width:100%;border-radius:19px;padding:17px 16px;font-size:17px;font-weight:bold;cursor:pointer}.enter{border:2px solid #d9bc64;background:linear-gradient(100deg,#08734f,#10a36b,#08734f);color:white}.cancel{margin-top:14px;border:2px solid #61776f;background:transparent;color:#c0cbc5}@media(max-width:520px){.card{padding:48px 20px 24px;border-radius:23px}.logo{width:82px;height:82px;top:-40px}.field input{font-size:15px}.btn{font-size:15px;padding:15px}}</style></head>"
            + "<body><main class='card'><img class='logo' src='https://lh3.googleusercontent.com/d/1nisPkmO93MQycG2iEWxMduwWb6ZOh0kA' alt='Logo Pondok'>"
            + "<div class='lock'>🔒</div><h1>Akses Bendahara</h1><p class='sub'>Masukkan sandi untuk melanjutkan<br>ke menu Bendahara.</p>"
            + "<form id='login'><div class='field'><span>♙</span><input id='sandi' type='password' inputmode='numeric' placeholder='Masukkan sandi Bendahara' required><button class='eye' id='lihat' type='button'>◉</button></div>"
            + "<button class='btn enter' type='submit'>⇥ &nbsp; MASUK</button><button class='btn cancel' id='batal' type='button'>× &nbsp; BATAL</button></form></main>"
            + "<script>const p=document.getElementById('sandi');document.getElementById('lihat').onclick=function(){p.type=p.type==='password'?'text':'password'};document.getElementById('login').onsubmit=function(e){e.preventDefault();BendaharaAndroid.verifikasiSandi(p.value)};document.getElementById('batal').onclick=function(){BendaharaAndroid.kembaliPilihAkses()};</script></body></html>";
        webView.loadDataWithBaseURL("https://spp-pesantren-zeta.vercel.app/", html, "text/html", "UTF-8", null);
    }

    private void tampilkanBerandaBendahara() {
        selectedRole = "bendahara";
        String html = "<!doctype html><html lang='id'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='theme-color' content='#075e54'><title>Beranda Bendahara PPFHA</title></head>"
            + "<body style='margin:0;background:#f1f8f6;font-family:Arial,sans-serif;color:#153b35;min-height:100vh'>"
            + "<header style='background:linear-gradient(135deg,#075e54,#0f766e);color:white;padding:28px 22px 30px;border-radius:0 0 26px 26px;box-shadow:0 8px 24px #064e3b22'>"
            + "<div style='display:flex;align-items:center;gap:14px;max-width:760px;margin:auto'><img src='https://lh3.googleusercontent.com/d/1nisPkmO93MQycG2iEWxMduwWb6ZOh0kA' style='width:66px;height:66px;object-fit:contain;background:#ffffff18;border-radius:18px;padding:5px;box-sizing:border-box'>"
            + "<div><div style='font-size:12px;letter-spacing:1.5px;color:#b8f3df;font-weight:bold'>PONDOK PESANTREN</div><div style='font-size:23px;font-weight:800;line-height:1.25;margin-top:4px'>Fathul Huda Al-Qorrobiyah</div><div style='font-size:14px;color:#d9fff1;margin-top:5px'>Beranda Bendahara</div></div></div></header>"
            + "<main style='max-width:760px;margin:0 auto;padding:25px 18px 32px;box-sizing:border-box'>"
            + "<div style='font-size:22px;font-weight:800;color:#163f38'>Assalamu’alaikum 👋</div><div style='font-size:14px;color:#648078;margin-top:6px;margin-bottom:22px'>Silakan pilih menu yang ingin dibuka.</div>"
            + "<div style='display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px'>"
            + "<button onclick='BendaharaAndroid.openSPP()' style='text-align:left;min-height:155px;padding:18px;border:1px solid #d6eee6;border-radius:22px;background:white;box-shadow:0 6px 18px #174c3b0d;color:#163f38'><span style='display:flex;width:50px;height:50px;align-items:center;justify-content:center;border-radius:16px;background:#e0f2fe;font-size:27px'>👨‍🎓</span><span style='display:block;font-size:17px;font-weight:800;margin-top:15px'>SPP Santri</span><span style='display:block;font-size:12px;color:#688078;margin-top:5px;line-height:1.45'>Data santri dan pembayaran SPP</span></button>"
            + "<button onclick='BendaharaAndroid.openFinance()' style='text-align:left;min-height:155px;padding:18px;border:1px solid #d6eee6;border-radius:22px;background:white;box-shadow:0 6px 18px #174c3b0d;color:#163f38'><span style='display:flex;width:50px;height:50px;align-items:center;justify-content:center;border-radius:16px;background:#dcfce7;font-size:27px'>💰</span><span style='display:block;font-size:17px;font-weight:800;margin-top:15px'>Keuangan</span><span style='display:block;font-size:12px;color:#688078;margin-top:5px;line-height:1.45'>Pemasukan, pengeluaran, dan saldo</span></button>"
            + "<button onclick='BendaharaAndroid.openBisyarah()' style='text-align:left;min-height:155px;padding:18px;border:1px solid #d6eee6;border-radius:22px;background:white;box-shadow:0 6px 18px #174c3b0d;color:#163f38'><span style='display:flex;width:50px;height:50px;align-items:center;justify-content:center;border-radius:16px;background:#f3e8ff;font-size:27px'>🕌</span><span style='display:block;font-size:17px;font-weight:800;margin-top:15px'>Bisyarah</span><span style='display:block;font-size:12px;color:#688078;margin-top:5px;line-height:1.45'>Menu bisyarah pengurus</span></button>"
            + "<button onclick='BendaharaAndroid.openReports()' style='text-align:left;min-height:155px;padding:18px;border:1px solid #d6eee6;border-radius:22px;background:white;box-shadow:0 6px 18px #174c3b0d;color:#163f38'><span style='display:flex;width:50px;height:50px;align-items:center;justify-content:center;border-radius:16px;background:#ffedd5;font-size:27px'>📑</span><span style='display:block;font-size:17px;font-weight:800;margin-top:15px'>Laporan</span><span style='display:block;font-size:12px;color:#688078;margin-top:5px;line-height:1.45'>Rekap keuangan dan simpan PDF</span></button>"
            + "</div><div style='margin-top:22px;padding:14px 16px;border-radius:16px;background:#e1f5ee;color:#397467;font-size:12px;line-height:1.6'>Data dan transaksi tetap menggunakan sistem SPP serta Keuangan Pesantren yang sudah ada.</div>"
            + "<button onclick='BendaharaAndroid.kembaliPilihAkses()' style='display:block;width:100%;margin-top:18px;padding:13px;border:1px solid #c9e3da;border-radius:14px;background:transparent;color:#47736a;font-size:14px;font-weight:700'>← Kembali ke Pilihan Akses</button>"
            + "</main></body></html>";
        webView.loadDataWithBaseURL("https://spp-pesantren-zeta.vercel.app/", html, "text/html", "UTF-8", null);
    }

    private void bukaHalamanSPP() {
        selectedRole = "bendahara";
        webView.loadUrl("https://spp-pesantren-zeta.vercel.app/");
    }

    private void bukaHalamanKeuangan() {
        selectedRole = "bendahara";
        webView.loadUrl("https://raw.githack.com/mohammedsaidilyas-bot/Keuangan-Pesantren/gabung-uang-jajan-santri/index.html?v=7af106eff05b9cfce5b799720a073d56bf40f773");
    }

    private void bukaLaporanKeuangan() {
        selectedRole = "bendahara";
        pendingFinanceAction = "laporan";
        bukaHalamanKeuangan();
    }

    private void bukaBisyarah() {
        selectedRole = "bendahara";
        String html = "<!doctype html><html lang='id'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Bisyarah PPFHA</title>"
            + "<style>*{box-sizing:border-box}body{margin:0;background:#f1f8f6;font-family:Arial,sans-serif;color:#153b35}.top{padding:20px;background:linear-gradient(135deg,#075e54,#0f766e);color:white;border-radius:0 0 22px 22px}.wrap{max-width:700px;margin:auto;padding:16px}.card{background:white;border:1px solid #dcece6;border-radius:16px;padding:16px;margin-bottom:14px}.field{width:100%;padding:12px;border:1px solid #d5e5df;border-radius:10px;margin:6px 0 12px;font-size:14px}.btn{padding:12px;border:0;border-radius:10px;font-weight:bold}.muted{font-size:12px;color:#71867f}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}</style></head>"
            + "<body><header class='top'><button class='btn' style='background:#ffffff20;color:white' onclick='BendaharaAndroid.openHome()'>← Beranda</button><div style='font-size:12px;margin-top:16px;color:#c9f7e7'>PONDOK PESANTREN FATHUL HUDA AL-QORROBIYAH</div><h2 style='margin:6px 0'>🕌 Pencatatan Bisyarah</h2><div style='font-size:13px'>Catat dan pantau pembayaran bisyarah ustaz/guru</div></header><main class='wrap'>"
            + "<div id='koneksi' style='padding:12px;border-radius:10px;background:#e0f2fe;color:#075985;font-size:12px;line-height:1.5;margin-bottom:14px'>Data akan dikirim ke database utama. Tunggu konfirmasi sebelum menutup halaman.</div>"
            + "<div class='grid'><div class='card'><div class='muted'>Total tercatat</div><h2 id='total'>Rp 0</h2></div><div class='card'><div class='muted'>Jumlah catatan</div><h2 id='count'>0</h2></div></div>"
            + "<section class='card'><h3>Tambah Bisyarah</h3><form id='form'><label class='muted'>Nama Ustadz atau Pengurus</label><input class='field' id='nama' required placeholder='Contoh: Ustadz Ahmad atau Pengurus Asrama' maxlength='100'><div class='grid'><div><label class='muted'>Tanggal</label><input class='field' id='tanggal' type='date' required></div><div><label class='muted'>Jumlah (Rp)</label><input class='field' id='jumlah' type='number' min='1' required placeholder='500000'></div></div><label class='muted'>Status</label><select class='field' id='status'><option>Belum Dibayar</option><option>Sudah Dibayar</option></select><label class='muted'>Catatan (opsional)</label><input class='field' id='catatan' placeholder='Contoh: Bisyarah bulan Oktober'><button id='simpan' class='btn' style='width:100%;background:#087f5b;color:white' type='submit'>+ Simpan Catatan</button></form></section>"
            + "<section class='card'><h3>Riwayat Bisyarah (catatan dari perangkat ini)</h3><div id='list' class='muted'>Belum ada catatan di perangkat ini.</div></section></main>"
            + "<script>const k='ppfha_bisyarah_prototype_v1',q=id=>document.getElementById(id);q('tanggal').value=new Date().toISOString().slice(0,10);function data(){try{return JSON.parse(localStorage.getItem(k)||'[]')}catch(e){return []}}function rp(n){return new Intl.NumberFormat('id-ID',{style:'currency',currency:'IDR',maximumFractionDigits:0}).format(n)}function safe(s){return String(s||'').replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))}function draw(){let a=data();q('total').textContent=rp(a.reduce((sum,x)=>sum+Number(x.jumlah),0));q('count').textContent=a.length;let list=q('list');list.innerHTML='';if(!a.length){list.textContent='Belum ada catatan di perangkat ini.';return}a.slice().reverse().forEach(x=>{let d=document.createElement('div');d.style.cssText='border-top:1px solid #e6efeb;padding:12px 0';d.innerHTML='<b>'+safe(x.nama)+'</b><div>'+rp(x.jumlah)+' · '+safe(x.tanggal)+'</div><div>'+safe(x.status)+'</div><div>'+safe(x.catatan)+'</div><div class=muted>ID: '+safe(x.id)+'</div>';list.appendChild(d)})}let pending=null;window.__bisyarahResult=function(raw){let b=q('simpan');b.disabled=false;b.textContent='+ Simpan Catatan';let r;try{r=JSON.parse(raw)}catch(e){q('koneksi').textContent='Respons server tidak terbaca. Periksa Apps Script.';q('koneksi').style.background='#fee2e2';q('koneksi').style.color='#991b1b';return}if(!r||r.success!==true){q('koneksi').textContent='Gagal menyimpan ke database: '+((r&&r.message)||raw||'Tidak ada respons');q('koneksi').style.background='#fee2e2';q('koneksi').style.color='#991b1b';return}let a=data();a.push({id:r.id||('SERVER-'+Date.now()),nama:pending.nama,tanggal:pending.tanggal,jumlah:pending.jumlah,status:pending.status,catatan:pending.catatan});localStorage.setItem(k,JSON.stringify(a));q('form').reset();q('tanggal').value=new Date().toISOString().slice(0,10);q('koneksi').textContent='Berhasil disimpan ke database utama. ID: '+(r.id||'-');q('koneksi').style.background='#dcfce7';q('koneksi').style.color='#166534';draw()};q('form').onsubmit=e=>{e.preventDefault();pending={nama:q('nama').value.trim(),tanggal:q('tanggal').value,jumlah:Number(q('jumlah').value),status:q('status').value,catatan:q('catatan').value.trim()};if(!pending.nama||!pending.tanggal||pending.jumlah<=0){alert('Lengkapi nama, tanggal, dan jumlah.');return}let b=q('simpan');b.disabled=true;b.textContent='Menyimpan...';q('koneksi').textContent='Mengirim data ke database...';try{BendaharaAndroid.simpanBisyarah(JSON.stringify({action:'catatBisyarah',nama:pending.nama,tanggal:pending.tanggal,jumlah:pending.jumlah,status:pending.status,catatan:pending.catatan}),'bisyarah')}catch(err){b.disabled=false;b.textContent='+ Simpan Catatan';q('koneksi').textContent='Koneksi gagal: '+err.message;q('koneksi').style.background='#fee2e2';q('koneksi').style.color='#991b1b'}};draw();</script></body></html>";
        webView.loadDataWithBaseURL("https://spp-pesantren-zeta.vercel.app/", html, "text/html", "UTF-8", null);
    }

    private class AndroidBridge {
        @JavascriptInterface public void verifikasiSandi(final String password) {
            runOnUiThread(() -> {
                if ("999999".equals(password)) {
                    tampilkanBerandaBendahara();
                } else {
                    webView.evaluateJavascript("(function(){var p=document.getElementById('sandi');if(p){p.value='';p.placeholder='Sandi salah, coba lagi';p.focus();}})()", null);
                    Toast.makeText(MainActivity.this, "Sandi salah. Akses ditolak.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public void pilihPeran(final String role) {
            runOnUiThread(() -> MainActivity.this.pilihPeran(role));
        }

        @JavascriptInterface public void openHome() {
            runOnUiThread(() -> tampilkanBerandaBendahara());
        }

        @JavascriptInterface public void kembaliPilihAkses() {
            runOnUiThread(() -> tampilkanPilihPeran());
        }

        @JavascriptInterface public void openSPP() {
            runOnUiThread(() -> bukaHalamanSPP());
        }

        @JavascriptInterface public void openFinance() {
            runOnUiThread(() -> {
                if (!"bendahara".equals(selectedRole)) {
                    Toast.makeText(MainActivity.this,
                            "Menu keuangan hanya tersedia dari akses Bendahara.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                bukaHalamanKeuangan();
            });
        }

        @JavascriptInterface public void openReports() {
            runOnUiThread(() -> bukaLaporanKeuangan());
        }

        @JavascriptInterface public void openBisyarah() {
            runOnUiThread(() -> bukaBisyarah());
        }

        @JavascriptInterface public void simpanBisyarah(final String payload, final String callbackId) {
            new Thread(() -> {
                String result;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://script.google.com/macros/s/AKfycbzXSerzZbcNn-1nVnH1Hg8TwvdhNGX2KML88IdF9RKLCpoLpzQ39G9K8TPJiqn5GZ10EQ/exec");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(body.length);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body);
                        out.flush();
                    }
                    int code = connection.getResponseCode();
                    String location = connection.getHeaderField("Location");
                    if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                            && location != null && !location.isEmpty()) {
                        connection.disconnect();
                        connection = (HttpURLConnection) new URL(location).openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(20000);
                        connection.setReadTimeout(30000);
                        code = connection.getResponseCode();
                    }
                    InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    StringBuilder response = new StringBuilder();
                    if (in != null) {
                        try (InputStream stream = in) {
                            byte[] buffer = new byte[4096];
                            int n;
                            while ((n = stream.read(buffer)) != -1) response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                        }
                    }
                    result = response.toString();
                    if (result.isEmpty()) {
                        java.util.HashMap<String, Object> error = new java.util.HashMap<>();
                        error.put("success", false);
                        error.put("message", "Server tidak mengembalikan respons (HTTP " + code + ")");
                        result = new org.json.JSONObject(error).toString();
                    }
                } catch (Exception e) {
                    java.util.HashMap<String, Object> error = new java.util.HashMap<>();
                    error.put("success", false);
                    error.put("message", "Koneksi gagal: " + e.getMessage());
                    result = new org.json.JSONObject(error).toString();
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final String answer = result;
                runOnUiThread(() -> {
                    if (webView != null) webView.evaluateJavascript(
                            "if(window.__bisyarahResult)window.__bisyarahResult(" + org.json.JSONObject.quote(answer) + ");", null);
                });
            }).start();
        }

        @JavascriptInterface public void saveLaporanPDF(final String html, final String fileName) {
            runOnUiThread(() -> bukaPratinjauCetakAndroid(html, fileName));
        }

        @JavascriptInterface public void shareLaporanPDF(final String html, final String fileName, final String message) {
            runOnUiThread(() -> bukaPDFUntukWhatsApp(html, fileName, message));
        }
    }

    /*
     * Simpan PDF langsung tanpa membuka Android Print Preview.
     * WebView tetap merender HTML laporan, lalu PrintDocumentAdapter
     * menulis hasil PDF ke file cache. Setelah selesai, file dipindahkan
     * ke Download/Bendahara dan pengguna mendapat tombol Tutup.
     */
    private void bukaPratinjauCetakAndroid(String html, String fileName) {
        siapkanWebViewPDF(html, () -> tulisPDFDenganPrintAdapter(fileName, false, ""));
    }

    private void bukaPDFUntukWhatsApp(String html, String fileName, String message) {
        // Pilih file PDF laporan yang sudah disimpan, kemudian bagikan sebagai lampiran.
        pendingWhatsAppMessage = message == null ? "" : message;
        try {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("application/pdf");
            startActivityForResult(pick, PICK_PDF_FOR_WHATSAPP);
            Toast.makeText(this,
                    "Pilih PDF laporan dari folder Download/Bendahara untuk dibagikan.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka pemilih file PDF.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_FOR_WEBVIEW) {
            if (fileChooserCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    Uri selected = data.getData();
                    if (selected != null) results = new Uri[]{selected};
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                            && data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode != PICK_PDF_FOR_WHATSAPP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            if (pendingWhatsAppMessage != null && !pendingWhatsAppMessage.isEmpty()) {
                share.putExtra(Intent.EXTRA_TEXT, pendingWhatsAppMessage);
            }
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newUri(getContentResolver(), "Laporan PDF", uri));
            startActivity(Intent.createChooser(share, "Bagikan laporan PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "PDF tidak dapat dibagikan.", Toast.LENGTH_LONG).show();
        }
    }

    private void siapkanWebViewPDF(String html, final Runnable setelahSiap) {
        try {
            bersihkanPrintWebView();

            printWebView = new WebView(this);
            WebSettings s = printWebView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadWithOverviewMode(false);
            s.setUseWideViewPort(false);
            s.setTextZoom(100);
            printWebView.setBackgroundColor(Color.WHITE);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = -3000;
            lp.topMargin = -3000;
            addContentView(printWebView, lp);

            printWebView.setWebViewClient(new WebViewClient() {
                private boolean selesai = false;

                @Override public void onPageFinished(WebView view, String url) {
                    if (selesai) return;
                    selesai = true;
                    printWebView.postDelayed(() -> {
                        try {
                            printWebView.evaluateJavascript(
                                    "(function(){window.scrollTo(0,0);"
                                    + "document.documentElement.scrollTop=0;"
                                    + "document.body.scrollTop=0;"
                                    + "})();", null);
                        } catch (Exception ignored) {}
                        printWebView.postDelayed(setelahSiap, 700);
                    }, 900);
                }
            });

            printWebView.loadDataWithBaseURL(
                    "https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/",
                    html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyiapkan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            bersihkanPrintWebView();
        }
    }

    /*
     * Gunakan mesin PDF bawaan WebView/Android PrintDocumentAdapter.
     * Ini menggantikan PdfDocument manual yang sebelumnya menyebabkan
     * halaman terpotong, ukuran berantakan, dan PDF sulit disimpan.
     */
    private void tulisPDFDenganPrintAdapter(String fileName, boolean shareToWhatsApp, String message) {
        if (printWebView == null) {
            Toast.makeText(this, "WebView laporan belum siap", Toast.LENGTH_LONG).show();
            bersihkanPrintWebView();
            return;
        }

        try {
            // PrintManager yang membuat dan mengelola callback resmi Android.
            // WebView menangani paginasi HTML/CSS sendiri agar tabel tidak
            // dipotong dengan cara menggambar viewport secara manual.
            android.print.PrintManager printManager =
                    (android.print.PrintManager) getSystemService(PRINT_SERVICE);
            if (printManager == null) {
                throw new IOException("Layanan cetak Android tidak tersedia");
            }

            String safeName = (fileName == null || fileName.trim().isEmpty())
                    ? "Laporan-Keuangan.pdf" : fileName.replaceAll("\\.pdf$", "");
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution(
                            "bendahara", "Bendahara PDF", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            printAdapter = printWebView.createPrintDocumentAdapter(safeName);
            returningFromPrint = true;
            printManager.print(safeName, printAdapter, attributes);
            Toast.makeText(this,
                    "Pilih 'Save as PDF' pada menu cetak Android untuk menyimpan laporan.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka cetak PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            bersihkanPrintWebView();
        }
    }

    private void setelahPDFSelesai(File pdfFile, String fileName,
                                   boolean shareToWhatsApp, String message) {
        try {
            if (!pdfFile.exists() || pdfFile.length() == 0) {
                throw new IOException("File PDF kosong");
            }

            // Simpan juga otomatis ke Download/Bendahara.
            if (!saveToDownload(pdfFile, fileName)) {
                throw new IOException("PDF tidak dapat disimpan ke Download/Bendahara");
            }

            if (shareToWhatsApp) {
                Uri uri = Uri.parse(
                        "content://com.ppfha.gabungan.fileprovider/laporan/"
                                + Uri.encode(fileName));

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                if (message != null && !message.isEmpty()) {
                    intent.putExtra(Intent.EXTRA_TEXT, message);
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Laporan PDF", uri));
                intent.setPackage("com.whatsapp");

                try {
                    grantUriPermission("com.whatsapp", uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    intent.setPackage(null);
                    try {
                        startActivity(Intent.createChooser(intent, "Kirim laporan PDF"));
                    } catch (Exception ex) {
                        Toast.makeText(this,
                                "PDF sudah tersimpan, tetapi WhatsApp tidak tersedia.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                tampilkanDialogPDFTersimpan(fileName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "PDF gagal disimpan: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            bersihkanPrintWebView();
        }
    }

    private boolean saveToDownload(File source, String fileName) {
        if (source == null || !source.exists() || source.length() == 0) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Bendahara");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        values);
                if (uri == null) throw new IOException("Folder Download/Bendahara tidak tersedia");

                try (InputStream in = new java.io.FileInputStream(source);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Tidak dapat membuka file tujuan");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                    out.flush();
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                return true;
            }

            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File folder = new File(downloads, "Bendahara");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Folder Download/Bendahara tidak dapat dibuat");
            }

            File target = new File(folder, fileName);
            try (InputStream in = new java.io.FileInputStream(source);
                 OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                out.flush();
            }
            return target.exists() && target.length() > 0;
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void bersihkanPrintWebView() {
        try {
            if (printWebView != null) {
                try { ((ViewGroup) printWebView.getParent()).removeView(printWebView); } catch (Exception ignored) {}
                try { printWebView.destroy(); } catch (Exception ignored) {}
                printWebView = null;
            }
        } catch (Exception ignored) {}
    }

    private void tampilkanDialogPDFTersimpan(String fileName) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("PDF berhasil disimpan")
                .setMessage("Laporan tersimpan di:\nDownload/Bendahara/" + fileName)
                .setPositiveButton("Tutup", (dialog, which) -> {
                    try {
                        webView.evaluateJavascript(
                                "(function(){var m=document.getElementById('previewLaporanKeuangan');"
                                + "if(m)m.remove();"
                                + "if(typeof loadDashboard==='function')loadDashboard();"
                                + "})()",
                                null);
                    } catch (Exception ignored) {}
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (returningFromPrint) {
            returningFromPrint = false;
            bersihkanPrintWebView();
            if (webView != null) {
                webView.postDelayed(() -> webView.evaluateJavascript(
                        "(function(){var m=document.getElementById('previewLaporanKeuangan');"
                        + "if(m)m.remove();if(typeof loadDashboard==='function')loadDashboard();})()",
                        null), 250);
            }
        }
    }

    @Override protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        bersihkanPrintWebView();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
