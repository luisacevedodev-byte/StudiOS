package com.luixard.studios.interfaz.finanzas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
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
                // ── CRÍTICO PARA EL MERGE ────────────────────────────────────
                // Conservar el syncId original para que el merge identifique
                // este presupuesto en el otro dispositivo y aplique last-write-wins.
                // Actualizar updatedAt para que el merge sepa que esta versión
                // es más reciente que la del otro dispositivo.
                actual.copy(
                    presupuesto_semanal_meta = monto,
                    updatedAt               = System.currentTimeMillis()  // ← CRÍTICO
                )
            } else {
                val calInicio = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calFin = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
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
        viewModelScope.launch { repositorio.eliminarPresupuesto(presupuesto) }
    }

    fun registrarTransaccion(transaccion: Transaccion) {
        viewModelScope.launch { repositorio.insertarTransaccion(transaccion) }
    }

    fun borrarTransaccion(transaccion: Transaccion) {
        viewModelScope.launch { repositorio.eliminarTransaccion(transaccion) }
    }

    fun obtenerTransacciones(idFinanza: Int): Flow<List<Transaccion>> {
        return repositorio.obtenerTransacciones(idFinanza)
    }

    fun obtenerDetalleSemana(idFinanza: Int): Flow<List<Transaccion>> {
        return repositorio.obtenerTransacciones(idFinanza)
    }

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
}