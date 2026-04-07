package com.luixard.studios.interfaz.tareas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
        val layoutPrincipal: LinearLayout = view.findViewById(R.id.layoutPrincipalHistorial)
        val layoutOpciones: LinearLayout = view.findViewById(R.id.layoutOpcionesHistorial)
        val titulo: TextView = view.findViewById(R.id.tvTituloHistorial)
        val fecha: TextView = view.findViewById(R.id.tvFechaHistorial)
        val ivFlecha: ImageView = view.findViewById(R.id.ivFlechaHistorial)
        val btnRestaurar: ImageButton = view.findViewById(R.id.btnRestaurar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminarPermanente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.tarea_item_historial, parent, false)
        return HistorialViewHolder(vista)
    }

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) {
        val tarea = getItem(position)
        holder.titulo.text = tarea.titulo_tarea

        val prefijo = if (tarea.es_completada) "Finalizada: " else "Borrada: "
        holder.fecha.text = prefijo + tarea.fecha_entrega

        holder.btnRestaurar.setOnClickListener { alRestaurar(tarea) }
        holder.btnEliminar.setOnClickListener { alEliminarPermanente(tarea) }

        // --- Animación de Despliegue Suave ---
        holder.layoutOpciones.visibility = View.GONE
        var estaExpandido = false

        holder.layoutPrincipal.setOnClickListener {
            estaExpandido = !estaExpandido
            holder.layoutOpciones.visibility = if (estaExpandido) View.VISIBLE else View.GONE
            // Gira la flecha 180 grados suavemente (350 ms = más lento)
            holder.ivFlecha.animate().rotation(if (estaExpandido) 180f else 0f).setDuration(350).start()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Tarea>() {
            override fun areItemsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem.id_tarea == newItem.id_tarea
            override fun areContentsTheSame(oldItem: Tarea, newItem: Tarea) = oldItem == newItem
        }
    }
}