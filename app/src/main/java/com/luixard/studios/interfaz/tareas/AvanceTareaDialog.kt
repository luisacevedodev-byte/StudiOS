package com.luixard.studios.interfaz.tareas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.utilidades.MensajesUI

class AvanceTareaDialog(
    private val textoExistente: String = "",
    private val alGuardar: (String) -> Unit
) : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return inflater.inflate(R.layout.pantalla_registrar_avance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etNota = view.findViewById<TextInputEditText>(R.id.etNotaAvance)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarAvance)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarAvance)

        if (textoExistente.isNotEmpty()) {
            etNota.setText(textoExistente)
        }

        btnCancelar.setOnClickListener { dismiss() }

        btnGuardar.setOnClickListener {
            val nota = etNota.text.toString().trim()
            if (nota.isEmpty()) {
                MensajesUI.advertencia(requireActivity(), "No puedes guardar un avance vacío")
                return@setOnClickListener
            }
            alGuardar(nota)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}