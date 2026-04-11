package com.luixard.studios.datos.modelos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "transacciones")
data class Transaccion(
    @PrimaryKey(autoGenerate = true)
    val id_transaccion: Int = 0,

    val id_usuario: Int?,
    val id_finanza: Int,
    val id_categoria: Int?,
    val tipo_transaccion: String,        // "Gasto" o "Ingreso"
    val monto: Double,
    val fecha_transaccion: Date,
    val nota_transaccion: String?,

    @ColumnInfo(name = "esta_borrada")
    val estaBorrada: Boolean = false,

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
