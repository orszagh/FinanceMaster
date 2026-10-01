package sk.orszagh.financemaster.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observe(id: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun find(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE status IN ('CAPTURED', 'PROCESSING') ORDER BY createdAt")
    suspend fun pending(): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: ReceiptEntity): Long

    @Update
    suspend fun update(receipt: ReceiptEntity)
}
