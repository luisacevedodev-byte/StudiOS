package com.luixard.studios.datos.modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id_usuario: Int = 0,
    val firebase_uid: String? = null,
    val nombre: String,
    val correo: String? = null,
    val fecha_registro: Date,
    val es_invitado: Boolean = true
)