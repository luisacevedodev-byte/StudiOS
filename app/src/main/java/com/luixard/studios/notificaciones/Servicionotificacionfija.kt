package com.luixard.studios.notificaciones

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.luixard.studios.AplicacionStudiOS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground Service que mantiene la notificación fija de seguimiento diario.
 */
class ServicioNotificacionFija : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        fun iniciar(context: Context) {
            context.startForegroundService(
                Intent(context, ServicioNotificacionFija::class.java)
            )
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, ServicioNotificacionFija::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Android exige startForeground() en los primeros 5 s — placeholder inmediato
        HelperNotificaciones.crearCanales(this)
        startForeground(
            HelperNotificaciones.ID_NOTIF_FIJA,
            HelperNotificaciones.construirNotifFija(this, 1).build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            val app = applicationContext as? AplicacionStudiOS ?: run { stopSelf(); return@launch }
            val totalPendientes = app.repositorioTareas.tareasPendientes.first().size

            if (totalPendientes > 0) {
                startForeground(
                    HelperNotificaciones.ID_NOTIF_FIJA,
                    HelperNotificaciones.construirNotifFija(applicationContext, totalPendientes).build()
                )
            } else {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        startForegroundService(Intent(applicationContext, ServicioNotificacionFija::class.java))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}