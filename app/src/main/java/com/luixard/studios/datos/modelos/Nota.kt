package com.luixard.studios.datos.modelos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notas")
data class Nota(
    @PrimaryKey(autoGenerate = true)
    val id_nota: Int = 0,

    val titulo: String,
    val contenido: String,
    val fecha_creacion: String,
    val color_fondo: String? = null,

    @ColumnInfo(name = "esta_borrada")
    val esta_borrada: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString()
)
