package com.shahpharmacy.app.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.shahpharmacy.app.base.FirestoreEmulatorTestBase
import com.shahpharmacy.app.model.PurchaseFirestoreModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PharmacyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createPurchase_authenticatedOwner_createsDocument() {
    runBlocking {
      signInTestUser(ALICE_EMAIL)
      val repository = FirestorePharmacyRepository(firestore, auth)

      withTimeout(DEFAULT_TIMEOUT_MS) {
        repository.addPurchase(
          name = "Panadol 500mg",
          company = "GSK",
          qty = 10,
          purchasePrice = 2.5,
          salePrice = 3.0,
          date = "2026-10-01",
          batch = "B123",
          expiry = "2027-10-01"
        )
      }

      val aliceUid = auth.currentUser!!.uid
      val purchases = withTimeout(FLOW_TIMEOUT_MS) {
        repository.observePurchases(aliceUid).first()
      }
      assertTrue(purchases.any { it.name == "Panadol 500mg" })
    }
  }

  @Test
  fun observePurchases_unauthenticated_failsWithPermissionDenied() {
    runBlocking {
      // Authenticate to get a UID first, then sign out
      val aliceUid = signInTestUser(ALICE_EMAIL)
      auth.signOut()

      val repository = FirestorePharmacyRepository(firestore, auth)

      assertThrows(Exception::class.java) {
        runBlocking {
          withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePurchases(aliceUid).first()
          }
        }
      }
    }
  }

  @Test
  fun saveDailySale_authenticatedOwner_savesAndQueries() {
    runBlocking {
      val aliceUid = signInTestUser(ALICE_EMAIL)
      val repository = FirestorePharmacyRepository(firestore, auth)

      withTimeout(DEFAULT_TIMEOUT_MS) {
        repository.saveOrUpdateDailySale("2026-10-01", 5000.0)
      }

      val sales = withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeDailySales(aliceUid).first()
      }
      assertTrue(sales.any { it.date == "2026-10-01" && it.amount == 5000.0 })
    }
  }

  @Test
  fun identifyMedicationAlerts_detectsLowStockAndNearExpiry() {
    val repository = FirestorePharmacyRepository(firestore, auth)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val baseCal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10) }
    val baseDate = baseCal.time

    // 1. Low stock only (qty 5 <= 10, expiry far in future)
    val farCal = Calendar.getInstance().apply {
      time = baseDate
      add(Calendar.MONTH, 12)
    }
    val medLowStock = PurchaseFirestoreModel(
      id = "m1",
      name = "Amoxicillin 500mg",
      qty = 5,
      expiry = sdf.format(farCal.time)
    )

    // 2. Near expiry within 2 months (qty 50 > 10, expiry in 25 days)
    val nearCal = Calendar.getInstance().apply {
      time = baseDate
      add(Calendar.DAY_OF_YEAR, 25)
    }
    val medNearExp = PurchaseFirestoreModel(
      id = "m2",
      name = "Augmentin Syrup",
      qty = 50,
      expiry = sdf.format(nearCal.time)
    )

    // 3. Critical: Both low stock and near expiry (qty 3, expiry in 15 days)
    val critCal = Calendar.getInstance().apply {
      time = baseDate
      add(Calendar.DAY_OF_YEAR, 15)
    }
    val medCritical = PurchaseFirestoreModel(
      id = "m3",
      name = "Insulin Glargine",
      qty = 3,
      expiry = sdf.format(critCal.time)
    )

    // 4. Safe medication (qty 100, expiry in 18 months)
    val medSafe = PurchaseFirestoreModel(
      id = "m4",
      name = "Paracetamol 500mg",
      qty = 100,
      expiry = sdf.format(farCal.time)
    )

    val alerts = repository.identifyMedicationAlerts(
      purchases = listOf(medLowStock, medNearExp, medCritical, medSafe),
      minQtyThreshold = 10,
      monthsNearExpiryThreshold = 2,
      referenceDate = baseDate
    )

    // medSafe should not be in alerts
    assertTrue(alerts.none { it.medicationId == "m4" })

    // medLowStock should be detected as low stock
    val lowStockAlert = alerts.firstOrNull { it.medicationId == "m1" }
    assertNotNull(lowStockAlert)
    assertTrue(lowStockAlert!!.isLowStock)
    assertTrue(!lowStockAlert.isNearExpiry)

    // medNearExp should be detected as near expiry (within 2 months)
    val nearExpAlert = alerts.firstOrNull { it.medicationId == "m2" }
    assertNotNull(nearExpAlert)
    assertTrue(nearExpAlert!!.isNearExpiry)
    assertTrue(!nearExpAlert.isLowStock)

    // medCritical should be detected as CRITICAL_BOTH
    val critAlert = alerts.firstOrNull { it.medicationId == "m3" }
    assertNotNull(critAlert)
    assertTrue(critAlert!!.alertStatus == com.shahpharmacy.app.model.InventoryAlertStatus.CRITICAL_BOTH)
  }

  @Test
  fun createPurchase_withShelf_persistsShelfField() {
    runBlocking {
      val aliceUid = signInTestUser(ALICE_EMAIL)
      val repository = FirestorePharmacyRepository(firestore, auth)

      withTimeout(DEFAULT_TIMEOUT_MS) {
        repository.addPurchase(
          name = "Augmentin 625mg",
          company = "GSK",
          qty = 20,
          purchasePrice = 12.0,
          salePrice = 15.0,
          date = "2026-10-01",
          batch = "B888",
          expiry = "2027-05-01",
          shelf = "Shelf B-2"
        )
      }

      val purchases = withTimeout(FLOW_TIMEOUT_MS) {
        repository.observePurchases(aliceUid).first()
      }
      val item = purchases.firstOrNull { it.name == "Augmentin 625mg" }
      assertNotNull(item)
      assertTrue(item!!.shelf == "Shelf B-2")
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
