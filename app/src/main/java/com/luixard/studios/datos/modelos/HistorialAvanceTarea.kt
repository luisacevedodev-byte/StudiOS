package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "historial_avance_tareas")
data class HistorialAvanceTarea(
    @PrimaryKey(autoGenerate = true) val id_avance: Int = 0,
    val id_tarea: Int, // Llave foránea hacia la tarea
    val fecha_hora_registro: Date, // Esta es la columna que busca el DAO
    val nota_avance: String? = null
)

