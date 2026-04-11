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

    // Para SyncManager autoBackup — incluye transacciones borradas
    val todasLasTransaccionesFlow: Flow<List<Transaccion>> =
        finanzasDao.obtenerTodasLasTransaccionesFlow()

    // ── Presupuesto ───────────────────────────────────────────────────────────

    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuesto)

    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.eliminarPresupuesto(presupuesto)

    fun obtenerHistorialSemanas(): Flow<List<PresupuestoSemanal>> =
        finanzasDao.obtenerTodasLasFinanzas()

    suspend fun realizarCierreSemanal(presupuestoActual: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuestoActual)

    // ── Transacciones ─────────────────────────────────────────────────────────

    suspend fun insertarTransaccion(transaccion: Transaccion) =
        finanzasDao.insertarTransaccion(transaccion)

    // Borrado LÓGICO — propaga el borrado a otros dispositivos vía syncId
    suspend fun eliminarTransaccion(transaccion: Transaccion) =
        finanzasDao.marcarTransaccionBorrada(
            transaccion.id_transaccion,
            System.currentTimeMillis()
        )

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> =
        finanzasDao.obtenerTransaccionesPorFinanza(idFinanza)

    // ── Categorías ────────────────────────────────────────────────────────────

    suspend fun insertarCategoria(categoria: CategoriaGasto) =
        finanzasDao.insertarCategoria(categoria)

    suspend fun eliminarCategoria(categoria: CategoriaGasto) =
        finanzasDao.eliminarCategoria(categoria)

    // ── Cálculos ──────────────────────────────────────────────────────────────

    fun calcularSaldoRestante(presupuestoMeta: Double, transacciones: List<Transaccion>): Double {
        var saldo = presupuestoMeta
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

    // Para el merge en SyncManager — lee todos los presupuestos en un momento puntual
    suspend fun obtenerTodasLasFinanzasSuspend(): List<PresupuestoSemanal> =
        finanzasDao.obtenerTodasLasFinanzasSuspend()

    // Para el merge en SyncManager — lee todas las transacciones (incluyendo borradas)
    suspend fun obtenerTodasLasTransacciones(): List<Transaccion> =
        finanzasDao.obtenerTodasLasTransaccionesSuspend()

    // Inserción masiva para el merge — REPLACE por id no duplica
    suspend fun restaurarDatosFinanzas(lista: List<PresupuestoSemanal>) =
        lista.forEach { finanzasDao.insertarPresupuesto(it) }

    suspend fun restaurarTransacciones(lista: List<Transaccion>) =
        finanzasDao.insertarTransacciones(lista)
}