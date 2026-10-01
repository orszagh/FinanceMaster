package sk.orszagh.financemaster.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.orszagh.financemaster.data.ReceiptEntity
import sk.orszagh.financemaster.data.ReceiptStatus
import java.io.File

@Composable
fun ReceiptDetailScreen(receipt: ReceiptEntity?, imageFile: (ReceiptEntity) -> File, onRetry: () -> Unit) {
    if (receipt == null) {
        Text("Načítava sa účtenka… Ak záznam neexistuje, vráťte sa do histórie.", Modifier.padding(20.dp))
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ReceiptPhoto(imageFile(receipt))
        Text(receipt.status.label(), style = MaterialTheme.typography.titleMedium)
        if (receipt.status == ReceiptStatus.OCR_FAILED) {
            Text("Fotografia je bezpečne uložená. Text sa nepodarilo rozpoznať.")
            Button(onRetry) { Text("Zopakovať OCR") }
        }
        DetailField("Obchodník", receipt.merchant)
        DetailField("Dátum nákupu", receipt.purchaseDate)
        DetailField("Čas nákupu", receipt.purchaseTime)
        DetailField("Celková suma", receipt.totalAmount)
        DetailField("Mena", receipt.currency)
        DetailField("Uložené", createdAtLabel(receipt.createdAt))
        HorizontalDivider()
        Text("Kompletný OCR text", style = MaterialTheme.typography.titleLarge)
        SelectionContainer { Text(receipt.rawOcrText?.takeIf(String::isNotBlank) ?: "Zatiaľ žiadny rozpoznaný text.") }
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value ?: "Nerozpoznané", style = MaterialTheme.typography.bodyLarge)
    }
}

private data class PhotoState(val bitmap: Bitmap? = null, val loading: Boolean = true)

@Composable
private fun ReceiptPhoto(file: File) {
    val photo by produceState(PhotoState(), file.absolutePath) {
        value = withContext(Dispatchers.IO) { PhotoState(runCatching { decodePhoto(file) }.getOrNull(), loading = false) }
    }
    var expanded by remember { mutableStateOf(false) }
    val bitmap = photo.bitmap
    when {
        photo.loading -> CircularProgressIndicator()
        bitmap == null -> Text("Fotografiu sa nepodarilo načítať.", color = MaterialTheme.colorScheme.error)
        else -> {
            Image(bitmap.asImageBitmap(), "Pôvodná fotografia účtenky. Klepnutím zväčšíte.",
                Modifier.fillMaxWidth().height(320.dp).clickable { expanded = true }, contentScale = ContentScale.Fit)
            Text("Klepnite na fotografiu pre priblíženie.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (expanded && bitmap != null) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    TextButton({ expanded = false }) { Text("Zavrieť fotografiu") }
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }) {
                        Image(bitmap.asImageBitmap(), "Pôvodná fotografia účtenky",
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offset.x; translationY = offset.y
                            }, contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }
}

private fun decodePhoto(file: File): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    // Zobrazujeme orientáciu z EXIF; pôvodný súbor nemeníme a neprepisujeme kompresiou.
    val exif = ExifInterface(file)
    val matrix = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
