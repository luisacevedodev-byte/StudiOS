package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.FinanzasDao
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

class FinanzasRepositorio(private val finanzasDao: FinanzasDao) {

    val presupuestoActual  = finanzasDao.obtenerPresupuestoActual()
    val categorias         = finanzasDao.obtenerTodasLasCategorias()
    val todosLosRegistros  = finanzasDao.obtenerTodasLasFinanzas()
    val todasLasTransaccionesFlow: Flow<List<Transaccion>> = finanzasDao.obtenerTodasLasTransaccionesFlow()

    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuesto)

    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal) =
        finanzasDao.eliminarPresupuesto(presupuesto)

    suspend fun insertarTransaccion(transaccion: Transaccion) =
        finanzasDao.insertarTransaccion(transaccion)

    // Borrado lógico — el merge propagará la eliminación al otro dispositivo
    suspend fun eliminarTransaccion(id: Int) =
        finanzasDao.borrarTransaccionLogico(id)

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> =
        finanzasDao.obtenerTransaccionesPorFinanza(idFinanza)

    suspend fun insertarCategoria(categoria: CategoriaGasto) =
        finanzasDao.insertarCategoria(categoria)

    suspend fun eliminarCategoria(categoria: CategoriaGasto) =
        finanzasDao.eliminarCategoria(categoria)

    fun obtenerHistorialSemanas(): Flow<List<PresupuestoSemanal>> =
        finanzasDao.obtenerTodasLasFinanzas()

    fun calcularSaldoRestante(presupuestoMeta: Double, transacciones: List<Transaccion>): Double {
        var saldo = presupuestoMeta
        for (t in transacciones) {
            if (!t.estaBorrada) { // No contar las eliminadas lógicamente
                if (t.tipo_transaccion == "Gasto") saldo -= t.monto
                else if (t.tipo_transaccion == "Ingreso") saldo += t.monto
            }
        }
        return saldo
    }

    suspend fun realizarCierreSemanal(presupuestoActual: PresupuestoSemanal) =
        finanzasDao.insertarPresupuesto(presupuestoActual)

    // ── Respaldo y restauración ───────────────────────────────────────────────

    suspend fun eliminarTodos() {
        finanzasDao.eliminarTodasLasFinanzas()
        finanzasDao.eliminarTodasLasTransacciones()
    }

    suspend fun restaurarDatosFinanzas(listaFinanzas: List<PresupuestoSemanal>) {
        listaFinanzas.forEach { finanzasDao.insertarPresupuesto(it) }
    }

    suspend fun restaurarTransacciones(lista: List<Transaccion>) =
        finanzasDao.insertarTransacciones(lista)

    // Todas — incluyendo borradas lógicamente — para backup y merge
    suspend fun obtenerTodasLasTransacciones(): List<Transaccion> =
        finanzasDao.obtenerTodasLasTransaccionesSuspend()

    suspend fun obtenerTodasLasFinanzasSuspend(): List<PresupuestoSemanal> =
        finanzasDao.obtenerTodasLasFinanzasSuspend()
}