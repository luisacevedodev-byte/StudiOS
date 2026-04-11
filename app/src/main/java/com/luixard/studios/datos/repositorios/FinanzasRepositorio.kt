package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.FinanzasDao
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

class FinanzasRepositorio(private val finanzasDao: FinanzasDao) {

    val presupuestoActual : Flow<PresupuestoSemanal?> = finanzasDao.obtenerPresupuestoActual()
    val categorias        : Flow<List<CategoriaGasto>> = finanzasDao.obtenerTodasLasCategorias()
    val todosLosRegistros : Flow<List<PresupuestoSemanal>> = finanzasDao.obtenerTodasLasFinanzas()

    val todasLasTransaccionesFlow: Flow<List<Transaccion>> =
        finanzasDao.obtenerTodasLasTransaccionesFlow()

    // ── Presupuesto ───────────────────────────────────────────────────────────

    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuesto)

    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.eliminarPresupuesto(presupuesto)

    fun obtenerHistorialSemanas(): Flow<List<PresupuestoSemanal>> =
        finanzasDao.obtenerTodasLasFinanzas()

    suspend fun realizarCierreSemanal(presupuesto: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuesto)

    // ── Transacciones ─────────────────────────────────────────────────────────

    suspend fun insertarTransaccion(transaccion: Transaccion) =
        finanzasDao.insertarTransaccion(transaccion)

    suspend fun eliminarTransaccion(transaccion: Transaccion) =
        finanzasDao.marcarTransaccionBorrada(transaccion.id_transaccion, System.currentTimeMillis())

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> =
        finanzasDao.obtenerTransaccionesPorFinanza(idFinanza)

    // ── Categorías ────────────────────────────────────────────────────────────

    suspend fun insertarCategoria(categoria: CategoriaGasto) =
        finanzasDao.insertarCategoria(categoria)

    suspend fun eliminarCategoria(categoria: CategoriaGasto) =
        finanzasDao.eliminarCategoria(categoria)

    suspend fun contarTransaccionesPorCategoria(idCategoria: Int): Int =
        finanzasDao.contarTransaccionesPorCategoria(idCategoria)

    // ── Cálculos ──────────────────────────────────────────────────────────────

    fun calcularSaldoRestante(meta: Double, transacciones: List<Transaccion>): Double {
        var saldo = meta
        for (t in transacciones) {
            if (!t.esta_borrada) {
                if (t.tipo_transaccion == "Gasto")   saldo -= t.monto
                else if (t.tipo_transaccion == "Ingreso") saldo += t.monto
            }
        }
        return saldo
    }

    // ── Backup y merge ────────────────────────────────────────────────────────

    suspend fun eliminarTodos() {
        finanzasDao.eliminarTodasLasFinanzas()
        finanzasDao.eliminarTodasLasTransacciones()
    }

    suspend fun obtenerTodasLasFinanzasSuspend(): List<PresupuestoSemanal> =
        finanzasDao.obtenerTodasLasFinanzasSuspend()

    suspend fun obtenerTodasLasTransacciones(): List<Transaccion> =
        finanzasDao.obtenerTodasLasTransaccionesSuspend()

    suspend fun restaurarDatosFinanzas(lista: List<PresupuestoSemanal>) =
        lista.forEach { finanzasDao.insertarPresupuesto(it) }

    suspend fun restaurarTransacciones(lista: List<Transaccion>) =
        finanzasDao.insertarTransacciones(lista)
}