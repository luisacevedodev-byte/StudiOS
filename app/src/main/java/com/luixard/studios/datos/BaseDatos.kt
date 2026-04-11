package com.luixard.studios.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luixard.studios.datos.dao.TareaDao
import com.luixard.studios.datos.dao.FinanzasDao
import com.luixard.studios.datos.dao.NotaDao
import com.luixard.studios.datos.dao.UsuarioDao
import com.luixard.studios.datos.dao.RegistroActividadDao
import com.luixard.studios.datos.modelos.*
import com.luixard.studios.datos.utilidades.Converters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Tarea::class,
        Materia::class,
        PresupuestoSemanal::class,
        Transaccion::class,
        CategoriaGasto::class,
        Nota::class,
        Usuario::class,
        HistorialAvanceTarea::class
    ],
    version = 12,           // ← 10 → 11 (updated_at) → 12 (sync_id + esta_borrada en notas y transacciones)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BaseDatos : RoomDatabase() {

    abstract fun tareaDao(): TareaDao
    abstract fun finanzasDao(): FinanzasDao
    abstract fun notaDao(): NotaDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun registroActividadDao(): RegistroActividadDao

    companion object {

        @Volatile
        private var INSTANCIA: BaseDatos? = null

        // ── Migración 10 → 11: agrega updated_at ─────────────────────────────
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tareas        ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notas         ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE finanzas      ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        // ── Migración 11 → 12: agrega sync_id (UUID) y esta_borrada ──────────
        // sync_id: permite que el merge distinga ítems entre dispositivos
        //          sin confundir IDs autoGenerate que chocan.
        // esta_borrada en notas/transacciones: permite propagar borrados
        //          al otro dispositivo sin que "resuciten" en el merge.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // sync_id — valor vacío por defecto; SyncManager lo llena al primer backup
                database.execSQL("ALTER TABLE tareas        ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE notas         ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE finanzas      ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")

                // esta_borrada solo en notas y transacciones (tareas ya la tenían)
                database.execSQL("ALTER TABLE notas         ADD COLUMN esta_borrada INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN esta_borrada INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): BaseDatos {
            return INSTANCIA ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "studios_db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCIA?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = database.finanzasDao()
                                    val porDefecto = listOf(
                                        "Comida", "Transporte", "Copias", "Juegos", "Varios"
                                    )
                                    porDefecto.forEach { nombre ->
                                        dao.insertarCategoria(
                                            CategoriaGasto(
                                                nombre_categoria  = nombre,
                                                id_usuario        = null,
                                                es_predeterminada = true
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    })
                    .build()
                INSTANCIA = instancia
                instancia
            }
        }
    }
}