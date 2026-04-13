package com.luixard.studios.interfaz.perfil

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.R
import com.luixard.studios.datos.sync.SyncManager
import com.luixard.studios.utilidades.MensajesUI
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class PerfilFragment : Fragment() {

    private lateinit var tvNombreUsuario:   TextView
    private lateinit var tvEmailSubtitulo:  TextView
    private lateinit var tvStatTareas:      TextView
    private lateinit var tvStatAsistencia:  TextView
    private lateinit var cardVincular:      MaterialCardView
    private lateinit var cardIniciarSesion: MaterialCardView
    private lateinit var cardCerrarSesion:  MaterialCardView
    private lateinit var layoutCargando:    LinearLayout

    // ── ViewModels ────────────────────────────────────────────────────────────
    private val viewModel: PerfilViewModel by viewModels {
        val app = requireActivity().application as AplicacionStudiOS
        PerfilViewModelFactory(
            app.repositorioTareas,
            app.repositorioAuth,
            app.repositorioFinanzas,
            app.repositorioNotas
        )
    }

    // AuthViewModel scoped to the activity so LoginFragment, RegistroFragment,
    // DialogoVerificacion y MenuAuthFragment lo comparten automáticamente.
    private val authViewModel: AuthViewModel by activityViewModels()

    private val PREFS_NAME         = "StudiosPrefs"
    private val KEY_NOMBRE_USUARIO = "nombre_usuario"

    // ── Google Sign-In ────────────────────────────────────────────────────────
    private var esVincularGoogle = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                authViewModel.autenticarConGoogle(account, esVincularGoogle)
            } catch (e: ApiException) {
                MensajesUI.error(requireActivity(), "Error con Google Sign-In (código ${e.statusCode})")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CICLO DE VIDA
    // ─────────────────────────────────────────────────────────────────────────

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

        // ── Listeners de tarjetas ─────────────────────────────────────────────
        btnEditarNombre.setOnClickListener   { mostrarDialogoEditarNombre() }
        cardCerrarSesion.setOnClickListener  { viewModel.cerrarSesion() }

        cardVincular.setOnClickListener {
            mostrarMenuAuth("Vincular Cuenta")
        }
        cardIniciarSesion.setOnClickListener {
            mostrarMenuAuth("Iniciar Sesión")
        }

        // Abrir directamente el menú de vinculación si viene desde el botón del Drawer
        val abrirVincular = arguments?.getBoolean("abrirVincularDirecto") ?: false
        if (abrirVincular) {
            arguments?.putBoolean("abrirVincularDirecto", false)
            mostrarMenuAuth("Vincular Cuenta")
        }

        // Observar loginCompletado del SyncManager para refrescar sesión
        viewLifecycleOwner.lifecycleScope.launch {
            SyncManager.loginCompletado.collect { completado ->
                if (completado) {
                    viewModel.verificarSesion()
                    SyncManager.loginCompletado.value = false
                }
            }
        }

        viewModel.verificarSesion()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ AUTH — lanza MenuAuthFragment con el callback de Google
    // ─────────────────────────────────────────────────────────────────────────

    private fun mostrarMenuAuth(titulo: String) {
        val menu = MenuAuthFragment.newInstance(titulo)
        menu.alSeleccionarGoogle = { esVincular ->
            esVincularGoogle = esVincular
            iniciarFlujoGoogle()
        }
        menu.show(parentFragmentManager, "MenuAuth")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GOOGLE SIGN-IN
    // ─────────────────────────────────────────────────────────────────────────

    private fun iniciarFlujoGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        val client = GoogleSignIn.getClient(requireContext(), gso)
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVADORES
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupObservers() {

        // ── PerfilViewModel ───────────────────────────────────────────────────
        viewModel.porcentajeTareas.observe(viewLifecycleOwner)     { tvStatTareas.text     = "$it%" }
        viewModel.porcentajeAsistencia.observe(viewLifecycleOwner) { tvStatAsistencia.text = "$it%" }

        viewModel.nombreUsuarioDisplay.observe(viewLifecycleOwner) { nombre ->
            if (!nombre.isNullOrEmpty()) tvNombreUsuario.text = nombre
        }
        viewModel.correoUsuario.observe(viewLifecycleOwner) { correo ->
            if (!correo.isNullOrEmpty()) tvEmailSubtitulo.text = correo
        }
        viewModel.usuarioLogueado.observe(viewLifecycleOwner) { logueado ->
            cardVincular.visibility      = if (logueado) View.GONE    else View.VISIBLE
            cardIniciarSesion.visibility = if (logueado) View.GONE    else View.VISIBLE
            cardCerrarSesion.visibility  = if (logueado) View.VISIBLE else View.GONE
            if (!logueado) {
                cargarNombreUsuario()
                tvEmailSubtitulo.text = "Usuario no registrado"
            }
        }
        viewModel.estaCargandoRespaldo.observe(viewLifecycleOwner) { cargando ->
            layoutCargando.visibility = if (cargando) View.VISIBLE else View.GONE
        }

        // ── AuthViewModel — reaccionar a resultados de auth ───────────────────
        authViewModel.authEstado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is AuthEstado.LoginExito -> {
                    if (estado.nombre.isNotEmpty()) {
                        guardarNombreUsuario("${estado.nombre} ${estado.apellido}".trim())
                        viewModel.actualizarNombreInmediato(estado.nombre, estado.apellido)
                    }
                    viewModel.verificarSesion()
                    // El mensaje de éxito lo muestra LoginFragment
                }
                is AuthEstado.RegistroExito -> {
                    guardarNombreUsuario("${estado.nombre} ${estado.apellido}".trim())
                    viewModel.actualizarNombreInmediato(estado.nombre, estado.apellido)
                    viewModel.verificarSesion()
                    // El mensaje de éxito lo muestra DialogoVerificacion
                }
                is AuthEstado.GoogleExito -> {
                    guardarNombreUsuario("${estado.nombre} ${estado.apellido}".trim())
                    tvNombreUsuario.text = "${estado.nombre} ${estado.apellido}".trim()
                    if (estado.esNuevo) {
                        viewModel.vincularCuentaGoogle(estado.nombre, estado.apellido)
                        MensajesUI.exito(requireActivity(), "¡Cuenta vinculada con Google!")
                    } else {
                        viewModel.iniciarSesionGoogle(estado.nombre, estado.apellido)
                        MensajesUI.exito(requireActivity(), "¡Bienvenido de nuevo, ${estado.nombre}!")
                    }
                    viewModel.verificarSesion()
                    authViewModel.resetEstado()
                }
                else -> {}
            }
        }
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

    private fun mostrarDialogoEditarNombre() {
        val nombreActual = tvNombreUsuario.text.toString().trim()
        val partes       = nombreActual.split(" ", limit = 2)
        val nomActual    = partes.getOrNull(0) ?: ""
        val apeActual    = partes.getOrNull(1) ?: ""

        val dialogView = layoutInflater.inflate(R.layout.dialogo_editar_nombre, null)
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

                val nombreCompleto = if (nuevoApellido.isNotEmpty())
                    "$nuevoNombre $nuevoApellido" else nuevoNombre

                guardarNombreUsuario(nombreCompleto)
                tvNombreUsuario.text = nombreCompleto
                viewModel.actualizarNombreInmediato(nuevoNombre, nuevoApellido)
                MensajesUI.exito(requireActivity(), "Nombre actualizado")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}