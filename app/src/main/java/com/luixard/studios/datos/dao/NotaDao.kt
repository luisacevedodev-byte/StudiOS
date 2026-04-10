package com.luixard.studios.datos.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luixard.studios.datos.modelos.Nota
import kotlinx.coroutines.flow.Flow

@Dao
interface NotaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarNota(nota: Nota)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun agregarNota(nota: Nota)

    @Update
    suspend fun actualizarNota(nota: Nota)

    @Delete
    suspend fun eliminarNota(nota: Nota)

    @Query("DELETE FROM notas")
    suspend fun eliminarTodas()

    @Query("SELECT * FROM notas ORDER BY id_nota DESC")
    fun obtenerTodasLasNotas(): Flow<List<Nota>>
}