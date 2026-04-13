package com.luixard.studios.interfaz.perfil

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.luixard.studios.datos.remoto.EmailJSManager
import com.luixard.studios.datos.sync.SyncManager
import kotlinx.coroutines.launch

// ── Estado sellado que PerfilFragment observa para reaccionar a cada resultado ──
sealed class AuthEstado {
    object Inactivo : AuthEstado()

    /** Login con correo/contraseña exitoso */
    data class LoginExito(
        val uid: String,
        val nombre: String,
        val apellido: String
    ) : AuthEstado()

    /** Cuenta nueva creada (registro con correo) */
    data class RegistroExito(
        val nombre: String,
        val apellido: String
    ) : AuthEstado()

    /** Autenticación con Google exitosa */
    data class GoogleExito(
        val nombre: String,
        val apellido: String,
        val esNuevo: Boolean
    ) : AuthEstado()

    /** El correo de restablecimiento fue enviado exitosamente */
    object ResetEnviado : AuthEstado()

    data class Error(val mensaje: String) : AuthEstado()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    val authEstado = MutableLiveData<AuthEstado>(AuthEstado.Inactivo)

    private val emailManager = EmailJSManager()

    // ── Código de verificación ─────────────────────────────────────────────────

    val codigoGenerado: String get() = emailManager.getCodigoActual()

    fun generarCodigoVerificacion() = emailManager.generarCodigoVerificacion()

    fun prepararDatosEmail(nombre: String, correo: String) =
        emailManager.prepararDatosEmail(nombre, correo)

    fun enviarEmail(datos: Map<String, Any>) {
        viewModelScope.launch { emailManager.enviarEmail(datos) }
    }

    // ── Login con correo / contraseña ──────────────────────────────────────────
    fun iniciarSesion(correo: String, password: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(correo, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: ""

                // 1) Publicar éxito de inmediato — la UI no queda bloqueada por Firestore.
                authEstado.value = AuthEstado.LoginExito(uid, "", "")

                // 2) Obtener nombre desde Firestore en segundo plano (best-effort).
                FirebaseFirestore.getInstance()
                    .collection("usuarios").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val nom = if (doc.exists()) doc.getString("perfil.nombre")   ?: "" else ""
                        val ape = if (doc.exists()) doc.getString("perfil.apellido") ?: "" else ""
                        if (nom.isNotEmpty()) {
                            authEstado.value = AuthEstado.LoginExito(uid, nom, ape)
                        }
                        try { SyncManager.onInicioSesion(uid, nom, ape) } catch (_: Exception) {}
                    }
                    .addOnFailureListener {
                        // Firestore no disponible — login ya fue aceptado, sync después.
                        try { SyncManager.onInicioSesion(uid, "", "") } catch (_: Exception) {}
                    }
            }
            .addOnFailureListener { e ->
                val ex  = e as? FirebaseAuthException
                val msg = when (ex?.errorCode) {
                    "ERROR_USER_NOT_FOUND"            -> "No existe una cuenta con este correo."
                    "ERROR_WRONG_PASSWORD"            -> "Contraseña incorrecta. Inténtalo de nuevo."
                    "ERROR_INVALID_CREDENTIAL",
                    "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Correo o contraseña incorrectos."
                    "ERROR_TOO_MANY_REQUESTS"         -> "Demasiados intentos. Espera un momento."
                    else                              -> "Correo o contraseña incorrectos."
                }
                authEstado.value = AuthEstado.Error(msg)
            }
    }

    // ── Verificar si correo ya tiene cuenta antes de registrar ────────────────


    fun verificarCorreoYRegistrar(
        correo: String,
        password: String,
        nombre: String,
        apellido: String,
        alEnviarCodigo: () -> Unit,
        alError: (String) -> Unit
    ) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(correo, "probe_check_only_${System.currentTimeMillis()}")
            .addOnSuccessListener { result ->
                // El correo NO existía — eliminar la cuenta de prueba y proceder con el código.
                result.user?.delete()
                generarCodigoVerificacion()
                enviarEmail(prepararDatosEmail(nombre, correo))
                alEnviarCodigo()
            }
            .addOnFailureListener { e ->
                val ex = e as? FirebaseAuthException
                when (ex?.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> {
                        // Correo en uso: puede ser Google o password. Pedir que use
                        // el método con el que se registró originalmente.
                        alError(
                            "Este correo ya tiene una cuenta registrada.\n\n" +
                                    "Si te registraste con Google, usa el botón \"Continuar con Google\". " +
                                    "Si usaste correo y contraseña, ve a \"Iniciar Sesión\"."
                        )
                    }
                    "ERROR_WEAK_PASSWORD" -> {
                        generarCodigoVerificacion()
                        enviarEmail(prepararDatosEmail(nombre, correo))
                        alEnviarCodigo()
                    }
                    else -> {
                        generarCodigoVerificacion()
                        enviarEmail(prepararDatosEmail(nombre, correo))
                        alEnviarCodigo()
                    }
                }
            }
    }

    // ── Crear cuenta Firebase (tras verificación de código) ───────────────────

    fun crearCuentaFirebase(correo: String, password: String, nombre: String, apellido: String) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(correo, password)
            .addOnSuccessListener {
                SyncManager.onNuevaCuentaVinculada(nombre, apellido)
                authEstado.value = AuthEstado.RegistroExito(nombre, apellido)
            }
            .addOnFailureListener { e ->
                val ex  = e as? FirebaseAuthException
                val msg = when (ex?.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" ->
                        "Este correo ya tiene una cuenta. Si usaste Google, inicia sesión con Google."
                    else -> "Error al crear la cuenta: ${e.message}"
                }
                authEstado.value = AuthEstado.Error(msg)
            }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    fun autenticarConGoogle(account: GoogleSignInAccount, esVincular: Boolean) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val nombre   = account.givenName  ?: ""
                val apellido = account.familyName ?: ""

                val esNuevo = result.additionalUserInfo?.isNewUser == true
                if (esNuevo) {
                    SyncManager.onNuevaCuentaVinculada(nombre, apellido)
                } else {
                    val uid = result.user?.uid ?: ""
                    SyncManager.onInicioSesion(uid, nombre, apellido)
                }

                authEstado.value = AuthEstado.GoogleExito(nombre, apellido, esNuevo)
            }
            .addOnFailureListener { e ->
                val msg = when {
                    e.message?.contains("account-exists-with-different-credential") == true ->
                        "Ya existe una cuenta con ese correo. Intenta con correo y contraseña."
                    else -> "Error al autenticar con Google: ${e.message}"
                }
                authEstado.value = AuthEstado.Error(msg)
            }
    }

    // ── Recuperar contraseña ──────────────────────────────────────────────────

    fun enviarResetPassword(correo: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(correo)
            .addOnSuccessListener { authEstado.value = AuthEstado.ResetEnviado }
            .addOnFailureListener { authEstado.value = AuthEstado.Error("Error al enviar el correo. Intenta de nuevo.") }
    }

    fun resetEstado() { authEstado.value = AuthEstado.Inactivo }
}