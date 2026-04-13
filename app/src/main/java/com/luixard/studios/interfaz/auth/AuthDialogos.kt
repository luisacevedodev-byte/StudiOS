package com.luixard.studios.interfaz.perfil
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.utilidades.MensajesUI

// ═════════════════════════════════════════════════════════════════════════════
// 1) MENÚ AUTH — opciones: correo o Google
// ═════════════════════════════════════════════════════════════════════════════

class MenuAuthFragment : DialogFragment() {

    companion object {
        private const val ARG_TITULO = "titulo"
        fun newInstance(titulo: String) = MenuAuthFragment().apply {
            arguments = Bundle().apply { putString(ARG_TITULO, titulo) }
        }
    }

    // Callback para que PerfilFragment lance el flujo de Google (necesita Activity)
    var alSeleccionarGoogle: ((esVincular: Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialogo_opciones_vincular, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titulo = arguments?.getString(ARG_TITULO) ?: "Vincular Cuenta"

        view.findViewById<TextView>(R.id.tvTituloVincular)?.text = titulo
        view.findViewById<ImageButton>(R.id.btnCerrarDialogo)?.setOnClickListener { dismiss() }

        view.findViewById<MaterialButton>(R.id.btnCorreo).setOnClickListener {
            dismiss()
            if (titulo == "Iniciar Sesión") {
                LoginFragment.newInstance().show(parentFragmentManager, "Login")
            } else {
                RegistroFragment.newInstance().show(parentFragmentManager, "Registro")
            }
        }

        view.findViewById<MaterialButton>(R.id.btnGoogle)?.setOnClickListener {
            dismiss()
            alSeleccionarGoogle?.invoke(titulo == "Vincular Cuenta")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2) REGISTRO — formulario de nueva cuenta con correo
// ═════════════════════════════════════════════════════════════════════════════

class RegistroFragment : DialogFragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        fun newInstance() = RegistroFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialogo_registro_correo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etNom    = view.findViewById<TextInputEditText>(R.id.etNombreRegistro)
        val etApe    = view.findViewById<TextInputEditText>(R.id.etApellidoRegistro)
        val etCor    = view.findViewById<TextInputEditText>(R.id.etCorreo)
        val etPas    = view.findViewById<TextInputEditText>(R.id.etContrasena)
        val etConf   = view.findViewById<TextInputEditText>(R.id.etConfirmarContrasena)
        val btnReg   = view.findViewById<MaterialButton>(R.id.btnRegistrar)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCerrarRegistro)
        val btnBack  = view.findViewById<ImageButton>(R.id.btnRegresarRegistro)

        btnClose.setOnClickListener { dismiss() }
        btnBack.setOnClickListener  {
            dismiss()
            MenuAuthFragment.newInstance("Vincular Cuenta")
                .show(parentFragmentManager, "MenuAuth")
        }

        btnReg.setOnClickListener {
            val nom  = etNom.text.toString().trim()
            val ape  = etApe.text.toString().trim()
            val cor  = etCor.text.toString().trim()
            val pas  = etPas.text.toString()
            val conf = etConf.text.toString()

            when {
                nom.isEmpty()  -> MensajesUI.error(requireActivity(), "El nombre no puede estar vacío")
                cor.isEmpty()  -> MensajesUI.error(requireActivity(), "Ingresa un correo válido")
                pas.length < 6 -> MensajesUI.error(requireActivity(), "La contraseña debe tener al menos 6 caracteres")
                pas != conf    -> MensajesUI.error(requireActivity(), "Las contraseñas no coinciden")
                else -> {
                    authViewModel.verificarCorreoYRegistrar(
                        correo   = cor,
                        password = pas,
                        nombre   = nom,
                        apellido = ape,
                        alEnviarCodigo = {
                            dismiss()
                            DialogoVerificacion.newInstanceRegistro(cor, pas, nom, ape)
                                .show(parentFragmentManager, "Verificacion")
                        },
                        alError = { msg ->
                            MensajesUI.error(requireActivity(), msg)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3) RECUPERAR CONTRASEÑA — pide el correo y manda a DialogoVerificacion
// ═════════════════════════════════════════════════════════════════════════════

class RecuperarPasswordFragment : DialogFragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        fun newInstance() = RecuperarPasswordFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialogo_recuperar_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etCorreo = view.findViewById<TextInputEditText>(R.id.etCorreoRecuperar)
        val btnEnviar = view.findViewById<MaterialButton>(R.id.btnEnviarCodigoRecup)

        btnEnviar.setOnClickListener {
            val cor = etCorreo.text.toString().trim()
            if (cor.isEmpty()) {
                MensajesUI.error(requireActivity(), "Ingresa tu correo")
                return@setOnClickListener
            }

            // Generar y enviar código de verificación
            authViewModel.generarCodigoVerificacion()
            authViewModel.enviarEmail(authViewModel.prepararDatosEmail("Usuario", cor))

            dismiss()
            DialogoVerificacion.newInstanceRecuperar(cor)
                .show(parentFragmentManager, "VerificacionRecuperacion")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}