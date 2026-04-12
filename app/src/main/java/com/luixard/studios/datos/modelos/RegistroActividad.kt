package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "registro_actividad_diaria")
data class RegistroActividad(
    @PrimaryKey(autoGenerate = true)
    val id_actividad: Int = 0,

    val id_tarea: Long,
    val nota: String?,
    val fecha_registro: Date,
    val tipo: String
)