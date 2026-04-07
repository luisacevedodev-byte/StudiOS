package com.luixard.studios.interfaz.tareas

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Tarea

class AdaptadorTareas(
    private val alCompletar: (Tarea, CheckBox) -> Unit,
    private val alAbrirDetalles: (Tarea) -> Unit
) : ListAdapter<Tarea, AdaptadorTareas.TareaViewHolder>(DiffCallback) {

    class TareaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutPrincipal: LinearLayout = view.findViewById(R.id.layoutPrincipalItem)
        val titulo: TextView = view.findViewById(R.id.tvTituloItem)
        val fecha: TextView = view.findViewById(R.id.tvFechaItem)
        val cardPrioridad: MaterialCardView = view.findViewById(R.id.cardPrioridad)
        val checkbox: CheckBox = view.findViewById(R.id.cbCompletar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TareaViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.tarea_item_tarea, parent, false)
        return TareaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: TareaViewHolder, position: Int) {
        val tarea = getItem(position)
        holder.titulo.text = tarea.titulo_tarea
        holder.fecha.text = "Entrega: " + tarea.fecha_entrega

        when(tarea.id_prioridad) {
            "ALTA" -> holder.cardPrioridad.setCardBackgroundColor(Color.parseColor("#D32F2F"))
            "MEDIA" -> holder.cardPrioridad.setCardBackgroundColor(Color.parseColor("#F57F17"))
            "BAJA" -> holder.cardPrioridad.setCardBackgroundColor(Color.parseColor("#388E3C"))
        }

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = tarea.es_completada

        holder.checkbox.setOnClickListener { alCompletar(tarea, holder.checkbox) }

        // Al tocar cualquier parte de la tarea, se abren los detalles
        holder.layoutPrincipal.setOnClickListener { alAbrirDetalles(tarea) }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Tarea>() {
            override fun areItemsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem.id_tarea == newItem.id_tarea
            override fun areContentsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem == newItem
        }
    }
}