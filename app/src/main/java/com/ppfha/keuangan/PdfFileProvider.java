package com.ppfha.keuangan;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;

public class PdfFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) {
        String name = uri.getLastPathSegment();
        File file = new File(new File(requireContext().getCacheDir(), "laporan"), name);
        try { return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (Exception e) { throw new IllegalArgumentException("PDF tidak ditemukan", e); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}