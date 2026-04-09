package com.luixard.studios.interfaz.perfil

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.utilidades.MensajesUI

class PerfilFragment : Fragment() {

    private lateinit var tvNombreUsuario: TextView
    private lateinit var tvStatTareas: TextView
    private lateinit var tvStatAsistencia: TextView

    // Inicializamos el ViewModel
    private val viewModel: PerfilViewModel by viewModels {
        val app = requireActivity().application as com.luixard.studios.AplicacionStudiOS
        PerfilViewModelFactory(app.repositorioTareas, app.repositorioAuth)
    }

    private val PREFS_NAME = "StudiosPrefs"
    private val KEY_NOMBRE_USUARIO = "nombre_usuario"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNombreUsuario = view.findViewById(R.id.tvNombreUsuario)
        tvStatTareas = view.findViewById(R.id.tvStatTareas)
        tvStatAsistencia = view.findViewById(R.id.tvStatAsistencia)

        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)
        val cardVincular = view.findViewById<MaterialCardView>(R.id.cardVincularCuenta)
        val cardIniciarSesion = view.findViewById<MaterialCardView>(R.id.cardIniciarSesion)

        cargarNombreUsuario()

        // --- OBSERVADORES DE ESTADÍSTICAS ---
        viewModel.porcentajeTareas.observe(viewLifecycleOwner) { porcentaje ->
            tvStatTareas.text = "$porcentaje%"
        }

        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner) { porcentaje ->
            tvStatAsistencia.text = "$porcentaje%"
        }


        // --- LISTENERS ---
        btnEditarNombre.setOnClickListener { mostrarDialogoEditarNombre() }
        cardVincular.setOnClickListener { Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show() }
        cardIniciarSesion.setOnClickListener { Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show() }
    }

    private fun mostrarDialogoEditarNombre() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_editar_nombre, null)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNuevoNombre)

        etNombre.setText(tvNombreUsuario.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Nombre")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, _ ->
                val textoIngresado = etNombre.text.toString().trim()
                val nuevoNombre = textoIngresado.ifEmpty { "Usuario Nuevo" }

                guardarNombreUsuario(nuevoNombre)
                tvNombreUsuario.text = nuevoNombre

                if (textoIngresado.isEmpty()) {
                    MensajesUI.exito(requireActivity(), "Nombre restablecido")
                } else {
                    MensajesUI.exito(requireActivity(), "Nombre actualizado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarNombreUsuario(nombre: String) {
        val sharedPref = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(KEY_NOMBRE_USUARIO, nombre)
            apply()
        }
    }

    private fun cargarNombreUsuario() {
        val sharedPref = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nombreGuardado = sharedPref.getString(KEY_NOMBRE_USUARIO, "Usuario Nuevo")
        tvNombreUsuario.text = nombreGuardado
    }
}