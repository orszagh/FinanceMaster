package sk.orszagh.financemaster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import sk.orszagh.financemaster.data.ReceiptEntity
import sk.orszagh.financemaster.data.ReceiptStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(receipts: List<ReceiptEntity>, onScan: () -> Unit, onHistory: () -> Unit, onDetail: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Vaše účtenky. Iba vo vašom telefóne.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("Odfotíte a uložíme. Rozpoznávanie textu prebehne automaticky aj bez internetu.")
            Spacer(Modifier.height(24.dp))
            Button(onScan, Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
                Text("SCAN RECEIPT", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Posledné účtenky", style = MaterialTheme.typography.titleMedium)
                TextButton(onHistory) { Text("História") }
            }
        }
        if (receipts.isEmpty()) item { Text("Zatiaľ žiadne účtenky. Začnite prvou fotografiou.") }
        items(receipts.take(5), key = { it.id }) { ReceiptCard(it) { onDetail(it.id) } }
    }
}

@Composable
fun HistoryScreen(receipts: List<ReceiptEntity>, onDetail: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Najnovšie účtenky sú hore.", style = MaterialTheme.typography.bodyMedium) }
        if (receipts.isEmpty()) item { Text("História je prázdna. Odfotografujte prvú účtenku na úvodnej obrazovke.") }
        items(receipts, key = { it.id }) { ReceiptCard(it) { onDetail(it.id) } }
    }
}

@Composable
private fun ReceiptCard(receipt: ReceiptEntity, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth().testTag("receipt-${receipt.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(receipt.merchant ?: "Účtenka", style = MaterialTheme.typography.titleMedium)
            Text(receipt.purchaseDate ?: createdAtLabel(receipt.createdAt))
            receipt.totalAmount?.let { Text(listOfNotNull(it, receipt.currency).joinToString(" "), style = MaterialTheme.typography.titleLarge) }
            Text(receipt.status.label(), style = MaterialTheme.typography.labelLarge,
                color = if (receipt.status == ReceiptStatus.OCR_FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

fun ReceiptStatus.label(): String = when (this) {
    ReceiptStatus.CAPTURED -> "Uložená – čaká na OCR"
    ReceiptStatus.PROCESSING -> "Rozpoznáva sa text…"
    ReceiptStatus.PROCESSED -> "Spracovaná"
    ReceiptStatus.OCR_FAILED -> "OCR zlyhalo – fotografia je uložená"
}

fun createdAtLabel(timestamp: Long): String = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
