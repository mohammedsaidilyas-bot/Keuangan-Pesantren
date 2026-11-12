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
  const raw=String(p.periode||'').trim();
  if(/^\d{4}-\d{2}$/.test(raw)) return raw;
  const match=raw.match(/^(\d{1,2})[/-](\d{4})$/);
  if(match) return match[2]+'-'+String(Number(match[1])).padStart(2,'0');
  return '';
}
