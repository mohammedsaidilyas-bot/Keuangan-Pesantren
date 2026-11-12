/**
 * KEUANGAN PUTRI PPFHA — backend mandiri.
 * Database: DATABASE SANTRI PUTRI PPFHA (ID sama dengan konfigurasi SPP PUTRI).
 * Tidak menggunakan endpoint atau database PUTRA.
 */
const KEUANGAN_PUTRI_DB_ID = '1ZFOtVEoB48NytlEqtq9RrFHomHMTF7einzGIAHOp9LM';
const KEUANGAN_PUTRI_HEADERS = {
  PengeluaranSPP: ['ID','Tanggal','Keterangan','Jumlah','Sumber','Periode','Catatan','Status'],
  KasPondok: ['ID','Tanggal','Jenis','Keterangan','Jumlah','Catatan','Petugas'],
  TransferSPPKeKas: ['ID','Tanggal','Jenis','Keterangan','Jumlah','Periode','Status']
};
const KEUANGAN_PUTRI_MONTHS = ['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
