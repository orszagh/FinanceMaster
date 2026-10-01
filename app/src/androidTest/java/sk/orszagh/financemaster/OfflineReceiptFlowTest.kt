package sk.orszagh.financemaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import sk.orszagh.financemaster.data.*
import sk.orszagh.financemaster.ocr.MlKitReceiptOcrService
import sk.orszagh.financemaster.parser.ConservativeReceiptParser
import sk.orszagh.financemaster.ui.FinanceMasterApp
import sk.orszagh.financemaster.ui.ReceiptsViewModel
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineReceiptFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledOcrToRoomPersistsOriginalImageAndDataAfterReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.filesDir, "integration-${System.nanoTime()}").apply { mkdirs() }
        val name = "integration-${System.nanoTime()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val database = Room.databaseBuilder(context, ReceiptDatabase::class.java, name).build()
        val viewModelStore = ViewModelStore()
        var showApp by mutableStateOf(true)
        try {
            val store = PrivateReceiptImageStore(root)
            val repository = ReceiptRepository(database.receiptDao(), store, MlKitReceiptOcrService(context), ConservativeReceiptParser(), scope)
            val model = ReceiptsViewModel(repository)
            viewModelStore.put("receipts", model)
            compose.setContent { if (showApp) FinanceMasterApp(model, MutableStateFlow(false)) }
            compose.onNodeWithText("SCAN RECEIPT").assertIsDisplayed()
            val id = repository.capture { file ->
                val bitmap = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
                listOf("Merchant: Test Shop", "2026-10-01", "12:30", "TOTAL: 12.30 EUR").forEachIndexed { index, line ->
                    canvas.drawText(line, 80f, 150f + index * 160f, paint)
                }
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                bitmap.recycle()
            }
            val processed = withTimeout(60_000) {
                repository.observe(id).first { it?.status in listOf(ReceiptStatus.PROCESSED, ReceiptStatus.OCR_FAILED) }!!
            }
            assertEquals(ReceiptStatus.PROCESSED, processed.status)
            assertTrue(processed.rawOcrText!!.contains("12.30"))
            assertEquals("12.30", processed.totalAmount)
            assertEquals("EUR", processed.currency)
            assertEquals("Test Shop", processed.merchant)
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Test Shop").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("História").performClick()
            compose.onNodeWithText("Test Shop").performClick()
            compose.onNodeWithText("Detail účtenky").assertIsDisplayed()
            val imageDescription = "Pôvodná fotografia účtenky. Klepnutím zväčšíte."
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription(imageDescription).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(imageDescription).assertIsDisplayed()
            compose.onNodeWithText("12.30").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(processed.rawOcrText!!).performScrollTo().assertIsDisplayed()
            val original = repository.imageFile(processed).readBytes()
            assertTrue(original.isNotEmpty())
            compose.runOnIdle { showApp = false }
            compose.waitForIdle()
            compose.runOnIdle { viewModelStore.clear() }
            database.close()
            val reopened = Room.databaseBuilder(context, ReceiptDatabase::class.java, name).build()
            try {
                val persisted = reopened.receiptDao().find(id)!!
                assertEquals(processed, persisted)
                assertArrayEquals(original, store.resolve(persisted.imagePath).readBytes())
            } finally { reopened.close() }
        } finally {
            compose.runOnIdle { showApp = false; viewModelStore.clear() }
            compose.waitForIdle()
            scope.cancel()
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }
}
