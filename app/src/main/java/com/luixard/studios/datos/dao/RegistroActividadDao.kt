package com.luixard.studios.datos.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import com.luixard.studios.datos.modelos.RegistroActividad
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface RegistroActividadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarAvance(avance: HistorialAvanceTarea)

    @Update
    suspend fun actualizarAvance(avance: HistorialAvanceTarea)

    @Delete
    suspend fun eliminarAvance(avance: HistorialAvanceTarea)

    @Query("SELECT fecha_hora_registro FROM historial_avance_tareas")
    fun obtenerFechasDeAvances(): Flow<List<Date>>

    @Query("SELECT * FROM historial_avance_tareas ORDER BY fecha_hora_registro DESC")
    fun obtenerTodoElHistorial(): Flow<List<HistorialAvanceTarea>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarRegistro(registro: RegistroActividad)

    @Query("SELECT * FROM historial_avance_tareas WHERE id_tarea = :idTarea ORDER BY fecha_hora_registro DESC")
    suspend fun obtenerAvancesDeTarea(idTarea: Int): List<HistorialAvanceTarea>
}