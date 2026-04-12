package com.luixard.studios.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luixard.studios.AplicacionStudiOS
import kotlinx.coroutines.flow.first

class WorkerRecordatorio(
    private val context: Context,
    params:              WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? AplicacionStudiOS
            ?: return Result.failure()

        return try {
            val pendientes = app.repositorioTareas.tareasPendientes.first()

            if (pendientes.isNotEmpty()) {
                // Elegir una tarea al azar
                val tareaAleatoria = pendientes.random()

                HelperNotificaciones.crearCanales(context)

                val notif = HelperNotificaciones.construirNotifRecordatorio(
                    context,
                    tareaAleatoria.titulo_tarea
                )
                HelperNotificaciones.mostrar(
                    context,
                    HelperNotificaciones.ID_NOTIF_RECORDATORIO,
                    notif
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}