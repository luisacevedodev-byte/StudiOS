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
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.luixard.studios.R
import com.luixard.studios.notificaciones.ProgramadorNotificaciones
import com.luixard.studios.notificaciones.ServicioNotificacionFija

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

        // ── Tema ──────────────────────────────────────────────────────────────
        when (p.getString(KEY_TEMA, TEMA_SISTEMA)) {
            TEMA_CLARO  -> marcarTema(radioTemaClaro)
            TEMA_OSCURO -> marcarTema(radioTemaOscuro)
            else        -> marcarTema(radioTemaSistema)
        }

        // ── Notificación fija ─────────────────────────────────────────────────
        val notifFijaActiva = p.getBoolean(KEY_NOTIF_FIJA, false)
        switchNotifFija.isChecked      = notifFijaActiva
        layoutHoraNotifFija.visibility = if (notifFijaActiva) View.VISIBLE else View.GONE

        val horaGuardada = p.getString(KEY_HORA_FIJA, "12:00") ?: "12:00"
        btnSeleccionarHoraFija.text = formatearHora12(horaGuardada)

        // ── Recordatorios ─────────────────────────────────────────────────────
        val recActivo = p.getBoolean(KEY_REC_ACTIVO, false)
        switchRecordatorios.isChecked  = recActivo
        layoutFrecuenciaRec.visibility = if (recActivo) View.VISIBLE else View.GONE

        when (p.getInt(KEY_REC_HORAS, 8)) {
            1    -> chipGroupFrecuencia.check(R.id.chip1h)
            2    -> chipGroupFrecuencia.check(R.id.chip2h)
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

            if (isChecked) {
                // Leer hora actual guardada para la programación inicial
                val horaStr = prefs().getString(KEY_HORA_FIJA, "12:00") ?: "12:00"
                val partes  = horaStr.split(":")
                val h = partes.getOrNull(0)?.toIntOrNull() ?: 12
                val m = partes.getOrNull(1)?.toIntOrNull() ?: 0

                ProgramadorNotificaciones.programarNotifFija(requireContext(), h, m)
            } else {
                ProgramadorNotificaciones.cancelarNotifFija(requireContext())
            }
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

                if (switchNotifFija.isChecked) {
                    ProgramadorNotificaciones.confirmarYprogramarNotifFija(
                        requireContext(), hora, minuto
                    )
                }
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

            if (isChecked) {
                val horas = prefs().getInt(KEY_REC_HORAS, 8).toLong()
                ProgramadorNotificaciones.programarRecordatorios(requireContext(), horas)
            } else {
                ProgramadorNotificaciones.cancelarRecordatorios(requireContext())
            }
        }

        // ── Chips de frecuencia de recordatorios ──────────────────────────────
        chipGroupFrecuencia.setOnCheckedStateChangeListener { _, checkedIds ->
            val horas = when (checkedIds.firstOrNull()) {
                R.id.chip1h  -> 1L
                R.id.chip2h  -> 2L
                R.id.chip4h  -> 4L
                R.id.chip12h -> 12L
                R.id.chip24h -> 24L
                else         -> 8L
            }
            prefs().edit().putInt(KEY_REC_HORAS, horas.toInt()).apply()

            if (switchRecordatorios.isChecked) {
                ProgramadorNotificaciones.programarRecordatorios(requireContext(), horas)
            }
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