package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.TareaDao
import com.luixard.studios.datos.modelos.Tarea

class TareaRepositorio(private val tareaDao: TareaDao) {

    val tareasPendientes       = tareaDao.obtenerTareasPendientes()
    val tareasCompletadas      = tareaDao.obtenerTareasCompletadas()
    val tareasBorradas         = tareaDao.obtenerTareasBorradas()
    val totalTareas            = tareaDao.contarTareasTotales()
    val totalTareasCompletadas = tareaDao.contarTareasCompletadas()
    val todasLasTareas         = tareaDao.obtenerTodasLasTareas()

    suspend fun agregarTarea(tarea: Tarea)        = tareaDao.insertarTarea(tarea)
    suspend fun actualizarTarea(tarea: Tarea)     = tareaDao.actualizarTarea(tarea)
    suspend fun eliminarPermanente(tarea: Tarea)  = tareaDao.eliminarTareaPermanente(tarea)

    suspend fun moverPapelera(id: Int)  = tareaDao.mandarAPapelera(id)
    suspend fun completarTarea(id: Int) = tareaDao.marcarComoCompletada(id)
    suspend fun restaurarTarea(id: Int) = tareaDao.restaurarTarea(id)

    suspend fun restaurarTareasMasivo(tareas: List<Tarea>) = tareaDao.insertarListaTareas(tareas)

    suspend fun insertarListaTareas(lista: List<Tarea>) = tareaDao.insertarLista(lista)

    // Limpia toda la tabla antes de restaurar desde la nube
    suspend fun eliminarTodas() = tareaDao.eliminarTodas()
}
 