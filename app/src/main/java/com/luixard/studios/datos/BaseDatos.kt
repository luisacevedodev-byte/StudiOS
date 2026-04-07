package com.luixard.studios.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luixard.studios.datos.dao.TareaDao
import com.luixard.studios.datos.dao.FinanzasDao
import com.luixard.studios.datos.modelos.*
import com.luixard.studios.datos.utilidades.Converters

// Agregamos @TypeConverters para manejar las fechas (Date)
@Database(
    entities = [
        Tarea::class,
        Materia::class,
        PresupuestoSemanal::class,
        Transaccion::class,
        CategoriaGasto::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BaseDatos : RoomDatabase() {

    abstract fun tareaDao(): TareaDao
    abstract fun finanzasDao(): FinanzasDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCIA = instancia
                instancia
            }
        }
    }
}