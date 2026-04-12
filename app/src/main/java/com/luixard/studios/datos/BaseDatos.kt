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
        HistorialAvanceTarea::class,
        RegistroActividad::class
    ],
    version = 13,
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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tareas        ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notas         ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE finanzas      ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tareas        ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE notas         ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE finanzas      ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")

                database.execSQL("UPDATE tareas        SET sync_id = LOWER(HEX(RANDOMBLOB(16)))")
                database.execSQL("UPDATE notas         SET sync_id = LOWER(HEX(RANDOMBLOB(16)))")
                database.execSQL("UPDATE transacciones SET sync_id = LOWER(HEX(RANDOMBLOB(16)))")
                database.execSQL("UPDATE finanzas      SET sync_id = LOWER(HEX(RANDOMBLOB(16)))")

                database.execSQL("ALTER TABLE notas         ADD COLUMN esta_borrada INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transacciones ADD COLUMN esta_borrada INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `registro_actividad_diaria` (" +
                            "`id_actividad` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`id_tarea` INTEGER NOT NULL, " +
                            "`nota` TEXT, " +
                            "`fecha_registro` INTEGER NOT NULL, " +
                            "`tipo` TEXT NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): BaseDatos {
            return INSTANCIA ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "studios_db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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