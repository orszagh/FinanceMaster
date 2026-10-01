package sk.orszagh.financemaster.ocr

import java.io.File

interface ReceiptOcrService {
    suspend fun recognize(image: File): String
}
