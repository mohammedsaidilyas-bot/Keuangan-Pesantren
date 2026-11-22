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
      action !== "PK_getArchive") {
    return ContentService.createTextOutput(JSON.stringify({
      status: "debug",
      actionDiterima: action,
      parameter: (e && e.parameter) ? e.parameter : null
    })).setMimeType(ContentService.MimeType.JSON);
  }
  try {
    var sheetName = String((e.parameter.sheet || "keuangan"));
    var monthKey = String(e.parameter.month || "");
    var payload;
    if (action === "PK_getAvailableMonths") {
      payload = {success:true, months:PK_getAvailableMonths(sheetName)};
    } else if (action === "PK_getTransactionsByMonth") {
      if (!/^\d{4}-\d{2}$/.test(monthKey)) throw new Error("Parameter month harus yyyy-MM");
      payload = {success:true, month:monthKey, transactions:PK_getTransactionsByMonth(sheetName,monthKey)};
    } else {
      if (!/^\d{4}-\d{2}$/.test(monthKey)) throw new Error("Parameter month harus yyyy-MM");
      payload = {success:true, archive:PK_getArchive(sheetName,monthKey)};
    }
    return ContentService.createTextOutput(JSON.stringify(payload)).setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({success:false,message:String(err.message||err)})).setMimeType(ContentService.MimeType.JSON);
  }
}


/** TES INTERNAL: memanggil handler langsung tanpa melalui doGet utama */
function PK_TEST_API_INTERNAL() {
  var e = { parameter: { action: "PK_getAvailableMonths", sheet: "keuangan" } };
  try {
    var result = PK_API_handle(e);
    if (!result) return "GAGAL: PK_API_handle mengembalikan null/false";
    var content = result.getContent();
    Logger.log("Respons handler: " + content);
    return content;
  } catch (err) {
    var message = "ERROR INTERNAL: " + String(err.message || err);
    Logger.log(message);
    return message;
  }
}
