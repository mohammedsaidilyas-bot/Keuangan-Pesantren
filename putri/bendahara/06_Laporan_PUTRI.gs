/**
 * LAPORAN KEUANGAN PUTRI — versi mandiri.
 * Seluruh helper laporan diberi prefix khusus agar tidak bergantung
 * pada helper dari file lain yang mungkin belum tersalin.
 * Membaca database PUTRI yang sama; tidak mengubah data santri/pembayaran.
 */

function keuanganPutriPeriod_(){
  const manual=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PERIODE');
  return manual || Utilities.formatDate(new Date(),'Asia/Jakarta','yyyy-MM');
}

function keuanganPutriPeriodLabel_(period){
  const m=String(period||'').match(/^(\d{4})-(\d{2})$/);
  return m ? KEUANGAN_PUTRI_MONTHS[Number(m[2])-1]+' '+m[1] : String(period||'');
}

function laporanPutriDb_(){
  return SpreadsheetApp.openById(KEUANGAN_PUTRI_DB_ID);
}

function laporanPutriRows_(sheetName){
  const sh=laporanPutriDb_().getSheetByName(sheetName);
  if(!sh || sh.getLastRow()<2) return [];
  return sh.getDataRange().getValues().slice(1).filter(r=>r.some(v=>v!==''&&v!==null));
}

function laporanPutriHeaderMap_(sheetName){
  const sh=laporanPutriDb_().getSheetByName(sheetName);
  if(!sh || sh.getLastRow()<1) return {headers:[],rows:[]};
  const values=sh.getDataRange().getValues();
  return {headers:values[0].map(h=>laporanPutriNorm_(h)),rows:values.slice(1)};
}

function laporanPutriNorm_(value){
  return String(value==null?'':value).toLowerCase().replace(/[^a-z0-9]/g,'');
}

function laporanPutriPick_(obj,aliases){
  for(const alias of aliases){
    const key=laporanPutriNorm_(alias);
    if(Object.prototype.hasOwnProperty.call(obj,key)) return obj[key];
  }
  return '';
}

function laporanPutriNum_(value){
  if(typeof value==='number') return value;
  return Number(String(value==null?'':value).replace(/[^0-9.-]/g,''))||0;
}

function laporanPutriPeriodFromValue_(value){
  // Google Sheets may return the periode cell as a Date object.
  if(value instanceof Date && !isNaN(value.getTime())){
    return Utilities.formatDate(value,'Asia/Jakarta','yyyy-MM');
  }
  const raw=String(value==null?'':value).trim();
  if(!raw) return '';

  // Accept yyyy-MM, yyyy-M, MM/yyyy, and MM-yyyy.
  let m=raw.match(/^(\d{4})[-/](\d{1,2})$/);
  if(m) return m[1]+'-'+String(Number(m[2])).padStart(2,'0');
  m=raw.match(/^(\d{1,2})[-/](\d{4})$/);
  if(m) return m[2]+'-'+String(Number(m[1])).padStart(2,'0');

  // Accept date strings such as "Sun Nov 01 2026 00:00:00 GMT+0700".
  const parsed=new Date(raw);
  if(!isNaN(parsed.getTime())){
    return Utilities.formatDate(parsed,'Asia/Jakarta','yyyy-MM');
  }

  // Accept Indonesian month labels, e.g. "November 2026".
  const months=KEUANGAN_PUTRI_MONTHS.map(x=>x.toLowerCase());
  const parts=raw.split(/\s+/);
  const ix=months.indexOf(String(parts[0]||'').toLowerCase());
  if(ix>=0){
    const year=/^\d{4}$/.test(parts[1]||'')?parts[1]:keuanganPutriPeriod_().slice(0,4);
    return year+'-'+String(ix+1).padStart(2,'0');
  }
  return '';
}

function laporanPutriActiveStudents_(){
  const data=laporanPutriHeaderMap_('database putri');
  if(!data.headers.length) return [];
  const result=[];
  data.rows.forEach(row=>{
    const o={}; data.headers.forEach((h,i)=>o[h]=row[i]);
    const nis=String(laporanPutriPick_(o,['nis','nomor induk','nomor induk santri'])||'').trim();
    const status=String(laporanPutriPick_(o,['status','status santri','statussantri'])||'').trim().toLowerCase();
    if(nis && (!status || status==='aktif' || status==='active')) result.push(nis);
  });
  return result;
}

