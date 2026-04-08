package com.luixard.studios.interfaz.finanzas

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luixard.studios.databinding.FinanzasItemHistorialSemanaBinding
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.datos.modelos.Transaccion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AdaptadorHistorialSemanas(
    private val onFetchDetails: (Int, (List<Transaccion>) -> Unit) -> Unit
) : RecyclerView.Adapter<AdaptadorHistorialSemanas.ViewHolder>() {

    private var semanas = emptyList<PresupuestoSemanal>()

    class ViewHolder(val binding: FinanzasItemHistorialSemanaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        FinanzasItemHistorialSemanaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val semana = semanas[position]
        val formatMoneda = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val formatFecha = SimpleDateFormat("dd MMM", Locale.getDefault())

        holder.binding.tvRangoFechas.text = "${formatFecha.format(semana.fecha_inicio!!)} - ${formatFecha.format(semana.fecha_fin!!)}"
        holder.binding.tvMetaItem.text = "Presupuesto: ${formatMoneda.format(semana.presupuesto_semanal_meta)}"

        // NUEVO: Pedimos los detalles de inmediato para llenar los totales (Gasto, Ingreso, Restante)
        onFetchDetails(semana.id_finanza!!) { transacciones ->
            // Llamamos a la función pero mantenemos el detalle oculto (View.GONE)
            actualizarTotalesCabecera(holder, transacciones, semana.presupuesto_semanal_meta)
        }

        holder.binding.layoutCabecera.setOnClickListener {
            val estaExpandido = holder.binding.layoutDetalleDia.visibility == View.VISIBLE
            if (estaExpandido) {
                holder.binding.layoutDetalleDia.visibility = View.GONE
                holder.binding.ivFlecha.animate().rotation(0f)
            } else {
                // Al expandir, construimos la lista visual de días
                onFetchDetails(semana.id_finanza!!) { transacciones ->
                    construirDetallePorDia(holder, transacciones, semana.presupuesto_semanal_meta)
                    holder.binding.layoutDetalleDia.visibility = View.VISIBLE
                    holder.binding.ivFlecha.animate().rotation(180f)
                }
            }
        }
    }
    private fun actualizarTotalesCabecera(holder: ViewHolder, lista: List<Transaccion>, meta: Double) {
        val formatMoneda = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val totalGasto = lista.filter { it.tipo_transaccion == "Gasto" }.sumOf { it.monto }
        val totalIngreso = lista.filter { it.tipo_transaccion == "Ingreso" }.sumOf { it.monto }
        val restante = (meta - totalGasto) + totalIngreso

        holder.binding.tvGastadoItem.text = "Gasto: ${formatMoneda.format(totalGasto)}"
        holder.binding.tvIngresoItem.text = "Ingreso: ${formatMoneda.format(totalIngreso)}"
        holder.binding.tvSobranteItem.text = "Restante: ${formatMoneda.format(restante)}"
    }

    private fun construirDetallePorDia(holder: ViewHolder, lista: List<Transaccion>, meta: Double) {
        val formatMoneda = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val formatDia = SimpleDateFormat("EEEE dd 'de' MMMM", Locale.getDefault())
        val context = holder.itemView.context

        var totalGastoSemana = 0.0
        var totalIngresoSemana = 0.0
        holder.binding.contenedorDias.removeAllViews()

        // Agrupar por día (YYYYMMDD) para procesar uno a uno
        val agrupadoPorDia = lista.groupBy { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(it.fecha_transaccion) }

        agrupadoPorDia.forEach { (_, transaccionesDia) ->
            val layoutDia = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 16)
            }

            val fechaDia = transaccionesDia.first().fecha_transaccion
            var gastoDelDia = 0.0

            val tvTituloDia = TextView(context).apply {
                text = formatDia.format(fechaDia).replaceFirstChar { it.uppercase() }
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                val colorDinamico = androidx.core.content.ContextCompat.getColor(context, com.luixard.studios.R.color.studios_cyan_titulo)
                setTextColor(colorDinamico)
            }
            layoutDia.addView(tvTituloDia)

            // Separar gastos e ingresos del día
            val gastos = transaccionesDia.filter { it.tipo_transaccion == "Gasto" }
            val ingresos = transaccionesDia.filter { it.tipo_transaccion == "Ingreso" }

            gastoDelDia = gastos.sumOf { it.monto }
            totalGastoSemana += gastoDelDia
            totalIngresoSemana += ingresos.sumOf { it.monto }

            val tvTotalGastoDia = TextView(context).apply {
                text = "Gasto total del día: ${formatMoneda.format(gastoDelDia)}"
                textSize = 12f
                setPadding(0, 4, 0, 4)
                setTypeface(null, Typeface.ITALIC)
            }
            layoutDia.addView(tvTotalGastoDia)

            // 3. Desglose de Gastos (Tamaño 12)
            gastos.forEach { gasto ->
                val tvDetalleGasto = TextView(context).apply {
                    text = " • ${gasto.nota_transaccion}: ${formatMoneda.format(gasto.monto)}"
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#FF5252"))
                }
                layoutDia.addView(tvDetalleGasto)
            }

            // 4. Desglose de Ingresos
            ingresos.forEach { ingreso ->
                val tvDetalleIngreso = TextView(context).apply {
                    text = " + Ingreso: ${formatMoneda.format(ingreso.monto)} (${ingreso.nota_transaccion})"
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
                layoutDia.addView(tvDetalleIngreso)
            }

            holder.binding.contenedorDias.addView(layoutDia)
        }

        // Actualizar el resumen de la tarjeta principal (Letras pequeñas ya definidas en el XML)
        holder.binding.tvGastadoItem.text = "Gasto: ${formatMoneda.format(totalGastoSemana)}"
        holder.binding.tvIngresoItem.text = "Ingreso: ${formatMoneda.format(totalIngresoSemana)}"
        val restante = (meta - totalGastoSemana) + totalIngresoSemana
        holder.binding.tvSobranteItem.text = "Restante: ${formatMoneda.format(restante)}"
    }

    override fun getItemCount() = semanas.size
    fun setSemanas(lista: List<PresupuestoSemanal>) {
        this.semanas = lista
        notifyDataSetChanged()
    }
}