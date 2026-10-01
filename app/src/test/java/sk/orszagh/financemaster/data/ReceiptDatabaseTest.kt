package sk.orszagh.financemaster.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ReceiptDatabaseTest {
    @Test fun realRoomDatabaseKeepsFieldsAndOrderingAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, ReceiptDatabase::class.java, name).build()
        val first = ReceiptEntity("older", "receipts/older.jpg", rawOcrText = "Complete raw OCR\nsecond line",
            merchant = "Test Shop", purchaseDate = "2026-10-01", purchaseTime = "12:30",
            totalAmount = "12.30", currency = "EUR", status = ReceiptStatus.PROCESSED, createdAt = 1)
        val newer = ReceiptEntity("newer", "receipts/newer.jpg", createdAt = 2)
        val database = open()
        try {
            database.receiptDao().insert(first)
            database.receiptDao().insert(newer)
        } finally { database.close() }
        val reopened = open()
        try {
            assertEquals(first, reopened.receiptDao().find("older"))
            assertEquals(listOf("newer", "older"), reopened.receiptDao().observeAll().first().map { it.id })
            assertEquals(listOf(newer), reopened.receiptDao().pending())
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
