package com.luixard.studios.datos.remoto

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseManager {

    // Instancias de los servicios que activamos en la consola
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Comprueba si el usuario ya vinculó una cuenta
    fun estaUsuarioLogueado(): Boolean {
        return auth.currentUser != null
    }

    // Obtiene el ID único que Firebase le da a cada usuario
    fun obtenerIdUsuarioFirebase(): String? {
        return auth.currentUser?.uid
    }

    // Cierra la sesión
    fun cerrarSesion() {
        auth.signOut()
    }

    // Esta función se llamará después de autenticarnos con Google o Correo,
    // para subir un respaldo inicial de la base de datos local (Room) a Firestore.
    suspend fun realizarRespaldoInicial(
        tareasLocales: List<Any>, // Reemplazaremos 'Any' con tus modelos reales después
        finanzasLocales: List<Any>
    ): Boolean {
        val userId = obtenerIdUsuarioFirebase() ?: return false

        return try {
            // Ejemplo de cómo se guardará un dato en la nube (Firestore)
            // Se crea una colección "usuarios", dentro el documento con su ID,
            // y dentro una subcolección "tareas".

            // TODO: Cuando conectemos Room con Firebase en el siguiente paso,
            // aquí recorreremos tus listas locales para subirlas con firestore.collection().set().await()

            true // Respaldo exitoso
        } catch (e: Exception) {
            e.printStackTrace()
            false // Error al respaldar
        }
    }
}