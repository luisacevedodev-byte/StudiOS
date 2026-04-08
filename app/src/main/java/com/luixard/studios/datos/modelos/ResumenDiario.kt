package com.luixard.studios.datos.modelos

import java.util.Date

data class ResumenDiario(
    val fecha: Date,
    val totalGastos: Double,
    val totalIngresos: Double,
    val transacciones: List<Transaccion>
)