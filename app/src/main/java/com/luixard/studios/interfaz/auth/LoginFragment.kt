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

class LoginFragment : DialogFragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        fun newInstance() = LoginFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialogo_login_correo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etCorreo    = view.findViewById<TextInputEditText>(R.id.etCorreoLogin)
        val etPassword  = view.findViewById<TextInputEditText>(R.id.etContrasenaLogin)
        val btnEntrar   = view.findViewById<MaterialButton>(R.id.btnEntrar)
        val btnCerrar   = view.findViewById<ImageButton>(R.id.btnCerrarLogin)
        val btnRegresar = view.findViewById<ImageButton>(R.id.btnRegresarLogin)
        val tvOlvide    = view.findViewById<TextView>(R.id.tvOlvidasteContrasena)

        // ── Navegación ────────────────────────────────────────────────────────
        btnCerrar.setOnClickListener   { dismiss() }
        btnRegresar.setOnClickListener {
            dismiss()
            MenuAuthFragment.newInstance("Iniciar Sesión")
                .show(parentFragmentManager, "MenuAuth")
        }

        // ── Recuperar contraseña ───────────────────────────────────────────────
        tvOlvide.setOnClickListener {
            dismiss()
            RecuperarPasswordFragment.newInstance()
                .show(parentFragmentManager, "RecuperarPassword")
        }

        // ── Login ─────────────────────────────────────────────────────────────
        btnEntrar.setOnClickListener {
            val cor = etCorreo.text.toString().trim()
            val pas = etPassword.text.toString()

            when {
                cor.isEmpty() -> MensajesUI.error(requireActivity(), "Ingresa tu correo")
                pas.isEmpty() -> MensajesUI.error(requireActivity(), "Ingresa tu contraseña")
                else          -> authViewModel.iniciarSesion(cor, pas)
            }
        }

        authViewModel.authEstado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is AuthEstado.LoginExito -> {
                    MensajesUI.exito(requireActivity(), "¡Bienvenido de nuevo!")
                    dismiss()
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
}