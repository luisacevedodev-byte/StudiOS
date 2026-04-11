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

    // ── Borrado lógico (para sync) ────────────────────────────────────────────
    @Query("UPDATE notas SET esta_borrada = 1, updated_at = :timestamp WHERE id_nota = :id")
    suspend fun borrarLogico(id: Int, timestamp: Long = System.currentTimeMillis())

    // Borrado físico — solo para casos extremos (sincronización interna)
    @Delete
    suspend fun eliminarNotaFisico(nota: Nota)

    // Limpiar toda la tabla — solo se usa en restauración total
    @Query("DELETE FROM notas")
    suspend fun eliminarTodas()

    // Solo notas NO borradas — lo que ve el usuario
    @Query("SELECT * FROM notas WHERE esta_borrada = 0 ORDER BY id_nota DESC")
    fun obtenerTodasLasNotas(): Flow<List<Nota>>

    // Todas incluyendo borradas — para el merge de sync
    @Query("SELECT * FROM notas ORDER BY id_nota DESC")
    suspend fun obtenerTodasSuspend(): List<Nota>
}
