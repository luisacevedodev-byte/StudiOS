package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(FlowPreview::class) // Necesario para usar debounce
class PerfilViewModel(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio,
    private val repositorioFinanzas: FinanzasRepositorio
) : ViewModel() {

    val porcentajeTareas = MutableLiveData(0)
    val porcentajeAsistencia = MutableLiveData(0)
    val usuarioLogueado = MutableLiveData(false)
    val nombreUsuarioDisplay = MutableLiveData<String?>(null)
    val correoUsuario = MutableLiveData("")
    val estaCargandoRespaldo = MutableLiveData(false)
    val respaldoEncontrado = MutableLiveData<Map<String, Any>?>(null)



    var codigoGenerado: String = ""

    init {
        calcularPorcentajeTareas()
        calcularPorcentajeAsistencia()
        verificarSesion()
        activarRespaldoAutomatico() // <--- Se activa al iniciar
    }

    // --- RESPALDO AUTOMÁTICO EN SEGUNDO PLANO ---
    private fun activarRespaldoAutomatico() {
        viewModelScope.launch(Dispatchers.IO) {
            // "Escuchamos" cambios en Tareas y Finanzas al mismo tiempo
            combine(
                repositorioTareas.todasLasTareas,
                repositorioFinanzas.todosLosRegistros
            ) { tareas, finanzas ->
                Pair(tareas, finanzas)
            }
                .debounce(2000) // Espera 2 segundos de inactividad antes de subir
                .collect { (tareas, finanzas) ->
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        // Subida silenciosa (sin activar la barra de carga UI)
                        realizarSubidaFirestore(user.uid, tareas, finanzas)
                    }
                }
        }
    }

    private fun realizarSubidaFirestore(uid: String, tareas: List<Any>, finanzas: List<Any>) {
        val db = FirebaseFirestore.getInstance()

        // Separamos el nombre del LiveData o de lo que ya tenemos en Firestore
        val nombreCompleto = nombreUsuarioDisplay.value ?: ""
        val partes = nombreCompleto.split(" ")
        val nom = partes.getOrNull(0) ?: ""
        val ape = partes.getOrNull(1) ?: ""

        val backup = hashMapOf(
            "perfil" to mapOf(
                "nombre" to nom,
                "apellido" to ape,
                "correo" to correoUsuario.value,
                "ultima_sinc" to Timestamp.now()
            ),
            "datos" to mapOf(
                "tareas" to tareas,
                "finanzas" to finanzas
                // Aquí puedes añadir "notas" cuando tengas el repositorio listo
            )
        )

        db.collection("usuarios").document(uid).set(backup)
            .addOnSuccessListener {
                println("DEBUG_STUDIOS: Respaldo automático realizado")
            }
    }

    // --- ESTADÍSTICAS ---
    private fun calcularPorcentajeTareas() {
        viewModelScope.launch {
            repositorioTareas.totalTareas.combine(repositorioTareas.totalTareasCompletadas) { total, comp ->
                if (total > 0) ((comp.toDouble() / total.toDouble()) * 100).toInt() else 0
            }.collect { porcentajeTareas.value = it }
        }
    }

    private fun calcularPorcentajeAsistencia() {
        viewModelScope.launch {
            repositorioAuth.fechaRegistroUsuario.combine(repositorioAuth.fechasDeAvances) { fecha, avances ->
                if (fecha == null) return@combine 0
                val dias = ((System.currentTimeMillis() - fecha.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                val prod = avances.distinctBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) }.size
                if (dias > 0) ((prod.toDouble() / dias.toDouble()) * 100).toInt() else 0
            }.collect { porcentajeAsistencia.value = it }
        }
    }

    // --- FIREBASE ---
    fun verificarSesion() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        usuarioLogueado.value = firebaseUser != null

        if (firebaseUser != null) {
            // Ponemos el correo de inmediato ya que lo tenemos en el objeto Auth
            correoUsuario.value = firebaseUser.email

            // Buscamos los datos detallados en Firestore
            val db = FirebaseFirestore.getInstance()
            db.collection("usuarios").document(firebaseUser.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val nom = doc.getString("perfil.nombre") ?: ""
                        val ape = doc.getString("perfil.apellido") ?: ""

                        // Si encontramos nombre y apellido, los mandamos al LiveData
                        if (nom.isNotEmpty()) {
                            nombreUsuarioDisplay.value = "$nom $ape"
                        } else {
                            // Si no hay nombre en Firestore, usamos el correo como nombre temporal
                            nombreUsuarioDisplay.value = firebaseUser.email
                        }

                        respaldoEncontrado.value = doc.data
                    }
                }
                .addOnFailureListener {
                    // Si falla el internet, nos aseguramos de que al menos se vea el correo
                    nombreUsuarioDisplay.value = firebaseUser.email
                }
        } else {
            // IMPORTANTE: Si no hay usuario, dejamos el nombreDisplay vacío
            // para que el Fragment use su carga local sin interferencias.
            nombreUsuarioDisplay.value = ""
            correoUsuario.value = "Usuario no registrado"
        }
    }

    fun iniciarRespaldoTotal(nombre: String, apellido: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        estaCargandoRespaldo.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tareas = repositorioTareas.todasLasTareas.first()
                val finanzas = repositorioFinanzas.todosLosRegistros.first()
                realizarSubidaFirestore(user.uid, tareas, finanzas)

                withContext(Dispatchers.Main) {
                    estaCargandoRespaldo.value = false
                    verificarSesion()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { estaCargandoRespaldo.value = false }
            }
        }
    }

    fun cerrarSesion() {
        FirebaseAuth.getInstance().signOut()
        verificarSesion()
    }

    // --- EMAILJS ---
    fun generarCodigoVerificacion(): String {
        codigoGenerado = (10000..99999).random().toString()
        return codigoGenerado
    }

    fun prepararDatosEmail(nom: String, cor: String) = mapOf(
        "service_id" to "service_90aab1h",
        "template_id" to "template_rbivst8",
        "user_id" to "akRDSWMA2agOzDoiW",
        "template_params" to mapOf("nombre_usuario" to nom, "codigo" to codigoGenerado, "to_email" to cor)
    )

    fun enviarEmail(datos: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject(datos).toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("https://api.emailjs.com/api/v1.0/email/send").post(body).build()
            try { client.newCall(request).execute() } catch (e: Exception) { e.printStackTrace() }
        }
    }
}