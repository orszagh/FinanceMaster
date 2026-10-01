package sk.orszagh.financemaster

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sk.orszagh.financemaster.data.PrivateReceiptImageStore
import sk.orszagh.financemaster.data.ReceiptDatabase
import sk.orszagh.financemaster.data.ReceiptRepository
import sk.orszagh.financemaster.ocr.MlKitReceiptOcrService
import sk.orszagh.financemaster.parser.ConservativeReceiptParser

class FinanceMasterApplication : Application() {
    lateinit var repository: ReceiptRepository
        private set
    val recoveryError = MutableStateFlow(false)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, ReceiptDatabase::class.java, "receipts.db").build()
        repository = ReceiptRepository(
            database.receiptDao(), PrivateReceiptImageStore(filesDir),
            MlKitReceiptOcrService(this), ConservativeReceiptParser(), applicationScope,
        )
        applicationScope.launch {
            try {
                repository.recover()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recoveryError.value = true
            }
        }
    }
}
