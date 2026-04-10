package com.luixard.studios.datos.sync

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.datos.modelos.Transaccion
import com.luixard.studios.datos.repositorios.FinanzasRepositorio
import com.luixard.studios.datos.repositorios.NotaRepositorio
import com.luixard.studios.datos.repositorios.TareaRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(FlowPreview::class)
object SyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var repositorioTareas:   TareaRepositorio?    = null
    private var repositorioNotas:    NotaRepositorio?     = null
    private var repositorioFinanzas: FinanzasRepositorio? = null
    private var inicializado = false

    // ── Anti-loop ─────────────────────────────────────────────────────────────
    @Volatile private var estaSubiendoANube      = false
    @Volatile private var estaRestaurandoLocal   = false
    @Volatile private var ignorarProximoSnapshot = false

    private var snapshotListener: ListenerRegistration? = null
    private var autoBackupJob:    Job? = null

    private val _estaCargando  = MutableStateFlow(false)
    val estaCargando: StateFlow<Boolean> = _estaCargando

    private val _nombreDisplay = MutableStateFlow("")
    val nombreDisplay: StateFlow<String> = _nombreDisplay

    private var nombreCached   = ""
    private var apellidoCached = ""

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    fun init(
        repoTareas:   TareaRepositorio,
        repoNotas:    NotaRepositorio,
        repoFinanzas: FinanzasRepositorio
    ) {
        if (inicializado) return
        repositorioTareas   = repoTareas
        repositorioNotas    = repoNotas
        repositorioFinanzas = repoFinanzas
        inicializado        = true

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            activarEscuchaEnTiempoReal(uid)
            activarRespaldoAutomatico()
        }
    }

    private fun listo() = inicializado
            && repositorioTareas   != null
            && repositorioNotas    != null
            && repositorioFinanzas != null

    fun actualizarNombre(nombre: String, apellido: String) {
        nombreCached         = nombre
        apellidoCached       = apellido
        _nombreDisplay.value = "$nombre $apellido".trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENTOS DE AUTENTICACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    fun onNuevaCuentaVinculada(nombre: String, apellido: String) {
        if (!listo()) return
        actualizarNombre(nombre, apellido)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        _estaCargando.value = true
        scope.launch {
            try {
                val tareas        = repositorioTareas!!.todasLasTareas.first()
                val notas         = repositorioNotas!!.todasLasNotas.first()
                val finanzas      = repositorioFinanzas!!.todosLosRegistros.first()
                val transacciones = repositorioFinanzas!!.obtenerTodasLasTransacciones()

                ignorarProximoSnapshot = true
                estaSubiendoANube      = true
                subirSync(uid, tareas, notas, finanzas, transacciones)
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { _estaCargando.value = false }
            }
        }
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    fun onInicioSesion(uid: String, nombre: String = "", apellido: String = "") {
        if (!listo()) return
        if (nombre.isNotEmpty()) actualizarNombre(nombre, apellido)
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    fun onSesionActiva(uid: String) {
        if (!listo()) return
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    fun onCerrarSesion() {
        snapshotListener?.remove()
        snapshotListener         = null
        autoBackupJob?.cancel()
        autoBackupJob            = null
        estaSubiendoANube        = false
        estaRestaurandoLocal     = false
        ignorarProximoSnapshot   = false
        _nombreDisplay.value     = ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LISTENER EN TIEMPO REAL
    // ─────────────────────────────────────────────────────────────────────────

    private fun activarEscuchaEnTiempoReal(uid: String) {
        if (snapshotListener != null) return

        snapshotListener = FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val nom = snapshot.getString("perfil.nombre") ?: ""
                val ape = snapshot.getString("perfil.apellido") ?: ""
                if (nom.isNotEmpty()) actualizarNombre(nom, ape)

                if (ignorarProximoSnapshot) {
                    ignorarProximoSnapshot = false
                    return@addSnapshotListener
                }
                if (estaSubiendoANube || estaRestaurandoLocal) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                scope.launch { restaurarDesdeNube(data) }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-BACKUP — ahora con 4 flows: tareas + notas + finanzas + transacciones
    //
    // ANTES: solo se combinaban tareas, notas y presupuestos.
    // Si el usuario agregaba un gasto/ingreso (tabla transacciones), ese cambio
    // no disparaba el combine → nunca se subía la transacción.
    //
    // AHORA: se incluye todasLasTransaccionesFlow para que cualquier cambio en
    // la tabla transacciones dispare inmediatamente el backup.
    // ─────────────────────────────────────────────────────────────────────────

    private fun activarRespaldoAutomatico() {
        if (autoBackupJob?.isActive == true) return
        if (!listo()) return

        autoBackupJob = scope.launch {
            combine(
                repositorioTareas!!.todasLasTareas,
                repositorioNotas!!.todasLasNotas,
                repositorioFinanzas!!.todosLosRegistros,
                repositorioFinanzas!!.todasLasTransaccionesFlow  // ← nuevo
            ) { tareas, notas, finanzas, transacciones ->
                // Empaquetar todo en un solo objeto para el collect
                Cuadrupla(tareas, notas, finanzas, transacciones)
            }
                .debounce(500)
                .collect { (tareas, notas, finanzas, transacciones) ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@collect
                    if (estaRestaurandoLocal) return@collect
                    subir(uid, tareas, notas, finanzas, transacciones)
                }
        }
    }

    // Data class auxiliar porque combine() de 4 flows no tiene desestructuración directa
    private data class Cuadrupla(
        val tareas:        List<Tarea>,
        val notas:         List<Nota>,
        val finanzas:      List<PresupuestoSemanal>,
        val transacciones: List<Transaccion>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // RESTAURACIÓN — Firestore → Room
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun restaurarDesdeNube(data: Map<String, Any>) {
        if (!listo()) return
        val backup = data["datos"] as? Map<*, *> ?: return
        val hoy    = fmt.format(Date())
        val ahora  = System.currentTimeMillis()

        estaRestaurandoLocal = true
        withContext(Dispatchers.Main) { _estaCargando.value = true }

        try {
            // ── TAREAS ──────────────────────────────────────────────────────
            val tareasRaw = backup["tareas"] as? List<Map<String, Any>> ?: emptyList()
            repositorioTareas!!.eliminarTodas()
            if (tareasRaw.isNotEmpty()) {
                repositorioTareas!!.restaurarTareasMasivo(tareasRaw.map { m ->
                    Tarea(
                        id_tarea          = (m["id_tarea"]         as? Long)?.toInt() ?: 0,
                        id_materia        = (m["id_materia"]       as? Long)?.toInt(),
                        id_prioridad      = m["id_prioridad"]      as? String ?: "MEDIA",
                        titulo_tarea      = m["titulo_tarea"]      as? String ?: "",
                        descripcion_tarea = m["descripcion_tarea"] as? String,
                        fecha_entrega     = m["fecha_entrega"]     as? String ?: hoy,
                        es_completada     = m["es_completada"]     as? Boolean ?: false,
                        esta_borrada      = m["esta_borrada"]      as? Boolean ?: false,
                        fecha_creacion    = (m["fecha_creacion"]   as? Long) ?: ahora
                    )
                })
            }

            // ── NOTAS ───────────────────────────────────────────────────────
            val notasRaw = backup["notas"] as? List<Map<String, Any>> ?: emptyList()
            repositorioNotas!!.eliminarTodas()
            if (notasRaw.isNotEmpty()) {
                notasRaw.forEach { m ->
                    repositorioNotas!!.insertarNota(Nota(
                        id_nota        = (m["id_nota"]       as? Long)?.toInt() ?: 0,
                        titulo         = m["titulo"]          as? String ?: "",
                        contenido      = m["contenido"]       as? String ?: "",
                        fecha_creacion = m["fecha_creacion"]  as? String ?: hoy,
                        color_fondo    = m["color_fondo"]     as? String
                    ))
                }
            }

            // ── FINANZAS + TRANSACCIONES ──────────────────────────────────
            // Limpiar ambas tablas juntas antes de restaurar
            repositorioFinanzas!!.eliminarTodos()

            val finanzasRaw = backup["finanzas"] as? List<Map<String, Any>> ?: emptyList()
            if (finanzasRaw.isNotEmpty()) {
                repositorioFinanzas!!.restaurarDatosFinanzas(finanzasRaw.map { m ->
                    PresupuestoSemanal(
                        id_finanza               = (m["id_finanza"]              as? Long)?.toInt() ?: 0,
                        id_usuario               = (m["id_usuario"]              as? Long)?.toInt(),
                        presupuesto_semanal_meta = m["presupuesto_semanal_meta"] as? Double ?: 0.0,
                        fecha_inicio             = Date(m["fecha_inicio"]         as? Long ?: ahora),
                        fecha_fin                = (m["fecha_fin"]                as? Long)?.let { Date(it) }
                    )
                })
            }

            val txRaw = backup["transacciones"] as? List<Map<String, Any>> ?: emptyList()
            if (txRaw.isNotEmpty()) {
                repositorioFinanzas!!.restaurarTransacciones(txRaw.map { m ->
                    Transaccion(
                        id_transaccion    = (m["id_transaccion"]   as? Long)?.toInt() ?: 0,
                        id_usuario        = (m["id_usuario"]       as? Long)?.toInt(),
                        id_finanza        = (m["id_finanza"]       as? Long)?.toInt() ?: 0,
                        id_categoria      = (m["id_categoria"]     as? Long)?.toInt(),
                        tipo_transaccion  = m["tipo_transaccion"]  as? String ?: "Gasto",
                        monto             = m["monto"]             as? Double ?: 0.0,
                        fecha_transaccion = Date(m["fecha_transaccion"] as? Long ?: ahora),
                        nota_transaccion  = m["nota_transaccion"]  as? String
                    )
                })
            }

        } catch (_: Exception) {
        } finally {
            estaRestaurandoLocal = false
            withContext(Dispatchers.Main) { _estaCargando.value = false }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBIDA suspend (backup inicial al vincular)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun subirSync(
        uid: String, tareas: List<Tarea>, notas: List<Nota>,
        finanzas: List<PresupuestoSemanal>, transacciones: List<Transaccion>
    ) {
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(buildDoc(tareas, notas, finanzas, transacciones))
            .addOnCompleteListener {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    estaSubiendoANube = false
                }, 2_000)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBIDA fire-and-forget (auto-backup)
    // ─────────────────────────────────────────────────────────────────────────

    private fun subir(
        uid: String, tareas: List<Tarea>, notas: List<Nota>,
        finanzas: List<PresupuestoSemanal>, transacciones: List<Transaccion>
    ) {
        estaSubiendoANube = true
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(buildDoc(tareas, notas, finanzas, transacciones))
            .addOnCompleteListener {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    estaSubiendoANube = false
                }, 2_000)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL DOCUMENTO
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildDoc(
        tareas: List<Tarea>, notas: List<Nota>,
        finanzas: List<PresupuestoSemanal>, transacciones: List<Transaccion>
    ): HashMap<String, Any> = hashMapOf(
        "perfil" to mapOf(
            "nombre"      to nombreCached,
            "apellido"    to apellidoCached,
            "correo"      to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            "ultima_sinc" to Timestamp.now()
        ),
        "datos" to mapOf(
            "tareas"        to tareas.map        { tareaAMap(it)       },
            "notas"         to notas.map         { notaAMap(it)        },
            "finanzas"      to finanzas.map      { finanzaAMap(it)     },
            "transacciones" to transacciones.map { transaccionAMap(it) }
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SERIALIZACIÓN
    // ─────────────────────────────────────────────────────────────────────────

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

    private fun transaccionAMap(t: Transaccion): Map<String, Any?> = mapOf(
        "id_transaccion"    to t.id_transaccion,
        "id_usuario"        to t.id_usuario,
        "id_finanza"        to t.id_finanza,
        "id_categoria"      to t.id_categoria,
        "tipo_transaccion"  to t.tipo_transaccion,
        "monto"             to t.monto,
        "fecha_transaccion" to t.fecha_transaccion.time,
        "nota_transaccion"  to t.nota_transaccion
    )
}