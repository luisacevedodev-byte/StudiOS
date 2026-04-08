package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notas")
data class Nota(
    @PrimaryKey(autoGenerate = true)
    val id_nota: Int = 0,
    val titulo: String,
    val contenido: String,
    val fecha_creacion: String,
    val color_fondo: String? = null
)