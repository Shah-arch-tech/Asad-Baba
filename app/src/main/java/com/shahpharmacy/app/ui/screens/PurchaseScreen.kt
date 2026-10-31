package com.shahpharmacy.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahpharmacy.app.model.PurchaseEntity
import com.shahpharmacy.app.ui.PharmacyViewModel
import com.shahpharmacy.app.ui.components.HeroBanner
import com.shahpharmacy.app.ui.components.formatCurrency
import com.shahpharmacy.app.ui.theme.BorderColor
import com.shahpharmacy.app.ui.theme.CrimsonRed
import com.shahpharmacy.app.ui.theme.EmeraldGreen
import com.shahpharmacy.app.ui.theme.NavyPrimary
import com.shahpharmacy.app.ui.theme.TextDark
import com.shahpharmacy.app.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PurchaseScreen(
    viewModel: PharmacyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var qtyStr by remember { mutableStateOf("") }
    var purchasePriceStr by remember { mutableStateOf("") }
    var salePriceStr by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayStr) }
    var batch by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var shelf by remember { mutableStateOf("Shelf A-1") }

    // File Upload States
    var showFilePreviewDialog by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileContent by remember { mutableStateOf("") }
    var parsedOrders by remember { mutableStateOf<List<PurchaseEntity>>(emptyList()) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    // Manual Paste CSV Dialog State
    var showCsvDialog by remember { mutableStateOf(false) }
    var csvText by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<PurchaseEntity?>(null) }

    val quickShelves = listOf("Shelf A-1", "Shelf A-2", "Shelf B-1", "Rack 1", "Rack 2", "Cold Storage", "General")

    // File Picker Launcher for CSV, TSV, JSON, TXT files
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "order_file.csv"
                    selectedFileName = fileName
                    selectedFileContent = content
                    parsedOrders = viewModel.parsePurchaseOrdersPreview(content)
                    showFilePreviewDialog = true
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HeroBanner(title = "Purchase Orders & Stock Inward")

        // SECTION 1: Purchase Order Creation Through File Upload Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                .testTag("upload_purchase_file_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NavyPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Upload Order",
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Purchase Order File Upload",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Bulk order ingestion with shelf separation (CSV, TSV, JSON)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    TextButton(
                        onClick = { showTemplateDialog = true },
                        modifier = Modifier.testTag("view_template_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Template", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { fileLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_file_picker_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select File to Upload")
                    }

                    OutlinedButton(
                        onClick = { showCsvDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("import_csv_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste CSV")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Supported headers: name, company, qty, purchase, sale, date, batch, expiry, shelf",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: Manual Purchase Form with Shelf Selection
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add Individual Medicine Purchase",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine name *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary,
                        focusedLabelColor = NavyPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_medicine_name")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Manufacturer") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary,
                        focusedLabelColor = NavyPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Shelf / Rack Location Assignment
                OutlinedTextField(
                    value = shelf,
                    onValueChange = { shelf = it },
                    label = { Text("Shelf / Rack Location *") },
                    leadingIcon = {
                        Icon(Icons.Default.Place, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary,
                        focusedLabelColor = NavyPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_medicine_shelf")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Shelf Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickShelves.forEach { quickShelf ->
                        val isSelected = shelf.equals(quickShelf, ignoreCase = true)
                        SuggestionChip(
                            onClick = { shelf = quickShelf },
                            label = { Text(quickShelf, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) NavyPrimary.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
                                labelColor = if (isSelected) NavyPrimary else TextDark
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) NavyPrimary else Color.Transparent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Quantity *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_medicine_qty")
                    )

                    OutlinedTextField(
                        value = purchasePriceStr,
                        onValueChange = { purchasePriceStr = it },
                        label = { Text("Buy Price/unit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_medicine_purchase_price")
                    )

                    OutlinedTextField(
                        value = salePriceStr,
                        onValueChange = { salePriceStr = it },
                        label = { Text("Sale Price/unit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_medicine_sale_price")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = batch,
                        onValueChange = { batch = it },
                        label = { Text("Batch #") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it },
                        label = { Text("Expiry (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            focusedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val q = qtyStr.toIntOrNull() ?: 0
                        val p = purchasePriceStr.toDoubleOrNull() ?: 0.0
                        val s = salePriceStr.toDoubleOrNull() ?: 0.0
                        viewModel.addPurchase(
                            name = name,
                            company = company,
                            qty = q,
                            purchasePrice = p,
                            salePrice = s,
                            date = date,
                            batch = batch,
                            expiry = expiry,
                            shelf = shelf
                        ) {
                            name = ""
                            company = ""
                            qtyStr = ""
                            purchasePriceStr = ""
                            salePriceStr = ""
                            batch = ""
                            expiry = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_purchase_button")
                ) {
                    Text("Save Purchase to $shelf")
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 3: Recent Purchases Table with Shelf Badge
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Purchases (${purchases.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Total: ${formatCurrency(purchases.sumOf { it.total })}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (purchases.isEmpty()) {
                    Text(
                        text = "No purchases yet. Add your first medicine purchase or upload an order file above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    purchases.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = NavyPrimary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "📍 ${item.shelf.ifBlank { "General" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${if (item.company.isNotBlank()) "${item.company} • " else ""}Qty: ${item.qty} • Buy: ${formatCurrency(item.purchase)} • Sale: ${formatCurrency(item.sale)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Date: ${item.date}${if (item.batch.isNotBlank()) " • Batch: ${item.batch}" else ""}${if (item.expiry.isNotBlank()) " • Exp: ${item.expiry}" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatCurrency(item.total),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                IconButton(
                                    onClick = { itemToDelete = item },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete purchase",
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (index < purchases.lastIndex) {
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // DIALOG: File Upload Preview & Confirmation
    if (showFilePreviewDialog) {
        val totalQty = parsedOrders.sumOf { it.qty }
        val totalValuation = parsedOrders.sumOf { it.total }
        val distinctShelves = parsedOrders.map { it.shelf.ifBlank { "General" } }.distinct()

        AlertDialog(
            onDismissRequest = { showFilePreviewDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Purchase Order Import")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "File: $selectedFileName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detected ${parsedOrders.size} medicines • Total Qty: $totalQty • Valuation: ${formatCurrency(totalValuation)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Shelves in file: ${distinctShelves.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NavyPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Preview of items to import:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            parsedOrders.take(6).forEachIndexed { idx, itm ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}. ${itm.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Qty: ${itm.qty} • Buy: ${formatCurrency(itm.purchase)} • Shelf: ${itm.shelf}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = formatCurrency(itm.total),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                }
                                if (idx < minOf(parsedOrders.lastIndex, 5)) {
                                    HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                            if (parsedOrders.size > 6) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+ ${parsedOrders.size - 6} more orders...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importPurchasesFile(selectedFileContent) { count, _ ->
                            showFilePreviewDialog = false
                            selectedFileContent = ""
                            parsedOrders = emptyList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.testTag("confirm_import_orders_button")
                ) {
                    Text("Import ${parsedOrders.size} Orders")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilePreviewDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Template Viewer
    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Purchase Order File Templates") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "1. CSV / TSV Format (Recommended):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        Text(
                            text = "name,company,qty,purchase,sale,date,batch,expiry,shelf\n" +
                                   "Panadol 500mg,GSK,100,2.5,3.0,2026-10-01,B102,2027-10-01,Shelf A-1\n" +
                                   "Augmentin 625mg,GSK,50,15.0,18.0,2026-10-01,B204,2026-12-15,Shelf A-2\n" +
                                   "Insulin Glargine,Sanofi,20,45.0,55.0,2026-10-01,B301,2027-04-01,Cold Storage",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "2. JSON Format:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        Text(
                            text = "[\n" +
                                   "  {\n" +
                                   "    \"name\": \"Brufen 400mg\",\n" +
                                   "    \"company\": \"Abbott\",\n" +
                                   "    \"qty\": 80,\n" +
                                   "    \"purchase\": 3.2,\n" +
                                   "    \"sale\": 4.0,\n" +
                                   "    \"shelf\": \"Shelf B-1\"\n" +
                                   "  }\n" +
                                   "]",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTemplateDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Got It")
                }
            }
        )
    }

    // DIALOG: Delete Purchase
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Purchase?") },
            text = { Text("Are you sure you want to delete '${itemToDelete?.name}' from purchase records?") },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.deletePurchase(it) }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Paste CSV
    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Import Purchases via CSV") },
            text = {
                Column {
                    Text(
                        text = "Paste comma-separated rows below. E.g.:\nname,company,qty,purchase,sale,date,shelf\nPanadol 500mg,GSK,100,2.5,3.0,2026-10-01,Shelf A-1",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        placeholder = { Text("name,company,qty,purchase,sale,date,shelf\n...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (csvText.isNotBlank()) {
                            viewModel.importPurchasesFile(csvText) { count, _ ->
                                showCsvDialog = false
                                csvText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
