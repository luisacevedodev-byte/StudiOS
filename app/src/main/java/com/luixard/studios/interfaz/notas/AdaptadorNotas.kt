package com.luixard.studios.interfaz.notas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Nota

class AdaptadorNotas(
    private val alEditarNota: (Nota) -> Unit,
    private val alBorrarNota: (Nota) -> Unit
) : ListAdapter<Nota, AdaptadorNotas.NotaViewHolder>(DiffCallback) {

    class NotaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardNota: MaterialCardView = view.findViewById(R.id.cardNota)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloNota)
        val tvFecha: TextView = view.findViewById(R.id.tvFechaNota)
        val btnBorrar: ImageButton = view.findViewById(R.id.btnBorrarNota)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotaViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.nota_item, parent, false)
        return NotaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: NotaViewHolder, position: Int) {
        val nota = getItem(position)

        // Asignamos los datos a la vista
        holder.tvTitulo.text = if (nota.titulo.isNotEmpty()) nota.titulo else "Nota sin título"
        holder.tvFecha.text = nota.fecha_creacion

        // Listeners
        holder.cardNota.setOnClickListener { alEditarNota(nota) }
        holder.btnBorrar.setOnClickListener { alBorrarNota(nota) }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Nota>() {
            override fun areItemsTheSame(oldItem: Nota, newItem: Nota) = oldItem.id_nota == newItem.id_nota
            override fun areContentsTheSame(oldItem: Nota, newItem: Nota) = oldItem == newItem
        }
    }
}