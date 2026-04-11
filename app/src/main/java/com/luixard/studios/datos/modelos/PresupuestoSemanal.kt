package com.luixard.studios.datos.modelos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "finanzas")
data class PresupuestoSemanal(
    @PrimaryKey(autoGenerate = true)
    val id_finanza: Int = 0,

    val id_usuario: Int?,
    val presupuesto_semanal_meta: Double,
    val fecha_inicio: Date,
    val fecha_fin: Date?,                // null hasta que se cierre la semana

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
