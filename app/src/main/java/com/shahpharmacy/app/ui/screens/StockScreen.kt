package com.shahpharmacy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ProductionQuantityLimits
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahpharmacy.app.model.InventoryAlertStatus
import com.shahpharmacy.app.model.MedicationInventoryAlert
import com.shahpharmacy.app.model.StockItem
import com.shahpharmacy.app.ui.PharmacyViewModel
import com.shahpharmacy.app.ui.components.HeroBanner
import com.shahpharmacy.app.ui.components.StatCard
import com.shahpharmacy.app.ui.components.formatCurrency
import com.shahpharmacy.app.ui.theme.AmberOrange
import com.shahpharmacy.app.ui.theme.BlueAccent
import com.shahpharmacy.app.ui.theme.BorderColor
import com.shahpharmacy.app.ui.theme.CrimsonRed
import com.shahpharmacy.app.ui.theme.DeepNavy
import com.shahpharmacy.app.ui.theme.EmeraldGreen
import com.shahpharmacy.app.ui.theme.NavyPrimary
import com.shahpharmacy.app.ui.theme.TextDark
import com.shahpharmacy.app.ui.theme.TextMuted
import java.util.Locale

private enum class AlertFilterOption {
    ALL,
    CRITICAL,
    LOW_STOCK,
    NEAR_EXPIRY,
    EXPIRED
}

private enum class InventoryViewMode {
    SHELF_WISE,
    ALL_LIST
}

