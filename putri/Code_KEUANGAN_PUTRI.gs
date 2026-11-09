/**
 * KEUANGAN PUTRI PPFHA — backend mandiri, satu database dengan SPP PUTRI.
 * Jalankan keuanganPutriSetupDatabase() sekali.
 * Script Properties: KEUANGAN_PUTRI_PIN (default awal 999999), SPP_PUTRI_NOMINAL.
 */
const KEUANGAN_PUTRI_DB_ID='1ZFOtVEoB48NytlEqtq9RrFHomHMTF7einzGIAHOp9LM';
const KEUANGAN_PUTRI_MONTHS=['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
const KEUANGAN_PUTRI_HEADERS={
  PengeluaranSPP:['ID','Tanggal','Keterangan','Jumlah','Sumber','Periode','Catatan','Status'],
  KasPondok:['ID','Tanggal','Jenis','Keterangan','Jumlah','Catatan','Petugas'],
  TransferSPPKeKas:['ID','Tanggal','Jenis','Keterangan','Jumlah','Periode','Status']
};
function keuanganPutriDb_(){return SpreadsheetApp.openById(KEUANGAN_PUTRI_DB_ID);}
function keuanganPutriSheet_(name){const sh=keuanganPutriDb_().getSheetByName(name);if(!sh)throw Error('Tab '+name+' belum dibuat. Jalankan keuanganPutriSetupDatabase().');return sh;}
function keuanganPutriSetupDatabase(){
 const db=keuanganPutriDb_(),out=[];
 Object.keys(KEUANGAN_PUTRI_HEADERS).forEach(name=>{
  let sh=db.getSheetByName(name);
  if(!sh){sh=db.insertSheet(name);sh.getRange(1,1,1,KEUANGAN_PUTRI_HEADERS[name].length).setValues([KEUANGAN_PUTRI_HEADERS[name]]);sh.setFrozenRows(1);out.push('Dibuat '+name);}
  else out.push('Dipertahankan '+name+' ('+sh.getLastRow()+' baris)');
 });
 const p=PropertiesService.getScriptProperties();if(!p.getProperty('KEUANGAN_PUTRI_PIN'))p.setProperty('KEUANGAN_PUTRI_PIN','999999');
 return out;
}
function keuanganPutriRows_(tab){const v=keuanganPutriSheet_(tab).getDataRange().getValues();return v.length>1?v.slice(1):[];}
function keuanganPutriNum_(v){if(typeof v==='number')return v;return Number(String(v||'').replace(/[^0-9-]/g,''))||0;}
function keuanganPutriDate_(v){return v instanceof Date?v:new Date(v);}
function keuanganPutriPeriod_(){return PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PERIODE')||Utilities.formatDate(new Date(),Session.getScriptTimeZone(),'MMMM yyyy');}
function keuanganPutriSpp_(){
 const sh=keuanganPutriDb_().getSheetByName('RiwayatPembayaranSPP');
 if(!sh||sh.getLastRow()<2)return [];
 const v=sh.getDataRange().getValues();return v.slice(1).filter(r=>String(r[7]||'').toUpperCase()==='LUNAS');
}
function keuanganPutriSummary_(){
 const spp=keuanganPutriSpp_(),expenses=keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL');
 const kas=keuanganPutriRows_('KasPondok'),transfer=keuanganPutriRows_('TransferSPPKeKas');
 const sppMasuk=spp.reduce((n,r)=>n+keuanganPutriNum_(r[5]),0);
 const pengeluaranSPP=expenses.reduce((n,r)=>n+keuanganPutriNum_(r[3]),0);
 const totalKasMasuk=kas.filter(r=>String(r[2]).toUpperCase()==='PEMASUKAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
 const totalKasKeluar=kas.filter(r=>String(r[2]).toUpperCase()==='PENGELUARAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
 const transferTotal=transfer.filter(r=>String(r[6]).toUpperCase()==='SELESAI').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
 return {periode:keuanganPutriPeriod_(),sppMasuk,pengeluaranSPP,sisaSPP:sppMasuk-pengeluaranSPP-transferTotal,saldoKas:totalKasMasuk-totalKasKeluar+transferTotal,totalKasMasuk,totalKasKeluar,transferSPP:transferTotal};
}
function keuanganPutriExpenseRows_(){return keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL').map(r=>({id:r[0],tanggal:r[1],keterangan:r[2],jumlah:keuanganPutriNum_(r[3]),sumber:r[4],periode:r[5],catatan:r[6],status:r[7]}));}
function keuanganPutriGet_(action){
 const s=keuanganPutriSummary_();
 if(action==='getDashboardKeuangan')return {success:true,...s};
 if(action==='getKasPondok')return {success:true,saldoKas:s.saldoKas,transaksi:keuanganPutriRows_('KasPondok').map(r=>({id:r[0],tanggal:r[1],jenis:r[2],keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),catatan:r[5]}))};
 if(action==='getTransferSPP')return {success:true,transaksi:keuanganPutriRows_('TransferSPPKeKas').map(r=>({id:r[0],tanggal:r[1],jenis:'transfer',keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),status:r[6]}))};
 if(action==='getRingkasanKeuangan')return {success:true,...s,totalPengeluaranSPP:s.pengeluaranSPP};
 if(action==='getPengeluaranTerbaru')return {success:true,transaksi:keuanganPutriExpenseRows_().reverse().slice(0,100),pengeluaran:keuanganPutriExpenseRows_().reverse().slice(0,100),totalPengeluaranSPP:s.pengeluaranSPP};
 if(action==='getStatusPembayaran'){
  const sh=keuanganPutriDb_().getSheetByName('Santri');if(!sh)return {success:true,totalSantri:0,lunas:0,belum:0,items:[]};
  const rows=sh.getDataRange().getDisplayValues().slice(1).filter(r=>r[0]);
  const ix=KEUANGAN_PUTRI_MONTHS.indexOf(String(s.periode).split(' ')[0]);
  const items=rows.map(r=>({nis:r[0],nama:r[1],status:ix>=0?String(r[7+ix]||'BELUM'):'BELUM'}));
  return {success:true,totalSantri:items.length,lunas:items.filter(x=>x.status.toUpperCase().includes('LUNAS')).length,belum:items.filter(x=>!x.status.toUpperCase().includes('LUNAS')).length,items};
 }
 throw Error('Aksi tidak dikenal: '+action);
}
function keuanganPutriPin_(p){const saved=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PIN');if(!saved||String(p||'')!==saved)throw Error('Sandi bendahara PUTRI salah.');}
function keuanganPutriWrite_(p){
 const action=String(p.action||'');keuanganPutriPin_(p.pin);
 const amount=keuanganPutriNum_(p.jumlah);if(!Number.isSafeInteger(amount)||amount<=0)throw Error('Jumlah harus berupa angka positif.');
 const tanggal=p.tanggal?new Date(p.tanggal):new Date();if(isNaN(tanggal.getTime()))throw Error('Tanggal tidak valid.');
 const keterangan=String(p.keterangan||'').trim();if(!keterangan||keterangan.length>500)throw Error('Keterangan wajib diisi (maksimal 500 karakter).');
 const id=Utilities.getUuid();
 if(action==='catatPengeluaran'){
  keuanganPutriSheet_('PengeluaranSPP').appendRow([id,tanggal,keterangan,amount,String(p.sumber||'SPP'),String(p.periode||keuanganPutriPeriod_()),String(p.catatan||''),'AKTIF']);
  return {success:true,message:'Pengeluaran SPP PUTRI berhasil dicatat.',id};
 }
 if(action==='catatPemasukanKas'){
  keuanganPutriSheet_('KasPondok').appendRow([id,tanggal,'PEMASUKAN',keterangan,amount,String(p.catatan||''),String(p.petugas||'Bendahara PUTRI')]);
  return {success:true,message:'Pemasukan kas PUTRI berhasil dicatat.',id};
 }
 if(action==='pindahkanSisaSPPKeKas'){
  const s=keuanganPutriSummary_();if(s.belum>0)throw Error('Masih ada santri yang belum lunas; transfer ditolak.');
  if(s.sisaSPP<=0)throw Error('Tidak ada sisa SPP yang dapat dipindahkan.');
  keuanganPutriSheet_('TransferSPPKeKas').appendRow([id,new Date(),'TRANSFER','Sisa SPP periode '+s.periode,s.sisaSPP,s.periode,'SELESAI']);
  return {success:true,message:'Sisa SPP PUTRI berhasil dipindahkan ke Kas Pondok.',jumlah:s.sisaSPP};
 }
 throw Error('Aksi tulis tidak dikenal.');
}
function doGet(e){
 try{const p=e&&e.parameter||{},action=String(p.action||'getDashboardKeuangan');
  if(['catatPengeluaran','catatPemasukanKas','pindahkanSisaSPPKeKas'].includes(action))return ContentService.createTextOutput(JSON.stringify(keuanganPutriWrite_(p))).setMimeType(ContentService.MimeType.JSON);
  return ContentService.createTextOutput(JSON.stringify(keuanganPutriGet_(action))).setMimeType(ContentService.MimeType.JSON);
 }catch(err){return ContentService.createTextOutput(JSON.stringify({success:false,message:err.message})).setMimeType(ContentService.MimeType.JSON);}
}
