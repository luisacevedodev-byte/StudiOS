package com.luixard.studios.datos.repositorios

import com.luixard.studios.datos.dao.RegistroActividadDao
import com.luixard.studios.datos.dao.UsuarioDao
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import com.luixard.studios.datos.modelos.Usuario

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
}