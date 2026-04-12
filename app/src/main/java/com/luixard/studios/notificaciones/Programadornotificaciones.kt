package com.luixard.studios.notificaciones

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ProgramadorNotificaciones {

    private const val TAG_RECORDATORIO = "recordatorio_aleatorio"

    private const val PREFS      = "studios_config"
    private const val KEY_HORA   = "notif_fija_hora"
    private const val KEY_MINUTO = "notif_fija_minuto"
    private const val KEY_DESCARTADO = "notif_fija_descartada_hoy"

    // ─────────────────────────────────────────────────────────────────────
    // NOTIFICACIÓN FIJA DIARIA — usa AlarmManager para disparo exacto
    // ─────────────────────────────────────────────────────────────────────

    fun programarNotifFija(context: Context, hora: Int, minuto: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DESCARTADO)
            .putInt(KEY_HORA, hora)
            .putInt(KEY_MINUTO, minuto)
            .apply()

        val alarma  = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = crearPendingIntentFija(context)
        val disparo = calcularMomentoAbsoluto(hora, minuto)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarma.canScheduleExactAlarms()) {
            alarma.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pending)
        } else {
            alarma.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pending)
        }
    }

    fun cancelarNotifFija(context: Context) {
        val alarma = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarma.cancel(crearPendingIntentFija(context))
        ServicioNotificacionFija.detener(context)
    }

    fun reprogramarNotifFijaAlDiaSiguiente(context: Context) {
        val prefs  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hora   = prefs.getInt(KEY_HORA, 8)
        val minuto = prefs.getInt(KEY_MINUTO, 0)
        programarNotifFija(context, hora, minuto)
    }

    private fun crearPendingIntentFija(context: Context): PendingIntent {
        val intent = Intent(context, ReceptorAccionNotif::class.java).apply {
            action = ReceptorAccionNotif.ACCION_DISPARAR_NOTIF_FIJA
        }
        return PendingIntent.getBroadcast(
            context, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // RECORDATORIOS ALEATORIOS
    // ─────────────────────────────────────────────────────────────────────

    fun programarRecordatorios(context: Context, intervalHoras: Long) {
        val solicitud = PeriodicWorkRequestBuilder<WorkerRecordatorio>(intervalHoras, TimeUnit.HOURS)
            .setInitialDelay(intervalHoras, TimeUnit.HOURS)
            .addTag(TAG_RECORDATORIO)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_RECORDATORIO,
            ExistingPeriodicWorkPolicy.REPLACE,
            solicitud
        )
    }

    fun cancelarRecordatorios(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_RECORDATORIO)
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILIDAD INTERNA
    // ─────────────────────────────────────────────────────────────────────

    private fun calcularMomentoAbsoluto(hora: Int, minuto: Int): Long {
        val ahora   = Calendar.getInstance()
        val objetivo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!objetivo.after(ahora)) {
            objetivo.add(Calendar.DAY_OF_YEAR, 1)
        }
        return objetivo.timeInMillis
    }
}