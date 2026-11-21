/* 
 * PeriodeKeuangan.js — modul tambahan untuk filter periode Bendahara.
 * SAFE MODE: file ini tidak dipanggil/diintegrasikan ke index.html secara otomatis.
 * Tidak menghapus, mengubah, atau menulis data transaksi maupun saldo.
 * Integrasi ke dashboard harus dilakukan setelah struktur API dan UI diuji.
 */
(function(global){
  "use strict";
  const MONTHS=["Januari","Februari","Maret","April","Mei","Juni","Juli","Agustus","September","Oktober","November","Desember"];
  function monthKey(date){
    const d=date instanceof Date?date:new Date(date);
    if(!Number.isFinite(d.getTime())) return "";
    return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0");
  }
  function activeMonth(now){ return monthKey(now||new Date()); }
  function monthLabel(key){
    const m=/^(\d{4})-(\d{2})$/.exec(String(key||""));
    return m?MONTHS[Number(m[2])-1]+" "+m[1]:"";
  }
  function transactionMonth(tx){
    const dateVal=tx && (tx.tanggal||tx.Tanggal||tx.date||tx.Date);
    if(!dateVal) return "";
    // ISO date (yyyy-mm-dd), commonly used by the finance sheet.
    const iso=/^(\d{4})-(\d{2})/.exec(String(dateVal).trim());
    if(iso) return iso[1]+"-"+iso[2];
    // Indonesian date forms, e.g. 22/11/2026 or 22-11-2026.
    const id=/^(\d{1,2})[\/.-](\d{1,2})[\/.-](\d{4})$/.exec(String(dateVal).trim());
    if(id) return id[3]+"-"+String(id[2]).padStart(2,"0");
    const d=new Date(dateVal); return monthKey(d);
  }
  function filterTransactions(tx,selectedMonth){
    const list=Array.isArray(tx)?tx:[];
    return list.filter(row=>transactionMonth(row)===selectedMonth);
  }
  function availableMonths(tx,now){
    const set=new Set([activeMonth(now)]);
    (Array.isArray(tx)?tx:[]).forEach(row=>{const key=transactionMonth(row);if(key)set.add(key);});
    return Array.from(set).sort().reverse().map(key=>({key,label:monthLabel(key),active:key===activeMonth(now)}));
  }
  function mountSelector(container,tx,options){
    if(!container) return null;
    const opts=options||{}, current=opts.selectedMonth||activeMonth();
    const months=availableMonths(tx,opts.now);
    container.innerHTML='<label style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-weight:700;color:#334155">📅 Periode <select id="periodeKeuanganSelector" style="border:1px solid #cbd5e1;border-radius:10px;padding:10px;background:#fff;font-weight:600">'+months.map(m=>'<option value="'+m.key+'" '+(m.key===current?'selected':'')+'>'+m.label+(m.active?' • Aktif':' • Arsip')+'</option>').join("")+'</select></label>';
    const el=container.querySelector("#periodeKeuanganSelector");
    if(el && typeof opts.onChange==="function")el.addEventListener("change",e=>opts.onChange(e.target.value));
    return el;
  }
  global.PeriodeKeuangan={
    activeMonth,monthLabel,transactionMonth,filterTransactions,availableMonths,mountSelector,
    // For testing only: describe selected period; does not mutate or persist anything.
    periodInfo(selectedMonth,now){const active=activeMonth(now);return {selectedMonth,label:monthLabel(selectedMonth),isActive:selectedMonth===active,isArchive:selectedMonth<active};}
  };
})(window);
