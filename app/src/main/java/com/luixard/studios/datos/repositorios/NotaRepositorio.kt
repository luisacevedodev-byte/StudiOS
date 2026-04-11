package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.NotaDao
import com.luixard.studios.datos.modelos.Nota
import kotlinx.coroutines.flow.Flow

class NotaRepositorio(private val notaDao: NotaDao) {

    // Para ViewModels — solo notas visibles (esta_borrada = false)
    val todasLasNotas: Flow<List<Nota>> = notaDao.obtenerTodasLasNotas()

    // Para SyncManager autoBackup — incluye borradas para propagarlas a la nube
    val todasLasNotasParaBackup: Flow<List<Nota>> = notaDao.obtenerTodasParaBackup()

    suspend fun agregarNota(nota: Nota) = notaDao.insertarNota(nota)

    // Inserción directa con ID real — para restaurar desde nube
    suspend fun insertarNota(nota: Nota) = notaDao.insertarNota(nota)

    suspend fun actualizarNota(nota: Nota) = notaDao.actualizarNota(nota)

    // Borrado LÓGICO — el borrado se propaga a otros dispositivos vía syncId
    suspend fun eliminarNota(nota: Nota) =
        notaDao.marcarNotaBorrada(nota.id_nota, System.currentTimeMillis())

    // Para MERGE en SyncManager — devuelve TODAS (incluyendo borradas)
    suspend fun obtenerTodas(): List<Nota> = notaDao.obtenerTodasSuspend()

    // Limpia toda la tabla antes de restaurar desde la nube (uso interno)
    suspend fun eliminarTodas() = notaDao.eliminarTodas()
}
