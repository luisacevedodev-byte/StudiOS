package com.luixard.studios.datos.sync

import android.content.Context
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    private var deviceId = ""

    var alCerrarSesionPorOtroDispositivo: (() -> Unit)? = null

    @Volatile private var estaHaciendoMerge  = false

    @Volatile private var snapshotsAIgnorar  = 0

    @Volatile private var loginEnProgreso    = false

    private var snapshotListener: ListenerRegistration? = null
    private var autoBackupJob:    Job? = null

    val estaCargando  = MutableStateFlow(false)
    val nombreDisplay = MutableStateFlow("")

    val loginCompletado = MutableStateFlow(false)

    private var nombreCached   = ""
    private var apellidoCached = ""

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    fun init(
        context:      Context,
        repoTareas:   TareaRepositorio,
        repoNotas:    NotaRepositorio,
        repoFinanzas: FinanzasRepositorio
    ) {
        if (inicializado) return

        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }

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
        nombreCached        = nombre
        apellidoCached      = apellido
        nombreDisplay.value = "$nombre $apellido".trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENTOS DE AUTENTICACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    fun onNuevaCuentaVinculada(nombre: String, apellido: String) {
        if (!listo()) return
        actualizarNombre(nombre, apellido)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        loginEnProgreso    = true
        estaCargando.value = true
        snapshotsAIgnorar  = 1

        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("usuarios").document(uid)
                    .set(buildDoc(tomarSnapshotLocal()))
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { estaCargando.value = false }
            }

            loginEnProgreso = false
            activarEscuchaEnTiempoReal(uid)
            activarAutoBackup()
            withContext(Dispatchers.Main) { loginCompletado.value = true }
        }
    }

    fun onInicioSesion(uid: String, nombre: String = "", apellido: String = "") {
        if (!listo()) return
        if (nombre.isNotEmpty()) actualizarNombre(nombre, apellido)

        loginEnProgreso    = true
        estaCargando.value = true

        scope.launch {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("usuarios").document(uid)
                    .get().await()

                val data   = doc.data
                val backup = (data?.get("datos") as? Map<*, *>)

                val nom = data?.let { it["perfil"] as? Map<*, *> }?.get("nombre") as? String ?: ""
                val ape = data?.let { it["perfil"] as? Map<*, *> }?.get("apellido") as? String ?: ""
                if (nom.isNotEmpty()) withContext(Dispatchers.Main) { actualizarNombre(nom, ape) }

                if (backup != null) {
                    val hoy   = fmt.format(Date())
                    val ahora = System.currentTimeMillis()

                    val remotasTareas = parseTareas(
                        backup["tareas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
                    aplicarMergeTareas(repositorioTareas!!.obtenerTodas(), remotasTareas)

                    val remotasNotas = parseNotas(
                        backup["notas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
                    aplicarMergeNotas(repositorioNotas!!.obtenerTodas(), remotasNotas)

                    val remotasFinanzas = parseFinanzas(
                        backup["finanzas"] as? List<Map<String, Any>> ?: emptyList(), ahora)
                    aplicarMergeFinanzas(repositorioFinanzas!!.obtenerTodasLasFinanzasSuspend(), remotasFinanzas)

                    val remotasTx = parseTransacciones(
                        backup["transacciones"] as? List<Map<String, Any>> ?: emptyList(), ahora)
                    aplicarMergeTransacciones(repositorioFinanzas!!.obtenerTodasLasTransacciones(), remotasTx)
                }

                snapshotsAIgnorar = 2

                FirebaseFirestore.getInstance()
                    .collection("usuarios").document(uid)
                    .set(buildDoc(tomarSnapshotLocal()))
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { estaCargando.value = false }
            }

            loginEnProgreso = false
            activarEscuchaEnTiempoReal(uid)
            activarAutoBackup()

            withContext(Dispatchers.Main) { loginCompletado.value = true }
        }
    }

    fun onSesionActiva(uid: String) {
        if (!listo()) return
        // Si onInicioSesion está corriendo, NO activar el listener aquí.
        // Lo activará onInicioSesion al final, con el deviceId correcto ya subido.
        if (loginEnProgreso) return
        activarEscuchaEnTiempoReal(uid)
        activarAutoBackup()
    }

    fun onCerrarSesion() {
        snapshotListener?.remove()
        snapshotListener      = null
        autoBackupJob?.cancel()
        autoBackupJob         = null
        estaHaciendoMerge     = false
        snapshotsAIgnorar     = 0
        loginEnProgreso       = false
        loginCompletado.value = false
        nombreDisplay.value   = ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LISTENER EN TIEMPO REAL — cambios desde otro dispositivo
    // ─────────────────────────────────────────────────────────────────────────

    private fun activarEscuchaEnTiempoReal(uid: String) {
        if (snapshotListener != null) return

        snapshotListener = FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Ignorar ecos de nuestros propios writes en curso (caché local Firestore)
                if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                // Consumir el contador de snapshots a ignorar
                if (snapshotsAIgnorar > 0) {
                    snapshotsAIgnorar--
                    return@addSnapshotListener
                }

                // ── SESIÓN ÚNICA ──────────────────────────────────────────────
                val activeDeviceId = snapshot.getString("perfil.active_device_id")
                if (activeDeviceId != null
                    && activeDeviceId.isNotEmpty()
                    && activeDeviceId != deviceId
                ) {
                    FirebaseAuth.getInstance().signOut()
                    onCerrarSesion()
                    alCerrarSesionPorOtroDispositivo?.invoke()
                    return@addSnapshotListener
                }

                if (estaHaciendoMerge) return@addSnapshotListener

                val nom = snapshot.getString("perfil.nombre") ?: ""
                val ape = snapshot.getString("perfil.apellido") ?: ""
                if (nom.isNotEmpty()) actualizarNombre(nom, ape)

                val data = snapshot.data ?: return@addSnapshotListener
                scope.launch { hacerMerge(data) }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-BACKUP continuo — dispara cuando Room cambia
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
    // MERGE CONTINUO — llamado por el snapshot listener
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun hacerMerge(data: Map<String, Any>) {
        if (!listo()) return
        val backup = data["datos"] as? Map<*, *> ?: return
        val hoy    = fmt.format(Date())
        val ahora  = System.currentTimeMillis()

        estaHaciendoMerge = true
        withContext(Dispatchers.Main) { estaCargando.value = true }

        try {
            val remotasTareas = parseTareas(
                backup["tareas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
            aplicarMergeTareas(repositorioTareas!!.obtenerTodas(), remotasTareas)

            val remotasNotas = parseNotas(
                backup["notas"] as? List<Map<String, Any>> ?: emptyList(), hoy, ahora)
            aplicarMergeNotas(repositorioNotas!!.obtenerTodas(), remotasNotas)

            val remotasFinanzas = parseFinanzas(
                backup["finanzas"] as? List<Map<String, Any>> ?: emptyList(), ahora)
            aplicarMergeFinanzas(repositorioFinanzas!!.obtenerTodasLasFinanzasSuspend(), remotasFinanzas)

            val remotasTx = parseTransacciones(
                backup["transacciones"] as? List<Map<String, Any>> ?: emptyList(), ahora)
            aplicarMergeTransacciones(repositorioFinanzas!!.obtenerTodasLasTransacciones(), remotasTx)

            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                try { subirAsync(uid, tomarSnapshotLocal()) } catch (_: Exception) { }
            }

        } catch (_: Exception) {
        } finally {
            estaHaciendoMerge = false
            withContext(Dispatchers.Main) { estaCargando.value = false }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APLICAR MERGE
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun aplicarMergeTareas(locales: List<Tarea>, remotas: List<Tarea>) {
        val mapaLocal = locales.filter { it.syncId.isNotEmpty() }.associateBy { it.syncId }

        for (remota in remotas) {
            if (remota.syncId.isEmpty()) continue
            val local = mapaLocal[remota.syncId]
            when {
                local == null && !remota.esta_borrada ->
                    repositorioTareas!!.agregarTarea(remota.copy(id_tarea = 0))

                local != null && remota.updatedAt > local.updatedAt ->
                    repositorioTareas!!.agregarTarea(remota.copy(id_tarea = local.id_tarea))
            }
        }
    }

    private suspend fun aplicarMergeNotas(locales: List<Nota>, remotas: List<Nota>) {
        val mapaLocal = locales.filter { it.syncId.isNotEmpty() }.associateBy { it.syncId }

        for (remota in remotas) {
            if (remota.syncId.isEmpty()) continue
            val local = mapaLocal[remota.syncId]
            when {
                local == null && !remota.esta_borrada ->
                    repositorioNotas!!.insertarNota(remota.copy(id_nota = 0))

                local != null && remota.updatedAt > local.updatedAt ->
                    repositorioNotas!!.insertarNota(remota.copy(id_nota = local.id_nota))
            }
        }
    }

    private suspend fun aplicarMergeFinanzas(
        locales: List<PresupuestoSemanal>,
        remotas: List<PresupuestoSemanal>
    ) {
        val mapaLocal = locales.filter { it.syncId.isNotEmpty() }.associateBy { it.syncId }

        for (remota in remotas) {
            if (remota.syncId.isEmpty()) continue
            val local = mapaLocal[remota.syncId]
            when {
                local == null ->
                    repositorioFinanzas!!.insertarPresupuesto(remota.copy(id_finanza = 0))

                local != null && remota.updatedAt > local.updatedAt ->
                    repositorioFinanzas!!.insertarPresupuesto(remota.copy(id_finanza = local.id_finanza))
            }
        }
    }

    private suspend fun aplicarMergeTransacciones(
        locales: List<Transaccion>,
        remotas: List<Transaccion>
    ) {
        val mapaLocal = locales.filter { it.syncId.isNotEmpty() }.associateBy { it.syncId }

        val finanzasActualizadas = repositorioFinanzas!!
            .obtenerTodasLasFinanzasSuspend()
            .associateBy { it.id_finanza }

        for (remota in remotas) {
            if (remota.syncId.isEmpty()) continue
            val local = mapaLocal[remota.syncId]

            when {
                local == null && !remota.esta_borrada -> {
                    val idFinanzaLocal = if (finanzasActualizadas.containsKey(remota.id_finanza)) {
                        remota.id_finanza
                    } else {
                        finanzasActualizadas.values
                            .maxByOrNull { it.updatedAt }
                            ?.id_finanza ?: remota.id_finanza
                    }
                    repositorioFinanzas!!.restaurarTransacciones(
                        listOf(remota.copy(id_transaccion = 0, id_finanza = idFinanzaLocal))
                    )
                }

                local != null && remota.updatedAt > local.updatedAt ->
                    repositorioFinanzas!!.restaurarTransacciones(
                        listOf(remota.copy(id_transaccion = local.id_transaccion))
                    )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT LOCAL y SUBIDA
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun tomarSnapshotLocal() = Snapshot(
        tareas        = repositorioTareas!!.todasLasTareas.first(),
        notas         = repositorioNotas!!.todasLasNotasParaBackup.first(),
        finanzas      = repositorioFinanzas!!.todosLosRegistros.first(),
        transacciones = repositorioFinanzas!!.obtenerTodasLasTransacciones()
    )

    private fun subirAsync(uid: String, snap: Snapshot) {
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .set(buildDoc(snap))
    }

    private fun buildDoc(snap: Snapshot): HashMap<String, Any> = hashMapOf(
        "perfil" to mapOf(
            "nombre"           to nombreCached,
            "apellido"         to apellidoCached,
            "correo"           to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            "ultima_sinc"      to Timestamp.now(),
            "active_device_id" to deviceId
        ),
        "datos" to mapOf(
            "tareas"        to snap.tareas.map        { tareaAMap(it)       },
            "notas"         to snap.notas.map         { notaAMap(it)        },
            "finanzas"      to snap.finanzas.map      { finanzaAMap(it)     },
            "transacciones" to snap.transacciones.map { transaccionAMap(it) }
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PARSERS
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
    // SERIALIZACIÓN
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