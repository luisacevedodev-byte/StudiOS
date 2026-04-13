package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import com.luixard.studios.datos.repositorios.NotaRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import com.luixard.studios.datos.sync.SyncManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilViewModel(
    private val repositorioTareas:   TareaRepositorio,
    private val repositorioAuth:     AuthRepositorio,
    private val repositorioFinanzas: FinanzasRepositorio,
    private val repositorioNotas:    NotaRepositorio
) : ViewModel() {

    val porcentajeTareas     = MutableLiveData(0)
    val porcentajeAsistencia = MutableLiveData(0)
    val usuarioLogueado      = MutableLiveData(false)
    val nombreUsuarioDisplay = MutableLiveData<String?>(null)
    val correoUsuario        = MutableLiveData("")
    val estaCargandoRespaldo = MutableLiveData(false)

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        calcularPorcentajeTareas()
        calcularPorcentajeAsistencia()

        viewModelScope.launch {
            SyncManager.estaCargando.collect { cargando ->
                estaCargandoRespaldo.postValue(cargando)
            }
        }
        viewModelScope.launch {
            SyncManager.nombreDisplay.collect { nombre ->
                if (nombre.isNotEmpty()) nombreUsuarioDisplay.postValue(nombre)
            }
        }

        verificarSesion()
    }

    private fun calcularPorcentajeTareas() {
        viewModelScope.launch {
            repositorioTareas.totalTareas
                .combine(repositorioTareas.totalTareasCompletadas) { total, comp ->
                    if (total > 0) ((comp.toDouble() / total.toDouble()) * 100).toInt() else 0
                }.collect { porcentajeTareas.value = it }
        }
    }

    private fun calcularPorcentajeAsistencia() {
        viewModelScope.launch {
            repositorioAuth.fechaRegistroUsuario
                .combine(repositorioAuth.fechasDeAvances) { fecha, avances ->
                    if (fecha == null) return@combine 0
                    val dias = ((System.currentTimeMillis() - fecha.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                    val prod = avances.distinctBy { fmt.format(it) }.size
                    if (dias > 0) ((prod.toDouble() / dias.toDouble()) * 100).toInt() else 0
                }.collect { porcentajeAsistencia.value = it }
        }
    }

    fun verificarSesion() {
        val user = FirebaseAuth.getInstance().currentUser
        usuarioLogueado.value = user != null

        if (user != null) {
            correoUsuario.value = user.email
            SyncManager.onSesionActiva(user.uid)
        } else {
            SyncManager.onCerrarSesion()
            nombreUsuarioDisplay.value = ""
            correoUsuario.value = "Usuario no registrado"
        }
    }

    fun marcarSesionActiva() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        usuarioLogueado.value = true
        correoUsuario.value   = user.email
    }

    fun actualizarNombreInmediato(nombre: String, apellido: String) {
        val nombreCompleto = "$nombre $apellido".trim()
        if (nombreCompleto.isNotEmpty()) {
            nombreUsuarioDisplay.value = nombreCompleto
            SyncManager.actualizarNombre(nombre, apellido)
        }
    }

    fun cerrarSesion() {
        SyncManager.onCerrarSesion()
        FirebaseAuth.getInstance().signOut()
        verificarSesion()
    }

    fun vincularCuentaGoogle(nombre: String, apellido: String) {
        actualizarNombreInmediato(nombre, apellido)
    }

    fun iniciarSesionGoogle(nombre: String, apellido: String) {
        actualizarNombreInmediato(nombre, apellido)
    }
}