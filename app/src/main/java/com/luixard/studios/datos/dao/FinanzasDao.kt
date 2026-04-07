package com.luixard.studios.datos.dao

import androidx.room.*
import com.luixard.studios.datos.modelos.CategoriaGasto
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanzasDao {
    // --- PRESUPUESTO ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPresupuesto(presupuesto: PresupuestoSemanal): Long

    @Delete
    suspend fun eliminarPresupuesto(presupuesto: PresupuestoSemanal)

    @Query("SELECT * FROM finanzas ORDER BY id_finanza DESC LIMIT 1")
    fun obtenerPresupuestoActual(): Flow<PresupuestoSemanal?>

    // --- TRANSACCIONES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransaccion(transaccion: Transaccion)

    @Delete
    suspend fun eliminarTransaccion(transaccion: Transaccion)

    @Query("SELECT * FROM transacciones WHERE id_finanza = :idFinanza ORDER BY fecha_transaccion DESC")
    fun obtenerTransaccionesPorFinanza(idFinanza: Int): Flow<List<Transaccion>>

    // --- CATEGORÍAS ---
    @Insert
    suspend fun insertarCategoria(categoria: CategoriaGasto)

    @Query("SELECT * FROM categorias_gasto")
    fun obtenerTodasLasCategorias(): Flow<List<CategoriaGasto>>
}