@Composable
fun StockScreen(
    viewModel: PharmacyViewModel,
    modifier: Modifier = Modifier
) {
    val stockList by viewModel.stockList.collectAsStateWithLifecycle()
    val availableShelves by viewModel.availableShelves.collectAsStateWithLifecycle()
    val selectedShelf by viewModel.selectedShelf.collectAsStateWithLifecycle()
    val inventoryAlerts by viewModel.inventoryAlerts.collectAsStateWithLifecycle()
    val alertSummary by viewModel.alertSummary.collectAsStateWithLifecycle()
    val minQtyThreshold by viewModel.minQtyThreshold.collectAsStateWithLifecycle()
    val expiryMonthsThreshold by viewModel.expiryMonthsThreshold.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedAlertFilter by remember { mutableStateOf(AlertFilterOption.ALL) }
    var showThresholdSettings by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(InventoryViewMode.SHELF_WISE) }

    val filteredAlerts = inventoryAlerts.filter { alert ->
        val matchesShelf = selectedShelf == "All Shelves" || alert.shelf.equals(selectedShelf, ignoreCase = true)
        val matchesFilter = when (selectedAlertFilter) {
            AlertFilterOption.ALL -> true
            AlertFilterOption.CRITICAL -> alert.alertStatus == InventoryAlertStatus.CRITICAL_BOTH
            AlertFilterOption.LOW_STOCK -> alert.isLowStock
            AlertFilterOption.NEAR_EXPIRY -> alert.isNearExpiry
            AlertFilterOption.EXPIRED -> alert.isExpired
        }
        matchesShelf && matchesFilter
    }

    val filteredStock = stockList.filter {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val matchesSearch = q.isEmpty() || it.name.lowercase(Locale.ROOT).contains(q) || it.company.lowercase(Locale.ROOT).contains(q) || it.shelf.lowercase(Locale.ROOT).contains(q)
        val matchesShelf = selectedShelf == "All Shelves" || it.shelf.equals(selectedShelf, ignoreCase = true)
        matchesSearch && matchesShelf
    }

    // Grouping by shelf for shelf-wise separation view
    val shelfGroupedStock = filteredStock.groupBy { it.shelf.trim().ifBlank { "General" } }

    val totalPurchaseValue = stockList.sumOf { it.purchaseValue }
    val totalSaleValue = stockList.sumOf { it.saleValue }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HeroBanner(title = "Inventory & Shelf Management")

        // 4 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "Purchase Value",
                value = formatCurrency(totalPurchaseValue),
                valueColor = AmberOrange,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Sale Value",
                value = formatCurrency(totalSaleValue),
                valueColor = BlueAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "Medicine Lines",
                value = stockList.size.toString(),
                valueColor = TextDark,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Attention Alerts",
                value = "${alertSummary.totalAlerts} items",
                valueColor = if (alertSummary.totalAlerts > 0) CrimsonRed else EmeraldGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: Firestore Medication Inventory Alerts (Low Stock & 2-Month Near Expiry)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (alertSummary.totalAlerts > 0) Color(0xFFFFF9F5) else Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (alertSummary.totalAlerts > 0) Color(0xFFFFD8BF) else BorderColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("inventory_alerts_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (alertSummary.totalAlerts > 0) CrimsonRed.copy(alpha = 0.12f) else EmeraldGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (alertSummary.totalAlerts > 0) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                                    contentDescription = "Alerts Icon",
                                    tint = if (alertSummary.totalAlerts > 0) CrimsonRed else EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Medication Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Qty ≤ $minQtyThreshold • Expiry ≤ $expiryMonthsThreshold months",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { showThresholdSettings = !showThresholdSettings }
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Threshold Settings",
                                tint = DeepNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Thresholds",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepNavy
                            )
                        }
                    }
                }

                // Thresholds Adjustment Bar (Expandable)
                if (showThresholdSettings) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Minimum Quantity Threshold (Low Stock):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 10, 15, 20).forEach { threshold ->
                                    val isSelected = minQtyThreshold == threshold
                                    Surface(
                                        color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.updateMinQtyThreshold(threshold) }
                                    ) {
                                        Text(
                                            text = "≤ $threshold",
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else TextDark,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Near-Expiry Threshold: 2 Months (60 Days)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Alert Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedAlertFilter == AlertFilterOption.ALL,
                        onClick = { selectedAlertFilter = AlertFilterOption.ALL },
                        label = { Text("All (${alertSummary.totalAlerts})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )

                    if (alertSummary.criticalCount > 0) {
                        FilterChip(
                            selected = selectedAlertFilter == AlertFilterOption.CRITICAL,
                            onClick = { selectedAlertFilter = AlertFilterOption.CRITICAL },
                            label = { Text("Critical (${alertSummary.criticalCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    FilterChip(
                        selected = selectedAlertFilter == AlertFilterOption.LOW_STOCK,
                        onClick = { selectedAlertFilter = AlertFilterOption.LOW_STOCK },
                        label = { Text("Low Stock (${alertSummary.lowStockCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberOrange,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedAlertFilter == AlertFilterOption.NEAR_EXPIRY,
                        onClick = { selectedAlertFilter = AlertFilterOption.NEAR_EXPIRY },
                        label = { Text("Near Expiry ≤ 2 Mo (${alertSummary.nearExpiryCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD97706),
                            selectedLabelColor = Color.White
                        )
                    )

                    if (alertSummary.expiredCount > 0) {
                        FilterChip(
                            selected = selectedAlertFilter == AlertFilterOption.EXPIRED,
                            onClick = { selectedAlertFilter = AlertFilterOption.EXPIRED },
                            label = { Text("Expired (${alertSummary.expiredCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF991B1B),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredAlerts.isEmpty()) {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "OK",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "All medications are well-stocked and have more than 2 months before expiry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    filteredAlerts.forEachIndexed { index, alert ->
                        MedicationAlertItem(alert = alert, minQty = minQtyThreshold)
                        if (index < filteredAlerts.lastIndex) {
                            HorizontalDivider(
                                color = Color(0xFFFFEAD9),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: Shelf-Wise Separation & General Stock Inventory
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .testTag("shelf_inventory_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shelf-Wise Inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "${filteredStock.size} Medicines in ${shelfGroupedStock.size} Shelves",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // View Mode Toggle (Shelf-Wise vs Flat List)
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == InventoryViewMode.SHELF_WISE) NavyPrimary else Color.Transparent,
                            modifier = Modifier
                                .clickable { viewMode = InventoryViewMode.SHELF_WISE }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = "Shelf View",
                                    tint = if (viewMode == InventoryViewMode.SHELF_WISE) Color.White else TextDark,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Shelves",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (viewMode == InventoryViewMode.SHELF_WISE) Color.White else TextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == InventoryViewMode.ALL_LIST) NavyPrimary else Color.Transparent,
                            modifier = Modifier
                                .clickable { viewMode = InventoryViewMode.ALL_LIST }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "List View",
                                    tint = if (viewMode == InventoryViewMode.ALL_LIST) Color.White else TextDark,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "List",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (viewMode == InventoryViewMode.ALL_LIST) Color.White else TextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search medicine name, company or shelf...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_medicine_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Shelf Filter Navigation Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableShelves.forEach { shelfName ->
                        val isSelected = selectedShelf.equals(shelfName, ignoreCase = true)
                        val itemCount = if (shelfName == "All Shelves") {
                            stockList.size
                        } else {
                            stockList.count { it.shelf.equals(shelfName, ignoreCase = true) }
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectShelf(shelfName) },
                            label = {
                                Text("$shelfName ($itemCount)")
                            },
                            leadingIcon = {
                                if (shelfName.contains("Cold", ignoreCase = true)) {
                                    Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (filteredStock.isEmpty()) {
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedShelf != "All Shelves") {
                            "No medications found matching your shelf or search criteria."
                        } else {
                            "No stock yet. Add purchases or upload an order file first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else if (viewMode == InventoryViewMode.SHELF_WISE) {
                    // Grouped Shelf-Wise Display
                    shelfGroupedStock.forEach { (shelfTitle, itemsOnShelf) ->
                        val shelfValuation = itemsOnShelf.sumOf { it.saleValue }
                        val shelfCost = itemsOnShelf.sumOf { it.purchaseValue }
                        val isCold = shelfTitle.contains("Cold", ignoreCase = true)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCold) Color(0xFFF0F9FF) else Color(0xFFFAFAFA)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isCold) Color(0xFFBAE6FD) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Shelf Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isCold) Color(0xFF0284C7).copy(alpha = 0.15f) else NavyPrimary.copy(alpha = 0.1f),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isCold) Icons.Default.AcUnit else Icons.Default.Inventory2,
                                                    contentDescription = null,
                                                    tint = if (isCold) Color(0xFF0284C7) else NavyPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = shelfTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCold) Color(0xFF0369A1) else NavyPrimary
                                            )
                                            Text(
                                                text = "${itemsOnShelf.size} medicine lines • Qty: ${itemsOnShelf.sumOf { it.qty }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(shelfValuation),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = "Cost: ${formatCurrency(shelfCost)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = if (isCold) Color(0xFFE0F2FE) else BorderColor)
                                Spacer(modifier = Modifier.height(6.dp))

                                // Medicines on this shelf
                                itemsOnShelf.forEachIndexed { itemIdx, med ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = med.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${if (med.company.isNotBlank()) "${med.company} • " else ""}Qty: ${med.qty} • Buy: ${formatCurrency(med.purchase)} • Sell: ${formatCurrency(med.sale)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatCurrency(med.saleValue),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "Cost: ${formatCurrency(med.purchaseValue)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AmberOrange
                                            )
                                        }
                                    }
                                    if (itemIdx < itemsOnShelf.lastIndex) {
                                        HorizontalDivider(
                                            color = Color(0xFFF1F5F9),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Flat List with Shelf Badges
                    filteredStock.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = NavyPrimary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "📍 ${item.shelf}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${if (item.company.isNotBlank()) "${item.company} • " else ""}Qty: ${item.qty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Buy: ${formatCurrency(item.purchase)} • Sell: ${formatCurrency(item.sale)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Buy Val: ${formatCurrency(item.purchaseValue)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberOrange
                                )
                                Text(
                                    text = "Sale Val: ${formatCurrency(item.saleValue)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                        }
                        if (index < filteredStock.lastIndex) {
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MedicationAlertItem(
    alert: MedicationInventoryAlert,
    minQty: Int,
    modifier: Modifier = Modifier
) {
    val (badgeText, badgeBg, badgeTextColor, icon) = when (alert.alertStatus) {
        InventoryAlertStatus.CRITICAL_BOTH -> Quadruple(
            "CRITICAL: Low Qty (${alert.qty}) & Expiring",
            Color(0xFFFEE2E2),
            Color(0xFF991B1B),
            Icons.Default.WarningAmber
        )
        InventoryAlertStatus.EXPIRED -> Quadruple(
            "EXPIRED (${alert.expiry})",
            Color(0xFFFEE2E2),
            Color(0xFF991B1B),
            Icons.Default.HourglassBottom
        )
        InventoryAlertStatus.NEAR_EXPIRY -> Quadruple(
            if (alert.daysRemaining != null) "Expires in ${alert.daysRemaining} days" else "Near Expiry (≤ 2 Mo)",
            Color(0xFFFEF3C7),
            Color(0xFF92400E),
            Icons.Default.HourglassBottom
        )
        InventoryAlertStatus.LOW_STOCK -> Quadruple(
            "Low Stock: ${alert.qty} units left (≤ $minQty)",
            Color(0xFFFFEDD5),
            Color(0xFFC2410C),
            Icons.Default.ProductionQuantityLimits
        )
        InventoryAlertStatus.NORMAL -> Quadruple(
            "OK",
            Color(0xFFDCFCE7),
            Color(0xFF166534),
            Icons.Default.CheckCircle
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = NavyPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "📍 ${alert.shelf.ifBlank { "General" }}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NavyPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${if (alert.company.isNotBlank()) "${alert.company} • " else ""}${if (alert.batch.isNotBlank()) "Batch: ${alert.batch}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stock: ${alert.qty} units • Buy: ${formatCurrency(alert.purchasePrice)} • Sell: ${formatCurrency(alert.salePrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (alert.expiry.isNotBlank()) {
                Text(
                    text = "Expiry: ${alert.expiry}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (alert.isNearExpiry || alert.isExpired) FontWeight.Bold else FontWeight.Normal,
                    color = if (alert.isExpired) CrimsonRed else if (alert.isNearExpiry) AmberOrange else TextMuted
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
