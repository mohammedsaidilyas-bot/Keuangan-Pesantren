/**
 * KEUANGAN PUTRI PPFHA — backend mandiri.
 * Database: DATABASE SANTRI PUTRI PPFHA (ID sama dengan konfigurasi SPP PUTRI).
 * Tidak menggunakan endpoint atau database PUTRA.
 */
const KEUANGAN_PUTRI_DB_ID = '1ZFOtVEoB48NytlEqtq9RrFHomHMTF7einzGIAHOp9LM';
const KEUANGAN_PUTRI_HEADERS = {
  PengeluaranSPP: ['ID','Tanggal','Keterangan','Jumlah','Sumber','Periode','Catatan','Status'],
  KasPondok: ['ID','Tanggal','Jenis','Keterangan','Jumlah','Catatan','Petugas'],
  TransferSPPKeKas: ['ID','Tanggal','Jenis','Keterangan','Jumlah','Periode','Status']
};
const KEUANGAN_PUTRI_MONTHS = ['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];

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
function keuanganPutriPeriod_(){
  const manual=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PERIODE');
  return manual || Utilities.formatDate(new Date(),'Asia/Jakarta','yyyy-MM');
}
function keuanganPutriPeriodLabel_(period){
  const m=String(period||'').match(/^(\d{4})-(\d{2})$/);
  return m ? KEUANGAN_PUTRI_MONTHS[Number(m[2])-1]+' '+m[1] : String(period||'');
}
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
function keuanganPutriSummary_(){
  const period=keuanganPutriPeriod_(), students=keuanganPutriActiveStudents_(), payments=keuanganPutriPayments_();
  const paid=payments.filter(p=>keuanganPutriIsPaid_(p)&&keuanganPutriPaymentPeriod_(p)===period);
  const paidNis=new Set(paid.map(p=>String(p.nis||'').trim()).filter(Boolean));
  const sppMasuk=paid.reduce((n,p)=>n+keuanganPutriNum_(p.nominal),0);
  const expenses=keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL');
  const pengeluaranSPP=expenses.filter(r=>String(r[5]||'').trim()===period).reduce((n,r)=>n+keuanganPutriNum_(r[3]),0);
  const kas=keuanganPutriRows_('KasPondok');
  const transfer=keuanganPutriRows_('TransferSPPKeKas').filter(r=>String(r[6]||'').toUpperCase()==='SELESAI');
  const totalKasMasuk=kas.filter(r=>String(r[2]).toUpperCase()==='PEMASUKAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const totalKasKeluar=kas.filter(r=>String(r[2]).toUpperCase()==='PENGELUARAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const transferTotal=transfer.reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const transferPeriode=transfer.filter(r=>String(r[5]||'')===period).reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const sisaSPP=sppMasuk-pengeluaranSPP-transferPeriode;
  return {periode:keuanganPutriPeriodLabel_(period),periodeKode:period,sppMasuk,pengeluaranSPP,lunas:paidNis.size,belumBayar:Math.max(0,students.length-paidNis.size),totalSantri:students.length,sisaSPP,saldoKas:totalKasMasuk-totalKasKeluar+transferTotal,totalKasMasuk,totalKasKeluar,transferSPP:transferTotal};
}
function keuanganPutriExpenseList_(){
  return keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL').map(r=>({id:r[0],tanggal:keuanganPutriDateText_(r[1]),keterangan:r[2],jumlah:keuanganPutriNum_(r[3]),sumber:r[4],periode:r[5],catatan:r[6],status:r[7]||'AKTIF'}));
}
function keuanganPutriGet_(action){
  const s=keuanganPutriSummary_();
  if(action==='getDashboardKeuangan') return {success:true,...s};
  if(action==='getKasPondok') return {success:true,saldoKas:s.totalKasMasuk-s.totalKasKeluar,transaksi:keuanganPutriRows_('KasPondok').map(r=>({id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:r[2],keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),catatan:r[5]}))};
  if(action==='getTransferSPP') return {success:true,transaksi:keuanganPutriRows_('TransferSPPKeKas').map(r=>({id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:'transfer',keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),periode:r[5],status:r[6]}))};
  if(action==='getRingkasanKeuangan') return {success:true,...s,totalPengeluaranSPP:s.pengeluaranSPP};
  if(action==='getPengeluaranTerbaru'){
    const list=keuanganPutriExpenseList_().reverse();
    return {success:true,transaksi:list.slice(0,200),pengeluaran:list.slice(0,200),totalPengeluaranSPP:s.pengeluaranSPP};
  }
  if(action==='getStatusPembayaran'){
    const students=keuanganPutriActiveStudents_(), paid=keuanganPutriPayments_().filter(p=>keuanganPutriIsPaid_(p)&&keuanganPutriPaymentPeriod_(p)===s.periodeKode);
    const paidNis=new Set(paid.map(p=>String(p.nis||'').trim()));
    const items=students.map(st=>({nis:st.nis,nama:st.nama,status:paidNis.has(st.nis)?'Lunas':'Belum Lunas'}));
    return {success:true,totalSantri:items.length,lunas:items.filter(x=>x.status==='Lunas').length,belum:items.filter(x=>x.status!=='Lunas').length,items};
  }
  throw new Error('Aksi tidak dikenal: '+action);
}
function keuanganPutriPin_(pin){
  const saved=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PIN')||'999999';
  if(String(pin||'')!==saved) throw new Error('Sandi Bendahara PUTRI salah.');
}
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
      keuanganPutriSheet_('PengeluaranSPP').appendRow([id,tanggal,keterangan,amount,'SPP',String(p.periode||keuanganPutriPeriod_()),String(p.catatan||''),'AKTIF']);
    }
    return {success:true,message:'Pengeluaran PUTRI berhasil dicatat.',id};
  }
  if(action==='catatPemasukanKas'){
    keuanganPutriSheet_('KasPondok').appendRow([id,tanggal,'PEMASUKAN',keterangan,amount,String(p.catatan||''),'Bendahara PUTRI']);
    return {success:true,message:'Pemasukan kas PUTRI berhasil dicatat.',id};
  }
  throw new Error('Aksi tulis tidak dikenal.');
}
function doGet(e){
  try{
    const p=e&&e.parameter||{}, action=String(p.action||'getDashboardKeuangan');
    const result=['catatPengeluaran','catatPemasukanKas','pindahkanSisaSPPKeKas'].includes(action)?keuanganPutriWrite_(p):keuanganPutriGet_(action);
    return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
  }catch(err){
    return ContentService.createTextOutput(JSON.stringify({success:false,message:err.message})).setMimeType(ContentService.MimeType.JSON);
  }
}
