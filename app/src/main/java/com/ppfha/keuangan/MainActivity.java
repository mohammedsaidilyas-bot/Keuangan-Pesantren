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
        printView.getSettings().setJavaScriptEnabled(true);
        printView.getSettings().setDomStorageEnabled(true);
        printView.getSettings().setLoadWithOverviewMode(false);
        printView.getSettings().setUseWideViewPort(false);
        printView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // WebView harus terpasang ke window agar benar-benar melakukan layout/paint sebelum digambar ke PDF.
        FrameLayout.LayoutParams hiddenParams = new FrameLayout.LayoutParams(1, 1);
        hiddenParams.leftMargin = -10;
        hiddenParams.topMargin = -10;
        addContentView(printView, hiddenParams);
        final File dir = new File(getCacheDir(), "laporan");
        if (!dir.exists()) dir.mkdirs();

        final String safeName = fileName == null || fileName.trim().isEmpty()
                ? "Laporan-Keuangan.pdf" : fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        final File pdfFile = new File(dir, safeName);

        printView.setBackgroundColor(Color.WHITE);
        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                // Tunggu sebentar agar CSS/layout/painting selesai sebelum WebView digambar ke PDF.
                printView.postDelayed(() -> {
                try {
                    final int pageWidth = 595;
                    final int pageHeight = 842;
                    final int margin = 30;
                    final int contentWidth = pageWidth - (margin * 2);
                    final int contentHeightPerPage = pageHeight - (margin * 2);

                    printView.measure(
                            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    int measuredHeight = printView.getMeasuredHeight();
                    int webContentHeight = printView.getContentHeight();
                    final int contentHeight = Math.max(Math.max(measuredHeight, webContentHeight), 1);
                    printView.layout(0, 0, contentWidth, contentHeight);

                    final int pageCount = Math.max(1,
                            (contentHeight + contentHeightPerPage - 1) / contentHeightPerPage);

                    PdfDocument document = new PdfDocument();
                    try {
                        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                            PdfDocument.PageInfo pageInfo =
                                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create();
                            PdfDocument.Page page = document.startPage(pageInfo);
                            Canvas canvas = page.getCanvas();
                            canvas.drawColor(Color.WHITE);
                            canvas.save();
                            canvas.translate(margin, margin - (pageIndex * contentHeightPerPage));
                            canvas.clipRect(0, pageIndex * contentHeightPerPage,
                                    contentWidth, (pageIndex + 1) * contentHeightPerPage);
                            printView.draw(canvas);
                            canvas.restore();
                            document.finishPage(page);
                        }

                        FileOutputStream out = new FileOutputStream(pdfFile);
                        try {
                            document.writeTo(out);
                        } finally {
                            try { out.close(); } catch (IOException ignored) {}
                        }
                    } finally {
                        document.close();
                    }

                    printView.destroy();
                    bagikanPDF(pdfFile, message);
                } catch (Exception e) {
                    printView.destroy();
                    Toast.makeText(MainActivity.this,
                            "Gagal membuat PDF: " + (e.getMessage() == null ? "kesalahan sistem" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                }
                }, 800);
            }
        });
        printView.loadDataWithBaseURL
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
