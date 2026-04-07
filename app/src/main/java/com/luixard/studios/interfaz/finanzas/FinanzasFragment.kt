package com.luixard.studios.interfaz.finanzas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luixard.studios.AplicacionStudiOS
import com.luixard.studios.databinding.*
import com.luixard.studios.datos.modelos.*
import com.luixard.studios.utilidades.MensajesUI
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class FinanzasFragment : Fragment() {

    private var _binding: FinanzasFragmentPrincipalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanzasViewModel by viewModels {
        FinanzasViewModelFactory((requireActivity().application as AplicacionStudiOS).repositorioFinanzas)
    }

    private lateinit var adaptadorTransacciones: AdaptadorTransacciones

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FinanzasFragmentPrincipalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarListeners()
        configurarObservadores()
    }

    private fun configurarRecyclerView() {
        adaptadorTransacciones = AdaptadorTransacciones(
            onEdit = { transaccion -> mostrarDialogoRegistro(transaccion.tipo_transaccion == "Gasto", transaccion) },
            onDelete = { transaccion -> confirmarEliminarTransaccion(transaccion) }
        )
        binding.rvHistorialTransacciones.apply {
            adapter = adaptadorTransacciones
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun confirmarEliminarTransaccion(transaccion: Transaccion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Eliminar registro?")
            .setMessage("Se borrará este movimiento de tu historial.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.borrarTransaccion(transaccion)
                MensajesUI.exito(requireActivity(), "Registro eliminado")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun configurarListeners() {
        binding.btnEstablecerPresupuesto.setOnClickListener { mostrarDialogoPresupuesto() }
        binding.btnEditarPresupuesto.setOnClickListener { mostrarDialogoPresupuesto() }
        binding.btnConfiguracionFinanzas.setOnClickListener { mostrarDialogoCategorias() }
        binding.fabAgregarTransaccion.setOnClickListener { mostrarDialogoSeleccion() }

        binding.btnBorrarPresupuesto.setOnClickListener {
            viewModel.presupuestoActual.value?.let { p ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Eliminar presupuesto?")
                    .setMessage("Se borrará tu meta semanal actual.")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.borrarPresupuesto(p)
                        MensajesUI.exito(requireActivity(), "Presupuesto eliminado")
                    }.setNegativeButton("Cancelar", null).show()
            }
        }
    }

    private fun configurarObservadores() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.presupuestoActual.collect { presupuesto ->
                if (presupuesto == null) {
                    gestionarEstadoVacio()
                } else {
                    gestionarEstadoLleno(presupuesto)
                    viewModel.obtenerTransacciones(presupuesto.id_finanza!!).collect { lista ->
                        actualizarListaMovimientos(presupuesto, lista)
                    }
                }
            }
        }
    }

    private fun gestionarEstadoVacio() {
        binding.layoutEstadoVacio.visibility = View.VISIBLE
        binding.layoutEstadoLleno.visibility = View.GONE
        binding.btnEditarPresupuesto.visibility = View.GONE
        binding.btnBorrarPresupuesto.visibility = View.GONE
        binding.tvMovimientosTitulo.visibility = View.GONE
        binding.rvHistorialTransacciones.visibility = View.GONE
    }

    private fun gestionarEstadoLleno(presupuesto: PresupuestoSemanal) {
        binding.layoutEstadoVacio.visibility = View.GONE
        binding.layoutEstadoLleno.visibility = View.VISIBLE
        binding.btnEditarPresupuesto.visibility = View.VISIBLE

        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        binding.tvMeta.text = "Meta: ${format.format(presupuesto.presupuesto_semanal_meta)}"
    }

    private fun actualizarListaMovimientos(presupuesto: PresupuestoSemanal, lista: List<Transaccion>) {
        adaptadorTransacciones.submitList(lista)
        val hayMovimientos = lista.isNotEmpty()
        binding.tvMovimientosTitulo.visibility = if (hayMovimientos) View.VISIBLE else View.GONE
        binding.rvHistorialTransacciones.visibility = if (hayMovimientos) View.VISIBLE else View.GONE
        binding.btnBorrarPresupuesto.visibility = if (hayMovimientos) View.GONE else View.VISIBLE
        actualizarResumenMatematico(presupuesto, lista)
    }

    private fun actualizarResumenMatematico(presupuesto: PresupuestoSemanal, lista: List<Transaccion>) {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        var gastado = 0.0
        var ingresos = 0.0
        lista.forEach { if (it.tipo_transaccion == "Gasto") gastado += it.monto else ingresos += it.monto }
        val saldo = (presupuesto.presupuesto_semanal_meta - gastado) + ingresos
        binding.tvSaldoRestanteMonto.text = format.format(saldo)
        binding.tvGastado.text = "Gastado: ${format.format(gastado)}"
        val progreso = if (presupuesto.presupuesto_semanal_meta > 0) ((gastado / presupuesto.presupuesto_semanal_meta) * 100).toInt() else 0
        binding.progresoPresupuesto.progress = progreso
    }

    private fun mostrarDialogoSeleccion() {
        val selBinding = FinanzasDialogoSeleccionTransaccionBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(selBinding.root).create()
        selBinding.btnSeleccionarGasto.setOnClickListener { dialog.dismiss(); mostrarDialogoRegistro(true) }
        selBinding.btnSeleccionarIngreso.setOnClickListener { dialog.dismiss(); mostrarDialogoRegistro(false) }
        dialog.show()
    }

    private fun mostrarDialogoRegistro(esGasto: Boolean, transaccionAEditar: Transaccion? = null) {
        val regBinding = FinanzasDialogoNuevoGastoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(regBinding.root).create()

        if (transaccionAEditar != null) {
            regBinding.tvTituloGasto.text = if (esGasto) "Gasto" else "Ingreso Extra"
            regBinding.etMontoGasto.setText(transaccionAEditar.monto.toString())
            regBinding.etNotaGasto.setText(transaccionAEditar.nota_transaccion)
            regBinding.btnGuardarGasto.text = "Guardar Cambios"
        }

        if (!esGasto) {
            regBinding.tilCategoriaTransaccion.visibility = View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorias.collect { listaCategorias: List<CategoriaGasto> ->
                val nombres = listaCategorias.map { it.nombre_categoria }
                val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres)
                regBinding.spinnerCategoria.setAdapter(adapterSpinner)

                if (transaccionAEditar != null && esGasto) {
                    val index = nombres.indexOf(transaccionAEditar.nota_transaccion)
                    if (index != -1) regBinding.spinnerCategoria.setText(nombres[index], false)
                }
            }
        }

        regBinding.btnGuardarGasto.setOnClickListener {
            regBinding.etMontoGasto.error = null
            regBinding.tilCategoriaTransaccion.error = null

            val montoStr = regBinding.etMontoGasto.text.toString()
            val catStr = regBinding.spinnerCategoria.text.toString()

            val montoValido = montoStr.isNotEmpty() && montoStr.toDouble() > 0
            val categoriaValida = !esGasto || catStr.isNotEmpty()

            if (montoValido && categoriaValida) {
                viewModel.presupuestoActual.value?.let { presupuesto ->
                    val transaccion = Transaccion(
                        id_transaccion = transaccionAEditar?.id_transaccion ?: 0,
                        id_usuario = null,
                        id_finanza = presupuesto.id_finanza!!,
                        id_categoria = null,
                        tipo_transaccion = if (esGasto) "Gasto" else "Ingreso",
                        monto = montoStr.toDouble(),
                        fecha_transaccion = transaccionAEditar?.fecha_transaccion ?: Date(),
                        nota_transaccion = regBinding.etNotaGasto.text.toString().ifEmpty {
                            if (esGasto) catStr else "Ingreso Extra"
                        }
                    )
                    viewModel.registrarTransaccion(transaccion)
                    dialog.dismiss()
                    MensajesUI.exito(requireActivity(), "Registro guardado")
                }
            } else {
                if (!montoValido) regBinding.etMontoGasto.error = "Ingresa un monto válido"
                if (esGasto && !categoriaValida) regBinding.tilCategoriaTransaccion.error = "Selecciona una categoría"
            }
        }

        regBinding.btnCancelarGasto.setOnClickListener { dialog.dismiss() }
        regBinding.btnCerrarGasto.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoCategorias() {
        val catBinding = FinanzasDialogoCategoriasBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(catBinding.root).create()

        val adapterCat = AdaptadorCategorias(
            onDelete = { categoriaSeleccionada: CategoriaGasto ->
                // NUEVA CONFIRMACIÓN DE BORRADO
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Eliminar categoría?")
                    .setMessage("¿Seguro que desea borrar la categoría?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.borrarCategoria(categoriaSeleccionada)
                        MensajesUI.exito(requireActivity(), "Categoría eliminada")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        catBinding.rvCategorias.apply {
            adapter = adapterCat
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorias.collect { lista: List<CategoriaGasto> ->
                adapterCat.submitList(lista)
            }
        }

        catBinding.btnAnadirCategoria.setOnClickListener {
            val nombre = catBinding.etNuevaCategoria.text.toString()
            if (nombre.isNotEmpty()) {
                viewModel.agregarCategoria(nombre)
                catBinding.etNuevaCategoria.text?.clear()
            } else {
                catBinding.etNuevaCategoria.error = "Escribe un nombre"
            }
        }

        catBinding.btnCerrarCategorias.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoPresupuesto() {
        val presBinding = FinanzasDialogoPresupuestoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(presBinding.root).create()
        presBinding.btnConfirmarDialogo.setOnClickListener {
            val entrada = presBinding.etMontoPresupuesto.text.toString()
            if (entrada.isNotEmpty() && entrada.toDouble() > 0) {
                viewModel.establecerPresupuesto(entrada.toDouble())
                dialog.dismiss()
                MensajesUI.exito(requireActivity(), "Presupuesto actualizado")
            } else {
                presBinding.etMontoPresupuesto.error = "Monto inválido"
            }
        }
        presBinding.btnCancelarDialogo.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}