package com.luixard.studios.interfaz.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.luixard.studios.R

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias principales
        val tvTareas = view.findViewById<TextView>(R.id.tvCountTareas)
        val tvUrgentes = view.findViewById<TextView>(R.id.tvCountUrgentes)
        val tvNotas = view.findViewById<TextView>(R.id.tvCountNotas)
        val tvSaldo = view.findViewById<TextView>(R.id.tvSaldoSemanal)
        val tvPresupuestoTotal = view.findViewById<TextView>(R.id.tvPresupuestoTotal)
        val tvDetalleFinanzas = view.findViewById<TextView>(R.id.tvDetalleFinanzas)
        val pbFinanzas = view.findViewById<ProgressBar>(R.id.pbFinanzas)

        // Contenedores dinámicos
        val containerTareas = view.findViewById<LinearLayout>(R.id.containerTareasDashboard)
        val containerNotas = view.findViewById<LinearLayout>(R.id.containerNotasDashboard)
        val tvTareasVacio = view.findViewById<TextView>(R.id.tvProximaEntregaVacio)
        val tvNotasVacio = view.findViewById<TextView>(R.id.tvNotaRecienteVacio)

        // --- OBSERVADOR TAREAS (3 Más Próximas) ---
        viewModel.tareasPendientes.observe(viewLifecycleOwner) { tareas ->
            tvTareas.text = tareas.size.toString()
            tvUrgentes.text = viewModel.obtenerConteoUrgentes(tareas).toString()
            containerTareas.removeAllViews() // Limpiar para actualizar

            if (tareas.isNotEmpty()) {
                tvTareasVacio.visibility = View.GONE
                // Ordenar por fecha y tomar 3
                val topTareas = tareas.sortedBy { it.fecha_entrega }.take(3)

                topTareas.forEach { tarea ->
                    val fila = LayoutInflater.from(context).inflate(R.layout.item_dashboard_fila, containerTareas, false)
                    fila.findViewById<TextView>(R.id.tvFilaTitulo).text = tarea.titulo_tarea
                    fila.findViewById<TextView>(R.id.tvFilaSubtitulo).text = "Entrega: ${tarea.fecha_entrega}"

                    // Lógica de círculo de urgencia
                    val color = when(tarea.id_prioridad?.lowercase()) {
                        "alta" -> android.graphics.Color.RED
                        "media" -> android.graphics.Color.parseColor("#FFA500")
                        else -> ContextCompat.getColor(requireContext(), R.color.studios_cyan_titulo)
                    }
                    fila.findViewById<com.google.android.material.card.MaterialCardView>(R.id.vFilaColor).setCardBackgroundColor(color)
                    containerTareas.addView(fila)
                }
            } else { tvTareasVacio.visibility = View.VISIBLE }
        }

        // --- OBSERVADOR NOTAS (3 Más Recientes) ---
        viewModel.todasLasNotas.observe(viewLifecycleOwner) { notas ->
            tvNotas.text = notas.size.toString()
            containerNotas.removeAllViews()

            if (notas.isNotEmpty()) {
                tvNotasVacio.visibility = View.GONE
                // Invertir orden para que la nueva aparezca primero y tomar 3
                val topNotas = notas.reversed().take(3)

                topNotas.forEach { nota ->
                    val fila = LayoutInflater.from(context).inflate(R.layout.item_dashboard_fila, containerNotas, false)
                    fila.findViewById<TextView>(R.id.tvFilaTitulo).text = nota.titulo
                    fila.findViewById<TextView>(R.id.tvFilaSubtitulo).text = "Creada: ${nota.fecha_creacion}"
                    // Ocultar círculo de color para notas
                    fila.findViewById<View>(R.id.vFilaColor).visibility = View.GONE
                    containerNotas.addView(fila)
                }
            } else { tvNotasVacio.visibility = View.VISIBLE }
        }

        // --- LÓGICA DE FINANZAS ---
        viewModel.presupuestoActual.observe(viewLifecycleOwner) { presupuesto ->
            val meta = presupuesto?.presupuesto_semanal_meta ?: 0.0
            tvPresupuestoTotal.text = "$${String.format("%.2f", meta)}"

            viewModel.transaccionesSemanales.observe(viewLifecycleOwner) { transacciones ->
                // Nombres corregidos según tu Repositorio
                val gastos = transacciones.filter { it.tipo_transaccion == "Gasto" }.sumOf { it.monto }
                val ingresos = transacciones.filter { it.tipo_transaccion == "Ingreso" }.sumOf { it.monto }
                val saldoRestante = (meta + ingresos) - gastos

                tvSaldo.text = "$${String.format("%.2f", saldoRestante)}"
                tvDetalleFinanzas.text = "Gastado: $${String.format("%.2f", gastos)} | Restante: $${String.format("%.2f", saldoRestante)}"

                if (meta > 0) {
                    val progreso = ((gastos / (meta + ingresos)) * 100).toInt()
                    pbFinanzas.progress = progreso.coerceAtMost(100)
                }
            }
        }
    }
}