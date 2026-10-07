package com.ppfha.keuangan;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
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
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.content.ClipData;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;

// Laporan: pratinjau HTML ditampilkan sebelum proses simpan PDF.
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
        @JavascriptInterface public void saveLaporanPDF(final String html, final String fileName) {
            runOnUiThread(() -> buatPDFDanSimpan(html, fileName));
        }

        @JavascriptInterface public void shareLaporanPDF(final String html, final String fileName, final String message) {
            runOnUiThread(() -> buatPDFDanBagikan(html, fileName, message));
        }
    }

    /*
     * Generate the report with Android's WebView print engine instead of drawing
     * the visible WebView onto a PdfDocument. This preserves A4 pagination,
     * tables, CSS and page breaks, and prevents blank/overlapping pages.
     */
    private void renderHtmlToPdf(String html, String fileName, boolean share, String message) {
        final WebView v = new WebView(this);
        final File dir = new File(getCacheDir(), "laporan");
        if (!dir.exists()) dir.mkdirs();

        final String n = (fileName == null || fileName.trim().isEmpty())
                ? "Laporan-Keuangan.pdf"
                : fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        final File pdf = new File(dir, n);

        WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        v.setBackgroundColor(Color.WHITE);

        // Keep the printing WebView attached so Chromium fully renders it.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(794, 1123);
        lp.leftMargin = -900;
        lp.topMargin = 0;
        addContentView(v, lp);

        v.setWebViewClient(new WebViewClient() {
            private boolean started = false;

            @Override public void onPageFinished(WebView view, String url) {
                if (started) return;
                started = true;

                v.postDelayed(() -> {
                    try {
                        PrintAttributes attributes = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                                .setResolution(new PrintAttributes.Resolution(
                                        "bendahara_pdf", "Bendahara PDF", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build();

                        final PrintDocumentAdapter adapter =
                                v.createPrintDocumentAdapter(n);

                        final CancellationSignal cancellationSignal = new CancellationSignal();
                        final ParcelFileDescriptor destination =
                                ParcelFileDescriptor.open(
                                        pdf,
                                        ParcelFileDescriptor.MODE_CREATE
                                                | ParcelFileDescriptor.MODE_TRUNCATE
                                                | ParcelFileDescriptor.MODE_READ_WRITE);

                        adapter.onLayout(
                                null,
                                attributes,
                                cancellationSignal,
                                new PrintDocumentAdapter.LayoutResultCallback() {
                                    @Override public void onLayoutFinished(
                                            PrintDocumentInfo info, boolean changed) {
                                        if (cancellationSignal.isCanceled()) {
                                            cleanupPrintWebView(v, adapter, destination);
                                            return;
                                        }

                                        adapter.onWrite(
                                                new PageRange[]{PageRange.ALL_PAGES},
                                                destination,
                                                cancellationSignal,
                                                new PrintDocumentAdapter.WriteResultCallback() {
                                                    @Override public void onWriteFinished(PageRange[] pages) {
                                                        finishPdfJob(v, adapter, destination, pdf, n, share, message);
                                                    }

                                                    @Override public void onWriteFailed(CharSequence error) {
                                                        cleanupPrintWebView(v, adapter, destination);
                                                        Toast.makeText(
                                                                MainActivity.this,
                                                                "Gagal membuat PDF: " + String.valueOf(error),
                                                                Toast.LENGTH_LONG).show();
                                                    }

                                                    @Override public void onWriteCancelled() {
                                                        cleanupPrintWebView(v, adapter, destination);
                                                    }
                                                });
                                    }

                                    @Override public void onLayoutFailed(CharSequence error) {
                                        cleanupPrintWebView(v, adapter, destination);
                                        Toast.makeText(
                                                MainActivity.this,
                                                "Gagal menata PDF: " + String.valueOf(error),
                                                Toast.LENGTH_LONG).show();
                                    }

                                    @Override public void onLayoutCancelled() {
                                        cleanupPrintWebView(v, adapter, destination);
                                    }
                                },
                                null);
                    } catch (Exception e) {
                        v.destroy();
                        ViewGroup parent = (ViewGroup) v.getParent();
                        if (parent != null) parent.removeView(v);
                        Toast.makeText(
                                MainActivity.this,
                                "Gagal membuat PDF: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }, 900);
            }
        });

        v.loadDataWithBaseURL(
                "https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/",
                html,
                "text/html",
                "UTF-8",
                null);
    }

    private void finishPdfJob(
            WebView v,
            PrintDocumentAdapter adapter,
            ParcelFileDescriptor destination,
            File pdf,
            String fileName,
            boolean share,
            String message) {
        try {
            destination.close();
        } catch (Exception ignored) {}

        try {
            adapter.onFinish();
        } catch (Exception ignored) {}

        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
        } catch (Exception ignored) {}

        try {
            v.destroy();
        } catch (Exception ignored) {}

        if (!pdf.exists() || pdf.length() == 0) {
            Toast.makeText(this, "PDF belum berhasil dibuat.", Toast.LENGTH_LONG).show();
            return;
        }

        if (share) {
            bagikanPDF(pdf, message);
        } else {
            saveToDownload(pdf, fileName);
        }
    }

    private void cleanupPrintWebView(
            WebView v,
            PrintDocumentAdapter adapter,
            ParcelFileDescriptor destination) {
        try { destination.close(); } catch (Exception ignored) {}
        try { adapter.onFinish(); } catch (Exception ignored) {}
        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
        } catch (Exception ignored) {}
        try { v.destroy(); } catch (Exception ignored) {}
    }

    private void buatPDFDanBagikan(String html, String fileName, String message) {
        renderHtmlToPdf(html, fileName, true, message);
    }

    private void buatPDFDanSimpan(String html, String fileName) {
        renderHtmlToPdf(html, fileName, false, null);
    }

    private void saveToDownload(File f, String n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, n);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                v.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Bendahara");
                v.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri u = getContentResolver().insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                if (u == null) throw new IOException("Download tidak tersedia");

                try (InputStream in = new java.io.FileInputStream(f);
                     OutputStream o = getContentResolver().openOutputStream(u)) {
                    byte[] b = new byte[8192];
                    int k;
                    while ((k = in.read(b)) != -1) o.write(b, 0, k);
                }

                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(u, v, null, null);
                Toast.makeText(this,
                        "PDF tersimpan di Download/Bendahara/" + n,
                        Toast.LENGTH_LONG).show();
            } else {
                pendingPdf = f;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_TITLE, n);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, 7001);
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Gagal menyimpan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private File pendingPdf;

    @Override protected void onActivityResult(int r, int s, Intent d) {
        super.onActivityResult(r, s, d);
        if (r == 7001 && s == RESULT_OK && d != null && d.getData() != null && pendingPdf != null) {
            try (InputStream i = new java.io.FileInputStream(pendingPdf);
                 OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                byte[] b = new byte[8192];
                int k;
                while ((k = i.read(b)) != -1) o.write(b, 0, k);
                Toast.makeText(this, "PDF berhasil disimpan.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this,
                        "Gagal menyimpan PDF: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            pendingPdf = null;
        }
    }

    private void bagikanPDF(File pdfFile, String message) {
        if (!pdfFile.exists() || pdfFile.length() == 0) {
            Toast.makeText(this, "PDF belum berhasil dibuat.", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = Uri.parse(
                "content://com.ppfha.keuangan.fileprovider/laporan/" +
                        Uri.encode(pdfFile.getName()));

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);

        // Nomor WhatsApp pimpinan: 6282219644442
        intent.putExtra("jid", "6282219644442@s.whatsapp.net");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), "Laporan PDF", uri));
        intent.setPackage("com.whatsapp");

        try {
            grantUriPermission(
                    "com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Toast.makeText(
                    this,
                    "PDF siap dikirim ke WhatsApp pimpinan.",
                    Toast.LENGTH_SHORT).show();
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try {
                startActivity(Intent.createChooser(intent, "Kirim laporan PDF"));
            } catch (Exception ex) {
                Toast.makeText(
                        this,
                        "WhatsApp tidak ditemukan.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
