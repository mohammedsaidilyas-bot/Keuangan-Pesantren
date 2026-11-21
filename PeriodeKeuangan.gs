/**
 * MODUL PERIODE KEUANGAN PESANTREN
 * File terpisah: PeriodeKeuangan.gs
 * MODE READ-ONLY: tidak menghapus/mengubah transaksi atau saldo.
 */

var PK_CONFIG = { HEADER_ROW: 1 };

function PK_formatMonthKey(date) {
  if (!(date instanceof Date) || isNaN(date.getTime())) return "";
  return Utilities.formatDate(date, Session.getScriptTimeZone(), "yyyy-MM");
}

function PK_parseDate(value) {
  if (value instanceof Date && !isNaN(value.getTime())) return value;
  if (!value) return null;
  var text = String(value).trim();
  var iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
  if (iso) return new Date(Number(iso[1]), Number(iso[2])-1, Number(iso[3]));
  var indo = /^(\d{1,2})[\/.-](\d{1,2})[\/.-](\d{4})$/.exec(text);
  if (indo) return new Date(Number(indo[3]), Number(indo[2])-1, Number(indo[1]));
  var d = new Date(value);
  return isNaN(d.getTime()) ? null : d;
}

function PK_readSheet(sheetName) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) throw new Error("Sheet tidak ditemukan: " + sheetName);
  var lastRow = sheet.getLastRow(), lastCol = sheet.getLastColumn();
  if (lastRow <= PK_CONFIG.HEADER_ROW || lastCol === 0) return {headers:[],rows:[]};
  var data = sheet.getRange(PK_CONFIG.HEADER_ROW,1,lastRow-PK_CONFIG.HEADER_ROW+1,lastCol).getValues();
  var headers = data.shift();
  return {headers:headers,rows:data};
}

function PK_findDateColumn(headers) {
  var candidates = ["tanggal","date","tgl"];
  for (var i=0;i<headers.length;i++) {
    if (candidates.indexOf(String(headers[i]).toLowerCase().trim())!==-1) return i;
  }
  return -1;
}

function PK_rowToObject(headers,row) {
  var obj={};
  for(var i=0;i<headers.length;i++) obj[String(headers[i])]=row[i];
  return obj;
}

function PK_getTransactionsByMonth(sheetName,monthKey) {
  var result=PK_readSheet(sheetName), dateCol=PK_findDateColumn(result.headers);
  if(dateCol===-1) throw new Error("Kolom tanggal tidak ditemukan di sheet "+sheetName);
  var output=[];
  result.rows.forEach(function(row,index){
    var date=PK_parseDate(row[dateCol]); if(!date)return;
    if(PK_formatMonthKey(date)===monthKey)
      output.push({rowNumber:index+PK_CONFIG.HEADER_ROW+1,data:PK_rowToObject(result.headers,row)});
  });
  return output;
}

function PK_getAvailableMonths(sheetName) {
  var result=PK_readSheet(sheetName),dateCol=PK_findDateColumn(result.headers);
  if(dateCol===-1) throw new Error("Kolom tanggal tidak ditemukan di sheet "+sheetName);
  var months={};
  result.rows.forEach(function(row){var d=PK_parseDate(row[dateCol]);if(d)months[PK_formatMonthKey(d)]=true;});
  months[PK_formatMonthKey(new Date())]=true;
  return Object.keys(months).sort().reverse();
}

function PK_getPeriodCount(sheetName,monthKey) {
  return {sheet:sheetName,periode:monthKey,jumlahTransaksi:PK_getTransactionsByMonth(sheetName,monthKey).length};
}

function PK_getArchive(sheetName,monthKey) {
  return {periode:monthKey,transaksi:PK_getTransactionsByMonth(sheetName,monthKey),tersedia:PK_getAvailableMonths(sheetName)};
}

function PK_diagnosa(sheetName) {
  var result=PK_readSheet(sheetName);
  return {namaSheet:sheetName,jumlahKolom:result.headers.length,headers:result.headers,
    jumlahBaris:result.rows.length,kolomTanggal:PK_findDateColumn(result.headers),
    periodeTersedia:PK_getAvailableMonths(sheetName)};
}
