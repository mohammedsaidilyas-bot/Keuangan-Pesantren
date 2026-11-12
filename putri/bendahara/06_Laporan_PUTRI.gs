function keuanganPutriPeriod_(){
  const manual=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PERIODE');
  return manual || Utilities.formatDate(new Date(),'Asia/Jakarta','yyyy-MM');
}

function keuanganPutriPeriodLabel_(period){
  const m=String(period||'').match(/^(\d{4})-(\d{2})$/);
  return m ? KEUANGAN_PUTRI_MONTHS[Number(m[2])-1]+' '+m[1] : String(period||'');
}

function keuanganPutriSummary_(){
  const period=keuanganPutriPeriod_(), students=keuanganPutriActiveStudents_(), payments=keuanganPutriPayments_();
  const paid=payments.filter(p=>keuanganPutriIsPaid_(p)&&keuanganPutriPaymentPeriod_(p)===period);
  const paidNis=new Set(paid.map(p=>String(p.nis||'').trim()).filter(Boolean));
  const sppMasuk=paid.reduce((n,p)=>n+keuanganPutriNum_(p.nominal),0);
  const expenses=keuanganPutriRows_('PengeluaranSPP').filter(r=>String(r[7]||'AKTIF').toUpperCase()!=='BATAL');
  const pengeluaranSPP=expenses.filter(r=>keuanganPutriExpensePeriod_(r[5])===period).reduce((n,r)=>n+keuanganPutriNum_(r[3]),0);
  const kas=keuanganPutriRows_('KasPondok');
  const transfer=keuanganPutriRows_('TransferSPPKeKas').filter(r=>String(r[6]||'').toUpperCase()==='SELESAI');
  const totalKasMasuk=kas.filter(r=>String(r[2]).toUpperCase()==='PEMASUKAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const totalKasKeluar=kas.filter(r=>String(r[2]).toUpperCase()==='PENGELUARAN').reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const transferTotal=transfer.reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const transferPeriode=transfer.filter(r=>String(r[5]||'')===period).reduce((n,r)=>n+keuanganPutriNum_(r[4]),0);
  const sisaSPP=sppMasuk-pengeluaranSPP-transferPeriode;
  return {periode:keuanganPutriPeriodLabel_(period),periodeKode:period,sppMasuk,pengeluaranSPP,lunas:paidNis.size,belumBayar:Math.max(0,students.length-paidNis.size),totalSantri:students.length,sisaSPP,saldoKas:totalKasMasuk-totalKasKeluar+transferTotal,totalKasMasuk,totalKasKeluar,transferSPP:transferTotal};
}
