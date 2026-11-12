function keuanganPutriWrite_(p){
  const action=String(p.action||'');
  if(action==='catatPengeluaran') return keuanganPutriCatatPengeluaran_(p);
  if(action==='catatPemasukanKas') return keuanganPutriCatatPemasukanKas_(p);
  if(action==='pindahkanSisaSPPKeKas') return keuanganPutriPindahkanSisaSPPKeKas_(p);
  throw new Error('Aksi tulis tidak dikenal.');
}

function doGet(e){
  try{
    const p=e&&e.parameter||{}, action=String(p.action||'getDashboardKeuangan');
    const result=['catatPengeluaran','catatPemasukanKas','pindahkanSisaSPPKeKas'].includes(action)
      ?keuanganPutriWrite_(p):keuanganPutriGet_(action);
    return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
  }catch(err){
    return ContentService.createTextOutput(JSON.stringify({success:false,message:err.message})).setMimeType(ContentService.MimeType.JSON);
  }
}