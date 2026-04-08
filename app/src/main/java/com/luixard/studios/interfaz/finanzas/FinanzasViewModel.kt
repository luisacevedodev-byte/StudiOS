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
import kotlinx.coroutines.flow.map
import java.util.Date

class FinanzasViewModel(private val repositorio: FinanzasRepositorio) : ViewModel() {

    val presupuestoActual: StateFlow<PresupuestoSemanal?> = repositorio.presupuestoActual
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historialSemanas: Flow<List<PresupuestoSemanal>> = repositorio.obtenerHistorialSemanas()

    fun obtenerDetalleSemana(idFinanza: Int): Flow<List<Transaccion>> {
        return repositorio.obtenerTransacciones(idFinanza)
    }

    val categorias: StateFlow<List<CategoriaGasto>> = repositorio.categorias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val listaExistente = repositorio.categorias.first()
            if (listaExistente.isEmpty()) {
                val porDefecto = listOf("Comida", "Transporte", "Copias", "Juegos", "Varios")
                porDefecto.forEach { nombre ->
                    repositorio.insertarCategoria(CategoriaGasto(nombre_categoria = nombre, id_usuario = null, es_predeterminada = true))
                }
            }
        }
    }

    fun establecerPresupuesto(monto: Double) {
        viewModelScope.launch {
            val actual = presupuestoActual.value

            val presupuestoParaGuardar = if (actual != null) {
                actual.copy(presupuesto_semanal_meta = monto)
            } else {
                // Configurar el inicio de la semana
                val calInicio = Calendar.getInstance()
                calInicio.firstDayOfWeek = Calendar.MONDAY
                // Lunes de la semana actual
                calInicio.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calInicio.set(Calendar.HOUR_OF_DAY, 0)
                calInicio.set(Calendar.MINUTE, 0)
                calInicio.set(Calendar.SECOND, 0)
                calInicio.set(Calendar.MILLISECOND, 0)

                // Configurar el fin de la semana (Domingo)
                val calFin = Calendar.getInstance()
                calFin.firstDayOfWeek = Calendar.MONDAY
                calFin.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) // Primero vamos al lunes
                calFin.add(Calendar.DAY_OF_YEAR, 6) // Y sumamos 6 días para llegar al domingo
                calFin.set(Calendar.HOUR_OF_DAY, 23)
                calFin.set(Calendar.MINUTE, 59)
                calFin.set(Calendar.SECOND, 59)

                PresupuestoSemanal(
                    id_finanza = 0,
                    id_usuario = null,
                    presupuesto_semanal_meta = monto,
                    fecha_inicio = calInicio.time,
                    fecha_fin = calFin.time
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

    fun agregarCategoria(nombre: String) {
        viewModelScope.launch {
            val nueva = CategoriaGasto(nombre_categoria = nombre, id_usuario = null, es_predeterminada = false)
            repositorio.insertarCategoria(nueva)
        }
    }

    fun borrarCategoria(categoria: CategoriaGasto) {
        viewModelScope.launch { repositorio.eliminarCategoria(categoria) }
    }

    class FinanzasViewModel(private val repositorio: FinanzasRepositorio) : ViewModel() {

        // Obtener todas las semanas registradas excepto la actual (historial)
        val historialSemanas: Flow<List<PresupuestoSemanal>> = repositorio.obtenerHistorialSemanas()

        // Función para procesar los datos detallados de una semana
        fun obtenerDetalleSemana(idFinanza: Int): Flow<Map<Date, List<Transaccion>>> {
            return repositorio.obtenerTransacciones(idFinanza).map { lista ->
                // Agrupamos por fecha (sin horas) para el detalle diario
                lista.groupBy { it.fecha_transaccion }
            }
        }
    }
}