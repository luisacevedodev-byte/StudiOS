package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import com.luixard.studios.datos.repositorios.FinanzasRepositorio

class PerfilViewModelFactory(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio,
    private val repositorioFinanzas: FinanzasRepositorio
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PerfilViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PerfilViewModel(
                repositorioTareas,
                repositorioAuth,
                repositorioFinanzas
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}