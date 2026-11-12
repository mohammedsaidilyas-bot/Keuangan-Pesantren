function keuanganPutriActiveStudents_(){
  const sh=keuanganPutriDb_().getSheetByName('database putri');
  if(!sh || sh.getLastRow()<2) return [];
  const values=sh.getDataRange().getDisplayValues(), headers=values[0].map(x=>String(x).trim().toLowerCase());
  const nisIx=headers.findIndex(x=>['nis','nomor induk','nomor induk santri'].includes(x));
  const statusIx=headers.findIndex(x=>['status','status santri','statussantri'].includes(x));
  return values.slice(1).filter(r=>{
    const nis=String(r[nisIx>=0?nisIx:0]||'').trim();
    const status=String(r[statusIx>=0?statusIx:7]||'').trim().toLowerCase();
    return !!nis && (!status || ['aktif','active'].includes(status));
  }).map(r=>({nis:String(r[nisIx>=0?nisIx:0]).trim(),nama:String(r[1]||'').trim()}));
}

function keuanganPutriPayments_(){
  const sh=keuanganPutriDb_().getSheetByName('SPP_PUTRI_Pembayaran');
  if(!sh || sh.getLastRow()<2) return [];
  const values=sh.getDataRange().getValues(), headers=values[0].map(x=>String(x).trim());
  return values.slice(1).filter(r=>r.some(x=>x!==''&&x!==null)).map(r=>{
    const o={}; headers.forEach((h,i)=>o[h]=r[i]); return o;
  });
}

function keuanganPutriIsPaid_(p){ return String(p.status||'').trim().toLowerCase()==='lunas'; }

function keuanganPutriPaymentPeriod_(p){
  const value=p.periode;
  // Kolom periode di Google Sheets dapat terbaca sebagai Date object.
  if(value instanceof Date && !isNaN(value.getTime())){
    return Utilities.formatDate(value,'Asia/Jakarta','yyyy-MM');
  }
  const raw=String(value==null?'':value).trim();
  if(!raw) return '';
  let match=raw.match(/^(\d{4})[-/](\d{1,2})$/);
  if(match) return match[1]+'-'+String(Number(match[2])).padStart(2,'0');
  match=raw.match(/^(\d{1,2})[-/](\d{4})$/);
  if(match) return match[2]+'-'+String(Number(match[1])).padStart(2,'0');
  // Cadangan jika nilai tanggal diterima sebagai teks, bukan Date object.
  const parsed=new Date(raw);
  if(!isNaN(parsed.getTime())){
    return Utilities.formatDate(parsed,'Asia/Jakarta','yyyy-MM');
  }
  return '';
}
