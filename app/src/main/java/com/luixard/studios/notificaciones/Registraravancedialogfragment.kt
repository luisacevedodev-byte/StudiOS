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

        /** Sin preselección (flujo desde notificación). */
        fun newInstance() = RegistrarAvanceDialogFragment()

        /** Con tarea preseleccionada (flujo desde detalle de tarea en la app). */
        fun newInstance(idTarea: Long) = RegistrarAvanceDialogFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID_TAREA, idTarea) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // VISTAS
    // ─────────────────────────────────────────────────────────────────────

    private lateinit var radioGroupTareas:   RadioGroup
    private lateinit var inputNota:          TextInputEditText
    private lateinit var btnConfirmar:       MaterialButton
    private lateinit var btnCancelar:        MaterialButton

    // ─────────────────────────────────────────────────────────────────────
    // INFLATE
    // ─────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_registrar_avance, container, false)

    // ─────────────────────────────────────────────────────────────────────
    // LÓGICA PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroupTareas = view.findViewById(R.id.radioGroupTareas)
        inputNota        = view.findViewById(R.id.inputNotaAvance)
        btnConfirmar     = view.findViewById(R.id.btnConfirmarAvance)
        btnCancelar      = view.findViewById(R.id.btnCancelarAvance)

        val idPreseleccionada = arguments?.getLong(ARG_ID_TAREA, -1L) ?: -1L
        val app = requireContext().applicationContext as AplicacionStudiOS

        // ── Cargar tareas pendientes ──────────────────────────────────────
        CoroutineScope(Dispatchers.IO).launch {
            val tareas = app.repositorioTareas.tareasPendientes.first()

            // Ordenar: prioridad Alta (id=1) y Media (id=2) primero
            // ← ADAPTA: si tu campo de prioridad tiene otro nombre o valor
            val ordenadas = tareas.sortedBy { it.id_prioridad }

            withContext(Dispatchers.Main) {
                if (ordenadas.isEmpty()) {
                    // Sin tareas, no tiene sentido el diálogo
                    Toast.makeText(requireContext(),
                        "¡No tienes tareas pendientes!", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@withContext
                }

                ordenadas.forEach { tarea ->
                    val rb = RadioButton(requireContext()).apply {
                        id        = tarea.id_tarea.toInt()
                        text      = tarea.titulo_tarea
                        textSize  = 15f
                        setPadding(16, 12, 16, 12)
                    }
                    radioGroupTareas.addView(rb)
                }

                if (idPreseleccionada != -1L) {
                    radioGroupTareas.check(idPreseleccionada.toInt())
                    inputNota.requestFocus()
                }
            }
        }

        // ── Confirmar avance ─────────────────────────────────────────────
        btnConfirmar.setOnClickListener {
            val idSeleccionada = radioGroupTareas.checkedRadioButtonId

            if (idSeleccionada == -1) {
                Toast.makeText(requireContext(),
                    "Selecciona la tarea en la que trabajaste", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nota = inputNota.text?.toString()?.trim() ?: ""

            CoroutineScope(Dispatchers.IO).launch {
                // Paso 6: timestamp automático
                val ahora = Date()

                val registro = RegistroActividad(
                    id_tarea       = idSeleccionada.toLong(),
                    nota           = nota.ifEmpty { null },
                    fecha_registro = ahora,
                    tipo           = "avance"
                )
                app.repositorioAuth.guardarRegistroActividad(registro)
                ServicioNotificacionFija.detener(requireContext())

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "¡Avance registrado! Sigue así 💪", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }

        // ── Cancelar ─────────────────────────────────────────────────────
        btnCancelar.setOnClickListener { dismiss() }
    }
}