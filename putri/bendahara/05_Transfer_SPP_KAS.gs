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

    // Verifikasi tulis-baca sebelum mengembalikan status berhasil.
    const last=sh.getLastRow();
    const saved=sh.getRange(last,1,1,7).getValues()[0];
    if(String(saved[0])!==id || keuanganPutriNum_(saved[4])!==keuanganPutriNum_(again.sisaSPP) ||
       String(saved[5])!==String(again.periodeKode) || String(saved[6]).toUpperCase()!=='SELESAI') {
      throw new Error('Transaksi belum dapat diverifikasi di tab TransferSPPKeKas. Tidak mengulangi pemindahan; periksa spreadsheet.');
    }
    return {success:true,message:'Sisa SPP PUTRI berhasil dipindahkan ke Kas Pondok.',jumlah:again.sisaSPP,periode:again.periode,id:id};
  } finally { lock.releaseLock(); }
}