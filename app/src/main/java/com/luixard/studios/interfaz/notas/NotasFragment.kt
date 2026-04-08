package com.luixard.studios.interfaz.notas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luixard.studios.R
import com.luixard.studios.datos.modelos.Nota
import com.luixard.studios.utilidades.MensajesUI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotasFragment : Fragment() {

    private val viewModel: NotasViewModel by viewModels()
    private lateinit var adaptador: AdaptadorNotas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.nota_pantalla_principal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvNotas = view.findViewById<RecyclerView>(R.id.rvListaNotas)
        val layoutVacio = view.findViewById<View>(R.id.layoutNotasVacias)
        val fabAgregar = view.findViewById<FloatingActionButton>(R.id.fabAgregarNota)
        val tvSubtitulo = view.findViewById<TextView>(R.id.tvSubtituloNotas) // <-- Buscamos el subtítulo

        adaptador = AdaptadorNotas(
            alEditarNota = { nota -> mostrarDialogoNota(nota) },
            alBorrarNota = { nota ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Nota")
                    .setMessage("¿Estás seguro de que deseas eliminar esta nota de forma permanente?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.borrarNota(nota)
                        MensajesUI.error(requireActivity(), "Nota eliminada")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        rvNotas.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        rvNotas.adapter = adaptador

        // Observamos los cambios en la base de datos
        viewModel.todasLasNotas.observe(viewLifecycleOwner) { listaNotas ->
            adaptador.submitList(listaNotas)

            // --- LÓGICA DEL CONTADOR DE NOTAS ---
            val cantidad = listaNotas.size
            if (cantidad == 1) {
                tvSubtitulo.text = "1 nota guardada"
            } else {
                tvSubtitulo.text = "$cantidad notas guardadas"
            }

            // --- LÓGICA DEL ESTADO VACÍO ---
            if (listaNotas.isEmpty()) {
                layoutVacio.visibility = View.VISIBLE
                rvNotas.visibility = View.GONE
            } else {
                layoutVacio.visibility = View.GONE
                rvNotas.visibility = View.VISIBLE
            }
        }

        fabAgregar.setOnClickListener {
            mostrarDialogoNota(null)
        }
    }

    private fun mostrarDialogoNota(notaExistente: Nota?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.nota_dialogo_editar, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setBackground(resources.getDrawable(android.R.color.transparent, null))
            .create()

        val tvTituloDialogo = dialogView.findViewById<TextView>(R.id.tvTituloDialogoNota)
        val etTitulo = dialogView.findViewById<TextInputEditText>(R.id.etTituloNota)

        // --- BUSCAMOS EL LAYOUT PARA PODER PINTAR EL ERROR ---
        val tilContenido = dialogView.findViewById<TextInputLayout>(R.id.tilContenidoNota)
        val etContenido = dialogView.findViewById<TextInputEditText>(R.id.etContenidoNota)

        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarNota)
        val btnGuardar = dialogView.findViewById<MaterialButton>(R.id.btnGuardarNota)

        if (notaExistente != null) {
            tvTituloDialogo.text = "Editar Nota"
            etTitulo.setText(notaExistente.titulo)
            etContenido.setText(notaExistente.contenido)
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val tituloStr = etTitulo.text.toString().trim()
            val contenidoStr = etContenido.text.toString().trim()

            // --- VALIDACIÓN DE ERROR VISUAL ---
            if (contenidoStr.isEmpty()) {
                // Pintamos la caja de rojo con el error
                tilContenido.error = "La nota no puede estar vacía"
                return@setOnClickListener
            } else {
                tilContenido.error = null // Limpiamos el error por si acaso
            }

            val fechaActual = SimpleDateFormat("dd MMM - hh:mm a", Locale.getDefault()).format(Date())

            if (notaExistente == null) {
                val nuevaNota = Nota(
                    titulo = tituloStr,
                    contenido = contenidoStr,
                    fecha_creacion = fechaActual
                )
                viewModel.guardarNota(nuevaNota)
                MensajesUI.exito(requireActivity(), "Nota creada")
            } else {
                val notaActualizada = notaExistente.copy(
                    titulo = tituloStr,
                    contenido = contenidoStr,
                    fecha_creacion = fechaActual
                )
                viewModel.actualizarNota(notaActualizada)
                MensajesUI.exito(requireActivity(), "Nota actualizada")
            }

            dialog.dismiss()
        }

        dialog.show()
    }
}