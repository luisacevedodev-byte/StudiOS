package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.TareaDao
import com.luixard.studios.datos.modelos.Tarea

class TareaRepositorio(private val tareaDao: TareaDao) {

    // ---------------- LECTURA DE DATOS ----------------
    val tareasPendientes = tareaDao.obtenerTareasPendientes()
    val tareasCompletadas = tareaDao.obtenerTareasCompletadas()
    val tareasBorradas = tareaDao.obtenerTareasBorradas()

    // ---------------- ACCIONES CRUD ----------------
    suspend fun agregarTarea(tarea: Tarea) {
        tareaDao.insertarTarea(tarea)
    }
    suspend fun actualizarTarea(tarea: Tarea) {
        tareaDao.actualizarTarea(tarea)
    }
    suspend fun eliminarPermanente(tarea: Tarea) {
        tareaDao.eliminarTareaPermanente(tarea)
    }

    // ---------------- ACCIONES RÁPIDAS ----------------
    // Como en el DAO pedimos el ID para estas acciones, aquí también se lo pasamos
    suspend fun moverPapelera(id: Int) {
        tareaDao.mandarAPapelera(id)
    }

    suspend fun completarTarea(id: Int) {
        tareaDao.marcarComoCompletada(id)
    }

    suspend fun restaurarTarea(id: Int) {
        tareaDao.restaurarTarea(id)
    }
}