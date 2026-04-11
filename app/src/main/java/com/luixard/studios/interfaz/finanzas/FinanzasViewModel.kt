package com.luixard.studios.interfaz.finanzas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import com.luixard.studios.datos.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class FinanzasViewModel(private val repositorio: FinanzasRepositorio) : ViewModel() {

    val presupuestoActual: StateFlow<PresupuestoSemanal?> = repositorio.presupuestoActual
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historialSemanas: Flow<List<PresupuestoSemanal>> = repositorio.obtenerHistorialSemanas()

    val categorias: StateFlow<List<CategoriaGasto>> = repositorio.categorias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val listaExistente = repositorio.categorias.first()
            if (listaExistente.isEmpty()) {
                val porDefecto = listOf("Comida", "Transporte", "Copias", "Juegos", "Varios")
                porDefecto.forEach { nombre ->
                    repositorio.insertarCategoria(
                        CategoriaGasto(nombre_categoria = nombre, id_usuario = null, es_predeterminada = true)
                    )
                }
            }
        }
    }

    fun establecerPresupuesto(monto: Double) {
        viewModelScope.launch {
            val actual = presupuestoActual.value

            val presupuestoParaGuardar = if (actual != null) {
                actual.copy(
                    presupuesto_semanal_meta = monto,
                    updatedAt               = System.currentTimeMillis()
                )
            } else {
                val calInicio = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                val calFin = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                PresupuestoSemanal(
                    id_finanza               = 0,
                    id_usuario               = null,
                    presupuesto_semanal_meta = monto,
                    fecha_inicio             = calInicio.time,
                    fecha_fin                = calFin.time
                )
            }
            repositorio.insertarPresupuesto(presupuestoParaGuardar)
        }
    }

    fun borrarPresupuesto(presupuesto: PresupuestoSemanal) {
        viewModelScope.launch {
            // 1. Obtener las transacciones vinculadas ANTES de borrar el presupuesto
            val txVinculadas = repositorio.obtenerTransacciones(presupuesto.id_finanza).first()

            for (tx in txVinculadas) {
                if (tx.syncId.isNotEmpty()) {
                    SyncManager.registrarEliminacionPermanente(tx.syncId)
                }
                repositorio.eliminarTransaccion(tx)
            }

            if (presupuesto.syncId.isNotEmpty()) {
                SyncManager.registrarEliminacionPermanente(presupuesto.syncId)
            }

            repositorio.eliminarPresupuesto(presupuesto)
        }
    }

    fun registrarTransaccion(transaccion: Transaccion) {
        viewModelScope.launch { repositorio.insertarTransaccion(transaccion) }
    }

    fun borrarTransaccion(transaccion: Transaccion) {
        viewModelScope.launch {
            if (transaccion.syncId.isNotEmpty()) {
                SyncManager.registrarEliminacionPermanente(transaccion.syncId)
            }
            repositorio.eliminarTransaccion(transaccion)
        }
    }

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> =
        repositorio.obtenerTransacciones(idFinanza)

    fun obtenerDetalleSemana(idFinanza: Int): Flow<List<Transaccion>> =
        repositorio.obtenerTransacciones(idFinanza)

    fun agregarCategoria(nombre: String) {
        viewModelScope.launch {
            repositorio.insertarCategoria(
                CategoriaGasto(nombre_categoria = nombre, id_usuario = null, es_predeterminada = false)
            )
        }
    }

    fun borrarCategoria(categoria: CategoriaGasto) {
        viewModelScope.launch { repositorio.eliminarCategoria(categoria) }
    }

    suspend fun categoriaEstaEnUso(idCategoria: Int): Boolean {
        val cantidad = repositorio.contarTransaccionesPorCategoria(idCategoria)
        return cantidad > 0
    }
}