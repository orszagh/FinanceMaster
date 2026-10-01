package sk.orszagh.financemaster.data

import java.io.File
import java.io.RandomAccessFile

interface ReceiptImageStore {
    fun temporaryFile(id: String): File
    fun commit(id: String): File
    fun resolve(imagePath: String): File
    fun savedImages(): List<File>
}

class PrivateReceiptImageStore(private val filesDir: File) : ReceiptImageStore {
    private val directory = File(filesDir, "receipts")

    override fun temporaryFile(id: String): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create receipt directory" }
        return File(directory, "$id.pending")
    }

    override fun commit(id: String): File {
        val pending = temporaryFile(id)
        check(pending.isFile && pending.length() > 0) { "Empty receipt image" }
        // CameraX dokončilo zápis. Sync pred premenovaním bráni spusteniu OCR nad neúplným súborom.
        RandomAccessFile(pending, "rw").use { it.fd.sync() }
        val saved = File(directory, "$id.jpg")
        check(!saved.exists() && pending.renameTo(saved)) { "Cannot commit receipt image" }
        return saved
    }

    override fun resolve(imagePath: String): File {
        val image = File(filesDir, imagePath).canonicalFile
        require(image.parentFile == directory.canonicalFile) { "Image outside private receipt directory" }
        return image
    }

    override fun savedImages(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "jpg" && it.length() > 0 }
        .orEmpty()
}
