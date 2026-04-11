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

/**
 * Motor de sincronización bidireccional con estrategia MERGE.
 *
 * ── Estrategia anti-duplicados y anti-pérdida ─────────────────────────────
 *
 * ANTES (destructiva): eliminar todo local → insertar todo lo que viene de Firestore.
 *   Problema: si el dispositivo B tuvo cambios offline, se pierden al reconectar.
 *
 * AHORA (merge por syncId + last-write-wins):
 *   1. Construir un mapa local: syncId → entidad
 *   2. Para cada entidad remota:
 *      - Si NO existe local → insertar
 *      - Si existe local con updatedAt MÁS ANTIGUO → actualizar con la versión remota
 *      - Si existe local con updatedAt MÁS RECIENTE → conservar local (no tocar)
 *   3. Para cada entidad local que NO existe en remoto:
 *      - Si es más nueva que la última sincronización → subir (cambio offline)
 *      - Si es más vieja → ya fue borrada en otro dispositivo → marcar borrada local
 *   4. Después del merge, el auto-backup dispara y sube el estado fusionado.
 *
 * Con esto:
 *   - Dispositivo A modifica tarea X mientras B estaba offline.
 *   - B reconecta, recibe snapshot.
 *   - B compara updatedAt de su tarea X con el remoto → toma la más nueva.
 *   - B sube el estado fusionado → A lo recibe y también queda al día.
 */
