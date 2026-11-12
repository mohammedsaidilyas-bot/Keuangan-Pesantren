function keuanganPutriWrite_(p){
  keuanganPutriPin_(p.pin);
  const action=String(p.action||''), amount=keuanganPutriNum_(p.jumlah);
  if(action==='pindahkanSisaSPPKeKas'){
    const s=keuanganPutriSummary_();
    if(s.belumBayar>0) throw new Error('Masih ada '+s.belumBayar+' santri yang belum lunas. Transfer ditolak.');
    if(s.sisaSPP<=0) throw new Error('Tidak ada sisa SPP yang dapat dipindahkan.');
    const lock=LockService.getScriptLock(); lock.waitLock(20000);
    try{
      const again=keuanganPutriSummary_();
      if(again.belumBayar>0||again.sisaSPP<=0) throw new Error('Saldo/periode berubah. Muat ulang dashboard.');
      const already=keuanganPutriRows_('TransferSPPKeKas').some(r=>String(r[5]||'')===again.periodeKode&&String(r[6]||'').toUpperCase()==='SELESAI');
      if(already) throw new Error('Sisa SPP periode ini sudah pernah dipindahkan.');
      keuanganPutriSheet_('TransferSPPKeKas').appendRow([Utilities.getUuid(),new Date(),'TRANSFER','Sisa SPP periode '+again.periode,again.sisaSPP,again.periodeKode,'SELESAI']);
      return {success:true,message:'Sisa SPP PUTRI berhasil dipindahkan ke Kas Pondok.',jumlah:again.sisaSPP};
    } finally {lock.releaseLock();}
  }
  if(!Number.isSafeInteger(amount)||amount<=0) throw new Error('Jumlah harus berupa angka positif.');
  const tanggal=p.tanggal?new Date(p.tanggal):new Date();
  if(isNaN(tanggal.getTime())) throw new Error('Tanggal tidak valid.');
  const keterangan=String(p.keterangan||'').trim();
  if(!keterangan||keterangan.length>500) throw new Error('Keterangan wajib diisi (maksimal 500 karakter).');
  const id=Utilities.getUuid(), sumber=String(p.sumber||'SPP').trim().toUpperCase();
  if(action==='catatPengeluaran'){
    if(sumber==='KAS'){
      keuanganPutriSheet_('KasPondok').appendRow([id,tanggal,'PENGELUARAN',keterangan,amount,String(p.catatan||''),'Bendahara PUTRI']);
    } else {
      keuanganPutriSheet_('PengeluaranSPP').appendRow([id,tanggal,keterangan,amount,'SPP',keuanganPutriExpensePeriod_(p.periode||keuanganPutriPeriod_()),String(p.catatan||''),'AKTIF']);
    }
    return {success:true,message:'Pengeluaran PUTRI berhasil dicatat.',id};
  }
  if(action==='catatPemasukanKas'){
    keuanganPutriSheet_('KasPondok').appendRow([id,tanggal,'PEMASUKAN',keterangan,amount,String(p.catatan||''),'Bendahara PUTRI']);
    return {success:true,message:'Pemasukan kas PUTRI berhasil dicatat.',id};
  }
  throw new Error('Aksi tulis tidak dikenal.');
}
