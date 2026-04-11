package com.luixard.studios.datos.modelos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import java.util.Date

@Entity(tableName = "finanzas")
data class PresupuestoSemanal(
    @PrimaryKey(autoGenerate = true)
    val id_finanza: Int = 0,

    val id_usuario: Int?,
    val presupuesto_semanal_meta: Double,
    val fecha_inicio: Date,
    val fecha_fin: Date?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString()
)
