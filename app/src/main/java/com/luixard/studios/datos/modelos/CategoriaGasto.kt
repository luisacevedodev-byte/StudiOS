package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "categorias_gasto")
data class CategoriaGasto(
    @PrimaryKey(autoGenerate = true) val id_categoria: Int = 0,
    val id_usuario: Int?, //para modo local/invitado
    val nombre_categoria: String,
    val es_sistema: Boolean = false // Para proteger las que vienen por defecto (Comida, Transporte, Juegos)
)