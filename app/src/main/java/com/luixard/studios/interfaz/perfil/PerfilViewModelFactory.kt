package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio

class PerfilViewModelFactory(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio // <-- AÑADIMOS EL NUEVO REPOSITORIO
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PerfilViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PerfilViewModel(repositorioTareas, repositorioAuth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}