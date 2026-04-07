package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.FinanzasDao
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

class FinanzasRepositorio(private val finanzasDao: FinanzasDao) {

    val presupuestoActual = finanzasDao.obtenerPresupuestoActual()
    val categorias = finanzasDao.obtenerTodasLasCategorias()

    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal) {
        finanzasDao.insertarPresupuesto(presupuesto)
    }

    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal) {
        finanzasDao.eliminarPresupuesto(presupuesto)
    }

    suspend fun insertarTransaccion(transaccion: Transaccion) {
        finanzasDao.insertarTransaccion(transaccion)
    }

    suspend fun eliminarTransaccion(transaccion: Transaccion) {
        finanzasDao.eliminarTransaccion(transaccion)
    }

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> {
        return finanzasDao.obtenerTransaccionesPorFinanza(idFinanza)
    }

    suspend fun insertarCategoria(categoria: CategoriaGasto) {
        finanzasDao.insertarCategoria(categoria)
    }

    suspend fun eliminarCategoria(categoria: CategoriaGasto) {
        finanzasDao.eliminarCategoria(categoria)
    }

    fun calcularSaldoRestante(presupuestoMeta: Double, transacciones: List<Transaccion>): Double {
        var saldo = presupuestoMeta
        for (t in transacciones) {
            if (t.tipo_transaccion == "Gasto") {
                saldo -= t.monto
            } else if (t.tipo_transaccion == "Ingreso") {
                saldo += t.monto
            }
        }
        return saldo
    }
}