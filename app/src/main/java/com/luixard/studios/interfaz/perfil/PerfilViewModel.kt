package com.luixard.studios.interfaz.perfil

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.datos.remoto.EmailJSManager
import com.luixard.studios.datos.repositorios.AuthRepositorio
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import com.luixard.studios.datos.repositorios.NotaRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(FlowPreview::class)
class PerfilViewModel(
    private val repositorioTareas: TareaRepositorio,
    private val repositorioAuth: AuthRepositorio,
    private val repositorioFinanzas: FinanzasRepositorio,
    private val repositorioNotas: NotaRepositorio
) : ViewModel() {

    // --- LiveData de UI ---
    val porcentajeTareas     = MutableLiveData(0)
    val porcentajeAsistencia = MutableLiveData(0)
    val usuarioLogueado      = MutableLiveData(false)
    val nombreUsuarioDisplay = MutableLiveData<String?>(null)
    val correoUsuario        = MutableLiveData("")
    val estaCargandoRespaldo = MutableLiveData(false)

    // --- Flags anti-loop ---
    // Cuando este dispositivo escribe en Firestore, el snapshot propio se ignora 1 s.
    // Cuando estamos restaurando desde la nube, el auto-backup espera.
    @Volatile private var estaSubiendoANube    = false
    @Volatile private var estaRestaurandoLocal = false

    // --- Email ---
    private val emailManager = EmailJSManager()
    val codigoGenerado: String get() = emailManager.getCodigoActual()

    private var snapshotListener: ListenerRegistration? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        calcularPorcentajeTareas()
        calcularPorcentajeAsistencia()
        verificarSesion()
        activarRespaldoAutomatico()
    }

    // -------------------------------------------------------------------------
    // ESTADÍSTICAS
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // SESIÓN
    // -------------------------------------------------------------------------

    fun verificarSesion() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        usuarioLogueado.value = firebaseUser != null

        if (firebaseUser != null) {
            correoUsuario.value = firebaseUser.email
            suscribirSnapshotListener(firebaseUser.uid)
        } else {
            snapshotListener?.remove()
            snapshotListener = null
            nombreUsuarioDisplay.value = ""
            correoUsuario.value = "Usuario no registrado"
        }
    }

    /**
     * Llama esto desde el Fragment justo después de que el usuario inicia sesión
     * o vincula su cuenta, antes de esperar el snapshot de Firestore.
     * Así el nombre aparece de inmediato sin tener que cambiar de pantalla.
     */
    fun actualizarNombreInmediato(nombre: String, apellido: String) {
        val nombreCompleto = "$nombre $apellido".trim()
        if (nombreCompleto.isNotEmpty()) nombreUsuarioDisplay.value = nombreCompleto
    }

    // -------------------------------------------------------------------------
    // LISTENER EN TIEMPO REAL — FIRESTORE
    // Detecta cambios de OTROS dispositivos y sincroniza localmente.
    // Si el cambio lo originó ESTE dispositivo, lo ignora para no hacer loop.
    // -------------------------------------------------------------------------

    private fun suscribirSnapshotListener(uid: String) {
        if (snapshotListener != null) return // Ya activo

        snapshotListener = FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Nombre: actualizar en UI siempre que llegue desde la nube
                val nom = snapshot.getString("perfil.nombre") ?: ""
                val ape = snapshot.getString("perfil.apellido") ?: ""
                if (nom.isNotEmpty()) nombreUsuarioDisplay.value = "$nom $ape"

                // Si YO acabo de subir estos datos, o ya estoy restaurando → ignorar
                if (estaSubiendoANube || estaRestaurandoLocal) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                restaurarTodoDesdeNube(data)
            }
    }

    // -------------------------------------------------------------------------
    // RESTAURACIÓN NUBE → LOCAL
    //
    // ESTRATEGIA ANTI-DUPLICADOS:
    //   Antes de insertar, se limpian las tablas locales.
    //   Firestore es la única fuente de verdad.
    //   Así no importa cuántos dispositivos haya — siempre quedan sincronizados.
    // -------------------------------------------------------------------------

    private fun restaurarTodoDesdeNube(data: Map<String, Any>) {
        val backup = data["datos"] as? Map<*, *> ?: return
        val hoy = fmt.format(Date())

        estaRestaurandoLocal = true
        estaCargandoRespaldo.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ── TAREAS ──────────────────────────────────────────────────
                val tareasRaw = backup["tareas"] as? List<Map<String, Any>> ?: emptyList()
                if (tareasRaw.isNotEmpty()) {
                    val listaTareas = tareasRaw.map { m ->
                        Tarea(
                            // Recuperar el ID real, si no existe usa 0
                            id_tarea          = (m["id_tarea"] as? Long)?.toInt() ?: 0,
                            id_materia        = (m["id_materia"] as? Long)?.toInt(),
                            id_prioridad      = m["id_prioridad"] as? String ?: "MEDIA",
                            titulo_tarea      = m["titulo_tarea"] as? String ?: "",
                            descripcion_tarea = m["descripcion_tarea"] as? String,
                            fecha_entrega     = m["fecha_entrega"] as? String ?: hoy,
                            es_completada     = m["es_completada"] as? Boolean ?: false,
                            esta_borrada      = m["esta_borrada"] as? Boolean ?: false,
                            fecha_creacion    = (m["fecha_creacion"] as? Long) ?: System.currentTimeMillis()
                        )
                    }
                    // ELIMINADO: repositorioTareas.eliminarTodas()
                    repositorioTareas.restaurarTareasMasivo(listaTareas)
                }

                // ── NOTAS ───────────────────────────────────────────────────
                val notasRaw = backup["notas"] as? List<Map<String, Any>> ?: emptyList()
                if (notasRaw.isNotEmpty()) {
                    val listaNotas = notasRaw.map { m ->
                        Nota(
                            // Recuperar el ID real
                            id_nota        = (m["id_nota"] as? Long)?.toInt() ?: 0,
                            titulo         = m["titulo"] as? String ?: "",
                            contenido      = m["contenido"] as? String ?: "",
                            fecha_creacion = m["fecha_creacion"] as? String ?: hoy,
                            color_fondo    = m["color_fondo"] as? String
                        )
                    }
                    // ELIMINADO: repositorioNotas.eliminarTodas()
                    listaNotas.forEach { repositorioNotas.agregarNota(it) }
                }

                // ── FINANZAS ────────────────────────────────────────────────
                val finanzasRaw = backup["finanzas"] as? List<Map<String, Any>> ?: emptyList()
                if (finanzasRaw.isNotEmpty()) {
                    val listaFinanzas = finanzasRaw.map { m ->
                        PresupuestoSemanal(
                            // Recuperar el ID real
                            id_finanza               = (m["id_finanza"] as? Long)?.toInt() ?: 0,
                            id_usuario               = (m["id_usuario"] as? Long)?.toInt(),
                            presupuesto_semanal_meta = m["presupuesto_semanal_meta"] as? Double ?: 0.0,
                            fecha_inicio             = Date(m["fecha_inicio"] as? Long ?: System.currentTimeMillis()),
                            fecha_fin                = (m["fecha_fin"] as? Long)?.let { Date(it) }
                        )
                    }
                    // ELIMINADO: repositorioFinanzas.eliminarTodos()
                    repositorioFinanzas.restaurarDatosFinanzas(listaFinanzas)
                }

            } catch (e: Exception) {
                // Fallo silencioso — datos locales previos ya fueron limpiados,
                // el siguiente snapshot volverá a intentar la restauración.
            } finally {
                withContext(Dispatchers.Main) {
                    estaCargandoRespaldo.value = false
                    estaRestaurandoLocal = false
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // RESPALDO AUTOMÁTICO LOCAL → NUBE
    // debounce de 500 ms: rápido pero sin saturar Firestore con cada tecla.
    // -------------------------------------------------------------------------

    private fun activarRespaldoAutomatico() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                repositorioTareas.todasLasTareas,
                repositorioNotas.todasLasNotas,
                repositorioFinanzas.todosLosRegistros
            ) { tareas, notas, finanzas ->
                Triple(tareas, notas, finanzas)
            }
                .debounce(500) // 500 ms es suficiente; evita subidas por cada letra
                .collect { (tareas, notas, finanzas) ->
                    val user = FirebaseAuth.getInstance().currentUser ?: return@collect
                    if (estaRestaurandoLocal) return@collect
                    realizarSubidaFirestore(user.uid, tareas, notas, finanzas)
                }
        }
    }

    // -------------------------------------------------------------------------
    // SERIALIZACIÓN
    // Firestore no serializa data classes de Room — hay que hacerlo a mano.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
