package com.luixard.studios.interfaz.finanzas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.databinding.FinanzasFragmentHistorialBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistorialFinanzasFragment : Fragment() {
    private var _binding: FinanzasFragmentHistorialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanzasViewModel by viewModels {
        FinanzasViewModelFactory((requireActivity().application as AplicacionStudiOS).repositorioFinanzas)
    }

    private lateinit var adaptador: AdaptadorHistorialSemanas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FinanzasFragmentHistorialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adaptador = AdaptadorHistorialSemanas { id, callback ->
            viewLifecycleOwner.lifecycleScope.launch {
                // Obtenemos el flujo de transacciones y tomamos la lista actual
                val lista = viewModel.obtenerTransacciones(id).first()
                callback(lista)
            }
        }

        binding.rvHistorialSemanas.apply {
            adapter = adaptador
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historialSemanas.collect { adaptador.setSemanas(it) }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}