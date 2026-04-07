package com.luixard.studios.interfaz.finanzas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luixard.studios.datos.repositorios.FinanzasRepositorio

class FinanzasViewModelFactory(private val repositorio: FinanzasRepositorio) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanzasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanzasViewModel(repositorio) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}