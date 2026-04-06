package com.luixard.studios.interfaz.tareas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.datos.repositorios.TareaRepositorio
import kotlinx.coroutines.launch

class TareasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio: TareaRepositorio

    init {
        val dao = (aplicacion as AplicacionStudiOS).baseDatos.tareaDao()
        repositorio = TareaRepositorio(dao)
    }

    // Estas variables se actualizan solas cada vez que la base de datos cambia
    val tareasPendientes = repositorio.tareasPendientes.asLiveData()
    val tareasCompletadas = repositorio.tareasCompletadas.asLiveData()

    // CU-01: Añadir tarea
    fun guardarTarea(tarea: Tarea) {
        viewModelScope.launch {
            repositorio.agregarTarea(tarea)
        }
    }

    // CU-03: Mover a la papelera (Borrado lógico)
    fun moverPapelera(id: Int) {
        viewModelScope.launch {
            repositorio.moverPapelera(id)
        }
    }

    // CU-04: Completar tarea
    fun marcarComoCompletada(id: Int) {
        viewModelScope.launch {
            repositorio.completarTarea(id)
        }
    }

    // --- NUEVAS FUNCIONES PARA EL HISTORIAL ---
    val tareasBorradas = repositorio.tareasBorradas.asLiveData()

    fun restaurarTarea(id: Int) {
        viewModelScope.launch {
            repositorio.restaurarTarea(id)
        }
    }

    fun eliminarPermanente(tarea: Tarea) {
        viewModelScope.launch {
            repositorio.eliminarPermanente(tarea)
        }
    }
}