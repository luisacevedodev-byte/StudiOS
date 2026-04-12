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

    // Estos son los flujos que consumirá el PerfilViewModel para la matemática
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

    // Llama a esta función desde TareasViewModel cada vez que se complete una tarea
    suspend fun registrarDiaProductivo(avance: HistorialAvanceTarea) {
        registroActividadDao.registrarAvance(avance)
    }

    // ---------------- FUNCIÓN PARA LA NOTIFICACIÓN ----------------
    suspend fun guardarRegistroActividad(registro: RegistroActividad) {
        // 1. Guardar la actividad general (avance o inactividad)
        registroActividadDao.insertarRegistro(registro)

        // 2. Validar si es un "avance" para vincularlo a la tarea y que sea visible
        if (registro.tipo == "avance") {
            val historialAvance = HistorialAvanceTarea(
                id_tarea = registro.id_tarea.toInt(),
                fecha_hora_registro = registro.fecha_registro,
                nota_avance = registro.nota
            )
            // Llama a la función que ya tienes en RegistroActividadDao
            registroActividadDao.registrarAvance(historialAvance)
        }
    }
}