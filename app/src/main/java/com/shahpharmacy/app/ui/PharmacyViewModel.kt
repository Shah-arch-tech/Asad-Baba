package com.shahpharmacy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.shahpharmacy.app.data.FirestorePharmacyRepository
import com.shahpharmacy.app.data.PharmacyRepository
import com.shahpharmacy.app.model.AlertSummary
import com.shahpharmacy.app.model.CashSettingEntity
import com.shahpharmacy.app.model.DailySaleEntity
import com.shahpharmacy.app.model.ExpenseEntity
import com.shahpharmacy.app.model.InventoryAlertStatus
import com.shahpharmacy.app.model.MedicationInventoryAlert
import com.shahpharmacy.app.model.PurchaseEntity
import com.shahpharmacy.app.model.PurchaseFirestoreModel
import com.shahpharmacy.app.model.StockItem
import com.shahpharmacy.app.model.TimeframeTotals
import com.shahpharmacy.app.model.UserEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class NavigationTab(val title: String) {
    DASHBOARD("Dashboard"),
    PURCHASE("Purchase"),
    SALE("Daily Sale"),
    EXPENSE("Expenses"),
    STOCK("Stock"),
    REPORTS("Reports"),
    CASH("Cash In Hand"),
    SETTINGS("Settings")
}

