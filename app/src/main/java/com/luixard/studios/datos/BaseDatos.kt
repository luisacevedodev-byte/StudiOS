package com.luixard.studios.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 10,
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

        fun getDatabase(context: Context): BaseDatos {
            return INSTANCIA ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "studios_db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCIA?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = database.finanzasDao()
                                    val porDefecto = listOf("Comida", "Transporte", "Copias", "Juegos", "Varios")
                                    porDefecto.forEach { nombre ->
                                        dao.insertarCategoria(CategoriaGasto(nombre_categoria = nombre, id_usuario = null, es_predeterminada = true))
                                    }
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCIA = instancia
                instancia
            }
        }
    }
}