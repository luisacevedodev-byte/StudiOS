package com.luixard.studios.interfaz.tareas

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Tarea
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditarTareaDialog(
    private val tareaAEditar: Tarea? = null,
    private val alGuardar: (Tarea) -> Unit,
    private val alEliminar: ((Tarea) -> Unit)? = null
) : DialogFragment() {

    private var fechaSeleccionada: String = ""
    private var listaAvances = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return inflater.inflate(R.layout.pantalla_editar_tarea, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTituloDialogo = view.findViewById<TextView>(R.id.tvTituloNuevaTarea)
        val etTitulo = view.findViewById<TextInputEditText>(R.id.etTituloTarea)
        val etFecha = view.findViewById<TextInputEditText>(R.id.etFechaEntrega)
        val etDescripcion = view.findViewById<TextInputEditText>(R.id.etDescripcion)
        val rgPrioridad = view.findViewById<RadioGroup>(R.id.rgPrioridad)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarTarea)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarFormulario)

        // Ahora el botón basura es buscado genéricamente como View para no generar conflicto con el XML
        val btnEliminar = view.findViewById<View>(R.id.btnEliminarDesdeDetalle)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutDetalles)
        val layoutGeneral = view.findViewById<LinearLayout>(R.id.layoutSeccionGeneral)
        val layoutAvances = view.findViewById<LinearLayout>(R.id.layoutSeccionAvances)

        val btnNuevoAvance = view.findViewById<Button>(R.id.btnNuevoAvance)
        val contenedorAvances = view.findViewById<LinearLayout>(R.id.contenedorListaAvances)
        val tvAvancesVacio = view.findViewById<TextView>(R.id.tvAvancesVacio)

        fun renderizarAvances() {
            contenedorAvances.removeAllViews()
            if (listaAvances.isEmpty()) {
                tvAvancesVacio.visibility = View.VISIBLE
            } else {
                tvAvancesVacio.visibility = View.GONE
                listaAvances.forEachIndexed { index, avance ->
                    val vistaAvance = layoutInflater.inflate(R.layout.item_avance, null)
                    val tvTexto = vistaAvance.findViewById<TextView>(R.id.tvTextoAvance)
                    val btnEdit = vistaAvance.findViewById<ImageButton>(R.id.btnEditarAvance)
                    val btnBorrar = vistaAvance.findViewById<ImageButton>(R.id.btnEliminarAvance)

                    tvTexto.text = avance

                    btnEdit.setOnClickListener {
                        val dialogAvance = AvanceTareaDialog(avance) { textoEditado ->
                            val fechaHoy = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                            listaAvances[index] = "• [$fechaHoy] $textoEditado"
                            renderizarAvances()
                        }
                        dialogAvance.show(parentFragmentManager, "AvanceTareaDialog")
                    }

                    btnBorrar.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Eliminar Avance")
                            .setMessage("¿Seguro que deseas eliminar este registro de avance?")
                            .setPositiveButton("Eliminar") { _, _ ->
                                listaAvances.removeAt(index)
                                renderizarAvances()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }

                    contenedorAvances.addView(vistaAvance)
                }
            }
        }

        if (tareaAEditar != null) {
            tvTituloDialogo.text = "Detalles de Tarea"
            btnGuardar.text = "Guardar Cambios"
            btnEliminar.visibility = View.VISIBLE
            tabLayout.visibility = View.VISIBLE

            etTitulo.setText(tareaAEditar.titulo_tarea)
            etFecha.setText(tareaAEditar.fecha_entrega)
            fechaSeleccionada = tareaAEditar.fecha_entrega

            val partesDescripcion = tareaAEditar.descripcion_tarea?.split("||") ?: listOf("", "")
            etDescripcion.setText(partesDescripcion[0])

            if (partesDescripcion.size > 1 && partesDescripcion[1].isNotEmpty()) {
                listaAvances = partesDescripcion[1].split("&&").toMutableList()
            }
            renderizarAvances()

            when (tareaAEditar.id_prioridad) {
                "ALTA" -> rgPrioridad.check(R.id.rbAlta)
                "MEDIA" -> rgPrioridad.check(R.id.rbMedia)
                "BAJA" -> rgPrioridad.check(R.id.rbBaja)
            }
        }

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
            val dialogAvance = AvanceTareaDialog("") { nuevaNota ->
                val fechaHoy = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                listaAvances.add("• [$fechaHoy] $nuevaNota")
                renderizarAvances()
                Toast.makeText(requireContext(), "Avance registrado. No olvides Guardar Cambios.", Toast.LENGTH_SHORT).show()
            }
            dialogAvance.show(parentFragmentManager, "AvanceTareaDialog")
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
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnGuardar.setOnClickListener {
            val titulo = etTitulo.text.toString().trim()
            val descripcionBase = etDescripcion.text.toString().trim()
            val prioridad = when (rgPrioridad.checkedRadioButtonId) {
                R.id.rbAlta -> "ALTA"
                R.id.rbMedia -> "MEDIA"
                R.id.rbBaja -> "BAJA"
                else -> ""
            }

            if (titulo.isEmpty() || fechaSeleccionada.isEmpty() || prioridad.isEmpty()) {
                Toast.makeText(requireContext(), "Campos obligatorios incompletos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val avancesString = listaAvances.joinToString("&&")
            val descripcionFinal = if (avancesString.isNotEmpty()) "$descripcionBase||$avancesString" else descripcionBase

            val tareaFinal = Tarea(
                id_tarea = tareaAEditar?.id_tarea ?: 0,
                titulo_tarea = titulo,
                fecha_entrega = fechaSeleccionada,
                id_prioridad = prioridad,
                descripcion_tarea = descripcionFinal.ifEmpty { null },
                id_materia = tareaAEditar?.id_materia,
                es_completada = tareaAEditar?.es_completada ?: false
            )

            alGuardar(tareaFinal)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // ¡REDUCCIÓN DE ALTURA! Cambié de 0.85 a 0.70 (aprox. 2.5 cm menos de pantalla)
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.70).toInt()
        )
    }
}