package com.luixard.studios.interfaz.notas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.datos.repositorios.NotaRepositorio
import kotlinx.coroutines.launch

class NotasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio: NotaRepositorio

    init {
        // Obtenemos el DAO desde la base de datos central
        val dao = (aplicacion as AplicacionStudiOS).baseDatos.notaDao()
        repositorio = NotaRepositorio(dao)
    }

    // Convertimos el Flow a LiveData para que la vista lo observe fácilmente
    val todasLasNotas = repositorio.todasLasNotas.asLiveData()

    fun guardarNota(nota: Nota) {
        viewModelScope.launch {
            repositorio.agregarNota(nota)
        }
    }

    fun actualizarNota(nota: Nota) {
        viewModelScope.launch {
            repositorio.actualizarNota(nota)
        }
    }

    fun borrarNota(nota: Nota) {
        viewModelScope.launch {
            repositorio.eliminarNota(nota)
        }
    }
}