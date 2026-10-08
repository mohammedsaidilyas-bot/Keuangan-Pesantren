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
        webView.loadUrl("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/");
    }

    private class AndroidBridge {
        @JavascriptInterface public void saveLaporanPDF(final String html, final String fileName) {
            runOnUiThread(() -> bukaPratinjauCetakAndroid(html, fileName));
        }

        @JavascriptInterface public void shareLaporanPDF(final String html, final String fileName, final String message) {
            runOnUiThread(() -> pilihPDFUntukWhatsApp(message));
        }
    }

    /*
     * Simpan PDF langsung tanpa membuka Android Print Preview.
     * WebView tetap merender HTML laporan, lalu PrintDocumentAdapter
     * menulis hasil PDF ke file cache. Setelah selesai, file dipindahkan
     * ke Download/Bendahara dan pengguna mendapat tombol Tutup.
     */
    private void bukaPratinjauCetakAndroid(String html, String fileName) {
        try {
            if (printWebView != null) {
                try { ((ViewGroup) printWebView.getParent()).removeView(printWebView); } catch (Exception ignored) {}
                try { printWebView.destroy(); } catch (Exception ignored) {}
            }

            printWebView = new WebView(this);
            WebSettings s = printWebView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadWithOverviewMode(false);
            s.setUseWideViewPort(false);
            s.setTextZoom(100);
            printWebView.setBackgroundColor(Color.WHITE);
            printWebView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = -2000;
            lp.topMargin = -2000;
            addContentView(printWebView, lp);

            printWebView.setWebViewClient(new WebViewClient() {
                private boolean saved = false;

                @Override public void onPageFinished(WebView view, String url) {
                    if (saved) return;
                    saved = true;
                    printWebView.postDelayed(() -> {\n                        printWebView.evaluateJavascript("window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;", null);\n                        printWebView.scrollTo(0, 0);\n                        printWebView.postDelayed(() -> simpanPDFLangsung(fileName), 350);\n                    }, 900);
                }
            });

            printWebView.loadDataWithBaseURL(
                    "https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/",
                    html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyiapkan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void simpanPDFLangsung(String fileName) {
        try {
            final String safeName = (fileName == null || fileName.trim().isEmpty())
                    ? "Laporan-Keuangan.pdf" : fileName;

            // A4 portrait: 595 x 842 points. WebView dirender pada lebar A4
            // dengan skala 96dpi, lalu dipotong menjadi beberapa halaman PDF.
            final int pageWidthPx = 794;
            final float pointsPerPx = 595f / pageWidthPx;
            final int pageHeightPx = Math.round(842f / pointsPerPx);

            printWebView.measure(
                    ViewGroup.MeasureSpec.makeMeasureSpec(pageWidthPx, ViewGroup.MeasureSpec.EXACTLY),
                    ViewGroup.MeasureSpec.makeMeasureSpec(0, ViewGroup.MeasureSpec.UNSPECIFIED)
            );
            printWebView.layout(0, 0, pageWidthPx, printWebView.getMeasuredHeight());

            final int contentHeightPx = printWebView.getMeasuredHeight();
            if (contentHeightPx <= 0) throw new IOException("Isi laporan kosong");

            File dir = new File(getCacheDir(), "laporan");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Folder cache laporan tidak dapat dibuat");
            }

            final File pdfFile = new File(dir, safeName);
            if (pdfFile.exists()) pdfFile.delete();

            PdfDocument document = new PdfDocument();
            try {
                int totalPages = (int)Math.ceil(contentHeightPx / (double)pageHeightPx);

                for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                            595, 842, pageNumber + 1).create();
                    PdfDocument.Page page = document.startPage(pageInfo);

                    android.graphics.Canvas canvas = page.getCanvas();
                    canvas.save();
                    canvas.scale(pointsPerPx, pointsPerPx);
                    canvas.translate(0, -pageNumber * pageHeightPx);
                    printWebView.draw(canvas);
                    canvas.restore();

                    document.finishPage(page);
                }

                try (OutputStream out = new java.io.FileOutputStream(pdfFile)) {
                    document.writeTo(out);
                }
            } finally {
                document.close();
            }

            boolean ok = saveToDownload(pdfFile, safeName);
            if (ok) {
                tampilkanDialogPDFTersimpan(safeName);
            }
            bersihkanPrintWebView();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            bersihkanPrintWebView();
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

    private void pilihPDFUntukWhatsApp(String message) {
        pendingWhatsAppMessage = message == null ? "" : message;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("application/pdf");
        pick.putExtra(Intent.EXTRA_TITLE, "Pilih laporan PDF");
        try {
            startActivityForResult(pick, PICK_PDF_FOR_WHATSAPP);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka pemilih PDF.", Toast.LENGTH_LONG).show();
        }
    }

    private void bagikanUriPDF(Uri uri, String message) {
        if (uri == null) {
            Toast.makeText(this, "PDF belum dipilih.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        if (message != null && !message.isEmpty()) intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Laporan PDF", uri));
        intent.setPackage("com.whatsapp");
        try {
            grantUriPermission("com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try { startActivity(Intent.createChooser(intent, "Kirim laporan PDF")); }
            catch (Exception ex) { Toast.makeText(this, "WhatsApp tidak tersedia.", Toast.LENGTH_LONG).show(); }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF_FOR_WHATSAPP && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                bagikanUriPDF(uri, pendingWhatsAppMessage);
            }
        }
    }

    private boolean saveToDownload(File f, String n) {
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

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(u, done, null, null);
                return true;
            }

            Toast.makeText(this,
                    "Android versi ini belum didukung untuk penyimpanan otomatis.",
                    Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void bagikanPDF(File pdfFile, String message) {
        if (!pdfFile.exists() || pdfFile.length() == 0) return;
        Uri uri = Uri.parse("content://com.ppfha.keuangan.fileprovider/laporan/" +
                Uri.encode(pdfFile.getName()));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.putExtra("jid", "6282219644442@s.whatsapp.net");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), "Laporan PDF", uri));
        intent.setPackage("com.whatsapp");
        try {
            grantUriPermission("com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try { startActivity(Intent.createChooser(intent, "Kirim laporan PDF")); }
            catch (Exception ignored) {}
        }
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
