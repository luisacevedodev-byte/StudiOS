package com.luixard.studios.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luixard.studios.datos.dao.TareaDao
import com.luixard.studios.datos.modelos.Materia
import com.luixard.studios.datos.modelos.Tarea

// Aquí le decimos a Room qué tablas existen y la versión de la BD
@Database(entities = [Tarea::class, Materia::class], version = 1, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {

    // Conectamos el DAO para poder usarlo desde otras partes de la app
    abstract fun tareaDao(): TareaDao

    // Usamos un 'companion object' (Patrón Singleton) para asegurarnos de que
    // solo se abra una sola conexión a la base de datos en todo el celular.
    companion object {
        @Volatile
        private var INSTANCIA: BaseDatos? = null

        fun getDatabase(context: Context): BaseDatos {
            return INSTANCIA ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "studios_db"
                ).build()
                INSTANCIA = instancia
                instancia
            }
        }
    }
}