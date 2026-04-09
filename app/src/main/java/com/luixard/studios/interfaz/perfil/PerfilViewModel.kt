package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilViewModel(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio
) : ViewModel() {

    val porcentajeTareas = MutableLiveData(0)
    val porcentajeAsistencia = MutableLiveData(0)
    val usuarioLogueado = MutableLiveData(false)
    val correoUsuario = MutableLiveData("Usuario no registrado")
    val estaCargandoRespaldo = MutableLiveData(false)

    // Variable para el código de 5 dígitos
    var codigoGenerado: String = ""

    init {
        calcularPorcentajeTareas()
        calcularPorcentajeAsistencia()
        verificarSesion()
    }

    private fun calcularPorcentajeTareas() {
        viewModelScope.launch {
            repositorioTareas.totalTareas.combine(repositorioTareas.totalTareasCompletadas) { total, completadas ->
                if (total > 0) ((completadas.toDouble() / total.toDouble()) * 100).toInt() else 0
            }.collect { porcentajeCalculado ->
                porcentajeTareas.value = porcentajeCalculado
            }
        }
    }

    private fun calcularPorcentajeAsistencia() {
        viewModelScope.launch {
            repositorioAuth.fechaRegistroUsuario.combine(repositorioAuth.fechasDeAvances) { fechaReg, listaAvances ->
                if (fechaReg == null) return@combine 0

                val milisegundosPorDia = 1000 * 60 * 60 * 24.toLong()
                val hoy = System.currentTimeMillis()
                val diasTotales = ((hoy - fechaReg.time) / milisegundosPorDia).toInt() + 1

                val formatoDia = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val diasUnicosProductivos = listaAvances.map { formatoDia.format(it) }.distinct().size

                if (diasTotales > 0) ((diasUnicosProductivos.toDouble() / diasTotales.toDouble()) * 100).toInt() else 0
            }.collect { porcentajeCalculado ->
                porcentajeAsistencia.value = porcentajeCalculado
            }
        }
    }

    // --- LÓGICA DE VERIFICACIÓN (EmailJS) ---

    fun generarCodigoVerificacion(): String {
        codigoGenerado = (10000..99999).random().toString()
        return codigoGenerado
    }

    fun prepararDatosEmail(nombreUsuario: String, correoDestino: String): Map<String, Any> {
        return mapOf(
            "service_id" to "service_90aab1h",
            "template_id" to "template_rbivst8",
            "user_id" to "akRDSWMA2agOzDoiW",
            "template_params" to mapOf(
                "nombre_usuario" to nombreUsuario,
                "codigo" to codigoGenerado,
                "to_email" to correoDestino
            )
        )
    }

    fun enviarEmail(datos: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = JSONObject(datos).toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://api.emailjs.com/api/v1.0/email/send")
                .post(body)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    println("DEBUG_STUDIOS: ¡Envío exitoso!")
                } else {
                    println("DEBUG_STUDIOS: Error ${response.code}: ${response.body?.string()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- GESTIÓN DE SESIÓN Y RESPALDO (Firebase) ---

    fun verificarSesion() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        usuarioLogueado.value = firebaseUser != null
        correoUsuario.value = firebaseUser?.email ?: "Usuario no registrado"
    }

    fun cerrarSesion() {
        FirebaseAuth.getInstance().signOut()
        verificarSesion()
    }

    fun iniciarRespaldoEnNube() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        estaCargandoRespaldo.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()

                val datosRespaldo = hashMapOf(
                    "usuario_info" to mapOf(
                        "ultima_sincronizacion" to Timestamp.now(),
                        "correo" to user.email
                    ),
                    "estadisticas" to mapOf(
                        "porcentaje_tareas" to porcentajeTareas.value,
                        "porcentaje_asistencia" to porcentajeAsistencia.value
                    )
                )

                // Guardar en Firestore
                db.collection("usuarios").document(user.uid)
                    .set(datosRespaldo)
                    .addOnCompleteListener {
                        // Usamos withContext para volver al hilo principal de forma segura
                        viewModelScope.launch(Dispatchers.Main) {
                            estaCargandoRespaldo.value = false
                            verificarSesion()
                        }
                    }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    estaCargandoRespaldo.value = false
                }
            }
        }
    }
}