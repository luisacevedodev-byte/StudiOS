package com.luixard.studios.interfaz.tareas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R

class AvanceTareaDialog(
    private val textoExistente: String = "", // Nuevo parámetro para editar
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

        // Si estamos editando, llenamos el campo
        if (textoExistente.isNotEmpty()) {
            // Quitamos la fecha "[dd/MM/yyyy] " para que solo edite la nota
            val textoLimpio = textoExistente.substringAfter("] ")
            etNota.setText(textoLimpio)
        }

        btnCancelar.setOnClickListener { dismiss() }

        btnGuardar.setOnClickListener {
            val nota = etNota.text.toString().trim()
            if (nota.isEmpty()) {
                Toast.makeText(requireContext(), "Escribe un avance", Toast.LENGTH_SHORT).show()
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