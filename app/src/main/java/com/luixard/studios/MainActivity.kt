package com.luixard.studios

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.luixard.studios.interfaz.finanzas.FinanzasFragment
import com.luixard.studios.interfaz.notas.NotasFragment
import com.luixard.studios.interfaz.tareas.ListaTareasFragment
import com.luixard.studios.interfaz.inicio.DashboardFragment // Asegúrate de tener este import

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var customDrawerView: View
    private lateinit var itemsMenu: List<LinearLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar Drawer y Vista Personalizada
        drawerLayout = findViewById(R.id.drawer_layout)
        customDrawerView = findViewById(R.id.custom_drawer_view)

        val customDrawerView = findViewById<View>(R.id.custom_drawer_view)

        // Referencias a la Barra Superior
        val btnOpenDrawer = findViewById<ImageView>(R.id.btnOpenDrawer)
        val btnCloseDrawer = customDrawerView.findViewById<ImageView>(R.id.btnCloseDrawer)

        // Referencias a los items del Menú Lateral (IDs de drawer_main.xml)
        val optDashboard = customDrawerView.findViewById<LinearLayout>(R.id.nav_dashboard_item)
        val optTareas = customDrawerView.findViewById<LinearLayout>(R.id.nav_tareas_item)
        val optFinanzas = customDrawerView.findViewById<LinearLayout>(R.id.nav_finanzas_item)
        val optNotas = customDrawerView.findViewById<LinearLayout>(R.id.nav_notas_item)

        itemsMenu = listOf(optDashboard, optTareas, optFinanzas, optNotas)

        // CARGA INICIAL
        if (savedInstanceState == null) {
            cargarFragmento(DashboardFragment(), optDashboard)
        }

        // --- LISTENERS ---

        btnOpenDrawer.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnCloseDrawer.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }

        optDashboard.setOnClickListener { cargarFragmento(DashboardFragment(), optDashboard) }
        optTareas.setOnClickListener { cargarFragmento(ListaTareasFragment(), optTareas) }
        optFinanzas.setOnClickListener { cargarFragmento(FinanzasFragment(), optFinanzas) }
        optNotas.setOnClickListener { cargarFragmento(NotasFragment(), optNotas) }
    }

    private fun cargarFragmento(fragmento: Fragment, itemSeleccionado: LinearLayout) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contenedor_principal, fragmento)
            .commit()

        // Esta función aplica el "brillo" al item correcto
        actualizarEstiloMenu(itemSeleccionado)
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun actualizarEstiloMenu(itemActivo: LinearLayout) {
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val esModoOscuro = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        for (item in itemsMenu) {
            val icono = item.getChildAt(0) as ImageView
            val texto = item.getChildAt(1) as TextView

            if (item == itemActivo) {
                // Estado Seleccionado
                item.setBackgroundResource(R.drawable.selector_drawer_fondo)

                val colorPrincipal = if (esModoOscuro) {
                    ContextCompat.getColor(this, R.color.studios_cyan)
                } else {
                    ContextCompat.getColor(this, R.color.studios_cyan_titulo)
                }

                val colorFondo = if (esModoOscuro) {
                    android.graphics.Color.parseColor("#2600D4FF")
                } else {
                    android.graphics.Color.parseColor("#1A007B99")
                }

                item.background?.setTint(colorFondo)
                icono.setColorFilter(colorPrincipal)
                texto.setTextColor(colorPrincipal)
                texto.paint.isFakeBoldText = true
            } else {
                // Estado Inactivo
                item.setBackgroundResource(android.R.color.transparent)
                icono.setColorFilter(ContextCompat.getColor(this, R.color.gris_texto))
                texto.setTextColor(ContextCompat.getColor(this, R.color.gris_texto))
                texto.paint.isFakeBoldText = false
            }
            texto.invalidate()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    // Función para navegar desde el Dashboard y actualizar el menú
    fun navegarDesdeDashboard(idDestino: Int) {
        val customDrawerView = findViewById<android.view.View>(R.id.custom_drawer_view)

        when (idDestino) {
            R.id.nav_tareas_item -> {
                val item = customDrawerView.findViewById<LinearLayout>(R.id.nav_tareas_item)
                cargarFragmento(ListaTareasFragment(), item)
            }
            R.id.nav_finanzas_item -> {
                val item = customDrawerView.findViewById<LinearLayout>(R.id.nav_finanzas_item)
                cargarFragmento(FinanzasFragment(), item)
            }
            R.id.nav_notas_item -> {
                val item = customDrawerView.findViewById<LinearLayout>(R.id.nav_notas_item)
                cargarFragmento(NotasFragment(), item)
            }
        }
    }
}