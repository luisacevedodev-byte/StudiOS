package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tareas")
data class Tarea(
    @PrimaryKey(autoGenerate = true)
    val id_tarea: Int = 0,

    val titulo_tarea: String,

    val descripcion_tarea: String?,

    val fecha_entrega: String, // formato "YYYY-MM-DD"

    val id_prioridad: String, // Aquí se guarda "ALTA", "MEDIA" o "BAJA"

    val id_materia: Int?, // Opcional, por si la tarea no tiene materia asignada

    val es_completada: Boolean = false, // Por defecto al crearla, no está completada

    val esta_borrada: Boolean = false, // Para la papelera (RF08)

    val fecha_creacion: Long = System.currentTimeMillis() // Guarda el momento exacto en que se creó
)