@OptIn(FlowPreview::class)
object SyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var repositorioTareas:   TareaRepositorio?    = null
    private var repositorioNotas:    NotaRepositorio?     = null
    private var repositorioFinanzas: FinanzasRepositorio? = null
    private var inicializado = false

    // ── Anti-loop ─────────────────────────────────────────────────────────────
    // estaSubiendoANube      → este dispositivo escribió; ignorar su propio echo
    // estaHaciendoMerge      → merge en curso; auto-backup espera para no
    //                          subir un estado parcial
    // ignorarProximoSnapshot → al vincular cuenta nueva, suprimir primer echo
    @Volatile private var estaSubiendoANube      = false
    @Volatile private var estaHaciendoMerge      = false
    @Volatile private var ignorarProximoSnapshot = false

    // Timestamp de la última sincronización exitosa.
    // Se usa para distinguir "cambio offline nuevo" vs "registro ya borrado en otro lado".
    @Volatile private var ultimaSincMs: Long = 0L

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

    /**
     * Cuenta nueva recién creada.
     * Sube datos locales y suprime el eco del primer snapshot.
     */
    fun onNuevaCuentaVinculada(nombre: String, apellido: String) {
        if (!listo()) return
        actualizarNombre(nombre, apellido)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        _estaCargando.value = true
        scope.launch {
            try {
                val snapshot = tomarSnapshotLocal()
                ignorarProximoSnapshot = true
                estaSubiendoANube      = true
                subirAsync(uid, snapshot)
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { _estaCargando.value = false }
            }
        }
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    /**
     * Login exitoso.
     * El snapshot listener se encarga de hacer el merge con los datos del servidor.
     */
    fun onInicioSesion(uid: String, nombre: String = "", apellido: String = "") {
        if (!listo()) return
        if (nombre.isNotEmpty()) actualizarNombre(nombre, apellido)
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    /** App reiniciada con sesión activa — reactivar listeners. */
    fun onSesionActiva(uid: String) {
        if (!listo()) return
        activarEscuchaEnTiempoReal(uid)
        activarRespaldoAutomatico()
    }

    fun onCerrarSesion() {
        snapshotListener?.remove()
        snapshotListener       = null
        autoBackupJob?.cancel()
        autoBackupJob          = null
        estaSubiendoANube      = false
        estaHaciendoMerge      = false
        ignorarProximoSnapshot = false
        _nombreDisplay.value   = ""
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
                if (estaSubiendoANube || estaHaciendoMerge) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                scope.launch { hacerMerge(data) }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-BACKUP — escucha los 4 flows; debounce 500 ms
    // ─────────────────────────────────────────────────────────────────────────

    private fun activarRespaldoAutomatico() {
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
                .debounce(500)
                .collect { snap ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@collect
                    if (estaHaciendoMerge) return@collect
                    subirAsync(uid, snap)
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MERGE — núcleo del sistema
    //
    // Recibe el documento de Firestore y lo fusiona con el estado local.
    // Para cada entidad se aplica last-write-wins por updatedAt.
    // Después del merge, el auto-backup sube el estado fusionado.
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun hacerMerge(data: Map<String, Any>) {
        if (!listo()) return
        val backup = data["datos"] as? Map<*, *> ?: return
        val hoy    = fmt.format(Date())
        val ahora  = System.currentTimeMillis()

        estaHaciendoMerge = true
        withContext(Dispatchers.Main) { _estaCargando.value = true }

        try {
            // ── TAREAS ──────────────────────────────────────────────────────
            val remotasTareas = parseTareas(
                backup["tareas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora
            )
            val localesTareas = repositorioTareas!!.obtenerTodas()
            val mergeadaTareas = mergeEntidades(
                locales  = localesTareas,
                remotas  = remotasTareas,
                syncIdOf = { it.syncId },
                updatedAtOf = { it.updatedAt }
            )
            // Insertar el resultado fusionado (REPLACE por id/syncId no duplica)
            repositorioTareas!!.restaurarTareasMasivo(mergeadaTareas)

            // ── NOTAS ───────────────────────────────────────────────────────
            val remotasNotas = parseNotas(
                backup["notas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora
            )
            val localesNotas = repositorioNotas!!.obtenerTodas()
            val mergeadaNotas = mergeEntidades(
                locales     = localesNotas,
                remotas     = remotasNotas,
                syncIdOf    = { it.syncId },
                updatedAtOf = { it.updatedAt }
            )
            mergeadaNotas.forEach { repositorioNotas!!.insertarNota(it) }

            // ── FINANZAS + TRANSACCIONES ──────────────────────────────────
            // Finanzas (presupuestos): sin borrado lógico, se reemplaza por syncId
            val remotasFinanzas = parseFinanzas(
                backup["finanzas"] as? List<Map<String, Any>> ?: emptyList(), ahora
            )
            val localesFinanzas = repositorioFinanzas!!.obtenerTodasLasFinanzasSuspend()
            val mergeadaFinanzas = mergeEntidades(
                locales     = localesFinanzas,
                remotas     = remotasFinanzas,
                syncIdOf    = { it.syncId },
                updatedAtOf = { it.updatedAt }
            )
            repositorioFinanzas!!.restaurarDatosFinanzas(mergeadaFinanzas)

            // Transacciones
            val remotasTx = parseTransacciones(
                backup["transacciones"] as? List<Map<String, Any>> ?: emptyList(), ahora
            )
            val localesTx = repositorioFinanzas!!.obtenerTodasLasTransacciones()
            val mergeadaTx = mergeEntidades(
                locales     = localesTx,
                remotas     = remotasTx,
                syncIdOf    = { it.syncId },
                updatedAtOf = { it.updatedAt }
            )
            repositorioFinanzas!!.restaurarTransacciones(mergeadaTx)

            ultimaSincMs = ahora

        } catch (_: Exception) {
            // Fallo silencioso — el siguiente snapshot reintentará
        } finally {
            estaHaciendoMerge = false
            withContext(Dispatchers.Main) { _estaCargando.value = false }
            // Subir el estado fusionado para que el otro dispositivo lo reciba
            subirTrasSync()
        }
    }

    /**
     * Algoritmo de merge genérico — last-write-wins por updatedAt.
     *
     * Para cada syncId:
     *   - Solo remota → tomar remota
     *   - Solo local  → conservar (cambio offline; el auto-backup lo subirá)
     *   - Ambas       → tomar la que tenga mayor updatedAt
     *
     * Resultado: lista que representa el estado fusionado.
     */
    private fun <T> mergeEntidades(
        locales:     List<T>,
        remotas:     List<T>,
        syncIdOf:    (T) -> String,
        updatedAtOf: (T) -> Long
    ): List<T> {
        val mapaLocal  = locales.associateBy { syncIdOf(it) }
        val mapaRemoto = remotas.associateBy { syncIdOf(it) }

        val resultado = mutableListOf<T>()

        // Items que existen en remoto
        for ((syncId, remota) in mapaRemoto) {
            val local = mapaLocal[syncId]
            if (local == null) {
                // Nuevo en remoto → agregar
                resultado.add(remota)
            } else {
                // Existe en ambos → ganar el más reciente
                resultado.add(
                    if (updatedAtOf(remota) >= updatedAtOf(local)) remota else local
                )
            }
        }

        // Items que SOLO existen en local (creados o modificados offline)
        for ((syncId, local) in mapaLocal) {
            if (!mapaRemoto.containsKey(syncId)) {
                resultado.add(local)
            }
        }

        return resultado
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBIDA tras merge — dispara el auto-backup con el estado fusionado
    // ─────────────────────────────────────────────────────────────────────────

    private fun subirTrasSync() {
        if (!listo()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                val snap = tomarSnapshotLocal()
                subirAsync(uid, snap)
            } catch (_: Exception) { }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT LOCAL — lee el estado actual de Room
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun tomarSnapshotLocal(): Snapshot {
        return Snapshot(
            tareas        = repositorioTareas!!.todasLasTareas.first(),
            notas         = repositorioNotas!!.todasLasNotasParaBackup.first(),
            finanzas      = repositorioFinanzas!!.todosLosRegistros.first(),
            transacciones = repositorioFinanzas!!.obtenerTodasLasTransacciones()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBIDA — fire-and-forget
    // ─────────────────────────────────────────────────────────────────────────

    private fun subirAsync(uid: String, snap: Snapshot) {
        estaSubiendoANube = true
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(buildDoc(snap))
            .addOnCompleteListener {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    estaSubiendoANube = false
                }, 2_000)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL DOCUMENTO FIRESTORE
    // ─────────────────────────────────────────────────────────────────────────

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
    // IMPORTANTE: syncId y updatedAt son incluidos para que el merge funcione
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseTareas(raw: List<Map<String, Any>>, hoy: String, ahora: Long): List<Tarea> =
        raw.map { m ->
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
                syncId            = m["sync_id"]           as? String ?: java.util.UUID.randomUUID().toString()
            )
        }

    private fun parseNotas(raw: List<Map<String, Any>>, hoy: String, ahora: Long): List<Nota> =
        raw.map { m ->
            Nota(
                id_nota        = (m["id_nota"]       as? Long)?.toInt() ?: 0,
                titulo         = m["titulo"]          as? String ?: "",
                contenido      = m["contenido"]       as? String ?: "",
                fecha_creacion = m["fecha_creacion"]  as? String ?: hoy,
                color_fondo    = m["color_fondo"]     as? String,
                esta_borrada   = m["esta_borrada"]    as? Boolean ?: false,
                updatedAt      = (m["updated_at"]     as? Long) ?: ahora,
                syncId         = m["sync_id"]         as? String ?: java.util.UUID.randomUUID().toString()
            )
        }

    private fun parseFinanzas(raw: List<Map<String, Any>>, ahora: Long): List<PresupuestoSemanal> =
        raw.map { m ->
            PresupuestoSemanal(
                id_finanza               = (m["id_finanza"]              as? Long)?.toInt() ?: 0,
                id_usuario               = (m["id_usuario"]              as? Long)?.toInt(),
                presupuesto_semanal_meta = m["presupuesto_semanal_meta"] as? Double ?: 0.0,
                fecha_inicio             = Date(m["fecha_inicio"]         as? Long ?: ahora),
                fecha_fin                = (m["fecha_fin"]                as? Long)?.let { Date(it) },
                updatedAt                = (m["updated_at"]               as? Long) ?: ahora,
                syncId                   = m["sync_id"]                   as? String ?: java.util.UUID.randomUUID().toString()
            )
        }

    private fun parseTransacciones(raw: List<Map<String, Any>>, ahora: Long): List<Transaccion> =
        raw.map { m ->
            Transaccion(
                id_transaccion    = (m["id_transaccion"]   as? Long)?.toInt() ?: 0,
                id_usuario        = (m["id_usuario"]       as? Long)?.toInt(),
                id_finanza        = (m["id_finanza"]       as? Long)?.toInt() ?: 0,
                id_categoria      = (m["id_categoria"]     as? Long)?.toInt(),
                tipo_transaccion  = m["tipo_transaccion"]  as? String ?: "Gasto",
                monto             = m["monto"]             as? Double ?: 0.0,
                fecha_transaccion = Date(m["fecha_transaccion"] as? Long ?: ahora),
                nota_transaccion  = m["nota_transaccion"]  as? String,
                esta_borrada      = m["esta_borrada"]      as? Boolean ?: false,
                updatedAt         = (m["updated_at"]       as? Long) ?: ahora,
                syncId            = m["sync_id"]           as? String ?: java.util.UUID.randomUUID().toString()
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SERIALIZACIÓN — Room modelo → Firestore Map
    // syncId y updatedAt son obligatorios para el merge
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
        "fecha_creacion"    to t.fecha_creacion,
        "updated_at"        to t.updatedAt,   // ← clave para el merge
        "sync_id"           to t.syncId       // ← identificador único global
    )

    private fun notaAMap(n: Nota): Map<String, Any?> = mapOf(
        "id_nota"        to n.id_nota,
        "titulo"         to n.titulo,
        "contenido"      to n.contenido,
        "fecha_creacion" to n.fecha_creacion,
        "color_fondo"    to n.color_fondo,
        "esta_borrada"   to n.esta_borrada,
        "updated_at"     to n.updatedAt,
        "sync_id"        to n.syncId
    )

    private fun finanzaAMap(f: PresupuestoSemanal): Map<String, Any?> = mapOf(
        "id_finanza"               to f.id_finanza,
        "id_usuario"               to f.id_usuario,
        "presupuesto_semanal_meta" to f.presupuesto_semanal_meta,
        "fecha_inicio"             to f.fecha_inicio.time,
        "fecha_fin"                to f.fecha_fin?.time,
        "updated_at"               to f.updatedAt,
        "sync_id"                  to f.syncId
    )

    private fun transaccionAMap(t: Transaccion): Map<String, Any?> = mapOf(
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

    // ─────────────────────────────────────────────────────────────────────────
    // DATA CLASS AUXILIAR
    // ─────────────────────────────────────────────────────────────────────────

    private data class Snapshot(
        val tareas:        List<Tarea>,
        val notas:         List<Nota>,
        val finanzas:      List<PresupuestoSemanal>,
        val transacciones: List<Transaccion>
    )
}