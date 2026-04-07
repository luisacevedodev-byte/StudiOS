package com.luixard.studios.interfaz.finanzas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luixard.studios.databinding.FinanzasItemCategoriaBinding
import com.luixard.studios.datos.modelos.CategoriaGasto

class AdaptadorCategorias(
    private val onDelete: (CategoriaGasto) -> Unit
) : ListAdapter<CategoriaGasto, AdaptadorCategorias.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: FinanzasItemCategoriaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(categoria: CategoriaGasto, onDelete: (CategoriaGasto) -> Unit) {
            binding.tvNombreCategoria.text = categoria.nombre_categoria

            binding.btnBorrarCategoria.visibility = View.VISIBLE

            binding.tvDefault.visibility = if (categoria.es_predeterminada) View.VISIBLE else View.GONE

            binding.btnBorrarCategoria.setOnClickListener { onDelete(categoria) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(FinanzasItemCategoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    object DiffCallback : DiffUtil.ItemCallback<CategoriaGasto>() {
        override fun areItemsTheSame(oldItem: CategoriaGasto, newItem: CategoriaGasto) = oldItem.id_categoria == newItem.id_categoria
        override fun areContentsTheSame(oldItem: CategoriaGasto, newItem: CategoriaGasto) = oldItem == newItem
    }
}