// SERIALIZACIÓN
// -------------------------------------------------------------------------

    private fun tareaAMap(t: Tarea): Map<String, Any?> = mapOf(
        "id_tarea"          to t.id_tarea,
        "titulo_tarea"      to t.titulo_tarea,
        "descripcion_tarea" to t.descripcion_tarea,
        "fecha_entrega"     to t.fecha_entrega,
        "id_prioridad"      to t.id_prioridad,
        "id_materia"        to t.id_materia,
        "es_completada"     to t.es_completada,
        "esta_borrada"      to t.esta_borrada,
        "fecha_creacion"    to t.fecha_creacion
    )

    private fun notaAMap(n: Nota): Map<String, Any?> = mapOf(
        "id_nota"        to n.id_nota,
        "titulo"         to n.titulo,
        "contenido"      to n.contenido,
        "fecha_creacion" to n.fecha_creacion,
        "color_fondo"    to n.color_fondo
    )

    private fun finanzaAMap(f: PresupuestoSemanal): Map<String, Any?> = mapOf(
        "id_finanza"               to f.id_finanza,
        "id_usuario"               to f.id_usuario,
        "presupuesto_semanal_meta" to f.presupuesto_semanal_meta,
        "fecha_inicio"             to f.fecha_inicio.time,
        "fecha_fin"                to f.fecha_fin?.time
    )

    private fun realizarSubidaFirestore(
        uid: String,
        tareas: List<Tarea>,
        notas: List<Nota>,
        finanzas: List<PresupuestoSemanal>
    ) {
        val partes = (nombreUsuarioDisplay.value ?: "").split(" ")

        val documento = hashMapOf(
            "perfil" to mapOf(
                "nombre"      to (partes.getOrNull(0) ?: ""),
                "apellido"    to (partes.drop(1).joinToString(" ")),
                "correo"      to correoUsuario.value,
                "ultima_sinc" to Timestamp.now()
            ),
            "datos" to mapOf(
                "tareas"   to tareas.map { tareaAMap(it) },
                "notas"    to notas.map { notaAMap(it) },
                "finanzas" to finanzas.map { finanzaAMap(it) }
            )
        )

        estaSubiendoANube = true
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(documento) // set() reemplaza el documento completo
            .addOnCompleteListener {
                // 1 s de margen para que el snapshot listener propio
                // reciba su propia escritura antes de volver a escuchar
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    estaSubiendoANube = false
                }, 1_000)
            }
    }

    // -------------------------------------------------------------------------
    // RESPALDO INICIAL (al vincular cuenta por primera vez)
    // -------------------------------------------------------------------------

    fun iniciarRespaldoTotal(nombre: String, apellido: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        estaCargandoRespaldo.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tareas   = repositorioTareas.todasLasTareas.first()
                val notas    = repositorioNotas.todasLasNotas.first()
                val finanzas = repositorioFinanzas.todosLosRegistros.first()
                realizarSubidaFirestore(user.uid, tareas, notas, finanzas)
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
        snapshotListener?.remove()
        snapshotListener = null
        FirebaseAuth.getInstance().signOut()
        verificarSesion()
    }

    // -------------------------------------------------------------------------
    // EMAIL
    // -------------------------------------------------------------------------

    fun generarCodigoVerificacion() = emailManager.generarCodigoVerificacion()

    fun prepararDatosEmail(nombre: String, correo: String): Map<String, Any> =
        emailManager.prepararDatosEmail(nombre, correo)

    fun enviarEmail(datos: Map<String, Any>) {
        viewModelScope.launch { emailManager.enviarEmail(datos) }
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
    }
}