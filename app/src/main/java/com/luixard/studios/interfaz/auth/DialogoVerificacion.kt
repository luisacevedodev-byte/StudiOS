package com.luixard.studios.interfaz.perfil

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.luixard.studios.R
import com.luixard.studios.utilidades.MensajesUI

class DialogoVerificacion : DialogFragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        const val TIPO_REGISTRO  = "REGISTRO"
        const val TIPO_RECUPERAR = "RECUPERAR"

        private const val ARG_TIPO     = "tipo"
        private const val ARG_CORREO   = "correo"
        private const val ARG_PASSWORD = "password"
        private const val ARG_NOMBRE   = "nombre"
        private const val ARG_APELLIDO = "apellido"

        /** Para el flujo de registro. */
        fun newInstanceRegistro(
            correo: String, password: String, nombre: String, apellido: String
        ) = DialogoVerificacion().apply {
            arguments = Bundle().apply {
                putString(ARG_TIPO,     TIPO_REGISTRO)
                putString(ARG_CORREO,   correo)
                putString(ARG_PASSWORD, password)
                putString(ARG_NOMBRE,   nombre)
                putString(ARG_APELLIDO, apellido)
            }
        }

        /** Para el flujo de recuperación de contraseña. */
        fun newInstanceRecuperar(correo: String) = DialogoVerificacion().apply {
            arguments = Bundle().apply {
                putString(ARG_TIPO,   TIPO_RECUPERAR)
                putString(ARG_CORREO, correo)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialogo_verificacion_codigo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tipo     = arguments?.getString(ARG_TIPO)     ?: TIPO_REGISTRO
        val correo   = arguments?.getString(ARG_CORREO)   ?: ""
        val password = arguments?.getString(ARG_PASSWORD) ?: ""
        val nombre   = arguments?.getString(ARG_NOMBRE)   ?: ""
        val apellido = arguments?.getString(ARG_APELLIDO) ?: ""

        val etCodigo    = view.findViewById<TextInputEditText>(R.id.etCodigoVerificacion)
        val tvDestino   = view.findViewById<TextView>(R.id.tvCorreoDestino)
        val btnVerif    = view.findViewById<MaterialButton>(R.id.btnVerificarCodigo)
        val btnCerrar   = view.findViewById<ImageButton>(R.id.btnCerrarVerif)
        val btnRegresar = view.findViewById<ImageButton>(R.id.btnRegresarVerif)

        tvDestino?.text = correo

        // ── Navegación ────────────────────────────────────────────────────────
        btnCerrar.setOnClickListener { dismiss() }
        btnRegresar.setOnClickListener {
            dismiss()
            when (tipo) {
                TIPO_REGISTRO  -> RegistroFragment.newInstance()
                    .show(parentFragmentManager, "Registro")
                TIPO_RECUPERAR -> RecuperarPasswordFragment.newInstance()
                    .show(parentFragmentManager, "RecuperarPassword")
            }
        }

        // ── Verificar código e invocar acción correspondiente ─────────────────
        btnVerif.setOnClickListener {
            val codigoIngresado = etCodigo.text.toString().trim()

            if (codigoIngresado != authViewModel.codigoGenerado) {
                MensajesUI.error(requireActivity(), "El código no coincide")
                return@setOnClickListener
            }

            when (tipo) {
                TIPO_REGISTRO  -> authViewModel.crearCuentaFirebase(correo, password, nombre, apellido)
                TIPO_RECUPERAR -> authViewModel.enviarResetPassword(correo)
            }
        }

        // ── Observar resultado ─────────────────────────────────────────────────
        authViewModel.authEstado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is AuthEstado.RegistroExito -> {
                    MensajesUI.exito(requireActivity(), "¡Cuenta vinculada con éxito!")
                    dismiss()
                    authViewModel.resetEstado()
                }
                is AuthEstado.ResetEnviado -> {
                    dismiss()
                    mostrarMensajeResetContrasena(correo)
                    authViewModel.resetEstado()
                }
                is AuthEstado.Error -> {
                    MensajesUI.error(requireActivity(), estado.mensaje)
                    authViewModel.resetEstado()
                }
                else -> {}
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

    private fun mostrarMensajeResetContrasena(correo: String) {
        if (!isAdded) return
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Correo enviado")
            .setMessage(
                "Te enviamos un enlace a\n$correo\npara restablecer tu contraseña.\n\n" +
                        "Si no lo encuentras en tu bandeja principal, revisa la carpeta de SPAM."
            )
            .setCancelable(false)
            .setPositiveButton("Entendido") { d, _ -> d.dismiss() }
            .show()

        Handler(Looper.getMainLooper()).postDelayed({
            if (alertDialog.isShowing && isAdded) alertDialog.dismiss()
        }, 8_000)
    }
}