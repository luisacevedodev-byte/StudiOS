package com.luixard.studios.notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.luixard.studios.MainActivity
import com.luixard.studios.R

object HelperNotificaciones {

    // ── IDs de canales ────────────────────────────────────────────────────────
    const val CANAL_FIJA         = "canal_notif_fija"
    const val CANAL_RECORDATORIO = "canal_recordatorio"

    // ── IDs de notificaciones ─────────────────────────────────────────────────
    const val ID_NOTIF_FIJA         = 1001
    const val ID_NOTIF_RECORDATORIO = 1002

    // ─────────────────────────────────────────────────────────────────────────
    // CREAR CANALES — llamar UNA VEZ al iniciar la app (API 26+)
    // ─────────────────────────────────────────────────────────────────────────

    fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Canal para la notificación fija diaria (importancia alta para que aparezca siempre)
        val canalFija = NotificationChannel(
            CANAL_FIJA,
            "Seguimiento diario",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificación persistente de tareas pendientes"
            setShowBadge(true)
        }

        // Canal para recordatorios aleatorios (importancia normal, descartable)
        val canalRecordatorio = NotificationChannel(
            CANAL_RECORDATORIO,
            "Recordatorios de tareas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorios aleatorios de tus tareas pendientes"
            setShowBadge(false)
        }

        manager.createNotificationChannel(canalFija)
        manager.createNotificationChannel(canalRecordatorio)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICACIÓN FIJA — persistente, no se descarta con deslizar
    // ─────────────────────────────────────────────────────────────────────────

    fun construirNotifFija(
        context:         Context,
        totalPendientes: Int
    ): NotificationCompat.Builder {

        val intentAbrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destino", "tareas")
        }
        val pendingAbrir = PendingIntent.getActivity(
            context, 0, intentAbrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: "Registrar avance" → abre la app en tareas
        val intentAvance = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destino", "registrar_avance")
        }
        val pendingAvance = PendingIntent.getActivity(
            context, 1, intentAvance,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: "Sin avances" → broadcast para descartar silenciosamente
        val intentSinAvance = Intent(context, ReceptorAccionNotif::class.java).apply {
            action = ReceptorAccionNotif.ACCION_SIN_AVANCE
        }
        val pendingSinAvance = PendingIntent.getBroadcast(
            context, 2, intentSinAvance,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intentReiniciar = Intent(context, ReceptorAccionNotif::class.java).apply {
            action = ReceptorAccionNotif.ACCION_REINICIAR_SERVICIO
        }
        val pendingReiniciar = PendingIntent.getBroadcast(
            context, 98, intentReiniciar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cuerpo = if (totalPendientes == 1)
            "Tienes 1 tarea pendiente. ¿Avanzaste algo hoy?"
        else
            "Tienes $totalPendientes tareas pendientes. ¿Avanzaste algo hoy?"

        return NotificationCompat.Builder(context, CANAL_FIJA)
            .setSmallIcon(R.drawable.ic_notificacion_activa)
            .setContentTitle("StudiOS — Seguimiento diario")
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(pendingReiniciar)
            .setContentIntent(pendingAbrir)
            .addAction(
                R.drawable.ic_check_circle,
                "Sí, registrar avance",
                pendingAvance
            )
            .addAction(
                R.drawable.ic_remove_circle_24,
                "No hubo avances",
                pendingSinAvance
            )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICACIÓN RECORDATORIO ALEATORIO — descartable
    // ─────────────────────────────────────────────────────────────────────────

    fun construirNotifRecordatorio(
        context:      Context,
        tituloTarea:  String
    ): NotificationCompat.Builder {

        val intentAbrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destino", "tareas")
        }
        val pendingAbrir = PendingIntent.getActivity(
            context, 3, intentAbrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CANAL_RECORDATORIO)
            .setSmallIcon(R.drawable.ic_alarma)
            .setContentTitle("Recuerda tu tarea 📚")
            .setContentText("\"$tituloTarea\" sigue pendiente. ¡No la dejes para el final!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("\"$tituloTarea\" sigue pendiente. ¡No la dejes para el final!")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingAbrir)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MOSTRAR / CANCELAR
    // ─────────────────────────────────────────────────────────────────────────

    fun mostrar(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelar(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}