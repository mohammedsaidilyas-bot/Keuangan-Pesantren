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
    keuanganPutriSheet_('TransferSPPKeKas').appendRow([Utilities.getUuid(),new Date(),'TRANSFER','Sisa SPP periode '+again.periode,again.sisaSPP,again.periodeKode,'SELESAI']);
    return {success:true,message:'Sisa SPP PUTRI berhasil dipindahkan ke Kas Pondok.',jumlah:again.sisaSPP};
  } finally { lock.releaseLock(); }
}