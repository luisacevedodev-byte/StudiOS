package com.luixard.studios.interfaz.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        // Referencias de la UI - Contadores y Finanzas
        val tvTareas = view.findViewById<TextView>(R.id.tvCountTareas)
        val tvUrgentes = view.findViewById<TextView>(R.id.tvCountUrgentes)
        val tvNotas = view.findViewById<TextView>(R.id.tvCountNotas)
        val tvSaldo = view.findViewById<TextView>(R.id.tvSaldoSemanal)
        val tvPresupuestoTotal = view.findViewById<TextView>(R.id.tvPresupuestoTotal)
        val tvDetalleFinanzas = view.findViewById<TextView>(R.id.tvDetalleFinanzas)
        val pbFinanzas = view.findViewById<ProgressBar>(R.id.pbFinanzas)

        // Referencias de la UI - Próxima Entrega
        val llProximaEntregaDetalle = view.findViewById<View>(R.id.llProximaEntregaDetalle)
        val vUrgenciaColor = view.findViewById<View>(R.id.vUrgenciaColor)
        val tvProximaEntregaTitulo = view.findViewById<TextView>(R.id.tvProximaEntregaTitulo)
        val tvProximaEntregaFecha = view.findViewById<TextView>(R.id.tvProximaEntregaFecha)
        val tvProximaEntregaVacio = view.findViewById<TextView>(R.id.tvProximaEntregaVacio)

        // Referencias de la UI - Notas Recientes
        val llNotaRecienteDetalle = view.findViewById<View>(R.id.llNotaRecienteDetalle)
        val tvNotaRecienteTitulo = view.findViewById<TextView>(R.id.tvNotaRecienteTitulo)
        val tvNotaRecienteFecha = view.findViewById<TextView>(R.id.tvNotaRecienteFecha)
        val tvNotaRecienteVacio = view.findViewById<TextView>(R.id.tvNotaRecienteVacio)

        // --- OBSERVADORES ---

        // Tareas y Próxima Entrega Detallada
        viewModel.tareasPendientes.observe(viewLifecycleOwner) { tareas ->
            tvTareas.text = tareas.size.toString()
            tvUrgentes.text = viewModel.obtenerConteoUrgentes(tareas).toString()

            if (tareas.isNotEmpty()) {
                // Tarea más reciente (la primera de la lista)
                val tarea = tareas[0]
                tvProximaEntregaTitulo.text = tarea.titulo_tarea
                tvProximaEntregaFecha.text = "Entrega: ${tarea.fecha_entrega}" // Aquí va tu fecha

                // Lógica de color de urgencia
                val color = when (tarea.id_prioridad?.lowercase()) {
                    "alta" -> android.graphics.Color.RED
                    "media" -> android.graphics.Color.parseColor("#FFA500")
                    else -> android.graphics.Color.parseColor("#00bb2d")
                }
                vUrgenciaColor.setBackgroundColor(color)

                // Mostrar detalle, ocultar vacío
                llProximaEntregaDetalle.visibility = View.VISIBLE
                tvProximaEntregaVacio.visibility = View.GONE
            }
        }

        // Notas y Nota Reciente Detallada
        viewModel.todasLasNotas.observe(viewLifecycleOwner) { notas ->
            tvNotas.text = notas.size.toString()

            if (notas.isNotEmpty()) {
                // Última nota creada
                val nota = notas.last()
                tvNotaRecienteTitulo.text = nota.titulo

                tvNotaRecienteFecha.text = "Creada: ${nota.fecha_creacion}"

                // Mostrar detalle, ocultar vacío
                llNotaRecienteDetalle.visibility = View.VISIBLE
                tvNotaRecienteVacio.visibility = View.GONE
            } else {
                // Ocultar detalle, mostrar vacío
                llNotaRecienteDetalle.visibility = View.GONE
                tvNotaRecienteVacio.visibility = View.VISIBLE
            }
        }

        // Presupuesto y Finanzas (Se mantiene igual)
        viewModel.presupuestoActual.observe(viewLifecycleOwner) { presupuesto ->
            val meta = presupuesto?.presupuesto_semanal_meta ?: 0.0
            tvPresupuestoTotal.text = "$${String.format("%.2f", meta)}"

            viewModel.transaccionesSemanales.observe(viewLifecycleOwner) { transacciones ->
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