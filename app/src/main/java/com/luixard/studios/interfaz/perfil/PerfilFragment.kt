package com.luixard.studios.interfaz.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.luixard.studios.R

class PerfilFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias a los botones de la interfaz
        val btnEditarNombre = view.findViewById<ImageButton>(R.id.btnEditarNombre)
        val cardVincular = view.findViewById<MaterialCardView>(R.id.cardVincularCuenta)
        val cardIniciarSesion = view.findViewById<MaterialCardView>(R.id.cardIniciarSesion)

        // Listener para editar el nombre local
        btnEditarNombre.setOnClickListener {
            Toast.makeText(requireContext(), "Editar nombre (Próximamente)", Toast.LENGTH_SHORT).show()
        }

        // Listener para ir a Vincular Cuenta (CU-14)
        cardVincular.setOnClickListener {
            Toast.makeText(requireContext(), "Vincular con Firebase (Próximamente)", Toast.LENGTH_SHORT).show()
        }

        // Listener para ir a Iniciar Sesión (CU-15)
        cardIniciarSesion.setOnClickListener {
            Toast.makeText(requireContext(), "Iniciar Sesión (Próximamente)", Toast.LENGTH_SHORT).show()
        }
    }
}