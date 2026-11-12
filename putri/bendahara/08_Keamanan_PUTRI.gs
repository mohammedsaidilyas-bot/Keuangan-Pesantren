function keuanganPutriPin_(pin){
  const saved=PropertiesService.getScriptProperties().getProperty('KEUANGAN_PUTRI_PIN')||'999999';
  if(String(pin||'')!==saved) throw new Error('Sandi Bendahara PUTRI salah.');
}
