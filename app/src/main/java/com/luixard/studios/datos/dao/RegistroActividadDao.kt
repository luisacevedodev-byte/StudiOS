package com.luixard.studios.datos.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface RegistroActividadDao {

    // Guardar un nuevo avance cada vez que el usuario hace algo productivo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarAvance(avance: HistorialAvanceTarea)

    // Consulta clave para calcular el porcentaje de días productivos
    @Query("SELECT fecha_hora_registro FROM historial_avance_tareas")
    fun obtenerFechasDeAvances(): Flow<List<Date>>

    // Opcional: Obtener todo el historial si en el futuro quieres hacer una lista de actividad
    @Query("SELECT * FROM historial_avance_tareas ORDER BY fecha_hora_registro DESC")
    fun obtenerTodoElHistorial(): Flow<List<HistorialAvanceTarea>>
}