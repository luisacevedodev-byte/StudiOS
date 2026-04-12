package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.RegistroActividadDao
import com.luixard.studios.datos.dao.UsuarioDao
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import com.luixard.studios.datos.modelos.Usuario
import com.luixard.studios.datos.modelos.RegistroActividad

class AuthRepositorio(
    private val usuarioDao: UsuarioDao,
    private val registroActividadDao: RegistroActividadDao
) {
    // ---------------- LECTURA DE DATOS (Flujos) ----------------

    val fechaRegistroUsuario = usuarioDao.obtenerFechaRegistro()
    val fechasDeAvances = registroActividadDao.obtenerFechasDeAvances()
    val usuarioActual = usuarioDao.obtenerUsuarioActual()

    // ---------------- ACCIONES CRUD ----------------

    suspend fun registrarUsuarioLocal(usuario: Usuario) {
        usuarioDao.insertarUsuario(usuario)
    }

    suspend fun actualizarUsuario(usuario: Usuario) {
        usuarioDao.actualizarUsuario(usuario)
    }

    suspend fun registrarDiaProductivo(avance: HistorialAvanceTarea) {
        registroActividadDao.registrarAvance(avance)
    }

    // ---------------- FUNCIÓN PARA LA NOTIFICACIÓN ----------------

    suspend fun guardarRegistroActividad(registro: RegistroActividad) {
        try {
            registroActividadDao.insertarRegistro(registro)
        } catch (_: Exception) {
        }

        if (registro.tipo == "avance") {
            val historialAvance = HistorialAvanceTarea(
                id_tarea            = registro.id_tarea.toInt(),
                fecha_hora_registro = registro.fecha_registro,
                nota_avance         = registro.nota
            )
            registroActividadDao.registrarAvance(historialAvance)
        }
    }
}