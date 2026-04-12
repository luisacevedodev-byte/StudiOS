package com.luixard.studios.notificaciones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReceptorAccionNotif : BroadcastReceiver() {

    companion object {
        const val ACCION_SIN_AVANCE = "com.luixard.studios.ACCION_SIN_AVANCE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCION_SIN_AVANCE -> {
                ServicioNotificacionFija.detener(context)
            }
        }
    }
}