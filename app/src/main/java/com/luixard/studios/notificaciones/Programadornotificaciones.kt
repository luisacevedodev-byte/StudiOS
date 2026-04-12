package com.luixard.studios.notificaciones

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ProgramadorNotificaciones {

    private const val TAG_NOTIF_FIJA         = "notif_fija_diaria"
    private const val TAG_RECORDATORIO       = "recordatorio_aleatorio"

    // ─────────────────────────────────────────────────────────────────────
    // NOTIFICACIÓN FIJA DIARIA
    // ─────────────────────────────────────────────────────────────────────

    fun programarNotifFija(context: Context, hora: Int, minuto: Int) {
        val demoraMilis = calcularDemoraHasta(hora, minuto)

        val solicitud = PeriodicWorkRequestBuilder<WorkerNotificacionFija>(1, TimeUnit.DAYS)
            .setInitialDelay(demoraMilis, TimeUnit.MILLISECONDS)
            .addTag(TAG_NOTIF_FIJA)
            .build()

        // REPLACE: reemplaza cualquier work anterior con el mismo nombre,
        // actualizando la hora si el usuario la cambió en Configuración.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_NOTIF_FIJA,
            ExistingPeriodicWorkPolicy.REPLACE,
            solicitud
        )
    }

    fun cancelarNotifFija(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_NOTIF_FIJA)
        ServicioNotificacionFija.detener(context)
    }

    // ─────────────────────────────────────────────────────────────────────
    // RECORDATORIOS ALEATORIOS
    // ─────────────────────────────────────────────────────────────────────

    fun programarRecordatorios(context: Context, intervalHoras: Long) {
        val solicitud = PeriodicWorkRequestBuilder<WorkerRecordatorio>(intervalHoras, TimeUnit.HOURS)
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

    private fun calcularDemoraHasta(hora: Int, minuto: Int): Long {
        val ahora = Calendar.getInstance()

        val objetivo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Si la hora ya pasó (o es exactamente ahora), agendar para el día siguiente
        if (!objetivo.after(ahora)) {
            objetivo.add(Calendar.DAY_OF_YEAR, 1)
        }

        return objetivo.timeInMillis - ahora.timeInMillis
    }
}