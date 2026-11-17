function keuanganPutriGet_(action,requestedPeriod){
  if(action==='getDashboardKeuangan') return {success:true,...keuanganPutriSummary_(requestedPeriod)};

  if(action==='getKasPondok'){
    const s=keuanganPutriSummary_(requestedPeriod);
    const period=keuanganPutriPeriod_(requestedPeriod);
    const rows=keuanganPutriRows_('KasPondok');
    const periodRows=rows.filter(r=>laporanPutriPeriodFromValue_(r[1])===period);
    return {
      success:true,
      periodeKode:period,
      saldoKas:s.saldoKas,
      pemasukanKas:s.totalKasMasuk,
      pengeluaranKas:s.totalKasKeluar,
      transaksi:periodRows.map(r=>({
        id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:r[2],
        keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),catatan:r[5]
      }))
    };
  }

  if(action==='getTransferSPP'){
    return {
      success:true,
      transaksi:keuanganPutriRows_('TransferSPPKeKas').map(r=>({
        id:r[0],tanggal:keuanganPutriDateText_(r[1]),jenis:'transfer',
        keterangan:r[3],jumlah:keuanganPutriNum_(r[4]),periode:r[5],status:r[6]
      }))
    };
  }

  if(action==='getRingkasanKeuangan') {
    const s=keuanganPutriSummary_(requestedPeriod);
    return {success:true,...s,totalPengeluaranSPP:s.pengeluaranSPP};
  }

  if(action==='getPengeluaranTerbaru'){
    const list=keuanganPutriExpenseList_().reverse().map(t=>({
      ...t,
      jenis:'pengeluaran',
      tipe:'pengeluaran',
      nominal:t.jumlah
    }));
    return {
      success:true,
      transaksi:list.slice(0,200),
      pengeluaran:list.slice(0,200),
      data:list.slice(0,200),
      totalPengeluaranSPP:0
    };
  }

  if(action==='getStatusPembayaran'){
    const s=keuanganPutriSummary_(requestedPeriod);
    const students=keuanganPutriActiveStudents_();
    // Ambil status dari tagihan per periode, bukan periode gabungan di sheet Pembayaran.
    const paidNis=new Set(laporanPutriPaidBills_(s.periodeKode).map(x=>String(x.nis||'').replace(/\\D/g,'')));
    // Lengkapi data dari master database PUTRI, termasuk kolom Kelas (kolom C).
    const detailByNis=laporanPutriStudentDetails_();
    const items=students.map(st=>{
      const nis=String(st.nis||'').replace(/\\D/g,'');
      const detail=detailByNis[nis]||{};
      return {
        nis:st.nis,
        nama:st.nama||detail.nama||'',
        kelas:detail.kelas||st.kelas||'',
        status:paidNis.has(nis)?'Lunas':'Belum Lunas'
      };
    });
    const lunas=items.filter(x=>x.status==='Lunas').length;
    const belum=items.length-lunas;
    return {
      success:true,
      periode:s.periode,
      periodeKode:s.periodeKode,
      totalSantri:items.length,
      lunas,
      belum,
      items,
      data:items
    };
  }

  if(action==='getRiwayatPembayaran'){
    const transaksi=keuanganPutriPayments_().map(p=>({
      idPembayaran:String(p.idPembayaran||''),
      tanggalAjukan:keuanganPutriDateText_(p.tanggalAjukan),
      tanggalVerifikasi:keuanganPutriDateText_(p.tanggalVerifikasi),
      nis:String(p.nis||''),
      nama:String(p.nama||''),
      kelas:String(p.kelas||''),
      periode:keuanganPutriDateText_(p.periode),
      nominal:keuanganPutriNum_(p.nominal),
      metode:String(p.metode||''),
      status:String(p.status||''),
      catatan:String(p.catatan||''),
      diverifikasiOleh:String(p.diverifikasiOleh||'')
    })).sort((a,b)=>String(b.tanggalVerifikasi||b.tanggalAjukan).localeCompare(String(a.tanggalVerifikasi||a.tanggalAjukan)));
    return {success:true,total:transaksi.length,transaksi};
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
      ?keuanganPutriWrite_(p):keuanganPutriGet_(action,p.periode);
    return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
  }catch(err){
    return ContentService.createTextOutput(JSON.stringify({success:false,message:err.message})).setMimeType(ContentService.MimeType.JSON);
  }
}