function keuanganPutriPin_(pin){
  const saved=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PIN')||'999999';
  if(String(pin||'')!==saved) throw new Error('Sandi Bendahara PUTRI salah.');
}

function keuanganPutriValidasiTransaksi_(p){
  const amount=keuanganPutriNum_(p.jumlah);
  if(!Number.isSafeInteger(amount)||amount<=0) throw new Error('Jumlah harus berupa angka positif.');
  const tanggal=p.tanggal?new Date(p.tanggal):new Date();
  if(isNaN(tanggal.getTime())) throw new Error('Tanggal tidak valid.');
  const keterangan=String(p.keterangan||'').trim();
  if(!keterangan||keterangan.length>500) throw new Error('Keterangan wajib diisi (maksimal 500 karakter).');
  return {id:Utilities.getUuid(),amount,tanggal,keterangan};
}