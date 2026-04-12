package com.luixard.studios.interfaz.notificaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.RegistroActividad
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import com.luixard.studios.notificaciones.ServicioNotificacionFija
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class RegistrarAvanceDialogFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID_TAREA = "id_tarea_preseleccionada"

        fun newInstance() = RegistrarAvanceDialogFragment()

        fun newInstance(idTarea: Long) = RegistrarAvanceDialogFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID_TAREA, idTarea) }
        }

        // Orden de prioridad para mostrar las tareas más urgentes primero.
        private val ORDEN_PRIORIDAD = mapOf(
            "Alta"  to 0, "alta"  to 0, "ALTA"  to 0, "1" to 0,
            "Media" to 1, "media" to 1, "MEDIA" to 1, "2" to 1,
            "Baja"  to 2, "baja"  to 2, "BAJA"  to 2, "3" to 2
        )
    }

    private lateinit var radioGroupTareas: RadioGroup
    private lateinit var inputNota:        TextInputEditText
    private lateinit var btnConfirmar:     MaterialButton
    private lateinit var btnCancelar:      MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_registrar_avance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroupTareas = view.findViewById(R.id.radioGroupTareas)
        inputNota        = view.findViewById(R.id.inputNotaAvance)
        btnConfirmar     = view.findViewById(R.id.btnConfirmarAvance)
        btnCancelar      = view.findViewById(R.id.btnCancelarAvance)

        val idPreseleccionada = arguments?.getLong(ARG_ID_TAREA, -1L) ?: -1L
        val app = requireContext().applicationContext as AplicacionStudiOS

        // ── Cargar y ordenar tareas pendientes ────────────────────────────
        CoroutineScope(Dispatchers.IO).launch {
            val tareas = app.repositorioTareas.tareasPendientes.first()

            // Alta → Media → Baja (las más urgentes primero)
            val ordenadas = tareas.sortedBy { ORDEN_PRIORIDAD[it.id_prioridad] ?: 99 }

            withContext(Dispatchers.Main) {
                if (ordenadas.isEmpty()) {
                    Toast.makeText(requireContext(),
                        "¡No tienes tareas pendientes!", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@withContext
                }

                ordenadas.forEach { tarea ->
                    val rb = RadioButton(requireContext()).apply {
                        id       = tarea.id_tarea
                        text     = tarea.titulo_tarea
                        textSize = 15f
                        setPadding(16, 12, 16, 12)
                    }
                    radioGroupTareas.addView(rb)
                }

                // Pre-seleccionar si viene del detalle de la tarea
                if (idPreseleccionada != -1L) {
                    radioGroupTareas.check(idPreseleccionada.toInt())
                    inputNota.requestFocus()
                }
            }
        }

        // ── Confirmar avance ──────────────────────────────────────────────
        btnConfirmar.setOnClickListener {
            val idSeleccionada = radioGroupTareas.checkedRadioButtonId
            if (idSeleccionada == -1) {
                Toast.makeText(requireContext(),
                    "Selecciona la tarea en la que trabajaste", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // NOTA OBLIGATORIA: se debe describir qué avanzaste para que quede
            // registrado en el historial de la tarea y sea visible después.
            val nota = inputNota.text?.toString()?.trim() ?: ""
            if (nota.isEmpty()) {
                inputNota.error = "Escribe qué avanzaste hoy (ej. \"Leí el capítulo 1\")"
                inputNota.requestFocus()
                return@setOnClickListener
            }
            inputNota.error = null

            CoroutineScope(Dispatchers.IO).launch {
                val ahora = Date()

                val registro = RegistroActividad(
                    id_tarea       = idSeleccionada.toLong(),
                    nota           = nota,
                    fecha_registro = ahora,
                    tipo           = "avance"
                )
                app.repositorioAuth.guardarRegistroActividad(registro)

                // Cerrar la notificación fija
                ServicioNotificacionFija.detener(requireContext())

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "¡Avance registrado! Sigue así 💪", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }

        btnCancelar.setOnClickListener { dismiss() }
    }
}