# Deployment Bendahara PUTRI — terpisah dari PUTRA

## 1. Buat proyek Apps Script khusus
1. Buka https://script.google.com/ lalu buat proyek baru bernama **Bendahara PUTRI PPFHA**.
2. Hapus isi file `Code.gs`, lalu salin seluruh isi `Code_KEUANGAN_PUTRI.gs` dari folder ini ke `Code.gs`.
3. Jangan menyalin kode dari proyek Apps Script PUTRA dan jangan mengganti deployment SPP PUTRI yang sudah berjalan.

## 2. Database
Backend ini diarahkan ke database PUTRI:
`1ZFOtVEoB48NytlEqtq9RrFHomHMTF7einzGIAHOp9LM`

Sumber SPP yang dibaca:
- Tab santri: `database putri`
- Tab pembayaran: `SPP_PUTRI_Pembayaran`

Tab transaksi Bendahara yang dibuat otomatis oleh setup:
- `PengeluaranSPP`
- `KasPondok`
- `TransferSPPKeKas`

Tab pembayaran dan data santri tidak dibuat ulang atau diubah oleh setup Bendahara.

## 3. Jalankan setup
1. Di editor Apps Script pilih fungsi `keuanganPutriSetupDatabase`.
2. Klik **Run/Jalankan** dan berikan izin yang diminta.
3. Pastikan log menyebut tiga tab transaksi dibuat atau dipertahankan.
4. Pada **Project Settings → Script Properties**, tambahkan:
   - `KEUANGAN_PUTRI_PIN` = PIN khusus Bendahara PUTRI (untuk uji awal dapat memakai `999999`; ganti sebelum produksi).
   - Opsional `KEUANGAN_PUTRI_PERIODE` = periode format `yyyy-MM`, misalnya `2026-11`. Jika tidak diisi, backend memakai bulan berjalan WIB.

## 4. Deploy Web App
1. Klik **Deploy → New deployment**.
2. Pilih tipe **Web app**.
3. Execute as: **Me**.
4. Who has access: **Anyone** (akses baca dashboard publik; semua penulisan tetap memerlukan PIN Bendahara).
5. Klik Deploy dan salin URL berakhiran `/exec`.

## 5. Sambungkan UI Bendahara PUTRI
Buka `KEUANGAN_PUTRI.html`, cari konstanta:
`const API_URL = "ISI_URL_APPS_SCRIPT_KEUANGAN_PUTRI_DI_SINI";`
Ganti hanya nilai tersebut dengan URL Web App Bendahara PUTRI yang baru, lalu simpan.

## 6. Uji sebelum dipakai
- Buka URL Web App dengan `?action=getDashboardKeuangan`; respons harus JSON dan `success:true`.
- Uji `getKasPondok`, `getPengeluaranTerbaru`, dan `getStatusPembayaran`.
- Catat pemasukan/pengeluaran nominal kecil dengan PIN uji, lalu pastikan hanya tab transaksi PUTRI yang bertambah.
- Pastikan transfer sisa SPP ditolak jika masih ada santri belum lunas dan tidak bisa dilakukan dua kali pada periode yang sama.

**Penting:** URL Web App belum dapat diisi otomatis sebelum deployment dibuat dari akun Google pengelola. Jangan menghubungkan backend ini ke endpoint SPP PUTRI atau endpoint PUTRA. Backend ini membaca database SPP PUTRI, tetapi deployment Bendahara tetap berdiri sendiri.
