package com.luixard.studios.interfaz.tareas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListaTareasFragment : Fragment() {

    private val viewModel: TareasViewModel by viewModels()
    private lateinit var adaptador: AdaptadorTareas

    // Variables para mantener el estado de los filtros y la búsqueda
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

        // Elementos de Búsqueda y Filtros
        val etBusqueda = view.findViewById<TextInputEditText>(R.id.etBusqueda)
        val cgFiltrosTareas = view.findViewById<ChipGroup>(R.id.cgFiltrosTareas)
        val chipTodas = view.findViewById<Chip>(R.id.chipTodas)
        val chipPendientes = view.findViewById<Chip>(R.id.chipPendientes)
        val chipVencidas = view.findViewById<Chip>(R.id.chipVencidas)

        // Inicializar Adaptador
        adaptador = AdaptadorTareas(
            alCompletar = { tarea, checkbox ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Completar Tarea")
                    .setMessage("¿Deseas marcar '${tarea.titulo_tarea}' como completa?")
                    .setPositiveButton("Confirmar") { _, _ ->
                        viewModel.marcarComoCompletada(tarea.id_tarea)
                    }
                    .setNegativeButton("Cancelar") { dialog, _ ->
                        checkbox.isChecked = false
                        dialog.dismiss()
                    }
                    .show()
            },
            alAbrirDetalles = { tarea ->
                // Al tocar una tarea, se abre el formulario de Detalles de Tarea (CU-03)
                val dialog = EditarTareaDialog(
                    tareaAEditar = tarea,
                    alGuardar = { tareaEditada ->
                        viewModel.guardarTarea(tareaEditada)
                        Toast.makeText(requireContext(), "Cambios guardados", Toast.LENGTH_SHORT).show()
                    },
                    alEliminar = { tareaABorrar ->
                        viewModel.moverPapelera(tareaABorrar.id_tarea)
                        Toast.makeText(requireContext(), "Tarea enviada a papelera", Toast.LENGTH_SHORT).show()
                    }
                )
                dialog.show(parentFragmentManager, "EditarTareaDialog")
            }
        )

        recyclerTareas.layoutManager = LinearLayoutManager(requireContext())
        recyclerTareas.adapter = adaptador

        // --- SISTEMA DE BÚSQUEDA Y FILTRADO ---
        fun aplicarFiltros() {
            var listaFiltrada = listaOriginal

            // 1. Filtrar por Búsqueda (Texto)
            if (textoBusquedaActual.isNotEmpty()) {
                listaFiltrada = listaFiltrada.filter {
                    it.titulo_tarea.contains(textoBusquedaActual, ignoreCase = true)
                }
            }

            // 2. Filtrar por Chip seleccionado
            val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            listaFiltrada = when (filtroChipActual) {
                R.id.chipPendientes -> listaFiltrada.filter { !it.es_completada && it.fecha_entrega >= fechaHoy }
                R.id.chipVencidas -> listaFiltrada.filter { it.fecha_entrega < fechaHoy }
                else -> listaFiltrada // Todas
            }

            // Actualizar la lista visible
            adaptador.submitList(listaFiltrada)

            // Mostrar/Ocultar el diseño de "Vacío"
            layoutVacio.visibility = if (listaFiltrada.isEmpty()) View.VISIBLE else View.GONE
            recyclerTareas.visibility = if (listaFiltrada.isEmpty()) View.GONE else View.VISIBLE
        }

        // Listener para la barra de búsqueda
        etBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textoBusquedaActual = s.toString().trim()
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para los Chips
        cgFiltrosTareas.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                filtroChipActual = checkedIds[0]
                aplicarFiltros()
            }
        }

        // --- OBSERVADOR PRINCIPAL DE LA BASE DE DATOS ---
        viewModel.tareasPendientes.observe(viewLifecycleOwner) { lista ->
            listaOriginal = lista // Guardamos la lista original intacta

            // Actualizamos los contadores de los Chips en tiempo real
            val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val cantidadVencidas = lista.count { it.fecha_entrega < fechaHoy }
            val cantidadPendientes = lista.count { !it.es_completada && it.fecha_entrega >= fechaHoy }

            chipTodas.text = "Todas (${lista.size})"
            chipPendientes.text = "Pendientes ($cantidadPendientes)"
            chipVencidas.text = "Vencidas ($cantidadVencidas)"

            tvContador.text = "${lista.size} tareas en total"

            aplicarFiltros() // Re-filtramos automáticamente si hay cambios nuevos en la BD
        }

        // --- NAVEGACIÓN ---

        // Botón + para agregar nueva tarea (CU-01)
        botonAgregar.setOnClickListener {
            val dialog = EditarTareaDialog(
                tareaAEditar = null,
                alGuardar = { nuevaTarea ->
                    viewModel.guardarTarea(nuevaTarea)
                }
            )
            dialog.show(parentFragmentManager, "EditarTareaDialog")
        }

        // Botón de Historial (Reloj) (CU-05)
        val btnHistorial = view.findViewById<View>(R.id.btnHistorialTareas)
        btnHistorial.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, HistorialTareasFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}