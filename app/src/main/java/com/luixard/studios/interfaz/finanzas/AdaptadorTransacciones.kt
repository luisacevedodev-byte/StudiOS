package com.luixard.studios.interfaz.finanzas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luixard.studios.R
import com.luixard.studios.databinding.FinanzasItemTransaccionBinding
import com.luixard.studios.datos.modelos.Transaccion
import java.text.NumberFormat
import java.util.Locale

class AdaptadorTransacciones(
    private val onEdit: (Transaccion) -> Unit,
    private val onDelete: (Transaccion) -> Unit
) : ListAdapter<Transaccion, AdaptadorTransacciones.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: FinanzasItemTransaccionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(transaccion: Transaccion, onEdit: (Transaccion) -> Unit, onDelete: (Transaccion) -> Unit) {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            binding.tvCategoriaTransaccion.text = transaccion.nota_transaccion ?: "Sin nota"

            if (transaccion.tipo_transaccion == "Gasto") {
                binding.tvMontoTransaccion.text = "-${format.format(transaccion.monto)}"
                binding.tvMontoTransaccion.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                binding.ivIconoTransaccion.setImageResource(R.drawable.ic_remove_circle_24)
                binding.ivIconoTransaccion.setColorFilter(android.graphics.Color.parseColor("#FF5252"))
            } else {
                binding.tvMontoTransaccion.text = "+${format.format(transaccion.monto)}"
                binding.tvMontoTransaccion.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                binding.ivIconoTransaccion.setImageResource(R.drawable.ic_add_circle_24)
                binding.ivIconoTransaccion.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
            }

            // Listeners para editar y borrar
            binding.btnEditarTransaccion.setOnClickListener { onEdit(transaccion) }
            binding.btnBorrarTransaccion.setOnClickListener { onDelete(transaccion) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(FinanzasItemTransaccionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    object DiffCallback : DiffUtil.ItemCallback<Transaccion>() {
        override fun areItemsTheSame(oldItem: Transaccion, newItem: Transaccion) = oldItem.id_transaccion == newItem.id_transaccion
        override fun areContentsTheSame(oldItem: Transaccion, newItem: Transaccion) = oldItem == newItem
    }
}