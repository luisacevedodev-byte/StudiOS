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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransaccion(transaccion: Transaccion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransacciones(transacciones: List<Transaccion>)

    @Delete
    suspend fun eliminarTransaccion(transaccion: Transaccion)

    @Query("SELECT * FROM transacciones WHERE id_finanza = :idFinanza ORDER BY fecha_transaccion DESC")
    fun obtenerTransaccionesPorFinanza(idFinanza: Int): Flow<List<Transaccion>>

    // Para el backup puntual (suspend)
    @Query("SELECT * FROM transacciones")
    suspend fun obtenerTodasLasTransaccionesSuspend(): List<Transaccion>

    // NUEVO — Flow para que el auto-backup detecte cualquier cambio en transacciones
    @Query("SELECT * FROM transacciones ORDER BY fecha_transaccion DESC")
    fun obtenerTodasLasTransaccionesFlow(): Flow<List<Transaccion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCategoria(categoria: CategoriaGasto)

    @Delete
    suspend fun eliminarCategoria(categoria: CategoriaGasto)

    @Query("SELECT * FROM categorias_gasto")
    fun obtenerTodasLasCategorias(): Flow<List<CategoriaGasto>>
}
