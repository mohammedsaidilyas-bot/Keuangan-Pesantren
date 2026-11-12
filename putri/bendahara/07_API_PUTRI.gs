function keuanganPutriGet_(action){
  const s=keuanganPutriSummary_();
  if(action==='getDashboardKeuangan') return {success:true,...s};
  if(action==='getKasPondok') return {
    success:true,
    saldoKas:s.totalKasMasuk-s.totalKasKeluar,
    transaksi:keuanganPutriRows_('KasPondok').map(r=>({
      id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:r[2],
      keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),catatan:r[5]
    }))
  };
  if(action==='getTransferSPP') return {
    success:true,
    transaksi:keuanganPutriRows_('TransferSPPKeKas').map(r=>({
      id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:'transfer',
      keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),periode:r[5],status:r[6]
    }))
  };
  if(action==='getRingkasanKeuangan') return {success:true,...s,totalPengeluaranSPP:s.pengeluaranSPP};
  if(action==='getPengeluaranTerbaru'){
    const list=keuanganPutriExpenseList_().reverse();
    return {success:true,transaksi:list.slice(0,200),pengeluaran:list.slice(0,200),totalPengeluaranSPP:s.pengeluaranSPP};
  }
  if(action==='getStatusPembayaran'){
    const students=keuanganPutriActiveStudents_();
    const paid=keuanganPutriPayments_().filter(p=>keuanganPutriIsPaid_(p)&&keuanganPutriPaymentPeriod_(p)===s.periodeKode);
    const paidNis=new Set(paid.map(p=>String(p.nis||'').trim()));
    const items=students.map(st=>({nis:st.nis,nama:st.nama,status:paidNis.has(st.nis)?'Lunas':'Belum Lunas'}));
    return {success:true,totalSantri:items.length,lunas:items.filter(x=>x.status==='Lunas').length,belum:items.filter(x=>x.status!=='Lunas').length,items};
  }
  throw new Error('Aksi tidak dikenal: '+action);
}

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