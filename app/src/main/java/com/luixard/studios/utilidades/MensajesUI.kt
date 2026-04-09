package com.luixard.studios.utilidades

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

object MensajesUI {

    fun exito(activity: Activity?, mensaje: String) {
        mostrarSnackbar(activity, "✅ $mensaje", "#00838F")
    }

    fun error(activity: Activity?, mensaje: String) {
        mostrarSnackbar(activity, "🗑️ $mensaje", "#D32F2F")
    }

    fun advertencia(activity: Activity?, mensaje: String) {
        mostrarSnackbar(activity, "⚠️ $mensaje", "#F57F17")
    }

    private fun mostrarSnackbar(activity: Activity?, texto: String, colorHex: String) {
        activity?.findViewById<View>(android.R.id.content)?.let { view ->
            val snackbar = Snackbar.make(view, texto, Snackbar.LENGTH_SHORT)
            snackbar.setBackgroundTint(Color.parseColor(colorHex))
            snackbar.setTextColor(Color.WHITE)
            snackbar.animationMode = Snackbar.ANIMATION_MODE_SLIDE

            val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView.textSize = 15f
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER

            snackbar.show()
        }
    }


}