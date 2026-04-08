package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.NotaDao
import com.luixard.studios.datos.modelos.Nota
import kotlinx.coroutines.flow.Flow

class NotaRepositorio(private val notaDao: NotaDao) {

    // LECTURA
    val todasLasNotas: Flow<List<Nota>> = notaDao.obtenerTodasLasNotas()

    // ACCIONES CRUD
    suspend fun agregarNota(nota: Nota) {
        notaDao.insertarNota(nota)
    }

    suspend fun actualizarNota(nota: Nota) {
        notaDao.actualizarNota(nota)
    }

    suspend fun eliminarNota(nota: Nota) {
        notaDao.eliminarNota(nota)
    }
}