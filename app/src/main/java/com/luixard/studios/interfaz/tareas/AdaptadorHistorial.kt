package com.luixard.studios.interfaz.tareas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Tarea

class AdaptadorHistorial(
    private val alRestaurar: (Tarea) -> Unit,
    private val alEliminarPermanente: (Tarea) -> Unit
) : ListAdapter<Tarea, AdaptadorHistorial.HistorialViewHolder>(DiffCallback) {

    class HistorialViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titulo: TextView = view.findViewById(R.id.tvTituloHistorial)
        val fecha: TextView = view.findViewById(R.id.tvFechaHistorial)
        val btnRestaurar: ImageButton = view.findViewById(R.id.btnRestaurar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminarPermanente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_historial, parent, false)
        return HistorialViewHolder(vista)
    }

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) {
        val tarea = getItem(position)
        holder.titulo.text = tarea.titulo_tarea
        holder.fecha.text = tarea.fecha_entrega

        holder.btnRestaurar.setOnClickListener { alRestaurar(tarea) }
        holder.btnEliminar.setOnClickListener { alEliminarPermanente(tarea) }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Tarea>() {
            override fun areItemsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem.id_tarea == newItem.id_tarea
            override fun areContentsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem == newItem
        }
    }
}