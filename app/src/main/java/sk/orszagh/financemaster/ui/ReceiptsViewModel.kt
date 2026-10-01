package sk.orszagh.financemaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.orszagh.financemaster.data.ReceiptEntity
import sk.orszagh.financemaster.data.ReceiptRepository
import java.io.File

data class CaptureState(val saving: Boolean = false, val savedId: String? = null, val error: String? = null)

class ReceiptsViewModel(private val repository: ReceiptRepository) : ViewModel() {
    val processingError = repository.processingError
    val receipts = repository.receipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableCapture = MutableStateFlow(CaptureState())
    val capture: StateFlow<CaptureState> = mutableCapture.asStateFlow()

    fun takePhoto(captureImage: suspend (File) -> Unit) {
        if (mutableCapture.value.saving) return
        mutableCapture.value = CaptureState(saving = true)
        viewModelScope.launch {
            try {
                val id = repository.capture(captureImage)
                mutableCapture.value = CaptureState(savedId = id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableCapture.value = CaptureState(error = "Účtenku sa nepodarilo uložiť. Skontrolujte voľné miesto a skúste znova. Ak je fotografia uložená, obnoví sa pri ďalšom spustení.")
            }
        }
    }

    fun consumeCapture() { mutableCapture.value = CaptureState() }
    fun observe(id: String) = repository.observe(id)
    fun imageFile(receipt: ReceiptEntity) = repository.imageFile(receipt)
    fun retry(id: String) = repository.retry(id)
}
