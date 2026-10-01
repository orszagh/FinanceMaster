package sk.orszagh.financemaster.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReceiptStatus { CAPTURED, PROCESSING, PROCESSED, OCR_FAILED }

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val rawOcrText: String? = null,
    val merchant: String? = null,
    val purchaseDate: String? = null,
    val purchaseTime: String? = null,
    val totalAmount: String? = null,
    val currency: String? = null,
    val status: ReceiptStatus = ReceiptStatus.CAPTURED,
    val createdAt: Long,
)
