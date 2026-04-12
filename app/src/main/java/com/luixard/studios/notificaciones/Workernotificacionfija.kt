package com.luixard.studios.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luixard.studios.AplicacionStudiOS
import kotlinx.coroutines.flow.first

/**
 * Worker diario que verifica si hay tareas pendientes y, si las hay,
 * INICIA el ServicioNotificacionFija.
 */
class WorkerNotificacionFija(
    private val context: Context,
    params:              WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? AplicacionStudiOS
            ?: return Result.failure()

        return try {
            val totalPendientes = app.repositorioTareas.tareasPendientes.first().size

            if (totalPendientes > 0) {
                ServicioNotificacionFija.iniciar(context)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}