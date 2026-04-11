package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.NotaDao
import com.luixard.studios.datos.modelos.Nota
import kotlinx.coroutines.flow.Flow

class NotaRepositorio(private val notaDao: NotaDao) {

    // Solo notas visibles para el usuario (esta_borrada = false)
    val todasLasNotas: Flow<List<Nota>> = notaDao.obtenerTodasLasNotas()

    suspend fun agregarNota(nota: Nota) = notaDao.insertarNota(nota)

    // Usado en restauración — inserta con ID real (REPLACE)
    suspend fun insertarNota(nota: Nota) = notaDao.insertarNota(nota)

    suspend fun actualizarNota(nota: Nota) = notaDao.actualizarNota(nota)

    // Borrado lógico — el merge propaga la eliminación al otro dispositivo
    suspend fun eliminarNota(id: Int) = notaDao.borrarLogico(id)

    // Limpia toda la tabla antes de restaurar desde la nube
    suspend fun eliminarTodas() = notaDao.eliminarTodas()

    // Todas (incluyendo borradas) — para sync
    suspend fun obtenerTodas(): List<Nota> = notaDao.obtenerTodasSuspend()
}
