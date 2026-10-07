package com.ppfha.keuangan;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.FileOutputStream;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        webView.loadUrl("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/");
    }

    private class AndroidBridge {
        @JavascriptInterface public void shareLaporanPDF(final String html, final String fileName, final String message) {
            runOnUiThread(() -> buatPDFDanBagikan(html, fileName, message));
        }
    }

    private void buatPDFDanBagikan(String html, String fileName, String message) {
        final WebView printView = new WebView(this);
        final File dir = new File(getCacheDir(), "laporan");
        if (!dir.exists()) dir.mkdirs();

        final String safeName = fileName == null || fileName.trim().isEmpty()
                ? "Laporan-Keuangan.pdf" : fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        final File pdfFile = new File(dir, safeName);

        WebSettings ps = printView.getSettings();
        ps.setJavaScriptEnabled(true);
        ps.setDomStorageEnabled(true);
        ps.setLoadWithOverviewMode(false);
        ps.setUseWideViewPort(false);
        printView.setBackgroundColor(Color.WHITE);

        // WebView dipasang sementara supaya layout dan CSS benar-benar dirender.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        lp.leftMargin = -10;
        lp.topMargin = -10;
        addContentView(printView, lp);

        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                printView.postDelayed(() -> {
                    try {
                        android.print.PrintAttributes attrs =
                                new android.print.PrintAttributes.Builder()
                                        .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                        .setResolution(new android.print.PrintAttributes.Resolution(
                                                "laporan", "Laporan", 300, 300))
                                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                                        .build();

                        final android.print.PrintDocumentAdapter adapter =
                                printView.createPrintDocumentAdapter("Laporan-Keuangan");

                        adapter.onLayout(null, attrs,
                                null,
                                new android.print.PrintDocumentAdapter.LayoutResultCallback() {
                                    @Override public void onLayoutFinished(
                                            android.print.PrintDocumentInfo info, boolean changed) {
                                        try {
                                            android.os.ParcelFileDescriptor pfd =
                                                    android.os.ParcelFileDescriptor.open(
                                                            pdfFile,
                                                            android.os.ParcelFileDescriptor.MODE_CREATE |
                                                            android.os.ParcelFileDescriptor.MODE_TRUNCATE |
                                                            android.os.ParcelFileDescriptor.MODE_READ_WRITE);

                                            adapter.onWrite(
                                                    new android.print.PageRange[]{
                                                            android.print.PageRange.ALL_PAGES
                                                    },
                                                    pfd,
                                                    null,
                                                    new android.print.PrintDocumentAdapter.WriteResultCallback() {
                                                        @Override public void onWriteFinished(
                                                                android.print.PageRange[] pages) {
                                                            try { pfd.close(); } catch (Exception ignored) {}
                                                            printView.destroy();
                                                            bagikanPDF(pdfFile, message);
                                                        }

                                                        @Override public void onWriteFailed(CharSequence error) {
                                                            try { pfd.close(); } catch (Exception ignored) {}
                                                            printView.destroy();
                                                            Toast.makeText(MainActivity.this,
                                                                    "Gagal menulis PDF: " + String.valueOf(error),
                                                                    Toast.LENGTH_LONG).show();
                                                        }
                                                    });
                                        } catch (Exception e) {
                                            printView.destroy();
                                            Toast.makeText(MainActivity.this,
                                                    "Gagal membuat PDF: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }

                                    @Override public void onLayoutFailed(CharSequence error) {
                                        printView.destroy();
                                        Toast.makeText(MainActivity.this,
                                                "Gagal menata laporan PDF: " + String.valueOf(error),
                                                Toast.LENGTH_LONG).show();
                                    }
                                },
                                null);
                    } catch (Exception e) {
                        printView.destroy();
                        Toast.makeText(MainActivity.this,
                                "Gagal menyiapkan PDF: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }, 1000);
            }
        });

        printView.loadDataWithBaseURL(
                "https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/",
                html, "text/html", "UTF-8", null);
    }

    private void bagikanPDF(File pdfFile, String message) {
        if (!pdfFile.exists() || pdfFile.length() == 0) {
            Toast.makeText(this, "PDF belum berhasil dibuat.", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = Uri.parse("content://com.ppfha.keuangan.fileprovider/laporan/" + Uri.encode(pdfFile.getName()));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);

        // Nomor WhatsApp pimpinan: 6282219644442
        // jid membuat WhatsApp mencoba membuka percakapan tujuan langsung.
        intent.putExtra("jid", "6282219644442@s.whatsapp.net");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), "Laporan PDF", uri));
        intent.setPackage("com.whatsapp");
        try {
            grantUriPermission("com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Toast.makeText(this, "PDF siap dikirim ke WhatsApp pimpinan.", Toast.LENGTH_SHORT).show();
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try { startActivity(Intent.createChooser(intent, "Kirim laporan PDF")); }
            catch (Exception ex) { Toast.makeText(this, "WhatsApp tidak ditemukan.", Toast.LENGTH_LONG).show(); }
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
