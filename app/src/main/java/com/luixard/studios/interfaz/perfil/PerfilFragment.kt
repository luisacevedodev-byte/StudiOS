package com.luixard.studios.interfaz.perfil

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
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

    // Inicializamos el ViewModel pasando ambos repositorios para las estadísticas reales
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

        // Referencias a la interfaz
        tvNombreUsuario = view.findViewById(R.id.tvNombreUsuario)
        tvStatTareas = view.findViewById(R.id.tvStatTareas)
        tvStatAsistencia = view.findViewById(R.id.tvStatAsistencia)

        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)
        val cardVincular = view.findViewById<MaterialCardView>(R.id.cardVincularCuenta)
        val cardIniciarSesion = view.findViewById<MaterialCardView>(R.id.cardIniciarSesion)

        // Cargar identidad local del usuario
        cargarNombreUsuario()

        // --- OBSERVADORES DE ESTADÍSTICAS (Sprints 1 y 4) ---
        viewModel.porcentajeTareas.observe(viewLifecycleOwner) { porcentaje ->
            tvStatTareas.text = "$porcentaje%"
        }

        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner) { porcentaje ->
            tvStatAsistencia.text = "$porcentaje%"
        }

        // --- LISTENERS ---
        btnEditarNombre.setOnClickListener { mostrarDialogoEditarNombre() }

        // Listener para el flujo de vinculación (Sprint 4)
        cardVincular.setOnClickListener { mostrarDialogoVincular() }

        cardIniciarSesion.setOnClickListener {
            // TODO: Implementar lógica de recuperación (CU-15)
            MensajesUI.exito(requireActivity(), "Iniciar Sesión próximamente")
        }
    }

    // --- LÓGICA DE IDENTIDAD LOCAL ---

    private fun mostrarDialogoEditarNombre() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_editar_nombre, null)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNuevoNombre)

        etNombre.setText(tvNombreUsuario.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Nombre")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
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

    // --- FLUJO DE VINCULACIÓN DE CUENTA (CU-14) ---

    private fun mostrarDialogoVincular() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_opciones_vincular, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarDialogo)
        val btnGoogle = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGoogle)
        val btnCorreo = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCorreo)

        btnCerrar.setOnClickListener { dialog.dismiss() }
        btnGoogle.setOnClickListener {
            dialog.dismiss()
            MensajesUI.exito(requireActivity(), "Conectando con Google...")
        }
        btnCorreo.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoRegistroCorreo()
        }
        dialog.show()
    }

    private fun mostrarDialogoRegistroCorreo() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_registro_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        val btnRegistrar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegistrar)
        val etCorreo = view.findViewById<TextInputEditText>(R.id.etCorreo)
        val btnRegresar = view.findViewById<ImageButton>(R.id.btnRegresar)

        btnRegresar.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoVincular()
        }

        btnRegistrar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            if (correo.isNotEmpty()) {
                dialog.dismiss()
                mostrarDialogoVerificacion(correo)
            } else {
                MensajesUI.error(requireActivity(), "Ingresa un correo válido")
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoVerificacion(correoDestino: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_verificacion_codigo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        val tvCorreo = view.findViewById<TextView>(R.id.tvCorreoDestino)
        val btnVerificar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerificarCodigo)
        val etCodigo = view.findViewById<TextInputEditText>(R.id.etCodigoVerificacion)

        tvCorreo.text = correoDestino

        btnVerificar.setOnClickListener {
            if (etCodigo.text?.length == 5) {
                dialog.dismiss()
                MensajesUI.exito(requireActivity(), "Cuenta vinculada con éxito")
            } else {
                MensajesUI.error(requireActivity(), "Código incompleto")
            }
        }
        dialog.show()
    }
}