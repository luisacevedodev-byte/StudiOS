package com.luixard.studios.notificaciones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luixard.studios.AplicacionStudiOS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceptorAccionNotif : BroadcastReceiver() {

    companion object {
        const val ACCION_SIN_AVANCE          = "com.luixard.studios.ACCION_SIN_AVANCE"
        const val ACCION_REINICIAR_SERVICIO  = "com.luixard.studios.ACCION_REINICIAR_SERVICIO"
        const val ACCION_DISPARAR_NOTIF_FIJA = "com.luixard.studios.ACCION_DISPARAR_NOTIF_FIJA"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            ACCION_SIN_AVANCE -> {
                ServicioNotificacionFija.detener(context)
            }

            ACCION_REINICIAR_SERVICIO -> {
                ServicioNotificacionFija.iniciar(context)
            }

            // ── Alarma exacta diaria ──────────────────────────────────────────
            ACCION_DISPARAR_NOTIF_FIJA -> {
                // Reprogramar primero (síncrono, no falla)
                ProgramadorNotificaciones.reprogramarNotifFijaAlDiaSiguiente(context)

                val resultado = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        HelperNotificaciones.crearCanales(context)

                        val app   = context.applicationContext as? AplicacionStudiOS
                        val total = try {
                            app?.repositorioTareas?.tareasPendientes?.first()?.size ?: 1
                        } catch (_: Exception) { 1 }

                        if (total <= 0) return@launch

                        val notifDirecta = HelperNotificaciones
                            .construirNotifFija(context, total)
                            .setOngoing(false)
                        HelperNotificaciones.mostrar(
                            context,
                            HelperNotificaciones.ID_NOTIF_FIJA,
                            notifDirecta
                        )

                        withContext(Dispatchers.Main) {
                            try {
                                ServicioNotificacionFija.iniciar(context)
                            } catch (_: Exception) {
                            }
                        }

                    } catch (_: Exception) {
                        try {
                            HelperNotificaciones.crearCanales(context)
                            val notifEmergencia = HelperNotificaciones
                                .construirNotifFija(context, 1)
                                .setOngoing(false)
                            HelperNotificaciones.mostrar(
                                context,
                                HelperNotificaciones.ID_NOTIF_FIJA,
                                notifEmergencia
                            )
                        } catch (_: Exception) {}
                    } finally {
                        resultado.finish()
                    }
                }
            }
        }
    }
}