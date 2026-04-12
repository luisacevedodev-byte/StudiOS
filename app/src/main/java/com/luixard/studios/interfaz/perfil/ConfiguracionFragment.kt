package com.luixard.studios.interfaz.perfil

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.luixard.studios.R
import com.luixard.studios.notificaciones.WorkerNotificacionFija
import com.luixard.studios.notificaciones.WorkerRecordatorio
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ConfiguracionFragment : Fragment() {

    private lateinit var opcionTemaPorDefecto:   LinearLayout
    private lateinit var opcionTemaClaro:        LinearLayout
    private lateinit var opcionTemaOscuro:       LinearLayout
    private lateinit var radioTemaSistema:       RadioButton
    private lateinit var radioTemaClaro:         RadioButton
    private lateinit var radioTemaOscuro:        RadioButton
    private lateinit var tvEstadoPermiso:        TextView
    private lateinit var btnIrAjustesPermiso:    MaterialButton
    private lateinit var switchNotifFija:        SwitchMaterial
    private lateinit var layoutHoraNotifFija:    LinearLayout
    private lateinit var btnSeleccionarHoraFija: MaterialButton
    private lateinit var switchRecordatorios:    SwitchMaterial
    private lateinit var layoutFrecuenciaRec:    LinearLayout
    private lateinit var chipGroupFrecuencia:    ChipGroup

    private val PREFS          = "studios_config"
    private val KEY_TEMA       = "tema"
    private val KEY_NOTIF_FIJA = "notif_fija_activa"
    private val KEY_HORA_FIJA  = "notif_fija_hora"
    private val KEY_REC_ACTIVO = "recordatorio_activo"
    private val KEY_REC_HORAS  = "recordatorio_horas"
    private val TEMA_SISTEMA   = "sistema"
    private val TEMA_CLARO     = "claro"
    private val TEMA_OSCURO    = "oscuro"
    private val WORKER_FIJA    = "NotFijaWorker"
    private val WORKER_REC     = "RecordatorioWorker"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_configuracion, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        cargarPreferencias()
        setupListeners()
        actualizarEstadoPermiso()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoPermiso()
    }

    private fun bindViews(v: View) {
        opcionTemaPorDefecto   = v.findViewById(R.id.opcionTemaPorDefecto)
        opcionTemaClaro        = v.findViewById(R.id.opcionTemaClaro)
        opcionTemaOscuro       = v.findViewById(R.id.opcionTemaOscuro)
        radioTemaSistema       = v.findViewById(R.id.radioTemaSistema)
        radioTemaClaro         = v.findViewById(R.id.radioTemaClaro)
        radioTemaOscuro        = v.findViewById(R.id.radioTemaOscuro)
        tvEstadoPermiso        = v.findViewById(R.id.tvEstadoPermiso)
        btnIrAjustesPermiso    = v.findViewById(R.id.btnIrAjustesPermiso)
        switchNotifFija        = v.findViewById(R.id.switchNotifFija)
        layoutHoraNotifFija    = v.findViewById(R.id.layoutHoraNotifFija)
        btnSeleccionarHoraFija = v.findViewById(R.id.btnSeleccionarHoraFija)
        switchRecordatorios    = v.findViewById(R.id.switchRecordatorios)
        layoutFrecuenciaRec    = v.findViewById(R.id.layoutFrecuenciaRecordatorio)
        chipGroupFrecuencia    = v.findViewById(R.id.chipGroupFrecuencia)
    }

    private fun cargarPreferencias() {
        val p = prefs()

        when (p.getString(KEY_TEMA, TEMA_SISTEMA)) {
            TEMA_CLARO  -> marcarTema(radioTemaClaro)
            TEMA_OSCURO -> marcarTema(radioTemaOscuro)
            else        -> marcarTema(radioTemaSistema)
        }

        val notifFijaActiva = p.getBoolean(KEY_NOTIF_FIJA, false)
        switchNotifFija.isChecked      = notifFijaActiva
        layoutHoraNotifFija.visibility = if (notifFijaActiva) View.VISIBLE else View.GONE

        val horaGuardada = p.getString(KEY_HORA_FIJA, "12:00") ?: "12:00"
        btnSeleccionarHoraFija.text = formatearHora12(horaGuardada)

        val recActivo = p.getBoolean(KEY_REC_ACTIVO, false)
        switchRecordatorios.isChecked  = recActivo
        layoutFrecuenciaRec.visibility = if (recActivo) View.VISIBLE else View.GONE

        when (p.getInt(KEY_REC_HORAS, 8)) {
            4    -> chipGroupFrecuencia.check(R.id.chip4h)
            12   -> chipGroupFrecuencia.check(R.id.chip12h)
            24   -> chipGroupFrecuencia.check(R.id.chip24h)
            else -> chipGroupFrecuencia.check(R.id.chip8h)
        }
    }

    private fun setupListeners() {

        // ── Tema ──────────────────────────────────────────────────────────────
        opcionTemaPorDefecto.setOnClickListener { aplicarTema(TEMA_SISTEMA); marcarTema(radioTemaSistema) }
        opcionTemaClaro.setOnClickListener      { aplicarTema(TEMA_CLARO);   marcarTema(radioTemaClaro)  }
        opcionTemaOscuro.setOnClickListener     { aplicarTema(TEMA_OSCURO);  marcarTema(radioTemaOscuro) }

        // ── Permiso ───────────────────────────────────────────────────────────
        btnIrAjustesPermiso.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }

        // ── Switch: Notificación Fija ─────────────────────────────────────────
        switchNotifFija.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !tienePermisoNotificaciones()) {
                switchNotifFija.isChecked = false
                mostrarDialogoSinPermiso()
                return@setOnCheckedChangeListener
            }
            prefs().edit().putBoolean(KEY_NOTIF_FIJA, isChecked).apply()
            layoutHoraNotifFija.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) programarWorkerFijo() else cancelarWorker(WORKER_FIJA)
        }

        // ── Botón hora ────────────────────────────────────────────────────────
        btnSeleccionarHoraFija.setOnClickListener {
            val horaActual = prefs().getString(KEY_HORA_FIJA, "12:00") ?: "12:00"
            val partes = horaActual.split(":")
            val h = partes.getOrNull(0)?.toIntOrNull() ?: 12
            val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(requireContext(), { _, hora, minuto ->
                val horaStr = "%02d:%02d".format(hora, minuto)
                prefs().edit().putString(KEY_HORA_FIJA, horaStr).apply()
                btnSeleccionarHoraFija.text = formatearHora12(horaStr)
                if (switchNotifFija.isChecked) programarWorkerFijo()
            }, h, m, false).show()
        }

        // ── Switch: Recordatorios ─────────────────────────────────────────────
        switchRecordatorios.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !tienePermisoNotificaciones()) {
                switchRecordatorios.isChecked = false
                mostrarDialogoSinPermiso()
                return@setOnCheckedChangeListener
            }
            prefs().edit().putBoolean(KEY_REC_ACTIVO, isChecked).apply()
            layoutFrecuenciaRec.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) programarWorkerRecordatorio() else cancelarWorker(WORKER_REC)
        }

        // ── Chips frecuencia ──────────────────────────────────────────────────
        chipGroupFrecuencia.setOnCheckedStateChangeListener { _, checkedIds ->
            val horas = when (checkedIds.firstOrNull()) {
                R.id.chip4h  -> 4
                R.id.chip12h -> 12
                R.id.chip24h -> 24
                else         -> 8
            }
            prefs().edit().putInt(KEY_REC_HORAS, horas).apply()
            if (switchRecordatorios.isChecked) programarWorkerRecordatorio()
        }
    }

    // ── Tema ──────────────────────────────────────────────────────────────────

    private fun aplicarTema(tema: String) {
        prefs().edit().putString(KEY_TEMA, tema).apply()
        val modo = when (tema) {
            TEMA_CLARO  -> AppCompatDelegate.MODE_NIGHT_NO
            TEMA_OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
            else        -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(modo)
    }

    private fun marcarTema(seleccionado: RadioButton) {
        radioTemaSistema.isChecked = (seleccionado == radioTemaSistema)
        radioTemaClaro.isChecked   = (seleccionado == radioTemaClaro)
        radioTemaOscuro.isChecked  = (seleccionado == radioTemaOscuro)
    }

    // ── Permisos ──────────────────────────────────────────────────────────────

    private fun tienePermisoNotificaciones(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun actualizarEstadoPermiso() {
        if (tienePermisoNotificaciones()) {
            tvEstadoPermiso.text = "✓ Permiso concedido"
            tvEstadoPermiso.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.studios_cyan_titulo)
            )
            btnIrAjustesPermiso.visibility = View.GONE
        } else {
            tvEstadoPermiso.text = "✗ Permiso denegado"
            tvEstadoPermiso.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            )
            btnIrAjustesPermiso.visibility = View.VISIBLE
        }
    }

    private fun mostrarDialogoSinPermiso() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permiso requerido")
            .setMessage("Para activar las notificaciones debes permitirlas en los ajustes de tu celular.")
            .setPositiveButton("Ir a ajustes") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Workers ───────────────────────────────────────────────────────────────

    private fun programarWorkerFijo() {
        val horaStr = prefs().getString(KEY_HORA_FIJA, "12:00") ?: "12:00"
        val partes  = horaStr.split(":")
        val horaObj = partes.getOrNull(0)?.toIntOrNull() ?: 12
        val minObj  = partes.getOrNull(1)?.toIntOrNull() ?: 0

        val ahora   = Calendar.getInstance()
        val destino = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, horaObj)
            set(Calendar.MINUTE, minObj)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!destino.after(ahora)) destino.add(Calendar.DAY_OF_YEAR, 1)

        val delayMins = TimeUnit.MILLISECONDS.toMinutes(destino.timeInMillis - ahora.timeInMillis)

        val request = OneTimeWorkRequestBuilder<WorkerNotificacionFija>()
            .setInitialDelay(delayMins, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork(WORKER_FIJA, ExistingWorkPolicy.REPLACE, request)
    }

    private fun programarWorkerRecordatorio() {
        val horas   = prefs().getInt(KEY_REC_HORAS, 8).toLong()
        val request = PeriodicWorkRequestBuilder<WorkerRecordatorio>(horas, TimeUnit.HOURS).build()
        WorkManager.getInstance(requireContext())
            .enqueueUniquePeriodicWork(WORKER_REC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancelarWorker(nombre: String) {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(nombre)
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun formatearHora12(hora24: String): String {
        val partes = hora24.split(":")
        val h      = partes.getOrNull(0)?.toIntOrNull() ?: 12
        val m      = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val ampm   = if (h < 12) "AM" else "PM"
        val h12    = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "%d:%02d %s".format(h12, m, ampm)
    }
}