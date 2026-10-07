package com.ppfha.keuangan;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
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
        printView.getSettings().setJavaScriptEnabled(false);
        final File dir = new File(getCacheDir(), "laporan");
        if (!dir.exists()) dir.mkdirs();
        final String safeName = fileName == null || fileName.trim().isEmpty()
                ? "Laporan-Keuangan.pdf" : fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        final File pdfFile = new File(dir, safeName);

        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                final PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("Laporan Keuangan");
                final PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("laporan", "Laporan", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();

                adapter.onLayout(null, attrs, new CancellationSignal(), new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        try {
                            final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile,
                                    ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                            adapter.onWrite(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES}, pfd,
                                    new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                                @Override public void onWriteFinished(android.print.PageRange[] pages) {
                                    try { pfd.close(); } catch (IOException ignored) {}
                                    printView.destroy();
                                    bagikanPDF(pdfFile, message);
                                }
                                @Override public void onWriteFailed(CharSequence error) {
                                    try { pfd.close(); } catch (IOException ignored) {}
                                    printView.destroy();
                                    Toast.makeText(MainActivity.this, "Gagal membuat PDF.", Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (Exception e) {
                            printView.destroy();
                            Toast.makeText(MainActivity.this, "Gagal menyiapkan PDF.", Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override public void onLayoutFailed(CharSequence error) {
                        printView.destroy();
                        Toast.makeText(MainActivity.this, "Gagal membuat tata letak PDF.", Toast.LENGTH_LONG).show();
                    }
                }, null);
            }
        });
        printView.loadDataWithBaseURL("https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/", html, "text/html", "UTF-8", null);
    }

    private void bagikanPDF(File pdfFile, String message) {
        Uri uri = Uri.parse("content://com.ppfha.keuangan.fileprovider/laporan/" + Uri.encode(pdfFile.getName()));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Laporan PDF", uri));
        intent.setPackage("com.whatsapp");
        try {
            grantUriPermission("com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
