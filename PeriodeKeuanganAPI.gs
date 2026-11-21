/**
 * ROUTE API MODUL PERIODE KEUANGAN
 * Tambahan aman; tidak menggantikan doGet utama.
 * Integrasi: di doGet(e) utama, sebelum router action lain, tambahkan:
 *   var pk = PK_API_handle(e);
 *   if (pk) return pk;
 */

function PK_API_handle(e) {
  var action = String((e && e.parameter && e.parameter.action) || "");
  if (action !== "PK_getAvailableMonths" &&
      action !== "PK_getTransactionsByMonth" &&
      action !== "PK_getArchive") return null;

  try {
    var sheetName = String((e.parameter.sheet || "keuangan"));
    var monthKey = String(e.parameter.month || "");
    var payload;

    if (action === "PK_getAvailableMonths") {
      payload = {success:true, months:PK_getAvailableMonths(sheetName)};
    } else if (action === "PK_getTransactionsByMonth") {
      if (!/^\\d{4}-\\d{2}$/.test(monthKey)) throw new Error("Parameter month harus yyyy-MM");
      payload = {success:true, month:monthKey, transactions:PK_getTransactionsByMonth(sheetName,monthKey)};
    } else {
      if (!/^\\d{4}-\\d{2}$/.test(monthKey)) throw new Error("Parameter month harus yyyy-MM");
      payload = {success:true, archive:PK_getArchive(sheetName,monthKey)};
    }
    return ContentService.createTextOutput(JSON.stringify(payload))
      .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({success:false,message:String(err.message||err)}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
