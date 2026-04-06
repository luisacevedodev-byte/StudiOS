package com.luixard.studios

import android.app.Application
import com.luixard.studios.datos.BaseDatos

class AplicacionStudiOS : Application() {

    val baseDatos: BaseDatos by lazy {
        BaseDatos.getDatabase(this)
    }
}