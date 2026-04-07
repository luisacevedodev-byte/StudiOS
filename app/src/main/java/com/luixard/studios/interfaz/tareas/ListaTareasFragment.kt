package com.luixard.studios.interfaz.tareas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Tarea
import com.luixard.studios.utilidades.MensajesUI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListaTareasFragment : Fragment() {

    private val viewModel: TareasViewModel by viewModels()
    private lateinit var adaptador: AdaptadorTareas

    private var listaOriginal: List<Tarea> = emptyList()
    private var textoBusquedaActual = ""
    private var filtroChipActual = R.id.chipTodas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pantalla_tareas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val botonAgregar = view.findViewById<FloatingActionButton>(R.id.fabAgregarTarea)
        val recyclerTareas = view.findViewById<RecyclerView>(R.id.rvListaTareas)
        val layoutVacio = view.findViewById<View>(R.id.layoutEstadoVacio)
        val tvContador = view.findViewById<TextView>(R.id.tvContadorPendientes)

        val etBusqueda = view.findViewById<TextInputEditText>(R.id.etBusqueda)
        val cgFiltrosTareas = view.findViewById<ChipGroup>(R.id.cgFiltrosTareas)
        val chipTodas = view.findViewById<Chip>(R.id.chipTodas)
        val chipPendientes = view.findViewById<Chip>(R.id.chipPendientes)
        val chipVencidas = view.findViewById<Chip>(R.id.chipVencidas)

        adaptador = AdaptadorTareas(
            alCompletar = { tarea, checkbox ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Completar Tarea")
                    .setMessage("¿Deseas marcar '${tarea.titulo_tarea}' como completa?")
                    .setPositiveButton("Confirmar") { _, _ ->
                        viewModel.marcarComoCompletada(tarea.id_tarea)
                        MensajesUI.exito(requireActivity(), "¡Excelente! Tarea completada")
                    }
                    .setNegativeButton("Cancelar") { dialog, _ ->
                        checkbox.isChecked = false
                        dialog.dismiss()
                    }
                    .show()
            },
            alAbrirDetalles = { tarea ->
                val dialog = EditarTareaDialog(
                    tareaAEditar = tarea,
                    alGuardar = { tareaEditada ->
                        viewModel.guardarTarea(tareaEditada)
                        MensajesUI.exito(requireActivity(), "Cambios guardados correctamente")
                    },
                    alEliminar = { tareaABorrar ->
                        viewModel.moverPapelera(tareaABorrar.id_tarea)
                        MensajesUI.error(requireActivity(), "Tarea enviada a la papelera")
                    }
                )
                dialog.show(parentFragmentManager, "EditarTareaDialog")
            }
        )

        recyclerTareas.layoutManager = LinearLayoutManager(requireContext())
        recyclerTareas.adapter = adaptador

        fun aplicarFiltros() {
            var listaFiltrada = listaOriginal
            if (textoBusquedaActual.isNotEmpty()) {
                listaFiltrada = listaFiltrada.filter {
                    it.titulo_tarea.contains(textoBusquedaActual, ignoreCase = true)
                }
            }
            val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            listaFiltrada = when (filtroChipActual) {
                R.id.chipPendientes -> listaFiltrada.filter { !it.es_completada && it.fecha_entrega >= fechaHoy }
                R.id.chipVencidas -> listaFiltrada.filter { it.fecha_entrega < fechaHoy }
                else -> listaFiltrada
            }
            adaptador.submitList(listaFiltrada)
            layoutVacio.visibility = if (listaFiltrada.isEmpty()) View.VISIBLE else View.GONE
            recyclerTareas.visibility = if (listaFiltrada.isEmpty()) View.GONE else View.VISIBLE
        }

        etBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textoBusquedaActual = s.toString().trim()
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cgFiltrosTareas.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                filtroChipActual = checkedIds[0]
                aplicarFiltros()
            }
        }

        viewModel.tareasPendientes.observe(viewLifecycleOwner) { lista ->
            listaOriginal = lista
            val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val cantidadVencidas = lista.count { it.fecha_entrega < fechaHoy }
            val cantidadPendientes = lista.count { !it.es_completada && it.fecha_entrega >= fechaHoy }

            chipTodas.text = "Todas (${lista.size})"
            chipPendientes.text = "Pendientes ($cantidadPendientes)"
            chipVencidas.text = "Vencidas ($cantidadVencidas)"
            tvContador.text = "${lista.size} tareas en total"

            aplicarFiltros()
        }

        botonAgregar.setOnClickListener {
            val dialog = EditarTareaDialog(
                tareaAEditar = null,
                alGuardar = { nuevaTarea ->
                    viewModel.guardarTarea(nuevaTarea)
                    MensajesUI.exito(requireActivity(), "Tarea creada exitosamente")
                }
            )
            dialog.show(parentFragmentManager, "EditarTareaDialog")
        }

        val btnHistorial = view.findViewById<View>(R.id.btnHistorialTareas)
        btnHistorial.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, HistorialTareasFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}