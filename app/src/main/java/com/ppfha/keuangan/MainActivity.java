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
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
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
        @JavascriptInterface public void saveLaporanPDF(final String html, final String fileName) { runOnUiThread(() -> buatPDFDanSimpan(html,fileName)); }

        @JavascriptInterface public void shareLaporanPDF(final String html, final String fileName, final String message) {
            runOnUiThread(() -> buatPDFDanBagikan(html, fileName, message));
        }
    }


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
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        v.setBackgroundColor(Color.WHITE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(794, 1123);
        lp.leftMargin = -1000;
        lp.topMargin = -1000;
        addContentView(v, lp);

        v.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                v.postDelayed(() -> {
                    try {
                        final int pageWidthCss = 794;
                        final int pageHeightCss = 1123;

                        v.measure(
                                View.MeasureSpec.makeMeasureSpec(pageWidthCss, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        v.layout(0, 0, pageWidthCss, v.getMeasuredHeight());

                        int contentHeight = Math.max(v.getContentHeight(), v.getMeasuredHeight());
                        if (contentHeight <= 0) contentHeight = pageHeightCss;

                        int pageCount = (contentHeight + pageHeightCss - 1) / pageHeightCss;
                        PdfDocument doc = new PdfDocument();

                        float scale = 595f / pageWidthCss;
                        for (int pageNo = 0; pageNo < pageCount; pageNo++) {
                            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                                    595, 842, pageNo + 1).create();
                            PdfDocument.Page page = doc.startPage(info);
                            Canvas canvas = page.getCanvas();
                            canvas.drawColor(Color.WHITE);
                            canvas.save();
                            canvas.scale(scale, scale);
                            canvas.translate(0, -pageNo * pageHeightCss);
                            v.draw(canvas);
                            canvas.restore();
                            doc.finishPage(page);
                        }

                        try (FileOutputStream out = new FileOutputStream(pdf)) {
                            doc.writeTo(out);
                        }
                        doc.close();
                        v.destroy();

                        if (share) {
                            bagikanPDF(pdf, message);
                        } else {
                            saveToDownload(pdf, n);
                        }
                    } catch (Exception e) {
                        v.destroy();
                        Toast.makeText(MainActivity.this,
                                "Gagal membuat PDF: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }, 1200);
            }
        });

        v.loadDataWithBaseURL(
                "https://mohammedsaidilyas-bot.github.io/Keuangan-Pesantren/",
                html, "text/html", "UTF-8", null);
    }

    private void buatPDFDanBagikan(String html, String fileName, String message) {
        renderHtmlToPdf(html, fileName, true, message);
    }

    private void buatPDFDanSimpan(String html, String fileName) {
        renderHtmlToPdf(html, fileName, false, null);
    }

    private void saveToDownload(File f,String n){try{
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,n);v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Bendahara");v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri u=getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),v);if(u==null)throw new IOException("Download tidak tersedia");
            try(InputStream in=new java.io.FileInputStream(f);OutputStream o=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int k;while((k=in.read(b))!=-1)o.write(b,0,k);}
            v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,v,null,null);Toast.makeText(this,"PDF tersimpan di Download/Bendahara/"+n,Toast.LENGTH_LONG).show();
        }else{pendingPdf=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,n);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,7001);}
    }catch(Exception e){Toast.makeText(this,"Gagal menyimpan PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private File pendingPdf;
    @Override protected void onActivityResult(int r,int s,Intent d){super.onActivityResult(r,s,d);if(r==7001&&s==RESULT_OK&&d!=null&&d.getData()!=null&&pendingPdf!=null){try(InputStream i=new java.io.FileInputStream(pendingPdf);OutputStream o=getContentResolver().openOutputStream(d.getData())){byte[] b=new byte[8192];int k;while((k=i.read(b))!=-1)o.write(b,0,k);Toast.makeText(this,"PDF berhasil disimpan.",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Gagal menyimpan PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}pendingPdf=null;}}
    
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
