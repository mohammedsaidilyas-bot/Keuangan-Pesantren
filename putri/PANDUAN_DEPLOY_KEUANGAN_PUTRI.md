# Deployment Bendahara PUTRI — modular dan terpisah dari PUTRA

## 1. Buat proyek Apps Script khusus
1. Buka https://script.google.com/ lalu buat proyek baru bernama **Bendahara PUTRI PPFHA**.
2. Di dalam project baru, buat file Script terpisah sesuai daftar di bawah. Gunakan tombol **+ → Script** untuk setiap file.
3. Salin isi setiap file dari folder `putri/bendahara/` pada branch `putri-development`.
4. Hapus atau kosongkan file bawaan `Code.gs` setelah seluruh kode modular dibuat, agar tidak ada fungsi ganda.
5. Jangan mengubah proyek PROGRAM PESANTREN PUTRI PPFHA yang sedang berjalan.

## 2. Daftar file yang harus dibuat
- `00_Config_PUTRI.gs` — ID database dan konfigurasi.
- `01_Database_PUTRI.gs` — akses database dan setup tab transaksi.
- `02_SPP_PUTRI.gs` — membaca data santri dan pembayaran SPP.
- `03_Kas_PUTRI.gs` — pemasukan dan transaksi kas.
- `04_Pengeluaran_PUTRI.gs` — pengeluaran SPP dan kas.
- `05_Transfer_SPP_KAS.gs` — pemindahan sisa SPP ke kas.
- `06_Laporan_PUTRI.gs` — ringkasan dan perhitungan saldo.
- `07_API_PUTRI.gs` — penghubung aplikasi dengan backend.
- `08_Keamanan_PUTRI.gs` — PIN dan validasi transaksi.

Semua file berada dalam **satu project Apps Script Bendahara PUTRI**. Jangan membuat project terpisah untuk setiap file.

## 3. Database tetap satu
Backend menggunakan database PUTRI yang sama:
`1ZFOtVEoB48NytlEqtq9RrFHomHMTF7einzGIAHOp9LM`

Data yang dibaca:
- Tab santri: `database putri`
- Tab pembayaran: `SPP_PUTRI_Pembayaran`

Tab transaksi Bendahara yang disiapkan:
- `PengeluaranSPP`
- `KasPondok`
- `TransferSPPKeKas`

Setup tidak membuat ulang atau mengubah tab data santri dan pembayaran SPP.

## 4. Jalankan setup
1. Pilih fungsi `keuanganPutriSetupDatabase`.
2. Klik **Run/Jalankan** dan berikan izin yang diminta.
3. Pastikan tiga tab transaksi dibuat atau dipertahankan.
4. Pada **Project Settings → Script Properties**, atur:
   - `KEUANGAN_PUTRI_PIN` = PIN khusus Bendahara PUTRI. Nilai awal uji `999999` wajib diganti sebelum produksi.
   - Opsional `KEUANGAN_PUTRI_PERIODE` = format `yyyy-MM`, misalnya `2026-11`.

## 5. Deploy Web App
1. Klik **Deploy → New deployment**.
2. Pilih **Web app**.
3. Execute as: **Me**.
4. Who has access: **Anyone** (akses baca dashboard; penulisan tetap memerlukan PIN).
5. Deploy dan salin URL berakhiran `/exec`.

## 6. Sambungkan UI
Di file `KEUANGAN_PUTRI.html`, ganti nilai `API_URL` dengan URL Web App Bendahara PUTRI yang baru. URL belum dapat diisi sebelum deployment dibuat dari akun Google pengelola.

## 7. Uji
- Buka URL dengan `?action=getDashboardKeuangan`; pastikan JSON `success:true`.
- Uji `getKasPondok`, `getPengeluaranTerbaru`, dan `getStatusPembayaran`.
- Uji satu transaksi nominal kecil dan pastikan hanya tab transaksi PUTRI yang berubah.
- Uji aturan transfer: ditolak jika masih ada santri belum lunas dan tidak dapat dipindahkan dua kali pada periode yang sama.

**Penting:** Jangan sambungkan ke endpoint PUTRA atau mengganti deployment SPP PUTRI. Backend Bendahara membaca database PUTRI yang sama, tetapi script dan deployment-nya berdiri sendiri.
