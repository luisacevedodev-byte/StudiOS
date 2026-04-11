package com.luixard.studios.interfaz.tareas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.datos.repositorios.TareaRepositorio
import com.luixard.studios.datos.sync.SyncManager
import kotlinx.coroutines.launch

class TareasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio: TareaRepositorio

    init {
        val dao = (aplicacion as AplicacionStudiOS).baseDatos.tareaDao()
        repositorio = TareaRepositorio(dao)
    }

    val tareasPendientes       = repositorio.tareasPendientes.asLiveData()
    val tareasCompletadas      = repositorio.tareasCompletadas.asLiveData()
    val tareasBorradas         = repositorio.tareasBorradas.asLiveData()

    fun guardarTarea(tarea: Tarea) {
        viewModelScope.launch {
            repositorio.agregarTarea(tarea)
        }
    }

    fun moverPapelera(id: Int) {
        viewModelScope.launch {
            repositorio.moverPapelera(id)
        }
    }

    fun marcarComoCompletada(id: Int) {
        viewModelScope.launch {
            repositorio.completarTarea(id)
        }
    }

    fun restaurarTarea(id: Int) {
        viewModelScope.launch {
            repositorio.restaurarTarea(id)
        }
    }

    fun eliminarPermanente(tarea: Tarea) {
        viewModelScope.launch {
            SyncManager.registrarEliminacionPermanente(tarea.syncId)
            repositorio.eliminarPermanente(tarea)
        }
    }

    fun actualizarTextosDeTarea(tarea: Tarea) {
        viewModelScope.launch {
            repositorio.actualizarTarea(tarea.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}