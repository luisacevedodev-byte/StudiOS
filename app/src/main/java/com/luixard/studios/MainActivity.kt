package com.luixard.studios

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luixard.studios.interfaz.tareas.ListaTareasFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Esto carga tu pantalla de tareas en el contenedor principal al abrir la app
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, ListaTareasFragment())
                .commit()
        }
    }
}