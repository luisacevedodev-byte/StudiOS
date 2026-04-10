package com.luixard.studios.interfaz.perfil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
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
    private lateinit var tvEmailSubtitulo: TextView
    private lateinit var tvStatTareas: TextView
    private lateinit var tvStatAsistencia: TextView
    private lateinit var cardVincular: MaterialCardView
    private lateinit var cardIniciarSesion: MaterialCardView
    private lateinit var cardCerrarSesion: MaterialCardView
    private lateinit var layoutCargando: LinearLayout

    private val viewModel: PerfilViewModel by viewModels {
        val app = requireActivity().application as AplicacionStudiOS
        PerfilViewModelFactory(
            app.repositorioTareas,
            app.repositorioAuth,
            app.repositorioFinanzas,
            app.repositorioNotas
        )
    }

    private val PREFS_NAME = "StudiosPrefs"
    private val KEY_NOMBRE_USUARIO = "nombre_usuario"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_perfil, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Enlaces de vistas
        tvNombreUsuario  = view.findViewById(R.id.tvNombreUsuario)
        tvEmailSubtitulo = view.findViewById(R.id.tvEmailSubtitulo)
        tvStatTareas     = view.findViewById(R.id.tvStatTareas)
        tvStatAsistencia = view.findViewById(R.id.tvStatAsistencia)
        cardVincular     = view.findViewById(R.id.cardVincularCuenta)
        cardIniciarSesion = view.findViewById(R.id.cardIniciarSesion)
        cardCerrarSesion = view.findViewById(R.id.cardCerrarSesion)
        layoutCargando   = view.findViewById(R.id.layoutCargandoRespaldo)
        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)

        // 2. Carga local inmediata (antes de que llegue la nube)
        cargarNombreUsuario()

        // 3. Observadores
        setupObservers()

        // 4. Listeners
        btnEditarNombre.setOnClickListener { mostrarDialogoEditarNombre() }
        cardVincular.setOnClickListener    { mostrarDialogoMenuAuth("Vincular Cuenta") }
        cardIniciarSesion.setOnClickListener { mostrarDialogoMenuAuth("Iniciar Sesión") }
        cardCerrarSesion.setOnClickListener  { viewModel.cerrarSesion() }

        // 5. Argumento desde el Drawer
        val abrirVincular = arguments?.getBoolean("abrirVincularDirecto") ?: false
        if (abrirVincular) {
            arguments?.putBoolean("abrirVincularDirecto", false)
            mostrarDialogoMenuAuth("Vincular Cuenta")
        }

        // 6. Verificar sesión en la nube
        viewModel.verificarSesion()
    }

    // -------------------------------------------------------------------------
    // NOMBRE LOCAL (SharedPreferences)
    // -------------------------------------------------------------------------

    private fun cargarNombreUsuario() {
        val sp = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tvNombreUsuario.text = sp.getString(KEY_NOMBRE_USUARIO, "Usuario Nuevo")
    }

    private fun guardarNombreUsuario(nombre: String) {
        requireActivity()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOMBRE_USUARIO, nombre)
            .apply()
    }

    private fun mostrarDialogoEditarNombre() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_editar_nombre, null)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNuevoNombre)
        etNombre?.setText(tvNombreUsuario.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Nombre")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = etNombre?.text.toString().trim()
                if (nuevoNombre.isNotEmpty()) {
                    guardarNombreUsuario(nuevoNombre)
                    tvNombreUsuario.text = nuevoNombre
                    MensajesUI.exito(requireActivity(), "Nombre actualizado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // OBSERVADORES
    // -------------------------------------------------------------------------

    private fun setupObservers() {
        // Estadísticas
        viewModel.porcentajeTareas.observe(viewLifecycleOwner) { tvStatTareas.text = "$it%" }
        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner) { tvStatAsistencia.text = "$it%" }

        // Nombre (anti-parpadeo: solo sobreescribimos si el ViewModel tiene algo)
        viewModel.nombreUsuarioDisplay.observe(viewLifecycleOwner) { nombre ->
            if (!nombre.isNullOrEmpty()) tvNombreUsuario.text = nombre
        }

        // Correo
        viewModel.correoUsuario.observe(viewLifecycleOwner) { correo ->
            if (!correo.isNullOrEmpty()) tvEmailSubtitulo.text = correo
        }

        // Estado de sesión
        viewModel.usuarioLogueado.observe(viewLifecycleOwner) { logueado ->
            cardVincular.visibility      = if (logueado) View.GONE else View.VISIBLE
            cardIniciarSesion.visibility = if (logueado) View.GONE else View.VISIBLE
            cardCerrarSesion.visibility  = if (logueado) View.VISIBLE else View.GONE

            if (!logueado) {
                cargarNombreUsuario()
                tvEmailSubtitulo.text = "Usuario no registrado"
            }
        }

        // Barra de carga
        viewModel.estaCargandoRespaldo.observe(viewLifecycleOwner) { cargando ->
            layoutCargando.visibility = if (cargando) View.VISIBLE else View.GONE
        }
    }

    // -------------------------------------------------------------------------
    // DIÁLOGOS DE AUTENTICACIÓN
    // -------------------------------------------------------------------------

    private fun mostrarDialogoMenuAuth(titulo: String) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_opciones_vincular, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<ImageButton>(R.id.btnCerrarDialogo)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.tvTituloVincular)?.text = titulo
        view.findViewById<MaterialButton>(R.id.btnCorreo).setOnClickListener {
            dialog.dismiss()
            if (titulo == "Iniciar Sesión") mostrarDialogoLogin() else mostrarDialogoRegistro()
        }
        dialog.show()
    }

    private fun mostrarDialogoRegistro() {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_registro_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<ImageButton>(R.id.btnCerrarRegistro)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarRegistro)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoMenuAuth("Vincular Cuenta")
        }

        val etNom  = view.findViewById<TextInputEditText>(R.id.etNombreRegistro)
        val etApe  = view.findViewById<TextInputEditText>(R.id.etApellidoRegistro)
        val etCor  = view.findViewById<TextInputEditText>(R.id.etCorreo)
        val etPas  = view.findViewById<TextInputEditText>(R.id.etContrasena)
        val etConf = view.findViewById<TextInputEditText>(R.id.etConfirmarContrasena)

        view.findViewById<MaterialButton>(R.id.btnRegistrar).setOnClickListener {
            val nom = etNom?.text.toString().trim()
            val ape = etApe?.text.toString().trim()
            val cor = etCor?.text.toString().trim()
            val pas = etPas?.text.toString()

            if (nom.isNotEmpty() && pas == etConf?.text.toString() && pas.length >= 6) {
                viewModel.generarCodigoVerificacion()
                viewModel.enviarEmail(viewModel.prepararDatosEmail(nom, cor))
                dialog.dismiss()
                mostrarDialogoVerificacion(cor, pas, nom, ape, "registro")
            } else {
                MensajesUI.error(requireActivity(), "Datos inválidos o contraseña muy corta")
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoVerificacion(
        cor: String, pas: String, nom: String, ape: String, tipo: String
    ) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_verificacion_codigo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<ImageButton>(R.id.btnCerrarVerif)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarVerif)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoRegistro()
        }
        view.findViewById<TextView>(R.id.tvCorreoDestino)?.text = cor

        view.findViewById<MaterialButton>(R.id.btnVerificarCodigo).setOnClickListener {
            val codigo = view.findViewById<TextInputEditText>(R.id.etCodigoVerificacion)
                .text.toString().trim()

            if (codigo == viewModel.codigoGenerado) {
                if (tipo == "registro") {
                    FirebaseAuth.getInstance()
                        .createUserWithEmailAndPassword(cor, pas)
                        .addOnSuccessListener {
                            guardarNombreUsuario("$nom $ape")
                            viewModel.actualizarNombreInmediato(nom, ape)
                            viewModel.iniciarRespaldoTotal(nom, ape)
                            dialog.dismiss()
                            MensajesUI.exito(requireActivity(), "¡Cuenta vinculada con éxito!")
                        }
                        .addOnFailureListener {
                            MensajesUI.error(requireActivity(), "Error al crear cuenta: ${it.message}")
                        }
                } else {
                    dialog.dismiss()
                    MensajesUI.exito(requireActivity(), "Código verificado")
                }
            } else {
                MensajesUI.error(requireActivity(), "El código no coincide")
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoLogin() {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_login_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        view.findViewById<ImageButton>(R.id.btnCerrarLogin)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarLogin)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoMenuAuth("Iniciar Sesión")
        }

        view.findViewById<MaterialButton>(R.id.btnEntrar).setOnClickListener {
            val cor = view.findViewById<TextInputEditText>(R.id.etCorreoLogin).text.toString().trim()
            val pas = view.findViewById<TextInputEditText>(R.id.etContrasenaLogin).text.toString()

            if (cor.isNotEmpty() && pas.isNotEmpty()) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(cor, pas)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: ""
                        // Captura del nombre desde Firestore post-login
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("usuarios").document(uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    val nom = doc.getString("perfil.nombre") ?: ""
                                    val ape = doc.getString("perfil.apellido") ?: ""
                                    if (nom.isNotEmpty()) {
                                        guardarNombreUsuario("$nom $ape")
                                        viewModel.actualizarNombreInmediato(nom, ape)
                                    }
                                }
                                dialog.dismiss()
                                viewModel.verificarSesion()
                                MensajesUI.exito(requireActivity(), "¡Bienvenido de nuevo!")
                            }
                    }
                    .addOnFailureListener { e ->
                        val exception = e as? com.google.firebase.auth.FirebaseAuthException
                        val errorMsg = when (exception?.errorCode) {
                            "ERROR_USER_NOT_FOUND",
                            "ERROR_INVALID_CREDENTIAL" ->
                                "No existe una cuenta con este correo. Por favor, regístrate primero."
                            "ERROR_WRONG_PASSWORD" ->
                                "Credenciales incorrectas. Inténtalo de nuevo."
                            else -> "Correo o contraseña incorrectos"
                        }
                        MensajesUI.error(requireActivity(), errorMsg)
                    }
            }
        }
        dialog.show()
    }
}