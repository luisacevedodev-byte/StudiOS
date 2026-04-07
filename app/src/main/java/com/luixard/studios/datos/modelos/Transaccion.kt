package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transacciones")
data class Transaccion(
    @PrimaryKey(autoGenerate = true) val id_transaccion: Int = 0,
    val id_usuario: Int?,
    val id_finanza: Int, // Llave foránea hacia el presupuesto de la semana actual
    val id_categoria: Int?,
    val tipo_transaccion: String, // "Gasto" o "Ingreso"
    val monto: Double,
    val fecha_transaccion: Date,
    val nota_transaccion: String?
)