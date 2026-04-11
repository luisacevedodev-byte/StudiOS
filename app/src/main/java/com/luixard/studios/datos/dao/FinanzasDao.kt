package com.luixard.studios.datos.dao

import androidx.room.*
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanzasDao {

    // ── Presupuesto ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal): Long

    @Delete
    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal)

    @Query("DELETE FROM finanzas")
    suspend fun eliminarTodasLasFinanzas()

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC LIMIT 1")
    fun obtenerPresupuestoActual(): Flow<PresupuestoSemanal?>

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC")
    fun obtenerTodasLasFinanzas(): Flow<List<PresupuestoSemanal>>

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC")
    suspend fun obtenerTodasLasFinanzasSuspend(): List<PresupuestoSemanal>

    // ── Transacciones ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransaccion(transaccion: Transaccion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransacciones(transacciones: List<Transaccion>)

    // Borrado lógico: NO borra físicamente, marca esta_borrada = 1
    // Así el otro dispositivo puede detectar que fue eliminada en el merge
    @Query("UPDATE transacciones SET esta_borrada = 1, updated_at = :ts WHERE id_transaccion = :id")
    suspend fun borrarTransaccionLogico(id: Int, ts: Long = System.currentTimeMillis())

    // Borrado físico — solo para limpieza interna antes de restaurar
    @Delete
    suspend fun eliminarTransaccionFisico(transaccion: Transaccion)

    @Query("DELETE FROM transacciones")
    suspend fun eliminarTodasLasTransacciones()

    // Solo transacciones NO borradas — lo que ve el usuario
    @Query("SELECT * FROM transacciones WHERE id_finanza = :idFinanza AND esta_borrada = 0 ORDER BY fecha_transaccion DESC")
    fun obtenerTransaccionesPorFinanza(idFinanza: Int): Flow<List<Transaccion>>

    // Todas incluyendo borradas — para backup y merge
    @Query("SELECT * FROM transacciones")
    suspend fun obtenerTodasLasTransaccionesSuspend(): List<Transaccion>

    @Query("SELECT * FROM transacciones ORDER BY fecha_transaccion DESC")
    fun obtenerTodasLasTransaccionesFlow(): Flow<List<Transaccion>>

    // ── Categorías ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCategoria(categoria: CategoriaGasto)

    @Delete
    suspend fun eliminarCategoria(categoria: CategoriaGasto)

    @Query("SELECT * FROM categorias_gasto")
    fun obtenerTodasLasCategorias(): Flow<List<CategoriaGasto>>
}