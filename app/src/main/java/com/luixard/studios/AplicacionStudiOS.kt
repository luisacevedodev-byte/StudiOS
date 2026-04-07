package com.luixard.studios

import android.app.Application
import com.luixard.studios.datos.BaseDatos
import com.luixard.studios.datos.repositorios.FinanzasRepositorio

class AplicacionStudiOS : Application() {

    // Instancia única de la base de datos
    val baseDatos: BaseDatos by lazy {
        BaseDatos.getDatabase(this)
    }

    // Instancia del repositorio que usa el DAO de la base de datos
    val repositorioFinanzas by lazy {
        FinanzasRepositorio(baseDatos.finanzasDao())
    }
}