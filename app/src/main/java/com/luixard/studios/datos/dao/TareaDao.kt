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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTarea(tarea: Tarea)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarLista(tareas: List<Tarea>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarListaTareas(tareas: List<Tarea>)

    @Update
    suspend fun actualizarTarea(tarea: Tarea)

    @Delete
    suspend fun eliminarTareaPermanente(tarea: Tarea)

    @Query("DELETE FROM tareas")
    suspend fun eliminarTodas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restaurarTareasMasivo(tareas: List<Tarea>)

    // ── Lectura ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM tareas WHERE es_completada = 0 AND esta_borrada = 0 ORDER BY fecha_entrega ASC")
    fun obtenerTareasPendientes(): Flow<List<Tarea>>

    @Query("SELECT * FROM tareas WHERE es_completada = 1 AND esta_borrada = 0 ORDER BY fecha_entrega DESC")
    fun obtenerTareasCompletadas(): Flow<List<Tarea>>

    @Query("SELECT * FROM tareas WHERE esta_borrada = 1 ORDER BY fecha_entrega DESC")
    fun obtenerTareasBorradas(): Flow<List<Tarea>>

    @Query("SELECT * FROM tareas")
    fun obtenerTodasLasTareas(): Flow<List<Tarea>>

    // Para el merge — devuelve TODAS incluyendo borradas
    @Query("SELECT * FROM tareas")
    suspend fun obtenerTodasSuspend(): List<Tarea>

    // ── Cambios de estado — actualizan updated_at para que el merge los detecte

    @Query("UPDATE tareas SET es_completada = 1, updated_at = :ts WHERE id_tarea = :id")
    suspend fun marcarComoCompletada(id: Int, ts: Long = System.currentTimeMillis())

    // Borrado lógico: marca esta_borrada Y actualiza el timestamp
    @Query("UPDATE tareas SET esta_borrada = 1, updated_at = :ts WHERE id_tarea = :id")
    suspend fun mandarAPapelera(id: Int, ts: Long = System.currentTimeMillis())

    // Restaurar: quita completada y borrada, actualiza timestamp
    @Query("UPDATE tareas SET es_completada = 0, esta_borrada = 0, updated_at = :ts WHERE id_tarea = :id")
    suspend fun restaurarTarea(id: Int, ts: Long = System.currentTimeMillis())

    // ── Conteos ───────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM tareas")
    fun contarTareasTotales(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tareas WHERE es_completada = 1")
    fun contarTareasCompletadas(): Flow<Int>
}
