package io.zer0.memory.fact

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FactTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tombstone: FactTombstoneEntity): Long

    @Query("SELECT normalized_fact FROM fact_deletion_tombstones WHERE scope = :scope AND space_id = :spaceId ORDER BY id")
    suspend fun getNormalizedFacts(scope: String, spaceId: String): List<String>

    @Query("SELECT normalized_fact FROM fact_deletion_tombstones ORDER BY id")
    suspend fun getAllNormalizedFacts(): List<String>

    @Query("DELETE FROM fact_deletion_tombstones WHERE scope = :scope AND space_id = :spaceId AND id NOT IN (" +
        "SELECT id FROM fact_deletion_tombstones WHERE scope = :scope AND space_id = :spaceId ORDER BY id DESC LIMIT :limit)")
    suspend fun trimScopeToLimit(scope: String, spaceId: String, limit: Int): Int

    @Query("DELETE FROM fact_deletion_tombstones")
    suspend fun deleteAll(): Int
}
