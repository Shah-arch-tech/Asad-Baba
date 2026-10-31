package com.shahpharmacy.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.shahpharmacy.app.model.CashSettingEntity
import com.shahpharmacy.app.model.DailySaleEntity
import com.shahpharmacy.app.model.ExpenseEntity
import com.shahpharmacy.app.model.PurchaseEntity
import com.shahpharmacy.app.model.StockItem
import com.shahpharmacy.app.model.TimeframeTotals
import com.shahpharmacy.app.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class PharmacyRepository(private val context: Context) {
    private val db = PharmacyDatabase(context)

    private val _allUsers = MutableStateFlow<List<UserEntity>>(emptyList())
    val allUsers: StateFlow<List<UserEntity>> = _allUsers.asStateFlow()

    private val _allPurchases = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    val allPurchases: StateFlow<List<PurchaseEntity>> = _allPurchases.asStateFlow()

    private val _allSales = MutableStateFlow<List<DailySaleEntity>>(emptyList())
    val allSales: StateFlow<List<DailySaleEntity>> = _allSales.asStateFlow()

    private val _allExpenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val allExpenses: StateFlow<List<ExpenseEntity>> = _allExpenses.asStateFlow()

    private val _cashSetting = MutableStateFlow<CashSettingEntity?>(null)
    val cashSetting: StateFlow<CashSettingEntity?> = _cashSetting.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val existingAdmin = db.getUserByUsername("admin")
        if (existingAdmin == null) {
            db.insertUser(UserEntity("admin", "1234", "admin"))
            db.insertUser(UserEntity("staff", "1234", "staff"))
        }

        val setting = db.getCashSetting()
        if (setting == null) {
            db.setCashSetting(CashSettingEntity(1, 0.0))
        }

        tryMigrateLegacyDb()
        refreshAll()
    }

    private fun refreshAll() {
        _allUsers.value = db.getAllUsers()
        _allPurchases.value = db.getAllPurchases()
        _allSales.value = db.getAllSales()
        _allExpenses.value = db.getAllExpenses()
        _cashSetting.value = db.getCashSetting() ?: CashSettingEntity(1, 0.0)
    }

    private suspend fun tryMigrateLegacyDb() {
        val legacyDbFile = context.getDatabasePath("shah_pharmacy.db")
        if (legacyDbFile.exists()) {
            try {
                val legacyDb = SQLiteDatabase.openDatabase(
                    legacyDbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                val authCursor = legacyDb.rawQuery("SELECT json FROM auth_data WHERE id=1", null)
                if (authCursor.moveToFirst()) {
                    val rawAuth = authCursor.getString(0)
                    val authObj = JSONObject(rawAuth)
                    val usersArr = authObj.optJSONArray("users")
                    if (usersArr != null) {
                        for (i in 0 until usersArr.length()) {
                            val u = usersArr.getJSONObject(i)
                            val uname = u.optString("username")
                            val pwd = u.optString("password", "1234")
                            val role = u.optString("role", "staff")
                            if (uname.isNotBlank()) {
                                db.insertUser(UserEntity(uname, pwd, role))
                            }
                        }
                    }
                }
                authCursor.close()

                val appCursor = legacyDb.rawQuery("SELECT json FROM app_data WHERE id=1", null)
                if (appCursor.moveToFirst()) {
                    val rawData = appCursor.getString(0)
                    importJsonBackup(rawData)
                }
                appCursor.close()
                legacyDb.close()
            } catch (ignored: Exception) {
            }
        }
    }

    suspend fun authenticate(username: String, pass: String): UserEntity? = withContext(Dispatchers.IO) {
        val user = db.getUserByUsername(username.trim())
        if (user != null && user.password == pass) {
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val updated = user.copy(lastLogin = nowStr)
            db.updateUser(updated)
            refreshAll()
            return@withContext updated
        }
        null
    }

    suspend fun changePassword(username: String, newPass: String) = withContext(Dispatchers.IO) {
        val user = db.getUserByUsername(username)
        if (user != null) {
            db.updateUser(user.copy(password = newPass))
            refreshAll()
        }
    }

    suspend fun addStaff(username: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = username.trim()
        if (trimmed.isEmpty() || pass.length < 4) return@withContext false
        if (db.getUserByUsername(trimmed) != null) return@withContext false
        db.insertUser(UserEntity(trimmed, pass, "staff"))
        refreshAll()
        true
    }

    suspend fun deleteUser(user: UserEntity) = withContext(Dispatchers.IO) {
        if (user.username != "admin") {
            db.deleteUser(user)
            refreshAll()
        }
    }

    suspend fun addPurchase(purchase: PurchaseEntity) = withContext(Dispatchers.IO) {
        db.insertPurchase(purchase)
        refreshAll()
    }

    suspend fun deletePurchase(purchase: PurchaseEntity) = withContext(Dispatchers.IO) {
        db.deletePurchase(purchase.id)
        refreshAll()
    }

    suspend fun saveOrUpdateSale(date: String, amount: Double) = withContext(Dispatchers.IO) {
        val existing = db.getSaleByDate(date)
        if (existing != null) {
            db.insertOrUpdateSale(existing.copy(amount = amount))
        } else {
            db.insertOrUpdateSale(DailySaleEntity(date = date, amount = amount))
        }
        refreshAll()
    }

    suspend fun deleteSale(sale: DailySaleEntity) = withContext(Dispatchers.IO) {
        db.deleteSale(sale.id)
        refreshAll()
    }

    suspend fun addExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        db.insertExpense(expense)
        refreshAll()
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        db.deleteExpense(expense.id)
        refreshAll()
    }

    suspend fun updateOpeningCash(amount: Double) = withContext(Dispatchers.IO) {
        db.setCashSetting(CashSettingEntity(1, amount))
        refreshAll()
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.clearAllPurchases()
        db.clearAllSales()
        db.clearAllExpenses()
        db.setCashSetting(CashSettingEntity(1, 0.0))
        refreshAll()
    }

    fun calculateStock(purchases: List<PurchaseEntity>): List<StockItem> {
        val map = mutableMapOf<String, MutableStock>()
        for (p in purchases) {
            val medName = p.name.trim()
            if (medName.isEmpty()) continue
            val shelfName = p.shelf.trim().ifBlank { "General" }
            val key = "${medName.lowercase(Locale.ROOT)}__${shelfName.lowercase(Locale.ROOT)}"
            val item = map.getOrPut(key) {
                MutableStock(medName, p.company.trim(), 0, p.purchase, p.sale, shelfName)
            }
            item.qty += p.qty
            if (p.purchase > 0) item.purchase = p.purchase
            if (p.sale > 0) item.sale = p.sale
            if (item.company.isEmpty() && p.company.isNotBlank()) {
                item.company = p.company.trim()
            }
        }
        return map.values.map {
            StockItem(
                name = it.name,
                company = it.company,
                qty = it.qty,
                purchase = it.purchase,
                sale = it.sale,
                purchaseValue = it.qty * it.purchase,
                saleValue = it.qty * it.sale,
                shelf = it.shelf
            )
        }.sortedWith(compareBy({ it.shelf.lowercase(Locale.ROOT) }, { it.name.lowercase(Locale.ROOT) }))
    }

    fun calculateTotals(
        sales: List<DailySaleEntity>,
        purchases: List<PurchaseEntity>,
        expenses: List<ExpenseEntity>,
        startDate: String? = null,
        endDate: String? = null
    ): TimeframeTotals {
        fun inRange(date: String): Boolean {
            if (startDate != null && date < startDate) return false
            if (endDate != null && date > endDate) return false
            return true
        }

        val saleSum = sales.filter { inRange(it.date) }.sumOf { it.amount }
        val purchaseSum = purchases.filter { inRange(it.date) }.sumOf { it.total }
        val expenseSum = expenses.filter { inRange(it.date) }.sumOf { it.amount }

        val stock = calculateStock(purchases)
        val totalCost = stock.sumOf { it.purchase * it.qty }
        val weightedSale = stock.sumOf {
            val r = if (it.purchase > 0) it.sale / it.purchase else 1.0
            r * it.purchase * it.qty
        }
        val ratio = if (totalCost > 0) weightedSale / totalCost else 1.0
        val safeRatio = max(1.0, ratio)
        val estimatedProfit = saleSum - (saleSum / safeRatio) - expenseSum

        return TimeframeTotals(
            sale = saleSum,
            purchase = purchaseSum,
            expense = expenseSum,
            estimatedProfit = estimatedProfit
        )
    }

    fun parsePurchaseOrdersFromText(content: String): List<PurchaseEntity> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val result = mutableListOf<PurchaseEntity>()

        // 1. Check if JSON format
        if (trimmed.startsWith("[") || (trimmed.startsWith("{") && trimmed.contains("\"purchases\""))) {
            try {
                val array = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONObject(trimmed).optJSONArray("purchases") ?: JSONArray()
                }
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").ifBlank {
                        obj.optString("medicine", "").ifBlank {
                            obj.optString("item", "")
                        }
                    }
                    if (name.isBlank()) continue
                    val company = obj.optString("company", obj.optString("manufacturer", ""))
                    val qty = obj.optInt("qty", obj.optInt("quantity", 0))
                    val purchase = obj.optDouble("purchase", obj.optDouble("purchasePrice", obj.optDouble("buy", 0.0)))
                    val sale = obj.optDouble("sale", obj.optDouble("salePrice", obj.optDouble("mrp", 0.0)))
                    val date = obj.optString("date", todayStr).ifBlank { todayStr }
                    val batch = obj.optString("batch", "")
                    val expiry = obj.optString("expiry", obj.optString("exp", ""))
                    val shelf = obj.optString("shelf", obj.optString("rack", obj.optString("location", "General"))).ifBlank { "General" }
                    val total = if (obj.has("total")) obj.optDouble("total", qty * purchase) else qty * purchase

                    if (qty > 0) {
                        result.add(
                            PurchaseEntity(
                                name = name.trim(),
                                company = company.trim(),
                                qty = qty,
                                purchase = purchase,
                                sale = sale,
                                date = date.trim(),
                                batch = batch.trim(),
                                expiry = expiry.trim(),
                                total = total,
                                shelf = shelf.trim().ifBlank { "General" }
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {
            }
        }

        // 2. CSV / TSV / Delimited
        val lines = trimmed.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headerLine = lines.first()
        val delimiter = when {
            headerLine.contains("\t") -> "\t"
            headerLine.contains(";") -> ";"
            else -> ","
        }

        val headerCols = headerLine.split(delimiter).map { it.trim().lowercase(Locale.ROOT) }
        val nameIdx = headerCols.indexOfFirst { it in listOf("name", "medicine", "medication", "item", "product") }
        val compIdx = headerCols.indexOfFirst { it in listOf("company", "manufacturer", "mfg", "brand") }
        val qtyIdx = headerCols.indexOfFirst { it in listOf("qty", "quantity", "units", "count") }
        val purIdx = headerCols.indexOfFirst { it in listOf("purchase", "purchase price", "buy", "buy price", "cost", "cost price") }
        val saleIdx = headerCols.indexOfFirst { it in listOf("sale", "sale price", "mrp", "retail", "price") }
        val dateIdx = headerCols.indexOfFirst { it in listOf("date", "purchase date", "inv date") }
        val batchIdx = headerCols.indexOfFirst { it in listOf("batch", "batch no", "batch #", "lot") }
        val expIdx = headerCols.indexOfFirst { it in listOf("expiry", "exp", "exp date", "expiry date") }
        val shelfIdx = headerCols.indexOfFirst { it in listOf("shelf", "rack", "location", "shelf no", "shelf #", "bin") }

        val startIndex = if (nameIdx >= 0) 1 else 0

        for (i in startIndex until lines.size) {
            val cols = lines[i].split(delimiter).map { it.trim().removeSurrounding("\"") }
            if (cols.isEmpty()) continue

            val name = if (nameIdx >= 0 && nameIdx < cols.size) cols[nameIdx] else if (startIndex == 0 && cols.isNotEmpty()) cols[0] else ""
            if (name.isBlank()) continue

            val company = if (compIdx in cols.indices) cols[compIdx] else ""
            val qty = if (qtyIdx in cols.indices) cols[qtyIdx].toIntOrNull() ?: 0 else if (startIndex == 0 && cols.size > 2) cols[2].toIntOrNull() ?: 0 else 0
            val purchase = if (purIdx in cols.indices) cols[purIdx].toDoubleOrNull() ?: 0.0 else if (startIndex == 0 && cols.size > 3) cols[3].toDoubleOrNull() ?: 0.0 else 0.0
            val sale = if (saleIdx in cols.indices) cols[saleIdx].toDoubleOrNull() ?: 0.0 else if (startIndex == 0 && cols.size > 4) cols[4].toDoubleOrNull() ?: 0.0 else 0.0
            val date = if (dateIdx in cols.indices && cols[dateIdx].isNotBlank()) cols[dateIdx] else todayStr
            val batch = if (batchIdx in cols.indices) cols[batchIdx] else ""
            val expiry = if (expIdx in cols.indices) cols[expIdx] else ""
            val shelf = if (shelfIdx in cols.indices && cols[shelfIdx].isNotBlank()) cols[shelfIdx] else "General"

            if (qty > 0) {
                result.add(
                    PurchaseEntity(
                        name = name.trim(),
                        company = company.trim(),
                        qty = qty,
                        purchase = purchase,
                        sale = sale,
                        date = date.trim(),
                        batch = batch.trim(),
                        expiry = expiry.trim(),
                        total = qty * purchase,
                        shelf = shelf.trim().ifBlank { "General" }
                    )
                )
            }
        }

        return result
    }

    suspend fun importPurchasesList(purchases: List<PurchaseEntity>): Int = withContext(Dispatchers.IO) {
        if (purchases.isEmpty()) return@withContext 0
        db.insertPurchases(purchases)
        refreshAll()
        purchases.size
    }

    suspend fun importCsvText(csvContent: String): Int = withContext(Dispatchers.IO) {
        val list = parsePurchaseOrdersFromText(csvContent)
        importPurchasesList(list)
    }

    suspend fun exportJsonBackup(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val pList = db.getAllPurchases()
        val sList = db.getAllSales()
        val eList = db.getAllExpenses()
        val cash = db.getCashSetting()?.openingCash ?: 0.0

        val pArr = JSONArray()
        for (p in pList) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("company", p.company)
            obj.put("qty", p.qty)
            obj.put("purchase", p.purchase)
            obj.put("sale", p.sale)
            obj.put("date", p.date)
            obj.put("batch", p.batch)
            obj.put("expiry", p.expiry)
            obj.put("total", p.total)
            obj.put("shelf", p.shelf)
            pArr.put(obj)
        }

        val sArr = JSONArray()
        for (s in sList) {
            val obj = JSONObject()
            obj.put("date", s.date)
            obj.put("amount", s.amount)
            sArr.put(obj)
        }

        val eArr = JSONArray()
        for (e in eList) {
            val obj = JSONObject()
            obj.put("date", e.date)
            obj.put("category", e.category)
            obj.put("amount", e.amount)
            obj.put("note", e.note)
            eArr.put(obj)
        }

        root.put("purchases", pArr)
        root.put("sales", sArr)
        root.put("expenses", eArr)
        root.put("openingCash", cash)
        root.toString(2)
    }

    suspend fun importJsonBackup(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val pArr = root.optJSONArray("purchases")
            val sArr = root.optJSONArray("sales")
            val eArr = root.optJSONArray("expenses")
            val openingCash = root.optDouble("openingCash", 0.0)

            val pList = mutableListOf<PurchaseEntity>()
            if (pArr != null) {
                for (i in 0 until pArr.length()) {
                    val o = pArr.getJSONObject(i)
                    val q = o.optInt("qty", 0)
                    val p = o.optDouble("purchase", 0.0)
                    pList.add(
                        PurchaseEntity(
                            name = o.optString("name"),
                            company = o.optString("company", ""),
                            qty = q,
                            purchase = p,
                            sale = o.optDouble("sale", 0.0),
                            date = o.optString("date", ""),
                            batch = o.optString("batch", ""),
                            expiry = o.optString("expiry", ""),
                            total = o.optDouble("total", q * p),
                            shelf = o.optString("shelf", "General").ifBlank { "General" }
                        )
                    )
                }
            }

            val sList = mutableListOf<DailySaleEntity>()
            if (sArr != null) {
                for (i in 0 until sArr.length()) {
                    val o = sArr.getJSONObject(i)
                    sList.add(
                        DailySaleEntity(
                            date = o.optString("date"),
                            amount = o.optDouble("amount", 0.0)
                        )
                    )
                }
            }

            val eList = mutableListOf<ExpenseEntity>()
            if (eArr != null) {
                for (i in 0 until eArr.length()) {
                    val o = eArr.getJSONObject(i)
                    eList.add(
                        ExpenseEntity(
                            date = o.optString("date"),
                            category = o.optString("category", "Other"),
                            amount = o.optDouble("amount", 0.0),
                            note = o.optString("note", "")
                        )
                    )
                }
            }

            db.clearAllPurchases()
            db.clearAllSales()
            db.clearAllExpenses()

            if (pList.isNotEmpty()) db.insertPurchases(pList)
            if (sList.isNotEmpty()) db.insertSales(sList)
            if (eList.isNotEmpty()) db.insertExpenses(eList)
            db.setCashSetting(CashSettingEntity(1, openingCash))
            refreshAll()
            true
        } catch (e: Exception) {
            false
        }
    }

    private data class MutableStock(
        val name: String,
        var company: String,
        var qty: Int,
        var purchase: Double,
        var sale: Double,
        var shelf: String = "General"
    )
}
