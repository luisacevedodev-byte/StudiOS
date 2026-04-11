package com.luixard.studios.datos.dao

import androidx.room.*
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanzasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal): Long

    @Delete
    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal)

    @Query("DELETE FROM finanzas")
    suspend fun eliminarTodasLasFinanzas()

    @Query("DELETE FROM transacciones")
    suspend fun eliminarTodasLasTransacciones()

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC LIMIT 1")
    fun obtenerPresupuestoActual(): Flow<PresupuestoSemanal?>

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC")
    fun obtenerTodasLasFinanzas(): Flow<List<PresupuestoSemanal>>

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC")
    suspend fun obtenerTodasLasFinanzasSuspend(): List<PresupuestoSemanal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransaccion(transaccion: Transaccion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransacciones(transacciones: List<Transaccion>)

    // Borrado LÓGICO — el borrado se propaga a otros dispositivos vía sync
    @Query("UPDATE transacciones SET esta_borrada = 1, updated_at = :timestamp WHERE id_transaccion = :id")
    suspend fun marcarTransaccionBorrada(id: Int, timestamp: Long)

    // Borrado físico — solo para limpieza interna
    @Delete
    suspend fun eliminarTransaccionFisica(transaccion: Transaccion)

    // Para DISPLAY: excluye transacciones borradas
    @Query("SELECT * FROM transacciones WHERE id_finanza = :idFinanza AND esta_borrada = 0 ORDER BY fecha_transaccion DESC")
    fun obtenerTransaccionesPorFinanza(idFinanza: Int): Flow<List<Transaccion>>

    // Para BACKUP: incluye borradas para propagar el borrado a la nube
    @Query("SELECT * FROM transacciones ORDER BY fecha_transaccion DESC")
    fun obtenerTodasLasTransaccionesFlow(): Flow<List<Transaccion>>

    // Para MERGE en SyncManager: incluye borradas
    @Query("SELECT * FROM transacciones")
    suspend fun obtenerTodasLasTransaccionesSuspend(): List<Transaccion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCategoria(categoria: CategoriaGasto)

    @Delete
    suspend fun eliminarCategoria(categoria: CategoriaGasto)

    @Query("SELECT * FROM categorias_gasto")
    fun obtenerTodasLasCategorias(): Flow<List<CategoriaGasto>>
}