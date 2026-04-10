package com.luixard.studios

import android.app.Application
import com.luixard.studios.datos.BaseDatos
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import com.luixard.studios.datos.repositorios.NotaRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import com.luixard.studios.datos.sync.SyncManager

class AplicacionStudiOS : Application() {

    val baseDatos by lazy { BaseDatos.getDatabase(this) }

    val repositorioTareas   by lazy { TareaRepositorio(baseDatos.tareaDao()) }
    val repositorioFinanzas by lazy { FinanzasRepositorio(baseDatos.finanzasDao()) }
    val repositorioNotas    by lazy { NotaRepositorio(baseDatos.notaDao()) }
    val repositorioAuth     by lazy { AuthRepositorio(baseDatos.usuarioDao(), baseDatos.registroActividadDao()) }

    override fun onCreate() {
        super.onCreate()
        SyncManager.init(repositorioTareas, repositorioNotas, repositorioFinanzas)
    }
}
