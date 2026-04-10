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
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.R
import com.luixard.studios.datos.sync.SyncManager
import com.luixard.studios.utilidades.MensajesUI

class PerfilFragment : Fragment() {

    private lateinit var tvNombreUsuario:   TextView
    private lateinit var tvEmailSubtitulo:  TextView
    private lateinit var tvStatTareas:      TextView
    private lateinit var tvStatAsistencia:  TextView
    private lateinit var cardVincular:      MaterialCardView
    private lateinit var cardIniciarSesion: MaterialCardView
    private lateinit var cardCerrarSesion:  MaterialCardView
    private lateinit var layoutCargando:    LinearLayout

    private val viewModel: PerfilViewModel by viewModels {
        val app = requireActivity().application as AplicacionStudiOS
        PerfilViewModelFactory(
            app.repositorioTareas,
            app.repositorioAuth,
            app.repositorioFinanzas,
            app.repositorioNotas
        )
    }

    private val PREFS_NAME         = "StudiosPrefs"
    private val KEY_NOMBRE_USUARIO = "nombre_usuario"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_perfil, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNombreUsuario   = view.findViewById(R.id.tvNombreUsuario)
        tvEmailSubtitulo  = view.findViewById(R.id.tvEmailSubtitulo)
        tvStatTareas      = view.findViewById(R.id.tvStatTareas)
        tvStatAsistencia  = view.findViewById(R.id.tvStatAsistencia)
        cardVincular      = view.findViewById(R.id.cardVincularCuenta)
        cardIniciarSesion = view.findViewById(R.id.cardIniciarSesion)
        cardCerrarSesion  = view.findViewById(R.id.cardCerrarSesion)
        layoutCargando    = view.findViewById(R.id.layoutCargandoRespaldo)
        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)

        cargarNombreUsuario()
        setupObservers()

        btnEditarNombre.setOnClickListener       { mostrarDialogoEditarNombre() }
        cardVincular.setOnClickListener          { mostrarDialogoMenuAuth("Vincular Cuenta") }
        cardIniciarSesion.setOnClickListener     { mostrarDialogoMenuAuth("Iniciar Sesión") }
        cardCerrarSesion.setOnClickListener      { viewModel.cerrarSesion() }

        val abrirVincular = arguments?.getBoolean("abrirVincularDirecto") ?: false
        if (abrirVincular) {
            arguments?.putBoolean("abrirVincularDirecto", false)
            mostrarDialogoMenuAuth("Vincular Cuenta")
        }

        viewModel.verificarSesion()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOMBRE LOCAL
    // ─────────────────────────────────────────────────────────────────────────

    private fun cargarNombreUsuario() {
        val sp = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tvNombreUsuario.text = sp.getString(KEY_NOMBRE_USUARIO, "Usuario Nuevo")
    }

    private fun guardarNombreUsuario(nombre: String) {
        requireActivity()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOMBRE_USUARIO, nombre).apply()
    }

    /**
     * Diálogo de edición de nombre con dos campos separados:
     * Nombre (obligatorio) y Apellido (opcional).
     */
    private fun mostrarDialogoEditarNombre() {
        // Recuperar nombre y apellido actuales para pre-llenar
        val nombreActual = tvNombreUsuario.text.toString().trim()
        val partes       = nombreActual.split(" ", limit = 2)
        val nomActual    = partes.getOrNull(0) ?: ""
        val apeActual    = partes.getOrNull(1) ?: ""

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_editar_nombre, null)

        // El layout dialogo_editar_nombre debe tener:
        //   etNuevoNombre    → campo Nombre (obligatorio)
        //   etNuevoApellido  → campo Apellido (opcional)
        // Si solo tienes un campo (etNuevoNombre), agrega etNuevoApellido al XML.
        val etNombre   = dialogView.findViewById<TextInputEditText>(R.id.etNuevoNombre)
        val etApellido = dialogView.findViewById<TextInputEditText>(R.id.etNuevoApellido)
        val tilNombre  = dialogView.findViewById<TextInputLayout>(R.id.tilNuevoNombre)

        etNombre?.setText(nomActual)
        etApellido?.setText(apeActual)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Nombre")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre   = etNombre?.text.toString().trim()
                val nuevoApellido = etApellido?.text.toString().trim()

                if (nuevoNombre.isEmpty()) {
                    tilNombre?.error = "El nombre no puede estar vacío"
                    MensajesUI.error(requireActivity(), "El nombre no puede estar vacío")
                    return@setPositiveButton
                }

                val nombreCompleto = if (nuevoApellido.isNotEmpty()) "$nuevoNombre $nuevoApellido"
                else nuevoNombre
                guardarNombreUsuario(nombreCompleto)
                tvNombreUsuario.text = nombreCompleto
                viewModel.actualizarNombreInmediato(nuevoNombre, nuevoApellido)
                MensajesUI.exito(requireActivity(), "Nombre actualizado")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVADORES
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.porcentajeTareas.observe(viewLifecycleOwner)    { tvStatTareas.text     = "$it%" }
        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner){ tvStatAsistencia.text = "$it%" }

        viewModel.nombreUsuarioDisplay.observe(viewLifecycleOwner) { nombre ->
            if (!nombre.isNullOrEmpty()) tvNombreUsuario.text = nombre
        }
        viewModel.correoUsuario.observe(viewLifecycleOwner) { correo ->
            if (!correo.isNullOrEmpty()) tvEmailSubtitulo.text = correo
        }
        viewModel.usuarioLogueado.observe(viewLifecycleOwner) { logueado ->
            cardVincular.visibility      = if (logueado) View.GONE  else View.VISIBLE
            cardIniciarSesion.visibility = if (logueado) View.GONE  else View.VISIBLE
            cardCerrarSesion.visibility  = if (logueado) View.VISIBLE else View.GONE
            if (!logueado) {
                cargarNombreUsuario()
                tvEmailSubtitulo.text = "Usuario no registrado"
            }
        }
        viewModel.estaCargandoRespaldo.observe(viewLifecycleOwner) { cargando ->
            layoutCargando.visibility = if (cargando) View.VISIBLE else View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIÁLOGOS DE AUTENTICACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private fun mostrarDialogoMenuAuth(titulo: String) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_opciones_vincular, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        view.findViewById<ImageButton>(R.id.btnCerrarDialogo)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.tvTituloVincular)?.text = titulo
        view.findViewById<MaterialButton>(R.id.btnCorreo).setOnClickListener {
            dialog.dismiss()
            if (titulo == "Iniciar Sesión") mostrarDialogoLogin() else mostrarDialogoRegistro()
        }
        dialog.show()
    }

    // ── REGISTRO ──────────────────────────────────────────────────────────────

    private fun mostrarDialogoRegistro() {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_registro_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

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
            val nom  = etNom?.text.toString().trim()
            val ape  = etApe?.text.toString().trim()
            val cor  = etCor?.text.toString().trim()
            val pas  = etPas?.text.toString()
            val conf = etConf?.text.toString()

            when {
                // Nombre obligatorio
                nom.isEmpty() -> {
                    MensajesUI.error(requireActivity(), "El nombre no puede estar vacío")
                    return@setOnClickListener
                }
                cor.isEmpty() -> {
                    MensajesUI.error(requireActivity(), "Ingresa un correo válido")
                    return@setOnClickListener
                }
                pas.length < 6 -> {
                    MensajesUI.error(requireActivity(), "La contraseña debe tener al menos 6 caracteres")
                    return@setOnClickListener
                }
                pas != conf -> {
                    MensajesUI.error(requireActivity(), "Las contraseñas no coinciden")
                    return@setOnClickListener
                }
                else -> {
                    // Verificar si el correo ya tiene cuenta antes de enviar código
                    FirebaseAuth.getInstance().fetchSignInMethodsForEmail(cor)
                        .addOnSuccessListener { result ->
                            if (!result.signInMethods.isNullOrEmpty()) {
                                // Correo ya registrado
                                MensajesUI.error(
                                    requireActivity(),
                                    "Ya existe una cuenta con este correo. Intenta iniciar sesión."
                                )
                            } else {
                                // Correo libre → enviar código de verificación
                                viewModel.generarCodigoVerificacion()
                                viewModel.enviarEmail(viewModel.prepararDatosEmail(nom, cor))
                                dialog.dismiss()
                                mostrarDialogoVerificacionRegistro(cor, pas, nom, ape)
                            }
                        }
                        .addOnFailureListener {
                            MensajesUI.error(requireActivity(), "Error al verificar el correo. Intenta de nuevo.")
                        }
                }
            }
        }
        dialog.show()
    }

    // ── VERIFICACIÓN — REGISTRO ───────────────────────────────────────────────

    private fun mostrarDialogoVerificacionRegistro(
        cor: String, pas: String, nom: String, ape: String
    ) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_verificacion_codigo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        view.findViewById<ImageButton>(R.id.btnCerrarVerif)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarVerif)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoRegistro()
        }
        view.findViewById<TextView>(R.id.tvCorreoDestino)?.text = cor

        view.findViewById<MaterialButton>(R.id.btnVerificarCodigo).setOnClickListener {
            val codigo = view
                .findViewById<TextInputEditText>(R.id.etCodigoVerificacion)
                .text.toString().trim()

            if (codigo != viewModel.codigoGenerado) {
                MensajesUI.error(requireActivity(), "El código no coincide")
                return@setOnClickListener
            }

            FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(cor, pas)
                .addOnSuccessListener {
                    guardarNombreUsuario("$nom $ape".trim())
                    viewModel.actualizarNombreInmediato(nom, ape)
                    SyncManager.onNuevaCuentaVinculada(nom, ape)
                    dialog.dismiss()
                    viewModel.verificarSesion()
                    MensajesUI.exito(requireActivity(), "¡Cuenta vinculada con éxito!")
                }
                .addOnFailureListener { e ->
                    val ex  = e as? FirebaseAuthException
                    val msg = when (ex?.errorCode) {
                        "ERROR_EMAIL_ALREADY_IN_USE" ->
                            "Ya existe una cuenta con este correo. Intenta iniciar sesión."
                        else -> "Error al crear la cuenta: ${e.message}"
                    }
                    MensajesUI.error(requireActivity(), msg)
                }
        }
        dialog.show()
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    private fun mostrarDialogoLogin() {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_login_correo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        view.findViewById<ImageButton>(R.id.btnCerrarLogin)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarLogin)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoMenuAuth("Iniciar Sesión")
        }

        // Botón "¿Olvidaste tu contraseña?"
        view.findViewById<TextView>(R.id.tvOlvidasteContrasena)?.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoRecuperarPassword()
        }

        view.findViewById<MaterialButton>(R.id.btnEntrar).setOnClickListener {
            val cor = view.findViewById<TextInputEditText>(R.id.etCorreoLogin).text.toString().trim()
            val pas = view.findViewById<TextInputEditText>(R.id.etContrasenaLogin).text.toString()

            if (cor.isEmpty()) {
                MensajesUI.error(requireActivity(), "Ingresa tu correo")
                return@setOnClickListener
            }
            if (pas.isEmpty()) {
                MensajesUI.error(requireActivity(), "Ingresa tu contraseña")
                return@setOnClickListener
            }

            FirebaseAuth.getInstance().signInWithEmailAndPassword(cor, pas)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: ""
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("usuarios").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val nom = if (doc.exists()) doc.getString("perfil.nombre")   ?: "" else ""
                            val ape = if (doc.exists()) doc.getString("perfil.apellido") ?: "" else ""
                            if (nom.isNotEmpty()) {
                                guardarNombreUsuario("$nom $ape".trim())
                                viewModel.actualizarNombreInmediato(nom, ape)
                            }
                            SyncManager.onInicioSesion(uid, nom, ape)
                            dialog.dismiss()
                            viewModel.verificarSesion()
                            MensajesUI.exito(requireActivity(), "¡Bienvenido de nuevo!")
                        }
                }
                .addOnFailureListener { e ->
                    val ex  = e as? FirebaseAuthException
                    val msg = when (ex?.errorCode) {
                        "ERROR_USER_NOT_FOUND",
                        "ERROR_INVALID_CREDENTIAL"  -> "No existe una cuenta con este correo."
                        "ERROR_WRONG_PASSWORD"      -> "Contraseña incorrecta. Inténtalo de nuevo."
                        "ERROR_TOO_MANY_REQUESTS"   -> "Demasiados intentos. Espera un momento."
                        else                        -> "Correo o contraseña incorrectos."
                    }
                    MensajesUI.error(requireActivity(), msg)
                }
        }
        dialog.show()
    }

    // ── RECUPERAR CONTRASEÑA — PASO 1: ingresar correo ────────────────────────

    private fun mostrarDialogoRecuperarPassword() {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_recuperar_password, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        view.findViewById<MaterialButton>(R.id.btnEnviarCodigoRecup).setOnClickListener {
            val cor = view.findViewById<TextInputEditText>(R.id.etCorreoRecuperar)
                .text.toString().trim()

            if (cor.isEmpty()) {
                MensajesUI.error(requireActivity(), "Ingresa tu correo")
                return@setOnClickListener
            }

            // Verificar que el correo tenga cuenta registrada antes de enviar
            FirebaseAuth.getInstance().fetchSignInMethodsForEmail(cor)
                .addOnSuccessListener { result ->
                    if (result.signInMethods.isNullOrEmpty()) {
                        MensajesUI.error(
                            requireActivity(),
                            "No existe una cuenta con este correo."
                        )
                    } else {
                        // Generar y enviar código de verificación
                        viewModel.generarCodigoVerificacion()
                        viewModel.enviarEmail(viewModel.prepararDatosEmail("Usuario", cor))
                        dialog.dismiss()
                        mostrarDialogoVerificacionRecuperacion(cor)
                    }
                }
                .addOnFailureListener {
                    MensajesUI.error(requireActivity(), "Error al verificar el correo.")
                }
        }
        dialog.show()
    }

    // ── RECUPERAR CONTRASEÑA — PASO 2: verificar código ──────────────────────

    private fun mostrarDialogoVerificacionRecuperacion(cor: String) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_verificacion_codigo, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        view.findViewById<ImageButton>(R.id.btnCerrarVerif)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<ImageButton>(R.id.btnRegresarVerif)?.setOnClickListener {
            dialog.dismiss(); mostrarDialogoRecuperarPassword()
        }
        view.findViewById<TextView>(R.id.tvCorreoDestino)?.text = cor

        view.findViewById<MaterialButton>(R.id.btnVerificarCodigo).setOnClickListener {
            val codigo = view
                .findViewById<TextInputEditText>(R.id.etCodigoVerificacion)
                .text.toString().trim()

            if (codigo != viewModel.codigoGenerado) {
                MensajesUI.error(requireActivity(), "El código no coincide")
                return@setOnClickListener
            }
            // Código correcto → abrir formulario de nueva contraseña
            dialog.dismiss()
            mostrarDialogoNuevaPassword(cor)
        }
        dialog.show()
    }

    // ── RECUPERAR CONTRASEÑA — PASO 3: nueva contraseña ──────────────────────

    private fun mostrarDialogoNuevaPassword(cor: String) {
        // Crear el diálogo con dos campos: nueva contraseña y confirmar contraseña
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialogo_nueva_password, null
        )
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView).setBackground(ColorDrawable(Color.TRANSPARENT)).create()

        dialogView.findViewById<ImageButton>(R.id.btnCerrarNuevaPwd)?.setOnClickListener {
            dialog.dismiss()
        }

        val etNuevaPwd    = dialogView.findViewById<TextInputEditText>(R.id.etNuevaPassword)
        val etConfirmaPwd = dialogView.findViewById<TextInputEditText>(R.id.etConfirmarNuevaPassword)

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarNuevaPwd).setOnClickListener {
            val nueva    = etNuevaPwd?.text.toString()
            val confirma = etConfirmaPwd?.text.toString()

            when {
                nueva.length < 6 -> {
                    MensajesUI.error(requireActivity(), "La contraseña debe tener al menos 6 caracteres")
                    return@setOnClickListener
                }
                nueva != confirma -> {
                    MensajesUI.error(requireActivity(), "Las contraseñas no coinciden")
                    return@setOnClickListener
                }
                else -> {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(cor)
                        .addOnSuccessListener {
                            dialog.dismiss()
                            MensajesUI.exito(
                                requireActivity(),
                                "Te enviamos un enlace a $cor para confirmar tu nueva contraseña."
                            )
                        }
                        .addOnFailureListener {
                            MensajesUI.error(requireActivity(), "Error al enviar. Intenta de nuevo.")
                        }
                }
            }
        }
        dialog.show()
    }
}
