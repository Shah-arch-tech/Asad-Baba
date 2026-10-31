package com.shahpharmacy.app.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.shahpharmacy.app.R
import com.shahpharmacy.app.model.CashSettingFirestoreModel
import com.shahpharmacy.app.model.DailySaleFirestoreModel
import com.shahpharmacy.app.model.ExpenseFirestoreModel
import com.shahpharmacy.app.model.InventoryAlertStatus
import com.shahpharmacy.app.model.MedicationInventoryAlert
import com.shahpharmacy.app.model.PurchaseFirestoreModel
import com.shahpharmacy.app.model.UserFirestoreModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class FirestorePharmacyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserFirestoreModel?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { it.toObject(UserFirestoreModel::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                throw e
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun createOrUpdateUserProfile(displayName: String, email: String, role: String) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "email" to email,
                "role" to role,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val payload = mapOf(
                "displayName" to displayName,
                "email" to email,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(payload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
    }

    fun observePurchases(userId: String): Flow<List<PurchaseFirestoreModel>> {
        val path = "purchases"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(PurchaseFirestoreModel::class.java).sortedByDescending { it.date }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Identifies medications in the inventory that are:
     * 1. Below a minimum quantity threshold (e.g. qty <= minQtyThreshold)
     * 2. Within a 2-month near-expiry threshold (or already expired)
     */
    fun identifyMedicationAlerts(
        purchases: List<PurchaseFirestoreModel>,
        minQtyThreshold: Int = 10,
        monthsNearExpiryThreshold: Int = 2,
        referenceDate: Date = Date()
    ): List<MedicationInventoryAlert> {
        val calendarNow = Calendar.getInstance().apply { time = referenceDate }
        val thresholdCalendar = Calendar.getInstance().apply {
            time = referenceDate
            add(Calendar.MONTH, monthsNearExpiryThreshold)
        }
        val thresholdTime = thresholdCalendar.timeInMillis
        val nowTime = calendarNow.timeInMillis

        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("yyyy-MM", Locale.US),
            SimpleDateFormat("MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        )

        return purchases.mapNotNull { item ->
            val isLow = item.qty in 0..minQtyThreshold

            var expiryDate: Date? = null
            if (item.expiry.isNotBlank()) {
                for (format in dateFormats) {
                    try {
                        val parsed = format.parse(item.expiry.trim())
                        if (parsed != null) {
                            expiryDate = parsed
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            val isExpired: Boolean
            val isNearExp: Boolean
            val daysRemaining: Long?

            if (expiryDate != null) {
                val expTime = expiryDate.time
                isExpired = expTime < nowTime
                isNearExp = expTime in nowTime..thresholdTime
                daysRemaining = TimeUnit.MILLISECONDS.toDays(expTime - nowTime)
            } else {
                isExpired = false
                isNearExp = false
                daysRemaining = null
            }

            if (isLow || isNearExp || isExpired) {
                val status = when {
                    isLow && (isExpired || isNearExp) -> InventoryAlertStatus.CRITICAL_BOTH
                    isExpired -> InventoryAlertStatus.EXPIRED
                    isNearExp -> InventoryAlertStatus.NEAR_EXPIRY
                    isLow -> InventoryAlertStatus.LOW_STOCK
                    else -> InventoryAlertStatus.NORMAL
                }

                MedicationInventoryAlert(
                    medicationId = item.id,
                    name = item.name,
                    company = item.company,
                    qty = item.qty,
                    expiry = item.expiry,
                    batch = item.batch,
                    shelf = item.shelf.ifBlank { "General" },
                    purchasePrice = item.purchase,
                    salePrice = item.sale,
                    isLowStock = isLow,
                    isNearExpiry = isNearExp,
                    isExpired = isExpired,
                    daysRemaining = daysRemaining,
                    alertStatus = status
                )
            } else {
                null
            }
        }.sortedWith(
            compareBy(
                {
                    when (it.alertStatus) {
                        InventoryAlertStatus.CRITICAL_BOTH -> 0
                        InventoryAlertStatus.EXPIRED -> 1
                        InventoryAlertStatus.NEAR_EXPIRY -> 2
                        InventoryAlertStatus.LOW_STOCK -> 3
                        InventoryAlertStatus.NORMAL -> 4
                    }
                },
                { it.daysRemaining ?: Long.MAX_VALUE },
                { it.qty }
            )
        )
    }

    fun observeMedicationAlerts(
        userId: String,
        minQtyThreshold: Int = 10,
        monthsNearExpiryThreshold: Int = 2
    ): Flow<List<MedicationInventoryAlert>> {
        return observePurchases(userId).map { purchases ->
            identifyMedicationAlerts(purchases, minQtyThreshold, monthsNearExpiryThreshold)
        }
    }

    suspend fun addPurchase(
        name: String,
        company: String,
        qty: Int,
        purchasePrice: Double,
        salePrice: Double,
        date: String,
        batch: String,
        expiry: String,
        shelf: String = "General"
    ) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val collection = db.collection("purchases")
        val docRef = collection.document()
        val total = qty * purchasePrice
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim(),
            "company" to company.trim(),
            "qty" to qty,
            "purchase" to purchasePrice,
            "sale" to salePrice,
            "date" to date,
            "batch" to batch.trim(),
            "expiry" to expiry.trim(),
            "shelf" to shelf.trim().ifBlank { "General" },
            "total" to total,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePurchase(purchaseId: String) = withContext(Dispatchers.IO) {
        require(purchaseId.isNotBlank())
        val docRef = db.collection("purchases").document(purchaseId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeDailySales(userId: String): Flow<List<DailySaleFirestoreModel>> {
        val path = "daily_sales"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(DailySaleFirestoreModel::class.java).sortedByDescending { it.date }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun saveOrUpdateDailySale(date: String, amount: Double) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val collection = db.collection("daily_sales")
        val existing = collection
            .whereEqualTo("userId", uid)
            .whereEqualTo("date", date)
            .get()
            .await()

        if (!existing.isEmpty) {
            val doc = existing.documents.first()
            val docRef = collection.document(doc.id)
            val payload = mapOf(
                "amount" to amount,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(payload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val docRef = collection.document()
            val payload = mapOf(
                "userId" to uid,
                "date" to date,
                "amount" to amount,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun deleteDailySale(saleId: String) = withContext(Dispatchers.IO) {
        require(saleId.isNotBlank())
        val docRef = db.collection("daily_sales").document(saleId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeExpenses(userId: String): Flow<List<ExpenseFirestoreModel>> {
        val path = "expenses"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ExpenseFirestoreModel::class.java).sortedByDescending { it.date }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun addExpense(
        date: String,
        category: String,
        amount: Double,
        note: String
    ) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val collection = db.collection("expenses")
        val docRef = collection.document()
        val payload = mapOf(
            "userId" to uid,
            "date" to date,
            "category" to category.trim(),
            "amount" to amount,
            "note" to note.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExpense(expenseId: String) = withContext(Dispatchers.IO) {
        require(expenseId.isNotBlank())
        val docRef = db.collection("expenses").document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeCashSetting(userId: String): Flow<CashSettingFirestoreModel?> {
        val path = "cash_settings/$userId"
        return db.collection("cash_settings").document(userId)
            .snapshots()
            .map { it.toObject(CashSettingFirestoreModel::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                throw e
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun updateOpeningCash(amount: Double) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("cash_settings").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "openingCash" to amount,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun clearAllCloudData(userId: String) = withContext(Dispatchers.IO) {
        val purchases = db.collection("purchases").whereEqualTo("userId", userId).get().await()
        for (doc in purchases.documents) {
            doc.reference.delete().await()
        }
        val sales = db.collection("daily_sales").whereEqualTo("userId", userId).get().await()
        for (doc in sales.documents) {
            doc.reference.delete().await()
        }
        val expenses = db.collection("expenses").whereEqualTo("userId", userId).get().await()
        for (doc in expenses.documents) {
            doc.reference.delete().await()
        }
        db.collection("cash_settings").document(userId).set(
            mapOf("userId" to userId, "openingCash" to 0.0, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }
}
