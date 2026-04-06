package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materias")
data class Materia(
    @PrimaryKey(autoGenerate = true)
    val id_materia: Int = 0,
    val nombre_materia: String,
    val color_hex: String // Para guardar el color personalizado
)