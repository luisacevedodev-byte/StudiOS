package com.luixard.studios.datos.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luixard.studios.datos.modelos.Usuario
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUsuario(usuario: Usuario)

    @Update
    suspend fun actualizarUsuario(usuario: Usuario)

    // Obtenemos el usuario actual (útil para el modo invitado/local)
    @Query("SELECT * FROM usuarios LIMIT 1")
    fun obtenerUsuarioActual(): Flow<Usuario?>

    // Consulta específica para el cálculo de Constancia/Asistencia
    @Query("SELECT fecha_registro FROM usuarios LIMIT 1")
    fun obtenerFechaRegistro(): Flow<Date?>
}