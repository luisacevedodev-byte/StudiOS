package com.luixard.studios.interfaz.notas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.datos.repositorios.NotaRepositorio
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class NotasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio: NotaRepositorio

    init {
        // Obtenemos el DAO desde la base de datos central
        val dao = (aplicacion as AplicacionStudiOS).baseDatos.notaDao()
        repositorio = NotaRepositorio(dao)
    }

    // Convertimos el Flow a LiveData para que la vista lo observe fácilmente
    val todasLasNotas = repositorio.todasLasNotas.asLiveData()

    fun guardarNota(tituloIngresado: String, contenido: String) {
        viewModelScope.launch {
            // Obtenemos la lista actual para el conteo de forma segura
            val listaActual = repositorio.todasLasNotas.first()
            val numeroNota = listaActual.size + 1

            // Usamos .ifBlank como sugirió el IDE para que sea más limpio
            val tituloFinal = tituloIngresado.ifBlank { "Nota $numeroNota" }

            val nuevaNota = Nota(
                titulo = tituloFinal,
                contenido = contenido,
                fecha_creacion = obtenerFechaActual()
            )
            repositorio.agregarNota(nuevaNota)
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

    private fun obtenerFechaActual(): String {
        // Formato corregido para que incluya la hora como antes
        val sdf = java.text.SimpleDateFormat("dd MMM - hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}