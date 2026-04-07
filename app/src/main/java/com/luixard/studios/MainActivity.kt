package com.luixard.studios

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luixard.studios.interfaz.finanzas.FinanzasFragment // Importamos el fragmento de finanzas

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cambiamos el fragmento que se carga inicialmente
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedor_principal, FinanzasFragment())
                .commit()
        }
    }
}