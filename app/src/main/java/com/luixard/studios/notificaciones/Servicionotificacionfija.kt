package com.luixard.studios.notificaciones

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.luixard.studios.AplicacionStudiOS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground Service que mantiene la notificación fija de seguimiento diario.
 */
class ServicioNotificacionFija : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─────────────────────────────────────────────────────────────────────────
    // COMPANION
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS          = "studios_config"
        private const val KEY_DESCARTADO = "notif_fija_descartada_fecha"
        private val FMT_FECHA            = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        fun iniciar(context: Context) {
            if (fueDescartadoHoy(context)) return
            context.startForegroundService(Intent(context, ServicioNotificacionFija::class.java))
        }

        fun detener(context: Context) {
            val hoy = FMT_FECHA.format(Date())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_DESCARTADO, hoy).apply()
            context.stopService(Intent(context, ServicioNotificacionFija::class.java))
        }

        private fun fueDescartadoHoy(context: Context): Boolean {
            val hoy      = FMT_FECHA.format(Date())
            val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DESCARTADO, "")
            return guardado == hoy
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CICLO DE VIDA
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        HelperNotificaciones.crearCanales(this)
        startForeground(
            HelperNotificaciones.ID_NOTIF_FIJA,
            HelperNotificaciones.construirNotifFija(this, 1).build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            val app = applicationContext as? AplicacionStudiOS ?: run { stopSelf(); return@launch }
            val total = app.repositorioTareas.tareasPendientes.first().size

            if (total > 0) {
                startForeground(
                    HelperNotificaciones.ID_NOTIF_FIJA,
                    HelperNotificaciones.construirNotifFija(applicationContext, total).build()
                )
            } else {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        programarAlarmaReinicio()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────
    // ALARMA DE REINICIO (defensa contra Android 16)
    // ─────────────────────────────────────────────────────────────────────────

    private fun programarAlarmaReinicio() {
        val alarma = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(applicationContext, ReceptorAccionNotif::class.java).apply {
            action = ReceptorAccionNotif.ACCION_REINICIAR_SERVICIO
        }
        val pending = PendingIntent.getBroadcast(
            applicationContext, 99, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disparo = System.currentTimeMillis() + 30_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarma.canScheduleExactAlarms()) {
            alarma.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pending)
        } else {
            @Suppress("DEPRECATION")
            alarma.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pending)
        }
    }
}