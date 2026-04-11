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

    // Borrado LÓGICO — el borrado se propaga a otros dispositivos vía sync
    @Query("UPDATE notas SET esta_borrada = 1, updated_at = :timestamp WHERE id_nota = :id")
    suspend fun marcarNotaBorrada(id: Int, timestamp: Long)

    // Borrado físico — solo para limpieza interna, nunca llamar desde UI
    @Delete
    suspend fun eliminarNotaFisica(nota: Nota)

    @Query("DELETE FROM notas")
    suspend fun eliminarTodas()

    // Para DISPLAY: excluye notas borradas
    @Query("SELECT * FROM notas WHERE esta_borrada = 0 ORDER BY id_nota DESC")
    fun obtenerTodasLasNotas(): Flow<List<Nota>>

    // Para BACKUP: incluye borradas para que el borrado se propague a la nube
    @Query("SELECT * FROM notas ORDER BY id_nota DESC")
    fun obtenerTodasParaBackup(): Flow<List<Nota>>

    // Para MERGE en SyncManager: incluye borradas
    @Query("SELECT * FROM notas ORDER BY id_nota DESC")
    suspend fun obtenerTodasSuspend(): List<Nota>
}
