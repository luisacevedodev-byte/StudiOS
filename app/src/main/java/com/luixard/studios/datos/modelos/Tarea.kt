package com.luixard.studios.datos.modelos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tareas")
data class Tarea(
    @PrimaryKey(autoGenerate = true)
    val id_tarea: Int = 0,

    val titulo_tarea: String,
    val descripcion_tarea: String?,
    val fecha_entrega: String,
    val id_prioridad: String,
    val id_materia: Int?,
    val es_completada: Boolean = false,
    val esta_borrada: Boolean = false,
    val fecha_creacion: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString()
)
