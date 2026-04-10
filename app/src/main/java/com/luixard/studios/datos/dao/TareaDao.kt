package com.luixard.studios.datos.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luixard.studios.datos.modelos.Tarea
import kotlinx.coroutines.flow.Flow

@Dao
interface TareaDao {

    // Añadir nueva tarea
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTarea(tarea: Tarea)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarLista(tareas: List<Tarea>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarListaTareas(tareas: List<Tarea>)

    // Editar tarea existente
    @Update
    suspend fun actualizarTarea(tarea: Tarea)

    // Eliminar permanentemente de la base de datos
    @Delete
    suspend fun eliminarTareaPermanente(tarea: Tarea)

    @Query("DELETE FROM tareas")
    suspend fun eliminarTodas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restaurarTareasMasivo(tareas: List<Tarea>)

    // ---------------- LECTURA DE DATOS  ----------------

    // Mostrar tareas pendientes (no completadas y no en papelera), ordenadas por fecha más próxima
    @Query("SELECT * FROM tareas WHERE es_completada = 0 AND esta_borrada = 0 ORDER BY fecha_entrega ASC")
    fun obtenerTareasPendientes(): Flow<List<Tarea>>

    // Mostrar tareas completadas (Historial)
    @Query("SELECT * FROM tareas WHERE es_completada = 1 AND esta_borrada = 0 ORDER BY fecha_entrega DESC")
    fun obtenerTareasCompletadas(): Flow<List<Tarea>>

    // Mostrar tareas borradas (Papelera)
    @Query("SELECT * FROM tareas WHERE esta_borrada = 1 ORDER BY fecha_entrega DESC")
    fun obtenerTareasBorradas(): Flow<List<Tarea>>

    // Marcar una tarea como completada
    @Query("UPDATE tareas SET es_completada = 1 WHERE id_tarea = :id")
    suspend fun marcarComoCompletada(id: Int)

    // Borrado lógico (Mandar a papelera)
    @Query("UPDATE tareas SET esta_borrada = 1 WHERE id_tarea = :id")
    suspend fun mandarAPapelera(id: Int)

    // Restaurar una tarea (Quitarla de completada o de la papelera)
    @Query("UPDATE tareas SET es_completada = 0, esta_borrada = 0 WHERE id_tarea = :id")
    suspend fun restaurarTarea(id: Int)

    @Query("SELECT COUNT(*) FROM tareas")
    fun contarTareasTotales(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM tareas WHERE es_completada = 1")
    fun contarTareasCompletadas(): kotlinx.coroutines.flow.Flow<Int>

    // En TareaDao.kt
    @Query("SELECT * FROM tareas") // "tareas" debe ser el nombre de tu tabla en @Entity
    fun obtenerTodasLasTareas(): kotlinx.coroutines.flow.Flow<List<Tarea>>
}