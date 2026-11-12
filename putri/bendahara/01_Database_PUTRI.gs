function keuanganPutriDb_(){ return SpreadsheetApp.openById(KEUANGAN_PUTRI_DB_ID); }

function keuanganPutriSheet_(name){
  const sh=keuanganPutriDb_().getSheetByName(name);
  if(!sh) throw new Error('Tab '+name+' belum tersedia. Jalankan keuanganPutriSetupDatabase().');
  return sh;
}

function keuanganPutriSetupDatabase(){
  const db=keuanganPutriDb_(), result=[];
  Object.keys(KEUANGAN_PUTRI_HEADERS).forEach(name=>{
    let sh=db.getSheetByName(name);
    if(!sh){
      sh=db.insertSheet(name);
      sh.getRange(1,1,1,KEUANGAN_PUTRI_HEADERS[name].length).setValues([KEUANGAN_PUTRI_HEADERS[name]]);
      sh.setFrozenRows(1);
      result.push('Dibuat: '+name);
    } else {
      const first=sh.getRange(1,1,1,KEUANGAN_PUTRI_HEADERS[name].length).getDisplayValues()[0];
      if(sh.getLastRow()===0 || first.every(v=>!v)){
        sh.getRange(1,1,1,KEUANGAN_PUTRI_HEADERS[name].length).setValues([KEUANGAN_PUTRI_HEADERS[name]]);
        sh.setFrozenRows(1);
      }
      result.push('Dipertahankan: '+name);
    }
  });
  const props=PropertiesService.getScriptProperties();
  if(!props.getProperty('KEUANGAN_PUTRI_PIN')) props.setProperty('KEUANGAN_PUTRI_PIN','999999');
  return result;
}

function keuanganPutriRows_(name){
  const sh=keuanganPutriSheet_(name), v=sh.getDataRange().getValues();
  return v.length>1?v.slice(1).filter(r=>r.some(x=>x!==''&&x!==null)):[];
}

function keuanganPutriNum_(v){
  if(typeof v==='number') return v;
  return Number(String(v==null?'':v).replace(/[^0-9.-]/g,''))||0;
}

function keuanganPutriDateText_(v){
  if(v instanceof Date && !isNaN(v.getTime())) return Utilities.formatDate(v,'Asia/Jakarta',"yyyy-MM-dd'T'HH:mm:ss");
  return String(v||'');
}
