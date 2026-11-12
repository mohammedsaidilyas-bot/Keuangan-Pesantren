function keuanganPutriCatatPemasukanKas_(p){
  keuanganPutriPin_(p.pin);
  const input=keuanganPutriValidasiTransaksi_(p);
  keuanganPutriSheet_('KasPondok').appendRow([input.id,input.tanggal,'PEMASUKAN',input.keterangan,input.amount,String(p.catatan||''),'Bendahara PUTRI']);
  return {success:true,message:'Pemasukan kas PUTRI berhasil dicatat.',id:input.id};
}

function keuanganPutriCatatPengeluaranKas_(input,p){
  keuanganPutriSheet_('KasPondok').appendRow([input.id,input.tanggal,'PENGELUARAN',input.keterangan,input.amount,String(p.catatan||''),'Bendahara PUTRI']);
  return {success:true,message:'Pengeluaran kas PUTRI berhasil dicatat.',id:input.id};
}