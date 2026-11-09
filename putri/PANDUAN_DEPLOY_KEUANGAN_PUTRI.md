# Panduan Keuangan PUTRI (belum production)
1. Buat Apps Script baru khusus Keuangan PUTRI, lalu salin `Code_KEUANGAN_PUTRI.gs` sebagai `Code.gs`.
2. Jalankan `keuanganPutriSetupDatabase()` satu kali.
3. Atur Script Properties `KEUANGAN_PUTRI_PIN` (PIN awal uji 999999, wajib diganti sebelum produksi) dan `KEUANGAN_PUTRI_PERIODE` bila ingin menentukan periode aktif secara manual.
4. Pastikan tab `Santri`, `RiwayatPembayaranSPP`, `KasSPP`, dan `PendingSPP` dibuat/diisi oleh backend SPP PUTRI pada spreadsheet PUTRI yang sama. Jangan menghubungkan spreadsheet PUTRA.
5. Deploy sebagai Web App, kemudian isi konstanta `API_URL` pada `KEUANGAN_PUTRI.html` dengan URL deployment PUTRI.
6. Uji dashboard, kas, riwayat pengeluaran, status pembayaran, pencatatan pengeluaran/pemasukan, dan transfer sisa SPP dengan data uji.
Catatan: backend ini memakai tab PengeluaranSPP, KasPondok, TransferSPPKeKas dan membaca riwayat pembayaran dari tab yang dibuat SPP PUTRI. Validasi periode dan audit laporan perlu dilakukan sebelum produksi.
