package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PerfilViewModel(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio // <-- AÑADIMOS EL NUEVO REPOSITORIO
) : ViewModel() {

    val porcentajeTareas = MutableLiveData<Int>(0)
    val porcentajeAsistencia = MutableLiveData<Int>(0)

    init {
        calcularPorcentajeTareas()
        calcularPorcentajeAsistencia()
    }

    private fun calcularPorcentajeTareas() {
        viewModelScope.launch {
            repositorioTareas.totalTareas.combine(repositorioTareas.totalTareasCompletadas) { total, completadas ->
                if (total > 0) {
                    ((completadas.toDouble() / total.toDouble()) * 100).toInt()
                } else {
                    0
                }
            }.collect { porcentajeCalculado ->
                porcentajeTareas.value = porcentajeCalculado
            }
        }
    }

    private fun calcularPorcentajeAsistencia() {
        viewModelScope.launch {
            // Usamos repositorioAuth en lugar de "repositorio"
            repositorioAuth.fechaRegistroUsuario.combine(repositorioAuth.fechasDeAvances) { fechaReg, listaAvances ->
                if (fechaReg == null) return@combine 0

                val milisegundosPorDia = 1000 * 60 * 60 * 24.toLong()
                val hoy = System.currentTimeMillis()
                val diasTotales = ((hoy - fechaReg.time) / milisegundosPorDia).toInt() + 1

                val formatoDia = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val diasUnicosProductivos = listaAvances.map { formatoDia.format(it) }.distinct().size

                if (diasTotales > 0) {
                    ((diasUnicosProductivos.toDouble() / diasTotales.toDouble()) * 100).toInt()
                } else {
                    0
                }
            }.collect { porcentajeCalculado ->
                porcentajeAsistencia.value = porcentajeCalculado
            }
        }
    }
}