package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.NotaDao
import com.luixard.studios.datos.modelos.Nota
import kotlinx.coroutines.flow.Flow

class NotaRepositorio(private val notaDao: NotaDao) {

    val todasLasNotas: Flow<List<Nota>> = notaDao.obtenerTodasLasNotas()

    suspend fun agregarNota(nota: Nota) = notaDao.insertarNota(nota)

    // Usado en restauración — inserta con ID real (REPLACE), no duplica
    suspend fun insertarNota(nota: Nota) = notaDao.insertarNota(nota)

    suspend fun actualizarNota(nota: Nota) = notaDao.actualizarNota(nota)

    suspend fun eliminarNota(nota: Nota) = notaDao.eliminarNota(nota)

    // Limpia toda la tabla antes de restaurar desde la nube
    suspend fun eliminarTodas() = notaDao.eliminarTodas()
}
