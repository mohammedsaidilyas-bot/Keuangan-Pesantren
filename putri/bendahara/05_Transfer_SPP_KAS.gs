function keuanganPutriPindahkanSisaSPPKeKas_(p){
  keuanganPutriPin_(p.pin);
  const s=keuanganPutriSummary_();
  if(s.belumBayar>0) throw new Error('Masih ada '+s.belumBayar+' santri yang belum lunas. Transfer ditolak.');
  if(s.sisaSPP<=0) throw new Error('Tidak ada sisa SPP yang dapat dipindahkan.');
  const lock=LockService.getScriptLock();
  lock.waitLock(20000);
  try{
    const again=keuanganPutriSummary_();
    if(again.belumBayar>0||again.sisaSPP<=0) throw new Error('Saldo/periode berubah. Muat ulang dashboard.');
    const already=keuanganPutriRows_('TransferSPPKeKas').some(r=>String(r[5]||'')===again.periodeKode&&String(r[6]||'').toUpperCase()==='SELESAI');
    if(already) throw new Error('Sisa SPP periode ini sudah pernah dipindahkan.');
    const sh=keuanganPutriSheet_('TransferSPPKeKas');
    const id=Utilities.getUuid();
    const row=[id,new Date(),'TRANSFER','Sisa SPP periode '+again.periode,again.sisaSPP,again.periodeKode,'SELESAI'];
    sh.appendRow(row);
    SpreadsheetApp.flush();

    // Verifikasi berdasarkan ID unik, bukan mengasumsikan appendRow selalu berada di baris terakhir.
    // Ini lebih aman jika sheet memiliki baris lain/format tambahan.
    const last=sh.getLastRow();
    if(last<2) throw new Error('Baris transfer belum ditemukan setelah disimpan. Periksa tab TransferSPPKeKas.');
    const rowsAfterWrite=sh.getRange(2,1,last-1,7).getValues();
    const saved=rowsAfterWrite.find(r=>String(r[0])===id);
    if(!saved) {
      throw new Error('ID transaksi '+id+' belum ditemukan di kolom A tab TransferSPPKeKas. Periksa spreadsheet; jangan ulangi sebelum memastikan data.');
    }
    const amountSaved=keuanganPutriNum_(saved[4]);
    const periodSaved=String(saved[5]||'').trim();
    const statusSaved=String(saved[6]||'').trim().toUpperCase();
    if(amountSaved!==keuanganPutriNum_(again.sisaSPP) ||
       periodSaved!==String(again.periodeKode) || statusSaved!=='SELESAI') {
      throw new Error('Transaksi ditemukan, tetapi nilainya tidak cocok. Tersimpan: Rp'+amountSaved+'; periode '+periodSaved+'; status '+statusSaved+'. Jangan ulangi transaksi.');
    }
    return {success:true,message:'Sisa SPP PUTRI berhasil dipindahkan ke Kas Pondok.',jumlah:again.sisaSPP,periode:again.periode,id:id};
  } finally { lock.releaseLock(); }
}