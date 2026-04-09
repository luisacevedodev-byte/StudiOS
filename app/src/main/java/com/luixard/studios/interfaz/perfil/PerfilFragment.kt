package com.luixard.studios.interfaz.perfil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.R
import com.luixard.studios.utilidades.MensajesUI

class PerfilFragment : Fragment() {

    private lateinit var tvNombreUsuario: TextView
    private lateinit var tvStatTareas: TextView
    private lateinit var tvStatAsistencia: TextView
    private lateinit var cardVincular: MaterialCardView
    private lateinit var cardIniciarSesion: MaterialCardView
    private lateinit var cardCerrarSesion: MaterialCardView
    private lateinit var layoutCargando: LinearLayout

    private val viewModel: PerfilViewModel by viewModels {
        val app = requireActivity().application as AplicacionStudiOS
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
        cardVincular = view.findViewById(R.id.cardVincularCuenta)
        cardIniciarSesion = view.findViewById(R.id.cardIniciarSesion)
        cardCerrarSesion = view.findViewById(R.id.cardCerrarSesion)
        layoutCargando = view.findViewById(R.id.layoutCargandoRespaldo)
        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)

        // --- OBSERVADORES ---

        viewModel.porcentajeTareas.observe(viewLifecycleOwner) { tvStatTareas.text = "$it%" }
        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner) { tvStatAsistencia.text = "$it%" }

        // Observador de Sesión: Cambia la UI según si hay usuario o no
        viewModel.usuarioLogueado.observe(viewLifecycleOwner) { estaLogueado ->
            if (estaLogueado) {
                cardVincular.visibility = View.GONE
                cardIniciarSesion.visibility = View.GONE
                cardCerrarSesion.visibility = View.VISIBLE
                tvNombreUsuario.text = viewModel.correoUsuario.value
            } else {
                cardVincular.visibility = View.VISIBLE
                cardIniciarSesion.visibility = View.VISIBLE
                cardCerrarSesion.visibility = View.GONE
                cargarNombreUsuario() // Muestra el nombre local si no hay sesión
            }
        }

        // Observador de Carga (Backup)
        viewModel.estaCargandoRespaldo.observe(viewLifecycleOwner) { cargando ->
            layoutCargando.visibility = if (cargando) View.VISIBLE else View.GONE
            // Deshabilitar clics mientras se hace el backup
            cardCerrarSesion.isEnabled = !cargando
        }

        // --- LISTENERS ---

        btnEditarNombre.setOnClickListener { mostrarDialogoEditarNombre() }
        cardVincular.setOnClickListener { mostrarDialogoMenuAuth("Vincular Cuenta") }
        cardIniciarSesion.setOnClickListener { mostrarDialogoMenuAuth("Iniciar Sesión") }
        cardCerrarSesion.setOnClickListener { viewModel.cerrarSesion() }

        viewModel.verificarSesion()
    }

    // --- FLUJO DE AUTENTICACIÓN ---

    private fun mostrarDialogoMenuAuth(titulo: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_opciones_vincular, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<TextView>(R.id.tvTituloVincular)?.text = titulo
        view.findViewById<ImageButton>(R.id.btnCerrarDialogo).setOnClickListener { dialog.dismiss() }

        view.findViewById<MaterialButton>(R.id.btnCorreo).setOnClickListener {
            dialog.dismiss()
            if (titulo == "Iniciar Sesión") mostrarDialogoLogin() else mostrarDialogoRegistroCorreo()
        }
        dialog.show()
    }

    private fun mostrarDialogoLogin() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_login_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        // --- ENLACE DE BOTONES DE NAVEGACIÓN ---
        val btnRegresar = view.findViewById<ImageButton>(R.id.btnRegresarLogin)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarLogin)
        val btnEntrar = view.findViewById<MaterialButton>(R.id.btnEntrar)
        val tvOlvide = view.findViewById<TextView>(R.id.tvOlvidePassword)
        val etCorreo = view.findViewById<TextInputEditText>(R.id.etCorreoLogin)
        val etPass = view.findViewById<TextInputEditText>(R.id.etContrasenaLogin)

        // Configurar clics
        btnRegresar.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoMenuAuth("Iniciar Sesión")
        }

        btnCerrar.setOnClickListener { dialog.dismiss() }

        tvOlvide.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoRecuperarPassword()
        }

        btnEntrar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val pass = etPass.text.toString()
            if (correo.isNotEmpty() && pass.isNotEmpty()) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(correo, pass)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            MensajesUI.exito(requireActivity(), "¡Bienvenido de nuevo!")
                            viewModel.iniciarRespaldoEnNube()
                        } else {
                            MensajesUI.error(requireActivity(), "Error: ${task.exception?.message}")
                        }
                    }
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoRegistroCorreo() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_registro_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        val etCorreo = view.findViewById<TextInputEditText>(R.id.etCorreo)
        val etPass = view.findViewById<TextInputEditText>(R.id.etContrasena)
        val etConf = view.findViewById<TextInputEditText>(R.id.etConfirmarContrasena)

        view.findViewById<MaterialButton>(R.id.btnRegistrar).setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val pass = etPass.text.toString()
            if (correo.isNotEmpty() && pass == etConf.text.toString() && pass.length >= 6) {
                viewModel.generarCodigoVerificacion()
                viewModel.enviarEmail(viewModel.prepararDatosEmail(tvNombreUsuario.text.toString(), correo))
                dialog.dismiss()
                mostrarDialogoVerificacion(correo, pass, "registro")
            } else {
                MensajesUI.error(requireActivity(), "Verifica los datos (mínimo 6 caracteres)")
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoRecuperarPassword() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_recuperar_password, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        val etCorreo = view.findViewById<TextInputEditText>(R.id.etCorreoRecuperar)

        view.findViewById<MaterialButton>(R.id.btnEnviarCodigoRecup).setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            if (correo.isNotEmpty()) {
                viewModel.generarCodigoVerificacion()
                viewModel.enviarEmail(viewModel.prepararDatosEmail("Usuario StudiOS", correo))
                dialog.dismiss()
                mostrarDialogoVerificacion(correo, "", "recuperacion")
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoVerificacion(correo: String, pass: String, tipo: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_verificacion_codigo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<TextView>(R.id.tvCorreoDestino).text = correo
        val etCodigo = view.findViewById<TextInputEditText>(R.id.etCodigoVerificacion)

        view.findViewById<MaterialButton>(R.id.btnVerificarCodigo).setOnClickListener {
            if (etCodigo.text.toString().trim() == viewModel.codigoGenerado) {
                dialog.dismiss()
                if (tipo == "registro") {
                    crearCuentaFirebase(correo, pass)
                } else {
                    MensajesUI.exito(requireActivity(), "Código verificado")
                    // TODO: Abrir diálogo para nueva contraseña
                }
            } else {
                MensajesUI.error(requireActivity(), "Código incorrecto")
            }
        }
        dialog.show()
    }

    private fun crearCuentaFirebase(correo: String, pass: String) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(correo, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    MensajesUI.exito(requireActivity(), "Cuenta vinculada con éxito")
                    viewModel.iniciarRespaldoEnNube()
                } else {
                    MensajesUI.error(requireActivity(), "Error: ${task.exception?.message}")
                }
            }
    }

    // --- GESTIÓN LOCAL ---

    private fun mostrarDialogoEditarNombre() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialogo_editar_nombre, null)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNuevoNombre)
        etNombre.setText(tvNombreUsuario.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Nombre")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevo = etNombre.text.toString().trim().ifEmpty { "Usuario Nuevo" }
                guardarNombreUsuario(nuevo)
                if (!viewModel.usuarioLogueado.value!!) tvNombreUsuario.text = nuevo
                MensajesUI.exito(requireActivity(), "Nombre actualizado")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun guardarNombreUsuario(nombre: String) {
        requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOMBRE_USUARIO, nombre).apply()
    }

    private fun cargarNombreUsuario() {
        val nombre = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOMBRE_USUARIO, "Usuario Nuevo")
        tvNombreUsuario.text = nombre
    }
}