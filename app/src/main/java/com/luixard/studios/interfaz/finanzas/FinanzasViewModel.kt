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

class FinanzasViewModel(private val repositorio: FinanzasRepositorio) : ViewModel() {

    val presupuestoActual: StateFlow<PresupuestoSemanal?> = repositorio.presupuestoActual
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            val calendar = Calendar.getInstance()
            val fechaInicio = calendar.time
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            val fechaFin = calendar.time

            val nuevoPresupuesto = PresupuestoSemanal(
                id_usuario = null,
                presupuesto_semanal_meta = monto,
                fecha_inicio = fechaInicio,
                fecha_fin = fechaFin
            )
            repositorio.insertarPresupuesto(nuevoPresupuesto)
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
}