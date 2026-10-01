package sk.orszagh.financemaster.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.orszagh.financemaster.ocr.ReceiptOcrService
import sk.orszagh.financemaster.parser.ConservativeReceiptParser
import sk.orszagh.financemaster.parser.ReceiptParser
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val parser = ConservativeReceiptParser()

    @Test fun imageAndRecordExistBeforeOcrAndFailurePreservesBoth() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        val ocr = service { image ->
            assertTrue(image.isFile)
            assertArrayEquals(byteArrayOf(1, 2, 3), image.readBytes())
            assertEquals(ReceiptStatus.PROCESSING, dao.find(image.nameWithoutExtension)?.status)
            throw IllegalStateException("simulated OCR failure")
        }
        val repository = ReceiptRepository(dao, store, ocr, parser, this, StandardTestDispatcher(testScheduler))
        val id = repository.capture { it.writeBytes(byteArrayOf(1, 2, 3)) }
        advanceUntilIdle()
        assertEquals(ReceiptStatus.OCR_FAILED, dao.find(id)?.status)
        assertTrue(store.resolve(dao.find(id)!!.imagePath).exists())
        assertEquals(listOf(ReceiptStatus.CAPTURED, ReceiptStatus.PROCESSING, ReceiptStatus.OCR_FAILED), dao.states)
    }

    @Test fun successfulCaptureStoresTextAndExtractedFields() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        val raw = "Merchant: Shop\n2026-10-01\nTotal: 12.30 EUR"
        val repository = ReceiptRepository(dao, store, service { raw }, parser, this, StandardTestDispatcher(testScheduler))
        val id = repository.capture { it.writeText("photo") }
        advanceUntilIdle()
        val receipt = dao.find(id)!!
        assertEquals(ReceiptStatus.PROCESSED, receipt.status)
        assertEquals(raw, receipt.rawOcrText)
        assertEquals("Shop", receipt.merchant)
        assertEquals("12.30", receipt.totalAmount)
        assertTrue(store.resolve(receipt.imagePath).exists())
    }

    @Test fun failedPhotoCreatesNoRecordAndDoesNotRunOcr() = runTest {
        val dao = FakeDao()
        var calls = 0
        val store = PrivateReceiptImageStore(temporary.root)
        val repository = ReceiptRepository(dao, store, service { calls++; "text" }, parser, this, StandardTestDispatcher(testScheduler))
        try {
            repository.capture { it.writeText("partial"); error("camera failure") }
            fail("capture must fail")
        } catch (_: IllegalStateException) { }
        advanceUntilIdle()
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(store.savedImages().isEmpty())
        assertEquals(0, calls)
    }

    @Test fun databaseInsertFailureKeepsPhotoAndRecoveryCreatesRecord() = runTest {
        val dao = FakeDao().apply { failInsert = true }
        val store = PrivateReceiptImageStore(temporary.root)
        var calls = 0
        val repository = ReceiptRepository(dao, store, service { calls++; "Total: 10.00 EUR" }, parser, this, StandardTestDispatcher(testScheduler))
        try {
            repository.capture { it.writeText("photo") }
            fail("insert must fail")
        } catch (_: IllegalStateException) { }
        assertEquals(1, store.savedImages().size)
        assertEquals(0, calls)
        dao.failInsert = false
        repository.recover()
        val receipt = dao.rows.value.single()
        assertEquals(ReceiptStatus.PROCESSED, receipt.status)
        assertTrue(store.resolve(receipt.imagePath).exists())
        assertEquals(1, calls)
        repository.recover()
        assertEquals(1, dao.rows.value.size)
        assertEquals(1, calls)
    }

    @Test fun interruptedProcessingResumesWithoutDuplicatingReceipt() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        val id = UUID.randomUUID().toString()
        store.temporaryFile(id).writeText("photo")
        store.commit(id)
        dao.insert(ReceiptEntity(id, "receipts/$id.jpg", status = ReceiptStatus.PROCESSING, createdAt = 100))
        var calls = 0
        val repository = ReceiptRepository(dao, store, service { calls++; "Total: 5.50 EUR" }, parser, this, StandardTestDispatcher(testScheduler))
        repository.recover()
        assertEquals(ReceiptStatus.PROCESSED, dao.find(id)?.status)
        assertEquals(1, dao.rows.value.size)
        assertEquals(1, calls)
    }

    @Test fun parserFailurePreservesRawTextAndAllowsRetry() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        var failParser = true
        val repository = ReceiptRepository(dao, store, service { "Total: 1.20 EUR" }, object : ReceiptParser {
            override fun parse(rawText: String) = if (failParser) error("parser failure") else parser.parse(rawText)
        }, this, StandardTestDispatcher(testScheduler))
        val id = repository.capture { it.writeText("photo") }
        advanceUntilIdle()
        assertEquals(ReceiptStatus.OCR_FAILED, dao.find(id)?.status)
        assertEquals("Total: 1.20 EUR", dao.find(id)?.rawOcrText)
        failParser = false
        repository.retry(id)
        advanceUntilIdle()
        assertEquals(ReceiptStatus.PROCESSED, dao.find(id)?.status)
        assertTrue(store.resolve(dao.find(id)!!.imagePath).exists())
    }

    @Test fun blankOcrIsRetryableAndImageRemains() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        val repository = ReceiptRepository(dao, store, service { "" }, parser, this, StandardTestDispatcher(testScheduler))
        val id = repository.capture { it.writeText("photo") }
        advanceUntilIdle()
        assertEquals(ReceiptStatus.OCR_FAILED, dao.find(id)?.status)
        assertEquals("", dao.find(id)?.rawOcrText)
        assertTrue(store.resolve(dao.find(id)!!.imagePath).exists())
    }

    @Test fun cancellationLeavesProcessingRecoverable() = runTest {
        val dao = FakeDao()
        val store = PrivateReceiptImageStore(temporary.root)
        val id = UUID.randomUUID().toString()
        store.temporaryFile(id).writeText("photo")
        store.commit(id)
        dao.insert(ReceiptEntity(id, "receipts/$id.jpg", createdAt = 100))
        val repository = ReceiptRepository(dao, store, service { throw CancellationException() }, parser, this, StandardTestDispatcher(testScheduler))
        try { repository.process(id); fail("must cancel") } catch (_: CancellationException) { }
        assertEquals(ReceiptStatus.PROCESSING, dao.find(id)?.status)
        assertTrue(store.resolve(dao.find(id)!!.imagePath).exists())
    }

    private fun service(block: suspend (File) -> String) = object : ReceiptOcrService {
        override suspend fun recognize(image: File) = block(image)
    }

    private class FakeDao : ReceiptDao {
        val rows = MutableStateFlow<List<ReceiptEntity>>(emptyList())
        val states = mutableListOf<ReceiptStatus>()
        var failInsert = false
        override fun observeAll(): Flow<List<ReceiptEntity>> = rows
        override fun observe(id: String) = rows.map { list -> list.find { it.id == id } }
        override suspend fun find(id: String) = rows.value.find { it.id == id }
        override suspend fun pending() = rows.value.filter { it.status in listOf(ReceiptStatus.CAPTURED, ReceiptStatus.PROCESSING) }
        override suspend fun insert(receipt: ReceiptEntity): Long {
            check(!failInsert)
            if (find(receipt.id) != null) return -1
            rows.value += receipt
            states += receipt.status
            return rows.value.size.toLong()
        }
        override suspend fun update(receipt: ReceiptEntity) {
            rows.value = rows.value.map { if (it.id == receipt.id) receipt else it }
            states += receipt.status
        }
    }
}
