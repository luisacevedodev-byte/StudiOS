package com.luixard.studios.interfaz.finanzas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class FinanzasViewModel(private val repositorio: FinanzasRepositorio) : ViewModel() {

    val presupuestoActual: StateFlow<PresupuestoSemanal?> = repositorio.presupuestoActual
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun establecerPresupuesto(monto: Double) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val fechaInicio = calendar.time

            // Configurar fin de semana
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
        viewModelScope.launch {
            repositorio.eliminarPresupuesto(presupuesto)
        }
    }
}