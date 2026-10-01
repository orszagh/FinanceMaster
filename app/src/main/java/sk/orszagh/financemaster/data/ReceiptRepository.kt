package sk.orszagh.financemaster.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sk.orszagh.financemaster.ocr.ReceiptOcrService
import sk.orszagh.financemaster.parser.ReceiptParser
import java.io.File
import java.util.UUID

class ReceiptRepository(
    private val dao: ReceiptDao,
    private val images: ReceiptImageStore,
    private val ocr: ReceiptOcrService,
    private val parser: ReceiptParser,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val receipts = dao.observeAll()
    private val mutableProcessingError = MutableStateFlow(false)
    val processingError = mutableProcessingError.asStateFlow()
    private val savingMutex = Mutex()
    private val processingMutex = Mutex()

    fun observe(id: String) = dao.observe(id)
    fun imageFile(receipt: ReceiptEntity): File = images.resolve(receipt.imagePath)

    suspend fun capture(takePhoto: suspend (File) -> Unit): String = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        savingMutex.withLock {
            val pending = images.temporaryFile(id)
            try {
                takePhoto(pending)
                val saved = images.commit(id)
                // Zlyhanie insertu nevymaže finálny obrázok; startup recovery ho nájde podľa UUID.
                dao.insert(ReceiptEntity(id, "receipts/${saved.name}", createdAt = System.currentTimeMillis()))
            } finally {
                pending.delete()
            }
        }
        enqueue(id)
        id
    }

    suspend fun recover() = withContext(ioDispatcher) {
        savingMutex.withLock {
            images.savedImages().forEach { file ->
                val id = file.nameWithoutExtension
                if (runCatching { UUID.fromString(id) }.isSuccess && dao.find(id) == null) {
                    dao.insert(ReceiptEntity(id, "receipts/${file.name}", createdAt = file.lastModified()))
                }
            }
        }
        dao.pending().forEach { process(it.id) }
    }

    fun retry(id: String) {
        enqueue(id, retryFailed = true)
    }

    private fun enqueue(id: String, retryFailed: Boolean = false) {
        scope.launch {
            try {
                process(id, retryFailed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Napríklad plný disk pri update: fotka zostáva a stav sa obnoví pri ďalšom štarte.
                mutableProcessingError.value = true
            }
        }
    }

    internal suspend fun process(id: String, retryFailed: Boolean = false) = withContext(ioDispatcher) {
        // Jedno OCR naraz znižuje pamäťový tlak a bráni súbežnému prepísaniu stavu pri retry.
        processingMutex.withLock {
            val receipt = dao.find(id) ?: return@withLock
            if (receipt.status == ReceiptStatus.PROCESSED ||
                (receipt.status == ReceiptStatus.OCR_FAILED && !retryFailed)
            ) return@withLock
            dao.update(receipt.copy(status = ReceiptStatus.PROCESSING))
            try {
                val text = ocr.recognize(images.resolve(receipt.imagePath))
                val withText = receipt.copy(rawOcrText = text, status = ReceiptStatus.PROCESSING)
                dao.update(withText)
                check(text.isNotBlank()) { "No readable receipt text" }
                val parsed = parser.parse(text)
                dao.update(withText.copy(
                    merchant = parsed.merchant,
                    purchaseDate = parsed.purchaseDate,
                    purchaseTime = parsed.purchaseTime,
                    totalAmount = parsed.totalAmount,
                    currency = parsed.currency,
                    status = ReceiptStatus.PROCESSED,
                ))
            } catch (cancelled: CancellationException) {
                // PROCESSING zostáva obnoviteľný po ukončení procesu; zrušenie nie je chyba OCR.
                throw cancelled
            } catch (_: Exception) {
                dao.find(id)?.let { dao.update(it.copy(status = ReceiptStatus.OCR_FAILED)) }
            }
        }
    }
}
