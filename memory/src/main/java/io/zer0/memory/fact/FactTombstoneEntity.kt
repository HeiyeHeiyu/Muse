package io.zer0.memory.fact

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A durable deletion marker, isolated by the owning FactDb, memory scope, and space. */
@Entity(
    tableName = "fact_deletion_tombstones",
    indices = [
        Index(
            value = ["scope", "space_id", "normalized_fact"],
            name = "index_fact_deletion_tombstones_scope_space_id_normalized_fact",
            unique = true,
        ),
    ],
)
data class FactTombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "normalized_fact")
    val normalizedFact: String,
    @ColumnInfo(name = "scope")
    val scope: String,
    @ColumnInfo(name = "space_id")
    val spaceId: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String,
)
