package com.luixard.studios.interfaz.finanzas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.databinding.FinanzasFragmentPrincipalBinding
import com.luixard.studios.databinding.FinanzasDialogoPresupuestoBinding
import com.luixard.studios.datos.modelos.PresupuestoSemanal
import com.luixard.studios.utilidades.MensajesUI
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class FinanzasFragment : Fragment() {

    private var _binding: FinanzasFragmentPrincipalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanzasViewModel by viewModels {
        FinanzasViewModelFactory((requireActivity().application as AplicacionStudiOS).repositorioFinanzas)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FinanzasFragmentPrincipalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarListeners()
        configurarObservadores()
    }

    private fun configurarListeners() {
        // CU-06: Establecer presupuesto inicial
        binding.btnEstablecerPresupuesto.setOnClickListener { mostrarDialogoPresupuesto() }

        // CU-08: Editar presupuesto existente
        binding.btnEditarPresupuesto.setOnClickListener { mostrarDialogoPresupuesto() }

        // Nueva opción: Borrar presupuesto
        binding.btnBorrarPresupuesto.setOnClickListener {
            val presupuesto = viewModel.presupuestoActual.value
            if (presupuesto != null) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Eliminar presupuesto?")
                    .setMessage("Se borrará tu meta semanal actual. Esta acción no se puede deshacer.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.borrarPresupuesto(presupuesto)
                        MensajesUI.exito(requireActivity(), "Presupuesto eliminado")
                    }
                    .show()
            }
        }

        binding.fabAgregarTransaccion.setOnClickListener {
            MensajesUI.advertencia(requireActivity(), "Próximamente: Registro de transacciones")
        }
    }

    private fun configurarObservadores() {
        // RF15: Observamos el presupuesto para cambiar el estado de la pantalla
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.presupuestoActual.collect { presupuesto ->
                if (presupuesto == null) {
                    // ESTADO VACÍO: Ocultamos el dashboard y los botones de gestión
                    binding.layoutEstadoVacio.visibility = View.VISIBLE
                    binding.layoutEstadoLleno.visibility = View.GONE

                    // Aquí aplicamos tu petición: ocultar editar y borrar
                    binding.btnEditarPresupuesto.visibility = View.GONE
                    binding.btnBorrarPresupuesto.visibility = View.GONE
                } else {
                    // ESTADO LLENO: Mostramos el dashboard y activamos los botones
                    binding.layoutEstadoVacio.visibility = View.GONE
                    binding.layoutEstadoLleno.visibility = View.VISIBLE

                    // Mostramos ambos botones ahora que sí hay datos
                    binding.btnEditarPresupuesto.visibility = View.VISIBLE
                    binding.btnBorrarPresupuesto.visibility = View.VISIBLE

                    actualizarDatosPresupuesto(presupuesto)
                }
            }
        }
    }

    private fun actualizarDatosPresupuesto(presupuesto: PresupuestoSemanal) {
        val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.getDefault())
        binding.tvSaldoRestanteMonto.text = formatoMoneda.format(presupuesto.presupuesto_semanal_meta)
        binding.tvMeta.text = "Meta: ${formatoMoneda.format(presupuesto.presupuesto_semanal_meta)}"
        binding.tvGastado.text = "Gastado: ${formatoMoneda.format(0.0)}"
        binding.progresoPresupuesto.progress = 0
    }

    private fun mostrarDialogoPresupuesto() {
        val dialogoBinding = FinanzasDialogoPresupuestoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogoBinding.root)
            .setCancelable(false)
            .create()

        dialogoBinding.btnConfirmarDialogo.setOnClickListener {
            val entrada = dialogoBinding.etMontoPresupuesto.text.toString()
            if (entrada.isNotEmpty() && entrada.toDouble() > 0) {
                viewModel.establecerPresupuesto(entrada.toDouble())
                dialog.dismiss()
                MensajesUI.exito(requireActivity(), "Presupuesto actualizado")
            } else {
                dialogoBinding.etMontoPresupuesto.error = "Ingresa un monto válido"
            }
        }

        dialogoBinding.btnCancelarDialogo.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}