class PharmacyViewModel(application: Application) : AndroidViewModel(application) {
    val repository = PharmacyRepository(application)
    val firestoreRepository = FirestorePharmacyRepository(application)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentTab = MutableStateFlow(NavigationTab.DASHBOARD)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    val purchases: StateFlow<List<PurchaseEntity>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<DailySaleEntity>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashSetting: StateFlow<CashSettingEntity?> = repository.cashSetting
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashSettingEntity(1, 0.0))

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockList: StateFlow<List<StockItem>> = purchases
        .combine(sales) { pList, _ ->
            repository.calculateStock(pList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashInHand: StateFlow<Double> = combine(
        cashSetting, sales, purchases, expenses
    ) { cash, sList, pList, eList ->
        val opening = cash?.openingCash ?: 0.0
        val totalSale = sList.sumOf { it.amount }
        val totalPurchase = pList.sumOf { it.total }
        val totalExpense = eList.sumOf { it.amount }
        opening + totalSale - totalPurchase - totalExpense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotals: StateFlow<TimeframeTotals> = combine(
        sales, purchases, expenses
    ) { sList, pList, eList ->
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        repository.calculateTotals(sList, pList, eList, today, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeframeTotals(0.0, 0.0, 0.0, 0.0))

    val weeklyTotals: StateFlow<TimeframeTotals> = combine(
        sales, purchases, expenses
    ) { sList, pList, eList ->
        val cal = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startWeekStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        repository.calculateTotals(sList, pList, eList, startWeekStr, todayStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeframeTotals(0.0, 0.0, 0.0, 0.0))

    val monthlyTotals: StateFlow<TimeframeTotals> = combine(
        sales, purchases, expenses
    ) { sList, pList, eList ->
        val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        repository.calculateTotals(sList, pList, eList, "$monthStr-01", "$monthStr-31")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeframeTotals(0.0, 0.0, 0.0, 0.0))

    val allTimeTotals: StateFlow<TimeframeTotals> = combine(
        sales, purchases, expenses
    ) { sList, pList, eList ->
        repository.calculateTotals(sList, pList, eList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeframeTotals(0.0, 0.0, 0.0, 0.0))

    val availableShelves: StateFlow<List<String>> = stockList.map { list ->
        val set = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
        list.forEach { if (it.shelf.isNotBlank()) set.add(it.shelf.trim()) }
        if (!set.contains("General")) set.add("General")
        listOf("All Shelves") + set.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All Shelves", "General"))

    val selectedShelf = MutableStateFlow("All Shelves")

    fun selectShelf(shelf: String) {
        selectedShelf.value = shelf
    }

    // Minimum quantity threshold (default: 10 units)
    val minQtyThreshold = MutableStateFlow(10)
    // Near-expiry threshold in months (default: 2 months)
    val expiryMonthsThreshold = MutableStateFlow(2)

    val inventoryAlerts: StateFlow<List<MedicationInventoryAlert>> = combine(
        purchases,
        minQtyThreshold,
        expiryMonthsThreshold
    ) { pList, minQty, months ->
        val firestoreList = pList.map { p ->
            PurchaseFirestoreModel(
                id = p.id.toString(),
                name = p.name,
                company = p.company,
                qty = p.qty,
                purchase = p.purchase,
                sale = p.sale,
                date = p.date,
                batch = p.batch,
                expiry = p.expiry,
                shelf = p.shelf,
                total = p.total
            )
        }
        firestoreRepository.identifyMedicationAlerts(
            purchases = firestoreList,
            minQtyThreshold = minQty,
            monthsNearExpiryThreshold = months
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertSummary: StateFlow<AlertSummary> = inventoryAlerts.map { alerts ->
        AlertSummary(
            totalAlerts = alerts.size,
            lowStockCount = alerts.count { it.isLowStock },
            nearExpiryCount = alerts.count { it.isNearExpiry },
            expiredCount = alerts.count { it.isExpired },
            criticalCount = alerts.count { it.alertStatus == InventoryAlertStatus.CRITICAL_BOTH }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlertSummary())

    fun updateMinQtyThreshold(threshold: Int) {
        minQtyThreshold.value = threshold.coerceAtLeast(1)
    }

    fun updateExpiryMonthsThreshold(months: Int) {
        expiryMonthsThreshold.value = months.coerceAtLeast(1)
    }

    init {
        viewModelScope.launch {
            repository.initialize()
        }
    }

    fun selectTab(tab: NavigationTab) {
        val user = _currentUser.value
        if (user?.role == "staff") {
            // Staff allowed tabs
            if (tab == NavigationTab.PURCHASE || tab == NavigationTab.REPORTS || tab == NavigationTab.CASH) {
                viewModelScope.launch { _uiMessage.emit("Access restricted to Admin role.") }
                return
            }
        }
        _currentTab.value = tab
    }

    fun onGoogleSignInSuccess(firebaseUser: FirebaseUser) {
        val displayName = firebaseUser.displayName?.ifBlank { null }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Admin"
        val user = UserEntity(
            username = displayName,
            password = "",
            role = "admin"
        )
        _currentUser.value = user
        _currentTab.value = NavigationTab.DASHBOARD
        viewModelScope.launch {
            try {
                firestoreRepository.createOrUpdateUserProfile(
                    displayName = displayName,
                    email = firebaseUser.email ?: "",
                    role = "admin"
                )
            } catch (e: Exception) {
                // Ignore profile update error if offline
            }
            _uiMessage.emit("Welcome $displayName! Connected to Firebase Cloud.")
        }
    }

    fun login(username: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.authenticate(username, pass)
            if (user != null) {
                _currentUser.value = user
                _currentTab.value = NavigationTab.DASHBOARD
                onResult(true, "Login successful")
            } else {
                onResult(false, "Invalid username or password.")
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _currentTab.value = NavigationTab.DASHBOARD
        try {
            Firebase.auth.signOut()
        } catch (_: Exception) {
        }
    }

    fun changePassword(oldPass: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        if (user.password != oldPass) {
            onResult(false, "Current password is incorrect.")
            return
        }
        if (newPass.length < 4) {
            onResult(false, "New password must be at least 4 characters.")
            return
        }
        if (newPass != confirmPass) {
            onResult(false, "New passwords do not match.")
            return
        }
        viewModelScope.launch {
            repository.changePassword(user.username, newPass)
            _currentUser.value = user.copy(password = newPass)
            onResult(true, "Password changed successfully.")
        }
    }

    fun addStaff(username: String, pass: String, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value
        if (user?.role != "admin") {
            onResult(false, "Only admin can add staff.")
            return
        }
        viewModelScope.launch {
            val success = repository.addStaff(username, pass)
            if (success) {
                onResult(true, "Staff user '$username' added successfully.")
            } else {
                onResult(false, "Username already exists or password is less than 4 chars.")
            }
        }
    }

    fun deleteUser(target: UserEntity) {
        val user = _currentUser.value
        if (user?.role != "admin") return
        if (target.username == "admin") {
            viewModelScope.launch { _uiMessage.emit("Admin account cannot be deleted.") }
            return
        }
        viewModelScope.launch {
            repository.deleteUser(target)
            _uiMessage.emit("User '${target.username}' deleted.")
        }
    }

    fun addPurchase(
        name: String,
        company: String,
        qty: Int,
        purchasePrice: Double,
        salePrice: Double,
        date: String,
        batch: String,
        expiry: String,
        shelf: String = "General",
        onSuccess: () -> Unit
    ) {
        if (name.isBlank() || qty <= 0) {
            viewModelScope.launch { _uiMessage.emit("Please enter medicine name and valid quantity.") }
            return
        }
        val safeDate = if (date.isBlank()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) else date
        val safeShelf = shelf.trim().ifBlank { "General" }
        viewModelScope.launch {
            repository.addPurchase(
                PurchaseEntity(
                    name = name.trim(),
                    company = company.trim(),
                    qty = qty,
                    purchase = purchasePrice,
                    sale = salePrice,
                    date = safeDate,
                    batch = batch.trim(),
                    expiry = expiry.trim(),
                    total = qty * purchasePrice,
                    shelf = safeShelf
                )
            )
            if (Firebase.auth.currentUser != null) {
                try {
                    firestoreRepository.addPurchase(
                        name = name.trim(),
                        company = company.trim(),
                        qty = qty,
                        purchasePrice = purchasePrice,
                        salePrice = salePrice,
                        date = safeDate,
                        batch = batch.trim(),
                        expiry = expiry.trim(),
                        shelf = safeShelf
                    )
                } catch (e: Exception) {
                    // Handled inside firestoreRepository
                }
            }
            _uiMessage.emit("Purchase saved.")
            onSuccess()
        }
    }

    fun deletePurchase(purchase: PurchaseEntity) {
        viewModelScope.launch {
            repository.deletePurchase(purchase)
            _uiMessage.emit("Purchase deleted.")
        }
    }

    fun saveDailySale(date: String, amount: Double, onSuccess: () -> Unit) {
        if (date.isBlank() || amount < 0) {
            viewModelScope.launch { _uiMessage.emit("Please enter valid date and amount.") }
            return
        }
        viewModelScope.launch {
            repository.saveOrUpdateSale(date, amount)
            if (Firebase.auth.currentUser != null) {
                try {
                    firestoreRepository.saveOrUpdateDailySale(date, amount)
                } catch (e: Exception) {
                }
            }
            _uiMessage.emit("Daily sale for $date saved.")
            onSuccess()
        }
    }

    fun deleteSale(sale: DailySaleEntity) {
        viewModelScope.launch {
            repository.deleteSale(sale)
            _uiMessage.emit("Daily sale deleted.")
        }
    }

    fun addExpense(
        date: String,
        category: String,
        amount: Double,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (amount <= 0) {
            viewModelScope.launch { _uiMessage.emit("Please enter a valid amount greater than 0.") }
            return
        }
        val safeDate = if (date.isBlank()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) else date
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(
                    date = safeDate,
                    category = category.trim(),
                    amount = amount,
                    note = note.trim()
                )
            )
            if (Firebase.auth.currentUser != null) {
                try {
                    firestoreRepository.addExpense(
                        date = safeDate,
                        category = category.trim(),
                        amount = amount,
                        note = note.trim()
                    )
                } catch (e: Exception) {
                }
            }
            _uiMessage.emit("Expense saved.")
            onSuccess()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _uiMessage.emit("Expense deleted.")
        }
    }

    fun updateOpeningCash(amount: Double) {
        viewModelScope.launch {
            repository.updateOpeningCash(amount)
            if (Firebase.auth.currentUser != null) {
                try {
                    firestoreRepository.updateOpeningCash(amount)
                } catch (e: Exception) {
                }
            }
            _uiMessage.emit("Opening cash updated to Rs. ${String.format(Locale.US, "%,.2f", amount)}")
        }
    }

    fun parsePurchaseOrdersPreview(content: String): List<PurchaseEntity> {
        return repository.parsePurchaseOrdersFromText(content)
    }

    fun importPurchasesFile(content: String, onResult: (Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val list = repository.parsePurchaseOrdersFromText(content)
                if (list.isEmpty()) {
                    onResult(0, "No valid purchase records found in file. Please ensure columns include name, quantity, and buy price.")
                    return@launch
                }
                val count = repository.importPurchasesList(list)
                if (Firebase.auth.currentUser != null) {
                    for (item in list) {
                        try {
                            firestoreRepository.addPurchase(
                                name = item.name,
                                company = item.company,
                                qty = item.qty,
                                purchasePrice = item.purchase,
                                salePrice = item.sale,
                                date = item.date,
                                batch = item.batch,
                                expiry = item.expiry,
                                shelf = item.shelf
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
                _uiMessage.emit("Successfully imported $count purchase orders.")
                onResult(count, "Successfully imported $count purchase orders into inventory.")
            } catch (e: Exception) {
                onResult(0, "Failed to import file: ${e.message}")
            }
        }
    }

    fun importCsv(content: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importCsvText(content)
            _uiMessage.emit("$count purchase records imported.")
            onResult(count)
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportJsonBackup()
            onResult(json)
        }
    }

    fun restoreBackupJson(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.importJsonBackup(json)
            if (ok) {
                _uiMessage.emit("Backup restored successfully.")
            } else {
                _uiMessage.emit("Failed to restore backup: invalid format.")
            }
            onResult(ok)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            val uid = Firebase.auth.currentUser?.uid
            if (uid != null) {
                try {
                    firestoreRepository.clearAllCloudData(uid)
                } catch (e: Exception) {
                }
            }
            _uiMessage.emit("All pharmacy data cleared.")
        }
    }
}
