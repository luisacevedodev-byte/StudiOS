package com.luixard.studios.interfaz.tareas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.luixard.studios.R

class HistorialTareasFragment : Fragment() {

    private val viewModel: TareasViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pantalla_historial_tareas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarHistorial)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutHistorial)
        val recyclerHistorial = view.findViewById<RecyclerView>(R.id.rvListaHistorial)
        val tvVacio = view.findViewById<TextView>(R.id.tvHistorialVacio)

        // 1. Configurar la flecha de regreso
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack() // Cierra esta pantalla y vuelve a la lista
        }

        // 2. Configurar el adaptador con los diálogos de confirmación (CU-05)
        val adaptador = AdaptadorHistorial(
            alRestaurar = { tarea ->
                val mensaje = if (tarea.es_completada) "¿Desmarcar esta tarea y devolverla a pendientes?" else "¿Restaurar esta tarea borrada?"
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restaurar Tarea")
                    .setMessage(mensaje)
                    .setPositiveButton("Sí, restaurar") { _, _ ->
                        viewModel.restaurarTarea(tarea.id_tarea)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            alEliminarPermanente = { tarea ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Permanentemente")
                    .setMessage("¿Estás seguro? Esta acción no se puede deshacer y borrará la tarea definitivamente.")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.eliminarPermanente(tarea)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        recyclerHistorial.layoutManager = LinearLayoutManager(requireContext())
        recyclerHistorial.adapter = adaptador

        // 3. Función para cambiar entre Completadas y Borradas
        fun actualizarLista(tabSeleccionada: Int) {
            // Removemos observadores anteriores para que no se mezclen
            viewModel.tareasCompletadas.removeObservers(viewLifecycleOwner)
            viewModel.tareasBorradas.removeObservers(viewLifecycleOwner)

            if (tabSeleccionada == 0) {
                // Pestaña Completadas
                viewModel.tareasCompletadas.observe(viewLifecycleOwner) { lista ->
                    adaptador.submitList(lista)
                    tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
            } else {
                // Pestaña Borradas
                viewModel.tareasBorradas.observe(viewLifecycleOwner) { lista ->
                    adaptador.submitList(lista)
                    tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Cargamos las completadas por defecto al entrar
        actualizarLista(0)

        // 4. Escuchar cuando tocas las pestañas
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { actualizarLista(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}