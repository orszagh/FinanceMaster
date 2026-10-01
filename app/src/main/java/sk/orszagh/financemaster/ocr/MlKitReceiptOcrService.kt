package sk.orszagh.financemaster.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

class MlKitReceiptOcrService(private val context: Context) : ReceiptOcrService {
    override suspend fun recognize(image: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val input = InputImage.fromFilePath(context, Uri.fromFile(image))
            recognizer.process(input).await().text
        } finally {
            recognizer.close()
        }
    }
}
