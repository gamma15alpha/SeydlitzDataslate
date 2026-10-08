package space.seydlitz.dataslate.characters

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import space.seydlitz.dataslate.sync.DocumentStore
import space.seydlitz.dataslate.sync.DocumentTransaction
import space.seydlitz.dataslate.sync.StoredDocument

// D25: документы по (collection, key) — та же модель, что в IndexedDB веба; D68 — поля обмена.
@Entity(tableName = "documents", primaryKeys = ["collection", "key"])
data class DocumentEntity(
    val collection: String,
    val key: String,
    val json: String,
    val revision: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean,
    @ColumnInfo(name = "server_revision") val serverRevision: Long,
    val dirty: Boolean,
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE collection = :collection")
    suspend fun all(collection: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE collection = :collection AND `key` = :key")
    suspend fun get(collection: String, key: String): DocumentEntity?

    @Upsert
    suspend fun put(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE collection = :collection AND `key` = :key")
    suspend fun delete(collection: String, key: String)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Upsert
    suspend fun setMeta(meta: MetaEntity)
}

@Database(entities = [DocumentEntity::class, MetaEntity::class], version = 1)
abstract class DataslateDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao

    companion object {
        // Своя база на учётку: на общем устройстве анкеты не уходят на чужой аккаунт.
        fun open(context: Context, userId: String): DataslateDatabase =
            Room.databaseBuilder(context, DataslateDatabase::class.java, "dataslate-$userId.db").build()
    }
}

private const val CHARACTERS = "characters"
private const val CURSOR = "characters.cursor"

class RoomDocumentStore(private val db: DataslateDatabase) : DocumentStore {
    private val dao = db.documents()

    private val tx = object : DocumentTransaction {
        override suspend fun get(key: String) = dao.get(CHARACTERS, key)?.toDocument()
        override suspend fun put(doc: StoredDocument) = dao.put(doc.toEntity())
        override suspend fun delete(key: String) = dao.delete(CHARACTERS, key)
    }

    override suspend fun all() = dao.all(CHARACTERS).map { it.toDocument() }

    override suspend fun <R> transaction(block: suspend DocumentTransaction.() -> R): R = db.withTransaction { tx.block() }

    override suspend fun cursor() = dao.meta(CURSOR)?.toLongOrNull() ?: 0

    override suspend fun setCursor(cursor: Long) = dao.setMeta(MetaEntity(CURSOR, cursor.toString()))
}

private fun DocumentEntity.toDocument() = StoredDocument(key, json, revision, updatedAt, deleted, serverRevision, dirty)

private fun StoredDocument.toEntity() = DocumentEntity(CHARACTERS, key, json, revision, updatedAt, deleted, serverRevision, dirty)
