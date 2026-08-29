package com.example.invoiceapp

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class InvoiceRow(
    val id: String = UUID.randomUUID().toString(),
    val details: String = "",
    val amount: String = "",
    val balance: String = "0.00"
)

data class SavedInvoice(
    val id: String = UUID.randomUUID().toString(),
    val customerName: String,
    val date: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val items: List<InvoiceRow>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF10B981),
                    secondary = Color(0xFF0D9488),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF0F172A)
                    ) {
                        MainAppScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: الفاتورة, 1: حسابات العملاء
    
    var customerName by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf(InvoiceRow(), InvoiceRow()) }
    
    val savedInvoices = remember { mutableStateListOf<SavedInvoice>() }
    var selectedCustomerForStatement by remember { mutableStateOf<String?>(null) }
    var selectedCustomerForPayment by remember { mutableStateOf<String?>(null) }
    var paymentInputAmount by remember { mutableStateOf("") }
    
    var searchQuery by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = loadInvoicesFromStorage(context)
        savedInvoices.clear()
        savedInvoices.addAll(loaded)
    }

    val totalAmount = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val paid = paidAmount.toDoubleOrNull() ?: 0.0
    val remaining = totalAmount - paid

    val currentDate = remember {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        sdf.format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        HeaderLogoSection()

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabButton(
                title = "👥 حسابات العملاء",
                isSelected = activeTab == 1,
                modifier = Modifier.weight(1f)
            ) {
                activeTab = 1
            }
            TabButton(
                title = "📝 الفاتورة الحالية",
                isSelected = activeTab == 0,
                modifier = Modifier.weight(1f)
            ) {
                activeTab = 0
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 0) {
                InvoiceFormScreen(
                    currentDate = currentDate,
                    customerName = customerName,
                    onCustomerNameChange = { customerName = it },
                    rows = rows,
                    paidAmount = paidAmount,
                    onPaidAmountChange = { paidAmount = it },
                    totalAmount = totalAmount,
                    remaining = remaining,
                    onAddNewRow = { rows.add(InvoiceRow()) },
                    onRemoveRow = { index -> if (rows.size > 1) rows.removeAt(index) },
                    onUpdateRow = { index, updatedRow -> rows[index] = updatedRow }
                )
            } else {
                CustomerAccountsScreen(
                    invoices = savedInvoices,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onViewStatement = { customer -> selectedCustomerForStatement = customer },
                    onPayDebt = { customer -> selectedCustomerForPayment = customer },
                    onDeleteCustomer = { customer ->
                        val toRemove = savedInvoices.filter { it.customerName.trim().equals(customer.trim(), ignoreCase = true) }
                        savedInvoices.removeAll(toRemove)
                        saveInvoicesToStorage(context, savedInvoices)
                        Toast.makeText(context, "تم مسح حساب العميل وفواتيره", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        if (activeTab == 0) {
            Spacer(modifier = Modifier.height(10.dp))
            BottomActionBar(
                onNewInvoice = { showResetDialog = true },
                onSaveInvoice = {
                    if (customerName.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة اسم العميل أولاً", Toast.LENGTH_SHORT).show()
                    } else if (totalAmount <= 0) {
                        Toast.makeText(context, "يرجى إضافة مبالغ للفاتورة", Toast.LENGTH_SHORT).show()
                    } else {
                        val invoice = SavedInvoice(
                            customerName = customerName.trim(),
                            date = currentDate,
                            totalAmount = totalAmount,
                            paidAmount = paid,
                            remainingAmount = remaining,
                            items = rows.toList()
                        )
                        savedInvoices.add(0, invoice)
                        saveInvoicesToStorage(context, savedInvoices)
                        Toast.makeText(context, "تم حفظ الفاتورة في حساب العميل بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                },
                onPrintInvoice = {
                    if (customerName.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة اسم العميل للطباعة", Toast.LENGTH_SHORT).show()
                    } else {
                        printThermalInvoice(context, customerName, currentDate, rows, totalAmount, paid, remaining)
                    }
                },
                onShareInvoice = {
                    if (customerName.isBlank()) {
                        Toast.makeText(context, "يرجى إدخال اسم العميل للمشاركة", Toast.LENGTH_SHORT).show()
                    } else {
                        shareInvoiceText(context, customerName, currentDate, rows, totalAmount, paid, remaining)
                    }
                }
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("تصفير الفاتورة", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("هل أنت محتار أو تريد تصفير الفاتورة الحالية والبدء بصفحة جديدة؟", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        customerName = ""
                        paidAmount = ""
                        rows.clear()
                        rows.add(InvoiceRow())
                        rows.add(InvoiceRow())
                        showResetDialog = false
                        Toast.makeText(context, "تم تصفير الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("تصفير")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Modal Statement Dialog
    selectedCustomerForStatement?.let { customer ->
        val customerInvoices = savedInvoices.filter { it.customerName.trim().equals(customer.trim(), ignoreCase = true) }
        val totalPurchases = customerInvoices.sumOf { it.totalAmount }
        val totalPaid = customerInvoices.sumOf { it.paidAmount }
        val netDebt = totalPurchases - totalPaid

        AlertDialog(
            onDismissRequest = { selectedCustomerForStatement = null },
            title = {
                Text("كشف حساب: ${customer}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("إجمالي المشتريات: ${String.format(Locale.US, "%.2f", totalPurchases)} ر.ي", color = Color.White)
                    Text("إجمالي الواصل: ${String.format(Locale.US, "%.2f", totalPaid)} ر.ي", color = Color(0xFF10B981))
                    Text("الرصيد المتبقي عليه: ${String.format(Locale.US, "%.2f", netDebt)} ر.ي", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("الفواتير السابقة (${customerInvoices.size}):", color = Color.Gray, fontSize = 12.sp)
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(customerInvoices) { inv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(inv.date, color = Color.LightGray, fontSize = 12.sp)
                                Text("${String.format(Locale.US, "%.2f", inv.totalAmount)} ر.ي", color = Color.White, fontSize = 12.sp)
                            }
                            HorizontalDivider(color = Color(0xFF334155))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedCustomerForStatement = null
                        Toast.makeText(context, "تم إغلاق الكشف", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("تم")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Payment Dialog
    selectedCustomerForPayment?.let { customer ->
        AlertDialog(
            onDismissRequest = { selectedCustomerForPayment = null },
            title = { Text("تسديد دفعة لحساب: ${customer}", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = paymentInputAmount,
                    onValueChange = { paymentInputAmount = it },
                    label = { Text("المبلغ الواصل (ر.ي)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentInputAmount.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            val payInv = SavedInvoice(
                                customerName = customer,
                                date = currentDate,
                                totalAmount = 0.0,
                                paidAmount = amount,
                                remainingAmount = -amount,
                                items = listOf(InvoiceRow(details = "دفعة حساب مسددة"))
                            )
                            savedInvoices.add(0, payInv)
                            saveInvoicesToStorage(context, savedInvoices)
                            Toast.makeText(context, "تم تسجيل تسديد ${amount} ر.ي بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        paymentInputAmount = ""
                        selectedCustomerForPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("تأكيد التسديد")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCustomerForPayment = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun HeaderLogoSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "بقالة العزي للمواد الغذائية",
                    color = Color(0xFFF8FAFC),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "نظام الفواتير المباشر وحسابات العملاء",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun InvoiceFormScreen(
    currentDate: String,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    rows: List<InvoiceRow>,
    paidAmount: String,
    onPaidAmountChange: (String) -> Unit,
    totalAmount: Double,
    remaining: Double,
    onAddNewRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onUpdateRow: (Int, InvoiceRow) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentDate, color = Color.DarkGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("العميل: ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Column {
                        BasicTextField(
                            value = customerName,
                            onValueChange = onCustomerNameChange,
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (customerName.isEmpty()) {
                                    Text("اسم العميل...", color = Color.Gray, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(130.dp)
                                .height(2.dp)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حذف", modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("الرصيد", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("التفاصيل", modifier = Modifier.weight(2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("عليه", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                HorizontalDivider(color = Color(0xFFCBD5E1))

                LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
                    itemsIndexed(rows) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onRemoveRow(index) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                text = if (item.amount.isNotBlank()) String.format(Locale.US, "%.2f", item.amount.toDoubleOrNull() ?: 0.0) else "0.00",
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.Center,
                                color = Color.Black,
                                fontSize = 13.sp
                            )
                            BasicTextField(
                                value = item.details,
                                onValueChange = { onUpdateRow(index, item.copy(details = it)) },
                                modifier = Modifier.weight(2f).padding(horizontal = 4.dp),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (item.details.isEmpty()) Text("التفاصيل...", color = Color.LightGray, fontSize = 12.sp)
                                    inner()
                                }
                            )
                            BasicTextField(
                                value = item.amount,
                                onValueChange = { onUpdateRow(index, item.copy(amount = it, balance = it)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (item.amount.isEmpty()) Text("0.00", color = Color.LightGray, fontSize = 12.sp)
                                    inner()
                                }
                            )
                        }
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAddNewRow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة سطر", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = onPaidAmountChange,
                    modifier = Modifier.width(160.dp).height(48.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(6.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("الواصل:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(String.format(Locale.US, "%.2f", totalAmount), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Text("إجمالي عليه:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(String.format(Locale.US, "%.2f", remaining), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                Text("الصافي المتبقي:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun CustomerAccountsScreen(
    invoices: List<SavedInvoice>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onViewStatement: (String) -> Unit,
    onPayDebt: (String) -> Unit,
    onDeleteCustomer: (String) -> Unit
) {
    val grouped = remember(invoices) {
        invoices.groupBy { it.customerName.trim() }
    }

    val filteredCustomers = remember(grouped, searchQuery) {
        grouped.keys.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("🔍 بحث عن حساب عميل...", color = Color.Gray, fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = Color(0xFF10B981),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.List, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لا توجد حسابات عملاء مجمعة حتى الآن", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCustomers) { customer ->
                    val customerInvoices = grouped[customer] ?: emptyList()
                    val totalPurchases = customerInvoices.sumOf { it.totalAmount }
                    val totalPaid = customerInvoices.sumOf { it.paidAmount }
                    val remainingDebt = totalPurchases - totalPaid

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(customer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${String.format(Locale.US, "%.2f", remainingDebt)} ر.ي", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المشتريات: ${String.format(Locale.US, "%.2f", totalPurchases)}", color = Color.Gray, fontSize = 12.sp)
                                Text("الواصل: ${String.format(Locale.US, "%.2f", totalPaid)}", color = Color(0xFF10B981), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { onViewStatement(customer) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("كشف الحساب", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onPayDebt(customer) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("تسديد", fontSize = 12.sp)
                                }
                                IconButton(onClick = { onDeleteCustomer(customer) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomActionBar(
    onNewInvoice: () -> Unit,
    onSaveInvoice: () -> Unit,
    onPrintInvoice: () -> Unit,
    onShareInvoice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ActionButton(
            title = "جديد",
            icon = Icons.Default.Delete,
            color = Color(0xFFEF4444),
            modifier = Modifier.weight(1f),
            onClick = onNewInvoice
        )
        ActionButton(
            title = "صورة",
            icon = Icons.Default.Share,
            color = Color(0xFF0D9488),
            modifier = Modifier.weight(1f),
            onClick = onShareInvoice
        )
        ActionButton(
            title = "حفظ",
            icon = Icons.Default.Done,
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f),
            onClick = onSaveInvoice
        )
        ActionButton(
            title = "طباعة",
            icon = Icons.Default.Refresh,
            color = Color(0xFF2563EB),
            modifier = Modifier.weight(1f),
            onClick = onPrintInvoice
        )
    }
}

@Composable
fun ActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private const val PREFS_NAME = "ezzi_grocery_prefs"
private const val KEY_INVOICES = "saved_invoices_json"

fun saveInvoicesToStorage(context: Context, invoices: List<SavedInvoice>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonArray = JSONArray()

    for (inv in invoices) {
        val obj = JSONObject().apply {
            put("id", inv.id)
            put("customerName", inv.customerName)
            put("date", inv.date)
            put("totalAmount", inv.totalAmount)
            put("paidAmount", inv.paidAmount)
            put("remainingAmount", inv.remainingAmount)

            val itemsArr = JSONArray()
            for (item in inv.items) {
                val itemObj = JSONObject().apply {
                    put("id", item.id)
                    put("details", item.details)
                    put("amount", item.amount)
                    put("balance", item.balance)
                }
                itemsArr.put(itemObj)
            }
            put("items", itemsArr)
        }
        jsonArray.put(obj)
    }

    prefs.edit().putString(KEY_INVOICES, jsonArray.toString()).apply()
}

fun loadInvoicesFromStorage(context: Context): List<SavedInvoice> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(KEY_INVOICES, null) ?: return emptyList()
    val result = mutableListOf<SavedInvoice>()

    try {
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val itemsArr = obj.getJSONArray("items")
            val itemsList = mutableListOf<InvoiceRow>()

            for (j in 0 until itemsArr.length()) {
                val itemObj = itemsArr.getJSONObject(j)
                itemsList.add(
                    InvoiceRow(
                        id = itemObj.optString("id", UUID.randomUUID().toString()),
                        details = itemObj.optString("details", ""),
                        amount = itemObj.optString("amount", ""),
                        balance = itemObj.optString("balance", "0.00")
                    )
                )
            }

            result.add(
                SavedInvoice(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    customerName = obj.optString("customerName", ""),
                    date = obj.optString("date", ""),
                    totalAmount = obj.optDouble("totalAmount", 0.0),
                    paidAmount = obj.optDouble("paidAmount", 0.0),
                    remainingAmount = obj.optDouble("remainingAmount", 0.0),
                    items = itemsList
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return result
}

fun printThermalInvoice(
    context: Context,
    customerName: String,
    date: String,
    items: List<InvoiceRow>,
    total: Double,
    paid: Double,
    remaining: Double
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            val pdi = PrintDocumentInfo.Builder("Invoice_${customerName}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback?.onLayoutFinished(pdi, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
            }

            var y = 30f
            canvas.drawText("بقالة العزي للمواد الغذائية", 60f, y, paint)
            y += 20f
            canvas.drawText("التاريخ: $date", 20f, y, paint)
            y += 18f
            canvas.drawText("العميل: $customerName", 20f, y, paint)
            y += 25f
            canvas.drawLine(10f, y, 290f, y, paint)
            y += 20f

            for (item in items) {
                if (item.details.isNotBlank() || item.amount.isNotBlank()) {
                    canvas.drawText("${item.details} : ${item.amount} ر.ي", 20f, y, paint)
                    y += 18f
                }
            }

            y += 10f
            canvas.drawLine(10f, y, 290f, y, paint)
            y += 20f
            canvas.drawText("الإجمالي: ${String.format(Locale.US, "%.2f", total)} ر.ي", 20f, y, paint)
            y += 18f
            canvas.drawText("الواصل: ${String.format(Locale.US, "%.2f", paid)} ر.ي", 20f, y, paint)
            y += 18f
            canvas.drawText("المتبقي: ${String.format(Locale.US, "%.2f", remaining)} ر.ي", 20f, y, paint)

            pdfDocument.finishPage(page)

            try {
                pdfDocument.writeTo(FileOutputStream(destination?.fileDescriptor))
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            } finally {
                pdfDocument.close()
            }
        }
    }

    printManager.print("Invoice_${customerName}", printAdapter, PrintAttributes.Builder().build())
}

fun shareInvoiceText(
    context: Context,
    customerName: String,
    date: String,
    items: List<InvoiceRow>,
    total: Double,
    paid: Double,
    remaining: Double
) {
    val sb = StringBuilder()
    sb.append("🧾 *بقالة العزي للمواد الغذائية*\n")
    sb.append("-----------------------------\n")
    sb.append("👤 *العميل:* $customerName\n")
    sb.append("📅 *التاريخ:* $date\n")
    sb.append("-----------------------------\n")
    for (item in items) {
        if (item.details.isNotBlank() || item.amount.isNotBlank()) {
            sb.append("▫️ ${item.details}: ${item.amount} ر.ي\n")
        }
    }
    sb.append("-----------------------------\n")
    sb.append("💰 *الإجمالي:* ${String.format(Locale.US, "%.2f", total)} ر.ي\n")
    sb.append("💵 *الواصل:* ${String.format(Locale.US, "%.2f", paid)} ر.ي\n")
    sb.append("🔴 *الصافي المتبقي:* ${String.format(Locale.US, "%.2f", remaining)} ر.ي\n")
    sb.append("-----------------------------\n")
    sb.append("شكراً لتعاملكم معنا!")

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "مشاركة الفاتورة عبر:"))
}
