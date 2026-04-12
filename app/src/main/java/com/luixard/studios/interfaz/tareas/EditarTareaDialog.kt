package com.luixard.studios.interfaz.tareas

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.HistorialAvanceTarea
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.utilidades.MensajesUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditarTareaDialog(
    private val tareaAEditar: Tarea? = null,
    private val alGuardar: (Tarea) -> Unit,
    private val alEliminar: ((Tarea) -> Unit)? = null
) : DialogFragment() {

    private var fechaSeleccionada: String = ""

    // Avances editables: guardados en descripcion_tarea (fuente: este dialog)
    private var listaAvances = mutableListOf<String>()

    // Avances del historial: registrados desde la notificación, guardados en historial_avance_tareas
    private val avancesHistorial = mutableListOf<HistorialAvanceTarea>()

    // Vistas promovidas a nivel de clase para que renderizarAvances() pueda usarlas desde coroutines
    private lateinit var contenedorAvances: LinearLayout
    private lateinit var tvAvancesVacio: TextView

    private val fmtFecha = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return inflater.inflate(R.layout.tarea_pantalla_editar_tarea, container, false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERIZADO UNIFICADO DE AVANCES
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderizarAvances() {
        contenedorAvances.removeAllViews()
        val hayContenido = avancesHistorial.isNotEmpty() || listaAvances.isNotEmpty()
        tvAvancesVacio.visibility = if (hayContenido) View.GONE else View.VISIBLE

        // ── 1) Avances del historial (editables y borrables) ─────────────────
        // Se muestran con un ícono 📲 para indicar que vienen de la notificación.
        avancesHistorial.forEach { avance ->
            val vistaAvance = layoutInflater.inflate(R.layout.tarea_item_avance, null)

            val layoutPrincipal = vistaAvance.findViewById<LinearLayout>(R.id.layoutPrincipalAvance)
            val layoutOpciones  = vistaAvance.findViewById<LinearLayout>(R.id.layoutOpcionesAvance)
            val tvFechaHora     = vistaAvance.findViewById<TextView>(R.id.tvFechaHoraAvance)
            val tvTexto         = vistaAvance.findViewById<TextView>(R.id.tvTextoAvance)
            val ivFlecha        = vistaAvance.findViewById<ImageView>(R.id.ivFlechaAvance)
            val btnEdit         = vistaAvance.findViewById<Button>(R.id.btnEditarAvance)
            val btnBorrar       = vistaAvance.findViewById<Button>(R.id.btnEliminarAvance)

            tvFechaHora.text = "📲 " + fmtFecha.format(avance.fecha_hora_registro)
            tvTexto.text     = avance.nota_avance ?: "(sin nota)"

            layoutOpciones.visibility = View.GONE
            var expandido = false
            layoutPrincipal.setOnClickListener {
                expandido = !expandido
                layoutOpciones.visibility = if (expandido) View.VISIBLE else View.GONE
                ivFlecha.animate().rotation(if (expandido) 180f else 0f).setDuration(350).start()
            }

            btnEdit.setOnClickListener {
                AvanceTareaDialog(avance.nota_avance ?: "") { textoEditado ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val app = requireContext().applicationContext as AplicacionStudiOS
                            val avanceActualizado = avance.copy(nota_avance = textoEditado)
                            app.baseDatos.registroActividadDao().actualizarAvance(avanceActualizado)

                            withContext(Dispatchers.Main) {
                                if (!isAdded) return@withContext
                                val idx = avancesHistorial.indexOfFirst { it.id_avance == avance.id_avance }
                                if (idx >= 0) avancesHistorial[idx] = avanceActualizado
                                renderizarAvances()
                            }
                        } catch (_: Exception) {}
                    }
                }.show(parentFragmentManager, "AvanceTareaDialog")
            }

            btnBorrar.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Avance")
                    .setMessage("¿Seguro que deseas eliminar este registro de avance?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val app = requireContext().applicationContext as AplicacionStudiOS
                                app.baseDatos.registroActividadDao().eliminarAvance(avance)
                                withContext(Dispatchers.Main) {
                                    if (!isAdded) return@withContext
                                    avancesHistorial.remove(avance)
                                    renderizarAvances()
                                    MensajesUI.error(requireActivity(), "Registro de avance eliminado")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    .setNegativeButton("Cancelar", null).show()
            }

            contenedorAvances.addView(vistaAvance)
        }

        // ── 2) Avances editables guardados en descripcion_tarea ───────────────
        listaAvances.forEachIndexed { index, avance ->
            val vistaAvance = layoutInflater.inflate(R.layout.tarea_item_avance, null)

            val layoutPrincipal = vistaAvance.findViewById<LinearLayout>(R.id.layoutPrincipalAvance)
            val layoutOpciones  = vistaAvance.findViewById<LinearLayout>(R.id.layoutOpcionesAvance)
            val tvFechaHora     = vistaAvance.findViewById<TextView>(R.id.tvFechaHoraAvance)
            val tvTexto         = vistaAvance.findViewById<TextView>(R.id.tvTextoAvance)
            val ivFlecha        = vistaAvance.findViewById<ImageView>(R.id.ivFlechaAvance)
            val btnEdit         = vistaAvance.findViewById<Button>(R.id.btnEditarAvance)
            val btnBorrar       = vistaAvance.findViewById<Button>(R.id.btnEliminarAvance)

            val partes      = avance.split("|SPLIT|")
            val fechaHora   = if (partes.size > 1) partes[0] else ""
            val textoAvance = if (partes.size > 1) partes[1] else avance

            tvFechaHora.text = fechaHora
            tvTexto.text     = textoAvance

            layoutOpciones.visibility = View.GONE
            var expandido = false
            layoutPrincipal.setOnClickListener {
                expandido = !expandido
                layoutOpciones.visibility = if (expandido) View.VISIBLE else View.GONE
                ivFlecha.animate().rotation(if (expandido) 180f else 0f).setDuration(350).start()
            }

            btnEdit.setOnClickListener {
                AvanceTareaDialog(textoAvance) { textoEditado ->
                    val fechaHoy = fmtFecha.format(Date())
                    listaAvances[index] = "$fechaHoy|SPLIT|$textoEditado"
                    renderizarAvances()
                }.show(parentFragmentManager, "AvanceTareaDialog")
            }

            btnBorrar.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Avance")
                    .setMessage("¿Seguro que deseas eliminar este registro de avance?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        listaAvances.removeAt(index)
                        renderizarAvances()
                        MensajesUI.error(requireActivity(), "Registro de avance eliminado")
                    }
                    .setNegativeButton("Cancelar", null).show()
            }

            contenedorAvances.addView(vistaAvance)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CICLO DE VIDA
    // ─────────────────────────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTituloDialogo = view.findViewById<TextView>(R.id.tvTituloNuevaTarea)
        val etTitulo        = view.findViewById<TextInputEditText>(R.id.etTituloTarea)
        val etFecha         = view.findViewById<TextInputEditText>(R.id.etFechaEntrega)
        val etMateria       = view.findViewById<TextInputEditText>(R.id.etMateria)
        val etDescripcion   = view.findViewById<TextInputEditText>(R.id.etDescripcion)
        val rgPrioridad     = view.findViewById<RadioGroup>(R.id.rgPrioridad)
        val btnGuardar      = view.findViewById<Button>(R.id.btnGuardarTarea)
        val btnCerrar       = view.findViewById<ImageButton>(R.id.btnCerrarFormulario)
        val btnEliminar     = view.findViewById<View>(R.id.btnEliminarDesdeDetalle)
        val tabLayout       = view.findViewById<TabLayout>(R.id.tabLayoutDetalles)
        val layoutGeneral   = view.findViewById<LinearLayout>(R.id.layoutSeccionGeneral)
        val layoutAvances   = view.findViewById<LinearLayout>(R.id.layoutSeccionAvances)
        val btnNuevoAvance  = view.findViewById<Button>(R.id.btnNuevoAvance)

        // Inicializar campos de clase
        contenedorAvances = view.findViewById(R.id.contenedorListaAvances)
        tvAvancesVacio    = view.findViewById(R.id.tvAvancesVacio)

        // ── Cargar datos de la tarea existente ────────────────────────────────
        if (tareaAEditar != null) {
            tvTituloDialogo.text   = "Detalles de Tarea"
            btnGuardar.text        = "Guardar Cambios"
            btnEliminar.visibility = View.VISIBLE
            tabLayout.visibility   = View.VISIBLE

            etTitulo.setText(tareaAEditar.titulo_tarea)
            etFecha.setText(tareaAEditar.fecha_entrega)
            etMateria.setText(tareaAEditar.id_materia?.toString() ?: "")
            fechaSeleccionada = tareaAEditar.fecha_entrega

            val partes = tareaAEditar.descripcion_tarea?.split("||") ?: listOf("", "")
            etDescripcion.setText(partes[0])
            if (partes.size > 1 && partes[1].isNotEmpty()) {
                listaAvances = partes[1].split("&&").toMutableList()
            }
            renderizarAvances()

            when (tareaAEditar.id_prioridad) {
                "ALTA"  -> rgPrioridad.check(R.id.rbAlta)
                "MEDIA" -> rgPrioridad.check(R.id.rbMedia)
                "BAJA"  -> rgPrioridad.check(R.id.rbBaja)
            }

            // Cargar avances del historial desde la BD (registrados via notificación)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app      = requireContext().applicationContext as AplicacionStudiOS
                    val historial = app.baseDatos.registroActividadDao()
                        .obtenerAvancesDeTarea(tareaAEditar.id_tarea)

                    if (historial.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            avancesHistorial.clear()
                            avancesHistorial.addAll(historial)
                            renderizarAvances()
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // ── Listeners ────────────────────────────────────────────────────────

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    layoutGeneral.visibility = View.VISIBLE
                    layoutAvances.visibility = View.GONE
                } else {
                    layoutGeneral.visibility = View.GONE
                    layoutAvances.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        etFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                fechaSeleccionada = "$y-${String.format("%02d", m + 1)}-${String.format("%02d", d)}"
                etFecha.setText(fechaSeleccionada)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnNuevoAvance.setOnClickListener {
            AvanceTareaDialog("") { nuevaNota ->
                val fechaHoy = fmtFecha.format(Date())
                listaAvances.add("$fechaHoy|SPLIT|$nuevaNota")
                renderizarAvances()
                MensajesUI.exito(requireActivity(), "Avance registrado. No olvides Guardar Cambios.")
            }.show(parentFragmentManager, "AvanceTareaDialog")
        }

        btnCerrar.setOnClickListener { dismiss() }

        btnEliminar.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar Tarea")
                .setMessage("¿Estás seguro de eliminar esta tarea? Se moverá a la papelera.")
                .setPositiveButton("Sí, eliminar") { _, _ ->
                    alEliminar?.invoke(tareaAEditar!!)
                    dismiss()
                }
                .setNegativeButton("Cancelar", null).show()
        }

        btnGuardar.setOnClickListener {
            val titulo          = etTitulo.text.toString().trim()
            val descripcionBase = etDescripcion.text.toString().trim()
            val prioridad       = when (rgPrioridad.checkedRadioButtonId) {
                R.id.rbAlta  -> "ALTA"
                R.id.rbMedia -> "MEDIA"
                R.id.rbBaja  -> "BAJA"
                else         -> ""
            }

            if (titulo.isEmpty() || fechaSeleccionada.isEmpty() || prioridad.isEmpty()) {
                MensajesUI.advertencia(requireActivity(), "Por favor, llena los campos con *")
                return@setOnClickListener
            }

            val avancesString    = listaAvances.joinToString("&&")
            val descripcionFinal = if (avancesString.isNotEmpty()) "$descripcionBase||$avancesString" else descripcionBase

            val tareaFinal = Tarea(
                id_tarea          = tareaAEditar?.id_tarea ?: 0,
                titulo_tarea      = titulo,
                fecha_entrega     = fechaSeleccionada,
                id_prioridad      = prioridad,
                descripcion_tarea = descripcionFinal.ifEmpty { null },
                id_materia        = tareaAEditar?.id_materia,
                es_completada     = tareaAEditar?.es_completada ?: false,
                esta_borrada      = tareaAEditar?.esta_borrada  ?: false,
                fecha_creacion    = tareaAEditar?.fecha_creacion ?: System.currentTimeMillis(),
                syncId            = tareaAEditar?.syncId ?: UUID.randomUUID().toString(),
                updatedAt         = System.currentTimeMillis()
            )

            alGuardar(tareaFinal)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels  * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.70).toInt()
        )
    }
}