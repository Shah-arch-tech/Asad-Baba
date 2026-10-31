package com.shahpharmacy.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserFirestoreModel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "staff", // "admin" or "staff"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PurchaseFirestoreModel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val company: String = "",
    val qty: Int = 0,
    val purchase: Double = 0.0,
    val sale: Double = 0.0,
    val date: String = "",
    val batch: String = "",
    val expiry: String = "",
    val shelf: String = "General",
    val total: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DailySaleFirestoreModel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val amount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExpenseFirestoreModel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val category: String = "Other",
    val amount: Double = 0.0,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CashSettingFirestoreModel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val openingCash: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class InventoryAlertStatus {
    EXPIRED,
    NEAR_EXPIRY,
    LOW_STOCK,
    CRITICAL_BOTH,
    NORMAL
}

data class MedicationInventoryAlert(
    val medicationId: String = "",
    val name: String = "",
    val company: String = "",
    val qty: Int = 0,
    val expiry: String = "",
    val batch: String = "",
    val shelf: String = "General",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val isLowStock: Boolean = false,
    val isNearExpiry: Boolean = false,
    val isExpired: Boolean = false,
    val daysRemaining: Long? = null,
    val alertStatus: InventoryAlertStatus = InventoryAlertStatus.NORMAL
)

data class AlertSummary(
    val totalAlerts: Int = 0,
    val lowStockCount: Int = 0,
    val nearExpiryCount: Int = 0,
    val expiredCount: Int = 0,
    val criticalCount: Int = 0
)
