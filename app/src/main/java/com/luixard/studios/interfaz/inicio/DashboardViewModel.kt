package com.luixard.studios.interfaz.inicio

import android.app.Application
import androidx.lifecycle.*
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class DashboardViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = (aplicacion as AplicacionStudiOS)

    // 1. TAREAS: "tareasPendientes"
    val tareasPendientes: LiveData<List<Tarea>> = app.repositorioTareas.tareasPendientes.asLiveData()

    // 2. NOTAS: "todasLasNotas" sí coincide, así que lo dejamos igual
    val todasLasNotas: LiveData<List<Nota>> = app.repositorioNotas.todasLasNotas.asLiveData()

    // 3. FINANZAS: lógica para obtener el presupuesto y sus transacciones
    val presupuestoActual = app.repositorioFinanzas.presupuestoActual.asLiveData()

    // Obtenemos las transacciones basándonos en el ID del presupuesto actual
    val transaccionesSemanales: LiveData<List<Transaccion>> = presupuestoActual.switchMap { presupuesto ->
        if (presupuesto != null) {
            app.repositorioFinanzas.obtenerTransacciones(presupuesto.id_finanza).asLiveData()
        } else {
            MutableLiveData(emptyList())
        }
    }

    fun obtenerConteoUrgentes(tareas: List<Tarea>): Int {
        return tareas.count { it.id_prioridad.equals("Alta", ignoreCase = true) }
    }
}