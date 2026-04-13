package com.luixard.studios

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luixard.studios.interfaz.finanzas.FinanzasFragment
import com.luixard.studios.interfaz.notas.NotasFragment
import com.luixard.studios.interfaz.tareas.ListaTareasFragment
import com.luixard.studios.interfaz.inicio.DashboardFragment
import com.luixard.studios.interfaz.perfil.PerfilFragment
import com.luixard.studios.interfaz.perfil.ConfiguracionFragment
import com.luixard.studios.interfaz.notificaciones.RegistrarAvanceDialogFragment
import android.widget.Toast
import com.luixard.studios.datos.sync.SyncManager

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var customDrawerView: View
    private lateinit var itemsMenu: List<LinearLayout>

    private var loginRecienteEnEsteDispositivo = false

    companion object {
        private const val CODIGO_PERMISO_NOTIF = 1001
        private const val PREFS                = "studios_config"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout     = findViewById(R.id.drawer_layout)
        customDrawerView = findViewById(R.id.custom_drawer_view)

        val btnOpenDrawer  = findViewById<ImageView>(R.id.btnOpenDrawer)
        val btnCloseDrawer = customDrawerView.findViewById<ImageView>(R.id.btnCloseDrawer)

        val headerPerfil   = customDrawerView.findViewById<View>(R.id.nav_header_perfil)
        val IconoPerfil    = customDrawerView.findViewById<View>(R.id.nav_img_perfil)
        val btnNavVincular = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavVincular)

        val optDashboard = customDrawerView.findViewById<LinearLayout>(R.id.nav_dashboard_item)
        val optTareas    = customDrawerView.findViewById<LinearLayout>(R.id.nav_tareas_item)
        val optFinanzas  = customDrawerView.findViewById<LinearLayout>(R.id.nav_finanzas_item)
        val optNotas     = customDrawerView.findViewById<LinearLayout>(R.id.nav_notas_item)
        val optAjustes   = customDrawerView.findViewById<LinearLayout>(R.id.nav_ajustes_item)

        itemsMenu = listOf(optDashboard, optTareas, optFinanzas, optNotas)

        if (savedInstanceState == null) {
            cargarFragmento(DashboardFragment(), optDashboard)
        }

        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            btnNavVincular.visibility =
                if (auth.currentUser != null) android.view.View.GONE else android.view.View.VISIBLE
        }

        btnOpenDrawer.setOnClickListener  { drawerLayout.openDrawer(GravityCompat.START) }
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
                val colorCyan = ContextCompat.getColor(this, R.color.studios_cyan_titulo)
                ivStatus.setColorFilter(colorCyan)
                tvStatus.setTextColor(colorCyan)
            } else {
                ivStatus.setImageResource(R.drawable.ic_wifi_off)
                tvStatus.text = "Offline"
                val colorGris = ContextCompat.getColor(this, R.color.gris_texto)
                ivStatus.setColorFilter(colorGris)
                tvStatus.setTextColor(colorGris)
            }
        }

        SyncManager.alCerrarSesionPorOtroDispositivo = {
            // Ignorar si este dispositivo fue el que acaba de iniciar sesión:
            // el listener de Firestore se dispara localmente al escribir el propio token.
            if (!loginRecienteEnEsteDispositivo) {
                runOnUiThread {
                    Toast.makeText(this,
                        "Se cerró la sesión, nuevo inicio de sesión en otro dispositivo.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        manejarIntentDeNotificacion(intent)

        // ── Marcar login reciente para no disparar el callback de "otro dispositivo" ──
        val authViewModel: com.luixard.studios.interfaz.perfil.AuthViewModel by viewModels()
        authViewModel.authEstado.observe(this) { estado ->
            if (estado is com.luixard.studios.interfaz.perfil.AuthEstado.LoginExito ||
                estado is com.luixard.studios.interfaz.perfil.AuthEstado.GoogleExito) {
                loginRecienteEnEsteDispositivo = true
                // Resetear el flag después de 4 s — tiempo suficiente para que el
                // listener de Firestore se dispare y sea ignorado.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loginRecienteEnEsteDispositivo = false
                }, 4_000)
            }
        }

        // ── Solicitar permisos al primer arranque ──────────────────────────────
        solicitarPermisosNecesarios()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLUJO DE PERMISOS CON UI EDUCATIVA
    // ─────────────────────────────────────────────────────────────────────────
    private fun solicitarPermisosNecesarios() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        // Si ya se presentó el flujo, solo verificar batería silenciosamente
        if (prefs.getBoolean("permisos_presentados", false)) {
            verificarBateriaConDialog(silencioso = true)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_notificacion_activa)
            .setTitle("Activar recordatorios 🔔")
            .setMessage(
                "StudiOS te avisará cada día sobre tus tareas pendientes para que nunca se te olvide una entrega.\n\n" +
                        "Necesitamos dos cosas:\n" +
                        "  • Permiso para enviar notificaciones\n" +
                        "  • Funcionar en segundo plano sin restricciones de batería"
            )
            .setPositiveButton("Activar") { _, _ ->
                prefs.edit().putBoolean("permisos_presentados", true).apply()
                pedirPermisoNotificacion()
            }
            .setNegativeButton("Ahora no") { _, _ ->
                prefs.edit().putBoolean("permisos_presentados", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun pedirPermisoNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permiso = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permiso), CODIGO_PERMISO_NOTIF)
                return
            }
        }
        verificarBateriaConDialog(silencioso = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODIGO_PERMISO_NOTIF) {
            verificarBateriaConDialog(silencioso = false)
        }
    }

    private fun verificarBateriaConDialog(silencioso: Boolean) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            return
        }

        val esRestringido = esDispositivoRestringido()
        val mensajeExtra  = obtenerMensajeExtraFabricante()

        if (silencioso && !esRestringido) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Funcionar en segundo plano ⚙️")
            .setMessage(
                "Para que los recordatorios lleguen exactamente a la hora que configures, " +
                        "incluso con la app cerrada, desactiva la optimización de batería para StudiOS." +
                        mensajeExtra
            )
            .setPositiveButton("Configurar") { _, _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) { /* nada que hacer */ }
                }
            }
            .setNegativeButton("Omitir", null)
            .show()
    }

    private fun esDispositivoRestringido(): Boolean {
        val fabricante = android.os.Build.MANUFACTURER.lowercase()
        return fabricante in listOf(
            "xiaomi", "redmi", "poco",          // MIUI / HyperOS
            "huawei", "honor",                  // EMUI / MagicUI
            "samsung",                          // OneUI
            "oneplus",                          // OxygenOS
            "oppo", "realme",                   // ColorOS
            "vivo",                             // OriginOS / Funtouch
            "meizu",                            // Flyme
            "zte", "nubia",                     // MyOS
            "lenovo", "motorola"                // algunas versiones de Moto restringen alarmas
        ) || esMIUI()
    }

    private fun obtenerMensajeExtraFabricante(): String {
        val fabricante = android.os.Build.MANUFACTURER.lowercase()
        return when {
            fabricante in listOf("xiaomi", "redmi", "poco") || esMIUI() ->
                "\n\nEn tu dispositivo Xiaomi / POCO / Redmi también ve a:\n" +
                        "Ajustes › Aplicaciones › StudiOS › Autoarranque y actívalo.\n" +
                        "Sin esto, MIUI/HyperOS puede bloquear las alarmas en segundo plano."

            fabricante == "huawei" || fabricante == "honor" ->
                "\n\nEn tu dispositivo Huawei / Honor también ve a:\n" +
                        "Ajustes › Aplicaciones › StudiOS › Inicio de aplicación\n" +
                        "y activa 'Gestión manual' con todas las opciones habilitadas."

            fabricante == "samsung" ->
                "\n\nEn tu dispositivo Samsung también ve a:\n" +
                        "Ajustes › Batería › Uso de batería de la aplicación › StudiOS\n" +
                        "y selecciona 'Sin restricciones'."

            fabricante == "oneplus" ->
                "\n\nEn tu dispositivo OnePlus también ve a:\n" +
                        "Ajustes › Batería › Optimización de batería › StudiOS\n" +
                        "y selecciona 'No optimizar'."

            fabricante == "oppo" || fabricante == "realme" ->
                "\n\nEn tu dispositivo OPPO / Realme también ve a:\n" +
                        "Ajustes › Administración de apps › StudiOS\n" +
                        "y activa 'Inicio automático'."

            fabricante == "vivo" ->
                "\n\nEn tu dispositivo Vivo también ve a:\n" +
                        "iManager › Inicio automático de aplicaciones\n" +
                        "y activa StudiOS."

            else -> ""
        }
    }

    private fun esMIUI(): Boolean = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get   = clazz.getMethod("get", String::class.java)
        (get.invoke(clazz, "ro.miui.ui.version.name") as String).isNotEmpty()
    } catch (_: Exception) { false }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVEGACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private fun manejarIntentDeNotificacion(intent: Intent?) {
        when (intent?.getStringExtra("destino")) {
            "registrar_avance" -> {
                RegistrarAvanceDialogFragment.newInstance()
                    .show(supportFragmentManager, "registrar_avance")
                intent.removeExtra("destino")
            }
            "tareas" -> {
                val item = customDrawerView.findViewById<LinearLayout>(R.id.nav_tareas_item)
                cargarFragmento(ListaTareasFragment(), item)
                intent.removeExtra("destino")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        manejarIntentDeNotificacion(intent)
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
        val uiMode     = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
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