function laporanPutriPaidPayments_(){
  const data=laporanPutriHeaderMap_('SPP_PUTRI_Pembayaran');
  if(!data.headers.length) return [];
  return data.rows.map(row=>{
    const o={}; data.headers.forEach((h,i)=>o[h]=row[i]);
    return {
      nis:String(laporanPutriPick_(o,['nis','nomor induk'])||'').trim(),
      status:String(laporanPutriPick_(o,['status'])||'').trim().toLowerCase(),
      periode:laporanPutriPeriodFromValue_(laporanPutriPick_(o,['periode','period'])),
      nominal:laporanPutriNum_(laporanPutriPick_(o,['nominal','jumlah','jumlah bayar','jumlah pembayaran','total']))
    };
  }).filter(p=>p.status==='lunas');
}

/**
 * Tagihan adalah sumber status lunas per santri/periode.
 * Satu pembayaran dapat mencakup beberapa bulan; sheet pembayaran
 * menyimpan periode gabungan, sedangkan setiap baris tagihan menyimpan
 * satu periode dan status yang diperbarui saat verifikasi.
 */
function laporanPutriPaidBills_(period){
  const data=laporanPutriHeaderMap_('SPP_PUTRI_Tagihan');
  if(!data.headers.length) return [];
  return data.rows.map(row=>{
    const o={}; data.headers.forEach((h,i)=>o[h]=row[i]);
    return {
      nis:String(laporanPutriPick_(o,['nis','nomor induk','nomor induk santri'])||'').replace(/\\D/g,''),
      nama:String(laporanPutriPick_(o,['nama','nama santri'])||''),
      kelas:String(laporanPutriPick_(o,['kelas','rombel'])||''),
      status:String(laporanPutriPick_(o,['status'])||'').trim().toLowerCase(),
      periode:laporanPutriPeriodFromValue_(laporanPutriPick_(o,['periode','period'])),
      nominal:laporanPutriNum_(laporanPutriPick_(o,['nominal','jumlah','jumlah bayar','jumlah pembayaran','total']))
    };
  }).filter(b=>b.status==='lunas' && b.nis && (!period || b.periode===period));
}

function keuanganPutriSummary_(){
  const period=keuanganPutriPeriod_();
  const students=laporanPutriActiveStudents_();
  const paidBills=laporanPutriPaidBills_(period);
  const paidNis=new Set(paidBills.map(b=>b.nis));
  // Jumlahkan tagihan lunas satu per satu agar pembayaran multi-bulan
  // tidak hilang akibat kolom periode gabungan pada sheet Pembayaran.
  const sppMasuk=paidBills.reduce((sum,b)=>sum+b.nominal,0);

  const expenseRows=laporanPutriRows_('PengeluaranSPP')
    .filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL');
  const pengeluaranSPP=expenseRows
    .filter(r=>laporanPutriPeriodFromValue_(r[5])===period)
    .reduce((sum,r)=>sum+laporanPutriNum_(r[3]),0);

  const cashRows=laporanPutriRows_('KasPondok');
  const totalKasMasuk=cashRows
    .filter(r=>String(r[2]||'').toUpperCase()==='PEMASUKAN')
    .reduce((sum,r)=>sum+laporanPutriNum_(r[4]),0);
  const totalKasKeluar=cashRows
    .filter(r=>String(r[2]||'').toUpperCase()==='PENGELUARAN')
    .reduce((sum,r)=>sum+laporanPutriNum_(r[4]),0);

  const transferRows=laporanPutriRows_('TransferSPPKeKas')
    .filter(r=>String(r[6]||'').toUpperCase()==='SELESAI');
  const transferTotal=transferRows.reduce((sum,r)=>sum+laporanPutriNum_(r[4]),0);
  const transferPeriode=transferRows
    .filter(r=>laporanPutriPeriodFromValue_(r[5])===period)
    .reduce((sum,r)=>sum+laporanPutriNum_(r[4]),0);

  const sisaSPP=sppMasuk-pengeluaranSPP-transferPeriode;
  return {
    periode:keuanganPutriPeriodLabel_(period),
    periodeKode:period,
    sppMasuk,
    pengeluaranSPP,
    lunas:paidNis.size,
    belumBayar:Math.max(0,students.length-paidNis.size),
    totalSantri:students.length,
    sisaSPP,
    saldoKas:totalKasMasuk-totalKasKeluar+transferTotal,
    totalKasMasuk,
    totalKasKeluar,
    transferSPP:transferTotal
  };
}
