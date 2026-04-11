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
import java.util.UUID
@OptIn(FlowPreview::class)
object SyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var repositorioTareas:   TareaRepositorio?    = null
    private var repositorioNotas:    NotaRepositorio?     = null
    private var repositorioFinanzas: FinanzasRepositorio? = null
    private var inicializado = false

    @Volatile private var estaHaciendoMerge = false

    @Volatile private var ignorarPrimerSnapshot = false

    private var snapshotListener: ListenerRegistration? = null
    private var autoBackupJob:    Job? = null

    val estaCargando  = MutableStateFlow(false)
    val nombreDisplay = MutableStateFlow("")

    private var nombreCached   = ""
    private var apellidoCached = ""

    // ─────────────────────────────────────────────────────────────────────────
    // INIT — llamar en AplicacionStudiOS.onCreate()
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
            activarAutoBackup()
        }
    }

    private fun listo() = inicializado
            && repositorioTareas   != null
            && repositorioNotas    != null
            && repositorioFinanzas != null

    fun actualizarNombre(nombre: String, apellido: String) {
        nombreCached         = nombre
        apellidoCached       = apellido
        nombreDisplay.value  = "$nombre $apellido".trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENTOS DE AUTENTICACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    /** Cuenta nueva creada → subir datos locales primero. */
    fun onNuevaCuentaVinculada(nombre: String, apellido: String) {
        if (!listo()) return
        actualizarNombre(nombre, apellido)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        estaCargando.value    = true
        ignorarPrimerSnapshot = true

        scope.launch {
            try {
                subirAsync(uid, tomarSnapshotLocal())
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { estaCargando.value = false }
            }
        }
        activarEscuchaEnTiempoReal(uid)
        activarAutoBackup()
    }

    /**
     * Login exitoso → el snapshot listener se encarga de mergear.
     * NO subir datos locales aquí para no pisar la nube antes de saber
     * qué hay en ella.
     */
    fun onInicioSesion(uid: String, nombre: String = "", apellido: String = "") {
        if (!listo()) return
        if (nombre.isNotEmpty()) actualizarNombre(nombre, apellido)
        activarEscuchaEnTiempoReal(uid)
        activarAutoBackup()
    }

    /** App reiniciada con sesión activa → reactivar listeners. */
    fun onSesionActiva(uid: String) {
        if (!listo()) return
        activarEscuchaEnTiempoReal(uid)
        activarAutoBackup()
    }

    fun onCerrarSesion() {
        snapshotListener?.remove()
        snapshotListener      = null
        autoBackupJob?.cancel()
        autoBackupJob         = null
        estaHaciendoMerge     = false
        ignorarPrimerSnapshot = false
        nombreDisplay.value   = ""
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

                // Ignorar ecos de nuestras propias escrituras
                if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                // Capa extra de seguridad al vincular cuenta nueva
                if (ignorarPrimerSnapshot) {
                    ignorarPrimerSnapshot = false
                    return@addSnapshotListener
                }

                if (estaHaciendoMerge) return@addSnapshotListener

                // Nombre: Firestore es fuente de verdad para el nombre
                val nom = snapshot.getString("perfil.nombre") ?: ""
                val ape = snapshot.getString("perfil.apellido") ?: ""
                if (nom.isNotEmpty()) actualizarNombre(nom, ape)

                val data = snapshot.data ?: return@addSnapshotListener
                scope.launch { hacerMerge(data) }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-BACKUP — 4 flows; debounce 800 ms
    // El debounce más largo (800ms) da margen para que el merge termine
    // antes de que el autobackup suba el estado fusionado.
    // ─────────────────────────────────────────────────────────────────────────

    private fun activarAutoBackup() {
        if (autoBackupJob?.isActive == true) return
        if (!listo()) return

        autoBackupJob = scope.launch {
            combine(
                repositorioTareas!!.todasLasTareas,
                repositorioNotas!!.todasLasNotasParaBackup,
                repositorioFinanzas!!.todosLosRegistros,
                repositorioFinanzas!!.todasLasTransaccionesFlow
            ) { tareas, notas, finanzas, transacciones ->
                Snapshot(tareas, notas, finanzas, transacciones)
            }
                .debounce(800)
                .collect { snap ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@collect
                    if (estaHaciendoMerge) return@collect
                    subirAsync(uid, snap)
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MERGE — last-write-wins por syncId + updatedAt
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun hacerMerge(data: Map<String, Any>) {
        if (!listo()) return
        val backup = data["datos"] as? Map<*, *> ?: return
        val hoy    = fmt.format(Date())
        val ahora  = System.currentTimeMillis()

        estaHaciendoMerge = true
        withContext(Dispatchers.Main) { estaCargando.value = true }

        try {
            // ── TAREAS ──────────────────────────────────────────────────────
            val remotasTareas  = parseTareas(backup["tareas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
            val localesTareas  = repositorioTareas!!.obtenerTodas()
            val fusionadaTareas = merge(localesTareas, remotasTareas, { it.syncId }, { it.updatedAt })
            // insertarListaTareas usa REPLACE → actualiza si el id_tarea coincide, inserta si es nuevo
            repositorioTareas!!.restaurarTareasMasivo(fusionadaTareas)

            // ── NOTAS ───────────────────────────────────────────────────────
            val remotasNotas   = parseNotas(backup["notas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
            val localesNotas   = repositorioNotas!!.obtenerTodas()
            val fusionadaNotas = merge(localesNotas, remotasNotas, { it.syncId }, { it.updatedAt })
            fusionadaNotas.forEach { repositorioNotas!!.insertarNota(it) }

            // ── FINANZAS ─────────────────────────────────────────────────────
            // Para login en dispositivo nuevo: merge SUMA los presupuestos de
            // ambos lados (syncIds diferentes). El usuario puede editar después.
            val remotasFinanzas  = parseFinanzas(backup["finanzas"] as? List<Map<String, Any>> ?: emptyList(), ahora)
            val localesFinanzas  = repositorioFinanzas!!.obtenerTodasLasFinanzasSuspend()
            val fusionadaFinanzas = merge(localesFinanzas, remotasFinanzas, { it.syncId }, { it.updatedAt })
            repositorioFinanzas!!.restaurarDatosFinanzas(fusionadaFinanzas)

            // ── TRANSACCIONES ────────────────────────────────────────────────
            val remotasTx  = parseTransacciones(backup["transacciones"] as? List<Map<String, Any>> ?: emptyList(), ahora)
            val localesTx  = repositorioFinanzas!!.obtenerTodasLasTransacciones()
            val fusionadaTx = merge(localesTx, remotasTx, { it.syncId }, { it.updatedAt })
            repositorioFinanzas!!.restaurarTransacciones(fusionadaTx)

        } catch (_: Exception) {
            // El próximo snapshot reintentará
        } finally {
            estaHaciendoMerge = false
            withContext(Dispatchers.Main) { estaCargando.value = false }
            // Subir estado fusionado para que el otro dispositivo lo reciba
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                try { subirAsync(uid, tomarSnapshotLocal()) } catch (_: Exception) { }
            }
        }
    }

    /**
     * Merge genérico last-write-wins por syncId.
     *
     *   Solo remota → insertar
     *   Solo local  → conservar (cambio offline)
     *   Ambas       → tomar la con mayor updatedAt
     */
    private fun <T> merge(
        locales:     List<T>,
        remotas:     List<T>,
        syncIdOf:    (T) -> String,
        updatedAtOf: (T) -> Long
    ): List<T> {
        val mapaLocal  = locales.associateBy { syncIdOf(it) }
        val mapaRemoto = remotas.associateBy { syncIdOf(it) }
        val resultado  = mutableListOf<T>()

        // Items remotos: ganar el más reciente
        for ((syncId, remota) in mapaRemoto) {
            val local = mapaLocal[syncId]
            resultado.add(
                if (local == null || updatedAtOf(remota) >= updatedAtOf(local)) remota
                else local
            )
        }
        // Items solo locales (offline): conservar siempre
        for ((syncId, local) in mapaLocal) {
            if (!mapaRemoto.containsKey(syncId)) resultado.add(local)
        }
        return resultado
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT LOCAL
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun tomarSnapshotLocal() = Snapshot(
        tareas        = repositorioTareas!!.todasLasTareas.first(),
        notas         = repositorioNotas!!.todasLasNotasParaBackup.first(),
        finanzas      = repositorioFinanzas!!.todosLosRegistros.first(),
        transacciones = repositorioFinanzas!!.obtenerTodasLasTransacciones()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SUBIDA
    // ─────────────────────────────────────────────────────────────────────────

    private fun subirAsync(uid: String, snap: Snapshot) {
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(buildDoc(snap))
        // No necesitamos hacer nada en el listener de éxito:
        // hasPendingWrites maneja el eco automáticamente
    }

    private fun buildDoc(snap: Snapshot): HashMap<String, Any> = hashMapOf(
        "perfil" to mapOf(
            "nombre"      to nombreCached,
            "apellido"    to apellidoCached,
            "correo"      to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            "ultima_sinc" to Timestamp.now()
        ),
        "datos" to mapOf(
            "tareas"        to snap.tareas.map        { tareaAMap(it)       },
            "notas"         to snap.notas.map         { notaAMap(it)        },
            "finanzas"      to snap.finanzas.map      { finanzaAMap(it)     },
            "transacciones" to snap.transacciones.map { transaccionAMap(it) }
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PARSERS — Firestore Map → modelo Room
    // syncId y updatedAt son obligatorios para el merge
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseTareas(raw: List<Map<String, Any>>, hoy: String, ahora: Long) = raw.map { m ->
        Tarea(
            id_tarea          = (m["id_tarea"]         as? Long)?.toInt() ?: 0,
            id_materia        = (m["id_materia"]       as? Long)?.toInt(),
            id_prioridad      = m["id_prioridad"]      as? String ?: "MEDIA",
            titulo_tarea      = m["titulo_tarea"]      as? String ?: "",
            descripcion_tarea = m["descripcion_tarea"] as? String,
            fecha_entrega     = m["fecha_entrega"]     as? String ?: hoy,
            es_completada     = m["es_completada"]     as? Boolean ?: false,
            esta_borrada      = m["esta_borrada"]      as? Boolean ?: false,
            fecha_creacion    = (m["fecha_creacion"]   as? Long) ?: ahora,
            updatedAt         = (m["updated_at"]       as? Long) ?: ahora,
            syncId            = m["sync_id"]           as? String ?: UUID.randomUUID().toString()
        )
    }

    private fun parseNotas(raw: List<Map<String, Any>>, hoy: String, ahora: Long) = raw.map { m ->
        Nota(
            id_nota        = (m["id_nota"]      as? Long)?.toInt() ?: 0,
            titulo         = m["titulo"]         as? String ?: "",
            contenido      = m["contenido"]      as? String ?: "",
            fecha_creacion = m["fecha_creacion"] as? String ?: hoy,
            color_fondo    = m["color_fondo"]    as? String,
            esta_borrada   = m["esta_borrada"]   as? Boolean ?: false,
            updatedAt      = (m["updated_at"]    as? Long) ?: ahora,
            syncId         = m["sync_id"]        as? String ?: UUID.randomUUID().toString()
        )
    }

    private fun parseFinanzas(raw: List<Map<String, Any>>, ahora: Long) = raw.map { m ->
        PresupuestoSemanal(
            id_finanza               = (m["id_finanza"]              as? Long)?.toInt() ?: 0,
            id_usuario               = (m["id_usuario"]              as? Long)?.toInt(),
            presupuesto_semanal_meta = m["presupuesto_semanal_meta"] as? Double ?: 0.0,
            fecha_inicio             = Date(m["fecha_inicio"]         as? Long ?: ahora),
            fecha_fin                = (m["fecha_fin"]                as? Long)?.let { Date(it) },
            updatedAt                = (m["updated_at"]               as? Long) ?: ahora,
            syncId                   = m["sync_id"]                   as? String ?: UUID.randomUUID().toString()
        )
    }

    private fun parseTransacciones(raw: List<Map<String, Any>>, ahora: Long) = raw.map { m ->
        Transaccion(
            id_transaccion    = (m["id_transaccion"]    as? Long)?.toInt() ?: 0,
            id_usuario        = (m["id_usuario"]        as? Long)?.toInt(),
            id_finanza        = (m["id_finanza"]        as? Long)?.toInt() ?: 0,
            id_categoria      = (m["id_categoria"]      as? Long)?.toInt(),
            tipo_transaccion  = m["tipo_transaccion"]   as? String ?: "Gasto",
            monto             = m["monto"]              as? Double ?: 0.0,
            fecha_transaccion = Date(m["fecha_transaccion"] as? Long ?: ahora),
            nota_transaccion  = m["nota_transaccion"]   as? String,
            esta_borrada      = m["esta_borrada"]       as? Boolean ?: false,
            updatedAt         = (m["updated_at"]        as? Long) ?: ahora,
            syncId            = m["sync_id"]            as? String ?: UUID.randomUUID().toString()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SERIALIZACIÓN — incluye syncId y updatedAt (obligatorios para el merge)
    // ─────────────────────────────────────────────────────────────────────────

    private fun tareaAMap(t: Tarea) = mapOf(
        "id_tarea"          to t.id_tarea,
        "titulo_tarea"      to t.titulo_tarea,
        "descripcion_tarea" to t.descripcion_tarea,
        "fecha_entrega"     to t.fecha_entrega,
        "id_prioridad"      to t.id_prioridad,
        "id_materia"        to t.id_materia,
        "es_completada"     to t.es_completada,
        "esta_borrada"      to t.esta_borrada,
        "fecha_creacion"    to t.fecha_creacion,
        "updated_at"        to t.updatedAt,
        "sync_id"           to t.syncId
    )

    private fun notaAMap(n: Nota) = mapOf(
        "id_nota"        to n.id_nota,
        "titulo"         to n.titulo,
        "contenido"      to n.contenido,
        "fecha_creacion" to n.fecha_creacion,
        "color_fondo"    to n.color_fondo,
        "esta_borrada"   to n.esta_borrada,
        "updated_at"     to n.updatedAt,
        "sync_id"        to n.syncId
    )

    private fun finanzaAMap(f: PresupuestoSemanal) = mapOf(
        "id_finanza"               to f.id_finanza,
        "id_usuario"               to f.id_usuario,
        "presupuesto_semanal_meta" to f.presupuesto_semanal_meta,
        "fecha_inicio"             to f.fecha_inicio.time,
        "fecha_fin"                to f.fecha_fin?.time,
        "updated_at"               to f.updatedAt,
        "sync_id"                  to f.syncId
    )

    private fun transaccionAMap(t: Transaccion) = mapOf(
        "id_transaccion"    to t.id_transaccion,
        "id_usuario"        to t.id_usuario,
        "id_finanza"        to t.id_finanza,
        "id_categoria"      to t.id_categoria,
        "tipo_transaccion"  to t.tipo_transaccion,
        "monto"             to t.monto,
        "fecha_transaccion" to t.fecha_transaccion.time,
        "nota_transaccion"  to t.nota_transaccion,
        "esta_borrada"      to t.esta_borrada,
        "updated_at"        to t.updatedAt,
        "sync_id"           to t.syncId
    )

    private data class Snapshot(
        val tareas:        List<Tarea>,
        val notas:         List<Nota>,
        val finanzas:      List<PresupuestoSemanal>,
        val transacciones: List<Transaccion>
    )
}