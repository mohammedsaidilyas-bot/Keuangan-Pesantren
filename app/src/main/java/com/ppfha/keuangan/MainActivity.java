package com.ppfha.keuangan;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
import android.content.ClipData;
import android.net.Uri;
import android.provider.Settings;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    // Build acuan: PDF web sudah diuji kembali ke Dashboard setelah cetak.
    private WebView webView;
    private WebView printWebView;
    private PrintDocumentAdapter printAdapter;
    private static final int PICK_PDF_FOR_WHATSAPP = 4101;
    private String pendingWhatsAppMessage = "";

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
        webView.setWebViewClient(new WebViewClient());
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
        webView.loadUrl("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7001);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 7002);
        }
    }

    private class AndroidBridge {
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
        siapkanWebViewPDF(html, () -> tulisPDFDenganPrintAdapter(fileName, true, message));
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
        try {
            if (printWebView == null) throw new IOException("WebView laporan belum siap");

            final String safeName = (fileName == null || fileName.trim().isEmpty())
                    ? "Laporan-Keuangan.pdf" : fileName;

            File dir = new File(getCacheDir(), "laporan");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Folder laporan tidak dapat dibuat");
            }

            File pdfFile = new File(dir, safeName);
            if (pdfFile.exists()) pdfFile.delete();

            final int pageWidth = 595;
            final int pageHeight = 842;

            printWebView.measure(
                    View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            printWebView.layout(0, 0, pageWidth, printWebView.getMeasuredHeight());

            int contentHeight = printWebView.getMeasuredHeight();
            if (contentHeight <= 0) throw new IOException("Isi laporan kosong");

            int pageCount = (contentHeight + pageHeight - 1) / pageHeight;
            PdfDocument document = new PdfDocument();
            try {
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                            pageWidth, pageHeight, pageIndex + 1).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    android.graphics.Canvas canvas = page.getCanvas();
                    canvas.save();
                    canvas.clipRect(0, 0, pageWidth, pageHeight);
                    canvas.translate(0, -pageIndex * pageHeight);
                    printWebView.draw(canvas);
                    canvas.restore();
                    document.finishPage(page);
                }

                try (OutputStream out = new java.io.FileOutputStream(pdfFile)) {
                    document.writeTo(out);
                    out.flush();
                }
            } finally {
                document.close();
            }

            setelahPDFSelesai(pdfFile, safeName, shareToWhatsApp, message);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuat PDF: " + e.getMessage(),
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
                        "content://com.ppfha.keuangan.fileprovider/laporan/"
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

    @Override protected void onDestroy() {
        bersihkanPrintWebView();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
