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
import com.luixard.studios.interfaz.inicio.DashboardFragment
import com.luixard.studios.interfaz.perfil.PerfilFragment
import com.luixard.studios.interfaz.perfil.ConfiguracionFragment
import android.widget.Toast
import com.luixard.studios.datos.sync.SyncManager

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

        // Referencias a la Barra Superior
        val btnOpenDrawer = findViewById<ImageView>(R.id.btnOpenDrawer)
        val btnCloseDrawer = customDrawerView.findViewById<ImageView>(R.id.btnCloseDrawer)

        // Referencia a la cabecera del menú
        val headerPerfil = customDrawerView.findViewById<View>(R.id.nav_header_perfil)
        val IconoPerfil = customDrawerView.findViewById<View>(R.id.nav_img_perfil)
        val btnNavVincular = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavVincular)

        // Referencias a los items del Menú Lateral
        val optDashboard    = customDrawerView.findViewById<LinearLayout>(R.id.nav_dashboard_item)
        val optTareas       = customDrawerView.findViewById<LinearLayout>(R.id.nav_tareas_item)
        val optFinanzas     = customDrawerView.findViewById<LinearLayout>(R.id.nav_finanzas_item)
        val optNotas        = customDrawerView.findViewById<LinearLayout>(R.id.nav_notas_item)
        // ── NUEVO: referencia al item de Configuración ──────────────────────
        val optAjustes      = customDrawerView.findViewById<LinearLayout>(R.id.nav_ajustes_item)

        // Solo los items principales participan en el resaltado azul del menú
        itemsMenu = listOf(optDashboard, optTareas, optFinanzas, optNotas)

        // CARGA INICIAL
        if (savedInstanceState == null) {
            cargarFragmento(DashboardFragment(), optDashboard)
        }

        // Controlar visibilidad según la sesión de btnNavVincular
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                btnNavVincular.visibility = android.view.View.GONE
            } else {
                btnNavVincular.visibility = android.view.View.VISIBLE
            }
        }

        // --- LISTENERS ---

        btnOpenDrawer.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnCloseDrawer.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }

        optDashboard.setOnClickListener { cargarFragmento(DashboardFragment(), optDashboard) }
        optTareas.setOnClickListener    { cargarFragmento(ListaTareasFragment(), optTareas)  }
        optFinanzas.setOnClickListener  { cargarFragmento(FinanzasFragment(), optFinanzas)   }
        optNotas.setOnClickListener     { cargarFragmento(NotasFragment(), optNotas)         }

        optAjustes?.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, ConfiguracionFragment())
                .addToBackStack(null)
                .commit()
            actualizarEstiloMenu(null)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnNavVincular.setOnClickListener {
            val bundle = Bundle().apply { putBoolean("abrirVincularDirecto", true) }
            val perfilFrag = PerfilFragment().apply { arguments = bundle }
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, perfilFrag)
                .commit()
        }

        // LISTENER PARA EL PERFIL
        headerPerfil?.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, PerfilFragment())
                .addToBackStack(null)
                .commit()
            actualizarEstiloMenu(null)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        IconoPerfil?.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, PerfilFragment())
                .addToBackStack(null)
                .commit()
            actualizarEstiloMenu(null)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        val ivStatus = findViewById<ImageView>(R.id.ivOffline)
        val tvStatus = findViewById<TextView>(R.id.tvStatusText)

        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                ivStatus.setImageResource(R.drawable.ic_wifi_on)
                tvStatus.text = "Online"
                val colorCyan = androidx.core.content.ContextCompat.getColor(this, R.color.studios_cyan_titulo)
                ivStatus.setColorFilter(colorCyan)
                tvStatus.setTextColor(colorCyan)
            } else {
                ivStatus.setImageResource(R.drawable.ic_wifi_off)
                tvStatus.text = "Offline"
                val colorGris = androidx.core.content.ContextCompat.getColor(this, R.color.gris_texto)
                ivStatus.setColorFilter(colorGris)
                tvStatus.setTextColor(colorGris)
            }
        }

        SyncManager.alCerrarSesionPorOtroDispositivo = {
            runOnUiThread {
                Toast.makeText(this,
                    "Se cerró la sesión, nuevo inicio de sesión en otro dispositivo.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cargarFragmento(fragmento: Fragment, itemSeleccionado: LinearLayout) {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.contenedor_principal, fragmento)
            .commit()
        actualizarEstiloMenu(itemSeleccionado)
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun actualizarEstiloMenu(itemActivo: LinearLayout?) {
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val esModoOscuro = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        for (item in itemsMenu) {
            val icono = item.getChildAt(0) as ImageView
            val texto = item.getChildAt(1) as TextView

            if (item == itemActivo) {
                item.setBackgroundResource(R.drawable.selector_drawer_fondo)
                val colorPrincipal = if (esModoOscuro)
                    ContextCompat.getColor(this, R.color.studios_cyan)
                else
                    ContextCompat.getColor(this, R.color.studios_cyan_titulo)

                val colorFondo = if (esModoOscuro)
                    android.graphics.Color.parseColor("#2600D4FF")
                else
                    android.graphics.Color.parseColor("#1A007B99")

                item.background?.setTint(colorFondo)
                icono.setColorFilter(colorPrincipal)
                texto.setTextColor(colorPrincipal)
                texto.paint.isFakeBoldText = true
            } else {
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