function keuanganPutriExpensePeriod_(value){
  const raw=String(value||'').trim();
  if(/^\\d{4}-\\d{2}$/.test(raw)) return raw;
  const parts=raw.split(/\\s+/), month=KEUANGAN_PUTRI_MONTHS.findIndex(m=>m.toLowerCase()===String(parts[0]||'').toLowerCase());
  if(month<0) return '';
  const year=parts[1]&&/^\\d{4}$/.test(parts[1])?parts[1]:String(keuanganPutriPeriod_()).slice(0,4);
  return year+'-'+String(month+1).padStart(2,'0');
}

function keuanganPutriExpenseList_(){
  return keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL').map(r=>({id:r[0],tanggal:keuanganPutriDateText_(r[1]),keterangan:r[2],jumlah:keuanganPutriNum_(r[3]),sumber:r[4],periode:r[5],catatan:r[6],status:r[7]||'AKTIF'}));
}
