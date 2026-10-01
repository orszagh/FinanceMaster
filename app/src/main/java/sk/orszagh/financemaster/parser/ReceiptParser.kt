package sk.orszagh.financemaster.parser

data class ParsedReceipt(
    val merchant: String? = null,
    val purchaseDate: String? = null,
    val purchaseTime: String? = null,
    val totalAmount: String? = null,
    val currency: String? = null,
)

interface ReceiptParser {
    fun parse(rawText: String): ParsedReceipt
}
