package com.ppfha.keuangan;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;

public class PdfFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File getPdfFile(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Nama file PDF tidak valid");
        }
        return new File(new File(requireContext().getCacheDir(), "laporan"), name);
    }

    @Override public String getType(Uri uri) {
        return "application/pdf";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) {
        File file = getPdfFile(uri);
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF tidak ditemukan", e);
        }
    }

    // WhatsApp membaca metadata file melalui ContentResolver.query().
    // Tanpa DISPLAY_NAME/SIZE, beberapa versi WhatsApp membuka chat tetapi
    // tidak menampilkan PDF sebagai lampiran.
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = getPdfFile(uri);
        String[] cols = projection != null ? projection : new String[] {
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        };
        MatrixCursor cursor = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file.exists() ? file.length() : 0L;
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}